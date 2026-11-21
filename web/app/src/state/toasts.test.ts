import { beforeEach, describe, expect, it } from 'vitest';
import { toast, toastResult, useToasts } from './toasts';

const current = () => useToasts.getState().current;

describe('toasts', () => {
  beforeEach(() => useToasts.setState({ current: null }));

  it('shows one at a time, the newest replacing the last', () => {
    toast('Added to queue');
    toast('Playing next');
    expect(current()?.message).toBe('Playing next');
  });

  it("only closes the toast a timer was set for, not the one that replaced it", () => {
    toast('First');
    const first = current()!.id;
    toast('Second');
    useToasts.getState().dismiss(first);
    expect(current()?.message).toBe('Second');
    useToasts.getState().dismiss(current()!.id);
    expect(current()).toBeNull();
  });

  it('confirms work that succeeds, with its action', async () => {
    const action = { label: 'View', href: '/playlist/PL1' };
    toastResult(Promise.resolve(), { message: 'Added to Road Trip', action }, 'Couldn’t add');
    await Promise.resolve();
    expect(current()).toMatchObject({ message: 'Added to Road Trip', action });
  });

  it('says so when the work fails', async () => {
    toastResult(Promise.reject(new Error('offline')), 'Added', 'Couldn’t add to Road Trip');
    await new Promise((resolve) => setTimeout(resolve));
    expect(current()).toMatchObject({ message: 'Couldn’t add to Road Trip', error: true });
  });
});
