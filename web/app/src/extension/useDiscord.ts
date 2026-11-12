import { useMutation, useQuery } from '@tanstack/react-query';
import { useEffect } from 'react';
import type { DetectedExtension, DiscordPresenceOptions, DiscordState } from '@bitchord/shared/bridge';
import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { queryClient } from '@/lib/queryClient';
import { extensionQueryKey, useExtension } from './useExtension';

/**
 * Discord rich presence (prompt §7.2–7.3). The extension holds the account
 * and the gateway; this page only says what's playing. How the card reads is
 * chosen here, with the phone's options (DiscordScreen) and defaults.
 */

export const discordQueryKey = ['discord', 'state'] as const;

const UNAVAILABLE: DiscordState = { available: false, connected: false, enabled: false, user: null, gateway: 'off', problem: null };

function detected(): DetectedExtension | null {
  return queryClient.getQueryData<DetectedExtension | null>(extensionQueryKey) ?? null;
}

const supported = (extension: DetectedExtension | null) => !!extension?.info.capabilities.includes('discord');

const adopt = (state: DiscordState) => queryClient.setQueryData(discordQueryKey, state);

export function discordSnapshot(): DiscordState {
  return queryClient.getQueryData<DiscordState>(discordQueryKey) ?? UNAVAILABLE;
}

async function readState(): Promise<DiscordState> {
  const extension = detected();
  if (!extension || !supported(extension)) return UNAVAILABLE;
  const state = await extension.bridge.call('discord.state', {});
  adopt(state);
  return state;
}

export type DiscordStatus = { state: 'checking' } | { state: 'unavailable' } | { state: 'ready'; discord: DiscordState };

export function useDiscord(): DiscordStatus {
  const extension = useExtension();
  const present = extension.state === 'present' ? extension.extension : null;
  const enabled = supported(present);
  const query = useQuery({ queryKey: discordQueryKey, queryFn: readState, enabled, staleTime: 30_000, refetchOnWindowFocus: true });

  useEffect(() => {
    if (!present || !enabled) return;
    return present.bridge.subscribe((event) => {
      if (event.event === 'discord.state') adopt(event.data);
    });
  }, [present, enabled]);

  if (extension.state === 'checking' || (enabled && query.isPending)) return { state: 'checking' };
  if (!enabled) return { state: 'unavailable' };
  return { state: 'ready', discord: query.data ?? UNAVAILABLE };
}

function bridgeCall() {
  const extension = detected();
  if (!extension) throw new Error('The extension is not installed');
  return extension.bridge;
}

export function useDiscordConnect() {
  return useMutation({ mutationFn: () => bridgeCall().call('discord.connect', {}) });
}

export function useDiscordDisconnect() {
  return useMutation({ mutationFn: () => bridgeCall().call('discord.disconnect', {}), onSuccess: adopt });
}

export function useDiscordEnabled() {
  return useMutation({ mutationFn: (on: boolean) => bridgeCall().call('discord.setEnabled', { enabled: on }), onSuccess: adopt });
}

// ---- How the card reads --------------------------------------------------------

export const DEFAULT_DISCORD_OPTIONS: DiscordPresenceOptions = {
  activityType: 'listening',
  status: 'online',
  useDetails: false,
  activityName: '',
  button1Text: '',
  button1Visible: true,
  button2Text: '',
  button2Visible: true,
};

interface DiscordOptionsStore {
  options: DiscordPresenceOptions;
  set: (change: Partial<DiscordPresenceOptions>) => void;
  reset: () => void;
}

export const useDiscordOptions = create<DiscordOptionsStore>()(
  persist(
    (set) => ({
      options: DEFAULT_DISCORD_OPTIONS,
      set: (change) => set((s) => ({ options: { ...s.options, ...change } })),
      reset: () => set({ options: DEFAULT_DISCORD_OPTIONS }),
    }),
    { name: 'bitchord:discord', version: 1 },
  ),
);
