import { config } from '@/config';

/**
 * Permission to keep one hidden folder in the listener's Google Drive, as the
 * Android app asks for it (DriveAuth.kt): the `drive.appdata` scope and nothing
 * else, a folder only BitChord can see, which the listener can size up and
 * delete from Drive's "Manage apps".
 *
 * In a browser that is Google Identity Services' token client: Google's own
 * popup, an access token good for an hour, no refresh token (that would need a
 * server). So a token is kept until it runs out, and after that only a click
 * can ask for another: browsers block a popup nobody asked for.
 */

const SCOPE_APPDATA = 'https://www.googleapis.com/auth/drive.appdata';
const GSI_SCRIPT = 'https://accounts.google.com/gsi/client';
const TOKEN_KEY = 'bitchord:drive:token';
/** Asked for again a little before Google's hour is up, so a sync never starts on a dying token. */
const EXPIRY_MARGIN_MS = 5 * 60_000;

/** The slice of GIS's API used here (developers.google.com/identity/oauth2/web/reference/js-reference). */
interface TokenResponse {
  access_token?: string;
  expires_in?: number | string;
  scope?: string;
  error?: string;
  error_description?: string;
}
interface TokenClient {
  requestAccessToken(overrides?: { prompt?: string; login_hint?: string }): void;
}
interface Gsi {
  accounts: {
    oauth2: {
      initTokenClient(config: {
        client_id: string;
        scope: string;
        login_hint?: string;
        callback: (response: TokenResponse) => void;
        error_callback?: (error: { type: string; message?: string }) => void;
      }): TokenClient;
      hasGrantedAllScopes(response: TokenResponse, scope: string): boolean;
      revoke(token: string, done?: () => void): void;
    };
  };
}

/** Drive sync is offered only with an OAuth client configured (web/README.md). */
export const driveAvailable = (): boolean => !!config.googleClientId;

let gsi: Promise<Gsi> | null = null;

function loadGsi(): Promise<Gsi> {
  gsi ??= new Promise<Gsi>((resolve, reject) => {
    const script = document.createElement('script');
    script.src = GSI_SCRIPT;
    script.async = true;
    script.onload = () => {
      const google = (window as unknown as { google?: Gsi }).google;
      if (google?.accounts?.oauth2) resolve(google);
      else reject(new Error('Google sign-in didn’t load'));
    };
    script.onerror = () => reject(new Error('Couldn’t reach Google sign-in'));
    document.head.append(script);
  }).catch((error: unknown) => {
    gsi = null;
    throw error;
  });
  return gsi;
}

interface Saved {
  token: string;
  expiresAt: number;
}

/** The token from the last sign-in, while it still has a few minutes in it. */
export function currentToken(): string | null {
  try {
    const saved = JSON.parse(localStorage.getItem(TOKEN_KEY) ?? 'null') as Saved | null;
    return saved && saved.expiresAt - EXPIRY_MARGIN_MS > Date.now() ? saved.token : null;
  } catch {
    return null;
  }
}

/** Drops a token Drive refused, so the next sync asks for a new one rather than reusing it. */
export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

/**
 * Google's popup, for a token. Must be called from a click. With [account] set
 * the popup is pinned to it, so a browser signed in to two Google accounts
 * never quietly backs up into the other (DriveAuth.authorize's setAccount).
 */
export async function requestToken(account: string | null): Promise<string> {
  const google = await loadGsi();
  const clientId = config.googleClientId;
  if (!clientId) throw new Error('Google Drive isn’t set up on this site');
  return new Promise<string>((resolve, reject) => {
    const client = google.accounts.oauth2.initTokenClient({
      client_id: clientId,
      scope: SCOPE_APPDATA,
      ...(account && { login_hint: account }),
      callback: (response) => {
        if (response.error || !response.access_token) {
          reject(new Error(response.error_description ?? response.error ?? 'Google didn’t return an access token'));
        } else if (!google.accounts.oauth2.hasGrantedAllScopes(response, SCOPE_APPDATA)) {
          reject(new Error('Google Drive access wasn’t granted'));
        } else {
          const saved: Saved = { token: response.access_token, expiresAt: Date.now() + Number(response.expires_in ?? 3600) * 1000 };
          localStorage.setItem(TOKEN_KEY, JSON.stringify(saved));
          resolve(saved.token);
        }
      },
      // The popup closed or was blocked: nothing granted, nothing to report beyond that.
      error_callback: (error) => reject(new Error(error.message ?? 'Google sign-in was closed')),
    });
    client.requestAccessToken({ prompt: account ? '' : 'select_account' });
  });
}

/**
 * Withdraws the grant itself, as "Disconnect" promises (DriveAuth.revoke):
 * Google's revoke ends the grant behind the token, not just the token. Best
 * effort; with no live token there is nothing to send it with.
 */
export async function revoke(): Promise<void> {
  const token = currentToken();
  clearToken();
  if (!token) return;
  const google = await loadGsi().catch(() => null);
  google?.accounts.oauth2.revoke(token);
}
