import { usePlayerOptions } from '@/playback/playerOptions';
import { Toggle } from './IntegrationSettings';
import { settingsUi } from './settingsStyles';

/** The phone's player options (SettingsSheet): the Best Part button and the seek line's right-hand time. */
export function PlayerSettings() {
  const showBestPart = usePlayerOptions((s) => s.showBestPart);
  const showTotalDuration = usePlayerOptions((s) => s.showTotalDuration);
  const { setShowBestPart, setShowTotalDuration } = usePlayerOptions.getState();
  return (
    <>
      <h2 className={settingsUi.sectionHeader}>Player</h2>
      <section className={settingsUi.group}>
        <Toggle
          label="Best Part Button"
          detail="A button on the artwork that skips to the part of the song people replay most."
          checked={showBestPart}
          onChange={setShowBestPart}
        />
        <Toggle
          label="Show Total Duration"
          detail="Shows the song’s full length instead of the time remaining."
          checked={showTotalDuration}
          onChange={setShowTotalDuration}
        />
      </section>
    </>
  );
}
