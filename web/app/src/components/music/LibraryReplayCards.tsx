import { useQuery } from '@tanstack/react-query';
import { PrefetchLink } from '@/components/PrefetchLink';
import { count, formatMinutes } from '@/replay/format';
import { periodLabel } from '@/replay/periods';
import { useReplay } from '@/replay/queries';
import { allMonths } from '@/replay/store';
import { Artwork } from './Artwork';

/**
 * The way in to Replay at the head of the Library (LibraryPhoneParts.kt
 * LibraryReplayEntry): this month's headline cards, each opening the Replay
 * page, and nothing at all until there is listening to show. Cards are the
 * phone's ReplayCreditCard shape, 300px at a bank card's 1.586 and r20, 12
 * apart in a row that snaps; the face is the top song's artwork under a scrim,
 * as the Replay page's own cards are drawn.
 */
export function LibraryReplayCards() {
  const months = useQuery({ queryKey: ['replay', 'months'], queryFn: allMonths, staleTime: Infinity });
  const replay = useReplay('month');
  const charts = months.data?.length ? replay.data?.charts : null;
  if (!charts || charts.totalPlays === 0) return null;
  const [song] = charts.songs;
  const [artist] = charts.artists;
  const label = periodLabel('month', new Date());
  const cards = [
    { label: 'Minutes listened', value: formatMinutes(charts.totalMs), detail: `${count(charts.totalPlays, 'play')} · ${label}`, art: song?.art },
    ...(song ? [{ label: 'Top song', value: song.title, detail: song.artist, art: song.art }] : []),
    ...(artist ? [{ label: 'Top artist', value: artist.title, detail: count(artist.plays, 'play'), art: artist.art }] : []),
  ];
  return (
    <ul className="flex snap-x snap-mandatory scroll-px-(--body-gutter) gap-3 overflow-x-auto px-(--body-gutter) py-1.5 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
      {cards.map((card) => (
        <li key={card.label} className="w-[300px] flex-none snap-start">
          <PrefetchLink
            to="/replay"
            className="group relative isolate flex aspect-[1.586] flex-col justify-end overflow-hidden rounded-[20px] p-5 text-white shadow-[0_8px_16px_rgb(0_0_0/0.25)]"
          >
            <span className="absolute inset-0 -z-1 transition-transform duration-500 group-hover:scale-105 motion-reduce:transition-none">
              <Artwork src={card.art ?? null} displayWidth={300} className="size-full" />
            </span>
            <span className="absolute inset-0 -z-1 bg-linear-to-t from-black/80 via-black/40 to-black/15" />
            <span className="type-subhead-emphasized tracking-wide text-white/70 uppercase">{card.label}</span>
            <span className="mt-0.5 line-clamp-2 type-[700_27px/30px]">{card.value}</span>
            <span className="mt-1 truncate type-callout text-white/75">{card.detail}</span>
          </PrefetchLink>
        </li>
      ))}
    </ul>
  );
}
