import { shelfHeader, shelfKinds, shelfList, shelfSection, type ShelfKind } from '@/components/shelf';
import { SkeletonBlock, SkeletonLine } from '@/components/Skeleton';

export type { ShelfKind };

/** How many placeholders a shelf lays out: enough to run past the widest window. */
const ITEMS: Record<ShelfKind, number> = {
  feature: 6,
  square: 10,
  tracks: 16,
  horizontal: 8,
  video: 7,
  circle: 10,
  'top-picks': 6,
  replay: 4,
};

// Ragged widths, as Skeletons.kt's TitleWidths and SubtitleWidths, so a run reads as text.
const TITLE = ['68%', '46%', '58%', '74%', '52%'];
const SUBTITLE = ['34%', '44%', '27%', '38%', '31%'];
const HEADER = [190, 130, 160, 205, 145];

/** ShelfSkeleton's card: the art, 12px, a 13px title line, 6px, an 11px credit line. */
function Card({ i, aspect, radius }: { i: number; aspect: string; radius: string }) {
  return (
    <div className="flex flex-col">
      <SkeletonBlock aspectRatio={aspect} radius={radius} />
      <SkeletonBlock width={TITLE[i % 5]} height={13} radius={4} style={{ marginTop: 12 }} />
      <SkeletonBlock width={SUBTITLE[i % 5]} height={11} radius={4} style={{ marginTop: 6 }} />
    </div>
  );
}

function Item({ kind, i }: { kind: ShelfKind; i: number }) {
  switch (kind) {
    case 'square':
      return <Card i={i} aspect="1" radius="var(--radius-artwork-medium)" />;
    case 'video':
      return <Card i={i} aspect="16 / 9" radius="var(--radius-artwork-medium)" />;
    case 'replay':
    case 'feature':
      return <SkeletonBlock aspectRatio="240 / 297" radius="var(--radius-settings-group)" />;
    case 'top-picks':
      return <SkeletonBlock aspectRatio="3 / 4" radius="var(--radius-artwork-medium)" />;
    case 'circle':
      return (
        <div className="flex flex-col items-center">
          <SkeletonBlock aspectRatio="1" radius="50%" className="w-full" />
          <SkeletonBlock width={TITLE[i % 5]} height={13} radius={4} style={{ marginTop: 12 }} />
        </div>
      );
    case 'tracks':
      // CompactSongRowSkeleton, at the real row's 56px so nothing moves when it lands.
      return (
        <div className="grid h-14 grid-cols-[48px_1fr] items-center gap-x-3 py-1">
          <SkeletonBlock width={48} height={48} radius="var(--radius-artwork-small)" />
          <div className="flex flex-col gap-1.5">
            <SkeletonBlock width={TITLE[i % 5]} height={14} radius={4} />
            <SkeletonBlock width={SUBTITLE[i % 5]} height={11} radius={4} />
          </div>
        </div>
      );
    case 'horizontal':
      return (
        <div className="grid grid-cols-[97px_1fr] items-center gap-x-5">
          <SkeletonBlock width={97} height={97} radius="var(--radius-artwork-small)" />
          <div className="flex flex-col gap-1.5">
            <SkeletonLine lineHeight={20} fontSize={15} width="75%" />
            <SkeletonLine lineHeight={15} fontSize={12} width="45%" />
          </div>
        </div>
      );
  }
}

/** A shelf in waiting: the real shelf's row, filled with placeholders (Skeletons.kt ShelfSkeleton). */
export function ShelfSkeleton({ kind, title = true, index = 0 }: { kind: ShelfKind; title?: boolean; index?: number }) {
  return (
    <section className={shelfSection} aria-hidden="true">
      {title && (
        // SectionHeaderSkeleton: one 18px line, a ragged title's width as it lands on a phone.
        <div className={shelfHeader}>
          <SkeletonBlock width={HEADER[index % 5]} height={18} radius={4} />
        </div>
      )}
      <div className="relative overflow-hidden">
        <div className={`${shelfList} ${shelfKinds[kind]}`}>
          {Array.from({ length: ITEMS[kind] }, (_, i) => (
            <Item key={i} kind={kind} i={i} />
          ))}
        </div>
      </div>
    </section>
  );
}
