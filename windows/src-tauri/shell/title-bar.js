// The native title bar follows the player's theme (src/title_bar.rs title_bar_theme): Settings' Light or Dark
// puts data-theme on <html> (web/app/src/state/theme.ts); with none (System) the bar follows Windows itself.
(() => {
  let sent;
  const report = () => {
    const theme = document.documentElement.dataset.theme;
    const dark = theme ? theme === 'dark' : null;
    if (dark === sent) return;
    window.__TAURI_INTERNALS__?.invoke('title_bar_theme', { dark }).then(() => (sent = dark), () => {});
  };
  const start = () => {
    new MutationObserver(report).observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
    report();
  };
  // Init scripts run before <html> exists; by DOMContentLoaded the player's theme is already applied.
  if (document.documentElement) start();
  else document.addEventListener('DOMContentLoaded', start, { once: true });
})();
