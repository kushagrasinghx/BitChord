import { afterEach, describe, expect, it, vi } from 'vitest';
import { modelSession, verifyModelBytes, type ModelSpec } from './modelSession';

const create = vi.hoisted(() => vi.fn());
vi.mock('onnxruntime-web/wasm', () => ({ env: { wasm: {} }, InferenceSession: { create } }));
const bytes = new Uint8Array([1, 2, 3, 4]);
const checksum = '9f64a747e1b97f131fabb6b447296c9b6f0201e79fb3c5356e6c77e89b6a806a';
const spec: ModelSpec = { asset: 'test.onnx', bytes: 4, sha256: checksum, input: 'spectrogram', outputs: { beat: [-1] } };

afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); vi.restoreAllMocks(); create.mockReset(); });
describe('pinned model sessions', () => {
  it('checks both the size and the content of downloaded weights', async () => {
    expect(await verifyModelBytes(bytes.buffer, spec)).toEqual(bytes);
    await expect(verifyModelBytes(new Uint8Array([4, 3, 2, 1]).buffer, spec)).rejects.toThrow('checksum');
    await expect(verifyModelBytes(new ArrayBuffer(3), spec)).rejects.toThrow('length');
  });
  it('retries transient failures after a minute and shares the recovered session', async () => {
    vi.useFakeTimers();
    vi.spyOn(console, 'warn').mockImplementation(() => {});
    const fetch = vi.fn().mockResolvedValueOnce(new Response(null, { status: 503 })).mockResolvedValue(new Response(bytes));
    vi.stubGlobal('fetch', fetch);
    const active = { inputNames: ['spectrogram'], outputNames: ['beat'], release: vi.fn() };
    create.mockResolvedValue(active);
    const retrySpec = { ...spec, asset: 'retry.onnx' };
    expect(await modelSession(retrySpec)).toBeNull();
    expect(await modelSession(retrySpec)).toBeNull();
    expect(fetch).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(60_001);
    const [first, second] = await Promise.all([modelSession(retrySpec), modelSession(retrySpec)]);
    expect(first).toBe(active);
    expect(second).toBe(active);
    expect(fetch).toHaveBeenCalledTimes(2);
    expect(create).toHaveBeenCalledTimes(1);
  });
  it('releases a graph with the wrong tensor contract', async () => {
    vi.spyOn(console, 'warn').mockImplementation(() => {});
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(bytes)));
    const active = { inputNames: ['wrong_input'], outputNames: ['beat'], release: vi.fn().mockResolvedValue(undefined) };
    create.mockResolvedValue(active);
    expect(await modelSession({ ...spec, asset: 'wrong-contract.onnx' })).toBeNull();
    expect(active.release).toHaveBeenCalledOnce();
  });
});
