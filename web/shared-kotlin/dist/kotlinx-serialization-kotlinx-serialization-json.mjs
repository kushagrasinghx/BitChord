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
  serializer2lw83vwvpnyms as serializer_0,
  get_isNullable36pbikm8xb7bz as get_isNullable,
  get_isInline5x26qrhi9qs6 as get_isInline,
  get_annotationshjxdbdcl8kmv as get_annotations,
  Encoderqvmrpqtq8hnu as Encoder,
  CompositeEncoderknecpkexzn3v as CompositeEncoder,
  ElementMarker33ojvsajwmzts as ElementMarker,
  SerializationExceptioneqrdve3ts2n9 as SerializationException,
  SerializationException_init_$Init$16fnhi19wdhkq as SerializationException_init_$Init$,
  CLASS_getInstance25nhr3t715wdo as CLASS_getInstance,
  LIST_getInstance2leq5bbahk4rz as LIST_getInstance,
  SealedClassSerializeriwipiibk55zc as SealedClassSerializer,
  jsonCachedSerialNameslxufy2gu43jt as jsonCachedSerialNames,
  ENUMlmq49cvwy4ow as ENUM,
  PrimitiveKindndgbuh6is7ze as PrimitiveKind,
  PolymorphicKindla9gurooefwb as PolymorphicKind,
  CONTEXTUAL_getInstance2j6ylx8hc4yv2 as CONTEXTUAL_getInstance,
  MAP_getInstance141vh6u7faopd as MAP_getInstance,
  contextual3hpp1gupsu4al as contextual,
  SerializersModuleCollector3dddz14wd7brg as SerializersModuleCollector,
  AbstractDecoder35guh02ubh2hm as AbstractDecoder,
  AbstractPolymorphicSerializer1ccxwp48nfy58 as AbstractPolymorphicSerializer,
  DeserializationStrategy1z3z5pj9f7zc8 as DeserializationStrategy,
  findPolymorphicSerializer1nm87hvemahcj as findPolymorphicSerializer,
  MissingFieldException24tqif29emcmi as MissingFieldException,
  AbstractEncoder2gxtu3xmy3f8j as AbstractEncoder,
  OBJECT_getInstance167untwb9admi as OBJECT_getInstance,
  findPolymorphicSerializerk638ixyjovk5 as findPolymorphicSerializer_0,
  SerializationStrategyh6ouydnm6hci as SerializationStrategy,
  serializer3ikrxnm8b29d6 as serializer_1,
  serializer36584sjyg5661 as serializer_2,
  serializer1q7c5q67ysppr as serializer_3,
  NamedValueDecoderzk26ztf92xbq as NamedValueDecoder,
  getContextualDescriptor2n1gf3b895yb8 as getContextualDescriptor,
} from './kotlinx-serialization-kotlinx-serialization-core.mjs';
import {
  protoOf180f3jzyo7rfj as protoOf,
  initMetadataForObject1cxne3s9w65el as initMetadataForObject,
  VOID3gxj6tk5isa35 as VOID,
  Unit_instancev9v8hjid95df as Unit_instance,
  initMetadataForClassbxx6q50dy2s7 as initMetadataForClass,
  toString1pkumu07cwy4m as toString,
  IllegalArgumentException_init_$Create$i4126l9nkbuo as IllegalArgumentException_init_$Create$,
  charSequenceLength3278n89t01tmv as charSequenceLength,
  charSequenceGet1vxk1y5n17t1z as charSequenceGet,
  _Char___init__impl__6a9atx2gndcvjvc5pke as _Char___init__impl__6a9atx,
  equals2au1ep9vhcato as equals,
  toString30pk9tzaqopn as toString_0,
  Enum3alwj03lh1n41 as Enum,
  initMetadataForInterface1egvbzx539z91 as initMetadataForInterface,
  initMetadataForCompanion1wyw17z38v6ac as initMetadataForCompanion,
  StringBuilder_init_$Create$3ds9zx2kx9u4d as StringBuilder_init_$Create$,
  hashCodeq5arwsb9dgti as hashCode,
  joinToString1cxrrlmo0chqs as joinToString,
  THROW_CCE2g6jy02ryeudk as THROW_CCE,
  KtMap140uvy3s5zad8 as KtMap,
  KtList3hktaavzmj137 as KtList,
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
  noWhenBranchMatchedException2a6r7ubxgky5j as noWhenBranchMatchedException,
  toLongOrNullutqivezb0wx1 as toLongOrNull,
  toULongOrNullojoyxi0i9tgj as toULongOrNull,
  ULong3f9k7s38t3rfp as ULong,
  Companion_getInstance2ln0tj1crqgxz as Companion_getInstance,
  _ULong___get_data__impl__fggpzb2bbucljk081qt as _ULong___get_data__impl__fggpzb,
  toDoubleOrNullkxwozihadygj as toDoubleOrNull,
  toBooleanStrictOrNull2j0md398tkvbj as toBooleanStrictOrNull,
  IllegalStateException_init_$Create$1h4bcfod21ri0 as IllegalStateException_init_$Create$,
  KProperty1ca4yb4wlo496 as KProperty1,
  getPropertyCallableRef3hckxc0xueiaj as getPropertyCallableRef,
  lazy2hsh8ze7j6ikd as lazy,
  fromInt1lka3ktyu79a4 as fromInt,
  _UInt___init__impl__l7qpdl2xbmh7to8lpd7 as _UInt___init__impl__l7qpdl,
  UInt__toString_impl_dbgl211hnbf9xsikfsf as UInt__toString_impl_dbgl21,
  _ULong___init__impl__c78o9k4z3cjzifecv6 as _ULong___init__impl__c78o9k,
  ULong__toString_impl_f9au7k3n8y2imw2kac3 as ULong__toString_impl_f9au7k,
  _UByte___init__impl__g9hnc43bwrjlqz9k9id as _UByte___init__impl__g9hnc4,
  UByte__toString_impl_v72jg35cd2pgynmah7 as UByte__toString_impl_v72jg,
  _UShort___init__impl__jigrne3vu9egv7mch1k as _UShort___init__impl__jigrne,
  UShort__toString_impl_edaoee1ut8mfnxr9a1q as UShort__toString_impl_edaoee,
  captureStack1fzi4aczwc4hg as captureStack,
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
  equalsLong28bsrfhwvd686 as equalsLong,
  convertToShortvtefcftm709c as convertToShort,
  IllegalArgumentException2asla15b5jaob as IllegalArgumentException,
  isFinite1tx0gn65nl9tj as isFinite,
  isFinite2t9l5a275mxm6 as isFinite_0,
  charCodeAt1yspne1d8erbm as charCodeAt,
  toUInt21lx0mz8wkp7c as toUInt,
  _UInt___get_data__impl__f0vqqw2j5mttix8w0l0 as _UInt___get_data__impl__f0vqqw,
  toULong266mnyksbttkw as toULong,
  toUByteh6p4wmqswkrs as toUByte,
  _UByte___get_data__impl__jof9qr23gcv767stjfh as _UByte___get_data__impl__jof9qr,
  toUShort7yqspfnhrot4 as toUShort,
  _UShort___get_data__impl__g0245313795p1x5hw5 as _UShort___get_data__impl__g0245,
  objectCreate1ve4bgxiu4x98 as objectCreate,
  toString22rbkbhdb5cl2 as toString_1,
  Companion_getInstance1339zuieiizg3 as Companion_getInstance_0,
  Companion_getInstance2utgi65n0nnqj as Companion_getInstance_1,
  Companion_getInstance3rx8u2n07bmtm as Companion_getInstance_2,
  setOf45ia9pnfhe90 as setOf,
  Char__toInt_impl_vasixdnyockrdnmht5 as Char__toInt_impl_vasixd,
  numberToChar93r9buh19yek as numberToChar,
  equals2v6cggk171b6e as equals_0,
  toByte4i43936u611k as toByte,
  startsWith26w8qjqapeeq6 as startsWith,
  toShort36kaw0zjdq3ex as toShort,
  single29ec4rh52687r as single,
  Char19o2r8palgjof as Char,
  emptySetcxexqki71qfa as emptySet,
  plus1ogy4liedzq5j as plus_0,
  toInt2q8uldh7sc951 as toInt,
  toList3jhuyej2anx2q as toList,
  enumEntries20mr21zbe3az4 as enumEntries,
  toNumberlmbpvqo27r53 as toNumber,
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
  StringBuilder_init_$Create$3pdhqn0ol6juo as StringBuilder_init_$Create$_0,
  HashMap_init_$Create$j8c89adh6ge5 as HashMap_init_$Create$,
} from './kotlin-kotlin-stdlib.mjs';
//region block: imports
var imul = Math.imul;
//endregion
//region block: pre-declaration
initMetadataForClass(Json, 'Json');
initMetadataForObject(Default, 'Default', VOID, Json);
initMetadataForClass(JsonBuilder, 'JsonBuilder');
initMetadataForClass(JsonImpl, 'JsonImpl', VOID, Json);
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
initMetadataForInterface(JsonEncoder, 'JsonEncoder', VOID, VOID, [Encoder, CompositeEncoder]);
initMetadataForClass(Composer, 'Composer');
initMetadataForClass(ComposerForUnsignedNumbers, 'ComposerForUnsignedNumbers', VOID, Composer);
initMetadataForClass(ComposerForUnquotedLiterals, 'ComposerForUnquotedLiterals', VOID, Composer);
initMetadataForClass(ComposerWithPrettyPrint, 'ComposerWithPrettyPrint', VOID, Composer);
initMetadataForClass(JsonElementMarker, 'JsonElementMarker');
initMetadataForClass(JsonException, 'JsonException', VOID, SerializationException);
initMetadataForClass(JsonDecodingException, 'JsonDecodingException', VOID, JsonException);
initMetadataForClass(JsonEncodingException, 'JsonEncodingException', VOID, JsonException);
initMetadataForObject(Tombstone, 'Tombstone');
initMetadataForClass(JsonPath, 'JsonPath', JsonPath);
initMetadataForLambda(JsonTreeReader$readDeepRecursive$slambda, CoroutineImpl, VOID, [2]);
initMetadataForCoroutine($readObjectCOROUTINE$, CoroutineImpl);
initMetadataForClass(JsonTreeReader, 'JsonTreeReader', VOID, VOID, VOID, [1]);
initMetadataForClass(PolymorphismValidator, 'PolymorphismValidator', VOID, VOID, [SerializersModuleCollector]);
initMetadataForClass(Key, 'Key', Key);
initMetadataForClass(DescriptorSchemaCache, 'DescriptorSchemaCache', DescriptorSchemaCache);
initMetadataForClass(DiscriminatorHolder, 'DiscriminatorHolder');
initMetadataForClass(StreamingJsonDecoder, 'StreamingJsonDecoder', VOID, AbstractDecoder, [JsonDecoder]);
initMetadataForClass(JsonDecoderForUnsignedTypes, 'JsonDecoderForUnsignedTypes', VOID, AbstractDecoder);
initMetadataForClass(StreamingJsonEncoder, 'StreamingJsonEncoder', VOID, AbstractEncoder, [JsonEncoder]);
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
initMetadataForClass(JsonToStringWriter, 'JsonToStringWriter', JsonToStringWriter);
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
  this.bz_1 = configuration;
  this.cz_1 = serializersModule;
  this.dz_1 = new DescriptorSchemaCache();
}
protoOf(Json).ul = function () {
  return this.cz_1;
};
protoOf(Json).ez = function (serializer, value) {
  var result = new JsonToStringWriter();
  try {
    encodeByWriter(this, result, serializer, value);
    return result.toString();
  }finally {
    result.iz();
  }
};
protoOf(Json).fz = function (deserializer, string) {
  var lexer = StringJsonLexer_0(this, string);
  var input = new StreamingJsonDecoder(this, WriteMode_OBJ_getInstance(), lexer, deserializer.si(), null);
  var result = input.el(deserializer);
  lexer.vz();
  return result;
};
protoOf(Json).gz = function (string) {
  return this.fz(JsonElementSerializer_getInstance(), string);
};
function Json_0(from, builderAction) {
  from = from === VOID ? Default_getInstance() : from;
  var builder = new JsonBuilder(from);
  builderAction(builder);
  var conf = builder.o10();
  return new JsonImpl(conf, builder.n10_1);
}
function JsonBuilder(json) {
  this.wz_1 = json.bz_1.p10_1;
  this.xz_1 = json.bz_1.u10_1;
  this.yz_1 = json.bz_1.q10_1;
  this.zz_1 = json.bz_1.r10_1;
  this.a10_1 = json.bz_1.t10_1;
  this.b10_1 = json.bz_1.v10_1;
  this.c10_1 = json.bz_1.w10_1;
  this.d10_1 = json.bz_1.y10_1;
  this.e10_1 = json.bz_1.f11_1;
  this.f10_1 = json.bz_1.a11_1;
  this.g10_1 = json.bz_1.b11_1;
  this.h10_1 = json.bz_1.c11_1;
  this.i10_1 = json.bz_1.d11_1;
  this.j10_1 = json.bz_1.e11_1;
  this.k10_1 = json.bz_1.z10_1;
  this.l10_1 = json.bz_1.s10_1;
  this.m10_1 = json.bz_1.x10_1;
  this.n10_1 = json.ul();
}
protoOf(JsonBuilder).o10 = function () {
  if (this.m10_1) {
    // Inline function 'kotlin.require' call
    if (!(this.d10_1 === 'type')) {
      var message = 'Class discriminator should not be specified when array polymorphism is specified';
      throw IllegalArgumentException_init_$Create$(toString(message));
    }
    // Inline function 'kotlin.require' call
    if (!this.e10_1.equals(ClassDiscriminatorMode_POLYMORPHIC_getInstance())) {
      var message_0 = 'useArrayPolymorphism option can only be used if classDiscriminatorMode in a default POLYMORPHIC state.';
      throw IllegalArgumentException_init_$Create$(toString(message_0));
    }
  }
  if (!this.a10_1) {
    // Inline function 'kotlin.require' call
    if (!(this.b10_1 === '    ')) {
      var message_1 = 'Indent should not be specified when default printing mode is used';
      throw IllegalArgumentException_init_$Create$(toString(message_1));
    }
  } else if (!(this.b10_1 === '    ')) {
    var tmp0 = this.b10_1;
    var tmp$ret$6;
    $l$block: {
      // Inline function 'kotlin.text.all' call
      var inductionVariable = 0;
      while (inductionVariable < charSequenceLength(tmp0)) {
        var element = charSequenceGet(tmp0, inductionVariable);
        inductionVariable = inductionVariable + 1 | 0;
        if (!(element === _Char___init__impl__6a9atx(32) || element === _Char___init__impl__6a9atx(9) || element === _Char___init__impl__6a9atx(13) || element === _Char___init__impl__6a9atx(10))) {
          tmp$ret$6 = false;
          break $l$block;
        }
      }
      tmp$ret$6 = true;
    }
    var allWhitespaces = tmp$ret$6;
    // Inline function 'kotlin.require' call
    if (!allWhitespaces) {
      var message_2 = 'Only whitespace, tab, newline and carriage return are allowed as pretty print symbols. Had ' + this.b10_1;
      throw IllegalArgumentException_init_$Create$(toString(message_2));
    }
  }
  return new JsonConfiguration(this.wz_1, this.yz_1, this.zz_1, this.l10_1, this.a10_1, this.xz_1, this.b10_1, this.c10_1, this.m10_1, this.d10_1, this.k10_1, this.f10_1, this.g10_1, this.h10_1, this.i10_1, this.j10_1, this.e10_1);
};
function validateConfiguration($this) {
  if (equals($this.ul(), EmptySerializersModule()))
    return Unit_instance;
  var collector = new PolymorphismValidator($this.bz_1.x10_1, $this.bz_1.y10_1);
  $this.ul().my(collector);
}
function JsonImpl(configuration, module_0) {
  Json.call(this, configuration, module_0);
  validateConfiguration(this);
}
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
  this.p10_1 = encodeDefaults;
  this.q10_1 = ignoreUnknownKeys;
  this.r10_1 = isLenient;
  this.s10_1 = allowStructuredMapKeys;
  this.t10_1 = prettyPrint;
  this.u10_1 = explicitNulls;
  this.v10_1 = prettyPrintIndent;
  this.w10_1 = coerceInputValues;
  this.x10_1 = useArrayPolymorphism;
  this.y10_1 = classDiscriminator;
  this.z10_1 = allowSpecialFloatingPointValues;
  this.a11_1 = useAlternativeNames;
  this.b11_1 = namingStrategy;
  this.c11_1 = decodeEnumsCaseInsensitive;
  this.d11_1 = allowTrailingComma;
  this.e11_1 = allowComments;
  this.f11_1 = classDiscriminatorMode;
}
protoOf(JsonConfiguration).toString = function () {
  return 'JsonConfiguration(encodeDefaults=' + this.p10_1 + ', ignoreUnknownKeys=' + this.q10_1 + ', isLenient=' + this.r10_1 + ', ' + ('allowStructuredMapKeys=' + this.s10_1 + ', prettyPrint=' + this.t10_1 + ', explicitNulls=' + this.u10_1 + ', ') + ("prettyPrintIndent='" + this.v10_1 + "', coerceInputValues=" + this.w10_1 + ', useArrayPolymorphism=' + this.x10_1 + ', ') + ("classDiscriminator='" + this.y10_1 + "', allowSpecialFloatingPointValues=" + this.z10_1 + ', ') + ('useAlternativeNames=' + this.a11_1 + ', namingStrategy=' + toString_0(this.b11_1) + ', decodeEnumsCaseInsensitive=' + this.c11_1 + ', ') + ('allowTrailingComma=' + this.d11_1 + ', allowComments=' + this.e11_1 + ', classDiscriminatorMode=' + this.f11_1.toString() + ')');
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
function ClassDiscriminatorMode_NONE_getInstance() {
  ClassDiscriminatorMode_initEntries();
  return ClassDiscriminatorMode_NONE_instance;
}
function ClassDiscriminatorMode_POLYMORPHIC_getInstance() {
  ClassDiscriminatorMode_initEntries();
  return ClassDiscriminatorMode_POLYMORPHIC_instance;
}
function JsonDecoder() {
}
function get_jsonUnquotedLiteralDescriptor() {
  _init_properties_JsonElement_kt__7cbdc2();
  return jsonUnquotedLiteralDescriptor;
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
  var k = _destruct__k2r9zo.q1();
  // Inline function 'kotlin.collections.component2' call
  var v = _destruct__k2r9zo.r1();
  // Inline function 'kotlin.text.buildString' call
  // Inline function 'kotlin.apply' call
  var this_0 = StringBuilder_init_$Create$();
  printQuoted(this_0, k);
  this_0.b7(_Char___init__impl__6a9atx(58));
  this_0.z6(v);
  return this_0.toString();
}
function JsonObject(content) {
  JsonElement.call(this);
  this.i11_1 = content;
}
protoOf(JsonObject).equals = function (other) {
  return equals(this.i11_1, other);
};
protoOf(JsonObject).hashCode = function () {
  return hashCode(this.i11_1);
};
protoOf(JsonObject).toString = function () {
  var tmp = this.i11_1.x1();
  return joinToString(tmp, ',', '{', '}', VOID, VOID, JsonObject$toString$lambda);
};
protoOf(JsonObject).j11 = function (key) {
  return this.i11_1.s1(key);
};
protoOf(JsonObject).s1 = function (key) {
  if (!(!(key == null) ? typeof key === 'string' : false))
    return false;
  return this.j11((!(key == null) ? typeof key === 'string' : false) ? key : THROW_CCE());
};
protoOf(JsonObject).jb = function (key) {
  return this.i11_1.u1(key);
};
protoOf(JsonObject).u1 = function (key) {
  if (!(!(key == null) ? typeof key === 'string' : false))
    return null;
  return this.jb((!(key == null) ? typeof key === 'string' : false) ? key : THROW_CCE());
};
protoOf(JsonObject).r = function () {
  return this.i11_1.r();
};
protoOf(JsonObject).x1 = function () {
  return this.i11_1.x1();
};
protoOf(JsonObject).v1 = function () {
  return this.i11_1.v1();
};
protoOf(JsonObject).k = function () {
  return this.i11_1.k();
};
protoOf(JsonObject).w1 = function () {
  return this.i11_1.w1();
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
  return this.k11();
};
function get_contentOrNull(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  var tmp;
  if (_this__u8e3s4 instanceof JsonNull) {
    tmp = null;
  } else {
    tmp = _this__u8e3s4.k11();
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
  this.l11_1 = content;
}
protoOf(JsonArray).equals = function (other) {
  return equals(this.l11_1, other);
};
protoOf(JsonArray).hashCode = function () {
  return hashCode(this.l11_1);
};
protoOf(JsonArray).toString = function () {
  return joinToString(this.l11_1, ',', '[', ']');
};
protoOf(JsonArray).m11 = function (element) {
  return this.l11_1.a1(element);
};
protoOf(JsonArray).a1 = function (element) {
  if (!(element instanceof JsonElement))
    return false;
  return this.m11(element instanceof JsonElement ? element : THROW_CCE());
};
protoOf(JsonArray).p = function (index) {
  return this.l11_1.p(index);
};
protoOf(JsonArray).n11 = function (element) {
  return this.l11_1.o1(element);
};
protoOf(JsonArray).o1 = function (element) {
  if (!(element instanceof JsonElement))
    return -1;
  return this.n11(element instanceof JsonElement ? element : THROW_CCE());
};
protoOf(JsonArray).r = function () {
  return this.l11_1.r();
};
protoOf(JsonArray).h = function () {
  return this.l11_1.h();
};
protoOf(JsonArray).q = function (index) {
  return this.l11_1.q(index);
};
protoOf(JsonArray).k = function () {
  return this.l11_1.k();
};
function JsonNull() {
  JsonNull_instance = this;
  JsonPrimitive.call(this);
  this.o11_1 = 'null';
}
protoOf(JsonNull).k11 = function () {
  return this.o11_1;
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
  this.p11_1 = isString;
  this.q11_1 = coerceToInlineType;
  this.r11_1 = toString(body);
  if (!(this.q11_1 == null)) {
    // Inline function 'kotlin.require' call
    // Inline function 'kotlin.require' call
    if (!this.q11_1.ij()) {
      var message = 'Failed requirement.';
      throw IllegalArgumentException_init_$Create$(toString(message));
    }
  }
}
protoOf(JsonLiteral).k11 = function () {
  return this.r11_1;
};
protoOf(JsonLiteral).toString = function () {
  var tmp;
  if (this.p11_1) {
    // Inline function 'kotlin.text.buildString' call
    // Inline function 'kotlin.apply' call
    var this_0 = StringBuilder_init_$Create$();
    printQuoted(this_0, this.r11_1);
    tmp = this_0.toString();
  } else {
    tmp = this.r11_1;
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
  if (!(this.p11_1 === other.p11_1))
    return false;
  if (!(this.r11_1 === other.r11_1))
    return false;
  return true;
};
protoOf(JsonLiteral).hashCode = function () {
  var result = getBooleanHashCode(this.p11_1);
  result = imul(31, result) + getStringHashCode(this.r11_1) | 0;
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
  return toBooleanStrictOrNull_0(_this__u8e3s4.k11());
}
function get_int(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  // Inline function 'kotlinx.serialization.json.mapExceptions' call
  var tmp;
  try {
    tmp = (new StringJsonLexer(_this__u8e3s4.k11())).s11();
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
    throw NumberFormatException_init_$Create$(_this__u8e3s4.k11() + ' is not an Int');
  return convertToInt(result);
}
function get_long(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  // Inline function 'kotlinx.serialization.json.mapExceptions' call
  var tmp;
  try {
    tmp = (new StringJsonLexer(_this__u8e3s4.k11())).s11();
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
  var this_0 = _this__u8e3s4.k11();
  // Inline function 'kotlin.js.unsafeCast' call
  // Inline function 'kotlin.js.asDynamic' call
  return toDouble(this_0);
}
function get_double(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  return toDouble(_this__u8e3s4.k11());
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
  throw IllegalArgumentException_init_$Create$('Element ' + toString(getKClassFromExpression(_this__u8e3s4)) + ' is not a ' + element);
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
  this.t11_1 = MapSerializer(serializer(StringCompanionObject_instance), JsonElementSerializer_getInstance()).si();
  this.u11_1 = 'kotlinx.serialization.json.JsonObject';
}
protoOf(JsonObjectDescriptor).gj = function () {
  return this.u11_1;
};
protoOf(JsonObjectDescriptor).lj = function (index) {
  return this.t11_1.lj(index);
};
protoOf(JsonObjectDescriptor).mj = function (name) {
  return this.t11_1.mj(name);
};
protoOf(JsonObjectDescriptor).nj = function (index) {
  return this.t11_1.nj(index);
};
protoOf(JsonObjectDescriptor).oj = function (index) {
  return this.t11_1.oj(index);
};
protoOf(JsonObjectDescriptor).pj = function (index) {
  return this.t11_1.pj(index);
};
protoOf(JsonObjectDescriptor).hj = function () {
  return this.t11_1.hj();
};
protoOf(JsonObjectDescriptor).aj = function () {
  return this.t11_1.aj();
};
protoOf(JsonObjectDescriptor).ij = function () {
  return this.t11_1.ij();
};
protoOf(JsonObjectDescriptor).jj = function () {
  return this.t11_1.jj();
};
protoOf(JsonObjectDescriptor).kj = function () {
  return this.t11_1.kj();
};
var JsonObjectDescriptor_instance;
function JsonObjectDescriptor_getInstance() {
  if (JsonObjectDescriptor_instance == null)
    new JsonObjectDescriptor();
  return JsonObjectDescriptor_instance;
}
function JsonObjectSerializer() {
  JsonObjectSerializer_instance = this;
  this.v11_1 = JsonObjectDescriptor_getInstance();
}
protoOf(JsonObjectSerializer).si = function () {
  return this.v11_1;
};
protoOf(JsonObjectSerializer).w11 = function (encoder, value) {
  verify(encoder);
  MapSerializer(serializer(StringCompanionObject_instance), JsonElementSerializer_getInstance()).ti(encoder, value);
};
protoOf(JsonObjectSerializer).ti = function (encoder, value) {
  return this.w11(encoder, value instanceof JsonObject ? value : THROW_CCE());
};
protoOf(JsonObjectSerializer).ui = function (decoder) {
  verify_0(decoder);
  return new JsonObject(MapSerializer(serializer(StringCompanionObject_instance), JsonElementSerializer_getInstance()).ui(decoder));
};
var JsonObjectSerializer_instance;
function JsonObjectSerializer_getInstance() {
  if (JsonObjectSerializer_instance == null)
    new JsonObjectSerializer();
  return JsonObjectSerializer_instance;
}
function JsonElementSerializer$descriptor$lambda($this$buildSerialDescriptor) {
  $this$buildSerialDescriptor.ck('JsonPrimitive', defer(JsonElementSerializer$descriptor$lambda$lambda));
  $this$buildSerialDescriptor.ck('JsonNull', defer(JsonElementSerializer$descriptor$lambda$lambda_0));
  $this$buildSerialDescriptor.ck('JsonLiteral', defer(JsonElementSerializer$descriptor$lambda$lambda_1));
  $this$buildSerialDescriptor.ck('JsonObject', defer(JsonElementSerializer$descriptor$lambda$lambda_2));
  $this$buildSerialDescriptor.ck('JsonArray', defer(JsonElementSerializer$descriptor$lambda$lambda_3));
  return Unit_instance;
}
function JsonElementSerializer$descriptor$lambda$lambda() {
  return JsonPrimitiveSerializer_getInstance().x11_1;
}
function JsonElementSerializer$descriptor$lambda$lambda_0() {
  return JsonNullSerializer_getInstance().y11_1;
}
function JsonElementSerializer$descriptor$lambda$lambda_1() {
  return JsonLiteralSerializer_getInstance().z11_1;
}
function JsonElementSerializer$descriptor$lambda$lambda_2() {
  return JsonObjectSerializer_getInstance().v11_1;
}
function JsonElementSerializer$descriptor$lambda$lambda_3() {
  return JsonArraySerializer_getInstance().a12_1;
}
function JsonElementSerializer() {
  JsonElementSerializer_instance = this;
  var tmp = this;
  var tmp_0 = SEALED_getInstance();
  tmp.b12_1 = buildSerialDescriptor('kotlinx.serialization.json.JsonElement', tmp_0, [], JsonElementSerializer$descriptor$lambda);
}
protoOf(JsonElementSerializer).si = function () {
  return this.b12_1;
};
protoOf(JsonElementSerializer).c12 = function (encoder, value) {
  verify(encoder);
  if (value instanceof JsonPrimitive) {
    encoder.wm(JsonPrimitiveSerializer_getInstance(), value);
  } else {
    if (value instanceof JsonObject) {
      encoder.wm(JsonObjectSerializer_getInstance(), value);
    } else {
      if (value instanceof JsonArray) {
        encoder.wm(JsonArraySerializer_getInstance(), value);
      } else {
        noWhenBranchMatchedException();
      }
    }
  }
};
protoOf(JsonElementSerializer).ti = function (encoder, value) {
  return this.c12(encoder, value instanceof JsonElement ? value : THROW_CCE());
};
protoOf(JsonElementSerializer).ui = function (decoder) {
  var input = asJsonDecoder(decoder);
  return input.h11();
};
var JsonElementSerializer_instance;
function JsonElementSerializer_getInstance() {
  if (JsonElementSerializer_instance == null)
    new JsonElementSerializer();
  return JsonElementSerializer_instance;
}
function JsonPrimitiveSerializer() {
  JsonPrimitiveSerializer_instance = this;
  this.x11_1 = buildSerialDescriptor('kotlinx.serialization.json.JsonPrimitive', STRING_getInstance(), []);
}
protoOf(JsonPrimitiveSerializer).si = function () {
  return this.x11_1;
};
protoOf(JsonPrimitiveSerializer).d12 = function (encoder, value) {
  verify(encoder);
  var tmp;
  if (value instanceof JsonNull) {
    encoder.wm(JsonNullSerializer_getInstance(), JsonNull_getInstance());
    tmp = Unit_instance;
  } else {
    var tmp_0 = JsonLiteralSerializer_getInstance();
    encoder.wm(tmp_0, value instanceof JsonLiteral ? value : THROW_CCE());
    tmp = Unit_instance;
  }
  return tmp;
};
protoOf(JsonPrimitiveSerializer).ti = function (encoder, value) {
  return this.d12(encoder, value instanceof JsonPrimitive ? value : THROW_CCE());
};
protoOf(JsonPrimitiveSerializer).ui = function (decoder) {
  var result = asJsonDecoder(decoder).h11();
  if (!(result instanceof JsonPrimitive))
    throw JsonDecodingException_0(-1, 'Unexpected JSON element, expected JsonPrimitive, had ' + toString(getKClassFromExpression(result)), toString(result));
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
  this.e12_1 = ListSerializer(JsonElementSerializer_getInstance()).si();
  this.f12_1 = 'kotlinx.serialization.json.JsonArray';
}
protoOf(JsonArrayDescriptor).gj = function () {
  return this.f12_1;
};
protoOf(JsonArrayDescriptor).lj = function (index) {
  return this.e12_1.lj(index);
};
protoOf(JsonArrayDescriptor).mj = function (name) {
  return this.e12_1.mj(name);
};
protoOf(JsonArrayDescriptor).nj = function (index) {
  return this.e12_1.nj(index);
};
protoOf(JsonArrayDescriptor).oj = function (index) {
  return this.e12_1.oj(index);
};
protoOf(JsonArrayDescriptor).pj = function (index) {
  return this.e12_1.pj(index);
};
protoOf(JsonArrayDescriptor).hj = function () {
  return this.e12_1.hj();
};
protoOf(JsonArrayDescriptor).aj = function () {
  return this.e12_1.aj();
};
protoOf(JsonArrayDescriptor).ij = function () {
  return this.e12_1.ij();
};
protoOf(JsonArrayDescriptor).jj = function () {
  return this.e12_1.jj();
};
protoOf(JsonArrayDescriptor).kj = function () {
  return this.e12_1.kj();
};
var JsonArrayDescriptor_instance;
function JsonArrayDescriptor_getInstance() {
  if (JsonArrayDescriptor_instance == null)
    new JsonArrayDescriptor();
  return JsonArrayDescriptor_instance;
}
function JsonArraySerializer() {
  JsonArraySerializer_instance = this;
  this.a12_1 = JsonArrayDescriptor_getInstance();
}
protoOf(JsonArraySerializer).si = function () {
  return this.a12_1;
};
protoOf(JsonArraySerializer).g12 = function (encoder, value) {
  verify(encoder);
  ListSerializer(JsonElementSerializer_getInstance()).ti(encoder, value);
};
protoOf(JsonArraySerializer).ti = function (encoder, value) {
  return this.g12(encoder, value instanceof JsonArray ? value : THROW_CCE());
};
protoOf(JsonArraySerializer).ui = function (decoder) {
  verify_0(decoder);
  return new JsonArray(ListSerializer(JsonElementSerializer_getInstance()).ui(decoder));
};
var JsonArraySerializer_instance;
function JsonArraySerializer_getInstance() {
  if (JsonArraySerializer_instance == null)
    new JsonArraySerializer();
  return JsonArraySerializer_instance;
}
function JsonNullSerializer() {
  JsonNullSerializer_instance = this;
  this.y11_1 = buildSerialDescriptor('kotlinx.serialization.json.JsonNull', ENUM_getInstance(), []);
}
protoOf(JsonNullSerializer).si = function () {
  return this.y11_1;
};
protoOf(JsonNullSerializer).h12 = function (encoder, value) {
  verify(encoder);
  encoder.am();
};
protoOf(JsonNullSerializer).ti = function (encoder, value) {
  return this.h12(encoder, value instanceof JsonNull ? value : THROW_CCE());
};
protoOf(JsonNullSerializer).ui = function (decoder) {
  verify_0(decoder);
  if (decoder.rk()) {
    throw new JsonDecodingException("Expected 'null' literal");
  }
  decoder.sk();
  return JsonNull_getInstance();
};
var JsonNullSerializer_instance;
function JsonNullSerializer_getInstance() {
  if (JsonNullSerializer_instance == null)
    new JsonNullSerializer();
  return JsonNullSerializer_instance;
}
function verify(encoder) {
  asJsonEncoder(encoder);
}
function verify_0(decoder) {
  asJsonDecoder(decoder);
}
function defer(deferred) {
  return new defer$1(deferred);
}
function JsonLiteralSerializer() {
  JsonLiteralSerializer_instance = this;
  this.z11_1 = PrimitiveSerialDescriptor('kotlinx.serialization.json.JsonLiteral', STRING_getInstance());
}
protoOf(JsonLiteralSerializer).si = function () {
  return this.z11_1;
};
protoOf(JsonLiteralSerializer).i12 = function (encoder, value) {
  verify(encoder);
  if (value.p11_1) {
    return encoder.jm(value.r11_1);
  }
  if (!(value.q11_1 == null)) {
    return encoder.km(value.q11_1).jm(value.r11_1);
  }
  var tmp0_safe_receiver = toLongOrNull(value.r11_1);
  if (tmp0_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    return encoder.fm(tmp0_safe_receiver);
  }
  var tmp1_safe_receiver = toULongOrNull(value.r11_1);
  var tmp = tmp1_safe_receiver;
  if ((tmp == null ? null : new ULong(tmp)) == null)
    null;
  else {
    var tmp_0 = tmp1_safe_receiver;
    // Inline function 'kotlin.let' call
    var it = (tmp_0 == null ? null : new ULong(tmp_0)).ai_1;
    var tmp_1 = encoder.km(serializer_0(Companion_getInstance()).si());
    // Inline function 'kotlin.ULong.toLong' call
    var tmp$ret$4 = _ULong___get_data__impl__fggpzb(it);
    tmp_1.fm(tmp$ret$4);
    return Unit_instance;
  }
  var tmp2_safe_receiver = toDoubleOrNull(value.r11_1);
  if (tmp2_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    return encoder.hm(tmp2_safe_receiver);
  }
  var tmp3_safe_receiver = toBooleanStrictOrNull(value.r11_1);
  if (tmp3_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    return encoder.bm(tmp3_safe_receiver);
  }
  encoder.jm(value.r11_1);
};
protoOf(JsonLiteralSerializer).ti = function (encoder, value) {
  return this.i12(encoder, value instanceof JsonLiteral ? value : THROW_CCE());
};
protoOf(JsonLiteralSerializer).ui = function (decoder) {
  var result = asJsonDecoder(decoder).h11();
  if (!(result instanceof JsonLiteral))
    throw JsonDecodingException_0(-1, 'Unexpected JSON element, expected JsonLiteral, had ' + toString(getKClassFromExpression(result)), toString(result));
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
    throw IllegalStateException_init_$Create$('This serializer can be used only with Json format.' + ('Expected Decoder to be JsonDecoder, got ' + toString(getKClassFromExpression(_this__u8e3s4))));
  } else {
    tmp = tmp0_elvis_lhs;
  }
  return tmp;
}
function asJsonEncoder(_this__u8e3s4) {
  var tmp0_elvis_lhs = isInterface(_this__u8e3s4, JsonEncoder) ? _this__u8e3s4 : null;
  var tmp;
  if (tmp0_elvis_lhs == null) {
    throw IllegalStateException_init_$Create$('This serializer can be used only with Json format.' + ('Expected Encoder to be JsonEncoder, got ' + toString(getKClassFromExpression(_this__u8e3s4))));
  } else {
    tmp = tmp0_elvis_lhs;
  }
  return tmp;
}
function _get_original__l7ku1m($this) {
  var tmp0 = $this.j12_1;
  var tmp = KProperty1;
  // Inline function 'kotlin.getValue' call
  getPropertyCallableRef('original', 1, tmp, defer$o$_get_original_$ref_3cje7k(), null);
  return tmp0.r1();
}
function defer$o$_get_original_$ref_3cje7k() {
  return function (p0) {
    return _get_original__l7ku1m(p0);
  };
}
function defer$1($deferred) {
  this.j12_1 = lazy($deferred);
}
protoOf(defer$1).gj = function () {
  return _get_original__l7ku1m(this).gj();
};
protoOf(defer$1).hj = function () {
  return _get_original__l7ku1m(this).hj();
};
protoOf(defer$1).jj = function () {
  return _get_original__l7ku1m(this).jj();
};
protoOf(defer$1).lj = function (index) {
  return _get_original__l7ku1m(this).lj(index);
};
protoOf(defer$1).mj = function (name) {
  return _get_original__l7ku1m(this).mj(name);
};
protoOf(defer$1).nj = function (index) {
  return _get_original__l7ku1m(this).nj(index);
};
protoOf(defer$1).oj = function (index) {
  return _get_original__l7ku1m(this).oj(index);
};
protoOf(defer$1).pj = function (index) {
  return _get_original__l7ku1m(this).pj(index);
};
function JsonEncoder() {
}
function Composer(writer) {
  this.k12_1 = writer;
  this.l12_1 = true;
}
protoOf(Composer).m12 = function () {
  this.l12_1 = true;
};
protoOf(Composer).n12 = function () {
  return Unit_instance;
};
protoOf(Composer).o12 = function () {
  this.l12_1 = false;
};
protoOf(Composer).p12 = function () {
  this.l12_1 = false;
};
protoOf(Composer).q12 = function () {
  return Unit_instance;
};
protoOf(Composer).r12 = function (v) {
  return this.k12_1.s12(v);
};
protoOf(Composer).t12 = function (v) {
  return this.k12_1.u12(v);
};
protoOf(Composer).v12 = function (v) {
  return this.k12_1.u12(v.toString());
};
protoOf(Composer).w12 = function (v) {
  return this.k12_1.u12(v.toString());
};
protoOf(Composer).x12 = function (v) {
  return this.k12_1.y12(fromInt(v));
};
protoOf(Composer).z12 = function (v) {
  return this.k12_1.y12(fromInt(v));
};
protoOf(Composer).a13 = function (v) {
  return this.k12_1.y12(fromInt(v));
};
protoOf(Composer).b13 = function (v) {
  return this.k12_1.y12(v);
};
protoOf(Composer).c13 = function (v) {
  return this.k12_1.u12(v.toString());
};
protoOf(Composer).d13 = function (value) {
  return this.k12_1.e13(value);
};
function Composer_0(sb, json) {
  return json.bz_1.t10_1 ? new ComposerWithPrettyPrint(sb, json) : new Composer(sb);
}
function ComposerForUnsignedNumbers(writer, forceQuoting) {
  Composer.call(this, writer);
  this.h13_1 = forceQuoting;
}
protoOf(ComposerForUnsignedNumbers).a13 = function (v) {
  if (this.h13_1) {
    // Inline function 'kotlin.toUInt' call
    var tmp$ret$0 = _UInt___init__impl__l7qpdl(v);
    this.d13(UInt__toString_impl_dbgl21(tmp$ret$0));
  } else {
    // Inline function 'kotlin.toUInt' call
    var tmp$ret$1 = _UInt___init__impl__l7qpdl(v);
    this.t12(UInt__toString_impl_dbgl21(tmp$ret$1));
  }
};
protoOf(ComposerForUnsignedNumbers).b13 = function (v) {
  if (this.h13_1) {
    // Inline function 'kotlin.toULong' call
    var tmp$ret$0 = _ULong___init__impl__c78o9k(v);
    this.d13(ULong__toString_impl_f9au7k(tmp$ret$0));
  } else {
    // Inline function 'kotlin.toULong' call
    var tmp$ret$1 = _ULong___init__impl__c78o9k(v);
    this.t12(ULong__toString_impl_f9au7k(tmp$ret$1));
  }
};
protoOf(ComposerForUnsignedNumbers).x12 = function (v) {
  if (this.h13_1) {
    // Inline function 'kotlin.toUByte' call
    var tmp$ret$0 = _UByte___init__impl__g9hnc4(v);
    this.d13(UByte__toString_impl_v72jg(tmp$ret$0));
  } else {
    // Inline function 'kotlin.toUByte' call
    var tmp$ret$1 = _UByte___init__impl__g9hnc4(v);
    this.t12(UByte__toString_impl_v72jg(tmp$ret$1));
  }
};
protoOf(ComposerForUnsignedNumbers).z12 = function (v) {
  if (this.h13_1) {
    // Inline function 'kotlin.toUShort' call
    var tmp$ret$0 = _UShort___init__impl__jigrne(v);
    this.d13(UShort__toString_impl_edaoee(tmp$ret$0));
  } else {
    // Inline function 'kotlin.toUShort' call
    var tmp$ret$1 = _UShort___init__impl__jigrne(v);
    this.t12(UShort__toString_impl_edaoee(tmp$ret$1));
  }
};
function ComposerForUnquotedLiterals(writer, forceQuoting) {
  Composer.call(this, writer);
  this.k13_1 = forceQuoting;
}
protoOf(ComposerForUnquotedLiterals).d13 = function (value) {
  if (this.k13_1) {
    protoOf(Composer).d13.call(this, value);
  } else {
    protoOf(Composer).t12.call(this, value);
  }
};
function ComposerWithPrettyPrint(writer, json) {
  Composer.call(this, writer);
  this.n13_1 = json;
  this.o13_1 = 0;
}
protoOf(ComposerWithPrettyPrint).m12 = function () {
  this.l12_1 = true;
  this.o13_1 = this.o13_1 + 1 | 0;
};
protoOf(ComposerWithPrettyPrint).n12 = function () {
  this.o13_1 = this.o13_1 - 1 | 0;
};
protoOf(ComposerWithPrettyPrint).o12 = function () {
  this.l12_1 = false;
  this.t12('\n');
  // Inline function 'kotlin.repeat' call
  var times = this.o13_1;
  var inductionVariable = 0;
  if (inductionVariable < times)
    do {
      var index = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      this.t12(this.n13_1.bz_1.v10_1);
    }
     while (inductionVariable < times);
};
protoOf(ComposerWithPrettyPrint).p12 = function () {
  if (this.l12_1)
    this.l12_1 = false;
  else {
    this.o12();
  }
};
protoOf(ComposerWithPrettyPrint).q12 = function () {
  this.r12(_Char___init__impl__6a9atx(32));
};
function readIfAbsent($this, descriptor, index) {
  $this.q13_1 = (!descriptor.pj(index) && descriptor.oj(index).aj());
  return $this.q13_1;
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
  tmp.p13_1 = new ElementMarker(descriptor, JsonElementMarker$readIfAbsent$ref(this));
  this.q13_1 = false;
}
protoOf(JsonElementMarker).r13 = function (index) {
  this.p13_1.bq(index);
};
protoOf(JsonElementMarker).s13 = function () {
  return this.p13_1.cq();
};
function JsonDecodingException(message) {
  JsonException.call(this, message);
  captureStack(this, JsonDecodingException);
}
function JsonDecodingException_0(offset, message, input) {
  return JsonDecodingException_1(offset, message + '\nJSON input: ' + toString(minify(input, offset)));
}
function invalidTrailingComma(_this__u8e3s4, entity) {
  entity = entity === VOID ? 'object' : entity;
  _this__u8e3s4.t13('Trailing comma before the end of JSON ' + entity, _this__u8e3s4.rz_1 - 1 | 0, "Trailing commas are non-complaint JSON and not allowed by default. Use 'allowTrailingCommas = true' in 'Json {}' builder to support them.");
}
function throwInvalidFloatingPointDecoded(_this__u8e3s4, result) {
  _this__u8e3s4.u13('Unexpected special floating-point value ' + toString(result) + '. By default, ' + 'non-finite floating point values are prohibited because they do not conform JSON specification', VOID, "It is possible to deserialize them using 'JsonBuilder.allowSpecialFloatingPointValues = true'");
}
function JsonEncodingException(message) {
  JsonException.call(this, message);
  captureStack(this, JsonEncodingException);
}
function InvalidKeyKindException(keyDescriptor) {
  return new JsonEncodingException("Value of type '" + keyDescriptor.gj() + "' can't be used in JSON as a key in the map. " + ("It should have either primitive or enum kind, but its kind is '" + keyDescriptor.hj().toString() + "'.\n") + "Use 'allowStructuredMapKeys = true' in 'Json {}' builder to convert such maps to [key1, value1, key2, value2,...] arrays.");
}
function InvalidFloatingPointDecoded(value, key, output) {
  return JsonDecodingException_1(-1, unexpectedFpErrorMessage(value, key, output));
}
function UnknownKeyException(key, input) {
  return JsonDecodingException_1(-1, "Encountered an unknown key '" + key + "'.\n" + "Use 'ignoreUnknownKeys = true' in 'Json {}' builder to ignore unknown keys.\n" + ('Current input: ' + toString(minify(input))));
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
    return '.....' + toString(charSequenceSubSequence(_this__u8e3s4, start, endIndex));
  }
  var start_0 = offset - 30 | 0;
  var end = offset + 30 | 0;
  var prefix = start_0 <= 0 ? '' : '.....';
  var suffix = end >= charSequenceLength(_this__u8e3s4) ? '' : '.....';
  var tmp2 = coerceAtLeast(start_0, 0);
  // Inline function 'kotlin.text.substring' call
  var endIndex_0 = coerceAtMost(end, charSequenceLength(_this__u8e3s4));
  return prefix + toString(charSequenceSubSequence(_this__u8e3s4, tmp2, endIndex_0)) + suffix;
}
function unexpectedFpErrorMessage(value, key, output) {
  return 'Unexpected special floating-point value ' + toString(value) + ' with key ' + key + '. By default, ' + 'non-finite floating point values are prohibited because they do not conform JSON specification. ' + "It is possible to deserialize them using 'JsonBuilder.allowSpecialFloatingPointValues = true'\n" + ('Current output: ' + toString(minify(output)));
}
function InvalidFloatingPointEncoded(value, output) {
  return new JsonEncodingException('Unexpected special floating-point value ' + toString(value) + '. By default, ' + 'non-finite floating point values are prohibited because they do not conform JSON specification. ' + "It is possible to deserialize them using 'JsonBuilder.allowSpecialFloatingPointValues = true'\n" + ('Current output: ' + toString(minify(output))));
}
function get_JsonDeserializationNamesKey() {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  return JsonDeserializationNamesKey;
}
var JsonDeserializationNamesKey;
function get_JsonSerializationNamesKey() {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  return JsonSerializationNamesKey;
}
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
  var index = _this__u8e3s4.mj(name);
  if (!(index === -3))
    return index;
  if (!json.bz_1.a11_1)
    return index;
  return getJsonNameIndexSlowPath(_this__u8e3s4, json, name);
}
function getJsonElementName(_this__u8e3s4, json, index) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  var strategy = namingStrategy(_this__u8e3s4, json);
  return strategy == null ? _this__u8e3s4.lj(index) : serializationNamesIndices(_this__u8e3s4, json, strategy)[index];
}
function namingStrategy(_this__u8e3s4, json) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  return equals(_this__u8e3s4.hj(), CLASS_getInstance()) ? json.bz_1.b11_1 : null;
}
function deserializationNamesMap(_this__u8e3s4, descriptor) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  var tmp = get_schemaCache(_this__u8e3s4);
  var tmp_0 = get_JsonDeserializationNamesKey();
  return tmp.w13(descriptor, tmp_0, deserializationNamesMap$lambda(descriptor, _this__u8e3s4));
}
function decodeCaseInsensitive(_this__u8e3s4, descriptor) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  return _this__u8e3s4.bz_1.c11_1 && equals(descriptor.hj(), ENUM_getInstance());
}
function getJsonNameIndexSlowPath(_this__u8e3s4, json, name) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  var tmp0_elvis_lhs = deserializationNamesMap(json, _this__u8e3s4).u1(name);
  return tmp0_elvis_lhs == null ? -3 : tmp0_elvis_lhs;
}
function serializationNamesIndices(_this__u8e3s4, json, strategy) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  var tmp = get_schemaCache(json);
  var tmp_0 = get_JsonSerializationNamesKey();
  return tmp.w13(_this__u8e3s4, tmp_0, serializationNamesIndices$lambda(_this__u8e3s4, strategy));
}
function buildDeserializationNamesMap(_this__u8e3s4, json) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  // Inline function 'kotlin.collections.mutableMapOf' call
  var builder = LinkedHashMap_init_$Create$();
  var useLowercaseEnums = decodeCaseInsensitive(json, _this__u8e3s4);
  var strategyForClasses = namingStrategy(_this__u8e3s4, json);
  var inductionVariable = 0;
  var last = _this__u8e3s4.jj();
  if (inductionVariable < last)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      // Inline function 'kotlin.collections.filterIsInstance' call
      var tmp0 = _this__u8e3s4.nj(i);
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
      var tmp1_safe_receiver = tmp0_safe_receiver == null ? null : tmp0_safe_receiver.x13_1;
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
        tmp_0 = _this__u8e3s4.lj(i).toLowerCase();
      } else if (!(strategyForClasses == null)) {
        tmp_0 = strategyForClasses.y13(_this__u8e3s4, i, _this__u8e3s4.lj(i));
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
function serializationNamesIndices$lambda($this_serializationNamesIndices, $strategy) {
  return function () {
    var tmp = 0;
    var tmp_0 = $this_serializationNamesIndices.jj();
    // Inline function 'kotlin.arrayOfNulls' call
    var tmp_1 = Array(tmp_0);
    while (tmp < tmp_0) {
      var tmp_2 = tmp;
      var baseName = $this_serializationNamesIndices.lj(tmp_2);
      tmp_1[tmp_2] = $strategy.y13($this_serializationNamesIndices, tmp_2, baseName);
      tmp = tmp + 1 | 0;
    }
    return tmp_1;
  };
}
function buildDeserializationNamesMap$putOrThrow(_this__u8e3s4, $this_buildDeserializationNamesMap, name, index) {
  var entity = equals($this_buildDeserializationNamesMap.hj(), ENUM_getInstance()) ? 'enum value' : 'property';
  // Inline function 'kotlin.collections.contains' call
  // Inline function 'kotlin.collections.containsKey' call
  if ((isInterface(_this__u8e3s4, KtMap) ? _this__u8e3s4 : THROW_CCE()).s1(name)) {
    throw new JsonException("The suggested name '" + name + "' for " + entity + ' ' + $this_buildDeserializationNamesMap.lj(index) + ' is already one of the names for ' + entity + ' ' + ($this_buildDeserializationNamesMap.lj(getValue(_this__u8e3s4, name)) + ' in ' + toString($this_buildDeserializationNamesMap)));
  }
  // Inline function 'kotlin.collections.set' call
  _this__u8e3s4.y1(name, index);
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
  var newSize = imul($this.b14_1, 2);
  $this.z13_1 = copyOf($this.z13_1, newSize);
  $this.a14_1 = copyOf_0($this.a14_1, newSize);
}
function JsonPath() {
  var tmp = this;
  // Inline function 'kotlin.arrayOfNulls' call
  tmp.z13_1 = Array(8);
  var tmp_0 = this;
  var tmp_1 = 0;
  var tmp_2 = new Int32Array(8);
  while (tmp_1 < 8) {
    tmp_2[tmp_1] = -1;
    tmp_1 = tmp_1 + 1 | 0;
  }
  tmp_0.a14_1 = tmp_2;
  this.b14_1 = -1;
}
protoOf(JsonPath).c14 = function (sd) {
  this.b14_1 = this.b14_1 + 1 | 0;
  var depth = this.b14_1;
  if (depth === this.z13_1.length) {
    resize(this);
  }
  this.z13_1[depth] = sd;
};
protoOf(JsonPath).d14 = function (index) {
  this.a14_1[this.b14_1] = index;
};
protoOf(JsonPath).e14 = function (key) {
  var tmp;
  if (!(this.a14_1[this.b14_1] === -2)) {
    this.b14_1 = this.b14_1 + 1 | 0;
    tmp = this.b14_1 === this.z13_1.length;
  } else {
    tmp = false;
  }
  if (tmp) {
    resize(this);
  }
  this.z13_1[this.b14_1] = key;
  this.a14_1[this.b14_1] = -2;
};
protoOf(JsonPath).f14 = function () {
  if (this.a14_1[this.b14_1] === -2) {
    this.z13_1[this.b14_1] = Tombstone_instance;
  }
};
protoOf(JsonPath).g14 = function () {
  var depth = this.b14_1;
  if (this.a14_1[depth] === -2) {
    this.a14_1[depth] = -1;
    this.b14_1 = this.b14_1 - 1 | 0;
  }
  if (!(this.b14_1 === -1)) {
    this.b14_1 = this.b14_1 - 1 | 0;
  }
};
protoOf(JsonPath).h14 = function () {
  // Inline function 'kotlin.text.buildString' call
  // Inline function 'kotlin.apply' call
  var this_0 = StringBuilder_init_$Create$();
  this_0.a7('$');
  // Inline function 'kotlin.repeat' call
  var times = this.b14_1 + 1 | 0;
  var inductionVariable = 0;
  if (inductionVariable < times)
    do {
      var index = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      var element = this.z13_1[index];
      if (!(element == null) ? isInterface(element, SerialDescriptor) : false) {
        if (equals(element.hj(), LIST_getInstance())) {
          if (!(this.a14_1[index] === -1)) {
            this_0.a7('[');
            this_0.ca(this.a14_1[index]);
            this_0.a7(']');
          }
        } else {
          var idx = this.a14_1[index];
          if (idx >= 0) {
            this_0.a7('.');
            this_0.a7(element.lj(idx));
          }
        }
      } else {
        if (!(element === Tombstone_instance)) {
          this_0.a7('[');
          this_0.a7("'");
          this_0.z6(element);
          this_0.a7("'");
          this_0.a7(']');
        }
      }
    }
     while (inductionVariable < times);
  return this_0.toString();
};
protoOf(JsonPath).toString = function () {
  return this.h14();
};
function encodeByWriter(json, writer, serializer, value) {
  var tmp = WriteMode_OBJ_getInstance();
  // Inline function 'kotlin.arrayOfNulls' call
  var size = get_entries().k();
  var tmp$ret$0 = Array(size);
  var encoder = StreamingJsonEncoder_init_$Create$(writer, json, tmp, tmp$ret$0);
  encoder.wm(serializer, value);
}
function readObject($this) {
  // Inline function 'kotlinx.serialization.json.internal.JsonTreeReader.readObjectImpl' call
  var lastToken = $this.r14_1.v14(6);
  if ($this.r14_1.w14() === 4) {
    $this.r14_1.u13('Unexpected leading comma');
  }
  // Inline function 'kotlin.collections.linkedMapOf' call
  var result = LinkedHashMap_init_$Create$();
  $l$loop: while ($this.r14_1.x14()) {
    var key = $this.s14_1 ? $this.r14_1.z14() : $this.r14_1.y14();
    $this.r14_1.v14(5);
    var element = $this.a15();
    // Inline function 'kotlin.collections.set' call
    result.y1(key, element);
    lastToken = $this.r14_1.b15();
    var tmp0_subject = lastToken;
    if (tmp0_subject !== 4)
      if (tmp0_subject === 7)
        break $l$loop;
      else {
        $this.r14_1.u13('Expected end of the object or comma');
      }
  }
  if (lastToken === 6) {
    $this.r14_1.v14(7);
  } else if (lastToken === 4) {
    if (!$this.t14_1) {
      invalidTrailingComma($this.r14_1);
    }
    $this.r14_1.v14(7);
  }
  return new JsonObject(result);
}
function readObject_0($this, $receiver, $completion) {
  var tmp = new $readObjectCOROUTINE$($this, $receiver, $completion);
  tmp.o7_1 = Unit_instance;
  tmp.p7_1 = null;
  return tmp.u7();
}
function readArray($this) {
  var lastToken = $this.r14_1.b15();
  if ($this.r14_1.w14() === 4) {
    $this.r14_1.u13('Unexpected leading comma');
  }
  // Inline function 'kotlin.collections.arrayListOf' call
  var result = ArrayList_init_$Create$();
  while ($this.r14_1.x14()) {
    var element = $this.a15();
    result.e(element);
    lastToken = $this.r14_1.b15();
    if (!(lastToken === 4)) {
      var tmp0 = $this.r14_1;
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.require' call
      var condition = lastToken === 9;
      var position = tmp0.rz_1;
      if (!condition) {
        var tmp$ret$2 = 'Expected end of the array or comma';
        tmp0.u13(tmp$ret$2, position);
      }
    }
  }
  if (lastToken === 8) {
    $this.r14_1.v14(9);
  } else if (lastToken === 4) {
    if (!$this.t14_1) {
      invalidTrailingComma($this.r14_1, 'array');
    }
    $this.r14_1.v14(9);
  }
  return new JsonArray(result);
}
function readValue($this, isString) {
  var tmp;
  if ($this.s14_1 || !isString) {
    tmp = $this.r14_1.z14();
  } else {
    tmp = $this.r14_1.y14();
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
  this.y15_1 = this$0;
  CoroutineImpl.call(this, resultContinuation);
}
protoOf(JsonTreeReader$readDeepRecursive$slambda).c16 = function ($this$DeepRecursiveFunction, it, $completion) {
  var tmp = this.d16($this$DeepRecursiveFunction, it, $completion);
  tmp.o7_1 = Unit_instance;
  tmp.p7_1 = null;
  return tmp.u7();
};
protoOf(JsonTreeReader$readDeepRecursive$slambda).a8 = function (p1, p2, $completion) {
  var tmp = p1 instanceof DeepRecursiveScope ? p1 : THROW_CCE();
  return this.c16(tmp, p2 instanceof Unit ? p2 : THROW_CCE(), $completion);
};
protoOf(JsonTreeReader$readDeepRecursive$slambda).u7 = function () {
  var suspendResult = this.o7_1;
  $sm: do
    try {
      var tmp = this.m7_1;
      switch (tmp) {
        case 0:
          this.n7_1 = 3;
          var tmp0_subject = this.y15_1.r14_1.w14();
          if (tmp0_subject === 1) {
            this.b16_1 = readValue(this.y15_1, true);
            this.m7_1 = 2;
            continue $sm;
          } else {
            if (tmp0_subject === 0) {
              this.b16_1 = readValue(this.y15_1, false);
              this.m7_1 = 2;
              continue $sm;
            } else {
              if (tmp0_subject === 6) {
                this.m7_1 = 1;
                suspendResult = readObject_0(this.y15_1, this.z15_1, this);
                if (suspendResult === get_COROUTINE_SUSPENDED()) {
                  return suspendResult;
                }
                continue $sm;
              } else {
                if (tmp0_subject === 8) {
                  this.b16_1 = readArray(this.y15_1);
                  this.m7_1 = 2;
                  continue $sm;
                } else {
                  var tmp_0 = this;
                  this.y15_1.r14_1.u13("Can't begin reading element, unexpected token");
                }
              }
            }
          }

          break;
        case 1:
          this.b16_1 = suspendResult;
          this.m7_1 = 2;
          continue $sm;
        case 2:
          return this.b16_1;
        case 3:
          throw this.p7_1;
      }
    } catch ($p) {
      var e = $p;
      if (this.n7_1 === 3) {
        throw e;
      } else {
        this.m7_1 = this.n7_1;
        this.p7_1 = e;
      }
    }
   while (true);
};
protoOf(JsonTreeReader$readDeepRecursive$slambda).d16 = function ($this$DeepRecursiveFunction, it, completion) {
  var i = new JsonTreeReader$readDeepRecursive$slambda(this.y15_1, completion);
  i.z15_1 = $this$DeepRecursiveFunction;
  i.a16_1 = it;
  return i;
};
function JsonTreeReader$readDeepRecursive$slambda_0(this$0, resultContinuation) {
  var i = new JsonTreeReader$readDeepRecursive$slambda(this$0, resultContinuation);
  var l = function ($this$DeepRecursiveFunction, it, $completion) {
    return i.c16($this$DeepRecursiveFunction, it, $completion);
  };
  l.$arity = 2;
  return l;
}
function $readObjectCOROUTINE$(_this__u8e3s4, _this__u8e3s4_0, resultContinuation) {
  CoroutineImpl.call(this, resultContinuation);
  this.k15_1 = _this__u8e3s4;
  this.l15_1 = _this__u8e3s4_0;
}
protoOf($readObjectCOROUTINE$).u7 = function () {
  var suspendResult = this.o7_1;
  $sm: do
    try {
      var tmp = this.m7_1;
      switch (tmp) {
        case 0:
          this.n7_1 = 5;
          this.n15_1 = this.k15_1;
          this.o15_1 = this.n15_1.r14_1.v14(6);
          if (this.n15_1.r14_1.w14() === 4) {
            this.n15_1.r14_1.u13('Unexpected leading comma');
          }

          var tmp_0 = this;
          tmp_0.m15_1 = LinkedHashMap_init_$Create$();
          this.m7_1 = 1;
          continue $sm;
        case 1:
          if (!this.n15_1.r14_1.x14()) {
            this.m7_1 = 4;
            continue $sm;
          }

          this.p15_1 = this.n15_1.s14_1 ? this.n15_1.r14_1.z14() : this.n15_1.r14_1.y14();
          this.n15_1.r14_1.v14(5);
          this.m7_1 = 2;
          suspendResult = this.l15_1.fg(Unit_instance, this);
          if (suspendResult === get_COROUTINE_SUSPENDED()) {
            return suspendResult;
          }

          continue $sm;
        case 2:
          var element = suspendResult;
          var tmp0 = this.m15_1;
          var key = this.p15_1;
          tmp0.y1(key, element);
          this.o15_1 = this.n15_1.r14_1.b15();
          var tmp0_subject = this.o15_1;
          if (tmp0_subject === 4) {
            this.m7_1 = 3;
            continue $sm;
          } else {
            if (tmp0_subject === 7) {
              this.m7_1 = 4;
              continue $sm;
            } else {
              this.n15_1.r14_1.u13('Expected end of the object or comma');
            }
          }

          break;
        case 3:
          this.m7_1 = 1;
          continue $sm;
        case 4:
          if (this.o15_1 === 6) {
            this.n15_1.r14_1.v14(7);
          } else if (this.o15_1 === 4) {
            if (!this.n15_1.t14_1) {
              invalidTrailingComma(this.n15_1.r14_1);
            }
            this.n15_1.r14_1.v14(7);
          }

          return new JsonObject(this.m15_1);
        case 5:
          throw this.p7_1;
      }
    } catch ($p) {
      var e = $p;
      if (this.n7_1 === 5) {
        throw e;
      } else {
        this.m7_1 = this.n7_1;
        this.p7_1 = e;
      }
    }
   while (true);
};
function JsonTreeReader(configuration, lexer) {
  this.r14_1 = lexer;
  this.s14_1 = configuration.r10_1;
  this.t14_1 = configuration.d11_1;
  this.u14_1 = 0;
}
protoOf(JsonTreeReader).a15 = function () {
  var token = this.r14_1.w14();
  var tmp;
  if (token === 1) {
    tmp = readValue(this, true);
  } else if (token === 0) {
    tmp = readValue(this, false);
  } else if (token === 6) {
    var tmp_0;
    this.u14_1 = this.u14_1 + 1 | 0;
    if (this.u14_1 === 200) {
      tmp_0 = readDeepRecursive(this);
    } else {
      tmp_0 = readObject(this);
    }
    var result = tmp_0;
    this.u14_1 = this.u14_1 - 1 | 0;
    tmp = result;
  } else if (token === 8) {
    tmp = readArray(this);
  } else {
    this.r14_1.u13('Cannot read Json element because of unexpected ' + tokenDescription(token));
  }
  return tmp;
};
function classDiscriminator(_this__u8e3s4, json) {
  var _iterator__ex2g4s = _this__u8e3s4.kj().h();
  while (_iterator__ex2g4s.i()) {
    var annotation = _iterator__ex2g4s.j();
    if (annotation instanceof JsonClassDiscriminator)
      return annotation.e16_1;
  }
  return json.bz_1.y10_1;
}
function validateIfSealed(serializer, actualSerializer, classDiscriminator) {
  if (!(serializer instanceof SealedClassSerializer))
    return Unit_instance;
  if (jsonCachedSerialNames(actualSerializer.si()).a1(classDiscriminator)) {
    var baseName = serializer.si().gj();
    var actualName = actualSerializer.si().gj();
    // Inline function 'kotlin.error' call
    var message = "Sealed class '" + actualName + "' cannot be serialized as base class '" + baseName + "' because" + (" it has property name that conflicts with JSON class discriminator '" + classDiscriminator + "'. ") + 'You can either change class discriminator in JsonConfiguration, ' + 'rename property with @SerialName annotation or fall back to array polymorphism';
    throw IllegalStateException_init_$Create$(toString(message));
  }
}
function checkKind(kind) {
  if (kind instanceof ENUM) {
    // Inline function 'kotlin.error' call
    var message = "Enums cannot be serialized polymorphically with 'type' parameter. You can use 'JsonBuilder.useArrayPolymorphism' instead";
    throw IllegalStateException_init_$Create$(toString(message));
  }
  if (kind instanceof PrimitiveKind) {
    // Inline function 'kotlin.error' call
    var message_0 = "Primitives cannot be serialized polymorphically with 'type' parameter. You can use 'JsonBuilder.useArrayPolymorphism' instead";
    throw IllegalStateException_init_$Create$(toString(message_0));
  }
  if (kind instanceof PolymorphicKind) {
    // Inline function 'kotlin.error' call
    var message_1 = 'Actual serializer for polymorphic cannot be polymorphic itself';
    throw IllegalStateException_init_$Create$(toString(message_1));
  }
}
function access$validateIfSealed$tPolymorphicKt(serializer, actualSerializer, classDiscriminator) {
  return validateIfSealed(serializer, actualSerializer, classDiscriminator);
}
function checkKind_0($this, descriptor, actualClass) {
  var kind = descriptor.hj();
  var tmp;
  if (kind instanceof PolymorphicKind) {
    tmp = true;
  } else {
    tmp = equals(kind, CONTEXTUAL_getInstance());
  }
  if (tmp) {
    throw IllegalArgumentException_init_$Create$('Serializer for ' + actualClass.x8() + " can't be registered as a subclass for polymorphic serialization " + ('because its kind ' + kind.toString() + ' is not concrete. To work with multiple hierarchies, register it as a base class.'));
  }
  if ($this.f16_1)
    return Unit_instance;
  var tmp_0;
  var tmp_1;
  if (equals(kind, LIST_getInstance()) || equals(kind, MAP_getInstance())) {
    tmp_1 = true;
  } else {
    tmp_1 = kind instanceof PrimitiveKind;
  }
  if (tmp_1) {
    tmp_0 = true;
  } else {
    tmp_0 = kind instanceof ENUM;
  }
  if (tmp_0) {
    throw IllegalArgumentException_init_$Create$('Serializer for ' + actualClass.x8() + ' of kind ' + kind.toString() + ' cannot be serialized polymorphically with class discriminator.');
  }
}
function checkDiscriminatorCollisions($this, descriptor, actualClass) {
  var inductionVariable = 0;
  var last = descriptor.jj();
  if (inductionVariable < last)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      var name = descriptor.lj(i);
      if (name === $this.g16_1) {
        throw IllegalArgumentException_init_$Create$('Polymorphic serializer for ' + toString(actualClass) + " has property '" + name + "' that conflicts " + 'with JSON class discriminator. You can either change class discriminator in JsonConfiguration, ' + 'rename property with @SerialName annotation ' + 'or fall back to array polymorphism');
      }
    }
     while (inductionVariable < last);
}
function PolymorphismValidator(useArrayPolymorphism, discriminator) {
  this.f16_1 = useArrayPolymorphism;
  this.g16_1 = discriminator;
}
protoOf(PolymorphismValidator).vy = function (kClass, provider) {
};
protoOf(PolymorphismValidator).yy = function (baseClass, actualClass, actualSerializer) {
  var descriptor = actualSerializer.si();
  checkKind_0(this, descriptor, actualClass);
  if (!this.f16_1) {
    checkDiscriminatorCollisions(this, descriptor, actualClass);
  }
};
protoOf(PolymorphismValidator).zy = function (baseClass, defaultSerializerProvider) {
};
protoOf(PolymorphismValidator).az = function (baseClass, defaultDeserializerProvider) {
};
function Key() {
}
function DescriptorSchemaCache() {
  this.v13_1 = createMapForCache(16);
}
protoOf(DescriptorSchemaCache).h16 = function (descriptor, key, value) {
  // Inline function 'kotlin.collections.getOrPut' call
  var this_0 = this.v13_1;
  var value_0 = this_0.u1(descriptor);
  var tmp;
  if (value_0 == null) {
    var answer = createMapForCache(2);
    this_0.y1(descriptor, answer);
    tmp = answer;
  } else {
    tmp = value_0;
  }
  var tmp0 = tmp;
  var tmp2 = key instanceof Key ? key : THROW_CCE();
  // Inline function 'kotlin.collections.set' call
  var value_1 = !(value == null) ? value : THROW_CCE();
  tmp0.y1(tmp2, value_1);
};
protoOf(DescriptorSchemaCache).w13 = function (descriptor, key, defaultValue) {
  var tmp0_safe_receiver = this.i16(descriptor, key);
  if (tmp0_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    return tmp0_safe_receiver;
  }
  var value = defaultValue();
  this.h16(descriptor, key, value);
  return value;
};
protoOf(DescriptorSchemaCache).i16 = function (descriptor, key) {
  var tmp0_safe_receiver = this.v13_1.u1(descriptor);
  var tmp;
  if (tmp0_safe_receiver == null) {
    tmp = null;
  } else {
    tmp = tmp0_safe_receiver.u1(key instanceof Key ? key : THROW_CCE());
  }
  var tmp_0 = tmp;
  return !(tmp_0 == null) ? tmp_0 : null;
};
function DiscriminatorHolder(discriminatorToSkip) {
  this.j16_1 = discriminatorToSkip;
}
function trySkip($this, $receiver, unknownKey) {
  if ($receiver == null)
    return false;
  if ($receiver.j16_1 === unknownKey) {
    $receiver.j16_1 = null;
    return true;
  }
  return false;
}
function skipLeftoverElements($this, descriptor) {
  while (!($this.wl(descriptor) === -1)) {
  }
}
function checkLeadingComma($this) {
  if ($this.lz_1.w14() === 4) {
    $this.lz_1.u13('Unexpected leading comma');
  }
}
function decodeMapIndex($this) {
  var hasComma = false;
  var decodingKey = !(($this.nz_1 % 2 | 0) === 0);
  if (decodingKey) {
    if (!($this.nz_1 === -1)) {
      hasComma = $this.lz_1.l16();
    }
  } else {
    $this.lz_1.k16(_Char___init__impl__6a9atx(58));
  }
  var tmp;
  if ($this.lz_1.x14()) {
    if (decodingKey) {
      if ($this.nz_1 === -1) {
        var tmp0 = $this.lz_1;
        // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.require' call
        var condition = !hasComma;
        var position = tmp0.rz_1;
        if (!condition) {
          var tmp$ret$1 = 'Unexpected leading comma';
          tmp0.u13(tmp$ret$1, position);
        }
      } else {
        var tmp0_0 = $this.lz_1;
        // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.require' call
        var condition_0 = hasComma;
        var position_0 = tmp0_0.rz_1;
        if (!condition_0) {
          var tmp$ret$3 = 'Expected comma after the key-value pair';
          tmp0_0.u13(tmp$ret$3, position_0);
        }
      }
    }
    $this.nz_1 = $this.nz_1 + 1 | 0;
    tmp = $this.nz_1;
  } else {
    if (hasComma && !$this.jz_1.bz_1.d11_1) {
      invalidTrailingComma($this.lz_1);
    }
    tmp = -1;
  }
  return tmp;
}
function coerceInputValue($this, descriptor, index) {
  var tmp0 = $this.jz_1;
  var tmp$ret$0;
  $l$block_2: {
    // Inline function 'kotlinx.serialization.json.internal.tryCoerceValue' call
    var isOptional = descriptor.pj(index);
    var elementDescriptor = descriptor.oj(index);
    var tmp;
    if (isOptional && !elementDescriptor.aj()) {
      tmp = $this.lz_1.m16(true);
    } else {
      tmp = false;
    }
    if (tmp) {
      tmp$ret$0 = true;
      break $l$block_2;
    }
    if (equals(elementDescriptor.hj(), ENUM_getInstance())) {
      var tmp_0;
      if (elementDescriptor.aj()) {
        tmp_0 = $this.lz_1.m16(false);
      } else {
        tmp_0 = false;
      }
      if (tmp_0) {
        tmp$ret$0 = false;
        break $l$block_2;
      }
      var tmp0_elvis_lhs = $this.lz_1.n16($this.pz_1.r10_1);
      var tmp_1;
      if (tmp0_elvis_lhs == null) {
        tmp$ret$0 = false;
        break $l$block_2;
      } else {
        tmp_1 = tmp0_elvis_lhs;
      }
      var enumValue = tmp_1;
      var enumIndex = getJsonNameIndex(elementDescriptor, tmp0, enumValue);
      var coerceToNull = !tmp0.bz_1.u10_1 && elementDescriptor.aj();
      if (enumIndex === -3 && (isOptional || coerceToNull)) {
        $this.lz_1.y14();
        tmp$ret$0 = true;
        break $l$block_2;
      }
    }
    tmp$ret$0 = false;
  }
  return tmp$ret$0;
}
function decodeObjectIndex($this, descriptor) {
  var hasComma = $this.lz_1.l16();
  while ($this.lz_1.x14()) {
    hasComma = false;
    var key = decodeStringKey($this);
    $this.lz_1.k16(_Char___init__impl__6a9atx(58));
    var index = getJsonNameIndex(descriptor, $this.jz_1, key);
    var tmp;
    if (!(index === -3)) {
      var tmp_0;
      if ($this.pz_1.w10_1 && coerceInputValue($this, descriptor, index)) {
        hasComma = $this.lz_1.l16();
        tmp_0 = false;
      } else {
        var tmp0_safe_receiver = $this.qz_1;
        if (tmp0_safe_receiver == null)
          null;
        else {
          tmp0_safe_receiver.r13(index);
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
  if (hasComma && !$this.jz_1.bz_1.d11_1) {
    invalidTrailingComma($this.lz_1);
  }
  var tmp1_safe_receiver = $this.qz_1;
  var tmp2_elvis_lhs = tmp1_safe_receiver == null ? null : tmp1_safe_receiver.s13();
  return tmp2_elvis_lhs == null ? -1 : tmp2_elvis_lhs;
}
function handleUnknown($this, key) {
  if ($this.pz_1.q10_1 || trySkip($this, $this.oz_1, key)) {
    $this.lz_1.p16($this.pz_1.r10_1);
  } else {
    $this.lz_1.o16(key);
  }
  return $this.lz_1.l16();
}
function decodeListIndex($this) {
  var hasComma = $this.lz_1.l16();
  var tmp;
  if ($this.lz_1.x14()) {
    if (!($this.nz_1 === -1) && !hasComma) {
      $this.lz_1.u13('Expected end of the array or comma');
    }
    $this.nz_1 = $this.nz_1 + 1 | 0;
    tmp = $this.nz_1;
  } else {
    if (hasComma && !$this.jz_1.bz_1.d11_1) {
      invalidTrailingComma($this.lz_1, 'array');
    }
    tmp = -1;
  }
  return tmp;
}
function decodeStringKey($this) {
  var tmp;
  if ($this.pz_1.r10_1) {
    tmp = $this.lz_1.r16();
  } else {
    tmp = $this.lz_1.q16();
  }
  return tmp;
}
function StreamingJsonDecoder(json, mode, lexer, descriptor, discriminatorHolder) {
  AbstractDecoder.call(this);
  this.jz_1 = json;
  this.kz_1 = mode;
  this.lz_1 = lexer;
  this.mz_1 = this.jz_1.ul();
  this.nz_1 = -1;
  this.oz_1 = discriminatorHolder;
  this.pz_1 = this.jz_1.bz_1;
  this.qz_1 = this.pz_1.u10_1 ? null : new JsonElementMarker(descriptor);
}
protoOf(StreamingJsonDecoder).g11 = function () {
  return this.jz_1;
};
protoOf(StreamingJsonDecoder).ul = function () {
  return this.mz_1;
};
protoOf(StreamingJsonDecoder).h11 = function () {
  return (new JsonTreeReader(this.jz_1.bz_1, this.lz_1)).a15();
};
protoOf(StreamingJsonDecoder).el = function (deserializer) {
  try {
    var tmp;
    if (!(deserializer instanceof AbstractPolymorphicSerializer)) {
      tmp = true;
    } else {
      tmp = this.jz_1.bz_1.x10_1;
    }
    if (tmp) {
      return deserializer.ui(this);
    }
    var discriminator = classDiscriminator(deserializer.si(), this.jz_1);
    var tmp0_elvis_lhs = this.lz_1.s16(discriminator, this.pz_1.r10_1);
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
          tmp_1 = this.g11().bz_1.x10_1;
        }
        if (tmp_1) {
          tmp$ret$0 = tmp2.ui(this);
          break $l$block;
        }
        var discriminator_0 = classDiscriminator(tmp2.si(), this.g11());
        var tmp0 = this.h11();
        // Inline function 'kotlinx.serialization.json.internal.cast' call
        var serialName = tmp2.si().gj();
        if (!(tmp0 instanceof JsonObject)) {
          var tmp_2 = getKClass(JsonObject).x8();
          var tmp_3 = getKClassFromExpression(tmp0).x8();
          var tmp$ret$2 = this.lz_1.sz_1.h14();
          throw JsonDecodingException_0(-1, 'Expected ' + tmp_2 + ', but had ' + tmp_3 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$2, toString(tmp0));
        }
        var jsonTree = tmp0;
        var tmp0_safe_receiver = jsonTree.jb(discriminator_0);
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
        tmp$ret$0 = readPolymorphicJson(this.g11(), discriminator_0, jsonTree, actualSerializer);
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
        this.lz_1.u13(message, VOID, hint);
      } else {
        throw $p;
      }
      tmp_7 = tmp_8;
    }
    var tmp_9 = tmp_7;
    var actualSerializer_0 = isInterface(tmp_9, DeserializationStrategy) ? tmp_9 : THROW_CCE();
    this.oz_1 = new DiscriminatorHolder(discriminator);
    return actualSerializer_0.ui(this);
  } catch ($p) {
    if ($p instanceof MissingFieldException) {
      var e = $p;
      if (contains_0(ensureNotNull(e.message), 'at path'))
        throw e;
      throw new MissingFieldException(e.zi_1, plus(e.message, ' at path: ') + this.lz_1.sz_1.h14(), e);
    } else {
      throw $p;
    }
  }
};
protoOf(StreamingJsonDecoder).fl = function (descriptor) {
  var newMode = switchMode(this.jz_1, descriptor);
  this.lz_1.sz_1.c14(descriptor);
  this.lz_1.k16(newMode.v16_1);
  checkLeadingComma(this);
  var tmp;
  switch (newMode.a2_1) {
    case 1:
    case 2:
    case 3:
      tmp = new StreamingJsonDecoder(this.jz_1, newMode, this.lz_1, descriptor, this.oz_1);
      break;
    default:
      var tmp_0;
      if (this.kz_1.equals(newMode) && this.jz_1.bz_1.u10_1) {
        tmp_0 = this;
      } else {
        tmp_0 = new StreamingJsonDecoder(this.jz_1, newMode, this.lz_1, descriptor, this.oz_1);
      }

      tmp = tmp_0;
      break;
  }
  return tmp;
};
protoOf(StreamingJsonDecoder).gl = function (descriptor) {
  if (this.jz_1.bz_1.q10_1 && descriptor.jj() === 0) {
    skipLeftoverElements(this, descriptor);
  }
  if (this.lz_1.l16() && !this.jz_1.bz_1.d11_1) {
    invalidTrailingComma(this.lz_1, '');
  }
  this.lz_1.k16(this.kz_1.w16_1);
  this.lz_1.sz_1.g14();
};
protoOf(StreamingJsonDecoder).rk = function () {
  var tmp;
  var tmp0_safe_receiver = this.qz_1;
  var tmp1_elvis_lhs = tmp0_safe_receiver == null ? null : tmp0_safe_receiver.q13_1;
  if (!(tmp1_elvis_lhs == null ? false : tmp1_elvis_lhs)) {
    tmp = !this.lz_1.x16();
  } else {
    tmp = false;
  }
  return tmp;
};
protoOf(StreamingJsonDecoder).sk = function () {
  return null;
};
protoOf(StreamingJsonDecoder).rl = function (descriptor, index, deserializer, previousValue) {
  var isMapKey = this.kz_1.equals(WriteMode_MAP_getInstance()) && (index & 1) === 0;
  if (isMapKey) {
    this.lz_1.sz_1.f14();
  }
  var value = protoOf(AbstractDecoder).rl.call(this, descriptor, index, deserializer, previousValue);
  if (isMapKey) {
    this.lz_1.sz_1.e14(value);
  }
  return value;
};
protoOf(StreamingJsonDecoder).wl = function (descriptor) {
  var index;
  switch (this.kz_1.a2_1) {
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
  if (!this.kz_1.equals(WriteMode_MAP_getInstance())) {
    this.lz_1.sz_1.d14(index);
  }
  return index;
};
protoOf(StreamingJsonDecoder).tk = function () {
  return this.lz_1.y16();
};
protoOf(StreamingJsonDecoder).uk = function () {
  var value = this.lz_1.s11();
  if (!equalsLong(value, fromInt(convertToByte(value)))) {
    this.lz_1.u13("Failed to parse byte for input '" + value.toString() + "'");
  }
  return convertToByte(value);
};
protoOf(StreamingJsonDecoder).vk = function () {
  var value = this.lz_1.s11();
  if (!equalsLong(value, fromInt(convertToShort(value)))) {
    this.lz_1.u13("Failed to parse short for input '" + value.toString() + "'");
  }
  return convertToShort(value);
};
protoOf(StreamingJsonDecoder).wk = function () {
  var value = this.lz_1.s11();
  if (!equalsLong(value, fromInt(convertToInt(value)))) {
    this.lz_1.u13("Failed to parse int for input '" + value.toString() + "'");
  }
  return convertToInt(value);
};
protoOf(StreamingJsonDecoder).xk = function () {
  return this.lz_1.s11();
};
protoOf(StreamingJsonDecoder).yk = function () {
  var tmp0 = this.lz_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.z14();
    try {
      // Inline function 'kotlin.text.toFloat' call
      // Inline function 'kotlin.js.unsafeCast' call
      // Inline function 'kotlin.js.asDynamic' call
      tmp$ret$0 = toDouble(input);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.u13("Failed to parse type '" + 'float' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  var result = tmp$ret$0;
  var specialFp = this.jz_1.bz_1.z10_1;
  if (specialFp || isFinite(result))
    return result;
  throwInvalidFloatingPointDecoded(this.lz_1, result);
};
protoOf(StreamingJsonDecoder).zk = function () {
  var tmp0 = this.lz_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.z14();
    try {
      tmp$ret$0 = toDouble(input);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.u13("Failed to parse type '" + 'double' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  var result = tmp$ret$0;
  var specialFp = this.jz_1.bz_1.z10_1;
  if (specialFp || isFinite_0(result))
    return result;
  throwInvalidFloatingPointDecoded(this.lz_1, result);
};
protoOf(StreamingJsonDecoder).al = function () {
  var string = this.lz_1.z14();
  if (!(string.length === 1)) {
    this.lz_1.u13("Expected single char, but got '" + string + "'");
  }
  return charCodeAt(string, 0);
};
protoOf(StreamingJsonDecoder).bl = function () {
  var tmp;
  if (this.pz_1.r10_1) {
    tmp = this.lz_1.r16();
  } else {
    tmp = this.lz_1.y14();
  }
  return tmp;
};
protoOf(StreamingJsonDecoder).cl = function (descriptor) {
  return get_isUnsignedNumber(descriptor) ? new JsonDecoderForUnsignedTypes(this.lz_1, this.jz_1) : protoOf(AbstractDecoder).cl.call(this, descriptor);
};
function JsonDecoderForUnsignedTypes(lexer, json) {
  AbstractDecoder.call(this);
  this.z16_1 = lexer;
  this.a17_1 = json.ul();
}
protoOf(JsonDecoderForUnsignedTypes).ul = function () {
  return this.a17_1;
};
protoOf(JsonDecoderForUnsignedTypes).wl = function (descriptor) {
  // Inline function 'kotlin.error' call
  var message = 'unsupported';
  throw IllegalStateException_init_$Create$(toString(message));
};
protoOf(JsonDecoderForUnsignedTypes).wk = function () {
  var tmp0 = this.z16_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.z14();
    try {
      // Inline function 'kotlin.UInt.toInt' call
      var this_0 = toUInt(input);
      tmp$ret$0 = _UInt___get_data__impl__f0vqqw(this_0);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.u13("Failed to parse type '" + 'UInt' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$0;
};
protoOf(JsonDecoderForUnsignedTypes).xk = function () {
  var tmp0 = this.z16_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.z14();
    try {
      // Inline function 'kotlin.ULong.toLong' call
      var this_0 = toULong(input);
      tmp$ret$0 = _ULong___get_data__impl__fggpzb(this_0);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.u13("Failed to parse type '" + 'ULong' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$0;
};
protoOf(JsonDecoderForUnsignedTypes).uk = function () {
  var tmp0 = this.z16_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.z14();
    try {
      // Inline function 'kotlin.UByte.toByte' call
      var this_0 = toUByte(input);
      tmp$ret$0 = _UByte___get_data__impl__jof9qr(this_0);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.u13("Failed to parse type '" + 'UByte' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$0;
};
protoOf(JsonDecoderForUnsignedTypes).vk = function () {
  var tmp0 = this.z16_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.z14();
    try {
      // Inline function 'kotlin.UShort.toShort' call
      var this_0 = toUShort(input);
      tmp$ret$0 = _UShort___get_data__impl__g0245(this_0);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.u13("Failed to parse type '" + 'UShort' + "' for input '" + input + "'");
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
function StreamingJsonEncoder_init_$Init$(output, json, mode, modeReuseCache, $this) {
  StreamingJsonEncoder.call($this, Composer_0(output, json), json, mode, modeReuseCache);
  return $this;
}
function StreamingJsonEncoder_init_$Create$(output, json, mode, modeReuseCache) {
  return StreamingJsonEncoder_init_$Init$(output, json, mode, modeReuseCache, objectCreate(protoOf(StreamingJsonEncoder)));
}
function encodeTypeInfo($this, discriminator, serialName) {
  $this.i14_1.o12();
  $this.jm(discriminator);
  $this.i14_1.r12(_Char___init__impl__6a9atx(58));
  $this.i14_1.q12();
  $this.jm(serialName);
}
function StreamingJsonEncoder(composer, json, mode, modeReuseCache) {
  AbstractEncoder.call(this);
  this.i14_1 = composer;
  this.j14_1 = json;
  this.k14_1 = mode;
  this.l14_1 = modeReuseCache;
  this.m14_1 = this.j14_1.ul();
  this.n14_1 = this.j14_1.bz_1;
  this.o14_1 = false;
  this.p14_1 = null;
  this.q14_1 = null;
  var i = this.k14_1.a2_1;
  if (!(this.l14_1 == null)) {
    if (!(this.l14_1[i] === null) || !(this.l14_1[i] === this)) {
      this.l14_1[i] = this;
    }
  }
}
protoOf(StreamingJsonEncoder).g11 = function () {
  return this.j14_1;
};
protoOf(StreamingJsonEncoder).ul = function () {
  return this.m14_1;
};
protoOf(StreamingJsonEncoder).bn = function (descriptor, index) {
  return this.n14_1.p10_1;
};
protoOf(StreamingJsonEncoder).wm = function (serializer, value) {
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.encodePolymorphically' call
    if (this.g11().bz_1.x10_1) {
      serializer.ti(this, value);
      break $l$block;
    }
    var isPolymorphicSerializer = serializer instanceof AbstractPolymorphicSerializer;
    var tmp;
    if (isPolymorphicSerializer) {
      tmp = !this.g11().bz_1.f11_1.equals(ClassDiscriminatorMode_NONE_getInstance());
    } else {
      var tmp_0;
      switch (this.g11().bz_1.f11_1.a2_1) {
        case 0:
        case 2:
          tmp_0 = false;
          break;
        case 1:
          // Inline function 'kotlin.let' call

          var it = serializer.si().hj();
          tmp_0 = equals(it, CLASS_getInstance()) || equals(it, OBJECT_getInstance());
          break;
        default:
          noWhenBranchMatchedException();
          break;
      }
      tmp = tmp_0;
    }
    var needDiscriminator = tmp;
    var baseClassDiscriminator = needDiscriminator ? classDiscriminator(serializer.si(), this.g11()) : null;
    var tmp_1;
    if (isPolymorphicSerializer) {
      var casted = serializer instanceof AbstractPolymorphicSerializer ? serializer : THROW_CCE();
      $l$block_0: {
        // Inline function 'kotlin.requireNotNull' call
        if (value == null) {
          var message = 'Value for serializer ' + toString(serializer.si()) + ' should always be non-null. Please report issue to the kotlinx.serialization tracker.';
          throw IllegalArgumentException_init_$Create$(toString(message));
        } else {
          break $l$block_0;
        }
      }
      var actual = findPolymorphicSerializer_0(casted, this, value);
      if (!(baseClassDiscriminator == null)) {
        access$validateIfSealed$tPolymorphicKt(serializer, actual, baseClassDiscriminator);
      }
      checkKind(actual.si().hj());
      tmp_1 = isInterface(actual, SerializationStrategy) ? actual : THROW_CCE();
    } else {
      tmp_1 = serializer;
    }
    var actualSerializer = tmp_1;
    if (!(baseClassDiscriminator == null)) {
      var serialName = actualSerializer.si().gj();
      this.p14_1 = baseClassDiscriminator;
      this.q14_1 = serialName;
    }
    actualSerializer.ti(this, value);
  }
};
protoOf(StreamingJsonEncoder).fl = function (descriptor) {
  var newMode = switchMode(this.j14_1, descriptor);
  if (!(newMode.v16_1 === _Char___init__impl__6a9atx(0))) {
    this.i14_1.r12(newMode.v16_1);
    this.i14_1.m12();
  }
  var discriminator = this.p14_1;
  if (!(discriminator == null)) {
    var tmp0_elvis_lhs = this.q14_1;
    encodeTypeInfo(this, discriminator, tmp0_elvis_lhs == null ? descriptor.gj() : tmp0_elvis_lhs);
    this.p14_1 = null;
    this.q14_1 = null;
  }
  if (this.k14_1.equals(newMode)) {
    return this;
  }
  var tmp1_safe_receiver = this.l14_1;
  var tmp2_elvis_lhs = tmp1_safe_receiver == null ? null : tmp1_safe_receiver[newMode.a2_1];
  return tmp2_elvis_lhs == null ? new StreamingJsonEncoder(this.i14_1, this.j14_1, newMode, this.l14_1) : tmp2_elvis_lhs;
};
protoOf(StreamingJsonEncoder).gl = function (descriptor) {
  if (!(this.k14_1.w16_1 === _Char___init__impl__6a9atx(0))) {
    this.i14_1.n12();
    this.i14_1.p12();
    this.i14_1.r12(this.k14_1.w16_1);
  }
};
protoOf(StreamingJsonEncoder).yl = function (descriptor, index) {
  switch (this.k14_1.a2_1) {
    case 1:
      if (!this.i14_1.l12_1) {
        this.i14_1.r12(_Char___init__impl__6a9atx(44));
      }

      this.i14_1.o12();
      break;
    case 2:
      if (!this.i14_1.l12_1) {
        var tmp = this;
        var tmp_0;
        if ((index % 2 | 0) === 0) {
          this.i14_1.r12(_Char___init__impl__6a9atx(44));
          this.i14_1.o12();
          tmp_0 = true;
        } else {
          this.i14_1.r12(_Char___init__impl__6a9atx(58));
          this.i14_1.q12();
          tmp_0 = false;
        }
        tmp.o14_1 = tmp_0;
      } else {
        this.o14_1 = true;
        this.i14_1.o12();
      }

      break;
    case 3:
      if (index === 0)
        this.o14_1 = true;
      if (index === 1) {
        this.i14_1.r12(_Char___init__impl__6a9atx(44));
        this.i14_1.q12();
        this.o14_1 = false;
      }

      break;
    default:
      if (!this.i14_1.l12_1) {
        this.i14_1.r12(_Char___init__impl__6a9atx(44));
      }

      this.i14_1.o12();
      this.jm(getJsonElementName(descriptor, this.j14_1, index));
      this.i14_1.r12(_Char___init__impl__6a9atx(58));
      this.i14_1.q12();
      break;
  }
  return true;
};
protoOf(StreamingJsonEncoder).xm = function (descriptor, index, serializer, value) {
  if (!(value == null) || this.n14_1.u10_1) {
    protoOf(AbstractEncoder).xm.call(this, descriptor, index, serializer, value);
  }
};
protoOf(StreamingJsonEncoder).km = function (descriptor) {
  var tmp;
  if (get_isUnsignedNumber(descriptor)) {
    // Inline function 'kotlinx.serialization.json.internal.StreamingJsonEncoder.composerAs' call
    var tmp_0;
    var tmp_1 = this.i14_1;
    if (tmp_1 instanceof ComposerForUnsignedNumbers) {
      tmp_0 = this.i14_1;
    } else {
      var tmp0 = this.i14_1.k12_1;
      var p1 = this.o14_1;
      tmp_0 = new ComposerForUnsignedNumbers(tmp0, p1);
    }
    var tmp$ret$0 = tmp_0;
    tmp = new StreamingJsonEncoder(tmp$ret$0, this.j14_1, this.k14_1, null);
  } else if (get_isUnquotedLiteral(descriptor)) {
    // Inline function 'kotlinx.serialization.json.internal.StreamingJsonEncoder.composerAs' call
    var tmp_2;
    var tmp_3 = this.i14_1;
    if (tmp_3 instanceof ComposerForUnquotedLiterals) {
      tmp_2 = this.i14_1;
    } else {
      var tmp0_0 = this.i14_1.k12_1;
      var p1_0 = this.o14_1;
      tmp_2 = new ComposerForUnquotedLiterals(tmp0_0, p1_0);
    }
    var tmp$ret$2 = tmp_2;
    tmp = new StreamingJsonEncoder(tmp$ret$2, this.j14_1, this.k14_1, null);
  } else if (!(this.p14_1 == null)) {
    // Inline function 'kotlin.apply' call
    this.q14_1 = descriptor.gj();
    tmp = this;
  } else {
    tmp = protoOf(AbstractEncoder).km.call(this, descriptor);
  }
  return tmp;
};
protoOf(StreamingJsonEncoder).am = function () {
  this.i14_1.t12('null');
};
protoOf(StreamingJsonEncoder).bm = function (value) {
  if (this.o14_1) {
    this.jm(value.toString());
  } else {
    this.i14_1.c13(value);
  }
};
protoOf(StreamingJsonEncoder).cm = function (value) {
  if (this.o14_1) {
    this.jm(value.toString());
  } else {
    this.i14_1.x12(value);
  }
};
protoOf(StreamingJsonEncoder).dm = function (value) {
  if (this.o14_1) {
    this.jm(value.toString());
  } else {
    this.i14_1.z12(value);
  }
};
protoOf(StreamingJsonEncoder).em = function (value) {
  if (this.o14_1) {
    this.jm(value.toString());
  } else {
    this.i14_1.a13(value);
  }
};
protoOf(StreamingJsonEncoder).fm = function (value) {
  if (this.o14_1) {
    this.jm(value.toString());
  } else {
    this.i14_1.b13(value);
  }
};
protoOf(StreamingJsonEncoder).gm = function (value) {
  if (this.o14_1) {
    this.jm(value.toString());
  } else {
    this.i14_1.v12(value);
  }
  if (!this.n14_1.z10_1 && !isFinite(value)) {
    throw InvalidFloatingPointEncoded(value, toString(this.i14_1.k12_1));
  }
};
protoOf(StreamingJsonEncoder).hm = function (value) {
  if (this.o14_1) {
    this.jm(value.toString());
  } else {
    this.i14_1.w12(value);
  }
  if (!this.n14_1.z10_1 && !isFinite_0(value)) {
    throw InvalidFloatingPointEncoded(value, toString(this.i14_1.k12_1));
  }
};
protoOf(StreamingJsonEncoder).im = function (value) {
  this.jm(toString_1(value));
};
protoOf(StreamingJsonEncoder).jm = function (value) {
  return this.i14_1.d13(value);
};
function get_isUnsignedNumber(_this__u8e3s4) {
  _init_properties_StreamingJsonEncoder_kt__pn1bsi();
  return _this__u8e3s4.ij() && get_unsignedNumberDescriptors().a1(_this__u8e3s4);
}
function get_isUnquotedLiteral(_this__u8e3s4) {
  _init_properties_StreamingJsonEncoder_kt__pn1bsi();
  return _this__u8e3s4.ij() && equals(_this__u8e3s4, get_jsonUnquotedLiteralDescriptor());
}
var properties_initialized_StreamingJsonEncoder_kt_6ifwwk;
function _init_properties_StreamingJsonEncoder_kt__pn1bsi() {
  if (!properties_initialized_StreamingJsonEncoder_kt_6ifwwk) {
    properties_initialized_StreamingJsonEncoder_kt_6ifwwk = true;
    unsignedNumberDescriptors = setOf([serializer_1(Companion_getInstance_0()).si(), serializer_0(Companion_getInstance()).si(), serializer_2(Companion_getInstance_1()).si(), serializer_3(Companion_getInstance_2()).si()]);
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
  _this__u8e3s4.b7(_Char___init__impl__6a9atx(34));
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
        _this__u8e3s4.aa(value, lastPos, i);
        _this__u8e3s4.a7(get_ESCAPE_STRINGS()[c]);
        lastPos = i + 1 | 0;
      }
    }
     while (inductionVariable <= last);
  if (!(lastPos === 0))
    _this__u8e3s4.aa(value, lastPos, value.length);
  else
    _this__u8e3s4.a7(value);
  _this__u8e3s4.b7(_Char___init__impl__6a9atx(34));
}
function toBooleanStrictOrNull_0(_this__u8e3s4) {
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
  throw JsonDecodingException_0(-1, "Failed to parse literal '" + literal.toString() + "' as " + type + ' value at element: ' + $this.h17(tag), toString($this.i17()));
}
function AbstractJsonTreeDecoder(json, value, polymorphicDiscriminator) {
  polymorphicDiscriminator = polymorphicDiscriminator === VOID ? null : polymorphicDiscriminator;
  NamedValueDecoder.call(this);
  this.d17_1 = json;
  this.e17_1 = value;
  this.f17_1 = polymorphicDiscriminator;
  this.g17_1 = this.g11().bz_1;
}
protoOf(AbstractJsonTreeDecoder).g11 = function () {
  return this.d17_1;
};
protoOf(AbstractJsonTreeDecoder).r1 = function () {
  return this.e17_1;
};
protoOf(AbstractJsonTreeDecoder).ul = function () {
  return this.g11().ul();
};
protoOf(AbstractJsonTreeDecoder).i17 = function () {
  var tmp0_safe_receiver = this.ix();
  var tmp;
  if (tmp0_safe_receiver == null) {
    tmp = null;
  } else {
    // Inline function 'kotlin.let' call
    tmp = this.j17(tmp0_safe_receiver);
  }
  var tmp1_elvis_lhs = tmp;
  return tmp1_elvis_lhs == null ? this.r1() : tmp1_elvis_lhs;
};
protoOf(AbstractJsonTreeDecoder).h17 = function (currentTag) {
  return this.kx() + ('.' + currentTag);
};
protoOf(AbstractJsonTreeDecoder).h11 = function () {
  return this.i17();
};
protoOf(AbstractJsonTreeDecoder).el = function (deserializer) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.decodeSerializableValuePolymorphic' call
    var tmp;
    if (!(deserializer instanceof AbstractPolymorphicSerializer)) {
      tmp = true;
    } else {
      tmp = this.g11().bz_1.x10_1;
    }
    if (tmp) {
      tmp$ret$0 = deserializer.ui(this);
      break $l$block;
    }
    var discriminator = classDiscriminator(deserializer.si(), this.g11());
    var tmp0 = this.h11();
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var serialName = deserializer.si().gj();
    if (!(tmp0 instanceof JsonObject)) {
      var tmp_0 = getKClass(JsonObject).x8();
      var tmp_1 = getKClassFromExpression(tmp0).x8();
      var tmp$ret$2 = this.kx();
      throw JsonDecodingException_0(-1, 'Expected ' + tmp_0 + ', but had ' + tmp_1 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$2, toString(tmp0));
    }
    var jsonTree = tmp0;
    var tmp0_safe_receiver = jsonTree.jb(discriminator);
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
    tmp$ret$0 = readPolymorphicJson(this.g11(), discriminator, jsonTree, actualSerializer);
  }
  return tmp$ret$0;
};
protoOf(AbstractJsonTreeDecoder).jx = function (parentName, childName) {
  return childName;
};
protoOf(AbstractJsonTreeDecoder).fl = function (descriptor) {
  var currentObject = this.i17();
  var tmp0_subject = descriptor.hj();
  var tmp;
  var tmp_0;
  if (equals(tmp0_subject, LIST_getInstance())) {
    tmp_0 = true;
  } else {
    tmp_0 = tmp0_subject instanceof PolymorphicKind;
  }
  if (tmp_0) {
    var tmp_1 = this.g11();
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var serialName = descriptor.gj();
    if (!(currentObject instanceof JsonArray)) {
      var tmp_2 = getKClass(JsonArray).x8();
      var tmp_3 = getKClassFromExpression(currentObject).x8();
      var tmp$ret$2 = this.kx();
      throw JsonDecodingException_0(-1, 'Expected ' + tmp_2 + ', but had ' + tmp_3 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$2, toString(currentObject));
    }
    tmp = new JsonTreeListDecoder(tmp_1, currentObject);
  } else {
    if (equals(tmp0_subject, MAP_getInstance())) {
      // Inline function 'kotlinx.serialization.json.internal.selectMapMode' call
      var this_0 = this.g11();
      var keyDescriptor = carrierDescriptor(descriptor.oj(0), this_0.ul());
      var keyKind = keyDescriptor.hj();
      var tmp_4;
      var tmp_5;
      if (keyKind instanceof PrimitiveKind) {
        tmp_5 = true;
      } else {
        tmp_5 = equals(keyKind, ENUM_getInstance());
      }
      if (tmp_5) {
        var tmp_6 = this.g11();
        // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
        // Inline function 'kotlinx.serialization.json.internal.cast' call
        var serialName_0 = descriptor.gj();
        if (!(currentObject instanceof JsonObject)) {
          var tmp_7 = getKClass(JsonObject).x8();
          var tmp_8 = getKClassFromExpression(currentObject).x8();
          var tmp$ret$7 = this.kx();
          throw JsonDecodingException_0(-1, 'Expected ' + tmp_7 + ', but had ' + tmp_8 + ' as the serialized body of ' + serialName_0 + ' at element: ' + tmp$ret$7, toString(currentObject));
        }
        tmp_4 = new JsonTreeMapDecoder(tmp_6, currentObject);
      } else {
        if (this_0.bz_1.s10_1) {
          var tmp_9 = this.g11();
          // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
          // Inline function 'kotlinx.serialization.json.internal.cast' call
          var serialName_1 = descriptor.gj();
          if (!(currentObject instanceof JsonArray)) {
            var tmp_10 = getKClass(JsonArray).x8();
            var tmp_11 = getKClassFromExpression(currentObject).x8();
            var tmp$ret$11 = this.kx();
            throw JsonDecodingException_0(-1, 'Expected ' + tmp_10 + ', but had ' + tmp_11 + ' as the serialized body of ' + serialName_1 + ' at element: ' + tmp$ret$11, toString(currentObject));
          }
          tmp_4 = new JsonTreeListDecoder(tmp_9, currentObject);
        } else {
          throw InvalidKeyKindException(keyDescriptor);
        }
      }
      tmp = tmp_4;
    } else {
      var tmp_12 = this.g11();
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
      // Inline function 'kotlinx.serialization.json.internal.cast' call
      var serialName_2 = descriptor.gj();
      if (!(currentObject instanceof JsonObject)) {
        var tmp_13 = getKClass(JsonObject).x8();
        var tmp_14 = getKClassFromExpression(currentObject).x8();
        var tmp$ret$14 = this.kx();
        throw JsonDecodingException_0(-1, 'Expected ' + tmp_13 + ', but had ' + tmp_14 + ' as the serialized body of ' + serialName_2 + ' at element: ' + tmp$ret$14, toString(currentObject));
      }
      tmp = new JsonTreeDecoder(tmp_12, currentObject, this.f17_1);
    }
  }
  return tmp;
};
protoOf(AbstractJsonTreeDecoder).gl = function (descriptor) {
};
protoOf(AbstractJsonTreeDecoder).rk = function () {
  var tmp = this.i17();
  return !(tmp instanceof JsonNull);
};
protoOf(AbstractJsonTreeDecoder).k17 = function (tag) {
  return !(this.j17(tag) === JsonNull_getInstance());
};
protoOf(AbstractJsonTreeDecoder).mx = function (tag) {
  return this.k17((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).l17 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.j17(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).x8();
      var tmp_0 = getKClassFromExpression(value).x8();
      var tmp$ret$3 = this.h17(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'boolean' + ' at element: ' + tmp$ret$3, toString(value));
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
protoOf(AbstractJsonTreeDecoder).nx = function (tag) {
  return this.l17((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).m17 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.j17(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).x8();
      var tmp_0 = getKClassFromExpression(value).x8();
      var tmp$ret$3 = this.h17(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'byte' + ' at element: ' + tmp$ret$3, toString(value));
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
protoOf(AbstractJsonTreeDecoder).ox = function (tag) {
  return this.m17((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).n17 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.j17(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).x8();
      var tmp_0 = getKClassFromExpression(value).x8();
      var tmp$ret$3 = this.h17(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'short' + ' at element: ' + tmp$ret$3, toString(value));
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
protoOf(AbstractJsonTreeDecoder).px = function (tag) {
  return this.n17((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).o17 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.j17(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).x8();
      var tmp_0 = getKClassFromExpression(value).x8();
      var tmp$ret$3 = this.h17(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'int' + ' at element: ' + tmp$ret$3, toString(value));
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
protoOf(AbstractJsonTreeDecoder).qx = function (tag) {
  return this.o17((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).p17 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.j17(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).x8();
      var tmp_0 = getKClassFromExpression(value).x8();
      var tmp$ret$3 = this.h17(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'long' + ' at element: ' + tmp$ret$3, toString(value));
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
protoOf(AbstractJsonTreeDecoder).rx = function (tag) {
  return this.p17((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).q17 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.j17(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).x8();
      var tmp_0 = getKClassFromExpression(value).x8();
      var tmp$ret$3 = this.h17(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'float' + ' at element: ' + tmp$ret$3, toString(value));
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
  var specialFp = this.g11().bz_1.z10_1;
  if (specialFp || isFinite(result))
    return result;
  throw InvalidFloatingPointDecoded(result, tag, toString(this.i17()));
};
protoOf(AbstractJsonTreeDecoder).sx = function (tag) {
  return this.q17((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).r17 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.j17(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).x8();
      var tmp_0 = getKClassFromExpression(value).x8();
      var tmp$ret$3 = this.h17(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'double' + ' at element: ' + tmp$ret$3, toString(value));
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
  var specialFp = this.g11().bz_1.z10_1;
  if (specialFp || isFinite_0(result))
    return result;
  throw InvalidFloatingPointDecoded(result, tag, toString(this.i17()));
};
protoOf(AbstractJsonTreeDecoder).tx = function (tag) {
  return this.r17((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).s17 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.j17(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).x8();
      var tmp_0 = getKClassFromExpression(value).x8();
      var tmp$ret$3 = this.h17(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'char' + ' at element: ' + tmp$ret$3, toString(value));
    }
    var literal = value;
    try {
      var tmp0_elvis_lhs = new Char(single(literal.k11()));
      var tmp_1;
      if (tmp0_elvis_lhs == null) {
        unparsedPrimitive(this, literal, 'char', tag);
      } else {
        tmp_1 = tmp0_elvis_lhs;
      }
      tmp$ret$0 = tmp_1.c1_1;
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
protoOf(AbstractJsonTreeDecoder).ux = function (tag) {
  return this.s17((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).t17 = function (tag) {
  // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
  // Inline function 'kotlinx.serialization.json.internal.cast' call
  var value = this.j17(tag);
  if (!(value instanceof JsonPrimitive)) {
    var tmp = getKClass(JsonPrimitive).x8();
    var tmp_0 = getKClassFromExpression(value).x8();
    var tmp$ret$2 = this.h17(tag);
    throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'string' + ' at element: ' + tmp$ret$2, toString(value));
  }
  var value_0 = value;
  if (!(value_0 instanceof JsonLiteral))
    throw JsonDecodingException_0(-1, "Expected string value for a non-null key '" + tag + "', got null literal instead at element: " + this.h17(tag), toString(this.i17()));
  if (!value_0.p11_1 && !this.g11().bz_1.r10_1) {
    throw JsonDecodingException_0(-1, "String literal for key '" + tag + "' should be quoted at element: " + this.h17(tag) + ".\nUse 'isLenient = true' in 'Json {}' builder to accept non-compliant JSON.", toString(this.i17()));
  }
  return value_0.r11_1;
};
protoOf(AbstractJsonTreeDecoder).vx = function (tag) {
  return this.t17((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).u17 = function (tag, inlineDescriptor) {
  var tmp;
  if (get_isUnsignedNumber(inlineDescriptor)) {
    var tmp_0 = this.g11();
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    var tmp2 = this.j17(tag);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var serialName = inlineDescriptor.gj();
    if (!(tmp2 instanceof JsonPrimitive)) {
      var tmp_1 = getKClass(JsonPrimitive).x8();
      var tmp_2 = getKClassFromExpression(tmp2).x8();
      var tmp$ret$3 = this.h17(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp_1 + ', but had ' + tmp_2 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$3, toString(tmp2));
    }
    var lexer = StringJsonLexer_0(tmp_0, tmp2.k11());
    tmp = new JsonDecoderForUnsignedTypes(lexer, this.g11());
  } else {
    tmp = protoOf(NamedValueDecoder).wx.call(this, tag, inlineDescriptor);
  }
  return tmp;
};
protoOf(AbstractJsonTreeDecoder).wx = function (tag, inlineDescriptor) {
  return this.u17((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE(), inlineDescriptor);
};
protoOf(AbstractJsonTreeDecoder).cl = function (descriptor) {
  return !(this.ix() == null) ? protoOf(NamedValueDecoder).cl.call(this, descriptor) : (new JsonPrimitiveDecoder(this.g11(), this.r1(), this.f17_1)).cl(descriptor);
};
function coerceInputValue_0($this, descriptor, index, tag) {
  var tmp0 = $this.g11();
  var tmp$ret$0;
  $l$block_2: {
    // Inline function 'kotlinx.serialization.json.internal.tryCoerceValue' call
    var isOptional = descriptor.pj(index);
    var elementDescriptor = descriptor.oj(index);
    var tmp;
    if (isOptional && !elementDescriptor.aj()) {
      var tmp_0 = $this.j17(tag);
      tmp = tmp_0 instanceof JsonNull;
    } else {
      tmp = false;
    }
    if (tmp) {
      tmp$ret$0 = true;
      break $l$block_2;
    }
    if (equals(elementDescriptor.hj(), ENUM_getInstance())) {
      var tmp_1;
      if (elementDescriptor.aj()) {
        var tmp_2 = $this.j17(tag);
        tmp_1 = tmp_2 instanceof JsonNull;
      } else {
        tmp_1 = false;
      }
      if (tmp_1) {
        tmp$ret$0 = false;
        break $l$block_2;
      }
      var tmp_3 = $this.j17(tag);
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
      var coerceToNull = !tmp0.bz_1.u10_1 && elementDescriptor.aj();
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
  $this.e18_1 = (!$this.g11().bz_1.u10_1 && !descriptor.pj(index) && descriptor.oj(index).aj());
  return $this.e18_1;
}
function JsonTreeDecoder(json, value, polymorphicDiscriminator, polyDescriptor) {
  polymorphicDiscriminator = polymorphicDiscriminator === VOID ? null : polymorphicDiscriminator;
  polyDescriptor = polyDescriptor === VOID ? null : polyDescriptor;
  AbstractJsonTreeDecoder.call(this, json, value, polymorphicDiscriminator);
  this.b18_1 = value;
  this.c18_1 = polyDescriptor;
  this.d18_1 = 0;
  this.e18_1 = false;
}
protoOf(JsonTreeDecoder).r1 = function () {
  return this.b18_1;
};
protoOf(JsonTreeDecoder).wl = function (descriptor) {
  while (this.d18_1 < descriptor.jj()) {
    var _unary__edvuaz = this.d18_1;
    this.d18_1 = _unary__edvuaz + 1 | 0;
    var name = this.dx(descriptor, _unary__edvuaz);
    var index = this.d18_1 - 1 | 0;
    this.e18_1 = false;
    var tmp;
    var tmp_0;
    // Inline function 'kotlin.collections.contains' call
    // Inline function 'kotlin.collections.containsKey' call
    var this_0 = this.r1();
    if ((isInterface(this_0, KtMap) ? this_0 : THROW_CCE()).s1(name)) {
      tmp_0 = true;
    } else {
      tmp_0 = absenceIsNull(this, descriptor, index);
    }
    if (tmp_0) {
      tmp = !this.g17_1.w10_1 || !coerceInputValue_0(this, descriptor, index, name);
    } else {
      tmp = false;
    }
    if (tmp) {
      return index;
    }
  }
  return -1;
};
protoOf(JsonTreeDecoder).rk = function () {
  return !this.e18_1 && protoOf(AbstractJsonTreeDecoder).rk.call(this);
};
protoOf(JsonTreeDecoder).ex = function (descriptor, index) {
  var strategy = namingStrategy(descriptor, this.g11());
  var baseName = descriptor.lj(index);
  if (strategy == null) {
    if (!this.g17_1.a11_1)
      return baseName;
    if (this.r1().v1().a1(baseName))
      return baseName;
  }
  var deserializationNamesMap_0 = deserializationNamesMap(this.g11(), descriptor);
  // Inline function 'kotlin.collections.find' call
  var tmp0 = this.r1().v1();
  var tmp$ret$1;
  $l$block: {
    // Inline function 'kotlin.collections.firstOrNull' call
    var _iterator__ex2g4s = tmp0.h();
    while (_iterator__ex2g4s.i()) {
      var element = _iterator__ex2g4s.j();
      if (deserializationNamesMap_0.u1(element) === index) {
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
  var fallbackName = strategy == null ? null : strategy.y13(descriptor, index, baseName);
  return fallbackName == null ? baseName : fallbackName;
};
protoOf(JsonTreeDecoder).j17 = function (tag) {
  return getValue(this.r1(), tag);
};
protoOf(JsonTreeDecoder).fl = function (descriptor) {
  if (descriptor === this.c18_1) {
    var tmp = this.g11();
    var tmp2 = this.i17();
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var serialName = this.c18_1.gj();
    if (!(tmp2 instanceof JsonObject)) {
      var tmp_0 = getKClass(JsonObject).x8();
      var tmp_1 = getKClassFromExpression(tmp2).x8();
      var tmp$ret$2 = this.kx();
      throw JsonDecodingException_0(-1, 'Expected ' + tmp_0 + ', but had ' + tmp_1 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$2, toString(tmp2));
    }
    return new JsonTreeDecoder(tmp, tmp2, this.f17_1, this.c18_1);
  }
  return protoOf(AbstractJsonTreeDecoder).fl.call(this, descriptor);
};
protoOf(JsonTreeDecoder).gl = function (descriptor) {
  var tmp;
  if (this.g17_1.q10_1) {
    tmp = true;
  } else {
    var tmp_0 = descriptor.hj();
    tmp = tmp_0 instanceof PolymorphicKind;
  }
  if (tmp)
    return Unit_instance;
  var strategy = namingStrategy(descriptor, this.g11());
  var tmp_1;
  if (strategy == null && !this.g17_1.a11_1) {
    tmp_1 = jsonCachedSerialNames(descriptor);
  } else if (!(strategy == null)) {
    tmp_1 = deserializationNamesMap(this.g11(), descriptor).v1();
  } else {
    var tmp_2 = jsonCachedSerialNames(descriptor);
    var tmp0_safe_receiver = get_schemaCache(this.g11()).i16(descriptor, get_JsonDeserializationNamesKey());
    // Inline function 'kotlin.collections.orEmpty' call
    var tmp0_elvis_lhs = tmp0_safe_receiver == null ? null : tmp0_safe_receiver.v1();
    var tmp$ret$0 = tmp0_elvis_lhs == null ? emptySet() : tmp0_elvis_lhs;
    tmp_1 = plus_0(tmp_2, tmp$ret$0);
  }
  var names = tmp_1;
  var _iterator__ex2g4s = this.r1().v1().h();
  while (_iterator__ex2g4s.i()) {
    var key = _iterator__ex2g4s.j();
    if (!names.a1(key) && !(key === this.f17_1)) {
      throw UnknownKeyException(key, this.r1().toString());
    }
  }
};
function JsonTreeListDecoder(json, value) {
  AbstractJsonTreeDecoder.call(this, json, value);
  this.l18_1 = value;
  this.m18_1 = this.l18_1.k();
  this.n18_1 = -1;
}
protoOf(JsonTreeListDecoder).r1 = function () {
  return this.l18_1;
};
protoOf(JsonTreeListDecoder).ex = function (descriptor, index) {
  return index.toString();
};
protoOf(JsonTreeListDecoder).j17 = function (tag) {
  return this.l18_1.p(toInt(tag));
};
protoOf(JsonTreeListDecoder).wl = function (descriptor) {
  while (this.n18_1 < (this.m18_1 - 1 | 0)) {
    this.n18_1 = this.n18_1 + 1 | 0;
    return this.n18_1;
  }
  return -1;
};
function JsonPrimitiveDecoder(json, value, polymorphicDiscriminator) {
  polymorphicDiscriminator = polymorphicDiscriminator === VOID ? null : polymorphicDiscriminator;
  AbstractJsonTreeDecoder.call(this, json, value, polymorphicDiscriminator);
  this.u18_1 = value;
  this.xx('primitive');
}
protoOf(JsonPrimitiveDecoder).r1 = function () {
  return this.u18_1;
};
protoOf(JsonPrimitiveDecoder).wl = function (descriptor) {
  return 0;
};
protoOf(JsonPrimitiveDecoder).j17 = function (tag) {
  // Inline function 'kotlin.require' call
  if (!(tag === 'primitive')) {
    var message = "This input can only handle primitives with 'primitive' tag";
    throw IllegalArgumentException_init_$Create$(toString(message));
  }
  return this.u18_1;
};
function JsonTreeMapDecoder(json, value) {
  JsonTreeDecoder.call(this, json, value);
  this.f19_1 = value;
  this.g19_1 = toList(this.f19_1.v1());
  this.h19_1 = imul(this.g19_1.k(), 2);
  this.i19_1 = -1;
}
protoOf(JsonTreeMapDecoder).r1 = function () {
  return this.f19_1;
};
protoOf(JsonTreeMapDecoder).ex = function (descriptor, index) {
  var i = index / 2 | 0;
  return this.g19_1.p(i);
};
protoOf(JsonTreeMapDecoder).wl = function (descriptor) {
  while (this.i19_1 < (this.h19_1 - 1 | 0)) {
    this.i19_1 = this.i19_1 + 1 | 0;
    return this.i19_1;
  }
  return -1;
};
protoOf(JsonTreeMapDecoder).j17 = function (tag) {
  return (this.i19_1 % 2 | 0) === 0 ? JsonPrimitive_0(tag) : getValue(this.f19_1, tag);
};
protoOf(JsonTreeMapDecoder).gl = function (descriptor) {
};
function readPolymorphicJson(_this__u8e3s4, discriminator, element, deserializer) {
  return (new JsonTreeDecoder(_this__u8e3s4, element, discriminator, deserializer.si())).el(deserializer);
}
var WriteMode_OBJ_instance;
var WriteMode_LIST_instance;
var WriteMode_MAP_instance;
var WriteMode_POLY_OBJ_instance;
function values() {
  return [WriteMode_OBJ_getInstance(), WriteMode_LIST_getInstance(), WriteMode_MAP_getInstance(), WriteMode_POLY_OBJ_getInstance()];
}
function get_entries() {
  if ($ENTRIES == null)
    $ENTRIES = enumEntries(values());
  return $ENTRIES;
}
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
var $ENTRIES;
function WriteMode(name, ordinal, begin, end) {
  Enum.call(this, name, ordinal);
  this.v16_1 = begin;
  this.w16_1 = end;
}
function switchMode(_this__u8e3s4, desc) {
  var tmp0_subject = desc.hj();
  var tmp;
  if (tmp0_subject instanceof PolymorphicKind) {
    tmp = WriteMode_POLY_OBJ_getInstance();
  } else {
    if (equals(tmp0_subject, LIST_getInstance())) {
      tmp = WriteMode_LIST_getInstance();
    } else {
      if (equals(tmp0_subject, MAP_getInstance())) {
        // Inline function 'kotlinx.serialization.json.internal.selectMapMode' call
        var keyDescriptor = carrierDescriptor(desc.oj(0), _this__u8e3s4.ul());
        var keyKind = keyDescriptor.hj();
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
          if (_this__u8e3s4.bz_1.s10_1) {
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
  if (equals(_this__u8e3s4.hj(), CONTEXTUAL_getInstance())) {
    var tmp0_safe_receiver = getContextualDescriptor(module_0, _this__u8e3s4);
    var tmp1_elvis_lhs = tmp0_safe_receiver == null ? null : carrierDescriptor(tmp0_safe_receiver, module_0);
    tmp = tmp1_elvis_lhs == null ? _this__u8e3s4 : tmp1_elvis_lhs;
  } else if (_this__u8e3s4.ij()) {
    tmp = carrierDescriptor(_this__u8e3s4.oj(0), module_0);
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
  $this.j19(lastPosition, current);
  return appendEsc($this, current + 1 | 0);
}
function decodedString($this, lastPosition, currentPosition) {
  $this.j19(lastPosition, currentPosition);
  var result = $this.uz_1.toString();
  $this.uz_1.fa(0);
  return result;
}
function takePeeked($this) {
  // Inline function 'kotlin.also' call
  var this_0 = ensureNotNull($this.tz_1);
  $this.tz_1 = null;
  return this_0;
}
function wasUnquotedString($this) {
  return !(charSequenceGet($this.k19(), $this.rz_1 - 1 | 0) === _Char___init__impl__6a9atx(34));
}
function appendEsc($this, startPosition) {
  var currentPosition = startPosition;
  currentPosition = $this.l19(currentPosition);
  if (currentPosition === -1) {
    $this.u13('Expected escape sequence to continue, got EOF');
  }
  var tmp = $this.k19();
  var _unary__edvuaz = currentPosition;
  currentPosition = _unary__edvuaz + 1 | 0;
  var currentChar = charSequenceGet(tmp, _unary__edvuaz);
  if (currentChar === _Char___init__impl__6a9atx(117)) {
    return appendHex($this, $this.k19(), currentPosition);
  }
  // Inline function 'kotlin.code' call
  var tmp$ret$0 = Char__toInt_impl_vasixd(currentChar);
  var c = escapeToChar(tmp$ret$0);
  if (c === _Char___init__impl__6a9atx(0)) {
    $this.u13("Invalid escaped char '" + toString_1(currentChar) + "'");
  }
  $this.uz_1.b7(c);
  return currentPosition;
}
function appendHex($this, source, startPos) {
  if ((startPos + 4 | 0) >= charSequenceLength(source)) {
    $this.rz_1 = startPos;
    $this.m19();
    if (($this.rz_1 + 4 | 0) >= charSequenceLength(source)) {
      $this.u13('Unexpected EOF during unicode escape');
    }
    return appendHex($this, source, $this.rz_1);
  }
  $this.uz_1.b7(numberToChar((((fromHexChar($this, source, startPos) << 12) + (fromHexChar($this, source, startPos + 1 | 0) << 8) | 0) + (fromHexChar($this, source, startPos + 2 | 0) << 4) | 0) + fromHexChar($this, source, startPos + 3 | 0) | 0));
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
    $this.u13("Invalid toHexChar char '" + toString_1(character) + "' in unicode escape");
  }
  return tmp;
}
function consumeBoolean2($this, start) {
  var current = $this.l19(start);
  if (current >= charSequenceLength($this.k19()) || current === -1) {
    $this.u13('EOF');
  }
  var tmp = $this.k19();
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
      $this.u13("Expected valid boolean literal prefix, but had '" + $this.z14() + "'");
    }
  }
  return tmp_0;
}
function consumeBooleanLiteral($this, literalSuffix, current) {
  if ((charSequenceLength($this.k19()) - current | 0) < literalSuffix.length) {
    $this.u13('Unexpected end of boolean literal');
  }
  var inductionVariable = 0;
  var last = charSequenceLength(literalSuffix) - 1 | 0;
  if (inductionVariable <= last)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      var expected = charCodeAt(literalSuffix, i);
      var actual = charSequenceGet($this.k19(), current + i | 0);
      // Inline function 'kotlin.code' call
      var tmp = Char__toInt_impl_vasixd(expected);
      // Inline function 'kotlin.code' call
      if (!(tmp === (Char__toInt_impl_vasixd(actual) | 32))) {
        $this.u13("Expected valid boolean literal prefix, but had '" + $this.z14() + "'");
      }
    }
     while (inductionVariable <= last);
  $this.rz_1 = current + literalSuffix.length | 0;
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
  this.rz_1 = 0;
  this.sz_1 = new JsonPath();
  this.tz_1 = null;
  this.uz_1 = StringBuilder_init_$Create$();
}
protoOf(AbstractJsonLexer).m19 = function () {
};
protoOf(AbstractJsonLexer).l16 = function () {
  var current = this.n19();
  var source = this.k19();
  if (current >= charSequenceLength(source) || current === -1)
    return false;
  if (charSequenceGet(source, current) === _Char___init__impl__6a9atx(44)) {
    this.rz_1 = this.rz_1 + 1 | 0;
    return true;
  }
  return false;
};
protoOf(AbstractJsonLexer).o19 = function (c) {
  return c === _Char___init__impl__6a9atx(125) || c === _Char___init__impl__6a9atx(93) || (c === _Char___init__impl__6a9atx(58) || c === _Char___init__impl__6a9atx(44)) ? false : true;
};
protoOf(AbstractJsonLexer).vz = function () {
  var nextToken = this.b15();
  if (!(nextToken === 10)) {
    this.u13('Expected EOF after parsing, but had ' + toString_1(charSequenceGet(this.k19(), this.rz_1 - 1 | 0)) + ' instead');
  }
};
protoOf(AbstractJsonLexer).v14 = function (expected) {
  var token = this.b15();
  if (!(token === expected)) {
    this.p19(expected);
  }
  return token;
};
protoOf(AbstractJsonLexer).q19 = function (expected) {
  if (this.rz_1 > 0 && expected === _Char___init__impl__6a9atx(34)) {
    var tmp$ret$0;
    $l$block: {
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.withPositionRollback' call
      var snapshot = this.rz_1;
      try {
        this.rz_1 = this.rz_1 - 1 | 0;
        tmp$ret$0 = this.z14();
        break $l$block;
      }finally {
        this.rz_1 = snapshot;
      }
    }
    var inputLiteral = tmp$ret$0;
    if (inputLiteral === 'null') {
      this.t13("Expected string literal but 'null' literal was found", this.rz_1 - 1 | 0, "Use 'coerceInputValues = true' in 'Json {}' builder to coerce nulls if property has a default value.");
    }
  }
  this.p19(charToTokenClass(expected));
};
protoOf(AbstractJsonLexer).r19 = function (expectedToken, wasConsumed) {
  var expected = tokenDescription(expectedToken);
  var position = wasConsumed ? this.rz_1 - 1 | 0 : this.rz_1;
  var s = this.rz_1 === charSequenceLength(this.k19()) || position < 0 ? 'EOF' : toString_1(charSequenceGet(this.k19(), position));
  this.u13('Expected ' + expected + ", but had '" + s + "' instead", position);
};
protoOf(AbstractJsonLexer).p19 = function (expectedToken, wasConsumed, $super) {
  wasConsumed = wasConsumed === VOID ? true : wasConsumed;
  return $super === VOID ? this.r19(expectedToken, wasConsumed) : $super.r19.call(this, expectedToken, wasConsumed);
};
protoOf(AbstractJsonLexer).w14 = function () {
  var source = this.k19();
  var cpos = this.rz_1;
  $l$loop_0: while (true) {
    cpos = this.l19(cpos);
    if (cpos === -1)
      break $l$loop_0;
    var ch = charSequenceGet(source, cpos);
    if (ch === _Char___init__impl__6a9atx(32) || ch === _Char___init__impl__6a9atx(10) || ch === _Char___init__impl__6a9atx(13) || ch === _Char___init__impl__6a9atx(9)) {
      cpos = cpos + 1 | 0;
      continue $l$loop_0;
    }
    this.rz_1 = cpos;
    return charToTokenClass(ch);
  }
  this.rz_1 = cpos;
  return 10;
};
protoOf(AbstractJsonLexer).m16 = function (doConsume) {
  var current = this.n19();
  current = this.l19(current);
  var len = charSequenceLength(this.k19()) - current | 0;
  if (len < 4 || current === -1)
    return false;
  var inductionVariable = 0;
  if (inductionVariable <= 3)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      if (!(charCodeAt('null', i) === charSequenceGet(this.k19(), current + i | 0)))
        return false;
    }
     while (inductionVariable <= 3);
  if (len > 4 && charToTokenClass(charSequenceGet(this.k19(), current + 4 | 0)) === 0)
    return false;
  if (doConsume) {
    this.rz_1 = current + 4 | 0;
  }
  return true;
};
protoOf(AbstractJsonLexer).x16 = function (doConsume, $super) {
  doConsume = doConsume === VOID ? true : doConsume;
  return $super === VOID ? this.m16(doConsume) : $super.m16.call(this, doConsume);
};
protoOf(AbstractJsonLexer).n16 = function (isLenient) {
  var token = this.w14();
  var tmp;
  if (isLenient) {
    if (!(token === 1) && !(token === 0))
      return null;
    tmp = this.z14();
  } else {
    if (!(token === 1))
      return null;
    tmp = this.y14();
  }
  var string = tmp;
  this.tz_1 = string;
  return string;
};
protoOf(AbstractJsonLexer).s19 = function () {
  this.tz_1 = null;
};
protoOf(AbstractJsonLexer).t19 = function (startPos, endPos) {
  // Inline function 'kotlin.text.substring' call
  var this_0 = this.k19();
  return toString(charSequenceSubSequence(this_0, startPos, endPos));
};
protoOf(AbstractJsonLexer).y14 = function () {
  if (!(this.tz_1 == null)) {
    return takePeeked(this);
  }
  return this.q16();
};
protoOf(AbstractJsonLexer).consumeString2 = function (source, startPosition, current) {
  var currentPosition = current;
  var lastPosition = startPosition;
  var char = charSequenceGet(source, currentPosition);
  var usedAppend = false;
  while (!(char === _Char___init__impl__6a9atx(34))) {
    if (char === _Char___init__impl__6a9atx(92)) {
      usedAppend = true;
      currentPosition = this.l19(appendEscape(this, lastPosition, currentPosition));
      if (currentPosition === -1) {
        this.u13('Unexpected EOF', currentPosition);
      }
      lastPosition = currentPosition;
    } else {
      currentPosition = currentPosition + 1 | 0;
      if (currentPosition >= charSequenceLength(source)) {
        usedAppend = true;
        this.j19(lastPosition, currentPosition);
        currentPosition = this.l19(currentPosition);
        if (currentPosition === -1) {
          this.u13('Unexpected EOF', currentPosition);
        }
        lastPosition = currentPosition;
      }
    }
    char = charSequenceGet(source, currentPosition);
  }
  var tmp;
  if (!usedAppend) {
    tmp = this.t19(lastPosition, currentPosition);
  } else {
    tmp = decodedString(this, lastPosition, currentPosition);
  }
  var string = tmp;
  this.rz_1 = currentPosition + 1 | 0;
  return string;
};
protoOf(AbstractJsonLexer).r16 = function () {
  var result = this.z14();
  if (result === 'null' && wasUnquotedString(this)) {
    this.u13("Unexpected 'null' value instead of string literal");
  }
  return result;
};
protoOf(AbstractJsonLexer).z14 = function () {
  if (!(this.tz_1 == null)) {
    return takePeeked(this);
  }
  var current = this.n19();
  if (current >= charSequenceLength(this.k19()) || current === -1) {
    this.u13('EOF', current);
  }
  var token = charToTokenClass(charSequenceGet(this.k19(), current));
  if (token === 1) {
    return this.y14();
  }
  if (!(token === 0)) {
    this.u13('Expected beginning of the string, but got ' + toString_1(charSequenceGet(this.k19(), current)));
  }
  var usedAppend = false;
  while (charToTokenClass(charSequenceGet(this.k19(), current)) === 0) {
    current = current + 1 | 0;
    if (current >= charSequenceLength(this.k19())) {
      usedAppend = true;
      this.j19(this.rz_1, current);
      var eof = this.l19(current);
      if (eof === -1) {
        this.rz_1 = current;
        return decodedString(this, 0, 0);
      } else {
        current = eof;
      }
    }
  }
  var tmp;
  if (!usedAppend) {
    tmp = this.t19(this.rz_1, current);
  } else {
    tmp = decodedString(this, this.rz_1, current);
  }
  var result = tmp;
  this.rz_1 = current;
  return result;
};
protoOf(AbstractJsonLexer).j19 = function (fromIndex, toIndex) {
  this.uz_1.aa(this.k19(), fromIndex, toIndex);
};
protoOf(AbstractJsonLexer).p16 = function (allowLenientStrings) {
  // Inline function 'kotlin.collections.mutableListOf' call
  var tokenStack = ArrayList_init_$Create$();
  var lastToken = this.w14();
  if (!(lastToken === 8) && !(lastToken === 6)) {
    this.z14();
    return Unit_instance;
  }
  $l$loop: while (true) {
    lastToken = this.w14();
    if (lastToken === 1) {
      if (allowLenientStrings)
        this.z14();
      else
        this.q16();
      continue $l$loop;
    }
    var tmp0_subject = lastToken;
    if (tmp0_subject === 8 || tmp0_subject === 6) {
      tokenStack.e(lastToken);
    } else if (tmp0_subject === 9) {
      if (!(last(tokenStack) === 8))
        throw JsonDecodingException_0(this.rz_1, 'found ] instead of } at path: ' + this.sz_1.toString(), this.k19());
      removeLast(tokenStack);
    } else if (tmp0_subject === 7) {
      if (!(last(tokenStack) === 6))
        throw JsonDecodingException_0(this.rz_1, 'found } instead of ] at path: ' + this.sz_1.toString(), this.k19());
      removeLast(tokenStack);
    } else if (tmp0_subject === 10) {
      this.u13('Unexpected end of input due to malformed JSON during ignoring unknown keys');
    }
    this.b15();
    if (tokenStack.k() === 0)
      return Unit_instance;
  }
};
protoOf(AbstractJsonLexer).toString = function () {
  return "JsonReader(source='" + toString(this.k19()) + "', currentPosition=" + this.rz_1 + ')';
};
protoOf(AbstractJsonLexer).o16 = function (key) {
  var processed = this.t19(0, this.rz_1);
  var lastIndexOf_0 = lastIndexOf(processed, key);
  this.t13("Encountered an unknown key '" + key + "'", lastIndexOf_0, "Use 'ignoreUnknownKeys = true' in 'Json {}' builder to ignore unknown keys.");
};
protoOf(AbstractJsonLexer).t13 = function (message, position, hint) {
  var tmp;
  // Inline function 'kotlin.text.isEmpty' call
  if (charSequenceLength(hint) === 0) {
    tmp = '';
  } else {
    tmp = '\n' + hint;
  }
  var hintMessage = tmp;
  throw JsonDecodingException_0(position, message + ' at path: ' + this.sz_1.h14() + hintMessage, this.k19());
};
protoOf(AbstractJsonLexer).u13 = function (message, position, hint, $super) {
  position = position === VOID ? this.rz_1 : position;
  hint = hint === VOID ? '' : hint;
  return $super === VOID ? this.t13(message, position, hint) : $super.t13.call(this, message, position, hint);
};
protoOf(AbstractJsonLexer).s11 = function () {
  var current = this.n19();
  current = this.l19(current);
  if (current >= charSequenceLength(this.k19()) || current === -1) {
    this.u13('EOF');
  }
  var tmp;
  if (charSequenceGet(this.k19(), current) === _Char___init__impl__6a9atx(34)) {
    current = current + 1 | 0;
    if (current === charSequenceLength(this.k19())) {
      this.u13('EOF');
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
  $l$loop_4: while (!(current === charSequenceLength(this.k19()))) {
    var ch = charSequenceGet(this.k19(), current);
    if ((ch === _Char___init__impl__6a9atx(101) || ch === _Char___init__impl__6a9atx(69)) && !hasExponent) {
      if (current === start) {
        this.u13('Unexpected symbol ' + toString_1(ch) + ' in numeric literal');
      }
      isExponentPositive = true;
      hasExponent = true;
      current = current + 1 | 0;
      continue $l$loop_4;
    }
    if (ch === _Char___init__impl__6a9atx(45) && hasExponent) {
      if (current === start) {
        this.u13("Unexpected symbol '-' in numeric literal");
      }
      isExponentPositive = false;
      current = current + 1 | 0;
      continue $l$loop_4;
    }
    if (ch === _Char___init__impl__6a9atx(43) && hasExponent) {
      if (current === start) {
        this.u13("Unexpected symbol '+' in numeric literal");
      }
      isExponentPositive = true;
      current = current + 1 | 0;
      continue $l$loop_4;
    }
    if (ch === _Char___init__impl__6a9atx(45)) {
      if (!(current === start)) {
        this.u13("Unexpected symbol '-' in numeric literal");
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
      this.u13("Unexpected symbol '" + toString_1(ch) + "' in numeric literal");
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
      this.u13('Numeric value overflow');
    }
  }
  var hasChars = !(current === start);
  if (start === current || (isNegative && start === (current - 1 | 0))) {
    this.u13('Expected numeric literal');
  }
  if (hasQuotation) {
    if (!hasChars) {
      this.u13('EOF');
    }
    if (!(charSequenceGet(this.k19(), current) === _Char___init__impl__6a9atx(34))) {
      this.u13('Expected closing quotation mark');
    }
    current = current + 1 | 0;
  }
  this.rz_1 = current;
  if (hasExponent) {
    var doubleAccumulator = toNumber(accumulator) * consumeNumericLiteral$calculateExponent(exponentAccumulator, isExponentPositive);
    if (doubleAccumulator > toNumber(new Long(-1, 2147483647)) || doubleAccumulator < toNumber(new Long(0, -2147483648))) {
      this.u13('Numeric value overflow');
    }
    // Inline function 'kotlin.math.floor' call
    if (!(Math.floor(doubleAccumulator) === doubleAccumulator)) {
      this.u13("Can't convert " + doubleAccumulator + ' to Long');
    }
    accumulator = numberToLong(doubleAccumulator);
  }
  var tmp_0;
  if (isNegative) {
    tmp_0 = accumulator;
  } else if (!equalsLong(accumulator, new Long(0, -2147483648))) {
    tmp_0 = negate(accumulator);
  } else {
    this.u13('Numeric value overflow');
  }
  return tmp_0;
};
protoOf(AbstractJsonLexer).y16 = function () {
  var current = this.n19();
  if (current === charSequenceLength(this.k19())) {
    this.u13('EOF');
  }
  var tmp;
  if (charSequenceGet(this.k19(), current) === _Char___init__impl__6a9atx(34)) {
    current = current + 1 | 0;
    tmp = true;
  } else {
    tmp = false;
  }
  var hasQuotation = tmp;
  var result = consumeBoolean2(this, current);
  if (hasQuotation) {
    if (this.rz_1 === charSequenceLength(this.k19())) {
      this.u13('EOF');
    }
    if (!(charSequenceGet(this.k19(), this.rz_1) === _Char___init__impl__6a9atx(34))) {
      this.u13('Expected closing quotation mark');
    }
    this.rz_1 = this.rz_1 + 1 | 0;
  }
  return result;
};
function charToTokenClass(c) {
  var tmp;
  // Inline function 'kotlin.code' call
  if (Char__toInt_impl_vasixd(c) < 126) {
    var tmp_0 = CharMappings_getInstance().v19_1;
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
  return c < 117 ? CharMappings_getInstance().u19_1[c] : _Char___init__impl__6a9atx(0);
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
    $this.u19_1[tmp$ret$0] = numberToChar(c);
  }
}
function initC2ESC_0($this, c, esc) {
  // Inline function 'kotlin.code' call
  var tmp$ret$0 = Char__toInt_impl_vasixd(c);
  return initC2ESC($this, tmp$ret$0, esc);
}
function initC2TC($this, c, cl) {
  $this.v19_1[c] = cl;
}
function initC2TC_0($this, c, cl) {
  // Inline function 'kotlin.code' call
  var tmp$ret$0 = Char__toInt_impl_vasixd(c);
  return initC2TC($this, tmp$ret$0, cl);
}
function CharMappings() {
  CharMappings_instance = this;
  this.u19_1 = charArray(117);
  this.v19_1 = new Int8Array(126);
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
protoOf(StringJsonLexerWithComments).b15 = function () {
  var source = this.k19();
  var cpos = this.n19();
  if (cpos >= source.length || cpos === -1)
    return 10;
  this.rz_1 = cpos + 1 | 0;
  return charToTokenClass(charCodeAt(source, cpos));
};
protoOf(StringJsonLexerWithComments).x14 = function () {
  var current = this.n19();
  if (current >= this.k19().length || current === -1)
    return false;
  return this.o19(charCodeAt(this.k19(), current));
};
protoOf(StringJsonLexerWithComments).k16 = function (expected) {
  var source = this.k19();
  var current = this.n19();
  if (current >= source.length || current === -1) {
    this.rz_1 = -1;
    this.q19(expected);
  }
  var c = charCodeAt(source, current);
  this.rz_1 = current + 1 | 0;
  if (c === expected)
    return Unit_instance;
  else {
    this.q19(expected);
  }
};
protoOf(StringJsonLexerWithComments).w14 = function () {
  var source = this.k19();
  var cpos = this.n19();
  if (cpos >= source.length || cpos === -1)
    return 10;
  this.rz_1 = cpos;
  return charToTokenClass(charCodeAt(source, cpos));
};
protoOf(StringJsonLexerWithComments).n19 = function () {
  var current = this.rz_1;
  if (current === -1)
    return current;
  var source = this.k19();
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
          this.rz_1 = source.length;
          this.u13('Expected end of the block comment: "*/", but had EOF instead');
        } else {
          current = current + 2 | 0;
        }
        continue $l$loop_1;
      }
    }
    break $l$loop_1;
  }
  this.rz_1 = current;
  return current;
};
function StringJsonLexer(source) {
  AbstractJsonLexer.call(this);
  this.f1a_1 = source;
}
protoOf(StringJsonLexer).k19 = function () {
  return this.f1a_1;
};
protoOf(StringJsonLexer).l19 = function (position) {
  return position < this.k19().length ? position : -1;
};
protoOf(StringJsonLexer).b15 = function () {
  var source = this.k19();
  var cpos = this.rz_1;
  $l$loop: while (!(cpos === -1) && cpos < source.length) {
    var _unary__edvuaz = cpos;
    cpos = _unary__edvuaz + 1 | 0;
    var c = charCodeAt(source, _unary__edvuaz);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.isWs' call
    if (c === _Char___init__impl__6a9atx(32) || c === _Char___init__impl__6a9atx(10) || c === _Char___init__impl__6a9atx(13) || c === _Char___init__impl__6a9atx(9))
      continue $l$loop;
    this.rz_1 = cpos;
    return charToTokenClass(c);
  }
  this.rz_1 = source.length;
  return 10;
};
protoOf(StringJsonLexer).x14 = function () {
  var current = this.rz_1;
  if (current === -1)
    return false;
  var source = this.k19();
  $l$loop: while (current < source.length) {
    var c = charCodeAt(source, current);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.isWs' call
    if (c === _Char___init__impl__6a9atx(32) || c === _Char___init__impl__6a9atx(10) || c === _Char___init__impl__6a9atx(13) || c === _Char___init__impl__6a9atx(9)) {
      current = current + 1 | 0;
      continue $l$loop;
    }
    this.rz_1 = current;
    return this.o19(c);
  }
  this.rz_1 = current;
  return false;
};
protoOf(StringJsonLexer).n19 = function () {
  var current = this.rz_1;
  if (current === -1)
    return current;
  var source = this.k19();
  $l$loop: while (current < source.length) {
    var c = charCodeAt(source, current);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.isWs' call
    if (c === _Char___init__impl__6a9atx(32) || c === _Char___init__impl__6a9atx(10) || c === _Char___init__impl__6a9atx(13) || c === _Char___init__impl__6a9atx(9)) {
      current = current + 1 | 0;
    } else {
      break $l$loop;
    }
  }
  this.rz_1 = current;
  return current;
};
protoOf(StringJsonLexer).k16 = function (expected) {
  if (this.rz_1 === -1) {
    this.q19(expected);
  }
  var source = this.k19();
  var cpos = this.rz_1;
  $l$loop: while (cpos < source.length) {
    var _unary__edvuaz = cpos;
    cpos = _unary__edvuaz + 1 | 0;
    var c = charCodeAt(source, _unary__edvuaz);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.isWs' call
    if (c === _Char___init__impl__6a9atx(32) || c === _Char___init__impl__6a9atx(10) || c === _Char___init__impl__6a9atx(13) || c === _Char___init__impl__6a9atx(9))
      continue $l$loop;
    this.rz_1 = cpos;
    if (c === expected)
      return Unit_instance;
    this.q19(expected);
  }
  this.rz_1 = -1;
  this.q19(expected);
};
protoOf(StringJsonLexer).q16 = function () {
  this.k16(_Char___init__impl__6a9atx(34));
  var current = this.rz_1;
  var closingQuote = indexOf_0(this.k19(), _Char___init__impl__6a9atx(34), current);
  if (closingQuote === -1) {
    this.z14();
    this.r19(1, false);
  }
  var inductionVariable = current;
  if (inductionVariable < closingQuote)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      if (charCodeAt(this.k19(), i) === _Char___init__impl__6a9atx(92)) {
        return this.consumeString2(this.k19(), this.rz_1, i);
      }
    }
     while (inductionVariable < closingQuote);
  this.rz_1 = closingQuote + 1 | 0;
  return substring(this.k19(), current, closingQuote);
};
protoOf(StringJsonLexer).s16 = function (keyToMatch, isLenient) {
  var positionSnapshot = this.rz_1;
  try {
    if (!(this.b15() === 6))
      return null;
    var firstKey = this.n16(isLenient);
    if (!(firstKey === keyToMatch))
      return null;
    this.s19();
    if (!(this.b15() === 5))
      return null;
    return this.n16(isLenient);
  }finally {
    this.rz_1 = positionSnapshot;
    this.s19();
  }
};
function StringJsonLexer_0(json, source) {
  return !json.bz_1.e11_1 ? new StringJsonLexer(source) : new StringJsonLexerWithComments(source);
}
function get_schemaCache(_this__u8e3s4) {
  return _this__u8e3s4.dz_1;
}
function JsonToStringWriter() {
  this.hz_1 = StringBuilder_init_$Create$_0(128);
}
protoOf(JsonToStringWriter).y12 = function (value) {
  this.hz_1.da(value);
};
protoOf(JsonToStringWriter).s12 = function (char) {
  this.hz_1.b7(char);
};
protoOf(JsonToStringWriter).u12 = function (text) {
  this.hz_1.a7(text);
};
protoOf(JsonToStringWriter).e13 = function (text) {
  printQuoted(this.hz_1, text);
};
protoOf(JsonToStringWriter).iz = function () {
  this.hz_1.ga();
};
protoOf(JsonToStringWriter).toString = function () {
  return this.hz_1.toString();
};
function createMapForCache(initialCapacity) {
  return HashMap_init_$Create$(initialCapacity);
}
//region block: post-declaration
protoOf(defer$1).aj = get_isNullable;
protoOf(defer$1).ij = get_isInline;
protoOf(defer$1).kj = get_annotations;
protoOf(PolymorphismValidator).xy = contextual;
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
  Json_0 as Jsonsmkyu9xjl7fv,
  get_contentOrNull as get_contentOrNull35s97cgi8z9eo,
};
//endregion


