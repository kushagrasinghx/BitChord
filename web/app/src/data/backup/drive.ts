/**
 * What BitChord asks of Google Drive, as the Android app asks it (DriveApi.kt):
 * list its files, write one, read one, delete one, as plain REST calls into the
 * hidden `appDataFolder`. Every file is small (a manifest, or one month of one
 * device's listening), so each goes up as a single multipart request.
 */

const MIME = 'application/gzip';
const API = 'https://www.googleapis.com/drive/v3';
const UPLOAD = 'https://www.googleapis.com/upload/drive/v3';
const FIELDS = 'id,name,md5Checksum,modifiedTime,size,appProperties';

/** Drive refused the token: the caller asks for another and tries once more. */
export class Unauthorized extends Error {
  override readonly name = 'Unauthorized';
  constructor() {
    super('Google Drive refused the sign-in');
  }
}

/** A refusal that asking again won't change (quota full, say). */
export class DriveError extends Error {
  override readonly name = 'DriveError';
  constructor(
    readonly code: number,
    message: string,
  ) {
    super(message);
  }
}

/** One file in the app folder. */
export interface DriveFile {
  id: string;
  name: string;
  /** Drive's MD5 of the stored bytes: changes exactly when the file does. */
  md5: string | null;
  modifiedAt: string;
  sizeBytes: number;
  properties: Record<string, string>;
}

type Json = Record<string, unknown>;

async function execute(url: string, token: string, init: RequestInit = {}): Promise<Response> {
  const response = await fetch(url, { ...init, headers: { ...init.headers, Authorization: `Bearer ${token}` } });
  if (response.ok) return response;
  if (response.status === 401) throw new Unauthorized();
  if (response.status >= 500) throw new Error(`Google Drive is having trouble (HTTP ${response.status})`);
  // Drive's own words for a refusal: "storage quota exceeded" and the like.
  const message = await response
    .json()
    .then((body: { error?: { message?: string } }) => body.error?.message)
    .catch(() => undefined);
  throw new DriveError(response.status, message ?? `Google Drive said no (HTTP ${response.status})`);
}

function toDriveFile(json: Json): DriveFile | null {
  if (typeof json.id !== 'string') return null;
  const props = (json.appProperties ?? {}) as Record<string, unknown>;
  return {
    id: json.id,
    name: typeof json.name === 'string' ? json.name : '',
    md5: typeof json.md5Checksum === 'string' ? json.md5Checksum : null,
    modifiedAt: typeof json.modifiedTime === 'string' ? json.modifiedTime : '',
    sizeBytes: Number(json.size ?? 0) || 0,
    properties: Object.fromEntries(Object.entries(props).filter((e): e is [string, string] => typeof e[1] === 'string')),
  };
}

/** Whose Drive the token is for: shown in Settings, and kept to pin the account. */
export async function profile(token: string): Promise<{ email: string | null; name: string | null }> {
  const response = await execute(`${API}/about?fields=${encodeURIComponent('user(emailAddress,displayName)')}`, token);
  const user = ((await response.json()) as { user?: { emailAddress?: string; displayName?: string } }).user;
  return { email: user?.emailAddress ?? null, name: user?.displayName ?? null };
}

/** Every file in this account's app folder, following Drive's pages. */
export async function listAll(token: string): Promise<DriveFile[]> {
  const files: DriveFile[] = [];
  let page: string | undefined;
  do {
    const params = new URLSearchParams({
      spaces: 'appDataFolder',
      q: 'trashed = false',
      fields: `nextPageToken,files(${FIELDS})`,
      pageSize: '1000',
      ...(page && { pageToken: page }),
    });
    const body = (await (await execute(`${API}/files?${params}`, token)).json()) as { files?: Json[]; nextPageToken?: string };
    for (const json of body.files ?? []) {
      const file = toDriveFile(json);
      if (file) files.push(file);
    }
    page = body.nextPageToken;
  } while (page);
  return files;
}

/** The multipart/related body DriveApi.put sends: the metadata, then the bytes. */
export function multipartBody(name: string, bytes: Uint8Array, properties: Record<string, string>, existingId: string | null) {
  const metadata = {
    name,
    mimeType: MIME,
    appProperties: properties,
    ...(existingId === null && { parents: ['appDataFolder'] }),
  };
  const boundary = `bitchord${crypto.randomUUID().replaceAll('-', '')}`;
  const body = new Blob([
    `--${boundary}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${JSON.stringify(metadata)}\r\n`,
    `--${boundary}\r\nContent-Type: ${MIME}\r\n\r\n`,
    bytes as BlobPart,
    `\r\n--${boundary}--`,
  ]);
  return { body, contentType: `multipart/related; boundary=${boundary}` };
}

/** Writes [bytes] as [name], over [existingId] when there is one. [properties] are what other devices find it by. */
export async function put(
  token: string,
  name: string,
  bytes: Uint8Array,
  properties: Record<string, string>,
  existingId: string | null,
): Promise<DriveFile> {
  const { body, contentType } = multipartBody(name, bytes, properties, existingId);
  const base = existingId === null ? `${UPLOAD}/files` : `${UPLOAD}/files/${existingId}`;
  const response = await execute(`${base}?uploadType=multipart&fields=${FIELDS}`, token, {
    method: existingId === null ? 'POST' : 'PATCH',
    headers: { 'Content-Type': contentType },
    body,
  });
  const file = toDriveFile((await response.json()) as Json);
  if (!file) throw new Error('Drive’s reply had no file in it');
  return file;
}

/** The whole of file [id]. */
export async function download(token: string, id: string): Promise<Uint8Array> {
  const response = await execute(`${API}/files/${id}?alt=media`, token);
  return new Uint8Array(await response.arrayBuffer());
}

/** Deletes file [id]. Already gone counts as done. */
export async function remove(token: string, id: string): Promise<void> {
  try {
    await execute(`${API}/files/${id}`, token, { method: 'DELETE' });
  } catch (error) {
    if (!(error instanceof DriveError && error.code === 404)) throw error;
  }
}
