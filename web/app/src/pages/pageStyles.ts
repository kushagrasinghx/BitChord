/** Styles shared by several pages. */

/** Album and playlist footer (report §3): 13px/18px secondary, release line, counts, label. */
export const detailFooter = 'mx-(--body-gutter) mt-[34px] px-3 pb-10 type-body-tall text-secondary';

/**
 * See All grids: the shelf's column counts, wrapping instead of scrolling
 * (report §2). `data-wide` for video lockups.
 */
export const seeAllGrid =
  'mx-(--body-gutter) mt-5 mb-10 grid grid-cols-[repeat(auto-fill,minmax(170px,1fr))] gap-x-5 gap-y-6 data-wide:grid-cols-[repeat(auto-fill,minmax(240px,1fr))] min-[1260px]:grid-cols-5 min-[1260px]:data-wide:grid-cols-4 min-[1580px]:grid-cols-6';

/** A track list under a See All grid. */
export const seeAllTracks = 'pt-7';
