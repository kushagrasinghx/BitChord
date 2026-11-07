import type { PlaylistPrivacy } from '@/data/music/types';

/** The privacy YouTube words into a playlist header's second line ("Playlist • Private • 2026"). */
export function privacyOf(subtitle: string): PlaylistPrivacy {
  if (/\bunlisted\b/i.test(subtitle)) return 'UNLISTED';
  if (/\bpublic\b/i.test(subtitle)) return 'PUBLIC';
  return 'PRIVATE';
}
