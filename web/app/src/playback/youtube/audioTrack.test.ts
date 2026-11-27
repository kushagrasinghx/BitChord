import { describe, expect, it } from 'vitest';
import { bestAudioTrack } from './engine';

const track = (originalAudioId: string, audioCodec: string, kbps: number) => ({
  originalAudioId,
  audioCodec,
  audioBandwidth: kbps * 1000,
  bandwidth: kbps * 1000,
});

describe('choosing the audio stream', () => {
  const standard = [track('251-0', 'opus', 160), track('250-0', 'opus', 70), track('140-0', 'mp4a.40.2', 130)];

  it('plays Opus 251 for an ordinary account', () => {
    expect(bestAudioTrack(standard, ['opus', 'mp4a'])?.track.originalAudioId).toBe('251-0');
  });

  it("plays Premium's 256 kbps Opus (itag 774) when YouTube offers it", () => {
    const premium = [...standard, track('774-0', 'opus', 256)];
    expect(bestAudioTrack(premium, ['opus', 'mp4a'])?.track.originalAudioId).toBe('774-0');
  });

  it('never picks a loudness-normalised (DRC) copy, however high its bitrate', () => {
    const withDrc = [...standard, track('251-drc', 'opus', 300)];
    expect(bestAudioTrack(withDrc, ['opus', 'mp4a'])?.track.originalAudioId).toBe('251-0');
  });

  it('falls back to AAC where Opus is unavailable', () => {
    expect(bestAudioTrack(standard, ['mp4a'])?.track.originalAudioId).toBe('140-0');
  });
});
