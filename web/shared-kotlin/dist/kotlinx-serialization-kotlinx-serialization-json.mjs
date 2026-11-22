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
  this.mx_1 = configuration;
  this.nx_1 = serializersModule;
  this.ox_1 = new DescriptorSchemaCache();
}
protoOf(Json).fk = function () {
  return this.nx_1;
};
protoOf(Json).px = function (serializer, value) {
  var result = new JsonToStringWriter();
  try {
    encodeByWriter(this, result, serializer, value);
    return result.toString();
  }finally {
    result.tx();
  }
};
protoOf(Json).qx = function (deserializer, string) {
  var lexer = StringJsonLexer_0(this, string);
  var input = new StreamingJsonDecoder(this, WriteMode_OBJ_getInstance(), lexer, deserializer.dh(), null);
  var result = input.pj(deserializer);
  lexer.gy();
  return result;
};
protoOf(Json).rx = function (string) {
  return this.qx(JsonElementSerializer_getInstance(), string);
};
function Json_0(from, builderAction) {
  from = from === VOID ? Default_getInstance() : from;
  var builder = new JsonBuilder(from);
  builderAction(builder);
  var conf = builder.zy();
  return new JsonImpl(conf, builder.yy_1);
}
function JsonBuilder(json) {
  this.hy_1 = json.mx_1.az_1;
  this.iy_1 = json.mx_1.fz_1;
  this.jy_1 = json.mx_1.bz_1;
  this.ky_1 = json.mx_1.cz_1;
  this.ly_1 = json.mx_1.ez_1;
  this.my_1 = json.mx_1.gz_1;
  this.ny_1 = json.mx_1.hz_1;
  this.oy_1 = json.mx_1.jz_1;
  this.py_1 = json.mx_1.qz_1;
  this.qy_1 = json.mx_1.lz_1;
  this.ry_1 = json.mx_1.mz_1;
  this.sy_1 = json.mx_1.nz_1;
  this.ty_1 = json.mx_1.oz_1;
  this.uy_1 = json.mx_1.pz_1;
  this.vy_1 = json.mx_1.kz_1;
  this.wy_1 = json.mx_1.dz_1;
  this.xy_1 = json.mx_1.iz_1;
  this.yy_1 = json.fk();
}
protoOf(JsonBuilder).zy = function () {
  if (this.xy_1) {
    // Inline function 'kotlin.require' call
    if (!(this.oy_1 === 'type')) {
      var message = 'Class discriminator should not be specified when array polymorphism is specified';
      throw IllegalArgumentException_init_$Create$(toString(message));
    }
    // Inline function 'kotlin.require' call
    if (!this.py_1.equals(ClassDiscriminatorMode_POLYMORPHIC_getInstance())) {
      var message_0 = 'useArrayPolymorphism option can only be used if classDiscriminatorMode in a default POLYMORPHIC state.';
      throw IllegalArgumentException_init_$Create$(toString(message_0));
    }
  }
  if (!this.ly_1) {
    // Inline function 'kotlin.require' call
    if (!(this.my_1 === '    ')) {
      var message_1 = 'Indent should not be specified when default printing mode is used';
      throw IllegalArgumentException_init_$Create$(toString(message_1));
    }
  } else if (!(this.my_1 === '    ')) {
    var tmp0 = this.my_1;
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
      var message_2 = 'Only whitespace, tab, newline and carriage return are allowed as pretty print symbols. Had ' + this.my_1;
      throw IllegalArgumentException_init_$Create$(toString(message_2));
    }
  }
  return new JsonConfiguration(this.hy_1, this.jy_1, this.ky_1, this.wy_1, this.ly_1, this.iy_1, this.my_1, this.ny_1, this.xy_1, this.oy_1, this.vy_1, this.qy_1, this.ry_1, this.sy_1, this.ty_1, this.uy_1, this.py_1);
};
function validateConfiguration($this) {
  if (equals($this.fk(), EmptySerializersModule()))
    return Unit_instance;
  var collector = new PolymorphismValidator($this.mx_1.iz_1, $this.mx_1.jz_1);
  $this.fk().xw(collector);
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
  this.az_1 = encodeDefaults;
  this.bz_1 = ignoreUnknownKeys;
  this.cz_1 = isLenient;
  this.dz_1 = allowStructuredMapKeys;
  this.ez_1 = prettyPrint;
  this.fz_1 = explicitNulls;
  this.gz_1 = prettyPrintIndent;
  this.hz_1 = coerceInputValues;
  this.iz_1 = useArrayPolymorphism;
  this.jz_1 = classDiscriminator;
  this.kz_1 = allowSpecialFloatingPointValues;
  this.lz_1 = useAlternativeNames;
  this.mz_1 = namingStrategy;
  this.nz_1 = decodeEnumsCaseInsensitive;
  this.oz_1 = allowTrailingComma;
  this.pz_1 = allowComments;
  this.qz_1 = classDiscriminatorMode;
}
protoOf(JsonConfiguration).toString = function () {
  return 'JsonConfiguration(encodeDefaults=' + this.az_1 + ', ignoreUnknownKeys=' + this.bz_1 + ', isLenient=' + this.cz_1 + ', ' + ('allowStructuredMapKeys=' + this.dz_1 + ', prettyPrint=' + this.ez_1 + ', explicitNulls=' + this.fz_1 + ', ') + ("prettyPrintIndent='" + this.gz_1 + "', coerceInputValues=" + this.hz_1 + ', useArrayPolymorphism=' + this.iz_1 + ', ') + ("classDiscriminator='" + this.jz_1 + "', allowSpecialFloatingPointValues=" + this.kz_1 + ', ') + ('useAlternativeNames=' + this.lz_1 + ', namingStrategy=' + toString_0(this.mz_1) + ', decodeEnumsCaseInsensitive=' + this.nz_1 + ', ') + ('allowTrailingComma=' + this.oz_1 + ', allowComments=' + this.pz_1 + ', classDiscriminatorMode=' + this.qz_1.toString() + ')');
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
  var k = _destruct__k2r9zo.n1();
  // Inline function 'kotlin.collections.component2' call
  var v = _destruct__k2r9zo.o1();
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
  this.tz_1 = content;
}
protoOf(JsonObject).equals = function (other) {
  return equals(this.tz_1, other);
};
protoOf(JsonObject).hashCode = function () {
  return hashCode(this.tz_1);
};
protoOf(JsonObject).toString = function () {
  var tmp = this.tz_1.u1();
  return joinToString(tmp, ',', '{', '}', VOID, VOID, JsonObject$toString$lambda);
};
protoOf(JsonObject).uz = function (key) {
  return this.tz_1.p1(key);
};
protoOf(JsonObject).p1 = function (key) {
  if (!(!(key == null) ? typeof key === 'string' : false))
    return false;
  return this.uz((!(key == null) ? typeof key === 'string' : false) ? key : THROW_CCE());
};
protoOf(JsonObject).ua = function (key) {
  return this.tz_1.r1(key);
};
protoOf(JsonObject).r1 = function (key) {
  if (!(!(key == null) ? typeof key === 'string' : false))
    return null;
  return this.ua((!(key == null) ? typeof key === 'string' : false) ? key : THROW_CCE());
};
protoOf(JsonObject).o = function () {
  return this.tz_1.o();
};
protoOf(JsonObject).u1 = function () {
  return this.tz_1.u1();
};
protoOf(JsonObject).s1 = function () {
  return this.tz_1.s1();
};
protoOf(JsonObject).m = function () {
  return this.tz_1.m();
};
protoOf(JsonObject).t1 = function () {
  return this.tz_1.t1();
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
  this.vz_1 = content;
}
protoOf(JsonArray).equals = function (other) {
  return equals(this.vz_1, other);
};
protoOf(JsonArray).hashCode = function () {
  return hashCode(this.vz_1);
};
protoOf(JsonArray).toString = function () {
  return joinToString(this.vz_1, ',', '[', ']');
};
protoOf(JsonArray).wz = function (element) {
  return this.vz_1.l1(element);
};
protoOf(JsonArray).l1 = function (element) {
  if (!(element instanceof JsonElement))
    return false;
  return this.wz(element instanceof JsonElement ? element : THROW_CCE());
};
protoOf(JsonArray).n = function (index) {
  return this.vz_1.n(index);
};
protoOf(JsonArray).o = function () {
  return this.vz_1.o();
};
protoOf(JsonArray).j = function () {
  return this.vz_1.j();
};
protoOf(JsonArray).p = function (index) {
  return this.vz_1.p(index);
};
protoOf(JsonArray).m = function () {
  return this.vz_1.m();
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
  return this.xz();
};
function get_contentOrNull(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  var tmp;
  if (_this__u8e3s4 instanceof JsonNull) {
    tmp = null;
  } else {
    tmp = _this__u8e3s4.xz();
  }
  return tmp;
}
function JsonNull() {
  JsonNull_instance = this;
  JsonPrimitive.call(this);
  this.yz_1 = 'null';
}
protoOf(JsonNull).xz = function () {
  return this.yz_1;
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
  this.zz_1 = isString;
  this.a10_1 = coerceToInlineType;
  this.b10_1 = toString(body);
  if (!(this.a10_1 == null)) {
    // Inline function 'kotlin.require' call
    // Inline function 'kotlin.require' call
    if (!this.a10_1.th()) {
      var message = 'Failed requirement.';
      throw IllegalArgumentException_init_$Create$(toString(message));
    }
  }
}
protoOf(JsonLiteral).xz = function () {
  return this.b10_1;
};
protoOf(JsonLiteral).toString = function () {
  var tmp;
  if (this.zz_1) {
    // Inline function 'kotlin.text.buildString' call
    // Inline function 'kotlin.apply' call
    var this_0 = StringBuilder_init_$Create$();
    printQuoted(this_0, this.b10_1);
    tmp = this_0.toString();
  } else {
    tmp = this.b10_1;
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
  if (!(this.zz_1 === other.zz_1))
    return false;
  if (!(this.b10_1 === other.b10_1))
    return false;
  return true;
};
protoOf(JsonLiteral).hashCode = function () {
  var result = getBooleanHashCode(this.zz_1);
  result = imul(31, result) + getStringHashCode(this.b10_1) | 0;
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
  return toBooleanStrictOrNull_0(_this__u8e3s4.xz());
}
function get_int(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  // Inline function 'kotlinx.serialization.json.mapExceptions' call
  var tmp;
  try {
    tmp = (new StringJsonLexer(_this__u8e3s4.xz())).c10();
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
    throw NumberFormatException_init_$Create$(_this__u8e3s4.xz() + ' is not an Int');
  return convertToInt(result);
}
function get_long(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  // Inline function 'kotlinx.serialization.json.mapExceptions' call
  var tmp;
  try {
    tmp = (new StringJsonLexer(_this__u8e3s4.xz())).c10();
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
  var this_0 = _this__u8e3s4.xz();
  // Inline function 'kotlin.js.unsafeCast' call
  // Inline function 'kotlin.js.asDynamic' call
  return toDouble(this_0);
}
function get_double(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  return toDouble(_this__u8e3s4.xz());
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
  this.d10_1 = MapSerializer(serializer(StringCompanionObject_instance), JsonElementSerializer_getInstance()).dh();
  this.e10_1 = 'kotlinx.serialization.json.JsonObject';
}
protoOf(JsonObjectDescriptor).rh = function () {
  return this.e10_1;
};
protoOf(JsonObjectDescriptor).wh = function (index) {
  return this.d10_1.wh(index);
};
protoOf(JsonObjectDescriptor).xh = function (name) {
  return this.d10_1.xh(name);
};
protoOf(JsonObjectDescriptor).yh = function (index) {
  return this.d10_1.yh(index);
};
protoOf(JsonObjectDescriptor).zh = function (index) {
  return this.d10_1.zh(index);
};
protoOf(JsonObjectDescriptor).ai = function (index) {
  return this.d10_1.ai(index);
};
protoOf(JsonObjectDescriptor).sh = function () {
  return this.d10_1.sh();
};
protoOf(JsonObjectDescriptor).lh = function () {
  return this.d10_1.lh();
};
protoOf(JsonObjectDescriptor).th = function () {
  return this.d10_1.th();
};
protoOf(JsonObjectDescriptor).uh = function () {
  return this.d10_1.uh();
};
protoOf(JsonObjectDescriptor).vh = function () {
  return this.d10_1.vh();
};
var JsonObjectDescriptor_instance;
function JsonObjectDescriptor_getInstance() {
  if (JsonObjectDescriptor_instance == null)
    new JsonObjectDescriptor();
  return JsonObjectDescriptor_instance;
}
function JsonObjectSerializer() {
  JsonObjectSerializer_instance = this;
  this.f10_1 = JsonObjectDescriptor_getInstance();
}
protoOf(JsonObjectSerializer).dh = function () {
  return this.f10_1;
};
protoOf(JsonObjectSerializer).g10 = function (encoder, value) {
  verify(encoder);
  MapSerializer(serializer(StringCompanionObject_instance), JsonElementSerializer_getInstance()).eh(encoder, value);
};
protoOf(JsonObjectSerializer).eh = function (encoder, value) {
  return this.g10(encoder, value instanceof JsonObject ? value : THROW_CCE());
};
protoOf(JsonObjectSerializer).fh = function (decoder) {
  verify_0(decoder);
  return new JsonObject(MapSerializer(serializer(StringCompanionObject_instance), JsonElementSerializer_getInstance()).fh(decoder));
};
var JsonObjectSerializer_instance;
function JsonObjectSerializer_getInstance() {
  if (JsonObjectSerializer_instance == null)
    new JsonObjectSerializer();
  return JsonObjectSerializer_instance;
}
function JsonElementSerializer$descriptor$lambda($this$buildSerialDescriptor) {
  $this$buildSerialDescriptor.ni('JsonPrimitive', defer(JsonElementSerializer$descriptor$lambda$lambda));
  $this$buildSerialDescriptor.ni('JsonNull', defer(JsonElementSerializer$descriptor$lambda$lambda_0));
  $this$buildSerialDescriptor.ni('JsonLiteral', defer(JsonElementSerializer$descriptor$lambda$lambda_1));
  $this$buildSerialDescriptor.ni('JsonObject', defer(JsonElementSerializer$descriptor$lambda$lambda_2));
  $this$buildSerialDescriptor.ni('JsonArray', defer(JsonElementSerializer$descriptor$lambda$lambda_3));
  return Unit_instance;
}
function JsonElementSerializer$descriptor$lambda$lambda() {
  return JsonPrimitiveSerializer_getInstance().h10_1;
}
function JsonElementSerializer$descriptor$lambda$lambda_0() {
  return JsonNullSerializer_getInstance().i10_1;
}
function JsonElementSerializer$descriptor$lambda$lambda_1() {
  return JsonLiteralSerializer_getInstance().j10_1;
}
function JsonElementSerializer$descriptor$lambda$lambda_2() {
  return JsonObjectSerializer_getInstance().f10_1;
}
function JsonElementSerializer$descriptor$lambda$lambda_3() {
  return JsonArraySerializer_getInstance().k10_1;
}
function JsonElementSerializer() {
  JsonElementSerializer_instance = this;
  var tmp = this;
  var tmp_0 = SEALED_getInstance();
  tmp.l10_1 = buildSerialDescriptor('kotlinx.serialization.json.JsonElement', tmp_0, [], JsonElementSerializer$descriptor$lambda);
}
protoOf(JsonElementSerializer).dh = function () {
  return this.l10_1;
};
protoOf(JsonElementSerializer).m10 = function (encoder, value) {
  verify(encoder);
  if (value instanceof JsonPrimitive) {
    encoder.hl(JsonPrimitiveSerializer_getInstance(), value);
  } else {
    if (value instanceof JsonObject) {
      encoder.hl(JsonObjectSerializer_getInstance(), value);
    } else {
      if (value instanceof JsonArray) {
        encoder.hl(JsonArraySerializer_getInstance(), value);
      } else {
        noWhenBranchMatchedException();
      }
    }
  }
};
protoOf(JsonElementSerializer).eh = function (encoder, value) {
  return this.m10(encoder, value instanceof JsonElement ? value : THROW_CCE());
};
protoOf(JsonElementSerializer).fh = function (decoder) {
  var input = asJsonDecoder(decoder);
  return input.sz();
};
var JsonElementSerializer_instance;
function JsonElementSerializer_getInstance() {
  if (JsonElementSerializer_instance == null)
    new JsonElementSerializer();
  return JsonElementSerializer_instance;
}
function JsonArrayDescriptor() {
  JsonArrayDescriptor_instance = this;
  this.n10_1 = ListSerializer(JsonElementSerializer_getInstance()).dh();
  this.o10_1 = 'kotlinx.serialization.json.JsonArray';
}
protoOf(JsonArrayDescriptor).rh = function () {
  return this.o10_1;
};
protoOf(JsonArrayDescriptor).wh = function (index) {
  return this.n10_1.wh(index);
};
protoOf(JsonArrayDescriptor).xh = function (name) {
  return this.n10_1.xh(name);
};
protoOf(JsonArrayDescriptor).yh = function (index) {
  return this.n10_1.yh(index);
};
protoOf(JsonArrayDescriptor).zh = function (index) {
  return this.n10_1.zh(index);
};
protoOf(JsonArrayDescriptor).ai = function (index) {
  return this.n10_1.ai(index);
};
protoOf(JsonArrayDescriptor).sh = function () {
  return this.n10_1.sh();
};
protoOf(JsonArrayDescriptor).lh = function () {
  return this.n10_1.lh();
};
protoOf(JsonArrayDescriptor).th = function () {
  return this.n10_1.th();
};
protoOf(JsonArrayDescriptor).uh = function () {
  return this.n10_1.uh();
};
protoOf(JsonArrayDescriptor).vh = function () {
  return this.n10_1.vh();
};
var JsonArrayDescriptor_instance;
function JsonArrayDescriptor_getInstance() {
  if (JsonArrayDescriptor_instance == null)
    new JsonArrayDescriptor();
  return JsonArrayDescriptor_instance;
}
function JsonArraySerializer() {
  JsonArraySerializer_instance = this;
  this.k10_1 = JsonArrayDescriptor_getInstance();
}
protoOf(JsonArraySerializer).dh = function () {
  return this.k10_1;
};
protoOf(JsonArraySerializer).p10 = function (encoder, value) {
  verify(encoder);
  ListSerializer(JsonElementSerializer_getInstance()).eh(encoder, value);
};
protoOf(JsonArraySerializer).eh = function (encoder, value) {
  return this.p10(encoder, value instanceof JsonArray ? value : THROW_CCE());
};
protoOf(JsonArraySerializer).fh = function (decoder) {
  verify_0(decoder);
  return new JsonArray(ListSerializer(JsonElementSerializer_getInstance()).fh(decoder));
};
var JsonArraySerializer_instance;
function JsonArraySerializer_getInstance() {
  if (JsonArraySerializer_instance == null)
    new JsonArraySerializer();
  return JsonArraySerializer_instance;
}
function JsonPrimitiveSerializer() {
  JsonPrimitiveSerializer_instance = this;
  this.h10_1 = buildSerialDescriptor('kotlinx.serialization.json.JsonPrimitive', STRING_getInstance(), []);
}
protoOf(JsonPrimitiveSerializer).dh = function () {
  return this.h10_1;
};
protoOf(JsonPrimitiveSerializer).q10 = function (encoder, value) {
  verify(encoder);
  var tmp;
  if (value instanceof JsonNull) {
    encoder.hl(JsonNullSerializer_getInstance(), JsonNull_getInstance());
    tmp = Unit_instance;
  } else {
    var tmp_0 = JsonLiteralSerializer_getInstance();
    encoder.hl(tmp_0, value instanceof JsonLiteral ? value : THROW_CCE());
    tmp = Unit_instance;
  }
  return tmp;
};
protoOf(JsonPrimitiveSerializer).eh = function (encoder, value) {
  return this.q10(encoder, value instanceof JsonPrimitive ? value : THROW_CCE());
};
protoOf(JsonPrimitiveSerializer).fh = function (decoder) {
  var result = asJsonDecoder(decoder).sz();
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
function JsonNullSerializer() {
  JsonNullSerializer_instance = this;
  this.i10_1 = buildSerialDescriptor('kotlinx.serialization.json.JsonNull', ENUM_getInstance(), []);
}
protoOf(JsonNullSerializer).dh = function () {
  return this.i10_1;
};
protoOf(JsonNullSerializer).r10 = function (encoder, value) {
  verify(encoder);
  encoder.lk();
};
protoOf(JsonNullSerializer).eh = function (encoder, value) {
  return this.r10(encoder, value instanceof JsonNull ? value : THROW_CCE());
};
protoOf(JsonNullSerializer).fh = function (decoder) {
  verify_0(decoder);
  if (decoder.cj()) {
    throw new JsonDecodingException("Expected 'null' literal");
  }
  decoder.dj();
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
  this.j10_1 = PrimitiveSerialDescriptor('kotlinx.serialization.json.JsonLiteral', STRING_getInstance());
}
protoOf(JsonLiteralSerializer).dh = function () {
  return this.j10_1;
};
protoOf(JsonLiteralSerializer).s10 = function (encoder, value) {
  verify(encoder);
  if (value.zz_1) {
    return encoder.uk(value.b10_1);
  }
  if (!(value.a10_1 == null)) {
    return encoder.vk(value.a10_1).uk(value.b10_1);
  }
  var tmp0_safe_receiver = toLongOrNull(value.b10_1);
  if (tmp0_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    return encoder.qk(tmp0_safe_receiver);
  }
  var tmp1_safe_receiver = toULongOrNull(value.b10_1);
  var tmp = tmp1_safe_receiver;
  if ((tmp == null ? null : new ULong(tmp)) == null)
    null;
  else {
    var tmp_0 = tmp1_safe_receiver;
    // Inline function 'kotlin.let' call
    var it = (tmp_0 == null ? null : new ULong(tmp_0)).ng_1;
    var tmp_1 = encoder.vk(serializer_0(Companion_getInstance()).dh());
    // Inline function 'kotlin.ULong.toLong' call
    var tmp$ret$4 = _ULong___get_data__impl__fggpzb(it);
    tmp_1.qk(tmp$ret$4);
    return Unit_instance;
  }
  var tmp2_safe_receiver = toDoubleOrNull(value.b10_1);
  if (tmp2_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    return encoder.sk(tmp2_safe_receiver);
  }
  var tmp3_safe_receiver = toBooleanStrictOrNull(value.b10_1);
  if (tmp3_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    return encoder.mk(tmp3_safe_receiver);
  }
  encoder.uk(value.b10_1);
};
protoOf(JsonLiteralSerializer).eh = function (encoder, value) {
  return this.s10(encoder, value instanceof JsonLiteral ? value : THROW_CCE());
};
protoOf(JsonLiteralSerializer).fh = function (decoder) {
  var result = asJsonDecoder(decoder).sz();
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
  var tmp0 = $this.t10_1;
  var tmp = KProperty1;
  // Inline function 'kotlin.getValue' call
  getPropertyCallableRef('original', 1, tmp, defer$o$_get_original_$ref_3cje7k(), null);
  return tmp0.o1();
}
function defer$o$_get_original_$ref_3cje7k() {
  return function (p0) {
    return _get_original__l7ku1m(p0);
  };
}
function defer$1($deferred) {
  this.t10_1 = lazy($deferred);
}
protoOf(defer$1).rh = function () {
  return _get_original__l7ku1m(this).rh();
};
protoOf(defer$1).sh = function () {
  return _get_original__l7ku1m(this).sh();
};
protoOf(defer$1).uh = function () {
  return _get_original__l7ku1m(this).uh();
};
protoOf(defer$1).wh = function (index) {
  return _get_original__l7ku1m(this).wh(index);
};
protoOf(defer$1).xh = function (name) {
  return _get_original__l7ku1m(this).xh(name);
};
protoOf(defer$1).yh = function (index) {
  return _get_original__l7ku1m(this).yh(index);
};
protoOf(defer$1).zh = function (index) {
  return _get_original__l7ku1m(this).zh(index);
};
protoOf(defer$1).ai = function (index) {
  return _get_original__l7ku1m(this).ai(index);
};
function JsonEncoder() {
}
function Composer(writer) {
  this.u10_1 = writer;
  this.v10_1 = true;
}
protoOf(Composer).w10 = function () {
  this.v10_1 = true;
};
protoOf(Composer).x10 = function () {
  return Unit_instance;
};
protoOf(Composer).y10 = function () {
  this.v10_1 = false;
};
protoOf(Composer).z10 = function () {
  this.v10_1 = false;
};
protoOf(Composer).a11 = function () {
  return Unit_instance;
};
protoOf(Composer).b11 = function (v) {
  return this.u10_1.c11(v);
};
protoOf(Composer).d11 = function (v) {
  return this.u10_1.e11(v);
};
protoOf(Composer).f11 = function (v) {
  return this.u10_1.e11(v.toString());
};
protoOf(Composer).g11 = function (v) {
  return this.u10_1.e11(v.toString());
};
protoOf(Composer).h11 = function (v) {
  return this.u10_1.i11(fromInt(v));
};
protoOf(Composer).j11 = function (v) {
  return this.u10_1.i11(fromInt(v));
};
protoOf(Composer).k11 = function (v) {
  return this.u10_1.i11(fromInt(v));
};
protoOf(Composer).l11 = function (v) {
  return this.u10_1.i11(v);
};
protoOf(Composer).m11 = function (v) {
  return this.u10_1.e11(v.toString());
};
protoOf(Composer).n11 = function (value) {
  return this.u10_1.o11(value);
};
function Composer_0(sb, json) {
  return json.mx_1.ez_1 ? new ComposerWithPrettyPrint(sb, json) : new Composer(sb);
}
function ComposerForUnsignedNumbers(writer, forceQuoting) {
  Composer.call(this, writer);
  this.r11_1 = forceQuoting;
}
protoOf(ComposerForUnsignedNumbers).k11 = function (v) {
  if (this.r11_1) {
    // Inline function 'kotlin.toUInt' call
    var tmp$ret$0 = _UInt___init__impl__l7qpdl(v);
    this.n11(UInt__toString_impl_dbgl21(tmp$ret$0));
  } else {
    // Inline function 'kotlin.toUInt' call
    var tmp$ret$1 = _UInt___init__impl__l7qpdl(v);
    this.d11(UInt__toString_impl_dbgl21(tmp$ret$1));
  }
};
protoOf(ComposerForUnsignedNumbers).l11 = function (v) {
  if (this.r11_1) {
    // Inline function 'kotlin.toULong' call
    var tmp$ret$0 = _ULong___init__impl__c78o9k(v);
    this.n11(ULong__toString_impl_f9au7k(tmp$ret$0));
  } else {
    // Inline function 'kotlin.toULong' call
    var tmp$ret$1 = _ULong___init__impl__c78o9k(v);
    this.d11(ULong__toString_impl_f9au7k(tmp$ret$1));
  }
};
protoOf(ComposerForUnsignedNumbers).h11 = function (v) {
  if (this.r11_1) {
    // Inline function 'kotlin.toUByte' call
    var tmp$ret$0 = _UByte___init__impl__g9hnc4(v);
    this.n11(UByte__toString_impl_v72jg(tmp$ret$0));
  } else {
    // Inline function 'kotlin.toUByte' call
    var tmp$ret$1 = _UByte___init__impl__g9hnc4(v);
    this.d11(UByte__toString_impl_v72jg(tmp$ret$1));
  }
};
protoOf(ComposerForUnsignedNumbers).j11 = function (v) {
  if (this.r11_1) {
    // Inline function 'kotlin.toUShort' call
    var tmp$ret$0 = _UShort___init__impl__jigrne(v);
    this.n11(UShort__toString_impl_edaoee(tmp$ret$0));
  } else {
    // Inline function 'kotlin.toUShort' call
    var tmp$ret$1 = _UShort___init__impl__jigrne(v);
    this.d11(UShort__toString_impl_edaoee(tmp$ret$1));
  }
};
function ComposerForUnquotedLiterals(writer, forceQuoting) {
  Composer.call(this, writer);
  this.u11_1 = forceQuoting;
}
protoOf(ComposerForUnquotedLiterals).n11 = function (value) {
  if (this.u11_1) {
    protoOf(Composer).n11.call(this, value);
  } else {
    protoOf(Composer).d11.call(this, value);
  }
};
function ComposerWithPrettyPrint(writer, json) {
  Composer.call(this, writer);
  this.x11_1 = json;
  this.y11_1 = 0;
}
protoOf(ComposerWithPrettyPrint).w10 = function () {
  this.v10_1 = true;
  this.y11_1 = this.y11_1 + 1 | 0;
};
protoOf(ComposerWithPrettyPrint).x10 = function () {
  this.y11_1 = this.y11_1 - 1 | 0;
};
protoOf(ComposerWithPrettyPrint).y10 = function () {
  this.v10_1 = false;
  this.d11('\n');
  // Inline function 'kotlin.repeat' call
  var times = this.y11_1;
  var inductionVariable = 0;
  if (inductionVariable < times)
    do {
      var index = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      this.d11(this.x11_1.mx_1.gz_1);
    }
     while (inductionVariable < times);
};
protoOf(ComposerWithPrettyPrint).z10 = function () {
  if (this.v10_1)
    this.v10_1 = false;
  else {
    this.y10();
  }
};
protoOf(ComposerWithPrettyPrint).a11 = function () {
  this.b11(_Char___init__impl__6a9atx(32));
};
function readIfAbsent($this, descriptor, index) {
  $this.a12_1 = (!descriptor.ai(index) && descriptor.zh(index).lh());
  return $this.a12_1;
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
  tmp.z11_1 = new ElementMarker(descriptor, JsonElementMarker$readIfAbsent$ref(this));
  this.a12_1 = false;
}
protoOf(JsonElementMarker).b12 = function (index) {
  this.z11_1.mo(index);
};
protoOf(JsonElementMarker).c12 = function () {
  return this.z11_1.no();
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
  _this__u8e3s4.d12('Trailing comma before the end of JSON ' + entity, _this__u8e3s4.cy_1 - 1 | 0, "Trailing commas are non-complaint JSON and not allowed by default. Use 'allowTrailingCommas = true' in 'Json {}' builder to support them.");
}
function throwInvalidFloatingPointDecoded(_this__u8e3s4, result) {
  _this__u8e3s4.e12('Unexpected special floating-point value ' + toString(result) + '. By default, ' + 'non-finite floating point values are prohibited because they do not conform JSON specification', VOID, "It is possible to deserialize them using 'JsonBuilder.allowSpecialFloatingPointValues = true'");
}
function JsonEncodingException(message) {
  JsonException.call(this, message);
  captureStack(this, JsonEncodingException);
}
function InvalidKeyKindException(keyDescriptor) {
  return new JsonEncodingException("Value of type '" + keyDescriptor.rh() + "' can't be used in JSON as a key in the map. " + ("It should have either primitive or enum kind, but its kind is '" + keyDescriptor.sh().toString() + "'.\n") + "Use 'allowStructuredMapKeys = true' in 'Json {}' builder to convert such maps to [key1, value1, key2, value2,...] arrays.");
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
  var index = _this__u8e3s4.xh(name);
  if (!(index === -3))
    return index;
  if (!json.mx_1.lz_1)
    return index;
  return getJsonNameIndexSlowPath(_this__u8e3s4, json, name);
}
function getJsonElementName(_this__u8e3s4, json, index) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  var strategy = namingStrategy(_this__u8e3s4, json);
  return strategy == null ? _this__u8e3s4.wh(index) : serializationNamesIndices(_this__u8e3s4, json, strategy)[index];
}
function namingStrategy(_this__u8e3s4, json) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  return equals(_this__u8e3s4.sh(), CLASS_getInstance()) ? json.mx_1.mz_1 : null;
}
function deserializationNamesMap(_this__u8e3s4, descriptor) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  var tmp = get_schemaCache(_this__u8e3s4);
  var tmp_0 = get_JsonDeserializationNamesKey();
  return tmp.g12(descriptor, tmp_0, deserializationNamesMap$lambda(descriptor, _this__u8e3s4));
}
function decodeCaseInsensitive(_this__u8e3s4, descriptor) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  return _this__u8e3s4.mx_1.nz_1 && equals(descriptor.sh(), ENUM_getInstance());
}
function getJsonNameIndexSlowPath(_this__u8e3s4, json, name) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  var tmp0_elvis_lhs = deserializationNamesMap(json, _this__u8e3s4).r1(name);
  return tmp0_elvis_lhs == null ? -3 : tmp0_elvis_lhs;
}
function serializationNamesIndices(_this__u8e3s4, json, strategy) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  var tmp = get_schemaCache(json);
  var tmp_0 = get_JsonSerializationNamesKey();
  return tmp.g12(_this__u8e3s4, tmp_0, serializationNamesIndices$lambda(_this__u8e3s4, strategy));
}
function buildDeserializationNamesMap(_this__u8e3s4, json) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  // Inline function 'kotlin.collections.mutableMapOf' call
  var builder = LinkedHashMap_init_$Create$();
  var useLowercaseEnums = decodeCaseInsensitive(json, _this__u8e3s4);
  var strategyForClasses = namingStrategy(_this__u8e3s4, json);
  var inductionVariable = 0;
  var last = _this__u8e3s4.uh();
  if (inductionVariable < last)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      // Inline function 'kotlin.collections.filterIsInstance' call
      var tmp0 = _this__u8e3s4.yh(i);
      // Inline function 'kotlin.collections.filterIsInstanceTo' call
      var destination = ArrayList_init_$Create$();
      var _iterator__ex2g4s = tmp0.j();
      while (_iterator__ex2g4s.k()) {
        var element = _iterator__ex2g4s.l();
        if (element instanceof JsonNames) {
          destination.h(element);
        }
      }
      var tmp0_safe_receiver = singleOrNull(destination);
      var tmp1_safe_receiver = tmp0_safe_receiver == null ? null : tmp0_safe_receiver.h12_1;
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
        tmp_0 = _this__u8e3s4.wh(i).toLowerCase();
      } else if (!(strategyForClasses == null)) {
        tmp_0 = strategyForClasses.i12(_this__u8e3s4, i, _this__u8e3s4.wh(i));
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
  if (builder.o()) {
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
    var tmp_0 = $this_serializationNamesIndices.uh();
    // Inline function 'kotlin.arrayOfNulls' call
    var tmp_1 = Array(tmp_0);
    while (tmp < tmp_0) {
      var tmp_2 = tmp;
      var baseName = $this_serializationNamesIndices.wh(tmp_2);
      tmp_1[tmp_2] = $strategy.i12($this_serializationNamesIndices, tmp_2, baseName);
      tmp = tmp + 1 | 0;
    }
    return tmp_1;
  };
}
function buildDeserializationNamesMap$putOrThrow(_this__u8e3s4, $this_buildDeserializationNamesMap, name, index) {
  var entity = equals($this_buildDeserializationNamesMap.sh(), ENUM_getInstance()) ? 'enum value' : 'property';
  // Inline function 'kotlin.collections.contains' call
  // Inline function 'kotlin.collections.containsKey' call
  if ((isInterface(_this__u8e3s4, KtMap) ? _this__u8e3s4 : THROW_CCE()).p1(name)) {
    throw new JsonException("The suggested name '" + name + "' for " + entity + ' ' + $this_buildDeserializationNamesMap.wh(index) + ' is already one of the names for ' + entity + ' ' + ($this_buildDeserializationNamesMap.wh(getValue(_this__u8e3s4, name)) + ' in ' + toString($this_buildDeserializationNamesMap)));
  }
  // Inline function 'kotlin.collections.set' call
  _this__u8e3s4.v1(name, index);
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
  var newSize = imul($this.l12_1, 2);
  $this.j12_1 = copyOf($this.j12_1, newSize);
  $this.k12_1 = copyOf_0($this.k12_1, newSize);
}
function JsonPath() {
  var tmp = this;
  // Inline function 'kotlin.arrayOfNulls' call
  tmp.j12_1 = Array(8);
  var tmp_0 = this;
  var tmp_1 = 0;
  var tmp_2 = new Int32Array(8);
  while (tmp_1 < 8) {
    tmp_2[tmp_1] = -1;
    tmp_1 = tmp_1 + 1 | 0;
  }
  tmp_0.k12_1 = tmp_2;
  this.l12_1 = -1;
}
protoOf(JsonPath).m12 = function (sd) {
  this.l12_1 = this.l12_1 + 1 | 0;
  var depth = this.l12_1;
  if (depth === this.j12_1.length) {
    resize(this);
  }
  this.j12_1[depth] = sd;
};
protoOf(JsonPath).n12 = function (index) {
  this.k12_1[this.l12_1] = index;
};
protoOf(JsonPath).o12 = function (key) {
  var tmp;
  if (!(this.k12_1[this.l12_1] === -2)) {
    this.l12_1 = this.l12_1 + 1 | 0;
    tmp = this.l12_1 === this.j12_1.length;
  } else {
    tmp = false;
  }
  if (tmp) {
    resize(this);
  }
  this.j12_1[this.l12_1] = key;
  this.k12_1[this.l12_1] = -2;
};
protoOf(JsonPath).p12 = function () {
  if (this.k12_1[this.l12_1] === -2) {
    this.j12_1[this.l12_1] = Tombstone_instance;
  }
};
protoOf(JsonPath).q12 = function () {
  var depth = this.l12_1;
  if (this.k12_1[depth] === -2) {
    this.k12_1[depth] = -1;
    this.l12_1 = this.l12_1 - 1 | 0;
  }
  if (!(this.l12_1 === -1)) {
    this.l12_1 = this.l12_1 - 1 | 0;
  }
};
protoOf(JsonPath).r12 = function () {
  // Inline function 'kotlin.text.buildString' call
  // Inline function 'kotlin.apply' call
  var this_0 = StringBuilder_init_$Create$();
  this_0.y6('$');
  // Inline function 'kotlin.repeat' call
  var times = this.l12_1 + 1 | 0;
  var inductionVariable = 0;
  if (inductionVariable < times)
    do {
      var index = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      var element = this.j12_1[index];
      if (!(element == null) ? isInterface(element, SerialDescriptor) : false) {
        if (equals(element.sh(), LIST_getInstance())) {
          if (!(this.k12_1[index] === -1)) {
            this_0.y6('[');
            this_0.o9(this.k12_1[index]);
            this_0.y6(']');
          }
        } else {
          var idx = this.k12_1[index];
          if (idx >= 0) {
            this_0.y6('.');
            this_0.y6(element.wh(idx));
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
  return this.r12();
};
function encodeByWriter(json, writer, serializer, value) {
  var tmp = WriteMode_OBJ_getInstance();
  // Inline function 'kotlin.arrayOfNulls' call
  var size = get_entries().m();
  var tmp$ret$0 = Array(size);
  var encoder = StreamingJsonEncoder_init_$Create$(writer, json, tmp, tmp$ret$0);
  encoder.hl(serializer, value);
}
function readObject($this) {
  // Inline function 'kotlinx.serialization.json.internal.JsonTreeReader.readObjectImpl' call
  var lastToken = $this.b13_1.f13(6);
  if ($this.b13_1.g13() === 4) {
    $this.b13_1.e12('Unexpected leading comma');
  }
  // Inline function 'kotlin.collections.linkedMapOf' call
  var result = LinkedHashMap_init_$Create$();
  $l$loop: while ($this.b13_1.h13()) {
    var key = $this.c13_1 ? $this.b13_1.j13() : $this.b13_1.i13();
    $this.b13_1.f13(5);
    var element = $this.k13();
    // Inline function 'kotlin.collections.set' call
    result.v1(key, element);
    lastToken = $this.b13_1.l13();
    var tmp0_subject = lastToken;
    if (tmp0_subject !== 4)
      if (tmp0_subject === 7)
        break $l$loop;
      else {
        $this.b13_1.e12('Expected end of the object or comma');
      }
  }
  if (lastToken === 6) {
    $this.b13_1.f13(7);
  } else if (lastToken === 4) {
    if (!$this.d13_1) {
      invalidTrailingComma($this.b13_1);
    }
    $this.b13_1.f13(7);
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
  var lastToken = $this.b13_1.l13();
  if ($this.b13_1.g13() === 4) {
    $this.b13_1.e12('Unexpected leading comma');
  }
  // Inline function 'kotlin.collections.arrayListOf' call
  var result = ArrayList_init_$Create$();
  while ($this.b13_1.h13()) {
    var element = $this.k13();
    result.h(element);
    lastToken = $this.b13_1.l13();
    if (!(lastToken === 4)) {
      var tmp0 = $this.b13_1;
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.require' call
      var condition = lastToken === 9;
      var position = tmp0.cy_1;
      if (!condition) {
        var tmp$ret$2 = 'Expected end of the array or comma';
        tmp0.e12(tmp$ret$2, position);
      }
    }
  }
  if (lastToken === 8) {
    $this.b13_1.f13(9);
  } else if (lastToken === 4) {
    if (!$this.d13_1) {
      invalidTrailingComma($this.b13_1, 'array');
    }
    $this.b13_1.f13(9);
  }
  return new JsonArray(result);
}
function readValue($this, isString) {
  var tmp;
  if ($this.c13_1 || !isString) {
    tmp = $this.b13_1.j13();
  } else {
    tmp = $this.b13_1.i13();
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
  this.i14_1 = this$0;
  CoroutineImpl.call(this, resultContinuation);
}
protoOf(JsonTreeReader$readDeepRecursive$slambda).m14 = function ($this$DeepRecursiveFunction, it, $completion) {
  var tmp = this.n14($this$DeepRecursiveFunction, it, $completion);
  tmp.m7_1 = Unit_instance;
  tmp.n7_1 = null;
  return tmp.s7();
};
protoOf(JsonTreeReader$readDeepRecursive$slambda).y7 = function (p1, p2, $completion) {
  var tmp = p1 instanceof DeepRecursiveScope ? p1 : THROW_CCE();
  return this.m14(tmp, p2 instanceof Unit ? p2 : THROW_CCE(), $completion);
};
protoOf(JsonTreeReader$readDeepRecursive$slambda).s7 = function () {
  var suspendResult = this.m7_1;
  $sm: do
    try {
      var tmp = this.k7_1;
      switch (tmp) {
        case 0:
          this.l7_1 = 3;
          var tmp0_subject = this.i14_1.b13_1.g13();
          if (tmp0_subject === 1) {
            this.l14_1 = readValue(this.i14_1, true);
            this.k7_1 = 2;
            continue $sm;
          } else {
            if (tmp0_subject === 0) {
              this.l14_1 = readValue(this.i14_1, false);
              this.k7_1 = 2;
              continue $sm;
            } else {
              if (tmp0_subject === 6) {
                this.k7_1 = 1;
                suspendResult = readObject_0(this.i14_1, this.j14_1, this);
                if (suspendResult === get_COROUTINE_SUSPENDED()) {
                  return suspendResult;
                }
                continue $sm;
              } else {
                if (tmp0_subject === 8) {
                  this.l14_1 = readArray(this.i14_1);
                  this.k7_1 = 2;
                  continue $sm;
                } else {
                  var tmp_0 = this;
                  this.i14_1.b13_1.e12("Can't begin reading element, unexpected token");
                }
              }
            }
          }

          break;
        case 1:
          this.l14_1 = suspendResult;
          this.k7_1 = 2;
          continue $sm;
        case 2:
          return this.l14_1;
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
protoOf(JsonTreeReader$readDeepRecursive$slambda).n14 = function ($this$DeepRecursiveFunction, it, completion) {
  var i = new JsonTreeReader$readDeepRecursive$slambda(this.i14_1, completion);
  i.j14_1 = $this$DeepRecursiveFunction;
  i.k14_1 = it;
  return i;
};
function JsonTreeReader$readDeepRecursive$slambda_0(this$0, resultContinuation) {
  var i = new JsonTreeReader$readDeepRecursive$slambda(this$0, resultContinuation);
  var l = function ($this$DeepRecursiveFunction, it, $completion) {
    return i.m14($this$DeepRecursiveFunction, it, $completion);
  };
  l.$arity = 2;
  return l;
}
function $readObjectCOROUTINE$(_this__u8e3s4, _this__u8e3s4_0, resultContinuation) {
  CoroutineImpl.call(this, resultContinuation);
  this.u13_1 = _this__u8e3s4;
  this.v13_1 = _this__u8e3s4_0;
}
protoOf($readObjectCOROUTINE$).s7 = function () {
  var suspendResult = this.m7_1;
  $sm: do
    try {
      var tmp = this.k7_1;
      switch (tmp) {
        case 0:
          this.l7_1 = 5;
          this.x13_1 = this.u13_1;
          this.y13_1 = this.x13_1.b13_1.f13(6);
          if (this.x13_1.b13_1.g13() === 4) {
            this.x13_1.b13_1.e12('Unexpected leading comma');
          }

          var tmp_0 = this;
          tmp_0.w13_1 = LinkedHashMap_init_$Create$();
          this.k7_1 = 1;
          continue $sm;
        case 1:
          if (!this.x13_1.b13_1.h13()) {
            this.k7_1 = 4;
            continue $sm;
          }

          this.z13_1 = this.x13_1.c13_1 ? this.x13_1.b13_1.j13() : this.x13_1.b13_1.i13();
          this.x13_1.b13_1.f13(5);
          this.k7_1 = 2;
          suspendResult = this.v13_1.te(Unit_instance, this);
          if (suspendResult === get_COROUTINE_SUSPENDED()) {
            return suspendResult;
          }

          continue $sm;
        case 2:
          var element = suspendResult;
          var tmp0 = this.w13_1;
          var key = this.z13_1;
          tmp0.v1(key, element);
          this.y13_1 = this.x13_1.b13_1.l13();
          var tmp0_subject = this.y13_1;
          if (tmp0_subject === 4) {
            this.k7_1 = 3;
            continue $sm;
          } else {
            if (tmp0_subject === 7) {
              this.k7_1 = 4;
              continue $sm;
            } else {
              this.x13_1.b13_1.e12('Expected end of the object or comma');
            }
          }

          break;
        case 3:
          this.k7_1 = 1;
          continue $sm;
        case 4:
          if (this.y13_1 === 6) {
            this.x13_1.b13_1.f13(7);
          } else if (this.y13_1 === 4) {
            if (!this.x13_1.d13_1) {
              invalidTrailingComma(this.x13_1.b13_1);
            }
            this.x13_1.b13_1.f13(7);
          }

          return new JsonObject(this.w13_1);
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
  this.b13_1 = lexer;
  this.c13_1 = configuration.cz_1;
  this.d13_1 = configuration.oz_1;
  this.e13_1 = 0;
}
protoOf(JsonTreeReader).k13 = function () {
  var token = this.b13_1.g13();
  var tmp;
  if (token === 1) {
    tmp = readValue(this, true);
  } else if (token === 0) {
    tmp = readValue(this, false);
  } else if (token === 6) {
    var tmp_0;
    this.e13_1 = this.e13_1 + 1 | 0;
    if (this.e13_1 === 200) {
      tmp_0 = readDeepRecursive(this);
    } else {
      tmp_0 = readObject(this);
    }
    var result = tmp_0;
    this.e13_1 = this.e13_1 - 1 | 0;
    tmp = result;
  } else if (token === 8) {
    tmp = readArray(this);
  } else {
    this.b13_1.e12('Cannot read Json element because of unexpected ' + tokenDescription(token));
  }
  return tmp;
};
function classDiscriminator(_this__u8e3s4, json) {
  var _iterator__ex2g4s = _this__u8e3s4.vh().j();
  while (_iterator__ex2g4s.k()) {
    var annotation = _iterator__ex2g4s.l();
    if (annotation instanceof JsonClassDiscriminator)
      return annotation.o14_1;
  }
  return json.mx_1.jz_1;
}
function validateIfSealed(serializer, actualSerializer, classDiscriminator) {
  if (!(serializer instanceof SealedClassSerializer))
    return Unit_instance;
  if (jsonCachedSerialNames(actualSerializer.dh()).l1(classDiscriminator)) {
    var baseName = serializer.dh().rh();
    var actualName = actualSerializer.dh().rh();
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
  var kind = descriptor.sh();
  var tmp;
  if (kind instanceof PolymorphicKind) {
    tmp = true;
  } else {
    tmp = equals(kind, CONTEXTUAL_getInstance());
  }
  if (tmp) {
    throw IllegalArgumentException_init_$Create$('Serializer for ' + actualClass.j8() + " can't be registered as a subclass for polymorphic serialization " + ('because its kind ' + kind.toString() + ' is not concrete. To work with multiple hierarchies, register it as a base class.'));
  }
  if ($this.p14_1)
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
    throw IllegalArgumentException_init_$Create$('Serializer for ' + actualClass.j8() + ' of kind ' + kind.toString() + ' cannot be serialized polymorphically with class discriminator.');
  }
}
function checkDiscriminatorCollisions($this, descriptor, actualClass) {
  var inductionVariable = 0;
  var last = descriptor.uh();
  if (inductionVariable < last)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      var name = descriptor.wh(i);
      if (name === $this.q14_1) {
        throw IllegalArgumentException_init_$Create$('Polymorphic serializer for ' + toString(actualClass) + " has property '" + name + "' that conflicts " + 'with JSON class discriminator. You can either change class discriminator in JsonConfiguration, ' + 'rename property with @SerialName annotation ' + 'or fall back to array polymorphism');
      }
    }
     while (inductionVariable < last);
}
function PolymorphismValidator(useArrayPolymorphism, discriminator) {
  this.p14_1 = useArrayPolymorphism;
  this.q14_1 = discriminator;
}
protoOf(PolymorphismValidator).gx = function (kClass, provider) {
};
protoOf(PolymorphismValidator).jx = function (baseClass, actualClass, actualSerializer) {
  var descriptor = actualSerializer.dh();
  checkKind_0(this, descriptor, actualClass);
  if (!this.p14_1) {
    checkDiscriminatorCollisions(this, descriptor, actualClass);
  }
};
protoOf(PolymorphismValidator).kx = function (baseClass, defaultSerializerProvider) {
};
protoOf(PolymorphismValidator).lx = function (baseClass, defaultDeserializerProvider) {
};
function Key() {
}
function DescriptorSchemaCache() {
  this.f12_1 = createMapForCache(16);
}
protoOf(DescriptorSchemaCache).r14 = function (descriptor, key, value) {
  // Inline function 'kotlin.collections.getOrPut' call
  var this_0 = this.f12_1;
  var value_0 = this_0.r1(descriptor);
  var tmp;
  if (value_0 == null) {
    var answer = createMapForCache(2);
    this_0.v1(descriptor, answer);
    tmp = answer;
  } else {
    tmp = value_0;
  }
  var tmp0 = tmp;
  var tmp2 = key instanceof Key ? key : THROW_CCE();
  // Inline function 'kotlin.collections.set' call
  var value_1 = !(value == null) ? value : THROW_CCE();
  tmp0.v1(tmp2, value_1);
};
protoOf(DescriptorSchemaCache).g12 = function (descriptor, key, defaultValue) {
  var tmp0_safe_receiver = this.s14(descriptor, key);
  if (tmp0_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    return tmp0_safe_receiver;
  }
  var value = defaultValue();
  this.r14(descriptor, key, value);
  return value;
};
protoOf(DescriptorSchemaCache).s14 = function (descriptor, key) {
  var tmp0_safe_receiver = this.f12_1.r1(descriptor);
  var tmp;
  if (tmp0_safe_receiver == null) {
    tmp = null;
  } else {
    tmp = tmp0_safe_receiver.r1(key instanceof Key ? key : THROW_CCE());
  }
  var tmp_0 = tmp;
  return !(tmp_0 == null) ? tmp_0 : null;
};
function DiscriminatorHolder(discriminatorToSkip) {
  this.t14_1 = discriminatorToSkip;
}
function trySkip($this, $receiver, unknownKey) {
  if ($receiver == null)
    return false;
  if ($receiver.t14_1 === unknownKey) {
    $receiver.t14_1 = null;
    return true;
  }
  return false;
}
function skipLeftoverElements($this, descriptor) {
  while (!($this.hk(descriptor) === -1)) {
  }
}
function checkLeadingComma($this) {
  if ($this.wx_1.g13() === 4) {
    $this.wx_1.e12('Unexpected leading comma');
  }
}
function decodeMapIndex($this) {
  var hasComma = false;
  var decodingKey = !(($this.yx_1 % 2 | 0) === 0);
  if (decodingKey) {
    if (!($this.yx_1 === -1)) {
      hasComma = $this.wx_1.v14();
    }
  } else {
    $this.wx_1.u14(_Char___init__impl__6a9atx(58));
  }
  var tmp;
  if ($this.wx_1.h13()) {
    if (decodingKey) {
      if ($this.yx_1 === -1) {
        var tmp0 = $this.wx_1;
        // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.require' call
        var condition = !hasComma;
        var position = tmp0.cy_1;
        if (!condition) {
          var tmp$ret$1 = 'Unexpected leading comma';
          tmp0.e12(tmp$ret$1, position);
        }
      } else {
        var tmp0_0 = $this.wx_1;
        // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.require' call
        var condition_0 = hasComma;
        var position_0 = tmp0_0.cy_1;
        if (!condition_0) {
          var tmp$ret$3 = 'Expected comma after the key-value pair';
          tmp0_0.e12(tmp$ret$3, position_0);
        }
      }
    }
    $this.yx_1 = $this.yx_1 + 1 | 0;
    tmp = $this.yx_1;
  } else {
    if (hasComma && !$this.ux_1.mx_1.oz_1) {
      invalidTrailingComma($this.wx_1);
    }
    tmp = -1;
  }
  return tmp;
}
function coerceInputValue($this, descriptor, index) {
  var tmp0 = $this.ux_1;
  var tmp$ret$0;
  $l$block_2: {
    // Inline function 'kotlinx.serialization.json.internal.tryCoerceValue' call
    var isOptional = descriptor.ai(index);
    var elementDescriptor = descriptor.zh(index);
    var tmp;
    if (isOptional && !elementDescriptor.lh()) {
      tmp = $this.wx_1.w14(true);
    } else {
      tmp = false;
    }
    if (tmp) {
      tmp$ret$0 = true;
      break $l$block_2;
    }
    if (equals(elementDescriptor.sh(), ENUM_getInstance())) {
      var tmp_0;
      if (elementDescriptor.lh()) {
        tmp_0 = $this.wx_1.w14(false);
      } else {
        tmp_0 = false;
      }
      if (tmp_0) {
        tmp$ret$0 = false;
        break $l$block_2;
      }
      var tmp0_elvis_lhs = $this.wx_1.x14($this.ay_1.cz_1);
      var tmp_1;
      if (tmp0_elvis_lhs == null) {
        tmp$ret$0 = false;
        break $l$block_2;
      } else {
        tmp_1 = tmp0_elvis_lhs;
      }
      var enumValue = tmp_1;
      var enumIndex = getJsonNameIndex(elementDescriptor, tmp0, enumValue);
      var coerceToNull = !tmp0.mx_1.fz_1 && elementDescriptor.lh();
      if (enumIndex === -3 && (isOptional || coerceToNull)) {
        $this.wx_1.i13();
        tmp$ret$0 = true;
        break $l$block_2;
      }
    }
    tmp$ret$0 = false;
  }
  return tmp$ret$0;
}
function decodeObjectIndex($this, descriptor) {
  var hasComma = $this.wx_1.v14();
  while ($this.wx_1.h13()) {
    hasComma = false;
    var key = decodeStringKey($this);
    $this.wx_1.u14(_Char___init__impl__6a9atx(58));
    var index = getJsonNameIndex(descriptor, $this.ux_1, key);
    var tmp;
    if (!(index === -3)) {
      var tmp_0;
      if ($this.ay_1.hz_1 && coerceInputValue($this, descriptor, index)) {
        hasComma = $this.wx_1.v14();
        tmp_0 = false;
      } else {
        var tmp0_safe_receiver = $this.by_1;
        if (tmp0_safe_receiver == null)
          null;
        else {
          tmp0_safe_receiver.b12(index);
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
  if (hasComma && !$this.ux_1.mx_1.oz_1) {
    invalidTrailingComma($this.wx_1);
  }
  var tmp1_safe_receiver = $this.by_1;
  var tmp2_elvis_lhs = tmp1_safe_receiver == null ? null : tmp1_safe_receiver.c12();
  return tmp2_elvis_lhs == null ? -1 : tmp2_elvis_lhs;
}
function handleUnknown($this, key) {
  if ($this.ay_1.bz_1 || trySkip($this, $this.zx_1, key)) {
    $this.wx_1.z14($this.ay_1.cz_1);
  } else {
    $this.wx_1.y14(key);
  }
  return $this.wx_1.v14();
}
function decodeListIndex($this) {
  var hasComma = $this.wx_1.v14();
  var tmp;
  if ($this.wx_1.h13()) {
    if (!($this.yx_1 === -1) && !hasComma) {
      $this.wx_1.e12('Expected end of the array or comma');
    }
    $this.yx_1 = $this.yx_1 + 1 | 0;
    tmp = $this.yx_1;
  } else {
    if (hasComma && !$this.ux_1.mx_1.oz_1) {
      invalidTrailingComma($this.wx_1, 'array');
    }
    tmp = -1;
  }
  return tmp;
}
function decodeStringKey($this) {
  var tmp;
  if ($this.ay_1.cz_1) {
    tmp = $this.wx_1.b15();
  } else {
    tmp = $this.wx_1.a15();
  }
  return tmp;
}
function StreamingJsonDecoder(json, mode, lexer, descriptor, discriminatorHolder) {
  AbstractDecoder.call(this);
  this.ux_1 = json;
  this.vx_1 = mode;
  this.wx_1 = lexer;
  this.xx_1 = this.ux_1.fk();
  this.yx_1 = -1;
  this.zx_1 = discriminatorHolder;
  this.ay_1 = this.ux_1.mx_1;
  this.by_1 = this.ay_1.fz_1 ? null : new JsonElementMarker(descriptor);
}
protoOf(StreamingJsonDecoder).rz = function () {
  return this.ux_1;
};
protoOf(StreamingJsonDecoder).fk = function () {
  return this.xx_1;
};
protoOf(StreamingJsonDecoder).sz = function () {
  return (new JsonTreeReader(this.ux_1.mx_1, this.wx_1)).k13();
};
protoOf(StreamingJsonDecoder).pj = function (deserializer) {
  try {
    var tmp;
    if (!(deserializer instanceof AbstractPolymorphicSerializer)) {
      tmp = true;
    } else {
      tmp = this.ux_1.mx_1.iz_1;
    }
    if (tmp) {
      return deserializer.fh(this);
    }
    var discriminator = classDiscriminator(deserializer.dh(), this.ux_1);
    var tmp0_elvis_lhs = this.wx_1.c15(discriminator, this.ay_1.cz_1);
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
          tmp_1 = this.rz().mx_1.iz_1;
        }
        if (tmp_1) {
          tmp$ret$0 = tmp2.fh(this);
          break $l$block;
        }
        var discriminator_0 = classDiscriminator(tmp2.dh(), this.rz());
        var tmp0 = this.sz();
        // Inline function 'kotlinx.serialization.json.internal.cast' call
        var serialName = tmp2.dh().rh();
        if (!(tmp0 instanceof JsonObject)) {
          var tmp_2 = getKClass(JsonObject).j8();
          var tmp_3 = getKClassFromExpression(tmp0).j8();
          var tmp$ret$2 = this.wx_1.dy_1.r12();
          throw JsonDecodingException_0(-1, 'Expected ' + tmp_2 + ', but had ' + tmp_3 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$2, toString(tmp0));
        }
        var jsonTree = tmp0;
        var tmp0_safe_receiver = jsonTree.ua(discriminator_0);
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
        tmp$ret$0 = readPolymorphicJson(this.rz(), discriminator_0, jsonTree, actualSerializer);
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
        this.wx_1.e12(message, VOID, hint);
      } else {
        throw $p;
      }
      tmp_7 = tmp_8;
    }
    var tmp_9 = tmp_7;
    var actualSerializer_0 = isInterface(tmp_9, DeserializationStrategy) ? tmp_9 : THROW_CCE();
    this.zx_1 = new DiscriminatorHolder(discriminator);
    return actualSerializer_0.fh(this);
  } catch ($p) {
    if ($p instanceof MissingFieldException) {
      var e = $p;
      if (contains_0(ensureNotNull(e.message), 'at path'))
        throw e;
      throw new MissingFieldException(e.kh_1, plus(e.message, ' at path: ') + this.wx_1.dy_1.r12(), e);
    } else {
      throw $p;
    }
  }
};
protoOf(StreamingJsonDecoder).qj = function (descriptor) {
  var newMode = switchMode(this.ux_1, descriptor);
  this.wx_1.dy_1.m12(descriptor);
  this.wx_1.u14(newMode.f15_1);
  checkLeadingComma(this);
  var tmp;
  switch (newMode.y1_1) {
    case 1:
    case 2:
    case 3:
      tmp = new StreamingJsonDecoder(this.ux_1, newMode, this.wx_1, descriptor, this.zx_1);
      break;
    default:
      var tmp_0;
      if (this.vx_1.equals(newMode) && this.ux_1.mx_1.fz_1) {
        tmp_0 = this;
      } else {
        tmp_0 = new StreamingJsonDecoder(this.ux_1, newMode, this.wx_1, descriptor, this.zx_1);
      }

      tmp = tmp_0;
      break;
  }
  return tmp;
};
protoOf(StreamingJsonDecoder).rj = function (descriptor) {
  if (this.ux_1.mx_1.bz_1 && descriptor.uh() === 0) {
    skipLeftoverElements(this, descriptor);
  }
  if (this.wx_1.v14() && !this.ux_1.mx_1.oz_1) {
    invalidTrailingComma(this.wx_1, '');
  }
  this.wx_1.u14(this.vx_1.g15_1);
  this.wx_1.dy_1.q12();
};
protoOf(StreamingJsonDecoder).cj = function () {
  var tmp;
  var tmp0_safe_receiver = this.by_1;
  var tmp1_elvis_lhs = tmp0_safe_receiver == null ? null : tmp0_safe_receiver.a12_1;
  if (!(tmp1_elvis_lhs == null ? false : tmp1_elvis_lhs)) {
    tmp = !this.wx_1.h15();
  } else {
    tmp = false;
  }
  return tmp;
};
protoOf(StreamingJsonDecoder).dj = function () {
  return null;
};
protoOf(StreamingJsonDecoder).ck = function (descriptor, index, deserializer, previousValue) {
  var isMapKey = this.vx_1.equals(WriteMode_MAP_getInstance()) && (index & 1) === 0;
  if (isMapKey) {
    this.wx_1.dy_1.p12();
  }
  var value = protoOf(AbstractDecoder).ck.call(this, descriptor, index, deserializer, previousValue);
  if (isMapKey) {
    this.wx_1.dy_1.o12(value);
  }
  return value;
};
protoOf(StreamingJsonDecoder).hk = function (descriptor) {
  var index;
  switch (this.vx_1.y1_1) {
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
  if (!this.vx_1.equals(WriteMode_MAP_getInstance())) {
    this.wx_1.dy_1.n12(index);
  }
  return index;
};
protoOf(StreamingJsonDecoder).ej = function () {
  return this.wx_1.i15();
};
protoOf(StreamingJsonDecoder).fj = function () {
  var value = this.wx_1.c10();
  if (!equalsLong(value, fromInt(convertToByte(value)))) {
    this.wx_1.e12("Failed to parse byte for input '" + value.toString() + "'");
  }
  return convertToByte(value);
};
protoOf(StreamingJsonDecoder).gj = function () {
  var value = this.wx_1.c10();
  if (!equalsLong(value, fromInt(convertToShort(value)))) {
    this.wx_1.e12("Failed to parse short for input '" + value.toString() + "'");
  }
  return convertToShort(value);
};
protoOf(StreamingJsonDecoder).hj = function () {
  var value = this.wx_1.c10();
  if (!equalsLong(value, fromInt(convertToInt(value)))) {
    this.wx_1.e12("Failed to parse int for input '" + value.toString() + "'");
  }
  return convertToInt(value);
};
protoOf(StreamingJsonDecoder).ij = function () {
  return this.wx_1.c10();
};
protoOf(StreamingJsonDecoder).jj = function () {
  var tmp0 = this.wx_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.j13();
    try {
      // Inline function 'kotlin.text.toFloat' call
      // Inline function 'kotlin.js.unsafeCast' call
      // Inline function 'kotlin.js.asDynamic' call
      tmp$ret$0 = toDouble(input);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.e12("Failed to parse type '" + 'float' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  var result = tmp$ret$0;
  var specialFp = this.ux_1.mx_1.kz_1;
  if (specialFp || isFinite(result))
    return result;
  throwInvalidFloatingPointDecoded(this.wx_1, result);
};
protoOf(StreamingJsonDecoder).kj = function () {
  var tmp0 = this.wx_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.j13();
    try {
      tmp$ret$0 = toDouble(input);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.e12("Failed to parse type '" + 'double' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  var result = tmp$ret$0;
  var specialFp = this.ux_1.mx_1.kz_1;
  if (specialFp || isFinite_0(result))
    return result;
  throwInvalidFloatingPointDecoded(this.wx_1, result);
};
protoOf(StreamingJsonDecoder).lj = function () {
  var string = this.wx_1.j13();
  if (!(string.length === 1)) {
    this.wx_1.e12("Expected single char, but got '" + string + "'");
  }
  return charCodeAt(string, 0);
};
protoOf(StreamingJsonDecoder).mj = function () {
  var tmp;
  if (this.ay_1.cz_1) {
    tmp = this.wx_1.b15();
  } else {
    tmp = this.wx_1.i13();
  }
  return tmp;
};
protoOf(StreamingJsonDecoder).nj = function (descriptor) {
  return get_isUnsignedNumber(descriptor) ? new JsonDecoderForUnsignedTypes(this.wx_1, this.ux_1) : protoOf(AbstractDecoder).nj.call(this, descriptor);
};
function JsonDecoderForUnsignedTypes(lexer, json) {
  AbstractDecoder.call(this);
  this.j15_1 = lexer;
  this.k15_1 = json.fk();
}
protoOf(JsonDecoderForUnsignedTypes).fk = function () {
  return this.k15_1;
};
protoOf(JsonDecoderForUnsignedTypes).hk = function (descriptor) {
  // Inline function 'kotlin.error' call
  var message = 'unsupported';
  throw IllegalStateException_init_$Create$(toString(message));
};
protoOf(JsonDecoderForUnsignedTypes).hj = function () {
  var tmp0 = this.j15_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.j13();
    try {
      // Inline function 'kotlin.UInt.toInt' call
      var this_0 = toUInt(input);
      tmp$ret$0 = _UInt___get_data__impl__f0vqqw(this_0);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.e12("Failed to parse type '" + 'UInt' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$0;
};
protoOf(JsonDecoderForUnsignedTypes).ij = function () {
  var tmp0 = this.j15_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.j13();
    try {
      // Inline function 'kotlin.ULong.toLong' call
      var this_0 = toULong(input);
      tmp$ret$0 = _ULong___get_data__impl__fggpzb(this_0);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.e12("Failed to parse type '" + 'ULong' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$0;
};
protoOf(JsonDecoderForUnsignedTypes).fj = function () {
  var tmp0 = this.j15_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.j13();
    try {
      // Inline function 'kotlin.UByte.toByte' call
      var this_0 = toUByte(input);
      tmp$ret$0 = _UByte___get_data__impl__jof9qr(this_0);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.e12("Failed to parse type '" + 'UByte' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$0;
};
protoOf(JsonDecoderForUnsignedTypes).gj = function () {
  var tmp0 = this.j15_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.j13();
    try {
      // Inline function 'kotlin.UShort.toShort' call
      var this_0 = toUShort(input);
      tmp$ret$0 = _UShort___get_data__impl__g0245(this_0);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.e12("Failed to parse type '" + 'UShort' + "' for input '" + input + "'");
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
  $this.s12_1.y10();
  $this.uk(discriminator);
  $this.s12_1.b11(_Char___init__impl__6a9atx(58));
  $this.s12_1.a11();
  $this.uk(serialName);
}
function StreamingJsonEncoder(composer, json, mode, modeReuseCache) {
  AbstractEncoder.call(this);
  this.s12_1 = composer;
  this.t12_1 = json;
  this.u12_1 = mode;
  this.v12_1 = modeReuseCache;
  this.w12_1 = this.t12_1.fk();
  this.x12_1 = this.t12_1.mx_1;
  this.y12_1 = false;
  this.z12_1 = null;
  this.a13_1 = null;
  var i = this.u12_1.y1_1;
  if (!(this.v12_1 == null)) {
    if (!(this.v12_1[i] === null) || !(this.v12_1[i] === this)) {
      this.v12_1[i] = this;
    }
  }
}
protoOf(StreamingJsonEncoder).rz = function () {
  return this.t12_1;
};
protoOf(StreamingJsonEncoder).fk = function () {
  return this.w12_1;
};
protoOf(StreamingJsonEncoder).ml = function (descriptor, index) {
  return this.x12_1.az_1;
};
protoOf(StreamingJsonEncoder).hl = function (serializer, value) {
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.encodePolymorphically' call
    if (this.rz().mx_1.iz_1) {
      serializer.eh(this, value);
      break $l$block;
    }
    var isPolymorphicSerializer = serializer instanceof AbstractPolymorphicSerializer;
    var tmp;
    if (isPolymorphicSerializer) {
      tmp = !this.rz().mx_1.qz_1.equals(ClassDiscriminatorMode_NONE_getInstance());
    } else {
      var tmp_0;
      switch (this.rz().mx_1.qz_1.y1_1) {
        case 0:
        case 2:
          tmp_0 = false;
          break;
        case 1:
          // Inline function 'kotlin.let' call

          var it = serializer.dh().sh();
          tmp_0 = equals(it, CLASS_getInstance()) || equals(it, OBJECT_getInstance());
          break;
        default:
          noWhenBranchMatchedException();
          break;
      }
      tmp = tmp_0;
    }
    var needDiscriminator = tmp;
    var baseClassDiscriminator = needDiscriminator ? classDiscriminator(serializer.dh(), this.rz()) : null;
    var tmp_1;
    if (isPolymorphicSerializer) {
      var casted = serializer instanceof AbstractPolymorphicSerializer ? serializer : THROW_CCE();
      $l$block_0: {
        // Inline function 'kotlin.requireNotNull' call
        if (value == null) {
          var message = 'Value for serializer ' + toString(serializer.dh()) + ' should always be non-null. Please report issue to the kotlinx.serialization tracker.';
          throw IllegalArgumentException_init_$Create$(toString(message));
        } else {
          break $l$block_0;
        }
      }
      var actual = findPolymorphicSerializer_0(casted, this, value);
      if (!(baseClassDiscriminator == null)) {
        access$validateIfSealed$tPolymorphicKt(serializer, actual, baseClassDiscriminator);
      }
      checkKind(actual.dh().sh());
      tmp_1 = isInterface(actual, SerializationStrategy) ? actual : THROW_CCE();
    } else {
      tmp_1 = serializer;
    }
    var actualSerializer = tmp_1;
    if (!(baseClassDiscriminator == null)) {
      var serialName = actualSerializer.dh().rh();
      this.z12_1 = baseClassDiscriminator;
      this.a13_1 = serialName;
    }
    actualSerializer.eh(this, value);
  }
};
protoOf(StreamingJsonEncoder).qj = function (descriptor) {
  var newMode = switchMode(this.t12_1, descriptor);
  if (!(newMode.f15_1 === _Char___init__impl__6a9atx(0))) {
    this.s12_1.b11(newMode.f15_1);
    this.s12_1.w10();
  }
  var discriminator = this.z12_1;
  if (!(discriminator == null)) {
    var tmp0_elvis_lhs = this.a13_1;
    encodeTypeInfo(this, discriminator, tmp0_elvis_lhs == null ? descriptor.rh() : tmp0_elvis_lhs);
    this.z12_1 = null;
    this.a13_1 = null;
  }
  if (this.u12_1.equals(newMode)) {
    return this;
  }
  var tmp1_safe_receiver = this.v12_1;
  var tmp2_elvis_lhs = tmp1_safe_receiver == null ? null : tmp1_safe_receiver[newMode.y1_1];
  return tmp2_elvis_lhs == null ? new StreamingJsonEncoder(this.s12_1, this.t12_1, newMode, this.v12_1) : tmp2_elvis_lhs;
};
protoOf(StreamingJsonEncoder).rj = function (descriptor) {
  if (!(this.u12_1.g15_1 === _Char___init__impl__6a9atx(0))) {
    this.s12_1.x10();
    this.s12_1.z10();
    this.s12_1.b11(this.u12_1.g15_1);
  }
};
protoOf(StreamingJsonEncoder).jk = function (descriptor, index) {
  switch (this.u12_1.y1_1) {
    case 1:
      if (!this.s12_1.v10_1) {
        this.s12_1.b11(_Char___init__impl__6a9atx(44));
      }

      this.s12_1.y10();
      break;
    case 2:
      if (!this.s12_1.v10_1) {
        var tmp = this;
        var tmp_0;
        if ((index % 2 | 0) === 0) {
          this.s12_1.b11(_Char___init__impl__6a9atx(44));
          this.s12_1.y10();
          tmp_0 = true;
        } else {
          this.s12_1.b11(_Char___init__impl__6a9atx(58));
          this.s12_1.a11();
          tmp_0 = false;
        }
        tmp.y12_1 = tmp_0;
      } else {
        this.y12_1 = true;
        this.s12_1.y10();
      }

      break;
    case 3:
      if (index === 0)
        this.y12_1 = true;
      if (index === 1) {
        this.s12_1.b11(_Char___init__impl__6a9atx(44));
        this.s12_1.a11();
        this.y12_1 = false;
      }

      break;
    default:
      if (!this.s12_1.v10_1) {
        this.s12_1.b11(_Char___init__impl__6a9atx(44));
      }

      this.s12_1.y10();
      this.uk(getJsonElementName(descriptor, this.t12_1, index));
      this.s12_1.b11(_Char___init__impl__6a9atx(58));
      this.s12_1.a11();
      break;
  }
  return true;
};
protoOf(StreamingJsonEncoder).il = function (descriptor, index, serializer, value) {
  if (!(value == null) || this.x12_1.fz_1) {
    protoOf(AbstractEncoder).il.call(this, descriptor, index, serializer, value);
  }
};
protoOf(StreamingJsonEncoder).vk = function (descriptor) {
  var tmp;
  if (get_isUnsignedNumber(descriptor)) {
    // Inline function 'kotlinx.serialization.json.internal.StreamingJsonEncoder.composerAs' call
    var tmp_0;
    var tmp_1 = this.s12_1;
    if (tmp_1 instanceof ComposerForUnsignedNumbers) {
      tmp_0 = this.s12_1;
    } else {
      var tmp0 = this.s12_1.u10_1;
      var p1 = this.y12_1;
      tmp_0 = new ComposerForUnsignedNumbers(tmp0, p1);
    }
    var tmp$ret$0 = tmp_0;
    tmp = new StreamingJsonEncoder(tmp$ret$0, this.t12_1, this.u12_1, null);
  } else if (get_isUnquotedLiteral(descriptor)) {
    // Inline function 'kotlinx.serialization.json.internal.StreamingJsonEncoder.composerAs' call
    var tmp_2;
    var tmp_3 = this.s12_1;
    if (tmp_3 instanceof ComposerForUnquotedLiterals) {
      tmp_2 = this.s12_1;
    } else {
      var tmp0_0 = this.s12_1.u10_1;
      var p1_0 = this.y12_1;
      tmp_2 = new ComposerForUnquotedLiterals(tmp0_0, p1_0);
    }
    var tmp$ret$2 = tmp_2;
    tmp = new StreamingJsonEncoder(tmp$ret$2, this.t12_1, this.u12_1, null);
  } else if (!(this.z12_1 == null)) {
    // Inline function 'kotlin.apply' call
    this.a13_1 = descriptor.rh();
    tmp = this;
  } else {
    tmp = protoOf(AbstractEncoder).vk.call(this, descriptor);
  }
  return tmp;
};
protoOf(StreamingJsonEncoder).lk = function () {
  this.s12_1.d11('null');
};
protoOf(StreamingJsonEncoder).mk = function (value) {
  if (this.y12_1) {
    this.uk(value.toString());
  } else {
    this.s12_1.m11(value);
  }
};
protoOf(StreamingJsonEncoder).nk = function (value) {
  if (this.y12_1) {
    this.uk(value.toString());
  } else {
    this.s12_1.h11(value);
  }
};
protoOf(StreamingJsonEncoder).ok = function (value) {
  if (this.y12_1) {
    this.uk(value.toString());
  } else {
    this.s12_1.j11(value);
  }
};
protoOf(StreamingJsonEncoder).pk = function (value) {
  if (this.y12_1) {
    this.uk(value.toString());
  } else {
    this.s12_1.k11(value);
  }
};
protoOf(StreamingJsonEncoder).qk = function (value) {
  if (this.y12_1) {
    this.uk(value.toString());
  } else {
    this.s12_1.l11(value);
  }
};
protoOf(StreamingJsonEncoder).rk = function (value) {
  if (this.y12_1) {
    this.uk(value.toString());
  } else {
    this.s12_1.f11(value);
  }
  if (!this.x12_1.kz_1 && !isFinite(value)) {
    throw InvalidFloatingPointEncoded(value, toString(this.s12_1.u10_1));
  }
};
protoOf(StreamingJsonEncoder).sk = function (value) {
  if (this.y12_1) {
    this.uk(value.toString());
  } else {
    this.s12_1.g11(value);
  }
  if (!this.x12_1.kz_1 && !isFinite_0(value)) {
    throw InvalidFloatingPointEncoded(value, toString(this.s12_1.u10_1));
  }
};
protoOf(StreamingJsonEncoder).tk = function (value) {
  this.uk(toString_1(value));
};
protoOf(StreamingJsonEncoder).uk = function (value) {
  return this.s12_1.n11(value);
};
function get_isUnsignedNumber(_this__u8e3s4) {
  _init_properties_StreamingJsonEncoder_kt__pn1bsi();
  return _this__u8e3s4.th() && get_unsignedNumberDescriptors().l1(_this__u8e3s4);
}
function get_isUnquotedLiteral(_this__u8e3s4) {
  _init_properties_StreamingJsonEncoder_kt__pn1bsi();
  return _this__u8e3s4.th() && equals(_this__u8e3s4, get_jsonUnquotedLiteralDescriptor());
}
var properties_initialized_StreamingJsonEncoder_kt_6ifwwk;
function _init_properties_StreamingJsonEncoder_kt__pn1bsi() {
  if (!properties_initialized_StreamingJsonEncoder_kt_6ifwwk) {
    properties_initialized_StreamingJsonEncoder_kt_6ifwwk = true;
    unsignedNumberDescriptors = setOf([serializer_1(Companion_getInstance_0()).dh(), serializer_0(Companion_getInstance()).dh(), serializer_2(Companion_getInstance_1()).dh(), serializer_3(Companion_getInstance_2()).dh()]);
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
        _this__u8e3s4.m9(value, lastPos, i);
        _this__u8e3s4.y6(get_ESCAPE_STRINGS()[c]);
        lastPos = i + 1 | 0;
      }
    }
     while (inductionVariable <= last);
  if (!(lastPos === 0))
    _this__u8e3s4.m9(value, lastPos, value.length);
  else
    _this__u8e3s4.y6(value);
  _this__u8e3s4.z6(_Char___init__impl__6a9atx(34));
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
  throw JsonDecodingException_0(-1, "Failed to parse literal '" + literal.toString() + "' as " + type + ' value at element: ' + $this.r15(tag), toString($this.s15()));
}
function AbstractJsonTreeDecoder(json, value, polymorphicDiscriminator) {
  polymorphicDiscriminator = polymorphicDiscriminator === VOID ? null : polymorphicDiscriminator;
  NamedValueDecoder.call(this);
  this.n15_1 = json;
  this.o15_1 = value;
  this.p15_1 = polymorphicDiscriminator;
  this.q15_1 = this.rz().mx_1;
}
protoOf(AbstractJsonTreeDecoder).rz = function () {
  return this.n15_1;
};
protoOf(AbstractJsonTreeDecoder).o1 = function () {
  return this.o15_1;
};
protoOf(AbstractJsonTreeDecoder).fk = function () {
  return this.rz().fk();
};
protoOf(AbstractJsonTreeDecoder).s15 = function () {
  var tmp0_safe_receiver = this.tv();
  var tmp;
  if (tmp0_safe_receiver == null) {
    tmp = null;
  } else {
    // Inline function 'kotlin.let' call
    tmp = this.t15(tmp0_safe_receiver);
  }
  var tmp1_elvis_lhs = tmp;
  return tmp1_elvis_lhs == null ? this.o1() : tmp1_elvis_lhs;
};
protoOf(AbstractJsonTreeDecoder).r15 = function (currentTag) {
  return this.vv() + ('.' + currentTag);
};
protoOf(AbstractJsonTreeDecoder).sz = function () {
  return this.s15();
};
protoOf(AbstractJsonTreeDecoder).pj = function (deserializer) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.decodeSerializableValuePolymorphic' call
    var tmp;
    if (!(deserializer instanceof AbstractPolymorphicSerializer)) {
      tmp = true;
    } else {
      tmp = this.rz().mx_1.iz_1;
    }
    if (tmp) {
      tmp$ret$0 = deserializer.fh(this);
      break $l$block;
    }
    var discriminator = classDiscriminator(deserializer.dh(), this.rz());
    var tmp0 = this.sz();
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var serialName = deserializer.dh().rh();
    if (!(tmp0 instanceof JsonObject)) {
      var tmp_0 = getKClass(JsonObject).j8();
      var tmp_1 = getKClassFromExpression(tmp0).j8();
      var tmp$ret$2 = this.vv();
      throw JsonDecodingException_0(-1, 'Expected ' + tmp_0 + ', but had ' + tmp_1 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$2, toString(tmp0));
    }
    var jsonTree = tmp0;
    var tmp0_safe_receiver = jsonTree.ua(discriminator);
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
    tmp$ret$0 = readPolymorphicJson(this.rz(), discriminator, jsonTree, actualSerializer);
  }
  return tmp$ret$0;
};
protoOf(AbstractJsonTreeDecoder).uv = function (parentName, childName) {
  return childName;
};
protoOf(AbstractJsonTreeDecoder).qj = function (descriptor) {
  var currentObject = this.s15();
  var tmp0_subject = descriptor.sh();
  var tmp;
  var tmp_0;
  if (equals(tmp0_subject, LIST_getInstance())) {
    tmp_0 = true;
  } else {
    tmp_0 = tmp0_subject instanceof PolymorphicKind;
  }
  if (tmp_0) {
    var tmp_1 = this.rz();
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var serialName = descriptor.rh();
    if (!(currentObject instanceof JsonArray)) {
      var tmp_2 = getKClass(JsonArray).j8();
      var tmp_3 = getKClassFromExpression(currentObject).j8();
      var tmp$ret$2 = this.vv();
      throw JsonDecodingException_0(-1, 'Expected ' + tmp_2 + ', but had ' + tmp_3 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$2, toString(currentObject));
    }
    tmp = new JsonTreeListDecoder(tmp_1, currentObject);
  } else {
    if (equals(tmp0_subject, MAP_getInstance())) {
      // Inline function 'kotlinx.serialization.json.internal.selectMapMode' call
      var this_0 = this.rz();
      var keyDescriptor = carrierDescriptor(descriptor.zh(0), this_0.fk());
      var keyKind = keyDescriptor.sh();
      var tmp_4;
      var tmp_5;
      if (keyKind instanceof PrimitiveKind) {
        tmp_5 = true;
      } else {
        tmp_5 = equals(keyKind, ENUM_getInstance());
      }
      if (tmp_5) {
        var tmp_6 = this.rz();
        // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
        // Inline function 'kotlinx.serialization.json.internal.cast' call
        var serialName_0 = descriptor.rh();
        if (!(currentObject instanceof JsonObject)) {
          var tmp_7 = getKClass(JsonObject).j8();
          var tmp_8 = getKClassFromExpression(currentObject).j8();
          var tmp$ret$7 = this.vv();
          throw JsonDecodingException_0(-1, 'Expected ' + tmp_7 + ', but had ' + tmp_8 + ' as the serialized body of ' + serialName_0 + ' at element: ' + tmp$ret$7, toString(currentObject));
        }
        tmp_4 = new JsonTreeMapDecoder(tmp_6, currentObject);
      } else {
        if (this_0.mx_1.dz_1) {
          var tmp_9 = this.rz();
          // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
          // Inline function 'kotlinx.serialization.json.internal.cast' call
          var serialName_1 = descriptor.rh();
          if (!(currentObject instanceof JsonArray)) {
            var tmp_10 = getKClass(JsonArray).j8();
            var tmp_11 = getKClassFromExpression(currentObject).j8();
            var tmp$ret$11 = this.vv();
            throw JsonDecodingException_0(-1, 'Expected ' + tmp_10 + ', but had ' + tmp_11 + ' as the serialized body of ' + serialName_1 + ' at element: ' + tmp$ret$11, toString(currentObject));
          }
          tmp_4 = new JsonTreeListDecoder(tmp_9, currentObject);
        } else {
          throw InvalidKeyKindException(keyDescriptor);
        }
      }
      tmp = tmp_4;
    } else {
      var tmp_12 = this.rz();
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
      // Inline function 'kotlinx.serialization.json.internal.cast' call
      var serialName_2 = descriptor.rh();
      if (!(currentObject instanceof JsonObject)) {
        var tmp_13 = getKClass(JsonObject).j8();
        var tmp_14 = getKClassFromExpression(currentObject).j8();
        var tmp$ret$14 = this.vv();
        throw JsonDecodingException_0(-1, 'Expected ' + tmp_13 + ', but had ' + tmp_14 + ' as the serialized body of ' + serialName_2 + ' at element: ' + tmp$ret$14, toString(currentObject));
      }
      tmp = new JsonTreeDecoder(tmp_12, currentObject, this.p15_1);
    }
  }
  return tmp;
};
protoOf(AbstractJsonTreeDecoder).rj = function (descriptor) {
};
protoOf(AbstractJsonTreeDecoder).cj = function () {
  var tmp = this.s15();
  return !(tmp instanceof JsonNull);
};
protoOf(AbstractJsonTreeDecoder).u15 = function (tag) {
  return !(this.t15(tag) === JsonNull_getInstance());
};
protoOf(AbstractJsonTreeDecoder).xv = function (tag) {
  return this.u15((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).v15 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.t15(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).j8();
      var tmp_0 = getKClassFromExpression(value).j8();
      var tmp$ret$3 = this.r15(tag);
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
protoOf(AbstractJsonTreeDecoder).yv = function (tag) {
  return this.v15((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).w15 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.t15(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).j8();
      var tmp_0 = getKClassFromExpression(value).j8();
      var tmp$ret$3 = this.r15(tag);
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
protoOf(AbstractJsonTreeDecoder).zv = function (tag) {
  return this.w15((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).x15 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.t15(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).j8();
      var tmp_0 = getKClassFromExpression(value).j8();
      var tmp$ret$3 = this.r15(tag);
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
protoOf(AbstractJsonTreeDecoder).aw = function (tag) {
  return this.x15((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).y15 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.t15(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).j8();
      var tmp_0 = getKClassFromExpression(value).j8();
      var tmp$ret$3 = this.r15(tag);
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
protoOf(AbstractJsonTreeDecoder).bw = function (tag) {
  return this.y15((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).z15 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.t15(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).j8();
      var tmp_0 = getKClassFromExpression(value).j8();
      var tmp$ret$3 = this.r15(tag);
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
protoOf(AbstractJsonTreeDecoder).cw = function (tag) {
  return this.z15((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).a16 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.t15(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).j8();
      var tmp_0 = getKClassFromExpression(value).j8();
      var tmp$ret$3 = this.r15(tag);
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
  var specialFp = this.rz().mx_1.kz_1;
  if (specialFp || isFinite(result))
    return result;
  throw InvalidFloatingPointDecoded(result, tag, toString(this.s15()));
};
protoOf(AbstractJsonTreeDecoder).dw = function (tag) {
  return this.a16((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).b16 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.t15(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).j8();
      var tmp_0 = getKClassFromExpression(value).j8();
      var tmp$ret$3 = this.r15(tag);
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
  var specialFp = this.rz().mx_1.kz_1;
  if (specialFp || isFinite_0(result))
    return result;
  throw InvalidFloatingPointDecoded(result, tag, toString(this.s15()));
};
protoOf(AbstractJsonTreeDecoder).ew = function (tag) {
  return this.b16((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).c16 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.t15(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).j8();
      var tmp_0 = getKClassFromExpression(value).j8();
      var tmp$ret$3 = this.r15(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'char' + ' at element: ' + tmp$ret$3, toString(value));
    }
    var literal = value;
    try {
      var tmp0_elvis_lhs = new Char(single(literal.xz()));
      var tmp_1;
      if (tmp0_elvis_lhs == null) {
        unparsedPrimitive(this, literal, 'char', tag);
      } else {
        tmp_1 = tmp0_elvis_lhs;
      }
      tmp$ret$0 = tmp_1.z_1;
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
protoOf(AbstractJsonTreeDecoder).fw = function (tag) {
  return this.c16((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).d16 = function (tag) {
  // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
  // Inline function 'kotlinx.serialization.json.internal.cast' call
  var value = this.t15(tag);
  if (!(value instanceof JsonPrimitive)) {
    var tmp = getKClass(JsonPrimitive).j8();
    var tmp_0 = getKClassFromExpression(value).j8();
    var tmp$ret$2 = this.r15(tag);
    throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'string' + ' at element: ' + tmp$ret$2, toString(value));
  }
  var value_0 = value;
  if (!(value_0 instanceof JsonLiteral))
    throw JsonDecodingException_0(-1, "Expected string value for a non-null key '" + tag + "', got null literal instead at element: " + this.r15(tag), toString(this.s15()));
  if (!value_0.zz_1 && !this.rz().mx_1.cz_1) {
    throw JsonDecodingException_0(-1, "String literal for key '" + tag + "' should be quoted at element: " + this.r15(tag) + ".\nUse 'isLenient = true' in 'Json {}' builder to accept non-compliant JSON.", toString(this.s15()));
  }
  return value_0.b10_1;
};
protoOf(AbstractJsonTreeDecoder).gw = function (tag) {
  return this.d16((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).e16 = function (tag, inlineDescriptor) {
  var tmp;
  if (get_isUnsignedNumber(inlineDescriptor)) {
    var tmp_0 = this.rz();
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    var tmp2 = this.t15(tag);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var serialName = inlineDescriptor.rh();
    if (!(tmp2 instanceof JsonPrimitive)) {
      var tmp_1 = getKClass(JsonPrimitive).j8();
      var tmp_2 = getKClassFromExpression(tmp2).j8();
      var tmp$ret$3 = this.r15(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp_1 + ', but had ' + tmp_2 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$3, toString(tmp2));
    }
    var lexer = StringJsonLexer_0(tmp_0, tmp2.xz());
    tmp = new JsonDecoderForUnsignedTypes(lexer, this.rz());
  } else {
    tmp = protoOf(NamedValueDecoder).hw.call(this, tag, inlineDescriptor);
  }
  return tmp;
};
protoOf(AbstractJsonTreeDecoder).hw = function (tag, inlineDescriptor) {
  return this.e16((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE(), inlineDescriptor);
};
protoOf(AbstractJsonTreeDecoder).nj = function (descriptor) {
  return !(this.tv() == null) ? protoOf(NamedValueDecoder).nj.call(this, descriptor) : (new JsonPrimitiveDecoder(this.rz(), this.o1(), this.p15_1)).nj(descriptor);
};
function coerceInputValue_0($this, descriptor, index, tag) {
  var tmp0 = $this.rz();
  var tmp$ret$0;
  $l$block_2: {
    // Inline function 'kotlinx.serialization.json.internal.tryCoerceValue' call
    var isOptional = descriptor.ai(index);
    var elementDescriptor = descriptor.zh(index);
    var tmp;
    if (isOptional && !elementDescriptor.lh()) {
      var tmp_0 = $this.t15(tag);
      tmp = tmp_0 instanceof JsonNull;
    } else {
      tmp = false;
    }
    if (tmp) {
      tmp$ret$0 = true;
      break $l$block_2;
    }
    if (equals(elementDescriptor.sh(), ENUM_getInstance())) {
      var tmp_1;
      if (elementDescriptor.lh()) {
        var tmp_2 = $this.t15(tag);
        tmp_1 = tmp_2 instanceof JsonNull;
      } else {
        tmp_1 = false;
      }
      if (tmp_1) {
        tmp$ret$0 = false;
        break $l$block_2;
      }
      var tmp_3 = $this.t15(tag);
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
      var coerceToNull = !tmp0.mx_1.fz_1 && elementDescriptor.lh();
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
  $this.o16_1 = (!$this.rz().mx_1.fz_1 && !descriptor.ai(index) && descriptor.zh(index).lh());
  return $this.o16_1;
}
function JsonTreeDecoder(json, value, polymorphicDiscriminator, polyDescriptor) {
  polymorphicDiscriminator = polymorphicDiscriminator === VOID ? null : polymorphicDiscriminator;
  polyDescriptor = polyDescriptor === VOID ? null : polyDescriptor;
  AbstractJsonTreeDecoder.call(this, json, value, polymorphicDiscriminator);
  this.l16_1 = value;
  this.m16_1 = polyDescriptor;
  this.n16_1 = 0;
  this.o16_1 = false;
}
protoOf(JsonTreeDecoder).o1 = function () {
  return this.l16_1;
};
protoOf(JsonTreeDecoder).hk = function (descriptor) {
  while (this.n16_1 < descriptor.uh()) {
    var _unary__edvuaz = this.n16_1;
    this.n16_1 = _unary__edvuaz + 1 | 0;
    var name = this.ov(descriptor, _unary__edvuaz);
    var index = this.n16_1 - 1 | 0;
    this.o16_1 = false;
    var tmp;
    var tmp_0;
    // Inline function 'kotlin.collections.contains' call
    // Inline function 'kotlin.collections.containsKey' call
    var this_0 = this.o1();
    if ((isInterface(this_0, KtMap) ? this_0 : THROW_CCE()).p1(name)) {
      tmp_0 = true;
    } else {
      tmp_0 = absenceIsNull(this, descriptor, index);
    }
    if (tmp_0) {
      tmp = !this.q15_1.hz_1 || !coerceInputValue_0(this, descriptor, index, name);
    } else {
      tmp = false;
    }
    if (tmp) {
      return index;
    }
  }
  return -1;
};
protoOf(JsonTreeDecoder).cj = function () {
  return !this.o16_1 && protoOf(AbstractJsonTreeDecoder).cj.call(this);
};
protoOf(JsonTreeDecoder).pv = function (descriptor, index) {
  var strategy = namingStrategy(descriptor, this.rz());
  var baseName = descriptor.wh(index);
  if (strategy == null) {
    if (!this.q15_1.lz_1)
      return baseName;
    if (this.o1().s1().l1(baseName))
      return baseName;
  }
  var deserializationNamesMap_0 = deserializationNamesMap(this.rz(), descriptor);
  // Inline function 'kotlin.collections.find' call
  var tmp0 = this.o1().s1();
  var tmp$ret$1;
  $l$block: {
    // Inline function 'kotlin.collections.firstOrNull' call
    var _iterator__ex2g4s = tmp0.j();
    while (_iterator__ex2g4s.k()) {
      var element = _iterator__ex2g4s.l();
      if (deserializationNamesMap_0.r1(element) === index) {
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
  var fallbackName = strategy == null ? null : strategy.i12(descriptor, index, baseName);
  return fallbackName == null ? baseName : fallbackName;
};
protoOf(JsonTreeDecoder).t15 = function (tag) {
  return getValue(this.o1(), tag);
};
protoOf(JsonTreeDecoder).qj = function (descriptor) {
  if (descriptor === this.m16_1) {
    var tmp = this.rz();
    var tmp2 = this.s15();
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var serialName = this.m16_1.rh();
    if (!(tmp2 instanceof JsonObject)) {
      var tmp_0 = getKClass(JsonObject).j8();
      var tmp_1 = getKClassFromExpression(tmp2).j8();
      var tmp$ret$2 = this.vv();
      throw JsonDecodingException_0(-1, 'Expected ' + tmp_0 + ', but had ' + tmp_1 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$2, toString(tmp2));
    }
    return new JsonTreeDecoder(tmp, tmp2, this.p15_1, this.m16_1);
  }
  return protoOf(AbstractJsonTreeDecoder).qj.call(this, descriptor);
};
protoOf(JsonTreeDecoder).rj = function (descriptor) {
  var tmp;
  if (this.q15_1.bz_1) {
    tmp = true;
  } else {
    var tmp_0 = descriptor.sh();
    tmp = tmp_0 instanceof PolymorphicKind;
  }
  if (tmp)
    return Unit_instance;
  var strategy = namingStrategy(descriptor, this.rz());
  var tmp_1;
  if (strategy == null && !this.q15_1.lz_1) {
    tmp_1 = jsonCachedSerialNames(descriptor);
  } else if (!(strategy == null)) {
    tmp_1 = deserializationNamesMap(this.rz(), descriptor).s1();
  } else {
    var tmp_2 = jsonCachedSerialNames(descriptor);
    var tmp0_safe_receiver = get_schemaCache(this.rz()).s14(descriptor, get_JsonDeserializationNamesKey());
    // Inline function 'kotlin.collections.orEmpty' call
    var tmp0_elvis_lhs = tmp0_safe_receiver == null ? null : tmp0_safe_receiver.s1();
    var tmp$ret$0 = tmp0_elvis_lhs == null ? emptySet() : tmp0_elvis_lhs;
    tmp_1 = plus_0(tmp_2, tmp$ret$0);
  }
  var names = tmp_1;
  var _iterator__ex2g4s = this.o1().s1().j();
  while (_iterator__ex2g4s.k()) {
    var key = _iterator__ex2g4s.l();
    if (!names.l1(key) && !(key === this.p15_1)) {
      throw UnknownKeyException(key, this.o1().toString());
    }
  }
};
function JsonTreeListDecoder(json, value) {
  AbstractJsonTreeDecoder.call(this, json, value);
  this.v16_1 = value;
  this.w16_1 = this.v16_1.m();
  this.x16_1 = -1;
}
protoOf(JsonTreeListDecoder).o1 = function () {
  return this.v16_1;
};
protoOf(JsonTreeListDecoder).pv = function (descriptor, index) {
  return index.toString();
};
protoOf(JsonTreeListDecoder).t15 = function (tag) {
  return this.v16_1.n(toInt(tag));
};
protoOf(JsonTreeListDecoder).hk = function (descriptor) {
  while (this.x16_1 < (this.w16_1 - 1 | 0)) {
    this.x16_1 = this.x16_1 + 1 | 0;
    return this.x16_1;
  }
  return -1;
};
function JsonPrimitiveDecoder(json, value, polymorphicDiscriminator) {
  polymorphicDiscriminator = polymorphicDiscriminator === VOID ? null : polymorphicDiscriminator;
  AbstractJsonTreeDecoder.call(this, json, value, polymorphicDiscriminator);
  this.e17_1 = value;
  this.iw('primitive');
}
protoOf(JsonPrimitiveDecoder).o1 = function () {
  return this.e17_1;
};
protoOf(JsonPrimitiveDecoder).hk = function (descriptor) {
  return 0;
};
protoOf(JsonPrimitiveDecoder).t15 = function (tag) {
  // Inline function 'kotlin.require' call
  if (!(tag === 'primitive')) {
    var message = "This input can only handle primitives with 'primitive' tag";
    throw IllegalArgumentException_init_$Create$(toString(message));
  }
  return this.e17_1;
};
function JsonTreeMapDecoder(json, value) {
  JsonTreeDecoder.call(this, json, value);
  this.p17_1 = value;
  this.q17_1 = toList(this.p17_1.s1());
  this.r17_1 = imul(this.q17_1.m(), 2);
  this.s17_1 = -1;
}
protoOf(JsonTreeMapDecoder).o1 = function () {
  return this.p17_1;
};
protoOf(JsonTreeMapDecoder).pv = function (descriptor, index) {
  var i = index / 2 | 0;
  return this.q17_1.n(i);
};
protoOf(JsonTreeMapDecoder).hk = function (descriptor) {
  while (this.s17_1 < (this.r17_1 - 1 | 0)) {
    this.s17_1 = this.s17_1 + 1 | 0;
    return this.s17_1;
  }
  return -1;
};
protoOf(JsonTreeMapDecoder).t15 = function (tag) {
  return (this.s17_1 % 2 | 0) === 0 ? JsonPrimitive_0(tag) : getValue(this.p17_1, tag);
};
protoOf(JsonTreeMapDecoder).rj = function (descriptor) {
};
function readPolymorphicJson(_this__u8e3s4, discriminator, element, deserializer) {
  return (new JsonTreeDecoder(_this__u8e3s4, element, discriminator, deserializer.dh())).pj(deserializer);
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
  this.f15_1 = begin;
  this.g15_1 = end;
}
function switchMode(_this__u8e3s4, desc) {
  var tmp0_subject = desc.sh();
  var tmp;
  if (tmp0_subject instanceof PolymorphicKind) {
    tmp = WriteMode_POLY_OBJ_getInstance();
  } else {
    if (equals(tmp0_subject, LIST_getInstance())) {
      tmp = WriteMode_LIST_getInstance();
    } else {
      if (equals(tmp0_subject, MAP_getInstance())) {
        // Inline function 'kotlinx.serialization.json.internal.selectMapMode' call
        var keyDescriptor = carrierDescriptor(desc.zh(0), _this__u8e3s4.fk());
        var keyKind = keyDescriptor.sh();
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
          if (_this__u8e3s4.mx_1.dz_1) {
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
  if (equals(_this__u8e3s4.sh(), CONTEXTUAL_getInstance())) {
    var tmp0_safe_receiver = getContextualDescriptor(module_0, _this__u8e3s4);
    var tmp1_elvis_lhs = tmp0_safe_receiver == null ? null : carrierDescriptor(tmp0_safe_receiver, module_0);
    tmp = tmp1_elvis_lhs == null ? _this__u8e3s4 : tmp1_elvis_lhs;
  } else if (_this__u8e3s4.th()) {
    tmp = carrierDescriptor(_this__u8e3s4.zh(0), module_0);
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
  $this.t17(lastPosition, current);
  return appendEsc($this, current + 1 | 0);
}
function decodedString($this, lastPosition, currentPosition) {
  $this.t17(lastPosition, currentPosition);
  var result = $this.fy_1.toString();
  $this.fy_1.r9(0);
  return result;
}
function takePeeked($this) {
  // Inline function 'kotlin.also' call
  var this_0 = ensureNotNull($this.ey_1);
  $this.ey_1 = null;
  return this_0;
}
function wasUnquotedString($this) {
  return !(charSequenceGet($this.u17(), $this.cy_1 - 1 | 0) === _Char___init__impl__6a9atx(34));
}
function appendEsc($this, startPosition) {
  var currentPosition = startPosition;
  currentPosition = $this.v17(currentPosition);
  if (currentPosition === -1) {
    $this.e12('Expected escape sequence to continue, got EOF');
  }
  var tmp = $this.u17();
  var _unary__edvuaz = currentPosition;
  currentPosition = _unary__edvuaz + 1 | 0;
  var currentChar = charSequenceGet(tmp, _unary__edvuaz);
  if (currentChar === _Char___init__impl__6a9atx(117)) {
    return appendHex($this, $this.u17(), currentPosition);
  }
  // Inline function 'kotlin.code' call
  var tmp$ret$0 = Char__toInt_impl_vasixd(currentChar);
  var c = escapeToChar(tmp$ret$0);
  if (c === _Char___init__impl__6a9atx(0)) {
    $this.e12("Invalid escaped char '" + toString_1(currentChar) + "'");
  }
  $this.fy_1.z6(c);
  return currentPosition;
}
function appendHex($this, source, startPos) {
  if ((startPos + 4 | 0) >= charSequenceLength(source)) {
    $this.cy_1 = startPos;
    $this.w17();
    if (($this.cy_1 + 4 | 0) >= charSequenceLength(source)) {
      $this.e12('Unexpected EOF during unicode escape');
    }
    return appendHex($this, source, $this.cy_1);
  }
  $this.fy_1.z6(numberToChar((((fromHexChar($this, source, startPos) << 12) + (fromHexChar($this, source, startPos + 1 | 0) << 8) | 0) + (fromHexChar($this, source, startPos + 2 | 0) << 4) | 0) + fromHexChar($this, source, startPos + 3 | 0) | 0));
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
    $this.e12("Invalid toHexChar char '" + toString_1(character) + "' in unicode escape");
  }
  return tmp;
}
function consumeBoolean2($this, start) {
  var current = $this.v17(start);
  if (current >= charSequenceLength($this.u17()) || current === -1) {
    $this.e12('EOF');
  }
  var tmp = $this.u17();
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
      $this.e12("Expected valid boolean literal prefix, but had '" + $this.j13() + "'");
    }
  }
  return tmp_0;
}
function consumeBooleanLiteral($this, literalSuffix, current) {
  if ((charSequenceLength($this.u17()) - current | 0) < literalSuffix.length) {
    $this.e12('Unexpected end of boolean literal');
  }
  var inductionVariable = 0;
  var last = charSequenceLength(literalSuffix) - 1 | 0;
  if (inductionVariable <= last)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      var expected = charCodeAt(literalSuffix, i);
      var actual = charSequenceGet($this.u17(), current + i | 0);
      // Inline function 'kotlin.code' call
      var tmp = Char__toInt_impl_vasixd(expected);
      // Inline function 'kotlin.code' call
      if (!(tmp === (Char__toInt_impl_vasixd(actual) | 32))) {
        $this.e12("Expected valid boolean literal prefix, but had '" + $this.j13() + "'");
      }
    }
     while (inductionVariable <= last);
  $this.cy_1 = current + literalSuffix.length | 0;
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
  this.cy_1 = 0;
  this.dy_1 = new JsonPath();
  this.ey_1 = null;
  this.fy_1 = StringBuilder_init_$Create$();
}
protoOf(AbstractJsonLexer).w17 = function () {
};
protoOf(AbstractJsonLexer).v14 = function () {
  var current = this.x17();
  var source = this.u17();
  if (current >= charSequenceLength(source) || current === -1)
    return false;
  if (charSequenceGet(source, current) === _Char___init__impl__6a9atx(44)) {
    this.cy_1 = this.cy_1 + 1 | 0;
    return true;
  }
  return false;
};
protoOf(AbstractJsonLexer).y17 = function (c) {
  return c === _Char___init__impl__6a9atx(125) || c === _Char___init__impl__6a9atx(93) || (c === _Char___init__impl__6a9atx(58) || c === _Char___init__impl__6a9atx(44)) ? false : true;
};
protoOf(AbstractJsonLexer).gy = function () {
  var nextToken = this.l13();
  if (!(nextToken === 10)) {
    this.e12('Expected EOF after parsing, but had ' + toString_1(charSequenceGet(this.u17(), this.cy_1 - 1 | 0)) + ' instead');
  }
};
protoOf(AbstractJsonLexer).f13 = function (expected) {
  var token = this.l13();
  if (!(token === expected)) {
    this.z17(expected);
  }
  return token;
};
protoOf(AbstractJsonLexer).a18 = function (expected) {
  if (this.cy_1 > 0 && expected === _Char___init__impl__6a9atx(34)) {
    var tmp$ret$0;
    $l$block: {
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.withPositionRollback' call
      var snapshot = this.cy_1;
      try {
        this.cy_1 = this.cy_1 - 1 | 0;
        tmp$ret$0 = this.j13();
        break $l$block;
      }finally {
        this.cy_1 = snapshot;
      }
    }
    var inputLiteral = tmp$ret$0;
    if (inputLiteral === 'null') {
      this.d12("Expected string literal but 'null' literal was found", this.cy_1 - 1 | 0, "Use 'coerceInputValues = true' in 'Json {}' builder to coerce nulls if property has a default value.");
    }
  }
  this.z17(charToTokenClass(expected));
};
protoOf(AbstractJsonLexer).b18 = function (expectedToken, wasConsumed) {
  var expected = tokenDescription(expectedToken);
  var position = wasConsumed ? this.cy_1 - 1 | 0 : this.cy_1;
  var s = this.cy_1 === charSequenceLength(this.u17()) || position < 0 ? 'EOF' : toString_1(charSequenceGet(this.u17(), position));
  this.e12('Expected ' + expected + ", but had '" + s + "' instead", position);
};
protoOf(AbstractJsonLexer).z17 = function (expectedToken, wasConsumed, $super) {
  wasConsumed = wasConsumed === VOID ? true : wasConsumed;
  return $super === VOID ? this.b18(expectedToken, wasConsumed) : $super.b18.call(this, expectedToken, wasConsumed);
};
protoOf(AbstractJsonLexer).g13 = function () {
  var source = this.u17();
  var cpos = this.cy_1;
  $l$loop_0: while (true) {
    cpos = this.v17(cpos);
    if (cpos === -1)
      break $l$loop_0;
    var ch = charSequenceGet(source, cpos);
    if (ch === _Char___init__impl__6a9atx(32) || ch === _Char___init__impl__6a9atx(10) || ch === _Char___init__impl__6a9atx(13) || ch === _Char___init__impl__6a9atx(9)) {
      cpos = cpos + 1 | 0;
      continue $l$loop_0;
    }
    this.cy_1 = cpos;
    return charToTokenClass(ch);
  }
  this.cy_1 = cpos;
  return 10;
};
protoOf(AbstractJsonLexer).w14 = function (doConsume) {
  var current = this.x17();
  current = this.v17(current);
  var len = charSequenceLength(this.u17()) - current | 0;
  if (len < 4 || current === -1)
    return false;
  var inductionVariable = 0;
  if (inductionVariable <= 3)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      if (!(charCodeAt('null', i) === charSequenceGet(this.u17(), current + i | 0)))
        return false;
    }
     while (inductionVariable <= 3);
  if (len > 4 && charToTokenClass(charSequenceGet(this.u17(), current + 4 | 0)) === 0)
    return false;
  if (doConsume) {
    this.cy_1 = current + 4 | 0;
  }
  return true;
};
protoOf(AbstractJsonLexer).h15 = function (doConsume, $super) {
  doConsume = doConsume === VOID ? true : doConsume;
  return $super === VOID ? this.w14(doConsume) : $super.w14.call(this, doConsume);
};
protoOf(AbstractJsonLexer).x14 = function (isLenient) {
  var token = this.g13();
  var tmp;
  if (isLenient) {
    if (!(token === 1) && !(token === 0))
      return null;
    tmp = this.j13();
  } else {
    if (!(token === 1))
      return null;
    tmp = this.i13();
  }
  var string = tmp;
  this.ey_1 = string;
  return string;
};
protoOf(AbstractJsonLexer).c18 = function () {
  this.ey_1 = null;
};
protoOf(AbstractJsonLexer).d18 = function (startPos, endPos) {
  // Inline function 'kotlin.text.substring' call
  var this_0 = this.u17();
  return toString(charSequenceSubSequence(this_0, startPos, endPos));
};
protoOf(AbstractJsonLexer).i13 = function () {
  if (!(this.ey_1 == null)) {
    return takePeeked(this);
  }
  return this.a15();
};
protoOf(AbstractJsonLexer).consumeString2 = function (source, startPosition, current) {
  var currentPosition = current;
  var lastPosition = startPosition;
  var char = charSequenceGet(source, currentPosition);
  var usedAppend = false;
  while (!(char === _Char___init__impl__6a9atx(34))) {
    if (char === _Char___init__impl__6a9atx(92)) {
      usedAppend = true;
      currentPosition = this.v17(appendEscape(this, lastPosition, currentPosition));
      if (currentPosition === -1) {
        this.e12('Unexpected EOF', currentPosition);
      }
      lastPosition = currentPosition;
    } else {
      currentPosition = currentPosition + 1 | 0;
      if (currentPosition >= charSequenceLength(source)) {
        usedAppend = true;
        this.t17(lastPosition, currentPosition);
        currentPosition = this.v17(currentPosition);
        if (currentPosition === -1) {
          this.e12('Unexpected EOF', currentPosition);
        }
        lastPosition = currentPosition;
      }
    }
    char = charSequenceGet(source, currentPosition);
  }
  var tmp;
  if (!usedAppend) {
    tmp = this.d18(lastPosition, currentPosition);
  } else {
    tmp = decodedString(this, lastPosition, currentPosition);
  }
  var string = tmp;
  this.cy_1 = currentPosition + 1 | 0;
  return string;
};
protoOf(AbstractJsonLexer).b15 = function () {
  var result = this.j13();
  if (result === 'null' && wasUnquotedString(this)) {
    this.e12("Unexpected 'null' value instead of string literal");
  }
  return result;
};
protoOf(AbstractJsonLexer).j13 = function () {
  if (!(this.ey_1 == null)) {
    return takePeeked(this);
  }
  var current = this.x17();
  if (current >= charSequenceLength(this.u17()) || current === -1) {
    this.e12('EOF', current);
  }
  var token = charToTokenClass(charSequenceGet(this.u17(), current));
  if (token === 1) {
    return this.i13();
  }
  if (!(token === 0)) {
    this.e12('Expected beginning of the string, but got ' + toString_1(charSequenceGet(this.u17(), current)));
  }
  var usedAppend = false;
  while (charToTokenClass(charSequenceGet(this.u17(), current)) === 0) {
    current = current + 1 | 0;
    if (current >= charSequenceLength(this.u17())) {
      usedAppend = true;
      this.t17(this.cy_1, current);
      var eof = this.v17(current);
      if (eof === -1) {
        this.cy_1 = current;
        return decodedString(this, 0, 0);
      } else {
        current = eof;
      }
    }
  }
  var tmp;
  if (!usedAppend) {
    tmp = this.d18(this.cy_1, current);
  } else {
    tmp = decodedString(this, this.cy_1, current);
  }
  var result = tmp;
  this.cy_1 = current;
  return result;
};
protoOf(AbstractJsonLexer).t17 = function (fromIndex, toIndex) {
  this.fy_1.m9(this.u17(), fromIndex, toIndex);
};
protoOf(AbstractJsonLexer).z14 = function (allowLenientStrings) {
  // Inline function 'kotlin.collections.mutableListOf' call
  var tokenStack = ArrayList_init_$Create$();
  var lastToken = this.g13();
  if (!(lastToken === 8) && !(lastToken === 6)) {
    this.j13();
    return Unit_instance;
  }
  $l$loop: while (true) {
    lastToken = this.g13();
    if (lastToken === 1) {
      if (allowLenientStrings)
        this.j13();
      else
        this.a15();
      continue $l$loop;
    }
    var tmp0_subject = lastToken;
    if (tmp0_subject === 8 || tmp0_subject === 6) {
      tokenStack.h(lastToken);
    } else if (tmp0_subject === 9) {
      if (!(last(tokenStack) === 8))
        throw JsonDecodingException_0(this.cy_1, 'found ] instead of } at path: ' + this.dy_1.toString(), this.u17());
      removeLast(tokenStack);
    } else if (tmp0_subject === 7) {
      if (!(last(tokenStack) === 6))
        throw JsonDecodingException_0(this.cy_1, 'found } instead of ] at path: ' + this.dy_1.toString(), this.u17());
      removeLast(tokenStack);
    } else if (tmp0_subject === 10) {
      this.e12('Unexpected end of input due to malformed JSON during ignoring unknown keys');
    }
    this.l13();
    if (tokenStack.m() === 0)
      return Unit_instance;
  }
};
protoOf(AbstractJsonLexer).toString = function () {
  return "JsonReader(source='" + toString(this.u17()) + "', currentPosition=" + this.cy_1 + ')';
};
protoOf(AbstractJsonLexer).y14 = function (key) {
  var processed = this.d18(0, this.cy_1);
  var lastIndexOf_0 = lastIndexOf(processed, key);
  this.d12("Encountered an unknown key '" + key + "'", lastIndexOf_0, "Use 'ignoreUnknownKeys = true' in 'Json {}' builder to ignore unknown keys.");
};
protoOf(AbstractJsonLexer).d12 = function (message, position, hint) {
  var tmp;
  // Inline function 'kotlin.text.isEmpty' call
  if (charSequenceLength(hint) === 0) {
    tmp = '';
  } else {
    tmp = '\n' + hint;
  }
  var hintMessage = tmp;
  throw JsonDecodingException_0(position, message + ' at path: ' + this.dy_1.r12() + hintMessage, this.u17());
};
protoOf(AbstractJsonLexer).e12 = function (message, position, hint, $super) {
  position = position === VOID ? this.cy_1 : position;
  hint = hint === VOID ? '' : hint;
  return $super === VOID ? this.d12(message, position, hint) : $super.d12.call(this, message, position, hint);
};
protoOf(AbstractJsonLexer).c10 = function () {
  var current = this.x17();
  current = this.v17(current);
  if (current >= charSequenceLength(this.u17()) || current === -1) {
    this.e12('EOF');
  }
  var tmp;
  if (charSequenceGet(this.u17(), current) === _Char___init__impl__6a9atx(34)) {
    current = current + 1 | 0;
    if (current === charSequenceLength(this.u17())) {
      this.e12('EOF');
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
  $l$loop_4: while (!(current === charSequenceLength(this.u17()))) {
    var ch = charSequenceGet(this.u17(), current);
    if ((ch === _Char___init__impl__6a9atx(101) || ch === _Char___init__impl__6a9atx(69)) && !hasExponent) {
      if (current === start) {
        this.e12('Unexpected symbol ' + toString_1(ch) + ' in numeric literal');
      }
      isExponentPositive = true;
      hasExponent = true;
      current = current + 1 | 0;
      continue $l$loop_4;
    }
    if (ch === _Char___init__impl__6a9atx(45) && hasExponent) {
      if (current === start) {
        this.e12("Unexpected symbol '-' in numeric literal");
      }
      isExponentPositive = false;
      current = current + 1 | 0;
      continue $l$loop_4;
    }
    if (ch === _Char___init__impl__6a9atx(43) && hasExponent) {
      if (current === start) {
        this.e12("Unexpected symbol '+' in numeric literal");
      }
      isExponentPositive = true;
      current = current + 1 | 0;
      continue $l$loop_4;
    }
    if (ch === _Char___init__impl__6a9atx(45)) {
      if (!(current === start)) {
        this.e12("Unexpected symbol '-' in numeric literal");
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
      this.e12("Unexpected symbol '" + toString_1(ch) + "' in numeric literal");
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
      this.e12('Numeric value overflow');
    }
  }
  var hasChars = !(current === start);
  if (start === current || (isNegative && start === (current - 1 | 0))) {
    this.e12('Expected numeric literal');
  }
  if (hasQuotation) {
    if (!hasChars) {
      this.e12('EOF');
    }
    if (!(charSequenceGet(this.u17(), current) === _Char___init__impl__6a9atx(34))) {
      this.e12('Expected closing quotation mark');
    }
    current = current + 1 | 0;
  }
  this.cy_1 = current;
  if (hasExponent) {
    var doubleAccumulator = toNumber(accumulator) * consumeNumericLiteral$calculateExponent(exponentAccumulator, isExponentPositive);
    if (doubleAccumulator > toNumber(new Long(-1, 2147483647)) || doubleAccumulator < toNumber(new Long(0, -2147483648))) {
      this.e12('Numeric value overflow');
    }
    // Inline function 'kotlin.math.floor' call
    if (!(Math.floor(doubleAccumulator) === doubleAccumulator)) {
      this.e12("Can't convert " + doubleAccumulator + ' to Long');
    }
    accumulator = numberToLong(doubleAccumulator);
  }
  var tmp_0;
  if (isNegative) {
    tmp_0 = accumulator;
  } else if (!equalsLong(accumulator, new Long(0, -2147483648))) {
    tmp_0 = negate(accumulator);
  } else {
    this.e12('Numeric value overflow');
  }
  return tmp_0;
};
protoOf(AbstractJsonLexer).i15 = function () {
  var current = this.x17();
  if (current === charSequenceLength(this.u17())) {
    this.e12('EOF');
  }
  var tmp;
  if (charSequenceGet(this.u17(), current) === _Char___init__impl__6a9atx(34)) {
    current = current + 1 | 0;
    tmp = true;
  } else {
    tmp = false;
  }
  var hasQuotation = tmp;
  var result = consumeBoolean2(this, current);
  if (hasQuotation) {
    if (this.cy_1 === charSequenceLength(this.u17())) {
      this.e12('EOF');
    }
    if (!(charSequenceGet(this.u17(), this.cy_1) === _Char___init__impl__6a9atx(34))) {
      this.e12('Expected closing quotation mark');
    }
    this.cy_1 = this.cy_1 + 1 | 0;
  }
  return result;
};
function charToTokenClass(c) {
  var tmp;
  // Inline function 'kotlin.code' call
  if (Char__toInt_impl_vasixd(c) < 126) {
    var tmp_0 = CharMappings_getInstance().f18_1;
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
  return c < 117 ? CharMappings_getInstance().e18_1[c] : _Char___init__impl__6a9atx(0);
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
    $this.e18_1[tmp$ret$0] = numberToChar(c);
  }
}
function initC2ESC_0($this, c, esc) {
  // Inline function 'kotlin.code' call
  var tmp$ret$0 = Char__toInt_impl_vasixd(c);
  return initC2ESC($this, tmp$ret$0, esc);
}
function initC2TC($this, c, cl) {
  $this.f18_1[c] = cl;
}
function initC2TC_0($this, c, cl) {
  // Inline function 'kotlin.code' call
  var tmp$ret$0 = Char__toInt_impl_vasixd(c);
  return initC2TC($this, tmp$ret$0, cl);
}
function CharMappings() {
  CharMappings_instance = this;
  this.e18_1 = charArray(117);
  this.f18_1 = new Int8Array(126);
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
protoOf(StringJsonLexerWithComments).l13 = function () {
  var source = this.u17();
  var cpos = this.x17();
  if (cpos >= source.length || cpos === -1)
    return 10;
  this.cy_1 = cpos + 1 | 0;
  return charToTokenClass(charCodeAt(source, cpos));
};
protoOf(StringJsonLexerWithComments).h13 = function () {
  var current = this.x17();
  if (current >= this.u17().length || current === -1)
    return false;
  return this.y17(charCodeAt(this.u17(), current));
};
protoOf(StringJsonLexerWithComments).u14 = function (expected) {
  var source = this.u17();
  var current = this.x17();
  if (current >= source.length || current === -1) {
    this.cy_1 = -1;
    this.a18(expected);
  }
  var c = charCodeAt(source, current);
  this.cy_1 = current + 1 | 0;
  if (c === expected)
    return Unit_instance;
  else {
    this.a18(expected);
  }
};
protoOf(StringJsonLexerWithComments).g13 = function () {
  var source = this.u17();
  var cpos = this.x17();
  if (cpos >= source.length || cpos === -1)
    return 10;
  this.cy_1 = cpos;
  return charToTokenClass(charCodeAt(source, cpos));
};
protoOf(StringJsonLexerWithComments).x17 = function () {
  var current = this.cy_1;
  if (current === -1)
    return current;
  var source = this.u17();
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
          this.cy_1 = source.length;
          this.e12('Expected end of the block comment: "*/", but had EOF instead');
        } else {
          current = current + 2 | 0;
        }
        continue $l$loop_1;
      }
    }
    break $l$loop_1;
  }
  this.cy_1 = current;
  return current;
};
function StringJsonLexer(source) {
  AbstractJsonLexer.call(this);
  this.p18_1 = source;
}
protoOf(StringJsonLexer).u17 = function () {
  return this.p18_1;
};
protoOf(StringJsonLexer).v17 = function (position) {
  return position < this.u17().length ? position : -1;
};
protoOf(StringJsonLexer).l13 = function () {
  var source = this.u17();
  var cpos = this.cy_1;
  $l$loop: while (!(cpos === -1) && cpos < source.length) {
    var _unary__edvuaz = cpos;
    cpos = _unary__edvuaz + 1 | 0;
    var c = charCodeAt(source, _unary__edvuaz);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.isWs' call
    if (c === _Char___init__impl__6a9atx(32) || c === _Char___init__impl__6a9atx(10) || c === _Char___init__impl__6a9atx(13) || c === _Char___init__impl__6a9atx(9))
      continue $l$loop;
    this.cy_1 = cpos;
    return charToTokenClass(c);
  }
  this.cy_1 = source.length;
  return 10;
};
protoOf(StringJsonLexer).h13 = function () {
  var current = this.cy_1;
  if (current === -1)
    return false;
  var source = this.u17();
  $l$loop: while (current < source.length) {
    var c = charCodeAt(source, current);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.isWs' call
    if (c === _Char___init__impl__6a9atx(32) || c === _Char___init__impl__6a9atx(10) || c === _Char___init__impl__6a9atx(13) || c === _Char___init__impl__6a9atx(9)) {
      current = current + 1 | 0;
      continue $l$loop;
    }
    this.cy_1 = current;
    return this.y17(c);
  }
  this.cy_1 = current;
  return false;
};
protoOf(StringJsonLexer).x17 = function () {
  var current = this.cy_1;
  if (current === -1)
    return current;
  var source = this.u17();
  $l$loop: while (current < source.length) {
    var c = charCodeAt(source, current);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.isWs' call
    if (c === _Char___init__impl__6a9atx(32) || c === _Char___init__impl__6a9atx(10) || c === _Char___init__impl__6a9atx(13) || c === _Char___init__impl__6a9atx(9)) {
      current = current + 1 | 0;
    } else {
      break $l$loop;
    }
  }
  this.cy_1 = current;
  return current;
};
protoOf(StringJsonLexer).u14 = function (expected) {
  if (this.cy_1 === -1) {
    this.a18(expected);
  }
  var source = this.u17();
  var cpos = this.cy_1;
  $l$loop: while (cpos < source.length) {
    var _unary__edvuaz = cpos;
    cpos = _unary__edvuaz + 1 | 0;
    var c = charCodeAt(source, _unary__edvuaz);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.isWs' call
    if (c === _Char___init__impl__6a9atx(32) || c === _Char___init__impl__6a9atx(10) || c === _Char___init__impl__6a9atx(13) || c === _Char___init__impl__6a9atx(9))
      continue $l$loop;
    this.cy_1 = cpos;
    if (c === expected)
      return Unit_instance;
    this.a18(expected);
  }
  this.cy_1 = -1;
  this.a18(expected);
};
protoOf(StringJsonLexer).a15 = function () {
  this.u14(_Char___init__impl__6a9atx(34));
  var current = this.cy_1;
  var closingQuote = indexOf_0(this.u17(), _Char___init__impl__6a9atx(34), current);
  if (closingQuote === -1) {
    this.j13();
    this.b18(1, false);
  }
  var inductionVariable = current;
  if (inductionVariable < closingQuote)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      if (charCodeAt(this.u17(), i) === _Char___init__impl__6a9atx(92)) {
        return this.consumeString2(this.u17(), this.cy_1, i);
      }
    }
     while (inductionVariable < closingQuote);
  this.cy_1 = closingQuote + 1 | 0;
  return substring(this.u17(), current, closingQuote);
};
protoOf(StringJsonLexer).c15 = function (keyToMatch, isLenient) {
  var positionSnapshot = this.cy_1;
  try {
    if (!(this.l13() === 6))
      return null;
    var firstKey = this.x14(isLenient);
    if (!(firstKey === keyToMatch))
      return null;
    this.c18();
    if (!(this.l13() === 5))
      return null;
    return this.x14(isLenient);
  }finally {
    this.cy_1 = positionSnapshot;
    this.c18();
  }
};
function StringJsonLexer_0(json, source) {
  return !json.mx_1.pz_1 ? new StringJsonLexer(source) : new StringJsonLexerWithComments(source);
}
function get_schemaCache(_this__u8e3s4) {
  return _this__u8e3s4.ox_1;
}
function JsonToStringWriter() {
  this.sx_1 = StringBuilder_init_$Create$_0(128);
}
protoOf(JsonToStringWriter).i11 = function (value) {
  this.sx_1.p9(value);
};
protoOf(JsonToStringWriter).c11 = function (char) {
  this.sx_1.z6(char);
};
protoOf(JsonToStringWriter).e11 = function (text) {
  this.sx_1.y6(text);
};
protoOf(JsonToStringWriter).o11 = function (text) {
  printQuoted(this.sx_1, text);
};
protoOf(JsonToStringWriter).tx = function () {
  this.sx_1.s9();
};
protoOf(JsonToStringWriter).toString = function () {
  return this.sx_1.toString();
};
function createMapForCache(initialCapacity) {
  return HashMap_init_$Create$(initialCapacity);
}
//region block: post-declaration
protoOf(defer$1).lh = get_isNullable;
protoOf(defer$1).th = get_isInline;
protoOf(defer$1).vh = get_annotations;
protoOf(PolymorphismValidator).ix = contextual;
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


