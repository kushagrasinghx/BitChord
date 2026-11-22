import { useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';
import { PageHeader } from '@/components/PageHeader';
import { PrefetchLink } from '@/components/PrefetchLink';
import { LogoMark } from '@/components/icons/LogoMark';
import { Artwork } from '@/components/music/Artwork';
import { hrefFor } from '@/components/music/links';
import { ShelfScroller, ShelfTitle, ShelfView } from '@/components/music/ShelfView';
import { shelfSection } from '@/components/shelf';
import { SignInBanner } from '@/components/music/SignInBanner';
import { MoodGrid } from '@/components/music/TopPicksShelf';
import { EmptyState, LoadError, NeedsExtension } from '@/components/music/States';
import { fetchMixes, groupedMixes } from '@/data/music/library';
import { useSignedIn, useYouTubeReady } from '@/data/music/queries';
import type { MusicItem } from '@/data/music/types';
import { useYtAuth } from '@/extension/useYtAuth';
import { useArtworkColor } from '@/lib/artworkColor';
import { FeedSkeleton } from '@/skeletons/PageSkeletons';

const TITLE = 'Radio';

/**
 * One named mix as a station (MixesScreen StationTile): its artwork blurred to
 * colour alone, the BitChord mark in the room above its name. The artwork is
 * asked for tiny, so stretching it already loses every edge; the blur smooths
 * the pixels into a wash, and is scaled past the tile so its soft rim is clipped.
 */
function StationTile({ item }: { item: MusicItem }) {
  return (
    <PrefetchLink
      to={hrefFor(item) ?? '/radio'}
      className="relative isolate flex aspect-square flex-col overflow-hidden rounded-[12px] bg-joe text-white artwork-stroke"
      aria-label={item.title}
    >
      <Artwork src={item.thumbnail} displayWidth={48} radius={0} className="absolute! inset-0 -z-1 scale-150 blur-[24px]" />
      {/* A light veil, so a pale wash still carries the white mark and name. */}
      <span className="absolute inset-0 -z-1 bg-[rgb(0_0_0/0.18)]" aria-hidden="true" />
      <span className="grid flex-1 place-items-center">
        {/* The mark at 42% of the tile's width, at its own 730:484 shape. */}
        <LogoMark className="aspect-[730/484] h-auto! w-[42%]!" />
      </span>
      {/* labelMedium, semibold, 8px in and 9px off the foot. */}
      <span className="truncate px-2 pb-[9px] text-center type-label-medium">{item.title}</span>
    </PrefetchLink>
  );
}

/**
 * A mix of the listener's own, a size up from a shelf card, with its caption
 * on a band tinted from its own artwork (MixesScreen FeatureCard, Apple
 * Music's On Air Now): one fixed 240px wide, radius 14, title 16 over a two-line 14 credit.
 */
function FeatureCard({ item }: { item: MusicItem }) {
  const color = useArtworkColor(item.thumbnail);
  return (
    <PrefetchLink
      to={hrefFor(item) ?? '/radio'}
      className="relative flex flex-col overflow-hidden rounded-group bg-joe artwork-stroke data-dark:text-white data-light:text-black"
      style={color ? { backgroundColor: color.background } : undefined}
      data-dark={color?.dark || undefined}
      data-light={(color && !color.dark) || undefined}
    >
      <Artwork src={item.thumbnail} displayWidth={240} radius={0} />
      <span className="flex min-w-0 flex-col px-3 py-2.5">
        <span className="truncate type-title-2-emphasized">{item.title}</span>
        {/* Two lines held even when the credit is short, so the cards stand the same height. */}
        <span className="line-clamp-2 min-h-[2lh] type-callout opacity-70">{item.subtitle}</span>
      </span>
    </PrefetchLink>
  );
}

/**
 * Radio (Android's Radio tab, MixesScreen): YouTube Music's "Mixed for you"
 * cards, sorted by name into the named mixes as station tiles, the listener's
 * own as feature cards, and a shelf per mood, rather than one undivided grid.
 */
export default function RadioPage() {
  const { missing } = useYouTubeReady();
  const auth = useYtAuth();
  const signedIn = useSignedIn();
  // Under the library's key, so whatever refreshes the library refreshes this too.
  const mixes = useQuery({ queryKey: ['music', 'library', 'radio'], queryFn: fetchMixes, enabled: signedIn, staleTime: 2 * 60_000 });
  const sections = useMemo(() => mixes.data && groupedMixes(mixes.data), [mixes.data]);

  if (missing) {
    return (
      <>
        <PageHeader title={TITLE} />
        <NeedsExtension />
      </>
    );
  }
  // Every mix here is made from an account's listening. Signed out, the way in comes first, then the stations
  // that need no account: each mood plays from YouTube Music's public mood playlists (topPicks.ts moodQueue).
  if (auth.state === 'ready' && !signedIn) {
    return (
      <>
        <PageHeader title={TITLE} />
        <SignInBanner />
        <section className="mt-4" aria-label="Mood stations">
          <ShelfTitle title="Mood stations" strapline="Play one now, no account needed." />
          <MoodGrid />
        </section>
      </>
    );
  }
  if (mixes.isError) {
    return (
      <>
        <PageHeader title={TITLE} />
        <LoadError error={mixes.error} onRetry={() => void mixes.refetch()} />
      </>
    );
  }
  if (!sections) {
    return (
      <div aria-busy="true">
        <PageHeader title={TITLE} />
        <FeedSkeleton />
      </div>
    );
  }

  const empty = !sections.stations.length && !sections.yours.length && !sections.moods.length;
  return (
    <>
      <PageHeader title={TITLE} />
      {empty && <EmptyState title="No mixes yet. Keep listening and YouTube Music will make some for you." />}
      {sections.stations.length > 0 && (
        // Three across, six once the row is 600px wide (MixesScreen.kt WIDE_GRID_WIDTH), 12px apart.
        <div className="@container mx-(--body-gutter) mb-[26px]">
          <ul className="grid grid-cols-3 gap-3 @min-[600px]:grid-cols-6" aria-label="Stations">
            {sections.stations.map((item) => (
              <li key={item.id}>
                <StationTile item={item} />
              </li>
            ))}
          </ul>
        </div>
      )}
      {sections.yours.length > 0 && (
        <section className={shelfSection} aria-label="Your Mixes">
          <ShelfTitle title="Your Mixes" />
          <ShelfScroller kind="feature">
            {sections.yours.map((item) => (
              <li key={item.id}>
                <FeatureCard item={item} />
              </li>
            ))}
          </ShelfScroller>
        </section>
      )}
      {sections.moods.map((mood) => (
        <ShelfView key={mood.key} shelf={{ title: mood.name, strapline: null, layout: 'square', items: mood.items, seeAll: null }} />
      ))}
    </>
  );
}
