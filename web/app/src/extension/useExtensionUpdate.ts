import { useQuery } from '@tanstack/react-query';
import { config } from '@/config';
import { useExtension } from './useExtension';

/** Where a published zip is served: the site's downloads folder, its name escaped (it has spaces and brackets). */
export function downloadUrl(name: string): string {
  return new URL(encodeURIComponent(name), new URL(config.extensionManifestUrl, location.origin)).toString();
}

/** What `pnpm package` last published next to the zips (web/extension/scripts/publish-to-site.mjs). */
export interface PublishedExtension {
  version: string;
  builtAt: string;
  files: { chrome: string; firefox: string };
}

export type ExtensionUpdate =
  | { state: 'unknown' }
  | { state: 'current'; latest: PublishedExtension }
  | { state: 'available'; installed: string; latest: PublishedExtension };

/** Numeric dot-separated comparison, which is all a manifest version can be. */
export function compareVersions(a: string, b: string): number {
  const left = a.split('.').map(Number);
  const right = b.split('.').map(Number);
  for (let i = 0; i < Math.max(left.length, right.length); i++) {
    const diff = (left[i] ?? 0) - (right[i] ?? 0);
    if (diff !== 0) return Math.sign(diff);
  }
  return 0;
}

async function fetchPublished(): Promise<PublishedExtension | null> {
  const response = await fetch(config.extensionManifestUrl, { cache: 'no-store' });
  if (!response.ok) return null;
  const body = (await response.json()) as Partial<PublishedExtension>;
  return typeof body.version === 'string' && body.files ? (body as PublishedExtension) : null;
}

/**
 * Whether the site offers a newer extension than the one installed. The
 * manifest is re-read when the tab regains focus and every few minutes, so a
 * freshly packaged zip shows up without a reload; the installed version is
 * re-pinged on focus too (useExtension), so the notice clears once the user
 * reloads the extension.
 */
export function usePublishedExtension(): PublishedExtension | null {
  return (
    useQuery({
      queryKey: ['extension', 'published'],
      queryFn: fetchPublished,
      staleTime: 60_000,
      refetchInterval: 5 * 60_000,
      refetchOnWindowFocus: true,
      retry: false,
    }).data ?? null
  );
}

export function useExtensionUpdate(): ExtensionUpdate {
  const extension = useExtension();
  const latest = usePublishedExtension();
  if (!latest || extension.state !== 'present') return { state: 'unknown' };
  const installed = extension.extension.info.version;
  return compareVersions(latest.version, installed) > 0
    ? { state: 'available', installed, latest }
    : { state: 'current', latest };
}
