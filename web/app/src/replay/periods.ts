/*
 * The Replay's stretches of time, on the browser's own calendar: which months a
 * period reads (ListeningStats.kt ReplayPeriod), and what it is called.
 */

/** `2026-11` for [date], in the browser's own time zone, as the phone files months. */
export function monthKey(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}

/** How far back a Replay reaches (ListeningStats.kt ReplayPeriod), and its tab in the picker. */
export const PERIODS = [
  { id: 'week', tab: 'Weekly' },
  { id: 'month', tab: 'Monthly' },
  { id: 'year', tab: 'Yearly' },
  { id: 'all', tab: 'Lifetime' },
] as const;

export type ReplayPeriod = (typeof PERIODS)[number]['id'];

const isoDay = (date: Date) => `${monthKey(date)}-${String(date.getDate()).padStart(2, '0')}`;

/**
 * Which months [period] reads on [today], of [months], and for a week the
 * days it keeps (ReplayPeriod.covers and range): Monday to today, which may
 * span two months.
 */
export function periodMonths(period: ReplayPeriod, months: string[], today: Date): { months: string[]; week: [string, string] | null } {
  switch (period) {
    case 'week': {
      const monday = new Date(today.getFullYear(), today.getMonth(), today.getDate() - ((today.getDay() + 6) % 7));
      const span = new Set([monthKey(monday), monthKey(today)]);
      return { months: months.filter((m) => span.has(m)), week: [isoDay(monday), isoDay(today)] };
    }
    case 'month':
      return { months: months.filter((m) => m === monthKey(today)), week: null };
    case 'year':
      return { months: months.filter((m) => m.startsWith(`${today.getFullYear()}-`)), week: null };
    case 'all':
      return { months, week: null };
  }
}

/** The Replay's title for [period] on [today]: "This week", "November 2026", "2026", "All time". */
export function periodLabel(period: ReplayPeriod, today: Date): string {
  switch (period) {
    case 'week':
      return 'This week';
    case 'month':
      return today.toLocaleDateString(undefined, { month: 'long', year: 'numeric' });
    case 'year':
      return String(today.getFullYear());
    case 'all':
      return 'All time';
  }
}

/** Which year's story Home and the Replay offer on [today]: only through December, for the year that is ending. */
export function yearShown(today: Date): number | null {
  return today.getMonth() === 11 ? today.getFullYear() : null;
}
