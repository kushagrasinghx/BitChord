import { PageHeader } from '@/components/PageHeader';
import { SkeletonBlock, SkeletonLine } from '@/components/Skeleton';
import { DetailSkeleton } from './DetailSkeleton';
import { ShelfSkeleton } from './ShelfSkeleton';

// Route fallbacks. They live in the main bundle, so a route's skeleton shows
// before its own chunk has loaded. The title is real text: it's known up front.

const sectionTitle = 'mx-(--body-gutter) mt-3';

export function HomeSkeleton() {
  return (
    <div aria-busy="true">
      <PageHeader title="Home" />
      <ShelfSkeleton kind="editorial" />
      <ShelfSkeleton kind="tracks" />
      <ShelfSkeleton kind="square" />
      <ShelfSkeleton kind="square" />
    </div>
  );
}

export function NewSkeleton() {
  return (
    <div aria-busy="true">
      <PageHeader title="New" />
      <ShelfSkeleton kind="editorial" title={false} />
      <ShelfSkeleton kind="tracks" />
      <ShelfSkeleton kind="square" />
      <ShelfSkeleton kind="square" />
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
      {/* The artist hero is ~1.93:1 of the content column (604px tall at 1165px). */}
      <SkeletonBlock className="aspect-[1165/604] max-h-[70vh] w-full" radius={0} />
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
