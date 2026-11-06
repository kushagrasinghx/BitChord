import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

/** Local development signing; does not provide Developer ID or notarization. */
export function signSafariApp(app) {
  const entitlements = fileURLToPath(new URL('./safari-development.entitlements', import.meta.url));
  for (const bundle of [`${app}/Contents/PlugIns/BitChord Extension.appex`, app]) {
    execFileSync('codesign', ['--force', '--sign', '-', '--entitlements', entitlements, bundle], { stdio: 'inherit' });
  }
  execFileSync('codesign', ['--verify', '--deep', '--strict', app], { stdio: 'inherit' });
}
