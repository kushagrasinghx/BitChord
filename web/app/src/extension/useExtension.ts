import { detectExtension, type DetectedExtension } from '@bitchord/shared/bridge';
import { useQuery } from '@tanstack/react-query';
import { config } from '@/config';
import { queryClient } from '@/lib/queryClient';

export type ExtensionStatus =
  | { state: 'checking' }
  | { state: 'missing' }
  | { state: 'present'; extension: DetectedExtension };

export const extensionQueryKey = ['extension', 'detect'] as const;

/** Re-pings after a miss: an MV3 service worker waking from sleep can miss the first one. */
const RECHECKS = 4;
const RECHECK_MS = 1500;
/** After those, a still-missing extension is polled slowly, so one that wakes late (or is installed meanwhile) is picked up without a reload. */
const SLOW_RECHECK_MS = 5000;

/**
 * One ping. When the extension was already known, a miss gets a second, more
 * patient ping, and if that misses too the known extension is kept: a worker
 * that fell asleep or restarted must not flip the site to "not installed" and
 * strand everything that reads the bridge (history reporting, lyrics) until a
 * reload. A bridge is only a function over chrome.runtime, so it recovers on
 * its own when the worker is back; a removed extension fails each call instead.
 */
async function detect(): Promise<DetectedExtension | null> {
  const known = queryClient.getQueryData<DetectedExtension | null>(extensionQueryKey) ?? null;
  const found = await detectExtension({ chromiumExtensionId: config.chromiumExtensionId });
  if (found || !known) return found;
  return (await detectExtension({ chromiumExtensionId: config.chromiumExtensionId, timeoutMs: 4000 })) ?? known;
}

/**
 * Whether the companion extension is installed (prompt §4.2: a `ping` on
 * startup). A miss is reported at once, so a visitor without the extension
 * sees the install prompt straight away, but re-checked a few times: a
 * sleeping worker answering late flips the page to its content on its own.
 */
export function useExtension(): ExtensionStatus {
  const query = useQuery({
    queryKey: extensionQueryKey,
    queryFn: detect,
    staleTime: 30_000,
    gcTime: Infinity,
    retry: false,
    // Re-pinged when the tab comes back, so an extension updated or reloaded
    // meanwhile reports its new version (useExtensionUpdate).
    refetchOnWindowFocus: true,
    refetchInterval: (q) =>
      q.state.data === null ? (q.state.dataUpdateCount <= RECHECKS ? RECHECK_MS : SLOW_RECHECK_MS) : false,
  });
  if (query.isPending) return { state: 'checking' };
  if (!query.data) return { state: 'missing' };
  return { state: 'present', extension: query.data };
}
