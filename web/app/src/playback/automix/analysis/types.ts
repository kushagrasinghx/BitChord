// From AutoMix (https://github.com/heydavidf/automix), Copyright (C) 2026 David Finnegan.
// AGPL-3.0-only: see ./LICENSE and ./NOTICE.md in playback/automix.

export interface CamelotKey {
  num: number; // 1-12
  letter: 'A' | 'B'; // A = minor, B = major
}

export interface TrackTags {
  title: string;
  artist: string;
  album?: string;
}

// BitChord: 8 records music videos, 7 widens the vocal mask to the loop searches, 6 adds Beat This! (re-exported), 5 the vocal mask; 3 since the gap-tolerant fitGrid (beatgrid.ts) and the head/tail
// grids; AutoMix's whole-track grids were mostly unusable on real songs.
export const ANALYSIS_VERSION = 8;

/** BitChord: Beat This!'s beats and downbeats over one window, on the track's timeline. */
export interface BeatSide {
  beats: number[];
  downbeats: number[];
  bpm: number;
  /** The phone's scale: 0.35-0.95, beatmatchable from 0.55. */
  confidence: number;
}

/** BitChord: the open-unmix vocal curve over one window: curve[i] is at startSec + i / fps. */
export interface VocalWindow {
  startSec: number;
  fps: number;
  curve: number[];
}

/** BitChord: a beat grid fitted to one stretch of the track. */
export interface LocalGrid {
  bpm: number;
  beatIntervalSec: number;
  firstDownbeatSec: number;
  gridConfidence: number;
}

export interface TrackAnalysis {
  fileKey: string;
  path: string;
  tags: TrackTags;
  durationSec: number;
  /**
   * BitChord: bpm, beatIntervalSec, firstDownbeatSec and gridConfidence are
   * fitted to the track's opening (where it is mixed in); [tailGrid] to its
   * last two minutes (where it is mixed out). A breakdown that comes back on
   * a different phase can't break either one.
   */
  bpm: number | null;
  /** Seconds per beat of the regressed constant grid. */
  beatIntervalSec: number | null;
  firstDownbeatSec: number | null;
  /** 0-1; below threshold we fall back to plain crossfades. */
  gridConfidence: number;
  camelot: CamelotKey | null;
  keyName: string | null;
  keyConfidence: number;
  /** 0-1 overall energy. */
  energy: number;
  /** Per-phrase energy, 0-1. */
  energyCurve: number[];
  introEndSec: number | null;
  outroStartSec: number | null;
  /** BitChord: the grid where the track is mixed out; null when no beat was found there. */
  tailGrid?: LocalGrid | null;
  /** BitChord: Beat This! over the opening (where it's mixed in) and the end (where it's mixed out). */
  headBeats?: BeatSide | null;
  tailBeats?: BeatSide | null;
  /**
   * BitChord: YouTube serves this as a music video, not the song. Its audio
   * often differs from the album cut (a spoken intro, a longer outro), so its
   * synced lyrics don't line up, and the loop mix leaves it alone.
   */
  musicVideo?: boolean;
  /** BitChord: vocal curves over the opening and the end; empty when the model couldn't run. */
  vocals?: VocalWindow[];
  analysisVersion: number;
  manualOverrides?: {
    bpm?: number;
    camelot?: CamelotKey;
    gridNudgeBeats?: number;
  };
}
