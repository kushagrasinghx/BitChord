import { afterEach, describe, expect, it, vi } from 'vitest';
import { gunzip, gzip, sha256Hex } from './bytes';
import { DriveError, listAll, multipartBody, put, remove, Unauthorized } from './drive';

afterEach(() => vi.unstubAllGlobals());

function respond(...answers: (object | number)[]) {
  const calls: { url: string; init: RequestInit }[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init: RequestInit = {}) => {
      calls.push({ url, init });
      const answer = answers.shift()!;
      return typeof answer === 'number' ? new Response('{}', { status: answer }) : Response.json(answer);
    }),
  );
  return calls;
}

describe('Drive requests (DriveApi.kt)', () => {
  it('lists the app folder page by page', async () => {
    const calls = respond(
      { files: [{ id: 'a', name: 'device-x.json.gz', appProperties: { kind: 'device', deviceId: 'x' }, size: '12' }], nextPageToken: 'p2' },
      { files: [{ id: 'b', md5Checksum: 'm' }] },
    );
    const files = await listAll('tok');
    expect(files.map((f) => [f.id, f.sizeBytes, f.md5])).toEqual([
      ['a', 12, null],
      ['b', 0, 'm'],
    ]);
    expect(files[0]!.properties).toEqual({ kind: 'device', deviceId: 'x' });
    const first = new URL(calls[0]!.url);
    expect(first.searchParams.get('spaces')).toBe('appDataFolder');
    expect(first.searchParams.get('q')).toBe('trashed = false');
    expect(new URL(calls[1]!.url).searchParams.get('pageToken')).toBe('p2');
    expect((calls[0]!.init.headers as Record<string, string>).Authorization).toBe('Bearer tok');
  });

  it('writes a new file into appDataFolder and an existing one in place', async () => {
    const calls = respond({ id: 'new' }, { id: 'old' });
    await put('tok', 'replay-d-2026-11.json.gz', new Uint8Array([1]), { kind: 'replay' }, null);
    await put('tok', 'replay-d-2026-11.json.gz', new Uint8Array([1]), { kind: 'replay' }, 'old');
    expect(calls[0]!.init.method).toBe('POST');
    expect(calls[0]!.url).toMatch(/^https:\/\/www\.googleapis\.com\/upload\/drive\/v3\/files\?uploadType=multipart/);
    expect(calls[1]!.init.method).toBe('PATCH');
    expect(calls[1]!.url).toContain('/files/old?');
  });

  it('sends the metadata, then the gzip, as multipart/related', async () => {
    const { body, contentType } = multipartBody('device-d.json.gz', new Uint8Array([7, 8]), { kind: 'device', deviceId: 'd' }, null);
    const boundary = contentType.split('boundary=')[1]!;
    expect(contentType).toMatch(/^multipart\/related; boundary=/);
    const text = await body.text();
    const metadata = JSON.parse(text.split('\r\n')[3]!) as Record<string, unknown>;
    expect(metadata).toEqual({ name: 'device-d.json.gz', mimeType: 'application/gzip', appProperties: { kind: 'device', deviceId: 'd' }, parents: ['appDataFolder'] });
    expect(text).toContain('Content-Type: application/gzip\r\n\r\n\u0007\u0008\r\n');
    expect(text.endsWith(`--${boundary}--`)).toBe(true);
    // An update leaves the file where it is.
    const update = await multipartBody('x', new Uint8Array(), {}, 'id').body.text();
    expect(update).not.toContain('parents');
  });

  it('says what a refusal was', async () => {
    respond(401);
    await expect(listAll('tok')).rejects.toBeInstanceOf(Unauthorized);
    respond({ error: { message: 'The user’s Drive storage quota has been exceeded.' } });
    vi.stubGlobal('fetch', async () => Response.json({ error: { message: 'quota exceeded' } }, { status: 403 }));
    await expect(listAll('tok')).rejects.toEqual(new DriveError(403, 'quota exceeded'));
    // Already gone counts as deleted.
    respond(404);
    await expect(remove('tok', 'id')).resolves.toBeUndefined();
  });
});

describe('bytes', () => {
  it('gzips and back, and hashes as BucketMath.hash does', async () => {
    const text = new TextEncoder().encode('{"month":"2026-11"}');
    expect(await gunzip(await gzip(text))).toEqual(text);
    expect(await sha256Hex(new TextEncoder().encode('abc'))).toBe('ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');
  });
});
