import { create } from 'zustand';

/**
 * A music video's picture (playback/youtube/videoDeck.ts plays it). One muted
 * <video> element follows the audio; the surfaces that show it (the mini
 * player's preview, the full-screen player) take turns holding it, the one
 * mounted last in front, as only one can be on screen at a time.
 */
/**
 * What the listener picked in the full-screen player's quality switcher: a
 * height to hold, or 'auto' to let the stream adapt to the connection.
 */
export type VideoQuality = 'auto' | number;

interface VideoState {
  /** The track whose picture is loaded, once its first frame is ready to show. */
  videoId: string | null;
  /** Width over height, e.g. 16/9. */
  aspect: number;
  /** How many surfaces want the picture; with none it isn't streamed. */
  surfaces: number;
  /** The heights the loaded picture offers, largest first. */
  heights: number[];
  /** The listener's choice, remembered across sessions. */
  quality: VideoQuality;
  /** The height actually on screen, as Shaka reports the active rendition. */
  activeHeight: number | null;
}

const QUALITY_KEY = 'bitchord:video-quality';

function storedQuality(): VideoQuality {
  const stored = localStorage.getItem(QUALITY_KEY);
  const height = Number(stored);
  return stored && Number.isFinite(height) && height > 0 ? height : 'auto';
}

export const useVideo = create<VideoState>()(() => ({
  videoId: null,
  aspect: 16 / 9,
  surfaces: 0,
  heights: [],
  quality: storedQuality(),
  activeHeight: null,
}));

/** Holds the picture at [quality] from now on; the deck reloads it at the same moment. */
export function setVideoQuality(quality: VideoQuality): void {
  if (quality === 'auto') localStorage.removeItem(QUALITY_KEY);
  else localStorage.setItem(QUALITY_KEY, String(quality));
  useVideo.setState({ quality });
}

/** Whether [videoId]'s picture is ready to show. */
export const useHasVideo = (videoId: string | null | undefined) => useVideo((s) => !!videoId && s.videoId === videoId);

/** How a height is named on screen: the marketing name where there is one, the height otherwise. */
export function qualityLabel(height: number): string {
  if (height >= 4320) return '8K';
  if (height >= 2160) return '4K';
  if (height >= 1440) return '2K';
  if (height >= 1080) return 'FullHD';
  if (height >= 720) return 'HD';
  return `${height}p`;
}

let element: HTMLVideoElement | null = null;

export function getVideoElement(): HTMLVideoElement {
  if (!element) {
    element = document.createElement('video');
    element.muted = true;
    element.playsInline = true;
    element.disablePictureInPicture = true;
    element.setAttribute('aria-hidden', 'true');
    // For browser-driven tests, next to __bcAudio.
    if (import.meta.env.DEV) Object.assign(window, { __bcVideo: element });
  }
  return element;
}

export type VideoFit = 'cover' | 'contain';

const stack: { host: HTMLElement; fit: VideoFit }[] = [];

function place() {
  const top = stack[stack.length - 1];
  const video = getVideoElement();
  if (!top) {
    video.remove();
    return;
  }
  video.className = top.fit === 'contain' ? 'block size-full object-contain' : 'block size-full object-cover';
  if (video.parentElement !== top.host) top.host.append(video);
}

/** Puts the picture in [host] until the returned release is called. */
export function claimVideoSurface(host: HTMLElement, fit: VideoFit): () => void {
  const entry = { host, fit };
  stack.push(entry);
  place();
  useVideo.setState((s) => ({ surfaces: s.surfaces + 1 }));
  return () => {
    const at = stack.indexOf(entry);
    if (at >= 0) stack.splice(at, 1);
    place();
    useVideo.setState((s) => ({ surfaces: s.surfaces - 1 }));
  };
}
