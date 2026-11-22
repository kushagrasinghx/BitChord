import { describe, expect, it } from 'vitest';
import { permanentReason } from './resolver';

describe('what a refused track tells the listener', () => {
  it("names YouTube's bot check as a sign-in request, not an age restriction", () => {
    const bot = 'Sign in to confirm you’re not a bot';
    expect(permanentReason('LOGIN_REQUIRED', bot, false)).toMatch(/asking for a sign-in/);
    expect(permanentReason('LOGIN_REQUIRED', bot, false)).not.toMatch(/age-restricted/);
    expect(permanentReason('LOGIN_REQUIRED', bot, true)).toMatch(/not a bot/);
  });

  it('still calls a real age gate one', () => {
    expect(permanentReason('LOGIN_REQUIRED', 'Sign in to confirm your age', false)).toMatch(/age-restricted/);
  });
});
