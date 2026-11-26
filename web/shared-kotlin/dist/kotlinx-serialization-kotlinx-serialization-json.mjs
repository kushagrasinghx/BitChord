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
initMetadataForInterface(JsonDecoder, 'JsonDecoder', VOID, VOID, [CompositeDecoder, Decoder]);
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
initMetadataForInterface(JsonEncoder, 'JsonEncoder', VOID, VOID, [CompositeEncoder, Encoder]);
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
  this.sz_1 = configuration;
  this.tz_1 = serializersModule;
  this.uz_1 = new DescriptorSchemaCache();
}
protoOf(Json).lm = function () {
  return this.tz_1;
};
protoOf(Json).vz = function (serializer, value) {
  var result = new JsonToStringWriter();
  try {
    encodeByWriter(this, result, serializer, value);
    return result.toString();
  }finally {
    result.zz();
  }
};
protoOf(Json).wz = function (deserializer, string) {
  var lexer = StringJsonLexer_0(this, string);
  var input = new StreamingJsonDecoder(this, WriteMode_OBJ_getInstance(), lexer, deserializer.jj(), null);
  var result = input.vl(deserializer);
  lexer.m10();
  return result;
};
protoOf(Json).xz = function (string) {
  return this.wz(JsonElementSerializer_getInstance(), string);
};
function Json_0(from, builderAction) {
  from = from === VOID ? Default_getInstance() : from;
  var builder = new JsonBuilder(from);
  builderAction(builder);
  var conf = builder.f11();
  return new JsonImpl(conf, builder.e11_1);
}
function JsonBuilder(json) {
  this.n10_1 = json.sz_1.g11_1;
  this.o10_1 = json.sz_1.l11_1;
  this.p10_1 = json.sz_1.h11_1;
  this.q10_1 = json.sz_1.i11_1;
  this.r10_1 = json.sz_1.k11_1;
  this.s10_1 = json.sz_1.m11_1;
  this.t10_1 = json.sz_1.n11_1;
  this.u10_1 = json.sz_1.p11_1;
  this.v10_1 = json.sz_1.w11_1;
  this.w10_1 = json.sz_1.r11_1;
  this.x10_1 = json.sz_1.s11_1;
  this.y10_1 = json.sz_1.t11_1;
  this.z10_1 = json.sz_1.u11_1;
  this.a11_1 = json.sz_1.v11_1;
  this.b11_1 = json.sz_1.q11_1;
  this.c11_1 = json.sz_1.j11_1;
  this.d11_1 = json.sz_1.o11_1;
  this.e11_1 = json.lm();
}
protoOf(JsonBuilder).f11 = function () {
  if (this.d11_1) {
    // Inline function 'kotlin.require' call
    if (!(this.u10_1 === 'type')) {
      var message = 'Class discriminator should not be specified when array polymorphism is specified';
      throw IllegalArgumentException_init_$Create$(toString(message));
    }
    // Inline function 'kotlin.require' call
    if (!this.v10_1.equals(ClassDiscriminatorMode_POLYMORPHIC_getInstance())) {
      var message_0 = 'useArrayPolymorphism option can only be used if classDiscriminatorMode in a default POLYMORPHIC state.';
      throw IllegalArgumentException_init_$Create$(toString(message_0));
    }
  }
  if (!this.r10_1) {
    // Inline function 'kotlin.require' call
    if (!(this.s10_1 === '    ')) {
      var message_1 = 'Indent should not be specified when default printing mode is used';
      throw IllegalArgumentException_init_$Create$(toString(message_1));
    }
  } else if (!(this.s10_1 === '    ')) {
    var tmp0 = this.s10_1;
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
      var message_2 = 'Only whitespace, tab, newline and carriage return are allowed as pretty print symbols. Had ' + this.s10_1;
      throw IllegalArgumentException_init_$Create$(toString(message_2));
    }
  }
  return new JsonConfiguration(this.n10_1, this.p10_1, this.q10_1, this.c11_1, this.r10_1, this.o10_1, this.s10_1, this.t10_1, this.d11_1, this.u10_1, this.b11_1, this.w10_1, this.x10_1, this.y10_1, this.z10_1, this.a11_1, this.v10_1);
};
function validateConfiguration($this) {
  if (equals($this.lm(), EmptySerializersModule()))
    return Unit_instance;
  var collector = new PolymorphismValidator($this.sz_1.o11_1, $this.sz_1.p11_1);
  $this.lm().dz(collector);
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
  this.g11_1 = encodeDefaults;
  this.h11_1 = ignoreUnknownKeys;
  this.i11_1 = isLenient;
  this.j11_1 = allowStructuredMapKeys;
  this.k11_1 = prettyPrint;
  this.l11_1 = explicitNulls;
  this.m11_1 = prettyPrintIndent;
  this.n11_1 = coerceInputValues;
  this.o11_1 = useArrayPolymorphism;
  this.p11_1 = classDiscriminator;
  this.q11_1 = allowSpecialFloatingPointValues;
  this.r11_1 = useAlternativeNames;
  this.s11_1 = namingStrategy;
  this.t11_1 = decodeEnumsCaseInsensitive;
  this.u11_1 = allowTrailingComma;
  this.v11_1 = allowComments;
  this.w11_1 = classDiscriminatorMode;
}
protoOf(JsonConfiguration).toString = function () {
  return 'JsonConfiguration(encodeDefaults=' + this.g11_1 + ', ignoreUnknownKeys=' + this.h11_1 + ', isLenient=' + this.i11_1 + ', ' + ('allowStructuredMapKeys=' + this.j11_1 + ', prettyPrint=' + this.k11_1 + ', explicitNulls=' + this.l11_1 + ', ') + ("prettyPrintIndent='" + this.m11_1 + "', coerceInputValues=" + this.n11_1 + ', useArrayPolymorphism=' + this.o11_1 + ', ') + ("classDiscriminator='" + this.p11_1 + "', allowSpecialFloatingPointValues=" + this.q11_1 + ', ') + ('useAlternativeNames=' + this.r11_1 + ', namingStrategy=' + toString_0(this.s11_1) + ', decodeEnumsCaseInsensitive=' + this.t11_1 + ', ') + ('allowTrailingComma=' + this.u11_1 + ', allowComments=' + this.v11_1 + ', classDiscriminatorMode=' + this.w11_1.toString() + ')');
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
  var k = _destruct__k2r9zo.r1();
  // Inline function 'kotlin.collections.component2' call
  var v = _destruct__k2r9zo.s1();
  // Inline function 'kotlin.text.buildString' call
  // Inline function 'kotlin.apply' call
  var this_0 = StringBuilder_init_$Create$();
  printQuoted(this_0, k);
  this_0.d7(_Char___init__impl__6a9atx(58));
  this_0.b7(v);
  return this_0.toString();
}
function JsonObject(content) {
  JsonElement.call(this);
  this.z11_1 = content;
}
protoOf(JsonObject).equals = function (other) {
  return equals(this.z11_1, other);
};
protoOf(JsonObject).hashCode = function () {
  return hashCode(this.z11_1);
};
protoOf(JsonObject).toString = function () {
  var tmp = this.z11_1.y1();
  return joinToString(tmp, ',', '{', '}', VOID, VOID, JsonObject$toString$lambda);
};
protoOf(JsonObject).a12 = function (key) {
  return this.z11_1.t1(key);
};
protoOf(JsonObject).t1 = function (key) {
  if (!(!(key == null) ? typeof key === 'string' : false))
    return false;
  return this.a12((!(key == null) ? typeof key === 'string' : false) ? key : THROW_CCE());
};
protoOf(JsonObject).mb = function (key) {
  return this.z11_1.v1(key);
};
protoOf(JsonObject).v1 = function (key) {
  if (!(!(key == null) ? typeof key === 'string' : false))
    return null;
  return this.mb((!(key == null) ? typeof key === 'string' : false) ? key : THROW_CCE());
};
protoOf(JsonObject).p = function () {
  return this.z11_1.p();
};
protoOf(JsonObject).y1 = function () {
  return this.z11_1.y1();
};
protoOf(JsonObject).w1 = function () {
  return this.z11_1.w1();
};
protoOf(JsonObject).n = function () {
  return this.z11_1.n();
};
protoOf(JsonObject).x1 = function () {
  return this.z11_1.x1();
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
  return this.b12();
};
function get_contentOrNull(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  var tmp;
  if (_this__u8e3s4 instanceof JsonNull) {
    tmp = null;
  } else {
    tmp = _this__u8e3s4.b12();
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
  this.c12_1 = content;
}
protoOf(JsonArray).equals = function (other) {
  return equals(this.c12_1, other);
};
protoOf(JsonArray).hashCode = function () {
  return hashCode(this.c12_1);
};
protoOf(JsonArray).toString = function () {
  return joinToString(this.c12_1, ',', '[', ']');
};
protoOf(JsonArray).d12 = function (element) {
  return this.c12_1.b1(element);
};
protoOf(JsonArray).b1 = function (element) {
  if (!(element instanceof JsonElement))
    return false;
  return this.d12(element instanceof JsonElement ? element : THROW_CCE());
};
protoOf(JsonArray).o = function (index) {
  return this.c12_1.o(index);
};
protoOf(JsonArray).e12 = function (element) {
  return this.c12_1.p1(element);
};
protoOf(JsonArray).p1 = function (element) {
  if (!(element instanceof JsonElement))
    return -1;
  return this.e12(element instanceof JsonElement ? element : THROW_CCE());
};
protoOf(JsonArray).p = function () {
  return this.c12_1.p();
};
protoOf(JsonArray).k = function () {
  return this.c12_1.k();
};
protoOf(JsonArray).r = function (index) {
  return this.c12_1.r(index);
};
protoOf(JsonArray).n = function () {
  return this.c12_1.n();
};
function JsonNull() {
  JsonNull_instance = this;
  JsonPrimitive.call(this);
  this.f12_1 = 'null';
}
protoOf(JsonNull).b12 = function () {
  return this.f12_1;
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
  this.g12_1 = isString;
  this.h12_1 = coerceToInlineType;
  this.i12_1 = toString(body);
  if (!(this.h12_1 == null)) {
    // Inline function 'kotlin.require' call
    // Inline function 'kotlin.require' call
    if (!this.h12_1.zj()) {
      var message = 'Failed requirement.';
      throw IllegalArgumentException_init_$Create$(toString(message));
    }
  }
}
protoOf(JsonLiteral).b12 = function () {
  return this.i12_1;
};
protoOf(JsonLiteral).toString = function () {
  var tmp;
  if (this.g12_1) {
    // Inline function 'kotlin.text.buildString' call
    // Inline function 'kotlin.apply' call
    var this_0 = StringBuilder_init_$Create$();
    printQuoted(this_0, this.i12_1);
    tmp = this_0.toString();
  } else {
    tmp = this.i12_1;
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
  if (!(this.g12_1 === other.g12_1))
    return false;
  if (!(this.i12_1 === other.i12_1))
    return false;
  return true;
};
protoOf(JsonLiteral).hashCode = function () {
  var result = getBooleanHashCode(this.g12_1);
  result = imul(31, result) + getStringHashCode(this.i12_1) | 0;
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
  return toBooleanStrictOrNull_0(_this__u8e3s4.b12());
}
function get_int(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  // Inline function 'kotlinx.serialization.json.mapExceptions' call
  var tmp;
  try {
    tmp = (new StringJsonLexer(_this__u8e3s4.b12())).j12();
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
    throw NumberFormatException_init_$Create$(_this__u8e3s4.b12() + ' is not an Int');
  return convertToInt(result);
}
function get_long(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  // Inline function 'kotlinx.serialization.json.mapExceptions' call
  var tmp;
  try {
    tmp = (new StringJsonLexer(_this__u8e3s4.b12())).j12();
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
  var this_0 = _this__u8e3s4.b12();
  // Inline function 'kotlin.js.unsafeCast' call
  // Inline function 'kotlin.js.asDynamic' call
  return toDouble(this_0);
}
function get_double(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  return toDouble(_this__u8e3s4.b12());
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
  this.k12_1 = MapSerializer(serializer(StringCompanionObject_instance), JsonElementSerializer_getInstance()).jj();
  this.l12_1 = 'kotlinx.serialization.json.JsonObject';
}
protoOf(JsonObjectDescriptor).xj = function () {
  return this.l12_1;
};
protoOf(JsonObjectDescriptor).ck = function (index) {
  return this.k12_1.ck(index);
};
protoOf(JsonObjectDescriptor).dk = function (name) {
  return this.k12_1.dk(name);
};
protoOf(JsonObjectDescriptor).ek = function (index) {
  return this.k12_1.ek(index);
};
protoOf(JsonObjectDescriptor).fk = function (index) {
  return this.k12_1.fk(index);
};
protoOf(JsonObjectDescriptor).gk = function (index) {
  return this.k12_1.gk(index);
};
protoOf(JsonObjectDescriptor).yj = function () {
  return this.k12_1.yj();
};
protoOf(JsonObjectDescriptor).rj = function () {
  return this.k12_1.rj();
};
protoOf(JsonObjectDescriptor).zj = function () {
  return this.k12_1.zj();
};
protoOf(JsonObjectDescriptor).ak = function () {
  return this.k12_1.ak();
};
protoOf(JsonObjectDescriptor).bk = function () {
  return this.k12_1.bk();
};
var JsonObjectDescriptor_instance;
function JsonObjectDescriptor_getInstance() {
  if (JsonObjectDescriptor_instance == null)
    new JsonObjectDescriptor();
  return JsonObjectDescriptor_instance;
}
function JsonObjectSerializer() {
  JsonObjectSerializer_instance = this;
  this.m12_1 = JsonObjectDescriptor_getInstance();
}
protoOf(JsonObjectSerializer).jj = function () {
  return this.m12_1;
};
protoOf(JsonObjectSerializer).n12 = function (encoder, value) {
  verify(encoder);
  MapSerializer(serializer(StringCompanionObject_instance), JsonElementSerializer_getInstance()).kj(encoder, value);
};
protoOf(JsonObjectSerializer).kj = function (encoder, value) {
  return this.n12(encoder, value instanceof JsonObject ? value : THROW_CCE());
};
protoOf(JsonObjectSerializer).lj = function (decoder) {
  verify_0(decoder);
  return new JsonObject(MapSerializer(serializer(StringCompanionObject_instance), JsonElementSerializer_getInstance()).lj(decoder));
};
var JsonObjectSerializer_instance;
function JsonObjectSerializer_getInstance() {
  if (JsonObjectSerializer_instance == null)
    new JsonObjectSerializer();
  return JsonObjectSerializer_instance;
}
function JsonElementSerializer$descriptor$lambda($this$buildSerialDescriptor) {
  $this$buildSerialDescriptor.tk('JsonPrimitive', defer(JsonElementSerializer$descriptor$lambda$lambda));
  $this$buildSerialDescriptor.tk('JsonNull', defer(JsonElementSerializer$descriptor$lambda$lambda_0));
  $this$buildSerialDescriptor.tk('JsonLiteral', defer(JsonElementSerializer$descriptor$lambda$lambda_1));
  $this$buildSerialDescriptor.tk('JsonObject', defer(JsonElementSerializer$descriptor$lambda$lambda_2));
  $this$buildSerialDescriptor.tk('JsonArray', defer(JsonElementSerializer$descriptor$lambda$lambda_3));
  return Unit_instance;
}
function JsonElementSerializer$descriptor$lambda$lambda() {
  return JsonPrimitiveSerializer_getInstance().o12_1;
}
function JsonElementSerializer$descriptor$lambda$lambda_0() {
  return JsonNullSerializer_getInstance().p12_1;
}
function JsonElementSerializer$descriptor$lambda$lambda_1() {
  return JsonLiteralSerializer_getInstance().q12_1;
}
function JsonElementSerializer$descriptor$lambda$lambda_2() {
  return JsonObjectSerializer_getInstance().m12_1;
}
function JsonElementSerializer$descriptor$lambda$lambda_3() {
  return JsonArraySerializer_getInstance().r12_1;
}
function JsonElementSerializer() {
  JsonElementSerializer_instance = this;
  var tmp = this;
  var tmp_0 = SEALED_getInstance();
  tmp.s12_1 = buildSerialDescriptor('kotlinx.serialization.json.JsonElement', tmp_0, [], JsonElementSerializer$descriptor$lambda);
}
protoOf(JsonElementSerializer).jj = function () {
  return this.s12_1;
};
protoOf(JsonElementSerializer).t12 = function (encoder, value) {
  verify(encoder);
  if (value instanceof JsonPrimitive) {
    encoder.nn(JsonPrimitiveSerializer_getInstance(), value);
  } else {
    if (value instanceof JsonObject) {
      encoder.nn(JsonObjectSerializer_getInstance(), value);
    } else {
      if (value instanceof JsonArray) {
        encoder.nn(JsonArraySerializer_getInstance(), value);
      } else {
        noWhenBranchMatchedException();
      }
    }
  }
};
protoOf(JsonElementSerializer).kj = function (encoder, value) {
  return this.t12(encoder, value instanceof JsonElement ? value : THROW_CCE());
};
protoOf(JsonElementSerializer).lj = function (decoder) {
  var input = asJsonDecoder(decoder);
  return input.y11();
};
var JsonElementSerializer_instance;
function JsonElementSerializer_getInstance() {
  if (JsonElementSerializer_instance == null)
    new JsonElementSerializer();
  return JsonElementSerializer_instance;
}
function JsonPrimitiveSerializer() {
  JsonPrimitiveSerializer_instance = this;
  this.o12_1 = buildSerialDescriptor('kotlinx.serialization.json.JsonPrimitive', STRING_getInstance(), []);
}
protoOf(JsonPrimitiveSerializer).jj = function () {
  return this.o12_1;
};
protoOf(JsonPrimitiveSerializer).u12 = function (encoder, value) {
  verify(encoder);
  var tmp;
  if (value instanceof JsonNull) {
    encoder.nn(JsonNullSerializer_getInstance(), JsonNull_getInstance());
    tmp = Unit_instance;
  } else {
    var tmp_0 = JsonLiteralSerializer_getInstance();
    encoder.nn(tmp_0, value instanceof JsonLiteral ? value : THROW_CCE());
    tmp = Unit_instance;
  }
  return tmp;
};
protoOf(JsonPrimitiveSerializer).kj = function (encoder, value) {
  return this.u12(encoder, value instanceof JsonPrimitive ? value : THROW_CCE());
};
protoOf(JsonPrimitiveSerializer).lj = function (decoder) {
  var result = asJsonDecoder(decoder).y11();
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
  this.v12_1 = ListSerializer(JsonElementSerializer_getInstance()).jj();
  this.w12_1 = 'kotlinx.serialization.json.JsonArray';
}
protoOf(JsonArrayDescriptor).xj = function () {
  return this.w12_1;
};
protoOf(JsonArrayDescriptor).ck = function (index) {
  return this.v12_1.ck(index);
};
protoOf(JsonArrayDescriptor).dk = function (name) {
  return this.v12_1.dk(name);
};
protoOf(JsonArrayDescriptor).ek = function (index) {
  return this.v12_1.ek(index);
};
protoOf(JsonArrayDescriptor).fk = function (index) {
  return this.v12_1.fk(index);
};
protoOf(JsonArrayDescriptor).gk = function (index) {
  return this.v12_1.gk(index);
};
protoOf(JsonArrayDescriptor).yj = function () {
  return this.v12_1.yj();
};
protoOf(JsonArrayDescriptor).rj = function () {
  return this.v12_1.rj();
};
protoOf(JsonArrayDescriptor).zj = function () {
  return this.v12_1.zj();
};
protoOf(JsonArrayDescriptor).ak = function () {
  return this.v12_1.ak();
};
protoOf(JsonArrayDescriptor).bk = function () {
  return this.v12_1.bk();
};
var JsonArrayDescriptor_instance;
function JsonArrayDescriptor_getInstance() {
  if (JsonArrayDescriptor_instance == null)
    new JsonArrayDescriptor();
  return JsonArrayDescriptor_instance;
}
function JsonArraySerializer() {
  JsonArraySerializer_instance = this;
  this.r12_1 = JsonArrayDescriptor_getInstance();
}
protoOf(JsonArraySerializer).jj = function () {
  return this.r12_1;
};
protoOf(JsonArraySerializer).x12 = function (encoder, value) {
  verify(encoder);
  ListSerializer(JsonElementSerializer_getInstance()).kj(encoder, value);
};
protoOf(JsonArraySerializer).kj = function (encoder, value) {
  return this.x12(encoder, value instanceof JsonArray ? value : THROW_CCE());
};
protoOf(JsonArraySerializer).lj = function (decoder) {
  verify_0(decoder);
  return new JsonArray(ListSerializer(JsonElementSerializer_getInstance()).lj(decoder));
};
var JsonArraySerializer_instance;
function JsonArraySerializer_getInstance() {
  if (JsonArraySerializer_instance == null)
    new JsonArraySerializer();
  return JsonArraySerializer_instance;
}
function JsonNullSerializer() {
  JsonNullSerializer_instance = this;
  this.p12_1 = buildSerialDescriptor('kotlinx.serialization.json.JsonNull', ENUM_getInstance(), []);
}
protoOf(JsonNullSerializer).jj = function () {
  return this.p12_1;
};
protoOf(JsonNullSerializer).y12 = function (encoder, value) {
  verify(encoder);
  encoder.rm();
};
protoOf(JsonNullSerializer).kj = function (encoder, value) {
  return this.y12(encoder, value instanceof JsonNull ? value : THROW_CCE());
};
protoOf(JsonNullSerializer).lj = function (decoder) {
  verify_0(decoder);
  if (decoder.il()) {
    throw new JsonDecodingException("Expected 'null' literal");
  }
  decoder.jl();
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
  this.q12_1 = PrimitiveSerialDescriptor('kotlinx.serialization.json.JsonLiteral', STRING_getInstance());
}
protoOf(JsonLiteralSerializer).jj = function () {
  return this.q12_1;
};
protoOf(JsonLiteralSerializer).z12 = function (encoder, value) {
  verify(encoder);
  if (value.g12_1) {
    return encoder.an(value.i12_1);
  }
  if (!(value.h12_1 == null)) {
    return encoder.bn(value.h12_1).an(value.i12_1);
  }
  var tmp0_safe_receiver = toLongOrNull(value.i12_1);
  if (tmp0_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    return encoder.wm(tmp0_safe_receiver);
  }
  var tmp1_safe_receiver = toULongOrNull(value.i12_1);
  var tmp = tmp1_safe_receiver;
  if ((tmp == null ? null : new ULong(tmp)) == null)
    null;
  else {
    var tmp_0 = tmp1_safe_receiver;
    // Inline function 'kotlin.let' call
    var it = (tmp_0 == null ? null : new ULong(tmp_0)).ri_1;
    var tmp_1 = encoder.bn(serializer_0(Companion_getInstance()).jj());
    // Inline function 'kotlin.ULong.toLong' call
    var tmp$ret$4 = _ULong___get_data__impl__fggpzb(it);
    tmp_1.wm(tmp$ret$4);
    return Unit_instance;
  }
  var tmp2_safe_receiver = toDoubleOrNull(value.i12_1);
  if (tmp2_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    return encoder.ym(tmp2_safe_receiver);
  }
  var tmp3_safe_receiver = toBooleanStrictOrNull(value.i12_1);
  if (tmp3_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    return encoder.sm(tmp3_safe_receiver);
  }
  encoder.an(value.i12_1);
};
protoOf(JsonLiteralSerializer).kj = function (encoder, value) {
  return this.z12(encoder, value instanceof JsonLiteral ? value : THROW_CCE());
};
protoOf(JsonLiteralSerializer).lj = function (decoder) {
  var result = asJsonDecoder(decoder).y11();
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
  var tmp0 = $this.a13_1;
  var tmp = KProperty1;
  // Inline function 'kotlin.getValue' call
  getPropertyCallableRef('original', 1, tmp, defer$o$_get_original_$ref_3cje7k(), null);
  return tmp0.s1();
}
function defer$o$_get_original_$ref_3cje7k() {
  return function (p0) {
    return _get_original__l7ku1m(p0);
  };
}
function defer$1($deferred) {
  this.a13_1 = lazy($deferred);
}
protoOf(defer$1).xj = function () {
  return _get_original__l7ku1m(this).xj();
};
protoOf(defer$1).yj = function () {
  return _get_original__l7ku1m(this).yj();
};
protoOf(defer$1).ak = function () {
  return _get_original__l7ku1m(this).ak();
};
protoOf(defer$1).ck = function (index) {
  return _get_original__l7ku1m(this).ck(index);
};
protoOf(defer$1).dk = function (name) {
  return _get_original__l7ku1m(this).dk(name);
};
protoOf(defer$1).ek = function (index) {
  return _get_original__l7ku1m(this).ek(index);
};
protoOf(defer$1).fk = function (index) {
  return _get_original__l7ku1m(this).fk(index);
};
protoOf(defer$1).gk = function (index) {
  return _get_original__l7ku1m(this).gk(index);
};
function JsonEncoder() {
}
function Composer(writer) {
  this.b13_1 = writer;
  this.c13_1 = true;
}
protoOf(Composer).d13 = function () {
  this.c13_1 = true;
};
protoOf(Composer).e13 = function () {
  return Unit_instance;
};
protoOf(Composer).f13 = function () {
  this.c13_1 = false;
};
protoOf(Composer).g13 = function () {
  this.c13_1 = false;
};
protoOf(Composer).h13 = function () {
  return Unit_instance;
};
protoOf(Composer).i13 = function (v) {
  return this.b13_1.j13(v);
};
protoOf(Composer).k13 = function (v) {
  return this.b13_1.l13(v);
};
protoOf(Composer).m13 = function (v) {
  return this.b13_1.l13(v.toString());
};
protoOf(Composer).n13 = function (v) {
  return this.b13_1.l13(v.toString());
};
protoOf(Composer).o13 = function (v) {
  return this.b13_1.p13(fromInt(v));
};
protoOf(Composer).q13 = function (v) {
  return this.b13_1.p13(fromInt(v));
};
protoOf(Composer).r13 = function (v) {
  return this.b13_1.p13(fromInt(v));
};
protoOf(Composer).s13 = function (v) {
  return this.b13_1.p13(v);
};
protoOf(Composer).t13 = function (v) {
  return this.b13_1.l13(v.toString());
};
protoOf(Composer).u13 = function (value) {
  return this.b13_1.v13(value);
};
function Composer_0(sb, json) {
  return json.sz_1.k11_1 ? new ComposerWithPrettyPrint(sb, json) : new Composer(sb);
}
function ComposerForUnsignedNumbers(writer, forceQuoting) {
  Composer.call(this, writer);
  this.y13_1 = forceQuoting;
}
protoOf(ComposerForUnsignedNumbers).r13 = function (v) {
  if (this.y13_1) {
    // Inline function 'kotlin.toUInt' call
    var tmp$ret$0 = _UInt___init__impl__l7qpdl(v);
    this.u13(UInt__toString_impl_dbgl21(tmp$ret$0));
  } else {
    // Inline function 'kotlin.toUInt' call
    var tmp$ret$1 = _UInt___init__impl__l7qpdl(v);
    this.k13(UInt__toString_impl_dbgl21(tmp$ret$1));
  }
};
protoOf(ComposerForUnsignedNumbers).s13 = function (v) {
  if (this.y13_1) {
    // Inline function 'kotlin.toULong' call
    var tmp$ret$0 = _ULong___init__impl__c78o9k(v);
    this.u13(ULong__toString_impl_f9au7k(tmp$ret$0));
  } else {
    // Inline function 'kotlin.toULong' call
    var tmp$ret$1 = _ULong___init__impl__c78o9k(v);
    this.k13(ULong__toString_impl_f9au7k(tmp$ret$1));
  }
};
protoOf(ComposerForUnsignedNumbers).o13 = function (v) {
  if (this.y13_1) {
    // Inline function 'kotlin.toUByte' call
    var tmp$ret$0 = _UByte___init__impl__g9hnc4(v);
    this.u13(UByte__toString_impl_v72jg(tmp$ret$0));
  } else {
    // Inline function 'kotlin.toUByte' call
    var tmp$ret$1 = _UByte___init__impl__g9hnc4(v);
    this.k13(UByte__toString_impl_v72jg(tmp$ret$1));
  }
};
protoOf(ComposerForUnsignedNumbers).q13 = function (v) {
  if (this.y13_1) {
    // Inline function 'kotlin.toUShort' call
    var tmp$ret$0 = _UShort___init__impl__jigrne(v);
    this.u13(UShort__toString_impl_edaoee(tmp$ret$0));
  } else {
    // Inline function 'kotlin.toUShort' call
    var tmp$ret$1 = _UShort___init__impl__jigrne(v);
    this.k13(UShort__toString_impl_edaoee(tmp$ret$1));
  }
};
function ComposerForUnquotedLiterals(writer, forceQuoting) {
  Composer.call(this, writer);
  this.b14_1 = forceQuoting;
}
protoOf(ComposerForUnquotedLiterals).u13 = function (value) {
  if (this.b14_1) {
    protoOf(Composer).u13.call(this, value);
  } else {
    protoOf(Composer).k13.call(this, value);
  }
};
function ComposerWithPrettyPrint(writer, json) {
  Composer.call(this, writer);
  this.e14_1 = json;
  this.f14_1 = 0;
}
protoOf(ComposerWithPrettyPrint).d13 = function () {
  this.c13_1 = true;
  this.f14_1 = this.f14_1 + 1 | 0;
};
protoOf(ComposerWithPrettyPrint).e13 = function () {
  this.f14_1 = this.f14_1 - 1 | 0;
};
protoOf(ComposerWithPrettyPrint).f13 = function () {
  this.c13_1 = false;
  this.k13('\n');
  // Inline function 'kotlin.repeat' call
  var times = this.f14_1;
  var inductionVariable = 0;
  if (inductionVariable < times)
    do {
      var index = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      this.k13(this.e14_1.sz_1.m11_1);
    }
     while (inductionVariable < times);
};
protoOf(ComposerWithPrettyPrint).g13 = function () {
  if (this.c13_1)
    this.c13_1 = false;
  else {
    this.f13();
  }
};
protoOf(ComposerWithPrettyPrint).h13 = function () {
  this.i13(_Char___init__impl__6a9atx(32));
};
function readIfAbsent($this, descriptor, index) {
  $this.h14_1 = (!descriptor.gk(index) && descriptor.fk(index).rj());
  return $this.h14_1;
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
  tmp.g14_1 = new ElementMarker(descriptor, JsonElementMarker$readIfAbsent$ref(this));
  this.h14_1 = false;
}
protoOf(JsonElementMarker).i14 = function (index) {
  this.g14_1.sq(index);
};
protoOf(JsonElementMarker).j14 = function () {
  return this.g14_1.tq();
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
  _this__u8e3s4.k14('Trailing comma before the end of JSON ' + entity, _this__u8e3s4.i10_1 - 1 | 0, "Trailing commas are non-complaint JSON and not allowed by default. Use 'allowTrailingCommas = true' in 'Json {}' builder to support them.");
}
function throwInvalidFloatingPointDecoded(_this__u8e3s4, result) {
  _this__u8e3s4.l14('Unexpected special floating-point value ' + toString(result) + '. By default, ' + 'non-finite floating point values are prohibited because they do not conform JSON specification', VOID, "It is possible to deserialize them using 'JsonBuilder.allowSpecialFloatingPointValues = true'");
}
function JsonEncodingException(message) {
  JsonException.call(this, message);
  captureStack(this, JsonEncodingException);
}
function InvalidKeyKindException(keyDescriptor) {
  return new JsonEncodingException("Value of type '" + keyDescriptor.xj() + "' can't be used in JSON as a key in the map. " + ("It should have either primitive or enum kind, but its kind is '" + keyDescriptor.yj().toString() + "'.\n") + "Use 'allowStructuredMapKeys = true' in 'Json {}' builder to convert such maps to [key1, value1, key2, value2,...] arrays.");
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
  var index = _this__u8e3s4.dk(name);
  if (!(index === -3))
    return index;
  if (!json.sz_1.r11_1)
    return index;
  return getJsonNameIndexSlowPath(_this__u8e3s4, json, name);
}
function getJsonElementName(_this__u8e3s4, json, index) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  var strategy = namingStrategy(_this__u8e3s4, json);
  return strategy == null ? _this__u8e3s4.ck(index) : serializationNamesIndices(_this__u8e3s4, json, strategy)[index];
}
function namingStrategy(_this__u8e3s4, json) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  return equals(_this__u8e3s4.yj(), CLASS_getInstance()) ? json.sz_1.s11_1 : null;
}
function deserializationNamesMap(_this__u8e3s4, descriptor) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  var tmp = get_schemaCache(_this__u8e3s4);
  var tmp_0 = get_JsonDeserializationNamesKey();
  return tmp.n14(descriptor, tmp_0, deserializationNamesMap$lambda(descriptor, _this__u8e3s4));
}
function decodeCaseInsensitive(_this__u8e3s4, descriptor) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  return _this__u8e3s4.sz_1.t11_1 && equals(descriptor.yj(), ENUM_getInstance());
}
function getJsonNameIndexSlowPath(_this__u8e3s4, json, name) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  var tmp0_elvis_lhs = deserializationNamesMap(json, _this__u8e3s4).v1(name);
  return tmp0_elvis_lhs == null ? -3 : tmp0_elvis_lhs;
}
function serializationNamesIndices(_this__u8e3s4, json, strategy) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  var tmp = get_schemaCache(json);
  var tmp_0 = get_JsonSerializationNamesKey();
  return tmp.n14(_this__u8e3s4, tmp_0, serializationNamesIndices$lambda(_this__u8e3s4, strategy));
}
function buildDeserializationNamesMap(_this__u8e3s4, json) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  // Inline function 'kotlin.collections.mutableMapOf' call
  var builder = LinkedHashMap_init_$Create$();
  var useLowercaseEnums = decodeCaseInsensitive(json, _this__u8e3s4);
  var strategyForClasses = namingStrategy(_this__u8e3s4, json);
  var inductionVariable = 0;
  var last = _this__u8e3s4.ak();
  if (inductionVariable < last)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      // Inline function 'kotlin.collections.filterIsInstance' call
      var tmp0 = _this__u8e3s4.ek(i);
      // Inline function 'kotlin.collections.filterIsInstanceTo' call
      var destination = ArrayList_init_$Create$();
      var _iterator__ex2g4s = tmp0.k();
      while (_iterator__ex2g4s.l()) {
        var element = _iterator__ex2g4s.m();
        if (element instanceof JsonNames) {
          destination.h(element);
        }
      }
      var tmp0_safe_receiver = singleOrNull(destination);
      var tmp1_safe_receiver = tmp0_safe_receiver == null ? null : tmp0_safe_receiver.o14_1;
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
        tmp_0 = _this__u8e3s4.ck(i).toLowerCase();
      } else if (!(strategyForClasses == null)) {
        tmp_0 = strategyForClasses.p14(_this__u8e3s4, i, _this__u8e3s4.ck(i));
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
  if (builder.p()) {
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
    var tmp_0 = $this_serializationNamesIndices.ak();
    // Inline function 'kotlin.arrayOfNulls' call
    var tmp_1 = Array(tmp_0);
    while (tmp < tmp_0) {
      var tmp_2 = tmp;
      var baseName = $this_serializationNamesIndices.ck(tmp_2);
      tmp_1[tmp_2] = $strategy.p14($this_serializationNamesIndices, tmp_2, baseName);
      tmp = tmp + 1 | 0;
    }
    return tmp_1;
  };
}
function buildDeserializationNamesMap$putOrThrow(_this__u8e3s4, $this_buildDeserializationNamesMap, name, index) {
  var entity = equals($this_buildDeserializationNamesMap.yj(), ENUM_getInstance()) ? 'enum value' : 'property';
  // Inline function 'kotlin.collections.contains' call
  // Inline function 'kotlin.collections.containsKey' call
  if ((isInterface(_this__u8e3s4, KtMap) ? _this__u8e3s4 : THROW_CCE()).t1(name)) {
    throw new JsonException("The suggested name '" + name + "' for " + entity + ' ' + $this_buildDeserializationNamesMap.ck(index) + ' is already one of the names for ' + entity + ' ' + ($this_buildDeserializationNamesMap.ck(getValue(_this__u8e3s4, name)) + ' in ' + toString($this_buildDeserializationNamesMap)));
  }
  // Inline function 'kotlin.collections.set' call
  _this__u8e3s4.z1(name, index);
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
  var newSize = imul($this.s14_1, 2);
  $this.q14_1 = copyOf($this.q14_1, newSize);
  $this.r14_1 = copyOf_0($this.r14_1, newSize);
}
function JsonPath() {
  var tmp = this;
  // Inline function 'kotlin.arrayOfNulls' call
  tmp.q14_1 = Array(8);
  var tmp_0 = this;
  var tmp_1 = 0;
  var tmp_2 = new Int32Array(8);
  while (tmp_1 < 8) {
    tmp_2[tmp_1] = -1;
    tmp_1 = tmp_1 + 1 | 0;
  }
  tmp_0.r14_1 = tmp_2;
  this.s14_1 = -1;
}
protoOf(JsonPath).t14 = function (sd) {
  this.s14_1 = this.s14_1 + 1 | 0;
  var depth = this.s14_1;
  if (depth === this.q14_1.length) {
    resize(this);
  }
  this.q14_1[depth] = sd;
};
protoOf(JsonPath).u14 = function (index) {
  this.r14_1[this.s14_1] = index;
};
protoOf(JsonPath).v14 = function (key) {
  var tmp;
  if (!(this.r14_1[this.s14_1] === -2)) {
    this.s14_1 = this.s14_1 + 1 | 0;
    tmp = this.s14_1 === this.q14_1.length;
  } else {
    tmp = false;
  }
  if (tmp) {
    resize(this);
  }
  this.q14_1[this.s14_1] = key;
  this.r14_1[this.s14_1] = -2;
};
protoOf(JsonPath).w14 = function () {
  if (this.r14_1[this.s14_1] === -2) {
    this.q14_1[this.s14_1] = Tombstone_instance;
  }
};
protoOf(JsonPath).x14 = function () {
  var depth = this.s14_1;
  if (this.r14_1[depth] === -2) {
    this.r14_1[depth] = -1;
    this.s14_1 = this.s14_1 - 1 | 0;
  }
  if (!(this.s14_1 === -1)) {
    this.s14_1 = this.s14_1 - 1 | 0;
  }
};
protoOf(JsonPath).y14 = function () {
  // Inline function 'kotlin.text.buildString' call
  // Inline function 'kotlin.apply' call
  var this_0 = StringBuilder_init_$Create$();
  this_0.c7('$');
  // Inline function 'kotlin.repeat' call
  var times = this.s14_1 + 1 | 0;
  var inductionVariable = 0;
  if (inductionVariable < times)
    do {
      var index = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      var element = this.q14_1[index];
      if (!(element == null) ? isInterface(element, SerialDescriptor) : false) {
        if (equals(element.yj(), LIST_getInstance())) {
          if (!(this.r14_1[index] === -1)) {
            this_0.c7('[');
            this_0.ea(this.r14_1[index]);
            this_0.c7(']');
          }
        } else {
          var idx = this.r14_1[index];
          if (idx >= 0) {
            this_0.c7('.');
            this_0.c7(element.ck(idx));
          }
        }
      } else {
        if (!(element === Tombstone_instance)) {
          this_0.c7('[');
          this_0.c7("'");
          this_0.b7(element);
          this_0.c7("'");
          this_0.c7(']');
        }
      }
    }
     while (inductionVariable < times);
  return this_0.toString();
};
protoOf(JsonPath).toString = function () {
  return this.y14();
};
function encodeByWriter(json, writer, serializer, value) {
  var tmp = WriteMode_OBJ_getInstance();
  // Inline function 'kotlin.arrayOfNulls' call
  var size = get_entries().n();
  var tmp$ret$0 = Array(size);
  var encoder = StreamingJsonEncoder_init_$Create$(writer, json, tmp, tmp$ret$0);
  encoder.nn(serializer, value);
}
function readObject($this) {
  // Inline function 'kotlinx.serialization.json.internal.JsonTreeReader.readObjectImpl' call
  var lastToken = $this.i15_1.m15(6);
  if ($this.i15_1.n15() === 4) {
    $this.i15_1.l14('Unexpected leading comma');
  }
  // Inline function 'kotlin.collections.linkedMapOf' call
  var result = LinkedHashMap_init_$Create$();
  $l$loop: while ($this.i15_1.o15()) {
    var key = $this.j15_1 ? $this.i15_1.q15() : $this.i15_1.p15();
    $this.i15_1.m15(5);
    var element = $this.r15();
    // Inline function 'kotlin.collections.set' call
    result.z1(key, element);
    lastToken = $this.i15_1.s15();
    var tmp0_subject = lastToken;
    if (tmp0_subject !== 4)
      if (tmp0_subject === 7)
        break $l$loop;
      else {
        $this.i15_1.l14('Expected end of the object or comma');
      }
  }
  if (lastToken === 6) {
    $this.i15_1.m15(7);
  } else if (lastToken === 4) {
    if (!$this.k15_1) {
      invalidTrailingComma($this.i15_1);
    }
    $this.i15_1.m15(7);
  }
  return new JsonObject(result);
}
function readObject_0($this, $receiver, $completion) {
  var tmp = new $readObjectCOROUTINE$($this, $receiver, $completion);
  tmp.q7_1 = Unit_instance;
  tmp.r7_1 = null;
  return tmp.w7();
}
function readArray($this) {
  var lastToken = $this.i15_1.s15();
  if ($this.i15_1.n15() === 4) {
    $this.i15_1.l14('Unexpected leading comma');
  }
  // Inline function 'kotlin.collections.arrayListOf' call
  var result = ArrayList_init_$Create$();
  while ($this.i15_1.o15()) {
    var element = $this.r15();
    result.h(element);
    lastToken = $this.i15_1.s15();
    if (!(lastToken === 4)) {
      var tmp0 = $this.i15_1;
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.require' call
      var condition = lastToken === 9;
      var position = tmp0.i10_1;
      if (!condition) {
        var tmp$ret$2 = 'Expected end of the array or comma';
        tmp0.l14(tmp$ret$2, position);
      }
    }
  }
  if (lastToken === 8) {
    $this.i15_1.m15(9);
  } else if (lastToken === 4) {
    if (!$this.k15_1) {
      invalidTrailingComma($this.i15_1, 'array');
    }
    $this.i15_1.m15(9);
  }
  return new JsonArray(result);
}
function readValue($this, isString) {
  var tmp;
  if ($this.j15_1 || !isString) {
    tmp = $this.i15_1.q15();
  } else {
    tmp = $this.i15_1.p15();
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
  this.p16_1 = this$0;
  CoroutineImpl.call(this, resultContinuation);
}
protoOf(JsonTreeReader$readDeepRecursive$slambda).t16 = function ($this$DeepRecursiveFunction, it, $completion) {
  var tmp = this.u16($this$DeepRecursiveFunction, it, $completion);
  tmp.q7_1 = Unit_instance;
  tmp.r7_1 = null;
  return tmp.w7();
};
protoOf(JsonTreeReader$readDeepRecursive$slambda).c8 = function (p1, p2, $completion) {
  var tmp = p1 instanceof DeepRecursiveScope ? p1 : THROW_CCE();
  return this.t16(tmp, p2 instanceof Unit ? p2 : THROW_CCE(), $completion);
};
protoOf(JsonTreeReader$readDeepRecursive$slambda).w7 = function () {
  var suspendResult = this.q7_1;
  $sm: do
    try {
      var tmp = this.o7_1;
      switch (tmp) {
        case 0:
          this.p7_1 = 3;
          var tmp0_subject = this.p16_1.i15_1.n15();
          if (tmp0_subject === 1) {
            this.s16_1 = readValue(this.p16_1, true);
            this.o7_1 = 2;
            continue $sm;
          } else {
            if (tmp0_subject === 0) {
              this.s16_1 = readValue(this.p16_1, false);
              this.o7_1 = 2;
              continue $sm;
            } else {
              if (tmp0_subject === 6) {
                this.o7_1 = 1;
                suspendResult = readObject_0(this.p16_1, this.q16_1, this);
                if (suspendResult === get_COROUTINE_SUSPENDED()) {
                  return suspendResult;
                }
                continue $sm;
              } else {
                if (tmp0_subject === 8) {
                  this.s16_1 = readArray(this.p16_1);
                  this.o7_1 = 2;
                  continue $sm;
                } else {
                  var tmp_0 = this;
                  this.p16_1.i15_1.l14("Can't begin reading element, unexpected token");
                }
              }
            }
          }

          break;
        case 1:
          this.s16_1 = suspendResult;
          this.o7_1 = 2;
          continue $sm;
        case 2:
          return this.s16_1;
        case 3:
          throw this.r7_1;
      }
    } catch ($p) {
      var e = $p;
      if (this.p7_1 === 3) {
        throw e;
      } else {
        this.o7_1 = this.p7_1;
        this.r7_1 = e;
      }
    }
   while (true);
};
protoOf(JsonTreeReader$readDeepRecursive$slambda).u16 = function ($this$DeepRecursiveFunction, it, completion) {
  var i = new JsonTreeReader$readDeepRecursive$slambda(this.p16_1, completion);
  i.q16_1 = $this$DeepRecursiveFunction;
  i.r16_1 = it;
  return i;
};
function JsonTreeReader$readDeepRecursive$slambda_0(this$0, resultContinuation) {
  var i = new JsonTreeReader$readDeepRecursive$slambda(this$0, resultContinuation);
  var l = function ($this$DeepRecursiveFunction, it, $completion) {
    return i.t16($this$DeepRecursiveFunction, it, $completion);
  };
  l.$arity = 2;
  return l;
}
function $readObjectCOROUTINE$(_this__u8e3s4, _this__u8e3s4_0, resultContinuation) {
  CoroutineImpl.call(this, resultContinuation);
  this.b16_1 = _this__u8e3s4;
  this.c16_1 = _this__u8e3s4_0;
}
protoOf($readObjectCOROUTINE$).w7 = function () {
  var suspendResult = this.q7_1;
  $sm: do
    try {
      var tmp = this.o7_1;
      switch (tmp) {
        case 0:
          this.p7_1 = 5;
          this.e16_1 = this.b16_1;
          this.f16_1 = this.e16_1.i15_1.m15(6);
          if (this.e16_1.i15_1.n15() === 4) {
            this.e16_1.i15_1.l14('Unexpected leading comma');
          }

          var tmp_0 = this;
          tmp_0.d16_1 = LinkedHashMap_init_$Create$();
          this.o7_1 = 1;
          continue $sm;
        case 1:
          if (!this.e16_1.i15_1.o15()) {
            this.o7_1 = 4;
            continue $sm;
          }

          this.g16_1 = this.e16_1.j15_1 ? this.e16_1.i15_1.q15() : this.e16_1.i15_1.p15();
          this.e16_1.i15_1.m15(5);
          this.o7_1 = 2;
          suspendResult = this.c16_1.vg(Unit_instance, this);
          if (suspendResult === get_COROUTINE_SUSPENDED()) {
            return suspendResult;
          }

          continue $sm;
        case 2:
          var element = suspendResult;
          var tmp0 = this.d16_1;
          var key = this.g16_1;
          tmp0.z1(key, element);
          this.f16_1 = this.e16_1.i15_1.s15();
          var tmp0_subject = this.f16_1;
          if (tmp0_subject === 4) {
            this.o7_1 = 3;
            continue $sm;
          } else {
            if (tmp0_subject === 7) {
              this.o7_1 = 4;
              continue $sm;
            } else {
              this.e16_1.i15_1.l14('Expected end of the object or comma');
            }
          }

          break;
        case 3:
          this.o7_1 = 1;
          continue $sm;
        case 4:
          if (this.f16_1 === 6) {
            this.e16_1.i15_1.m15(7);
          } else if (this.f16_1 === 4) {
            if (!this.e16_1.k15_1) {
              invalidTrailingComma(this.e16_1.i15_1);
            }
            this.e16_1.i15_1.m15(7);
          }

          return new JsonObject(this.d16_1);
        case 5:
          throw this.r7_1;
      }
    } catch ($p) {
      var e = $p;
      if (this.p7_1 === 5) {
        throw e;
      } else {
        this.o7_1 = this.p7_1;
        this.r7_1 = e;
      }
    }
   while (true);
};
function JsonTreeReader(configuration, lexer) {
  this.i15_1 = lexer;
  this.j15_1 = configuration.i11_1;
  this.k15_1 = configuration.u11_1;
  this.l15_1 = 0;
}
protoOf(JsonTreeReader).r15 = function () {
  var token = this.i15_1.n15();
  var tmp;
  if (token === 1) {
    tmp = readValue(this, true);
  } else if (token === 0) {
    tmp = readValue(this, false);
  } else if (token === 6) {
    var tmp_0;
    this.l15_1 = this.l15_1 + 1 | 0;
    if (this.l15_1 === 200) {
      tmp_0 = readDeepRecursive(this);
    } else {
      tmp_0 = readObject(this);
    }
    var result = tmp_0;
    this.l15_1 = this.l15_1 - 1 | 0;
    tmp = result;
  } else if (token === 8) {
    tmp = readArray(this);
  } else {
    this.i15_1.l14('Cannot read Json element because of unexpected ' + tokenDescription(token));
  }
  return tmp;
};
function classDiscriminator(_this__u8e3s4, json) {
  var _iterator__ex2g4s = _this__u8e3s4.bk().k();
  while (_iterator__ex2g4s.l()) {
    var annotation = _iterator__ex2g4s.m();
    if (annotation instanceof JsonClassDiscriminator)
      return annotation.v16_1;
  }
  return json.sz_1.p11_1;
}
function validateIfSealed(serializer, actualSerializer, classDiscriminator) {
  if (!(serializer instanceof SealedClassSerializer))
    return Unit_instance;
  if (jsonCachedSerialNames(actualSerializer.jj()).b1(classDiscriminator)) {
    var baseName = serializer.jj().xj();
    var actualName = actualSerializer.jj().xj();
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
  var kind = descriptor.yj();
  var tmp;
  if (kind instanceof PolymorphicKind) {
    tmp = true;
  } else {
    tmp = equals(kind, CONTEXTUAL_getInstance());
  }
  if (tmp) {
    throw IllegalArgumentException_init_$Create$('Serializer for ' + actualClass.z8() + " can't be registered as a subclass for polymorphic serialization " + ('because its kind ' + kind.toString() + ' is not concrete. To work with multiple hierarchies, register it as a base class.'));
  }
  if ($this.w16_1)
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
    throw IllegalArgumentException_init_$Create$('Serializer for ' + actualClass.z8() + ' of kind ' + kind.toString() + ' cannot be serialized polymorphically with class discriminator.');
  }
}
function checkDiscriminatorCollisions($this, descriptor, actualClass) {
  var inductionVariable = 0;
  var last = descriptor.ak();
  if (inductionVariable < last)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      var name = descriptor.ck(i);
      if (name === $this.x16_1) {
        throw IllegalArgumentException_init_$Create$('Polymorphic serializer for ' + toString(actualClass) + " has property '" + name + "' that conflicts " + 'with JSON class discriminator. You can either change class discriminator in JsonConfiguration, ' + 'rename property with @SerialName annotation ' + 'or fall back to array polymorphism');
      }
    }
     while (inductionVariable < last);
}
function PolymorphismValidator(useArrayPolymorphism, discriminator) {
  this.w16_1 = useArrayPolymorphism;
  this.x16_1 = discriminator;
}
protoOf(PolymorphismValidator).mz = function (kClass, provider) {
};
protoOf(PolymorphismValidator).pz = function (baseClass, actualClass, actualSerializer) {
  var descriptor = actualSerializer.jj();
  checkKind_0(this, descriptor, actualClass);
  if (!this.w16_1) {
    checkDiscriminatorCollisions(this, descriptor, actualClass);
  }
};
protoOf(PolymorphismValidator).qz = function (baseClass, defaultSerializerProvider) {
};
protoOf(PolymorphismValidator).rz = function (baseClass, defaultDeserializerProvider) {
};
function Key() {
}
function DescriptorSchemaCache() {
  this.m14_1 = createMapForCache(16);
}
protoOf(DescriptorSchemaCache).y16 = function (descriptor, key, value) {
  // Inline function 'kotlin.collections.getOrPut' call
  var this_0 = this.m14_1;
  var value_0 = this_0.v1(descriptor);
  var tmp;
  if (value_0 == null) {
    var answer = createMapForCache(2);
    this_0.z1(descriptor, answer);
    tmp = answer;
  } else {
    tmp = value_0;
  }
  var tmp0 = tmp;
  var tmp2 = key instanceof Key ? key : THROW_CCE();
  // Inline function 'kotlin.collections.set' call
  var value_1 = !(value == null) ? value : THROW_CCE();
  tmp0.z1(tmp2, value_1);
};
protoOf(DescriptorSchemaCache).n14 = function (descriptor, key, defaultValue) {
  var tmp0_safe_receiver = this.z16(descriptor, key);
  if (tmp0_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    return tmp0_safe_receiver;
  }
  var value = defaultValue();
  this.y16(descriptor, key, value);
  return value;
};
protoOf(DescriptorSchemaCache).z16 = function (descriptor, key) {
  var tmp0_safe_receiver = this.m14_1.v1(descriptor);
  var tmp;
  if (tmp0_safe_receiver == null) {
    tmp = null;
  } else {
    tmp = tmp0_safe_receiver.v1(key instanceof Key ? key : THROW_CCE());
  }
  var tmp_0 = tmp;
  return !(tmp_0 == null) ? tmp_0 : null;
};
function DiscriminatorHolder(discriminatorToSkip) {
  this.a17_1 = discriminatorToSkip;
}
function trySkip($this, $receiver, unknownKey) {
  if ($receiver == null)
    return false;
  if ($receiver.a17_1 === unknownKey) {
    $receiver.a17_1 = null;
    return true;
  }
  return false;
}
function skipLeftoverElements($this, descriptor) {
  while (!($this.nm(descriptor) === -1)) {
  }
}
function checkLeadingComma($this) {
  if ($this.c10_1.n15() === 4) {
    $this.c10_1.l14('Unexpected leading comma');
  }
}
function decodeMapIndex($this) {
  var hasComma = false;
  var decodingKey = !(($this.e10_1 % 2 | 0) === 0);
  if (decodingKey) {
    if (!($this.e10_1 === -1)) {
      hasComma = $this.c10_1.c17();
    }
  } else {
    $this.c10_1.b17(_Char___init__impl__6a9atx(58));
  }
  var tmp;
  if ($this.c10_1.o15()) {
    if (decodingKey) {
      if ($this.e10_1 === -1) {
        var tmp0 = $this.c10_1;
        // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.require' call
        var condition = !hasComma;
        var position = tmp0.i10_1;
        if (!condition) {
          var tmp$ret$1 = 'Unexpected leading comma';
          tmp0.l14(tmp$ret$1, position);
        }
      } else {
        var tmp0_0 = $this.c10_1;
        // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.require' call
        var condition_0 = hasComma;
        var position_0 = tmp0_0.i10_1;
        if (!condition_0) {
          var tmp$ret$3 = 'Expected comma after the key-value pair';
          tmp0_0.l14(tmp$ret$3, position_0);
        }
      }
    }
    $this.e10_1 = $this.e10_1 + 1 | 0;
    tmp = $this.e10_1;
  } else {
    if (hasComma && !$this.a10_1.sz_1.u11_1) {
      invalidTrailingComma($this.c10_1);
    }
    tmp = -1;
  }
  return tmp;
}
function coerceInputValue($this, descriptor, index) {
  var tmp0 = $this.a10_1;
  var tmp$ret$0;
  $l$block_2: {
    // Inline function 'kotlinx.serialization.json.internal.tryCoerceValue' call
    var isOptional = descriptor.gk(index);
    var elementDescriptor = descriptor.fk(index);
    var tmp;
    if (isOptional && !elementDescriptor.rj()) {
      tmp = $this.c10_1.d17(true);
    } else {
      tmp = false;
    }
    if (tmp) {
      tmp$ret$0 = true;
      break $l$block_2;
    }
    if (equals(elementDescriptor.yj(), ENUM_getInstance())) {
      var tmp_0;
      if (elementDescriptor.rj()) {
        tmp_0 = $this.c10_1.d17(false);
      } else {
        tmp_0 = false;
      }
      if (tmp_0) {
        tmp$ret$0 = false;
        break $l$block_2;
      }
      var tmp0_elvis_lhs = $this.c10_1.e17($this.g10_1.i11_1);
      var tmp_1;
      if (tmp0_elvis_lhs == null) {
        tmp$ret$0 = false;
        break $l$block_2;
      } else {
        tmp_1 = tmp0_elvis_lhs;
      }
      var enumValue = tmp_1;
      var enumIndex = getJsonNameIndex(elementDescriptor, tmp0, enumValue);
      var coerceToNull = !tmp0.sz_1.l11_1 && elementDescriptor.rj();
      if (enumIndex === -3 && (isOptional || coerceToNull)) {
        $this.c10_1.p15();
        tmp$ret$0 = true;
        break $l$block_2;
      }
    }
    tmp$ret$0 = false;
  }
  return tmp$ret$0;
}
function decodeObjectIndex($this, descriptor) {
  var hasComma = $this.c10_1.c17();
  while ($this.c10_1.o15()) {
    hasComma = false;
    var key = decodeStringKey($this);
    $this.c10_1.b17(_Char___init__impl__6a9atx(58));
    var index = getJsonNameIndex(descriptor, $this.a10_1, key);
    var tmp;
    if (!(index === -3)) {
      var tmp_0;
      if ($this.g10_1.n11_1 && coerceInputValue($this, descriptor, index)) {
        hasComma = $this.c10_1.c17();
        tmp_0 = false;
      } else {
        var tmp0_safe_receiver = $this.h10_1;
        if (tmp0_safe_receiver == null)
          null;
        else {
          tmp0_safe_receiver.i14(index);
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
  if (hasComma && !$this.a10_1.sz_1.u11_1) {
    invalidTrailingComma($this.c10_1);
  }
  var tmp1_safe_receiver = $this.h10_1;
  var tmp2_elvis_lhs = tmp1_safe_receiver == null ? null : tmp1_safe_receiver.j14();
  return tmp2_elvis_lhs == null ? -1 : tmp2_elvis_lhs;
}
function handleUnknown($this, key) {
  if ($this.g10_1.h11_1 || trySkip($this, $this.f10_1, key)) {
    $this.c10_1.g17($this.g10_1.i11_1);
  } else {
    $this.c10_1.f17(key);
  }
  return $this.c10_1.c17();
}
function decodeListIndex($this) {
  var hasComma = $this.c10_1.c17();
  var tmp;
  if ($this.c10_1.o15()) {
    if (!($this.e10_1 === -1) && !hasComma) {
      $this.c10_1.l14('Expected end of the array or comma');
    }
    $this.e10_1 = $this.e10_1 + 1 | 0;
    tmp = $this.e10_1;
  } else {
    if (hasComma && !$this.a10_1.sz_1.u11_1) {
      invalidTrailingComma($this.c10_1, 'array');
    }
    tmp = -1;
  }
  return tmp;
}
function decodeStringKey($this) {
  var tmp;
  if ($this.g10_1.i11_1) {
    tmp = $this.c10_1.i17();
  } else {
    tmp = $this.c10_1.h17();
  }
  return tmp;
}
function StreamingJsonDecoder(json, mode, lexer, descriptor, discriminatorHolder) {
  AbstractDecoder.call(this);
  this.a10_1 = json;
  this.b10_1 = mode;
  this.c10_1 = lexer;
  this.d10_1 = this.a10_1.lm();
  this.e10_1 = -1;
  this.f10_1 = discriminatorHolder;
  this.g10_1 = this.a10_1.sz_1;
  this.h10_1 = this.g10_1.l11_1 ? null : new JsonElementMarker(descriptor);
}
protoOf(StreamingJsonDecoder).x11 = function () {
  return this.a10_1;
};
protoOf(StreamingJsonDecoder).lm = function () {
  return this.d10_1;
};
protoOf(StreamingJsonDecoder).y11 = function () {
  return (new JsonTreeReader(this.a10_1.sz_1, this.c10_1)).r15();
};
protoOf(StreamingJsonDecoder).vl = function (deserializer) {
  try {
    var tmp;
    if (!(deserializer instanceof AbstractPolymorphicSerializer)) {
      tmp = true;
    } else {
      tmp = this.a10_1.sz_1.o11_1;
    }
    if (tmp) {
      return deserializer.lj(this);
    }
    var discriminator = classDiscriminator(deserializer.jj(), this.a10_1);
    var tmp0_elvis_lhs = this.c10_1.j17(discriminator, this.g10_1.i11_1);
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
          tmp_1 = this.x11().sz_1.o11_1;
        }
        if (tmp_1) {
          tmp$ret$0 = tmp2.lj(this);
          break $l$block;
        }
        var discriminator_0 = classDiscriminator(tmp2.jj(), this.x11());
        var tmp0 = this.y11();
        // Inline function 'kotlinx.serialization.json.internal.cast' call
        var serialName = tmp2.jj().xj();
        if (!(tmp0 instanceof JsonObject)) {
          var tmp_2 = getKClass(JsonObject).z8();
          var tmp_3 = getKClassFromExpression(tmp0).z8();
          var tmp$ret$2 = this.c10_1.j10_1.y14();
          throw JsonDecodingException_0(-1, 'Expected ' + tmp_2 + ', but had ' + tmp_3 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$2, toString(tmp0));
        }
        var jsonTree = tmp0;
        var tmp0_safe_receiver = jsonTree.mb(discriminator_0);
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
        tmp$ret$0 = readPolymorphicJson(this.x11(), discriminator_0, jsonTree, actualSerializer);
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
        this.c10_1.l14(message, VOID, hint);
      } else {
        throw $p;
      }
      tmp_7 = tmp_8;
    }
    var tmp_9 = tmp_7;
    var actualSerializer_0 = isInterface(tmp_9, DeserializationStrategy) ? tmp_9 : THROW_CCE();
    this.f10_1 = new DiscriminatorHolder(discriminator);
    return actualSerializer_0.lj(this);
  } catch ($p) {
    if ($p instanceof MissingFieldException) {
      var e = $p;
      if (contains_0(ensureNotNull(e.message), 'at path'))
        throw e;
      throw new MissingFieldException(e.qj_1, plus(e.message, ' at path: ') + this.c10_1.j10_1.y14(), e);
    } else {
      throw $p;
    }
  }
};
protoOf(StreamingJsonDecoder).wl = function (descriptor) {
  var newMode = switchMode(this.a10_1, descriptor);
  this.c10_1.j10_1.t14(descriptor);
  this.c10_1.b17(newMode.m17_1);
  checkLeadingComma(this);
  var tmp;
  switch (newMode.c2_1) {
    case 1:
    case 2:
    case 3:
      tmp = new StreamingJsonDecoder(this.a10_1, newMode, this.c10_1, descriptor, this.f10_1);
      break;
    default:
      var tmp_0;
      if (this.b10_1.equals(newMode) && this.a10_1.sz_1.l11_1) {
        tmp_0 = this;
      } else {
        tmp_0 = new StreamingJsonDecoder(this.a10_1, newMode, this.c10_1, descriptor, this.f10_1);
      }

      tmp = tmp_0;
      break;
  }
  return tmp;
};
protoOf(StreamingJsonDecoder).xl = function (descriptor) {
  if (this.a10_1.sz_1.h11_1 && descriptor.ak() === 0) {
    skipLeftoverElements(this, descriptor);
  }
  if (this.c10_1.c17() && !this.a10_1.sz_1.u11_1) {
    invalidTrailingComma(this.c10_1, '');
  }
  this.c10_1.b17(this.b10_1.n17_1);
  this.c10_1.j10_1.x14();
};
protoOf(StreamingJsonDecoder).il = function () {
  var tmp;
  var tmp0_safe_receiver = this.h10_1;
  var tmp1_elvis_lhs = tmp0_safe_receiver == null ? null : tmp0_safe_receiver.h14_1;
  if (!(tmp1_elvis_lhs == null ? false : tmp1_elvis_lhs)) {
    tmp = !this.c10_1.o17();
  } else {
    tmp = false;
  }
  return tmp;
};
protoOf(StreamingJsonDecoder).jl = function () {
  return null;
};
protoOf(StreamingJsonDecoder).im = function (descriptor, index, deserializer, previousValue) {
  var isMapKey = this.b10_1.equals(WriteMode_MAP_getInstance()) && (index & 1) === 0;
  if (isMapKey) {
    this.c10_1.j10_1.w14();
  }
  var value = protoOf(AbstractDecoder).im.call(this, descriptor, index, deserializer, previousValue);
  if (isMapKey) {
    this.c10_1.j10_1.v14(value);
  }
  return value;
};
protoOf(StreamingJsonDecoder).nm = function (descriptor) {
  var index;
  switch (this.b10_1.c2_1) {
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
  if (!this.b10_1.equals(WriteMode_MAP_getInstance())) {
    this.c10_1.j10_1.u14(index);
  }
  return index;
};
protoOf(StreamingJsonDecoder).kl = function () {
  return this.c10_1.p17();
};
protoOf(StreamingJsonDecoder).ll = function () {
  var value = this.c10_1.j12();
  if (!equalsLong(value, fromInt(convertToByte(value)))) {
    this.c10_1.l14("Failed to parse byte for input '" + value.toString() + "'");
  }
  return convertToByte(value);
};
protoOf(StreamingJsonDecoder).ml = function () {
  var value = this.c10_1.j12();
  if (!equalsLong(value, fromInt(convertToShort(value)))) {
    this.c10_1.l14("Failed to parse short for input '" + value.toString() + "'");
  }
  return convertToShort(value);
};
protoOf(StreamingJsonDecoder).nl = function () {
  var value = this.c10_1.j12();
  if (!equalsLong(value, fromInt(convertToInt(value)))) {
    this.c10_1.l14("Failed to parse int for input '" + value.toString() + "'");
  }
  return convertToInt(value);
};
protoOf(StreamingJsonDecoder).ol = function () {
  return this.c10_1.j12();
};
protoOf(StreamingJsonDecoder).pl = function () {
  var tmp0 = this.c10_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.q15();
    try {
      // Inline function 'kotlin.text.toFloat' call
      // Inline function 'kotlin.js.unsafeCast' call
      // Inline function 'kotlin.js.asDynamic' call
      tmp$ret$0 = toDouble(input);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.l14("Failed to parse type '" + 'float' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  var result = tmp$ret$0;
  var specialFp = this.a10_1.sz_1.q11_1;
  if (specialFp || isFinite(result))
    return result;
  throwInvalidFloatingPointDecoded(this.c10_1, result);
};
protoOf(StreamingJsonDecoder).ql = function () {
  var tmp0 = this.c10_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.q15();
    try {
      tmp$ret$0 = toDouble(input);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.l14("Failed to parse type '" + 'double' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  var result = tmp$ret$0;
  var specialFp = this.a10_1.sz_1.q11_1;
  if (specialFp || isFinite_0(result))
    return result;
  throwInvalidFloatingPointDecoded(this.c10_1, result);
};
protoOf(StreamingJsonDecoder).rl = function () {
  var string = this.c10_1.q15();
  if (!(string.length === 1)) {
    this.c10_1.l14("Expected single char, but got '" + string + "'");
  }
  return charCodeAt(string, 0);
};
protoOf(StreamingJsonDecoder).sl = function () {
  var tmp;
  if (this.g10_1.i11_1) {
    tmp = this.c10_1.i17();
  } else {
    tmp = this.c10_1.p15();
  }
  return tmp;
};
protoOf(StreamingJsonDecoder).tl = function (descriptor) {
  return get_isUnsignedNumber(descriptor) ? new JsonDecoderForUnsignedTypes(this.c10_1, this.a10_1) : protoOf(AbstractDecoder).tl.call(this, descriptor);
};
function JsonDecoderForUnsignedTypes(lexer, json) {
  AbstractDecoder.call(this);
  this.q17_1 = lexer;
  this.r17_1 = json.lm();
}
protoOf(JsonDecoderForUnsignedTypes).lm = function () {
  return this.r17_1;
};
protoOf(JsonDecoderForUnsignedTypes).nm = function (descriptor) {
  // Inline function 'kotlin.error' call
  var message = 'unsupported';
  throw IllegalStateException_init_$Create$(toString(message));
};
protoOf(JsonDecoderForUnsignedTypes).nl = function () {
  var tmp0 = this.q17_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.q15();
    try {
      // Inline function 'kotlin.UInt.toInt' call
      var this_0 = toUInt(input);
      tmp$ret$0 = _UInt___get_data__impl__f0vqqw(this_0);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.l14("Failed to parse type '" + 'UInt' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$0;
};
protoOf(JsonDecoderForUnsignedTypes).ol = function () {
  var tmp0 = this.q17_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.q15();
    try {
      // Inline function 'kotlin.ULong.toLong' call
      var this_0 = toULong(input);
      tmp$ret$0 = _ULong___get_data__impl__fggpzb(this_0);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.l14("Failed to parse type '" + 'ULong' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$0;
};
protoOf(JsonDecoderForUnsignedTypes).ll = function () {
  var tmp0 = this.q17_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.q15();
    try {
      // Inline function 'kotlin.UByte.toByte' call
      var this_0 = toUByte(input);
      tmp$ret$0 = _UByte___get_data__impl__jof9qr(this_0);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.l14("Failed to parse type '" + 'UByte' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$0;
};
protoOf(JsonDecoderForUnsignedTypes).ml = function () {
  var tmp0 = this.q17_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.q15();
    try {
      // Inline function 'kotlin.UShort.toShort' call
      var this_0 = toUShort(input);
      tmp$ret$0 = _UShort___get_data__impl__g0245(this_0);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.l14("Failed to parse type '" + 'UShort' + "' for input '" + input + "'");
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
  $this.z14_1.f13();
  $this.an(discriminator);
  $this.z14_1.i13(_Char___init__impl__6a9atx(58));
  $this.z14_1.h13();
  $this.an(serialName);
}
function StreamingJsonEncoder(composer, json, mode, modeReuseCache) {
  AbstractEncoder.call(this);
  this.z14_1 = composer;
  this.a15_1 = json;
  this.b15_1 = mode;
  this.c15_1 = modeReuseCache;
  this.d15_1 = this.a15_1.lm();
  this.e15_1 = this.a15_1.sz_1;
  this.f15_1 = false;
  this.g15_1 = null;
  this.h15_1 = null;
  var i = this.b15_1.c2_1;
  if (!(this.c15_1 == null)) {
    if (!(this.c15_1[i] === null) || !(this.c15_1[i] === this)) {
      this.c15_1[i] = this;
    }
  }
}
protoOf(StreamingJsonEncoder).x11 = function () {
  return this.a15_1;
};
protoOf(StreamingJsonEncoder).lm = function () {
  return this.d15_1;
};
protoOf(StreamingJsonEncoder).sn = function (descriptor, index) {
  return this.e15_1.g11_1;
};
protoOf(StreamingJsonEncoder).nn = function (serializer, value) {
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.encodePolymorphically' call
    if (this.x11().sz_1.o11_1) {
      serializer.kj(this, value);
      break $l$block;
    }
    var isPolymorphicSerializer = serializer instanceof AbstractPolymorphicSerializer;
    var tmp;
    if (isPolymorphicSerializer) {
      tmp = !this.x11().sz_1.w11_1.equals(ClassDiscriminatorMode_NONE_getInstance());
    } else {
      var tmp_0;
      switch (this.x11().sz_1.w11_1.c2_1) {
        case 0:
        case 2:
          tmp_0 = false;
          break;
        case 1:
          // Inline function 'kotlin.let' call

          var it = serializer.jj().yj();
          tmp_0 = equals(it, CLASS_getInstance()) || equals(it, OBJECT_getInstance());
          break;
        default:
          noWhenBranchMatchedException();
          break;
      }
      tmp = tmp_0;
    }
    var needDiscriminator = tmp;
    var baseClassDiscriminator = needDiscriminator ? classDiscriminator(serializer.jj(), this.x11()) : null;
    var tmp_1;
    if (isPolymorphicSerializer) {
      var casted = serializer instanceof AbstractPolymorphicSerializer ? serializer : THROW_CCE();
      $l$block_0: {
        // Inline function 'kotlin.requireNotNull' call
        if (value == null) {
          var message = 'Value for serializer ' + toString(serializer.jj()) + ' should always be non-null. Please report issue to the kotlinx.serialization tracker.';
          throw IllegalArgumentException_init_$Create$(toString(message));
        } else {
          break $l$block_0;
        }
      }
      var actual = findPolymorphicSerializer_0(casted, this, value);
      if (!(baseClassDiscriminator == null)) {
        access$validateIfSealed$tPolymorphicKt(serializer, actual, baseClassDiscriminator);
      }
      checkKind(actual.jj().yj());
      tmp_1 = isInterface(actual, SerializationStrategy) ? actual : THROW_CCE();
    } else {
      tmp_1 = serializer;
    }
    var actualSerializer = tmp_1;
    if (!(baseClassDiscriminator == null)) {
      var serialName = actualSerializer.jj().xj();
      this.g15_1 = baseClassDiscriminator;
      this.h15_1 = serialName;
    }
    actualSerializer.kj(this, value);
  }
};
protoOf(StreamingJsonEncoder).wl = function (descriptor) {
  var newMode = switchMode(this.a15_1, descriptor);
  if (!(newMode.m17_1 === _Char___init__impl__6a9atx(0))) {
    this.z14_1.i13(newMode.m17_1);
    this.z14_1.d13();
  }
  var discriminator = this.g15_1;
  if (!(discriminator == null)) {
    var tmp0_elvis_lhs = this.h15_1;
    encodeTypeInfo(this, discriminator, tmp0_elvis_lhs == null ? descriptor.xj() : tmp0_elvis_lhs);
    this.g15_1 = null;
    this.h15_1 = null;
  }
  if (this.b15_1.equals(newMode)) {
    return this;
  }
  var tmp1_safe_receiver = this.c15_1;
  var tmp2_elvis_lhs = tmp1_safe_receiver == null ? null : tmp1_safe_receiver[newMode.c2_1];
  return tmp2_elvis_lhs == null ? new StreamingJsonEncoder(this.z14_1, this.a15_1, newMode, this.c15_1) : tmp2_elvis_lhs;
};
protoOf(StreamingJsonEncoder).xl = function (descriptor) {
  if (!(this.b15_1.n17_1 === _Char___init__impl__6a9atx(0))) {
    this.z14_1.e13();
    this.z14_1.g13();
    this.z14_1.i13(this.b15_1.n17_1);
  }
};
protoOf(StreamingJsonEncoder).pm = function (descriptor, index) {
  switch (this.b15_1.c2_1) {
    case 1:
      if (!this.z14_1.c13_1) {
        this.z14_1.i13(_Char___init__impl__6a9atx(44));
      }

      this.z14_1.f13();
      break;
    case 2:
      if (!this.z14_1.c13_1) {
        var tmp = this;
        var tmp_0;
        if ((index % 2 | 0) === 0) {
          this.z14_1.i13(_Char___init__impl__6a9atx(44));
          this.z14_1.f13();
          tmp_0 = true;
        } else {
          this.z14_1.i13(_Char___init__impl__6a9atx(58));
          this.z14_1.h13();
          tmp_0 = false;
        }
        tmp.f15_1 = tmp_0;
      } else {
        this.f15_1 = true;
        this.z14_1.f13();
      }

      break;
    case 3:
      if (index === 0)
        this.f15_1 = true;
      if (index === 1) {
        this.z14_1.i13(_Char___init__impl__6a9atx(44));
        this.z14_1.h13();
        this.f15_1 = false;
      }

      break;
    default:
      if (!this.z14_1.c13_1) {
        this.z14_1.i13(_Char___init__impl__6a9atx(44));
      }

      this.z14_1.f13();
      this.an(getJsonElementName(descriptor, this.a15_1, index));
      this.z14_1.i13(_Char___init__impl__6a9atx(58));
      this.z14_1.h13();
      break;
  }
  return true;
};
protoOf(StreamingJsonEncoder).on = function (descriptor, index, serializer, value) {
  if (!(value == null) || this.e15_1.l11_1) {
    protoOf(AbstractEncoder).on.call(this, descriptor, index, serializer, value);
  }
};
protoOf(StreamingJsonEncoder).bn = function (descriptor) {
  var tmp;
  if (get_isUnsignedNumber(descriptor)) {
    // Inline function 'kotlinx.serialization.json.internal.StreamingJsonEncoder.composerAs' call
    var tmp_0;
    var tmp_1 = this.z14_1;
    if (tmp_1 instanceof ComposerForUnsignedNumbers) {
      tmp_0 = this.z14_1;
    } else {
      var tmp0 = this.z14_1.b13_1;
      var p1 = this.f15_1;
      tmp_0 = new ComposerForUnsignedNumbers(tmp0, p1);
    }
    var tmp$ret$0 = tmp_0;
    tmp = new StreamingJsonEncoder(tmp$ret$0, this.a15_1, this.b15_1, null);
  } else if (get_isUnquotedLiteral(descriptor)) {
    // Inline function 'kotlinx.serialization.json.internal.StreamingJsonEncoder.composerAs' call
    var tmp_2;
    var tmp_3 = this.z14_1;
    if (tmp_3 instanceof ComposerForUnquotedLiterals) {
      tmp_2 = this.z14_1;
    } else {
      var tmp0_0 = this.z14_1.b13_1;
      var p1_0 = this.f15_1;
      tmp_2 = new ComposerForUnquotedLiterals(tmp0_0, p1_0);
    }
    var tmp$ret$2 = tmp_2;
    tmp = new StreamingJsonEncoder(tmp$ret$2, this.a15_1, this.b15_1, null);
  } else if (!(this.g15_1 == null)) {
    // Inline function 'kotlin.apply' call
    this.h15_1 = descriptor.xj();
    tmp = this;
  } else {
    tmp = protoOf(AbstractEncoder).bn.call(this, descriptor);
  }
  return tmp;
};
protoOf(StreamingJsonEncoder).rm = function () {
  this.z14_1.k13('null');
};
protoOf(StreamingJsonEncoder).sm = function (value) {
  if (this.f15_1) {
    this.an(value.toString());
  } else {
    this.z14_1.t13(value);
  }
};
protoOf(StreamingJsonEncoder).tm = function (value) {
  if (this.f15_1) {
    this.an(value.toString());
  } else {
    this.z14_1.o13(value);
  }
};
protoOf(StreamingJsonEncoder).um = function (value) {
  if (this.f15_1) {
    this.an(value.toString());
  } else {
    this.z14_1.q13(value);
  }
};
protoOf(StreamingJsonEncoder).vm = function (value) {
  if (this.f15_1) {
    this.an(value.toString());
  } else {
    this.z14_1.r13(value);
  }
};
protoOf(StreamingJsonEncoder).wm = function (value) {
  if (this.f15_1) {
    this.an(value.toString());
  } else {
    this.z14_1.s13(value);
  }
};
protoOf(StreamingJsonEncoder).xm = function (value) {
  if (this.f15_1) {
    this.an(value.toString());
  } else {
    this.z14_1.m13(value);
  }
  if (!this.e15_1.q11_1 && !isFinite(value)) {
    throw InvalidFloatingPointEncoded(value, toString(this.z14_1.b13_1));
  }
};
protoOf(StreamingJsonEncoder).ym = function (value) {
  if (this.f15_1) {
    this.an(value.toString());
  } else {
    this.z14_1.n13(value);
  }
  if (!this.e15_1.q11_1 && !isFinite_0(value)) {
    throw InvalidFloatingPointEncoded(value, toString(this.z14_1.b13_1));
  }
};
protoOf(StreamingJsonEncoder).zm = function (value) {
  this.an(toString_1(value));
};
protoOf(StreamingJsonEncoder).an = function (value) {
  return this.z14_1.u13(value);
};
function get_isUnsignedNumber(_this__u8e3s4) {
  _init_properties_StreamingJsonEncoder_kt__pn1bsi();
  return _this__u8e3s4.zj() && get_unsignedNumberDescriptors().b1(_this__u8e3s4);
}
function get_isUnquotedLiteral(_this__u8e3s4) {
  _init_properties_StreamingJsonEncoder_kt__pn1bsi();
  return _this__u8e3s4.zj() && equals(_this__u8e3s4, get_jsonUnquotedLiteralDescriptor());
}
var properties_initialized_StreamingJsonEncoder_kt_6ifwwk;
function _init_properties_StreamingJsonEncoder_kt__pn1bsi() {
  if (!properties_initialized_StreamingJsonEncoder_kt_6ifwwk) {
    properties_initialized_StreamingJsonEncoder_kt_6ifwwk = true;
    unsignedNumberDescriptors = setOf([serializer_1(Companion_getInstance_0()).jj(), serializer_0(Companion_getInstance()).jj(), serializer_2(Companion_getInstance_1()).jj(), serializer_3(Companion_getInstance_2()).jj()]);
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
  _this__u8e3s4.d7(_Char___init__impl__6a9atx(34));
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
        _this__u8e3s4.ca(value, lastPos, i);
        _this__u8e3s4.c7(get_ESCAPE_STRINGS()[c]);
        lastPos = i + 1 | 0;
      }
    }
     while (inductionVariable <= last);
  if (!(lastPos === 0))
    _this__u8e3s4.ca(value, lastPos, value.length);
  else
    _this__u8e3s4.c7(value);
  _this__u8e3s4.d7(_Char___init__impl__6a9atx(34));
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
  throw JsonDecodingException_0(-1, "Failed to parse literal '" + literal.toString() + "' as " + type + ' value at element: ' + $this.y17(tag), toString($this.z17()));
}
function AbstractJsonTreeDecoder(json, value, polymorphicDiscriminator) {
  polymorphicDiscriminator = polymorphicDiscriminator === VOID ? null : polymorphicDiscriminator;
  NamedValueDecoder.call(this);
  this.u17_1 = json;
  this.v17_1 = value;
  this.w17_1 = polymorphicDiscriminator;
  this.x17_1 = this.x11().sz_1;
}
protoOf(AbstractJsonTreeDecoder).x11 = function () {
  return this.u17_1;
};
protoOf(AbstractJsonTreeDecoder).s1 = function () {
  return this.v17_1;
};
protoOf(AbstractJsonTreeDecoder).lm = function () {
  return this.x11().lm();
};
protoOf(AbstractJsonTreeDecoder).z17 = function () {
  var tmp0_safe_receiver = this.zx();
  var tmp;
  if (tmp0_safe_receiver == null) {
    tmp = null;
  } else {
    // Inline function 'kotlin.let' call
    tmp = this.a18(tmp0_safe_receiver);
  }
  var tmp1_elvis_lhs = tmp;
  return tmp1_elvis_lhs == null ? this.s1() : tmp1_elvis_lhs;
};
protoOf(AbstractJsonTreeDecoder).y17 = function (currentTag) {
  return this.by() + ('.' + currentTag);
};
protoOf(AbstractJsonTreeDecoder).y11 = function () {
  return this.z17();
};
protoOf(AbstractJsonTreeDecoder).vl = function (deserializer) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.decodeSerializableValuePolymorphic' call
    var tmp;
    if (!(deserializer instanceof AbstractPolymorphicSerializer)) {
      tmp = true;
    } else {
      tmp = this.x11().sz_1.o11_1;
    }
    if (tmp) {
      tmp$ret$0 = deserializer.lj(this);
      break $l$block;
    }
    var discriminator = classDiscriminator(deserializer.jj(), this.x11());
    var tmp0 = this.y11();
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var serialName = deserializer.jj().xj();
    if (!(tmp0 instanceof JsonObject)) {
      var tmp_0 = getKClass(JsonObject).z8();
      var tmp_1 = getKClassFromExpression(tmp0).z8();
      var tmp$ret$2 = this.by();
      throw JsonDecodingException_0(-1, 'Expected ' + tmp_0 + ', but had ' + tmp_1 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$2, toString(tmp0));
    }
    var jsonTree = tmp0;
    var tmp0_safe_receiver = jsonTree.mb(discriminator);
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
    tmp$ret$0 = readPolymorphicJson(this.x11(), discriminator, jsonTree, actualSerializer);
  }
  return tmp$ret$0;
};
protoOf(AbstractJsonTreeDecoder).ay = function (parentName, childName) {
  return childName;
};
protoOf(AbstractJsonTreeDecoder).wl = function (descriptor) {
  var currentObject = this.z17();
  var tmp0_subject = descriptor.yj();
  var tmp;
  var tmp_0;
  if (equals(tmp0_subject, LIST_getInstance())) {
    tmp_0 = true;
  } else {
    tmp_0 = tmp0_subject instanceof PolymorphicKind;
  }
  if (tmp_0) {
    var tmp_1 = this.x11();
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var serialName = descriptor.xj();
    if (!(currentObject instanceof JsonArray)) {
      var tmp_2 = getKClass(JsonArray).z8();
      var tmp_3 = getKClassFromExpression(currentObject).z8();
      var tmp$ret$2 = this.by();
      throw JsonDecodingException_0(-1, 'Expected ' + tmp_2 + ', but had ' + tmp_3 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$2, toString(currentObject));
    }
    tmp = new JsonTreeListDecoder(tmp_1, currentObject);
  } else {
    if (equals(tmp0_subject, MAP_getInstance())) {
      // Inline function 'kotlinx.serialization.json.internal.selectMapMode' call
      var this_0 = this.x11();
      var keyDescriptor = carrierDescriptor(descriptor.fk(0), this_0.lm());
      var keyKind = keyDescriptor.yj();
      var tmp_4;
      var tmp_5;
      if (keyKind instanceof PrimitiveKind) {
        tmp_5 = true;
      } else {
        tmp_5 = equals(keyKind, ENUM_getInstance());
      }
      if (tmp_5) {
        var tmp_6 = this.x11();
        // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
        // Inline function 'kotlinx.serialization.json.internal.cast' call
        var serialName_0 = descriptor.xj();
        if (!(currentObject instanceof JsonObject)) {
          var tmp_7 = getKClass(JsonObject).z8();
          var tmp_8 = getKClassFromExpression(currentObject).z8();
          var tmp$ret$7 = this.by();
          throw JsonDecodingException_0(-1, 'Expected ' + tmp_7 + ', but had ' + tmp_8 + ' as the serialized body of ' + serialName_0 + ' at element: ' + tmp$ret$7, toString(currentObject));
        }
        tmp_4 = new JsonTreeMapDecoder(tmp_6, currentObject);
      } else {
        if (this_0.sz_1.j11_1) {
          var tmp_9 = this.x11();
          // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
          // Inline function 'kotlinx.serialization.json.internal.cast' call
          var serialName_1 = descriptor.xj();
          if (!(currentObject instanceof JsonArray)) {
            var tmp_10 = getKClass(JsonArray).z8();
            var tmp_11 = getKClassFromExpression(currentObject).z8();
            var tmp$ret$11 = this.by();
            throw JsonDecodingException_0(-1, 'Expected ' + tmp_10 + ', but had ' + tmp_11 + ' as the serialized body of ' + serialName_1 + ' at element: ' + tmp$ret$11, toString(currentObject));
          }
          tmp_4 = new JsonTreeListDecoder(tmp_9, currentObject);
        } else {
          throw InvalidKeyKindException(keyDescriptor);
        }
      }
      tmp = tmp_4;
    } else {
      var tmp_12 = this.x11();
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
      // Inline function 'kotlinx.serialization.json.internal.cast' call
      var serialName_2 = descriptor.xj();
      if (!(currentObject instanceof JsonObject)) {
        var tmp_13 = getKClass(JsonObject).z8();
        var tmp_14 = getKClassFromExpression(currentObject).z8();
        var tmp$ret$14 = this.by();
        throw JsonDecodingException_0(-1, 'Expected ' + tmp_13 + ', but had ' + tmp_14 + ' as the serialized body of ' + serialName_2 + ' at element: ' + tmp$ret$14, toString(currentObject));
      }
      tmp = new JsonTreeDecoder(tmp_12, currentObject, this.w17_1);
    }
  }
  return tmp;
};
protoOf(AbstractJsonTreeDecoder).xl = function (descriptor) {
};
protoOf(AbstractJsonTreeDecoder).il = function () {
  var tmp = this.z17();
  return !(tmp instanceof JsonNull);
};
protoOf(AbstractJsonTreeDecoder).b18 = function (tag) {
  return !(this.a18(tag) === JsonNull_getInstance());
};
protoOf(AbstractJsonTreeDecoder).dy = function (tag) {
  return this.b18((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).c18 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.a18(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).z8();
      var tmp_0 = getKClassFromExpression(value).z8();
      var tmp$ret$3 = this.y17(tag);
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
protoOf(AbstractJsonTreeDecoder).ey = function (tag) {
  return this.c18((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).d18 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.a18(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).z8();
      var tmp_0 = getKClassFromExpression(value).z8();
      var tmp$ret$3 = this.y17(tag);
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
protoOf(AbstractJsonTreeDecoder).fy = function (tag) {
  return this.d18((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).e18 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.a18(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).z8();
      var tmp_0 = getKClassFromExpression(value).z8();
      var tmp$ret$3 = this.y17(tag);
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
protoOf(AbstractJsonTreeDecoder).gy = function (tag) {
  return this.e18((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).f18 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.a18(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).z8();
      var tmp_0 = getKClassFromExpression(value).z8();
      var tmp$ret$3 = this.y17(tag);
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
protoOf(AbstractJsonTreeDecoder).hy = function (tag) {
  return this.f18((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).g18 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.a18(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).z8();
      var tmp_0 = getKClassFromExpression(value).z8();
      var tmp$ret$3 = this.y17(tag);
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
protoOf(AbstractJsonTreeDecoder).iy = function (tag) {
  return this.g18((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).h18 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.a18(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).z8();
      var tmp_0 = getKClassFromExpression(value).z8();
      var tmp$ret$3 = this.y17(tag);
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
  var specialFp = this.x11().sz_1.q11_1;
  if (specialFp || isFinite(result))
    return result;
  throw InvalidFloatingPointDecoded(result, tag, toString(this.z17()));
};
protoOf(AbstractJsonTreeDecoder).jy = function (tag) {
  return this.h18((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).i18 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.a18(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).z8();
      var tmp_0 = getKClassFromExpression(value).z8();
      var tmp$ret$3 = this.y17(tag);
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
  var specialFp = this.x11().sz_1.q11_1;
  if (specialFp || isFinite_0(result))
    return result;
  throw InvalidFloatingPointDecoded(result, tag, toString(this.z17()));
};
protoOf(AbstractJsonTreeDecoder).ky = function (tag) {
  return this.i18((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).j18 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.a18(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).z8();
      var tmp_0 = getKClassFromExpression(value).z8();
      var tmp$ret$3 = this.y17(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'char' + ' at element: ' + tmp$ret$3, toString(value));
    }
    var literal = value;
    try {
      var tmp0_elvis_lhs = new Char(single(literal.b12()));
      var tmp_1;
      if (tmp0_elvis_lhs == null) {
        unparsedPrimitive(this, literal, 'char', tag);
      } else {
        tmp_1 = tmp0_elvis_lhs;
      }
      tmp$ret$0 = tmp_1.d1_1;
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
protoOf(AbstractJsonTreeDecoder).ly = function (tag) {
  return this.j18((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).k18 = function (tag) {
  // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
  // Inline function 'kotlinx.serialization.json.internal.cast' call
  var value = this.a18(tag);
  if (!(value instanceof JsonPrimitive)) {
    var tmp = getKClass(JsonPrimitive).z8();
    var tmp_0 = getKClassFromExpression(value).z8();
    var tmp$ret$2 = this.y17(tag);
    throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'string' + ' at element: ' + tmp$ret$2, toString(value));
  }
  var value_0 = value;
  if (!(value_0 instanceof JsonLiteral))
    throw JsonDecodingException_0(-1, "Expected string value for a non-null key '" + tag + "', got null literal instead at element: " + this.y17(tag), toString(this.z17()));
  if (!value_0.g12_1 && !this.x11().sz_1.i11_1) {
    throw JsonDecodingException_0(-1, "String literal for key '" + tag + "' should be quoted at element: " + this.y17(tag) + ".\nUse 'isLenient = true' in 'Json {}' builder to accept non-compliant JSON.", toString(this.z17()));
  }
  return value_0.i12_1;
};
protoOf(AbstractJsonTreeDecoder).my = function (tag) {
  return this.k18((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).l18 = function (tag, inlineDescriptor) {
  var tmp;
  if (get_isUnsignedNumber(inlineDescriptor)) {
    var tmp_0 = this.x11();
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    var tmp2 = this.a18(tag);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var serialName = inlineDescriptor.xj();
    if (!(tmp2 instanceof JsonPrimitive)) {
      var tmp_1 = getKClass(JsonPrimitive).z8();
      var tmp_2 = getKClassFromExpression(tmp2).z8();
      var tmp$ret$3 = this.y17(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp_1 + ', but had ' + tmp_2 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$3, toString(tmp2));
    }
    var lexer = StringJsonLexer_0(tmp_0, tmp2.b12());
    tmp = new JsonDecoderForUnsignedTypes(lexer, this.x11());
  } else {
    tmp = protoOf(NamedValueDecoder).ny.call(this, tag, inlineDescriptor);
  }
  return tmp;
};
protoOf(AbstractJsonTreeDecoder).ny = function (tag, inlineDescriptor) {
  return this.l18((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE(), inlineDescriptor);
};
protoOf(AbstractJsonTreeDecoder).tl = function (descriptor) {
  return !(this.zx() == null) ? protoOf(NamedValueDecoder).tl.call(this, descriptor) : (new JsonPrimitiveDecoder(this.x11(), this.s1(), this.w17_1)).tl(descriptor);
};
function coerceInputValue_0($this, descriptor, index, tag) {
  var tmp0 = $this.x11();
  var tmp$ret$0;
  $l$block_2: {
    // Inline function 'kotlinx.serialization.json.internal.tryCoerceValue' call
    var isOptional = descriptor.gk(index);
    var elementDescriptor = descriptor.fk(index);
    var tmp;
    if (isOptional && !elementDescriptor.rj()) {
      var tmp_0 = $this.a18(tag);
      tmp = tmp_0 instanceof JsonNull;
    } else {
      tmp = false;
    }
    if (tmp) {
      tmp$ret$0 = true;
      break $l$block_2;
    }
    if (equals(elementDescriptor.yj(), ENUM_getInstance())) {
      var tmp_1;
      if (elementDescriptor.rj()) {
        var tmp_2 = $this.a18(tag);
        tmp_1 = tmp_2 instanceof JsonNull;
      } else {
        tmp_1 = false;
      }
      if (tmp_1) {
        tmp$ret$0 = false;
        break $l$block_2;
      }
      var tmp_3 = $this.a18(tag);
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
      var coerceToNull = !tmp0.sz_1.l11_1 && elementDescriptor.rj();
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
  $this.v18_1 = (!$this.x11().sz_1.l11_1 && !descriptor.gk(index) && descriptor.fk(index).rj());
  return $this.v18_1;
}
function JsonTreeDecoder(json, value, polymorphicDiscriminator, polyDescriptor) {
  polymorphicDiscriminator = polymorphicDiscriminator === VOID ? null : polymorphicDiscriminator;
  polyDescriptor = polyDescriptor === VOID ? null : polyDescriptor;
  AbstractJsonTreeDecoder.call(this, json, value, polymorphicDiscriminator);
  this.s18_1 = value;
  this.t18_1 = polyDescriptor;
  this.u18_1 = 0;
  this.v18_1 = false;
}
protoOf(JsonTreeDecoder).s1 = function () {
  return this.s18_1;
};
protoOf(JsonTreeDecoder).nm = function (descriptor) {
  while (this.u18_1 < descriptor.ak()) {
    var _unary__edvuaz = this.u18_1;
    this.u18_1 = _unary__edvuaz + 1 | 0;
    var name = this.ux(descriptor, _unary__edvuaz);
    var index = this.u18_1 - 1 | 0;
    this.v18_1 = false;
    var tmp;
    var tmp_0;
    // Inline function 'kotlin.collections.contains' call
    // Inline function 'kotlin.collections.containsKey' call
    var this_0 = this.s1();
    if ((isInterface(this_0, KtMap) ? this_0 : THROW_CCE()).t1(name)) {
      tmp_0 = true;
    } else {
      tmp_0 = absenceIsNull(this, descriptor, index);
    }
    if (tmp_0) {
      tmp = !this.x17_1.n11_1 || !coerceInputValue_0(this, descriptor, index, name);
    } else {
      tmp = false;
    }
    if (tmp) {
      return index;
    }
  }
  return -1;
};
protoOf(JsonTreeDecoder).il = function () {
  return !this.v18_1 && protoOf(AbstractJsonTreeDecoder).il.call(this);
};
protoOf(JsonTreeDecoder).vx = function (descriptor, index) {
  var strategy = namingStrategy(descriptor, this.x11());
  var baseName = descriptor.ck(index);
  if (strategy == null) {
    if (!this.x17_1.r11_1)
      return baseName;
    if (this.s1().w1().b1(baseName))
      return baseName;
  }
  var deserializationNamesMap_0 = deserializationNamesMap(this.x11(), descriptor);
  // Inline function 'kotlin.collections.find' call
  var tmp0 = this.s1().w1();
  var tmp$ret$1;
  $l$block: {
    // Inline function 'kotlin.collections.firstOrNull' call
    var _iterator__ex2g4s = tmp0.k();
    while (_iterator__ex2g4s.l()) {
      var element = _iterator__ex2g4s.m();
      if (deserializationNamesMap_0.v1(element) === index) {
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
  var fallbackName = strategy == null ? null : strategy.p14(descriptor, index, baseName);
  return fallbackName == null ? baseName : fallbackName;
};
protoOf(JsonTreeDecoder).a18 = function (tag) {
  return getValue(this.s1(), tag);
};
protoOf(JsonTreeDecoder).wl = function (descriptor) {
  if (descriptor === this.t18_1) {
    var tmp = this.x11();
    var tmp2 = this.z17();
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var serialName = this.t18_1.xj();
    if (!(tmp2 instanceof JsonObject)) {
      var tmp_0 = getKClass(JsonObject).z8();
      var tmp_1 = getKClassFromExpression(tmp2).z8();
      var tmp$ret$2 = this.by();
      throw JsonDecodingException_0(-1, 'Expected ' + tmp_0 + ', but had ' + tmp_1 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$2, toString(tmp2));
    }
    return new JsonTreeDecoder(tmp, tmp2, this.w17_1, this.t18_1);
  }
  return protoOf(AbstractJsonTreeDecoder).wl.call(this, descriptor);
};
protoOf(JsonTreeDecoder).xl = function (descriptor) {
  var tmp;
  if (this.x17_1.h11_1) {
    tmp = true;
  } else {
    var tmp_0 = descriptor.yj();
    tmp = tmp_0 instanceof PolymorphicKind;
  }
  if (tmp)
    return Unit_instance;
  var strategy = namingStrategy(descriptor, this.x11());
  var tmp_1;
  if (strategy == null && !this.x17_1.r11_1) {
    tmp_1 = jsonCachedSerialNames(descriptor);
  } else if (!(strategy == null)) {
    tmp_1 = deserializationNamesMap(this.x11(), descriptor).w1();
  } else {
    var tmp_2 = jsonCachedSerialNames(descriptor);
    var tmp0_safe_receiver = get_schemaCache(this.x11()).z16(descriptor, get_JsonDeserializationNamesKey());
    // Inline function 'kotlin.collections.orEmpty' call
    var tmp0_elvis_lhs = tmp0_safe_receiver == null ? null : tmp0_safe_receiver.w1();
    var tmp$ret$0 = tmp0_elvis_lhs == null ? emptySet() : tmp0_elvis_lhs;
    tmp_1 = plus_0(tmp_2, tmp$ret$0);
  }
  var names = tmp_1;
  var _iterator__ex2g4s = this.s1().w1().k();
  while (_iterator__ex2g4s.l()) {
    var key = _iterator__ex2g4s.m();
    if (!names.b1(key) && !(key === this.w17_1)) {
      throw UnknownKeyException(key, this.s1().toString());
    }
  }
};
function JsonTreeListDecoder(json, value) {
  AbstractJsonTreeDecoder.call(this, json, value);
  this.c19_1 = value;
  this.d19_1 = this.c19_1.n();
  this.e19_1 = -1;
}
protoOf(JsonTreeListDecoder).s1 = function () {
  return this.c19_1;
};
protoOf(JsonTreeListDecoder).vx = function (descriptor, index) {
  return index.toString();
};
protoOf(JsonTreeListDecoder).a18 = function (tag) {
  return this.c19_1.o(toInt(tag));
};
protoOf(JsonTreeListDecoder).nm = function (descriptor) {
  while (this.e19_1 < (this.d19_1 - 1 | 0)) {
    this.e19_1 = this.e19_1 + 1 | 0;
    return this.e19_1;
  }
  return -1;
};
function JsonPrimitiveDecoder(json, value, polymorphicDiscriminator) {
  polymorphicDiscriminator = polymorphicDiscriminator === VOID ? null : polymorphicDiscriminator;
  AbstractJsonTreeDecoder.call(this, json, value, polymorphicDiscriminator);
  this.l19_1 = value;
  this.oy('primitive');
}
protoOf(JsonPrimitiveDecoder).s1 = function () {
  return this.l19_1;
};
protoOf(JsonPrimitiveDecoder).nm = function (descriptor) {
  return 0;
};
protoOf(JsonPrimitiveDecoder).a18 = function (tag) {
  // Inline function 'kotlin.require' call
  if (!(tag === 'primitive')) {
    var message = "This input can only handle primitives with 'primitive' tag";
    throw IllegalArgumentException_init_$Create$(toString(message));
  }
  return this.l19_1;
};
function JsonTreeMapDecoder(json, value) {
  JsonTreeDecoder.call(this, json, value);
  this.w19_1 = value;
  this.x19_1 = toList(this.w19_1.w1());
  this.y19_1 = imul(this.x19_1.n(), 2);
  this.z19_1 = -1;
}
protoOf(JsonTreeMapDecoder).s1 = function () {
  return this.w19_1;
};
protoOf(JsonTreeMapDecoder).vx = function (descriptor, index) {
  var i = index / 2 | 0;
  return this.x19_1.o(i);
};
protoOf(JsonTreeMapDecoder).nm = function (descriptor) {
  while (this.z19_1 < (this.y19_1 - 1 | 0)) {
    this.z19_1 = this.z19_1 + 1 | 0;
    return this.z19_1;
  }
  return -1;
};
protoOf(JsonTreeMapDecoder).a18 = function (tag) {
  return (this.z19_1 % 2 | 0) === 0 ? JsonPrimitive_0(tag) : getValue(this.w19_1, tag);
};
protoOf(JsonTreeMapDecoder).xl = function (descriptor) {
};
function readPolymorphicJson(_this__u8e3s4, discriminator, element, deserializer) {
  return (new JsonTreeDecoder(_this__u8e3s4, element, discriminator, deserializer.jj())).vl(deserializer);
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
  this.m17_1 = begin;
  this.n17_1 = end;
}
function switchMode(_this__u8e3s4, desc) {
  var tmp0_subject = desc.yj();
  var tmp;
  if (tmp0_subject instanceof PolymorphicKind) {
    tmp = WriteMode_POLY_OBJ_getInstance();
  } else {
    if (equals(tmp0_subject, LIST_getInstance())) {
      tmp = WriteMode_LIST_getInstance();
    } else {
      if (equals(tmp0_subject, MAP_getInstance())) {
        // Inline function 'kotlinx.serialization.json.internal.selectMapMode' call
        var keyDescriptor = carrierDescriptor(desc.fk(0), _this__u8e3s4.lm());
        var keyKind = keyDescriptor.yj();
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
          if (_this__u8e3s4.sz_1.j11_1) {
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
  if (equals(_this__u8e3s4.yj(), CONTEXTUAL_getInstance())) {
    var tmp0_safe_receiver = getContextualDescriptor(module_0, _this__u8e3s4);
    var tmp1_elvis_lhs = tmp0_safe_receiver == null ? null : carrierDescriptor(tmp0_safe_receiver, module_0);
    tmp = tmp1_elvis_lhs == null ? _this__u8e3s4 : tmp1_elvis_lhs;
  } else if (_this__u8e3s4.zj()) {
    tmp = carrierDescriptor(_this__u8e3s4.fk(0), module_0);
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
  $this.a1a(lastPosition, current);
  return appendEsc($this, current + 1 | 0);
}
function decodedString($this, lastPosition, currentPosition) {
  $this.a1a(lastPosition, currentPosition);
  var result = $this.l10_1.toString();
  $this.l10_1.ha(0);
  return result;
}
function takePeeked($this) {
  // Inline function 'kotlin.also' call
  var this_0 = ensureNotNull($this.k10_1);
  $this.k10_1 = null;
  return this_0;
}
function wasUnquotedString($this) {
  return !(charSequenceGet($this.b1a(), $this.i10_1 - 1 | 0) === _Char___init__impl__6a9atx(34));
}
function appendEsc($this, startPosition) {
  var currentPosition = startPosition;
  currentPosition = $this.c1a(currentPosition);
  if (currentPosition === -1) {
    $this.l14('Expected escape sequence to continue, got EOF');
  }
  var tmp = $this.b1a();
  var _unary__edvuaz = currentPosition;
  currentPosition = _unary__edvuaz + 1 | 0;
  var currentChar = charSequenceGet(tmp, _unary__edvuaz);
  if (currentChar === _Char___init__impl__6a9atx(117)) {
    return appendHex($this, $this.b1a(), currentPosition);
  }
  // Inline function 'kotlin.code' call
  var tmp$ret$0 = Char__toInt_impl_vasixd(currentChar);
  var c = escapeToChar(tmp$ret$0);
  if (c === _Char___init__impl__6a9atx(0)) {
    $this.l14("Invalid escaped char '" + toString_1(currentChar) + "'");
  }
  $this.l10_1.d7(c);
  return currentPosition;
}
function appendHex($this, source, startPos) {
  if ((startPos + 4 | 0) >= charSequenceLength(source)) {
    $this.i10_1 = startPos;
    $this.d1a();
    if (($this.i10_1 + 4 | 0) >= charSequenceLength(source)) {
      $this.l14('Unexpected EOF during unicode escape');
    }
    return appendHex($this, source, $this.i10_1);
  }
  $this.l10_1.d7(numberToChar((((fromHexChar($this, source, startPos) << 12) + (fromHexChar($this, source, startPos + 1 | 0) << 8) | 0) + (fromHexChar($this, source, startPos + 2 | 0) << 4) | 0) + fromHexChar($this, source, startPos + 3 | 0) | 0));
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
    $this.l14("Invalid toHexChar char '" + toString_1(character) + "' in unicode escape");
  }
  return tmp;
}
function consumeBoolean2($this, start) {
  var current = $this.c1a(start);
  if (current >= charSequenceLength($this.b1a()) || current === -1) {
    $this.l14('EOF');
  }
  var tmp = $this.b1a();
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
      $this.l14("Expected valid boolean literal prefix, but had '" + $this.q15() + "'");
    }
  }
  return tmp_0;
}
function consumeBooleanLiteral($this, literalSuffix, current) {
  if ((charSequenceLength($this.b1a()) - current | 0) < literalSuffix.length) {
    $this.l14('Unexpected end of boolean literal');
  }
  var inductionVariable = 0;
  var last = charSequenceLength(literalSuffix) - 1 | 0;
  if (inductionVariable <= last)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      var expected = charCodeAt(literalSuffix, i);
      var actual = charSequenceGet($this.b1a(), current + i | 0);
      // Inline function 'kotlin.code' call
      var tmp = Char__toInt_impl_vasixd(expected);
      // Inline function 'kotlin.code' call
      if (!(tmp === (Char__toInt_impl_vasixd(actual) | 32))) {
        $this.l14("Expected valid boolean literal prefix, but had '" + $this.q15() + "'");
      }
    }
     while (inductionVariable <= last);
  $this.i10_1 = current + literalSuffix.length | 0;
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
  this.i10_1 = 0;
  this.j10_1 = new JsonPath();
  this.k10_1 = null;
  this.l10_1 = StringBuilder_init_$Create$();
}
protoOf(AbstractJsonLexer).d1a = function () {
};
protoOf(AbstractJsonLexer).c17 = function () {
  var current = this.e1a();
  var source = this.b1a();
  if (current >= charSequenceLength(source) || current === -1)
    return false;
  if (charSequenceGet(source, current) === _Char___init__impl__6a9atx(44)) {
    this.i10_1 = this.i10_1 + 1 | 0;
    return true;
  }
  return false;
};
protoOf(AbstractJsonLexer).f1a = function (c) {
  return c === _Char___init__impl__6a9atx(125) || c === _Char___init__impl__6a9atx(93) || (c === _Char___init__impl__6a9atx(58) || c === _Char___init__impl__6a9atx(44)) ? false : true;
};
protoOf(AbstractJsonLexer).m10 = function () {
  var nextToken = this.s15();
  if (!(nextToken === 10)) {
    this.l14('Expected EOF after parsing, but had ' + toString_1(charSequenceGet(this.b1a(), this.i10_1 - 1 | 0)) + ' instead');
  }
};
protoOf(AbstractJsonLexer).m15 = function (expected) {
  var token = this.s15();
  if (!(token === expected)) {
    this.g1a(expected);
  }
  return token;
};
protoOf(AbstractJsonLexer).h1a = function (expected) {
  if (this.i10_1 > 0 && expected === _Char___init__impl__6a9atx(34)) {
    var tmp$ret$0;
    $l$block: {
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.withPositionRollback' call
      var snapshot = this.i10_1;
      try {
        this.i10_1 = this.i10_1 - 1 | 0;
        tmp$ret$0 = this.q15();
        break $l$block;
      }finally {
        this.i10_1 = snapshot;
      }
    }
    var inputLiteral = tmp$ret$0;
    if (inputLiteral === 'null') {
      this.k14("Expected string literal but 'null' literal was found", this.i10_1 - 1 | 0, "Use 'coerceInputValues = true' in 'Json {}' builder to coerce nulls if property has a default value.");
    }
  }
  this.g1a(charToTokenClass(expected));
};
protoOf(AbstractJsonLexer).i1a = function (expectedToken, wasConsumed) {
  var expected = tokenDescription(expectedToken);
  var position = wasConsumed ? this.i10_1 - 1 | 0 : this.i10_1;
  var s = this.i10_1 === charSequenceLength(this.b1a()) || position < 0 ? 'EOF' : toString_1(charSequenceGet(this.b1a(), position));
  this.l14('Expected ' + expected + ", but had '" + s + "' instead", position);
};
protoOf(AbstractJsonLexer).g1a = function (expectedToken, wasConsumed, $super) {
  wasConsumed = wasConsumed === VOID ? true : wasConsumed;
  return $super === VOID ? this.i1a(expectedToken, wasConsumed) : $super.i1a.call(this, expectedToken, wasConsumed);
};
protoOf(AbstractJsonLexer).n15 = function () {
  var source = this.b1a();
  var cpos = this.i10_1;
  $l$loop_0: while (true) {
    cpos = this.c1a(cpos);
    if (cpos === -1)
      break $l$loop_0;
    var ch = charSequenceGet(source, cpos);
    if (ch === _Char___init__impl__6a9atx(32) || ch === _Char___init__impl__6a9atx(10) || ch === _Char___init__impl__6a9atx(13) || ch === _Char___init__impl__6a9atx(9)) {
      cpos = cpos + 1 | 0;
      continue $l$loop_0;
    }
    this.i10_1 = cpos;
    return charToTokenClass(ch);
  }
  this.i10_1 = cpos;
  return 10;
};
protoOf(AbstractJsonLexer).d17 = function (doConsume) {
  var current = this.e1a();
  current = this.c1a(current);
  var len = charSequenceLength(this.b1a()) - current | 0;
  if (len < 4 || current === -1)
    return false;
  var inductionVariable = 0;
  if (inductionVariable <= 3)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      if (!(charCodeAt('null', i) === charSequenceGet(this.b1a(), current + i | 0)))
        return false;
    }
     while (inductionVariable <= 3);
  if (len > 4 && charToTokenClass(charSequenceGet(this.b1a(), current + 4 | 0)) === 0)
    return false;
  if (doConsume) {
    this.i10_1 = current + 4 | 0;
  }
  return true;
};
protoOf(AbstractJsonLexer).o17 = function (doConsume, $super) {
  doConsume = doConsume === VOID ? true : doConsume;
  return $super === VOID ? this.d17(doConsume) : $super.d17.call(this, doConsume);
};
protoOf(AbstractJsonLexer).e17 = function (isLenient) {
  var token = this.n15();
  var tmp;
  if (isLenient) {
    if (!(token === 1) && !(token === 0))
      return null;
    tmp = this.q15();
  } else {
    if (!(token === 1))
      return null;
    tmp = this.p15();
  }
  var string = tmp;
  this.k10_1 = string;
  return string;
};
protoOf(AbstractJsonLexer).j1a = function () {
  this.k10_1 = null;
};
protoOf(AbstractJsonLexer).k1a = function (startPos, endPos) {
  // Inline function 'kotlin.text.substring' call
  var this_0 = this.b1a();
  return toString(charSequenceSubSequence(this_0, startPos, endPos));
};
protoOf(AbstractJsonLexer).p15 = function () {
  if (!(this.k10_1 == null)) {
    return takePeeked(this);
  }
  return this.h17();
};
protoOf(AbstractJsonLexer).consumeString2 = function (source, startPosition, current) {
  var currentPosition = current;
  var lastPosition = startPosition;
  var char = charSequenceGet(source, currentPosition);
  var usedAppend = false;
  while (!(char === _Char___init__impl__6a9atx(34))) {
    if (char === _Char___init__impl__6a9atx(92)) {
      usedAppend = true;
      currentPosition = this.c1a(appendEscape(this, lastPosition, currentPosition));
      if (currentPosition === -1) {
        this.l14('Unexpected EOF', currentPosition);
      }
      lastPosition = currentPosition;
    } else {
      currentPosition = currentPosition + 1 | 0;
      if (currentPosition >= charSequenceLength(source)) {
        usedAppend = true;
        this.a1a(lastPosition, currentPosition);
        currentPosition = this.c1a(currentPosition);
        if (currentPosition === -1) {
          this.l14('Unexpected EOF', currentPosition);
        }
        lastPosition = currentPosition;
      }
    }
    char = charSequenceGet(source, currentPosition);
  }
  var tmp;
  if (!usedAppend) {
    tmp = this.k1a(lastPosition, currentPosition);
  } else {
    tmp = decodedString(this, lastPosition, currentPosition);
  }
  var string = tmp;
  this.i10_1 = currentPosition + 1 | 0;
  return string;
};
protoOf(AbstractJsonLexer).i17 = function () {
  var result = this.q15();
  if (result === 'null' && wasUnquotedString(this)) {
    this.l14("Unexpected 'null' value instead of string literal");
  }
  return result;
};
protoOf(AbstractJsonLexer).q15 = function () {
  if (!(this.k10_1 == null)) {
    return takePeeked(this);
  }
  var current = this.e1a();
  if (current >= charSequenceLength(this.b1a()) || current === -1) {
    this.l14('EOF', current);
  }
  var token = charToTokenClass(charSequenceGet(this.b1a(), current));
  if (token === 1) {
    return this.p15();
  }
  if (!(token === 0)) {
    this.l14('Expected beginning of the string, but got ' + toString_1(charSequenceGet(this.b1a(), current)));
  }
  var usedAppend = false;
  while (charToTokenClass(charSequenceGet(this.b1a(), current)) === 0) {
    current = current + 1 | 0;
    if (current >= charSequenceLength(this.b1a())) {
      usedAppend = true;
      this.a1a(this.i10_1, current);
      var eof = this.c1a(current);
      if (eof === -1) {
        this.i10_1 = current;
        return decodedString(this, 0, 0);
      } else {
        current = eof;
      }
    }
  }
  var tmp;
  if (!usedAppend) {
    tmp = this.k1a(this.i10_1, current);
  } else {
    tmp = decodedString(this, this.i10_1, current);
  }
  var result = tmp;
  this.i10_1 = current;
  return result;
};
protoOf(AbstractJsonLexer).a1a = function (fromIndex, toIndex) {
  this.l10_1.ca(this.b1a(), fromIndex, toIndex);
};
protoOf(AbstractJsonLexer).g17 = function (allowLenientStrings) {
  // Inline function 'kotlin.collections.mutableListOf' call
  var tokenStack = ArrayList_init_$Create$();
  var lastToken = this.n15();
  if (!(lastToken === 8) && !(lastToken === 6)) {
    this.q15();
    return Unit_instance;
  }
  $l$loop: while (true) {
    lastToken = this.n15();
    if (lastToken === 1) {
      if (allowLenientStrings)
        this.q15();
      else
        this.h17();
      continue $l$loop;
    }
    var tmp0_subject = lastToken;
    if (tmp0_subject === 8 || tmp0_subject === 6) {
      tokenStack.h(lastToken);
    } else if (tmp0_subject === 9) {
      if (!(last(tokenStack) === 8))
        throw JsonDecodingException_0(this.i10_1, 'found ] instead of } at path: ' + this.j10_1.toString(), this.b1a());
      removeLast(tokenStack);
    } else if (tmp0_subject === 7) {
      if (!(last(tokenStack) === 6))
        throw JsonDecodingException_0(this.i10_1, 'found } instead of ] at path: ' + this.j10_1.toString(), this.b1a());
      removeLast(tokenStack);
    } else if (tmp0_subject === 10) {
      this.l14('Unexpected end of input due to malformed JSON during ignoring unknown keys');
    }
    this.s15();
    if (tokenStack.n() === 0)
      return Unit_instance;
  }
};
protoOf(AbstractJsonLexer).toString = function () {
  return "JsonReader(source='" + toString(this.b1a()) + "', currentPosition=" + this.i10_1 + ')';
};
protoOf(AbstractJsonLexer).f17 = function (key) {
  var processed = this.k1a(0, this.i10_1);
  var lastIndexOf_0 = lastIndexOf(processed, key);
  this.k14("Encountered an unknown key '" + key + "'", lastIndexOf_0, "Use 'ignoreUnknownKeys = true' in 'Json {}' builder to ignore unknown keys.");
};
protoOf(AbstractJsonLexer).k14 = function (message, position, hint) {
  var tmp;
  // Inline function 'kotlin.text.isEmpty' call
  if (charSequenceLength(hint) === 0) {
    tmp = '';
  } else {
    tmp = '\n' + hint;
  }
  var hintMessage = tmp;
  throw JsonDecodingException_0(position, message + ' at path: ' + this.j10_1.y14() + hintMessage, this.b1a());
};
protoOf(AbstractJsonLexer).l14 = function (message, position, hint, $super) {
  position = position === VOID ? this.i10_1 : position;
  hint = hint === VOID ? '' : hint;
  return $super === VOID ? this.k14(message, position, hint) : $super.k14.call(this, message, position, hint);
};
protoOf(AbstractJsonLexer).j12 = function () {
  var current = this.e1a();
  current = this.c1a(current);
  if (current >= charSequenceLength(this.b1a()) || current === -1) {
    this.l14('EOF');
  }
  var tmp;
  if (charSequenceGet(this.b1a(), current) === _Char___init__impl__6a9atx(34)) {
    current = current + 1 | 0;
    if (current === charSequenceLength(this.b1a())) {
      this.l14('EOF');
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
  $l$loop_4: while (!(current === charSequenceLength(this.b1a()))) {
    var ch = charSequenceGet(this.b1a(), current);
    if ((ch === _Char___init__impl__6a9atx(101) || ch === _Char___init__impl__6a9atx(69)) && !hasExponent) {
      if (current === start) {
        this.l14('Unexpected symbol ' + toString_1(ch) + ' in numeric literal');
      }
      isExponentPositive = true;
      hasExponent = true;
      current = current + 1 | 0;
      continue $l$loop_4;
    }
    if (ch === _Char___init__impl__6a9atx(45) && hasExponent) {
      if (current === start) {
        this.l14("Unexpected symbol '-' in numeric literal");
      }
      isExponentPositive = false;
      current = current + 1 | 0;
      continue $l$loop_4;
    }
    if (ch === _Char___init__impl__6a9atx(43) && hasExponent) {
      if (current === start) {
        this.l14("Unexpected symbol '+' in numeric literal");
      }
      isExponentPositive = true;
      current = current + 1 | 0;
      continue $l$loop_4;
    }
    if (ch === _Char___init__impl__6a9atx(45)) {
      if (!(current === start)) {
        this.l14("Unexpected symbol '-' in numeric literal");
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
      this.l14("Unexpected symbol '" + toString_1(ch) + "' in numeric literal");
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
      this.l14('Numeric value overflow');
    }
  }
  var hasChars = !(current === start);
  if (start === current || (isNegative && start === (current - 1 | 0))) {
    this.l14('Expected numeric literal');
  }
  if (hasQuotation) {
    if (!hasChars) {
      this.l14('EOF');
    }
    if (!(charSequenceGet(this.b1a(), current) === _Char___init__impl__6a9atx(34))) {
      this.l14('Expected closing quotation mark');
    }
    current = current + 1 | 0;
  }
  this.i10_1 = current;
  if (hasExponent) {
    var doubleAccumulator = toNumber(accumulator) * consumeNumericLiteral$calculateExponent(exponentAccumulator, isExponentPositive);
    if (doubleAccumulator > toNumber(new Long(-1, 2147483647)) || doubleAccumulator < toNumber(new Long(0, -2147483648))) {
      this.l14('Numeric value overflow');
    }
    // Inline function 'kotlin.math.floor' call
    if (!(Math.floor(doubleAccumulator) === doubleAccumulator)) {
      this.l14("Can't convert " + doubleAccumulator + ' to Long');
    }
    accumulator = numberToLong(doubleAccumulator);
  }
  var tmp_0;
  if (isNegative) {
    tmp_0 = accumulator;
  } else if (!equalsLong(accumulator, new Long(0, -2147483648))) {
    tmp_0 = negate(accumulator);
  } else {
    this.l14('Numeric value overflow');
  }
  return tmp_0;
};
protoOf(AbstractJsonLexer).p17 = function () {
  var current = this.e1a();
  if (current === charSequenceLength(this.b1a())) {
    this.l14('EOF');
  }
  var tmp;
  if (charSequenceGet(this.b1a(), current) === _Char___init__impl__6a9atx(34)) {
    current = current + 1 | 0;
    tmp = true;
  } else {
    tmp = false;
  }
  var hasQuotation = tmp;
  var result = consumeBoolean2(this, current);
  if (hasQuotation) {
    if (this.i10_1 === charSequenceLength(this.b1a())) {
      this.l14('EOF');
    }
    if (!(charSequenceGet(this.b1a(), this.i10_1) === _Char___init__impl__6a9atx(34))) {
      this.l14('Expected closing quotation mark');
    }
    this.i10_1 = this.i10_1 + 1 | 0;
  }
  return result;
};
function charToTokenClass(c) {
  var tmp;
  // Inline function 'kotlin.code' call
  if (Char__toInt_impl_vasixd(c) < 126) {
    var tmp_0 = CharMappings_getInstance().m1a_1;
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
  return c < 117 ? CharMappings_getInstance().l1a_1[c] : _Char___init__impl__6a9atx(0);
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
    $this.l1a_1[tmp$ret$0] = numberToChar(c);
  }
}
function initC2ESC_0($this, c, esc) {
  // Inline function 'kotlin.code' call
  var tmp$ret$0 = Char__toInt_impl_vasixd(c);
  return initC2ESC($this, tmp$ret$0, esc);
}
function initC2TC($this, c, cl) {
  $this.m1a_1[c] = cl;
}
function initC2TC_0($this, c, cl) {
  // Inline function 'kotlin.code' call
  var tmp$ret$0 = Char__toInt_impl_vasixd(c);
  return initC2TC($this, tmp$ret$0, cl);
}
function CharMappings() {
  CharMappings_instance = this;
  this.l1a_1 = charArray(117);
  this.m1a_1 = new Int8Array(126);
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
protoOf(StringJsonLexerWithComments).s15 = function () {
  var source = this.b1a();
  var cpos = this.e1a();
  if (cpos >= source.length || cpos === -1)
    return 10;
  this.i10_1 = cpos + 1 | 0;
  return charToTokenClass(charCodeAt(source, cpos));
};
protoOf(StringJsonLexerWithComments).o15 = function () {
  var current = this.e1a();
  if (current >= this.b1a().length || current === -1)
    return false;
  return this.f1a(charCodeAt(this.b1a(), current));
};
protoOf(StringJsonLexerWithComments).b17 = function (expected) {
  var source = this.b1a();
  var current = this.e1a();
  if (current >= source.length || current === -1) {
    this.i10_1 = -1;
    this.h1a(expected);
  }
  var c = charCodeAt(source, current);
  this.i10_1 = current + 1 | 0;
  if (c === expected)
    return Unit_instance;
  else {
    this.h1a(expected);
  }
};
protoOf(StringJsonLexerWithComments).n15 = function () {
  var source = this.b1a();
  var cpos = this.e1a();
  if (cpos >= source.length || cpos === -1)
    return 10;
  this.i10_1 = cpos;
  return charToTokenClass(charCodeAt(source, cpos));
};
protoOf(StringJsonLexerWithComments).e1a = function () {
  var current = this.i10_1;
  if (current === -1)
    return current;
  var source = this.b1a();
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
          this.i10_1 = source.length;
          this.l14('Expected end of the block comment: "*/", but had EOF instead');
        } else {
          current = current + 2 | 0;
        }
        continue $l$loop_1;
      }
    }
    break $l$loop_1;
  }
  this.i10_1 = current;
  return current;
};
function StringJsonLexer(source) {
  AbstractJsonLexer.call(this);
  this.w1a_1 = source;
}
protoOf(StringJsonLexer).b1a = function () {
  return this.w1a_1;
};
protoOf(StringJsonLexer).c1a = function (position) {
  return position < this.b1a().length ? position : -1;
};
protoOf(StringJsonLexer).s15 = function () {
  var source = this.b1a();
  var cpos = this.i10_1;
  $l$loop: while (!(cpos === -1) && cpos < source.length) {
    var _unary__edvuaz = cpos;
    cpos = _unary__edvuaz + 1 | 0;
    var c = charCodeAt(source, _unary__edvuaz);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.isWs' call
    if (c === _Char___init__impl__6a9atx(32) || c === _Char___init__impl__6a9atx(10) || c === _Char___init__impl__6a9atx(13) || c === _Char___init__impl__6a9atx(9))
      continue $l$loop;
    this.i10_1 = cpos;
    return charToTokenClass(c);
  }
  this.i10_1 = source.length;
  return 10;
};
protoOf(StringJsonLexer).o15 = function () {
  var current = this.i10_1;
  if (current === -1)
    return false;
  var source = this.b1a();
  $l$loop: while (current < source.length) {
    var c = charCodeAt(source, current);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.isWs' call
    if (c === _Char___init__impl__6a9atx(32) || c === _Char___init__impl__6a9atx(10) || c === _Char___init__impl__6a9atx(13) || c === _Char___init__impl__6a9atx(9)) {
      current = current + 1 | 0;
      continue $l$loop;
    }
    this.i10_1 = current;
    return this.f1a(c);
  }
  this.i10_1 = current;
  return false;
};
protoOf(StringJsonLexer).e1a = function () {
  var current = this.i10_1;
  if (current === -1)
    return current;
  var source = this.b1a();
  $l$loop: while (current < source.length) {
    var c = charCodeAt(source, current);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.isWs' call
    if (c === _Char___init__impl__6a9atx(32) || c === _Char___init__impl__6a9atx(10) || c === _Char___init__impl__6a9atx(13) || c === _Char___init__impl__6a9atx(9)) {
      current = current + 1 | 0;
    } else {
      break $l$loop;
    }
  }
  this.i10_1 = current;
  return current;
};
protoOf(StringJsonLexer).b17 = function (expected) {
  if (this.i10_1 === -1) {
    this.h1a(expected);
  }
  var source = this.b1a();
  var cpos = this.i10_1;
  $l$loop: while (cpos < source.length) {
    var _unary__edvuaz = cpos;
    cpos = _unary__edvuaz + 1 | 0;
    var c = charCodeAt(source, _unary__edvuaz);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.isWs' call
    if (c === _Char___init__impl__6a9atx(32) || c === _Char___init__impl__6a9atx(10) || c === _Char___init__impl__6a9atx(13) || c === _Char___init__impl__6a9atx(9))
      continue $l$loop;
    this.i10_1 = cpos;
    if (c === expected)
      return Unit_instance;
    this.h1a(expected);
  }
  this.i10_1 = -1;
  this.h1a(expected);
};
protoOf(StringJsonLexer).h17 = function () {
  this.b17(_Char___init__impl__6a9atx(34));
  var current = this.i10_1;
  var closingQuote = indexOf_0(this.b1a(), _Char___init__impl__6a9atx(34), current);
  if (closingQuote === -1) {
    this.q15();
    this.i1a(1, false);
  }
  var inductionVariable = current;
  if (inductionVariable < closingQuote)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      if (charCodeAt(this.b1a(), i) === _Char___init__impl__6a9atx(92)) {
        return this.consumeString2(this.b1a(), this.i10_1, i);
      }
    }
     while (inductionVariable < closingQuote);
  this.i10_1 = closingQuote + 1 | 0;
  return substring(this.b1a(), current, closingQuote);
};
protoOf(StringJsonLexer).j17 = function (keyToMatch, isLenient) {
  var positionSnapshot = this.i10_1;
  try {
    if (!(this.s15() === 6))
      return null;
    var firstKey = this.e17(isLenient);
    if (!(firstKey === keyToMatch))
      return null;
    this.j1a();
    if (!(this.s15() === 5))
      return null;
    return this.e17(isLenient);
  }finally {
    this.i10_1 = positionSnapshot;
    this.j1a();
  }
};
function StringJsonLexer_0(json, source) {
  return !json.sz_1.v11_1 ? new StringJsonLexer(source) : new StringJsonLexerWithComments(source);
}
function get_schemaCache(_this__u8e3s4) {
  return _this__u8e3s4.uz_1;
}
function JsonToStringWriter() {
  this.yz_1 = StringBuilder_init_$Create$_0(128);
}
protoOf(JsonToStringWriter).p13 = function (value) {
  this.yz_1.fa(value);
};
protoOf(JsonToStringWriter).j13 = function (char) {
  this.yz_1.d7(char);
};
protoOf(JsonToStringWriter).l13 = function (text) {
  this.yz_1.c7(text);
};
protoOf(JsonToStringWriter).v13 = function (text) {
  printQuoted(this.yz_1, text);
};
protoOf(JsonToStringWriter).zz = function () {
  this.yz_1.ia();
};
protoOf(JsonToStringWriter).toString = function () {
  return this.yz_1.toString();
};
function createMapForCache(initialCapacity) {
  return HashMap_init_$Create$(initialCapacity);
}
//region block: post-declaration
protoOf(defer$1).rj = get_isNullable;
protoOf(defer$1).zj = get_isInline;
protoOf(defer$1).bk = get_annotations;
protoOf(PolymorphismValidator).oz = contextual;
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


