// Registers <am-lyrics>.
import '@uimaxbai/am-lyrics/am-lyrics.js';
import { Check, ChevronDown, Globe, Languages, Loader2, Minus, Settings2, Share } from 'lucide-react';
import { lazy, Suspense, useCallback, useEffect, useRef, useState, useSyncExternalStore, type ReactNode } from 'react';
import { LyricClock } from '@bitchord/shared-kotlin';
import { useCanvas } from '@/data/spotify/canvas';
import { getBridge } from '@/extension/bridge';
import { Menu, MenuHeader, MenuItem, MenuSeparator } from '@/components/Menu';
import { onUngrantedChange, ungrantedOrigins } from '@/lyrics/http';
import type { LyricLine } from '@/lyrics/model';
import { adjustedLyricsPosition, adjustedLyricsSeekTarget, useLyricsOffset } from '@/lyrics/offset';
import type { SourceState } from '@/lyrics/repository';
import { enabledSources } from '@/lyrics/settings';
import { sourceById, type LyricsSourceId } from '@/lyrics/sources';
import { useLyrics, useLyricsUi } from '@/lyrics/useLyrics';
import { getAudio, getPlayhead, usePlayer } from '@/state/player';
import { LyricsSettings } from './LyricsSettings';

type AmLyricsElement = HTMLElement & {
  currentTime: number;
  ttml?: string;
  interpolate: boolean;
  autoScroll: boolean;
  highlightColor: string;
  fontFamily?: string;
  updateComplete?: Promise<unknown>;
};

/**
 * am-lyrics' own footer offers to fetch from its own providers; ours decide
 * (prompt §10.2). It stays in the layout, only unseen: am-lyrics measures its
 * scroll range to the footer, and `display: none` collapsed that range to zero,
 * so the lyrics never scrolled.
 */
const HIDE_FOOTER = '.lyrics-footer { visibility: hidden !important; pointer-events: none !important; }';

/** The phone's lyric typeface (@font-face in styles/global.css), then the app's. */
const LYRIC_FONT = "'BitChord SF Pro Display', 'DM Sans', system-ui, sans-serif";

let lyricFont: Promise<void> | null = null;
let lyricFontLoaded = false;

/**
 * Both weights, fetched once. am-lyrics measures its lines for the scroll and
 * the wipe, so it's only built once the face is in: built earlier, it laid
 * out in the fallback and then jumped when the font arrived. A slow network
 * gives up after 1.5 s and lets the fallback show.
 */
function loadLyricFont(): Promise<void> {
  lyricFont ??= Promise.race([
    Promise.all([document.fonts.load(`700 28px ${LYRIC_FONT}`), document.fonts.load(`400 28px ${LYRIC_FONT}`)]),
    new Promise((resolve) => setTimeout(resolve, 1500)),
  ])
    .catch(() => undefined)
    .then(() => {
      lyricFontLoaded = true;
    });
  return lyricFont;
}

function useLyricFont(): boolean {
  const [ready, setReady] = useState(lyricFontLoaded);
  useEffect(() => {
    if (!ready) void loadLyricFont().then(() => setReady(true));
  }, [ready]);
  return ready;
}

/**
 * am-lyrics fades the four lines either side of the current one (0.7, 0.58,
 * 0.46) and leaves every line past them at its base 0.8, so the fifth line on
 * came back brighter. Those stay at the farthest step. Gaps keep their own
 * opacity, and the library's !important rules (scrolling by hand, unsynced
 * lyrics) still win.
 */
const FAR_LINES =
  '.lyrics-line:not(.active):not(.pre-active):not(.lyrics-activest):not(.post-active-line):not(.next-active-line)' +
  ':not(.prev-2):not(.prev-3):not(.next-2):not(.next-3):not(.lyrics-gap) { opacity: 0.46; }';

/**
 * A translation reads in its own direction, as the phone sets it from its own
 * text: am-lyrics gives it the line's, so an English translation of a Hebrew
 * line came out right to left, its punctuation at the wrong end. It still
 * aligns with its line (am-lyrics' physical left/right).
 */
const TRANSLATION_DIRECTION = '.lyrics-translation-container { unicode-bidi: plaintext; }';

/**
 * The full-screen player's sizing (report §7): its line size, set on the host
 * by breakpoint, replaces all three of am-lyrics' container tiers, and the
 * line being sung sits in the middle of the column, as the phone centres it
 * (PlayerLyrics.kt: half the viewport above the first line); am-lyrics places
 * a line's top, so a little above half, and the first line starts there too
 * rather than at the top of the column. Scoped to `data-full`, so the side
 * panel keeps the library's own sizes.
 */
const FULL_SIZE =
  ':host([data-full]) .lyrics-container { --lyplus-font-size-base: var(--bitchord-lyric-size, 34px);' +
  ' --am-lyrics-line-height: 1.2; --am-lyrics-line-spacing: calc(var(--bitchord-lyric-size, 34px) * 0.7);' +
  ' --lyrics-scroll-padding-top: 42%; --am-lyrics-inline-padding: 0px; padding-block-start: 38vh; }';

/**
 * The phone's falloff (PlayerLyrics.kt LINE_FALLOFF_ALPHA, LINE_FALLOFF_BLUR,
 * UNSUNG_ALPHA) in the full-screen column. am-lyrics already dims by distance
 * as the phone does (1, .8, .7, .58, .46) and sets lines back to 98%; this
 * gives its blur the phone's steps, 0, 1, 1, 1.7 and 2.4px, and holds the
 * words not yet sung at 45%. The handover keeps am-lyrics' own curve, which
 * is the phone's (.41, 0, .12, .99) over 400ms with a 350–500ms lead. Its
 * romanize and translate buttons move to the bar under the column, so its
 * own header goes.
 */
const blurred = (lines: string, px: number) =>
  `${lines
    .split(',')
    .map((l) => `:host([data-full]) .lyrics-line${l}:not(.lyrics-gap):not(.active):not(.pre-active)`)
    .join(',')} { filter: blur(${px}px); }`;
const FULL_FALLOFF =
  ':host([data-full]) { --lyplus-text-secondary: color-mix(in srgb, var(--lyplus-lyrics-palette), transparent 55%); --am-lyrics-inactive-scale: 0.98; }' +
  ':host([data-full]) .lyrics-header { display: none; }' +
  blurred('.post-active-line,.next-active-line,.prev-2,.next-2', 1) +
  blurred('.prev-3,.next-3', 1.7) +
  blurred(':not(.post-active-line):not(.next-active-line):not(.prev-2):not(.next-2):not(.prev-3):not(.next-3)', 2.4);

/**
 * The side panel keeps am-lyrics' own header: romanize, translate, and a
 * download format picker that only says "Auto". A word before the picker says
 * what it is for, and its tooltip says what Auto picks (am-lyrics'
 * downloadLyrics: TTML when any line is synced by word, else LRC).
 */
const HEADER_LABEL =
  ".download-controls::before { content: 'Download'; margin-inline-end: 4px; font-size: 11px; color: color-mix(in srgb, var(--lyplus-text-primary) 55%, transparent); }";
const FORMAT_TITLE = 'Download format. Auto saves TTML when the lyrics are synced by word, LRC when by line.';
function labelHeader(el: AmLyricsElement) {
  const select = el.shadowRoot?.querySelector<HTMLSelectElement>('.format-select');
  if (select) select.title = FORMAT_TITLE;
}

/**
 * am-lyrics, exactly as it is, fed our TTML and a steered clock (prompt §10.2).
 * The clock reads the media time each frame, stamps the reading with when it
 * was taken, and is told about every seek and track change: the "lyric clock
 * dates readings by sample time" lesson. No React state per frame: the time is
 * written straight onto the element.
 */
function LyricsView({ ttml, full, onElement }: { ttml: string; full: boolean; onElement?: (el: AmLyricsElement | null) => void }) {
  const host = useRef<HTMLDivElement>(null);
  const element = useRef<AmLyricsElement | null>(null);
  const deck = usePlayer((s) => s.deck);
  const mixing = usePlayer((s) => s.mixing);
  const videoId = usePlayer((s) => s.nowPlaying?.videoId);
  const offsetMs = useLyricsOffset((s) => s.offsetMs);

  // Created by hand, with its TTML set before it's attached: am-lyrics starts
  // loading the moment it connects, and through React the properties arrive
  // a beat later, so its first load went to its own providers with no title
  // and finished after ours, wiping it.
  useEffect(() => {
    const el = document.createElement('am-lyrics') as AmLyricsElement;
    el.ttml = ttml;
    el.interpolate = true;
    el.autoScroll = true;
    el.highlightColor = '#ffffff';
    el.fontFamily = LYRIC_FONT;
    el.className = 'block min-h-0 flex-1 overflow-hidden [--am-lyrics-highlight-color:#fff]';
    el.toggleAttribute('data-full', full);
    const onLineClick = (event: Event) => {
      const timestamp = (event as CustomEvent<{ timestamp: number }>).detail?.timestamp;
      // The line shows where the offset puts it, so that is where a tap lands (NowPlayingScreen.kt adjustedLyricsSeekTarget).
      if (typeof timestamp === 'number') usePlayer.getState().seek(adjustedLyricsSeekTarget(timestamp, useLyricsOffset.getState().offsetMs) / 1000);
    };
    el.addEventListener('line-click', onLineClick);
    host.current?.append(el);
    element.current = el;
    void Promise.resolve(el.updateComplete).then(() => {
      const root = el.shadowRoot;
      if (!root) return;
      const sheet = new CSSStyleSheet();
      sheet.replaceSync(HIDE_FOOTER + FAR_LINES + TRANSLATION_DIRECTION + FULL_SIZE + FULL_FALLOFF + HEADER_LABEL);
      root.adoptedStyleSheets = [...root.adoptedStyleSheets, sheet];
      labelHeader(el);
      onElement?.(el);
    });
    return () => {
      onElement?.(null);
      el.removeEventListener('line-click', onLineClick);
      el.remove();
      element.current = null;
    };
    // Mounted once; a new provider's TTML is handed over below.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const el = element.current;
    if (!el || el.ttml === ttml) return;
    el.ttml = ttml;
    // The header is drawn again with the new lyrics.
    void Promise.resolve(el.updateComplete).then(() => labelHeader(el));
  }, [ttml]);

  useEffect(() => {
    const audio = getAudio();
    const el = element.current;
    if (!audio || !el) return;
    // The clock reads the shifted time (PlayerLyrics.kt LyricPositionSource); a new
    // offset restarts it below, as the phone counts a change as a discontinuity.
    const at = (sec: number) => adjustedLyricsPosition(Math.trunc(sec * 1000), offsetMs);
    if (mixing) {
      // A loop mix plays the song from a deck while its element sits paused:
      // follow the mix's playhead, which loops and moves at the ramped tempo.
      // A loop's wrap (or the handover) is a jump on purpose, not drift to steer out.
      const start = getPlayhead()?.sec ?? audio.currentTime;
      const clock = new LyricClock(at(start));
      let discontinuity = 0;
      let last = start;
      let frame = 0;
      const tick = () => {
        const now = performance.now();
        const sec = getPlayhead()?.sec ?? last;
        if (sec < last - 0.15 || sec > last + 1) discontinuity++;
        last = sec;
        el.currentTime = clock.frame(now, at(sec), now, discontinuity, true);
        frame = requestAnimationFrame(tick);
      };
      frame = requestAnimationFrame(tick);
      return () => cancelAnimationFrame(frame);
    }
    const clock = new LyricClock(at(audio.currentTime));
    let discontinuity = 0;
    let frame = 0;
    const tick = () => {
      const now = performance.now();
      // The reading and its stamp are taken together.
      el.currentTime = clock.frame(now, at(audio.currentTime), now, discontinuity, !audio.paused);
      frame = requestAnimationFrame(tick);
    };
    // Paused, the sweep stays where it was drawn rather than snapping to the
    // media time (which a running clock is a frame or two ahead of), and the
    // resume carries on from there; a seek while paused still lands.
    const hold = () => {
      cancelAnimationFrame(frame);
      const now = performance.now();
      el.currentTime = clock.observe(now, at(audio.currentTime), now, discontinuity, false) ?? clock.displayedMs;
    };
    const resume = () => {
      cancelAnimationFrame(frame);
      clock.restart();
      frame = requestAnimationFrame(tick);
    };
    const onSeeking = () => {
      discontinuity++;
      if (audio.paused) hold();
    };
    audio.addEventListener('seeking', onSeeking);
    audio.addEventListener('seeked', onSeeking);
    audio.addEventListener('pause', hold);
    audio.addEventListener('play', resume);
    if (audio.paused) hold();
    else resume();
    return () => {
      cancelAnimationFrame(frame);
      audio.removeEventListener('seeking', onSeeking);
      audio.removeEventListener('seeked', onSeeking);
      audio.removeEventListener('pause', hold);
      audio.removeEventListener('play', resume);
    };
  }, [deck, videoId, ttml, mixing, offsetMs]);

  // am-lyrics renders and scrolls itself; it only needs the space. Full screen fades it out at the top and bottom.
  return (
    <div
      ref={host}
      className={full ? 'flex min-h-0 flex-1 [mask-image:linear-gradient(180deg,transparent,#000_14%,#000_86%,transparent)]' : 'flex min-h-0 flex-1'}
    />
  );
}

function useUngranted(): string[] {
  return useSyncExternalStore(onUngrantedChange, () => [...ungrantedOrigins].join(','), () => '')
    .split(',')
    .filter(Boolean);
}

const NO_STATES: Partial<Record<LyricsSourceId, SourceState>> = {};

const headerButton = 'grid size-7 place-items-center rounded-[8px] text-secondary hover:bg-quinary';
const PICKER_TITLE = 'Choose where the lyrics come from';

// The share sheet (its card renderer and recorder) loads the first time it opens.
const ShareLyricsSheet = lazy(() => import('@/lyrics/share/ShareLyricsSheet'));

/** The provider picker: which provider these lyrics are from, and the others' answers for this track. */
/**
 * Romanize or translate, as the phone's round toggles under the lyrics
 * (PlayerLyrics.kt RomanizationToggleButton, TranslationToggleButton): 34px,
 * white at 18% (34% while on), a 19px glyph at 78% (white while on). They
 * press am-lyrics' own buttons, hidden in its header, and read back the
 * state those report.
 */
function AmToggle({ element, which, label, icon }: { element: AmLyricsElement | null; which: 'romanization' | 'translation'; label: string; icon: ReactNode }) {
  const [on, setOn] = useState(false);
  const target = () => element?.shadowRoot?.querySelector<HTMLButtonElement>(`button[aria-label="Toggle ${which}"]`) ?? null;
  const read = () => setOn(target()?.getAttribute('aria-pressed') === 'true');
  useEffect(read, [element]); // eslint-disable-line react-hooks/exhaustive-deps
  return (
    <button
      title={label}
      className="grid size-[34px] place-items-center rounded-full bg-white/18 text-white/78 transition-colors duration-200 hover:bg-white/24 aria-pressed:bg-white/34 aria-pressed:text-white disabled:text-white/42"
      aria-label={label}
      aria-pressed={on}
      disabled={!element}
      onClick={() => {
        target()?.click();
        void Promise.resolve(element?.updateComplete).then(read);
      }}
    >
      {icon}
    </button>
  );
}

function ProviderPicker({
  current,
  full,
  lines,
  element = null,
}: {
  current: LyricsSourceId | null;
  full: boolean;
  lines: LyricLine[] | null;
  /** The lyrics' am-lyrics, whose romanize and translate the full-screen bar drives. */
  element?: AmLyricsElement | null;
}) {
  const videoId = usePlayer((s) => s.nowPlaying?.videoId ?? '');
  const states = useLyricsUi((s) => s.states[videoId]) ?? NO_STATES;
  const picked = useLyricsUi((s) => s.picked[videoId]);
  const pick = useLyricsUi((s) => s.pick);
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const [settings, setSettings] = useState(false);
  const [sharing, setSharing] = useState(false);
  const close = useCallback(() => setAnchor(null), []);
  const closeSharing = useCallback(() => setSharing(false), []);
  // In the side panel the provider is the title's second line; with none yet, the picker still names itself.
  const label = current ? sourceById(current)?.label : full ? 'Lyrics' : 'Choose provider';

  const picker = (
    <button
      className={
        full
          ? 'inline-flex min-w-0 items-center gap-1 rounded-pill px-2 py-1 type-label-medium text-white/55 hover:bg-white/10 hover:text-white/80'
          : '-ms-1.5 inline-flex max-w-full items-center gap-0.5 self-start rounded-[6px] px-1.5 py-0.5 type-footnote text-secondary hover:bg-quinary'
      }
      title={PICKER_TITLE}
      aria-label={current ? `Lyrics provider: ${label}` : 'Lyrics provider'}
      aria-haspopup="menu"
      onClick={(e) => setAnchor(e.currentTarget)}
    >
      <span className="truncate">{label}</span>
      <ChevronDown size={14} className="flex-none" />
    </button>
  );
  const actions = (
    <span className="flex gap-0.5">
      {lines && (
        <button className={headerButton} aria-label="Share lyrics" title="Share lyrics" onClick={() => setSharing(true)}>
          <Share size={16} />
        </button>
      )}
      <button className={headerButton} aria-label="Lyric settings" title="Lyric settings" onClick={() => setSettings(true)}>
        <Settings2 size={16} />
      </button>
    </span>
  );

  return (
    <div
      className={
        full
          ? // LandscapeLyricsPane's bar under the column: romanize and translate in fixed 34px slots either side, so the provider stays centred.
            'grid grid-cols-[34px_minmax(0,1fr)_34px] items-center gap-3 pt-2.5'
          : // The panel's title, as the queue's "Queue" heads its panel, with the provider under it.
            'flex items-start justify-between gap-2 px-5 pt-[23px] pb-1'
      }
    >
      {full ? (
        <>
          <span>{lines && <AmToggle element={element} which="romanization" label="Romanize lyrics" icon={<Globe size={19} />} />}</span>
          <span className="flex min-w-0 items-center justify-center gap-1">
            {picker}
            {lines && (
              <button className="grid size-7 flex-none place-items-center rounded-full text-white/55 hover:bg-white/10 hover:text-white/80" aria-label="Share lyrics" title="Share lyrics" onClick={() => setSharing(true)}>
                <Share size={15} />
              </button>
            )}
          </span>
          <span>{lines && <AmToggle element={element} which="translation" label="Translate lyrics" icon={<Languages size={19} />} />}</span>
        </>
      ) : (
        <>
          <div className="flex min-w-0 flex-1 flex-col">
            <h3 className="truncate type-title-large text-primary">Lyrics</h3>
            {picker}
          </div>
          {actions}
        </>
      )}
      {anchor && (
        <Menu anchor={anchor} placement="below-end" onClose={close} label="Lyrics provider">
          <MenuItem checked={!picked} onSelect={() => (close(), pick(videoId, null))}>
            Best available
          </MenuItem>
          <MenuSeparator />
          <MenuHeader>Providers</MenuHeader>
          {enabledSources().map((id) => {
            const state = states[id];
            return (
              <MenuItem
                key={id}
                checked={picked === id}
                trailing={
                  state === 'pending' ? (
                    <Loader2 size={13} className="animate-spin" />
                  ) : state === 'found' ? (
                    <Check size={13} />
                  ) : state === 'missing' ? (
                    <Minus size={13} />
                  ) : undefined
                }
                onSelect={() => (close(), pick(videoId, id))}
              >
                {sourceById(id)?.label}
              </MenuItem>
            );
          })}
          <MenuSeparator />
          <MenuItem onSelect={() => (close(), setSettings(true))}>Lyric Settings…</MenuItem>
        </Menu>
      )}
      {settings && <LyricsSettings onClose={() => setSettings(false)} />}
      {sharing && lines && (
        <Suspense fallback={null}>
          <ShareLyricsSheet lyrics={lines} onClose={closeSharing} />
        </Suspense>
      )}
    </div>
  );
}

function PermissionNotice({ origins }: { origins: string[] }) {
  const names = origins.map((o) => new URL(o).host.replace(/^www\./, ''));
  return (
    <div className="mx-4 my-1.5 flex items-center gap-2.5 rounded-[10px] bg-quinary px-2.5 py-2 type-callout text-secondary">
      <span>
        {names.slice(0, 2).join(', ')}
        {names.length > 2 ? ` and ${names.length - 2} more` : ''} need the extension’s permission.
      </span>
      <button
        className="flex-none type-callout-emphasized text-key"
        // The notice stays until the extension confirms the grant (checked when this tab comes back).
        onClick={() => void getBridge()?.call('lyrics.requestPermissions', { origins })}
      >
        Allow
      </button>
    </div>
  );
}

/**
 * The track's Spotify Canvas behind the lyrics, dimmed so they stay legible.
 * Muted and looping, it fades in once it can play and pauses with the music.
 *
 * The blur runs on a quarter-size video that is then scaled up 4×: a 3px blur
 * there reads as about 12px on screen for a sixteenth of the pixels, which is
 * what keeps a blurred, playing video cheap. The 5% overscan on each side
 * keeps the soft edges out of view.
 */
function CanvasBackdrop({ url }: { url: string }) {
  const video = useRef<HTMLVideoElement>(null);
  const [ready, setReady] = useState(false);
  const playing = usePlayer((s) => s.status === 'playing' || s.status === 'buffering');
  useEffect(() => setReady(false), [url]);
  useEffect(() => {
    const el = video.current;
    if (!el || !ready) return;
    if (playing) void el.play().catch(() => undefined);
    else el.pause();
  }, [playing, ready]);
  return (
    <video
      ref={video}
      key={url}
      className="absolute top-[-5%] left-[-5%] -z-1 size-[27.5%] origin-[0_0] scale-400 object-cover opacity-0 blur-[3px] transition-opacity duration-600 [mask-image:linear-gradient(to_bottom,rgb(0_0_0/1),rgb(0_0_0/0.85))] data-ready:opacity-85"
      data-ready={ready || undefined}
      src={url}
      muted
      loop
      playsInline
      preload="auto"
      aria-hidden="true"
      onCanPlay={() => setReady(true)}
      onError={() => setReady(false)}
    />
  );
}

const panel = 'group/panel relative isolate flex min-h-0 flex-1 flex-col overflow-hidden';
// The full-screen player's column (report §7): lines 26px, then 30px from 980px, 36px from 1320px and 42px from 1680px.
const fullSizes =
  '[--bitchord-lyric-size:26px] min-[980px]:[--bitchord-lyric-size:30px] min-[1320px]:[--bitchord-lyric-size:36px] min-[1680px]:[--bitchord-lyric-size:42px]';

/**
 * The lyrics, with their provider picker and empty states: the side panel
 * (report §7), and the right-hand column of the full-screen player, where the
 * lines are larger and the player's mesh stands in for the Canvas.
 */
export function LyricsPanel({ variant = 'panel' }: { variant?: 'panel' | 'full' }) {
  const lyrics = useLyrics();
  const hasTrack = usePlayer((s) => s.nowPlaying !== null);
  const videoId = usePlayer((s) => s.nowPlaying?.videoId ?? '');
  // A provider picked by hand that came back with nothing is named in the
  // empty state: the lyrics didn't go missing, this one provider hasn't got them.
  const picked = useLyricsUi((s) => s.picked[videoId]);
  const ungranted = useUngranted();
  const canvas = useCanvas();
  const fontReady = useLyricFont();
  const result = lyrics.data;
  const full = variant === 'full';
  const empty = full
    ? 'flex flex-1 flex-col items-center justify-center gap-2 px-6 text-center type-title-3 text-secondary'
    : 'flex flex-1 flex-col items-center justify-center gap-2 px-6 pb-20 text-center type-[400_12px/16px] text-secondary';
  const emptyTitle = full ? 'type-title-1-emphasized text-primary' : 'type-[600_12px/16px]';
  const [element, setElement] = useState<AmLyricsElement | null>(null);
  const picker = <ProviderPicker current={result?.source ?? null} full={full} lines={result?.lines ?? null} element={element} />;

  return (
    <div className={full ? `${panel} ${fullSizes}` : panel}>
      {canvas && variant === 'panel' && <CanvasBackdrop url={canvas} />}
      {/* The side panel's picker heads it; the full-screen one is the bar under the lines. */}
      {!full && picker}
      {/* Only worth raising when nothing was found: then the hosts behind the extension might have it. */}
      {ungranted.length > 0 && !result && !lyrics.isFetching && <PermissionNotice origins={ungranted} />}
      {!hasTrack ? (
        <div className={empty}>
          <p className={emptyTitle}>No lyrics available</p>
          <p>Play a song to see its lyrics here.</p>
        </div>
      ) : (lyrics.isPending && lyrics.fetchStatus !== 'idle') || (result && !fontReady) ? (
        <div className={empty}>
          <Loader2 size={20} className="animate-spin" />
        </div>
      ) : result ? (
        <LyricsView ttml={result.ttml} full={full} onElement={full ? setElement : undefined} />
      ) : picked ? (
        <div className={empty}>
          <p className={emptyTitle}>Not on {sourceById(picked)?.label ?? 'this provider'}</p>
          <p>This provider hasn’t got this song. Pick another, or Best available, in the {full ? 'list below' : 'list above'}.</p>
        </div>
      ) : (
        <div className={empty}>
          <p className={emptyTitle}>No lyrics available</p>
          <p>None of the lyric providers has this song. Try another in the {full ? 'list below' : 'list above'}.</p>
        </div>
      )}
      {full && picker}
    </div>
  );
}
