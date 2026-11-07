import * as ort from 'onnxruntime-web/wasm';
import ortWasmUrl from 'onnxruntime-web/ort-wasm-simd-threaded.wasm?url';

ort.env.wasm.wasmPaths = { wasm: ortWasmUrl };
ort.env.wasm.numThreads = 1; // The site does not require cross-origin isolation.

export interface ModelSpec {
  asset: string;
  bytes: number;
  sha256: string;
  input: string;
  outputs: Readonly<Record<string, readonly number[]>>;
}
const RETRY_MS = 60_000;
const sessions = new Map<string, { retryAt: number; promise: Promise<ort.InferenceSession | null> }>();

/** Verify downloads before parsing: a mixed deployment must not silently run stale weights. */
export async function verifyModelBytes(bytes: ArrayBuffer, spec: Pick<ModelSpec, 'bytes' | 'sha256'>): Promise<Uint8Array> {
  if (bytes.byteLength !== spec.bytes) throw new Error('model length mismatch');
  const hash = new Uint8Array(await crypto.subtle.digest('SHA-256', bytes));
  const checksum = Array.from(hash, (b) => b.toString(16).padStart(2, '0')).join('');
  if (checksum !== spec.sha256) throw new Error('model checksum mismatch');
  return new Uint8Array(bytes);
}

/** One session per pinned model; a transient download failure can be retried on a later track. */
export function modelSession(spec: ModelSpec): Promise<ort.InferenceSession | null> {
  const key = `${spec.asset}:${spec.sha256}`;
  const known = sessions.get(key);
  if (known && Date.now() < known.retryAt) return known.promise;
  const entry = { retryAt: Infinity, promise: Promise.resolve<ort.InferenceSession | null>(null) };
  entry.promise = (async () => {
    const url = `${import.meta.env.BASE_URL}models/${spec.asset}`;
    const response = await fetch(url);
    if (!response.ok) throw new Error(`model HTTP ${response.status}`);
    const bytes = await verifyModelBytes(await response.arrayBuffer(), spec);
    const active = await ort.InferenceSession.create(bytes, { executionProviders: ['wasm'], graphOptimizationLevel: 'all' });
    const outputs = Object.keys(spec.outputs);
    if (active.inputNames.length !== 1 || active.inputNames[0] !== spec.input
      || active.outputNames.length !== outputs.length || !outputs.every((name) => active.outputNames.includes(name))) {
      await active.release();
      throw new Error('model tensor names mismatch');
    }
    return active;
  })().catch((error: unknown) => {
    entry.retryAt = Date.now() + RETRY_MS;
    console.warn(`automix: model ${spec.asset} unavailable`, error);
    return null;
  });
  sessions.set(key, entry);
  return entry.promise;
}
