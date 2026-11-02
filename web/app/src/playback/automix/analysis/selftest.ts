// From AutoMix's src/lib/analysis/selftest.ts (https://github.com/heydavidf/automix),
// Copyright (C) 2026 David Finnegan. AGPL-3.0-only: see ../LICENSE and ../NOTICE.md.
// BitChord: exposed from the player store in dev builds instead of on import.
import { makeTestLoop } from './testtones';
import type { AnalyzeResponse } from './analysis.worker';

/**
 * End-to-end analysis check on a synthesized 124 BPM / A-minor-ish loop.
 * Expect BPM ≈ 123.93 and Camelot 8A.
 */
export async function analysisSelfTest(): Promise<AnalyzeResponse> {
  const off = new OfflineAudioContext({ numberOfChannels: 1, length: 1, sampleRate: 44100 });
  const loop = makeTestLoop(off, { bpm: 124, bars: 32, toneHz: 110 });
  const mono = loop.getChannelData(0).slice();
  const worker = new Worker(new URL('./analysis.worker.ts', import.meta.url), {
    type: 'module',
  });
  try {
    return await new Promise<AnalyzeResponse>((resolve) => {
      worker.onmessage = (ev: MessageEvent<AnalyzeResponse>) => resolve(ev.data);
      worker.onerror = (ev) => resolve({ type: 'error', fileKey: 'selftest', message: ev.message });
      worker.postMessage({ type: 'analyze', fileKey: 'selftest', samples: mono, sampleRate: 44100 }, [mono.buffer]);
    });
  } finally {
    worker.terminate();
  }
}
