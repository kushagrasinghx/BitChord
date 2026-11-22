import { Check, Plus, Shuffle } from 'lucide-react';
import { useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { PrefetchLink } from '@/components/PrefetchLink';
import { PlayIcon } from '@/components/icons/Transport';
import { PrimaryPill } from '@/components/ui/PrimaryPill';
import { useSetListSaved, useSignedIn } from '@/data/music/queries';
import type { AppleMotion } from '@/data/apple/motionArt';
import type { HeaderInfo } from '@/data/music/types';
import { Artwork } from './Artwork';
import { ExplicitBadge } from './ExplicitBadge';
import { MotionArtwork } from './MotionArtwork';
import { artistHref } from './links';

interface DetailHeaderProps {
  info: HeaderInfo;
  onPlay: () => void;
  onShuffle: () => void;
  canPlay: boolean;
  /** The page's own small circles after the action row: filter, Add to Playlist, the ⋯ menu, a Spotify playlist's Import. */
  extra?: ReactNode;
  /** Apple's motion artwork for an album, over the cover once it plays. */
  motion?: AppleMotion | null;
}

/*
 * The phone's light veil (app/.../ui/screens/DetailScreen.kt LIGHT_FILL_ALPHA
 * .07 and TOP_RELEASE_EDGE_ALPHA .10): white on its dark pages. Mixed from the
 * page's primary text colour here, so it is white on a dark or dark-tinted
 * page and black on a light one, where white would vanish.
 */
export const veil =
  'border border-[color-mix(in_srgb,var(--systemPrimary)_10%,transparent)] bg-[color-mix(in_srgb,var(--systemPrimary)_7%,transparent)] text-primary enabled:hover:bg-[color-mix(in_srgb,var(--systemPrimary)_12%,transparent)]';
// Sinks while pressed, as PrimaryPill does; a control this user can't use is dimmed to DISABLED_ACTION_ALPHA (.38), not dropped.
export const pressable = 'transition-[scale,background-color] duration-150 ease-out enabled:active:scale-[.94] disabled:opacity-38';

/**
 * Buttons the header's neighbours share: the page's small circles (the ⋯
 * menu, Add to Playlist, a reorder's Cancel) in the side circles' veil, and a
 * filled pill (a reorder's Save, a Spotify playlist's Import).
 */
export const detailButton = {
  pill: `inline-flex h-9 min-w-[110px] items-center justify-center gap-1.5 rounded-pill bg-key px-4 type-title-3-emphasized text-on-key ${pressable}`,
  circle: `grid size-9 flex-none place-items-center rounded-full ${veil} ${pressable}`,
};

/** The release page's Shuffle and Add to Library either side of Play (DetailScreen.kt CircleIconButton, RELEASE_SIDE_BUTTON 50, its glyph 44% of that). */
export const sideCircle = `grid size-[50px] flex-none place-items-center rounded-full ${veil} ${pressable} [&_svg]:size-[22px]`;

/**
 * An album or playlist header (DetailScreen.kt ReleaseHeader): the framed
 * sleeve, title, credit and metadata, then Shuffle · Play · Library. On a wide
 * window the sleeve sits beside the text, the desktop's room for it; below
 * 768px it stacks and centres as on the phone.
 */
export function DetailHeader({ info, onPlay, onShuffle, canPlay, extra, motion }: DetailHeaderProps) {
  const signedIn = useSignedIn();
  const setSaved = useSetListSaved();
  const library = signedIn ? info.library : null;
  return (
    <div className="mx-(--body-gutter) mb-6 grid grid-cols-[auto_minmax(0,1fr)] items-end gap-x-8 pt-11 max-md:grid-cols-[minmax(0,1fr)] max-md:justify-items-center max-md:pt-4 max-md:text-center">
      {/* SLEEVE_SHAPE r12 and SLEEVE_SHADOW 18; no glow behind it, as the phone has none. 270–360px wide, 76% of a phone's width (SLEEVE_FRACTION). */}
      <div className="relative w-[clamp(270px,26vw,360px)] rounded-card shadow-[0_9px_18px_rgb(0_0_0/0.3)] max-md:w-[min(76vw,360px)]">
        <Artwork src={info.thumbnail} displayWidth={360} radius="var(--radius-artwork-large)" eager />
        {/* Apple's motion artwork, over the cover once it's playing. */}
        {motion && (
          <MotionArtwork
            key={motion.url}
            motion={motion}
            className="absolute inset-0 size-full rounded-card object-cover opacity-0 transition-opacity duration-600 ease-in data-ready:opacity-100"
          />
        )}
      </div>
      <div className="flex min-w-0 flex-col max-md:mt-5 max-md:w-full max-md:items-center">
        {/* headlineMedium 22/700, two lines at most. */}
        <h1 className="line-clamp-2 type-title-1-emphasized text-primary">
          {info.explicit && (
            <span className="me-1.5 inline-flex align-[1px] [&>svg]:ms-0">
              <ExplicitBadge size={16} />
            </span>
          )}
          {info.title}
        </h1>
        {info.artists.length > 0 && (
          // The credit: 19px at regular weight (titleLarge, re-weighted), in the secondary colour.
          <div className="mt-0.5 truncate type-[400_19px/23px] tracking-[-0.2px] text-secondary">
            {info.artists.map((artist, i) => {
              const href = artistHref(artist.id);
              return (
                <span key={`${artist.name}-${i}`}>
                  {i > 0 && ', '}
                  {href ? (
                    <PrefetchLink to={href} className="hover:text-primary hover:underline">
                      {artist.name}
                    </PrefetchLink>
                  ) : (
                    artist.name
                  )}
                </span>
              );
            })}
          </div>
        )}
        <div className="mt-1 truncate type-label-medium text-secondary">{[info.subtitle, info.meta].filter(Boolean).join(' · ')}</div>
        {/* RELEASE_ACTION_GAP 14, the row no wider than RELEASE_ACTIONS_MAX_WIDTH 420; the page's own circles follow it, wrapping under it on a phone. */}
        <div className="mt-[18px] flex flex-wrap items-center gap-3.5 max-md:justify-center">
          <div className="flex max-w-[420px] min-w-[260px] flex-1 items-center gap-3.5 max-md:w-full max-md:flex-none">
            <button className={sideCircle} aria-label="Shuffle" title="Shuffle" onClick={onShuffle} disabled={!canPlay}>
              <Shuffle strokeWidth={2.2} />
            </button>
            {/* White whatever the theme, so Play survives every artwork's colour (ActionPill, filled). */}
            <PrimaryPill tone="white" className="min-w-0 flex-1" icon={<PlayIcon size={18} />} onClick={onPlay} disabled={!canPlay}>
              Play
            </PrimaryPill>
            <button
              className={sideCircle}
              // Shown but greyed out for a guest, rather than leaving a hole beside Play.
              aria-label={library?.saved ? 'Remove from Library' : 'Add to Library'}
              aria-pressed={library ? library.saved : undefined}
              title={!library ? (signedIn ? 'Can’t be added to your library' : 'Sign in to add to your library') : library.saved ? 'In your library' : 'Add to Library'}
              disabled={!library || setSaved.isPending}
              onClick={() => library && setSaved.mutate({ playlistId: library.playlistId, saved: !library.saved })}
            >
              {library?.saved ? <Check strokeWidth={2.4} /> : <Plus strokeWidth={2.4} />}
            </button>
          </div>
          {extra && <div className="flex items-center gap-2.5">{extra}</div>}
        </div>
      </div>
    </div>
  );
}

/**
 * YouTube's editorial note under the header, two lines with More to read the
 * rest (DetailScreen.kt AboutSection): titleMedium heading, bodyLarge at
 * medium weight in the secondary colour. More only appears once the
 * clamped text has actually lost something.
 */
export function AboutSection({ title, text, children }: { title: string; text: string | null; children?: ReactNode }) {
  const [expanded, setExpanded] = useState(false);
  const [clipped, setClipped] = useState(false);
  const paragraph = useRef<HTMLParagraphElement>(null);
  useLayoutEffect(() => {
    const el = paragraph.current;
    if (el && !expanded) setClipped(el.scrollHeight > el.clientHeight + 1);
  }, [text, expanded]);
  if (!text && !children) return null;
  return (
    <section className="mx-(--body-gutter) mb-6 max-w-[720px]" aria-label={title}>
      <h2 className="mb-1.5 type-title-3-emphasized text-primary">{title}</h2>
      {text && (
        <p ref={paragraph} className={`type-[500_16px/22px] whitespace-pre-line text-secondary ${expanded ? '' : 'line-clamp-2'}`}>
          {text}
        </p>
      )}
      {(clipped || expanded) && (
        <button className="py-1 type-callout-medium text-primary hover:underline" aria-expanded={expanded} onClick={() => setExpanded(!expanded)}>
          {expanded ? 'Less' : 'More'}
        </button>
      )}
      {children}
    </section>
  );
}
