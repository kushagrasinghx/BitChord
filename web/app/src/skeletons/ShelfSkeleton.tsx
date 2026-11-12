import { shelfHeader, shelfKinds, shelfList, shelfSection, type ShelfKind } from '@/components/shelf';
import { SkeletonBlock, SkeletonLine } from '@/components/Skeleton';

export type { ShelfKind };

/** How many placeholders fill a shelf at the widest breakpoint, per row. */
const ITEMS: Record<ShelfKind, number> = {
  editorial: 3,
  square: 6,
  tracks: 16,
  horizontal: 8,
  video: 5,
  circle: 10,
};

const caption = 'mt-2 flex flex-col gap-px';
const trackText = 'flex flex-col gap-px px-1';

function Item({ kind }: { kind: ShelfKind }) {
  switch (kind) {
    case 'square':
      return (
        <div className="flex flex-col">
          <SkeletonBlock aspectRatio="1" radius="var(--radius-artwork-medium)" />
          <div className={caption}>
            <SkeletonLine lineHeight={15} fontSize={12} width="80%" />
            <SkeletonLine lineHeight={15} fontSize={12} width="55%" />
          </div>
        </div>
      );
    case 'video':
      return (
        <div className="flex flex-col">
          <SkeletonBlock aspectRatio="16 / 9" radius="var(--radius-artwork-medium)" />
          <div className={caption}>
            <SkeletonLine lineHeight={15} fontSize={12} width="75%" />
            <SkeletonLine lineHeight={15} fontSize={12} width="45%" />
          </div>
        </div>
      );
    case 'circle':
      return (
        <div className="flex flex-col">
          <SkeletonBlock aspectRatio="1" radius="50%" />
          <div className="mt-2.5 flex flex-col items-center gap-px">
            <SkeletonLine lineHeight={16} fontSize={13} width="65%" />
          </div>
        </div>
      );
    case 'tracks':
      return (
        <div className="grid h-[55px] grid-cols-[40px_1fr] items-center gap-x-3 py-[7.5px] pr-3.5 pl-0">
          <SkeletonBlock width={40} height={40} radius="var(--radius-artwork-small)" />
          <div className={trackText}>
            <SkeletonLine lineHeight={16} fontSize={13} width="70%" />
            <SkeletonLine lineHeight={15} fontSize={12} width="40%" />
          </div>
        </div>
      );
    case 'horizontal':
      return (
        <div className="grid grid-cols-[97px_1fr] items-center gap-x-5">
          <SkeletonBlock width={97} height={97} radius="var(--radius-artwork-small)" />
          <div className={trackText}>
            <SkeletonLine lineHeight={20} fontSize={15} width="75%" />
            <SkeletonLine lineHeight={15} fontSize={12} width="45%" />
          </div>
        </div>
      );
    case 'editorial':
      return (
        <div className="flex flex-col">
          <div className="mt-px mb-[15px] flex flex-col">
            <SkeletonLine lineHeight={14} fontSize={11} width="30%" style={{ marginBottom: 2 }} />
            <SkeletonLine lineHeight={20} fontSize={15} width="45%" />
            <SkeletonLine lineHeight={20} fontSize={15} width="35%" />
          </div>
          <SkeletonBlock aspectRatio="16 / 9" radius="var(--radius-artwork-large)" />
        </div>
      );
  }
}

/** A shelf in waiting: the real shelf's grid, filled with placeholders. */
export function ShelfSkeleton({ kind, title = true }: { kind: ShelfKind; title?: boolean }) {
  return (
    <section className={shelfSection} aria-hidden="true">
      {title && (
        <div className={shelfHeader}>
          <SkeletonLine lineHeight={22} fontSize={17} width={180} />
        </div>
      )}
      <div className="relative">
        <div className={`${shelfList} ${shelfKinds[kind]}`}>
          {Array.from({ length: ITEMS[kind] }, (_, i) => (
            <Item key={i} kind={kind} />
          ))}
        </div>
      </div>
    </section>
  );
}
