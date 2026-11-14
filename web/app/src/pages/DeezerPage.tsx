import { PageHeader } from '@/components/PageHeader';
import { SquareLockup } from '@/components/music/Lockups';
import { EmptyState, LoadError, NeedsExtension, stateStyles } from '@/components/music/States';
import { useDeezerPlaylists } from '@/data/deezer/queries';
import type { MusicItem } from '@/data/music/types';
import { openDeezerSignIn, useDeezerAuth, useRefreshDeezerAuth, useSetDeezerConnected } from '@/extension/useDeezerAuth';
import { useExtension } from '@/extension/useExtension';
import { PlainSkeleton } from '@/skeletons/PageSkeletons';
import { seeAllGrid } from './pageStyles';

/**
 * The signed-in Deezer account's playlists (DeezerLibraryScreen): this page
 * is only the way in; a playlist opens as an ordinary playlist page whose
 * songs are matched to YouTube Music.
 */
export default function DeezerPage() {
  const extension = useExtension();
  const auth = useDeezerAuth();
  const playlists = useDeezerPlaylists();
  const connect = useSetDeezerConnected();
  const retry = useRefreshDeezerAuth();

  if (extension.state === 'missing') return <NeedsExtension what="your Deezer playlists" />;
  if (auth.state === 'checking') return <PlainSkeleton />;
  if (auth.state === 'unavailable') {
    return (
      <>
        <PageHeader title="Deezer" />
        <EmptyState title="Update the extension" detail="This version of the BitChord extension doesn’t include Deezer yet." />
      </>
    );
  }
  const state = auth.auth;
  if (!state.signedIn) {
    const [title, detail, action] = !state.browserSignedIn
      ? ['Sign in to Deezer', 'BitChord uses the Deezer account this browser is signed in to, through the extension.', 'Sign In']
      : !state.connected
        ? ['Deezer is disconnected', 'You’re signed in to Deezer in this browser. Connect to see your playlists here.', 'Connect']
        : ['Couldn’t read your Deezer account', state.problem ?? 'Deezer didn’t answer.', 'Try Again'];
    return (
      <>
        <PageHeader title="Deezer" />
        <div className={stateStyles.state}>
          <p className={stateStyles.title}>{title}</p>
          <p className={stateStyles.detail}>{detail}</p>
          <button
            className={stateStyles.button}
            disabled={connect.isPending || retry.isPending}
            onClick={() =>
              !state.browserSignedIn ? openDeezerSignIn() : !state.connected ? connect.mutate(true) : retry.mutate()
            }
          >
            {action}
          </button>
        </div>
      </>
    );
  }
  if (playlists.isError && !playlists.data) return <LoadError error={playlists.error} onRetry={() => void playlists.refetch()} />;
  if (!playlists.data) return <PlainSkeleton />;

  const items: MusicItem[] = playlists.data.map((p) => ({
    kind: 'deezer-playlist',
    id: p.id,
    title: p.name,
    subtitle: p.owner ?? 'Deezer',
    artists: [],
    album: null,
    thumbnail: p.imageUrl,
    wide: false,
    durationSec: null,
    explicit: false,
  }));
  return (
    <>
      <PageHeader title="Deezer" />
      {!items.length && <EmptyState title="No playlists yet" />}
      <ul className={seeAllGrid}>
        {items.map((item) => (
          <li key={item.id}>
            <SquareLockup item={item} />
          </li>
        ))}
      </ul>
    </>
  );
}
