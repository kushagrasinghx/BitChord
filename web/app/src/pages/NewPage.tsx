import { PageHeader } from '@/components/PageHeader';
import { Shelves } from '@/components/music/ShelfView';
import { LoadError, NeedsExtension } from '@/components/music/States';
import { useExplore, useYouTubeReady } from '@/data/music/queries';
import { NewSkeleton } from '@/skeletons/PageSkeletons';

/** New: YouTube Music's Explore (new albums and singles, trending, new videos). */
export default function NewPage() {
  const { missing } = useYouTubeReady();
  const explore = useExplore();

  if (missing) {
    return (
      <>
        <PageHeader title="New" />
        <NeedsExtension />
      </>
    );
  }
  if (explore.isError) {
    return (
      <>
        <PageHeader title="New" />
        <LoadError error={explore.error} onRetry={() => void explore.refetch()} />
      </>
    );
  }
  if (!explore.data) return <NewSkeleton />;
  return (
    <>
      <PageHeader title="New" />
      <Shelves shelves={explore.data} />
    </>
  );
}
