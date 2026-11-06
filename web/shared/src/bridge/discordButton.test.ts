import { describe, expect, it } from 'vitest';
import { DISCORD_BUTTON_LABEL_MAX, discordButtonLabel } from './discordButton';

const track = { title: 'A Song Title That Runs Well Past Thirty-Two Characters', artist: 'Artist', album: null };

describe('discordButtonLabel', () => {
  it('fills in the variables', () => {
    expect(discordButtonLabel('Hear {artist_name}', 'Listen', track)).toBe('Hear Artist');
  });

  it("keeps a long song name inside Discord's limit", () => {
    const label = discordButtonLabel('Play {song_name}', 'Listen', track);
    expect([...label].length).toBeLessThanOrEqual(DISCORD_BUTTON_LABEL_MAX);
    expect(label.endsWith('…')).toBe(true);
  });

  it('falls back when a variable leaves nothing', () => {
    expect(discordButtonLabel('{album_name}', 'Listen', track)).toBe('Listen');
  });

  it('never cuts an emoji in half', () => {
    const label = discordButtonLabel('🎵'.repeat(40), 'Listen', track);
    expect(label).toBe(`${'🎵'.repeat(31)}…`);
  });
});
