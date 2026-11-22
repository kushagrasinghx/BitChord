/**
 * The Moods & Genres grid (sharedUi/.../screens/ExploreScreen.kt): as many
 * columns as fit tiles at least 220px wide 12px apart, which is the phone's
 * ⌊(w − 20) / 232⌋, held between two and six. Kept apart from the tiles so
 * the route's skeleton can use it from the main bundle.
 */
export const moodGenreGrid =
  'mx-(--body-gutter) mt-3.5 mb-[26px] grid gap-3 grid-cols-[repeat(auto-fill,minmax(min(max(220px,calc((100%_-_60px)/6)),calc((100%_-_12px)/2)),1fr))]';
