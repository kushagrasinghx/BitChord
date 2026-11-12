import { PageHeader } from '@/components/PageHeader';
import { useDiscord } from '@/extension/useDiscord';
import { DiscordIntegration, DiscordSettings } from './IntegrationSettings';
import { settingsUi } from './settingsStyles';

/** Discord rich presence (DiscordScreen on the phone): the account, then how the card reads. */
export default function DiscordSettingsPage() {
  const status = useDiscord();
  return (
    <div className={settingsUi.centered}>
      <PageHeader title="Discord" />
      <div className={settingsUi.body}>
        <section className={settingsUi.group}>
          <div className={settingsUi.integrationPlain}>
            <span className={settingsUi.integrationName}>Account</span>
            <DiscordIntegration />
          </div>
        </section>
        {status.state === 'ready' && !status.discord.connected && (
          <p className={settingsUi.footer}>
            Connect opens a page from the extension that reads your Discord session from a discord.com tab. The web player never
            sees it. This uses your account’s own token, which Discord’s terms don’t allow.
          </p>
        )}
        {status.state === 'ready' && status.discord.connected && (
          <>
            <h2 className={settingsUi.sectionHeader}>Presence</h2>
            <DiscordSettings />
          </>
        )}
      </div>
    </div>
  );
}
