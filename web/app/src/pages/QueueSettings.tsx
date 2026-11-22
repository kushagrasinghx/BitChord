import { History } from 'lucide-react';
import { usePlayer } from '@/state/player';
import { Toggle } from './SettingsRows';

/** Settings' queue rows (SettingsSheet's "Show queue history", under Miscellaneous). */
export function QueueSettings() {
  const showQueueHistory = usePlayer((s) => s.showQueueHistory);
  return (
    <Toggle
      icon={History}
      label="Show Queue History"
      detail="Show up to 25 previously played songs above what’s next in Up Next."
      keywords={['player', 'queue', 'history', 'previously played']}
      checked={showQueueHistory}
      onChange={usePlayer.getState().setShowQueueHistory}
    />
  );
}
