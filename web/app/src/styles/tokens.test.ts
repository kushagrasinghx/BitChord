import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const css = readFileSync(fileURLToPath(new URL('./tokens.css', import.meta.url)), 'utf8');

/** The declarations of the first rule whose selector is exactly `selector`. */
function block(selector: string): string[] {
  const at = css.indexOf(`${selector} {`);
  expect(at, selector).toBeGreaterThan(-1);
  const body = css.slice(css.indexOf('{', at) + 1, css.indexOf('}', at));
  return body
    .split('\n')
    .map((line) => line.replace(/\/\*.*?\*\//g, '').trim())
    .filter((line) => line.includes(':'));
}

describe('tokens.css', () => {
  it('writes dark the same for the Dark setting and for System on a dark OS', () => {
    expect(block(":root:not([data-theme='light'])")).toEqual(block(":root[data-theme='dark']"));
  });

  // Android's colour schemes are the source: a change to Theme.kt fails here, naming the token to update.
  it('carries the phone colours from Theme.kt', () => {
    const kt = readFileSync(fileURLToPath(new URL('../../../../app/src/main/java/com/music/bitchord/ui/theme/Theme.kt', import.meta.url)), 'utf8');
    const token: Record<string, string> = { background: '--pageBG', onBackground: '--systemPrimary', surface: '--surface', surfaceVariant: '--surfaceVariant', onSurfaceVariant: '--onSurfaceVariant', outline: '--outline' };
    const named: Record<string, string> = { White: '#ffffff', Black: '#000000' };
    for (const [scheme, selector] of [['LightColors', ':root'], [`DarkColors`, ":root[data-theme='dark']"]] as const) {
      const body = kt.slice(kt.indexOf(`val ${scheme} =`), kt.indexOf('\n)', kt.indexOf(`val ${scheme} =`)));
      const css = block(selector);
      const pairs = [...body.matchAll(/(\w+) = Color(?:\(0xFF([0-9A-Fa-f]{6})\)|\.(White|Black))/g)]
        .map(([, role = '', hex, name = '']) => ({ name: token[role], value: hex ? `#${hex.toLowerCase()}` : named[name] }))
        .filter((pair) => pair.name);
      expect(pairs.length, scheme).toBe(Object.keys(token).length);
      for (const { name, value } of pairs) expect(css, `${scheme} ${name}`).toContain(`${name}: ${value};`);
    }
  });

  it('keeps the names other code draws from', () => {
    // The Windows app's sign-in sheet and scrollbars read these by name.
    for (const name of [
      '--pageBG',
      '--keyColor',
      '--systemPrimary',
      '--systemSecondary',
      '--systemQuaternary',
      '--systemQuinary',
      '--labelDivider',
      '--modalScrimColor',
      '--glassMaterialBackground',
      '--glassMaterialInnerStroke',
      '--glassMaterialInnerStrokeAlpha',
      '--glassMaterialShadowColor',
      '--radius-pill',
      '--font-family',
    ]) {
      expect(css).toContain(`${name}:`);
    }
  });
});
