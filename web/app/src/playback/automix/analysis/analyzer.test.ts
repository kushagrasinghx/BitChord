import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Analyzer } from './analyzer';
import { ANALYSIS_VERSION, type TrackAnalysis } from './types';
import { ANALYSIS_REVISION } from './modelManifest';

const mocks = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn(), fetch: vi.fn(), log: vi.fn() }));
vi.mock('../db', () => ({ getAnalysis: mocks.get, putAnalysis: mocks.put }));
vi.mock('../fetchAudio', () => ({ fetchTrackAudio: mocks.fetch }));
vi.mock('@/playback/trackLog', () => ({ trackLog: { i: mocks.log, w: mocks.log } }));

const tags = { title: 'Song', artist: 'Artist' };
const cached: TrackAnalysis = {
  fileKey: 'song', path: 'youtube:song', tags, durationSec: 180,
  bpm: 120, beatIntervalSec: 0.5, firstDownbeatSec: 0, gridConfidence: 0.9,
  camelot: null, keyName: null, keyConfidence: 0, energy: 0.5, energyCurve: [],
  introEndSec: 0, outroStartSec: 170, analysisVersion: ANALYSIS_VERSION, analysisRevision: ANALYSIS_REVISION,
};

beforeEach(() => {
  vi.clearAllMocks();
  mocks.get.mockResolvedValue(null);
  mocks.put.mockResolvedValue(undefined);
  mocks.fetch.mockImplementation(() => new Promise(() => {}));
});
afterEach(() => vi.unstubAllGlobals());

describe('analysis cache', () => {
  it('waits for a slow lookup before starting another download', async () => {
    let resolve!: (value: TrackAnalysis) => void;
    mocks.get.mockReturnValue(new Promise<TrackAnalysis>((r) => { resolve = r; }));
    const analyzer = new Analyzer();
    analyzer.request('song', tags);
    expect(analyzer.statusOf('song')).toBe('pending');
    await new Promise((r) => setTimeout(r, 80)); // Longer than the former 50 ms guess.
    expect(mocks.fetch).not.toHaveBeenCalled();
    resolve(cached);
    await vi.waitFor(() => expect(analyzer.statusOf('song')).toBe('done'));
    expect(mocks.fetch).not.toHaveBeenCalled();
  });
  it('uses current analysis with fresh track metadata', async () => {
    mocks.get.mockResolvedValue(cached);
    const analyzer = new Analyzer();
    analyzer.request('song', { ...tags, title: 'Updated title' });
    await vi.waitFor(() => expect(analyzer.statusOf('song')).toBe('done'));
    expect(analyzer.get('song')!.tags.title).toBe('Updated title');
    expect(mocks.fetch).not.toHaveBeenCalled();
  });
  it.each([
    { ...cached, fileKey: 'other-song' },
    { ...cached, analysisRevision: 'old-weights' },
    { ...cached, analysisVersion: ANALYSIS_VERSION - 1 },
    { ...cached, analysisRevision: undefined },
  ])('reanalyzes stale or mismatched song profiles', async (value) => {
    mocks.get.mockResolvedValue(value);
    const analyzer = new Analyzer();
    analyzer.request('song', tags);
    await vi.waitFor(() => expect(mocks.fetch).toHaveBeenCalledWith('song'));
    expect(analyzer.get('song')).toBeNull();
  });
  it('still analyzes when IndexedDB cannot be read', async () => {
    mocks.get.mockRejectedValue(new Error('storage disabled'));
    const analyzer = new Analyzer();
    analyzer.request('song', tags);
    await vi.waitFor(() => expect(mocks.fetch).toHaveBeenCalledWith('song'));
  });
  it('keeps completed analysis when the cache is full', async () => {
    mocks.put.mockRejectedValue(new Error('quota exceeded'));
    const analyzer = new Analyzer();
    vi.spyOn(analyzer as unknown as { analyzeOne(): Promise<TrackAnalysis> }, 'analyzeOne').mockResolvedValue(cached);
    analyzer.request('song', tags);
    await vi.waitFor(() => expect(analyzer.statusOf('song')).toBe('done'));
    expect(analyzer.get('song')!.bpm).toBe(120);
  });
  it('recreates a crashed worker so later songs can still be detected', async () => {
    const workers: FakeWorker[] = [];
    class FakeWorker {
      onmessage?: (event: { data: unknown }) => void;
      onerror?: (event: { message: string }) => void;
      terminate = vi.fn();
      constructor() { workers.push(this); }
      postMessage(request: { fileKey: string }) {
        queueMicrotask(() => {
          if (workers.indexOf(this) === 0) this.onerror?.({ message: 'worker crashed' });
          else this.onmessage?.({ data: { type: 'result', fileKey: request.fileKey, result: {
            bpm: 120, beatIntervalSec: 0.5, firstDownbeatSec: 0, gridConfidence: 0.9,
            camelot: null, keyName: null, keyConfidence: 0, energy: 0.5, energyCurve: [],
            introEndSec: 0, outroStartSec: 8, tailGrid: null, headBeats: null, tailBeats: null, vocals: [],
          } } });
        });
      }
    }
    vi.stubGlobal('Worker', FakeWorker);
    vi.stubGlobal('OfflineAudioContext', class {
      private rate: number;
      constructor(options: { sampleRate: number }) { this.rate = options.sampleRate; }
      async decodeAudioData() {
        const samples = new Float32Array(this.rate * 9);
        return { numberOfChannels: 1, duration: 9, length: samples.length, getChannelData: () => samples };
      }
    });
    mocks.fetch.mockResolvedValue({ bytes: new ArrayBuffer(16), musicVideo: false });
    const analyzer = new Analyzer();
    analyzer.request('first', tags);
    await vi.waitFor(() => expect(analyzer.statusOf('first')).toBe('error'));
    expect(workers[0]!.terminate).toHaveBeenCalledOnce();
    analyzer.request('second', tags);
    await vi.waitFor(() => expect(analyzer.statusOf('second')).toBe('done'));
    expect(workers).toHaveLength(2);
    expect(analyzer.get('second')!.fileKey).toBe('second');
  });
});
