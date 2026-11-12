import '../popup/style.css';
import './style.css';

// Opened by lyrics.requestPermissions: the browser only shows a permission
// prompt in answer to a click on one of the extension's own pages.
const origins = (new URLSearchParams(location.search).get('origins') ?? '').split(',').filter(Boolean);
const list = document.getElementById('hosts')!;
for (const origin of origins) {
  const item = document.createElement('li');
  item.textContent = new URL(origin).host;
  list.append(item);
}

const status = document.getElementById('status')!;
document.getElementById('allow')!.addEventListener('click', async () => {
  const granted = await browser.permissions.request({ origins: origins.map((o) => `${o}/*`) }).catch(() => false);
  status.textContent = granted ? 'Done. You can close this tab and go back to BitChord.' : 'Not allowed. Those sources stay off.';
  if (granted) setTimeout(() => window.close(), 1200);
});
