// @vitest-environment happy-dom
import {
  isEligibleLocalMusic,
  localAlbums,
  localArtists,
  localFolders,
  localGroup,
  localGroupKind,
  LocalRow,
  localTitle,
  localTrackNumber,
  localUseTrackTags,
} from '@bitchord/shared-kotlin';
import { describe, expect, it } from 'vitest';
import { albumHref, artistHref, hrefFor } from '@/components/music/links';
import { isLocalId, LOCAL_FOLDER_ID, localTrackId } from './ids';
import { localView, trackOf } from './library';
import type { LocalFile } from './scan';

// The phone's own rules (LocalMusicRules.kt, LocalGroups.kt), run from the web through LocalWeb.kt.

describe('the non-music filter (LocalMediaFilterTest, from the web)', () => {
  it('drops short clips, WAVs and recorder folders, and keeps music it couldn’t time', () => {
    expect(isEligibleLocalMusic(29_999, 'effect.mp3', '/Music/effect.mp3')).toBe(false);
    expect(isEligibleLocalMusic(180_000, 'take.wav', '/Music/take.wav')).toBe(false);
    expect(isEligibleLocalMusic(180_000, 'memo.mp3', '/Phone/Voice Recorder/memo.mp3')).toBe(false);
    expect(isEligibleLocalMusic(50_000, 'Song.flac', '/Music/Artist/Song.flac')).toBe(true);
    expect(isEligibleLocalMusic(0, 'Starboy.m4a', '/Music/Starboy.m4a')).toBe(true);
  });
});

describe('tags', () => {
  it('names a track by its title tag, else its file name, and reads track numbers', () => {
    expect(localTitle('Song.mp3', 'x.mp3')).toBe('Song');
    expect(localTitle('  ', '01 Intro.flac')).toBe('01 Intro');
    expect(localTitle(null, '.mp3')).toBe('.mp3');
    expect([localTrackNumber('3/12'), localTrackNumber(' 7 '), localTrackNumber('0'), localTrackNumber(null)]).toEqual([3, 7, null, null]);
  });
});

describe('groupings (LocalGroupsTest, from the web)', () => {
  const rows = [
    new LocalRow('B', 'Band', 'Live: At The Hall', '/Music/Band/Live/b.mp3', 2, 'art'),
    new LocalRow('X', 'Solo', 'Other', '/Music/Solo/x.mp3', null, null),
    new LocalRow('A', 'Band', 'Live: At The Hall', '/Music/Band/Live/a.mp3', 1, null),
    new LocalRow('Loose', 'Band', null, '/Music/loose.mp3', null, null),
  ];

  it('groups albums by tag, in release order, with ids that survive colons in the name', () => {
    const albums = localAlbums(LOCAL_FOLDER_ID, rows);
    expect(albums.map((a) => a.title)).toEqual(['Live: At The Hall', 'Other']);
    expect(albums[0]!.songs).toEqual([2, 0]);
    expect(albums[0]!.subtitle).toBe('Band');
    expect(albums[0]!.thumbnailUrl).toBe('art');
    expect(localGroupKind(albums[0]!.browseId)).toBe('album');
    expect(localGroup(albums[0]!.browseId, rows)?.songs).toEqual([2, 0]);
    expect(localUseTrackTags([rows[0]!, rows[2]!])).toBe(true);
    expect(localUseTrackTags([rows[0]!, rows[1]!])).toBe(false);
  });

  it('groups artists by name and folders by directory', () => {
    expect(localArtists(LOCAL_FOLDER_ID, rows).map((a) => [a.title, a.kind, a.songs.length])).toEqual([
      ['Band', 'artist', 3],
      ['Solo', 'artist', 1],
    ]);
    const folders = localFolders(LOCAL_FOLDER_ID, rows);
    expect(folders.map((f) => [f.title, f.subtitle, f.kind])).toEqual([
      ['Live', 'Music/Band', 'folder'],
      ['Music', '', 'folder'],
      ['Solo', 'Music', 'folder'],
    ]);
    expect(localGroupKind('local:all')).toBeNull();
  });
});

describe('tracks', () => {
  const entry = (path: string, over: Partial<LocalFile> = {}): LocalFile => ({
    path,
    name: path.slice(path.lastIndexOf('/') + 1),
    size: 1,
    lastModified: 1,
    title: null,
    artist: null,
    album: null,
    albumArtist: null,
    track: null,
    year: null,
    durationSec: null,
    art: null,
    ...over,
  });

  it('gives a file a stable local id, its tags, and links to its own pages', () => {
    const track = trackOf(entry('/Music/01 Song.mp3', { artist: 'Band', album: 'Record', track: '4/10', durationSec: 201.6 }));
    expect(track.videoId).toBe(localTrackId('/Music/01 Song.mp3'));
    expect(isLocalId(track.videoId)).toBe(true);
    expect(localTrackId('/Music/a.mp3')).not.toBe(localTrackId('/Music/b.mp3'));
    expect(track).toMatchObject({ title: '01 Song', durationSec: 202, index: 4, thumbnail: null });
    expect(artistHref(track.artists[0]!.id)).toBe(`/library/local/${encodeURIComponent('local:group:artist:all:Band')}`);
    expect(albumHref(track.album!.id)).toBe(`/library/local/${encodeURIComponent('local:group:album:all:Record')}`);
    expect(hrefFor({ kind: 'album', id: track.album!.id! })).toBe(albumHref(track.album!.id));
    expect(trackOf(entry('/Music/x.mp3')).artists[0]!.name).toBe('Unknown Artist');
  });

  it('lists by title, and hides non-music only while the filter is on', () => {
    const files = [entry('/Music/b.mp3', { durationSec: 200 }), entry('/Music/a.mp3', { durationSec: 200 }), entry('/Music/ding.mp3', { durationSec: 3 })];
    expect(localView(files, true).tracks.map((t) => t.title)).toEqual(['a', 'b']);
    expect(localView(files, false).tracks.map((t) => t.title)).toEqual(['a', 'b', 'ding']);
  });
});
