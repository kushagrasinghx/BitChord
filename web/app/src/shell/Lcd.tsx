import { useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { LogoMark } from '@/components/icons/LogoMark';
import { ExplicitBadge } from '@/components/music/ExplicitBadge';
import { VideoSurface } from '@/components/music/VideoSurface';
import { artistHref } from '@/components/music/links';
import { PrefetchLink } from '@/components/PrefetchLink';
import { artistCredits, creditSegments } from '@/lib/artistCredits';
import type { Track } from '@/data/music/types';
import { usePlayer } from '@/state/player';
import { useUi } from '@/state/ui';
import { useHasVideo } from '@/state/video';
import { artistPageFor } from './nowPlayingLinks';
import { NowPlayingCover } from './NowPlayingCover';

// The title and each artist: plain until hovered, then underlined on their own.
export const pageLink = 'cursor-pointer text-start hover:underline focus-visible:underline aria-busy:cursor-progress';

/**
 * A name in the LCD that opens a page: a plain link when the page is known,
 * else a button that works out where it goes (nowPlayingLinks.ts) and then goes.
 */
interface PageLinkProps {
  href: string | null;
  resolve: () => Promise<string>;
  className: string;
  children: ReactNode;
}

export function PageLink({ href, resolve, className, children }: PageLinkProps) {
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  if (href) {
    return (
      <PrefetchLink to={href} className={className}>
        {children}
      </PrefetchLink>
    );
  }
  return (
    <button
      className={className}
      aria-busy={busy || undefined}
      onClick={() => {
        if (busy) return;
        setBusy(true);
        void resolve()
          .then((to) => navigate(to))
          .finally(() => setBusy(false));
      }}
    >
      {children}
    </button>
  );
}

/**
 * The artists, each its own link, as the phone draws its credit line: the
 * byline as written, with every credited name in it opening that artist's page.
 */
export function Credits({
  videoId,
  line,
  artists,
  className = pageLink,
}: {
  videoId: string;
  line: string;
  artists: Track['artists'];
  className?: string;
}) {
  const segments = creditSegments(line, artistCredits(line, artists));
  return segments.map((segment, i) =>
    segment.credit ? (
      <PageLink
        key={i}
        className={className}
        href={artistHref(segment.credit.id)}
        resolve={() => artistPageFor(videoId, segment.credit!)}
      >
        {segment.text}
      </PageLink>
    ) : (
      segment.text
    ),
  );
}

// A long line fades out at its end rather than being cut with an ellipsis (SongText.kt FadingLine).
const fadingLine = 'min-w-0 overflow-hidden whitespace-nowrap [mask-image:linear-gradient(to_right,#000_calc(100%-24px),transparent)]';

/**
 * The mini player's cover and names (MiniPlayer.kt): 40px artwork with an
 * 8px corner and the 1px border every thumbnail has, 71px wide while a music
 * video plays in it at 16:9; 10px on, the title at 16/600 with the explicit
 * badge ahead of it, and the artist at 14 in the secondary grey. It is one
 * button into the full-screen player, as the phone's whole bar is.
 * `times`, the desktop capsule's elapsed and remaining, end the artist line.
 */
export function Lcd({ times }: { times?: ReactNode }) {
  const nowPlaying = usePlayer((s) => s.nowPlaying);
  const status = usePlayer((s) => s.status);
  const error = usePlayer((s) => s.error);
  const openFullScreen = useUi((s) => s.setFullScreen);
  const explicit = usePlayer((s) => {
    const entry = s.queue[s.index]?.track;
    return entry?.videoId === s.nowPlaying?.videoId && !!entry?.explicit;
  });
  const video = useHasVideo(nowPlaying?.videoId);

  return (
    <button
      className="flex h-10 min-w-0 flex-1 items-center gap-2.5 text-start"
      aria-label={nowPlaying ? `Open full screen player: ${nowPlaying.title}` : 'Open full screen player'}
      disabled={!nowPlaying}
      onClick={() => openFullScreen(true)}
      data-open-player
    >
      <span
        data-player-cover
        className={`relative grid h-10 flex-none place-items-center overflow-hidden rounded-row bg-surface-variant artwork-stroke transition-[width] duration-300 ease-panel ${video ? 'w-[71px] bg-black' : 'w-10'}`}
      >
        {nowPlaying ? (
          <>
            <NowPlayingCover
              url={nowPlaying.artworkUrl}
              px={40}
              reveal
              className="absolute inset-0 size-full object-cover opacity-0 transition-opacity duration-200 ease-in data-loaded:opacity-100"
            />
            <VideoSurface className={`absolute inset-0 transition-opacity duration-300 ${video ? 'opacity-100' : 'opacity-0'}`} />
          </>
        ) : (
          <LogoMark size={22} className="text-tertiary" />
        )}
      </span>
      <span className="flex min-w-0 flex-1 flex-col">
        <span className={`${fadingLine} type-body-emphasized text-primary [&>svg]:ms-0 [&>svg]:me-1`}>
          {explicit && <ExplicitBadge size={12} />}
          {nowPlaying?.title || (status === 'loading' ? 'Loading…' : '')}
        </span>
        {/* The desktop capsule's times sit at the artist line's end, the line giving way to them. */}
        <span className="flex min-w-0 items-baseline">
          <span className={`${fadingLine} flex-1 type-callout ${status === 'error' ? 'text-red' : 'text-on-surface-variant'}`}>
            {status === 'error' ? error : nowPlaying?.artist}
          </span>
          {nowPlaying && times}
        </span>
      </span>
    </button>
  );
}

