import { Archive, ChevronLeft, CircleArrowDown, CircleEllipsis, CirclePlay, History, PaintbrushVertical, Puzzle, Search, User, Users, Video, X } from 'lucide-react';
import { useState, type ComponentType } from 'react';
import { Link, useSearchParams } from 'react-router';
import { SiDiscord, SiSpotify } from 'react-icons/si';
import { PrefetchLink } from '@/components/PrefetchLink';
import { GlassDisc } from '@/components/ui/GlassDisc';
import { useDiscord } from '@/extension/useDiscord';
import { useExtension } from '@/extension/useExtension';
import { useYtAuth } from '@/extension/useYtAuth';
import { useParty } from '@/party/useParty';
import { useHistorySettings } from '@/playback/historyReporting';
import { offlineSupported } from '@/playback/offline/store';
import { AppearanceSettings } from './AppearanceSettings';
import { DownloadsSettings, PremiumAudioToggle, YourDataSettings } from './DataSettings';
import { ContentSettings, SpotifyIntegration } from './IntegrationSettings';
import { MiscSettings, PlaybackSettings } from './PlayerSettings';
import { Chevron, Group, RowGlyph, SettingsRow, SettingsSearchProvider, Toggle, useMatches, type Glyph } from './SettingsRows';
import { settingsUi } from './settingsStyles';

/**
 * A row that opens a page of its own. Its root is the link itself, so in a
 * group it sits directly inside the <section> (the Windows app finds the
 * Extension row as `section > a[href="/extension"]`).
 */
function LinkRow({ to, icon, title, value, keywords }: { to: string; icon: Glyph; title: string; value?: string | null; keywords?: string[] }) {
  if (!useMatches(title, keywords)) return null;
  return (
    <PrefetchLink to={to} className={settingsUi.pressableRow}>
      <RowGlyph icon={icon} />
      <span className="min-w-0 flex-1">{title}</span>
      <span className={settingsUi.value}>
        {value}
        <Chevron />
      </span>
    </PrefetchLink>
  );
}

function youtubeText(auth: ReturnType<typeof useYtAuth>): string {
  if (auth.state === 'checking') return 'Checking…';
  if (auth.state === 'unavailable') return 'Needs the extension';
  const { browserSignedIn, connected, active } = auth.auth;
  if (!browserSignedIn) return 'Not signed in';
  if (!connected) return 'Disconnected';
  return active ? active.name : 'Signed in';
}

/**
 * The groups of Settings that are pages of their own, in the order the
 * phone's main page lists them (SettingsSheet.kt SettingsCategory), each with
 * its glyph. The phone's Performance, Local Music, Storage and Language have
 * nothing in them in a browser, so they aren't listed.
 */
const CATEGORIES: { id: string; title: string; icon: Glyph; Rows: ComponentType; shown?: () => boolean }[] = [
  { id: 'downloads', title: 'Downloads', icon: CircleArrowDown, Rows: DownloadsSettings, shown: offlineSupported },
  { id: 'playback', title: 'Playback', icon: CirclePlay, Rows: PlaybackSettings },
  { id: 'content', title: 'Content Preferences', icon: Video, Rows: ContentSettings },
  { id: 'appearance', title: 'Appearance', icon: PaintbrushVertical, Rows: AppearanceSettings },
  { id: 'data', title: 'Your Data', icon: Archive, Rows: YourDataSettings },
  { id: 'misc', title: 'Miscellaneous', icon: CircleEllipsis, Rows: MiscSettings },
];

/**
 * Settings (SettingsSheet on the phone), laid out for a wide window. On the
 * left, as the phone's main page: a search field, the Account card, Audio
 * quality, and the categories. A category opens on the right; in a window
 * too narrow for both, it takes the page, with a back button. Typed into,
 * the search lays out every category's rows that answer, as the single page
 * Settings once was.
 */
export default function SettingsPage() {
  const [params, setParams] = useSearchParams();
  const [query, setQuery] = useState('');
  const categories = CATEGORIES.filter((c) => c.shown?.() ?? true);
  const picked = categories.find((c) => c.id === params.get('category')) ?? null;
  const current = picked ?? categories[0]!;
  const searching = query.trim() !== '';

  return (
    <SettingsSearchProvider value={query}>
      <div className="@container mx-auto max-w-[calc(1120px+2*var(--body-gutter))] px-(--body-gutter) pb-[120px]">
        <div className="@4xl:grid @4xl:grid-cols-[minmax(320px,400px)_minmax(0,1fr)] @4xl:gap-10">
          {/* Narrow, the main column steps aside for an open category. */}
          <div className={picked && !searching ? '@max-4xl:hidden' : undefined}>
            <h1 className="pt-8 pb-3.5 type-header-emphasized">Settings</h1>
            <SearchField value={query} onChange={setQuery} />
            <div className="mt-[26px]">
              <AccountGroup />
              <Group header="Audio Quality">
                <PremiumAudioToggle />
              </Group>
              {!searching && (
                <Group>
                  {categories.map((category) => (
                    <Link
                      key={category.id}
                      to={{ search: `?category=${category.id}` }}
                      aria-current={category === current ? 'page' : undefined}
                      className={`${settingsUi.pressableRow} @4xl:aria-[current=page]:bg-quinary`}
                    >
                      <RowGlyph icon={category.icon} />
                      <span className="min-w-0 flex-1">{category.title}</span>
                      <Chevron />
                    </Link>
                  ))}
                </Group>
              )}
            </div>
          </div>

          <div className={!picked && !searching ? '@max-4xl:hidden' : undefined}>
            {searching ? (
              <div className="pt-6 @4xl:pt-8 [&:not(:has(section:not(:empty)))>p]:block">
                {categories.map(({ id, Rows }) => (
                  <Rows key={id} />
                ))}
                <p className="hidden pt-10 text-center type-body text-on-surface-variant">No settings match “{query.trim()}”.</p>
              </div>
            ) : (
              <>
                <div className="flex items-center gap-3 pt-8 pb-3.5">
                  <GlassDisc variant="back" label="Settings" className="@4xl:hidden" onClick={() => setParams({})}>
                    <ChevronLeft size={22} strokeWidth={2.4} />
                  </GlassDisc>
                  <h2 className="type-header-emphasized">{current.title}</h2>
                </div>
                <div className="mt-[26px]">
                  <current.Rows />
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    </SettingsSearchProvider>
  );
}

/** Android's SearchField: a rounded field, the magnifier, and a way to clear it. */
function SearchField({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  return (
    <label className="flex h-10 items-center gap-2 rounded-search bg-surface-variant px-3 text-on-surface-variant">
      <Search size={18} strokeWidth={2.2} aria-hidden />
      <input
        type="search"
        className="min-w-0 flex-1 border-0 bg-transparent type-body text-primary outline-none placeholder:text-on-surface-variant [&::-webkit-search-cancel-button]:hidden"
        placeholder="Search settings"
        value={value}
        onChange={(e) => onChange(e.currentTarget.value)}
        onKeyDown={(e) => e.key === 'Escape' && onChange('')}
      />
      {value && (
        <button type="button" aria-label="Clear" className="grid size-5 place-items-center rounded-full bg-on-surface-variant/40 text-surface" onClick={() => onChange('')}>
          <X size={13} strokeWidth={3} />
        </button>
      )}
    </label>
  );
}

/**
 * The Account card: the YouTube Music account (its avatar for the glyph,
 * as the phone's Account & integrations row), the other services and the
 * extension they come through, Listen Together, which sits with the account
 * as on the phone, and whether plays go to the account's history.
 */
function AccountGroup() {
  const extension = useExtension();
  const yt = useYtAuth();
  const discord = useDiscord();
  const party = useParty();
  const shareHistory = useHistorySettings((s) => s.share);
  const setShareHistory = useHistorySettings((s) => s.setShare);
  const present = extension.state === 'present';
  const avatar = yt.state === 'ready' && yt.auth.signedIn ? yt.auth.active?.avatarUrl : null;
  const discordText =
    discord.state === 'ready' && discord.discord.connected
      ? discord.discord.enabled
        ? 'On'
        : 'Off'
      : discord.state === 'ready' && discord.discord.available
        ? 'Not connected'
        : null;

  return (
    <Group>
      <SettingsRow
        icon={User}
        glyph={avatar ? <img className={settingsUi.glyph} src={avatar} alt="" referrerPolicy="no-referrer" /> : undefined}
        title="YouTube Music"
        keywords={['account', 'sign in', 'google', 'youtube']}
      >
        <span className={settingsUi.value}>{youtubeText(yt)}</span>
      </SettingsRow>
      <SettingsRow icon={SiSpotify} title="Spotify" keywords={['account', 'sign in', 'canvas']}>
        {present ? <SpotifyIntegration /> : <span className={settingsUi.value}>Needs the extension</span>}
      </SettingsRow>
      {present && discord.state === 'ready' && discord.discord.available && (
        <LinkRow to="/settings/discord" icon={SiDiscord} title="Discord" value={discordText} keywords={['presence', 'status', 'rich presence']} />
      )}
      <LinkRow
        to="/extension"
        icon={Puzzle}
        title="BitChord Extension"
        value={present ? `v${extension.extension.info.version}` : 'Not installed'}
        keywords={['extension', 'addon', 'chrome', 'firefox', 'update']}
      />
      <LinkRow
        to="/settings/listen-together"
        icon={Users}
        title="Listen Together"
        value={party.code ? `In party ${party.code}` : null}
        keywords={['jam', 'party', 'sync', 'friends', 'nickname', 'name']}
      />
      <Toggle
        icon={History}
        label="Share Listening Activity to YouTube"
        detail={
          present && !extension.extension.info.capabilities.includes('yt.stats')
            ? 'Update the BitChord extension to share: this version can’t send plays to YouTube.'
            : 'Songs you play here go to your YouTube Music history and shape its recommendations.'
        }
        keywords={['history', 'recommendations', 'scrobble']}
        checked={shareHistory}
        onChange={setShareHistory}
      />
    </Group>
  );
}
