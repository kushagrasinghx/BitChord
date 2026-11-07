/**
 * Mobile browsers can't install the extension, so they get the Android app
 * banner instead of the install prompt (prompt §1).
 */
export function isMobileBrowser(): boolean {
  const uaData = (navigator as Navigator & { userAgentData?: { mobile?: boolean } }).userAgentData;
  if (typeof uaData?.mobile === 'boolean') return uaData.mobile;
  return /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent);
}

/** Macs show ⌘ for the command key; elsewhere it's Ctrl. */
export function isMac(): boolean {
  const uaData = (navigator as Navigator & { userAgentData?: { platform?: string } }).userAgentData;
  return /mac/i.test(uaData?.platform || navigator.platform || navigator.userAgent);
}
