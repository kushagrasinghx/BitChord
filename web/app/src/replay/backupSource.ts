import { bucketMinus, bucketPlus } from '@bitchord/shared-kotlin';
import { listening, setListeningSource } from '@/data/backup/listening';
import { onPeersChanged, peerMonths } from '@/data/backup/store';
import { ownMonths, replaceMonths, replacePeerMonths } from './store';

const encoder = new TextEncoder();
const decoder = new TextDecoder();
// Kotlin's ByteArray is an Int8Array: the same bytes, viewed signed.
const signed = (bytes: Uint8Array) => new Int8Array(bytes.buffer, bytes.byteOffset, bytes.byteLength);
const unsigned = (bytes: Int8Array) => new Uint8Array(bytes.buffer, bytes.byteOffset, bytes.byteLength);

/**
 * Plugs the Replay's store into backup and Drive sync (data/backup/listening.ts):
 * an export or an upload sends this browser's months, a restore or an imported
 * backup replaces them, and the phone's months that sync downloads land in the
 * Replay as other devices' listening, as PeerListening.kt does on the phone.
 *
 * The month arithmetic is the phone's own BucketMath plus/minus (shared Kotlin),
 * over the same canonical bytes. Returns the unsubscribe.
 */
export function connectReplayToBackup(): () => void {
  const kept = listening();
  setListeningSource({
    exportMonths: async () => new Map([...(await ownMonths())].map(([month, json]) => [month, encoder.encode(json)])),
    importMonths: (months) => replaceMonths(new Map([...months].map(([month, bytes]) => [month, decoder.decode(bytes)]))),
    minus: (a, b) => {
      const left = bucketMinus(signed(a), signed(b));
      return left ? unsigned(left) : null;
    },
    plus: (a, b) => unsigned(bucketPlus(signed(a), signed(b))),
    readable: kept.readable,
  });
  const copyPeers = async () =>
    replacePeerMonths((await peerMonths()).map((peer) => ({ deviceId: peer.deviceId, month: peer.month, json: decoder.decode(peer.bytes) })));
  void copyPeers().catch(() => {});
  return onPeersChanged(() => void copyPeers().catch(() => {}));
}
