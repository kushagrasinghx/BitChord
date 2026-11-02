// From AutoMix (https://github.com/heydavidf/automix), Copyright (C) 2026 David Finnegan.
// AGPL-3.0-only: see ./LICENSE and ./NOTICE.md in playback/automix.

declare module 'essentia.js/dist/essentia-wasm.es.js' {
  /** Emscripten Module object; WASM is embedded base64 and instantiates async. */
  export const EssentiaWASM: {
    calledRun?: boolean;
    onRuntimeInitialized?: () => void;
    [key: string]: unknown;
  };
}

declare module 'essentia.js/dist/essentia.js-core.es.js' {
  export interface EssentiaVector {
    size(): number;
    get(i: number): number;
    delete(): void;
  }
  export default class Essentia {
    constructor(wasmModule: unknown, isDebug?: boolean);
    version: string;
    arrayToVector(arr: Float32Array): EssentiaVector;
    vectorToArray(vec: EssentiaVector): Float32Array;
    RhythmExtractor2013(
      signal: EssentiaVector,
      maxTempo?: number,
      method?: string,
      minTempo?: number,
    ): { bpm: number; ticks: EssentiaVector; confidence: number; estimates: EssentiaVector; bpmIntervals: EssentiaVector };
    KeyExtractor(
      audio: EssentiaVector,
      averageDetuningCorrection?: boolean,
      frameSize?: number,
      hopSize?: number,
      hpcpSize?: number,
      maxFrequency?: number,
      maximumSpectralPeaks?: number,
      minFrequency?: number,
      pcpThreshold?: number,
      profileType?: string,
      sampleRate?: number,
    ): { key: string; scale: 'major' | 'minor'; strength: number };
  }
}
