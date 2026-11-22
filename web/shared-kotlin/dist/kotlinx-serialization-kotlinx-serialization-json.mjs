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
  ListSerializer1hxuk9dx5n9du as ListSerializer,
  STRING_getInstance3lkmlozu5h7cs as STRING_getInstance,
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
initMetadataForClass(JsonArray, 'JsonArray', VOID, JsonElement, [KtList], VOID, VOID, {0: JsonArraySerializer_getInstance});
initMetadataForCompanion(Companion_2);
initMetadataForClass(JsonPrimitive, 'JsonPrimitive', VOID, JsonElement, VOID, VOID, VOID, {0: JsonPrimitiveSerializer_getInstance});
initMetadataForObject(JsonNull, 'JsonNull', VOID, JsonPrimitive, VOID, VOID, VOID, {0: JsonNullSerializer_getInstance});
initMetadataForClass(JsonLiteral, 'JsonLiteral', VOID, JsonPrimitive);
initMetadataForObject(JsonObjectDescriptor, 'JsonObjectDescriptor', VOID, VOID, [SerialDescriptor]);
initMetadataForObject(JsonObjectSerializer, 'JsonObjectSerializer', VOID, VOID, [KSerializer]);
initMetadataForObject(JsonElementSerializer, 'JsonElementSerializer', VOID, VOID, [KSerializer]);
initMetadataForObject(JsonArrayDescriptor, 'JsonArrayDescriptor', VOID, VOID, [SerialDescriptor]);
initMetadataForObject(JsonArraySerializer, 'JsonArraySerializer', VOID, VOID, [KSerializer]);
initMetadataForObject(JsonPrimitiveSerializer, 'JsonPrimitiveSerializer', VOID, VOID, [KSerializer]);
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
  this.ps_1 = configuration;
  this.qs_1 = serializersModule;
  this.rs_1 = new DescriptorSchemaCache();
}
protoOf(Json).bj = function () {
  return this.qs_1;
};
protoOf(Json).ss = function (deserializer, string) {
  var lexer = StringJsonLexer_0(this, string);
  var input = new StreamingJsonDecoder(this, WriteMode_OBJ_getInstance(), lexer, deserializer.gg(), null);
  var result = input.ni(deserializer);
  lexer.gt();
  return result;
};
protoOf(Json).ts = function (string) {
  return this.ss(JsonElementSerializer_getInstance(), string);
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
  this.ht_1 = encodeDefaults;
  this.it_1 = ignoreUnknownKeys;
  this.jt_1 = isLenient;
  this.kt_1 = allowStructuredMapKeys;
  this.lt_1 = prettyPrint;
  this.mt_1 = explicitNulls;
  this.nt_1 = prettyPrintIndent;
  this.ot_1 = coerceInputValues;
  this.pt_1 = useArrayPolymorphism;
  this.qt_1 = classDiscriminator;
  this.rt_1 = allowSpecialFloatingPointValues;
  this.st_1 = useAlternativeNames;
  this.tt_1 = namingStrategy;
  this.ut_1 = decodeEnumsCaseInsensitive;
  this.vt_1 = allowTrailingComma;
  this.wt_1 = allowComments;
  this.xt_1 = classDiscriminatorMode;
}
protoOf(JsonConfiguration).toString = function () {
  return 'JsonConfiguration(encodeDefaults=' + this.ht_1 + ', ignoreUnknownKeys=' + this.it_1 + ', isLenient=' + this.jt_1 + ', ' + ('allowStructuredMapKeys=' + this.kt_1 + ', prettyPrint=' + this.lt_1 + ', explicitNulls=' + this.mt_1 + ', ') + ("prettyPrintIndent='" + this.nt_1 + "', coerceInputValues=" + this.ot_1 + ', useArrayPolymorphism=' + this.pt_1 + ', ') + ("classDiscriminator='" + this.qt_1 + "', allowSpecialFloatingPointValues=" + this.rt_1 + ', ') + ('useAlternativeNames=' + this.st_1 + ', namingStrategy=' + toString(this.tt_1) + ', decodeEnumsCaseInsensitive=' + this.ut_1 + ', ') + ('allowTrailingComma=' + this.vt_1 + ', allowComments=' + this.wt_1 + ', classDiscriminatorMode=' + this.xt_1.toString() + ')');
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
  var k = _destruct__k2r9zo.l1();
  // Inline function 'kotlin.collections.component2' call
  var v = _destruct__k2r9zo.m1();
  // Inline function 'kotlin.text.buildString' call
  // Inline function 'kotlin.apply' call
  var this_0 = StringBuilder_init_$Create$();
  printQuoted(this_0, k);
  this_0.x6(_Char___init__impl__6a9atx(58));
  this_0.v6(v);
  return this_0.toString();
}
function JsonObject(content) {
  JsonElement.call(this);
  this.au_1 = content;
}
protoOf(JsonObject).equals = function (other) {
  return equals(this.au_1, other);
};
protoOf(JsonObject).hashCode = function () {
  return hashCode(this.au_1);
};
protoOf(JsonObject).toString = function () {
  var tmp = this.au_1.s1();
  return joinToString(tmp, ',', '{', '}', VOID, VOID, JsonObject$toString$lambda);
};
protoOf(JsonObject).bu = function (key) {
  return this.au_1.n1(key);
};
protoOf(JsonObject).n1 = function (key) {
  if (!(!(key == null) ? typeof key === 'string' : false))
    return false;
  return this.bu((!(key == null) ? typeof key === 'string' : false) ? key : THROW_CCE());
};
protoOf(JsonObject).ka = function (key) {
  return this.au_1.p1(key);
};
protoOf(JsonObject).p1 = function (key) {
  if (!(!(key == null) ? typeof key === 'string' : false))
    return null;
  return this.ka((!(key == null) ? typeof key === 'string' : false) ? key : THROW_CCE());
};
protoOf(JsonObject).l = function () {
  return this.au_1.l();
};
protoOf(JsonObject).s1 = function () {
  return this.au_1.s1();
};
protoOf(JsonObject).q1 = function () {
  return this.au_1.q1();
};
protoOf(JsonObject).j = function () {
  return this.au_1.j();
};
protoOf(JsonObject).r1 = function () {
  return this.au_1.r1();
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
function JsonArray(content) {
  JsonElement.call(this);
  this.cu_1 = content;
}
protoOf(JsonArray).equals = function (other) {
  return equals(this.cu_1, other);
};
protoOf(JsonArray).hashCode = function () {
  return hashCode(this.cu_1);
};
protoOf(JsonArray).toString = function () {
  return joinToString(this.cu_1, ',', '[', ']');
};
protoOf(JsonArray).du = function (element) {
  return this.cu_1.j1(element);
};
protoOf(JsonArray).j1 = function (element) {
  if (!(element instanceof JsonElement))
    return false;
  return this.du(element instanceof JsonElement ? element : THROW_CCE());
};
protoOf(JsonArray).k = function (index) {
  return this.cu_1.k(index);
};
protoOf(JsonArray).l = function () {
  return this.cu_1.l();
};
protoOf(JsonArray).g = function () {
  return this.cu_1.g();
};
protoOf(JsonArray).j = function () {
  return this.cu_1.j();
};
function Companion_2() {
}
var Companion_instance_2;
function Companion_getInstance_6() {
  return Companion_instance_2;
}
function JsonPrimitive() {
  JsonElement.call(this);
}
protoOf(JsonPrimitive).toString = function () {
  return this.eu();
};
function get_contentOrNull(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  var tmp;
  if (_this__u8e3s4 instanceof JsonNull) {
    tmp = null;
  } else {
    tmp = _this__u8e3s4.eu();
  }
  return tmp;
}
function JsonNull() {
  JsonNull_instance = this;
  JsonPrimitive.call(this);
  this.fu_1 = 'null';
}
protoOf(JsonNull).eu = function () {
  return this.fu_1;
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
  this.gu_1 = isString;
  this.hu_1 = coerceToInlineType;
  this.iu_1 = toString_0(body);
  if (!(this.hu_1 == null)) {
    // Inline function 'kotlin.require' call
    // Inline function 'kotlin.require' call
    if (!this.hu_1.rg()) {
      var message = 'Failed requirement.';
      throw IllegalArgumentException_init_$Create$(toString_0(message));
    }
  }
}
protoOf(JsonLiteral).eu = function () {
  return this.iu_1;
};
protoOf(JsonLiteral).toString = function () {
  var tmp;
  if (this.gu_1) {
    // Inline function 'kotlin.text.buildString' call
    // Inline function 'kotlin.apply' call
    var this_0 = StringBuilder_init_$Create$();
    printQuoted(this_0, this.iu_1);
    tmp = this_0.toString();
  } else {
    tmp = this.iu_1;
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
  if (!(this.gu_1 === other.gu_1))
    return false;
  if (!(this.iu_1 === other.iu_1))
    return false;
  return true;
};
protoOf(JsonLiteral).hashCode = function () {
  var result = getBooleanHashCode(this.gu_1);
  result = imul(31, result) + getStringHashCode(this.iu_1) | 0;
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
  return toBooleanStrictOrNull(_this__u8e3s4.eu());
}
function get_int(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  // Inline function 'kotlinx.serialization.json.mapExceptions' call
  var tmp;
  try {
    tmp = (new StringJsonLexer(_this__u8e3s4.eu())).ju();
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
    throw NumberFormatException_init_$Create$(_this__u8e3s4.eu() + ' is not an Int');
  return convertToInt(result);
}
function get_long(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  // Inline function 'kotlinx.serialization.json.mapExceptions' call
  var tmp;
  try {
    tmp = (new StringJsonLexer(_this__u8e3s4.eu())).ju();
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
  var this_0 = _this__u8e3s4.eu();
  // Inline function 'kotlin.js.unsafeCast' call
  // Inline function 'kotlin.js.asDynamic' call
  return toDouble(this_0);
}
function get_double(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  return toDouble(_this__u8e3s4.eu());
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
  this.ku_1 = MapSerializer(serializer(StringCompanionObject_instance), JsonElementSerializer_getInstance()).gg();
  this.lu_1 = 'kotlinx.serialization.json.JsonObject';
}
protoOf(JsonObjectDescriptor).og = function () {
  return this.lu_1;
};
protoOf(JsonObjectDescriptor).ug = function (index) {
  return this.ku_1.ug(index);
};
protoOf(JsonObjectDescriptor).vg = function (name) {
  return this.ku_1.vg(name);
};
protoOf(JsonObjectDescriptor).wg = function (index) {
  return this.ku_1.wg(index);
};
protoOf(JsonObjectDescriptor).xg = function (index) {
  return this.ku_1.xg(index);
};
protoOf(JsonObjectDescriptor).yg = function (index) {
  return this.ku_1.yg(index);
};
protoOf(JsonObjectDescriptor).pg = function () {
  return this.ku_1.pg();
};
protoOf(JsonObjectDescriptor).qg = function () {
  return this.ku_1.qg();
};
protoOf(JsonObjectDescriptor).rg = function () {
  return this.ku_1.rg();
};
protoOf(JsonObjectDescriptor).sg = function () {
  return this.ku_1.sg();
};
protoOf(JsonObjectDescriptor).tg = function () {
  return this.ku_1.tg();
};
var JsonObjectDescriptor_instance;
function JsonObjectDescriptor_getInstance() {
  if (JsonObjectDescriptor_instance == null)
    new JsonObjectDescriptor();
  return JsonObjectDescriptor_instance;
}
function JsonObjectSerializer() {
  JsonObjectSerializer_instance = this;
  this.mu_1 = JsonObjectDescriptor_getInstance();
}
protoOf(JsonObjectSerializer).gg = function () {
  return this.mu_1;
};
protoOf(JsonObjectSerializer).hg = function (decoder) {
  verify(decoder);
  return new JsonObject(MapSerializer(serializer(StringCompanionObject_instance), JsonElementSerializer_getInstance()).hg(decoder));
};
var JsonObjectSerializer_instance;
function JsonObjectSerializer_getInstance() {
  if (JsonObjectSerializer_instance == null)
    new JsonObjectSerializer();
  return JsonObjectSerializer_instance;
}
function JsonElementSerializer$descriptor$lambda($this$buildSerialDescriptor) {
  $this$buildSerialDescriptor.lh('JsonPrimitive', defer(JsonElementSerializer$descriptor$lambda$lambda));
  $this$buildSerialDescriptor.lh('JsonNull', defer(JsonElementSerializer$descriptor$lambda$lambda_0));
  $this$buildSerialDescriptor.lh('JsonLiteral', defer(JsonElementSerializer$descriptor$lambda$lambda_1));
  $this$buildSerialDescriptor.lh('JsonObject', defer(JsonElementSerializer$descriptor$lambda$lambda_2));
  $this$buildSerialDescriptor.lh('JsonArray', defer(JsonElementSerializer$descriptor$lambda$lambda_3));
  return Unit_instance;
}
function JsonElementSerializer$descriptor$lambda$lambda() {
  return JsonPrimitiveSerializer_getInstance().nu_1;
}
function JsonElementSerializer$descriptor$lambda$lambda_0() {
  return JsonNullSerializer_getInstance().ou_1;
}
function JsonElementSerializer$descriptor$lambda$lambda_1() {
  return JsonLiteralSerializer_getInstance().pu_1;
}
function JsonElementSerializer$descriptor$lambda$lambda_2() {
  return JsonObjectSerializer_getInstance().mu_1;
}
function JsonElementSerializer$descriptor$lambda$lambda_3() {
  return JsonArraySerializer_getInstance().qu_1;
}
function JsonElementSerializer() {
  JsonElementSerializer_instance = this;
  var tmp = this;
  var tmp_0 = SEALED_getInstance();
  tmp.ru_1 = buildSerialDescriptor('kotlinx.serialization.json.JsonElement', tmp_0, [], JsonElementSerializer$descriptor$lambda);
}
protoOf(JsonElementSerializer).gg = function () {
  return this.ru_1;
};
protoOf(JsonElementSerializer).hg = function (decoder) {
  var input = asJsonDecoder(decoder);
  return input.zt();
};
var JsonElementSerializer_instance;
function JsonElementSerializer_getInstance() {
  if (JsonElementSerializer_instance == null)
    new JsonElementSerializer();
  return JsonElementSerializer_instance;
}
function JsonArrayDescriptor() {
  JsonArrayDescriptor_instance = this;
  this.su_1 = ListSerializer(JsonElementSerializer_getInstance()).gg();
  this.tu_1 = 'kotlinx.serialization.json.JsonArray';
}
protoOf(JsonArrayDescriptor).og = function () {
  return this.tu_1;
};
protoOf(JsonArrayDescriptor).ug = function (index) {
  return this.su_1.ug(index);
};
protoOf(JsonArrayDescriptor).vg = function (name) {
  return this.su_1.vg(name);
};
protoOf(JsonArrayDescriptor).wg = function (index) {
  return this.su_1.wg(index);
};
protoOf(JsonArrayDescriptor).xg = function (index) {
  return this.su_1.xg(index);
};
protoOf(JsonArrayDescriptor).yg = function (index) {
  return this.su_1.yg(index);
};
protoOf(JsonArrayDescriptor).pg = function () {
  return this.su_1.pg();
};
protoOf(JsonArrayDescriptor).qg = function () {
  return this.su_1.qg();
};
protoOf(JsonArrayDescriptor).rg = function () {
  return this.su_1.rg();
};
protoOf(JsonArrayDescriptor).sg = function () {
  return this.su_1.sg();
};
protoOf(JsonArrayDescriptor).tg = function () {
  return this.su_1.tg();
};
var JsonArrayDescriptor_instance;
function JsonArrayDescriptor_getInstance() {
  if (JsonArrayDescriptor_instance == null)
    new JsonArrayDescriptor();
  return JsonArrayDescriptor_instance;
}
function JsonArraySerializer() {
  JsonArraySerializer_instance = this;
  this.qu_1 = JsonArrayDescriptor_getInstance();
}
protoOf(JsonArraySerializer).gg = function () {
  return this.qu_1;
};
protoOf(JsonArraySerializer).hg = function (decoder) {
  verify(decoder);
  return new JsonArray(ListSerializer(JsonElementSerializer_getInstance()).hg(decoder));
};
var JsonArraySerializer_instance;
function JsonArraySerializer_getInstance() {
  if (JsonArraySerializer_instance == null)
    new JsonArraySerializer();
  return JsonArraySerializer_instance;
}
function JsonPrimitiveSerializer() {
  JsonPrimitiveSerializer_instance = this;
  this.nu_1 = buildSerialDescriptor('kotlinx.serialization.json.JsonPrimitive', STRING_getInstance(), []);
}
protoOf(JsonPrimitiveSerializer).gg = function () {
  return this.nu_1;
};
protoOf(JsonPrimitiveSerializer).hg = function (decoder) {
  var result = asJsonDecoder(decoder).zt();
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
function JsonNullSerializer() {
  JsonNullSerializer_instance = this;
  this.ou_1 = buildSerialDescriptor('kotlinx.serialization.json.JsonNull', ENUM_getInstance(), []);
}
protoOf(JsonNullSerializer).gg = function () {
  return this.ou_1;
};
protoOf(JsonNullSerializer).hg = function (decoder) {
  verify(decoder);
  if (decoder.ai()) {
    throw new JsonDecodingException("Expected 'null' literal");
  }
  decoder.bi();
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
  this.pu_1 = PrimitiveSerialDescriptor('kotlinx.serialization.json.JsonLiteral', STRING_getInstance());
}
protoOf(JsonLiteralSerializer).gg = function () {
  return this.pu_1;
};
protoOf(JsonLiteralSerializer).hg = function (decoder) {
  var result = asJsonDecoder(decoder).zt();
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
  var tmp0 = $this.uu_1;
  var tmp = KProperty1;
  // Inline function 'kotlin.getValue' call
  getPropertyCallableRef('original', 1, tmp, defer$o$_get_original_$ref_3cje7k(), null);
  return tmp0.m1();
}
function defer$o$_get_original_$ref_3cje7k() {
  return function (p0) {
    return _get_original__l7ku1m(p0);
  };
}
function defer$1($deferred) {
  this.uu_1 = lazy($deferred);
}
protoOf(defer$1).og = function () {
  return _get_original__l7ku1m(this).og();
};
protoOf(defer$1).pg = function () {
  return _get_original__l7ku1m(this).pg();
};
protoOf(defer$1).sg = function () {
  return _get_original__l7ku1m(this).sg();
};
protoOf(defer$1).ug = function (index) {
  return _get_original__l7ku1m(this).ug(index);
};
protoOf(defer$1).vg = function (name) {
  return _get_original__l7ku1m(this).vg(name);
};
protoOf(defer$1).wg = function (index) {
  return _get_original__l7ku1m(this).wg(index);
};
protoOf(defer$1).xg = function (index) {
  return _get_original__l7ku1m(this).xg(index);
};
protoOf(defer$1).yg = function (index) {
  return _get_original__l7ku1m(this).yg(index);
};
function readIfAbsent($this, descriptor, index) {
  $this.wu_1 = (!descriptor.yg(index) && descriptor.xg(index).qg());
  return $this.wu_1;
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
  tmp.vu_1 = new ElementMarker(descriptor, JsonElementMarker$readIfAbsent$ref(this));
  this.wu_1 = false;
}
protoOf(JsonElementMarker).xu = function (index) {
  this.vu_1.pl(index);
};
protoOf(JsonElementMarker).yu = function () {
  return this.vu_1.ql();
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
  _this__u8e3s4.zu('Trailing comma before the end of JSON ' + entity, _this__u8e3s4.ct_1 - 1 | 0, "Trailing commas are non-complaint JSON and not allowed by default. Use 'allowTrailingCommas = true' in 'Json {}' builder to support them.");
}
function throwInvalidFloatingPointDecoded(_this__u8e3s4, result) {
  _this__u8e3s4.av('Unexpected special floating-point value ' + toString_0(result) + '. By default, ' + 'non-finite floating point values are prohibited because they do not conform JSON specification', VOID, "It is possible to deserialize them using 'JsonBuilder.allowSpecialFloatingPointValues = true'");
}
function JsonEncodingException(message) {
  JsonException.call(this, message);
  captureStack(this, JsonEncodingException);
}
function InvalidKeyKindException(keyDescriptor) {
  return new JsonEncodingException("Value of type '" + keyDescriptor.og() + "' can't be used in JSON as a key in the map. " + ("It should have either primitive or enum kind, but its kind is '" + keyDescriptor.pg().toString() + "'.\n") + "Use 'allowStructuredMapKeys = true' in 'Json {}' builder to convert such maps to [key1, value1, key2, value2,...] arrays.");
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
  var index = _this__u8e3s4.vg(name);
  if (!(index === -3))
    return index;
  if (!json.ps_1.st_1)
    return index;
  return getJsonNameIndexSlowPath(_this__u8e3s4, json, name);
}
function namingStrategy(_this__u8e3s4, json) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  return equals(_this__u8e3s4.pg(), CLASS_getInstance()) ? json.ps_1.tt_1 : null;
}
function deserializationNamesMap(_this__u8e3s4, descriptor) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  var tmp = get_schemaCache(_this__u8e3s4);
  var tmp_0 = get_JsonDeserializationNamesKey();
  return tmp.cv(descriptor, tmp_0, deserializationNamesMap$lambda(descriptor, _this__u8e3s4));
}
function decodeCaseInsensitive(_this__u8e3s4, descriptor) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  return _this__u8e3s4.ps_1.ut_1 && equals(descriptor.pg(), ENUM_getInstance());
}
function getJsonNameIndexSlowPath(_this__u8e3s4, json, name) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  var tmp0_elvis_lhs = deserializationNamesMap(json, _this__u8e3s4).p1(name);
  return tmp0_elvis_lhs == null ? -3 : tmp0_elvis_lhs;
}
function buildDeserializationNamesMap(_this__u8e3s4, json) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  // Inline function 'kotlin.collections.mutableMapOf' call
  var builder = LinkedHashMap_init_$Create$();
  var useLowercaseEnums = decodeCaseInsensitive(json, _this__u8e3s4);
  var strategyForClasses = namingStrategy(_this__u8e3s4, json);
  var inductionVariable = 0;
  var last = _this__u8e3s4.sg();
  if (inductionVariable < last)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      // Inline function 'kotlin.collections.filterIsInstance' call
      var tmp0 = _this__u8e3s4.wg(i);
      // Inline function 'kotlin.collections.filterIsInstanceTo' call
      var destination = ArrayList_init_$Create$();
      var _iterator__ex2g4s = tmp0.g();
      while (_iterator__ex2g4s.h()) {
        var element = _iterator__ex2g4s.i();
        if (element instanceof JsonNames) {
          destination.e(element);
        }
      }
      var tmp0_safe_receiver = singleOrNull(destination);
      var tmp1_safe_receiver = tmp0_safe_receiver == null ? null : tmp0_safe_receiver.dv_1;
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
        tmp_0 = _this__u8e3s4.ug(i).toLowerCase();
      } else if (!(strategyForClasses == null)) {
        tmp_0 = strategyForClasses.ev(_this__u8e3s4, i, _this__u8e3s4.ug(i));
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
  if (builder.l()) {
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
  var entity = equals($this_buildDeserializationNamesMap.pg(), ENUM_getInstance()) ? 'enum value' : 'property';
  // Inline function 'kotlin.collections.contains' call
  // Inline function 'kotlin.collections.containsKey' call
  if ((isInterface(_this__u8e3s4, KtMap) ? _this__u8e3s4 : THROW_CCE()).n1(name)) {
    throw new JsonException("The suggested name '" + name + "' for " + entity + ' ' + $this_buildDeserializationNamesMap.ug(index) + ' is already one of the names for ' + entity + ' ' + ($this_buildDeserializationNamesMap.ug(getValue(_this__u8e3s4, name)) + ' in ' + toString_0($this_buildDeserializationNamesMap)));
  }
  // Inline function 'kotlin.collections.set' call
  _this__u8e3s4.t1(name, index);
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
  var newSize = imul($this.hv_1, 2);
  $this.fv_1 = copyOf($this.fv_1, newSize);
  $this.gv_1 = copyOf_0($this.gv_1, newSize);
}
function JsonPath() {
  var tmp = this;
  // Inline function 'kotlin.arrayOfNulls' call
  tmp.fv_1 = Array(8);
  var tmp_0 = this;
  var tmp_1 = 0;
  var tmp_2 = new Int32Array(8);
  while (tmp_1 < 8) {
    tmp_2[tmp_1] = -1;
    tmp_1 = tmp_1 + 1 | 0;
  }
  tmp_0.gv_1 = tmp_2;
  this.hv_1 = -1;
}
protoOf(JsonPath).iv = function (sd) {
  this.hv_1 = this.hv_1 + 1 | 0;
  var depth = this.hv_1;
  if (depth === this.fv_1.length) {
    resize(this);
  }
  this.fv_1[depth] = sd;
};
protoOf(JsonPath).jv = function (index) {
  this.gv_1[this.hv_1] = index;
};
protoOf(JsonPath).kv = function (key) {
  var tmp;
  if (!(this.gv_1[this.hv_1] === -2)) {
    this.hv_1 = this.hv_1 + 1 | 0;
    tmp = this.hv_1 === this.fv_1.length;
  } else {
    tmp = false;
  }
  if (tmp) {
    resize(this);
  }
  this.fv_1[this.hv_1] = key;
  this.gv_1[this.hv_1] = -2;
};
protoOf(JsonPath).lv = function () {
  if (this.gv_1[this.hv_1] === -2) {
    this.fv_1[this.hv_1] = Tombstone_instance;
  }
};
protoOf(JsonPath).mv = function () {
  var depth = this.hv_1;
  if (this.gv_1[depth] === -2) {
    this.gv_1[depth] = -1;
    this.hv_1 = this.hv_1 - 1 | 0;
  }
  if (!(this.hv_1 === -1)) {
    this.hv_1 = this.hv_1 - 1 | 0;
  }
};
protoOf(JsonPath).nv = function () {
  // Inline function 'kotlin.text.buildString' call
  // Inline function 'kotlin.apply' call
  var this_0 = StringBuilder_init_$Create$();
  this_0.w6('$');
  // Inline function 'kotlin.repeat' call
  var times = this.hv_1 + 1 | 0;
  var inductionVariable = 0;
  if (inductionVariable < times)
    do {
      var index = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      var element = this.fv_1[index];
      if (!(element == null) ? isInterface(element, SerialDescriptor) : false) {
        if (equals(element.pg(), LIST_getInstance())) {
          if (!(this.gv_1[index] === -1)) {
            this_0.w6('[');
            this_0.l9(this.gv_1[index]);
            this_0.w6(']');
          }
        } else {
          var idx = this.gv_1[index];
          if (idx >= 0) {
            this_0.w6('.');
            this_0.w6(element.ug(idx));
          }
        }
      } else {
        if (!(element === Tombstone_instance)) {
          this_0.w6('[');
          this_0.w6("'");
          this_0.v6(element);
          this_0.w6("'");
          this_0.w6(']');
        }
      }
    }
     while (inductionVariable < times);
  return this_0.toString();
};
protoOf(JsonPath).toString = function () {
  return this.nv();
};
function readObject($this) {
  // Inline function 'kotlinx.serialization.json.internal.JsonTreeReader.readObjectImpl' call
  var lastToken = $this.ov_1.sv(6);
  if ($this.ov_1.tv() === 4) {
    $this.ov_1.av('Unexpected leading comma');
  }
  // Inline function 'kotlin.collections.linkedMapOf' call
  var result = LinkedHashMap_init_$Create$();
  $l$loop: while ($this.ov_1.uv()) {
    var key = $this.pv_1 ? $this.ov_1.wv() : $this.ov_1.vv();
    $this.ov_1.sv(5);
    var element = $this.xv();
    // Inline function 'kotlin.collections.set' call
    result.t1(key, element);
    lastToken = $this.ov_1.yv();
    var tmp0_subject = lastToken;
    if (tmp0_subject !== 4)
      if (tmp0_subject === 7)
        break $l$loop;
      else {
        $this.ov_1.av('Expected end of the object or comma');
      }
  }
  if (lastToken === 6) {
    $this.ov_1.sv(7);
  } else if (lastToken === 4) {
    if (!$this.qv_1) {
      invalidTrailingComma($this.ov_1);
    }
    $this.ov_1.sv(7);
  }
  return new JsonObject(result);
}
function readObject_0($this, $receiver, $completion) {
  var tmp = new $readObjectCOROUTINE$($this, $receiver, $completion);
  tmp.k7_1 = Unit_instance;
  tmp.l7_1 = null;
  return tmp.q7();
}
function readArray($this) {
  var lastToken = $this.ov_1.yv();
  if ($this.ov_1.tv() === 4) {
    $this.ov_1.av('Unexpected leading comma');
  }
  // Inline function 'kotlin.collections.arrayListOf' call
  var result = ArrayList_init_$Create$();
  while ($this.ov_1.uv()) {
    var element = $this.xv();
    result.e(element);
    lastToken = $this.ov_1.yv();
    if (!(lastToken === 4)) {
      var tmp0 = $this.ov_1;
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.require' call
      var condition = lastToken === 9;
      var position = tmp0.ct_1;
      if (!condition) {
        var tmp$ret$2 = 'Expected end of the array or comma';
        tmp0.av(tmp$ret$2, position);
      }
    }
  }
  if (lastToken === 8) {
    $this.ov_1.sv(9);
  } else if (lastToken === 4) {
    if (!$this.qv_1) {
      invalidTrailingComma($this.ov_1, 'array');
    }
    $this.ov_1.sv(9);
  }
  return new JsonArray(result);
}
function readValue($this, isString) {
  var tmp;
  if ($this.pv_1 || !isString) {
    tmp = $this.ov_1.wv();
  } else {
    tmp = $this.ov_1.vv();
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
  this.vw_1 = this$0;
  CoroutineImpl.call(this, resultContinuation);
}
protoOf(JsonTreeReader$readDeepRecursive$slambda).zw = function ($this$DeepRecursiveFunction, it, $completion) {
  var tmp = this.ax($this$DeepRecursiveFunction, it, $completion);
  tmp.k7_1 = Unit_instance;
  tmp.l7_1 = null;
  return tmp.q7();
};
protoOf(JsonTreeReader$readDeepRecursive$slambda).w7 = function (p1, p2, $completion) {
  var tmp = p1 instanceof DeepRecursiveScope ? p1 : THROW_CCE();
  return this.zw(tmp, p2 instanceof Unit ? p2 : THROW_CCE(), $completion);
};
protoOf(JsonTreeReader$readDeepRecursive$slambda).q7 = function () {
  var suspendResult = this.k7_1;
  $sm: do
    try {
      var tmp = this.i7_1;
      switch (tmp) {
        case 0:
          this.j7_1 = 3;
          var tmp0_subject = this.vw_1.ov_1.tv();
          if (tmp0_subject === 1) {
            this.yw_1 = readValue(this.vw_1, true);
            this.i7_1 = 2;
            continue $sm;
          } else {
            if (tmp0_subject === 0) {
              this.yw_1 = readValue(this.vw_1, false);
              this.i7_1 = 2;
              continue $sm;
            } else {
              if (tmp0_subject === 6) {
                this.i7_1 = 1;
                suspendResult = readObject_0(this.vw_1, this.ww_1, this);
                if (suspendResult === get_COROUTINE_SUSPENDED()) {
                  return suspendResult;
                }
                continue $sm;
              } else {
                if (tmp0_subject === 8) {
                  this.yw_1 = readArray(this.vw_1);
                  this.i7_1 = 2;
                  continue $sm;
                } else {
                  var tmp_0 = this;
                  this.vw_1.ov_1.av("Can't begin reading element, unexpected token");
                }
              }
            }
          }

          break;
        case 1:
          this.yw_1 = suspendResult;
          this.i7_1 = 2;
          continue $sm;
        case 2:
          return this.yw_1;
        case 3:
          throw this.l7_1;
      }
    } catch ($p) {
      var e = $p;
      if (this.j7_1 === 3) {
        throw e;
      } else {
        this.i7_1 = this.j7_1;
        this.l7_1 = e;
      }
    }
   while (true);
};
protoOf(JsonTreeReader$readDeepRecursive$slambda).ax = function ($this$DeepRecursiveFunction, it, completion) {
  var i = new JsonTreeReader$readDeepRecursive$slambda(this.vw_1, completion);
  i.ww_1 = $this$DeepRecursiveFunction;
  i.xw_1 = it;
  return i;
};
function JsonTreeReader$readDeepRecursive$slambda_0(this$0, resultContinuation) {
  var i = new JsonTreeReader$readDeepRecursive$slambda(this$0, resultContinuation);
  var l = function ($this$DeepRecursiveFunction, it, $completion) {
    return i.zw($this$DeepRecursiveFunction, it, $completion);
  };
  l.$arity = 2;
  return l;
}
function $readObjectCOROUTINE$(_this__u8e3s4, _this__u8e3s4_0, resultContinuation) {
  CoroutineImpl.call(this, resultContinuation);
  this.hw_1 = _this__u8e3s4;
  this.iw_1 = _this__u8e3s4_0;
}
protoOf($readObjectCOROUTINE$).q7 = function () {
  var suspendResult = this.k7_1;
  $sm: do
    try {
      var tmp = this.i7_1;
      switch (tmp) {
        case 0:
          this.j7_1 = 5;
          this.kw_1 = this.hw_1;
          this.lw_1 = this.kw_1.ov_1.sv(6);
          if (this.kw_1.ov_1.tv() === 4) {
            this.kw_1.ov_1.av('Unexpected leading comma');
          }

          var tmp_0 = this;
          tmp_0.jw_1 = LinkedHashMap_init_$Create$();
          this.i7_1 = 1;
          continue $sm;
        case 1:
          if (!this.kw_1.ov_1.uv()) {
            this.i7_1 = 4;
            continue $sm;
          }

          this.mw_1 = this.kw_1.pv_1 ? this.kw_1.ov_1.wv() : this.kw_1.ov_1.vv();
          this.kw_1.ov_1.sv(5);
          this.i7_1 = 2;
          suspendResult = this.iw_1.xd(Unit_instance, this);
          if (suspendResult === get_COROUTINE_SUSPENDED()) {
            return suspendResult;
          }

          continue $sm;
        case 2:
          var element = suspendResult;
          var tmp0 = this.jw_1;
          var key = this.mw_1;
          tmp0.t1(key, element);
          this.lw_1 = this.kw_1.ov_1.yv();
          var tmp0_subject = this.lw_1;
          if (tmp0_subject === 4) {
            this.i7_1 = 3;
            continue $sm;
          } else {
            if (tmp0_subject === 7) {
              this.i7_1 = 4;
              continue $sm;
            } else {
              this.kw_1.ov_1.av('Expected end of the object or comma');
            }
          }

          break;
        case 3:
          this.i7_1 = 1;
          continue $sm;
        case 4:
          if (this.lw_1 === 6) {
            this.kw_1.ov_1.sv(7);
          } else if (this.lw_1 === 4) {
            if (!this.kw_1.qv_1) {
              invalidTrailingComma(this.kw_1.ov_1);
            }
            this.kw_1.ov_1.sv(7);
          }

          return new JsonObject(this.jw_1);
        case 5:
          throw this.l7_1;
      }
    } catch ($p) {
      var e = $p;
      if (this.j7_1 === 5) {
        throw e;
      } else {
        this.i7_1 = this.j7_1;
        this.l7_1 = e;
      }
    }
   while (true);
};
function JsonTreeReader(configuration, lexer) {
  this.ov_1 = lexer;
  this.pv_1 = configuration.jt_1;
  this.qv_1 = configuration.vt_1;
  this.rv_1 = 0;
}
protoOf(JsonTreeReader).xv = function () {
  var token = this.ov_1.tv();
  var tmp;
  if (token === 1) {
    tmp = readValue(this, true);
  } else if (token === 0) {
    tmp = readValue(this, false);
  } else if (token === 6) {
    var tmp_0;
    this.rv_1 = this.rv_1 + 1 | 0;
    if (this.rv_1 === 200) {
      tmp_0 = readDeepRecursive(this);
    } else {
      tmp_0 = readObject(this);
    }
    var result = tmp_0;
    this.rv_1 = this.rv_1 - 1 | 0;
    tmp = result;
  } else if (token === 8) {
    tmp = readArray(this);
  } else {
    this.ov_1.av('Cannot read Json element because of unexpected ' + tokenDescription(token));
  }
  return tmp;
};
function classDiscriminator(_this__u8e3s4, json) {
  var _iterator__ex2g4s = _this__u8e3s4.tg().g();
  while (_iterator__ex2g4s.h()) {
    var annotation = _iterator__ex2g4s.i();
    if (annotation instanceof JsonClassDiscriminator)
      return annotation.bx_1;
  }
  return json.ps_1.qt_1;
}
function Key() {
}
function DescriptorSchemaCache() {
  this.bv_1 = createMapForCache(16);
}
protoOf(DescriptorSchemaCache).cx = function (descriptor, key, value) {
  // Inline function 'kotlin.collections.getOrPut' call
  var this_0 = this.bv_1;
  var value_0 = this_0.p1(descriptor);
  var tmp;
  if (value_0 == null) {
    var answer = createMapForCache(2);
    this_0.t1(descriptor, answer);
    tmp = answer;
  } else {
    tmp = value_0;
  }
  var tmp0 = tmp;
  var tmp2 = key instanceof Key ? key : THROW_CCE();
  // Inline function 'kotlin.collections.set' call
  var value_1 = !(value == null) ? value : THROW_CCE();
  tmp0.t1(tmp2, value_1);
};
protoOf(DescriptorSchemaCache).cv = function (descriptor, key, defaultValue) {
  var tmp0_safe_receiver = this.dx(descriptor, key);
  if (tmp0_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    return tmp0_safe_receiver;
  }
  var value = defaultValue();
  this.cx(descriptor, key, value);
  return value;
};
protoOf(DescriptorSchemaCache).dx = function (descriptor, key) {
  var tmp0_safe_receiver = this.bv_1.p1(descriptor);
  var tmp;
  if (tmp0_safe_receiver == null) {
    tmp = null;
  } else {
    tmp = tmp0_safe_receiver.p1(key instanceof Key ? key : THROW_CCE());
  }
  var tmp_0 = tmp;
  return !(tmp_0 == null) ? tmp_0 : null;
};
function DiscriminatorHolder(discriminatorToSkip) {
  this.ex_1 = discriminatorToSkip;
}
function trySkip($this, $receiver, unknownKey) {
  if ($receiver == null)
    return false;
  if ($receiver.ex_1 === unknownKey) {
    $receiver.ex_1 = null;
    return true;
  }
  return false;
}
function skipLeftoverElements($this, descriptor) {
  while (!($this.dj(descriptor) === -1)) {
  }
}
function checkLeadingComma($this) {
  if ($this.ws_1.tv() === 4) {
    $this.ws_1.av('Unexpected leading comma');
  }
}
function decodeMapIndex($this) {
  var hasComma = false;
  var decodingKey = !(($this.ys_1 % 2 | 0) === 0);
  if (decodingKey) {
    if (!($this.ys_1 === -1)) {
      hasComma = $this.ws_1.gx();
    }
  } else {
    $this.ws_1.fx(_Char___init__impl__6a9atx(58));
  }
  var tmp;
  if ($this.ws_1.uv()) {
    if (decodingKey) {
      if ($this.ys_1 === -1) {
        var tmp0 = $this.ws_1;
        // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.require' call
        var condition = !hasComma;
        var position = tmp0.ct_1;
        if (!condition) {
          var tmp$ret$1 = 'Unexpected leading comma';
          tmp0.av(tmp$ret$1, position);
        }
      } else {
        var tmp0_0 = $this.ws_1;
        // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.require' call
        var condition_0 = hasComma;
        var position_0 = tmp0_0.ct_1;
        if (!condition_0) {
          var tmp$ret$3 = 'Expected comma after the key-value pair';
          tmp0_0.av(tmp$ret$3, position_0);
        }
      }
    }
    $this.ys_1 = $this.ys_1 + 1 | 0;
    tmp = $this.ys_1;
  } else {
    if (hasComma && !$this.us_1.ps_1.vt_1) {
      invalidTrailingComma($this.ws_1);
    }
    tmp = -1;
  }
  return tmp;
}
function coerceInputValue($this, descriptor, index) {
  var tmp0 = $this.us_1;
  var tmp$ret$0;
  $l$block_2: {
    // Inline function 'kotlinx.serialization.json.internal.tryCoerceValue' call
    var isOptional = descriptor.yg(index);
    var elementDescriptor = descriptor.xg(index);
    var tmp;
    if (isOptional && !elementDescriptor.qg()) {
      tmp = $this.ws_1.hx(true);
    } else {
      tmp = false;
    }
    if (tmp) {
      tmp$ret$0 = true;
      break $l$block_2;
    }
    if (equals(elementDescriptor.pg(), ENUM_getInstance())) {
      var tmp_0;
      if (elementDescriptor.qg()) {
        tmp_0 = $this.ws_1.hx(false);
      } else {
        tmp_0 = false;
      }
      if (tmp_0) {
        tmp$ret$0 = false;
        break $l$block_2;
      }
      var tmp0_elvis_lhs = $this.ws_1.ix($this.at_1.jt_1);
      var tmp_1;
      if (tmp0_elvis_lhs == null) {
        tmp$ret$0 = false;
        break $l$block_2;
      } else {
        tmp_1 = tmp0_elvis_lhs;
      }
      var enumValue = tmp_1;
      var enumIndex = getJsonNameIndex(elementDescriptor, tmp0, enumValue);
      var coerceToNull = !tmp0.ps_1.mt_1 && elementDescriptor.qg();
      if (enumIndex === -3 && (isOptional || coerceToNull)) {
        $this.ws_1.vv();
        tmp$ret$0 = true;
        break $l$block_2;
      }
    }
    tmp$ret$0 = false;
  }
  return tmp$ret$0;
}
function decodeObjectIndex($this, descriptor) {
  var hasComma = $this.ws_1.gx();
  while ($this.ws_1.uv()) {
    hasComma = false;
    var key = decodeStringKey($this);
    $this.ws_1.fx(_Char___init__impl__6a9atx(58));
    var index = getJsonNameIndex(descriptor, $this.us_1, key);
    var tmp;
    if (!(index === -3)) {
      var tmp_0;
      if ($this.at_1.ot_1 && coerceInputValue($this, descriptor, index)) {
        hasComma = $this.ws_1.gx();
        tmp_0 = false;
      } else {
        var tmp0_safe_receiver = $this.bt_1;
        if (tmp0_safe_receiver == null)
          null;
        else {
          tmp0_safe_receiver.xu(index);
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
  if (hasComma && !$this.us_1.ps_1.vt_1) {
    invalidTrailingComma($this.ws_1);
  }
  var tmp1_safe_receiver = $this.bt_1;
  var tmp2_elvis_lhs = tmp1_safe_receiver == null ? null : tmp1_safe_receiver.yu();
  return tmp2_elvis_lhs == null ? -1 : tmp2_elvis_lhs;
}
function handleUnknown($this, key) {
  if ($this.at_1.it_1 || trySkip($this, $this.zs_1, key)) {
    $this.ws_1.kx($this.at_1.jt_1);
  } else {
    $this.ws_1.jx(key);
  }
  return $this.ws_1.gx();
}
function decodeListIndex($this) {
  var hasComma = $this.ws_1.gx();
  var tmp;
  if ($this.ws_1.uv()) {
    if (!($this.ys_1 === -1) && !hasComma) {
      $this.ws_1.av('Expected end of the array or comma');
    }
    $this.ys_1 = $this.ys_1 + 1 | 0;
    tmp = $this.ys_1;
  } else {
    if (hasComma && !$this.us_1.ps_1.vt_1) {
      invalidTrailingComma($this.ws_1, 'array');
    }
    tmp = -1;
  }
  return tmp;
}
function decodeStringKey($this) {
  var tmp;
  if ($this.at_1.jt_1) {
    tmp = $this.ws_1.mx();
  } else {
    tmp = $this.ws_1.lx();
  }
  return tmp;
}
function StreamingJsonDecoder(json, mode, lexer, descriptor, discriminatorHolder) {
  AbstractDecoder.call(this);
  this.us_1 = json;
  this.vs_1 = mode;
  this.ws_1 = lexer;
  this.xs_1 = this.us_1.bj();
  this.ys_1 = -1;
  this.zs_1 = discriminatorHolder;
  this.at_1 = this.us_1.ps_1;
  this.bt_1 = this.at_1.mt_1 ? null : new JsonElementMarker(descriptor);
}
protoOf(StreamingJsonDecoder).yt = function () {
  return this.us_1;
};
protoOf(StreamingJsonDecoder).bj = function () {
  return this.xs_1;
};
protoOf(StreamingJsonDecoder).zt = function () {
  return (new JsonTreeReader(this.us_1.ps_1, this.ws_1)).xv();
};
protoOf(StreamingJsonDecoder).ni = function (deserializer) {
  try {
    var tmp;
    if (!(deserializer instanceof AbstractPolymorphicSerializer)) {
      tmp = true;
    } else {
      tmp = this.us_1.ps_1.pt_1;
    }
    if (tmp) {
      return deserializer.hg(this);
    }
    var discriminator = classDiscriminator(deserializer.gg(), this.us_1);
    var tmp0_elvis_lhs = this.ws_1.nx(discriminator, this.at_1.jt_1);
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
          tmp_1 = this.yt().ps_1.pt_1;
        }
        if (tmp_1) {
          tmp$ret$0 = tmp2.hg(this);
          break $l$block;
        }
        var discriminator_0 = classDiscriminator(tmp2.gg(), this.yt());
        var tmp0 = this.zt();
        // Inline function 'kotlinx.serialization.json.internal.cast' call
        var serialName = tmp2.gg().og();
        if (!(tmp0 instanceof JsonObject)) {
          var tmp_2 = getKClass(JsonObject).h8();
          var tmp_3 = getKClassFromExpression(tmp0).h8();
          var tmp$ret$2 = this.ws_1.dt_1.nv();
          throw JsonDecodingException_0(-1, 'Expected ' + tmp_2 + ', but had ' + tmp_3 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$2, toString_0(tmp0));
        }
        var jsonTree = tmp0;
        var tmp0_safe_receiver = jsonTree.ka(discriminator_0);
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
        tmp$ret$0 = readPolymorphicJson(this.yt(), discriminator_0, jsonTree, actualSerializer);
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
        this.ws_1.av(message, VOID, hint);
      } else {
        throw $p;
      }
      tmp_7 = tmp_8;
    }
    var tmp_9 = tmp_7;
    var actualSerializer_0 = isInterface(tmp_9, DeserializationStrategy) ? tmp_9 : THROW_CCE();
    this.zs_1 = new DiscriminatorHolder(discriminator);
    return actualSerializer_0.hg(this);
  } catch ($p) {
    if ($p instanceof MissingFieldException) {
      var e = $p;
      if (contains_0(ensureNotNull(e.message), 'at path'))
        throw e;
      throw new MissingFieldException(e.kg_1, plus(e.message, ' at path: ') + this.ws_1.dt_1.nv(), e);
    } else {
      throw $p;
    }
  }
};
protoOf(StreamingJsonDecoder).oi = function (descriptor) {
  var newMode = switchMode(this.us_1, descriptor);
  this.ws_1.dt_1.iv(descriptor);
  this.ws_1.fx(newMode.qx_1);
  checkLeadingComma(this);
  var tmp;
  switch (newMode.v1_1) {
    case 1:
    case 2:
    case 3:
      tmp = new StreamingJsonDecoder(this.us_1, newMode, this.ws_1, descriptor, this.zs_1);
      break;
    default:
      var tmp_0;
      if (this.vs_1.equals(newMode) && this.us_1.ps_1.mt_1) {
        tmp_0 = this;
      } else {
        tmp_0 = new StreamingJsonDecoder(this.us_1, newMode, this.ws_1, descriptor, this.zs_1);
      }

      tmp = tmp_0;
      break;
  }
  return tmp;
};
protoOf(StreamingJsonDecoder).pi = function (descriptor) {
  if (this.us_1.ps_1.it_1 && descriptor.sg() === 0) {
    skipLeftoverElements(this, descriptor);
  }
  if (this.ws_1.gx() && !this.us_1.ps_1.vt_1) {
    invalidTrailingComma(this.ws_1, '');
  }
  this.ws_1.fx(this.vs_1.rx_1);
  this.ws_1.dt_1.mv();
};
protoOf(StreamingJsonDecoder).ai = function () {
  var tmp;
  var tmp0_safe_receiver = this.bt_1;
  var tmp1_elvis_lhs = tmp0_safe_receiver == null ? null : tmp0_safe_receiver.wu_1;
  if (!(tmp1_elvis_lhs == null ? false : tmp1_elvis_lhs)) {
    tmp = !this.ws_1.sx();
  } else {
    tmp = false;
  }
  return tmp;
};
protoOf(StreamingJsonDecoder).bi = function () {
  return null;
};
protoOf(StreamingJsonDecoder).zi = function (descriptor, index, deserializer, previousValue) {
  var isMapKey = this.vs_1.equals(WriteMode_MAP_getInstance()) && (index & 1) === 0;
  if (isMapKey) {
    this.ws_1.dt_1.lv();
  }
  var value = protoOf(AbstractDecoder).zi.call(this, descriptor, index, deserializer, previousValue);
  if (isMapKey) {
    this.ws_1.dt_1.kv(value);
  }
  return value;
};
protoOf(StreamingJsonDecoder).dj = function (descriptor) {
  var index;
  switch (this.vs_1.v1_1) {
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
  if (!this.vs_1.equals(WriteMode_MAP_getInstance())) {
    this.ws_1.dt_1.jv(index);
  }
  return index;
};
protoOf(StreamingJsonDecoder).ci = function () {
  return this.ws_1.tx();
};
protoOf(StreamingJsonDecoder).di = function () {
  var value = this.ws_1.ju();
  if (!equalsLong(value, fromInt(convertToByte(value)))) {
    this.ws_1.av("Failed to parse byte for input '" + value.toString() + "'");
  }
  return convertToByte(value);
};
protoOf(StreamingJsonDecoder).ei = function () {
  var value = this.ws_1.ju();
  if (!equalsLong(value, fromInt(convertToShort(value)))) {
    this.ws_1.av("Failed to parse short for input '" + value.toString() + "'");
  }
  return convertToShort(value);
};
protoOf(StreamingJsonDecoder).fi = function () {
  var value = this.ws_1.ju();
  if (!equalsLong(value, fromInt(convertToInt(value)))) {
    this.ws_1.av("Failed to parse int for input '" + value.toString() + "'");
  }
  return convertToInt(value);
};
protoOf(StreamingJsonDecoder).gi = function () {
  return this.ws_1.ju();
};
protoOf(StreamingJsonDecoder).hi = function () {
  var tmp0 = this.ws_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.wv();
    try {
      // Inline function 'kotlin.text.toFloat' call
      // Inline function 'kotlin.js.unsafeCast' call
      // Inline function 'kotlin.js.asDynamic' call
      tmp$ret$0 = toDouble(input);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.av("Failed to parse type '" + 'float' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  var result = tmp$ret$0;
  var specialFp = this.us_1.ps_1.rt_1;
  if (specialFp || isFinite(result))
    return result;
  throwInvalidFloatingPointDecoded(this.ws_1, result);
};
protoOf(StreamingJsonDecoder).ii = function () {
  var tmp0 = this.ws_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.wv();
    try {
      tmp$ret$0 = toDouble(input);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.av("Failed to parse type '" + 'double' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  var result = tmp$ret$0;
  var specialFp = this.us_1.ps_1.rt_1;
  if (specialFp || isFinite_0(result))
    return result;
  throwInvalidFloatingPointDecoded(this.ws_1, result);
};
protoOf(StreamingJsonDecoder).ji = function () {
  var string = this.ws_1.wv();
  if (!(string.length === 1)) {
    this.ws_1.av("Expected single char, but got '" + string + "'");
  }
  return charCodeAt(string, 0);
};
protoOf(StreamingJsonDecoder).ki = function () {
  var tmp;
  if (this.at_1.jt_1) {
    tmp = this.ws_1.mx();
  } else {
    tmp = this.ws_1.vv();
  }
  return tmp;
};
protoOf(StreamingJsonDecoder).li = function (descriptor) {
  return get_isUnsignedNumber(descriptor) ? new JsonDecoderForUnsignedTypes(this.ws_1, this.us_1) : protoOf(AbstractDecoder).li.call(this, descriptor);
};
function JsonDecoderForUnsignedTypes(lexer, json) {
  AbstractDecoder.call(this);
  this.ux_1 = lexer;
  this.vx_1 = json.bj();
}
protoOf(JsonDecoderForUnsignedTypes).bj = function () {
  return this.vx_1;
};
protoOf(JsonDecoderForUnsignedTypes).dj = function (descriptor) {
  // Inline function 'kotlin.error' call
  var message = 'unsupported';
  throw IllegalStateException_init_$Create$(toString_0(message));
};
protoOf(JsonDecoderForUnsignedTypes).fi = function () {
  var tmp0 = this.ux_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.wv();
    try {
      // Inline function 'kotlin.UInt.toInt' call
      var this_0 = toUInt(input);
      tmp$ret$0 = _UInt___get_data__impl__f0vqqw(this_0);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.av("Failed to parse type '" + 'UInt' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$0;
};
protoOf(JsonDecoderForUnsignedTypes).gi = function () {
  var tmp0 = this.ux_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.wv();
    try {
      // Inline function 'kotlin.ULong.toLong' call
      var this_0 = toULong(input);
      tmp$ret$0 = _ULong___get_data__impl__fggpzb(this_0);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.av("Failed to parse type '" + 'ULong' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$0;
};
protoOf(JsonDecoderForUnsignedTypes).di = function () {
  var tmp0 = this.ux_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.wv();
    try {
      // Inline function 'kotlin.UByte.toByte' call
      var this_0 = toUByte(input);
      tmp$ret$0 = _UByte___get_data__impl__jof9qr(this_0);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.av("Failed to parse type '" + 'UByte' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$0;
};
protoOf(JsonDecoderForUnsignedTypes).ei = function () {
  var tmp0 = this.ux_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.wv();
    try {
      // Inline function 'kotlin.UShort.toShort' call
      var this_0 = toUShort(input);
      tmp$ret$0 = _UShort___get_data__impl__g0245(this_0);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.av("Failed to parse type '" + 'UShort' + "' for input '" + input + "'");
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
  return _this__u8e3s4.rg() && get_unsignedNumberDescriptors().j1(_this__u8e3s4);
}
var properties_initialized_StreamingJsonEncoder_kt_6ifwwk;
function _init_properties_StreamingJsonEncoder_kt__pn1bsi() {
  if (!properties_initialized_StreamingJsonEncoder_kt_6ifwwk) {
    properties_initialized_StreamingJsonEncoder_kt_6ifwwk = true;
    unsignedNumberDescriptors = setOf([serializer_0(Companion_getInstance()).gg(), serializer_1(Companion_getInstance_0()).gg(), serializer_2(Companion_getInstance_1()).gg(), serializer_3(Companion_getInstance_2()).gg()]);
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
  _this__u8e3s4.x6(_Char___init__impl__6a9atx(34));
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
        _this__u8e3s4.j9(value, lastPos, i);
        _this__u8e3s4.w6(get_ESCAPE_STRINGS()[c]);
        lastPos = i + 1 | 0;
      }
    }
     while (inductionVariable <= last);
  if (!(lastPos === 0))
    _this__u8e3s4.j9(value, lastPos, value.length);
  else
    _this__u8e3s4.w6(value);
  _this__u8e3s4.x6(_Char___init__impl__6a9atx(34));
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
  throw JsonDecodingException_0(-1, "Failed to parse literal '" + literal.toString() + "' as " + type + ' value at element: ' + $this.cy(tag), toString_0($this.dy()));
}
function AbstractJsonTreeDecoder(json, value, polymorphicDiscriminator) {
  polymorphicDiscriminator = polymorphicDiscriminator === VOID ? null : polymorphicDiscriminator;
  NamedValueDecoder.call(this);
  this.yx_1 = json;
  this.zx_1 = value;
  this.ay_1 = polymorphicDiscriminator;
  this.by_1 = this.yt().ps_1;
}
protoOf(AbstractJsonTreeDecoder).yt = function () {
  return this.yx_1;
};
protoOf(AbstractJsonTreeDecoder).m1 = function () {
  return this.zx_1;
};
protoOf(AbstractJsonTreeDecoder).bj = function () {
  return this.yt().bj();
};
protoOf(AbstractJsonTreeDecoder).dy = function () {
  var tmp0_safe_receiver = this.ir();
  var tmp;
  if (tmp0_safe_receiver == null) {
    tmp = null;
  } else {
    // Inline function 'kotlin.let' call
    tmp = this.ey(tmp0_safe_receiver);
  }
  var tmp1_elvis_lhs = tmp;
  return tmp1_elvis_lhs == null ? this.m1() : tmp1_elvis_lhs;
};
protoOf(AbstractJsonTreeDecoder).cy = function (currentTag) {
  return this.kr() + ('.' + currentTag);
};
protoOf(AbstractJsonTreeDecoder).zt = function () {
  return this.dy();
};
protoOf(AbstractJsonTreeDecoder).ni = function (deserializer) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.decodeSerializableValuePolymorphic' call
    var tmp;
    if (!(deserializer instanceof AbstractPolymorphicSerializer)) {
      tmp = true;
    } else {
      tmp = this.yt().ps_1.pt_1;
    }
    if (tmp) {
      tmp$ret$0 = deserializer.hg(this);
      break $l$block;
    }
    var discriminator = classDiscriminator(deserializer.gg(), this.yt());
    var tmp0 = this.zt();
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var serialName = deserializer.gg().og();
    if (!(tmp0 instanceof JsonObject)) {
      var tmp_0 = getKClass(JsonObject).h8();
      var tmp_1 = getKClassFromExpression(tmp0).h8();
      var tmp$ret$2 = this.kr();
      throw JsonDecodingException_0(-1, 'Expected ' + tmp_0 + ', but had ' + tmp_1 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$2, toString_0(tmp0));
    }
    var jsonTree = tmp0;
    var tmp0_safe_receiver = jsonTree.ka(discriminator);
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
    tmp$ret$0 = readPolymorphicJson(this.yt(), discriminator, jsonTree, actualSerializer);
  }
  return tmp$ret$0;
};
protoOf(AbstractJsonTreeDecoder).jr = function (parentName, childName) {
  return childName;
};
protoOf(AbstractJsonTreeDecoder).oi = function (descriptor) {
  var currentObject = this.dy();
  var tmp0_subject = descriptor.pg();
  var tmp;
  var tmp_0;
  if (equals(tmp0_subject, LIST_getInstance())) {
    tmp_0 = true;
  } else {
    tmp_0 = tmp0_subject instanceof PolymorphicKind;
  }
  if (tmp_0) {
    var tmp_1 = this.yt();
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var serialName = descriptor.og();
    if (!(currentObject instanceof JsonArray)) {
      var tmp_2 = getKClass(JsonArray).h8();
      var tmp_3 = getKClassFromExpression(currentObject).h8();
      var tmp$ret$2 = this.kr();
      throw JsonDecodingException_0(-1, 'Expected ' + tmp_2 + ', but had ' + tmp_3 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$2, toString_0(currentObject));
    }
    tmp = new JsonTreeListDecoder(tmp_1, currentObject);
  } else {
    if (equals(tmp0_subject, MAP_getInstance())) {
      // Inline function 'kotlinx.serialization.json.internal.selectMapMode' call
      var this_0 = this.yt();
      var keyDescriptor = carrierDescriptor(descriptor.xg(0), this_0.bj());
      var keyKind = keyDescriptor.pg();
      var tmp_4;
      var tmp_5;
      if (keyKind instanceof PrimitiveKind) {
        tmp_5 = true;
      } else {
        tmp_5 = equals(keyKind, ENUM_getInstance());
      }
      if (tmp_5) {
        var tmp_6 = this.yt();
        // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
        // Inline function 'kotlinx.serialization.json.internal.cast' call
        var serialName_0 = descriptor.og();
        if (!(currentObject instanceof JsonObject)) {
          var tmp_7 = getKClass(JsonObject).h8();
          var tmp_8 = getKClassFromExpression(currentObject).h8();
          var tmp$ret$7 = this.kr();
          throw JsonDecodingException_0(-1, 'Expected ' + tmp_7 + ', but had ' + tmp_8 + ' as the serialized body of ' + serialName_0 + ' at element: ' + tmp$ret$7, toString_0(currentObject));
        }
        tmp_4 = new JsonTreeMapDecoder(tmp_6, currentObject);
      } else {
        if (this_0.ps_1.kt_1) {
          var tmp_9 = this.yt();
          // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
          // Inline function 'kotlinx.serialization.json.internal.cast' call
          var serialName_1 = descriptor.og();
          if (!(currentObject instanceof JsonArray)) {
            var tmp_10 = getKClass(JsonArray).h8();
            var tmp_11 = getKClassFromExpression(currentObject).h8();
            var tmp$ret$11 = this.kr();
            throw JsonDecodingException_0(-1, 'Expected ' + tmp_10 + ', but had ' + tmp_11 + ' as the serialized body of ' + serialName_1 + ' at element: ' + tmp$ret$11, toString_0(currentObject));
          }
          tmp_4 = new JsonTreeListDecoder(tmp_9, currentObject);
        } else {
          throw InvalidKeyKindException(keyDescriptor);
        }
      }
      tmp = tmp_4;
    } else {
      var tmp_12 = this.yt();
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
      // Inline function 'kotlinx.serialization.json.internal.cast' call
      var serialName_2 = descriptor.og();
      if (!(currentObject instanceof JsonObject)) {
        var tmp_13 = getKClass(JsonObject).h8();
        var tmp_14 = getKClassFromExpression(currentObject).h8();
        var tmp$ret$14 = this.kr();
        throw JsonDecodingException_0(-1, 'Expected ' + tmp_13 + ', but had ' + tmp_14 + ' as the serialized body of ' + serialName_2 + ' at element: ' + tmp$ret$14, toString_0(currentObject));
      }
      tmp = new JsonTreeDecoder(tmp_12, currentObject, this.ay_1);
    }
  }
  return tmp;
};
protoOf(AbstractJsonTreeDecoder).pi = function (descriptor) {
};
protoOf(AbstractJsonTreeDecoder).ai = function () {
  var tmp = this.dy();
  return !(tmp instanceof JsonNull);
};
protoOf(AbstractJsonTreeDecoder).fy = function (tag) {
  return !(this.ey(tag) === JsonNull_getInstance());
};
protoOf(AbstractJsonTreeDecoder).mr = function (tag) {
  return this.fy((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).gy = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.ey(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).h8();
      var tmp_0 = getKClassFromExpression(value).h8();
      var tmp$ret$3 = this.cy(tag);
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
protoOf(AbstractJsonTreeDecoder).nr = function (tag) {
  return this.gy((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).hy = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.ey(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).h8();
      var tmp_0 = getKClassFromExpression(value).h8();
      var tmp$ret$3 = this.cy(tag);
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
protoOf(AbstractJsonTreeDecoder).or = function (tag) {
  return this.hy((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).iy = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.ey(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).h8();
      var tmp_0 = getKClassFromExpression(value).h8();
      var tmp$ret$3 = this.cy(tag);
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
protoOf(AbstractJsonTreeDecoder).pr = function (tag) {
  return this.iy((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).jy = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.ey(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).h8();
      var tmp_0 = getKClassFromExpression(value).h8();
      var tmp$ret$3 = this.cy(tag);
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
protoOf(AbstractJsonTreeDecoder).qr = function (tag) {
  return this.jy((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).ky = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.ey(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).h8();
      var tmp_0 = getKClassFromExpression(value).h8();
      var tmp$ret$3 = this.cy(tag);
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
protoOf(AbstractJsonTreeDecoder).rr = function (tag) {
  return this.ky((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).ly = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.ey(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).h8();
      var tmp_0 = getKClassFromExpression(value).h8();
      var tmp$ret$3 = this.cy(tag);
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
  var specialFp = this.yt().ps_1.rt_1;
  if (specialFp || isFinite(result))
    return result;
  throw InvalidFloatingPointDecoded(result, tag, toString_0(this.dy()));
};
protoOf(AbstractJsonTreeDecoder).sr = function (tag) {
  return this.ly((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).my = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.ey(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).h8();
      var tmp_0 = getKClassFromExpression(value).h8();
      var tmp$ret$3 = this.cy(tag);
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
  var specialFp = this.yt().ps_1.rt_1;
  if (specialFp || isFinite_0(result))
    return result;
  throw InvalidFloatingPointDecoded(result, tag, toString_0(this.dy()));
};
protoOf(AbstractJsonTreeDecoder).tr = function (tag) {
  return this.my((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).ny = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.ey(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).h8();
      var tmp_0 = getKClassFromExpression(value).h8();
      var tmp$ret$3 = this.cy(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'char' + ' at element: ' + tmp$ret$3, toString_0(value));
    }
    var literal = value;
    try {
      var tmp0_elvis_lhs = new Char(single(literal.eu()));
      var tmp_1;
      if (tmp0_elvis_lhs == null) {
        unparsedPrimitive(this, literal, 'char', tag);
      } else {
        tmp_1 = tmp0_elvis_lhs;
      }
      tmp$ret$0 = tmp_1.x_1;
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
protoOf(AbstractJsonTreeDecoder).ur = function (tag) {
  return this.ny((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).oy = function (tag) {
  // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
  // Inline function 'kotlinx.serialization.json.internal.cast' call
  var value = this.ey(tag);
  if (!(value instanceof JsonPrimitive)) {
    var tmp = getKClass(JsonPrimitive).h8();
    var tmp_0 = getKClassFromExpression(value).h8();
    var tmp$ret$2 = this.cy(tag);
    throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'string' + ' at element: ' + tmp$ret$2, toString_0(value));
  }
  var value_0 = value;
  if (!(value_0 instanceof JsonLiteral))
    throw JsonDecodingException_0(-1, "Expected string value for a non-null key '" + tag + "', got null literal instead at element: " + this.cy(tag), toString_0(this.dy()));
  if (!value_0.gu_1 && !this.yt().ps_1.jt_1) {
    throw JsonDecodingException_0(-1, "String literal for key '" + tag + "' should be quoted at element: " + this.cy(tag) + ".\nUse 'isLenient = true' in 'Json {}' builder to accept non-compliant JSON.", toString_0(this.dy()));
  }
  return value_0.iu_1;
};
protoOf(AbstractJsonTreeDecoder).vr = function (tag) {
  return this.oy((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).py = function (tag, inlineDescriptor) {
  var tmp;
  if (get_isUnsignedNumber(inlineDescriptor)) {
    var tmp_0 = this.yt();
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    var tmp2 = this.ey(tag);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var serialName = inlineDescriptor.og();
    if (!(tmp2 instanceof JsonPrimitive)) {
      var tmp_1 = getKClass(JsonPrimitive).h8();
      var tmp_2 = getKClassFromExpression(tmp2).h8();
      var tmp$ret$3 = this.cy(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp_1 + ', but had ' + tmp_2 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$3, toString_0(tmp2));
    }
    var lexer = StringJsonLexer_0(tmp_0, tmp2.eu());
    tmp = new JsonDecoderForUnsignedTypes(lexer, this.yt());
  } else {
    tmp = protoOf(NamedValueDecoder).wr.call(this, tag, inlineDescriptor);
  }
  return tmp;
};
protoOf(AbstractJsonTreeDecoder).wr = function (tag, inlineDescriptor) {
  return this.py((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE(), inlineDescriptor);
};
protoOf(AbstractJsonTreeDecoder).li = function (descriptor) {
  return !(this.ir() == null) ? protoOf(NamedValueDecoder).li.call(this, descriptor) : (new JsonPrimitiveDecoder(this.yt(), this.m1(), this.ay_1)).li(descriptor);
};
function coerceInputValue_0($this, descriptor, index, tag) {
  var tmp0 = $this.yt();
  var tmp$ret$0;
  $l$block_2: {
    // Inline function 'kotlinx.serialization.json.internal.tryCoerceValue' call
    var isOptional = descriptor.yg(index);
    var elementDescriptor = descriptor.xg(index);
    var tmp;
    if (isOptional && !elementDescriptor.qg()) {
      var tmp_0 = $this.ey(tag);
      tmp = tmp_0 instanceof JsonNull;
    } else {
      tmp = false;
    }
    if (tmp) {
      tmp$ret$0 = true;
      break $l$block_2;
    }
    if (equals(elementDescriptor.pg(), ENUM_getInstance())) {
      var tmp_1;
      if (elementDescriptor.qg()) {
        var tmp_2 = $this.ey(tag);
        tmp_1 = tmp_2 instanceof JsonNull;
      } else {
        tmp_1 = false;
      }
      if (tmp_1) {
        tmp$ret$0 = false;
        break $l$block_2;
      }
      var tmp_3 = $this.ey(tag);
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
      var coerceToNull = !tmp0.ps_1.mt_1 && elementDescriptor.qg();
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
  $this.zy_1 = (!$this.yt().ps_1.mt_1 && !descriptor.yg(index) && descriptor.xg(index).qg());
  return $this.zy_1;
}
function JsonTreeDecoder(json, value, polymorphicDiscriminator, polyDescriptor) {
  polymorphicDiscriminator = polymorphicDiscriminator === VOID ? null : polymorphicDiscriminator;
  polyDescriptor = polyDescriptor === VOID ? null : polyDescriptor;
  AbstractJsonTreeDecoder.call(this, json, value, polymorphicDiscriminator);
  this.wy_1 = value;
  this.xy_1 = polyDescriptor;
  this.yy_1 = 0;
  this.zy_1 = false;
}
protoOf(JsonTreeDecoder).m1 = function () {
  return this.wy_1;
};
protoOf(JsonTreeDecoder).dj = function (descriptor) {
  while (this.yy_1 < descriptor.sg()) {
    var _unary__edvuaz = this.yy_1;
    this.yy_1 = _unary__edvuaz + 1 | 0;
    var name = this.dr(descriptor, _unary__edvuaz);
    var index = this.yy_1 - 1 | 0;
    this.zy_1 = false;
    var tmp;
    var tmp_0;
    // Inline function 'kotlin.collections.contains' call
    // Inline function 'kotlin.collections.containsKey' call
    var this_0 = this.m1();
    if ((isInterface(this_0, KtMap) ? this_0 : THROW_CCE()).n1(name)) {
      tmp_0 = true;
    } else {
      tmp_0 = absenceIsNull(this, descriptor, index);
    }
    if (tmp_0) {
      tmp = !this.by_1.ot_1 || !coerceInputValue_0(this, descriptor, index, name);
    } else {
      tmp = false;
    }
    if (tmp) {
      return index;
    }
  }
  return -1;
};
protoOf(JsonTreeDecoder).ai = function () {
  return !this.zy_1 && protoOf(AbstractJsonTreeDecoder).ai.call(this);
};
protoOf(JsonTreeDecoder).er = function (descriptor, index) {
  var strategy = namingStrategy(descriptor, this.yt());
  var baseName = descriptor.ug(index);
  if (strategy == null) {
    if (!this.by_1.st_1)
      return baseName;
    if (this.m1().q1().j1(baseName))
      return baseName;
  }
  var deserializationNamesMap_0 = deserializationNamesMap(this.yt(), descriptor);
  // Inline function 'kotlin.collections.find' call
  var tmp0 = this.m1().q1();
  var tmp$ret$1;
  $l$block: {
    // Inline function 'kotlin.collections.firstOrNull' call
    var _iterator__ex2g4s = tmp0.g();
    while (_iterator__ex2g4s.h()) {
      var element = _iterator__ex2g4s.i();
      if (deserializationNamesMap_0.p1(element) === index) {
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
  var fallbackName = strategy == null ? null : strategy.ev(descriptor, index, baseName);
  return fallbackName == null ? baseName : fallbackName;
};
protoOf(JsonTreeDecoder).ey = function (tag) {
  return getValue(this.m1(), tag);
};
protoOf(JsonTreeDecoder).oi = function (descriptor) {
  if (descriptor === this.xy_1) {
    var tmp = this.yt();
    var tmp2 = this.dy();
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var serialName = this.xy_1.og();
    if (!(tmp2 instanceof JsonObject)) {
      var tmp_0 = getKClass(JsonObject).h8();
      var tmp_1 = getKClassFromExpression(tmp2).h8();
      var tmp$ret$2 = this.kr();
      throw JsonDecodingException_0(-1, 'Expected ' + tmp_0 + ', but had ' + tmp_1 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$2, toString_0(tmp2));
    }
    return new JsonTreeDecoder(tmp, tmp2, this.ay_1, this.xy_1);
  }
  return protoOf(AbstractJsonTreeDecoder).oi.call(this, descriptor);
};
protoOf(JsonTreeDecoder).pi = function (descriptor) {
  var tmp;
  if (this.by_1.it_1) {
    tmp = true;
  } else {
    var tmp_0 = descriptor.pg();
    tmp = tmp_0 instanceof PolymorphicKind;
  }
  if (tmp)
    return Unit_instance;
  var strategy = namingStrategy(descriptor, this.yt());
  var tmp_1;
  if (strategy == null && !this.by_1.st_1) {
    tmp_1 = jsonCachedSerialNames(descriptor);
  } else if (!(strategy == null)) {
    tmp_1 = deserializationNamesMap(this.yt(), descriptor).q1();
  } else {
    var tmp_2 = jsonCachedSerialNames(descriptor);
    var tmp0_safe_receiver = get_schemaCache(this.yt()).dx(descriptor, get_JsonDeserializationNamesKey());
    // Inline function 'kotlin.collections.orEmpty' call
    var tmp0_elvis_lhs = tmp0_safe_receiver == null ? null : tmp0_safe_receiver.q1();
    var tmp$ret$0 = tmp0_elvis_lhs == null ? emptySet() : tmp0_elvis_lhs;
    tmp_1 = plus_0(tmp_2, tmp$ret$0);
  }
  var names = tmp_1;
  var _iterator__ex2g4s = this.m1().q1().g();
  while (_iterator__ex2g4s.h()) {
    var key = _iterator__ex2g4s.i();
    if (!names.j1(key) && !(key === this.ay_1)) {
      throw UnknownKeyException(key, this.m1().toString());
    }
  }
};
function JsonTreeListDecoder(json, value) {
  AbstractJsonTreeDecoder.call(this, json, value);
  this.gz_1 = value;
  this.hz_1 = this.gz_1.j();
  this.iz_1 = -1;
}
protoOf(JsonTreeListDecoder).m1 = function () {
  return this.gz_1;
};
protoOf(JsonTreeListDecoder).er = function (descriptor, index) {
  return index.toString();
};
protoOf(JsonTreeListDecoder).ey = function (tag) {
  return this.gz_1.k(toInt(tag));
};
protoOf(JsonTreeListDecoder).dj = function (descriptor) {
  while (this.iz_1 < (this.hz_1 - 1 | 0)) {
    this.iz_1 = this.iz_1 + 1 | 0;
    return this.iz_1;
  }
  return -1;
};
function JsonPrimitiveDecoder(json, value, polymorphicDiscriminator) {
  polymorphicDiscriminator = polymorphicDiscriminator === VOID ? null : polymorphicDiscriminator;
  AbstractJsonTreeDecoder.call(this, json, value, polymorphicDiscriminator);
  this.pz_1 = value;
  this.xr('primitive');
}
protoOf(JsonPrimitiveDecoder).m1 = function () {
  return this.pz_1;
};
protoOf(JsonPrimitiveDecoder).dj = function (descriptor) {
  return 0;
};
protoOf(JsonPrimitiveDecoder).ey = function (tag) {
  // Inline function 'kotlin.require' call
  if (!(tag === 'primitive')) {
    var message = "This input can only handle primitives with 'primitive' tag";
    throw IllegalArgumentException_init_$Create$(toString_0(message));
  }
  return this.pz_1;
};
function JsonTreeMapDecoder(json, value) {
  JsonTreeDecoder.call(this, json, value);
  this.a10_1 = value;
  this.b10_1 = toList(this.a10_1.q1());
  this.c10_1 = imul(this.b10_1.j(), 2);
  this.d10_1 = -1;
}
protoOf(JsonTreeMapDecoder).m1 = function () {
  return this.a10_1;
};
protoOf(JsonTreeMapDecoder).er = function (descriptor, index) {
  var i = index / 2 | 0;
  return this.b10_1.k(i);
};
protoOf(JsonTreeMapDecoder).dj = function (descriptor) {
  while (this.d10_1 < (this.c10_1 - 1 | 0)) {
    this.d10_1 = this.d10_1 + 1 | 0;
    return this.d10_1;
  }
  return -1;
};
protoOf(JsonTreeMapDecoder).ey = function (tag) {
  return (this.d10_1 % 2 | 0) === 0 ? JsonPrimitive_0(tag) : getValue(this.a10_1, tag);
};
protoOf(JsonTreeMapDecoder).pi = function (descriptor) {
};
function readPolymorphicJson(_this__u8e3s4, discriminator, element, deserializer) {
  return (new JsonTreeDecoder(_this__u8e3s4, element, discriminator, deserializer.gg())).ni(deserializer);
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
  this.qx_1 = begin;
  this.rx_1 = end;
}
function switchMode(_this__u8e3s4, desc) {
  var tmp0_subject = desc.pg();
  var tmp;
  if (tmp0_subject instanceof PolymorphicKind) {
    tmp = WriteMode_POLY_OBJ_getInstance();
  } else {
    if (equals(tmp0_subject, LIST_getInstance())) {
      tmp = WriteMode_LIST_getInstance();
    } else {
      if (equals(tmp0_subject, MAP_getInstance())) {
        // Inline function 'kotlinx.serialization.json.internal.selectMapMode' call
        var keyDescriptor = carrierDescriptor(desc.xg(0), _this__u8e3s4.bj());
        var keyKind = keyDescriptor.pg();
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
          if (_this__u8e3s4.ps_1.kt_1) {
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
  if (equals(_this__u8e3s4.pg(), CONTEXTUAL_getInstance())) {
    var tmp0_safe_receiver = getContextualDescriptor(module_0, _this__u8e3s4);
    var tmp1_elvis_lhs = tmp0_safe_receiver == null ? null : carrierDescriptor(tmp0_safe_receiver, module_0);
    tmp = tmp1_elvis_lhs == null ? _this__u8e3s4 : tmp1_elvis_lhs;
  } else if (_this__u8e3s4.rg()) {
    tmp = carrierDescriptor(_this__u8e3s4.xg(0), module_0);
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
  $this.e10(lastPosition, current);
  return appendEsc($this, current + 1 | 0);
}
function decodedString($this, lastPosition, currentPosition) {
  $this.e10(lastPosition, currentPosition);
  var result = $this.ft_1.toString();
  $this.ft_1.o9(0);
  return result;
}
function takePeeked($this) {
  // Inline function 'kotlin.also' call
  var this_0 = ensureNotNull($this.et_1);
  $this.et_1 = null;
  return this_0;
}
function wasUnquotedString($this) {
  return !(charSequenceGet($this.f10(), $this.ct_1 - 1 | 0) === _Char___init__impl__6a9atx(34));
}
function appendEsc($this, startPosition) {
  var currentPosition = startPosition;
  currentPosition = $this.g10(currentPosition);
  if (currentPosition === -1) {
    $this.av('Expected escape sequence to continue, got EOF');
  }
  var tmp = $this.f10();
  var _unary__edvuaz = currentPosition;
  currentPosition = _unary__edvuaz + 1 | 0;
  var currentChar = charSequenceGet(tmp, _unary__edvuaz);
  if (currentChar === _Char___init__impl__6a9atx(117)) {
    return appendHex($this, $this.f10(), currentPosition);
  }
  // Inline function 'kotlin.code' call
  var tmp$ret$0 = Char__toInt_impl_vasixd(currentChar);
  var c = escapeToChar(tmp$ret$0);
  if (c === _Char___init__impl__6a9atx(0)) {
    $this.av("Invalid escaped char '" + toString_1(currentChar) + "'");
  }
  $this.ft_1.x6(c);
  return currentPosition;
}
function appendHex($this, source, startPos) {
  if ((startPos + 4 | 0) >= charSequenceLength(source)) {
    $this.ct_1 = startPos;
    $this.h10();
    if (($this.ct_1 + 4 | 0) >= charSequenceLength(source)) {
      $this.av('Unexpected EOF during unicode escape');
    }
    return appendHex($this, source, $this.ct_1);
  }
  $this.ft_1.x6(numberToChar((((fromHexChar($this, source, startPos) << 12) + (fromHexChar($this, source, startPos + 1 | 0) << 8) | 0) + (fromHexChar($this, source, startPos + 2 | 0) << 4) | 0) + fromHexChar($this, source, startPos + 3 | 0) | 0));
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
    $this.av("Invalid toHexChar char '" + toString_1(character) + "' in unicode escape");
  }
  return tmp;
}
function consumeBoolean2($this, start) {
  var current = $this.g10(start);
  if (current >= charSequenceLength($this.f10()) || current === -1) {
    $this.av('EOF');
  }
  var tmp = $this.f10();
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
      $this.av("Expected valid boolean literal prefix, but had '" + $this.wv() + "'");
    }
  }
  return tmp_0;
}
function consumeBooleanLiteral($this, literalSuffix, current) {
  if ((charSequenceLength($this.f10()) - current | 0) < literalSuffix.length) {
    $this.av('Unexpected end of boolean literal');
  }
  var inductionVariable = 0;
  var last = charSequenceLength(literalSuffix) - 1 | 0;
  if (inductionVariable <= last)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      var expected = charCodeAt(literalSuffix, i);
      var actual = charSequenceGet($this.f10(), current + i | 0);
      // Inline function 'kotlin.code' call
      var tmp = Char__toInt_impl_vasixd(expected);
      // Inline function 'kotlin.code' call
      if (!(tmp === (Char__toInt_impl_vasixd(actual) | 32))) {
        $this.av("Expected valid boolean literal prefix, but had '" + $this.wv() + "'");
      }
    }
     while (inductionVariable <= last);
  $this.ct_1 = current + literalSuffix.length | 0;
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
  this.ct_1 = 0;
  this.dt_1 = new JsonPath();
  this.et_1 = null;
  this.ft_1 = StringBuilder_init_$Create$();
}
protoOf(AbstractJsonLexer).h10 = function () {
};
protoOf(AbstractJsonLexer).gx = function () {
  var current = this.i10();
  var source = this.f10();
  if (current >= charSequenceLength(source) || current === -1)
    return false;
  if (charSequenceGet(source, current) === _Char___init__impl__6a9atx(44)) {
    this.ct_1 = this.ct_1 + 1 | 0;
    return true;
  }
  return false;
};
protoOf(AbstractJsonLexer).j10 = function (c) {
  return c === _Char___init__impl__6a9atx(125) || c === _Char___init__impl__6a9atx(93) || (c === _Char___init__impl__6a9atx(58) || c === _Char___init__impl__6a9atx(44)) ? false : true;
};
protoOf(AbstractJsonLexer).gt = function () {
  var nextToken = this.yv();
  if (!(nextToken === 10)) {
    this.av('Expected EOF after parsing, but had ' + toString_1(charSequenceGet(this.f10(), this.ct_1 - 1 | 0)) + ' instead');
  }
};
protoOf(AbstractJsonLexer).sv = function (expected) {
  var token = this.yv();
  if (!(token === expected)) {
    this.k10(expected);
  }
  return token;
};
protoOf(AbstractJsonLexer).l10 = function (expected) {
  if (this.ct_1 > 0 && expected === _Char___init__impl__6a9atx(34)) {
    var tmp$ret$0;
    $l$block: {
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.withPositionRollback' call
      var snapshot = this.ct_1;
      try {
        this.ct_1 = this.ct_1 - 1 | 0;
        tmp$ret$0 = this.wv();
        break $l$block;
      }finally {
        this.ct_1 = snapshot;
      }
    }
    var inputLiteral = tmp$ret$0;
    if (inputLiteral === 'null') {
      this.zu("Expected string literal but 'null' literal was found", this.ct_1 - 1 | 0, "Use 'coerceInputValues = true' in 'Json {}' builder to coerce nulls if property has a default value.");
    }
  }
  this.k10(charToTokenClass(expected));
};
protoOf(AbstractJsonLexer).m10 = function (expectedToken, wasConsumed) {
  var expected = tokenDescription(expectedToken);
  var position = wasConsumed ? this.ct_1 - 1 | 0 : this.ct_1;
  var s = this.ct_1 === charSequenceLength(this.f10()) || position < 0 ? 'EOF' : toString_1(charSequenceGet(this.f10(), position));
  this.av('Expected ' + expected + ", but had '" + s + "' instead", position);
};
protoOf(AbstractJsonLexer).k10 = function (expectedToken, wasConsumed, $super) {
  wasConsumed = wasConsumed === VOID ? true : wasConsumed;
  return $super === VOID ? this.m10(expectedToken, wasConsumed) : $super.m10.call(this, expectedToken, wasConsumed);
};
protoOf(AbstractJsonLexer).tv = function () {
  var source = this.f10();
  var cpos = this.ct_1;
  $l$loop_0: while (true) {
    cpos = this.g10(cpos);
    if (cpos === -1)
      break $l$loop_0;
    var ch = charSequenceGet(source, cpos);
    if (ch === _Char___init__impl__6a9atx(32) || ch === _Char___init__impl__6a9atx(10) || ch === _Char___init__impl__6a9atx(13) || ch === _Char___init__impl__6a9atx(9)) {
      cpos = cpos + 1 | 0;
      continue $l$loop_0;
    }
    this.ct_1 = cpos;
    return charToTokenClass(ch);
  }
  this.ct_1 = cpos;
  return 10;
};
protoOf(AbstractJsonLexer).hx = function (doConsume) {
  var current = this.i10();
  current = this.g10(current);
  var len = charSequenceLength(this.f10()) - current | 0;
  if (len < 4 || current === -1)
    return false;
  var inductionVariable = 0;
  if (inductionVariable <= 3)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      if (!(charCodeAt('null', i) === charSequenceGet(this.f10(), current + i | 0)))
        return false;
    }
     while (inductionVariable <= 3);
  if (len > 4 && charToTokenClass(charSequenceGet(this.f10(), current + 4 | 0)) === 0)
    return false;
  if (doConsume) {
    this.ct_1 = current + 4 | 0;
  }
  return true;
};
protoOf(AbstractJsonLexer).sx = function (doConsume, $super) {
  doConsume = doConsume === VOID ? true : doConsume;
  return $super === VOID ? this.hx(doConsume) : $super.hx.call(this, doConsume);
};
protoOf(AbstractJsonLexer).ix = function (isLenient) {
  var token = this.tv();
  var tmp;
  if (isLenient) {
    if (!(token === 1) && !(token === 0))
      return null;
    tmp = this.wv();
  } else {
    if (!(token === 1))
      return null;
    tmp = this.vv();
  }
  var string = tmp;
  this.et_1 = string;
  return string;
};
protoOf(AbstractJsonLexer).n10 = function () {
  this.et_1 = null;
};
protoOf(AbstractJsonLexer).o10 = function (startPos, endPos) {
  // Inline function 'kotlin.text.substring' call
  var this_0 = this.f10();
  return toString_0(charSequenceSubSequence(this_0, startPos, endPos));
};
protoOf(AbstractJsonLexer).vv = function () {
  if (!(this.et_1 == null)) {
    return takePeeked(this);
  }
  return this.lx();
};
protoOf(AbstractJsonLexer).consumeString2 = function (source, startPosition, current) {
  var currentPosition = current;
  var lastPosition = startPosition;
  var char = charSequenceGet(source, currentPosition);
  var usedAppend = false;
  while (!(char === _Char___init__impl__6a9atx(34))) {
    if (char === _Char___init__impl__6a9atx(92)) {
      usedAppend = true;
      currentPosition = this.g10(appendEscape(this, lastPosition, currentPosition));
      if (currentPosition === -1) {
        this.av('Unexpected EOF', currentPosition);
      }
      lastPosition = currentPosition;
    } else {
      currentPosition = currentPosition + 1 | 0;
      if (currentPosition >= charSequenceLength(source)) {
        usedAppend = true;
        this.e10(lastPosition, currentPosition);
        currentPosition = this.g10(currentPosition);
        if (currentPosition === -1) {
          this.av('Unexpected EOF', currentPosition);
        }
        lastPosition = currentPosition;
      }
    }
    char = charSequenceGet(source, currentPosition);
  }
  var tmp;
  if (!usedAppend) {
    tmp = this.o10(lastPosition, currentPosition);
  } else {
    tmp = decodedString(this, lastPosition, currentPosition);
  }
  var string = tmp;
  this.ct_1 = currentPosition + 1 | 0;
  return string;
};
protoOf(AbstractJsonLexer).mx = function () {
  var result = this.wv();
  if (result === 'null' && wasUnquotedString(this)) {
    this.av("Unexpected 'null' value instead of string literal");
  }
  return result;
};
protoOf(AbstractJsonLexer).wv = function () {
  if (!(this.et_1 == null)) {
    return takePeeked(this);
  }
  var current = this.i10();
  if (current >= charSequenceLength(this.f10()) || current === -1) {
    this.av('EOF', current);
  }
  var token = charToTokenClass(charSequenceGet(this.f10(), current));
  if (token === 1) {
    return this.vv();
  }
  if (!(token === 0)) {
    this.av('Expected beginning of the string, but got ' + toString_1(charSequenceGet(this.f10(), current)));
  }
  var usedAppend = false;
  while (charToTokenClass(charSequenceGet(this.f10(), current)) === 0) {
    current = current + 1 | 0;
    if (current >= charSequenceLength(this.f10())) {
      usedAppend = true;
      this.e10(this.ct_1, current);
      var eof = this.g10(current);
      if (eof === -1) {
        this.ct_1 = current;
        return decodedString(this, 0, 0);
      } else {
        current = eof;
      }
    }
  }
  var tmp;
  if (!usedAppend) {
    tmp = this.o10(this.ct_1, current);
  } else {
    tmp = decodedString(this, this.ct_1, current);
  }
  var result = tmp;
  this.ct_1 = current;
  return result;
};
protoOf(AbstractJsonLexer).e10 = function (fromIndex, toIndex) {
  this.ft_1.j9(this.f10(), fromIndex, toIndex);
};
protoOf(AbstractJsonLexer).kx = function (allowLenientStrings) {
  // Inline function 'kotlin.collections.mutableListOf' call
  var tokenStack = ArrayList_init_$Create$();
  var lastToken = this.tv();
  if (!(lastToken === 8) && !(lastToken === 6)) {
    this.wv();
    return Unit_instance;
  }
  $l$loop: while (true) {
    lastToken = this.tv();
    if (lastToken === 1) {
      if (allowLenientStrings)
        this.wv();
      else
        this.lx();
      continue $l$loop;
    }
    var tmp0_subject = lastToken;
    if (tmp0_subject === 8 || tmp0_subject === 6) {
      tokenStack.e(lastToken);
    } else if (tmp0_subject === 9) {
      if (!(last(tokenStack) === 8))
        throw JsonDecodingException_0(this.ct_1, 'found ] instead of } at path: ' + this.dt_1.toString(), this.f10());
      removeLast(tokenStack);
    } else if (tmp0_subject === 7) {
      if (!(last(tokenStack) === 6))
        throw JsonDecodingException_0(this.ct_1, 'found } instead of ] at path: ' + this.dt_1.toString(), this.f10());
      removeLast(tokenStack);
    } else if (tmp0_subject === 10) {
      this.av('Unexpected end of input due to malformed JSON during ignoring unknown keys');
    }
    this.yv();
    if (tokenStack.j() === 0)
      return Unit_instance;
  }
};
protoOf(AbstractJsonLexer).toString = function () {
  return "JsonReader(source='" + toString_0(this.f10()) + "', currentPosition=" + this.ct_1 + ')';
};
protoOf(AbstractJsonLexer).jx = function (key) {
  var processed = this.o10(0, this.ct_1);
  var lastIndexOf_0 = lastIndexOf(processed, key);
  this.zu("Encountered an unknown key '" + key + "'", lastIndexOf_0, "Use 'ignoreUnknownKeys = true' in 'Json {}' builder to ignore unknown keys.");
};
protoOf(AbstractJsonLexer).zu = function (message, position, hint) {
  var tmp;
  // Inline function 'kotlin.text.isEmpty' call
  if (charSequenceLength(hint) === 0) {
    tmp = '';
  } else {
    tmp = '\n' + hint;
  }
  var hintMessage = tmp;
  throw JsonDecodingException_0(position, message + ' at path: ' + this.dt_1.nv() + hintMessage, this.f10());
};
protoOf(AbstractJsonLexer).av = function (message, position, hint, $super) {
  position = position === VOID ? this.ct_1 : position;
  hint = hint === VOID ? '' : hint;
  return $super === VOID ? this.zu(message, position, hint) : $super.zu.call(this, message, position, hint);
};
protoOf(AbstractJsonLexer).ju = function () {
  var current = this.i10();
  current = this.g10(current);
  if (current >= charSequenceLength(this.f10()) || current === -1) {
    this.av('EOF');
  }
  var tmp;
  if (charSequenceGet(this.f10(), current) === _Char___init__impl__6a9atx(34)) {
    current = current + 1 | 0;
    if (current === charSequenceLength(this.f10())) {
      this.av('EOF');
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
  $l$loop_4: while (!(current === charSequenceLength(this.f10()))) {
    var ch = charSequenceGet(this.f10(), current);
    if ((ch === _Char___init__impl__6a9atx(101) || ch === _Char___init__impl__6a9atx(69)) && !hasExponent) {
      if (current === start) {
        this.av('Unexpected symbol ' + toString_1(ch) + ' in numeric literal');
      }
      isExponentPositive = true;
      hasExponent = true;
      current = current + 1 | 0;
      continue $l$loop_4;
    }
    if (ch === _Char___init__impl__6a9atx(45) && hasExponent) {
      if (current === start) {
        this.av("Unexpected symbol '-' in numeric literal");
      }
      isExponentPositive = false;
      current = current + 1 | 0;
      continue $l$loop_4;
    }
    if (ch === _Char___init__impl__6a9atx(43) && hasExponent) {
      if (current === start) {
        this.av("Unexpected symbol '+' in numeric literal");
      }
      isExponentPositive = true;
      current = current + 1 | 0;
      continue $l$loop_4;
    }
    if (ch === _Char___init__impl__6a9atx(45)) {
      if (!(current === start)) {
        this.av("Unexpected symbol '-' in numeric literal");
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
      this.av("Unexpected symbol '" + toString_1(ch) + "' in numeric literal");
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
      this.av('Numeric value overflow');
    }
  }
  var hasChars = !(current === start);
  if (start === current || (isNegative && start === (current - 1 | 0))) {
    this.av('Expected numeric literal');
  }
  if (hasQuotation) {
    if (!hasChars) {
      this.av('EOF');
    }
    if (!(charSequenceGet(this.f10(), current) === _Char___init__impl__6a9atx(34))) {
      this.av('Expected closing quotation mark');
    }
    current = current + 1 | 0;
  }
  this.ct_1 = current;
  if (hasExponent) {
    var doubleAccumulator = toNumber(accumulator) * consumeNumericLiteral$calculateExponent(exponentAccumulator, isExponentPositive);
    if (doubleAccumulator > toNumber(new Long(-1, 2147483647)) || doubleAccumulator < toNumber(new Long(0, -2147483648))) {
      this.av('Numeric value overflow');
    }
    // Inline function 'kotlin.math.floor' call
    if (!(Math.floor(doubleAccumulator) === doubleAccumulator)) {
      this.av("Can't convert " + doubleAccumulator + ' to Long');
    }
    accumulator = numberToLong(doubleAccumulator);
  }
  var tmp_0;
  if (isNegative) {
    tmp_0 = accumulator;
  } else if (!equalsLong(accumulator, new Long(0, -2147483648))) {
    tmp_0 = negate(accumulator);
  } else {
    this.av('Numeric value overflow');
  }
  return tmp_0;
};
protoOf(AbstractJsonLexer).tx = function () {
  var current = this.i10();
  if (current === charSequenceLength(this.f10())) {
    this.av('EOF');
  }
  var tmp;
  if (charSequenceGet(this.f10(), current) === _Char___init__impl__6a9atx(34)) {
    current = current + 1 | 0;
    tmp = true;
  } else {
    tmp = false;
  }
  var hasQuotation = tmp;
  var result = consumeBoolean2(this, current);
  if (hasQuotation) {
    if (this.ct_1 === charSequenceLength(this.f10())) {
      this.av('EOF');
    }
    if (!(charSequenceGet(this.f10(), this.ct_1) === _Char___init__impl__6a9atx(34))) {
      this.av('Expected closing quotation mark');
    }
    this.ct_1 = this.ct_1 + 1 | 0;
  }
  return result;
};
function charToTokenClass(c) {
  var tmp;
  // Inline function 'kotlin.code' call
  if (Char__toInt_impl_vasixd(c) < 126) {
    var tmp_0 = CharMappings_getInstance().q10_1;
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
  return c < 117 ? CharMappings_getInstance().p10_1[c] : _Char___init__impl__6a9atx(0);
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
    $this.p10_1[tmp$ret$0] = numberToChar(c);
  }
}
function initC2ESC_0($this, c, esc) {
  // Inline function 'kotlin.code' call
  var tmp$ret$0 = Char__toInt_impl_vasixd(c);
  return initC2ESC($this, tmp$ret$0, esc);
}
function initC2TC($this, c, cl) {
  $this.q10_1[c] = cl;
}
function initC2TC_0($this, c, cl) {
  // Inline function 'kotlin.code' call
  var tmp$ret$0 = Char__toInt_impl_vasixd(c);
  return initC2TC($this, tmp$ret$0, cl);
}
function CharMappings() {
  CharMappings_instance = this;
  this.p10_1 = charArray(117);
  this.q10_1 = new Int8Array(126);
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
protoOf(StringJsonLexerWithComments).yv = function () {
  var source = this.f10();
  var cpos = this.i10();
  if (cpos >= source.length || cpos === -1)
    return 10;
  this.ct_1 = cpos + 1 | 0;
  return charToTokenClass(charCodeAt(source, cpos));
};
protoOf(StringJsonLexerWithComments).uv = function () {
  var current = this.i10();
  if (current >= this.f10().length || current === -1)
    return false;
  return this.j10(charCodeAt(this.f10(), current));
};
protoOf(StringJsonLexerWithComments).fx = function (expected) {
  var source = this.f10();
  var current = this.i10();
  if (current >= source.length || current === -1) {
    this.ct_1 = -1;
    this.l10(expected);
  }
  var c = charCodeAt(source, current);
  this.ct_1 = current + 1 | 0;
  if (c === expected)
    return Unit_instance;
  else {
    this.l10(expected);
  }
};
protoOf(StringJsonLexerWithComments).tv = function () {
  var source = this.f10();
  var cpos = this.i10();
  if (cpos >= source.length || cpos === -1)
    return 10;
  this.ct_1 = cpos;
  return charToTokenClass(charCodeAt(source, cpos));
};
protoOf(StringJsonLexerWithComments).i10 = function () {
  var current = this.ct_1;
  if (current === -1)
    return current;
  var source = this.f10();
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
          this.ct_1 = source.length;
          this.av('Expected end of the block comment: "*/", but had EOF instead');
        } else {
          current = current + 2 | 0;
        }
        continue $l$loop_1;
      }
    }
    break $l$loop_1;
  }
  this.ct_1 = current;
  return current;
};
function StringJsonLexer(source) {
  AbstractJsonLexer.call(this);
  this.a11_1 = source;
}
protoOf(StringJsonLexer).f10 = function () {
  return this.a11_1;
};
protoOf(StringJsonLexer).g10 = function (position) {
  return position < this.f10().length ? position : -1;
};
protoOf(StringJsonLexer).yv = function () {
  var source = this.f10();
  var cpos = this.ct_1;
  $l$loop: while (!(cpos === -1) && cpos < source.length) {
    var _unary__edvuaz = cpos;
    cpos = _unary__edvuaz + 1 | 0;
    var c = charCodeAt(source, _unary__edvuaz);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.isWs' call
    if (c === _Char___init__impl__6a9atx(32) || c === _Char___init__impl__6a9atx(10) || c === _Char___init__impl__6a9atx(13) || c === _Char___init__impl__6a9atx(9))
      continue $l$loop;
    this.ct_1 = cpos;
    return charToTokenClass(c);
  }
  this.ct_1 = source.length;
  return 10;
};
protoOf(StringJsonLexer).uv = function () {
  var current = this.ct_1;
  if (current === -1)
    return false;
  var source = this.f10();
  $l$loop: while (current < source.length) {
    var c = charCodeAt(source, current);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.isWs' call
    if (c === _Char___init__impl__6a9atx(32) || c === _Char___init__impl__6a9atx(10) || c === _Char___init__impl__6a9atx(13) || c === _Char___init__impl__6a9atx(9)) {
      current = current + 1 | 0;
      continue $l$loop;
    }
    this.ct_1 = current;
    return this.j10(c);
  }
  this.ct_1 = current;
  return false;
};
protoOf(StringJsonLexer).i10 = function () {
  var current = this.ct_1;
  if (current === -1)
    return current;
  var source = this.f10();
  $l$loop: while (current < source.length) {
    var c = charCodeAt(source, current);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.isWs' call
    if (c === _Char___init__impl__6a9atx(32) || c === _Char___init__impl__6a9atx(10) || c === _Char___init__impl__6a9atx(13) || c === _Char___init__impl__6a9atx(9)) {
      current = current + 1 | 0;
    } else {
      break $l$loop;
    }
  }
  this.ct_1 = current;
  return current;
};
protoOf(StringJsonLexer).fx = function (expected) {
  if (this.ct_1 === -1) {
    this.l10(expected);
  }
  var source = this.f10();
  var cpos = this.ct_1;
  $l$loop: while (cpos < source.length) {
    var _unary__edvuaz = cpos;
    cpos = _unary__edvuaz + 1 | 0;
    var c = charCodeAt(source, _unary__edvuaz);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.isWs' call
    if (c === _Char___init__impl__6a9atx(32) || c === _Char___init__impl__6a9atx(10) || c === _Char___init__impl__6a9atx(13) || c === _Char___init__impl__6a9atx(9))
      continue $l$loop;
    this.ct_1 = cpos;
    if (c === expected)
      return Unit_instance;
    this.l10(expected);
  }
  this.ct_1 = -1;
  this.l10(expected);
};
protoOf(StringJsonLexer).lx = function () {
  this.fx(_Char___init__impl__6a9atx(34));
  var current = this.ct_1;
  var closingQuote = indexOf_0(this.f10(), _Char___init__impl__6a9atx(34), current);
  if (closingQuote === -1) {
    this.wv();
    this.m10(1, false);
  }
  var inductionVariable = current;
  if (inductionVariable < closingQuote)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      if (charCodeAt(this.f10(), i) === _Char___init__impl__6a9atx(92)) {
        return this.consumeString2(this.f10(), this.ct_1, i);
      }
    }
     while (inductionVariable < closingQuote);
  this.ct_1 = closingQuote + 1 | 0;
  return substring(this.f10(), current, closingQuote);
};
protoOf(StringJsonLexer).nx = function (keyToMatch, isLenient) {
  var positionSnapshot = this.ct_1;
  try {
    if (!(this.yv() === 6))
      return null;
    var firstKey = this.ix(isLenient);
    if (!(firstKey === keyToMatch))
      return null;
    this.n10();
    if (!(this.yv() === 5))
      return null;
    return this.ix(isLenient);
  }finally {
    this.ct_1 = positionSnapshot;
    this.n10();
  }
};
function StringJsonLexer_0(json, source) {
  return !json.ps_1.wt_1 ? new StringJsonLexer(source) : new StringJsonLexerWithComments(source);
}
function get_schemaCache(_this__u8e3s4) {
  return _this__u8e3s4.rs_1;
}
function createMapForCache(initialCapacity) {
  return HashMap_init_$Create$(initialCapacity);
}
//region block: post-declaration
protoOf(defer$1).qg = get_isNullable;
protoOf(defer$1).rg = get_isInline;
protoOf(defer$1).tg = get_annotations;
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


