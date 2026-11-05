import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { mkdirSync } from 'node:fs';

const root = fileURLToPath(new URL('../', import.meta.url));
const output = `${root}.output/safari-native`;
const run = (command, args) => execFileSync(command, args, { cwd: root, stdio: 'inherit' });

// Full Xcode supplies the packager and macOS app build tools.
run('xcodebuild', ['-version']);
mkdirSync(output, { recursive: true });
run('xcrun', ['safari-web-extension-packager', `${root}.output/safari-mv3`,
  '--project-location', output, '--app-name', 'BitChord',
  '--bundle-identifier', 'in.bitchord.safari', '--swift', '--macos-only',
  '--copy-resources', '--no-open', '--no-prompt', '--force']);
run('xcodebuild', ['-project', `${output}/BitChord/BitChord.xcodeproj`,
  '-scheme', 'BitChord (macOS)', '-configuration', 'Release',
  '-derivedDataPath', `${output}/build`, 'CODE_SIGNING_ALLOWED=NO', 'build']);
console.log(`Unsigned app: ${output}/build/Build/Products/Release/BitChord.app`);
