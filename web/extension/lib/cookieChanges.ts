/** Safari can notify without a payload; both services must invalidate then. */
export function affectsSession<T>(change: { cookie: T } | null | undefined, predicate: (cookie: T) => boolean): boolean {
  return !change?.cookie || predicate(change.cookie);
}
