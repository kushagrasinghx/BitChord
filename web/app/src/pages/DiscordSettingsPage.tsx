import { SiDiscord } from 'react-icons/si';
import { PageHeader } from '@/components/PageHeader';
import { useDiscord } from '@/extension/useDiscord';
import { DiscordIntegration, DiscordSettings } from './IntegrationSettings';
import { Group, SettingsRow } from './SettingsRows';
import { settingsUi } from './settingsStyles';

/** Discord rich presence (DiscordScreen on the phone): the account, then how the card reads. */
export default function DiscordSettingsPage() {
  const status = useDiscord();
  return (
    <div className={settingsUi.centered}>
      <PageHeader title="Discord" />
      <div className={settingsUi.body}>
        <Group
          footer={
            status.state === 'ready' &&
            !status.discord.connected &&
            'Connect opens a page from the extension that reads your Discord session from a discord.com tab. The web player never sees it. This uses your account’s own token, which Discord’s terms don’t allow.'
          }
        >
          <SettingsRow icon={SiDiscord} title="Account">
            <DiscordIntegration />
          </SettingsRow>
        </Group>
        <DiscordSettings />
      </div>
    </div>
  );
}
