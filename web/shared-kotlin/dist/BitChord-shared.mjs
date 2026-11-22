import {
  substringAfter35b3qhto7hchb as substringAfter,
  charSequenceLength3278n89t01tmv as charSequenceLength,
  _Char___init__impl__6a9atx2gndcvjvc5pke as _Char___init__impl__6a9atx,
  substringBefore3n7kj60w69hju as substringBefore,
  endsWith3cq61xxngobwh as endsWith,
  substringiqarkczpya5m as substring,
  substring3saq8ornu0luv as substring_0,
  enumEntries20mr21zbe3az4 as enumEntries,
  Unit_instancev9v8hjid95df as Unit_instance,
  Enum3alwj03lh1n41 as Enum,
  VOID3gxj6tk5isa35 as VOID,
  protoOf180f3jzyo7rfj as protoOf,
  initMetadataForClassbxx6q50dy2s7 as initMetadataForClass,
  Regex_init_$Create$17zqsmfd15n8x as Regex_init_$Create$,
  setOf45ia9pnfhe90 as setOf,
  getStringHashCode26igk1bx568vk as getStringHashCode,
  emptyList1g2z5xcrvp2zy as emptyList,
  equals2au1ep9vhcato as equals,
  toSet2orjxp16sotqu as toSet,
  withIndex37cl61h9v5txo as withIndex,
  collectionSizeOrDefault36dulx8yinfqm as collectionSizeOrDefault,
  mapCapacity1h45rc3eh9p2l as mapCapacity,
  coerceAtLeast2bkz8m9ik7hep as coerceAtLeast,
  LinkedHashMap_init_$Create$1gzy1x4a46gd4 as LinkedHashMap_init_$Create$,
  to2cs3ny02qtbcb as to,
  ArrayList_init_$Create$2byt5m5yn22yy as ArrayList_init_$Create$,
  getValue48kllevslyh6 as getValue,
  HashSet_init_$Create$2w31ptb4ree5i as HashSet_init_$Create$,
  ArrayList_init_$Create$37gc04va6yfo2 as ArrayList_init_$Create$_0,
  getOrNull1go7ef9ldk0df as getOrNull,
  asReversed2y2qzr7vrqd5 as asReversed,
  toList383f556t1dixk as toList,
  copyToArray2j022khrow2yi as copyToArray,
} from './kotlin-kotlin-stdlib.mjs';
//region block: imports
var imul = Math.imul;
//endregion
//region block: pre-declaration
initMetadataForClass(SearchFilter, 'SearchFilter', VOID, Enum);
initMetadataForClass(PlaylistMove, 'PlaylistMove');
initMetadataForClass(PlaylistMove_0, 'PlaylistMove');
initMetadataForClass(SearchFilterOption, 'SearchFilterOption');
//endregion
function get_YT_THUMB_NAME_REGEX() {
  _init_properties_Models_kt__qy51oq();
  return YT_THUMB_NAME_REGEX;
}
var YT_THUMB_NAME_REGEX;
function get_YT_UPGRADED_RENDITIONS() {
  _init_properties_Models_kt__qy51oq();
  return YT_UPGRADED_RENDITIONS;
}
var YT_UPGRADED_RENDITIONS;
function get_SIZE_HINT() {
  _init_properties_Models_kt__qy51oq();
  return SIZE_HINT;
}
var SIZE_HINT;
function get_PARAM_SIZE_HINT() {
  _init_properties_Models_kt__qy51oq();
  return PARAM_SIZE_HINT;
}
var PARAM_SIZE_HINT;
function artworkAt(_this__u8e3s4, px) {
  _init_properties_Models_kt__qy51oq();
  if (_this__u8e3s4 == null)
    return null;
  var tmp2 = get_SIZE_HINT();
  // Inline function 'kotlin.text.replace' call
  var replacement = 'w' + px + '-h' + px;
  var sized = tmp2.m5(_this__u8e3s4, replacement);
  var tmp;
  if (isGoogleImageHost(_this__u8e3s4)) {
    var tmp2_0 = get_PARAM_SIZE_HINT();
    // Inline function 'kotlin.text.replace' call
    var replacement_0 = '=s' + px;
    tmp = tmp2_0.m5(sized, replacement_0);
  } else if (isYouTubeThumbnailHost(_this__u8e3s4)) {
    tmp = upgradeYouTubeThumbnail(sized, px);
  } else {
    tmp = sized;
  }
  return tmp;
}
function isGoogleImageHost(url) {
  _init_properties_Models_kt__qy51oq();
  // Inline function 'kotlin.text.ifEmpty' call
  var this_0 = substringAfter(url, '://', '');
  var tmp;
  // Inline function 'kotlin.text.isEmpty' call
  if (charSequenceLength(this_0) === 0) {
    tmp = substringAfter(url, '//', '');
  } else {
    tmp = this_0;
  }
  var tmp$ret$0 = tmp;
  // Inline function 'kotlin.text.lowercase' call
  // Inline function 'kotlin.js.asDynamic' call
  var host = substringBefore(substringBefore(substringBefore(tmp$ret$0, _Char___init__impl__6a9atx(47)), _Char___init__impl__6a9atx(58)), _Char___init__impl__6a9atx(63)).toLowerCase();
  return host === 'ggpht.com' || endsWith(host, '.ggpht.com') || host === 'googleusercontent.com' || endsWith(host, '.googleusercontent.com');
}
function isYouTubeThumbnailHost(url) {
  _init_properties_Models_kt__qy51oq();
  // Inline function 'kotlin.text.ifEmpty' call
  var this_0 = substringAfter(url, '://', '');
  var tmp;
  // Inline function 'kotlin.text.isEmpty' call
  if (charSequenceLength(this_0) === 0) {
    tmp = substringAfter(url, '//', '');
  } else {
    tmp = this_0;
  }
  var tmp$ret$0 = tmp;
  // Inline function 'kotlin.text.lowercase' call
  // Inline function 'kotlin.js.asDynamic' call
  var host = substringBefore(substringBefore(substringBefore(tmp$ret$0, _Char___init__impl__6a9atx(47)), _Char___init__impl__6a9atx(58)), _Char___init__impl__6a9atx(63)).toLowerCase();
  return host === 'i.ytimg.com' || endsWith(host, '.ytimg.com') || host === 'img.youtube.com' || endsWith(host, '.youtube.com');
}
function upgradeYouTubeThumbnail(url, px) {
  _init_properties_Models_kt__qy51oq();
  var tmp0_elvis_lhs = get_YT_THUMB_NAME_REGEX().l5(url);
  var tmp;
  if (tmp0_elvis_lhs == null) {
    return url;
  } else {
    tmp = tmp0_elvis_lhs;
  }
  var match = tmp;
  var current = match.k6().i(1);
  var tmp_0;
  if (px >= 480) {
    tmp_0 = 'maxresdefault';
  } else if (px >= 240) {
    tmp_0 = 'sddefault';
  } else if (get_YT_UPGRADED_RENDITIONS().m(current)) {
    tmp_0 = 'hqdefault';
  } else {
    return url;
  }
  var target = tmp_0;
  return target === current ? url : withRendition(url, match, target);
}
function withRendition(url, match, rendition) {
  _init_properties_Models_kt__qy51oq();
  return substringBefore(substring(url, 0, match.o5().o7_1) + ('/' + rendition + '.' + match.k6().i(2)) + substring_0(url, match.o5().p7_1 + 1 | 0), _Char___init__impl__6a9atx(63));
}
var SearchFilter_ALL_instance;
var SearchFilter_SONGS_instance;
var SearchFilter_VIDEOS_instance;
var SearchFilter_ALBUMS_instance;
var SearchFilter_ARTISTS_instance;
var SearchFilter_PLAYLISTS_instance;
var SearchFilter_LIBRARY_instance;
function values() {
  return [SearchFilter_ALL_getInstance(), SearchFilter_SONGS_getInstance(), SearchFilter_VIDEOS_getInstance(), SearchFilter_ALBUMS_getInstance(), SearchFilter_ARTISTS_getInstance(), SearchFilter_PLAYLISTS_getInstance(), SearchFilter_LIBRARY_getInstance()];
}
function get_entries() {
  if ($ENTRIES == null)
    $ENTRIES = enumEntries(values());
  return $ENTRIES;
}
var SearchFilter_entriesInitialized;
function SearchFilter_initEntries() {
  if (SearchFilter_entriesInitialized)
    return Unit_instance;
  SearchFilter_entriesInitialized = true;
  SearchFilter_ALL_instance = new SearchFilter('ALL', 0, 'All', null);
  SearchFilter_SONGS_instance = new SearchFilter('SONGS', 1, 'Songs', 'EgWKAQIIAWoKEAkQChAFEAMQBA==');
  SearchFilter_VIDEOS_instance = new SearchFilter('VIDEOS', 2, 'Videos', 'EgWKAQIQAWoKEAkQChAFEAMQBA==');
  SearchFilter_ALBUMS_instance = new SearchFilter('ALBUMS', 3, 'Albums', 'EgWKAQIYAWoKEAkQChAFEAMQBA==');
  SearchFilter_ARTISTS_instance = new SearchFilter('ARTISTS', 4, 'Artists', 'EgWKAQIgAWoKEAkQChAFEAMQBA==');
  SearchFilter_PLAYLISTS_instance = new SearchFilter('PLAYLISTS', 5, 'Playlists', 'EgWKAQIoAWoKEAkQChAFEAMQBA==');
  SearchFilter_LIBRARY_instance = new SearchFilter('LIBRARY', 6, 'Library', 'agIYBA==', true);
}
var $ENTRIES;
function SearchFilter(name, ordinal, label, params, requiresAuth) {
  requiresAuth = requiresAuth === VOID ? false : requiresAuth;
  Enum.call(this, name, ordinal);
  this.a8_1 = label;
  this.b8_1 = params;
  this.c8_1 = requiresAuth;
}
function SearchFilter_ALL_getInstance() {
  SearchFilter_initEntries();
  return SearchFilter_ALL_instance;
}
function SearchFilter_SONGS_getInstance() {
  SearchFilter_initEntries();
  return SearchFilter_SONGS_instance;
}
function SearchFilter_VIDEOS_getInstance() {
  SearchFilter_initEntries();
  return SearchFilter_VIDEOS_instance;
}
function SearchFilter_ALBUMS_getInstance() {
  SearchFilter_initEntries();
  return SearchFilter_ALBUMS_instance;
}
function SearchFilter_ARTISTS_getInstance() {
  SearchFilter_initEntries();
  return SearchFilter_ARTISTS_instance;
}
function SearchFilter_PLAYLISTS_getInstance() {
  SearchFilter_initEntries();
  return SearchFilter_PLAYLISTS_instance;
}
function SearchFilter_LIBRARY_getInstance() {
  SearchFilter_initEntries();
  return SearchFilter_LIBRARY_instance;
}
var properties_initialized_Models_kt_2wtf4k;
function _init_properties_Models_kt__qy51oq() {
  if (!properties_initialized_Models_kt_2wtf4k) {
    properties_initialized_Models_kt_2wtf4k = true;
    YT_THUMB_NAME_REGEX = Regex_init_$Create$('/(default|mqdefault|hqdefault|sddefault|hq720|maxresdefault)\\.([a-zA-Z0-9]+)');
    YT_UPGRADED_RENDITIONS = setOf(['sddefault', 'hq720', 'maxresdefault']);
    SIZE_HINT = Regex_init_$Create$('w\\d+-h\\d+');
    PARAM_SIZE_HINT = Regex_init_$Create$('=s\\d+');
  }
}
function PlaylistMove(setVideoId, before) {
  this.d8_1 = setVideoId;
  this.e8_1 = before;
}
protoOf(PlaylistMove).toString = function () {
  return 'PlaylistMove(setVideoId=' + this.d8_1 + ', before=' + this.e8_1 + ')';
};
protoOf(PlaylistMove).hashCode = function () {
  var result = getStringHashCode(this.d8_1);
  result = imul(result, 31) + (this.e8_1 == null ? 0 : getStringHashCode(this.e8_1)) | 0;
  return result;
};
protoOf(PlaylistMove).equals = function (other) {
  if (this === other)
    return true;
  if (!(other instanceof PlaylistMove))
    return false;
  if (!(this.d8_1 === other.d8_1))
    return false;
  if (!(this.e8_1 == other.e8_1))
    return false;
  return true;
};
function playlistMoves(current, target) {
  if (equals(current, target))
    return emptyList();
  if (!(current.h() === target.h()) || !equals(toSet(current), toSet(target)) || !(toSet(current).h() === current.h())) {
    return emptyList();
  }
  // Inline function 'kotlin.collections.associate' call
  var this_0 = withIndex(current);
  var capacity = coerceAtLeast(mapCapacity(collectionSizeOrDefault(this_0, 10)), 16);
  // Inline function 'kotlin.collections.associateTo' call
  var destination = LinkedHashMap_init_$Create$(capacity);
  var _iterator__ex2g4s = this_0.e();
  while (_iterator__ex2g4s.f()) {
    var element = _iterator__ex2g4s.g();
    var index = element.s6();
    var id = element.t6();
    // Inline function 'kotlin.collections.plusAssign' call
    var pair = to(id, index);
    destination.c2(pair.w7_1, pair.x7_1);
  }
  var position = destination;
  // Inline function 'kotlin.collections.map' call
  // Inline function 'kotlin.collections.mapTo' call
  var destination_0 = ArrayList_init_$Create$(collectionSizeOrDefault(target, 10));
  var _iterator__ex2g4s_0 = target.e();
  while (_iterator__ex2g4s_0.f()) {
    var item = _iterator__ex2g4s_0.g();
    var tmp$ret$6 = getValue(position, item);
    destination_0.c(tmp$ret$6);
  }
  var tmp0 = longestIncreasingRun(destination_0);
  // Inline function 'kotlin.collections.mapTo' call
  var destination_1 = HashSet_init_$Create$();
  var _iterator__ex2g4s_1 = tmp0.e();
  while (_iterator__ex2g4s_1.f()) {
    var item_0 = _iterator__ex2g4s_1.g();
    var tmp$ret$8 = target.i(item_0);
    destination_1.c(tmp$ret$8);
  }
  var stay = destination_1;
  var moves = ArrayList_init_$Create$_0();
  var inductionVariable = target.h() - 1 | 0;
  if (0 <= inductionVariable)
    $l$loop: do {
      var index_0 = inductionVariable;
      inductionVariable = inductionVariable + -1 | 0;
      var id_0 = target.i(index_0);
      if (stay.m(id_0))
        continue $l$loop;
      // Inline function 'kotlin.collections.plusAssign' call
      var element_0 = new PlaylistMove(id_0, getOrNull(target, index_0 + 1 | 0));
      moves.c(element_0);
    }
     while (0 <= inductionVariable);
  return moves;
}
function longestIncreasingRun(values) {
  if (values.l())
    return emptyList();
  var tails = new Int32Array(values.h());
  var tmp = 0;
  var tmp_0 = values.h();
  var tmp_1 = new Int32Array(tmp_0);
  while (tmp < tmp_0) {
    tmp_1[tmp] = -1;
    tmp = tmp + 1 | 0;
  }
  var previous = tmp_1;
  var length = 0;
  var inductionVariable = 0;
  var last = values.h() - 1 | 0;
  if (inductionVariable <= last)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      var low = 0;
      var high = length;
      while (low < high) {
        var mid = (low + high | 0) >>> 1 | 0;
        if (values.i(tails[mid]) < values.i(i))
          low = mid + 1 | 0;
        else
          high = mid;
      }
      if (low > 0) {
        previous[i] = tails[low - 1 | 0];
      }
      tails[low] = i;
      if (low === length) {
        length = length + 1 | 0;
      }
    }
     while (inductionVariable <= last);
  var run = ArrayList_init_$Create$(length);
  var at = tails[length - 1 | 0];
  while (at >= 0) {
    // Inline function 'kotlin.collections.plusAssign' call
    var element = at;
    run.c(element);
    at = previous[at];
  }
  return asReversed(run);
}
function artworkAt_0(url, px) {
  return artworkAt(url, px);
}
function PlaylistMove_0(setVideoId, before) {
  this.setVideoId = setVideoId;
  this.before = before;
}
protoOf(PlaylistMove_0).f8 = function () {
  return this.setVideoId;
};
protoOf(PlaylistMove_0).g8 = function () {
  return this.before;
};
function playlistMoves_0(current, target) {
  // Inline function 'kotlin.collections.map' call
  var this_0 = playlistMoves(toList(current), toList(target));
  // Inline function 'kotlin.collections.mapTo' call
  var destination = ArrayList_init_$Create$(collectionSizeOrDefault(this_0, 10));
  var _iterator__ex2g4s = this_0.e();
  while (_iterator__ex2g4s.f()) {
    var item = _iterator__ex2g4s.g();
    var tmp$ret$2 = new PlaylistMove_0(item.d8_1, item.e8_1);
    destination.c(tmp$ret$2);
  }
  // Inline function 'kotlin.collections.toTypedArray' call
  return copyToArray(destination);
}
function SearchFilterOption(id, label, params, requiresAuth) {
  this.id = id;
  this.label = label;
  this.params = params;
  this.requiresAuth = requiresAuth;
}
protoOf(SearchFilterOption).h8 = function () {
  return this.id;
};
protoOf(SearchFilterOption).i8 = function () {
  return this.label;
};
protoOf(SearchFilterOption).j8 = function () {
  return this.params;
};
protoOf(SearchFilterOption).k8 = function () {
  return this.requiresAuth;
};
function searchFilters() {
  // Inline function 'kotlin.collections.map' call
  var this_0 = get_entries();
  // Inline function 'kotlin.collections.mapTo' call
  var destination = ArrayList_init_$Create$(collectionSizeOrDefault(this_0, 10));
  var _iterator__ex2g4s = this_0.e();
  while (_iterator__ex2g4s.f()) {
    var item = _iterator__ex2g4s.g();
    var tmp$ret$2 = new SearchFilterOption(item.t_1, item.a8_1, item.b8_1, item.c8_1);
    destination.c(tmp$ret$2);
  }
  // Inline function 'kotlin.collections.toTypedArray' call
  return copyToArray(destination);
}
//region block: exports
export {
  artworkAt_0 as artworkAt,
  PlaylistMove_0 as PlaylistMove,
  playlistMoves_0 as playlistMoves,
  SearchFilterOption as SearchFilterOption,
  searchFilters as searchFilters,
};
//endregion


