/**
 * The share video, made the browser's way: the card is drawn frame by frame
 * onto a canvas, `canvas.captureStream()` turns that into video, the picked
 * stretches of the song are played back to back through WebAudio into the same
 * stream, and MediaRecorder encodes the two together. It runs in real time, so
 * a 30-second share takes 30 seconds to make.
 */

export interface VideoType {
  mimeType: string;
  extension: 'mp4' | 'webm';
}

/**
 * MP4 (H.264 and AAC, what a story or a chat expects) where MediaRecorder can
 * write it, which Chromium and so WebView2 can; WebM elsewhere.
 */
export function videoType(isTypeSupported: (type: string) => boolean): VideoType | null {
  for (const [mimeType, extension] of TYPES) if (isTypeSupported(mimeType)) return { mimeType, extension };
  return null;
}

const TYPES: [string, VideoType['extension']][] = [
  ['video/mp4;codecs=avc1,mp4a.40.2', 'mp4'],
  ['video/mp4', 'mp4'],
  ['video/webm;codecs=vp9,opus', 'webm'],
  ['video/webm;codecs=vp8,opus', 'webm'],
  ['video/webm', 'webm'],
];

/** One stretch of the song, in track milliseconds. */
export interface Segment {
  startMs: number;
  endMs: number;
}

export interface RecordOptions {
  canvas: HTMLCanvasElement;
  /** Draws the frame [elapsedMs] into the video onto [canvas]. */
  draw: (elapsedMs: number) => void;
  durationMs: number;
  type: VideoType;
  /** The decoded song and the stretches of it to play, in order; null makes a silent video. */
  audio: { context: AudioContext; buffer: AudioBuffer; segments: Segment[] } | null;
  signal: AbortSignal;
  onProgress: (fraction: number) => void;
}

export async function recordVideo({ canvas, draw, durationMs, type, audio, signal, onProgress }: RecordOptions): Promise<Blob> {
  const tracks = canvas.captureStream(FRAME_RATE).getVideoTracks();
  const sources: AudioBufferSourceNode[] = [];
  // With sound, the frames follow the audio's own clock, so the words stay on
  // the vocal however the page's timers slip.
  let clock: () => number;
  if (audio) {
    const { context, buffer, segments } = audio;
    await context.resume();
    const destination = context.createMediaStreamDestination();
    const startAt = context.currentTime + LEAD_IN_S;
    let at = startAt;
    for (const segment of segments) {
      const source = context.createBufferSource();
      source.buffer = buffer;
      source.connect(destination);
      const length = (segment.endMs - segment.startMs) / 1000;
      source.start(at, segment.startMs / 1000, length);
      sources.push(source);
      at += length;
    }
    tracks.push(...destination.stream.getAudioTracks());
    clock = () => (context.currentTime - startAt) * 1000;
  } else {
    const startAt = performance.now();
    clock = () => performance.now() - startAt;
  }

  const recorder = new MediaRecorder(new MediaStream(tracks), { mimeType: type.mimeType, videoBitsPerSecond: VIDEO_BITRATE });
  const chunks: Blob[] = [];
  recorder.ondataavailable = (event) => {
    if (event.data.size) chunks.push(event.data);
  };
  const stopped = new Promise<void>((resolve) => recorder.addEventListener('stop', () => resolve(), { once: true }));
  draw(0);
  recorder.start(1000);
  try {
    await new Promise<void>((resolve, reject) => {
      // Timers rather than animation frames: a frame loop stops dead in a
      // background tab, while the audio (and so the recording) carries on.
      const tick = () => {
        if (signal.aborted) return reject(signal.reason);
        const elapsed = Math.max(0, clock());
        draw(Math.min(elapsed, durationMs));
        onProgress(Math.min(1, elapsed / durationMs));
        if (elapsed >= durationMs) resolve();
        else setTimeout(tick, 1000 / FRAME_RATE);
      };
      tick();
    });
  } finally {
    if (recorder.state !== 'inactive') recorder.stop();
    for (const source of sources) source.disconnect();
    for (const track of tracks) track.stop();
  }
  await stopped;
  return new Blob(chunks, { type: recorder.mimeType || type.mimeType });
}

/**
 * Hands [file] to the system's share sheet where the browser has one that
 * takes files, else saves it. Sharing needs a fresh click, so a share asked
 * for after a long export may be refused: 'refused' says to ask again.
 */
export async function shareOrSave(file: File): Promise<'shared' | 'saved' | 'cancelled' | 'refused'> {
  if (navigator.canShare?.({ files: [file] })) {
    try {
      await navigator.share({ files: [file] });
      return 'shared';
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return 'cancelled';
      if (error instanceof DOMException && error.name === 'NotAllowedError') return 'refused';
    }
  }
  save(file);
  return 'saved';
}

export function save(file: File) {
  const url = URL.createObjectURL(file);
  const link = document.createElement('a');
  link.href = url;
  link.download = file.name;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

/** The audio starts this far after the recording does, so its first word isn't clipped. */
const LEAD_IN_S = 0.2;
const FRAME_RATE = 30;
/** LyricsShareVideo.kt's: comfortably past what Instagram re-encodes a 1080p story to. */
const VIDEO_BITRATE = 10_000_000;
