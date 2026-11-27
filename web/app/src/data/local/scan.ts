import { readTags, type LocalPicture, type LocalTags } from './tags';

/**
 * One audio file in the chosen folder, as the index keeps it: where it is,
 * what it was when last read (size and time, so a rescan only reads what
 * changed), and what its tags said. The cover is a key into the art store,
 * shared by every track carrying the same picture.
 */
export interface LocalFile extends Omit<LocalTags, 'picture'> {
  /** Under the chosen folder, led by its name: `/Music/Rock/song.mp3` (LocalWeb.kt LocalRow.path). */
  path: string;
  name: string;
  size: number;
  lastModified: number;
  art: string | null;
}

/** The File System Access handles the scan reads, cut to what it uses, so tests can hand it plain objects. */
export interface FileEntry {
  kind: 'file';
  name: string;
  getFile(): Promise<File>;
}
export interface DirectoryEntry {
  kind: 'directory';
  name: string;
  values(): AsyncIterable<FileEntry | DirectoryEntry>;
}

/**
 * The formats Chromium plays from a file and the tag reader knows. Android
 * also lists DSD and WebM (LocalMusicRules.localMusicExtensions); a browser
 * can't decode DSD, and a .webm is far more often a video than a song.
 */
const AUDIO_FILE = /\.(mp3|m4a|mp4|aac|flac|ogg|oga|opus|wav)$/i;

const MIME: Record<string, string> = {
  mp3: 'audio/mpeg',
  m4a: 'audio/mp4',
  mp4: 'audio/mp4',
  aac: 'audio/aac',
  flac: 'audio/flac',
  ogg: 'audio/ogg',
  oga: 'audio/ogg',
  opus: 'audio/ogg',
  wav: 'audio/wav',
};

/** The type the player loads [name] as (Shaka's src= mode needs one the element says it can play). */
export const mimeOf = (name: string): string => MIME[name.slice(name.lastIndexOf('.') + 1).toLowerCase()] ?? 'audio/mpeg';

/** Every audio file under [dir], depth first, with its path. Hidden folders (".git", ".Trash") are skipped. */
export async function* audioFiles(dir: DirectoryEntry, prefix = `/${dir.name}`): AsyncGenerator<{ path: string; entry: FileEntry }> {
  for await (const entry of dir.values()) {
    if (entry.name.startsWith('.')) continue;
    const path = `${prefix}/${entry.name}`;
    if (entry.kind === 'directory') yield* audioFiles(entry, path);
    else if (AUDIO_FILE.test(entry.name)) yield { path, entry };
  }
}

/** The handle for [path] (as audioFiles named it) under [root]. */
export async function fileAt(root: FileSystemDirectoryHandle, path: string): Promise<FileSystemFileHandle> {
  const parts = path.split('/').slice(2); // "", the root's own name, then the way down
  let dir = root;
  for (const part of parts.slice(0, -1)) dir = await dir.getDirectoryHandle(part);
  return dir.getFileHandle(parts[parts.length - 1]!);
}

/** A picture's key in the art store: its size and a hash of its two ends, which is enough to tell covers apart. */
export function artKey(data: Uint8Array): string {
  let h = 0x811c9dc5;
  const sample = (from: number, to: number) => {
    for (let i = from; i < to; i++) h = Math.imul(h ^ data[i]!, 16777619);
  };
  sample(0, Math.min(4096, data.length));
  sample(Math.max(0, data.length - 4096), data.length);
  return `${data.length.toString(36)}-${(h >>> 0).toString(36)}`;
}

/** The longest side a stored cover keeps: enough for the album header and the media session's 512px. */
const ART_PX = 600;

/**
 * A cover small enough to keep for every album: embedded art is often a
 * 2–5 MB scan. Shrunk where the browser can (createImageBitmap and
 * OffscreenCanvas); kept as it is where it can't, or when it's small already.
 */
export async function shrinkArt({ mime, data }: LocalPicture): Promise<Blob> {
  const blob = new Blob([data as BlobPart], { type: mime });
  if (data.length < 150 * 1024 || typeof OffscreenCanvas === 'undefined' || typeof createImageBitmap === 'undefined') return blob;
  try {
    const probe = await createImageBitmap(blob);
    const scale = Math.min(1, ART_PX / Math.max(probe.width, probe.height));
    const width = Math.round(probe.width * scale);
    const height = Math.round(probe.height * scale);
    const canvas = new OffscreenCanvas(width, height);
    canvas.getContext('2d')!.drawImage(probe, 0, 0, width, height);
    probe.close();
    const small = await canvas.convertToBlob({ type: 'image/jpeg', quality: 0.86 });
    return small.size < blob.size ? small : blob;
  } catch {
    return blob;
  }
}

export interface ScanOptions {
  /** The files as last indexed, by path. */
  previous: ReadonlyMap<string, LocalFile>;
  /** Covers already in the art store. */
  knownArt: ReadonlySet<string>;
  read?: (file: Blob, name: string) => Promise<LocalTags>;
  shrink?: (picture: LocalPicture) => Promise<Blob>;
  /** Told how many files have been looked at so far. */
  onProgress?: (seen: number) => void;
  signal?: AbortSignal;
}

export interface ScanResult {
  /** Every audio file there now, in walk order. */
  files: LocalFile[];
  /** Covers found that the art store doesn't have yet. */
  art: Map<string, Blob>;
  /** How many files had their tags read (new or changed); the rest came from [ScanOptions.previous]. */
  read: number;
}

/** How many files have their tags read at once: enough to hide each read's wait, few enough to stay light. */
const PARALLEL = 4;

/**
 * Walks [root] and indexes what's there. A file whose size and modified time
 * match the index is taken from it unread; anything new or changed has its
 * tags read. Nothing here writes: the caller stores the result, so a scan
 * that fails half-way leaves the old index as it was.
 */
export async function scanFolder(root: DirectoryEntry, options: ScanOptions): Promise<ScanResult> {
  const { previous, knownArt, read = readTags, shrink = shrinkArt, onProgress, signal } = options;
  const found: { path: string; entry: FileEntry }[] = [];
  for await (const item of audioFiles(root)) {
    signal?.throwIfAborted();
    found.push(item);
  }
  const files: LocalFile[] = new Array(found.length);
  const art = new Map<string, Blob>();
  let readCount = 0;
  let next = 0;
  let seen = 0;
  const worker = async () => {
    while (next < found.length) {
      signal?.throwIfAborted();
      const i = next++;
      const { path, entry } = found[i]!;
      const file = await entry.getFile();
      const old = previous.get(path);
      if (old && old.size === file.size && old.lastModified === file.lastModified) {
        files[i] = old;
      } else {
        readCount++;
        const { picture, ...tags } = await read(file, entry.name);
        let key: string | null = null;
        if (picture) {
          key = artKey(picture.data);
          if (!knownArt.has(key) && !art.has(key)) art.set(key, await shrink(picture));
        }
        files[i] = { ...tags, path, name: entry.name, size: file.size, lastModified: file.lastModified, art: key };
      }
      onProgress?.(++seen);
    }
  };
  await Promise.all(Array.from({ length: Math.min(PARALLEL, found.length) }, worker));
  return { files, art, read: readCount };
}
