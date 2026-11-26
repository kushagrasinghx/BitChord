// The native title bar follows the player's theme (src/title_bar.rs title_bar_theme): Settings'
// Light or Dark puts data-theme on <html> (web/app/src/state/theme.ts); with none, System follows Windows.
(() => {
  let last;
  const report = () => {
    const theme = document.documentElement.dataset.theme;
    const dark = theme ? theme === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches;
    if (dark === last) return;
    last = dark;
    window.__TAURI_INTERNALS__?.invoke('title_bar_theme', { dark });
  };
  new MutationObserver(report).observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
  matchMedia('(prefers-color-scheme: dark)').addEventListener('change', report);
  report();
})();
