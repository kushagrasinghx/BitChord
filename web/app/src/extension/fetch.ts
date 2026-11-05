import { getBridge } from './bridge';

function decodeBase64(base64: string): Uint8Array<ArrayBuffer> {
  return Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
}

/** shortcut: buffers each response; use a streaming bridge if large media segments require it. */
export async function extensionFetch(input: RequestInfo | URL, init: RequestInit | undefined, auth: boolean): Promise<Response> {
  const request = input instanceof Request ? input : null;
  const url = new URL(request ? request.url : input.toString());
  const bridge = getBridge();
  if (!bridge) throw new Error('YouTube requests need the BitChord extension');
  const method = (init?.method ?? request?.method ?? 'GET').toUpperCase() as 'GET' | 'POST' | 'HEAD';
  const headers: Record<string, string> = {};
  new Headers(init?.headers ?? request?.headers).forEach((value, key) => (headers[key] = value));
  let body = init?.body ?? undefined;
  if (body === undefined && request && method !== 'GET' && method !== 'HEAD') body = await request.arrayBuffer();
  let bodyEncoding: 'text' | 'base64' = 'text';
  if (body != null && typeof body !== 'string') {
    const bytes = new Uint8Array(await new Response(body).arrayBuffer());
    let binary = '';
    for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
    body = btoa(binary);
    bodyEncoding = 'base64';
  }

  const response = await bridge.call(
    'yt.fetch',
    { url: url.toString(), method, headers, ...(typeof body === 'string' && { body, bodyEncoding }), auth },
    { signal: init?.signal ?? request?.signal ?? undefined },
  );
  const payload = response.bodyEncoding === 'base64' ? decodeBase64(response.body) : response.body;
  const result = new Response(method === 'HEAD' || response.status === 204 || response.status === 304 ? null : payload, {
    status: response.status,
    headers: response.headers,
  });
  Object.defineProperty(result, 'url', { value: response.url });
  return result;
}

