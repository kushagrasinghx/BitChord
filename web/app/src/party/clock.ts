/**
 * This device's offset from the party server's clock: ServerClock.kt.
 *
 * NTP cut down to one WebSocket: offset = serverMs − (t0 + t1) / 2, and the
 * sample with the *smallest* round trip wins (its error is at most half that
 * trip), out of the last [WINDOW] taken within [SAMPLE_TTL_MS].
 *
 * The local reading is `performance.timeOrigin + performance.now()`
 * (prompt §8.2): monotonic like `elapsedRealtime`, so a stepped wall clock
 * never lands in the offset.
 */

const WINDOW = 12;
const SAMPLE_TTL_MS = 120_000;

interface Sample {
  offsetMs: number;
  roundTripMs: number;
  takenAtMs: number;
}

export const monotonicNow = () => performance.timeOrigin + performance.now();

export class ServerClock {
  private samples: Sample[] = [];
  offsetMs: number | null = null;
  roundTripMs = 0;

  constructor(private readonly localNowMs: () => number = monotonicNow) {}

  get synced(): boolean {
    return this.offsetMs !== null;
  }

  record(sentAtLocalMs: number, serverMs: number, receivedAtLocalMs: number): void {
    const roundTrip = Math.max(0, receivedAtLocalMs - sentAtLocalMs);
    const midpoint = sentAtLocalMs + Math.floor(roundTrip / 2);
    this.samples.push({ offsetMs: serverMs - midpoint, roundTripMs: roundTrip, takenAtMs: receivedAtLocalMs });
    while (this.samples.length > WINDOW) this.samples.shift();
    const cutoff = receivedAtLocalMs - SAMPLE_TTL_MS;
    const fresh = this.samples.filter((s) => s.takenAtMs >= cutoff);
    const usable = fresh.length ? fresh : this.samples;
    const best = usable.reduce((a, b) => (b.roundTripMs < a.roundTripMs ? b : a));
    this.offsetMs = best.offsetMs;
    this.roundTripMs = best.roundTripMs;
  }

  /** The server's clock, read from here; null until the first pong. */
  serverNowMs(): number | null {
    return this.offsetMs === null ? null : this.localNowMs() + this.offsetMs;
  }

  reset(): void {
    this.samples = [];
    this.offsetMs = null;
    this.roundTripMs = 0;
  }

  nowMs(): number {
    return this.localNowMs();
  }
}
