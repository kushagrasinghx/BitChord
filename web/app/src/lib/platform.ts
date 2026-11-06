/**
 * Mobile browsers can't install the extension, so they get the Android app
 * banner instead of the install prompt (prompt §1).
 */
export function isMobileBrowser(): boolean {
  const uaData = (navigator as Navigator & { userAgentData?: { mobile?: boolean } }).userAgentData;
  if (typeof uaData?.mobile === 'boolean') return uaData.mobile;
  return /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent);
}

/** Selects installation instructions; Chromium also includes Safari in its UA. */
export function extensionBrowser(): 'chrome' | 'firefox' | 'safari' {
  const ua = navigator.userAgent;
  if (/firefox/i.test(ua)) return 'firefox';
  if (/Version\/.*Safari\//i.test(ua) && !/Chrome|Chromium|CriOS|Edg|OPR/i.test(ua)) return 'safari';
  return 'chrome';
}
