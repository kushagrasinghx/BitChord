import assert from 'node:assert/strict';
import { test } from 'node:test';
import { allowsAnonymousYtRequest } from '../lib/ytRequests.ts';

test('allows only YouTube bootstrap, player and API endpoints', () => {
  for (const url of ['https://www.youtube.com/iframe_api', 'https://www.youtube.com/sw.js_data', 'https://www.youtube.com/s/player/abc/base.js', 'https://music.youtube.com/youtubei/v1/search', 'https://www.youtube.com/api/jnn/v1/GenerateIT']) {
    assert.equal(allowsAnonymousYtRequest(url), true, url);
  }
  for (const url of ['https://www.youtube.com/account', 'https://www.youtube.com/iframe_api_evil', 'https://www.youtube.com.evil.test/iframe_api', 'http://www.youtube.com/iframe_api', 'https://user:pass@www.youtube.com/iframe_api', 'not a url']) {
    assert.equal(allowsAnonymousYtRequest(url), false, url);
  }
});

test('accepts media only at HTTPS Googlevideo playback endpoints', () => {
  assert.equal(allowsAnonymousYtRequest('https://rr1.googlevideo.com/videoplayback?range=0-1023'), true);
  for (const url of ['https://googlevideo.com.evil.test/videoplayback', 'https://rr1.googlevideo.com/account', 'http://rr1.googlevideo.com/videoplayback']) {
    assert.equal(allowsAnonymousYtRequest(url), false);
  }
});
