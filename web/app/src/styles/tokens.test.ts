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
