import { listening, setListeningSource } from '@/data/backup/listening';
import { onPeersChanged, peerMonths } from '@/data/backup/store';
import { ownMonths, replaceMonths, replacePeerMonths } from './store';

const encoder = new TextEncoder();
const decoder = new TextDecoder();

/**
 * Plugs the Replay's store into backup and Drive sync (data/backup/listening.ts):
 * an export or an upload sends this browser's months, a restore or an imported
 * backup replaces them, and the phone's months that sync downloads land in the
 * Replay as other devices' listening, as PeerListening.kt does on the phone.
 *
 * The month arithmetic (BucketMath plus/minus) is still the phone's alone, so
 * the kept defaults stay: an unknown difference counts as nothing, which never
 * counts a play twice. Returns the unsubscribe.
 */
export function connectReplayToBackup(): () => void {
  const kept = listening();
  setListeningSource({
    exportMonths: async () => new Map([...(await ownMonths())].map(([month, json]) => [month, encoder.encode(json)])),
    importMonths: (months) => replaceMonths(new Map([...months].map(([month, bytes]) => [month, decoder.decode(bytes)]))),
    minus: kept.minus,
    plus: kept.plus,
    readable: kept.readable,
  });
  const copyPeers = async () =>
    replacePeerMonths((await peerMonths()).map((peer) => ({ deviceId: peer.deviceId, month: peer.month, json: decoder.decode(peer.bytes) })));
  void copyPeers().catch(() => {});
  return onPeersChanged(() => void copyPeers().catch(() => {}));
}
