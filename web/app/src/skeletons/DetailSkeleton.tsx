import { SkeletonBlock, SkeletonLine } from '@/components/Skeleton';

// Ragged widths, so a run of rows reads as text rather than as a barcode (Skeletons.kt TitleWidths, SubtitleWidths).
const TITLE_WIDTHS = ['68%', '46%', '58%', '74%', '52%'];
const SUBTITLE_WIDTHS = ['34%', '44%', '27%', '38%', '31%'];

/**
 * Album and playlist pages in waiting, to the real header's and rows'
 * metrics so nothing jumps when the data lands (Skeletons.kt): the sleeve,
 * title, credit and metadata, the Shuffle · Play · Library row, then rows,
 * numbered for an album (60px) or with 48px artwork for a playlist (56px).
 * Row artwork is r6, ShimmerBox's BlockShape.
 */
export function DetailSkeleton({ rows = 12, rowArtwork = false }: { rows?: number; rowArtwork?: boolean }) {
  return (
    <div aria-busy="true" aria-label="Loading">
      <div
        className="mx-(--body-gutter) mb-6 grid grid-cols-[auto_minmax(0,1fr)] items-end gap-x-8 pt-11 max-md:grid-cols-[minmax(0,1fr)] max-md:justify-items-center max-md:pt-4"
        aria-hidden="true"
      >
        <SkeletonBlock className="w-[clamp(270px,26vw,360px)] max-md:w-[min(76vw,360px)]" aspectRatio="1" radius="var(--radius-artwork-large)" />
        <div className="flex w-full flex-col max-md:mt-5 max-md:items-center">
          <SkeletonLine lineHeight={26} fontSize={22} width={240} />
          <SkeletonLine lineHeight={23} fontSize={19} width={160} style={{ marginTop: 2 }} />
          <SkeletonLine lineHeight={14} fontSize={12} width={120} style={{ marginTop: 4 }} />
          <div className="mt-[18px] flex w-full max-w-[420px] items-center gap-3.5">
            <SkeletonBlock width={50} height={50} radius="50%" />
            <SkeletonBlock className="flex-1" height={50} radius="var(--radius-pill)" />
            <SkeletonBlock width={50} height={50} radius="50%" />
          </div>
        </div>
      </div>
      <div className="mx-(--body-gutter)" aria-hidden="true">
        {Array.from({ length: rows }, (_, i) => (
          <div key={i} className={`flex items-center ${rowArtwork ? 'h-14 gap-3' : 'h-[60px] gap-4'}`}>
            {rowArtwork ? (
              <SkeletonBlock width={48} height={48} radius={6} />
            ) : (
              <div className="grid w-6 place-items-center">
                <SkeletonLine lineHeight={19} fontSize={16} width={12} />
              </div>
            )}
            <div className="flex min-w-0 flex-1 flex-col gap-0.5">
              <SkeletonLine lineHeight={19} fontSize={16} width={TITLE_WIDTHS[i % TITLE_WIDTHS.length]!} />
              <SkeletonLine lineHeight={17} fontSize={14} width={SUBTITLE_WIDTHS[i % SUBTITLE_WIDTHS.length]!} />
            </div>
            <SkeletonLine lineHeight={14} fontSize={12} width={30} style={{ marginRight: 36 }} />
          </div>
        ))}
      </div>
    </div>
  );
}
