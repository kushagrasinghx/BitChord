import { describe, expect, it } from 'vitest';
import { artistForLyricsSearch, forLyricsSearch } from './sources';

// Android's own cases (app/src/test/…/NewLyricsSourceTest.kt), run here through the compiled Kotlin (LyricsQuery.kt).
describe('forLyricsSearch', () => {
  it('takes guest credits off, however they are written', () => {
    for (const title of ['Dracula (feat. JENNIE)', 'Dracula (with JENNIE)', 'Dracula [ft. JENNIE]', 'Dracula feat. JENNIE', 'Dracula featuring JENNIE']) {
      expect(forLyricsSearch(title), title).toBe('Dracula');
    }
  });

  it('takes upload labels off', () => {
    for (const title of ['Blinding Lights (Official Video)', 'Blinding Lights (Official Music Video)', 'Blinding Lights [Lyrics]', 'Blinding Lights (4K)']) {
      expect(forLyricsSearch(title), title).toBe('Blinding Lights');
    }
    expect(forLyricsSearch('Levitating (feat. DaBaby) [Official Video]')).toBe('Levitating');
  });

  it('leaves on what names a different recording', () => {
    for (const title of ['Bad Habits (Acoustic)', 'Yesterday (Remastered 2009)', 'Heat Waves (Sped Up)']) expect(forLyricsSearch(title), title).toBe(title);
  });

  it('drops " - Topic" from an auto-generated channel name', () => {
    expect(artistForLyricsSearch('Arijit Singh - Topic')).toBe('Arijit Singh');
  });
});
