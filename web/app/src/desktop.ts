import { useMutation, useQuery } from '@tanstack/react-query';
import { queryClient } from '@/lib/queryClient';

/**
 * The desktop app's own API (web/desktop/src/preload.ts), present only when
 * the site runs there. The bridge itself goes through window messages, as
 * with the extension; this is just the app's window behaviour.
 */
export interface DesktopSettings {
  /** Closing the window keeps BitChord running in the system tray (the menu bar on macOS). */
  closeToTray: boolean;
}

interface DesktopApi {
  platform: string;
  settings: {
    get(): Promise<DesktopSettings>;
    set(change: Partial<DesktopSettings>): Promise<DesktopSettings>;
  };
}

export const desktop: DesktopApi | null =
  typeof window !== 'undefined' ? ((window as { bitchordDesktop?: DesktopApi }).bitchordDesktop ?? null) : null;

const settingsKey = ['desktop', 'settings'] as const;

export function useDesktopSettings() {
  return useQuery({ queryKey: settingsKey, queryFn: () => desktop!.settings.get(), enabled: !!desktop, staleTime: Infinity });
}

export function useSetDesktopSettings() {
  return useMutation({
    mutationFn: (change: Partial<DesktopSettings>) => desktop!.settings.set(change),
    onMutate: (change) => {
      const before = queryClient.getQueryData<DesktopSettings>(settingsKey);
      if (before) queryClient.setQueryData(settingsKey, { ...before, ...change });
      return before;
    },
    onSuccess: (settings) => queryClient.setQueryData(settingsKey, settings),
    onError: (_error, _change, before) => before && queryClient.setQueryData(settingsKey, before),
  });
}
