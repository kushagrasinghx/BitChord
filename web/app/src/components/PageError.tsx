import { useEffect, useState } from 'react';
import { isRouteErrorResponse, useRouteError } from 'react-router';
import { PrefetchLink } from '@/components/PrefetchLink';
import { stateStyles } from '@/components/music/States';
import { isStaleBuildError, reloadForNewBuild } from '@/lib/staleBuild';

/**
 * A page that failed to render, in place of React Router's bare "Unexpected
 * Application Error!": the sidebar and player stay. A page from an older
 * build (the site was updated while this tab was open) reloads into the new
 * one instead.
 */
export function PageError() {
  const error = useRouteError();
  const stale = isStaleBuildError(error);
  const [reloading, setReloading] = useState(false);
  useEffect(() => {
    if (stale) setReloading(reloadForNewBuild());
  }, [stale]);

  if (reloading) {
    return (
      <div className={stateStyles.state}>
        <p className={stateStyles.title}>Updating BitChord…</p>
        <p className={stateStyles.detail}>A newer version is out. Loading it now.</p>
      </div>
    );
  }
  const message = isRouteErrorResponse(error) ? `${error.status} ${error.statusText}` : error instanceof Error ? error.message : String(error);
  return (
    <div className={stateStyles.state} role="alert">
      <p className={stateStyles.title}>{stale ? 'BitChord was updated' : 'Something went wrong on this page'}</p>
      <p className={stateStyles.detail}>{stale ? 'Reload to get the new version.' : message}</p>
      <div className="flex gap-2">
        <button className={stateStyles.button} onClick={() => location.reload()}>
          {stale ? 'Reload' : 'Try Again'}
        </button>
        {!stale && (
          <PrefetchLink to="/home" className={`${stateStyles.button} bg-quaternary`}>
            Home
          </PrefetchLink>
        )}
      </div>
    </div>
  );
}
