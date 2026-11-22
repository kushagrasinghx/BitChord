import { useQuery } from '@tanstack/react-query';
import { Music2 } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useLyrics } from '@/lyrics/useLyrics';
import { bestPartLines, heatmapFor, pickBestPart } from '@/playback/bestPart';
import { usePlayerOptions } from '@/playback/playerOptions';
import { getPlayhead, usePlayer } from '@/state/player';

/** A playhead this close before the best part already counts as there (PlayerControls.kt AHEAD_SLACK_MS). */
const AHEAD_SLACK_MS = 400;
/** Only a playhead inside this much of the best part counts as having heard it (HEARD_WINDOW_MS). */
const HEARD_WINDOW_MS = 20_000;
const POLL_MS = 250;

/**
 * "Best Part" (the phone's BestPartPill): a glass pill on the artwork that
 * skips to the part of the song people replay most, or failing that to its
 * chorus.
 *
 * Offered only while it is still ahead: once the playhead has reached the best
 * part, by the button or by just listening, it goes for the rest of the track.
 * A track with no best part known (no replay curve, no synced lyrics to find a
 * chorus in) never shows it, rather than guessing at the fallback point.
 */
export function BestPartPill() {
  const enabled = usePlayerOptions((s) => s.showBestPart);
  const videoId = usePlayer((s) => s.nowPlaying?.videoId ?? null);
  if (!enabled || !videoId) return null;
  // Keyed by the track: having been heard is remembered per track.
  return <Pill key={videoId} videoId={videoId} />;
}

function Pill({ videoId }: { videoId: string }) {
  const durationMs = usePlayer((s) => Math.round((s.nowPlaying?.durationSec ?? 0) * 1000));
  // Not while AutoMix is mixing into the next track: what is ahead then is the next song.
  const mixing = usePlayer((s) => s.automixState?.state === 'scheduled');
  const heat = useQuery({ queryKey: ['best-part-heat', videoId], queryFn: () => heatmapFor(videoId), staleTime: Infinity, retry: false });
  // The lyrics panel's own query, so the chorus fallback costs no second lookup.
  const lyrics = useLyrics();
  const pick =
    heat.isSuccess && durationMs > 0
      ? pickBestPart(heat.data, bestPartLines(lyrics.data?.lines, !!lyrics.data?.synced), durationMs)
      : null;
  const at = pick && pick.source !== 'FALLBACK' ? pick.startMs : null;

  const [ahead, setAhead] = useState(false);
  const [heard, setHeard] = useState(false);
  useEffect(() => {
    if (at === null) return;
    const check = () => {
      const ms = (getPlayhead()?.sec ?? 0) * 1000;
      setAhead(ms < at - AHEAD_SLACK_MS);
      // Further on is a skip past it, and the button comes back for a seek to before it.
      if (ms >= at - AHEAD_SLACK_MS && ms <= at + HEARD_WINDOW_MS) setHeard(true);
    };
    check();
    const timer = setInterval(check, POLL_MS);
    return () => clearInterval(timer);
  }, [at]);

  if (at === null) return null;
  const shown = ahead && !heard && !mixing;
  return (
    <button
      className="glass-pill absolute bottom-4 left-1/2 z-1 flex -translate-x-1/2 items-center gap-1.5 rounded-pill py-[7px] ps-3 pe-[15px] type-[700_14px/18px] whitespace-nowrap text-white opacity-0 transition-opacity duration-280 data-shown:opacity-100"
      data-shown={shown || undefined}
      inert={!shown}
      aria-label="Skip to the best part"
      onClick={() => {
        setHeard(true);
        usePlayer.getState().seek(at / 1000);
      }}
    >
      <Music2 size={15} strokeWidth={2.5} aria-hidden="true" />
      Best Part
    </button>
  );
}
