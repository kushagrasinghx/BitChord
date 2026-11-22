import { ArrowUpRight } from 'lucide-react';
import { useCallback, useRef, useState } from 'react';
import { MdPerson } from 'react-icons/md';
import { useNavigate } from 'react-router';
import { Menu, MenuHeader, MenuItem, MenuSeparator } from '@/components/Menu';
import { GlassDisc } from '@/components/ui/GlassDisc';
import { useShortcutsSheet } from './ShortcutsSheet';
import { useRefreshYtAuth, useSelectChannel, useSetConnected, useYtAuth } from '@/extension/useYtAuth';

export const SIGN_IN_URL = 'https://accounts.google.com/ServiceLogin?service=youtube&continue=https://music.youtube.com/';

const avatar = 'inline-grid flex-none place-items-center rounded-full bg-surface-variant object-cover type-[600_11px/1] text-on-surface-variant';

function Avatar({ src, name, size }: { src: string | null; name: string; size: number }) {
  return src ? (
    <img className={avatar} src={src} alt="" width={size} height={size} referrerPolicy="no-referrer" />
  ) : (
    <span className={avatar} style={{ width: size, height: size }} aria-hidden="true">
      {name.slice(0, 1).toUpperCase()}
    </span>
  );
}

/*
 * The account photo at 28px in the top bar's 44px glass circle
 * (app/.../ui/components/FrostedTopBar.kt TopBarAccountButton, AVATAR_SIZE),
 * with artwork's hairline ring so a light-edged photo doesn't dissolve into
 * the glass. With no photo to show it is a person glyph on surfaceVariant.
 */
function TopBarAvatar({ src }: { src: string | null }) {
  return (
    <span className="relative grid size-7 place-items-center overflow-hidden rounded-full bg-surface-variant text-on-surface-variant artwork-stroke">
      {src ? <img className="size-full object-cover" src={src} alt="" referrerPolicy="no-referrer" /> : <MdPerson size={18} />}
    </span>
  );
}

/**
 * The account control at the right of the top bar.
 *
 * Without the extension there is no account to show, and the neutral avatar
 * opens Settings, as the phone's does signed out. With it, the avatar opens a
 * menu: the channels to switch between once signed in (the wheel over it
 * steps through them, as a vertical swipe does on the phone), or else the one
 * thing standing between the listener and their account.
 *
 * There is no sign-in form (prompt §5.3): BitChord uses the YouTube session
 * this browser already has, through the extension. "Sign In" opens Google's
 * own sign-in; the extension notices the new session and this updates itself.
 */
export function AccountButton() {
  const status = useYtAuth();
  const select = useSelectChannel();
  const connect = useSetConnected();
  const refresh = useRefreshYtAuth();
  const navigate = useNavigate();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const lastWheel = useRef(0);
  const close = useCallback(() => setAnchor(null), []);

  if (status.state !== 'ready') {
    return (
      <GlassDisc variant="back" label="Settings" title="Settings" onClick={() => navigate('/settings', { viewTransition: true })}>
        <TopBarAvatar src={null} />
      </GlassDisc>
    );
  }
  const { auth } = status;
  const active = auth.connected ? auth.active : null;
  const channels = auth.accounts.flatMap((a) => a.channels);
  const open = (event: React.MouseEvent<HTMLElement>) => setAnchor(anchor ? null : event.currentTarget);

  // One channel per wheel gesture (events 300ms apart): a trackpad's flick and glide send dozens.
  const step = (event: React.WheelEvent) => {
    const gesture = event.timeStamp - lastWheel.current > 300;
    lastWheel.current = event.timeStamp;
    if (!gesture || !active || select.isPending || channels.length < 2 || event.deltaY === 0) return;
    const at = channels.findIndex((c) => c.id === active.id);
    const next = channels[at + Math.sign(event.deltaY)];
    if (next) select.mutate(next.id);
  };

  return (
    <>
      <GlassDisc
        variant="back"
        label={active ? `Account: ${active.name}` : 'Account'}
        title={active?.name ?? auth.problem ?? undefined}
        aria-haspopup="menu"
        aria-expanded={anchor !== null}
        onClick={open}
        onWheel={step}
      >
        <TopBarAvatar src={active?.avatarUrl ?? null} />
      </GlassDisc>
      {anchor && (
        <Menu anchor={anchor} onClose={close} label="Account">
          {active ? (
            <>
              {auth.accounts.map((account) => (
                <div key={account.authUser} role="group" aria-label={account.email ?? account.name}>
                  <MenuHeader>{account.email ?? account.name}</MenuHeader>
                  {account.channels.map((channel) => (
                    <MenuItem
                      key={channel.id}
                      checked={channel.id === active.id}
                      icon={<Avatar src={channel.avatarUrl} name={channel.name} size={20} />}
                      trailing={channel.brand ? 'Brand' : undefined}
                      onSelect={() => {
                        close();
                        if (channel.id !== active.id) select.mutate(channel.id);
                      }}
                    >
                      {channel.name}
                    </MenuItem>
                  ))}
                </div>
              ))}
              <MenuSeparator />
              <MenuItem
                onSelect={() => {
                  close();
                  window.open(SIGN_IN_URL.replace('ServiceLogin', 'AddSession'), '_blank', 'noreferrer');
                }}
                trailing={<ArrowUpRight size={13} />}
              >
                Add Account
              </MenuItem>
              <MenuItem
                destructive
                onSelect={() => {
                  close();
                  connect.mutate(false);
                }}
              >
                Disconnect from BitChord
              </MenuItem>
            </>
          ) : !auth.browserSignedIn ? (
            <MenuItem
              onSelect={() => {
                close();
                window.open(SIGN_IN_URL, '_blank', 'noreferrer');
              }}
              trailing={<ArrowUpRight size={13} />}
            >
              Sign In
            </MenuItem>
          ) : !auth.connected ? (
            <MenuItem
              disabled={connect.isPending}
              onSelect={() => {
                close();
                connect.mutate(true);
              }}
            >
              Connect YouTube Music
            </MenuItem>
          ) : (
            // Signed in to the browser, but the account couldn't be read (the reason is on the Extension page).
            <MenuItem
              disabled={refresh.isPending}
              onSelect={() => {
                close();
                refresh.mutate();
              }}
            >
              Retry YouTube Sign-In
            </MenuItem>
          )}
          {!active && (
            <>
              <MenuSeparator />
              <MenuItem
                onSelect={() => {
                  close();
                  navigate('/settings', { viewTransition: true });
                }}
              >
                Settings
              </MenuItem>
            </>
          )}
          {/* The desktop's shortcuts are otherwise only behind "?" (ShortcutsSheet.tsx). */}
          <MenuSeparator />
          <MenuItem
            onSelect={() => {
              close();
              useShortcutsSheet.getState().setOpen(true);
            }}
          >
            Keyboard shortcuts
          </MenuItem>
        </Menu>
      )}
    </>
  );
}
