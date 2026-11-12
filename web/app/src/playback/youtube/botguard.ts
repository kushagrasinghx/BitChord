// BotGuard and WebPO tokens, ported from googlevideo's sabr-shaka-example
// (src/BotguardService.ts) and Kira, https://github.com/LuanRT, MIT License.
//
// BotGuard runs in the page because the user's real browser is where it
// expects to run (prompt §5.1). Create is a plain fetch: www.youtube.com/api/jnn
// allows cross-origin requests on its own. GenerateIT goes through the
// extension so it carries YouTube's Origin: an integrity token minted for
// another origin is accepted by nothing (measured: every stream, SABR or
// direct, was cut off at ~70s, googlevideo's limit for an unattested session).
// Kira sends GenerateIT through its extension too; Android runs BotGuard in a
// WebView on youtube.com.

import type { BotGuardClient } from 'bgutils-js/botguard';
import type { IBotguardClientSideBgChallenge, WebPoSignalOutput } from 'bgutils-js/shared-types';
import type { WebPoMinter } from 'bgutils-js/webpo';
import { getBridge } from '@/extension/bridge';
import { timed, trackLog } from '../trackLog';

const REQUEST_KEY = 'O43z0dpjhgX20SCx4KAo';

/** Renew the integrity token this long before its stated lifetime ends (PoTokenWebView: 10 minutes). */
const RENEW_MARGIN_MS = 10 * 60_000;
/** Only if GenerateIT ever omits the lifetime; a conservative guess, not a measured value. */
const DEFAULT_TTL_MS = 60 * 60_000;

class BotguardService {
  private client?: BotGuardClient;
  private challenge?: IBotguardClientSideBgChallenge;
  private minter?: WebPoMinter;
  private expiresAt = 0;
  private initializing: Promise<void> | null = null;
  /** Minted tokens by content binding (the videoId): one BotGuard run serves a track's whole session. */
  private tokens = new Map<string, string>();

  /** Starts BotGuard if it isn't running or its integrity token is about to lapse. Safe to call often. */
  init(): Promise<void> {
    if (this.minter && Date.now() < this.expiresAt - RENEW_MARGIN_MS) return Promise.resolve();
    this.initializing ??= timed('BotGuard init', null, () => this.setup()).finally(() => {
      this.initializing = null;
    });
    return this.initializing;
  }

  private async setup(): Promise<void> {
    this.dispose();
    const [{ buildURL, GOOG_API_KEY }, { BotGuardClient, parseChallengeData }, { WebPoMinter }] = await Promise.all([
      import('bgutils-js/utils'),
      import('bgutils-js/botguard'),
      import('bgutils-js/webpo'),
    ]);
    const headers = {
      'content-type': 'application/json+protobuf',
      'x-goog-api-key': GOOG_API_KEY,
      'x-user-agent': 'grpc-web-javascript/0.1',
    };

    const challengeResponse = await fetch(buildURL('Create', true), {
      method: 'POST',
      headers,
      body: JSON.stringify([REQUEST_KEY]),
    });
    const challenge = parseChallengeData(await challengeResponse.json());
    if (!challenge) throw new Error('BotGuard returned no challenge');
    this.challenge = challenge;

    const interpreter = challenge.interpreterJavascript?.privateDoNotAccessOrElseSafeScriptWrappedValue;
    if (!interpreter) throw new Error(`No BotGuard interpreter (hash ${challenge.interpreterHash})`);
    if (!document.getElementById(challenge.interpreterHash!)) {
      const script = document.createElement('script');
      script.type = 'text/javascript';
      script.id = challenge.interpreterHash!;
      script.textContent = interpreter;
      document.head.appendChild(script);
    }

    this.client = await BotGuardClient.create({
      globalObject: globalThis,
      globalName: challenge.globalName,
      program: challenge.program,
    });

    const webPoSignalOutput: WebPoSignalOutput = [];
    const botguardResponse = await this.client.snapshot({ webPoSignalOutput });
    const bridge = getBridge();
    if (!bridge) throw new Error('BotGuard needs the extension to request an integrity token');
    const integrityResponse = await bridge.call('yt.fetch', {
      url: buildURL('GenerateIT', true),
      method: 'POST',
      headers,
      body: JSON.stringify([REQUEST_KEY, botguardResponse]),
    });
    if (integrityResponse.status !== 200) throw new Error(`GenerateIT answered ${integrityResponse.status}`);
    const [integrityToken, ttlSeconds] = JSON.parse(integrityResponse.body) as [string?, number?];
    if (!integrityToken) throw new Error(`No integrity token (interpreter ${challenge.interpreterHash})`);

    this.minter = await WebPoMinter.create({ integrityToken }, webPoSignalOutput);
    this.expiresAt = Date.now() + (typeof ttlSeconds === 'number' ? ttlSeconds * 1000 : DEFAULT_TTL_MS);
    this.tokens.clear();
    trackLog.i(`BotGuard ready; integrity token valid for ${Math.round((this.expiresAt - Date.now()) / 60_000)} min`);
  }

  /**
   * A placeholder token YouTube accepts while the real one is minted, so a
   * VOD never waits on BotGuard to start (Kira's approach).
   */
  async coldStartToken(contentBinding: string): Promise<string> {
    const { createColdStartToken } = await import('bgutils-js/webpo');
    return createColdStartToken(contentBinding);
  }

  /**
   * The real WebPO token for [contentBinding], minted once and remembered. A
   * mint that fails gets one retry on a fresh BotGuard, as PoTokenGenerator does.
   */
  async mint(contentBinding: string): Promise<string | undefined> {
    const known = this.tokens.get(contentBinding);
    if (known && Date.now() < this.expiresAt - RENEW_MARGIN_MS) return known;
    for (let attempt = 0; attempt < 2; attempt++) {
      try {
        if (attempt > 0) this.expiresAt = 0; // force a fresh BotGuard
        await this.init();
        if (!this.minter) return undefined;
        const token = await this.minter.mintAsWebsafeString(contentBinding);
        this.tokens.set(contentBinding, token);
        trackLog.d('WebPO token minted', contentBinding);
        return token;
      } catch (error) {
        trackLog.w(`WebPO mint failed (attempt ${attempt + 1}): ${String(error)}`, contentBinding);
      }
    }
    return undefined;
  }

  private dispose() {
    this.client?.shutdown();
    this.client = undefined;
    this.minter = undefined;
    if (this.challenge?.interpreterHash) document.getElementById(this.challenge.interpreterHash)?.remove();
    this.challenge = undefined;
  }
}

export const botguard = new BotguardService();
