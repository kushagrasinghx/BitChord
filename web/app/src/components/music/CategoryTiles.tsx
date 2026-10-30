import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { PrefetchLink } from '@/components/PrefetchLink';
import { firstBrowseArtwork } from '@/data/music/mappers';
import { browseQuery, useYouTubeReady } from '@/data/music/queries';
import { sizedArtwork } from '@/lib/artwork';
import type { Category, CategoryGroup } from '@/data/music/types';
import { browseHref } from './links';

/**
 * True once the element is near the viewport. Tile artwork loads the way
 * the phone's does — in the background, as tiles arrive — rather than in
 * one burst of browse requests for every category on the page.
 */
function useNearViewport<T extends HTMLElement>(rootMargin = '600px') {
  const ref = useRef<T | null>(null);
  const [near, setNear] = useState(false);
  useEffect(() => {
    const el = ref.current;
    if (!el || near) return;
    if (!('IntersectionObserver' in window)) {
      setNear(true);
      return;
    }
    const io = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          setNear(true);
          io.disconnect();
        }
      },
      { rootMargin },
    );
    io.observe(el);
    return () => io.disconnect();
  }, [near, rootMargin]);
  return { ref, near };
}

const tile =
  'relative flex aspect-video items-end overflow-hidden rounded-artwork-lg bg-[radial-gradient(120%_90%_at_100%_0%,color-mix(in_srgb,var(--tile-color)_70%,#fff)_0%,transparent_70%),linear-gradient(160deg,var(--tile-color),color-mix(in_srgb,var(--tile-color)_55%,#000))] px-3.5 py-3 text-white transition-[scale] duration-200 ease-panel hover:scale-[1.015]';

function CategoryTile({ category }: { category: Category }) {
  const { ready } = useYouTubeReady();
  const { ref, near } = useNearViewport<HTMLLIElement>();
  const [loaded, setLoaded] = useState(false);
  // The same query BrowsePage runs, so borrowing the cover also warms the
  // page the tile opens — the phone caches the shelves for the same reason.
  const browse = useQuery({ ...browseQuery(category.link), enabled: ready && near });
  const borrowed = browse.data ? firstBrowseArtwork(browse.data) : null;
  // The URL as served is a small shelf thumbnail; stretched over a tile it
  // pixelates. Ask the CDN for the rendered size, as Artwork does
  // everywhere else (prompt §3). Wide stills keep their fixed size.
  const artwork = borrowed ? (borrowed.wide ? borrowed.thumbnail : sizedArtwork(borrowed.thumbnail, 480)) : null;
  const fade = {
    opacity: loaded ? 1 : 0,
    transition: 'opacity 300ms ease',
  } as React.CSSProperties;
  return (
    <li ref={ref}>
      <PrefetchLink to={browseHref(category.link)} className={tile} style={{ '--tile-color': category.color } as React.CSSProperties}>
        {artwork && (
          <>
            <img
              src={artwork}
              alt=""
              // As with Artwork: googleusercontent throttles by Referer.
              referrerPolicy="no-referrer"
              loading="lazy"
              decoding="async"
              draggable={false}
              onLoad={() => setLoaded(true)}
              className="absolute inset-0 size-full object-cover grayscale contrast-[1.05]"
              // Square covers in a 16:9 tile lose 44% of their height; the
              // crop window leans up (shows 11%–67%) so faces survive and
              // the bottom — where the tile's own label sits — is what goes.
              style={{ ...fade, objectPosition: '50% 25%' }}
            />
            {/*
              The phone's duotone, as blends: the greyscale cover multiplied
              by the stripe colour's highlight, screened by its shadow, so
              the photo reads as one tinted sleeve, not a pasted picture.
            */}
            <span
              aria-hidden="true"
              className="absolute inset-0 mix-blend-multiply"
              style={{ ...fade, background: 'color-mix(in srgb, var(--tile-color) 68%, #fff)' }}
            />
            <span
              aria-hidden="true"
              className="absolute inset-0 mix-blend-screen"
              style={{ ...fade, background: 'color-mix(in srgb, var(--tile-color) 32%, #000)' }}
            />
            <span
              aria-hidden="true"
              className="absolute inset-x-0 bottom-0 h-3/5 bg-[linear-gradient(to_top,rgba(0,0,0,0.45),transparent)]"
              style={fade}
            />
          </>
        )}
        {/* The solid stripe down the left, as on the phone. */}
        <span aria-hidden="true" className="absolute inset-y-0 left-0 w-2.5" style={{ background: 'var(--tile-color)' }} />
        <span className="relative type-title-2-emphasized [text-shadow:0_1px_8px_#0000004d]">{category.title}</span>
      </PrefetchLink>
    </li>
  );
}

/**
 * Browse Categories bricks (report §6): 16:9 tiles, radius 10, the title at
 * the bottom left. Coloured from YouTube's own stripe colour per mood/genre,
 * with the category's first real cover borrowed as a duotone sleeve — the
 * moods page itself sends no artwork (YtMusicRepository.moodGenreArtwork).
 */
export function CategoryTiles({ groups }: { groups: CategoryGroup[] }) {
  return (
    <>
      {groups.map((group) => (
        <section key={group.title} className="pt-3" aria-label={group.title}>
          <h2 className="mx-(--body-gutter) type-title-2-emphasized">{group.title}</h2>
          {/* 4 columns at ≥1000px, gap 20px (report §6); 3, then 2, below. */}
          <ul className="mx-(--body-gutter) mt-3 mb-8 grid grid-cols-4 gap-5 max-lg:grid-cols-3 max-[607px]:grid-cols-2 max-[607px]:gap-2.5">
            {group.categories.map((category) => (
              <CategoryTile key={`${category.title}-${category.link.params}`} category={category} />
            ))}
          </ul>
        </section>
      ))}
    </>
  );
}
