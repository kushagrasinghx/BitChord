#!/usr/bin/env python3
"""Summarize JSON/ZIP reports exported by BitChord's diagnostic release."""

import argparse
import json
import math
import statistics
import zipfile
from pathlib import Path


def reports(path):
    if zipfile.is_zipfile(path):
        with zipfile.ZipFile(path) as archive:
            for name in sorted(archive.namelist()):
                if name.endswith(".json"):
                    yield json.loads(archive.read(name))
    else:
        yield json.loads(path.read_text())


def percentile(values, quantile):
    values = sorted(value for value in values if value is not None and value >= 0)
    return values[max(0, math.ceil(len(values) * quantile) - 1)] if values else None


def ms(value):
    return "n/a" if value is None else f"{value / 1_000_000:.2f}"


def late(frame):
    return not frame["firstDraw"] and 0 < frame["deadlineNs"] < frame["totalNs"]


def normalize_frames(report):
    """Preserve distinct timing samples; reject repeat reports' conflicting budgets."""
    columns = report["frameColumns"]
    identity = [name for name in columns if name not in ("deadlineNs", "reportsLost")]
    groups = {}
    for row in report["frames"]:
        frame = dict(zip(columns, row))
        key = tuple(frame[name] for name in identity)
        groups.setdefault(key, []).append(frame)
    frames = []
    quality = {}
    for group in groups.values():
        frame = group[0].copy()
        q = quality.setdefault(frame["window"], {"reported": 0, "duplicates": 0, "conflicts": 0, "invalid": 0})
        budgets = {sample["deadlineNs"] for sample in group}
        conflict = len(budgets) > 1
        # Sanity guard only, never a substitute for an actual frame deadline.
        invalid = any(value > 1_000_000_000 for value in budgets)
        q["reported"] += len(group)
        q["duplicates"] += len(group) - 1
        q["conflicts"] += int(conflict)
        q["invalid"] += int(invalid)
        if conflict or invalid:
            frame["deadlineNs"] = -1
        frame["reportsLost"] = sum(sample["reportsLost"] for sample in group)
        frames.append(frame)
    return frames, quality


def analyze(report, details=False):
    if report.get("schema") != 1:
        raise ValueError("Unsupported report schema")
    frames, quality = normalize_frames(report)
    print(f"\n{report.get('manufacturer', '')} {report.get('model', '')} | Android {report.get('android')} | {report['mode']} | {report.get('id', '')}")
    if report.get("label"):
        print(f"  {report['label']}")
    print("  window       frames   late%  total p95   UI p95  GPU p95  budget p50  cadence p50  first max  lost reports")
    for window in sorted({frame["window"] for frame in frames}):
        samples = [frame for frame in frames if frame["window"] == window]
        steady = [frame for frame in samples if not frame["firstDraw"]]
        deadlines = [frame for frame in steady if frame["deadlineNs"] > 0 and frame["totalNs"] >= 0]
        complete = len(deadlines) == len(steady) and len(steady) > 0
        percentage = f"{100 * sum(late(frame) for frame in deadlines) / len(deadlines):5.1f}" if complete else "  n/a"
        intervals = [b["intendedNs"] - a["intendedNs"] for a, b in zip(samples, samples[1:])
                     if 0 < b["intendedNs"] - a["intendedNs"] <= 100_000_000]
        first = [frame["totalNs"] for frame in samples if frame["firstDraw"]]
        p95 = lambda key: ms(percentile([frame[key] for frame in steady], 0.95))
        print(f"  {window:12} {len(samples):6}  {percentage}  {p95('totalNs'):>9} {p95('uiNs'):>8} {p95('gpuNs'):>8}"
              f" {ms(percentile([frame['deadlineNs'] for frame in deadlines], 0.50) if complete else None):>11}"
              f" {ms(statistics.median(intervals) if intervals else None):>12} {ms(max(first) if first else None):>10}"
              f" {sum(frame['reportsLost'] for frame in samples):>13}")
        q = quality[window]
        if q["duplicates"] or q["conflicts"] or q["invalid"]:
            print(f"    Quality: {q['reported']} reports, {q['duplicates']} repeats collapsed;"
                  f" {q['conflicts']} samples with conflicting deadlines, {q['invalid']} with budgets over 1s.")
        if not complete:
            print(f"    Late percentage unavailable: {len(deadlines)}/{len(steady)} steady samples have usable deadlines.")
    operations = report.get("operations", [])
    for name in sorted({op["name"] for op in operations}):
        durations = [op["endNs"] - op["startNs"] for op in operations if op["name"] == name]
        print(f"  {name}: {len(durations)} calls; wall-time p95 {ms(percentile(durations, 0.95))} ms; max {ms(max(durations))} ms")
    events = report.get("events", [])
    seconds = (report.get("captureEndNs", 0) - report.get("captureStartNs", 0)) / 1e9
    for name in sorted({event["name"] for event in events}):
        count = sum(1 for event in events if event["name"] == name)
        rate = f" ({count / seconds:.1f}/s)" if seconds > 0 else ""
        print(f"  {name}: {count} events{rate}")
    if report.get("overflowFrames"):
        print(f"  BUFFER FULL: {report['overflowFrames']} additional frames omitted")
    if not frames:
        print("  No app frames captured. Wait for the 3-second warmup, then interact with the main app/player.")
    if details:
        print("  Window requests:", json.dumps(report.get("windows", [])))
        print("  Settings:", json.dumps(report.get("settingsDuring", {})))
        print("  Power:", json.dumps({key: report.get(key) for key in ("powerBefore", "powerAfter")}))
        print("  Profile verification:", json.dumps(report.get("profileVerification", "not recorded")))
        for frame in sorted(frames, key=lambda f: f["totalNs"], reverse=True)[:10]:
            start = frame["intendedNs"]
            end = start + frame["totalNs"]
            overlapping = {op["name"] for op in operations if op["startNs"] < end and op["endNs"] > start}
            budget = ms(frame["deadlineNs"]) if frame["deadlineNs"] > 0 else "unknown"
            print(f"  Slow {frame['window']} at {start}: total {ms(frame['totalNs'])} ms, deadline {budget} ms;"
                  f" overlapping operations: {', '.join(sorted(overlapping)) or 'none'}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reports", nargs="+", type=Path)
    parser.add_argument("--details", action="store_true")
    args = parser.parse_args()
    print("Durations in milliseconds. 'late%' is missed app deadlines, not a count of dropped display frames.")
    print("First draws are separate. Cadence uses adjacent same-window frames, excluding idle gaps over 100 ms.")
    print("Phase timings overlap; operation overlap is correlation, not proof of cause.")
    for path in args.reports:
        for report in reports(path):
            analyze(report, args.details)


if __name__ == "__main__":
    main()
