#!/usr/bin/env python3
"""Record a physical phone's scheduler, rendering and FrameTimeline over ADB."""

import argparse
import datetime
import subprocess
from pathlib import Path


def config(seconds, verbose_graphics=False, presentation_only=False):
    ftrace = """
data_sources { config {
  name: "linux.ftrace"
  target_buffer: 0
  ftrace_config {
    ftrace_events: "sched/sched_switch"
    ftrace_events: "sched/sched_waking"
    ftrace_events: "power/cpu_frequency"
    ftrace_events: "power/cpu_idle"
    %s
    atrace_categories: "view"
    atrace_categories: "wm"
    atrace_categories: "am"
    atrace_categories: "input"
    atrace_categories: "dalvik"
    atrace_apps: "com.music.bitchord"
    atrace_apps: "com.android.systemui"
  }
} }
""" % ('atrace_categories: "gfx"' if verbose_graphics else '')
    return """
buffers { size_kb: 65536 fill_policy: RING_BUFFER }
buffers { size_kb: 8192 fill_policy: RING_BUFFER }
duration_ms: %d
write_into_file: true
file_write_period_ms: 1000
flush_period_ms: 5000
compression_type: COMPRESSION_TYPE_DEFLATE
%s
data_sources { config {
  name: "android.surfaceflinger.frametimeline"
  target_buffer: 1
} }
data_sources { config {
  name: "linux.process_stats"
  target_buffer: 1
  process_stats_config { scan_all_processes_on_start: true }
} }
""" % (seconds * 1000, '' if presentation_only else ftrace)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", help="Physical phone's connected ADB serial, e.g. IP:PORT")
    parser.add_argument("--adb", default="adb", help="Path to the Android SDK adb executable")
    parser.add_argument("--seconds", type=int, default=45)
    parser.add_argument("--output", type=Path, default=Path("output/phone-traces"))
    detail = parser.add_mutually_exclusive_group()
    detail.add_argument("--verbose-graphics", action="store_true",
                        help="Include gfx/Skia internals; can add substantial tracing overhead")
    detail.add_argument("--presentation-only", action="store_true",
                        help="FrameTimeline/process metadata only; avoid app/ftrace tracing overhead")
    parser.add_argument("--print-config", action="store_true", help="Print configuration without contacting any device")
    args = parser.parse_args()
    if not 5 <= args.seconds <= 120:
        parser.error("--seconds must be between 5 and 120")
    if args.print_config:
        print(config(args.seconds, args.verbose_graphics, args.presentation_only))
        return
    if not args.serial or args.serial.startswith("emulator-"):
        parser.error("An explicit physical-phone --serial is required; emulators are excluded")
    adb = [args.adb, "-s", args.serial]
    def prop(key):
        return subprocess.check_output(adb + ["shell", "getprop", key], text=True).strip()
    if prop("ro.kernel.qemu") == "1" or prop("ro.boot.qemu") == "1":
        parser.error("The selected target reports that it is an emulator")
    print(f"Recording {prop('ro.product.model')} for {args.seconds} seconds. Reproduce scrolling/player/minimize jank now.", flush=True)
    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    filename = f"bitchord-{stamp}.pftrace"
    remote = f"/data/misc/perfetto-traces/{filename}"
    args.output.mkdir(parents=True, exist_ok=True)
    trace_config = config(args.seconds, args.verbose_graphics, args.presentation_only)
    (args.output / f"bitchord-{stamp}.pbtxt").write_text(trace_config)
    subprocess.run(adb + ["shell", "perfetto", "--txt", "-c", "-", "-o", remote],
                   input=trace_config, text=True, check=True, timeout=args.seconds + 45)
    print("Capture finished. You can stop reproducing the issue; transferring the trace now.", flush=True)
    out = args.output / filename
    subprocess.run(adb + ["pull", remote, str(out)], check=True, timeout=300)
    print(out.resolve())


if __name__ == "__main__":
    main()
