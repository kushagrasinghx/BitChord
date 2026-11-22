import { PageHeader } from '@/components/PageHeader';
import { MoodGrid } from '@/components/music/TopPicksShelf';

/** Find Your Mood's Show all (TopPicksShelf.kt MoodGridPage): every mood and activity, each a station. */
export default function MoodsPage() {
  return (
    <>
      <PageHeader title="Find Your Mood" />
      <MoodGrid />
    </>
  );
}
