/*
 * How the Replay says its numbers, as ReplayModel.kt does on the phone, so a
 * card reads the same in either.
 */

/** 1284 as "1,284". */
export const grouped = (value: number) => Math.floor(value).toLocaleString('en-US');

/** The headline figure on the minutes card: always minutes, always grouped. */
export const formatMinutes = (ms: number) => grouped(ms / 60_000);

/**
 * Listening time, in the largest unit that still says something: minutes up
 * to an hour, hours and minutes up to a day, then grouped minutes.
 */
export function formatListening(ms: number): string {
  const minutes = Math.floor(ms / 60_000);
  if (minutes < 60) return `${minutes} min`;
  if (minutes < 1_440) return `${Math.floor(minutes / 60)}h ${minutes % 60}m`;
  return `${grouped(minutes)} min`;
}

/** "3 PM", "midnight": an hour of the day said the way anyone would say it. */
export function formatHour(hour: number): string {
  if (hour === 0) return 'midnight';
  if (hour === 12) return 'midday';
  return hour < 12 ? `${hour} AM` : `${hour - 12} PM`;
}

/** `2026-08-14` as "14 August" (the phone's `d MMMM`). */
export function formatDay(iso: string): string {
  const [y, m, d] = iso.split('-').map(Number);
  if (!y || !m || !d) return iso;
  const month = new Date(y, m - 1, d).toLocaleDateString(undefined, { month: 'long' });
  return `${d} ${month}`;
}

/** "1 play", "1,284 plays": the phone's plurals, grouped. */
export const count = (n: number, one: string, other = `${one}s`) => `${grouped(n)} ${n === 1 ? one : other}`;
