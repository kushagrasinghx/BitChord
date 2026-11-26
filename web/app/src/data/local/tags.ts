/**
 * What a music file says about itself, read in the browser: title, artist,
 * album, album artist, track number, year, length and the embedded cover.
 *
 * Android has MediaStore and MediaMetadataRetriever do this
 * (LocalMediaRepository.kt buildSongFromUri); a browser has neither, so this
 * reads the containers itself: ID3v2.2–2.4 (MP3, and AAC with an ID3 tag),
 * MP4 `ilst`, FLAC Vorbis comments and PICTURE, Ogg Vorbis and Opus comments,
 * and only the length of a WAV. Only the headers are read, with
 * `Blob.slice()`, never the whole file: a library can be gigabytes.
 *
 * Every reader gives up quietly: a file it can't read still lists, under its
 * file name (see LocalWeb.kt localTitle), and the player times it when it plays.
 */

export interface LocalPicture {
  mime: string;
  data: Uint8Array;
}

export interface LocalTags {
  title: string | null;
  artist: string | null;
  album: string | null;
  albumArtist: string | null;
  /** As the tag has it: "3" or "3/12". */
  track: string | null;
  year: string | null;
  durationSec: number | null;
  picture: LocalPicture | null;
}

const empty = (): LocalTags => ({
  title: null,
  artist: null,
  album: null,
  albumArtist: null,
  track: null,
  year: null,
  durationSec: null,
  picture: null,
});

/** The most of one header read into memory: a tag with a big cover, never the audio behind it. */
const MAX_HEADER_BYTES = 32 * 1024 * 1024;

async function bytesAt(file: Blob, start: number, length: number): Promise<Uint8Array> {
  const end = Math.min(file.size, start + Math.min(length, MAX_HEADER_BYTES));
  if (start >= end) return new Uint8Array(0);
  return new Uint8Array(await file.slice(start, end).arrayBuffer());
}

const latin1 = new TextDecoder('latin1');
const utf8 = new TextDecoder('utf-8');
const utf16le = new TextDecoder('utf-16le');
const utf16be = new TextDecoder('utf-16be');

const ascii = (b: Uint8Array, at: number, n: number) => latin1.decode(b.subarray(at, at + n));
const u16be = (b: Uint8Array, at: number) => ((b[at]! << 8) | b[at + 1]!) >>> 0;
const u24be = (b: Uint8Array, at: number) => ((b[at]! << 16) | (b[at + 1]! << 8) | b[at + 2]!) >>> 0;
const u32be = (b: Uint8Array, at: number) => ((b[at]! << 24) | (b[at + 1]! << 16) | (b[at + 2]! << 8) | b[at + 3]!) >>> 0;
const u32le = (b: Uint8Array, at: number) => ((b[at + 3]! << 24) | (b[at + 2]! << 16) | (b[at + 1]! << 8) | b[at]!) >>> 0;
const u64be = (b: Uint8Array, at: number) => u32be(b, at) * 2 ** 32 + u32be(b, at + 4);
const u64le = (b: Uint8Array, at: number) => u32le(b, at + 4) * 2 ** 32 + u32le(b, at);
const syncsafe = (b: Uint8Array, at: number) => ((b[at]! & 0x7f) << 21) | ((b[at + 1]! & 0x7f) << 14) | ((b[at + 2]! & 0x7f) << 7) | (b[at + 3]! & 0x7f);

/** A tag's several values ("A\0B") as one credit, as MediaMetadataRetriever joins them. */
function joined(values: string[]): string | null {
  const kept = values.map((v) => v.trim()).filter(Boolean);
  return kept.length ? kept.join(', ') : null;
}

function finite(seconds: number): number | null {
  return Number.isFinite(seconds) && seconds > 0 ? seconds : null;
}

/** Reads [file]'s tags; [name] picks the reader when the first bytes don't. */
export async function readTags(file: Blob, name: string): Promise<LocalTags> {
  try {
    const head = await bytesAt(file, 0, 12);
    if (ascii(head, 0, 3) === 'ID3') return await readId3File(file, head);
    if (ascii(head, 0, 4) === 'fLaC') return await readFlac(file, 0);
    if (ascii(head, 0, 4) === 'OggS') return await readOgg(file);
    if (ascii(head, 4, 4) === 'ftyp') return await readMp4(file);
    if (ascii(head, 0, 4) === 'RIFF' && ascii(head, 8, 4) === 'WAVE') return await readWav(file);
    // A bare MP3 (no ID3v2): only its length is there to read.
    if (/\.mp3$/i.test(name)) return { ...empty(), durationSec: await mp3Duration(file, 0) };
  } catch {
    // A damaged or unusual file lists under its name.
  }
  return empty();
}

// ---- ID3v2 ---------------------------------------------------------------------------

const ID3_FRAMES: Record<string, keyof LocalTags> = {
  TIT2: 'title',
  TT2: 'title',
  TPE1: 'artist',
  TP1: 'artist',
  TALB: 'album',
  TAL: 'album',
  TPE2: 'albumArtist',
  TP2: 'albumArtist',
  TRCK: 'track',
  TRK: 'track',
  TYER: 'year',
  TYE: 'year',
  TDRC: 'year',
};

/** ID3's unsynchronisation: every 0xFF 0x00 was written for a 0xFF. */
function resync(b: Uint8Array): Uint8Array {
  const out = new Uint8Array(b.length);
  let n = 0;
  for (let i = 0; i < b.length; i++) {
    out[n++] = b[i]!;
    if (b[i] === 0xff && b[i + 1] === 0x00) i++;
  }
  return out.subarray(0, n);
}

/** One ID3 text, by its encoding byte: Latin-1, UTF-16 with a BOM, UTF-16BE, UTF-8. */
function id3Text(encoding: number, b: Uint8Array): string {
  if (encoding === 1) {
    if (b[0] === 0xfe && b[1] === 0xff) return utf16be.decode(b.subarray(2));
    if (b[0] === 0xff && b[1] === 0xfe) return utf16le.decode(b.subarray(2));
    return utf16le.decode(b);
  }
  if (encoding === 2) return utf16be.decode(b);
  return (encoding === 3 ? utf8 : latin1).decode(b);
}

/** Where a null-terminated string in [encoding] ends, and where what follows it starts. */
function terminated(encoding: number, b: Uint8Array, from: number): [end: number, next: number] {
  const wide = encoding === 1 || encoding === 2;
  for (let i = from; i < b.length; i += wide ? 2 : 1) {
    if (b[i] === 0 && (!wide || b[i + 1] === 0)) return [i, i + (wide ? 2 : 1)];
  }
  return [b.length, b.length];
}

function id3Picture(b: Uint8Array, v22: boolean): { type: number; picture: LocalPicture } | null {
  const encoding = b[0]!;
  let at = 1;
  let mime: string;
  if (v22) {
    // A three-letter format, "JPG" or "PNG".
    mime = ascii(b, 1, 3).toUpperCase() === 'PNG' ? 'image/png' : 'image/jpeg';
    at = 4;
  } else {
    const end = b.indexOf(0, 1);
    if (end < 0) return null;
    mime = ascii(b, 1, end - 1).toLowerCase() || 'image/jpeg';
    if (!mime.includes('/')) mime = `image/${mime === 'jpg' ? 'jpeg' : mime}`;
    at = end + 1;
  }
  const type = b[at]!;
  const [, data] = terminated(encoding, b, at + 1);
  return data < b.length ? { type, picture: { mime, data: b.slice(data) } } : null;
}

/** The ID3v2 tag at the head of [tag] (the whole tag, header included). */
export function parseId3(tag: Uint8Array): LocalTags {
  const tags = empty();
  const major = tag[3]!;
  const flags = tag[5]!;
  let body = tag.subarray(10, 10 + syncsafe(tag, 6));
  // Before 2.4 unsynchronisation covers the whole tag; in 2.4 it is per frame.
  if (major < 4 && flags & 0x80) body = resync(body);
  let at = 0;
  if (flags & 0x40) at = major === 3 ? 4 + u32be(body, 0) : syncsafe(body, 0);
  const v22 = major === 2;
  const headerSize = v22 ? 6 : 10;
  let cover: { type: number; picture: LocalPicture } | null = null;
  while (at + headerSize <= body.length) {
    const id = ascii(body, at, v22 ? 3 : 4);
    if (!/^[A-Z0-9]{3,4}$/.test(id)) break; // padding
    const size = v22 ? u24be(body, at + 3) : major === 4 ? syncsafe(body, at + 4) : u32be(body, at + 4);
    const frameFlags = v22 ? 0 : u16be(body, at + 8);
    let data = body.subarray(at + headerSize, at + headerSize + size);
    at += headerSize + size;
    if (major === 4) {
      // Compressed or encrypted frames are skipped; a length indicator and per-frame unsync are undone.
      if (frameFlags & 0x000c) continue;
      if (frameFlags & 0x0001) data = data.subarray(4);
      if (frameFlags & 0x0002) data = resync(data);
    } else if (major === 3 && frameFlags & 0x00c0) continue;
    if (!data.length) continue;
    if (id === 'APIC' || id === 'PIC') {
      const found = id3Picture(data, v22);
      // The front cover (type 3) wins; otherwise the first picture there is.
      if (found && (!cover || (found.type === 3 && cover.type !== 3))) cover = found;
      continue;
    }
    const field = ID3_FRAMES[id];
    if (!field || tags[field] !== null) continue;
    const text = joined(id3Text(data[0]!, data.subarray(1)).split('\0'));
    (tags as unknown as Record<string, string | null>)[field] = field === 'year' && text ? text.slice(0, 4) : text;
  }
  tags.picture = cover?.picture ?? null;
  return tags;
}

async function readId3File(file: Blob, head: Uint8Array): Promise<LocalTags> {
  const end = 10 + syncsafe(head, 6) + (head[5]! & 0x10 ? 10 : 0);
  const tags = parseId3(await bytesAt(file, 0, end));
  // What follows the tag decides the length: a FLAC stream (rare, but it happens), or MPEG audio.
  const next = await bytesAt(file, end, 4);
  if (ascii(next, 0, 4) === 'fLaC') {
    const flac = await readFlac(file, end);
    return { ...flac, ...Object.fromEntries(Object.entries(tags).filter(([, v]) => v !== null)), durationSec: flac.durationSec };
  }
  tags.durationSec = await mp3Duration(file, end);
  return tags;
}

// ---- MP3 length ----------------------------------------------------------------------

const MP3_BITRATES: Record<string, number[]> = {
  '1-1': [0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448],
  '1-2': [0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384],
  '1-3': [0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320],
  '2-1': [0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256],
  '2-2': [0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160],
  '2-3': [0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160],
};
const MP3_RATES = [44100, 48000, 32000];

/**
 * The length of the MPEG audio starting near [from]: exact from a Xing/Info
 * or VBRI header's frame count, else estimated from the first frame's bitrate
 * (right for constant bitrate, which is what an MP3 without one usually is).
 */
export async function mp3Duration(file: Blob, from: number): Promise<number | null> {
  const b = await bytesAt(file, from, 64 * 1024);
  for (let i = 0; i + 4 <= b.length; i++) {
    if (b[i] !== 0xff || (b[i + 1]! & 0xe0) !== 0xe0) continue;
    const versionBits = (b[i + 1]! >> 3) & 3; // 3 = MPEG 1, 2 = MPEG 2, 0 = MPEG 2.5
    const layerBits = (b[i + 1]! >> 1) & 3; // 3 = layer I, 2 = II, 1 = III
    const bitrateIndex = b[i + 2]! >> 4;
    const rateIndex = (b[i + 2]! >> 2) & 3;
    if (versionBits === 1 || layerBits === 0 || bitrateIndex === 0 || bitrateIndex === 15 || rateIndex === 3) continue;
    const layer = 4 - layerBits;
    const mpeg1 = versionBits === 3;
    const sampleRate = MP3_RATES[rateIndex]! / (mpeg1 ? 1 : versionBits === 2 ? 2 : 4);
    const samplesPerFrame = layer === 1 ? 384 : layer === 3 && !mpeg1 ? 576 : 1152;
    const mono = b[i + 3]! >> 6 === 3;
    const sideInfo = mpeg1 ? (mono ? 17 : 32) : mono ? 9 : 17;
    const xing = i + 4 + sideInfo;
    const tag = ascii(b, xing, 4);
    if ((tag === 'Xing' || tag === 'Info') && u32be(b, xing + 4) & 1) {
      return finite((u32be(b, xing + 8) * samplesPerFrame) / sampleRate);
    }
    if (ascii(b, i + 36, 4) === 'VBRI') return finite((u32be(b, i + 36 + 14) * samplesPerFrame) / sampleRate);
    const kbps = MP3_BITRATES[`${mpeg1 ? 1 : 2}-${layer}`]![bitrateIndex]!;
    return finite(((file.size - from - i) * 8) / (kbps * 1000));
  }
  return null;
}

// ---- Vorbis comments (FLAC, Ogg) -----------------------------------------------------

const VORBIS_FIELDS: Record<string, keyof LocalTags> = {
  TITLE: 'title',
  ARTIST: 'artist',
  ALBUM: 'album',
  ALBUMARTIST: 'albumArtist',
  'ALBUM ARTIST': 'albumArtist',
  TRACKNUMBER: 'track',
  DATE: 'year',
  YEAR: 'year',
};

/** A FLAC PICTURE block's body (also base64 inside an Ogg file's METADATA_BLOCK_PICTURE). */
function flacPicture(b: Uint8Array): { type: number; picture: LocalPicture } | null {
  const type = u32be(b, 0);
  const mimeLength = u32be(b, 4);
  const mime = ascii(b, 8, mimeLength) || 'image/jpeg';
  let at = 8 + mimeLength;
  at += 4 + u32be(b, at); // description
  at += 16; // width, height, depth, colours
  const length = u32be(b, at);
  at += 4;
  return at + length <= b.length && length > 0 ? { type, picture: { mime, data: b.slice(at, at + length) } } : null;
}

function base64Bytes(text: string): Uint8Array {
  const binary = atob(text.replace(/\s/g, ''));
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i);
  return out;
}

/** A Vorbis comment block (little-endian lengths, `KEY=value` entries) into [tags]. */
export function parseVorbisComments(b: Uint8Array, tags: LocalTags = empty()): LocalTags {
  const values = new Map<keyof LocalTags, string[]>();
  let at = 4 + u32le(b, 0); // vendor
  const count = u32le(b, at);
  at += 4;
  let cover: { type: number; picture: LocalPicture } | null = null;
  for (let i = 0; i < count && at + 4 <= b.length; i++) {
    const length = u32le(b, at);
    const entry = b.subarray(at + 4, at + 4 + length);
    at += 4 + length;
    const eq = entry.indexOf(0x3d);
    if (eq < 0) continue;
    const key = ascii(entry, 0, eq).toUpperCase();
    if (key === 'METADATA_BLOCK_PICTURE') {
      try {
        const found = flacPicture(base64Bytes(latin1.decode(entry.subarray(eq + 1))));
        if (found && (!cover || (found.type === 3 && cover.type !== 3))) cover = found;
      } catch {
        // Bad base64: no cover from it.
      }
      continue;
    }
    const field = VORBIS_FIELDS[key];
    if (field) values.set(field, [...(values.get(field) ?? []), utf8.decode(entry.subarray(eq + 1))]);
  }
  for (const [field, list] of values) {
    const text = joined(list);
    (tags as unknown as Record<string, string | null>)[field] = field === 'year' && text ? text.slice(0, 4) : text;
  }
  if (cover && !tags.picture) tags.picture = cover.picture;
  return tags;
}

// ---- FLAC ----------------------------------------------------------------------------

async function readFlac(file: Blob, start: number): Promise<LocalTags> {
  const tags = empty();
  let at = start + 4;
  let cover: { type: number; picture: LocalPicture } | null = null;
  for (let blocks = 0; blocks < 128; blocks++) {
    const header = await bytesAt(file, at, 4);
    if (header.length < 4) break;
    const last = header[0]! & 0x80;
    const type = header[0]! & 0x7f;
    const length = u24be(header, 1);
    if (type === 0 || type === 4 || type === 6) {
      const body = await bytesAt(file, at + 4, length);
      if (type === 0) {
        const sampleRate = (body[10]! << 12) | (body[11]! << 4) | (body[12]! >> 4);
        const samples = (body[13]! & 0x0f) * 2 ** 32 + u32be(body, 14);
        if (sampleRate) tags.durationSec = finite(samples / sampleRate);
      } else if (type === 4) {
        parseVorbisComments(body, tags);
      } else {
        const found = flacPicture(body);
        if (found && (!cover || (found.type === 3 && cover.type !== 3))) cover = found;
      }
    }
    at += 4 + length;
    if (last) break;
  }
  if (cover) tags.picture = cover.picture;
  return tags;
}

// ---- Ogg -----------------------------------------------------------------------------

/**
 * The first two packets of an Ogg stream (the codec's id header and its
 * comments), put back together from the pages: a comment packet holding a
 * cover runs over many pages.
 */
async function oggPackets(file: Blob, wanted: number): Promise<Uint8Array[]> {
  const packets: Uint8Array[] = [];
  let partial: Uint8Array[] = [];
  let at = 0;
  let total = 0;
  while (packets.length < wanted && total < MAX_HEADER_BYTES) {
    const header = await bytesAt(file, at, 27);
    if (header.length < 27 || ascii(header, 0, 4) !== 'OggS') break;
    const segments = header[26]!;
    const table = await bytesAt(file, at + 27, segments);
    const dataLength = table.reduce((sum, n) => sum + n, 0);
    const data = await bytesAt(file, at + 27 + segments, dataLength);
    at += 27 + segments + dataLength;
    total += dataLength;
    let offset = 0;
    for (const lacing of table) {
      partial.push(data.subarray(offset, offset + lacing));
      offset += lacing;
      // A lacing value under 255 ends the packet; 255 carries on into the next segment (or page).
      if (lacing < 255) {
        const length = partial.reduce((sum, p) => sum + p.length, 0);
        const packet = new Uint8Array(length);
        let o = 0;
        for (const p of partial) {
          packet.set(p, o);
          o += p.length;
        }
        packets.push(packet);
        partial = [];
        if (packets.length >= wanted) break;
      }
    }
  }
  return packets;
}

/** The last page's granule position: the stream's length in samples. */
async function lastGranule(file: Blob): Promise<number | null> {
  const tailStart = Math.max(0, file.size - 64 * 1024);
  const tail = await bytesAt(file, tailStart, file.size - tailStart);
  for (let i = tail.length - 27; i >= 0; i--) {
    if (tail[i] === 0x4f && ascii(tail, i, 4) === 'OggS') return u64le(tail, i + 6);
  }
  return null;
}

async function readOgg(file: Blob): Promise<LocalTags> {
  const [id, comments] = await oggPackets(file, 2);
  if (!id) return empty();
  const opus = ascii(id, 0, 8) === 'OpusHead';
  const vorbis = id[0] === 1 && ascii(id, 1, 6) === 'vorbis';
  const tags = empty();
  if (comments) {
    if (opus && ascii(comments, 0, 8) === 'OpusTags') parseVorbisComments(comments.subarray(8), tags);
    else if (vorbis && comments[0] === 3 && ascii(comments, 1, 6) === 'vorbis') parseVorbisComments(comments.subarray(7), tags);
  }
  const granule = await lastGranule(file);
  if (granule !== null) {
    // Opus always counts at 48 kHz, less the encoder's pre-skip; Vorbis at the rate its id header states.
    if (opus) tags.durationSec = finite((granule - (id[10]! | (id[11]! << 8))) / 48000);
    else if (vorbis) tags.durationSec = finite(granule / u32le(id, 12));
  }
  return tags;
}

// ---- MP4 -----------------------------------------------------------------------------

interface Box {
  type: string;
  /** Where the box's contents start, and end, in the buffer (or file) it was read from. */
  start: number;
  end: number;
}

/** The boxes laid end to end in b[from, to). */
function boxes(b: Uint8Array, from: number, to: number): Box[] {
  const out: Box[] = [];
  let at = from;
  while (at + 8 <= to) {
    let size = u32be(b, at);
    let header = 8;
    if (size === 1) {
      size = u64be(b, at + 8);
      header = 16;
    } else if (size === 0) size = to - at;
    if (size < header || at + size > to) break;
    out.push({ type: ascii(b, at + 4, 4), start: at + header, end: at + size });
    at += size;
  }
  return out;
}

const child = (b: Uint8Array, box: Box | undefined, type: string, skip = 0) =>
  box && boxes(b, box.start + skip, box.end).find((c) => c.type === type);

const MP4_FIELDS: Record<string, keyof LocalTags> = {
  '©nam': 'title',
  '©ART': 'artist',
  '©alb': 'album',
  aART: 'albumArtist',
  '©day': 'year',
};

/** The `moov` box, wherever it sits: after the audio is common, so it is found by walking the top-level boxes' headers. */
async function readMoov(file: Blob): Promise<Uint8Array | null> {
  let at = 0;
  for (let n = 0; n < 64 && at + 8 <= file.size; n++) {
    const header = await bytesAt(file, at, 16);
    let size = u32be(header, 0);
    if (size === 1) size = u64be(header, 8);
    else if (size === 0) size = file.size - at;
    if (size < 8) return null;
    if (ascii(header, 4, 4) === 'moov') return size <= MAX_HEADER_BYTES ? bytesAt(file, at, size) : null;
    at += size;
  }
  return null;
}

/** The tags and length in an MP4's `moov` box (the whole box, header included). */
export function parseMoov(moov: Uint8Array): LocalTags {
  const tags = empty();
  const root: Box = { type: 'moov', start: 8, end: moov.length };
  const mvhd = child(moov, root, 'mvhd');
  if (mvhd) {
    const v1 = moov[mvhd.start] === 1;
    const timescale = u32be(moov, mvhd.start + (v1 ? 20 : 12));
    const duration = v1 ? u64be(moov, mvhd.start + 24) : u32be(moov, mvhd.start + 16);
    if (timescale) tags.durationSec = finite(duration / timescale);
  }
  const meta = child(moov, child(moov, root, 'udta'), 'meta');
  if (!meta) return tags;
  // iTunes writes `meta` as a full box (four bytes of version and flags), QuickTime doesn't.
  const skip = ascii(moov, meta.start + 4, 4) === 'hdlr' ? 0 : 4;
  const ilst = child(moov, meta, 'ilst', skip);
  if (!ilst) return tags;
  for (const item of boxes(moov, ilst.start, ilst.end)) {
    const data = child(moov, item, 'data');
    if (!data || data.end - data.start < 8) continue;
    const kind = u32be(moov, data.start) & 0xffffff;
    const payload = moov.subarray(data.start + 8, data.end);
    if (item.type === 'trkn' && payload.length >= 4) {
      const n = u16be(payload, 2);
      const of = payload.length >= 6 ? u16be(payload, 4) : 0;
      if (n) tags.track = of ? `${n}/${of}` : String(n);
    } else if (item.type === 'covr' && !tags.picture && payload.length) {
      tags.picture = { mime: kind === 14 ? 'image/png' : 'image/jpeg', data: payload.slice() };
    } else {
      const field = MP4_FIELDS[item.type];
      if (field && kind === 1) {
        const text = utf8.decode(payload).trim() || null;
        (tags as unknown as Record<string, string | null>)[field] = field === 'year' && text ? text.slice(0, 4) : text;
      }
    }
  }
  return tags;
}

async function readMp4(file: Blob): Promise<LocalTags> {
  const moov = await readMoov(file);
  return moov ? parseMoov(moov) : empty();
}

// ---- WAV -----------------------------------------------------------------------------

/** A WAV's length, from its `fmt ` byte rate and `data` size. Its tags (LIST INFO) are rare enough to leave. */
async function readWav(file: Blob): Promise<LocalTags> {
  const b = await bytesAt(file, 0, 64 * 1024);
  let at = 12;
  let byteRate = 0;
  while (at + 8 <= b.length) {
    const id = ascii(b, at, 4);
    const size = u32le(b, at + 4);
    if (id === 'fmt ') byteRate = u32le(b, at + 16);
    if (id === 'data') return { ...empty(), durationSec: byteRate ? finite(Math.min(size, file.size - at - 8) / byteRate) : null };
    at += 8 + size + (size & 1);
  }
  return empty();
}
