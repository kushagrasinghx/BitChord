import type { NowPlaying } from '@/state/player';

/** "Opus · 48 kHz · 141 kbps · Stereo · itag 251 · VISIONOS direct", as much as is known. */
export function describeStream(np: NowPlaying): string | null {
  const s = np.stream;
  if (!s) return np.streamInfo ?? null;
  let codec = s.codec;
  if (codec) {
    if (/mp4a/i.test(codec)) codec = 'AAC';
    else if (/opus/i.test(codec)) codec = 'Opus';
    else if (/flac/i.test(codec)) codec = 'FLAC';
  }
  const parts = [
    codec,
    s.sampleRateHz ? `${+(s.sampleRateHz / 1000).toFixed(1)} kHz` : null,
    s.bitrateKbps ? `${s.bitrateKbps} kbps` : null,
    s.channels ? ({ 1: 'Mono', 2: 'Stereo' } as Record<number, string>)[s.channels] ?? `${s.channels} channels` : null,
    s.itag ? `itag ${s.itag}` : null,
    s.client && s.mode ? `${s.client} ${s.mode}` : s.client || s.mode || null,
  ];
  const formatted = parts.filter(Boolean).join(' · ');
  return formatted || np.streamInfo || null;
}
