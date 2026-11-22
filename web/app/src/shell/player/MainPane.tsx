import { PrefetchLink } from '@/components/PrefetchLink';
import { albumHref, artistHref, hrefFor } from '@/components/music/links';
import type { Track } from '@/data/music/types';
import type { QueueSource } from '@/playback/queue/timeline';
import { usePlayer, type NowPlaying } from '@/state/player';
import { NerdStats } from '../NerdStats';
import { LyricStrip } from './LyricStrip';
import { PlayerCredits } from './PlayerCredits';
import { Scrubber } from './Scrubber';
import { Transport } from './Transport';
import { VolumeRow } from './VolumeRow';

/** Where a queue's source lives, when it is somewhere a page can show. */
function sourceHref(source: QueueSource): string | null {
  if (!source.id) return null;
  if (source.type === 'album') return albumHref(source.id);
  if (source.type === 'artist') return artistHref(source.id);
  if (source.type === 'playlist') return hrefFor({ kind: 'playlist', id: source.id });
  return null;
}

/**
 * "Playing from …" (PlayerControls.kt PlaybackOriginCaption): labelSmall in
 * white with a soft shadow, linking back to the album, playlist or artist
 * the queue came from when there is one.
 */
function OriginCaption({ track }: { track: Track | null }) {
  const source = usePlayer((s) => s.queue[s.index]?.source ?? null);
  const name = source?.type === 'radio' ? null : (source?.title ?? track?.album?.name ?? 'Queue');
  const text = source?.type === 'radio' ? `Playing ${source.title} Radio` : `Playing from ${name}`;
  const href = source ? sourceHref(source) : null;
  const className = 'block max-w-full truncate py-0.5 text-start type-subhead-emphasized text-white [text-shadow:0_1px_4px_rgb(0_0_0/0.55)]';
  return href ? (
    <PrefetchLink to={href} className={`${className} hover:underline`}>
      {text}
    </PrefetchLink>
  ) : (
    <p className={className}>{text}</p>
  );
}

/**
 * The right column in its main state (LandscapePlayer.kt LandscapeMainPane):
 * where the queue came from, the credits, the current line, the scrubber, the
 * transport and the volume bar, at most 560px wide. A short window
 * (`compact`, under 440px tall) tightens every gap and the transport.
 */
export function MainPane({
  nowPlaying,
  track,
  compact,
  onOpenLyrics,
}: {
  nowPlaying: NowPlaying;
  track: Track | null;
  compact: boolean;
  onOpenLyrics: () => void;
}) {
  return (
    <div className="flex w-full max-w-[560px] flex-col">
      <OriginCaption track={track} />
      <div style={{ height: compact ? 4 : 8 }} />
      <PlayerCredits nowPlaying={nowPlaying} track={track} />
      <NerdStats />
      <div style={{ height: compact ? 2 : 10 }} />
      <LyricStrip onOpen={onOpenLyrics} />
      <Scrubber />
      <div style={{ height: compact ? 0 : 8 }} />
      <Transport compact={compact} />
      <div style={{ height: compact ? 0 : 12 }} />
      <VolumeRow />
    </div>
  );
}
