import { SkeletonBlock, SkeletonLine } from '@/components/Skeleton';

const row = 'grid items-center border-t-[0.5px] border-t-divider last:border-b-[0.5px] last:border-b-divider';

/** Album and playlist pages: artwork header, then the track list. */
export function DetailSkeleton({ rows = 12, rowArtwork = false }: { rows?: number; rowArtwork?: boolean }) {
  return (
    <div aria-busy="true" aria-label="Loading">
      <div
        className="mx-(--body-gutter) mb-10 grid grid-cols-[auto_1fr] items-end gap-x-[34px] pt-[52px] max-sm:grid-cols-[1fr] max-sm:justify-items-center max-sm:gap-y-4 max-sm:pt-4"
        aria-hidden="true"
      >
        <SkeletonBlock className="w-[270px] max-lg:w-[220px] max-sm:w-[min(270px,70vw)]" aspectRatio="1" radius="var(--radius-artwork-large)" />
        <div className="flex flex-col pb-1 max-sm:items-center">
          <SkeletonLine lineHeight={32} fontSize={26} width={260} />
          <SkeletonLine lineHeight={32} fontSize={26} width={180} />
          <SkeletonLine lineHeight={15} fontSize={12} width={90} style={{ marginTop: 4 }} />
          <SkeletonLine lineHeight={18} fontSize={13} width="min(440px, 100%)" style={{ marginTop: 38 }} />
          <SkeletonLine lineHeight={18} fontSize={13} width="min(380px, 90%)" />
          <div className="mt-3.5 flex gap-3">
            <SkeletonBlock width={100} height={36} radius={24} />
            <SkeletonBlock width={100} height={36} radius={24} />
          </div>
        </div>
      </div>
      <div className="mx-(--body-gutter)" aria-hidden="true">
        {Array.from({ length: rows }, (_, i) => (
          <div key={i} className={`${row} ${rowArtwork ? 'h-14 grid-cols-[52px_1fr_auto]' : 'h-[46px] grid-cols-[40px_1fr_auto]'}`}>
            {rowArtwork ? (
              <SkeletonBlock width={40} height={40} radius="var(--radius-artwork-small)" />
            ) : (
              <SkeletonLine lineHeight={16} fontSize={13} width={12} />
            )}
            <SkeletonLine lineHeight={16} fontSize={13} width={`${30 + ((i * 37) % 35)}%`} />
            <SkeletonLine lineHeight={16} fontSize={13} width={30} style={{ marginRight: 35 }} />
          </div>
        ))}
      </div>
    </div>
  );
}
