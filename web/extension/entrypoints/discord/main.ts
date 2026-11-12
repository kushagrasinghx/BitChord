import '../popup/style.css';
import '../grant/style.css';
import { connectFromTab, DISCORD_ORIGINS } from '../../lib/discord';

// Opened by discord.connect: the browser only asks for discord.com after a
// click on one of the extension's own pages, and the token is only read on it.
const status = document.getElementById('status')!;
const button = document.getElementById('connect') as HTMLButtonElement;

button.addEventListener('click', async () => {
  const granted = await browser.permissions.request({ origins: DISCORD_ORIGINS }).catch(() => false);
  if (!granted) {
    status.textContent = 'Not allowed. Discord stays off.';
    return;
  }
  button.disabled = true;
  try {
    const state = await connectFromTab((message) => (status.textContent = message));
    status.textContent = `Connected as ${state.user?.name ?? 'your account'}. You can close this tab.`;
    setTimeout(() => window.close(), 1500);
  } catch (error) {
    status.textContent = error instanceof Error ? error.message : String(error);
    button.disabled = false;
  }
});
