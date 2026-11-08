// From AutoMix (https://github.com/heydavidf/automix), Copyright (C) 2026 David Finnegan.
// AGPL-3.0-only: see ./LICENSE and ./NOTICE.md in playback/automix.

export interface TimelinePoint {
  /** AudioContext time the change takes effect. */
  output: number;
  /** Input-buffer (track) time at that moment. */
  input: number;
  rate: number;
  active: boolean;
}

/**
 * Mirror of the schedule() calls sent to a stretch node, so we can convert
 * trackTime <-> AudioContext time exactly. Mirrors signalsmith semantics:
 * adding a point removes any scheduled points at or after its output time.
 */
export class Timeline {
  private points: TimelinePoint[] = [];

  add(p: TimelinePoint): void {
    this.points = this.points.filter((q) => q.output < p.output);
    this.points.push(p);
  }

  clear(): void {
    this.points = [];
  }

  get isEmpty(): boolean {
    return this.points.length === 0;
  }

  private segmentAt(ctxTime: number): TimelinePoint | null {
    let seg: TimelinePoint | null = null;
    for (const p of this.points) {
      if (p.output <= ctxTime) seg = p;
      else break;
    }
    return seg;
  }

  trackTimeAt(ctxTime: number): number | null {
    const seg = this.segmentAt(ctxTime);
    if (!seg) return null;
    if (!seg.active) return seg.input;
    return seg.input + (ctxTime - seg.output) * seg.rate;
  }

  rateAt(ctxTime: number): number {
    const seg = this.segmentAt(ctxTime);
    return seg && seg.active ? seg.rate : 0;
  }

  activeAt(ctxTime: number): boolean {
    return this.segmentAt(ctxTime)?.active ?? false;
  }

  /**
   * AudioContext time at which the given track time will play, searching
   * forward from the segment containing fromCtxTime (or all segments).
   */
  ctxTimeAt(trackTime: number, fromCtxTime = -Infinity): number | null {
    for (let i = 0; i < this.points.length; i++) {
      const p = this.points[i]!;
      if (!p.active || p.rate <= 0) continue;
      const next = this.points[i + 1];
      const segEnd = next ? next.output : Infinity;
      const t = p.output + (trackTime - p.input) / p.rate;
      if (t >= Math.max(p.output, fromCtxTime) && t < segEnd) return t;
    }
    return null;
  }
}
