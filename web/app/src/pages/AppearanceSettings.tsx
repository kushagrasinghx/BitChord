import { Clock, Languages, SunMoon } from 'lucide-react';
import { lazy, Suspense, useState } from 'react';
import { usePlayerOptions } from '@/playback/playerOptions';
import { THEME_MODES, useTheme } from '@/state/theme';
import { AnimatedArtworkToggle, CanvasToggle, LyricSourcesAccess } from './IntegrationSettings';
import { ButtonRow, ChoiceRow, Group, Toggle } from './SettingsRows';

const LyricsSettings = lazy(() => import('@/shell/LyricsSettings').then((m) => ({ default: m.LyricsSettings })));

/**
 * Appearance (SettingsSheet.kt APPEARANCE): Theme, with its three choices in
 * a segmented control under the row's title as the phone lays it out, then
 * the player's look, the moving covers, and the lyrics.
 */
export function AppearanceSettings() {
  const mode = useTheme((s) => s.mode);
  const setMode = useTheme((s) => s.setMode);
  const showTotalDuration = usePlayerOptions((s) => s.showTotalDuration);
  const [lyrics, setLyrics] = useState(false);
  return (
    <>
      <Group header="Appearance">
        <ChoiceRow icon={SunMoon} title="Theme" keywords={['dark mode', 'light mode']} options={THEME_MODES} value={mode} onChange={setMode} />
        <Toggle
          icon={Clock}
          label="Show Total Duration"
          detail="Shows the song’s full length instead of the time remaining."
          keywords={['seek bar', 'remaining time', 'duration', 'song length']}
          checked={showTotalDuration}
          onChange={usePlayerOptions.getState().setShowTotalDuration}
        />
        <AnimatedArtworkToggle />
        <CanvasToggle />
      </Group>
      <Group header="Lyrics">
        <ButtonRow
          icon={Languages}
          title="Lyric Sources"
          detail="Order, sources, syllable timing"
          keywords={['lyrics', 'lrclib', 'musixmatch', 'synced', 'karaoke']}
          onClick={() => setLyrics(true)}
        />
        <LyricSourcesAccess />
      </Group>
      {lyrics && (
        <Suspense fallback={null}>
          <LyricsSettings onClose={() => setLyrics(false)} />
        </Suspense>
      )}
    </>
  );
}
