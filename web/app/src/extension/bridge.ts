import type { Bridge, DetectedExtension } from '@bitchord/shared/bridge';
import { queryClient } from '@/lib/queryClient';
import { extensionQueryKey } from './useExtension';

/** The detected extension's bridge, for code outside React; null when it isn't installed. */
export function getBridge(): Bridge | null {
  return queryClient.getQueryData<DetectedExtension | null>(extensionQueryKey)?.bridge ?? null;
}
