import {
  EmptySerializersModule991ju6pz9b79 as EmptySerializersModule,
  Decoder23nde051s631g as Decoder,
  CompositeDecoder2tzm7wpwkr0og as CompositeDecoder,
  serializer1x79l67jvwntn as serializer,
  InlinePrimitiveDescriptor3i6ccn1a4fw94 as InlinePrimitiveDescriptor,
  MapSerializer11kmegt3g5c1g as MapSerializer,
  SerialDescriptor2pelqekb5ic3a as SerialDescriptor,
  KSerializerzf77vz1967fq as KSerializer,
  SEALED_getInstance2urch17m532zf as SEALED_getInstance,
  buildSerialDescriptor2873qmkp8r2ib as buildSerialDescriptor,
  STRING_getInstance3lkmlozu5h7cs as STRING_getInstance,
  ListSerializer1hxuk9dx5n9du as ListSerializer,
  ENUM_getInstance3ph4fa6o285vr as ENUM_getInstance,
  PrimitiveSerialDescriptor3egfp53lutxj2 as PrimitiveSerialDescriptor,
  get_isNullable36pbikm8xb7bz as get_isNullable,
  get_isInline5x26qrhi9qs6 as get_isInline,
  get_annotationshjxdbdcl8kmv as get_annotations,
  ElementMarker33ojvsajwmzts as ElementMarker,
  SerializationExceptioneqrdve3ts2n9 as SerializationException,
  SerializationException_init_$Init$16fnhi19wdhkq as SerializationException_init_$Init$,
  CLASS_getInstance25nhr3t715wdo as CLASS_getInstance,
  LIST_getInstance2leq5bbahk4rz as LIST_getInstance,
  AbstractDecoder35guh02ubh2hm as AbstractDecoder,
  AbstractPolymorphicSerializer1ccxwp48nfy58 as AbstractPolymorphicSerializer,
  DeserializationStrategy1z3z5pj9f7zc8 as DeserializationStrategy,
  findPolymorphicSerializer1nm87hvemahcj as findPolymorphicSerializer,
  MissingFieldException24tqif29emcmi as MissingFieldException,
  serializer3ikrxnm8b29d6 as serializer_0,
  serializer2lw83vwvpnyms as serializer_1,
  serializer36584sjyg5661 as serializer_2,
  serializer1q7c5q67ysppr as serializer_3,
  NamedValueDecoderzk26ztf92xbq as NamedValueDecoder,
  PolymorphicKindla9gurooefwb as PolymorphicKind,
  PrimitiveKindndgbuh6is7ze as PrimitiveKind,
  MAP_getInstance141vh6u7faopd as MAP_getInstance,
  jsonCachedSerialNameslxufy2gu43jt as jsonCachedSerialNames,
  getContextualDescriptor2n1gf3b895yb8 as getContextualDescriptor,
  CONTEXTUAL_getInstance2j6ylx8hc4yv2 as CONTEXTUAL_getInstance,
} from './kotlinx-serialization-kotlinx-serialization-core.mjs';
import {
  protoOf180f3jzyo7rfj as protoOf,
  initMetadataForObject1cxne3s9w65el as initMetadataForObject,
  VOID3gxj6tk5isa35 as VOID,
  initMetadataForClassbxx6q50dy2s7 as initMetadataForClass,
  toString30pk9tzaqopn as toString,
  Unit_instancev9v8hjid95df as Unit_instance,
  Enum3alwj03lh1n41 as Enum,
  initMetadataForInterface1egvbzx539z91 as initMetadataForInterface,
  initMetadataForCompanion1wyw17z38v6ac as initMetadataForCompanion,
  StringBuilder_init_$Create$3ds9zx2kx9u4d as StringBuilder_init_$Create$,
  _Char___init__impl__6a9atx2gndcvjvc5pke as _Char___init__impl__6a9atx,
  equals2au1ep9vhcato as equals,
  hashCodeq5arwsb9dgti as hashCode,
  joinToString1cxrrlmo0chqs as joinToString,
  THROW_CCE2g6jy02ryeudk as THROW_CCE,
  KtMap140uvy3s5zad8 as KtMap,
  KtList3hktaavzmj137 as KtList,
  toString1pkumu07cwy4m as toString_0,
  IllegalArgumentException_init_$Create$i4126l9nkbuo as IllegalArgumentException_init_$Create$,
  getKClassFromExpression348iqjl4fnx2f as getKClassFromExpression,
  getBooleanHashCode1bbj3u6b3v0a7 as getBooleanHashCode,
  getStringHashCode26igk1bx568vk as getStringHashCode,
  NumberFormatException_init_$Create$1frrsjd61hhze as NumberFormatException_init_$Create$,
  numberRangeToNumber25vse2rgp6rs8 as numberRangeToNumber,
  ClosedRangehokgr73im9z3 as ClosedRange,
  isInterface3d6p8outrmvmk as isInterface,
  contains2c50nlxg7en7o as contains,
  convertToIntofdoxh9bstof as convertToInt,
  toDouble1kn912gjoizjp as toDouble,
  StringCompanionObject_instance1v0ht40fz3tq0 as StringCompanionObject_instance,
  IllegalStateException_init_$Create$1h4bcfod21ri0 as IllegalStateException_init_$Create$,
  KProperty1ca4yb4wlo496 as KProperty1,
  getPropertyCallableRef3hckxc0xueiaj as getPropertyCallableRef,
  lazy2hsh8ze7j6ikd as lazy,
  captureStack1fzi4aczwc4hg as captureStack,
  charSequenceLength3278n89t01tmv as charSequenceLength,
  charSequenceSubSequence1iwpdba8s3jc7 as charSequenceSubSequence,
  coerceAtLeast2bkz8m9ik7hep as coerceAtLeast,
  coerceAtMost322komnqp70ag as coerceAtMost,
  LinkedHashMap_init_$Create$2tibire69n089 as LinkedHashMap_init_$Create$,
  ArrayList_init_$Create$37gc04va6yfo2 as ArrayList_init_$Create$,
  singleOrNullrknfaxokm1sl as singleOrNull,
  emptyMapr06gerzljqtm as emptyMap,
  getValue48kllevslyh6 as getValue,
  copyOf2ng0t8oizk6it as copyOf,
  copyOf3rutauicler23 as copyOf_0,
  DeepRecursiveFunction3r49v8igsve1g as DeepRecursiveFunction,
  invoke246lvi6tzooz1 as invoke,
  CoroutineImpl2sn3kjnwmfr10 as CoroutineImpl,
  DeepRecursiveScope1pqaydvh4vdcu as DeepRecursiveScope,
  Unitkvevlwgzwiuc as Unit,
  get_COROUTINE_SUSPENDED3ujt3p13qm4iy as get_COROUTINE_SUSPENDED,
  initMetadataForLambda3af3he42mmnh as initMetadataForLambda,
  initMetadataForCoroutine1i7lbatuf5bnt as initMetadataForCoroutine,
  getKClass3t8tygqu4lcxf as getKClass,
  ensureNotNull1e947j3ixpazm as ensureNotNull,
  substringBefore3n7kj60w69hju as substringBefore,
  removeSuffix3d61x5lsuvuho as removeSuffix,
  substringAfter1hku067gwr5ve as substringAfter,
  contains3ue2qo8xhmpf1 as contains_0,
  plus17rl43at52ays as plus,
  convertToByte1epqhkuyxuz5a as convertToByte,
  fromInt1lka3ktyu79a4 as fromInt,
  equalsLong28bsrfhwvd686 as equalsLong,
  convertToShortvtefcftm709c as convertToShort,
  IllegalArgumentException2asla15b5jaob as IllegalArgumentException,
  isFinite1tx0gn65nl9tj as isFinite,
  isFinite2t9l5a275mxm6 as isFinite_0,
  charCodeAt1yspne1d8erbm as charCodeAt,
  toUInt21lx0mz8wkp7c as toUInt,
  _UInt___get_data__impl__f0vqqw2j5mttix8w0l0 as _UInt___get_data__impl__f0vqqw,
  toULong266mnyksbttkw as toULong,
  _ULong___get_data__impl__fggpzb2bbucljk081qt as _ULong___get_data__impl__fggpzb,
  toUByteh6p4wmqswkrs as toUByte,
  _UByte___get_data__impl__jof9qr23gcv767stjfh as _UByte___get_data__impl__jof9qr,
  toUShort7yqspfnhrot4 as toUShort,
  _UShort___get_data__impl__g0245313795p1x5hw5 as _UShort___get_data__impl__g0245,
  Companion_getInstance1339zuieiizg3 as Companion_getInstance,
  Companion_getInstance2ln0tj1crqgxz as Companion_getInstance_0,
  Companion_getInstance2utgi65n0nnqj as Companion_getInstance_1,
  Companion_getInstance3rx8u2n07bmtm as Companion_getInstance_2,
  setOf45ia9pnfhe90 as setOf,
  Char__toInt_impl_vasixdnyockrdnmht5 as Char__toInt_impl_vasixd,
  numberToChar93r9buh19yek as numberToChar,
  equals2v6cggk171b6e as equals_0,
  toString22rbkbhdb5cl2 as toString_1,
  toByte4i43936u611k as toByte,
  startsWith26w8qjqapeeq6 as startsWith,
  toShort36kaw0zjdq3ex as toShort,
  single29ec4rh52687r as single,
  Char19o2r8palgjof as Char,
  emptySetcxexqki71qfa as emptySet,
  plus1ogy4liedzq5j as plus_0,
  toInt2q8uldh7sc951 as toInt,
  toList3jhuyej2anx2q as toList,
  charSequenceGet1vxk1y5n17t1z as charSequenceGet,
  toNumberlmbpvqo27r53 as toNumber,
  noWhenBranchMatchedException2a6r7ubxgky5j as noWhenBranchMatchedException,
  last1vo29oleiqj36 as last,
  removeLast3759euu1xvfa3 as removeLast,
  lastIndexOf2d52xhix5ymjr as lastIndexOf,
  Long2qws0ah9gnpki as Long,
  Char__minus_impl_a2frrhux3psqasp3d9 as Char__minus_impl_a2frrh,
  multiply18i3gv3wlmcjg as multiply,
  add85si75olwt6n as add,
  subtract16cg4lfi29fq9 as subtract,
  compare2uud5j30pw5xc as compare,
  numberToLong345n6tb1n1i71 as numberToLong,
  negate12tprdg5pyd5t as negate,
  charArray2ujmm1qusno00 as charArray,
  indexOfwa4w6635jewi as indexOf,
  indexOf1xbs558u7wr52 as indexOf_0,
  substringiqarkczpya5m as substring,
  HashMap_init_$Create$j8c89adh6ge5 as HashMap_init_$Create$,
} from './kotlin-kotlin-stdlib.mjs';
//region block: imports
var imul = Math.imul;
//endregion
//region block: pre-declaration
initMetadataForClass(Json, 'Json');
initMetadataForObject(Default, 'Default', VOID, Json);
initMetadataForClass(JsonClassDiscriminator, 'JsonClassDiscriminator');
initMetadataForClass(JsonNames, 'JsonNames');
initMetadataForClass(JsonConfiguration, 'JsonConfiguration');
initMetadataForClass(ClassDiscriminatorMode, 'ClassDiscriminatorMode', VOID, Enum);
initMetadataForInterface(JsonDecoder, 'JsonDecoder', VOID, VOID, [Decoder, CompositeDecoder]);
initMetadataForCompanion(Companion);
initMetadataForClass(JsonElement, 'JsonElement', VOID, VOID, VOID, VOID, VOID, {0: JsonElementSerializer_getInstance});
initMetadataForClass(JsonObject, 'JsonObject', VOID, JsonElement, [KtMap], VOID, VOID, {0: JsonObjectSerializer_getInstance});
initMetadataForCompanion(Companion_0);
initMetadataForCompanion(Companion_1);
initMetadataForClass(JsonPrimitive, 'JsonPrimitive', VOID, JsonElement, VOID, VOID, VOID, {0: JsonPrimitiveSerializer_getInstance});
initMetadataForCompanion(Companion_2);
initMetadataForClass(JsonArray, 'JsonArray', VOID, JsonElement, [KtList], VOID, VOID, {0: JsonArraySerializer_getInstance});
initMetadataForObject(JsonNull, 'JsonNull', VOID, JsonPrimitive, VOID, VOID, VOID, {0: JsonNullSerializer_getInstance});
initMetadataForClass(JsonLiteral, 'JsonLiteral', VOID, JsonPrimitive);
initMetadataForObject(JsonObjectDescriptor, 'JsonObjectDescriptor', VOID, VOID, [SerialDescriptor]);
initMetadataForObject(JsonObjectSerializer, 'JsonObjectSerializer', VOID, VOID, [KSerializer]);
initMetadataForObject(JsonElementSerializer, 'JsonElementSerializer', VOID, VOID, [KSerializer]);
initMetadataForObject(JsonPrimitiveSerializer, 'JsonPrimitiveSerializer', VOID, VOID, [KSerializer]);
initMetadataForObject(JsonArrayDescriptor, 'JsonArrayDescriptor', VOID, VOID, [SerialDescriptor]);
initMetadataForObject(JsonArraySerializer, 'JsonArraySerializer', VOID, VOID, [KSerializer]);
initMetadataForObject(JsonNullSerializer, 'JsonNullSerializer', VOID, VOID, [KSerializer]);
initMetadataForObject(JsonLiteralSerializer, 'JsonLiteralSerializer', VOID, VOID, [KSerializer]);
initMetadataForClass(defer$1, VOID, VOID, VOID, [SerialDescriptor]);
initMetadataForClass(JsonElementMarker, 'JsonElementMarker');
initMetadataForClass(JsonException, 'JsonException', VOID, SerializationException);
initMetadataForClass(JsonDecodingException, 'JsonDecodingException', VOID, JsonException);
initMetadataForClass(JsonEncodingException, 'JsonEncodingException', VOID, JsonException);
initMetadataForObject(Tombstone, 'Tombstone');
initMetadataForClass(JsonPath, 'JsonPath', JsonPath);
initMetadataForLambda(JsonTreeReader$readDeepRecursive$slambda, CoroutineImpl, VOID, [2]);
initMetadataForCoroutine($readObjectCOROUTINE$, CoroutineImpl);
initMetadataForClass(JsonTreeReader, 'JsonTreeReader', VOID, VOID, VOID, [1]);
initMetadataForClass(Key, 'Key', Key);
initMetadataForClass(DescriptorSchemaCache, 'DescriptorSchemaCache', DescriptorSchemaCache);
initMetadataForClass(DiscriminatorHolder, 'DiscriminatorHolder');
initMetadataForClass(StreamingJsonDecoder, 'StreamingJsonDecoder', VOID, AbstractDecoder, [JsonDecoder]);
initMetadataForClass(JsonDecoderForUnsignedTypes, 'JsonDecoderForUnsignedTypes', VOID, AbstractDecoder);
initMetadataForClass(AbstractJsonTreeDecoder, 'AbstractJsonTreeDecoder', VOID, NamedValueDecoder, [JsonDecoder]);
initMetadataForClass(JsonTreeDecoder, 'JsonTreeDecoder', VOID, AbstractJsonTreeDecoder);
initMetadataForClass(JsonTreeListDecoder, 'JsonTreeListDecoder', VOID, AbstractJsonTreeDecoder);
initMetadataForClass(JsonPrimitiveDecoder, 'JsonPrimitiveDecoder', VOID, AbstractJsonTreeDecoder);
initMetadataForClass(JsonTreeMapDecoder, 'JsonTreeMapDecoder', VOID, JsonTreeDecoder);
initMetadataForClass(WriteMode, 'WriteMode', VOID, Enum);
initMetadataForClass(AbstractJsonLexer, 'AbstractJsonLexer');
initMetadataForObject(CharMappings, 'CharMappings');
initMetadataForClass(StringJsonLexer, 'StringJsonLexer', VOID, AbstractJsonLexer);
initMetadataForClass(StringJsonLexerWithComments, 'StringJsonLexerWithComments', VOID, StringJsonLexer);
//endregion
function Default() {
  Default_instance = this;
  Json.call(this, new JsonConfiguration(), EmptySerializersModule());
}
var Default_instance;
function Default_getInstance() {
  if (Default_instance == null)
    new Default();
  return Default_instance;
}
function Json(configuration, serializersModule) {
  Default_getInstance();
  this.tu_1 = configuration;
  this.uu_1 = serializersModule;
  this.vu_1 = new DescriptorSchemaCache();
}
protoOf(Json).fl = function () {
  return this.uu_1;
};
protoOf(Json).wu = function (deserializer, string) {
  var lexer = StringJsonLexer_0(this, string);
  var input = new StreamingJsonDecoder(this, WriteMode_OBJ_getInstance(), lexer, deserializer.ki(), null);
  var result = input.rk(deserializer);
  lexer.kv();
  return result;
};
protoOf(Json).xu = function (string) {
  return this.wu(JsonElementSerializer_getInstance(), string);
};
function JsonClassDiscriminator() {
}
function JsonNames() {
}
function JsonConfiguration(encodeDefaults, ignoreUnknownKeys, isLenient, allowStructuredMapKeys, prettyPrint, explicitNulls, prettyPrintIndent, coerceInputValues, useArrayPolymorphism, classDiscriminator, allowSpecialFloatingPointValues, useAlternativeNames, namingStrategy, decodeEnumsCaseInsensitive, allowTrailingComma, allowComments, classDiscriminatorMode) {
  encodeDefaults = encodeDefaults === VOID ? false : encodeDefaults;
  ignoreUnknownKeys = ignoreUnknownKeys === VOID ? false : ignoreUnknownKeys;
  isLenient = isLenient === VOID ? false : isLenient;
  allowStructuredMapKeys = allowStructuredMapKeys === VOID ? false : allowStructuredMapKeys;
  prettyPrint = prettyPrint === VOID ? false : prettyPrint;
  explicitNulls = explicitNulls === VOID ? true : explicitNulls;
  prettyPrintIndent = prettyPrintIndent === VOID ? '    ' : prettyPrintIndent;
  coerceInputValues = coerceInputValues === VOID ? false : coerceInputValues;
  useArrayPolymorphism = useArrayPolymorphism === VOID ? false : useArrayPolymorphism;
  classDiscriminator = classDiscriminator === VOID ? 'type' : classDiscriminator;
  allowSpecialFloatingPointValues = allowSpecialFloatingPointValues === VOID ? false : allowSpecialFloatingPointValues;
  useAlternativeNames = useAlternativeNames === VOID ? true : useAlternativeNames;
  namingStrategy = namingStrategy === VOID ? null : namingStrategy;
  decodeEnumsCaseInsensitive = decodeEnumsCaseInsensitive === VOID ? false : decodeEnumsCaseInsensitive;
  allowTrailingComma = allowTrailingComma === VOID ? false : allowTrailingComma;
  allowComments = allowComments === VOID ? false : allowComments;
  classDiscriminatorMode = classDiscriminatorMode === VOID ? ClassDiscriminatorMode_POLYMORPHIC_getInstance() : classDiscriminatorMode;
  this.lv_1 = encodeDefaults;
  this.mv_1 = ignoreUnknownKeys;
  this.nv_1 = isLenient;
  this.ov_1 = allowStructuredMapKeys;
  this.pv_1 = prettyPrint;
  this.qv_1 = explicitNulls;
  this.rv_1 = prettyPrintIndent;
  this.sv_1 = coerceInputValues;
  this.tv_1 = useArrayPolymorphism;
  this.uv_1 = classDiscriminator;
  this.vv_1 = allowSpecialFloatingPointValues;
  this.wv_1 = useAlternativeNames;
  this.xv_1 = namingStrategy;
  this.yv_1 = decodeEnumsCaseInsensitive;
  this.zv_1 = allowTrailingComma;
  this.aw_1 = allowComments;
  this.bw_1 = classDiscriminatorMode;
}
protoOf(JsonConfiguration).toString = function () {
  return 'JsonConfiguration(encodeDefaults=' + this.lv_1 + ', ignoreUnknownKeys=' + this.mv_1 + ', isLenient=' + this.nv_1 + ', ' + ('allowStructuredMapKeys=' + this.ov_1 + ', prettyPrint=' + this.pv_1 + ', explicitNulls=' + this.qv_1 + ', ') + ("prettyPrintIndent='" + this.rv_1 + "', coerceInputValues=" + this.sv_1 + ', useArrayPolymorphism=' + this.tv_1 + ', ') + ("classDiscriminator='" + this.uv_1 + "', allowSpecialFloatingPointValues=" + this.vv_1 + ', ') + ('useAlternativeNames=' + this.wv_1 + ', namingStrategy=' + toString(this.xv_1) + ', decodeEnumsCaseInsensitive=' + this.yv_1 + ', ') + ('allowTrailingComma=' + this.zv_1 + ', allowComments=' + this.aw_1 + ', classDiscriminatorMode=' + this.bw_1.toString() + ')');
};
var ClassDiscriminatorMode_NONE_instance;
var ClassDiscriminatorMode_ALL_JSON_OBJECTS_instance;
var ClassDiscriminatorMode_POLYMORPHIC_instance;
var ClassDiscriminatorMode_entriesInitialized;
function ClassDiscriminatorMode_initEntries() {
  if (ClassDiscriminatorMode_entriesInitialized)
    return Unit_instance;
  ClassDiscriminatorMode_entriesInitialized = true;
  ClassDiscriminatorMode_NONE_instance = new ClassDiscriminatorMode('NONE', 0);
  ClassDiscriminatorMode_ALL_JSON_OBJECTS_instance = new ClassDiscriminatorMode('ALL_JSON_OBJECTS', 1);
  ClassDiscriminatorMode_POLYMORPHIC_instance = new ClassDiscriminatorMode('POLYMORPHIC', 2);
}
function ClassDiscriminatorMode(name, ordinal) {
  Enum.call(this, name, ordinal);
}
function ClassDiscriminatorMode_POLYMORPHIC_getInstance() {
  ClassDiscriminatorMode_initEntries();
  return ClassDiscriminatorMode_POLYMORPHIC_instance;
}
function JsonDecoder() {
}
var jsonUnquotedLiteralDescriptor;
function Companion() {
}
var Companion_instance;
function Companion_getInstance_3() {
  return Companion_instance;
}
function JsonObject$toString$lambda(_destruct__k2r9zo) {
  // Inline function 'kotlin.collections.component1' call
  var k = _destruct__k2r9zo.p1();
  // Inline function 'kotlin.collections.component2' call
  var v = _destruct__k2r9zo.q1();
  // Inline function 'kotlin.text.buildString' call
  // Inline function 'kotlin.apply' call
  var this_0 = StringBuilder_init_$Create$();
  printQuoted(this_0, k);
  this_0.z6(_Char___init__impl__6a9atx(58));
  this_0.x6(v);
  return this_0.toString();
}
function JsonObject(content) {
  JsonElement.call(this);
  this.ew_1 = content;
}
protoOf(JsonObject).equals = function (other) {
  return equals(this.ew_1, other);
};
protoOf(JsonObject).hashCode = function () {
  return hashCode(this.ew_1);
};
protoOf(JsonObject).toString = function () {
  var tmp = this.ew_1.w1();
  return joinToString(tmp, ',', '{', '}', VOID, VOID, JsonObject$toString$lambda);
};
protoOf(JsonObject).fw = function (key) {
  return this.ew_1.r1(key);
};
protoOf(JsonObject).r1 = function (key) {
  if (!(!(key == null) ? typeof key === 'string' : false))
    return false;
  return this.fw((!(key == null) ? typeof key === 'string' : false) ? key : THROW_CCE());
};
protoOf(JsonObject).fb = function (key) {
  return this.ew_1.t1(key);
};
protoOf(JsonObject).t1 = function (key) {
  if (!(!(key == null) ? typeof key === 'string' : false))
    return null;
  return this.fb((!(key == null) ? typeof key === 'string' : false) ? key : THROW_CCE());
};
protoOf(JsonObject).r = function () {
  return this.ew_1.r();
};
protoOf(JsonObject).w1 = function () {
  return this.ew_1.w1();
};
protoOf(JsonObject).u1 = function () {
  return this.ew_1.u1();
};
protoOf(JsonObject).k = function () {
  return this.ew_1.k();
};
protoOf(JsonObject).v1 = function () {
  return this.ew_1.v1();
};
function Companion_0() {
}
var Companion_instance_0;
function Companion_getInstance_4() {
  return Companion_instance_0;
}
function JsonElement() {
}
function Companion_1() {
}
var Companion_instance_1;
function Companion_getInstance_5() {
  return Companion_instance_1;
}
function JsonPrimitive() {
  JsonElement.call(this);
}
protoOf(JsonPrimitive).toString = function () {
  return this.gw();
};
function get_contentOrNull(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  var tmp;
  if (_this__u8e3s4 instanceof JsonNull) {
    tmp = null;
  } else {
    tmp = _this__u8e3s4.gw();
  }
  return tmp;
}
function Companion_2() {
}
var Companion_instance_2;
function Companion_getInstance_6() {
  return Companion_instance_2;
}
function JsonArray(content) {
  JsonElement.call(this);
  this.hw_1 = content;
}
protoOf(JsonArray).equals = function (other) {
  return equals(this.hw_1, other);
};
protoOf(JsonArray).hashCode = function () {
  return hashCode(this.hw_1);
};
protoOf(JsonArray).toString = function () {
  return joinToString(this.hw_1, ',', '[', ']');
};
protoOf(JsonArray).iw = function (element) {
  return this.hw_1.m1(element);
};
protoOf(JsonArray).m1 = function (element) {
  if (!(element instanceof JsonElement))
    return false;
  return this.iw(element instanceof JsonElement ? element : THROW_CCE());
};
protoOf(JsonArray).p = function (index) {
  return this.hw_1.p(index);
};
protoOf(JsonArray).jw = function (element) {
  return this.hw_1.n1(element);
};
protoOf(JsonArray).n1 = function (element) {
  if (!(element instanceof JsonElement))
    return -1;
  return this.jw(element instanceof JsonElement ? element : THROW_CCE());
};
protoOf(JsonArray).r = function () {
  return this.hw_1.r();
};
protoOf(JsonArray).h = function () {
  return this.hw_1.h();
};
protoOf(JsonArray).q = function (index) {
  return this.hw_1.q(index);
};
protoOf(JsonArray).k = function () {
  return this.hw_1.k();
};
function JsonNull() {
  JsonNull_instance = this;
  JsonPrimitive.call(this);
  this.kw_1 = 'null';
}
protoOf(JsonNull).gw = function () {
  return this.kw_1;
};
var JsonNull_instance;
function JsonNull_getInstance() {
  if (JsonNull_instance == null)
    new JsonNull();
  return JsonNull_instance;
}
function JsonLiteral(body, isString, coerceToInlineType) {
  coerceToInlineType = coerceToInlineType === VOID ? null : coerceToInlineType;
  JsonPrimitive.call(this);
  this.lw_1 = isString;
  this.mw_1 = coerceToInlineType;
  this.nw_1 = toString_0(body);
  if (!(this.mw_1 == null)) {
    // Inline function 'kotlin.require' call
    // Inline function 'kotlin.require' call
    if (!this.mw_1.vi()) {
      var message = 'Failed requirement.';
      throw IllegalArgumentException_init_$Create$(toString_0(message));
    }
  }
}
protoOf(JsonLiteral).gw = function () {
  return this.nw_1;
};
protoOf(JsonLiteral).toString = function () {
  var tmp;
  if (this.lw_1) {
    // Inline function 'kotlin.text.buildString' call
    // Inline function 'kotlin.apply' call
    var this_0 = StringBuilder_init_$Create$();
    printQuoted(this_0, this.nw_1);
    tmp = this_0.toString();
  } else {
    tmp = this.nw_1;
  }
  return tmp;
};
protoOf(JsonLiteral).equals = function (other) {
  if (this === other)
    return true;
  if (other == null || !getKClassFromExpression(this).equals(getKClassFromExpression(other)))
    return false;
  if (!(other instanceof JsonLiteral))
    THROW_CCE();
  if (!(this.lw_1 === other.lw_1))
    return false;
  if (!(this.nw_1 === other.nw_1))
    return false;
  return true;
};
protoOf(JsonLiteral).hashCode = function () {
  var result = getBooleanHashCode(this.lw_1);
  result = imul(31, result) + getStringHashCode(this.nw_1) | 0;
  return result;
};
function JsonPrimitive_0(value) {
  _init_properties_JsonElement_kt__7cbdc2();
  if (value == null)
    return JsonNull_getInstance();
  return new JsonLiteral(value, true);
}
function get_booleanOrNull(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  return toBooleanStrictOrNull(_this__u8e3s4.gw());
}
function get_int(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  // Inline function 'kotlinx.serialization.json.mapExceptions' call
  var tmp;
  try {
    tmp = (new StringJsonLexer(_this__u8e3s4.gw())).ow();
  } catch ($p) {
    var tmp_0;
    if ($p instanceof JsonDecodingException) {
      var e = $p;
      throw NumberFormatException_init_$Create$(e.message);
    } else {
      throw $p;
    }
  }
  var result = tmp;
  // Inline function 'kotlin.ranges.contains' call
  var this_0 = numberRangeToNumber(-2147483648, 2147483647);
  if (!contains(isInterface(this_0, ClosedRange) ? this_0 : THROW_CCE(), result))
    throw NumberFormatException_init_$Create$(_this__u8e3s4.gw() + ' is not an Int');
  return convertToInt(result);
}
function get_long(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  // Inline function 'kotlinx.serialization.json.mapExceptions' call
  var tmp;
  try {
    tmp = (new StringJsonLexer(_this__u8e3s4.gw())).ow();
  } catch ($p) {
    var tmp_0;
    if ($p instanceof JsonDecodingException) {
      var e = $p;
      throw NumberFormatException_init_$Create$(e.message);
    } else {
      throw $p;
    }
  }
  return tmp;
}
function get_float(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  // Inline function 'kotlin.text.toFloat' call
  var this_0 = _this__u8e3s4.gw();
  // Inline function 'kotlin.js.unsafeCast' call
  // Inline function 'kotlin.js.asDynamic' call
  return toDouble(this_0);
}
function get_double(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  return toDouble(_this__u8e3s4.gw());
}
function get_jsonPrimitive(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  var tmp0_elvis_lhs = _this__u8e3s4 instanceof JsonPrimitive ? _this__u8e3s4 : null;
  var tmp;
  if (tmp0_elvis_lhs == null) {
    error(_this__u8e3s4, 'JsonPrimitive');
  } else {
    tmp = tmp0_elvis_lhs;
  }
  return tmp;
}
function error(_this__u8e3s4, element) {
  _init_properties_JsonElement_kt__7cbdc2();
  throw IllegalArgumentException_init_$Create$('Element ' + toString_0(getKClassFromExpression(_this__u8e3s4)) + ' is not a ' + element);
}
var properties_initialized_JsonElement_kt_abxy8s;
function _init_properties_JsonElement_kt__7cbdc2() {
  if (!properties_initialized_JsonElement_kt_abxy8s) {
    properties_initialized_JsonElement_kt_abxy8s = true;
    jsonUnquotedLiteralDescriptor = InlinePrimitiveDescriptor('kotlinx.serialization.json.JsonUnquotedLiteral', serializer(StringCompanionObject_instance));
  }
}
function JsonObjectDescriptor() {
  JsonObjectDescriptor_instance = this;
  this.pw_1 = MapSerializer(serializer(StringCompanionObject_instance), JsonElementSerializer_getInstance()).ki();
  this.qw_1 = 'kotlinx.serialization.json.JsonObject';
}
protoOf(JsonObjectDescriptor).si = function () {
  return this.qw_1;
};
protoOf(JsonObjectDescriptor).yi = function (index) {
  return this.pw_1.yi(index);
};
protoOf(JsonObjectDescriptor).zi = function (name) {
  return this.pw_1.zi(name);
};
protoOf(JsonObjectDescriptor).aj = function (index) {
  return this.pw_1.aj(index);
};
protoOf(JsonObjectDescriptor).bj = function (index) {
  return this.pw_1.bj(index);
};
protoOf(JsonObjectDescriptor).cj = function (index) {
  return this.pw_1.cj(index);
};
protoOf(JsonObjectDescriptor).ti = function () {
  return this.pw_1.ti();
};
protoOf(JsonObjectDescriptor).ui = function () {
  return this.pw_1.ui();
};
protoOf(JsonObjectDescriptor).vi = function () {
  return this.pw_1.vi();
};
protoOf(JsonObjectDescriptor).wi = function () {
  return this.pw_1.wi();
};
protoOf(JsonObjectDescriptor).xi = function () {
  return this.pw_1.xi();
};
var JsonObjectDescriptor_instance;
function JsonObjectDescriptor_getInstance() {
  if (JsonObjectDescriptor_instance == null)
    new JsonObjectDescriptor();
  return JsonObjectDescriptor_instance;
}
function JsonObjectSerializer() {
  JsonObjectSerializer_instance = this;
  this.rw_1 = JsonObjectDescriptor_getInstance();
}
protoOf(JsonObjectSerializer).ki = function () {
  return this.rw_1;
};
protoOf(JsonObjectSerializer).li = function (decoder) {
  verify(decoder);
  return new JsonObject(MapSerializer(serializer(StringCompanionObject_instance), JsonElementSerializer_getInstance()).li(decoder));
};
var JsonObjectSerializer_instance;
function JsonObjectSerializer_getInstance() {
  if (JsonObjectSerializer_instance == null)
    new JsonObjectSerializer();
  return JsonObjectSerializer_instance;
}
function JsonElementSerializer$descriptor$lambda($this$buildSerialDescriptor) {
  $this$buildSerialDescriptor.pj('JsonPrimitive', defer(JsonElementSerializer$descriptor$lambda$lambda));
  $this$buildSerialDescriptor.pj('JsonNull', defer(JsonElementSerializer$descriptor$lambda$lambda_0));
  $this$buildSerialDescriptor.pj('JsonLiteral', defer(JsonElementSerializer$descriptor$lambda$lambda_1));
  $this$buildSerialDescriptor.pj('JsonObject', defer(JsonElementSerializer$descriptor$lambda$lambda_2));
  $this$buildSerialDescriptor.pj('JsonArray', defer(JsonElementSerializer$descriptor$lambda$lambda_3));
  return Unit_instance;
}
function JsonElementSerializer$descriptor$lambda$lambda() {
  return JsonPrimitiveSerializer_getInstance().sw_1;
}
function JsonElementSerializer$descriptor$lambda$lambda_0() {
  return JsonNullSerializer_getInstance().tw_1;
}
function JsonElementSerializer$descriptor$lambda$lambda_1() {
  return JsonLiteralSerializer_getInstance().uw_1;
}
function JsonElementSerializer$descriptor$lambda$lambda_2() {
  return JsonObjectSerializer_getInstance().rw_1;
}
function JsonElementSerializer$descriptor$lambda$lambda_3() {
  return JsonArraySerializer_getInstance().vw_1;
}
function JsonElementSerializer() {
  JsonElementSerializer_instance = this;
  var tmp = this;
  var tmp_0 = SEALED_getInstance();
  tmp.ww_1 = buildSerialDescriptor('kotlinx.serialization.json.JsonElement', tmp_0, [], JsonElementSerializer$descriptor$lambda);
}
protoOf(JsonElementSerializer).ki = function () {
  return this.ww_1;
};
protoOf(JsonElementSerializer).li = function (decoder) {
  var input = asJsonDecoder(decoder);
  return input.dw();
};
var JsonElementSerializer_instance;
function JsonElementSerializer_getInstance() {
  if (JsonElementSerializer_instance == null)
    new JsonElementSerializer();
  return JsonElementSerializer_instance;
}
function JsonPrimitiveSerializer() {
  JsonPrimitiveSerializer_instance = this;
  this.sw_1 = buildSerialDescriptor('kotlinx.serialization.json.JsonPrimitive', STRING_getInstance(), []);
}
protoOf(JsonPrimitiveSerializer).ki = function () {
  return this.sw_1;
};
protoOf(JsonPrimitiveSerializer).li = function (decoder) {
  var result = asJsonDecoder(decoder).dw();
  if (!(result instanceof JsonPrimitive))
    throw JsonDecodingException_0(-1, 'Unexpected JSON element, expected JsonPrimitive, had ' + toString_0(getKClassFromExpression(result)), toString_0(result));
  return result;
};
var JsonPrimitiveSerializer_instance;
function JsonPrimitiveSerializer_getInstance() {
  if (JsonPrimitiveSerializer_instance == null)
    new JsonPrimitiveSerializer();
  return JsonPrimitiveSerializer_instance;
}
function JsonArrayDescriptor() {
  JsonArrayDescriptor_instance = this;
  this.xw_1 = ListSerializer(JsonElementSerializer_getInstance()).ki();
  this.yw_1 = 'kotlinx.serialization.json.JsonArray';
}
protoOf(JsonArrayDescriptor).si = function () {
  return this.yw_1;
};
protoOf(JsonArrayDescriptor).yi = function (index) {
  return this.xw_1.yi(index);
};
protoOf(JsonArrayDescriptor).zi = function (name) {
  return this.xw_1.zi(name);
};
protoOf(JsonArrayDescriptor).aj = function (index) {
  return this.xw_1.aj(index);
};
protoOf(JsonArrayDescriptor).bj = function (index) {
  return this.xw_1.bj(index);
};
protoOf(JsonArrayDescriptor).cj = function (index) {
  return this.xw_1.cj(index);
};
protoOf(JsonArrayDescriptor).ti = function () {
  return this.xw_1.ti();
};
protoOf(JsonArrayDescriptor).ui = function () {
  return this.xw_1.ui();
};
protoOf(JsonArrayDescriptor).vi = function () {
  return this.xw_1.vi();
};
protoOf(JsonArrayDescriptor).wi = function () {
  return this.xw_1.wi();
};
protoOf(JsonArrayDescriptor).xi = function () {
  return this.xw_1.xi();
};
var JsonArrayDescriptor_instance;
function JsonArrayDescriptor_getInstance() {
  if (JsonArrayDescriptor_instance == null)
    new JsonArrayDescriptor();
  return JsonArrayDescriptor_instance;
}
function JsonArraySerializer() {
  JsonArraySerializer_instance = this;
  this.vw_1 = JsonArrayDescriptor_getInstance();
}
protoOf(JsonArraySerializer).ki = function () {
  return this.vw_1;
};
protoOf(JsonArraySerializer).li = function (decoder) {
  verify(decoder);
  return new JsonArray(ListSerializer(JsonElementSerializer_getInstance()).li(decoder));
};
var JsonArraySerializer_instance;
function JsonArraySerializer_getInstance() {
  if (JsonArraySerializer_instance == null)
    new JsonArraySerializer();
  return JsonArraySerializer_instance;
}
function JsonNullSerializer() {
  JsonNullSerializer_instance = this;
  this.tw_1 = buildSerialDescriptor('kotlinx.serialization.json.JsonNull', ENUM_getInstance(), []);
}
protoOf(JsonNullSerializer).ki = function () {
  return this.tw_1;
};
protoOf(JsonNullSerializer).li = function (decoder) {
  verify(decoder);
  if (decoder.ek()) {
    throw new JsonDecodingException("Expected 'null' literal");
  }
  decoder.fk();
  return JsonNull_getInstance();
};
var JsonNullSerializer_instance;
function JsonNullSerializer_getInstance() {
  if (JsonNullSerializer_instance == null)
    new JsonNullSerializer();
  return JsonNullSerializer_instance;
}
function verify(decoder) {
  asJsonDecoder(decoder);
}
function defer(deferred) {
  return new defer$1(deferred);
}
function JsonLiteralSerializer() {
  JsonLiteralSerializer_instance = this;
  this.uw_1 = PrimitiveSerialDescriptor('kotlinx.serialization.json.JsonLiteral', STRING_getInstance());
}
protoOf(JsonLiteralSerializer).ki = function () {
  return this.uw_1;
};
protoOf(JsonLiteralSerializer).li = function (decoder) {
  var result = asJsonDecoder(decoder).dw();
  if (!(result instanceof JsonLiteral))
    throw JsonDecodingException_0(-1, 'Unexpected JSON element, expected JsonLiteral, had ' + toString_0(getKClassFromExpression(result)), toString_0(result));
  return result;
};
var JsonLiteralSerializer_instance;
function JsonLiteralSerializer_getInstance() {
  if (JsonLiteralSerializer_instance == null)
    new JsonLiteralSerializer();
  return JsonLiteralSerializer_instance;
}
function asJsonDecoder(_this__u8e3s4) {
  var tmp0_elvis_lhs = isInterface(_this__u8e3s4, JsonDecoder) ? _this__u8e3s4 : null;
  var tmp;
  if (tmp0_elvis_lhs == null) {
    throw IllegalStateException_init_$Create$('This serializer can be used only with Json format.' + ('Expected Decoder to be JsonDecoder, got ' + toString_0(getKClassFromExpression(_this__u8e3s4))));
  } else {
    tmp = tmp0_elvis_lhs;
  }
  return tmp;
}
function _get_original__l7ku1m($this) {
  var tmp0 = $this.zw_1;
  var tmp = KProperty1;
  // Inline function 'kotlin.getValue' call
  getPropertyCallableRef('original', 1, tmp, defer$o$_get_original_$ref_3cje7k(), null);
  return tmp0.q1();
}
function defer$o$_get_original_$ref_3cje7k() {
  return function (p0) {
    return _get_original__l7ku1m(p0);
  };
}
function defer$1($deferred) {
  this.zw_1 = lazy($deferred);
}
protoOf(defer$1).si = function () {
  return _get_original__l7ku1m(this).si();
};
protoOf(defer$1).ti = function () {
  return _get_original__l7ku1m(this).ti();
};
protoOf(defer$1).wi = function () {
  return _get_original__l7ku1m(this).wi();
};
protoOf(defer$1).yi = function (index) {
  return _get_original__l7ku1m(this).yi(index);
};
protoOf(defer$1).zi = function (name) {
  return _get_original__l7ku1m(this).zi(name);
};
protoOf(defer$1).aj = function (index) {
  return _get_original__l7ku1m(this).aj(index);
};
protoOf(defer$1).bj = function (index) {
  return _get_original__l7ku1m(this).bj(index);
};
protoOf(defer$1).cj = function (index) {
  return _get_original__l7ku1m(this).cj(index);
};
function readIfAbsent($this, descriptor, index) {
  $this.bx_1 = (!descriptor.cj(index) && descriptor.bj(index).ui());
  return $this.bx_1;
}
function JsonElementMarker$readIfAbsent$ref(p0) {
  var l = function (_this__u8e3s4, p0_0) {
    var tmp0 = p0;
    return readIfAbsent(tmp0, _this__u8e3s4, p0_0);
  };
  l.callableName = 'readIfAbsent';
  return l;
}
function JsonElementMarker(descriptor) {
  var tmp = this;
  tmp.ax_1 = new ElementMarker(descriptor, JsonElementMarker$readIfAbsent$ref(this));
  this.bx_1 = false;
}
protoOf(JsonElementMarker).cx = function (index) {
  this.ax_1.tn(index);
};
protoOf(JsonElementMarker).dx = function () {
  return this.ax_1.un();
};
function JsonDecodingException(message) {
  JsonException.call(this, message);
  captureStack(this, JsonDecodingException);
}
function JsonDecodingException_0(offset, message, input) {
  return JsonDecodingException_1(offset, message + '\nJSON input: ' + toString_0(minify(input, offset)));
}
function invalidTrailingComma(_this__u8e3s4, entity) {
  entity = entity === VOID ? 'object' : entity;
  _this__u8e3s4.ex('Trailing comma before the end of JSON ' + entity, _this__u8e3s4.gv_1 - 1 | 0, "Trailing commas are non-complaint JSON and not allowed by default. Use 'allowTrailingCommas = true' in 'Json {}' builder to support them.");
}
function throwInvalidFloatingPointDecoded(_this__u8e3s4, result) {
  _this__u8e3s4.fx('Unexpected special floating-point value ' + toString_0(result) + '. By default, ' + 'non-finite floating point values are prohibited because they do not conform JSON specification', VOID, "It is possible to deserialize them using 'JsonBuilder.allowSpecialFloatingPointValues = true'");
}
function JsonEncodingException(message) {
  JsonException.call(this, message);
  captureStack(this, JsonEncodingException);
}
function InvalidKeyKindException(keyDescriptor) {
  return new JsonEncodingException("Value of type '" + keyDescriptor.si() + "' can't be used in JSON as a key in the map. " + ("It should have either primitive or enum kind, but its kind is '" + keyDescriptor.ti().toString() + "'.\n") + "Use 'allowStructuredMapKeys = true' in 'Json {}' builder to convert such maps to [key1, value1, key2, value2,...] arrays.");
}
function InvalidFloatingPointDecoded(value, key, output) {
  return JsonDecodingException_1(-1, unexpectedFpErrorMessage(value, key, output));
}
function UnknownKeyException(key, input) {
  return JsonDecodingException_1(-1, "Encountered an unknown key '" + key + "'.\n" + "Use 'ignoreUnknownKeys = true' in 'Json {}' builder to ignore unknown keys.\n" + ('Current input: ' + toString_0(minify(input))));
}
function JsonException(message) {
  SerializationException_init_$Init$(message, this);
  captureStack(this, JsonException);
}
function JsonDecodingException_1(offset, message) {
  return new JsonDecodingException(offset >= 0 ? 'Unexpected JSON token at offset ' + offset + ': ' + message : message);
}
function minify(_this__u8e3s4, offset) {
  offset = offset === VOID ? -1 : offset;
  if (charSequenceLength(_this__u8e3s4) < 200)
    return _this__u8e3s4;
  if (offset === -1) {
    var start = charSequenceLength(_this__u8e3s4) - 60 | 0;
    if (start <= 0)
      return _this__u8e3s4;
    // Inline function 'kotlin.text.substring' call
    var endIndex = charSequenceLength(_this__u8e3s4);
    return '.....' + toString_0(charSequenceSubSequence(_this__u8e3s4, start, endIndex));
  }
  var start_0 = offset - 30 | 0;
  var end = offset + 30 | 0;
  var prefix = start_0 <= 0 ? '' : '.....';
  var suffix = end >= charSequenceLength(_this__u8e3s4) ? '' : '.....';
  var tmp2 = coerceAtLeast(start_0, 0);
  // Inline function 'kotlin.text.substring' call
  var endIndex_0 = coerceAtMost(end, charSequenceLength(_this__u8e3s4));
  return prefix + toString_0(charSequenceSubSequence(_this__u8e3s4, tmp2, endIndex_0)) + suffix;
}
function unexpectedFpErrorMessage(value, key, output) {
  return 'Unexpected special floating-point value ' + toString_0(value) + ' with key ' + key + '. By default, ' + 'non-finite floating point values are prohibited because they do not conform JSON specification. ' + "It is possible to deserialize them using 'JsonBuilder.allowSpecialFloatingPointValues = true'\n" + ('Current output: ' + toString_0(minify(output)));
}
function get_JsonDeserializationNamesKey() {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  return JsonDeserializationNamesKey;
}
var JsonDeserializationNamesKey;
var JsonSerializationNamesKey;
function getJsonNameIndex(_this__u8e3s4, json, name) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  if (decodeCaseInsensitive(json, _this__u8e3s4)) {
    // Inline function 'kotlin.text.lowercase' call
    // Inline function 'kotlin.js.asDynamic' call
    var tmp$ret$0 = name.toLowerCase();
    return getJsonNameIndexSlowPath(_this__u8e3s4, json, tmp$ret$0);
  }
  var strategy = namingStrategy(_this__u8e3s4, json);
  if (!(strategy == null))
    return getJsonNameIndexSlowPath(_this__u8e3s4, json, name);
  var index = _this__u8e3s4.zi(name);
  if (!(index === -3))
    return index;
  if (!json.tu_1.wv_1)
    return index;
  return getJsonNameIndexSlowPath(_this__u8e3s4, json, name);
}
function namingStrategy(_this__u8e3s4, json) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  return equals(_this__u8e3s4.ti(), CLASS_getInstance()) ? json.tu_1.xv_1 : null;
}
function deserializationNamesMap(_this__u8e3s4, descriptor) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  var tmp = get_schemaCache(_this__u8e3s4);
  var tmp_0 = get_JsonDeserializationNamesKey();
  return tmp.hx(descriptor, tmp_0, deserializationNamesMap$lambda(descriptor, _this__u8e3s4));
}
function decodeCaseInsensitive(_this__u8e3s4, descriptor) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  return _this__u8e3s4.tu_1.yv_1 && equals(descriptor.ti(), ENUM_getInstance());
}
function getJsonNameIndexSlowPath(_this__u8e3s4, json, name) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  var tmp0_elvis_lhs = deserializationNamesMap(json, _this__u8e3s4).t1(name);
  return tmp0_elvis_lhs == null ? -3 : tmp0_elvis_lhs;
}
function buildDeserializationNamesMap(_this__u8e3s4, json) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  // Inline function 'kotlin.collections.mutableMapOf' call
  var builder = LinkedHashMap_init_$Create$();
  var useLowercaseEnums = decodeCaseInsensitive(json, _this__u8e3s4);
  var strategyForClasses = namingStrategy(_this__u8e3s4, json);
  var inductionVariable = 0;
  var last = _this__u8e3s4.wi();
  if (inductionVariable < last)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      // Inline function 'kotlin.collections.filterIsInstance' call
      var tmp0 = _this__u8e3s4.aj(i);
      // Inline function 'kotlin.collections.filterIsInstanceTo' call
      var destination = ArrayList_init_$Create$();
      var _iterator__ex2g4s = tmp0.h();
      while (_iterator__ex2g4s.i()) {
        var element = _iterator__ex2g4s.j();
        if (element instanceof JsonNames) {
          destination.e(element);
        }
      }
      var tmp0_safe_receiver = singleOrNull(destination);
      var tmp1_safe_receiver = tmp0_safe_receiver == null ? null : tmp0_safe_receiver.ix_1;
      if (tmp1_safe_receiver == null)
        null;
      else {
        // Inline function 'kotlin.collections.forEach' call
        var inductionVariable_0 = 0;
        var last_0 = tmp1_safe_receiver.length;
        while (inductionVariable_0 < last_0) {
          var element_0 = tmp1_safe_receiver[inductionVariable_0];
          inductionVariable_0 = inductionVariable_0 + 1 | 0;
          var tmp;
          if (useLowercaseEnums) {
            // Inline function 'kotlin.text.lowercase' call
            // Inline function 'kotlin.js.asDynamic' call
            tmp = element_0.toLowerCase();
          } else {
            tmp = element_0;
          }
          buildDeserializationNamesMap$putOrThrow(builder, _this__u8e3s4, tmp, i);
        }
      }
      var tmp_0;
      if (useLowercaseEnums) {
        // Inline function 'kotlin.text.lowercase' call
        // Inline function 'kotlin.js.asDynamic' call
        tmp_0 = _this__u8e3s4.yi(i).toLowerCase();
      } else if (!(strategyForClasses == null)) {
        tmp_0 = strategyForClasses.jx(_this__u8e3s4, i, _this__u8e3s4.yi(i));
      } else {
        tmp_0 = null;
      }
      var nameToPut = tmp_0;
      if (nameToPut == null)
        null;
      else {
        // Inline function 'kotlin.let' call
        buildDeserializationNamesMap$putOrThrow(builder, _this__u8e3s4, nameToPut, i);
      }
    }
     while (inductionVariable < last);
  // Inline function 'kotlin.collections.ifEmpty' call
  var tmp_1;
  if (builder.r()) {
    tmp_1 = emptyMap();
  } else {
    tmp_1 = builder;
  }
  return tmp_1;
}
function deserializationNamesMap$lambda($descriptor, $this_deserializationNamesMap) {
  return function () {
    return buildDeserializationNamesMap($descriptor, $this_deserializationNamesMap);
  };
}
function buildDeserializationNamesMap$putOrThrow(_this__u8e3s4, $this_buildDeserializationNamesMap, name, index) {
  var entity = equals($this_buildDeserializationNamesMap.ti(), ENUM_getInstance()) ? 'enum value' : 'property';
  // Inline function 'kotlin.collections.contains' call
  // Inline function 'kotlin.collections.containsKey' call
  if ((isInterface(_this__u8e3s4, KtMap) ? _this__u8e3s4 : THROW_CCE()).r1(name)) {
    throw new JsonException("The suggested name '" + name + "' for " + entity + ' ' + $this_buildDeserializationNamesMap.yi(index) + ' is already one of the names for ' + entity + ' ' + ($this_buildDeserializationNamesMap.yi(getValue(_this__u8e3s4, name)) + ' in ' + toString_0($this_buildDeserializationNamesMap)));
  }
  // Inline function 'kotlin.collections.set' call
  _this__u8e3s4.x1(name, index);
}
var properties_initialized_JsonNamesMap_kt_ljpf42;
function _init_properties_JsonNamesMap_kt__cbbp0k() {
  if (!properties_initialized_JsonNamesMap_kt_ljpf42) {
    properties_initialized_JsonNamesMap_kt_ljpf42 = true;
    JsonDeserializationNamesKey = new Key();
    JsonSerializationNamesKey = new Key();
  }
}
function Tombstone() {
}
var Tombstone_instance;
function Tombstone_getInstance() {
  return Tombstone_instance;
}
function resize($this) {
  var newSize = imul($this.mx_1, 2);
  $this.kx_1 = copyOf($this.kx_1, newSize);
  $this.lx_1 = copyOf_0($this.lx_1, newSize);
}
function JsonPath() {
  var tmp = this;
  // Inline function 'kotlin.arrayOfNulls' call
  tmp.kx_1 = Array(8);
  var tmp_0 = this;
  var tmp_1 = 0;
  var tmp_2 = new Int32Array(8);
  while (tmp_1 < 8) {
    tmp_2[tmp_1] = -1;
    tmp_1 = tmp_1 + 1 | 0;
  }
  tmp_0.lx_1 = tmp_2;
  this.mx_1 = -1;
}
protoOf(JsonPath).nx = function (sd) {
  this.mx_1 = this.mx_1 + 1 | 0;
  var depth = this.mx_1;
  if (depth === this.kx_1.length) {
    resize(this);
  }
  this.kx_1[depth] = sd;
};
protoOf(JsonPath).ox = function (index) {
  this.lx_1[this.mx_1] = index;
};
protoOf(JsonPath).px = function (key) {
  var tmp;
  if (!(this.lx_1[this.mx_1] === -2)) {
    this.mx_1 = this.mx_1 + 1 | 0;
    tmp = this.mx_1 === this.kx_1.length;
  } else {
    tmp = false;
  }
  if (tmp) {
    resize(this);
  }
  this.kx_1[this.mx_1] = key;
  this.lx_1[this.mx_1] = -2;
};
protoOf(JsonPath).qx = function () {
  if (this.lx_1[this.mx_1] === -2) {
    this.kx_1[this.mx_1] = Tombstone_instance;
  }
};
protoOf(JsonPath).rx = function () {
  var depth = this.mx_1;
  if (this.lx_1[depth] === -2) {
    this.lx_1[depth] = -1;
    this.mx_1 = this.mx_1 - 1 | 0;
  }
  if (!(this.mx_1 === -1)) {
    this.mx_1 = this.mx_1 - 1 | 0;
  }
};
protoOf(JsonPath).sx = function () {
  // Inline function 'kotlin.text.buildString' call
  // Inline function 'kotlin.apply' call
  var this_0 = StringBuilder_init_$Create$();
  this_0.y6('$');
  // Inline function 'kotlin.repeat' call
  var times = this.mx_1 + 1 | 0;
  var inductionVariable = 0;
  if (inductionVariable < times)
    do {
      var index = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      var element = this.kx_1[index];
      if (!(element == null) ? isInterface(element, SerialDescriptor) : false) {
        if (equals(element.ti(), LIST_getInstance())) {
          if (!(this.lx_1[index] === -1)) {
            this_0.y6('[');
            this_0.z9(this.lx_1[index]);
            this_0.y6(']');
          }
        } else {
          var idx = this.lx_1[index];
          if (idx >= 0) {
            this_0.y6('.');
            this_0.y6(element.yi(idx));
          }
        }
      } else {
        if (!(element === Tombstone_instance)) {
          this_0.y6('[');
          this_0.y6("'");
          this_0.x6(element);
          this_0.y6("'");
          this_0.y6(']');
        }
      }
    }
     while (inductionVariable < times);
  return this_0.toString();
};
protoOf(JsonPath).toString = function () {
  return this.sx();
};
function readObject($this) {
  // Inline function 'kotlinx.serialization.json.internal.JsonTreeReader.readObjectImpl' call
  var lastToken = $this.tx_1.xx(6);
  if ($this.tx_1.yx() === 4) {
    $this.tx_1.fx('Unexpected leading comma');
  }
  // Inline function 'kotlin.collections.linkedMapOf' call
  var result = LinkedHashMap_init_$Create$();
  $l$loop: while ($this.tx_1.zx()) {
    var key = $this.ux_1 ? $this.tx_1.by() : $this.tx_1.ay();
    $this.tx_1.xx(5);
    var element = $this.cy();
    // Inline function 'kotlin.collections.set' call
    result.x1(key, element);
    lastToken = $this.tx_1.dy();
    var tmp0_subject = lastToken;
    if (tmp0_subject !== 4)
      if (tmp0_subject === 7)
        break $l$loop;
      else {
        $this.tx_1.fx('Expected end of the object or comma');
      }
  }
  if (lastToken === 6) {
    $this.tx_1.xx(7);
  } else if (lastToken === 4) {
    if (!$this.vx_1) {
      invalidTrailingComma($this.tx_1);
    }
    $this.tx_1.xx(7);
  }
  return new JsonObject(result);
}
function readObject_0($this, $receiver, $completion) {
  var tmp = new $readObjectCOROUTINE$($this, $receiver, $completion);
  tmp.m7_1 = Unit_instance;
  tmp.n7_1 = null;
  return tmp.s7();
}
function readArray($this) {
  var lastToken = $this.tx_1.dy();
  if ($this.tx_1.yx() === 4) {
    $this.tx_1.fx('Unexpected leading comma');
  }
  // Inline function 'kotlin.collections.arrayListOf' call
  var result = ArrayList_init_$Create$();
  while ($this.tx_1.zx()) {
    var element = $this.cy();
    result.e(element);
    lastToken = $this.tx_1.dy();
    if (!(lastToken === 4)) {
      var tmp0 = $this.tx_1;
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.require' call
      var condition = lastToken === 9;
      var position = tmp0.gv_1;
      if (!condition) {
        var tmp$ret$2 = 'Expected end of the array or comma';
        tmp0.fx(tmp$ret$2, position);
      }
    }
  }
  if (lastToken === 8) {
    $this.tx_1.xx(9);
  } else if (lastToken === 4) {
    if (!$this.vx_1) {
      invalidTrailingComma($this.tx_1, 'array');
    }
    $this.tx_1.xx(9);
  }
  return new JsonArray(result);
}
function readValue($this, isString) {
  var tmp;
  if ($this.ux_1 || !isString) {
    tmp = $this.tx_1.by();
  } else {
    tmp = $this.tx_1.ay();
  }
  var string = tmp;
  if (!isString && string === 'null')
    return JsonNull_getInstance();
  return new JsonLiteral(string, isString);
}
function readDeepRecursive($this) {
  return invoke(new DeepRecursiveFunction(JsonTreeReader$readDeepRecursive$slambda_0($this, null)), Unit_instance);
}
function JsonTreeReader$readDeepRecursive$slambda(this$0, resultContinuation) {
  this.az_1 = this$0;
  CoroutineImpl.call(this, resultContinuation);
}
protoOf(JsonTreeReader$readDeepRecursive$slambda).ez = function ($this$DeepRecursiveFunction, it, $completion) {
  var tmp = this.fz($this$DeepRecursiveFunction, it, $completion);
  tmp.m7_1 = Unit_instance;
  tmp.n7_1 = null;
  return tmp.s7();
};
protoOf(JsonTreeReader$readDeepRecursive$slambda).y7 = function (p1, p2, $completion) {
  var tmp = p1 instanceof DeepRecursiveScope ? p1 : THROW_CCE();
  return this.ez(tmp, p2 instanceof Unit ? p2 : THROW_CCE(), $completion);
};
protoOf(JsonTreeReader$readDeepRecursive$slambda).s7 = function () {
  var suspendResult = this.m7_1;
  $sm: do
    try {
      var tmp = this.k7_1;
      switch (tmp) {
        case 0:
          this.l7_1 = 3;
          var tmp0_subject = this.az_1.tx_1.yx();
          if (tmp0_subject === 1) {
            this.dz_1 = readValue(this.az_1, true);
            this.k7_1 = 2;
            continue $sm;
          } else {
            if (tmp0_subject === 0) {
              this.dz_1 = readValue(this.az_1, false);
              this.k7_1 = 2;
              continue $sm;
            } else {
              if (tmp0_subject === 6) {
                this.k7_1 = 1;
                suspendResult = readObject_0(this.az_1, this.bz_1, this);
                if (suspendResult === get_COROUTINE_SUSPENDED()) {
                  return suspendResult;
                }
                continue $sm;
              } else {
                if (tmp0_subject === 8) {
                  this.dz_1 = readArray(this.az_1);
                  this.k7_1 = 2;
                  continue $sm;
                } else {
                  var tmp_0 = this;
                  this.az_1.tx_1.fx("Can't begin reading element, unexpected token");
                }
              }
            }
          }

          break;
        case 1:
          this.dz_1 = suspendResult;
          this.k7_1 = 2;
          continue $sm;
        case 2:
          return this.dz_1;
        case 3:
          throw this.n7_1;
      }
    } catch ($p) {
      var e = $p;
      if (this.l7_1 === 3) {
        throw e;
      } else {
        this.k7_1 = this.l7_1;
        this.n7_1 = e;
      }
    }
   while (true);
};
protoOf(JsonTreeReader$readDeepRecursive$slambda).fz = function ($this$DeepRecursiveFunction, it, completion) {
  var i = new JsonTreeReader$readDeepRecursive$slambda(this.az_1, completion);
  i.bz_1 = $this$DeepRecursiveFunction;
  i.cz_1 = it;
  return i;
};
function JsonTreeReader$readDeepRecursive$slambda_0(this$0, resultContinuation) {
  var i = new JsonTreeReader$readDeepRecursive$slambda(this$0, resultContinuation);
  var l = function ($this$DeepRecursiveFunction, it, $completion) {
    return i.ez($this$DeepRecursiveFunction, it, $completion);
  };
  l.$arity = 2;
  return l;
}
function $readObjectCOROUTINE$(_this__u8e3s4, _this__u8e3s4_0, resultContinuation) {
  CoroutineImpl.call(this, resultContinuation);
  this.my_1 = _this__u8e3s4;
  this.ny_1 = _this__u8e3s4_0;
}
protoOf($readObjectCOROUTINE$).s7 = function () {
  var suspendResult = this.m7_1;
  $sm: do
    try {
      var tmp = this.k7_1;
      switch (tmp) {
        case 0:
          this.l7_1 = 5;
          this.py_1 = this.my_1;
          this.qy_1 = this.py_1.tx_1.xx(6);
          if (this.py_1.tx_1.yx() === 4) {
            this.py_1.tx_1.fx('Unexpected leading comma');
          }

          var tmp_0 = this;
          tmp_0.oy_1 = LinkedHashMap_init_$Create$();
          this.k7_1 = 1;
          continue $sm;
        case 1:
          if (!this.py_1.tx_1.zx()) {
            this.k7_1 = 4;
            continue $sm;
          }

          this.ry_1 = this.py_1.ux_1 ? this.py_1.tx_1.by() : this.py_1.tx_1.ay();
          this.py_1.tx_1.xx(5);
          this.k7_1 = 2;
          suspendResult = this.ny_1.bg(Unit_instance, this);
          if (suspendResult === get_COROUTINE_SUSPENDED()) {
            return suspendResult;
          }

          continue $sm;
        case 2:
          var element = suspendResult;
          var tmp0 = this.oy_1;
          var key = this.ry_1;
          tmp0.x1(key, element);
          this.qy_1 = this.py_1.tx_1.dy();
          var tmp0_subject = this.qy_1;
          if (tmp0_subject === 4) {
            this.k7_1 = 3;
            continue $sm;
          } else {
            if (tmp0_subject === 7) {
              this.k7_1 = 4;
              continue $sm;
            } else {
              this.py_1.tx_1.fx('Expected end of the object or comma');
            }
          }

          break;
        case 3:
          this.k7_1 = 1;
          continue $sm;
        case 4:
          if (this.qy_1 === 6) {
            this.py_1.tx_1.xx(7);
          } else if (this.qy_1 === 4) {
            if (!this.py_1.vx_1) {
              invalidTrailingComma(this.py_1.tx_1);
            }
            this.py_1.tx_1.xx(7);
          }

          return new JsonObject(this.oy_1);
        case 5:
          throw this.n7_1;
      }
    } catch ($p) {
      var e = $p;
      if (this.l7_1 === 5) {
        throw e;
      } else {
        this.k7_1 = this.l7_1;
        this.n7_1 = e;
      }
    }
   while (true);
};
function JsonTreeReader(configuration, lexer) {
  this.tx_1 = lexer;
  this.ux_1 = configuration.nv_1;
  this.vx_1 = configuration.zv_1;
  this.wx_1 = 0;
}
protoOf(JsonTreeReader).cy = function () {
  var token = this.tx_1.yx();
  var tmp;
  if (token === 1) {
    tmp = readValue(this, true);
  } else if (token === 0) {
    tmp = readValue(this, false);
  } else if (token === 6) {
    var tmp_0;
    this.wx_1 = this.wx_1 + 1 | 0;
    if (this.wx_1 === 200) {
      tmp_0 = readDeepRecursive(this);
    } else {
      tmp_0 = readObject(this);
    }
    var result = tmp_0;
    this.wx_1 = this.wx_1 - 1 | 0;
    tmp = result;
  } else if (token === 8) {
    tmp = readArray(this);
  } else {
    this.tx_1.fx('Cannot read Json element because of unexpected ' + tokenDescription(token));
  }
  return tmp;
};
function classDiscriminator(_this__u8e3s4, json) {
  var _iterator__ex2g4s = _this__u8e3s4.xi().h();
  while (_iterator__ex2g4s.i()) {
    var annotation = _iterator__ex2g4s.j();
    if (annotation instanceof JsonClassDiscriminator)
      return annotation.gz_1;
  }
  return json.tu_1.uv_1;
}
function Key() {
}
function DescriptorSchemaCache() {
  this.gx_1 = createMapForCache(16);
}
protoOf(DescriptorSchemaCache).hz = function (descriptor, key, value) {
  // Inline function 'kotlin.collections.getOrPut' call
  var this_0 = this.gx_1;
  var value_0 = this_0.t1(descriptor);
  var tmp;
  if (value_0 == null) {
    var answer = createMapForCache(2);
    this_0.x1(descriptor, answer);
    tmp = answer;
  } else {
    tmp = value_0;
  }
  var tmp0 = tmp;
  var tmp2 = key instanceof Key ? key : THROW_CCE();
  // Inline function 'kotlin.collections.set' call
  var value_1 = !(value == null) ? value : THROW_CCE();
  tmp0.x1(tmp2, value_1);
};
protoOf(DescriptorSchemaCache).hx = function (descriptor, key, defaultValue) {
  var tmp0_safe_receiver = this.iz(descriptor, key);
  if (tmp0_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    return tmp0_safe_receiver;
  }
  var value = defaultValue();
  this.hz(descriptor, key, value);
  return value;
};
protoOf(DescriptorSchemaCache).iz = function (descriptor, key) {
  var tmp0_safe_receiver = this.gx_1.t1(descriptor);
  var tmp;
  if (tmp0_safe_receiver == null) {
    tmp = null;
  } else {
    tmp = tmp0_safe_receiver.t1(key instanceof Key ? key : THROW_CCE());
  }
  var tmp_0 = tmp;
  return !(tmp_0 == null) ? tmp_0 : null;
};
function DiscriminatorHolder(discriminatorToSkip) {
  this.jz_1 = discriminatorToSkip;
}
function trySkip($this, $receiver, unknownKey) {
  if ($receiver == null)
    return false;
  if ($receiver.jz_1 === unknownKey) {
    $receiver.jz_1 = null;
    return true;
  }
  return false;
}
function skipLeftoverElements($this, descriptor) {
  while (!($this.hl(descriptor) === -1)) {
  }
}
function checkLeadingComma($this) {
  if ($this.av_1.yx() === 4) {
    $this.av_1.fx('Unexpected leading comma');
  }
}
function decodeMapIndex($this) {
  var hasComma = false;
  var decodingKey = !(($this.cv_1 % 2 | 0) === 0);
  if (decodingKey) {
    if (!($this.cv_1 === -1)) {
      hasComma = $this.av_1.lz();
    }
  } else {
    $this.av_1.kz(_Char___init__impl__6a9atx(58));
  }
  var tmp;
  if ($this.av_1.zx()) {
    if (decodingKey) {
      if ($this.cv_1 === -1) {
        var tmp0 = $this.av_1;
        // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.require' call
        var condition = !hasComma;
        var position = tmp0.gv_1;
        if (!condition) {
          var tmp$ret$1 = 'Unexpected leading comma';
          tmp0.fx(tmp$ret$1, position);
        }
      } else {
        var tmp0_0 = $this.av_1;
        // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.require' call
        var condition_0 = hasComma;
        var position_0 = tmp0_0.gv_1;
        if (!condition_0) {
          var tmp$ret$3 = 'Expected comma after the key-value pair';
          tmp0_0.fx(tmp$ret$3, position_0);
        }
      }
    }
    $this.cv_1 = $this.cv_1 + 1 | 0;
    tmp = $this.cv_1;
  } else {
    if (hasComma && !$this.yu_1.tu_1.zv_1) {
      invalidTrailingComma($this.av_1);
    }
    tmp = -1;
  }
  return tmp;
}
function coerceInputValue($this, descriptor, index) {
  var tmp0 = $this.yu_1;
  var tmp$ret$0;
  $l$block_2: {
    // Inline function 'kotlinx.serialization.json.internal.tryCoerceValue' call
    var isOptional = descriptor.cj(index);
    var elementDescriptor = descriptor.bj(index);
    var tmp;
    if (isOptional && !elementDescriptor.ui()) {
      tmp = $this.av_1.mz(true);
    } else {
      tmp = false;
    }
    if (tmp) {
      tmp$ret$0 = true;
      break $l$block_2;
    }
    if (equals(elementDescriptor.ti(), ENUM_getInstance())) {
      var tmp_0;
      if (elementDescriptor.ui()) {
        tmp_0 = $this.av_1.mz(false);
      } else {
        tmp_0 = false;
      }
      if (tmp_0) {
        tmp$ret$0 = false;
        break $l$block_2;
      }
      var tmp0_elvis_lhs = $this.av_1.nz($this.ev_1.nv_1);
      var tmp_1;
      if (tmp0_elvis_lhs == null) {
        tmp$ret$0 = false;
        break $l$block_2;
      } else {
        tmp_1 = tmp0_elvis_lhs;
      }
      var enumValue = tmp_1;
      var enumIndex = getJsonNameIndex(elementDescriptor, tmp0, enumValue);
      var coerceToNull = !tmp0.tu_1.qv_1 && elementDescriptor.ui();
      if (enumIndex === -3 && (isOptional || coerceToNull)) {
        $this.av_1.ay();
        tmp$ret$0 = true;
        break $l$block_2;
      }
    }
    tmp$ret$0 = false;
  }
  return tmp$ret$0;
}
function decodeObjectIndex($this, descriptor) {
  var hasComma = $this.av_1.lz();
  while ($this.av_1.zx()) {
    hasComma = false;
    var key = decodeStringKey($this);
    $this.av_1.kz(_Char___init__impl__6a9atx(58));
    var index = getJsonNameIndex(descriptor, $this.yu_1, key);
    var tmp;
    if (!(index === -3)) {
      var tmp_0;
      if ($this.ev_1.sv_1 && coerceInputValue($this, descriptor, index)) {
        hasComma = $this.av_1.lz();
        tmp_0 = false;
      } else {
        var tmp0_safe_receiver = $this.fv_1;
        if (tmp0_safe_receiver == null)
          null;
        else {
          tmp0_safe_receiver.cx(index);
        }
        return index;
      }
      tmp = tmp_0;
    } else {
      tmp = true;
    }
    var isUnknown = tmp;
    if (isUnknown) {
      hasComma = handleUnknown($this, key);
    }
  }
  if (hasComma && !$this.yu_1.tu_1.zv_1) {
    invalidTrailingComma($this.av_1);
  }
  var tmp1_safe_receiver = $this.fv_1;
  var tmp2_elvis_lhs = tmp1_safe_receiver == null ? null : tmp1_safe_receiver.dx();
  return tmp2_elvis_lhs == null ? -1 : tmp2_elvis_lhs;
}
function handleUnknown($this, key) {
  if ($this.ev_1.mv_1 || trySkip($this, $this.dv_1, key)) {
    $this.av_1.pz($this.ev_1.nv_1);
  } else {
    $this.av_1.oz(key);
  }
  return $this.av_1.lz();
}
function decodeListIndex($this) {
  var hasComma = $this.av_1.lz();
  var tmp;
  if ($this.av_1.zx()) {
    if (!($this.cv_1 === -1) && !hasComma) {
      $this.av_1.fx('Expected end of the array or comma');
    }
    $this.cv_1 = $this.cv_1 + 1 | 0;
    tmp = $this.cv_1;
  } else {
    if (hasComma && !$this.yu_1.tu_1.zv_1) {
      invalidTrailingComma($this.av_1, 'array');
    }
    tmp = -1;
  }
  return tmp;
}
function decodeStringKey($this) {
  var tmp;
  if ($this.ev_1.nv_1) {
    tmp = $this.av_1.rz();
  } else {
    tmp = $this.av_1.qz();
  }
  return tmp;
}
function StreamingJsonDecoder(json, mode, lexer, descriptor, discriminatorHolder) {
  AbstractDecoder.call(this);
  this.yu_1 = json;
  this.zu_1 = mode;
  this.av_1 = lexer;
  this.bv_1 = this.yu_1.fl();
  this.cv_1 = -1;
  this.dv_1 = discriminatorHolder;
  this.ev_1 = this.yu_1.tu_1;
  this.fv_1 = this.ev_1.qv_1 ? null : new JsonElementMarker(descriptor);
}
protoOf(StreamingJsonDecoder).cw = function () {
  return this.yu_1;
};
protoOf(StreamingJsonDecoder).fl = function () {
  return this.bv_1;
};
protoOf(StreamingJsonDecoder).dw = function () {
  return (new JsonTreeReader(this.yu_1.tu_1, this.av_1)).cy();
};
protoOf(StreamingJsonDecoder).rk = function (deserializer) {
  try {
    var tmp;
    if (!(deserializer instanceof AbstractPolymorphicSerializer)) {
      tmp = true;
    } else {
      tmp = this.yu_1.tu_1.tv_1;
    }
    if (tmp) {
      return deserializer.li(this);
    }
    var discriminator = classDiscriminator(deserializer.ki(), this.yu_1);
    var tmp0_elvis_lhs = this.av_1.sz(discriminator, this.ev_1.nv_1);
    var tmp_0;
    if (tmp0_elvis_lhs == null) {
      var tmp2 = isInterface(deserializer, DeserializationStrategy) ? deserializer : THROW_CCE();
      var tmp$ret$0;
      $l$block: {
        // Inline function 'kotlinx.serialization.json.internal.decodeSerializableValuePolymorphic' call
        var tmp_1;
        if (!(tmp2 instanceof AbstractPolymorphicSerializer)) {
          tmp_1 = true;
        } else {
          tmp_1 = this.cw().tu_1.tv_1;
        }
        if (tmp_1) {
          tmp$ret$0 = tmp2.li(this);
          break $l$block;
        }
        var discriminator_0 = classDiscriminator(tmp2.ki(), this.cw());
        var tmp0 = this.dw();
        // Inline function 'kotlinx.serialization.json.internal.cast' call
        var serialName = tmp2.ki().si();
        if (!(tmp0 instanceof JsonObject)) {
          var tmp_2 = getKClass(JsonObject).v8();
          var tmp_3 = getKClassFromExpression(tmp0).v8();
          var tmp$ret$2 = this.av_1.hv_1.sx();
          throw JsonDecodingException_0(-1, 'Expected ' + tmp_2 + ', but had ' + tmp_3 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$2, toString_0(tmp0));
        }
        var jsonTree = tmp0;
        var tmp0_safe_receiver = jsonTree.fb(discriminator_0);
        var tmp1_safe_receiver = tmp0_safe_receiver == null ? null : get_jsonPrimitive(tmp0_safe_receiver);
        var type = tmp1_safe_receiver == null ? null : get_contentOrNull(tmp1_safe_receiver);
        var tmp_4;
        try {
          tmp_4 = findPolymorphicSerializer(tmp2, this, type);
        } catch ($p) {
          var tmp_5;
          if ($p instanceof SerializationException) {
            var it = $p;
            throw JsonDecodingException_0(-1, ensureNotNull(it.message), jsonTree.toString());
          } else {
            throw $p;
          }
        }
        var tmp_6 = tmp_4;
        var actualSerializer = isInterface(tmp_6, DeserializationStrategy) ? tmp_6 : THROW_CCE();
        tmp$ret$0 = readPolymorphicJson(this.cw(), discriminator_0, jsonTree, actualSerializer);
      }
      return tmp$ret$0;
    } else {
      tmp_0 = tmp0_elvis_lhs;
    }
    var type_0 = tmp_0;
    var tmp_7;
    try {
      tmp_7 = findPolymorphicSerializer(deserializer, this, type_0);
    } catch ($p) {
      var tmp_8;
      if ($p instanceof SerializationException) {
        var it_0 = $p;
        var message = removeSuffix(substringBefore(ensureNotNull(it_0.message), _Char___init__impl__6a9atx(10)), '.');
        var hint = substringAfter(ensureNotNull(it_0.message), _Char___init__impl__6a9atx(10), '');
        this.av_1.fx(message, VOID, hint);
      } else {
        throw $p;
      }
      tmp_7 = tmp_8;
    }
    var tmp_9 = tmp_7;
    var actualSerializer_0 = isInterface(tmp_9, DeserializationStrategy) ? tmp_9 : THROW_CCE();
    this.dv_1 = new DiscriminatorHolder(discriminator);
    return actualSerializer_0.li(this);
  } catch ($p) {
    if ($p instanceof MissingFieldException) {
      var e = $p;
      if (contains_0(ensureNotNull(e.message), 'at path'))
        throw e;
      throw new MissingFieldException(e.oi_1, plus(e.message, ' at path: ') + this.av_1.hv_1.sx(), e);
    } else {
      throw $p;
    }
  }
};
protoOf(StreamingJsonDecoder).sk = function (descriptor) {
  var newMode = switchMode(this.yu_1, descriptor);
  this.av_1.hv_1.nx(descriptor);
  this.av_1.kz(newMode.vz_1);
  checkLeadingComma(this);
  var tmp;
  switch (newMode.z1_1) {
    case 1:
    case 2:
    case 3:
      tmp = new StreamingJsonDecoder(this.yu_1, newMode, this.av_1, descriptor, this.dv_1);
      break;
    default:
      var tmp_0;
      if (this.zu_1.equals(newMode) && this.yu_1.tu_1.qv_1) {
        tmp_0 = this;
      } else {
        tmp_0 = new StreamingJsonDecoder(this.yu_1, newMode, this.av_1, descriptor, this.dv_1);
      }

      tmp = tmp_0;
      break;
  }
  return tmp;
};
protoOf(StreamingJsonDecoder).tk = function (descriptor) {
  if (this.yu_1.tu_1.mv_1 && descriptor.wi() === 0) {
    skipLeftoverElements(this, descriptor);
  }
  if (this.av_1.lz() && !this.yu_1.tu_1.zv_1) {
    invalidTrailingComma(this.av_1, '');
  }
  this.av_1.kz(this.zu_1.wz_1);
  this.av_1.hv_1.rx();
};
protoOf(StreamingJsonDecoder).ek = function () {
  var tmp;
  var tmp0_safe_receiver = this.fv_1;
  var tmp1_elvis_lhs = tmp0_safe_receiver == null ? null : tmp0_safe_receiver.bx_1;
  if (!(tmp1_elvis_lhs == null ? false : tmp1_elvis_lhs)) {
    tmp = !this.av_1.xz();
  } else {
    tmp = false;
  }
  return tmp;
};
protoOf(StreamingJsonDecoder).fk = function () {
  return null;
};
protoOf(StreamingJsonDecoder).dl = function (descriptor, index, deserializer, previousValue) {
  var isMapKey = this.zu_1.equals(WriteMode_MAP_getInstance()) && (index & 1) === 0;
  if (isMapKey) {
    this.av_1.hv_1.qx();
  }
  var value = protoOf(AbstractDecoder).dl.call(this, descriptor, index, deserializer, previousValue);
  if (isMapKey) {
    this.av_1.hv_1.px(value);
  }
  return value;
};
protoOf(StreamingJsonDecoder).hl = function (descriptor) {
  var index;
  switch (this.zu_1.z1_1) {
    case 0:
      index = decodeObjectIndex(this, descriptor);
      break;
    case 2:
      index = decodeMapIndex(this);
      break;
    default:
      index = decodeListIndex(this);
      break;
  }
  if (!this.zu_1.equals(WriteMode_MAP_getInstance())) {
    this.av_1.hv_1.ox(index);
  }
  return index;
};
protoOf(StreamingJsonDecoder).gk = function () {
  return this.av_1.yz();
};
protoOf(StreamingJsonDecoder).hk = function () {
  var value = this.av_1.ow();
  if (!equalsLong(value, fromInt(convertToByte(value)))) {
    this.av_1.fx("Failed to parse byte for input '" + value.toString() + "'");
  }
  return convertToByte(value);
};
protoOf(StreamingJsonDecoder).ik = function () {
  var value = this.av_1.ow();
  if (!equalsLong(value, fromInt(convertToShort(value)))) {
    this.av_1.fx("Failed to parse short for input '" + value.toString() + "'");
  }
  return convertToShort(value);
};
protoOf(StreamingJsonDecoder).jk = function () {
  var value = this.av_1.ow();
  if (!equalsLong(value, fromInt(convertToInt(value)))) {
    this.av_1.fx("Failed to parse int for input '" + value.toString() + "'");
  }
  return convertToInt(value);
};
protoOf(StreamingJsonDecoder).kk = function () {
  return this.av_1.ow();
};
protoOf(StreamingJsonDecoder).lk = function () {
  var tmp0 = this.av_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.by();
    try {
      // Inline function 'kotlin.text.toFloat' call
      // Inline function 'kotlin.js.unsafeCast' call
      // Inline function 'kotlin.js.asDynamic' call
      tmp$ret$0 = toDouble(input);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.fx("Failed to parse type '" + 'float' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  var result = tmp$ret$0;
  var specialFp = this.yu_1.tu_1.vv_1;
  if (specialFp || isFinite(result))
    return result;
  throwInvalidFloatingPointDecoded(this.av_1, result);
};
protoOf(StreamingJsonDecoder).mk = function () {
  var tmp0 = this.av_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.by();
    try {
      tmp$ret$0 = toDouble(input);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.fx("Failed to parse type '" + 'double' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  var result = tmp$ret$0;
  var specialFp = this.yu_1.tu_1.vv_1;
  if (specialFp || isFinite_0(result))
    return result;
  throwInvalidFloatingPointDecoded(this.av_1, result);
};
protoOf(StreamingJsonDecoder).nk = function () {
  var string = this.av_1.by();
  if (!(string.length === 1)) {
    this.av_1.fx("Expected single char, but got '" + string + "'");
  }
  return charCodeAt(string, 0);
};
protoOf(StreamingJsonDecoder).ok = function () {
  var tmp;
  if (this.ev_1.nv_1) {
    tmp = this.av_1.rz();
  } else {
    tmp = this.av_1.ay();
  }
  return tmp;
};
protoOf(StreamingJsonDecoder).pk = function (descriptor) {
  return get_isUnsignedNumber(descriptor) ? new JsonDecoderForUnsignedTypes(this.av_1, this.yu_1) : protoOf(AbstractDecoder).pk.call(this, descriptor);
};
function JsonDecoderForUnsignedTypes(lexer, json) {
  AbstractDecoder.call(this);
  this.zz_1 = lexer;
  this.a10_1 = json.fl();
}
protoOf(JsonDecoderForUnsignedTypes).fl = function () {
  return this.a10_1;
};
protoOf(JsonDecoderForUnsignedTypes).hl = function (descriptor) {
  // Inline function 'kotlin.error' call
  var message = 'unsupported';
  throw IllegalStateException_init_$Create$(toString_0(message));
};
protoOf(JsonDecoderForUnsignedTypes).jk = function () {
  var tmp0 = this.zz_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.by();
    try {
      // Inline function 'kotlin.UInt.toInt' call
      var this_0 = toUInt(input);
      tmp$ret$0 = _UInt___get_data__impl__f0vqqw(this_0);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.fx("Failed to parse type '" + 'UInt' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$0;
};
protoOf(JsonDecoderForUnsignedTypes).kk = function () {
  var tmp0 = this.zz_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.by();
    try {
      // Inline function 'kotlin.ULong.toLong' call
      var this_0 = toULong(input);
      tmp$ret$0 = _ULong___get_data__impl__fggpzb(this_0);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.fx("Failed to parse type '" + 'ULong' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$0;
};
protoOf(JsonDecoderForUnsignedTypes).hk = function () {
  var tmp0 = this.zz_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.by();
    try {
      // Inline function 'kotlin.UByte.toByte' call
      var this_0 = toUByte(input);
      tmp$ret$0 = _UByte___get_data__impl__jof9qr(this_0);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.fx("Failed to parse type '" + 'UByte' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$0;
};
protoOf(JsonDecoderForUnsignedTypes).ik = function () {
  var tmp0 = this.zz_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.by();
    try {
      // Inline function 'kotlin.UShort.toShort' call
      var this_0 = toUShort(input);
      tmp$ret$0 = _UShort___get_data__impl__g0245(this_0);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.fx("Failed to parse type '" + 'UShort' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$0;
};
function get_unsignedNumberDescriptors() {
  _init_properties_StreamingJsonEncoder_kt__pn1bsi();
  return unsignedNumberDescriptors;
}
var unsignedNumberDescriptors;
function get_isUnsignedNumber(_this__u8e3s4) {
  _init_properties_StreamingJsonEncoder_kt__pn1bsi();
  return _this__u8e3s4.vi() && get_unsignedNumberDescriptors().m1(_this__u8e3s4);
}
var properties_initialized_StreamingJsonEncoder_kt_6ifwwk;
function _init_properties_StreamingJsonEncoder_kt__pn1bsi() {
  if (!properties_initialized_StreamingJsonEncoder_kt_6ifwwk) {
    properties_initialized_StreamingJsonEncoder_kt_6ifwwk = true;
    unsignedNumberDescriptors = setOf([serializer_0(Companion_getInstance()).ki(), serializer_1(Companion_getInstance_0()).ki(), serializer_2(Companion_getInstance_1()).ki(), serializer_3(Companion_getInstance_2()).ki()]);
  }
}
function get_ESCAPE_STRINGS() {
  _init_properties_StringOps_kt__fcy1db();
  return ESCAPE_STRINGS;
}
var ESCAPE_STRINGS;
var ESCAPE_MARKERS;
function toHexChar(i) {
  _init_properties_StringOps_kt__fcy1db();
  var d = i & 15;
  var tmp;
  if (d < 10) {
    // Inline function 'kotlin.code' call
    var this_0 = _Char___init__impl__6a9atx(48);
    var tmp$ret$0 = Char__toInt_impl_vasixd(this_0);
    tmp = numberToChar(d + tmp$ret$0 | 0);
  } else {
    var tmp_0 = d - 10 | 0;
    // Inline function 'kotlin.code' call
    var this_1 = _Char___init__impl__6a9atx(97);
    var tmp$ret$1 = Char__toInt_impl_vasixd(this_1);
    tmp = numberToChar(tmp_0 + tmp$ret$1 | 0);
  }
  return tmp;
}
function printQuoted(_this__u8e3s4, value) {
  _init_properties_StringOps_kt__fcy1db();
  _this__u8e3s4.z6(_Char___init__impl__6a9atx(34));
  var lastPos = 0;
  var inductionVariable = 0;
  var last = charSequenceLength(value) - 1 | 0;
  if (inductionVariable <= last)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      // Inline function 'kotlin.code' call
      var this_0 = charCodeAt(value, i);
      var c = Char__toInt_impl_vasixd(this_0);
      if (c < get_ESCAPE_STRINGS().length && !(get_ESCAPE_STRINGS()[c] == null)) {
        _this__u8e3s4.x9(value, lastPos, i);
        _this__u8e3s4.y6(get_ESCAPE_STRINGS()[c]);
        lastPos = i + 1 | 0;
      }
    }
     while (inductionVariable <= last);
  if (!(lastPos === 0))
    _this__u8e3s4.x9(value, lastPos, value.length);
  else
    _this__u8e3s4.y6(value);
  _this__u8e3s4.z6(_Char___init__impl__6a9atx(34));
}
function toBooleanStrictOrNull(_this__u8e3s4) {
  _init_properties_StringOps_kt__fcy1db();
  return equals_0(_this__u8e3s4, 'true', true) ? true : equals_0(_this__u8e3s4, 'false', true) ? false : null;
}
var properties_initialized_StringOps_kt_wzaea7;
function _init_properties_StringOps_kt__fcy1db() {
  if (!properties_initialized_StringOps_kt_wzaea7) {
    properties_initialized_StringOps_kt_wzaea7 = true;
    // Inline function 'kotlin.arrayOfNulls' call
    // Inline function 'kotlin.apply' call
    var this_0 = Array(93);
    var inductionVariable = 0;
    if (inductionVariable <= 31)
      do {
        var c = inductionVariable;
        inductionVariable = inductionVariable + 1 | 0;
        var c1 = toHexChar(c >> 12);
        var c2 = toHexChar(c >> 8);
        var c3 = toHexChar(c >> 4);
        var c4 = toHexChar(c);
        this_0[c] = '\\u' + toString_1(c1) + toString_1(c2) + toString_1(c3) + toString_1(c4);
      }
       while (inductionVariable <= 31);
    // Inline function 'kotlin.code' call
    var this_1 = _Char___init__impl__6a9atx(34);
    this_0[Char__toInt_impl_vasixd(this_1)] = '\\"';
    // Inline function 'kotlin.code' call
    var this_2 = _Char___init__impl__6a9atx(92);
    this_0[Char__toInt_impl_vasixd(this_2)] = '\\\\';
    // Inline function 'kotlin.code' call
    var this_3 = _Char___init__impl__6a9atx(9);
    this_0[Char__toInt_impl_vasixd(this_3)] = '\\t';
    // Inline function 'kotlin.code' call
    var this_4 = _Char___init__impl__6a9atx(8);
    this_0[Char__toInt_impl_vasixd(this_4)] = '\\b';
    // Inline function 'kotlin.code' call
    var this_5 = _Char___init__impl__6a9atx(10);
    this_0[Char__toInt_impl_vasixd(this_5)] = '\\n';
    // Inline function 'kotlin.code' call
    var this_6 = _Char___init__impl__6a9atx(13);
    this_0[Char__toInt_impl_vasixd(this_6)] = '\\r';
    this_0[12] = '\\f';
    ESCAPE_STRINGS = this_0;
    // Inline function 'kotlin.apply' call
    var this_7 = new Int8Array(93);
    var inductionVariable_0 = 0;
    if (inductionVariable_0 <= 31)
      do {
        var c_0 = inductionVariable_0;
        inductionVariable_0 = inductionVariable_0 + 1 | 0;
        this_7[c_0] = 1;
      }
       while (inductionVariable_0 <= 31);
    // Inline function 'kotlin.code' call
    var this_8 = _Char___init__impl__6a9atx(34);
    var tmp = Char__toInt_impl_vasixd(this_8);
    // Inline function 'kotlin.code' call
    var this_9 = _Char___init__impl__6a9atx(34);
    var tmp$ret$3 = Char__toInt_impl_vasixd(this_9);
    this_7[tmp] = toByte(tmp$ret$3);
    // Inline function 'kotlin.code' call
    var this_10 = _Char___init__impl__6a9atx(92);
    var tmp_0 = Char__toInt_impl_vasixd(this_10);
    // Inline function 'kotlin.code' call
    var this_11 = _Char___init__impl__6a9atx(92);
    var tmp$ret$5 = Char__toInt_impl_vasixd(this_11);
    this_7[tmp_0] = toByte(tmp$ret$5);
    // Inline function 'kotlin.code' call
    var this_12 = _Char___init__impl__6a9atx(9);
    var tmp_1 = Char__toInt_impl_vasixd(this_12);
    // Inline function 'kotlin.code' call
    var this_13 = _Char___init__impl__6a9atx(116);
    var tmp$ret$7 = Char__toInt_impl_vasixd(this_13);
    this_7[tmp_1] = toByte(tmp$ret$7);
    // Inline function 'kotlin.code' call
    var this_14 = _Char___init__impl__6a9atx(8);
    var tmp_2 = Char__toInt_impl_vasixd(this_14);
    // Inline function 'kotlin.code' call
    var this_15 = _Char___init__impl__6a9atx(98);
    var tmp$ret$9 = Char__toInt_impl_vasixd(this_15);
    this_7[tmp_2] = toByte(tmp$ret$9);
    // Inline function 'kotlin.code' call
    var this_16 = _Char___init__impl__6a9atx(10);
    var tmp_3 = Char__toInt_impl_vasixd(this_16);
    // Inline function 'kotlin.code' call
    var this_17 = _Char___init__impl__6a9atx(110);
    var tmp$ret$11 = Char__toInt_impl_vasixd(this_17);
    this_7[tmp_3] = toByte(tmp$ret$11);
    // Inline function 'kotlin.code' call
    var this_18 = _Char___init__impl__6a9atx(13);
    var tmp_4 = Char__toInt_impl_vasixd(this_18);
    // Inline function 'kotlin.code' call
    var this_19 = _Char___init__impl__6a9atx(114);
    var tmp$ret$13 = Char__toInt_impl_vasixd(this_19);
    this_7[tmp_4] = toByte(tmp$ret$13);
    // Inline function 'kotlin.code' call
    var this_20 = _Char___init__impl__6a9atx(102);
    var tmp$ret$14 = Char__toInt_impl_vasixd(this_20);
    this_7[12] = toByte(tmp$ret$14);
    ESCAPE_MARKERS = this_7;
  }
}
function unparsedPrimitive($this, literal, primitive, tag) {
  var type = startsWith(primitive, 'i') ? 'an ' + primitive : 'a ' + primitive;
  throw JsonDecodingException_0(-1, "Failed to parse literal '" + literal.toString() + "' as " + type + ' value at element: ' + $this.h10(tag), toString_0($this.i10()));
}
function AbstractJsonTreeDecoder(json, value, polymorphicDiscriminator) {
  polymorphicDiscriminator = polymorphicDiscriminator === VOID ? null : polymorphicDiscriminator;
  NamedValueDecoder.call(this);
  this.d10_1 = json;
  this.e10_1 = value;
  this.f10_1 = polymorphicDiscriminator;
  this.g10_1 = this.cw().tu_1;
}
protoOf(AbstractJsonTreeDecoder).cw = function () {
  return this.d10_1;
};
protoOf(AbstractJsonTreeDecoder).q1 = function () {
  return this.e10_1;
};
protoOf(AbstractJsonTreeDecoder).fl = function () {
  return this.cw().fl();
};
protoOf(AbstractJsonTreeDecoder).i10 = function () {
  var tmp0_safe_receiver = this.mt();
  var tmp;
  if (tmp0_safe_receiver == null) {
    tmp = null;
  } else {
    // Inline function 'kotlin.let' call
    tmp = this.j10(tmp0_safe_receiver);
  }
  var tmp1_elvis_lhs = tmp;
  return tmp1_elvis_lhs == null ? this.q1() : tmp1_elvis_lhs;
};
protoOf(AbstractJsonTreeDecoder).h10 = function (currentTag) {
  return this.ot() + ('.' + currentTag);
};
protoOf(AbstractJsonTreeDecoder).dw = function () {
  return this.i10();
};
protoOf(AbstractJsonTreeDecoder).rk = function (deserializer) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.decodeSerializableValuePolymorphic' call
    var tmp;
    if (!(deserializer instanceof AbstractPolymorphicSerializer)) {
      tmp = true;
    } else {
      tmp = this.cw().tu_1.tv_1;
    }
    if (tmp) {
      tmp$ret$0 = deserializer.li(this);
      break $l$block;
    }
    var discriminator = classDiscriminator(deserializer.ki(), this.cw());
    var tmp0 = this.dw();
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var serialName = deserializer.ki().si();
    if (!(tmp0 instanceof JsonObject)) {
      var tmp_0 = getKClass(JsonObject).v8();
      var tmp_1 = getKClassFromExpression(tmp0).v8();
      var tmp$ret$2 = this.ot();
      throw JsonDecodingException_0(-1, 'Expected ' + tmp_0 + ', but had ' + tmp_1 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$2, toString_0(tmp0));
    }
    var jsonTree = tmp0;
    var tmp0_safe_receiver = jsonTree.fb(discriminator);
    var tmp1_safe_receiver = tmp0_safe_receiver == null ? null : get_jsonPrimitive(tmp0_safe_receiver);
    var type = tmp1_safe_receiver == null ? null : get_contentOrNull(tmp1_safe_receiver);
    var tmp_2;
    try {
      tmp_2 = findPolymorphicSerializer(deserializer, this, type);
    } catch ($p) {
      var tmp_3;
      if ($p instanceof SerializationException) {
        var it = $p;
        throw JsonDecodingException_0(-1, ensureNotNull(it.message), jsonTree.toString());
      } else {
        throw $p;
      }
    }
    var tmp_4 = tmp_2;
    var actualSerializer = isInterface(tmp_4, DeserializationStrategy) ? tmp_4 : THROW_CCE();
    tmp$ret$0 = readPolymorphicJson(this.cw(), discriminator, jsonTree, actualSerializer);
  }
  return tmp$ret$0;
};
protoOf(AbstractJsonTreeDecoder).nt = function (parentName, childName) {
  return childName;
};
protoOf(AbstractJsonTreeDecoder).sk = function (descriptor) {
  var currentObject = this.i10();
  var tmp0_subject = descriptor.ti();
  var tmp;
  var tmp_0;
  if (equals(tmp0_subject, LIST_getInstance())) {
    tmp_0 = true;
  } else {
    tmp_0 = tmp0_subject instanceof PolymorphicKind;
  }
  if (tmp_0) {
    var tmp_1 = this.cw();
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var serialName = descriptor.si();
    if (!(currentObject instanceof JsonArray)) {
      var tmp_2 = getKClass(JsonArray).v8();
      var tmp_3 = getKClassFromExpression(currentObject).v8();
      var tmp$ret$2 = this.ot();
      throw JsonDecodingException_0(-1, 'Expected ' + tmp_2 + ', but had ' + tmp_3 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$2, toString_0(currentObject));
    }
    tmp = new JsonTreeListDecoder(tmp_1, currentObject);
  } else {
    if (equals(tmp0_subject, MAP_getInstance())) {
      // Inline function 'kotlinx.serialization.json.internal.selectMapMode' call
      var this_0 = this.cw();
      var keyDescriptor = carrierDescriptor(descriptor.bj(0), this_0.fl());
      var keyKind = keyDescriptor.ti();
      var tmp_4;
      var tmp_5;
      if (keyKind instanceof PrimitiveKind) {
        tmp_5 = true;
      } else {
        tmp_5 = equals(keyKind, ENUM_getInstance());
      }
      if (tmp_5) {
        var tmp_6 = this.cw();
        // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
        // Inline function 'kotlinx.serialization.json.internal.cast' call
        var serialName_0 = descriptor.si();
        if (!(currentObject instanceof JsonObject)) {
          var tmp_7 = getKClass(JsonObject).v8();
          var tmp_8 = getKClassFromExpression(currentObject).v8();
          var tmp$ret$7 = this.ot();
          throw JsonDecodingException_0(-1, 'Expected ' + tmp_7 + ', but had ' + tmp_8 + ' as the serialized body of ' + serialName_0 + ' at element: ' + tmp$ret$7, toString_0(currentObject));
        }
        tmp_4 = new JsonTreeMapDecoder(tmp_6, currentObject);
      } else {
        if (this_0.tu_1.ov_1) {
          var tmp_9 = this.cw();
          // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
          // Inline function 'kotlinx.serialization.json.internal.cast' call
          var serialName_1 = descriptor.si();
          if (!(currentObject instanceof JsonArray)) {
            var tmp_10 = getKClass(JsonArray).v8();
            var tmp_11 = getKClassFromExpression(currentObject).v8();
            var tmp$ret$11 = this.ot();
            throw JsonDecodingException_0(-1, 'Expected ' + tmp_10 + ', but had ' + tmp_11 + ' as the serialized body of ' + serialName_1 + ' at element: ' + tmp$ret$11, toString_0(currentObject));
          }
          tmp_4 = new JsonTreeListDecoder(tmp_9, currentObject);
        } else {
          throw InvalidKeyKindException(keyDescriptor);
        }
      }
      tmp = tmp_4;
    } else {
      var tmp_12 = this.cw();
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
      // Inline function 'kotlinx.serialization.json.internal.cast' call
      var serialName_2 = descriptor.si();
      if (!(currentObject instanceof JsonObject)) {
        var tmp_13 = getKClass(JsonObject).v8();
        var tmp_14 = getKClassFromExpression(currentObject).v8();
        var tmp$ret$14 = this.ot();
        throw JsonDecodingException_0(-1, 'Expected ' + tmp_13 + ', but had ' + tmp_14 + ' as the serialized body of ' + serialName_2 + ' at element: ' + tmp$ret$14, toString_0(currentObject));
      }
      tmp = new JsonTreeDecoder(tmp_12, currentObject, this.f10_1);
    }
  }
  return tmp;
};
protoOf(AbstractJsonTreeDecoder).tk = function (descriptor) {
};
protoOf(AbstractJsonTreeDecoder).ek = function () {
  var tmp = this.i10();
  return !(tmp instanceof JsonNull);
};
protoOf(AbstractJsonTreeDecoder).k10 = function (tag) {
  return !(this.j10(tag) === JsonNull_getInstance());
};
protoOf(AbstractJsonTreeDecoder).qt = function (tag) {
  return this.k10((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).l10 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.j10(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).v8();
      var tmp_0 = getKClassFromExpression(value).v8();
      var tmp$ret$3 = this.h10(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'boolean' + ' at element: ' + tmp$ret$3, toString_0(value));
    }
    var literal = value;
    try {
      var tmp0_elvis_lhs = get_booleanOrNull(literal);
      var tmp_1;
      if (tmp0_elvis_lhs == null) {
        unparsedPrimitive(this, literal, 'boolean', tag);
      } else {
        tmp_1 = tmp0_elvis_lhs;
      }
      tmp$ret$0 = tmp_1;
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        unparsedPrimitive(this, literal, 'boolean', tag);
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$0;
};
protoOf(AbstractJsonTreeDecoder).rt = function (tag) {
  return this.l10((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).m10 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.j10(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).v8();
      var tmp_0 = getKClassFromExpression(value).v8();
      var tmp$ret$3 = this.h10(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'byte' + ' at element: ' + tmp$ret$3, toString_0(value));
    }
    var literal = value;
    try {
      var result = get_int(literal);
      var tmp0_elvis_lhs = (-128 <= result ? result <= 127 : false) ? toByte(result) : null;
      var tmp_1;
      if (tmp0_elvis_lhs == null) {
        unparsedPrimitive(this, literal, 'byte', tag);
      } else {
        tmp_1 = tmp0_elvis_lhs;
      }
      tmp$ret$0 = tmp_1;
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        unparsedPrimitive(this, literal, 'byte', tag);
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$0;
};
protoOf(AbstractJsonTreeDecoder).st = function (tag) {
  return this.m10((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).n10 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.j10(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).v8();
      var tmp_0 = getKClassFromExpression(value).v8();
      var tmp$ret$3 = this.h10(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'short' + ' at element: ' + tmp$ret$3, toString_0(value));
    }
    var literal = value;
    try {
      var result = get_int(literal);
      var tmp0_elvis_lhs = (-32768 <= result ? result <= 32767 : false) ? toShort(result) : null;
      var tmp_1;
      if (tmp0_elvis_lhs == null) {
        unparsedPrimitive(this, literal, 'short', tag);
      } else {
        tmp_1 = tmp0_elvis_lhs;
      }
      tmp$ret$0 = tmp_1;
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        unparsedPrimitive(this, literal, 'short', tag);
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$0;
};
protoOf(AbstractJsonTreeDecoder).tt = function (tag) {
  return this.n10((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).o10 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.j10(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).v8();
      var tmp_0 = getKClassFromExpression(value).v8();
      var tmp$ret$3 = this.h10(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'int' + ' at element: ' + tmp$ret$3, toString_0(value));
    }
    var literal = value;
    try {
      var tmp0_elvis_lhs = get_int(literal);
      var tmp_1;
      if (tmp0_elvis_lhs == null) {
        unparsedPrimitive(this, literal, 'int', tag);
      } else {
        tmp_1 = tmp0_elvis_lhs;
      }
      tmp$ret$0 = tmp_1;
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        unparsedPrimitive(this, literal, 'int', tag);
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$0;
};
protoOf(AbstractJsonTreeDecoder).ut = function (tag) {
  return this.o10((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).p10 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.j10(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).v8();
      var tmp_0 = getKClassFromExpression(value).v8();
      var tmp$ret$3 = this.h10(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'long' + ' at element: ' + tmp$ret$3, toString_0(value));
    }
    var literal = value;
    try {
      var tmp0_elvis_lhs = get_long(literal);
      var tmp_1;
      if (tmp0_elvis_lhs == null) {
        unparsedPrimitive(this, literal, 'long', tag);
      } else {
        tmp_1 = tmp0_elvis_lhs;
      }
      tmp$ret$0 = tmp_1;
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        unparsedPrimitive(this, literal, 'long', tag);
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$0;
};
protoOf(AbstractJsonTreeDecoder).vt = function (tag) {
  return this.p10((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).q10 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.j10(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).v8();
      var tmp_0 = getKClassFromExpression(value).v8();
      var tmp$ret$3 = this.h10(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'float' + ' at element: ' + tmp$ret$3, toString_0(value));
    }
    var literal = value;
    try {
      var tmp0_elvis_lhs = get_float(literal);
      var tmp_1;
      if (tmp0_elvis_lhs == null) {
        unparsedPrimitive(this, literal, 'float', tag);
      } else {
        tmp_1 = tmp0_elvis_lhs;
      }
      tmp$ret$0 = tmp_1;
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        unparsedPrimitive(this, literal, 'float', tag);
      } else {
        throw $p;
      }
    }
  }
  var result = tmp$ret$0;
  var specialFp = this.cw().tu_1.vv_1;
  if (specialFp || isFinite(result))
    return result;
  throw InvalidFloatingPointDecoded(result, tag, toString_0(this.i10()));
};
protoOf(AbstractJsonTreeDecoder).wt = function (tag) {
  return this.q10((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).r10 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.j10(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).v8();
      var tmp_0 = getKClassFromExpression(value).v8();
      var tmp$ret$3 = this.h10(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'double' + ' at element: ' + tmp$ret$3, toString_0(value));
    }
    var literal = value;
    try {
      var tmp0_elvis_lhs = get_double(literal);
      var tmp_1;
      if (tmp0_elvis_lhs == null) {
        unparsedPrimitive(this, literal, 'double', tag);
      } else {
        tmp_1 = tmp0_elvis_lhs;
      }
      tmp$ret$0 = tmp_1;
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        unparsedPrimitive(this, literal, 'double', tag);
      } else {
        throw $p;
      }
    }
  }
  var result = tmp$ret$0;
  var specialFp = this.cw().tu_1.vv_1;
  if (specialFp || isFinite_0(result))
    return result;
  throw InvalidFloatingPointDecoded(result, tag, toString_0(this.i10()));
};
protoOf(AbstractJsonTreeDecoder).xt = function (tag) {
  return this.r10((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).s10 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.j10(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).v8();
      var tmp_0 = getKClassFromExpression(value).v8();
      var tmp$ret$3 = this.h10(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'char' + ' at element: ' + tmp$ret$3, toString_0(value));
    }
    var literal = value;
    try {
      var tmp0_elvis_lhs = new Char(single(literal.gw()));
      var tmp_1;
      if (tmp0_elvis_lhs == null) {
        unparsedPrimitive(this, literal, 'char', tag);
      } else {
        tmp_1 = tmp0_elvis_lhs;
      }
      tmp$ret$0 = tmp_1.a1_1;
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        unparsedPrimitive(this, literal, 'char', tag);
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$0;
};
protoOf(AbstractJsonTreeDecoder).yt = function (tag) {
  return this.s10((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).t10 = function (tag) {
  // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
  // Inline function 'kotlinx.serialization.json.internal.cast' call
  var value = this.j10(tag);
  if (!(value instanceof JsonPrimitive)) {
    var tmp = getKClass(JsonPrimitive).v8();
    var tmp_0 = getKClassFromExpression(value).v8();
    var tmp$ret$2 = this.h10(tag);
    throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'string' + ' at element: ' + tmp$ret$2, toString_0(value));
  }
  var value_0 = value;
  if (!(value_0 instanceof JsonLiteral))
    throw JsonDecodingException_0(-1, "Expected string value for a non-null key '" + tag + "', got null literal instead at element: " + this.h10(tag), toString_0(this.i10()));
  if (!value_0.lw_1 && !this.cw().tu_1.nv_1) {
    throw JsonDecodingException_0(-1, "String literal for key '" + tag + "' should be quoted at element: " + this.h10(tag) + ".\nUse 'isLenient = true' in 'Json {}' builder to accept non-compliant JSON.", toString_0(this.i10()));
  }
  return value_0.nw_1;
};
protoOf(AbstractJsonTreeDecoder).zt = function (tag) {
  return this.t10((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).u10 = function (tag, inlineDescriptor) {
  var tmp;
  if (get_isUnsignedNumber(inlineDescriptor)) {
    var tmp_0 = this.cw();
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    var tmp2 = this.j10(tag);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var serialName = inlineDescriptor.si();
    if (!(tmp2 instanceof JsonPrimitive)) {
      var tmp_1 = getKClass(JsonPrimitive).v8();
      var tmp_2 = getKClassFromExpression(tmp2).v8();
      var tmp$ret$3 = this.h10(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp_1 + ', but had ' + tmp_2 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$3, toString_0(tmp2));
    }
    var lexer = StringJsonLexer_0(tmp_0, tmp2.gw());
    tmp = new JsonDecoderForUnsignedTypes(lexer, this.cw());
  } else {
    tmp = protoOf(NamedValueDecoder).au.call(this, tag, inlineDescriptor);
  }
  return tmp;
};
protoOf(AbstractJsonTreeDecoder).au = function (tag, inlineDescriptor) {
  return this.u10((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE(), inlineDescriptor);
};
protoOf(AbstractJsonTreeDecoder).pk = function (descriptor) {
  return !(this.mt() == null) ? protoOf(NamedValueDecoder).pk.call(this, descriptor) : (new JsonPrimitiveDecoder(this.cw(), this.q1(), this.f10_1)).pk(descriptor);
};
function coerceInputValue_0($this, descriptor, index, tag) {
  var tmp0 = $this.cw();
  var tmp$ret$0;
  $l$block_2: {
    // Inline function 'kotlinx.serialization.json.internal.tryCoerceValue' call
    var isOptional = descriptor.cj(index);
    var elementDescriptor = descriptor.bj(index);
    var tmp;
    if (isOptional && !elementDescriptor.ui()) {
      var tmp_0 = $this.j10(tag);
      tmp = tmp_0 instanceof JsonNull;
    } else {
      tmp = false;
    }
    if (tmp) {
      tmp$ret$0 = true;
      break $l$block_2;
    }
    if (equals(elementDescriptor.ti(), ENUM_getInstance())) {
      var tmp_1;
      if (elementDescriptor.ui()) {
        var tmp_2 = $this.j10(tag);
        tmp_1 = tmp_2 instanceof JsonNull;
      } else {
        tmp_1 = false;
      }
      if (tmp_1) {
        tmp$ret$0 = false;
        break $l$block_2;
      }
      var tmp_3 = $this.j10(tag);
      var tmp0_safe_receiver = tmp_3 instanceof JsonPrimitive ? tmp_3 : null;
      var tmp0_elvis_lhs = tmp0_safe_receiver == null ? null : get_contentOrNull(tmp0_safe_receiver);
      var tmp_4;
      if (tmp0_elvis_lhs == null) {
        tmp$ret$0 = false;
        break $l$block_2;
      } else {
        tmp_4 = tmp0_elvis_lhs;
      }
      var enumValue = tmp_4;
      var enumIndex = getJsonNameIndex(elementDescriptor, tmp0, enumValue);
      var coerceToNull = !tmp0.tu_1.qv_1 && elementDescriptor.ui();
      if (enumIndex === -3 && (isOptional || coerceToNull)) {
        tmp$ret$0 = true;
        break $l$block_2;
      }
    }
    tmp$ret$0 = false;
  }
  return tmp$ret$0;
}
function absenceIsNull($this, descriptor, index) {
  $this.e11_1 = (!$this.cw().tu_1.qv_1 && !descriptor.cj(index) && descriptor.bj(index).ui());
  return $this.e11_1;
}
function JsonTreeDecoder(json, value, polymorphicDiscriminator, polyDescriptor) {
  polymorphicDiscriminator = polymorphicDiscriminator === VOID ? null : polymorphicDiscriminator;
  polyDescriptor = polyDescriptor === VOID ? null : polyDescriptor;
  AbstractJsonTreeDecoder.call(this, json, value, polymorphicDiscriminator);
  this.b11_1 = value;
  this.c11_1 = polyDescriptor;
  this.d11_1 = 0;
  this.e11_1 = false;
}
protoOf(JsonTreeDecoder).q1 = function () {
  return this.b11_1;
};
protoOf(JsonTreeDecoder).hl = function (descriptor) {
  while (this.d11_1 < descriptor.wi()) {
    var _unary__edvuaz = this.d11_1;
    this.d11_1 = _unary__edvuaz + 1 | 0;
    var name = this.ht(descriptor, _unary__edvuaz);
    var index = this.d11_1 - 1 | 0;
    this.e11_1 = false;
    var tmp;
    var tmp_0;
    // Inline function 'kotlin.collections.contains' call
    // Inline function 'kotlin.collections.containsKey' call
    var this_0 = this.q1();
    if ((isInterface(this_0, KtMap) ? this_0 : THROW_CCE()).r1(name)) {
      tmp_0 = true;
    } else {
      tmp_0 = absenceIsNull(this, descriptor, index);
    }
    if (tmp_0) {
      tmp = !this.g10_1.sv_1 || !coerceInputValue_0(this, descriptor, index, name);
    } else {
      tmp = false;
    }
    if (tmp) {
      return index;
    }
  }
  return -1;
};
protoOf(JsonTreeDecoder).ek = function () {
  return !this.e11_1 && protoOf(AbstractJsonTreeDecoder).ek.call(this);
};
protoOf(JsonTreeDecoder).it = function (descriptor, index) {
  var strategy = namingStrategy(descriptor, this.cw());
  var baseName = descriptor.yi(index);
  if (strategy == null) {
    if (!this.g10_1.wv_1)
      return baseName;
    if (this.q1().u1().m1(baseName))
      return baseName;
  }
  var deserializationNamesMap_0 = deserializationNamesMap(this.cw(), descriptor);
  // Inline function 'kotlin.collections.find' call
  var tmp0 = this.q1().u1();
  var tmp$ret$1;
  $l$block: {
    // Inline function 'kotlin.collections.firstOrNull' call
    var _iterator__ex2g4s = tmp0.h();
    while (_iterator__ex2g4s.i()) {
      var element = _iterator__ex2g4s.j();
      if (deserializationNamesMap_0.t1(element) === index) {
        tmp$ret$1 = element;
        break $l$block;
      }
    }
    tmp$ret$1 = null;
  }
  var tmp0_safe_receiver = tmp$ret$1;
  if (tmp0_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    return tmp0_safe_receiver;
  }
  var fallbackName = strategy == null ? null : strategy.jx(descriptor, index, baseName);
  return fallbackName == null ? baseName : fallbackName;
};
protoOf(JsonTreeDecoder).j10 = function (tag) {
  return getValue(this.q1(), tag);
};
protoOf(JsonTreeDecoder).sk = function (descriptor) {
  if (descriptor === this.c11_1) {
    var tmp = this.cw();
    var tmp2 = this.i10();
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var serialName = this.c11_1.si();
    if (!(tmp2 instanceof JsonObject)) {
      var tmp_0 = getKClass(JsonObject).v8();
      var tmp_1 = getKClassFromExpression(tmp2).v8();
      var tmp$ret$2 = this.ot();
      throw JsonDecodingException_0(-1, 'Expected ' + tmp_0 + ', but had ' + tmp_1 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$2, toString_0(tmp2));
    }
    return new JsonTreeDecoder(tmp, tmp2, this.f10_1, this.c11_1);
  }
  return protoOf(AbstractJsonTreeDecoder).sk.call(this, descriptor);
};
protoOf(JsonTreeDecoder).tk = function (descriptor) {
  var tmp;
  if (this.g10_1.mv_1) {
    tmp = true;
  } else {
    var tmp_0 = descriptor.ti();
    tmp = tmp_0 instanceof PolymorphicKind;
  }
  if (tmp)
    return Unit_instance;
  var strategy = namingStrategy(descriptor, this.cw());
  var tmp_1;
  if (strategy == null && !this.g10_1.wv_1) {
    tmp_1 = jsonCachedSerialNames(descriptor);
  } else if (!(strategy == null)) {
    tmp_1 = deserializationNamesMap(this.cw(), descriptor).u1();
  } else {
    var tmp_2 = jsonCachedSerialNames(descriptor);
    var tmp0_safe_receiver = get_schemaCache(this.cw()).iz(descriptor, get_JsonDeserializationNamesKey());
    // Inline function 'kotlin.collections.orEmpty' call
    var tmp0_elvis_lhs = tmp0_safe_receiver == null ? null : tmp0_safe_receiver.u1();
    var tmp$ret$0 = tmp0_elvis_lhs == null ? emptySet() : tmp0_elvis_lhs;
    tmp_1 = plus_0(tmp_2, tmp$ret$0);
  }
  var names = tmp_1;
  var _iterator__ex2g4s = this.q1().u1().h();
  while (_iterator__ex2g4s.i()) {
    var key = _iterator__ex2g4s.j();
    if (!names.m1(key) && !(key === this.f10_1)) {
      throw UnknownKeyException(key, this.q1().toString());
    }
  }
};
function JsonTreeListDecoder(json, value) {
  AbstractJsonTreeDecoder.call(this, json, value);
  this.l11_1 = value;
  this.m11_1 = this.l11_1.k();
  this.n11_1 = -1;
}
protoOf(JsonTreeListDecoder).q1 = function () {
  return this.l11_1;
};
protoOf(JsonTreeListDecoder).it = function (descriptor, index) {
  return index.toString();
};
protoOf(JsonTreeListDecoder).j10 = function (tag) {
  return this.l11_1.p(toInt(tag));
};
protoOf(JsonTreeListDecoder).hl = function (descriptor) {
  while (this.n11_1 < (this.m11_1 - 1 | 0)) {
    this.n11_1 = this.n11_1 + 1 | 0;
    return this.n11_1;
  }
  return -1;
};
function JsonPrimitiveDecoder(json, value, polymorphicDiscriminator) {
  polymorphicDiscriminator = polymorphicDiscriminator === VOID ? null : polymorphicDiscriminator;
  AbstractJsonTreeDecoder.call(this, json, value, polymorphicDiscriminator);
  this.u11_1 = value;
  this.bu('primitive');
}
protoOf(JsonPrimitiveDecoder).q1 = function () {
  return this.u11_1;
};
protoOf(JsonPrimitiveDecoder).hl = function (descriptor) {
  return 0;
};
protoOf(JsonPrimitiveDecoder).j10 = function (tag) {
  // Inline function 'kotlin.require' call
  if (!(tag === 'primitive')) {
    var message = "This input can only handle primitives with 'primitive' tag";
    throw IllegalArgumentException_init_$Create$(toString_0(message));
  }
  return this.u11_1;
};
function JsonTreeMapDecoder(json, value) {
  JsonTreeDecoder.call(this, json, value);
  this.f12_1 = value;
  this.g12_1 = toList(this.f12_1.u1());
  this.h12_1 = imul(this.g12_1.k(), 2);
  this.i12_1 = -1;
}
protoOf(JsonTreeMapDecoder).q1 = function () {
  return this.f12_1;
};
protoOf(JsonTreeMapDecoder).it = function (descriptor, index) {
  var i = index / 2 | 0;
  return this.g12_1.p(i);
};
protoOf(JsonTreeMapDecoder).hl = function (descriptor) {
  while (this.i12_1 < (this.h12_1 - 1 | 0)) {
    this.i12_1 = this.i12_1 + 1 | 0;
    return this.i12_1;
  }
  return -1;
};
protoOf(JsonTreeMapDecoder).j10 = function (tag) {
  return (this.i12_1 % 2 | 0) === 0 ? JsonPrimitive_0(tag) : getValue(this.f12_1, tag);
};
protoOf(JsonTreeMapDecoder).tk = function (descriptor) {
};
function readPolymorphicJson(_this__u8e3s4, discriminator, element, deserializer) {
  return (new JsonTreeDecoder(_this__u8e3s4, element, discriminator, deserializer.ki())).rk(deserializer);
}
var WriteMode_OBJ_instance;
var WriteMode_LIST_instance;
var WriteMode_MAP_instance;
var WriteMode_POLY_OBJ_instance;
var WriteMode_entriesInitialized;
function WriteMode_initEntries() {
  if (WriteMode_entriesInitialized)
    return Unit_instance;
  WriteMode_entriesInitialized = true;
  WriteMode_OBJ_instance = new WriteMode('OBJ', 0, _Char___init__impl__6a9atx(123), _Char___init__impl__6a9atx(125));
  WriteMode_LIST_instance = new WriteMode('LIST', 1, _Char___init__impl__6a9atx(91), _Char___init__impl__6a9atx(93));
  WriteMode_MAP_instance = new WriteMode('MAP', 2, _Char___init__impl__6a9atx(123), _Char___init__impl__6a9atx(125));
  WriteMode_POLY_OBJ_instance = new WriteMode('POLY_OBJ', 3, _Char___init__impl__6a9atx(91), _Char___init__impl__6a9atx(93));
}
function WriteMode(name, ordinal, begin, end) {
  Enum.call(this, name, ordinal);
  this.vz_1 = begin;
  this.wz_1 = end;
}
function switchMode(_this__u8e3s4, desc) {
  var tmp0_subject = desc.ti();
  var tmp;
  if (tmp0_subject instanceof PolymorphicKind) {
    tmp = WriteMode_POLY_OBJ_getInstance();
  } else {
    if (equals(tmp0_subject, LIST_getInstance())) {
      tmp = WriteMode_LIST_getInstance();
    } else {
      if (equals(tmp0_subject, MAP_getInstance())) {
        // Inline function 'kotlinx.serialization.json.internal.selectMapMode' call
        var keyDescriptor = carrierDescriptor(desc.bj(0), _this__u8e3s4.fl());
        var keyKind = keyDescriptor.ti();
        var tmp_0;
        var tmp_1;
        if (keyKind instanceof PrimitiveKind) {
          tmp_1 = true;
        } else {
          tmp_1 = equals(keyKind, ENUM_getInstance());
        }
        if (tmp_1) {
          tmp_0 = WriteMode_MAP_getInstance();
        } else {
          if (_this__u8e3s4.tu_1.ov_1) {
            tmp_0 = WriteMode_LIST_getInstance();
          } else {
            throw InvalidKeyKindException(keyDescriptor);
          }
        }
        tmp = tmp_0;
      } else {
        tmp = WriteMode_OBJ_getInstance();
      }
    }
  }
  return tmp;
}
function carrierDescriptor(_this__u8e3s4, module_0) {
  var tmp;
  if (equals(_this__u8e3s4.ti(), CONTEXTUAL_getInstance())) {
    var tmp0_safe_receiver = getContextualDescriptor(module_0, _this__u8e3s4);
    var tmp1_elvis_lhs = tmp0_safe_receiver == null ? null : carrierDescriptor(tmp0_safe_receiver, module_0);
    tmp = tmp1_elvis_lhs == null ? _this__u8e3s4 : tmp1_elvis_lhs;
  } else if (_this__u8e3s4.vi()) {
    tmp = carrierDescriptor(_this__u8e3s4.bj(0), module_0);
  } else {
    tmp = _this__u8e3s4;
  }
  return tmp;
}
function WriteMode_OBJ_getInstance() {
  WriteMode_initEntries();
  return WriteMode_OBJ_instance;
}
function WriteMode_LIST_getInstance() {
  WriteMode_initEntries();
  return WriteMode_LIST_instance;
}
function WriteMode_MAP_getInstance() {
  WriteMode_initEntries();
  return WriteMode_MAP_instance;
}
function WriteMode_POLY_OBJ_getInstance() {
  WriteMode_initEntries();
  return WriteMode_POLY_OBJ_instance;
}
function appendEscape($this, lastPosition, current) {
  $this.j12(lastPosition, current);
  return appendEsc($this, current + 1 | 0);
}
function decodedString($this, lastPosition, currentPosition) {
  $this.j12(lastPosition, currentPosition);
  var result = $this.jv_1.toString();
  $this.jv_1.ca(0);
  return result;
}
function takePeeked($this) {
  // Inline function 'kotlin.also' call
  var this_0 = ensureNotNull($this.iv_1);
  $this.iv_1 = null;
  return this_0;
}
function wasUnquotedString($this) {
  return !(charSequenceGet($this.k12(), $this.gv_1 - 1 | 0) === _Char___init__impl__6a9atx(34));
}
function appendEsc($this, startPosition) {
  var currentPosition = startPosition;
  currentPosition = $this.l12(currentPosition);
  if (currentPosition === -1) {
    $this.fx('Expected escape sequence to continue, got EOF');
  }
  var tmp = $this.k12();
  var _unary__edvuaz = currentPosition;
  currentPosition = _unary__edvuaz + 1 | 0;
  var currentChar = charSequenceGet(tmp, _unary__edvuaz);
  if (currentChar === _Char___init__impl__6a9atx(117)) {
    return appendHex($this, $this.k12(), currentPosition);
  }
  // Inline function 'kotlin.code' call
  var tmp$ret$0 = Char__toInt_impl_vasixd(currentChar);
  var c = escapeToChar(tmp$ret$0);
  if (c === _Char___init__impl__6a9atx(0)) {
    $this.fx("Invalid escaped char '" + toString_1(currentChar) + "'");
  }
  $this.jv_1.z6(c);
  return currentPosition;
}
function appendHex($this, source, startPos) {
  if ((startPos + 4 | 0) >= charSequenceLength(source)) {
    $this.gv_1 = startPos;
    $this.m12();
    if (($this.gv_1 + 4 | 0) >= charSequenceLength(source)) {
      $this.fx('Unexpected EOF during unicode escape');
    }
    return appendHex($this, source, $this.gv_1);
  }
  $this.jv_1.z6(numberToChar((((fromHexChar($this, source, startPos) << 12) + (fromHexChar($this, source, startPos + 1 | 0) << 8) | 0) + (fromHexChar($this, source, startPos + 2 | 0) << 4) | 0) + fromHexChar($this, source, startPos + 3 | 0) | 0));
  return startPos + 4 | 0;
}
function fromHexChar($this, source, currentPosition) {
  var character = charSequenceGet(source, currentPosition);
  var tmp;
  if (_Char___init__impl__6a9atx(48) <= character ? character <= _Char___init__impl__6a9atx(57) : false) {
    // Inline function 'kotlin.code' call
    var tmp_0 = Char__toInt_impl_vasixd(character);
    // Inline function 'kotlin.code' call
    var this_0 = _Char___init__impl__6a9atx(48);
    tmp = tmp_0 - Char__toInt_impl_vasixd(this_0) | 0;
  } else if (_Char___init__impl__6a9atx(97) <= character ? character <= _Char___init__impl__6a9atx(102) : false) {
    // Inline function 'kotlin.code' call
    var tmp_1 = Char__toInt_impl_vasixd(character);
    // Inline function 'kotlin.code' call
    var this_1 = _Char___init__impl__6a9atx(97);
    tmp = (tmp_1 - Char__toInt_impl_vasixd(this_1) | 0) + 10 | 0;
  } else if (_Char___init__impl__6a9atx(65) <= character ? character <= _Char___init__impl__6a9atx(70) : false) {
    // Inline function 'kotlin.code' call
    var tmp_2 = Char__toInt_impl_vasixd(character);
    // Inline function 'kotlin.code' call
    var this_2 = _Char___init__impl__6a9atx(65);
    tmp = (tmp_2 - Char__toInt_impl_vasixd(this_2) | 0) + 10 | 0;
  } else {
    $this.fx("Invalid toHexChar char '" + toString_1(character) + "' in unicode escape");
  }
  return tmp;
}
function consumeBoolean2($this, start) {
  var current = $this.l12(start);
  if (current >= charSequenceLength($this.k12()) || current === -1) {
    $this.fx('EOF');
  }
  var tmp = $this.k12();
  var _unary__edvuaz = current;
  current = _unary__edvuaz + 1 | 0;
  // Inline function 'kotlin.code' call
  var this_0 = charSequenceGet(tmp, _unary__edvuaz);
  var tmp0_subject = Char__toInt_impl_vasixd(this_0) | 32;
  var tmp_0;
  // Inline function 'kotlin.code' call
  var this_1 = _Char___init__impl__6a9atx(116);
  if (tmp0_subject === Char__toInt_impl_vasixd(this_1)) {
    consumeBooleanLiteral($this, 'rue', current);
    tmp_0 = true;
  } else {
    // Inline function 'kotlin.code' call
    var this_2 = _Char___init__impl__6a9atx(102);
    if (tmp0_subject === Char__toInt_impl_vasixd(this_2)) {
      consumeBooleanLiteral($this, 'alse', current);
      tmp_0 = false;
    } else {
      $this.fx("Expected valid boolean literal prefix, but had '" + $this.by() + "'");
    }
  }
  return tmp_0;
}
function consumeBooleanLiteral($this, literalSuffix, current) {
  if ((charSequenceLength($this.k12()) - current | 0) < literalSuffix.length) {
    $this.fx('Unexpected end of boolean literal');
  }
  var inductionVariable = 0;
  var last = charSequenceLength(literalSuffix) - 1 | 0;
  if (inductionVariable <= last)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      var expected = charCodeAt(literalSuffix, i);
      var actual = charSequenceGet($this.k12(), current + i | 0);
      // Inline function 'kotlin.code' call
      var tmp = Char__toInt_impl_vasixd(expected);
      // Inline function 'kotlin.code' call
      if (!(tmp === (Char__toInt_impl_vasixd(actual) | 32))) {
        $this.fx("Expected valid boolean literal prefix, but had '" + $this.by() + "'");
      }
    }
     while (inductionVariable <= last);
  $this.gv_1 = current + literalSuffix.length | 0;
}
function consumeNumericLiteral$calculateExponent(exponentAccumulator, isExponentPositive) {
  var tmp;
  switch (isExponentPositive) {
    case false:
      // Inline function 'kotlin.math.pow' call

      var x = -toNumber(exponentAccumulator);
      tmp = Math.pow(10.0, x);
      break;
    case true:
      // Inline function 'kotlin.math.pow' call

      var x_0 = toNumber(exponentAccumulator);
      tmp = Math.pow(10.0, x_0);
      break;
    default:
      noWhenBranchMatchedException();
      break;
  }
  return tmp;
}
function AbstractJsonLexer() {
  this.gv_1 = 0;
  this.hv_1 = new JsonPath();
  this.iv_1 = null;
  this.jv_1 = StringBuilder_init_$Create$();
}
protoOf(AbstractJsonLexer).m12 = function () {
};
protoOf(AbstractJsonLexer).lz = function () {
  var current = this.n12();
  var source = this.k12();
  if (current >= charSequenceLength(source) || current === -1)
    return false;
  if (charSequenceGet(source, current) === _Char___init__impl__6a9atx(44)) {
    this.gv_1 = this.gv_1 + 1 | 0;
    return true;
  }
  return false;
};
protoOf(AbstractJsonLexer).o12 = function (c) {
  return c === _Char___init__impl__6a9atx(125) || c === _Char___init__impl__6a9atx(93) || (c === _Char___init__impl__6a9atx(58) || c === _Char___init__impl__6a9atx(44)) ? false : true;
};
protoOf(AbstractJsonLexer).kv = function () {
  var nextToken = this.dy();
  if (!(nextToken === 10)) {
    this.fx('Expected EOF after parsing, but had ' + toString_1(charSequenceGet(this.k12(), this.gv_1 - 1 | 0)) + ' instead');
  }
};
protoOf(AbstractJsonLexer).xx = function (expected) {
  var token = this.dy();
  if (!(token === expected)) {
    this.p12(expected);
  }
  return token;
};
protoOf(AbstractJsonLexer).q12 = function (expected) {
  if (this.gv_1 > 0 && expected === _Char___init__impl__6a9atx(34)) {
    var tmp$ret$0;
    $l$block: {
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.withPositionRollback' call
      var snapshot = this.gv_1;
      try {
        this.gv_1 = this.gv_1 - 1 | 0;
        tmp$ret$0 = this.by();
        break $l$block;
      }finally {
        this.gv_1 = snapshot;
      }
    }
    var inputLiteral = tmp$ret$0;
    if (inputLiteral === 'null') {
      this.ex("Expected string literal but 'null' literal was found", this.gv_1 - 1 | 0, "Use 'coerceInputValues = true' in 'Json {}' builder to coerce nulls if property has a default value.");
    }
  }
  this.p12(charToTokenClass(expected));
};
protoOf(AbstractJsonLexer).r12 = function (expectedToken, wasConsumed) {
  var expected = tokenDescription(expectedToken);
  var position = wasConsumed ? this.gv_1 - 1 | 0 : this.gv_1;
  var s = this.gv_1 === charSequenceLength(this.k12()) || position < 0 ? 'EOF' : toString_1(charSequenceGet(this.k12(), position));
  this.fx('Expected ' + expected + ", but had '" + s + "' instead", position);
};
protoOf(AbstractJsonLexer).p12 = function (expectedToken, wasConsumed, $super) {
  wasConsumed = wasConsumed === VOID ? true : wasConsumed;
  return $super === VOID ? this.r12(expectedToken, wasConsumed) : $super.r12.call(this, expectedToken, wasConsumed);
};
protoOf(AbstractJsonLexer).yx = function () {
  var source = this.k12();
  var cpos = this.gv_1;
  $l$loop_0: while (true) {
    cpos = this.l12(cpos);
    if (cpos === -1)
      break $l$loop_0;
    var ch = charSequenceGet(source, cpos);
    if (ch === _Char___init__impl__6a9atx(32) || ch === _Char___init__impl__6a9atx(10) || ch === _Char___init__impl__6a9atx(13) || ch === _Char___init__impl__6a9atx(9)) {
      cpos = cpos + 1 | 0;
      continue $l$loop_0;
    }
    this.gv_1 = cpos;
    return charToTokenClass(ch);
  }
  this.gv_1 = cpos;
  return 10;
};
protoOf(AbstractJsonLexer).mz = function (doConsume) {
  var current = this.n12();
  current = this.l12(current);
  var len = charSequenceLength(this.k12()) - current | 0;
  if (len < 4 || current === -1)
    return false;
  var inductionVariable = 0;
  if (inductionVariable <= 3)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      if (!(charCodeAt('null', i) === charSequenceGet(this.k12(), current + i | 0)))
        return false;
    }
     while (inductionVariable <= 3);
  if (len > 4 && charToTokenClass(charSequenceGet(this.k12(), current + 4 | 0)) === 0)
    return false;
  if (doConsume) {
    this.gv_1 = current + 4 | 0;
  }
  return true;
};
protoOf(AbstractJsonLexer).xz = function (doConsume, $super) {
  doConsume = doConsume === VOID ? true : doConsume;
  return $super === VOID ? this.mz(doConsume) : $super.mz.call(this, doConsume);
};
protoOf(AbstractJsonLexer).nz = function (isLenient) {
  var token = this.yx();
  var tmp;
  if (isLenient) {
    if (!(token === 1) && !(token === 0))
      return null;
    tmp = this.by();
  } else {
    if (!(token === 1))
      return null;
    tmp = this.ay();
  }
  var string = tmp;
  this.iv_1 = string;
  return string;
};
protoOf(AbstractJsonLexer).s12 = function () {
  this.iv_1 = null;
};
protoOf(AbstractJsonLexer).t12 = function (startPos, endPos) {
  // Inline function 'kotlin.text.substring' call
  var this_0 = this.k12();
  return toString_0(charSequenceSubSequence(this_0, startPos, endPos));
};
protoOf(AbstractJsonLexer).ay = function () {
  if (!(this.iv_1 == null)) {
    return takePeeked(this);
  }
  return this.qz();
};
protoOf(AbstractJsonLexer).consumeString2 = function (source, startPosition, current) {
  var currentPosition = current;
  var lastPosition = startPosition;
  var char = charSequenceGet(source, currentPosition);
  var usedAppend = false;
  while (!(char === _Char___init__impl__6a9atx(34))) {
    if (char === _Char___init__impl__6a9atx(92)) {
      usedAppend = true;
      currentPosition = this.l12(appendEscape(this, lastPosition, currentPosition));
      if (currentPosition === -1) {
        this.fx('Unexpected EOF', currentPosition);
      }
      lastPosition = currentPosition;
    } else {
      currentPosition = currentPosition + 1 | 0;
      if (currentPosition >= charSequenceLength(source)) {
        usedAppend = true;
        this.j12(lastPosition, currentPosition);
        currentPosition = this.l12(currentPosition);
        if (currentPosition === -1) {
          this.fx('Unexpected EOF', currentPosition);
        }
        lastPosition = currentPosition;
      }
    }
    char = charSequenceGet(source, currentPosition);
  }
  var tmp;
  if (!usedAppend) {
    tmp = this.t12(lastPosition, currentPosition);
  } else {
    tmp = decodedString(this, lastPosition, currentPosition);
  }
  var string = tmp;
  this.gv_1 = currentPosition + 1 | 0;
  return string;
};
protoOf(AbstractJsonLexer).rz = function () {
  var result = this.by();
  if (result === 'null' && wasUnquotedString(this)) {
    this.fx("Unexpected 'null' value instead of string literal");
  }
  return result;
};
protoOf(AbstractJsonLexer).by = function () {
  if (!(this.iv_1 == null)) {
    return takePeeked(this);
  }
  var current = this.n12();
  if (current >= charSequenceLength(this.k12()) || current === -1) {
    this.fx('EOF', current);
  }
  var token = charToTokenClass(charSequenceGet(this.k12(), current));
  if (token === 1) {
    return this.ay();
  }
  if (!(token === 0)) {
    this.fx('Expected beginning of the string, but got ' + toString_1(charSequenceGet(this.k12(), current)));
  }
  var usedAppend = false;
  while (charToTokenClass(charSequenceGet(this.k12(), current)) === 0) {
    current = current + 1 | 0;
    if (current >= charSequenceLength(this.k12())) {
      usedAppend = true;
      this.j12(this.gv_1, current);
      var eof = this.l12(current);
      if (eof === -1) {
        this.gv_1 = current;
        return decodedString(this, 0, 0);
      } else {
        current = eof;
      }
    }
  }
  var tmp;
  if (!usedAppend) {
    tmp = this.t12(this.gv_1, current);
  } else {
    tmp = decodedString(this, this.gv_1, current);
  }
  var result = tmp;
  this.gv_1 = current;
  return result;
};
protoOf(AbstractJsonLexer).j12 = function (fromIndex, toIndex) {
  this.jv_1.x9(this.k12(), fromIndex, toIndex);
};
protoOf(AbstractJsonLexer).pz = function (allowLenientStrings) {
  // Inline function 'kotlin.collections.mutableListOf' call
  var tokenStack = ArrayList_init_$Create$();
  var lastToken = this.yx();
  if (!(lastToken === 8) && !(lastToken === 6)) {
    this.by();
    return Unit_instance;
  }
  $l$loop: while (true) {
    lastToken = this.yx();
    if (lastToken === 1) {
      if (allowLenientStrings)
        this.by();
      else
        this.qz();
      continue $l$loop;
    }
    var tmp0_subject = lastToken;
    if (tmp0_subject === 8 || tmp0_subject === 6) {
      tokenStack.e(lastToken);
    } else if (tmp0_subject === 9) {
      if (!(last(tokenStack) === 8))
        throw JsonDecodingException_0(this.gv_1, 'found ] instead of } at path: ' + this.hv_1.toString(), this.k12());
      removeLast(tokenStack);
    } else if (tmp0_subject === 7) {
      if (!(last(tokenStack) === 6))
        throw JsonDecodingException_0(this.gv_1, 'found } instead of ] at path: ' + this.hv_1.toString(), this.k12());
      removeLast(tokenStack);
    } else if (tmp0_subject === 10) {
      this.fx('Unexpected end of input due to malformed JSON during ignoring unknown keys');
    }
    this.dy();
    if (tokenStack.k() === 0)
      return Unit_instance;
  }
};
protoOf(AbstractJsonLexer).toString = function () {
  return "JsonReader(source='" + toString_0(this.k12()) + "', currentPosition=" + this.gv_1 + ')';
};
protoOf(AbstractJsonLexer).oz = function (key) {
  var processed = this.t12(0, this.gv_1);
  var lastIndexOf_0 = lastIndexOf(processed, key);
  this.ex("Encountered an unknown key '" + key + "'", lastIndexOf_0, "Use 'ignoreUnknownKeys = true' in 'Json {}' builder to ignore unknown keys.");
};
protoOf(AbstractJsonLexer).ex = function (message, position, hint) {
  var tmp;
  // Inline function 'kotlin.text.isEmpty' call
  if (charSequenceLength(hint) === 0) {
    tmp = '';
  } else {
    tmp = '\n' + hint;
  }
  var hintMessage = tmp;
  throw JsonDecodingException_0(position, message + ' at path: ' + this.hv_1.sx() + hintMessage, this.k12());
};
protoOf(AbstractJsonLexer).fx = function (message, position, hint, $super) {
  position = position === VOID ? this.gv_1 : position;
  hint = hint === VOID ? '' : hint;
  return $super === VOID ? this.ex(message, position, hint) : $super.ex.call(this, message, position, hint);
};
protoOf(AbstractJsonLexer).ow = function () {
  var current = this.n12();
  current = this.l12(current);
  if (current >= charSequenceLength(this.k12()) || current === -1) {
    this.fx('EOF');
  }
  var tmp;
  if (charSequenceGet(this.k12(), current) === _Char___init__impl__6a9atx(34)) {
    current = current + 1 | 0;
    if (current === charSequenceLength(this.k12())) {
      this.fx('EOF');
    }
    tmp = true;
  } else {
    tmp = false;
  }
  var hasQuotation = tmp;
  var accumulator = new Long(0, 0);
  var exponentAccumulator = new Long(0, 0);
  var isNegative = false;
  var isExponentPositive = false;
  var hasExponent = false;
  var start = current;
  $l$loop_4: while (!(current === charSequenceLength(this.k12()))) {
    var ch = charSequenceGet(this.k12(), current);
    if ((ch === _Char___init__impl__6a9atx(101) || ch === _Char___init__impl__6a9atx(69)) && !hasExponent) {
      if (current === start) {
        this.fx('Unexpected symbol ' + toString_1(ch) + ' in numeric literal');
      }
      isExponentPositive = true;
      hasExponent = true;
      current = current + 1 | 0;
      continue $l$loop_4;
    }
    if (ch === _Char___init__impl__6a9atx(45) && hasExponent) {
      if (current === start) {
        this.fx("Unexpected symbol '-' in numeric literal");
      }
      isExponentPositive = false;
      current = current + 1 | 0;
      continue $l$loop_4;
    }
    if (ch === _Char___init__impl__6a9atx(43) && hasExponent) {
      if (current === start) {
        this.fx("Unexpected symbol '+' in numeric literal");
      }
      isExponentPositive = true;
      current = current + 1 | 0;
      continue $l$loop_4;
    }
    if (ch === _Char___init__impl__6a9atx(45)) {
      if (!(current === start)) {
        this.fx("Unexpected symbol '-' in numeric literal");
      }
      isNegative = true;
      current = current + 1 | 0;
      continue $l$loop_4;
    }
    var token = charToTokenClass(ch);
    if (!(token === 0))
      break $l$loop_4;
    current = current + 1 | 0;
    var digit = Char__minus_impl_a2frrh(ch, _Char___init__impl__6a9atx(48));
    if (!(0 <= digit ? digit <= 9 : false)) {
      this.fx("Unexpected symbol '" + toString_1(ch) + "' in numeric literal");
    }
    if (hasExponent) {
      // Inline function 'kotlin.Long.times' call
      var this_0 = exponentAccumulator;
      // Inline function 'kotlin.Long.plus' call
      var this_1 = multiply(this_0, fromInt(10));
      exponentAccumulator = add(this_1, fromInt(digit));
      continue $l$loop_4;
    }
    // Inline function 'kotlin.Long.times' call
    var this_2 = accumulator;
    // Inline function 'kotlin.Long.minus' call
    var this_3 = multiply(this_2, fromInt(10));
    accumulator = subtract(this_3, fromInt(digit));
    if (compare(accumulator, new Long(0, 0)) > 0) {
      this.fx('Numeric value overflow');
    }
  }
  var hasChars = !(current === start);
  if (start === current || (isNegative && start === (current - 1 | 0))) {
    this.fx('Expected numeric literal');
  }
  if (hasQuotation) {
    if (!hasChars) {
      this.fx('EOF');
    }
    if (!(charSequenceGet(this.k12(), current) === _Char___init__impl__6a9atx(34))) {
      this.fx('Expected closing quotation mark');
    }
    current = current + 1 | 0;
  }
  this.gv_1 = current;
  if (hasExponent) {
    var doubleAccumulator = toNumber(accumulator) * consumeNumericLiteral$calculateExponent(exponentAccumulator, isExponentPositive);
    if (doubleAccumulator > toNumber(new Long(-1, 2147483647)) || doubleAccumulator < toNumber(new Long(0, -2147483648))) {
      this.fx('Numeric value overflow');
    }
    // Inline function 'kotlin.math.floor' call
    if (!(Math.floor(doubleAccumulator) === doubleAccumulator)) {
      this.fx("Can't convert " + doubleAccumulator + ' to Long');
    }
    accumulator = numberToLong(doubleAccumulator);
  }
  var tmp_0;
  if (isNegative) {
    tmp_0 = accumulator;
  } else if (!equalsLong(accumulator, new Long(0, -2147483648))) {
    tmp_0 = negate(accumulator);
  } else {
    this.fx('Numeric value overflow');
  }
  return tmp_0;
};
protoOf(AbstractJsonLexer).yz = function () {
  var current = this.n12();
  if (current === charSequenceLength(this.k12())) {
    this.fx('EOF');
  }
  var tmp;
  if (charSequenceGet(this.k12(), current) === _Char___init__impl__6a9atx(34)) {
    current = current + 1 | 0;
    tmp = true;
  } else {
    tmp = false;
  }
  var hasQuotation = tmp;
  var result = consumeBoolean2(this, current);
  if (hasQuotation) {
    if (this.gv_1 === charSequenceLength(this.k12())) {
      this.fx('EOF');
    }
    if (!(charSequenceGet(this.k12(), this.gv_1) === _Char___init__impl__6a9atx(34))) {
      this.fx('Expected closing quotation mark');
    }
    this.gv_1 = this.gv_1 + 1 | 0;
  }
  return result;
};
function charToTokenClass(c) {
  var tmp;
  // Inline function 'kotlin.code' call
  if (Char__toInt_impl_vasixd(c) < 126) {
    var tmp_0 = CharMappings_getInstance().v12_1;
    // Inline function 'kotlin.code' call
    tmp = tmp_0[Char__toInt_impl_vasixd(c)];
  } else {
    tmp = 0;
  }
  return tmp;
}
function tokenDescription(token) {
  return token === 1 ? "quotation mark '\"'" : token === 2 ? "string escape sequence '\\'" : token === 4 ? "comma ','" : token === 5 ? "colon ':'" : token === 6 ? "start of the object '{'" : token === 7 ? "end of the object '}'" : token === 8 ? "start of the array '['" : token === 9 ? "end of the array ']'" : token === 10 ? 'end of the input' : token === 127 ? 'invalid token' : 'valid token';
}
function escapeToChar(c) {
  return c < 117 ? CharMappings_getInstance().u12_1[c] : _Char___init__impl__6a9atx(0);
}
function initEscape($this) {
  var inductionVariable = 0;
  if (inductionVariable <= 31)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      initC2ESC($this, i, _Char___init__impl__6a9atx(117));
    }
     while (inductionVariable <= 31);
  initC2ESC($this, 8, _Char___init__impl__6a9atx(98));
  initC2ESC($this, 9, _Char___init__impl__6a9atx(116));
  initC2ESC($this, 10, _Char___init__impl__6a9atx(110));
  initC2ESC($this, 12, _Char___init__impl__6a9atx(102));
  initC2ESC($this, 13, _Char___init__impl__6a9atx(114));
  initC2ESC_0($this, _Char___init__impl__6a9atx(47), _Char___init__impl__6a9atx(47));
  initC2ESC_0($this, _Char___init__impl__6a9atx(34), _Char___init__impl__6a9atx(34));
  initC2ESC_0($this, _Char___init__impl__6a9atx(92), _Char___init__impl__6a9atx(92));
}
function initCharToToken($this) {
  var inductionVariable = 0;
  if (inductionVariable <= 32)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      initC2TC($this, i, 127);
    }
     while (inductionVariable <= 32);
  initC2TC($this, 9, 3);
  initC2TC($this, 10, 3);
  initC2TC($this, 13, 3);
  initC2TC($this, 32, 3);
  initC2TC_0($this, _Char___init__impl__6a9atx(44), 4);
  initC2TC_0($this, _Char___init__impl__6a9atx(58), 5);
  initC2TC_0($this, _Char___init__impl__6a9atx(123), 6);
  initC2TC_0($this, _Char___init__impl__6a9atx(125), 7);
  initC2TC_0($this, _Char___init__impl__6a9atx(91), 8);
  initC2TC_0($this, _Char___init__impl__6a9atx(93), 9);
  initC2TC_0($this, _Char___init__impl__6a9atx(34), 1);
  initC2TC_0($this, _Char___init__impl__6a9atx(92), 2);
}
function initC2ESC($this, c, esc) {
  if (!(esc === _Char___init__impl__6a9atx(117))) {
    // Inline function 'kotlin.code' call
    var tmp$ret$0 = Char__toInt_impl_vasixd(esc);
    $this.u12_1[tmp$ret$0] = numberToChar(c);
  }
}
function initC2ESC_0($this, c, esc) {
  // Inline function 'kotlin.code' call
  var tmp$ret$0 = Char__toInt_impl_vasixd(c);
  return initC2ESC($this, tmp$ret$0, esc);
}
function initC2TC($this, c, cl) {
  $this.v12_1[c] = cl;
}
function initC2TC_0($this, c, cl) {
  // Inline function 'kotlin.code' call
  var tmp$ret$0 = Char__toInt_impl_vasixd(c);
  return initC2TC($this, tmp$ret$0, cl);
}
function CharMappings() {
  CharMappings_instance = this;
  this.u12_1 = charArray(117);
  this.v12_1 = new Int8Array(126);
  initEscape(this);
  initCharToToken(this);
}
var CharMappings_instance;
function CharMappings_getInstance() {
  if (CharMappings_instance == null)
    new CharMappings();
  return CharMappings_instance;
}
function StringJsonLexerWithComments(source) {
  StringJsonLexer.call(this, source);
}
protoOf(StringJsonLexerWithComments).dy = function () {
  var source = this.k12();
  var cpos = this.n12();
  if (cpos >= source.length || cpos === -1)
    return 10;
  this.gv_1 = cpos + 1 | 0;
  return charToTokenClass(charCodeAt(source, cpos));
};
protoOf(StringJsonLexerWithComments).zx = function () {
  var current = this.n12();
  if (current >= this.k12().length || current === -1)
    return false;
  return this.o12(charCodeAt(this.k12(), current));
};
protoOf(StringJsonLexerWithComments).kz = function (expected) {
  var source = this.k12();
  var current = this.n12();
  if (current >= source.length || current === -1) {
    this.gv_1 = -1;
    this.q12(expected);
  }
  var c = charCodeAt(source, current);
  this.gv_1 = current + 1 | 0;
  if (c === expected)
    return Unit_instance;
  else {
    this.q12(expected);
  }
};
protoOf(StringJsonLexerWithComments).yx = function () {
  var source = this.k12();
  var cpos = this.n12();
  if (cpos >= source.length || cpos === -1)
    return 10;
  this.gv_1 = cpos;
  return charToTokenClass(charCodeAt(source, cpos));
};
protoOf(StringJsonLexerWithComments).n12 = function () {
  var current = this.gv_1;
  if (current === -1)
    return current;
  var source = this.k12();
  $l$loop_1: while (current < source.length) {
    var c = charCodeAt(source, current);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.isWs' call
    if (c === _Char___init__impl__6a9atx(32) || c === _Char___init__impl__6a9atx(10) || c === _Char___init__impl__6a9atx(13) || c === _Char___init__impl__6a9atx(9)) {
      current = current + 1 | 0;
      continue $l$loop_1;
    }
    if (c === _Char___init__impl__6a9atx(47) && (current + 1 | 0) < source.length) {
      var tmp0_subject = charCodeAt(source, current + 1 | 0);
      if (tmp0_subject === _Char___init__impl__6a9atx(47)) {
        current = indexOf_0(source, _Char___init__impl__6a9atx(10), current + 2 | 0);
        if (current === -1) {
          current = source.length;
        } else {
          current = current + 1 | 0;
        }
        continue $l$loop_1;
      } else if (tmp0_subject === _Char___init__impl__6a9atx(42)) {
        current = indexOf(source, '*/', current + 2 | 0);
        if (current === -1) {
          this.gv_1 = source.length;
          this.fx('Expected end of the block comment: "*/", but had EOF instead');
        } else {
          current = current + 2 | 0;
        }
        continue $l$loop_1;
      }
    }
    break $l$loop_1;
  }
  this.gv_1 = current;
  return current;
};
function StringJsonLexer(source) {
  AbstractJsonLexer.call(this);
  this.f13_1 = source;
}
protoOf(StringJsonLexer).k12 = function () {
  return this.f13_1;
};
protoOf(StringJsonLexer).l12 = function (position) {
  return position < this.k12().length ? position : -1;
};
protoOf(StringJsonLexer).dy = function () {
  var source = this.k12();
  var cpos = this.gv_1;
  $l$loop: while (!(cpos === -1) && cpos < source.length) {
    var _unary__edvuaz = cpos;
    cpos = _unary__edvuaz + 1 | 0;
    var c = charCodeAt(source, _unary__edvuaz);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.isWs' call
    if (c === _Char___init__impl__6a9atx(32) || c === _Char___init__impl__6a9atx(10) || c === _Char___init__impl__6a9atx(13) || c === _Char___init__impl__6a9atx(9))
      continue $l$loop;
    this.gv_1 = cpos;
    return charToTokenClass(c);
  }
  this.gv_1 = source.length;
  return 10;
};
protoOf(StringJsonLexer).zx = function () {
  var current = this.gv_1;
  if (current === -1)
    return false;
  var source = this.k12();
  $l$loop: while (current < source.length) {
    var c = charCodeAt(source, current);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.isWs' call
    if (c === _Char___init__impl__6a9atx(32) || c === _Char___init__impl__6a9atx(10) || c === _Char___init__impl__6a9atx(13) || c === _Char___init__impl__6a9atx(9)) {
      current = current + 1 | 0;
      continue $l$loop;
    }
    this.gv_1 = current;
    return this.o12(c);
  }
  this.gv_1 = current;
  return false;
};
protoOf(StringJsonLexer).n12 = function () {
  var current = this.gv_1;
  if (current === -1)
    return current;
  var source = this.k12();
  $l$loop: while (current < source.length) {
    var c = charCodeAt(source, current);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.isWs' call
    if (c === _Char___init__impl__6a9atx(32) || c === _Char___init__impl__6a9atx(10) || c === _Char___init__impl__6a9atx(13) || c === _Char___init__impl__6a9atx(9)) {
      current = current + 1 | 0;
    } else {
      break $l$loop;
    }
  }
  this.gv_1 = current;
  return current;
};
protoOf(StringJsonLexer).kz = function (expected) {
  if (this.gv_1 === -1) {
    this.q12(expected);
  }
  var source = this.k12();
  var cpos = this.gv_1;
  $l$loop: while (cpos < source.length) {
    var _unary__edvuaz = cpos;
    cpos = _unary__edvuaz + 1 | 0;
    var c = charCodeAt(source, _unary__edvuaz);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.isWs' call
    if (c === _Char___init__impl__6a9atx(32) || c === _Char___init__impl__6a9atx(10) || c === _Char___init__impl__6a9atx(13) || c === _Char___init__impl__6a9atx(9))
      continue $l$loop;
    this.gv_1 = cpos;
    if (c === expected)
      return Unit_instance;
    this.q12(expected);
  }
  this.gv_1 = -1;
  this.q12(expected);
};
protoOf(StringJsonLexer).qz = function () {
  this.kz(_Char___init__impl__6a9atx(34));
  var current = this.gv_1;
  var closingQuote = indexOf_0(this.k12(), _Char___init__impl__6a9atx(34), current);
  if (closingQuote === -1) {
    this.by();
    this.r12(1, false);
  }
  var inductionVariable = current;
  if (inductionVariable < closingQuote)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      if (charCodeAt(this.k12(), i) === _Char___init__impl__6a9atx(92)) {
        return this.consumeString2(this.k12(), this.gv_1, i);
      }
    }
     while (inductionVariable < closingQuote);
  this.gv_1 = closingQuote + 1 | 0;
  return substring(this.k12(), current, closingQuote);
};
protoOf(StringJsonLexer).sz = function (keyToMatch, isLenient) {
  var positionSnapshot = this.gv_1;
  try {
    if (!(this.dy() === 6))
      return null;
    var firstKey = this.nz(isLenient);
    if (!(firstKey === keyToMatch))
      return null;
    this.s12();
    if (!(this.dy() === 5))
      return null;
    return this.nz(isLenient);
  }finally {
    this.gv_1 = positionSnapshot;
    this.s12();
  }
};
function StringJsonLexer_0(json, source) {
  return !json.tu_1.aw_1 ? new StringJsonLexer(source) : new StringJsonLexerWithComments(source);
}
function get_schemaCache(_this__u8e3s4) {
  return _this__u8e3s4.vu_1;
}
function createMapForCache(initialCapacity) {
  return HashMap_init_$Create$(initialCapacity);
}
//region block: post-declaration
protoOf(defer$1).ui = get_isNullable;
protoOf(defer$1).vi = get_isInline;
protoOf(defer$1).xi = get_annotations;
//endregion
//region block: init
Companion_instance = new Companion();
Companion_instance_0 = new Companion_0();
Companion_instance_1 = new Companion_1();
Companion_instance_2 = new Companion_2();
Tombstone_instance = new Tombstone();
//endregion
//region block: exports
export {
  Default_getInstance as Default_getInstancejyc493d440j2,
  JsonArray as JsonArray2urf8ey7u44sd,
  JsonObject as JsonObjectee06ihoeeiqj,
  JsonPrimitive as JsonPrimitive3ttzjh2ft5dnx,
  get_contentOrNull as get_contentOrNull35s97cgi8z9eo,
};
//endregion


