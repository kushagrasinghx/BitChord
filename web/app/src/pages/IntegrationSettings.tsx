import type { DiscordPresenceOptions } from '@bitchord/shared/bridge';
import { APPLE_ORIGINS, useAppleAccess } from '@/data/apple/artistArt';
import { useAppleSettings } from '@/data/apple/motionArt';
import { useHostAccess } from '@/extension/useHostAccess';
import { LYRIC_EXTENSION_ORIGINS } from '@/lyrics/http';
import { useSpotifySettings } from '@/data/spotify/canvas';
import {
  useDiscord,
  useDiscordConnect,
  useDiscordDisconnect,
  useDiscordEnabled,
  useDiscordOptions,
  type DiscordStatus,
} from '@/extension/useDiscord';
import {
  openSpotifySignIn,
  useRefreshSpotifyAuth,
  useSetSpotifyConnected,
  useSpotifyAuth,
  type SpotifyAuthStatus,
} from '@/extension/useSpotifyAuth';
import { settingsUi } from './settingsStyles';
import { Select } from '@/components/Select';

/** Allowed, or an Allow button that opens the extension's permission page. */
function HostAccessValue({ access, request }: ReturnType<typeof useHostAccess>) {
  const text = { checking: 'Checking…', unavailable: 'Needs the extension', granted: 'Allowed', needed: 'Not allowed' }[access];
  return (
    <span className={settingsUi.value}>
      <span>{text}</span>
      {access === 'needed' && (
        <button className={settingsUi.inlineButton} onClick={request}>
          Allow
        </button>
      )}
    </span>
  );
}

/** The Apple Music section's access row: one grant covers artist art and animated artwork. */
export function AppleMusicIntegration() {
  return <HostAccessValue {...useAppleAccess()} />;
}

/** The Apple Music section's switches. Neither needs an Apple Music account. */
export function AppleMusicToggles() {
  const { artistArt, motion, setArtistArt, setMotion } = useAppleSettings();
  return (
    <>
      <Toggle
        label="Artist Art"
        detail="Artist pages take Apple Music’s photo, logo and colour."
        checked={artistArt}
        onChange={setArtistArt}
      />
      <Toggle
        label="Animated Artwork"
        detail="Apple Music’s moving covers in the full-screen player and on album pages. Not on metered connections or with reduced motion."
        checked={motion}
        onChange={setMotion}
      />
    </>
  );
}

/**
 * The lyric sources a page can't ask itself (KuGou, Musixmatch, Genius,
 * PaxSenix…), as in the Lyrics dialog. Apple's hosts are granted under Apple
 * Music, so they aren't asked for twice.
 */
const LYRIC_ONLY_ORIGINS = LYRIC_EXTENSION_ORIGINS.filter((o) => !APPLE_ORIGINS.includes(o));
export function LyricSourcesAccess() {
  return <HostAccessValue {...useHostAccess(LYRIC_ONLY_ORIGINS)} />;
}

/** The Spotify row's words and button (SpotifyLibraryScreen's states). */
export function SpotifyIntegration() {
  const auth = useSpotifyAuth();
  const connect = useSetSpotifyConnected();
  const retry = useRefreshSpotifyAuth();
  if (auth.state !== 'ready') return <span className={settingsUi.value}>{spotifyText(auth)}</span>;
  const { browserSignedIn, connected, signedIn } = auth.auth;
  const action = !browserSignedIn
    ? { label: 'Sign In', run: openSpotifySignIn }
    : !connected
      ? { label: 'Connect', run: () => connect.mutate(true) }
      : !signedIn
        ? { label: 'Retry', run: () => retry.mutate() }
        : { label: 'Disconnect', run: () => connect.mutate(false) };
  return (
    <span className={settingsUi.value}>
      <span title={auth.auth.problem ?? undefined}>{spotifyText(auth)}</span>
      <button className={settingsUi.inlineButton} onClick={action.run} disabled={connect.isPending || retry.isPending}>
        {action.label}
      </button>
    </span>
  );
}

function spotifyText(auth: SpotifyAuthStatus): string {
  if (auth.state === 'checking') return 'Checking…';
  if (auth.state === 'unavailable') return 'Not available in this version yet';
  const { browserSignedIn, connected, signedIn, user } = auth.auth;
  if (!browserSignedIn) return 'Not signed in in this browser';
  if (!connected) return 'Disconnected';
  return signedIn ? `Signed in as ${user?.name ?? 'your account'}` : 'Couldn’t read your account';
}

export function DiscordIntegration() {
  const status = useDiscord();
  const connect = useDiscordConnect();
  const disconnect = useDiscordDisconnect();
  const text = discordText(status);
  if (status.state !== 'ready' || !status.discord.available) return <span className={settingsUi.value}>{text}</span>;
  const connected = status.discord.connected;
  return (
    <span className={settingsUi.value}>
      <span title={status.discord.problem ?? undefined}>{text}</span>
      <button
        className={settingsUi.inlineButton}
        onClick={() => (connected ? disconnect.mutate() : connect.mutate())}
        disabled={connect.isPending || disconnect.isPending}
      >
        {connected ? 'Disconnect' : 'Connect'}
      </button>
    </span>
  );
}

function discordText(status: DiscordStatus): string {
  if (status.state === 'checking') return 'Checking…';
  if (status.state === 'unavailable') return 'Not in this build of the extension';
  const { available, connected, enabled, user, gateway, problem } = status.discord;
  if (!available) return 'Not in this build of the extension';
  if (!connected || !user) return 'Not connected';
  if (!enabled) return `${user.name} · presence off`;
  if (gateway === 'error') return problem ?? 'Couldn’t connect';
  if (gateway === 'ready') return `${user.name} · showing what you play`;
  if (gateway === 'connecting') return `${user.name} · connecting…`;
  return `${user.name} · on`;
}

export function Toggle({ label, detail, checked, onChange }: { label: string; detail?: string; checked: boolean; onChange: (on: boolean) => void }) {
  return (
    <label className={settingsUi.controlRow}>
      <span className={settingsUi.rowText}>
        <span>{label}</span>
        {detail && <span className={settingsUi.rowDetail}>{detail}</span>}
      </span>
      <input type="checkbox" className={settingsUi.switch} checked={checked} onChange={(e) => onChange(e.currentTarget.checked)} />
    </label>
  );
}

/** Spotify Canvas behind the lyrics; only offered once signed in to Spotify. */
export function CanvasToggle() {
  const auth = useSpotifyAuth();
  const canvas = useSpotifySettings((s) => s.canvas);
  const setCanvas = useSpotifySettings((s) => s.setCanvas);
  if (auth.state !== 'ready' || !auth.auth.signedIn) return null;
  return (
    <Toggle
      label="Spotify Canvas"
      detail="Spotify’s looping clip behind the lyrics. Not on metered connections or with reduced motion."
      checked={canvas}
      onChange={setCanvas}
    />
  );
}

const ACTIVITY_TYPES: { value: DiscordPresenceOptions['activityType']; label: string }[] = [
  { value: 'listening', label: 'Listening to' },
  { value: 'playing', label: 'Playing' },
  { value: 'watching', label: 'Watching' },
  { value: 'competing', label: 'Competing in' },
];

const STATUSES: { value: DiscordPresenceOptions['status']; label: string }[] = [
  { value: 'online', label: 'Online' },
  { value: 'idle', label: 'Idle' },
  { value: 'dnd', label: 'Do Not Disturb' },
  { value: 'invisible', label: 'Invisible' },
];

/** Discord presence (prompt §7.3): opt-in, with the one-line warning, then how the card reads. */
export function DiscordSettings() {
  const status = useDiscord();
  const setEnabled = useDiscordEnabled();
  const options = useDiscordOptions((s) => s.options);
  const set = useDiscordOptions((s) => s.set);
  if (status.state !== 'ready' || !status.discord.available || !status.discord.connected) return null;
  const on = status.discord.enabled;
  return (
    <>
      <section className={settingsUi.group}>
        <Toggle label="Show what you’re playing" checked={on} onChange={(v) => setEnabled.mutate(v)} />
        {on && (
          <>
            <label className={settingsUi.controlRow}>
              <span>Activity</span>
              <Select
                label="Activity"
                value={options.activityType}
                options={ACTIVITY_TYPES}
                onChange={(activityType) => set({ activityType })}
              />
            </label>
            <label className={settingsUi.controlRow}>
              <span>Name</span>
              <input
                className={settingsUi.input}
                placeholder="BitChord"
                value={options.activityName}
                onChange={(e) => set({ activityName: e.currentTarget.value })}
              />
            </label>
            <label className={settingsUi.controlRow}>
              <span>Status</span>
              <Select label="Status" value={options.status} options={STATUSES} onChange={(status) => set({ status })} />
            </label>
            <Toggle
              label="Lead with the song"
              detail="Member lists show the song title instead of the artist."
              checked={options.useDetails}
              onChange={(v) => set({ useDetails: v })}
            />
            <Toggle label="Listen button" checked={options.button1Visible} onChange={(v) => set({ button1Visible: v })} />
            {options.button1Visible && (
              <label className={settingsUi.controlRow}>
                <span>Listen button text</span>
                <input
                  className={settingsUi.input}
                  placeholder="Listen on YouTube Music"
                  value={options.button1Text}
                  onChange={(e) => set({ button1Text: e.currentTarget.value })}
                />
              </label>
            )}
            <Toggle label="BitChord button" checked={options.button2Visible} onChange={(v) => set({ button2Visible: v })} />
            {options.button2Visible && (
              <label className={settingsUi.controlRow}>
                <span>BitChord button text</span>
                <input
                  className={settingsUi.input}
                  placeholder="Visit BitChord"
                  value={options.button2Text}
                  onChange={(e) => set({ button2Text: e.currentTarget.value })}
                />
              </label>
            )}
          </>
        )}
      </section>
      <p className={settingsUi.footer}>
        This uses your Discord account’s own token, which Discord’s terms don’t allow. Button text can use {'{song_name}'},{' '}
        {'{artist_name}'} and {'{album_name}'}.
      </p>
    </>
  );
}
