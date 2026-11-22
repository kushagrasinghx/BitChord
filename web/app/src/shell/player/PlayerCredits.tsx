import { useState } from 'react';
import { ExplicitBadge } from '@/components/music/ExplicitBadge';
import { FavoriteButton } from '@/components/music/FavoriteButton';
import { albumHref } from '@/components/music/links';
import { TrackMenu } from '@/components/music/TrackMenu';
import type { Track } from '@/data/music/types';
import type { NowPlaying } from '@/state/player';
import { Credits, PageLink, pageLink } from '../Lcd';
import { albumPageFor } from '../nowPlayingLinks';
import { Marquee } from './Marquee';

/** MARQUEE_ARTIST_STAGGER_MS: a title that also crawls goes first; together they read as clutter. */
const ARTIST_STAGGER_MS = 3000;

/*
 * The star and ⋯ (PlayerControls.kt CircleGlyph in GlassDiscPair): 34px
 * discs 8px apart, white at 18% over a blurred slab, 34% while the star is
 * on, a rim brighter along the top, 19px white glyphs. As components/ui/
 * GlassDisc's `disc`, written out here because FavoriteButton and TrackMenu
 * draw their own buttons.
 */
const disc =
  "relative grid size-[34px] flex-none place-items-center rounded-full bg-[#1c1c1e61] bg-[linear-gradient(#ffffff2e,#ffffff2e)] text-white backdrop-blur-[20px] transition-[background-image] duration-150 aria-pressed:bg-[linear-gradient(#ffffff57,#ffffff57)] [&_svg]:size-[19px] after:pointer-events-none after:absolute after:inset-0 after:rounded-full after:bg-[linear-gradient(#ffffff6b,#ffffff24)] after:p-px after:content-[''] after:[mask:linear-gradient(#000_0_0)_content-box_exclude,linear-gradient(#000_0_0)]";

/**
 * Title, artist, like and ⋯ (LandscapePlayer.kt LandscapeCredits). The title
 * in titleLarge (20/700, −0.2) opens its album, with the explicit badge before
 * it in the artist line's colour; the artist line at 16/500, white at 55%,
 * each credited name opening its artist. Both crawl when they don't fit.
 */
export function PlayerCredits({ nowPlaying, track }: { nowPlaying: NowPlaying; track: Track | null }) {
  const [titleCrawls, setTitleCrawls] = useState(false);
  const { videoId, title, artist } = nowPlaying;
  const album = track?.album ?? null;
  const toAlbum = () => albumPageFor(videoId, album, [title, artist].filter(Boolean).join(' '));
  return (
    <div className="flex items-center gap-2.5">
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <Marquee
          key={`t-${videoId}`}
          className="type-title-large text-white"
          leading={track?.explicit ? <ExplicitBadge size={14} /> : undefined}
          onCrawl={setTitleCrawls}
        >
          <PageLink className={pageLink} href={albumHref(album?.id ?? null)} resolve={toAlbum}>
            {title}
          </PageLink>
        </Marquee>
        {artist && (
          <Marquee key={`a-${videoId}`} className="type-[500_16px/19px] tracking-[0.05px] text-white/55" delayMs={titleCrawls ? ARTIST_STAGGER_MS : 0}>
            <Credits videoId={videoId} line={artist} artists={track?.artists ?? []} />
          </Marquee>
        )}
      </div>
      <div className="flex flex-none items-center gap-2">
        <FavoriteButton videoId={videoId} className={disc} size={19} />
        {track && <TrackMenu track={track} className={disc} placement="above-end" player />}
      </div>
    </div>
  );
}
