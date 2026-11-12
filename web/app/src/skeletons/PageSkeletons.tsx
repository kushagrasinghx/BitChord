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
      <div className="flex justify-center px-(--body-gutter) py-3" aria-hidden="true">
        <SkeletonBlock width="min(492px, 100%)" height={38} radius="var(--radius-pill)" />
      </div>
      <div className={sectionTitle} aria-hidden="true">
        <SkeletonLine lineHeight={22} fontSize={17} width={160} />
      </div>
      <div
        className="mx-(--body-gutter) mt-3 mb-8 grid grid-cols-[repeat(4,1fr)] gap-5 max-lg:grid-cols-[repeat(3,1fr)] max-[607px]:grid-cols-[repeat(2,1fr)]"
        aria-hidden="true"
      >
        {Array.from({ length: 16 }, (_, i) => (
          <SkeletonBlock key={i} aspectRatio="16 / 9" radius="var(--radius-artwork-large)" />
        ))}
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
