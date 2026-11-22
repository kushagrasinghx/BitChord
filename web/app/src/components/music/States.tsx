import { PrefetchLink } from '@/components/PrefetchLink';

/**
 * A page-sized message, centred (sharedUi/.../components/Common.kt
 * MessageState): 16px bodyLarge in the secondary colour, 48px above and
 * below, and the action 16px under it as the phone's Material Button: a 40px
 * pill in the theme's primary (white on dark) with its contrast text. Also
 * the Spotify page's sign-in invitation and the Replay's empty state.
 */
export const stateStyles = {
  state: 'mx-auto flex max-w-[420px] flex-col items-center gap-1 px-[22px] py-12 text-center',
  title: 'type-body text-secondary',
  detail: 'type-callout text-secondary',
  button: 'mt-3 inline-flex h-10 items-center rounded-pill bg-key px-6 type-callout-medium text-on-key transition-[scale] duration-150 ease-out active:scale-[.97]',
};

/** In place of a YouTube page when the extension isn't installed (prompt §1): an invitation, not an error. */
export function NeedsExtension({ what = 'YouTube Music' }: { what?: string }) {
  return (
    <div className={stateStyles.state}>
      <p className={stateStyles.title}>Browse {what} with the BitChord extension</p>
      <p className={stateStyles.detail}>
        The extension lets BitChord reach YouTube Music with your own sign-in. Addons and lyrics work without it.
      </p>
      <PrefetchLink to="/extension" className={stateStyles.button}>
        Get the Extension
      </PrefetchLink>
    </div>
  );
}

export function LoadError({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  return (
    <div className={stateStyles.state}>
      <p className={stateStyles.title}>Couldn’t load this page</p>
      <p className={stateStyles.detail}>{error instanceof Error ? error.message : String(error)}</p>
      <button className={stateStyles.button} onClick={onRetry}>
        Try Again
      </button>
    </div>
  );
}

export function EmptyState({ title, detail }: { title: string; detail?: string }) {
  return (
    <div className={stateStyles.state}>
      <p className={stateStyles.title}>{title}</p>
      {detail && <p className={stateStyles.detail}>{detail}</p>}
    </div>
  );
}

/** A later page of a long list failed: what's loaded stays, with a way to try again. */
export function LoadMoreError({ onRetry }: { onRetry: () => void }) {
  return (
    <div className="flex items-center justify-center gap-2.5 px-(--body-gutter) pt-6 pb-10 type-callout text-secondary">
      <span>Couldn’t load more.</span>
      <button className="type-callout-emphasized text-key" onClick={onRetry}>
        Try Again
      </button>
    </div>
  );
}
