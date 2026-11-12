// Tizen TV Polyfills for Chromium 56-69 (Samsung Smart TVs 2018-2021)
(function () {
  'use strict';

  // 1. globalThis polyfill (Chromium < 71)
  if (typeof globalThis === 'undefined') {
    if (typeof self !== 'undefined') {
      self.globalThis = self;
    } else if (typeof window !== 'undefined') {
      window.globalThis = window;
    } else if (typeof global !== 'undefined') {
      global.globalThis = global;
    } else {
      this.globalThis = this;
    }
  }

  // 2. crypto.randomUUID polyfill (Chromium < 92)
  if (typeof window !== 'undefined') {
    if (!window.crypto) {
      window.crypto = {};
    }
    if (!window.crypto.randomUUID) {
      window.crypto.randomUUID = function () {
        return ([1e7] + -1e3 + -4e3 + -8e3 + -1e11).replace(/[018]/g, function (c) {
          return (c ^ (window.crypto.getRandomValues(new Uint8Array(1))[0] & (15 >> (c / 4)))).toString(16);
        });
      };
    }
  }

  // 3. String.prototype.matchAll polyfill (Chromium < 73)
  if (!String.prototype.matchAll) {
    String.prototype.matchAll = function (regexp) {
      var str = String(this);
      var flags = regexp.flags || '';
      if (flags.indexOf('g') === -1) {
        flags += 'g';
      }
      var rx = new RegExp(regexp.source, flags);
      var matches = [];
      var match;
      while ((match = rx.exec(str)) !== null) {
        matches.push(match);
        if (!rx.global) break;
      }
      return matches[Symbol.iterator]();
    };
  }

  // 4. queueMicrotask polyfill (Chromium < 71)
  if (typeof queueMicrotask !== 'function') {
    globalThis.queueMicrotask = function (callback) {
      Promise.resolve().then(callback).catch(function (err) {
        setTimeout(function () { throw err; }, 0);
      });
    };
  }

  // 5. Object.fromEntries polyfill (Chromium < 73)
  if (!Object.fromEntries) {
    Object.fromEntries = function (entries) {
      if (!entries) return {};
      var obj = {};
      for (var pair of entries) {
        if (Object(pair) !== pair) continue;
        obj[pair[0]] = pair[1];
      }
      return obj;
    };
  }

  // 6. Promise.allSettled polyfill (Chromium < 76)
  if (!Promise.allSettled) {
    Promise.allSettled = function (promises) {
      return Promise.all(
        Array.from(promises).map(function (p) {
          return Promise.resolve(p).then(
            function (value) { return { status: 'fulfilled', value: value }; },
            function (reason) { return { status: 'rejected', reason: reason }; }
          );
        })
      );
    };
  }

  // 7. Promise.any polyfill (Chromium < 85)
  if (!Promise.any) {
    Promise.any = function (promises) {
      return new Promise(function (resolve, reject) {
        var errors = [];
        var remaining = 0;
        var arr = Array.from(promises);
        if (arr.length === 0) {
          reject(new (window.AggregateError || Error)(errors, 'All promises were rejected'));
          return;
        }
        remaining = arr.length;
        arr.forEach(function (p, i) {
          Promise.resolve(p).then(resolve, function (err) {
            errors[i] = err;
            remaining--;
            if (remaining === 0) {
              reject(new (window.AggregateError || Error)(errors, 'All promises were rejected'));
            }
          });
        });
      });
    };
  }

  // 8. String.prototype.replaceAll polyfill (Chromium < 85)
  if (!String.prototype.replaceAll) {
    String.prototype.replaceAll = function (search, replacement) {
      if (search instanceof RegExp) {
        if (!search.global) {
          throw new TypeError('replaceAll must be called with a global RegExp');
        }
        return this.replace(search, replacement);
      }
      return this.split(search).join(replacement);
    };
  }

  // 9. Array.prototype.at & String.prototype.at polyfill (Chromium < 92)
  function at(n) {
    n = Math.trunc(n) || 0;
    if (n < 0) n += this.length;
    if (n < 0 || n >= this.length) return undefined;
    return this[n];
  }
  if (!Array.prototype.at) {
    Array.prototype.at = at;
  }
  if (!String.prototype.at) {
    String.prototype.at = at;
  }
  [
    typeof Int8Array !== 'undefined' && Int8Array,
    typeof Uint8Array !== 'undefined' && Uint8Array,
    typeof Uint8ClampedArray !== 'undefined' && Uint8ClampedArray,
    typeof Int16Array !== 'undefined' && Int16Array,
    typeof Uint16Array !== 'undefined' && Uint16Array,
    typeof Int32Array !== 'undefined' && Int32Array,
    typeof Uint32Array !== 'undefined' && Uint32Array,
    typeof Float32Array !== 'undefined' && Float32Array,
    typeof Float64Array !== 'undefined' && Float64Array
  ].forEach(function (T) {
    if (T && T.prototype && !T.prototype.at) {
      T.prototype.at = at;
    }
  });

  // 10. Array.prototype.flat & flatMap polyfill (Chromium < 69)
  if (!Array.prototype.flat) {
    Array.prototype.flat = function (depth) {
      var d = depth === undefined ? 1 : Math.floor(depth);
      if (d < 1) return Array.prototype.slice.call(this);
      return (function flatten(arr, currentDepth) {
        var res = [];
        for (var i = 0; i < arr.length; i++) {
          if (i in arr) {
            var val = arr[i];
            if (Array.isArray(val) && currentDepth > 0) {
              res.push.apply(res, flatten(val, currentDepth - 1));
            } else {
              res.push(val);
            }
          }
        }
        return res;
      })(this, d);
    };
  }
  if (!Array.prototype.flatMap) {
    Array.prototype.flatMap = function (callback, thisArg) {
      return Array.prototype.map.call(this, callback, thisArg).flat();
    };
  }

  // 11. structuredClone polyfill (Chromium < 98)
  if (typeof structuredClone !== 'function') {
    globalThis.structuredClone = function (obj) {
      if (obj === undefined) return undefined;
      try {
        return JSON.parse(JSON.stringify(obj));
      } catch (e) {
        return Object.assign({}, obj);
      }
    };
  }

  // 12. AudioContext webkit prefix fallback
  if (typeof window !== 'undefined' && !window.AudioContext && window.webkitAudioContext) {
    window.AudioContext = window.webkitAudioContext;
  }

  console.log('[BitChord Tizen] All Chromium 56-69 polyfills loaded.');
})();
