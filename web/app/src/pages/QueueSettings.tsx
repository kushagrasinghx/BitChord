import { usePlayer } from '@/state/player';
import { Toggle } from './IntegrationSettings';

/** Settings' queue rows (SettingsSheet's "Show queue history"). */
export function QueueSettings() {
  const showQueueHistory = usePlayer((s) => s.showQueueHistory);
  return (
    <Toggle
      label="Show Queue History"
      detail="Show up to 25 previously played songs above what’s next in Up Next."
      checked={showQueueHistory}
      onChange={usePlayer.getState().setShowQueueHistory}
    />
  );
}
