import { ChevronDown, Maximize, Minimize } from 'lucide-react';
import { useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { useLocation } from 'react-router';
import { usePlayer } from '@/state/player';
import { useUi } from '@/state/ui';
import { useHasVideo } from '@/state/video';
import { LyricsPanel } from './LyricsPanel';
import { MeshBackdrop } from './MeshBackdrop';
import { UpNext } from './UpNext';
import { ActionRow, type Pane } from './player/ActionRow';
import { ArtworkBlurBackdrop } from './player/ArtworkBlurBackdrop';
import { MainPane } from './player/MainPane';
import { Panes } from './player/Panes';
import { PlayerCredits } from './player/PlayerCredits';
import { Scrubber } from './player/Scrubber';
import { Sleeve } from './player/Sleeve';
import { Transport } from './player/Transport';
import { useSheet } from './player/useSheet';
import { VolumeRow } from './player/VolumeRow';
import { keyBelongsToFocus } from './useKeyboardShortcuts';

function useBrowserFullScreen(): boolean {
  return useSyncExternalStore(
    (onChange) => {
      document.addEventListener('fullscreenchange', onChange);
      return () => document.removeEventListener('fullscreenchange', onChange);
    },
    () => document.fullscreenElement !== null,
  );
}

function useWindowSize() {
  const [size, setSize] = useState(() => ({ width: window.innerWidth, height: window.innerHeight }));
  useEffect(() => {
    const onResize = () => setSize({ width: window.innerWidth, height: window.innerHeight });
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);
  return size;
}

/** NowPlayingScreen.kt LANDSCAPE_PLAYER_MIN_WIDTH: a window wider than tall and this wide gets the two columns. */
const LANDSCAPE_MIN_WIDTH = 560;
/** LandscapePlayer.kt LANDSCAPE_COMPACT_HEIGHT: shorter than this, the columns tighten. */
const COMPACT_HEIGHT = 440;
/** ArtworkMeshBackdrop.kt: from 700dp wide the backdrop is the still blurred cover; narrower, the mesh. */
const BLUR_MIN_WIDTH = 700;
/** The sleeve's size while lyrics or the queue fill the right column, leaving room for the controls under it. */
const DOCKED_SLEEVE = 'min(320px, 38vh)';
/** How tall a window has to be for the volume bar to join the docked controls. */
const DOCKED_VOLUME_MIN_HEIGHT = 720;

/** What a press on the sheet leaves alone: controls, and the lyrics and queue, which scroll. */
const NOT_A_SHEET_DRAG = 'button, a, input, select, [role="slider"], [role="menu"], [data-no-sheet-drag]';

/**
 * The full-screen player, mounted from its first open: it keeps itself on
 * screen while it slides away, then goes (useSheet). AppShell renders it
 * once the player has been opened.
 */
export function FullScreenPlayer() {
  const open = useUi((s) => s.fullScreen);
  const hasTrack = usePlayer((s) => s.nowPlaying !== null);
  const [shown, setShown] = useState(open);
  if (open && !shown) setShown(true);
  const setFullScreen = useUi((s) => s.setFullScreen);
  // Nothing left to show once the queue ends.
  useEffect(() => {
    if (!hasTrack && open) setFullScreen(false);
  }, [hasTrack, open, setFullScreen]);
  if (!shown || !hasTrack) return null;
  return <PlayerSheet open={open} onClosed={() => setShown(false)} />;
}

/**
 * The phone's player in a landscape window, ported to the desktop
 * (sharedUi/.../ui/player/LandscapePlayer.kt): two columns of equal width,
 * at most 1100px between them. The left one is the same in every state, the
 * sleeve and under it the lyrics / Listen Together / queue row; the right one
 * is whichever of the three it shows, the credits and transport, the lyrics or
 * the queue, the page turned by a crossfade. A window taller than wide, or
 * narrower than 560px, gets one column instead, and the sleeve collapses to
 * a 54px thumbnail heading the lyrics or the queue.
 *
 * Where the phone hides the transport behind the lyrics and the queue, a
 * desktop keeps it: with either open, the sleeve shrinks in the left column
 * and the names, the seek bar and the transport (and the volume, on a tall
 * enough window) dock under it; one column docks the seek bar and transport
 * above the action row.
 *
 * It is a sheet over the app: it slides up from the mini player with the
 * artwork flying in from its cover, and closes by its handle, the chevron at
 * the top left, a drag down, or Esc, the same way back. L and Q turn to the
 * lyrics and the queue.
 */
function PlayerSheet({ open, onClosed }: { open: boolean; onClosed: () => void }) {
  const nowPlaying = usePlayer((s) => s.nowPlaying)!;
  // The queue's row for what's playing: it carries the album and each artist's channel.
  const track = usePlayer((s) => {
    const entry = s.queue[s.index]?.track;
    return entry && entry.videoId === s.nowPlaying?.videoId ? entry : null;
  });
  const playing = usePlayer((s) => s.status === 'playing' || s.status === 'buffering');
  const setFullScreen = useUi((s) => s.setFullScreen);
  const video = useHasVideo(nowPlaying.videoId);
  const { pathname } = useLocation();
  const openedAt = useRef(pathname);
  const [pane, setPane] = useState<Pane>('main');
  const { width, height } = useWindowSize();
  const landscape = width > height && width >= LANDSCAPE_MIN_WIDTH;
  const compact = height < COMPACT_HEIGHT;
  const gutter = compact ? 20 : 30;
  const browserFullScreen = useBrowserFullScreen();
  // Whether this player put the page in the browser's full screen, so closing it leaves it again.
  const enteredBrowserFullScreen = useRef(false);
  const handle = useRef<HTMLButtonElement>(null);
  const sheet = useRef<HTMLDivElement>(null);
  const scrim = useRef<HTMLDivElement>(null);
  const dock = useRef<HTMLDivElement>(null);
  const close = () => setFullScreen(false);
  const drag = useSheet(open, close, onClosed, { sheet, scrim, dock });

  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null;
    handle.current?.focus({ preventScroll: true });
    return () => {
      if (enteredBrowserFullScreen.current && document.fullscreenElement) void document.exitFullscreen();
      opener?.focus({ preventScroll: true });
    };
  }, []);

  // L and Q, which open the side panel when the player isn't up, turn its own pages here.
  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.defaultPrevented || event.metaKey || event.ctrlKey || event.altKey || keyBelongsToFocus(event.target)) return;
      const to = ({ l: 'lyrics', q: 'queue' } as const)[event.key.toLowerCase() as 'l' | 'q'];
      if (!to) return;
      event.preventDefault();
      setPane((p) => (p === to ? 'main' : to));
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open]);

  // A title, artist or album link went somewhere: get out of the way so the page shows.
  useEffect(() => {
    if (pathname !== openedAt.current) setFullScreen(false);
  }, [pathname, setFullScreen]);

  const toggleBrowserFullScreen = () => {
    if (document.fullscreenElement) {
      void document.exitFullscreen();
    } else {
      // The whole page, not this overlay: menus and dialogs portal to <body> and must stay visible.
      void document.documentElement.requestFullscreen().then(
        () => (enteredBrowserFullScreen.current = true),
        () => undefined,
      );
    }
  };

  const thumb = !landscape && pane !== 'main';
  const docked = landscape && pane !== 'main';
  const sleeve = (
    <Sleeve
      nowPlaying={nowPlaying}
      album={track?.album?.name ?? null}
      playing={playing}
      video={video}
      thumb={thumb}
      docked={docked}
      dock={dock}
      // Clicking the art puts the player back, as on the phone.
      onClick={pane === 'main' ? undefined : () => setPane('main')}
    />
  );

  const panes = (
    <Panes
      pane={pane}
      className={(p) =>
        p === 'main'
          ? `flex min-h-0 ${landscape ? 'items-center justify-center' : 'items-end'}`
          : `flex min-h-0 flex-col ${landscape ? 'px-[30px]' : ''}`
      }
      render={(p) =>
        p === 'main' ? (
          <div className="flex w-full justify-center" style={{ paddingInline: landscape ? gutter : 0 }}>
            <MainPane nowPlaying={nowPlaying} track={track} compact={compact} onOpenLyrics={() => setPane('lyrics')} />
          </div>
        ) : p === 'lyrics' ? (
          <section className="flex min-h-0 flex-1 flex-col" aria-label="Lyrics" data-no-sheet-drag>
            <LyricsPanel variant="full" />
          </section>
        ) : (
          <section className="relative flex min-h-0 flex-1 flex-col" aria-label="Queue" data-no-sheet-drag>
            <UpNext variant="player" />
          </section>
        )
      }
    />
  );

  // The transport kept in view while lyrics or the queue are open; in one column the thumbnail heading the pane already names the song.
  const dockedControls = pane !== 'main' && (
    <div
      className={`flex w-full flex-none flex-col animate-[fade-in_280ms_ease-out] motion-reduce:animate-none ${landscape ? 'mb-auto max-w-[360px] pt-5' : 'pt-3'}`}
    >
      {landscape && (
        <>
          <PlayerCredits nowPlaying={nowPlaying} track={track} />
          <div className="h-2.5" />
        </>
      )}
      <Scrubber />
      <Transport compact />
      {landscape && height >= DOCKED_VOLUME_MIN_HEIGHT && (
        <div className="pt-2">
          <VolumeRow />
        </div>
      )}
    </div>
  );

  return (
    <>
      {/* The dim behind the sheet, as deep as it is open (PLAYER_SCRIM_ALPHA). */}
      <div ref={scrim} className="pointer-events-none fixed inset-0 z-[calc(var(--z-contextual-menus)-3)] bg-black opacity-0" aria-hidden="true" />
      <div
        ref={sheet}
        // Over everything but menus and dialogs, which portal above it. The on-dark colours come from tokens.css (data-full-screen).
        className="group/player fixed inset-0 z-[calc(var(--z-contextual-menus)-2)] isolate text-white will-change-transform"
        role="dialog"
        aria-modal="true"
        aria-label="Now Playing"
        data-full-screen
        data-player
        data-paused={!playing || undefined}
        onPointerDown={(e) => {
          if (!(e.target as Element).closest(NOT_A_SHEET_DRAG)) drag.onPointerDown(e);
        }}
      >
        {width >= BLUR_MIN_WIDTH ? (
          <ArtworkBlurBackdrop artworkUrl={nowPlaying.artworkUrl} />
        ) : (
          // A narrow window is the phone's portrait shape, which hangs a mesh off the sleeve (ArtworkMesh, scrim .06 → .30).
          <div className="absolute inset-0 -z-1 overflow-hidden">
            <MeshBackdrop artworkUrl={nowPlaying.artworkUrl} playing={playing} />
            <div className="absolute inset-0 bg-[linear-gradient(rgb(0_0_0/0.06),rgb(0_0_0/0.3))]" />
          </div>
        )}

        {/* The handle (LandscapePlayer.kt): a 38×5 bar, white at 70%, 8px down. Click it, drag it down, or press Esc. */}
        <button
          ref={handle}
          className="absolute top-0 left-1/2 z-1 grid h-6 w-16 -translate-x-1/2 cursor-grab place-items-start justify-center pt-2 active:cursor-grabbing"
          aria-label="Close"
          onPointerDown={drag.onPointerDown}
          onClick={close}
        >
          <span className="block h-[5px] w-[38px] rounded-[3px] bg-white/70 shadow-[0_1px_2px_rgb(0_0_0/0.3)]" />
        </button>
        {/* A plain way out for a mouse, beside the handle's drag. */}
        <button
          className="absolute top-2 left-3 z-1 grid size-8 place-items-center rounded-full text-white/60 transition-colors hover:bg-white/10 hover:text-white"
          aria-label="Close player"
          title="Close (Esc)"
          onClick={close}
        >
          <ChevronDown size={20} />
        </button>
        {document.fullscreenEnabled && (
          <button
            className="absolute top-2 right-3 z-1 grid size-8 place-items-center rounded-full text-white/60 transition-colors hover:bg-white/10 hover:text-white"
            aria-label={browserFullScreen ? 'Exit full screen' : 'Enter full screen'}
            onClick={toggleBrowserFullScreen}
          >
            {browserFullScreen ? <Minimize size={16} /> : <Maximize size={16} />}
          </button>
        )}

        <div className="absolute inset-0 flex justify-center pt-6" style={{ paddingBottom: compact ? 8 : 20 }}>
          <div
            className={landscape ? 'grid w-full max-w-[1100px] grid-cols-2 grid-rows-1' : 'flex w-full max-w-[560px] flex-col'}
            style={landscape ? undefined : { paddingInline: gutter }}
          >
            {/* The left column; in one column its parts flow straight into it, the open page between them. */}
            <div className={landscape ? 'flex min-h-0 flex-col items-center' : 'contents'} style={landscape ? { paddingInline: gutter } : undefined}>
              <div
                className={
                  thumb
                    ? 'flex h-[60px] flex-none items-center gap-3'
                    : `flex min-h-0 w-full flex-1 items-center justify-center [container-type:size] ${docked ? 'mt-auto' : ''}`
                }
                // Docked, it stops at its own size and the room left over sits above it and below the controls.
                style={docked ? { maxHeight: DOCKED_SLEEVE } : undefined}
              >
                {sleeve}
                {thumb && (
                  <div className="flex min-w-0 flex-col animate-[fade-in_280ms_ease-out] motion-reduce:animate-none">
                    <span className="truncate type-body-emphasized text-white">{nowPlaying.title}</span>
                    <span className="truncate type-callout text-white/55">{nowPlaying.artist}</span>
                  </div>
                )}
              </div>
              {!landscape && <div className={`flex min-h-0 flex-col ${pane === 'main' ? 'pt-4' : 'flex-1 pt-3'}`}>{panes}</div>}
              {dockedControls}
              <div className="flex-none" style={{ height: compact ? 12 : 24 }} />
              <ActionRow pane={pane} onPane={setPane} />
            </div>
            {landscape && panes}
          </div>
        </div>
      </div>
    </>
  );
}
