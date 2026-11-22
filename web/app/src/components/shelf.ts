/*
 * Horizontal shelves, as the phone lays them out (sharedUi/.../screens/HomeScreen.kt
 * Shelf): fixed-width cards 14px apart that snap as the row scrolls, so a wide
 * window shows more of them rather than the same few blown up. Each kind sets
 * its card width (--w) and how many rows it stacks.
 */

/** What a shelf holds. */
export type ShelfKind = 'feature' | 'square' | 'tracks' | 'horizontal' | 'video' | 'circle' | 'top-picks' | 'replay';

/** 26px under every shelf (Shelf's `padding(bottom = 26.dp)`). */
export const shelfSection = 'pb-[26px]';

/** SectionHeader: 10px above and below the title and its strapline. */
export const shelfHeader = 'mx-(--body-gutter) flex min-h-[22px] items-center gap-3 py-2.5';

/** The scrolling row; a kind from `shelfKinds` fills in its rows and card width. */
export const shelfList = [
  'grid grid-flow-col grid-rows-[repeat(var(--rows),max-content)] auto-cols-(--w) gap-x-(--gap) gap-y-0',
  // 15px bleed so hover shadows aren't clipped (--standard-lockup-shadow-offset).
  '-my-[15px] px-(--body-gutter) py-[15px] scroll-px-(--body-gutter)',
  'overflow-x-auto overflow-y-hidden overscroll-x-none snap-x snap-mandatory scroll-smooth motion-reduce:scroll-auto',
  '[scrollbar-width:none] [&::-webkit-scrollbar]:hidden *:min-w-0 *:snap-start',
].join(' ');

export const shelfKinds: Record<ShelfKind, string> = {
  // MixesScreen FeatureCard: one fixed 240px on every screen.
  feature: '[--rows:1] [--w:240px] [--gap:14px]',
  // ShelfCard: SHELF_CARD_WIDTH is 150dp on a phone; a little more at desktop reading distance.
  square: '[--rows:1] [--w:160px] [--gap:14px]',
  // Recents' pager: four rows a column, min(88% of the row, 400px), columns 12px apart.
  tracks: '[--rows:4] [--w:min(88%,400px)] [--gap:12px]',
  horizontal: '[--rows:2] [--w:298px] [--gap:14px]',
  // VIDEO_CARD_WIDTH: 248dp, about as tall as a square card at 16:9.
  video: '[--rows:1] [--w:248px] [--gap:14px]',
  circle: '[--rows:1] [--w:150px] [--gap:14px]',
  // TopPicksShelf: min(60% of the row, 280px), so the next card shows past it.
  'top-picks': '[--rows:1] [--w:min(60%,280px)] [--gap:14px]',
  // Replay's wallet (ReplayScreen.kt ReplayCardRow): 300px cards 12px apart, with room under them for their shadow.
  replay: '[--rows:1] [--w:300px] [--gap:12px] py-6! -my-1!',
};
