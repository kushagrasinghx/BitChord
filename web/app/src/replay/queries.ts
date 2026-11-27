import type { ReplayCharts, ReplayYearStory } from '@bitchord/shared-kotlin';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { periodMonths, type ReplayPeriod } from './periods';
import { flushListening } from './recorder';
import { allMonths, bucketsOf, onReplayChanged } from './store';

/** Reads the Replay again whenever a month is written, while something is showing it. */
function useLiveReplay() {
  const queryClient = useQueryClient();
  useEffect(() => onReplayChanged(() => void queryClient.invalidateQueries({ queryKey: ['replay'] })), [queryClient]);
}

export interface ReplayState {
  charts: ReplayCharts;
  /** `MM/YY` of the first month anything was recorded, here or on a synced device. */
  memberSince: string | null;
}

/** The Replay for [period], merged off IndexedDB by the shared Kotlin (ListeningStats.summary). */
export function useReplay(period: ReplayPeriod) {
  useLiveReplay();
  return useQuery({
    queryKey: ['replay', period],
    queryFn: async (): Promise<ReplayState> => {
      await flushListening();
      // The shared Kotlin is loaded with the first Replay read, not with the page.
      const { replayCharts } = await import('@bitchord/shared-kotlin');
      const months = await allMonths();
      const { months: read, week } = periodMonths(period, months, new Date());
      const buckets = (await Promise.all(read.map(bucketsOf))).flat();
      const first = months[0];
      return {
        charts: replayCharts(buckets, week?.[0] ?? null, week?.[1] ?? null),
        memberSince: first ? `${first.slice(5, 7)}/${first.slice(2, 4)}` : null,
      };
    },
    // Never stale on its own: a write invalidates it.
    staleTime: Infinity,
  });
}

/** [year]'s story (ListeningStats.year and ReplayYear), or null when the year has nothing in it. */
export function useReplayYear(year: number | null) {
  useLiveReplay();
  return useQuery({
    queryKey: ['replay', 'year', year],
    enabled: year !== null,
    queryFn: async (): Promise<ReplayYearStory | null> => {
      await flushListening();
      const { replayYear } = await import('@bitchord/shared-kotlin');
      const months = (await allMonths()).filter((m) => m.startsWith(`${year}-`));
      return replayYear(await Promise.all(months.map(bucketsOf))) ?? null;
    },
    staleTime: Infinity,
  });
}
