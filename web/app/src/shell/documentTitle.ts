/** The tab's fallback title, as declared in index.html. */
export const APP_TITLE = 'BitChord';

/**
 * The tab title: "⟨now playing⟩ - BitChord" while a track is loaded, plain
 * "BitChord" otherwise. Pure, so it can be unit-tested without React.
 */
export function documentTitle(nowPlaying: { title: string } | null | undefined): string {
  const title = nowPlaying?.title?.trim();
  return title ? `${title} - ${APP_TITLE}` : APP_TITLE;
}
