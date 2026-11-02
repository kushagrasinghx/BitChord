// From AutoMix (https://github.com/heydavidf/automix), Copyright (C) 2026 David Finnegan.
// AGPL-3.0-only: see ../LICENSE and ../NOTICE.md in playback/automix.

// BitChord: module form, mapped in tsconfig paths (TypeScript 7 resolves the
// package's .mjs ahead of an ambient declare module).
  export interface ScheduleOptions {
    /** AudioContext time for this change (seconds). Latency-compensated; schedule ahead when possible. */
    output?: number;
    /**
     * DANGER: the worklet keeps only ONE pending future change — each
     * schedule() prunes everything after "now" and fast-forwards its map
     * head to `outputTime` (default: now). Post changes only when imminent;
     * see Deck.scheduleChange. Do not pass `outputTime` for future times —
     * it consumes every segment before it.
     */
    outputTime?: number;
    /** Whether the node is processing audio. */
    active?: boolean;
    /** Position in the input buffer (seconds). */
    input?: number;
    /** Playback rate (1 = normal). Pitch is NOT affected. */
    rate?: number;
    /** Pitch shift in semitones. */
    semitones?: number;
    tonalityHz?: number;
    formantSemitones?: number;
    formantCompensation?: boolean;
    formantBaseHz?: number;
    loopStart?: number;
    loopEnd?: number;
  }

  export interface SignalsmithStretchNode extends AudioWorkletNode {
    /** Current input-buffer time (seconds), refreshed per setUpdateInterval. */
    readonly inputTime: number;
    schedule(opts: ScheduleOptions, adjustPrevious?: boolean): void;
    start(when?: number, offset?: number, duration?: number): void;
    stop(when?: number): void;
    /** One Float32Array per channel, equal lengths. Appends after existing buffers. */
    addBuffers(buffers: Float32Array[]): Promise<number>;
    dropBuffers(toSeconds?: number): Promise<{ start: number; end: number } | void>;
    /** Processing latency in seconds; schedule at least this far ahead. Remote call — resolves async. */
    latency(): Promise<number>;
    setUpdateInterval(seconds: number, callback?: (inputTime: number) => void): void;
    configure(opts: {
      blockMs?: number | null;
      intervalMs?: number;
      splitComputation?: boolean;
      preset?: 'default' | 'cheaper';
    }): void;
  }

  export default function SignalsmithStretch(
    ctx: BaseAudioContext,
    channelOptions?: Partial<AudioWorkletNodeOptions>,
  ): Promise<SignalsmithStretchNode>;
