import {
  BRIDGE_CHANNEL,
  BRIDGE_PROTOCOL,
  EVENTS_PORT,
  isBridgeEventMessage,
  isBridgeResponse,
  type BridgeError,
  type BridgeEvent,
  type BridgeMethod,
  type BridgeRequest,
  type BridgeResponse,
  type ParamsOf,
  type ResultOf,
} from './protocol';

/**
 * Page-side bridge. Two transports behind one call (prompt §4.2):
 *
 * - **Chromium**: `externally_connectable` lets the page message the extension
 *   directly with `chrome.runtime.sendMessage(extensionId, …)`. Chrome only
 *   exposes `chrome.runtime` to a page when some installed extension lists the
 *   page's origin, so its absence already means "not installed".
 * - **Firefox** has no `externally_connectable` for web pages. A content script
 *   on our origin relays `window.postMessage` ↔ `runtime.sendMessage`.
 */
export interface Bridge {
  readonly transport: 'external' | 'relay';
  call<M extends BridgeMethod>(method: M, params: ParamsOf<M>, options?: CallOptions): Promise<ResultOf<M>>;
  /**
   * The extension's pushes (auth changes now, Discord status later). Returns
   * the unsubscribe. Delivery is best effort: an MV3 worker that sleeps drops
   * its ports, so callers still re-read state when the tab regains focus.
   */
  subscribe(listener: (event: BridgeEvent) => void): () => void;
}

export interface CallOptions {
  timeoutMs?: number;
  signal?: AbortSignal;
}

export class BridgeCallError extends Error {
  readonly code: BridgeError['code'] | 'timeout' | 'unavailable';

  constructor(code: BridgeCallError['code'], message: string) {
    super(message);
    this.name = 'BridgeCallError';
    this.code = code;
  }
}

const DEFAULT_TIMEOUT_MS = 30_000;

let nextId = 0;
const newId = () => `${Date.now().toString(36)}-${(nextId++).toString(36)}`;

function envelope<M extends BridgeMethod>(method: M, params: ParamsOf<M>): BridgeRequest<M> {
  return { channel: BRIDGE_CHANNEL, protocol: BRIDGE_PROTOCOL, direction: 'to-extension', id: newId(), method, params };
}

function unwrap<M extends BridgeMethod>(response: BridgeResponse<M>): ResultOf<M> {
  if (response.ok) return response.result;
  throw new BridgeCallError(response.error.code, response.error.message);
}

/**
 * Runs `start`, settling with its result unless the timeout or the abort
 * signal fires first. `start` receives `settle` and returns a cleanup.
 */
function withDeadline<T>(
  options: CallOptions | undefined,
  start: (settle: (outcome: { value: T } | { error: unknown }) => void) => () => void,
): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    let done = false;
    let cleanup = () => {};
    const finish = (outcome: { value: T } | { error: unknown }) => {
      if (done) return;
      done = true;
      clearTimeout(timer);
      options?.signal?.removeEventListener('abort', onAbort);
      cleanup();
      if ('value' in outcome) resolve(outcome.value);
      else reject(outcome.error);
    };
    const timeoutMs = options?.timeoutMs ?? DEFAULT_TIMEOUT_MS;
    const timer = setTimeout(
      () => finish({ error: new BridgeCallError('timeout', `No answer from the extension within ${timeoutMs} ms`) }),
      timeoutMs,
    );
    const onAbort = () => finish({ error: options?.signal?.reason ?? new DOMException('Aborted', 'AbortError') });
    if (options?.signal?.aborted) return onAbort();
    options?.signal?.addEventListener('abort', onAbort, { once: true });
    cleanup = start(finish);
  });
}

// ---- Chromium: externally_connectable ---------------------------------------

interface ChromePortLike {
  onMessage: { addListener(listener: (message: unknown) => void): void };
  onDisconnect: { addListener(listener: () => void): void };
  disconnect(): void;
}

interface ChromeRuntimeLike {
  sendMessage(extensionId: string, message: unknown, callback: (response: unknown) => void): void;
  connect?(extensionId: string, info: { name: string }): ChromePortLike;
  readonly lastError?: { message?: string } | undefined;
}

/** How long a dropped event port waits before reconnecting, while the page is visible. */
const RECONNECT_MS = 2000;

/**
 * Keeps one event port open while subscribed. When the worker drops it (it
 * sleeps after ~30 s idle), it reconnects, but only while the page is
 * visible: a background tab shouldn't keep waking the extension.
 */
function keepPortOpen(open: () => ChromePortLike | null, listener: (event: BridgeEvent) => void): () => void {
  let port: ChromePortLike | null = null;
  let timer: ReturnType<typeof setTimeout> | undefined;
  let stopped = false;

  const connect = () => {
    if (stopped || port) return;
    if (typeof document !== 'undefined' && document.hidden) return; // resumed by visibilitychange
    port = open();
    if (!port) return;
    port.onMessage.addListener((message) => {
      const event = message as BridgeEvent | null;
      if (event && typeof event === 'object' && typeof event.event === 'string') listener(event);
    });
    port.onDisconnect.addListener(() => {
      // Reading lastError marks the drop as handled (Chrome logs it otherwise).
      void (globalThis as { chrome?: { runtime?: { lastError?: unknown } } }).chrome?.runtime?.lastError;
      port = null;
      if (!stopped) timer = setTimeout(connect, RECONNECT_MS);
    });
  };
  const onVisible = () => {
    if (!document.hidden) connect();
  };

  connect();
  if (typeof document !== 'undefined') document.addEventListener('visibilitychange', onVisible);
  return () => {
    stopped = true;
    clearTimeout(timer);
    if (typeof document !== 'undefined') document.removeEventListener('visibilitychange', onVisible);
    port?.disconnect();
    port = null;
  };
}

function chromeRuntime(): ChromeRuntimeLike | null {
  const runtime = (globalThis as { chrome?: { runtime?: Partial<ChromeRuntimeLike> } }).chrome?.runtime;
  return typeof runtime?.sendMessage === 'function' ? (runtime as ChromeRuntimeLike) : null;
}

export function externalBridge(extensionId: string): Bridge | null {
  const runtime = chromeRuntime();
  if (!runtime || !extensionId) return null;
  return {
    transport: 'external',
    call(method, params, options) {
      const request = envelope(method, params);
      return withDeadline(options, (settle) => {
        runtime.sendMessage(extensionId, request, (response) => {
          // Reading lastError is what tells Chrome the failure was handled.
          const failure = runtime.lastError?.message;
          if (failure) return settle({ error: new BridgeCallError('unavailable', failure) });
          if (!isBridgeResponse(response) || response.id !== request.id) {
            return settle({ error: new BridgeCallError('protocol', 'Malformed response from the extension') });
          }
          try {
            settle({ value: unwrap(response as BridgeResponse<typeof method>) });
          } catch (error) {
            settle({ error });
          }
        });
        return () => {};
      });
    },
    subscribe(listener) {
      return keepPortOpen(() => {
        try {
          return runtime.connect?.(extensionId, { name: EVENTS_PORT }) ?? null;
        } catch {
          return null;
        }
      }, listener);
    },
  };
}

// ---- Firefox: content-script relay over window.postMessage --------------------

export function relayBridge(target: Window = window): Bridge {
  return {
    transport: 'relay',
    call(method, params, options) {
      const request = envelope(method, params);
      return withDeadline(options, (settle) => {
        const onMessage = (event: MessageEvent) => {
          // Only our own window, and only replies addressed to this request.
          if (event.source !== target || event.origin !== target.location.origin) return;
          if (!isBridgeResponse(event.data) || event.data.id !== request.id) return;
          try {
            settle({ value: unwrap(event.data as BridgeResponse<typeof method>) });
          } catch (error) {
            settle({ error });
          }
        };
        target.addEventListener('message', onMessage);
        target.postMessage(request, target.location.origin);
        return () => target.removeEventListener('message', onMessage);
      });
    },
    subscribe(listener) {
      // The relay content script holds the port and forwards each push.
      const onMessage = (event: MessageEvent) => {
        if (event.source !== target || event.origin !== target.location.origin) return;
        if (isBridgeEventMessage(event.data)) listener(event.data.payload);
      };
      target.addEventListener('message', onMessage);
      return () => target.removeEventListener('message', onMessage);
    },
  };
}

// ---- Detection ----------------------------------------------------------------

export interface DetectedExtension {
  bridge: Bridge;
  info: ResultOf<'ping'>;
}

export interface DetectOptions {
  /** The Chromium extension id (fixed by the manifest `key` in development). */
  chromiumExtensionId?: string | undefined;
  /** How long to wait for a `ping` answer on each transport. */
  timeoutMs?: number;
}

/**
 * Finds the extension by pinging it: the external transport first when Chrome
 * exposes one, then the relay. Resolves `null` when nothing answers.
 */
export async function detectExtension(options: DetectOptions = {}): Promise<DetectedExtension | null> {
  const timeoutMs = options.timeoutMs ?? 1500;
  const candidates: Bridge[] = [];
  const external = options.chromiumExtensionId ? externalBridge(options.chromiumExtensionId) : null;
  if (external) candidates.push(external);
  if (typeof window !== 'undefined') candidates.push(relayBridge(window));

  for (const bridge of candidates) {
    try {
      const info = await bridge.call('ping', {}, { timeoutMs });
      return { bridge, info };
    } catch {
      // Not on this transport; try the next.
    }
  }
  return null;
}
