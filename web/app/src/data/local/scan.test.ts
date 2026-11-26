import { describe, expect, it, vi } from 'vitest';
import { artKey, audioFiles, mimeOf, scanFolder, type DirectoryEntry, type FileEntry, type LocalFile } from './scan';
import type { LocalTags } from './tags';

// Fake File System Access handles: a folder is a list, a file a File with a size and a modified time.

const file = (name: string, size = 10, lastModified = 1): FileEntry => ({
  kind: 'file',
  name,
  getFile: async () => new File([new Uint8Array(size)], name, { lastModified }),
});
const dir = (name: string, entries: (FileEntry | DirectoryEntry)[]): DirectoryEntry => ({
  kind: 'directory',
  name,
  async *values() {
    yield* entries;
  },
});

const COVER = { mime: 'image/jpeg', data: Uint8Array.from([1, 2, 3, 4]) };

/** A tag reader that names each file after itself and gives the "album" ones the same cover. */
function fakeReader() {
  return vi.fn(
    async (_file: Blob, name: string): Promise<LocalTags> => ({
      title: name,
      artist: 'Artist',
      album: name.startsWith('album') ? 'Album' : null,
      albumArtist: null,
      track: null,
      year: null,
      durationSec: 200,
      picture: name.startsWith('album') ? COVER : null,
    }),
  );
}
const keep = async (picture: { mime: string; data: Uint8Array }) => new Blob([picture.data as BlobPart], { type: picture.mime });

describe('audioFiles', () => {
  it('walks every folder for audio, skipping hidden folders and other files', async () => {
    const root = dir('Music', [
      file('a.mp3'),
      file('notes.txt'),
      file('cover.jpg'),
      dir('Rock', [file('b.FLAC'), dir('Live', [file('c.m4a')])]),
      dir('.Trash', [file('d.mp3')]),
    ]);
    const paths: string[] = [];
    for await (const { path } of audioFiles(root)) paths.push(path);
    expect(paths).toEqual(['/Music/a.mp3', '/Music/Rock/b.FLAC', '/Music/Rock/Live/c.m4a']);
  });

  it('types each file for the player by its extension', () => {
    expect(['a.mp3', 'b.FLAC', 'c.m4a', 'd.opus', 'e.wav'].map(mimeOf)).toEqual(['audio/mpeg', 'audio/flac', 'audio/mp4', 'audio/ogg', 'audio/wav']);
  });
});

describe('scanFolder', () => {
  it('reads every file the first time, and keeps one copy of a shared cover', async () => {
    const read = fakeReader();
    const root = dir('Music', [file('album1.mp3'), file('album2.mp3'), file('single.flac')]);
    const result = await scanFolder(root, { previous: new Map(), knownArt: new Set(), read, shrink: keep });
    expect(read).toHaveBeenCalledTimes(3);
    expect(result.read).toBe(3);
    expect(result.files.map((f) => f.path)).toEqual(['/Music/album1.mp3', '/Music/album2.mp3', '/Music/single.flac']);
    expect(result.art.size).toBe(1);
    const key = artKey(COVER.data);
    expect(result.files.map((f) => f.art)).toEqual([key, key, null]);
    expect(result.files[0]).toMatchObject({ title: 'album1.mp3', album: 'Album', durationSec: 200, size: 10, lastModified: 1 });
  });

  it('rescans only what is new or changed, and drops what is gone', async () => {
    const first = await scanFolder(dir('Music', [file('album1.mp3'), file('album2.mp3'), file('single.flac')]), {
      previous: new Map(),
      knownArt: new Set(),
      read: fakeReader(),
      shrink: keep,
    });
    const previous = new Map<string, LocalFile>(first.files.map((f) => [f.path, f]));
    const read = fakeReader();
    // album2 edited (a new modified time), single.flac deleted, new.ogg added.
    const second = await scanFolder(dir('Music', [file('album1.mp3'), file('album2.mp3', 10, 2), file('new.ogg')]), {
      previous,
      knownArt: new Set(first.art.keys()),
      read,
      shrink: keep,
    });
    expect(read.mock.calls.map(([, name]) => name)).toEqual(['album2.mp3', 'new.ogg']);
    expect(second.read).toBe(2);
    expect(second.files.map((f) => f.path)).toEqual(['/Music/album1.mp3', '/Music/album2.mp3', '/Music/new.ogg']);
    // The unchanged file is the very record it was.
    expect(second.files[0]).toBe(previous.get('/Music/album1.mp3'));
    // The cover is already stored, so nothing new to keep.
    expect(second.art.size).toBe(0);
  });

  it('reports progress, and stops when cancelled', async () => {
    const seen: number[] = [];
    await scanFolder(dir('Music', [file('a.mp3'), file('b.mp3')]), { previous: new Map(), knownArt: new Set(), read: fakeReader(), onProgress: (n) => seen.push(n) });
    expect(seen).toEqual([1, 2]);
    const controller = new AbortController();
    controller.abort();
    await expect(scanFolder(dir('Music', [file('a.mp3')]), { previous: new Map(), knownArt: new Set(), read: fakeReader(), signal: controller.signal })).rejects.toThrow();
  });
});
