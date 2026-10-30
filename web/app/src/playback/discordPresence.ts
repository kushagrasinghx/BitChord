import { useEffect } from 'react';
import type { DiscordNowPlaying } from '@bitchord/shared/bridge';
import { getBridge } from '@/extension/bridge';
import { useDiscord, useDiscordOptions } from '@/extension/useDiscord';
import { sizedArtwork } from '@/lib/artwork';
import { getAudio, usePlayer } from '@/state/player';

/**
 * Tells the extension what's playing, for the Discord card (prompt §7.2).
 * The same moments PlaybackService pushes on:
 * - a track change, or playback resuming: a new presence;
 * - a seek: new timestamps, since Discord draws the bar from them itself;
 * - a pause: the card comes down, or Discord would count on through a stopped song;
 * - a change to the card's options.
 * A buffering stall and the gap while the next track loads are not pauses.
 * Nothing in between: the countdown is Discord's own arithmetic.
 */

/** Discord draws the sleeve at about 96 dp; 480 px covers it on any density (DiscordRPC.ART_PX). */
const ART_PX = 480;

function nowPlaying(): DiscordNowPlaying | null {
  const { nowPlaying: np, queue, index } = usePlayer.getState();
  const audio = getAudio();
  if (!np) return null;
  const durationSec = audio && Number.isFinite(audio.duration) && audio.duration > 0 ? audio.duration : np.durationSec;
  return {
    videoId: np.videoId,
    title: np.title,
    artist: np.artist,
    album: queue[index]?.track.album?.name ?? null,
    artworkUrl: np.artworkUrl ? sizedArtwork(np.artworkUrl, ART_PX) : null,
    positionMs: Math.round((audio?.currentTime ?? 0) * 1000),
    durationMs: Math.round(durationSec * 1000),
    playbackRate: audio?.playbackRate || 1,
  };
}

export function startPublisher(): () => void {
  /** What was last sent: a track (and the moment it was pushed for), or a clear. */
  let sent: string | null = null;
  let timer: ReturnType<typeof setTimeout> | undefined;
  /** A push asked to be fresh (a seek, a resume) and not yet sent: a seek buffers before it plays again. */
  let forced = false;

  const publish = () => {
    const { status } = usePlayer.getState();
    const options = useDiscordOptions.getState().options;
    const bridge = getBridge();
    if (!bridge) return;
    if (status === 'paused' || status === 'idle' || status === 'error') {
      if (sent === 'clear') return;
      sent = 'clear';
      void bridge.call('discord.presence', { track: null, options }).catch(() => (sent = null));
      return;
    }
    if (status !== 'playing') return; // loading or buffering: wait for the music
    const track = nowPlaying();
    if (!track) return;
    const key = `${track.videoId}|${JSON.stringify(options)}`;
    if (!forced && sent === key) return;
    forced = false;
    sent = key;
    void bridge.call('discord.presence', { track, options }, { timeoutMs: 30_000 }).catch(() => (sent = null));
  };
  // Status flips in bursts (loading → buffering → playing); answer the last one,
  // keeping any forced push from earlier in the burst.
  const soon = (force = false) => {
    if (force) forced = true;
    clearTimeout(timer);
    timer = setTimeout(publish, 400);
  };

  let audio: HTMLMediaElement | null = null;
  const onSeeked = () => soon(true);
  const bindAudio = () => {
    audio?.removeEventListener('seeked', onSeeked);
    audio = getAudio();
    audio?.addEventListener('seeked', onSeeked);
  };
  bindAudio();

  const offPlayer = usePlayer.subscribe((state, before) => {
    // The decks are made on the first play, and a crossfade swaps them: follow the element, not the counter.
    if (getAudio() !== audio) bindAudio();
    if (state.nowPlaying?.videoId !== before.nowPlaying?.videoId || state.status !== before.status) {
      // Resuming after a pause is a fresh push: the old timestamps are stale.
      soon(before.status === 'paused' && state.status === 'playing');
    }
  });
  const offOptions = useDiscordOptions.subscribe(() => soon());
  soon(true);

  return () => {
    clearTimeout(timer);
    offPlayer();
    offOptions();
    audio?.removeEventListener('seeked', onSeeked);
  };
}

/** Mounted once in the shell: publishes while an account is connected and presence is on. */
export function useDiscordPresence(): void {
  const status = useDiscord();
  const active = status.state === 'ready' && status.discord.available && status.discord.connected && status.discord.enabled;
  useEffect(() => (active ? startPublisher() : undefined), [active]);
}
