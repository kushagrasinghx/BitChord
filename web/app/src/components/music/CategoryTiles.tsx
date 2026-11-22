import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { PrefetchLink } from '@/components/PrefetchLink';
import { firstBrowseArtwork } from '@/data/music/mappers';
import { browseQuery, useYouTubeReady } from '@/data/music/queries';
import { sizedArtwork } from '@/lib/artwork';
import type { Category, CategoryGroup } from '@/data/music/types';
import { browseHref } from './links';
import { moodGenreGrid } from './moodGenreGrid';

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

type Rgb = [number, number, number];
const lerp = (a: Rgb, b: Rgb, t: number): Rgb => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
const css = (c: Rgb) => `rgb(${Math.round(c[0])} ${Math.round(c[1])} ${Math.round(c[2])})`;
const BLACK: Rgb = [0, 0, 0];
const WHITE: Rgb = [255, 255, 255];

/** For a category that came without YouTube's colour: muted, never neon (ExploreScreen.kt fallbackMoodColor). */
const FALLBACK: Rgb[] = [
  [0xcc, 0x6a, 0x55], [0xc0, 0x7a, 0x92], [0x9c, 0x8a, 0xc0], [0x80, 0x90, 0xc8],
  [0xd0, 0xa0, 0x60], [0x6a, 0x88, 0xb0], [0x7a, 0xae, 0xd0], [0x86, 0xb8, 0x90],
];

/**
 * The sleeve's tones (ExploreScreen.kt moodTone): the stripe a touch darker
 * than YouTube's pastel so it doesn't glow on the black page, and the cover
 * redrawn as a duotone from 68%-black shadows to 32%-white highlights.
 */
export function moodTone(color: string, title: string) {
  const parsed = color.match(/\d+/g)?.slice(0, 3).map(Number);
  const hash = [...title].reduce((h, ch) => (Math.imul(31, h) + ch.charCodeAt(0)) | 0, 0);
  const base: Rgb = parsed?.length === 3 ? (parsed as Rgb) : FALLBACK[(hash & 0x7fffffff) % 8]!;
  const shadow = lerp(base, BLACK, 0.68);
  const highlight = lerp(base, WHITE, 0.32);
  // Each pixel's luminance mapped onto the ramp shadow → highlight.
  const row = (from: number, to: number) => {
    const span = (to - from) / 255;
    return `${span * 0.299} ${span * 0.587} ${span * 0.114} 0 ${from / 255}`;
  };
  return {
    stripe: css(lerp(base, BLACK, 0.08)),
    placeholder: css(lerp(shadow, highlight, 0.45)),
    matrix: `${row(shadow[0], highlight[0])} ${row(shadow[1], highlight[1])} ${row(shadow[2], highlight[2])} 0 0 0 1 0`,
    id: `duotone-${base.map(Math.round).join('-')}`,
  };
}

/**
 * A mood or genre as the phone's MoodGenreSleeve: aspect 1.72, radius 18, a
 * solid stripe down the left 28% and the category's cover filling the rest as
 * a duotone of the stripe's colour, a soft shade from the top so pale stripes
 * still carry the white title, top-left, 16 bold. A slight lift on hover.
 */
function CategoryTile({ category }: { category: Category }) {
  const { ready } = useYouTubeReady();
  const { ref, near } = useNearViewport<HTMLLIElement>();
  const [loaded, setLoaded] = useState(false);
  // The same query BrowsePage runs, so borrowing the cover also warms the
  // page the tile opens — the phone caches the shelves for the same reason.
  const browse = useQuery({ ...browseQuery(category.link), enabled: ready && near });
  const borrowed = browse.data ? firstBrowseArtwork(browse.data) : null;
  // The URL as served is a small shelf thumbnail; stretched over a tile it
  // pixelates. Ask the CDN for the rendered size; wide stills keep theirs.
  const artwork = borrowed ? (borrowed.wide ? borrowed.thumbnail : sizedArtwork(borrowed.thumbnail, 320)) : null;
  const tone = moodTone(category.color, category.title);
  return (
    <li ref={ref}>
      <PrefetchLink
        to={browseHref(category.link)}
        className="relative isolate block aspect-[1.72] overflow-hidden rounded-mood text-white transition-[scale] duration-200 ease-panel hover:scale-[1.015] active:scale-[.98]"
        style={{ background: tone.stripe }}
      >
        <span className="absolute inset-y-0 right-0 -z-1 w-[72%]" style={{ background: tone.placeholder }}>
          {artwork && (
            <img
              src={artwork}
              alt=""
              // As with Artwork: googleusercontent throttles by Referer.
              referrerPolicy="no-referrer"
              loading="lazy"
              decoding="async"
              draggable={false}
              onLoad={() => setLoaded(true)}
              className="size-full object-cover transition-opacity duration-300"
              style={{ filter: `url(#${tone.id})`, opacity: loaded ? 1 : 0 }}
            />
          )}
        </span>
        <svg className="absolute size-0" aria-hidden="true">
          <filter id={tone.id} colorInterpolationFilters="sRGB">
            <feColorMatrix type="matrix" values={tone.matrix} />
          </filter>
        </svg>
        <span aria-hidden="true" className="absolute inset-0 -z-1 bg-[linear-gradient(rgb(0_0_0/0.24),transparent_62%)]" />
        <span className="line-clamp-2 px-3.5 py-3 type-[700_16px/19px] [text-shadow:0_1px_6px_rgb(0_0_0/0.35)]">{category.title}</span>
      </PrefetchLink>
    </li>
  );
}

/**
 * Moods and genres as one grid (ExploreScreen.kt): the server's grouping only
 * decides the order, and a category listed twice shows once.
 */
export function MoodGenreGrid({ groups }: { groups: CategoryGroup[] }) {
  const seen = new Set<string>();
  const categories = groups
    .flatMap((g) => g.categories)
    .filter((c) => {
      const key = `${c.link.browseId}|${c.link.params}`;
      return !seen.has(key) && !!seen.add(key);
    });
  return (
    <ul className={moodGenreGrid}>
      {categories.map((category) => (
        <CategoryTile key={`${category.link.browseId}|${category.link.params}`} category={category} />
      ))}
    </ul>
  );
}

/** The same tiles under each group's heading, for Search's Browse Categories. */
export function CategoryTiles({ groups }: { groups: CategoryGroup[] }) {
  return (
    <>
      {groups.map((group) => (
        <section key={group.title} className="pt-3" aria-label={group.title}>
          <h2 className="mx-(--body-gutter) type-title-2-emphasized">{group.title}</h2>
          <ul className={moodGenreGrid}>
            {group.categories.map((category) => (
              <CategoryTile key={`${category.title}-${category.link.params}`} category={category} />
            ))}
          </ul>
        </section>
      ))}
    </>
  );
}
