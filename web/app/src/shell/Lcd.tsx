import { Maximize2 } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { LogoMark } from '@/components/icons/LogoMark';
import { ExplicitBadge } from '@/components/music/ExplicitBadge';
import { VideoSurface } from '@/components/music/VideoSurface';
import { albumHref, artistHref } from '@/components/music/links';
import { PrefetchLink } from '@/components/PrefetchLink';
import { artistCredits, creditSegments } from '@/lib/artistCredits';
import { sizedArtwork } from '@/lib/artwork';
import type { Track } from '@/data/music/types';
import { usePlayer } from '@/state/player';
import { useUi } from '@/state/ui';
import { useHasVideo } from '@/state/video';
import { albumPageFor, artistPageFor } from './nowPlayingLinks';
import { MixWindowHighlight } from './NerdStats';
import { useScrubber } from './useScrubber';

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

// As wide as the name, so only the name opens the album.
const titleClass = 'min-w-0 max-w-full self-start truncate type-body-emphasized';

/** The now-playing tile inside the pill (report §1, "LCD"). */
export function Lcd() {
  const nowPlaying = usePlayer((s) => s.nowPlaying);
  const status = usePlayer((s) => s.status);
  const error = usePlayer((s) => s.error);
  const openFullScreen = useUi((s) => s.setFullScreen);
  // The queue's row for what's playing: it carries the album and each artist's channel.
  const track = usePlayer((s) => {
    const entry = s.queue[s.index]?.track;
    return entry && entry.videoId === s.nowPlaying?.videoId ? entry : null;
  });
  const video = useHasVideo(nowPlaying?.videoId);

  if (!nowPlaying) {
    return (
      <div className="grid h-full place-items-center text-tertiary">
        <LogoMark size={26} />
      </div>
    );
  }

  const { videoId, title, artist } = nowPlaying;
  const album = track?.album ?? null;
  return (
    <div
      className={`group/lcd relative grid h-full items-center gap-x-2 pb-1.5 ${video ? 'grid-cols-[60px_minmax(0,1fr)]' : 'grid-cols-[34px_minmax(0,1fr)]'}`}
    >
      {/* As Apple's: the artwork opens the full-screen player, with an expand glyph on hover. */}
      <button
        // A music video plays here in small, at 16:9.
        className={`group/art relative block h-[34px] overflow-hidden rounded-[6px] bg-joe p-0 transition-[filter,opacity] ${video ? 'w-[60px] bg-black' : 'w-[34px]'}`}
        aria-label="Open full screen player"
        onClick={() => openFullScreen(true)}
      >
        {nowPlaying.artworkUrl && (
          <img
            key={nowPlaying.artworkUrl}
            src={sizedArtwork(nowPlaying.artworkUrl, 34)}
            alt=""
            referrerPolicy="no-referrer"
            decoding="async"
            className="size-full object-cover opacity-0 transition-opacity duration-200 ease-in data-loaded:opacity-100"
            onLoad={(e) => e.currentTarget.setAttribute('data-loaded', '')}
          />
        )}
        <VideoSurface
          className={`absolute inset-0 transition-opacity duration-300 ${video ? 'opacity-100' : 'opacity-0'}`}
        />
        {/* Hovering anywhere on the player pill dims the artwork and shows the expand glyph. */}
        <span
          className="absolute inset-0 grid place-items-center bg-[rgb(0_0_0/0.5)] text-white opacity-0 transition-opacity duration-200 group-hover/pill:opacity-100 group-focus-visible/art:opacity-100"
          aria-hidden="true"
        >
          <Maximize2 size={14} strokeWidth={2.25} />
        </span>
      </button>
      {/* Over the seek line, the names soften into the background so the times can sit over them. */}
      <div className="flex min-w-0 flex-col transition-[filter,opacity] group-has-[[data-scrubber]:hover]/lcd:opacity-30 group-has-[[data-scrubber]:hover]/lcd:blur-[3px] group-has-[[data-dragging]]/lcd:opacity-30 group-has-[[data-dragging]]/lcd:blur-[3px]">
        {title ? (
          // The song's name opens its album; an explicit song carries the E after it.
          <div className="flex min-w-0 items-center">
            <PageLink
              className={`${titleClass} ${pageLink}`}
              href={albumHref(album?.id ?? null)}
              resolve={() => albumPageFor(videoId, album, [title, artist].filter(Boolean).join(' '))}
            >
              {title}
            </PageLink>
            {track?.explicit && <ExplicitBadge size={11} />}
          </div>
        ) : (
          <span className={titleClass}>{status === 'loading' ? 'Loading…' : ''}</span>
        )}
        {status === 'error' ? (
          <span className="truncate type-callout-medium text-key">{error}</span>
        ) : (
          <span className="truncate type-callout-medium text-secondary">
            {artist ? <Credits videoId={videoId} line={artist} artists={track?.artists ?? []} /> : status === 'loading' ? 'Loading…' : ''}
          </span>
        )}
      </div>
      <Progress />
    </div>
  );
}

// Elapsed and remaining, over the line's two ends while the pointer is on it.
const time =
  'pointer-events-none absolute bottom-[11px] type-[600_11px/13px] tabular-nums text-primary opacity-0 transition-opacity group-hover/scrub:opacity-100 group-data-dragging/scrub:opacity-100';

/**
 * Elapsed time as a 2px line along the LCD's bottom (report §1). Hovering
 * shows the times; pressing or dragging on the line seeks.
 */
function Progress() {
  const { bar, elapsed, remaining, dragging, handlers } = useScrubber();
  return (
    // The hit area: taller than the 2px line it carries.
    <div
      className="group/scrub absolute inset-x-0 bottom-0 h-3.5 cursor-pointer touch-none"
      data-scrubber
      data-dragging={dragging || undefined}
      {...handlers}
    >
      <span ref={elapsed} className={`${time} left-0`} />
      <span ref={remaining} className={`${time} right-0`} />
      <div
        className="absolute inset-x-0 bottom-1.5 h-0.5 overflow-hidden rounded-[1px] bg-quaternary transition-[height,left,right] duration-100 ease-in group-hover/scrub:bottom-[5px] group-hover/scrub:h-1 group-data-dragging/scrub:bottom-[5px] group-data-dragging/scrub:h-1"
        aria-hidden="true"
      >
        <MixWindowHighlight />
        <div ref={bar} className="relative size-full origin-left scale-x-(--progress) bg-secondary [--progress:0]" />
      </div>
    </div>
  );
}
