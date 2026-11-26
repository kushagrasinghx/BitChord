import { describe, expect, it } from 'vitest';
import { readTags } from './tags';

// Tiny synthetic files, built byte by byte: just enough container for each reader.

type Part = Uint8Array | number[] | string;
const enc = new TextEncoder();
const bytes = (p: Part): Uint8Array => (typeof p === 'string' ? enc.encode(p) : p instanceof Uint8Array ? p : Uint8Array.from(p));
function cat(...parts: Part[]): Uint8Array {
  const list = parts.map(bytes);
  const out = new Uint8Array(list.reduce((n, p) => n + p.length, 0));
  let at = 0;
  for (const p of list) {
    out.set(p, at);
    at += p.length;
  }
  return out;
}
const be32 = (n: number) => [(n >>> 24) & 255, (n >>> 16) & 255, (n >>> 8) & 255, n & 255];
const be16 = (n: number) => [(n >>> 8) & 255, n & 255];
const le32 = (n: number) => [n & 255, (n >>> 8) & 255, (n >>> 16) & 255, (n >>> 24) & 255];
const le16 = (n: number) => [n & 255, (n >>> 8) & 255];
const le64 = (n: number) => [...le32(n % 2 ** 32), ...le32(Math.floor(n / 2 ** 32))];
const syncsafe = (n: number) => [(n >> 21) & 127, (n >> 14) & 127, (n >> 7) & 127, n & 127];
const blob = (...parts: Part[]) => new Blob([cat(...parts) as BlobPart]);
const COVER = [0xff, 0xd8, 0xff, 0xe0, 1, 2, 3];

describe('ID3v2', () => {
  const frame23 = (id: string, body: Part) => cat(id, be32(bytes(body).length), [0, 0], body);
  const frame24 = (id: string, body: Part) => cat(id, syncsafe(bytes(body).length), [0, 0], body);
  const tag = (major: number, frames: Uint8Array) => cat('ID3', [major, 0, 0], syncsafe(frames.length), frames);
  const utf16 = (text: string) => cat([0xff, 0xfe], Uint8Array.from([...text].flatMap((c) => le16(c.charCodeAt(0)))));

  it('reads a 2.3 tag, its front cover, and the Xing frame count', async () => {
    const frames = cat(
      frame23('TIT2', cat([0], 'Song')),
      frame23('TPE1', cat([1], utf16('Ärtist'))),
      frame23('TALB', cat([0], 'Album')),
      frame23('TPE2', cat([0], 'Band')),
      frame23('TRCK', cat([0], '3/12')),
      frame23('TYER', cat([0], '2019')),
      frame23('APIC', cat([0], 'image/png', [0], [0], 'back', [0], [9, 9])),
      frame23('APIC', cat([0], 'image/jpeg', [0], [3], 'front', [0], COVER)),
      [0, 0, 0, 0], // padding
    );
    // MPEG-1 layer III, 128 kbps, 44.1 kHz, joint stereo; Xing 32 bytes of side info in, 1000 frames.
    const xingFrame = cat([0xff, 0xfb, 0x90, 0x64], new Uint8Array(32), 'Xing', be32(1), be32(1000), new Uint8Array(400));
    const tags = await readTags(blob(tag(3, frames), xingFrame), 'a.mp3');
    expect(tags).toMatchObject({ title: 'Song', artist: 'Ärtist', album: 'Album', albumArtist: 'Band', track: '3/12', year: '2019' });
    expect(tags.picture?.mime).toBe('image/jpeg');
    expect([...tags.picture!.data]).toEqual(COVER);
    expect(tags.durationSec).toBeCloseTo((1000 * 1152) / 44100, 3);
  });

  it('reads a 2.4 tag in UTF-8 with several artists, and times constant bitrate audio', async () => {
    const frames = cat(frame24('TIT2', cat([3], 'Ünïcode')), frame24('TPE1', cat([3], 'A', [0], 'B')), frame24('TDRC', cat([3], '2021-05-01')));
    const audio = cat([0xff, 0xfb, 0x90, 0x64], new Uint8Array(16_000 - 4));
    const tags = await readTags(blob(tag(4, frames), audio), 'b.mp3');
    expect(tags).toMatchObject({ title: 'Ünïcode', artist: 'A, B', year: '2021', picture: null });
    expect(tags.durationSec).toBeCloseTo(1, 3); // 16,000 bytes at 128 kbps
  });
});

describe('MP4', () => {
  // A box type is four bytes, "©" one of them (0xA9), not UTF-8's two.
  const box = (type: string, ...body: Part[]) => {
    const content = cat(...body);
    return cat(be32(content.length + 8), Uint8Array.from(type, (c) => c.charCodeAt(0)), content);
  };
  const text = (type: string, value: string) => box(type, box('data', be32(1), be32(0), value));

  it('reads ilst from a moov after the audio, and mvhd for the length', async () => {
    const mvhd = box('mvhd', [0, 0, 0, 0], be32(0), be32(0), be32(1000), be32(215_000), new Uint8Array(80));
    const ilst = box(
      'ilst',
      text('©nam', 'M4A Song'),
      text('©ART', 'Artist'),
      text('©alb', 'Record'),
      text('aART', 'Various Artists'),
      text('©day', '2010-03-04'),
      box('trkn', box('data', be32(0), be32(0), [0, 0, ...be16(5), ...be16(9), 0, 0])),
      box('covr', box('data', be32(13), be32(0), COVER)),
    );
    const moov = box('moov', mvhd, box('udta', box('meta', [0, 0, 0, 0], box('hdlr', new Uint8Array(25)), ilst)));
    const tags = await readTags(blob(box('ftyp', 'M4A ', be32(0)), box('mdat', new Uint8Array(1000)), moov), 'c.m4a');
    expect(tags).toMatchObject({ title: 'M4A Song', artist: 'Artist', album: 'Record', albumArtist: 'Various Artists', track: '5/9', year: '2010' });
    expect(tags.durationSec).toBe(215);
    expect([...tags.picture!.data]).toEqual(COVER);
  });
});

describe('FLAC', () => {
  const block = (type: number, body: Uint8Array, last = false) => cat([(last ? 0x80 : 0) | type, (body.length >> 16) & 255, (body.length >> 8) & 255, body.length & 255], body);
  const comments = (...entries: string[]) => cat(le32(1), 'x', le32(entries.length), ...entries.flatMap((e) => [le32(enc.encode(e).length), e]));
  const picture = (type: number, mime: string, data: number[]) => cat(be32(type), be32(mime.length), mime, be32(0), new Uint8Array(16), be32(data.length), data);

  it('reads STREAMINFO, Vorbis comments and the front-cover PICTURE', async () => {
    // 44,100 Hz (20 bits: 0A C4 4…) and 441,000 samples: ten seconds.
    const streaminfo = cat(new Uint8Array(10), [0x0a, 0xc4, 0x42, 0xf0], be32(441_000), new Uint8Array(16));
    const file = blob(
      'fLaC',
      block(0, streaminfo),
      block(4, comments('TITLE=Flac Song', 'artist=A', 'ARTIST=B', 'ALBUM=Al', 'TRACKNUMBER=7', 'DATE=2001-01-01')),
      block(6, picture(3, 'image/png', COVER), true),
      new Uint8Array(100),
    );
    const tags = await readTags(file, 'd.flac');
    expect(tags).toMatchObject({ title: 'Flac Song', artist: 'A, B', album: 'Al', track: '7', year: '2001' });
    expect(tags.durationSec).toBe(10);
    expect(tags.picture?.mime).toBe('image/png');
  });
});

describe('Ogg', () => {
  const page = (granule: number, seq: number, lacing: number[], data: Uint8Array) =>
    cat('OggS', [0, seq === 0 ? 2 : 0], le64(granule), le32(1), le32(seq), le32(0), [lacing.length], lacing, data);
  const comments = (...entries: string[]) => cat(le32(3), 'enc', le32(entries.length), ...entries.flatMap((e) => [le32(enc.encode(e).length), e]));
  const flacPicture = cat(be32(3), be32(10), 'image/jpeg', be32(0), new Uint8Array(16), be32(COVER.length), COVER);

  it('reads Opus comments spanning two pages, the picture inside them, and the length from the last granule', async () => {
    const head = cat('OpusHead', [1, 2], le16(312), le32(48_000), [0, 0, 0]);
    const picture = `METADATA_BLOCK_PICTURE=${btoa(String.fromCharCode(...flacPicture))}`;
    let tags = cat('OpusTags', comments('TITLE=Opus Song', 'ARTIST=Singer', picture));
    // Padded to 300 bytes with one more comment, so it runs 255 bytes on one page and 45 on the next.
    tags = cat('OpusTags', comments('TITLE=Opus Song', 'ARTIST=Singer', picture, `X=${'.'.repeat(300 - tags.length - 6)}`));
    expect(tags.length).toBe(300);
    const file = blob(
      page(0, 0, [head.length], head),
      page(0, 1, [255], tags.subarray(0, 255)),
      page(0, 2, [45], tags.subarray(255)),
      page(312 + 48_000 * 5, 3, [10], new Uint8Array(10)),
    );
    const read = await readTags(file, 'e.opus');
    expect(read).toMatchObject({ title: 'Opus Song', artist: 'Singer' });
    expect(read.durationSec).toBe(5);
    expect([...read.picture!.data]).toEqual(COVER);
  });

  it('reads Vorbis comments and times by the id header’s rate', async () => {
    const id = cat([1], 'vorbis', le32(0), [1], le32(44_100), new Uint8Array(13));
    const comment = cat([3], 'vorbis', comments('TITLE=Vorbis Song', 'ALBUM=LP'), [1]);
    const file = blob(page(0, 0, [id.length], id), page(0, 1, [comment.length], comment), page(441_000, 2, [4], new Uint8Array(4)));
    const read = await readTags(file, 'f.ogg');
    expect(read).toMatchObject({ title: 'Vorbis Song', album: 'LP' });
    expect(read.durationSec).toBe(10);
  });
});

describe('the rest', () => {
  it('times a WAV from its byte rate and data size', async () => {
    const fmt = cat('fmt ', le32(16), le16(1), le16(2), le32(44_100), le32(176_400), le16(4), le16(16));
    const tags = await readTags(blob('RIFF', le32(0), 'WAVE', fmt, 'data', le32(352_800), new Uint8Array(352_800)), 'g.wav');
    expect(tags.durationSec).toBe(2);
  });

  it('gives up quietly on anything it can’t read', async () => {
    expect(await readTags(blob('not audio at all'), 'h.flac')).toMatchObject({ title: null, durationSec: null, picture: null });
    // A truncated ID3 header claims more than there is.
    expect((await readTags(blob('ID3', [3, 0, 0], syncsafe(5000), 'TIT2'), 'i.mp3')).title).toBeNull();
  });
});
