import { useOfflineSettings } from '@/playback/offline/settings';
import { driveAvailable } from './auth';

/** Where driveBackup.ts keeps its state (zustand's persist). */
export const DRIVE_STATE_KEY = 'bitchord:drive';

/**
 * What runs while the app is open in place of the phone's background jobs:
 * Drive sync (driveBackup.ts) and keeping liked songs downloaded
 * (playback/offline/likedSync.ts). Each is loaded only when it is switched on,
 * so a visitor who uses neither downloads neither.
 */
export function autostart(): void {
  if (driveAvailable() && hasDriveAccount()) void import('./driveBackup').then((m) => m.startDriveSync());
  if (useOfflineSettings.getState().keepLiked) void import('@/playback/offline/likedSync').then((m) => m.startLikedSync());
}

/** Read straight from storage: the sync module, and the shared Kotlin it imports, aren't loaded to ask. */
function hasDriveAccount(): boolean {
  try {
    const saved = JSON.parse(localStorage.getItem(DRIVE_STATE_KEY) ?? 'null') as { state?: { account?: string | null } } | null;
    return !!saved?.state?.account;
  } catch {
    return false;
  }
}
