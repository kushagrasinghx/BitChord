import { PageHeader } from '@/components/PageHeader';
import { MoodGenreGrid } from '@/components/music/CategoryTiles';
import { Shelves } from '@/components/music/ShelfView';
import { LoadError, NeedsExtension } from '@/components/music/States';
import { useCategories, useExplore, useYouTubeReady } from '@/data/music/queries';
import { FeedSkeleton, MoodGridSkeleton } from '@/skeletons/PageSkeletons';

/**
 * Explore (sharedUi/.../screens/ExploreScreen.kt): the tab is YouTube Music's
 * Moods & Genres, as one grid of tinted sleeves. Below it, the web keeps
 * YouTube's own Explore shelves (new albums and singles, trending, new videos).
 */
export default function ExplorePage() {
  const { missing } = useYouTubeReady();
  const categories = useCategories();
  const explore = useExplore();

  if (missing) {
    return (
      <>
        <PageHeader title="Explore" />
        <NeedsExtension />
      </>
    );
  }
  return (
    <>
      <PageHeader title="Explore" />
      {categories.isError ? (
        <LoadError error={categories.error} onRetry={() => void categories.refetch()} />
      ) : categories.data ? (
        <MoodGenreGrid groups={categories.data} />
      ) : (
        <MoodGridSkeleton />
      )}
      {explore.data ? <Shelves shelves={explore.data} /> : !explore.isError && categories.data && <FeedSkeleton />}
    </>
  );
}
