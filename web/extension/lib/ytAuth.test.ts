import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

interface Channel {
  handle?: string | null;
  pageId?: string | null;
}

interface Login {
  name?: string;
  email?: string | null;
  handle?: string | null;
  channels?: Channel[];
  listFails?: boolean;
}

let logins: (Login | null)[];

beforeEach(() => {
  vi.resetModules();
  logins = [];
  vi.stubGlobal('browser', {
    cookies: { get: vi.fn().mockResolvedValue({ value: 'synthetic-signing-secret' }) },
    storage: { local: { get: vi.fn().mockResolvedValue({}) } },
    declarativeNetRequest: { updateSessionRules: vi.fn().mockResolvedValue(undefined) },
  });
  vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit) => {
    if (new URL(url).pathname === '/') return new Response('"SESSION_INDEX":"0"');
    const index = Number(new Headers(init.headers).get('X-Goog-AuthUser'));
    const login = logins[index];
    if (!login) return new Response(null, { status: 403 });
    if (url.includes('/account_menu')) {
      return Response.json({ activeAccountHeaderRenderer: {
        accountName: { simpleText: login.name ?? 'Alex Example' },
        email: login.email ? { simpleText: login.email } : undefined,
        channelHandle: login.handle ? { simpleText: login.handle } : undefined,
      } });
    }
    if (login.listFails) return new Response(null, { status: 500 });
    return Response.json({ items: (login.channels ?? [{ handle: login.handle }]).map((channel) => ({
      accountItem: {
        accountName: { simpleText: login.name ?? 'Alex Example' },
        accountByline: channel.handle ? { simpleText: channel.handle } : undefined,
        endpoint: channel.pageId ? { pageId: channel.pageId } : undefined,
        isSelected: true,
      },
    })) });
  }));
});

afterEach(() => vi.unstubAllGlobals());

async function discoveredLogins() {
  const { authState } = await import('./ytAuth');
  const state = await authState(true);
  return state.accounts.map((account) => account.authUser);
}

describe('YouTube account discovery', () => {
  it('retains same-name channel accounts without emails alongside an email account', async () => {
    logins = [
      { handle: '@alex-one' },
      { handle: '@alex-two' },
      { name: 'Third Example', email: 'third@example.test', handle: 'No channel' },
    ];
    expect(await discoveredLogins()).toEqual(['0', '1', '2']);
  });

  it('deduplicates fallback responses from excess login indices', async () => {
    logins = Array.from({ length: 10 }, () => ({ handle: '@same-channel' }));
    expect(await discoveredLogins()).toEqual(['0']);
  });

  it('retains same-name brand channels with distinct page IDs', async () => {
    logins = [{ channels: [{ pageId: 'page-a' }] }, { channels: [{ pageId: 'page-b' }] }];
    expect(await discoveredLogins()).toEqual(['0', '1']);
  });

  it('deduplicates the same page identity across login indices', async () => {
    logins = [{ channels: [{ pageId: 'page-a' }] }, { channels: [{ pageId: 'page-a' }] }];
    expect(await discoveredLogins()).toEqual(['0']);
  });

  it('keeps unknown same-name identities separate instead of matching generic bylines', async () => {
    logins = [{ handle: 'No channel' }, { handle: 'No channel' }];
    expect(await discoveredLogins()).toEqual(['0', '1']);
  });

  it('uses normalized email identities when available', async () => {
    logins = [
      { email: 'first@example.test' },
      { email: 'second@example.test' },
      { email: ' FIRST@EXAMPLE.TEST ' },
    ];
    expect(await discoveredLogins()).toEqual(['0', '1']);
  });

  it('does not hide valid logins after expired indices', async () => {
    logins = [{ handle: '@first' }, null, { handle: '@third' }];
    expect(await discoveredLogins()).toEqual(['0', '2']);
  });

  it('ignores channel ordering when comparing repeated responses', async () => {
    const own = { handle: '@own' };
    const brand = { pageId: 'page-a' };
    logins = [{ channels: [own, brand] }, { channels: [brand, own] }];
    expect(await discoveredLogins()).toEqual(['0']);
  });

  it('matches handles case-insensitively', async () => {
    logins = [{ handle: '@Example' }, { handle: '@example' }];
    expect(await discoveredLogins()).toEqual(['0']);
  });

  it('uses the account header handle when the channel list fails', async () => {
    logins = [{ handle: '@first', listFails: true }, { handle: '@second', listFails: true }];
    expect(await discoveredLogins()).toEqual(['0', '1']);
  });

  it('keeps distinct own channels that share access to a brand channel', async () => {
    logins = [
      { channels: [{ handle: '@first' }, { pageId: 'shared-brand' }] },
      { channels: [{ handle: '@second' }, { pageId: 'shared-brand' }] },
    ];
    expect(await discoveredLogins()).toEqual(['0', '1']);
  });
});
