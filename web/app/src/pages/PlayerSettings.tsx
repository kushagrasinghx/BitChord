import { ChartColumn, Infinity as Loop, Music, Sparkles, Waves } from 'lucide-react';
import { AUTOMIX_BARS } from '@/playback/automix/choices';
import { usePlayerOptions } from '@/playback/playerOptions';
import { CROSSFADE_CHOICES, usePlayer } from '@/state/player';
import { QueueSettings } from './QueueSettings';
import { ChoiceRow, Group, Toggle } from './SettingsRows';

const AUTOMIX_NOTE =
  'Automix finds each song’s tempo, key and phrases, then blends on the beat: the next song is time-stretched to match, and the bass swaps over halfway. Where it can, it loops a few instrumental bars of each song and ramps the tempo between them. Songs it can’t read in time play to the end and crossfade. Based on AutoMix by David Finnegan (AGPL-3.0).';

/** Playback (SettingsSheet.kt PLAYBACK): AutoPlay, and how one song gives way to the next. */
export function PlaybackSettings() {
  const autoplay = usePlayer((s) => s.autoplay);
  const crossfadeSec = usePlayer((s) => s.crossfadeSec);
  const automix = usePlayer((s) => s.automix);
  const automixBars = usePlayer((s) => s.automixBars);
  const { setAutoplay, setCrossfade, setAutomix, setAutomixBars } = usePlayer.getState();
  return (
    <Group header="Playback" footer={automix ? AUTOMIX_NOTE : undefined}>
      <Toggle
        icon={Loop}
        label="AutoPlay"
        detail="When the queue ends, keep playing similar music."
        keywords={['radio', 'similar', 'queue']}
        checked={autoplay}
        onChange={setAutoplay}
      />
      <ChoiceRow<number | 'automix'>
        icon={Waves}
        title="Crossfade"
        keywords={['fade', 'gapless', 'automix', 'blend']}
        options={[
          ...CROSSFADE_CHOICES.map((seconds) => ({ value: seconds, label: seconds === 0 ? 'Off' : `${seconds}s` })),
          { value: 'automix', label: 'Automix' },
        ]}
        value={automix ? 'automix' : crossfadeSec}
        onChange={(value) => (value === 'automix' ? setAutomix(true) : setCrossfade(value))}
      />
      {automix && (
        <ChoiceRow
          icon={Sparkles}
          title="Blend Length"
          detail="The longest blend Automix makes, in bars."
          keywords={['automix', 'bars']}
          options={AUTOMIX_BARS.map((bars) => ({ value: bars, label: `${bars} bars` }))}
          value={automixBars}
          onChange={setAutomixBars}
        />
      )}
    </Group>
  );
}

/** Miscellaneous (SettingsSheet.kt MISCELLANEOUS): the player's smaller switches. */
export function MiscSettings() {
  const showBestPart = usePlayerOptions((s) => s.showBestPart);
  const showNerdStats = usePlayer((s) => s.showNerdStats);
  const { setShowBestPart } = usePlayerOptions.getState();
  return (
    <Group header="Miscellaneous">
      <QueueSettings />
      <Toggle
        icon={Music}
        label="Best Part Button"
        detail="A button on the artwork that skips to the part of the song people replay most."
        keywords={['best part', 'chorus', 'skip', 'replayed', 'highlight']}
        checked={showBestPart}
        onChange={setShowBestPart}
      />
      <Toggle
        icon={ChartColumn}
        label="Show Stats for Nerds"
        detail="Codec, bitrate and sample rate under the artist in the full-screen player, and what Automix is doing."
        keywords={['debug', 'bitrate', 'codec']}
        checked={showNerdStats}
        onChange={usePlayer.getState().setShowNerdStats}
      />
    </Group>
  );
}
