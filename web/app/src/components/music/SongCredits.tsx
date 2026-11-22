import { useQuery } from '@tanstack/react-query';
import { Dialog } from '@/components/Dialog';
import { fetchCredits } from '@/data/music/credits';
import { useYouTubeReady } from '@/data/music/queries';
import type { Track } from '@/data/music/types';

const section = 'mx-1 mt-[18px] mb-1.5 type-subhead-emphasized uppercase text-secondary first:mt-1';
const note = 'mx-1 py-3 type-callout text-secondary';

/**
 * Song credits (Android's Song Info credits): who performed, wrote and
 * produced the track, grouped by role as YouTube Music lists them.
 */
export function SongCredits({ track, onClose }: { track: Track; onClose: () => void }) {
  const { ready } = useYouTubeReady();
  const credits = useQuery({
    queryKey: ['music', 'credits', track.videoId],
    queryFn: () => fetchCredits(track.videoId),
    enabled: ready,
    staleTime: 60 * 60_000,
  });
  return (
    <Dialog title="Song Credits" subtitle={[track.title, ...track.artists.map((a) => a.name)].join(' · ')} onClose={onClose}>
      {credits.isError ? (
        <div className="flex items-center gap-3">
          <p className={note}>Couldn’t load the credits.</p>
          <button className="type-callout-emphasized text-key" onClick={() => void credits.refetch()}>
            Retry
          </button>
        </div>
      ) : !credits.data ? (
        <p className={note}>Loading…</p>
      ) : !credits.data.length ? (
        <p className={note}>YouTube Music has no credits for this song.</p>
      ) : (
        credits.data.map((credit, i) => (
          <section key={`${credit.role}-${i}`} aria-label={credit.role}>
            <h3 className={section}>{credit.role}</h3>
            <ul className="overflow-hidden rounded-[10px] bg-quinary">
              {credit.names.map((name, j) => (
                <li key={`${name}-${j}`} className="px-3 py-2 type-body [&+&]:border-t-[0.5px] [&+&]:border-t-divider">
                  {name}
                </li>
              ))}
            </ul>
          </section>
        ))
      )}
    </Dialog>
  );
}
