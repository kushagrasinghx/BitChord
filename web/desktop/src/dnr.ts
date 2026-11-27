import type { OnBeforeSendHeadersListenerDetails, OnHeadersReceivedListenerDetails, Session } from 'electron';

/**
 * The slice of declarativeNetRequest the extension's rules use (lib/rules.ts,
 * lib/lyricsFetch.ts, lib/spotify.ts, lib/discord.ts), applied through
 * Electron's webRequest: `modifyHeaders` rules matched by `urlFilter`,
 * `initiatorDomains`, `tabIds` and `resourceTypes`.
 *
 * "Tabs" here: the app's own requests come from its window; `tabIds: [-1]`
 * (the extension's own fetches) are the main process's, which carry
 * OWN_REQUEST_HEADER (browserShim.ts's fetch) and nothing else does.
 */

type Rule = Browser.declarativeNetRequest.Rule;
type HeaderInfo = Browser.declarativeNetRequest.ModifyHeaderInfo;

/** Set on every request the main process makes, and removed before it leaves. */
export const OWN_REQUEST_HEADER = 'X-BitChord-Own';

interface Compiled {
  rule: Rule;
  url: RegExp | null;
}

const dynamicRules = new Map<number, Compiled>();
const sessionRules = new Map<number, Compiled>();

/**
 * `||host/path*part` → a regex over the whole URL (DNR's urlFilter syntax:
 * `||` anchors a domain, `|` the start or end, `*` is anything, `^` a separator).
 */
export function compileUrlFilter(filter: string): RegExp {
  let source = '';
  let rest = filter;
  if (rest.startsWith('||')) {
    source = '^[a-z][a-z0-9+.-]*://(?:[^/?#]*\\.)?';
    rest = rest.slice(2);
  } else if (rest.startsWith('|')) {
    source = '^';
    rest = rest.slice(1);
  }
  const anchoredEnd = rest.endsWith('|');
  if (anchoredEnd) rest = rest.slice(0, -1);
  for (const char of rest) {
    if (char === '*') source += '.*';
    else if (char === '^') source += '(?:[^a-zA-Z0-9_.%-]|$)';
    else source += char.replace(/[.*+?^${}()|[\]\\/]/g, '\\$&');
  }
  if (anchoredEnd) source += '$';
  return new RegExp(source, 'i');
}

function compile(rule: Rule): Compiled {
  return { rule, url: rule.condition.urlFilter ? compileUrlFilter(rule.condition.urlFilter) : null };
}

export function updateRules(
  scope: 'dynamic' | 'session',
  options: { removeRuleIds?: number[]; addRules?: Rule[] },
): void {
  const rules = scope === 'dynamic' ? dynamicRules : sessionRules;
  for (const id of options.removeRuleIds ?? []) rules.delete(id);
  for (const rule of options.addRules ?? []) rules.set(rule.id, compile(rule));
}

/** Electron's resourceType → DNR's. Fetches from workers come through as `other`. */
function resourceTypeOf(type: string): string {
  switch (type) {
    case 'xhr':
    case 'other':
      return 'xmlhttprequest';
    case 'mainFrame':
      return 'main_frame';
    case 'subFrame':
      return 'sub_frame';
    case 'webSocket':
      return 'websocket';
    case 'cspReport':
      return 'csp_report';
    default:
      return type;
  }
}

export interface RequestContext {
  url: string;
  resourceType: string;
  /** The extension's own (main-process) request, as `tabIds: [-1]` means. */
  own: boolean;
  /** The hostname of the page that made it, for `initiatorDomains`. */
  initiator: string | null;
}

function matches({ rule, url }: Compiled, request: RequestContext): boolean {
  const { condition } = rule;
  if (url && !url.test(request.url)) return false;
  if (condition.resourceTypes && !condition.resourceTypes.includes(resourceTypeOf(request.resourceType) as never)) {
    return false;
  }
  if (condition.tabIds && !(request.own && condition.tabIds.includes(-1))) return false;
  if (condition.initiatorDomains) {
    const host = request.initiator;
    if (request.own || !host) return false;
    if (!condition.initiatorDomains.some((domain) => host === domain || host.endsWith(`.${domain}`))) return false;
  }
  return true;
}

/** Every matching rule, highest priority first (as DNR applies modifyHeaders). */
function matching(request: RequestContext): Rule[] {
  const found: Rule[] = [];
  for (const rules of [sessionRules, dynamicRules]) {
    for (const compiled of rules.values()) {
      if (compiled.rule.action.type === 'modifyHeaders' && matches(compiled, request)) found.push(compiled.rule);
    }
  }
  return found.sort((a, b) => (b.priority ?? 1) - (a.priority ?? 1));
}

/**
 * Applies `infos` to `headers` (case-insensitively), skipping headers a
 * higher-priority rule already changed: DNR lets the first one win.
 */
function apply(headers: Record<string, string | string[]>, infos: HeaderInfo[], touched: Set<string>): void {
  for (const info of infos) {
    const name = info.header.toLowerCase();
    if (touched.has(name)) continue;
    touched.add(name);
    for (const key of Object.keys(headers)) if (key.toLowerCase() === name) delete headers[key];
    if (info.operation === 'set' && info.value !== undefined) headers[info.header] = info.value;
    if (info.operation === 'append' && info.value !== undefined) headers[info.header] = info.value;
  }
}

function headerValue(headers: Record<string, string | string[]>, name: string): string | undefined {
  const lower = name.toLowerCase();
  for (const [key, value] of Object.entries(headers)) {
    if (key.toLowerCase() === lower) return Array.isArray(value) ? value[0] : value;
  }
  return undefined;
}

function hostnameOf(url: string | undefined | null): string | null {
  if (!url || url === 'null') return null;
  try {
    return new URL(url).hostname || null;
  } catch {
    return null;
  }
}

export interface EngineOptions {
  /** Whether a webContents is one of the app's own windows (the site's "tab"). */
  isAppContents(id: number | undefined): boolean;
  /** The hostname `initiatorDomains` should see for the app's requests. */
  appHost(): string;
  /** Extra rules on top of the extension's, applied to the app's own requests (main.ts). */
  extraRequestHeaders?(request: RequestContext, headers: Record<string, string | string[]>): void;
  extraResponseHeaders?(request: RequestContext, headers: Record<string, string[]>): void;
}

/** Which page a request came from: the app's window, or else the Origin or Referer it carries. */
function contextOf(
  details: OnBeforeSendHeadersListenerDetails | OnHeadersReceivedListenerDetails,
  options: EngineOptions,
  own: boolean,
  requestHeaders?: Record<string, string>,
): RequestContext {
  let initiator: string | null = null;
  if (!own) {
    if (options.isAppContents(details.webContentsId)) initiator = options.appHost();
    else {
      initiator =
        hostnameOf(details.frame?.origin) ??
        hostnameOf(requestHeaders && headerValue(requestHeaders, 'Origin')) ??
        hostnameOf(details.referrer);
    }
  }
  return { url: details.url, resourceType: details.resourceType, own, initiator };
}

/** Request ids of the main process's own requests, so their responses match `tabIds: [-1]` too. */
const ownRequests = new Set<number>();

export function installEngine(session: Session, options: EngineOptions): void {
  session.webRequest.onBeforeSendHeaders((details, callback) => {
    const headers: Record<string, string | string[]> = { ...details.requestHeaders };
    const own = headerValue(headers, OWN_REQUEST_HEADER) !== undefined;
    if (own) {
      ownRequests.add(details.id);
      for (const key of Object.keys(headers)) if (key.toLowerCase() === OWN_REQUEST_HEADER.toLowerCase()) delete headers[key];
    }
    const request = contextOf(details, options, own, details.requestHeaders);
    const touched = new Set<string>();
    for (const rule of matching(request)) apply(headers, rule.action.requestHeaders ?? [], touched);
    options.extraRequestHeaders?.(request, headers);
    callback({ requestHeaders: headers as Record<string, string> });
  });

  session.webRequest.onHeadersReceived((details, callback) => {
    const own = ownRequests.has(details.id);
    const request = contextOf(details, options, own);
    const rules = matching(request).filter((rule) => rule.action.responseHeaders?.length);
    if (!rules.length && !options.extraResponseHeaders) return callback({});
    const headers: Record<string, string[]> = { ...(details.responseHeaders ?? {}) };
    const touched = new Set<string>();
    for (const rule of rules) apply(headers as Record<string, string | string[]>, rule.action.responseHeaders ?? [], touched);
    for (const [key, value] of Object.entries(headers)) if (typeof value === 'string') headers[key] = [value];
    options.extraResponseHeaders?.(request, headers);
    callback({ responseHeaders: headers });
  });

  const forget = (details: { id: number }) => ownRequests.delete(details.id);
  session.webRequest.onCompleted(forget);
  session.webRequest.onErrorOccurred(forget);
}
