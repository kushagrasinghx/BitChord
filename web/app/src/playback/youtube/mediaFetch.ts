import shaka from 'shaka-player/dist/shaka-player.compiled';
import { extensionFetch } from '@/extension/fetch';
import { extensionBrowser } from '@/lib/platform';
import { createRecoverableError, headersToGenericObject, makeResponse } from './shakaHelpers';

export function mediaFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const url = new URL(input instanceof Request ? input.url : input.toString());
  return extensionBrowser() === 'safari' && url.hostname.endsWith('.googlevideo.com')
    ? extensionFetch(input, init, false)
    : fetch(input, init);
}

/** Shaka's direct DASH path needs the same Safari transport as SABR. */
export const safariMediaPlugin: shaka.extern.SchemePlugin = (uri, request, type, progress, headersReceived, config) => {
  if (!new URL(uri).hostname.endsWith('.googlevideo.com')) {
    return shaka.net.HttpFetchPlugin.parse(uri, request, type, progress, headersReceived, config);
  }
  const controller = new AbortController();
  const started = performance.now();
  const promise = mediaFetch(uri, {
    method: request.method, headers: request.headers,
    body: request.body as BodyInit | undefined, signal: controller.signal,
  }).then(async response => {
    const headers = headersToGenericObject(response.headers);
    headersReceived(headers);
    const data = await response.arrayBuffer();
    progress(performance.now() - started, data.byteLength, 0);
    return makeResponse(headers, data, response.status, uri, response.url, request, type);
  }).catch(error => {
    if (controller.signal.aborted) {
      throw new shaka.util.Error(shaka.util.Error.Severity.RECOVERABLE,
        shaka.util.Error.Category.NETWORK, shaka.util.Error.Code.OPERATION_ABORTED, uri, type);
    }
    if (error instanceof shaka.util.Error) throw error;
    throw createRecoverableError('Safari media request failed', { cause: String(error) });
  });
  return new shaka.util.AbortableOperation(promise, async () => { controller.abort(); });
};
