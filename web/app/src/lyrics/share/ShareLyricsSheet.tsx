import { fitsOnShareCard, pickShareLines, shareTimeline, ShareSourceLine } from '@bitchord/shared-kotlin';
import { Download, Loader2, Share, X } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { SegmentedControl } from '@/components/ui/SegmentedControl';
import { useCanvas } from '@/data/spotify/canvas';
import { artworkAtPixels } from '@/lib/artwork';
import { fetchTrackAudio } from '@/playback/automix/fetchAudio';
import { getPlayhead, usePlayer } from '@/state/player';
import type { LyricLine } from '../model';
import { useLyricsSettings } from '../settings';
import { drawBackdrop, LyricsCard, type ShareFormat } from './card';
import { recordVideo, save, shareOrSave, videoType } from './record';

/**
 * Share lyrics (the phone's lyrics share sheet, AndroidLyricsShareSheet.kt):
 * pick the lines, see the card, and send it as a video of that stretch of the
 * song, each line lighting as it is sung, or as a still picture.
 *
 * The phone picks lines by long-pressing them in the panel; here they're
 * picked in the sheet's own list (shift-click for a run). What a pick puts on
 * the card, how much a card holds and which stretches of the song the video
 * plays are the phone's own rules (LyricsShareLines.kt, from shared Kotlin).
 */
export default function ShareLyricsSheet({ lyrics, onClose }: { lyrics: LyricLine[]; onClose: () => void }) {
  const track = usePlayer((s) => s.nowPlaying);
  const canvasUrl = useCanvas();
  const logo = useLyricsSettings((s) => s.cardLogo);
  const setLogo = useLyricsSettings((s) => s.setCardLogo);

  const [picks, setPicks] = useState(() => initialPick(lyrics));
  const [anchor, setAnchor] = useState<number | null>(null);
  const [overBudget, setOverBudget] = useState(false);
  // The 9:16 story is what these are mostly made for, so it is where the sheet opens.
  const [format, setFormat] = useState<ShareFormat>('story');
  const [asVideo, setAsVideo] = useState(true);
  const [background, setBackground] = useState<'artwork' | 'canvas'>('artwork');
  const [work, setWork] = useState<Work>({ state: 'idle' });
  const [progress, setProgress] = useState(0);
  const made = useRef<{ look: string; file: File } | null>(null);
  const abort = useRef<AbortController | null>(null);

  const sourceLines = useMemo(() => lyrics.map((l) => new ShareSourceLine(l.timeMs, l.text, l.sungUntilMs)), [lyrics]);
  const lines = useMemo(() => pickShareLines(sourceLines, [...picks]), [sourceLines, picks]);
  const timeline = useMemo(() => shareTimeline(lines), [lines]);
  const cover = useImage(track?.artworkUrl ? artworkAtPixels(track.artworkUrl, 544) : null);
  // Nothing would move in a video with no vocal to follow and no Canvas.
  const videoPossible = !!timeline || !!canvasUrl;
  const isVideo = asVideo && videoPossible;
  const clip = useClip(isVideo && background === 'canvas' ? canvasUrl : null);
  const card = useMemo(
    () =>
      lines.length && track
        ? new LyricsCard({ lines, lyrics, title: track.title, artist: track.artist, cover, format, logo })
        : null,
    [lines, lyrics, track, cover, format, logo],
  );
  const busy = work.state === 'busy';

  // Another song: these lines are no longer its lines.
  const videoId = track?.videoId;
  const openedFor = useRef(videoId);
  useEffect(() => {
    if (videoId !== openedFor.current) onClose();
  }, [videoId, onClose]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  // Closed mid-export: stop recording. Opened on the line being sung: show it.
  const list = useRef<HTMLUListElement>(null);
  useEffect(() => {
    list.current?.querySelector('[aria-pressed="true"]')?.scrollIntoView({ block: 'center' });
    return () => abort.current?.abort();
  }, []);

  // The preview runs the card on the song's clock, silently, looping with a
  // beat of rest on the finished verse: what is shared is what was seen.
  const preview = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    const canvas = preview.current;
    if (!canvas || !card) return;
    canvas.width = card.width / 2;
    canvas.height = card.height / 2;
    const ctx = canvas.getContext('2d')!;
    const scratch = document.createElement('canvas');
    const duration = timeline?.durationMs ?? 0;
    const start = performance.now();
    let frame = 0;
    const paint = () => {
      ctx.setTransform(0.5, 0, 0, 0.5, 0, 0);
      drawBackdrop(ctx, clip ?? cover, scratch, card.width, card.height);
      const elapsed = (performance.now() - start) % (duration + PREVIEW_REST_MS);
      card.draw(ctx, isVideo && timeline ? timeline.trackMsAt(Math.min(elapsed, duration)) : null);
      if (isVideo) frame = requestAnimationFrame(paint);
    };
    paint();
    return () => cancelAnimationFrame(frame);
  }, [card, timeline, clip, cover, isVideo]);

  const toggle = (index: number, run: boolean) => {
    const adding = !picks.has(index);
    const targets = run && anchor !== null ? between(anchor, index).filter((i) => lyrics[i]!.text.trim()) : [index];
    const next = new Set(picks);
    let refused = false;
    for (const i of targets) {
      if (!adding) next.delete(i);
      else if (!next.has(i)) {
        const taken = [...next].reduce((total, j) => total + (lyrics[j]?.text.length ?? 0), 0);
        // A line that will not fit says so, and nothing already picked is dropped.
        if (!fitsOnShareCard(taken, lyrics[i]!.text)) {
          refused = true;
          break;
        }
        next.add(i);
      }
    }
    setPicks(next);
    setAnchor(index);
    setOverBudget(refused);
    setWork({ state: 'idle' });
  };

  /** The video or picture for the current look, made once and reused by Save and Share alike. */
  async function make(): Promise<File> {
    const look = JSON.stringify([[...picks].sort((a, b) => a - b), format, isVideo, isVideo && background, logo]);
    if (made.current?.look === look) return made.current.file;
    if (!card || !track) throw new Error('nothing picked');
    const name = `bitchord-lyrics-${track.title.toLowerCase().replace(/[^\p{L}\p{N}]+/gu, '-').replace(/^-|-$/g, '') || 'song'}`;
    const canvas = document.createElement('canvas');
    canvas.width = card.width;
    canvas.height = card.height;
    const ctx = canvas.getContext('2d')!;
    const scratch = document.createElement('canvas');
    const paint = (positionMs: number | null) => {
      drawBackdrop(ctx, clip ?? cover, scratch, card.width, card.height);
      card.draw(ctx, positionMs);
    };

    let file: File;
    let silent = false;
    if (!isVideo) {
      paint(null);
      const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/png'));
      if (!blob) throw new Error('no picture');
      file = new File([blob], `${name}.png`, { type: 'image/png' });
    } else {
      const type = videoType((t) => MediaRecorder.isTypeSupported(t));
      if (!type) throw new Error('MediaRecorder can’t write video here');
      const controller = new AbortController();
      abort.current = controller;
      setProgress(0);
      setWork({ state: 'busy', label: timeline ? 'Getting the song…' : 'Making the video…' });
      // The song's own audio for the picked stretches; failing that, the same
      // video made silent, as the phone does.
      let audio: { context: AudioContext; buffer: AudioBuffer; segments: { startMs: number; endMs: number }[] } | null = null;
      if (timeline) {
        const context = new AudioContext();
        try {
          const { bytes } = await fetchTrackAudio(track.videoId, controller.signal);
          audio = { context, buffer: await context.decodeAudioData(bytes), segments: timeline.segments };
        } catch (error) {
          void context.close();
          if (controller.signal.aborted) throw error;
          silent = true;
        }
      }
      setWork({ state: 'busy', label: 'Making the video…' });
      try {
        const blob = await recordVideo({
          canvas,
          type,
          audio,
          durationMs: timeline?.durationMs ?? MIN_LENGTH_MS,
          draw: (elapsed) => paint(timeline ? timeline.trackMsAt(elapsed) : null),
          signal: controller.signal,
          onProgress: (fraction) => setProgress(Math.round(fraction * 100)),
        });
        file = new File([blob], `${name}.${type.extension}`, { type: blob.type.split(';')[0] });
      } finally {
        void audio?.context.close();
      }
    }
    made.current = { look, file };
    setWork(silent ? { state: 'done', message: 'Made without sound: the song’s audio couldn’t be fetched.' } : { state: 'idle' });
    return file;
  }

  const run = async (action: 'share' | 'save') => {
    try {
      const file = await make();
      if (action === 'save') {
        save(file);
        setWork((w) => (w.state === 'done' ? w : { state: 'done', message: 'Saved to your downloads.' }));
        return;
      }
      const result = await shareOrSave(file);
      if (result === 'refused') setWork({ state: 'done', message: 'Ready. Press Share again to send it.' });
      else if (result === 'saved') setWork((w) => (w.state === 'done' ? w : { state: 'done', message: 'Saved to your downloads.' }));
    } catch (error) {
      if (abort.current?.signal.aborted) return;
      console.warn('[lyrics share]', error);
      setWork({ state: 'error', message: isVideo ? 'Couldn’t make the video.' : 'Couldn’t draw the picture.' });
    }
  };

  const shown = lyrics.map((line, index) => ({ line, index })).filter(({ line }) => line.text.trim());
  const synced = lyrics.some((l) => l.timeMs > 0);

  return createPortal(
    // A modal like Lyric Settings (report §8): scrim, 14px card radius, fade with a slight zoom.
    <div className="fixed inset-0 z-(--z-modal) grid animate-fade place-items-center bg-scrim" onPointerDown={(e) => e.target === e.currentTarget && onClose()}>
      <div
        className="flex max-h-[calc(100vh-48px)] w-[min(880px,calc(100vw-32px))] animate-zoom flex-col rounded-[14px] bg-page shadow-glass"
        role="dialog"
        aria-modal="true"
        aria-label="Share lyrics"
      >
        <header className="flex items-center justify-between px-5 pt-[18px] pb-2">
          <h2 className="type-title-2-emphasized">Share Lyrics</h2>
          <button className="grid size-[30px] place-items-center rounded-full bg-[#333336] text-secondary" aria-label="Close" onClick={onClose}>
            <X size={16} />
          </button>
        </header>

        <div className="grid min-h-0 flex-1 gap-5 overflow-y-auto px-5 pb-5 min-[720px]:grid-cols-[minmax(0,1fr)_auto] min-[720px]:overflow-hidden">
          <section className="flex min-h-0 flex-col">
            <p className="mx-1 mb-2 type-callout text-secondary">
              {overBudget ? 'That’s as much as one card holds.' : 'Pick the lines to share. Shift-click picks a run.'}
            </p>
            <ul ref={list} className="max-h-[40vh] min-h-0 flex-1 overflow-y-auto rounded-[10px] bg-quinary py-1 min-[720px]:max-h-none" aria-label="Lines">
              {shown.map(({ line, index }) => (
                <li key={index}>
                  <button
                    className="flex w-full items-baseline gap-3 px-3 py-1.5 text-left type-body text-secondary hover:bg-quaternary aria-pressed:text-primary aria-pressed:type-body-emphasized disabled:opacity-60"
                    aria-pressed={picks.has(index)}
                    dir="auto"
                    disabled={busy}
                    onClick={(e) => toggle(index, e.shiftKey)}
                  >
                    {synced && <span className="w-9 flex-none text-right type-footnote text-tertiary tabular-nums">{clockLabel(line.timeMs)}</span>}
                    <span className="min-w-0">{line.text}</span>
                  </button>
                </li>
              ))}
            </ul>
          </section>

          <section className="flex flex-col items-center gap-3 min-[720px]:w-[300px]">
            <div className="relative overflow-hidden rounded-[18px] bg-quinary">
              {card ? (
                <canvas
                  ref={preview}
                  className={format === 'story' ? 'block h-[min(48vh,480px)] w-auto' : 'block size-[min(36vh,300px)]'}
                  style={{ aspectRatio: `${card.width} / ${card.height}` }}
                  role="img"
                  aria-label="Your lyrics card"
                />
              ) : (
                <div className={`grid place-items-center px-6 text-center type-callout text-secondary ${format === 'story' ? 'h-[min(48vh,480px)] aspect-[9/16]' : 'size-[min(36vh,300px)]'}`}>
                  Pick a line to see the card.
                </div>
              )}
              {busy && (
                <div className="absolute inset-0 grid place-items-center bg-black/35">
                  <div className="flex flex-col items-center gap-2 type-callout-emphasized text-white">
                    <Loader2 size={24} className="animate-spin" />
                    <span>{work.label}</span>
                    {work.label === 'Making the video…' && <span className="tabular-nums">{progress}%</span>}
                  </div>
                </div>
              )}
            </div>

            <Segmented
              label="Share as"
              options={[['video', 'Video'], ['image', 'Image']]}
              value={isVideo ? 'video' : 'image'}
              disabled={busy ? ['video', 'image'] : videoPossible ? [] : ['video']}
              onChange={(v) => setAsVideo(v === 'video')}
            />
            <Segmented
              label="Format"
              options={[['story', 'Story'], ['square', 'Square']]}
              value={format}
              disabled={busy ? ['story', 'square'] : []}
              onChange={setFormat}
            />
            {isVideo && canvasUrl && (
              <Segmented
                label="Background"
                options={[['artwork', 'Artwork'], ['canvas', 'Canvas']]}
                value={background}
                disabled={busy ? ['artwork', 'canvas'] : []}
                onChange={setBackground}
              />
            )}
            <label className="flex w-full cursor-pointer items-center gap-2.5 rounded-[10px] bg-quinary py-2 pr-2.5 pl-3">
              <input type="checkbox" className="size-4 accent-key" checked={logo} disabled={busy} onChange={(e) => setLogo(e.currentTarget.checked)} />
              <span className="type-body">Show BitChord</span>
            </label>

            {(work.state === 'done' || work.state === 'error') && (
              <p className={`w-full text-center type-callout ${work.state === 'error' ? 'text-[#ff453a]' : 'text-secondary'}`} role="status">
                {work.message}
              </p>
            )}

            <div className="flex w-full gap-3">
              <button
                className="flex flex-1 items-center justify-center gap-2 rounded-[14px] bg-quinary px-4 py-2 type-body-emphasized enabled:hover:bg-quaternary disabled:opacity-40"
                disabled={!card || busy}
                onClick={() => void run('save')}
              >
                <Download size={16} />
                Save
              </button>
              <button
                className="flex flex-1 items-center justify-center gap-2 rounded-[14px] bg-key px-4 py-2 type-body-emphasized text-on-key disabled:opacity-40"
                disabled={!card || busy}
                onClick={() => void run('share')}
              >
                <Share size={16} />
                Share
              </button>
            </div>
          </section>
        </div>
      </div>
    </div>,
    document.body,
  );
}

type Work = { state: 'idle' } | { state: 'busy'; label: string } | { state: 'done' | 'error'; message: string };

/** Two to three choices, one of them on: the phone's SegmentedControl, from [id, label] pairs. */
function Segmented<T extends string>({
  label,
  options,
  value,
  disabled,
  onChange,
}: {
  label: string;
  options: [T, string][];
  value: T;
  disabled: T[];
  onChange: (value: T) => void;
}) {
  return (
    <SegmentedControl
      label={label}
      options={options.map(([id, text]) => ({ value: id, label: text, disabled: disabled.includes(id) }))}
      value={value}
      onChange={onChange}
      className="w-full"
    />
  );
}

/** The line being sung right now, so the sheet opens with something on the card. */
function initialPick(lyrics: LyricLine[]): Set<number> {
  const nowMs = (getPlayhead()?.sec ?? 0) * 1000;
  if (!lyrics.some((l) => l.timeMs > 0)) return new Set();
  let current = -1;
  lyrics.forEach((line, index) => {
    if (line.timeMs <= nowMs && line.text.trim()) current = index;
  });
  return current >= 0 && fitsOnShareCard(0, lyrics[current]!.text) ? new Set([current]) : new Set();
}

const between = (a: number, b: number) => Array.from({ length: Math.abs(a - b) + 1 }, (_, i) => Math.min(a, b) + i);

function clockLabel(ms: number): string {
  const seconds = Math.floor(ms / 1000);
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

/** The sleeve, loaded so the canvas it is drawn on can still be recorded (YouTube's image hosts allow it). */
function useImage(url: string | null): HTMLImageElement | null {
  const [image, setImage] = useState<HTMLImageElement | null>(null);
  useEffect(() => {
    setImage(null);
    if (!url) return;
    let live = true;
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.referrerPolicy = 'no-referrer';
    img.src = url;
    img.decode().then(
      () => live && setImage(img),
      () => undefined,
    );
    return () => {
      live = false;
    };
  }, [url]);
  return image;
}

/**
 * The song's Canvas, playing muted on a loop for the card to sit on. Loaded
 * CORS-clean, since a canvas that has drawn anything else can't be recorded;
 * where its host won't allow that, the sleeve stands in.
 */
function useClip(url: string | null): HTMLVideoElement | null {
  const [video, setVideo] = useState<HTMLVideoElement | null>(null);
  useEffect(() => {
    setVideo(null);
    if (!url) return;
    const el = document.createElement('video');
    el.crossOrigin = 'anonymous';
    el.muted = true;
    el.loop = true;
    el.playsInline = true;
    el.oncanplay = () => void el.play().then(() => setVideo(el), () => undefined);
    el.src = url;
    return () => {
      el.oncanplay = null;
      el.pause();
      el.removeAttribute('src');
      el.load();
    };
  }, [url]);
  return video;
}

/** LyricsShareVideo.kt MIN_LENGTH_MS: unsynced lyrics on a Canvas, held long enough to read. */
const MIN_LENGTH_MS = 10_000;

/** A beat on the finished verse before the preview loops back to the start. */
const PREVIEW_REST_MS = 1200;
