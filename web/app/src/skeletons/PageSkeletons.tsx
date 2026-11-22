import { PageHeader } from '@/components/PageHeader';
import { LogoMark } from '@/components/icons/LogoMark';
import { moodGenreGrid } from '@/components/music/moodGenreGrid';
import { SkeletonBlock, SkeletonLine } from '@/components/Skeleton';
import { DetailSkeleton } from './DetailSkeleton';
import { ShelfSkeleton } from './ShelfSkeleton';

// Route fallbacks. They live in the main bundle, so a route's skeleton shows
// before its own chunk has loaded. The title is real text: it's known up front.

const sectionTitle = 'mx-(--body-gutter) mt-3';

/** Home and Explore's feed while its first page loads (Skeletons.kt feedSkeleton): three shelves of cards. */
export function FeedSkeleton() {
  return (
    <>
      <ShelfSkeleton kind="square" index={0} />
      <ShelfSkeleton kind="square" index={1} />
      <ShelfSkeleton kind="square" index={2} />
    </>
  );
}

export function HomeSkeleton() {
  return (
    <div aria-busy="true">
      {/* HomeTitle's wordmark, where the real one lands. */}
      <div className="mx-(--body-gutter) pt-8 pb-3.5">
        <LogoMark size={(31 * 730) / 484} className="text-primary" />
      </div>
      <ShelfSkeleton kind="top-picks" />
      <FeedSkeleton />
    </div>
  );
}

/**
 * The Moods & Genres grid while it loads (ExploreScreen.kt ExploreSkeletonRows):
 * six rows of the real tiles' size and corner.
 */
export function MoodGridSkeleton() {
  return (
    <div className={moodGenreGrid} aria-hidden="true">
      {Array.from({ length: 24 }, (_, i) => (
        <SkeletonBlock key={i} aspectRatio="1.72" radius="var(--radius-mood-tile)" />
      ))}
    </div>
  );
}

export function ExploreSkeleton() {
  return (
    <div aria-busy="true">
      <PageHeader title="Explore" />
      <MoodGridSkeleton />
    </div>
  );
}
export function SearchSkeleton() {
  return (
    <div aria-busy="true">
      {/* The Search page's field (SearchField.tsx): 46px, r11, beside the top bar's account pill. */}
      <div className="px-(--body-gutter) pt-[3px] pb-1.5" aria-hidden="true">
        <div className="me-(--top-bar-trailing-width,0px)">
          <SkeletonBlock height={46} radius="var(--radius-search)" />
        </div>
      </div>
    </div>
  );
}

export function AlbumSkeleton() {
  return <DetailSkeleton />;
}

export function PlaylistSkeleton() {
  return <DetailSkeleton rowArtwork rows={10} />;
}

export function ArtistSkeleton() {
  return (
    <div aria-busy="true">
      {/* The photo is a shade taller than wide (ARTIST_PHOTO_RATIO .95), at most 60% of the window; the action row hangs 20px into its foot. */}
      <SkeletonBlock className="aspect-[0.95] max-h-[60vh] w-full" radius={0} />
      <div className="relative -mt-5 mb-[22px] flex items-center justify-center gap-6" aria-hidden="true">
        <SkeletonBlock width={52} height={52} radius="50%" />
        <SkeletonBlock width={70} height={70} radius="50%" />
        <SkeletonBlock width={52} height={52} radius="50%" />
      </div>
      <ShelfSkeleton kind="tracks" />
      <ShelfSkeleton kind="square" />
      <ShelfSkeleton kind="square" />
      <ShelfSkeleton kind="circle" />
    </div>
  );
}

export function PlainSkeleton() {
  return (
    <div aria-busy="true" className={sectionTitle}>
      <SkeletonLine lineHeight={40} fontSize={34} width={220} style={{ marginTop: 32 }} />
    </div>
  );
}
