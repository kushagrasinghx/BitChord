/**
 * Session plumbing with no browser globals, so it can be tested on its own.
 */

/**
 * One declarativeNetRequest rule carries one `Cookie` value. Requests that need
 * the same value share the installed rule. A request with a different value
 * (a rotated `sid`, another account's `arl`) waits until every request using
 * the installed one has finished and the rule is gone, so no request ever goes
 * out with someone else's cookie. [apply] installs a value, or removes the
 * rule when given null.
 */
export function createCookieGate(apply: (value: string | null) => Promise<void>) {
  interface Held {
    value: string;
    users: number;
    closing: boolean;
    ready: Promise<void>;
    done: Promise<void>;
    release: () => void;
  }
  let held: Held | null = null;

  return async function withCookie<T>(value: string, run: () => Promise<T>): Promise<T> {
    while (held && (held.value !== value || held.closing)) await held.done;
    if (!held) {
      let release!: () => void;
      const done = new Promise<void>((resolve) => (release = resolve));
      held = { value, users: 0, closing: false, ready: apply(value), done, release };
    }
    const mine = held;
    mine.users++;
    try {
      await mine.ready;
      return await run();
    } finally {
      if (--mine.users === 0) {
        mine.closing = true;
        await apply(null).catch(() => undefined);
        held = null;
        mine.release();
      }
    }
  };
}

/**
 * The open session for one key (the `arl` cookie), opened at most once at a
 * time. A session or an open in flight for another key is never handed out,
 * and an open that finishes after the key changed is not cached.
 */
export function createSessionCache<S extends { expiresAt: number }>(
  open: (key: string) => Promise<S>,
  now: () => number = Date.now,
) {
  let current: { key: string; session: S } | null = null;
  let opening: { key: string; promise: Promise<S> } | null = null;

  return {
    get(key: string): Promise<S> {
      if (current?.key === key && now() < current.session.expiresAt) return Promise.resolve(current.session);
      if (opening?.key === key) return opening.promise;
      current = null;
      const attempt: { key: string; promise: Promise<S> } = {
        key,
        promise: open(key)
          .then((session) => {
            if (opening === attempt) current = { key, session };
            return session;
          })
          .finally(() => {
            if (opening === attempt) opening = null;
          }),
      };
      opening = attempt;
      return attempt.promise;
    },
    /** Drops [session] if it is still the cached one; with no argument, drops everything. */
    invalidate(session?: S): void {
      if (session) {
        if (current?.session === session) current = null;
        return;
      }
      current = null;
      opening = null;
    },
  };
}
