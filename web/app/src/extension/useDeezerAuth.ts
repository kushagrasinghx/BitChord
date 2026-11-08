import { useMutation, useQuery } from '@tanstack/react-query';
import { useEffect } from 'react';
import type { DeezerAuthState, DetectedExtension } from '@bitchord/shared/bridge';
import { queryClient } from '@/lib/queryClient';
import { extensionQueryKey, useExtension } from './useExtension';

/**
 * The browser's Deezer session as the extension reports it:
 * a name and a picture, never the cookie or a token. Kept current like the
 * YouTube and Spotify ones: a read on start, the extension's pushes, a re-read on focus.
 */

export const deezerAuthQueryKey = ['deezer', 'auth'] as const;

const SIGNED_OUT: DeezerAuthState = { browserSignedIn: false, connected: true, signedIn: false, user: null, problem: null };

function detected(): DetectedExtension | null {
  return queryClient.getQueryData<DetectedExtension | null>(extensionQueryKey) ?? null;
}

const supported = (extension: DetectedExtension | null) => !!extension?.info.capabilities.includes('deezer');

function adopt(state: DeezerAuthState): void {
  const before = queryClient.getQueryData<DeezerAuthState>(deezerAuthQueryKey);
  queryClient.setQueryData(deezerAuthQueryKey, state);
  // Another account, or none: every list read as the old one goes.
  if (before && (before.signedIn !== state.signedIn || before.user?.id !== state.user?.id)) {
    void queryClient.resetQueries({ queryKey: ['deezer', 'data'] });
  }
}

async function readAuth(refresh: boolean): Promise<DeezerAuthState> {
  const extension = detected();
  if (!extension || !supported(extension)) return SIGNED_OUT;
  const state = await extension.bridge.call('deezer.authState', { refresh }, { timeoutMs: 30_000 });
  adopt(state);
  return state;
}

export type DeezerAuthStatus = { state: 'checking' } | { state: 'unavailable' } | { state: 'ready'; auth: DeezerAuthState };

export function useDeezerAuth(): DeezerAuthStatus {
  const extension = useExtension();
  const present = extension.state === 'present' ? extension.extension : null;
  const enabled = supported(present);
  const query = useQuery({
    queryKey: deezerAuthQueryKey,
    queryFn: () => readAuth(false),
    enabled,
    staleTime: 60_000,
    refetchOnWindowFocus: true,
    retry: 1,
  });

  useEffect(() => {
    if (!present || !enabled) return;
    return present.bridge.subscribe((event) => {
      if (event.event === 'deezer.authState') adopt(event.data);
    });
  }, [present, enabled]);

  if (extension.state === 'checking' || (enabled && query.isPending)) return { state: 'checking' };
  if (!enabled) return { state: 'unavailable' };
  return { state: 'ready', auth: query.data ?? SIGNED_OUT };
}

/** True once Deezer requests will be made as the user. */
export function useDeezerSignedIn(): boolean {
  const status = useDeezerAuth();
  return status.state === 'ready' && status.auth.signedIn;
}

export function useSetDeezerConnected() {
  return useMutation({
    mutationFn: async (connected: boolean) => {
      const extension = detected();
      if (!extension) throw new Error('The extension is not installed');
      return extension.bridge.call('deezer.setConnected', { connected });
    },
    onSuccess: adopt,
  });
}

export function useRefreshDeezerAuth() {
  return useMutation({ mutationFn: () => readAuth(true) });
}

export function openDeezerSignIn(): void {
  void detected()?.bridge.call('deezer.signIn', {});
}
