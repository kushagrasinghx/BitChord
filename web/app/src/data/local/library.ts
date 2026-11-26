import {
  isEligibleLocalMusic,
  localAlbumPageId,
  localArtistPageId,
  localCleanTag,
  LocalRow,
  localTitle,
  localTrackNumber,
} from '@bitchord/shared-kotlin';
import { create } from 'zustand';
import type { Track } from '@/data/music/types';
import type { OfflineTrack } from '@/playback/offline/store';
import { trackLog } from '@/playback/trackLog';
import { usePlayer } from '@/state/player';
import { isLocalId, LOCAL_FOLDER_ID, localTrackId } from './ids';
import { fileAt, mimeOf, scanFolder, type DirectoryEntry, type LocalFile } from './scan';
import { useLocalSettings } from './settings';
import { readIndex, readRoot, saveRoot, saveScan } from './store';

/**
 * The web's Local Music (LocalMediaRepository.kt getLocalMusic, as the phone
 * reads its chosen folder): one folder picked with the File System Access
 * API, its audio files indexed in this browser (store.ts), and kept current
 * by a rescan on launch, from Settings, and whenever the folder changes
 * where the browser can say so (FileSystemObserver).
 *
 * The browser asks again for permission to read the folder after a restart
 * unless the person chose to allow it on every visit; until it's given, the
 * index still lists and Library offers one tap to allow access (permission
 * needs a click). Playing a file asks too, from the Play click itself.
 */

type Access = 'granted' | 'prompt' | 'denied';

interface PermissionHandle extends FileSystemDirectoryHandle {
  queryPermission(descriptor: { mode: 'read' }): Promise<Access>;
  requestPermission(descriptor: { mode: 'read' }): Promise<Access>;
}

interface LocalState {
  /** The chosen folder's name, once the stored handle has been read; null for none. */
  folder: string | null;
  /** Whether this visit may read the folder; null before it's known. */
  access: Access | null;
  /** Every audio file indexed, before the non-music filter. */
  files: LocalFile[];
  /** The index has been read from storage. */
  loaded: boolean;
  /** Files looked at by the scan under way; null when none is. */
  scanning: number | null;
  error: string | null;
}

export const useLocalLibrary = create<LocalState>()(() => ({
  folder: null,
  access: null,
  files: [],
  loaded: false,
  scanning: null,
  error: null,
}));

let root: PermissionHandle | null = null;
/** Object URLs for the stored covers, by art key, made once per visit. */
const artUrls = new Map<string, string>();

// ---- Tracks --------------------------------------------------------------------------

const UNKNOWN_ARTIST = 'Unknown Artist';

/**
 * A file as a queue track (LocalMediaRepository.kt AudioColumns.song): the
 * title tag or the file name, "Unknown Artist" for an untagged credit, and
 * the artist and album linked to their LocalGroups pages, so a row's credit
 * and the player's "Go to album" open them.
 */
export function trackOf(file: LocalFile, artwork: string | null = file.art ? (artUrls.get(file.art) ?? null) : null): Track {
  const artist = localCleanTag(file.artist) ?? UNKNOWN_ARTIST;
  const album = localCleanTag(file.album);
  return {
    videoId: localTrackId(file.path),
    title: localTitle(file.title, file.name),
    artists: [{ name: artist, id: localArtistPageId(LOCAL_FOLDER_ID, artist) }],
    album: album ? { name: album, id: localAlbumPageId(LOCAL_FOLDER_ID, album) } : null,
    durationSec: file.durationSec ? Math.round(file.durationSec) : null,
    explicit: false,
    thumbnail: artwork,
    index: localTrackNumber(file.track) ?? null,
    plays: null,
  };
}

/** A file as LocalWeb.kt reads it, beside its track. */
const rowOf = (file: LocalFile, track: Track) =>
  new LocalRow(track.title, track.artists[0]!.name, track.album?.name ?? null, file.path, track.index, track.thumbnail);

export interface LocalView {
  /** The files shown, by title (LocalMusicSort.TITLE_ASC, the phone's default). */
  files: LocalFile[];
  tracks: Track[];
  /** The same, for LocalWeb.kt's groupings, position for position. */
  rows: LocalRow[];
}

let viewCache: { files: LocalFile[]; filter: boolean; view: LocalView } | null = null;

/** What Local Music shows: the index, less what "Filter non-music audio" hides (LocalMusicRules.isEligibleLocalMusic). */
export function localView(files: LocalFile[], filter: boolean): LocalView {
  if (viewCache?.files === files && viewCache.filter === filter) return viewCache.view;
  const kept = (filter ? files.filter((f) => isEligibleLocalMusic((f.durationSec ?? 0) * 1000, f.name, f.path)) : files).map((file) => ({
    file,
    track: trackOf(file),
  }));
  kept.sort((a, b) => a.track.title.localeCompare(b.track.title, undefined, { sensitivity: 'base' }));
  const view = { files: kept.map((k) => k.file), tracks: kept.map((k) => k.track), rows: kept.map((k) => rowOf(k.file, k.track)) };
  viewCache = { files, filter, view };
  return view;
}

export function useLocalView(): LocalView {
  const files = useLocalLibrary((s) => s.files);
  const filter = useLocalSettings((s) => s.filterNonMusic);
  return localView(files, filter);
}

const byId = () => new Map(useLocalLibrary.getState().files.map((f) => [localTrackId(f.path), f]));

/**
 * The queue and Now Playing as saved last visit still hold that visit's
 * cover URLs, which died with it: they take this visit's.
 */
export function refreshQueueArtwork() {
  const { queue, nowPlaying } = usePlayer.getState();
  if (!queue.some((e) => isLocalId(e.track.videoId))) return;
  const files = byId();
  const fresh = (id: string) => {
    const file = files.get(id);
    return file ? trackOf(file).thumbnail : null;
  };
  usePlayer.setState({
    queue: queue.map((e) => (isLocalId(e.track.videoId) ? { ...e, track: { ...e.track, thumbnail: fresh(e.track.videoId) } } : e)),
    nowPlaying: nowPlaying && isLocalId(nowPlaying.videoId) ? { ...nowPlaying, artworkUrl: fresh(nowPlaying.videoId) } : nowPlaying,
  });
}

// ---- The folder ----------------------------------------------------------------------

function keepArt(art: Map<string, Blob>) {
  for (const [key, blob] of art) if (!artUrls.has(key)) artUrls.set(key, URL.createObjectURL(blob));
}

function forgetArt() {
  for (const url of artUrls.values()) URL.revokeObjectURL(url);
  artUrls.clear();
}

let scanning: Promise<void> | null = null;
let again = false;

/** Reads the folder again; a request during a scan runs once more after it. */
export function rescan(): Promise<void> {
  if (scanning) {
    again = true;
    return scanning;
  }
  scanning = (async () => {
    do {
      again = false;
      await scanOnce();
    } while (again);
  })().finally(() => {
    scanning = null;
  });
  return scanning;
}

async function scanOnce() {
  if (!root || useLocalLibrary.getState().access !== 'granted') return;
  const started = performance.now();
  const { files } = useLocalLibrary.getState();
  useLocalLibrary.setState({ scanning: 0, error: null });
  try {
    const result = await scanFolder(root as unknown as DirectoryEntry, {
      previous: new Map(files.map((f) => [f.path, f])),
      knownArt: new Set(artUrls.keys()),
      onProgress: (seen) => useLocalLibrary.setState({ scanning: seen }),
    });
    await saveScan(result);
    keepArt(result.art);
    useLocalLibrary.setState({ files: result.files });
    trackLog.i(`local music: ${result.files.length} files, ${result.read} read, in ${Math.round(performance.now() - started)} ms`);
  } catch (error) {
    // A folder deleted or moved away answers NotFoundError; the index stays as it was.
    useLocalLibrary.setState({ error: error instanceof Error ? error.message : String(error) });
  } finally {
    useLocalLibrary.setState({ scanning: null });
  }
}

let observer: { disconnect(): void } | null = null;
let observeTimer: ReturnType<typeof setTimeout> | undefined;

/** Rescans a moment after the folder changes, where the browser can watch it (FileSystemObserver, Chromium 129+). */
async function watch() {
  observer?.disconnect();
  observer = null;
  const Observer = (window as unknown as { FileSystemObserver?: new (cb: () => void) => { observe(h: unknown, o: object): Promise<void>; disconnect(): void } })
    .FileSystemObserver;
  if (!Observer || !root) return;
  const next = new Observer(() => {
    clearTimeout(observeTimer);
    observeTimer = setTimeout(() => void rescan(), 2_000);
  });
  try {
    await next.observe(root, { recursive: true });
    observer = next;
  } catch {
    // Not every folder can be watched (a network drive); Rescan and the next launch still read it.
  }
}

async function granted() {
  useLocalLibrary.setState({ access: 'granted' });
  await watch();
  await rescan();
}

let started: Promise<void> | null = null;

/** Reads the index and, if this visit may read the folder, rescans it. Once per visit; later calls wait for it. */
export function startLocalLibrary(): Promise<void> {
  started ??= (async () => {
    try {
      const [handle, index] = await Promise.all([readRoot(), readIndex()]);
      root = handle as PermissionHandle | null;
      keepArt(index.art);
      useLocalLibrary.setState({ folder: root?.name ?? null, files: index.files, loaded: true });
      useLocalSettings.getState().setFolder(root?.name ?? null);
      refreshQueueArtwork();
      if (!root) return;
      const access = await root.queryPermission({ mode: 'read' });
      if (access === 'granted') void granted();
      else useLocalLibrary.setState({ access });
    } catch (error) {
      useLocalLibrary.setState({ loaded: true, error: error instanceof Error ? error.message : String(error) });
    }
  })();
  return started;
}

/** Settings' Change, and Library's first Choose a folder. Read-only: nothing here ever writes to it. */
export async function chooseFolder(): Promise<void> {
  const picker = (window as unknown as { showDirectoryPicker?: (o: { id: string; mode: 'read' }) => Promise<FileSystemDirectoryHandle> }).showDirectoryPicker;
  if (!picker) return;
  let picked: FileSystemDirectoryHandle;
  try {
    picked = await picker({ id: 'bitchord-local-music', mode: 'read' });
  } catch (error) {
    if (!(error instanceof DOMException && error.name === 'AbortError')) {
      useLocalLibrary.setState({ error: error instanceof Error ? error.message : String(error) });
    }
    return;
  }
  await startLocalLibrary();
  await saveRoot(picked);
  forgetArt();
  root = picked as PermissionHandle;
  useLocalLibrary.setState({ folder: picked.name, files: [], error: null });
  useLocalSettings.getState().setFolder(picked.name);
  await granted();
}

/** Library's "Allow access to <folder>": must run from the click itself. */
export async function allowAccess(): Promise<void> {
  if (!root) return;
  const access = await root.requestPermission({ mode: 'read' }).catch(() => 'prompt' as const);
  if (access === 'granted') await granted();
  else useLocalLibrary.setState({ access });
}

/** Settings' Remove: forgets the folder and its index. The files themselves are never touched. */
export async function forgetFolder(): Promise<void> {
  observer?.disconnect();
  observer = null;
  await saveRoot(null);
  forgetArt();
  root = null;
  useLocalLibrary.setState({ folder: null, access: null, files: [], error: null });
  useLocalSettings.getState().setFolder(null);
}

// ---- Playing a file ------------------------------------------------------------------

/**
 * The file behind a `local:` track id, for the player to play from a blob
 * URL (YouTubeEngine's file mode, as a download plays). The File is a
 * reference to the disk, not a copy in memory. Asks for permission when it
 * isn't held: a Play click still counts as the gesture that needs.
 */
export async function localCopy(videoId: string): Promise<{ track: OfflineTrack; blob: Blob }> {
  await startLocalLibrary();
  const file = byId().get(videoId);
  if (!file || !root) throw new Error('This song isn’t in your Local Music folder any more.');
  if ((await root.queryPermission({ mode: 'read' })) !== 'granted') {
    const access = await root.requestPermission({ mode: 'read' }).catch(() => 'prompt' as const);
    if (access !== 'granted') throw new Error(`Allow access to ${root.name} in Library › Local music to play this.`);
    void granted();
  }
  const blob = await (await fileAt(root, file.path)).getFile();
  const track = trackOf(file);
  return {
    track: {
      videoId,
      title: track.title,
      author: track.artists[0]!.name,
      durationSec: track.durationSec ?? 0,
      thumbnail: track.thumbnail,
      mimeType: mimeOf(file.name),
      sizeBytes: blob.size,
      savedAt: file.lastModified,
    },
    blob,
  };
}
