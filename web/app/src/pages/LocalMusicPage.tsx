import { localAlbums, localArtists, localFolders, type LocalGroupRow } from '@bitchord/shared-kotlin';
import { Disc3, Folder, FolderLock, MicVocal } from 'lucide-react';
import { useEffect, useMemo } from 'react';
import { useSearchParams } from 'react-router';
import { PageHeader } from '@/components/PageHeader';
import { PrefetchLink } from '@/components/PrefetchLink';
import { Artwork } from '@/components/music/Artwork';
import { EmptyState, stateStyles } from '@/components/music/States';
import { TrackList } from '@/components/music/TrackList';
import { SegmentedControl } from '@/components/ui/SegmentedControl';
import { LOCAL_FOLDER_ID, localGroupHref } from '@/data/local/ids';
import { allowAccess, chooseFolder, startLocalLibrary, useLocalLibrary, useLocalView } from '@/data/local/library';
import { localMusicSupported } from '@/data/local/settings';
import { PlainSkeleton } from '@/skeletons/PageSkeletons';
import { seeAllTracks } from './pageStyles';

/** LocalFolder.kt LocalFolderTab, as Local Music lists them (no Playlists: a browser keeps none of its own). */
const TABS = [
  { value: 'songs', label: 'Songs' },
  { value: 'artists', label: 'Artists' },
  { value: 'albums', label: 'Albums' },
  { value: 'folders', label: 'Folders' },
] as const;
type Tab = (typeof TABS)[number]['value'];

/** The Library's Show all grid (LibraryPage.tsx libraryGrid). */
const grid =
  'mx-(--body-gutter) mt-5 mb-10 grid grid-cols-[repeat(auto-fill,minmax(min(max(140px,calc((100%_-_48px)/5)),calc((100%_-_12px)/2)),1fr))] gap-x-3 gap-y-5';

const songCount = (n: number) => `${n} ${n === 1 ? 'song' : 'songs'}`;

/** LocalFolder.kt `albumSubtitle`: "Artist · 12 songs"; a directory's says where it is instead. */
function groupSubtitle(group: LocalGroupRow): string {
  const count = songCount(group.songs.length);
  return group.subtitle && group.subtitle !== group.title ? `${group.subtitle} · ${count}` : count;
}

/**
 * One album, artist or directory as a card that opens its page. Not the
 * catalogue's lockups: their hover play and menus would ask YouTube for it.
 * An artist is round and has no picture of its own (LocalGroups.kt artistsOf).
 */
function GroupCard({ group }: { group: LocalGroupRow }) {
  const artist = group.kind === 'artist';
  const Placeholder = artist ? MicVocal : group.kind === 'folder' ? Folder : Disc3;
  return (
    <li>
      <PrefetchLink to={localGroupHref(group.browseId)} className={`group/card flex min-w-0 flex-col ${artist ? 'items-center text-center' : ''}`}>
        <div className="relative w-full">
          <Artwork src={group.thumbnailUrl ?? null} displayWidth={200} radius={artist ? '50%' : 'var(--radius-artwork-medium)'} />
          {!group.thumbnailUrl && (
            <Placeholder className="absolute top-1/2 left-1/2 size-1/3 -translate-1/2 text-on-surface-variant" strokeWidth={1.5} aria-hidden />
          )}
        </div>
        <span className="mt-2 max-w-full truncate type-callout text-primary group-hover/card:underline">{group.title}</span>
        <span className="max-w-full truncate type-callout text-secondary">{groupSubtitle(group)}</span>
      </PrefetchLink>
    </li>
  );
}

/** Choose a folder, before there is one: the page's whole content. */
function ChooseFolder() {
  const error = useLocalLibrary((s) => s.error);
  return (
    <div className={stateStyles.state}>
      <p className={stateStyles.title}>Play the music on this computer</p>
      <p className={stateStyles.detail}>
        Choose a folder and BitChord lists its songs here, with their albums, artists and covers. It only ever reads the folder; nothing leaves this
        device.
      </p>
      <button type="button" className={stateStyles.button} onClick={() => void chooseFolder()}>
        Choose a folder
      </button>
      {error && <p className={`${stateStyles.detail} mt-2`}>{error}</p>}
    </div>
  );
}

/** The one tap a browser needs before reading the folder again after a restart. */
function AllowAccess({ folder }: { folder: string }) {
  return (
    <button
      type="button"
      className="mx-(--body-gutter) mt-4 flex w-[calc(100%-2*var(--body-gutter))] items-center gap-3 rounded-row bg-surface-variant px-4 py-3 text-left hover:brightness-110"
      onClick={() => void allowAccess()}
    >
      <FolderLock className="size-6 flex-none text-primary" aria-hidden />
      <span className="min-w-0 flex-1">
        <span className="block truncate type-body text-primary">Allow access to {folder}</span>
        <span className="block type-callout text-secondary">Your browser asks again after a restart. The songs below play once it’s allowed.</span>
      </span>
    </button>
  );
}

/**
 * Library › Local Music (LocalFolder.kt, the "local:all" folder): the files
 * of the folder chosen in Settings, as Songs, Artists, Albums and Folders,
 * each grouping opening its own page (LocalGroupPage).
 */
export default function LocalMusicPage() {
  const [params, setParams] = useSearchParams();
  const tab: Tab = TABS.find((t) => t.value === params.get('tab'))?.value ?? 'songs';
  const { folder, access, loaded, scanning } = useLocalLibrary();
  const view = useLocalView();
  useEffect(() => void startLocalLibrary(), []);
  const groups = useMemo(() => {
    if (tab === 'albums') return localAlbums(LOCAL_FOLDER_ID, view.rows);
    if (tab === 'artists') return localArtists(LOCAL_FOLDER_ID, view.rows);
    if (tab === 'folders') return localFolders(LOCAL_FOLDER_ID, view.rows);
    return [];
  }, [tab, view.rows]);

  if (!localMusicSupported()) {
    return (
      <>
        <PageHeader title="Local Music" />
        <EmptyState title="This browser can’t open folders" detail="Local Music needs Chrome or Edge on a computer, or the BitChord app for Windows." />
      </>
    );
  }
  if (!loaded) return <PlainSkeleton />;
  return (
    <>
      <div className="flex flex-wrap items-end justify-between gap-x-6 gap-y-3 pe-(--body-gutter)">
        <PageHeader title="Local Music" />
        {folder && (
          <SegmentedControl label="Show" className="w-[360px] max-w-full ms-(--body-gutter)" options={TABS} value={tab} onChange={(next) => setParams(next === 'songs' ? {} : { tab: next }, { replace: true })} />
        )}
      </div>
      {!folder ? (
        <ChooseFolder />
      ) : (
        <>
          {access !== null && access !== 'granted' && <AllowAccess folder={folder} />}
          {scanning !== null && (
            <p className="mx-(--body-gutter) mt-3 type-callout text-secondary" aria-live="polite">
              Scanning {folder}… {scanning} files
            </p>
          )}
          {tab === 'songs' ? (
            view.tracks.length ? (
              <div className={seeAllTracks}>
                <TrackList tracks={view.tracks} variant="playlist" source={{ title: 'Local Music', type: 'library', id: 'local' }} />
              </div>
            ) : (
              scanning === null && <EmptyState title="No tracks here" detail={`No music found in ${folder}.`} />
            )
          ) : groups.length ? (
            <ul className={grid}>
              {groups.map((group) => (
                <GroupCard key={group.browseId} group={group} />
              ))}
            </ul>
          ) : (
            scanning === null && <EmptyState title={tab === 'folders' ? 'No folders with music in them' : 'No tracks here'} />
          )}
        </>
      )}
    </>
  );
}
