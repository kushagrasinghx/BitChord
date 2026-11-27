import { ownMonths, setOwnMonths } from './store';

/**
 * The seam between backup and sync and the Replay store: this device's
 * listening, month by month, each month as BucketMath.canonicalBytes (the JSON
 * the phone writes, `{"month":"2026-11",...}`). Backup and sync never look
 * inside a month; the Replay store, which owns that format, registers itself
 * with [setListeningSource] and does the arithmetic.
 *
 * Other devices' months are not part of it: sync keeps those itself
 * (store.ts peerMonths / onPeersChanged) and the Replay adds them in when read,
 * as the phone's PeerListening does.
 *
 * Until a Replay store is registered, months from an imported backup or a
 * restored device are kept as they came, so exporting or syncing again loses
 * nothing.
 */
export interface ListeningSource {
  /** ListeningStats.exportAll: this device's months, by month ("2026-11"). */
  exportMonths(): Promise<Map<string, Uint8Array>>;
  /** ListeningStats.importAll: replaces this device's months with these. */
  importMonths(months: Map<string, Uint8Array>): Promise<void>;
  /** BucketMath.minus(a, b), or null when nothing is left (BucketMath.isEmpty). */
  minus(a: Uint8Array, b: Uint8Array): Uint8Array | null;
  /** BucketMath.plus. */
  plus(a: Uint8Array, b: Uint8Array): Uint8Array;
  /** Whether BucketMath.decode reads it: a month this build can't is left out rather than breaking the Replay. */
  readable(bytes: Uint8Array): boolean;
}

/** Months kept as they came, with no arithmetic: whatever is unknown is never counted twice. */
const keptMonths: ListeningSource = {
  exportMonths: ownMonths,
  importMonths: setOwnMonths,
  // Without the Replay's maths a difference can't be worked out; treating it
  // as empty keeps the copy already in Drive, and never counts a play twice.
  minus: () => null,
  plus: (a) => a,
  readable: (bytes) => {
    try {
      return typeof (JSON.parse(new TextDecoder().decode(bytes)) as { month?: unknown }).month === 'string';
    } catch {
      return false;
    }
  },
};

let source: ListeningSource = keptMonths;

/** For the Replay store: it becomes where this device's listening comes from and goes to. */
export function setListeningSource(next: ListeningSource): void {
  source = next;
}

export const listening = (): ListeningSource => source;
