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
  this.ww_1 = configuration;
  this.xw_1 = serializersModule;
  this.yw_1 = new DescriptorSchemaCache();
}
protoOf(Json).pj = function () {
  return this.xw_1;
};
protoOf(Json).zw = function (serializer, value) {
  var result = new JsonToStringWriter();
  try {
    encodeByWriter(this, result, serializer, value);
    return result.toString();
  }finally {
    result.dx();
  }
};
protoOf(Json).ax = function (deserializer, string) {
  var lexer = StringJsonLexer_0(this, string);
  var input = new StreamingJsonDecoder(this, WriteMode_OBJ_getInstance(), lexer, deserializer.ng(), null);
  var result = input.zi(deserializer);
  lexer.qx();
  return result;
};
protoOf(Json).bx = function (string) {
  return this.ax(JsonElementSerializer_getInstance(), string);
};
function Json_0(from, builderAction) {
  from = from === VOID ? Default_getInstance() : from;
  var builder = new JsonBuilder(from);
  builderAction(builder);
  var conf = builder.jy();
  return new JsonImpl(conf, builder.iy_1);
}
function JsonBuilder(json) {
  this.rx_1 = json.ww_1.ky_1;
  this.sx_1 = json.ww_1.py_1;
  this.tx_1 = json.ww_1.ly_1;
  this.ux_1 = json.ww_1.my_1;
  this.vx_1 = json.ww_1.oy_1;
  this.wx_1 = json.ww_1.qy_1;
  this.xx_1 = json.ww_1.ry_1;
  this.yx_1 = json.ww_1.ty_1;
  this.zx_1 = json.ww_1.az_1;
  this.ay_1 = json.ww_1.vy_1;
  this.by_1 = json.ww_1.wy_1;
  this.cy_1 = json.ww_1.xy_1;
  this.dy_1 = json.ww_1.yy_1;
  this.ey_1 = json.ww_1.zy_1;
  this.fy_1 = json.ww_1.uy_1;
  this.gy_1 = json.ww_1.ny_1;
  this.hy_1 = json.ww_1.sy_1;
  this.iy_1 = json.pj();
}
protoOf(JsonBuilder).jy = function () {
  if (this.hy_1) {
    // Inline function 'kotlin.require' call
    if (!(this.yx_1 === 'type')) {
      var message = 'Class discriminator should not be specified when array polymorphism is specified';
      throw IllegalArgumentException_init_$Create$(toString(message));
    }
    // Inline function 'kotlin.require' call
    if (!this.zx_1.equals(ClassDiscriminatorMode_POLYMORPHIC_getInstance())) {
      var message_0 = 'useArrayPolymorphism option can only be used if classDiscriminatorMode in a default POLYMORPHIC state.';
      throw IllegalArgumentException_init_$Create$(toString(message_0));
    }
  }
  if (!this.vx_1) {
    // Inline function 'kotlin.require' call
    if (!(this.wx_1 === '    ')) {
      var message_1 = 'Indent should not be specified when default printing mode is used';
      throw IllegalArgumentException_init_$Create$(toString(message_1));
    }
  } else if (!(this.wx_1 === '    ')) {
    var tmp0 = this.wx_1;
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
      var message_2 = 'Only whitespace, tab, newline and carriage return are allowed as pretty print symbols. Had ' + this.wx_1;
      throw IllegalArgumentException_init_$Create$(toString(message_2));
    }
  }
  return new JsonConfiguration(this.rx_1, this.tx_1, this.ux_1, this.gy_1, this.vx_1, this.sx_1, this.wx_1, this.xx_1, this.hy_1, this.yx_1, this.fy_1, this.ay_1, this.by_1, this.cy_1, this.dy_1, this.ey_1, this.zx_1);
};
function validateConfiguration($this) {
  if (equals($this.pj(), EmptySerializersModule()))
    return Unit_instance;
  var collector = new PolymorphismValidator($this.ww_1.sy_1, $this.ww_1.ty_1);
  $this.pj().hw(collector);
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
  this.ky_1 = encodeDefaults;
  this.ly_1 = ignoreUnknownKeys;
  this.my_1 = isLenient;
  this.ny_1 = allowStructuredMapKeys;
  this.oy_1 = prettyPrint;
  this.py_1 = explicitNulls;
  this.qy_1 = prettyPrintIndent;
  this.ry_1 = coerceInputValues;
  this.sy_1 = useArrayPolymorphism;
  this.ty_1 = classDiscriminator;
  this.uy_1 = allowSpecialFloatingPointValues;
  this.vy_1 = useAlternativeNames;
  this.wy_1 = namingStrategy;
  this.xy_1 = decodeEnumsCaseInsensitive;
  this.yy_1 = allowTrailingComma;
  this.zy_1 = allowComments;
  this.az_1 = classDiscriminatorMode;
}
protoOf(JsonConfiguration).toString = function () {
  return 'JsonConfiguration(encodeDefaults=' + this.ky_1 + ', ignoreUnknownKeys=' + this.ly_1 + ', isLenient=' + this.my_1 + ', ' + ('allowStructuredMapKeys=' + this.ny_1 + ', prettyPrint=' + this.oy_1 + ', explicitNulls=' + this.py_1 + ', ') + ("prettyPrintIndent='" + this.qy_1 + "', coerceInputValues=" + this.ry_1 + ', useArrayPolymorphism=' + this.sy_1 + ', ') + ("classDiscriminator='" + this.ty_1 + "', allowSpecialFloatingPointValues=" + this.uy_1 + ', ') + ('useAlternativeNames=' + this.vy_1 + ', namingStrategy=' + toString_0(this.wy_1) + ', decodeEnumsCaseInsensitive=' + this.xy_1 + ', ') + ('allowTrailingComma=' + this.yy_1 + ', allowComments=' + this.zy_1 + ', classDiscriminatorMode=' + this.az_1.toString() + ')');
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
  var k = _destruct__k2r9zo.m1();
  // Inline function 'kotlin.collections.component2' call
  var v = _destruct__k2r9zo.n1();
  // Inline function 'kotlin.text.buildString' call
  // Inline function 'kotlin.apply' call
  var this_0 = StringBuilder_init_$Create$();
  printQuoted(this_0, k);
  this_0.y6(_Char___init__impl__6a9atx(58));
  this_0.w6(v);
  return this_0.toString();
}
function JsonObject(content) {
  JsonElement.call(this);
  this.dz_1 = content;
}
protoOf(JsonObject).equals = function (other) {
  return equals(this.dz_1, other);
};
protoOf(JsonObject).hashCode = function () {
  return hashCode(this.dz_1);
};
protoOf(JsonObject).toString = function () {
  var tmp = this.dz_1.t1();
  return joinToString(tmp, ',', '{', '}', VOID, VOID, JsonObject$toString$lambda);
};
protoOf(JsonObject).ez = function (key) {
  return this.dz_1.o1(key);
};
protoOf(JsonObject).o1 = function (key) {
  if (!(!(key == null) ? typeof key === 'string' : false))
    return false;
  return this.ez((!(key == null) ? typeof key === 'string' : false) ? key : THROW_CCE());
};
protoOf(JsonObject).na = function (key) {
  return this.dz_1.q1(key);
};
protoOf(JsonObject).q1 = function (key) {
  if (!(!(key == null) ? typeof key === 'string' : false))
    return null;
  return this.na((!(key == null) ? typeof key === 'string' : false) ? key : THROW_CCE());
};
protoOf(JsonObject).l = function () {
  return this.dz_1.l();
};
protoOf(JsonObject).t1 = function () {
  return this.dz_1.t1();
};
protoOf(JsonObject).r1 = function () {
  return this.dz_1.r1();
};
protoOf(JsonObject).j = function () {
  return this.dz_1.j();
};
protoOf(JsonObject).s1 = function () {
  return this.dz_1.s1();
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
  this.fz_1 = content;
}
protoOf(JsonArray).equals = function (other) {
  return equals(this.fz_1, other);
};
protoOf(JsonArray).hashCode = function () {
  return hashCode(this.fz_1);
};
protoOf(JsonArray).toString = function () {
  return joinToString(this.fz_1, ',', '[', ']');
};
protoOf(JsonArray).gz = function (element) {
  return this.fz_1.w(element);
};
protoOf(JsonArray).w = function (element) {
  if (!(element instanceof JsonElement))
    return false;
  return this.gz(element instanceof JsonElement ? element : THROW_CCE());
};
protoOf(JsonArray).k = function (index) {
  return this.fz_1.k(index);
};
protoOf(JsonArray).l = function () {
  return this.fz_1.l();
};
protoOf(JsonArray).g = function () {
  return this.fz_1.g();
};
protoOf(JsonArray).j = function () {
  return this.fz_1.j();
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
  return this.hz();
};
function get_contentOrNull(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  var tmp;
  if (_this__u8e3s4 instanceof JsonNull) {
    tmp = null;
  } else {
    tmp = _this__u8e3s4.hz();
  }
  return tmp;
}
function JsonNull() {
  JsonNull_instance = this;
  JsonPrimitive.call(this);
  this.iz_1 = 'null';
}
protoOf(JsonNull).hz = function () {
  return this.iz_1;
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
  this.jz_1 = isString;
  this.kz_1 = coerceToInlineType;
  this.lz_1 = toString(body);
  if (!(this.kz_1 == null)) {
    // Inline function 'kotlin.require' call
    // Inline function 'kotlin.require' call
    if (!this.kz_1.dh()) {
      var message = 'Failed requirement.';
      throw IllegalArgumentException_init_$Create$(toString(message));
    }
  }
}
protoOf(JsonLiteral).hz = function () {
  return this.lz_1;
};
protoOf(JsonLiteral).toString = function () {
  var tmp;
  if (this.jz_1) {
    // Inline function 'kotlin.text.buildString' call
    // Inline function 'kotlin.apply' call
    var this_0 = StringBuilder_init_$Create$();
    printQuoted(this_0, this.lz_1);
    tmp = this_0.toString();
  } else {
    tmp = this.lz_1;
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
  if (!(this.jz_1 === other.jz_1))
    return false;
  if (!(this.lz_1 === other.lz_1))
    return false;
  return true;
};
protoOf(JsonLiteral).hashCode = function () {
  var result = getBooleanHashCode(this.jz_1);
  result = imul(31, result) + getStringHashCode(this.lz_1) | 0;
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
  return toBooleanStrictOrNull_0(_this__u8e3s4.hz());
}
function get_int(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  // Inline function 'kotlinx.serialization.json.mapExceptions' call
  var tmp;
  try {
    tmp = (new StringJsonLexer(_this__u8e3s4.hz())).mz();
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
    throw NumberFormatException_init_$Create$(_this__u8e3s4.hz() + ' is not an Int');
  return convertToInt(result);
}
function get_long(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  // Inline function 'kotlinx.serialization.json.mapExceptions' call
  var tmp;
  try {
    tmp = (new StringJsonLexer(_this__u8e3s4.hz())).mz();
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
  var this_0 = _this__u8e3s4.hz();
  // Inline function 'kotlin.js.unsafeCast' call
  // Inline function 'kotlin.js.asDynamic' call
  return toDouble(this_0);
}
function get_double(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  return toDouble(_this__u8e3s4.hz());
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
  this.nz_1 = MapSerializer(serializer(StringCompanionObject_instance), JsonElementSerializer_getInstance()).ng();
  this.oz_1 = 'kotlinx.serialization.json.JsonObject';
}
protoOf(JsonObjectDescriptor).bh = function () {
  return this.oz_1;
};
protoOf(JsonObjectDescriptor).gh = function (index) {
  return this.nz_1.gh(index);
};
protoOf(JsonObjectDescriptor).hh = function (name) {
  return this.nz_1.hh(name);
};
protoOf(JsonObjectDescriptor).ih = function (index) {
  return this.nz_1.ih(index);
};
protoOf(JsonObjectDescriptor).jh = function (index) {
  return this.nz_1.jh(index);
};
protoOf(JsonObjectDescriptor).kh = function (index) {
  return this.nz_1.kh(index);
};
protoOf(JsonObjectDescriptor).ch = function () {
  return this.nz_1.ch();
};
protoOf(JsonObjectDescriptor).vg = function () {
  return this.nz_1.vg();
};
protoOf(JsonObjectDescriptor).dh = function () {
  return this.nz_1.dh();
};
protoOf(JsonObjectDescriptor).eh = function () {
  return this.nz_1.eh();
};
protoOf(JsonObjectDescriptor).fh = function () {
  return this.nz_1.fh();
};
var JsonObjectDescriptor_instance;
function JsonObjectDescriptor_getInstance() {
  if (JsonObjectDescriptor_instance == null)
    new JsonObjectDescriptor();
  return JsonObjectDescriptor_instance;
}
function JsonObjectSerializer() {
  JsonObjectSerializer_instance = this;
  this.pz_1 = JsonObjectDescriptor_getInstance();
}
protoOf(JsonObjectSerializer).ng = function () {
  return this.pz_1;
};
protoOf(JsonObjectSerializer).qz = function (encoder, value) {
  verify(encoder);
  MapSerializer(serializer(StringCompanionObject_instance), JsonElementSerializer_getInstance()).og(encoder, value);
};
protoOf(JsonObjectSerializer).og = function (encoder, value) {
  return this.qz(encoder, value instanceof JsonObject ? value : THROW_CCE());
};
protoOf(JsonObjectSerializer).pg = function (decoder) {
  verify_0(decoder);
  return new JsonObject(MapSerializer(serializer(StringCompanionObject_instance), JsonElementSerializer_getInstance()).pg(decoder));
};
var JsonObjectSerializer_instance;
function JsonObjectSerializer_getInstance() {
  if (JsonObjectSerializer_instance == null)
    new JsonObjectSerializer();
  return JsonObjectSerializer_instance;
}
function JsonElementSerializer$descriptor$lambda($this$buildSerialDescriptor) {
  $this$buildSerialDescriptor.xh('JsonPrimitive', defer(JsonElementSerializer$descriptor$lambda$lambda));
  $this$buildSerialDescriptor.xh('JsonNull', defer(JsonElementSerializer$descriptor$lambda$lambda_0));
  $this$buildSerialDescriptor.xh('JsonLiteral', defer(JsonElementSerializer$descriptor$lambda$lambda_1));
  $this$buildSerialDescriptor.xh('JsonObject', defer(JsonElementSerializer$descriptor$lambda$lambda_2));
  $this$buildSerialDescriptor.xh('JsonArray', defer(JsonElementSerializer$descriptor$lambda$lambda_3));
  return Unit_instance;
}
function JsonElementSerializer$descriptor$lambda$lambda() {
  return JsonPrimitiveSerializer_getInstance().rz_1;
}
function JsonElementSerializer$descriptor$lambda$lambda_0() {
  return JsonNullSerializer_getInstance().sz_1;
}
function JsonElementSerializer$descriptor$lambda$lambda_1() {
  return JsonLiteralSerializer_getInstance().tz_1;
}
function JsonElementSerializer$descriptor$lambda$lambda_2() {
  return JsonObjectSerializer_getInstance().pz_1;
}
function JsonElementSerializer$descriptor$lambda$lambda_3() {
  return JsonArraySerializer_getInstance().uz_1;
}
function JsonElementSerializer() {
  JsonElementSerializer_instance = this;
  var tmp = this;
  var tmp_0 = SEALED_getInstance();
  tmp.vz_1 = buildSerialDescriptor('kotlinx.serialization.json.JsonElement', tmp_0, [], JsonElementSerializer$descriptor$lambda);
}
protoOf(JsonElementSerializer).ng = function () {
  return this.vz_1;
};
protoOf(JsonElementSerializer).wz = function (encoder, value) {
  verify(encoder);
  if (value instanceof JsonPrimitive) {
    encoder.rk(JsonPrimitiveSerializer_getInstance(), value);
  } else {
    if (value instanceof JsonObject) {
      encoder.rk(JsonObjectSerializer_getInstance(), value);
    } else {
      if (value instanceof JsonArray) {
        encoder.rk(JsonArraySerializer_getInstance(), value);
      } else {
        noWhenBranchMatchedException();
      }
    }
  }
};
protoOf(JsonElementSerializer).og = function (encoder, value) {
  return this.wz(encoder, value instanceof JsonElement ? value : THROW_CCE());
};
protoOf(JsonElementSerializer).pg = function (decoder) {
  var input = asJsonDecoder(decoder);
  return input.cz();
};
var JsonElementSerializer_instance;
function JsonElementSerializer_getInstance() {
  if (JsonElementSerializer_instance == null)
    new JsonElementSerializer();
  return JsonElementSerializer_instance;
}
function JsonArrayDescriptor() {
  JsonArrayDescriptor_instance = this;
  this.xz_1 = ListSerializer(JsonElementSerializer_getInstance()).ng();
  this.yz_1 = 'kotlinx.serialization.json.JsonArray';
}
protoOf(JsonArrayDescriptor).bh = function () {
  return this.yz_1;
};
protoOf(JsonArrayDescriptor).gh = function (index) {
  return this.xz_1.gh(index);
};
protoOf(JsonArrayDescriptor).hh = function (name) {
  return this.xz_1.hh(name);
};
protoOf(JsonArrayDescriptor).ih = function (index) {
  return this.xz_1.ih(index);
};
protoOf(JsonArrayDescriptor).jh = function (index) {
  return this.xz_1.jh(index);
};
protoOf(JsonArrayDescriptor).kh = function (index) {
  return this.xz_1.kh(index);
};
protoOf(JsonArrayDescriptor).ch = function () {
  return this.xz_1.ch();
};
protoOf(JsonArrayDescriptor).vg = function () {
  return this.xz_1.vg();
};
protoOf(JsonArrayDescriptor).dh = function () {
  return this.xz_1.dh();
};
protoOf(JsonArrayDescriptor).eh = function () {
  return this.xz_1.eh();
};
protoOf(JsonArrayDescriptor).fh = function () {
  return this.xz_1.fh();
};
var JsonArrayDescriptor_instance;
function JsonArrayDescriptor_getInstance() {
  if (JsonArrayDescriptor_instance == null)
    new JsonArrayDescriptor();
  return JsonArrayDescriptor_instance;
}
function JsonArraySerializer() {
  JsonArraySerializer_instance = this;
  this.uz_1 = JsonArrayDescriptor_getInstance();
}
protoOf(JsonArraySerializer).ng = function () {
  return this.uz_1;
};
protoOf(JsonArraySerializer).zz = function (encoder, value) {
  verify(encoder);
  ListSerializer(JsonElementSerializer_getInstance()).og(encoder, value);
};
protoOf(JsonArraySerializer).og = function (encoder, value) {
  return this.zz(encoder, value instanceof JsonArray ? value : THROW_CCE());
};
protoOf(JsonArraySerializer).pg = function (decoder) {
  verify_0(decoder);
  return new JsonArray(ListSerializer(JsonElementSerializer_getInstance()).pg(decoder));
};
var JsonArraySerializer_instance;
function JsonArraySerializer_getInstance() {
  if (JsonArraySerializer_instance == null)
    new JsonArraySerializer();
  return JsonArraySerializer_instance;
}
function JsonPrimitiveSerializer() {
  JsonPrimitiveSerializer_instance = this;
  this.rz_1 = buildSerialDescriptor('kotlinx.serialization.json.JsonPrimitive', STRING_getInstance(), []);
}
protoOf(JsonPrimitiveSerializer).ng = function () {
  return this.rz_1;
};
protoOf(JsonPrimitiveSerializer).a10 = function (encoder, value) {
  verify(encoder);
  var tmp;
  if (value instanceof JsonNull) {
    encoder.rk(JsonNullSerializer_getInstance(), JsonNull_getInstance());
    tmp = Unit_instance;
  } else {
    var tmp_0 = JsonLiteralSerializer_getInstance();
    encoder.rk(tmp_0, value instanceof JsonLiteral ? value : THROW_CCE());
    tmp = Unit_instance;
  }
  return tmp;
};
protoOf(JsonPrimitiveSerializer).og = function (encoder, value) {
  return this.a10(encoder, value instanceof JsonPrimitive ? value : THROW_CCE());
};
protoOf(JsonPrimitiveSerializer).pg = function (decoder) {
  var result = asJsonDecoder(decoder).cz();
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
  this.sz_1 = buildSerialDescriptor('kotlinx.serialization.json.JsonNull', ENUM_getInstance(), []);
}
protoOf(JsonNullSerializer).ng = function () {
  return this.sz_1;
};
protoOf(JsonNullSerializer).b10 = function (encoder, value) {
  verify(encoder);
  encoder.vj();
};
protoOf(JsonNullSerializer).og = function (encoder, value) {
  return this.b10(encoder, value instanceof JsonNull ? value : THROW_CCE());
};
protoOf(JsonNullSerializer).pg = function (decoder) {
  verify_0(decoder);
  if (decoder.mi()) {
    throw new JsonDecodingException("Expected 'null' literal");
  }
  decoder.ni();
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
  this.tz_1 = PrimitiveSerialDescriptor('kotlinx.serialization.json.JsonLiteral', STRING_getInstance());
}
protoOf(JsonLiteralSerializer).ng = function () {
  return this.tz_1;
};
protoOf(JsonLiteralSerializer).c10 = function (encoder, value) {
  verify(encoder);
  if (value.jz_1) {
    return encoder.ek(value.lz_1);
  }
  if (!(value.kz_1 == null)) {
    return encoder.fk(value.kz_1).ek(value.lz_1);
  }
  var tmp0_safe_receiver = toLongOrNull(value.lz_1);
  if (tmp0_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    return encoder.ak(tmp0_safe_receiver);
  }
  var tmp1_safe_receiver = toULongOrNull(value.lz_1);
  var tmp = tmp1_safe_receiver;
  if ((tmp == null ? null : new ULong(tmp)) == null)
    null;
  else {
    var tmp_0 = tmp1_safe_receiver;
    // Inline function 'kotlin.let' call
    var it = (tmp_0 == null ? null : new ULong(tmp_0)).vf_1;
    var tmp_1 = encoder.fk(serializer_0(Companion_getInstance()).ng());
    // Inline function 'kotlin.ULong.toLong' call
    var tmp$ret$4 = _ULong___get_data__impl__fggpzb(it);
    tmp_1.ak(tmp$ret$4);
    return Unit_instance;
  }
  var tmp2_safe_receiver = toDoubleOrNull(value.lz_1);
  if (tmp2_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    return encoder.ck(tmp2_safe_receiver);
  }
  var tmp3_safe_receiver = toBooleanStrictOrNull(value.lz_1);
  if (tmp3_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    return encoder.wj(tmp3_safe_receiver);
  }
  encoder.ek(value.lz_1);
};
protoOf(JsonLiteralSerializer).og = function (encoder, value) {
  return this.c10(encoder, value instanceof JsonLiteral ? value : THROW_CCE());
};
protoOf(JsonLiteralSerializer).pg = function (decoder) {
  var result = asJsonDecoder(decoder).cz();
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
  var tmp0 = $this.d10_1;
  var tmp = KProperty1;
  // Inline function 'kotlin.getValue' call
  getPropertyCallableRef('original', 1, tmp, defer$o$_get_original_$ref_3cje7k(), null);
  return tmp0.n1();
}
function defer$o$_get_original_$ref_3cje7k() {
  return function (p0) {
    return _get_original__l7ku1m(p0);
  };
}
function defer$1($deferred) {
  this.d10_1 = lazy($deferred);
}
protoOf(defer$1).bh = function () {
  return _get_original__l7ku1m(this).bh();
};
protoOf(defer$1).ch = function () {
  return _get_original__l7ku1m(this).ch();
};
protoOf(defer$1).eh = function () {
  return _get_original__l7ku1m(this).eh();
};
protoOf(defer$1).gh = function (index) {
  return _get_original__l7ku1m(this).gh(index);
};
protoOf(defer$1).hh = function (name) {
  return _get_original__l7ku1m(this).hh(name);
};
protoOf(defer$1).ih = function (index) {
  return _get_original__l7ku1m(this).ih(index);
};
protoOf(defer$1).jh = function (index) {
  return _get_original__l7ku1m(this).jh(index);
};
protoOf(defer$1).kh = function (index) {
  return _get_original__l7ku1m(this).kh(index);
};
function JsonEncoder() {
}
function Composer(writer) {
  this.e10_1 = writer;
  this.f10_1 = true;
}
protoOf(Composer).g10 = function () {
  this.f10_1 = true;
};
protoOf(Composer).h10 = function () {
  return Unit_instance;
};
protoOf(Composer).i10 = function () {
  this.f10_1 = false;
};
protoOf(Composer).j10 = function () {
  this.f10_1 = false;
};
protoOf(Composer).k10 = function () {
  return Unit_instance;
};
protoOf(Composer).l10 = function (v) {
  return this.e10_1.m10(v);
};
protoOf(Composer).n10 = function (v) {
  return this.e10_1.o10(v);
};
protoOf(Composer).p10 = function (v) {
  return this.e10_1.o10(v.toString());
};
protoOf(Composer).q10 = function (v) {
  return this.e10_1.o10(v.toString());
};
protoOf(Composer).r10 = function (v) {
  return this.e10_1.s10(fromInt(v));
};
protoOf(Composer).t10 = function (v) {
  return this.e10_1.s10(fromInt(v));
};
protoOf(Composer).u10 = function (v) {
  return this.e10_1.s10(fromInt(v));
};
protoOf(Composer).v10 = function (v) {
  return this.e10_1.s10(v);
};
protoOf(Composer).w10 = function (v) {
  return this.e10_1.o10(v.toString());
};
protoOf(Composer).x10 = function (value) {
  return this.e10_1.y10(value);
};
function Composer_0(sb, json) {
  return json.ww_1.oy_1 ? new ComposerWithPrettyPrint(sb, json) : new Composer(sb);
}
function ComposerForUnsignedNumbers(writer, forceQuoting) {
  Composer.call(this, writer);
  this.b11_1 = forceQuoting;
}
protoOf(ComposerForUnsignedNumbers).u10 = function (v) {
  if (this.b11_1) {
    // Inline function 'kotlin.toUInt' call
    var tmp$ret$0 = _UInt___init__impl__l7qpdl(v);
    this.x10(UInt__toString_impl_dbgl21(tmp$ret$0));
  } else {
    // Inline function 'kotlin.toUInt' call
    var tmp$ret$1 = _UInt___init__impl__l7qpdl(v);
    this.n10(UInt__toString_impl_dbgl21(tmp$ret$1));
  }
};
protoOf(ComposerForUnsignedNumbers).v10 = function (v) {
  if (this.b11_1) {
    // Inline function 'kotlin.toULong' call
    var tmp$ret$0 = _ULong___init__impl__c78o9k(v);
    this.x10(ULong__toString_impl_f9au7k(tmp$ret$0));
  } else {
    // Inline function 'kotlin.toULong' call
    var tmp$ret$1 = _ULong___init__impl__c78o9k(v);
    this.n10(ULong__toString_impl_f9au7k(tmp$ret$1));
  }
};
protoOf(ComposerForUnsignedNumbers).r10 = function (v) {
  if (this.b11_1) {
    // Inline function 'kotlin.toUByte' call
    var tmp$ret$0 = _UByte___init__impl__g9hnc4(v);
    this.x10(UByte__toString_impl_v72jg(tmp$ret$0));
  } else {
    // Inline function 'kotlin.toUByte' call
    var tmp$ret$1 = _UByte___init__impl__g9hnc4(v);
    this.n10(UByte__toString_impl_v72jg(tmp$ret$1));
  }
};
protoOf(ComposerForUnsignedNumbers).t10 = function (v) {
  if (this.b11_1) {
    // Inline function 'kotlin.toUShort' call
    var tmp$ret$0 = _UShort___init__impl__jigrne(v);
    this.x10(UShort__toString_impl_edaoee(tmp$ret$0));
  } else {
    // Inline function 'kotlin.toUShort' call
    var tmp$ret$1 = _UShort___init__impl__jigrne(v);
    this.n10(UShort__toString_impl_edaoee(tmp$ret$1));
  }
};
function ComposerForUnquotedLiterals(writer, forceQuoting) {
  Composer.call(this, writer);
  this.e11_1 = forceQuoting;
}
protoOf(ComposerForUnquotedLiterals).x10 = function (value) {
  if (this.e11_1) {
    protoOf(Composer).x10.call(this, value);
  } else {
    protoOf(Composer).n10.call(this, value);
  }
};
function ComposerWithPrettyPrint(writer, json) {
  Composer.call(this, writer);
  this.h11_1 = json;
  this.i11_1 = 0;
}
protoOf(ComposerWithPrettyPrint).g10 = function () {
  this.f10_1 = true;
  this.i11_1 = this.i11_1 + 1 | 0;
};
protoOf(ComposerWithPrettyPrint).h10 = function () {
  this.i11_1 = this.i11_1 - 1 | 0;
};
protoOf(ComposerWithPrettyPrint).i10 = function () {
  this.f10_1 = false;
  this.n10('\n');
  // Inline function 'kotlin.repeat' call
  var times = this.i11_1;
  var inductionVariable = 0;
  if (inductionVariable < times)
    do {
      var index = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      this.n10(this.h11_1.ww_1.qy_1);
    }
     while (inductionVariable < times);
};
protoOf(ComposerWithPrettyPrint).j10 = function () {
  if (this.f10_1)
    this.f10_1 = false;
  else {
    this.i10();
  }
};
protoOf(ComposerWithPrettyPrint).k10 = function () {
  this.l10(_Char___init__impl__6a9atx(32));
};
function readIfAbsent($this, descriptor, index) {
  $this.k11_1 = (!descriptor.kh(index) && descriptor.jh(index).vg());
  return $this.k11_1;
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
  tmp.j11_1 = new ElementMarker(descriptor, JsonElementMarker$readIfAbsent$ref(this));
  this.k11_1 = false;
}
protoOf(JsonElementMarker).l11 = function (index) {
  this.j11_1.wn(index);
};
protoOf(JsonElementMarker).m11 = function () {
  return this.j11_1.xn();
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
  _this__u8e3s4.n11('Trailing comma before the end of JSON ' + entity, _this__u8e3s4.mx_1 - 1 | 0, "Trailing commas are non-complaint JSON and not allowed by default. Use 'allowTrailingCommas = true' in 'Json {}' builder to support them.");
}
function throwInvalidFloatingPointDecoded(_this__u8e3s4, result) {
  _this__u8e3s4.o11('Unexpected special floating-point value ' + toString(result) + '. By default, ' + 'non-finite floating point values are prohibited because they do not conform JSON specification', VOID, "It is possible to deserialize them using 'JsonBuilder.allowSpecialFloatingPointValues = true'");
}
function JsonEncodingException(message) {
  JsonException.call(this, message);
  captureStack(this, JsonEncodingException);
}
function InvalidKeyKindException(keyDescriptor) {
  return new JsonEncodingException("Value of type '" + keyDescriptor.bh() + "' can't be used in JSON as a key in the map. " + ("It should have either primitive or enum kind, but its kind is '" + keyDescriptor.ch().toString() + "'.\n") + "Use 'allowStructuredMapKeys = true' in 'Json {}' builder to convert such maps to [key1, value1, key2, value2,...] arrays.");
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
  var index = _this__u8e3s4.hh(name);
  if (!(index === -3))
    return index;
  if (!json.ww_1.vy_1)
    return index;
  return getJsonNameIndexSlowPath(_this__u8e3s4, json, name);
}
function getJsonElementName(_this__u8e3s4, json, index) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  var strategy = namingStrategy(_this__u8e3s4, json);
  return strategy == null ? _this__u8e3s4.gh(index) : serializationNamesIndices(_this__u8e3s4, json, strategy)[index];
}
function namingStrategy(_this__u8e3s4, json) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  return equals(_this__u8e3s4.ch(), CLASS_getInstance()) ? json.ww_1.wy_1 : null;
}
function deserializationNamesMap(_this__u8e3s4, descriptor) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  var tmp = get_schemaCache(_this__u8e3s4);
  var tmp_0 = get_JsonDeserializationNamesKey();
  return tmp.q11(descriptor, tmp_0, deserializationNamesMap$lambda(descriptor, _this__u8e3s4));
}
function decodeCaseInsensitive(_this__u8e3s4, descriptor) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  return _this__u8e3s4.ww_1.xy_1 && equals(descriptor.ch(), ENUM_getInstance());
}
function getJsonNameIndexSlowPath(_this__u8e3s4, json, name) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  var tmp0_elvis_lhs = deserializationNamesMap(json, _this__u8e3s4).q1(name);
  return tmp0_elvis_lhs == null ? -3 : tmp0_elvis_lhs;
}
function serializationNamesIndices(_this__u8e3s4, json, strategy) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  var tmp = get_schemaCache(json);
  var tmp_0 = get_JsonSerializationNamesKey();
  return tmp.q11(_this__u8e3s4, tmp_0, serializationNamesIndices$lambda(_this__u8e3s4, strategy));
}
function buildDeserializationNamesMap(_this__u8e3s4, json) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  // Inline function 'kotlin.collections.mutableMapOf' call
  var builder = LinkedHashMap_init_$Create$();
  var useLowercaseEnums = decodeCaseInsensitive(json, _this__u8e3s4);
  var strategyForClasses = namingStrategy(_this__u8e3s4, json);
  var inductionVariable = 0;
  var last = _this__u8e3s4.eh();
  if (inductionVariable < last)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      // Inline function 'kotlin.collections.filterIsInstance' call
      var tmp0 = _this__u8e3s4.ih(i);
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
      var tmp1_safe_receiver = tmp0_safe_receiver == null ? null : tmp0_safe_receiver.r11_1;
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
        tmp_0 = _this__u8e3s4.gh(i).toLowerCase();
      } else if (!(strategyForClasses == null)) {
        tmp_0 = strategyForClasses.s11(_this__u8e3s4, i, _this__u8e3s4.gh(i));
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
function serializationNamesIndices$lambda($this_serializationNamesIndices, $strategy) {
  return function () {
    var tmp = 0;
    var tmp_0 = $this_serializationNamesIndices.eh();
    // Inline function 'kotlin.arrayOfNulls' call
    var tmp_1 = Array(tmp_0);
    while (tmp < tmp_0) {
      var tmp_2 = tmp;
      var baseName = $this_serializationNamesIndices.gh(tmp_2);
      tmp_1[tmp_2] = $strategy.s11($this_serializationNamesIndices, tmp_2, baseName);
      tmp = tmp + 1 | 0;
    }
    return tmp_1;
  };
}
function buildDeserializationNamesMap$putOrThrow(_this__u8e3s4, $this_buildDeserializationNamesMap, name, index) {
  var entity = equals($this_buildDeserializationNamesMap.ch(), ENUM_getInstance()) ? 'enum value' : 'property';
  // Inline function 'kotlin.collections.contains' call
  // Inline function 'kotlin.collections.containsKey' call
  if ((isInterface(_this__u8e3s4, KtMap) ? _this__u8e3s4 : THROW_CCE()).o1(name)) {
    throw new JsonException("The suggested name '" + name + "' for " + entity + ' ' + $this_buildDeserializationNamesMap.gh(index) + ' is already one of the names for ' + entity + ' ' + ($this_buildDeserializationNamesMap.gh(getValue(_this__u8e3s4, name)) + ' in ' + toString($this_buildDeserializationNamesMap)));
  }
  // Inline function 'kotlin.collections.set' call
  _this__u8e3s4.u1(name, index);
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
  var newSize = imul($this.v11_1, 2);
  $this.t11_1 = copyOf($this.t11_1, newSize);
  $this.u11_1 = copyOf_0($this.u11_1, newSize);
}
function JsonPath() {
  var tmp = this;
  // Inline function 'kotlin.arrayOfNulls' call
  tmp.t11_1 = Array(8);
  var tmp_0 = this;
  var tmp_1 = 0;
  var tmp_2 = new Int32Array(8);
  while (tmp_1 < 8) {
    tmp_2[tmp_1] = -1;
    tmp_1 = tmp_1 + 1 | 0;
  }
  tmp_0.u11_1 = tmp_2;
  this.v11_1 = -1;
}
protoOf(JsonPath).w11 = function (sd) {
  this.v11_1 = this.v11_1 + 1 | 0;
  var depth = this.v11_1;
  if (depth === this.t11_1.length) {
    resize(this);
  }
  this.t11_1[depth] = sd;
};
protoOf(JsonPath).x11 = function (index) {
  this.u11_1[this.v11_1] = index;
};
protoOf(JsonPath).y11 = function (key) {
  var tmp;
  if (!(this.u11_1[this.v11_1] === -2)) {
    this.v11_1 = this.v11_1 + 1 | 0;
    tmp = this.v11_1 === this.t11_1.length;
  } else {
    tmp = false;
  }
  if (tmp) {
    resize(this);
  }
  this.t11_1[this.v11_1] = key;
  this.u11_1[this.v11_1] = -2;
};
protoOf(JsonPath).z11 = function () {
  if (this.u11_1[this.v11_1] === -2) {
    this.t11_1[this.v11_1] = Tombstone_instance;
  }
};
protoOf(JsonPath).a12 = function () {
  var depth = this.v11_1;
  if (this.u11_1[depth] === -2) {
    this.u11_1[depth] = -1;
    this.v11_1 = this.v11_1 - 1 | 0;
  }
  if (!(this.v11_1 === -1)) {
    this.v11_1 = this.v11_1 - 1 | 0;
  }
};
protoOf(JsonPath).b12 = function () {
  // Inline function 'kotlin.text.buildString' call
  // Inline function 'kotlin.apply' call
  var this_0 = StringBuilder_init_$Create$();
  this_0.x6('$');
  // Inline function 'kotlin.repeat' call
  var times = this.v11_1 + 1 | 0;
  var inductionVariable = 0;
  if (inductionVariable < times)
    do {
      var index = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      var element = this.t11_1[index];
      if (!(element == null) ? isInterface(element, SerialDescriptor) : false) {
        if (equals(element.ch(), LIST_getInstance())) {
          if (!(this.u11_1[index] === -1)) {
            this_0.x6('[');
            this_0.n9(this.u11_1[index]);
            this_0.x6(']');
          }
        } else {
          var idx = this.u11_1[index];
          if (idx >= 0) {
            this_0.x6('.');
            this_0.x6(element.gh(idx));
          }
        }
      } else {
        if (!(element === Tombstone_instance)) {
          this_0.x6('[');
          this_0.x6("'");
          this_0.w6(element);
          this_0.x6("'");
          this_0.x6(']');
        }
      }
    }
     while (inductionVariable < times);
  return this_0.toString();
};
protoOf(JsonPath).toString = function () {
  return this.b12();
};
function encodeByWriter(json, writer, serializer, value) {
  var tmp = WriteMode_OBJ_getInstance();
  // Inline function 'kotlin.arrayOfNulls' call
  var size = get_entries().j();
  var tmp$ret$0 = Array(size);
  var encoder = StreamingJsonEncoder_init_$Create$(writer, json, tmp, tmp$ret$0);
  encoder.rk(serializer, value);
}
function readObject($this) {
  // Inline function 'kotlinx.serialization.json.internal.JsonTreeReader.readObjectImpl' call
  var lastToken = $this.l12_1.p12(6);
  if ($this.l12_1.q12() === 4) {
    $this.l12_1.o11('Unexpected leading comma');
  }
  // Inline function 'kotlin.collections.linkedMapOf' call
  var result = LinkedHashMap_init_$Create$();
  $l$loop: while ($this.l12_1.r12()) {
    var key = $this.m12_1 ? $this.l12_1.t12() : $this.l12_1.s12();
    $this.l12_1.p12(5);
    var element = $this.u12();
    // Inline function 'kotlin.collections.set' call
    result.u1(key, element);
    lastToken = $this.l12_1.v12();
    var tmp0_subject = lastToken;
    if (tmp0_subject !== 4)
      if (tmp0_subject === 7)
        break $l$loop;
      else {
        $this.l12_1.o11('Expected end of the object or comma');
      }
  }
  if (lastToken === 6) {
    $this.l12_1.p12(7);
  } else if (lastToken === 4) {
    if (!$this.n12_1) {
      invalidTrailingComma($this.l12_1);
    }
    $this.l12_1.p12(7);
  }
  return new JsonObject(result);
}
function readObject_0($this, $receiver, $completion) {
  var tmp = new $readObjectCOROUTINE$($this, $receiver, $completion);
  tmp.l7_1 = Unit_instance;
  tmp.m7_1 = null;
  return tmp.r7();
}
function readArray($this) {
  var lastToken = $this.l12_1.v12();
  if ($this.l12_1.q12() === 4) {
    $this.l12_1.o11('Unexpected leading comma');
  }
  // Inline function 'kotlin.collections.arrayListOf' call
  var result = ArrayList_init_$Create$();
  while ($this.l12_1.r12()) {
    var element = $this.u12();
    result.e(element);
    lastToken = $this.l12_1.v12();
    if (!(lastToken === 4)) {
      var tmp0 = $this.l12_1;
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.require' call
      var condition = lastToken === 9;
      var position = tmp0.mx_1;
      if (!condition) {
        var tmp$ret$2 = 'Expected end of the array or comma';
        tmp0.o11(tmp$ret$2, position);
      }
    }
  }
  if (lastToken === 8) {
    $this.l12_1.p12(9);
  } else if (lastToken === 4) {
    if (!$this.n12_1) {
      invalidTrailingComma($this.l12_1, 'array');
    }
    $this.l12_1.p12(9);
  }
  return new JsonArray(result);
}
function readValue($this, isString) {
  var tmp;
  if ($this.m12_1 || !isString) {
    tmp = $this.l12_1.t12();
  } else {
    tmp = $this.l12_1.s12();
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
  this.s13_1 = this$0;
  CoroutineImpl.call(this, resultContinuation);
}
protoOf(JsonTreeReader$readDeepRecursive$slambda).w13 = function ($this$DeepRecursiveFunction, it, $completion) {
  var tmp = this.x13($this$DeepRecursiveFunction, it, $completion);
  tmp.l7_1 = Unit_instance;
  tmp.m7_1 = null;
  return tmp.r7();
};
protoOf(JsonTreeReader$readDeepRecursive$slambda).x7 = function (p1, p2, $completion) {
  var tmp = p1 instanceof DeepRecursiveScope ? p1 : THROW_CCE();
  return this.w13(tmp, p2 instanceof Unit ? p2 : THROW_CCE(), $completion);
};
protoOf(JsonTreeReader$readDeepRecursive$slambda).r7 = function () {
  var suspendResult = this.l7_1;
  $sm: do
    try {
      var tmp = this.j7_1;
      switch (tmp) {
        case 0:
          this.k7_1 = 3;
          var tmp0_subject = this.s13_1.l12_1.q12();
          if (tmp0_subject === 1) {
            this.v13_1 = readValue(this.s13_1, true);
            this.j7_1 = 2;
            continue $sm;
          } else {
            if (tmp0_subject === 0) {
              this.v13_1 = readValue(this.s13_1, false);
              this.j7_1 = 2;
              continue $sm;
            } else {
              if (tmp0_subject === 6) {
                this.j7_1 = 1;
                suspendResult = readObject_0(this.s13_1, this.t13_1, this);
                if (suspendResult === get_COROUTINE_SUSPENDED()) {
                  return suspendResult;
                }
                continue $sm;
              } else {
                if (tmp0_subject === 8) {
                  this.v13_1 = readArray(this.s13_1);
                  this.j7_1 = 2;
                  continue $sm;
                } else {
                  var tmp_0 = this;
                  this.s13_1.l12_1.o11("Can't begin reading element, unexpected token");
                }
              }
            }
          }

          break;
        case 1:
          this.v13_1 = suspendResult;
          this.j7_1 = 2;
          continue $sm;
        case 2:
          return this.v13_1;
        case 3:
          throw this.m7_1;
      }
    } catch ($p) {
      var e = $p;
      if (this.k7_1 === 3) {
        throw e;
      } else {
        this.j7_1 = this.k7_1;
        this.m7_1 = e;
      }
    }
   while (true);
};
protoOf(JsonTreeReader$readDeepRecursive$slambda).x13 = function ($this$DeepRecursiveFunction, it, completion) {
  var i = new JsonTreeReader$readDeepRecursive$slambda(this.s13_1, completion);
  i.t13_1 = $this$DeepRecursiveFunction;
  i.u13_1 = it;
  return i;
};
function JsonTreeReader$readDeepRecursive$slambda_0(this$0, resultContinuation) {
  var i = new JsonTreeReader$readDeepRecursive$slambda(this$0, resultContinuation);
  var l = function ($this$DeepRecursiveFunction, it, $completion) {
    return i.w13($this$DeepRecursiveFunction, it, $completion);
  };
  l.$arity = 2;
  return l;
}
function $readObjectCOROUTINE$(_this__u8e3s4, _this__u8e3s4_0, resultContinuation) {
  CoroutineImpl.call(this, resultContinuation);
  this.e13_1 = _this__u8e3s4;
  this.f13_1 = _this__u8e3s4_0;
}
protoOf($readObjectCOROUTINE$).r7 = function () {
  var suspendResult = this.l7_1;
  $sm: do
    try {
      var tmp = this.j7_1;
      switch (tmp) {
        case 0:
          this.k7_1 = 5;
          this.h13_1 = this.e13_1;
          this.i13_1 = this.h13_1.l12_1.p12(6);
          if (this.h13_1.l12_1.q12() === 4) {
            this.h13_1.l12_1.o11('Unexpected leading comma');
          }

          var tmp_0 = this;
          tmp_0.g13_1 = LinkedHashMap_init_$Create$();
          this.j7_1 = 1;
          continue $sm;
        case 1:
          if (!this.h13_1.l12_1.r12()) {
            this.j7_1 = 4;
            continue $sm;
          }

          this.j13_1 = this.h13_1.m12_1 ? this.h13_1.l12_1.t12() : this.h13_1.l12_1.s12();
          this.h13_1.l12_1.p12(5);
          this.j7_1 = 2;
          suspendResult = this.f13_1.ae(Unit_instance, this);
          if (suspendResult === get_COROUTINE_SUSPENDED()) {
            return suspendResult;
          }

          continue $sm;
        case 2:
          var element = suspendResult;
          var tmp0 = this.g13_1;
          var key = this.j13_1;
          tmp0.u1(key, element);
          this.i13_1 = this.h13_1.l12_1.v12();
          var tmp0_subject = this.i13_1;
          if (tmp0_subject === 4) {
            this.j7_1 = 3;
            continue $sm;
          } else {
            if (tmp0_subject === 7) {
              this.j7_1 = 4;
              continue $sm;
            } else {
              this.h13_1.l12_1.o11('Expected end of the object or comma');
            }
          }

          break;
        case 3:
          this.j7_1 = 1;
          continue $sm;
        case 4:
          if (this.i13_1 === 6) {
            this.h13_1.l12_1.p12(7);
          } else if (this.i13_1 === 4) {
            if (!this.h13_1.n12_1) {
              invalidTrailingComma(this.h13_1.l12_1);
            }
            this.h13_1.l12_1.p12(7);
          }

          return new JsonObject(this.g13_1);
        case 5:
          throw this.m7_1;
      }
    } catch ($p) {
      var e = $p;
      if (this.k7_1 === 5) {
        throw e;
      } else {
        this.j7_1 = this.k7_1;
        this.m7_1 = e;
      }
    }
   while (true);
};
function JsonTreeReader(configuration, lexer) {
  this.l12_1 = lexer;
  this.m12_1 = configuration.my_1;
  this.n12_1 = configuration.yy_1;
  this.o12_1 = 0;
}
protoOf(JsonTreeReader).u12 = function () {
  var token = this.l12_1.q12();
  var tmp;
  if (token === 1) {
    tmp = readValue(this, true);
  } else if (token === 0) {
    tmp = readValue(this, false);
  } else if (token === 6) {
    var tmp_0;
    this.o12_1 = this.o12_1 + 1 | 0;
    if (this.o12_1 === 200) {
      tmp_0 = readDeepRecursive(this);
    } else {
      tmp_0 = readObject(this);
    }
    var result = tmp_0;
    this.o12_1 = this.o12_1 - 1 | 0;
    tmp = result;
  } else if (token === 8) {
    tmp = readArray(this);
  } else {
    this.l12_1.o11('Cannot read Json element because of unexpected ' + tokenDescription(token));
  }
  return tmp;
};
function classDiscriminator(_this__u8e3s4, json) {
  var _iterator__ex2g4s = _this__u8e3s4.fh().g();
  while (_iterator__ex2g4s.h()) {
    var annotation = _iterator__ex2g4s.i();
    if (annotation instanceof JsonClassDiscriminator)
      return annotation.y13_1;
  }
  return json.ww_1.ty_1;
}
function validateIfSealed(serializer, actualSerializer, classDiscriminator) {
  if (!(serializer instanceof SealedClassSerializer))
    return Unit_instance;
  if (jsonCachedSerialNames(actualSerializer.ng()).w(classDiscriminator)) {
    var baseName = serializer.ng().bh();
    var actualName = actualSerializer.ng().bh();
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
  var kind = descriptor.ch();
  var tmp;
  if (kind instanceof PolymorphicKind) {
    tmp = true;
  } else {
    tmp = equals(kind, CONTEXTUAL_getInstance());
  }
  if (tmp) {
    throw IllegalArgumentException_init_$Create$('Serializer for ' + actualClass.i8() + " can't be registered as a subclass for polymorphic serialization " + ('because its kind ' + kind.toString() + ' is not concrete. To work with multiple hierarchies, register it as a base class.'));
  }
  if ($this.z13_1)
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
    throw IllegalArgumentException_init_$Create$('Serializer for ' + actualClass.i8() + ' of kind ' + kind.toString() + ' cannot be serialized polymorphically with class discriminator.');
  }
}
function checkDiscriminatorCollisions($this, descriptor, actualClass) {
  var inductionVariable = 0;
  var last = descriptor.eh();
  if (inductionVariable < last)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      var name = descriptor.gh(i);
      if (name === $this.a14_1) {
        throw IllegalArgumentException_init_$Create$('Polymorphic serializer for ' + toString(actualClass) + " has property '" + name + "' that conflicts " + 'with JSON class discriminator. You can either change class discriminator in JsonConfiguration, ' + 'rename property with @SerialName annotation ' + 'or fall back to array polymorphism');
      }
    }
     while (inductionVariable < last);
}
function PolymorphismValidator(useArrayPolymorphism, discriminator) {
  this.z13_1 = useArrayPolymorphism;
  this.a14_1 = discriminator;
}
protoOf(PolymorphismValidator).qw = function (kClass, provider) {
};
protoOf(PolymorphismValidator).tw = function (baseClass, actualClass, actualSerializer) {
  var descriptor = actualSerializer.ng();
  checkKind_0(this, descriptor, actualClass);
  if (!this.z13_1) {
    checkDiscriminatorCollisions(this, descriptor, actualClass);
  }
};
protoOf(PolymorphismValidator).uw = function (baseClass, defaultSerializerProvider) {
};
protoOf(PolymorphismValidator).vw = function (baseClass, defaultDeserializerProvider) {
};
function Key() {
}
function DescriptorSchemaCache() {
  this.p11_1 = createMapForCache(16);
}
protoOf(DescriptorSchemaCache).b14 = function (descriptor, key, value) {
  // Inline function 'kotlin.collections.getOrPut' call
  var this_0 = this.p11_1;
  var value_0 = this_0.q1(descriptor);
  var tmp;
  if (value_0 == null) {
    var answer = createMapForCache(2);
    this_0.u1(descriptor, answer);
    tmp = answer;
  } else {
    tmp = value_0;
  }
  var tmp0 = tmp;
  var tmp2 = key instanceof Key ? key : THROW_CCE();
  // Inline function 'kotlin.collections.set' call
  var value_1 = !(value == null) ? value : THROW_CCE();
  tmp0.u1(tmp2, value_1);
};
protoOf(DescriptorSchemaCache).q11 = function (descriptor, key, defaultValue) {
  var tmp0_safe_receiver = this.c14(descriptor, key);
  if (tmp0_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    return tmp0_safe_receiver;
  }
  var value = defaultValue();
  this.b14(descriptor, key, value);
  return value;
};
protoOf(DescriptorSchemaCache).c14 = function (descriptor, key) {
  var tmp0_safe_receiver = this.p11_1.q1(descriptor);
  var tmp;
  if (tmp0_safe_receiver == null) {
    tmp = null;
  } else {
    tmp = tmp0_safe_receiver.q1(key instanceof Key ? key : THROW_CCE());
  }
  var tmp_0 = tmp;
  return !(tmp_0 == null) ? tmp_0 : null;
};
function DiscriminatorHolder(discriminatorToSkip) {
  this.d14_1 = discriminatorToSkip;
}
function trySkip($this, $receiver, unknownKey) {
  if ($receiver == null)
    return false;
  if ($receiver.d14_1 === unknownKey) {
    $receiver.d14_1 = null;
    return true;
  }
  return false;
}
function skipLeftoverElements($this, descriptor) {
  while (!($this.rj(descriptor) === -1)) {
  }
}
function checkLeadingComma($this) {
  if ($this.gx_1.q12() === 4) {
    $this.gx_1.o11('Unexpected leading comma');
  }
}
function decodeMapIndex($this) {
  var hasComma = false;
  var decodingKey = !(($this.ix_1 % 2 | 0) === 0);
  if (decodingKey) {
    if (!($this.ix_1 === -1)) {
      hasComma = $this.gx_1.f14();
    }
  } else {
    $this.gx_1.e14(_Char___init__impl__6a9atx(58));
  }
  var tmp;
  if ($this.gx_1.r12()) {
    if (decodingKey) {
      if ($this.ix_1 === -1) {
        var tmp0 = $this.gx_1;
        // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.require' call
        var condition = !hasComma;
        var position = tmp0.mx_1;
        if (!condition) {
          var tmp$ret$1 = 'Unexpected leading comma';
          tmp0.o11(tmp$ret$1, position);
        }
      } else {
        var tmp0_0 = $this.gx_1;
        // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.require' call
        var condition_0 = hasComma;
        var position_0 = tmp0_0.mx_1;
        if (!condition_0) {
          var tmp$ret$3 = 'Expected comma after the key-value pair';
          tmp0_0.o11(tmp$ret$3, position_0);
        }
      }
    }
    $this.ix_1 = $this.ix_1 + 1 | 0;
    tmp = $this.ix_1;
  } else {
    if (hasComma && !$this.ex_1.ww_1.yy_1) {
      invalidTrailingComma($this.gx_1);
    }
    tmp = -1;
  }
  return tmp;
}
function coerceInputValue($this, descriptor, index) {
  var tmp0 = $this.ex_1;
  var tmp$ret$0;
  $l$block_2: {
    // Inline function 'kotlinx.serialization.json.internal.tryCoerceValue' call
    var isOptional = descriptor.kh(index);
    var elementDescriptor = descriptor.jh(index);
    var tmp;
    if (isOptional && !elementDescriptor.vg()) {
      tmp = $this.gx_1.g14(true);
    } else {
      tmp = false;
    }
    if (tmp) {
      tmp$ret$0 = true;
      break $l$block_2;
    }
    if (equals(elementDescriptor.ch(), ENUM_getInstance())) {
      var tmp_0;
      if (elementDescriptor.vg()) {
        tmp_0 = $this.gx_1.g14(false);
      } else {
        tmp_0 = false;
      }
      if (tmp_0) {
        tmp$ret$0 = false;
        break $l$block_2;
      }
      var tmp0_elvis_lhs = $this.gx_1.h14($this.kx_1.my_1);
      var tmp_1;
      if (tmp0_elvis_lhs == null) {
        tmp$ret$0 = false;
        break $l$block_2;
      } else {
        tmp_1 = tmp0_elvis_lhs;
      }
      var enumValue = tmp_1;
      var enumIndex = getJsonNameIndex(elementDescriptor, tmp0, enumValue);
      var coerceToNull = !tmp0.ww_1.py_1 && elementDescriptor.vg();
      if (enumIndex === -3 && (isOptional || coerceToNull)) {
        $this.gx_1.s12();
        tmp$ret$0 = true;
        break $l$block_2;
      }
    }
    tmp$ret$0 = false;
  }
  return tmp$ret$0;
}
function decodeObjectIndex($this, descriptor) {
  var hasComma = $this.gx_1.f14();
  while ($this.gx_1.r12()) {
    hasComma = false;
    var key = decodeStringKey($this);
    $this.gx_1.e14(_Char___init__impl__6a9atx(58));
    var index = getJsonNameIndex(descriptor, $this.ex_1, key);
    var tmp;
    if (!(index === -3)) {
      var tmp_0;
      if ($this.kx_1.ry_1 && coerceInputValue($this, descriptor, index)) {
        hasComma = $this.gx_1.f14();
        tmp_0 = false;
      } else {
        var tmp0_safe_receiver = $this.lx_1;
        if (tmp0_safe_receiver == null)
          null;
        else {
          tmp0_safe_receiver.l11(index);
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
  if (hasComma && !$this.ex_1.ww_1.yy_1) {
    invalidTrailingComma($this.gx_1);
  }
  var tmp1_safe_receiver = $this.lx_1;
  var tmp2_elvis_lhs = tmp1_safe_receiver == null ? null : tmp1_safe_receiver.m11();
  return tmp2_elvis_lhs == null ? -1 : tmp2_elvis_lhs;
}
function handleUnknown($this, key) {
  if ($this.kx_1.ly_1 || trySkip($this, $this.jx_1, key)) {
    $this.gx_1.j14($this.kx_1.my_1);
  } else {
    $this.gx_1.i14(key);
  }
  return $this.gx_1.f14();
}
function decodeListIndex($this) {
  var hasComma = $this.gx_1.f14();
  var tmp;
  if ($this.gx_1.r12()) {
    if (!($this.ix_1 === -1) && !hasComma) {
      $this.gx_1.o11('Expected end of the array or comma');
    }
    $this.ix_1 = $this.ix_1 + 1 | 0;
    tmp = $this.ix_1;
  } else {
    if (hasComma && !$this.ex_1.ww_1.yy_1) {
      invalidTrailingComma($this.gx_1, 'array');
    }
    tmp = -1;
  }
  return tmp;
}
function decodeStringKey($this) {
  var tmp;
  if ($this.kx_1.my_1) {
    tmp = $this.gx_1.l14();
  } else {
    tmp = $this.gx_1.k14();
  }
  return tmp;
}
function StreamingJsonDecoder(json, mode, lexer, descriptor, discriminatorHolder) {
  AbstractDecoder.call(this);
  this.ex_1 = json;
  this.fx_1 = mode;
  this.gx_1 = lexer;
  this.hx_1 = this.ex_1.pj();
  this.ix_1 = -1;
  this.jx_1 = discriminatorHolder;
  this.kx_1 = this.ex_1.ww_1;
  this.lx_1 = this.kx_1.py_1 ? null : new JsonElementMarker(descriptor);
}
protoOf(StreamingJsonDecoder).bz = function () {
  return this.ex_1;
};
protoOf(StreamingJsonDecoder).pj = function () {
  return this.hx_1;
};
protoOf(StreamingJsonDecoder).cz = function () {
  return (new JsonTreeReader(this.ex_1.ww_1, this.gx_1)).u12();
};
protoOf(StreamingJsonDecoder).zi = function (deserializer) {
  try {
    var tmp;
    if (!(deserializer instanceof AbstractPolymorphicSerializer)) {
      tmp = true;
    } else {
      tmp = this.ex_1.ww_1.sy_1;
    }
    if (tmp) {
      return deserializer.pg(this);
    }
    var discriminator = classDiscriminator(deserializer.ng(), this.ex_1);
    var tmp0_elvis_lhs = this.gx_1.m14(discriminator, this.kx_1.my_1);
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
          tmp_1 = this.bz().ww_1.sy_1;
        }
        if (tmp_1) {
          tmp$ret$0 = tmp2.pg(this);
          break $l$block;
        }
        var discriminator_0 = classDiscriminator(tmp2.ng(), this.bz());
        var tmp0 = this.cz();
        // Inline function 'kotlinx.serialization.json.internal.cast' call
        var serialName = tmp2.ng().bh();
        if (!(tmp0 instanceof JsonObject)) {
          var tmp_2 = getKClass(JsonObject).i8();
          var tmp_3 = getKClassFromExpression(tmp0).i8();
          var tmp$ret$2 = this.gx_1.nx_1.b12();
          throw JsonDecodingException_0(-1, 'Expected ' + tmp_2 + ', but had ' + tmp_3 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$2, toString(tmp0));
        }
        var jsonTree = tmp0;
        var tmp0_safe_receiver = jsonTree.na(discriminator_0);
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
        tmp$ret$0 = readPolymorphicJson(this.bz(), discriminator_0, jsonTree, actualSerializer);
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
        this.gx_1.o11(message, VOID, hint);
      } else {
        throw $p;
      }
      tmp_7 = tmp_8;
    }
    var tmp_9 = tmp_7;
    var actualSerializer_0 = isInterface(tmp_9, DeserializationStrategy) ? tmp_9 : THROW_CCE();
    this.jx_1 = new DiscriminatorHolder(discriminator);
    return actualSerializer_0.pg(this);
  } catch ($p) {
    if ($p instanceof MissingFieldException) {
      var e = $p;
      if (contains_0(ensureNotNull(e.message), 'at path'))
        throw e;
      throw new MissingFieldException(e.ug_1, plus(e.message, ' at path: ') + this.gx_1.nx_1.b12(), e);
    } else {
      throw $p;
    }
  }
};
protoOf(StreamingJsonDecoder).aj = function (descriptor) {
  var newMode = switchMode(this.ex_1, descriptor);
  this.gx_1.nx_1.w11(descriptor);
  this.gx_1.e14(newMode.p14_1);
  checkLeadingComma(this);
  var tmp;
  switch (newMode.w1_1) {
    case 1:
    case 2:
    case 3:
      tmp = new StreamingJsonDecoder(this.ex_1, newMode, this.gx_1, descriptor, this.jx_1);
      break;
    default:
      var tmp_0;
      if (this.fx_1.equals(newMode) && this.ex_1.ww_1.py_1) {
        tmp_0 = this;
      } else {
        tmp_0 = new StreamingJsonDecoder(this.ex_1, newMode, this.gx_1, descriptor, this.jx_1);
      }

      tmp = tmp_0;
      break;
  }
  return tmp;
};
protoOf(StreamingJsonDecoder).bj = function (descriptor) {
  if (this.ex_1.ww_1.ly_1 && descriptor.eh() === 0) {
    skipLeftoverElements(this, descriptor);
  }
  if (this.gx_1.f14() && !this.ex_1.ww_1.yy_1) {
    invalidTrailingComma(this.gx_1, '');
  }
  this.gx_1.e14(this.fx_1.q14_1);
  this.gx_1.nx_1.a12();
};
protoOf(StreamingJsonDecoder).mi = function () {
  var tmp;
  var tmp0_safe_receiver = this.lx_1;
  var tmp1_elvis_lhs = tmp0_safe_receiver == null ? null : tmp0_safe_receiver.k11_1;
  if (!(tmp1_elvis_lhs == null ? false : tmp1_elvis_lhs)) {
    tmp = !this.gx_1.r14();
  } else {
    tmp = false;
  }
  return tmp;
};
protoOf(StreamingJsonDecoder).ni = function () {
  return null;
};
protoOf(StreamingJsonDecoder).mj = function (descriptor, index, deserializer, previousValue) {
  var isMapKey = this.fx_1.equals(WriteMode_MAP_getInstance()) && (index & 1) === 0;
  if (isMapKey) {
    this.gx_1.nx_1.z11();
  }
  var value = protoOf(AbstractDecoder).mj.call(this, descriptor, index, deserializer, previousValue);
  if (isMapKey) {
    this.gx_1.nx_1.y11(value);
  }
  return value;
};
protoOf(StreamingJsonDecoder).rj = function (descriptor) {
  var index;
  switch (this.fx_1.w1_1) {
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
  if (!this.fx_1.equals(WriteMode_MAP_getInstance())) {
    this.gx_1.nx_1.x11(index);
  }
  return index;
};
protoOf(StreamingJsonDecoder).oi = function () {
  return this.gx_1.s14();
};
protoOf(StreamingJsonDecoder).pi = function () {
  var value = this.gx_1.mz();
  if (!equalsLong(value, fromInt(convertToByte(value)))) {
    this.gx_1.o11("Failed to parse byte for input '" + value.toString() + "'");
  }
  return convertToByte(value);
};
protoOf(StreamingJsonDecoder).qi = function () {
  var value = this.gx_1.mz();
  if (!equalsLong(value, fromInt(convertToShort(value)))) {
    this.gx_1.o11("Failed to parse short for input '" + value.toString() + "'");
  }
  return convertToShort(value);
};
protoOf(StreamingJsonDecoder).ri = function () {
  var value = this.gx_1.mz();
  if (!equalsLong(value, fromInt(convertToInt(value)))) {
    this.gx_1.o11("Failed to parse int for input '" + value.toString() + "'");
  }
  return convertToInt(value);
};
protoOf(StreamingJsonDecoder).si = function () {
  return this.gx_1.mz();
};
protoOf(StreamingJsonDecoder).ti = function () {
  var tmp0 = this.gx_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.t12();
    try {
      // Inline function 'kotlin.text.toFloat' call
      // Inline function 'kotlin.js.unsafeCast' call
      // Inline function 'kotlin.js.asDynamic' call
      tmp$ret$0 = toDouble(input);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.o11("Failed to parse type '" + 'float' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  var result = tmp$ret$0;
  var specialFp = this.ex_1.ww_1.uy_1;
  if (specialFp || isFinite(result))
    return result;
  throwInvalidFloatingPointDecoded(this.gx_1, result);
};
protoOf(StreamingJsonDecoder).ui = function () {
  var tmp0 = this.gx_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.t12();
    try {
      tmp$ret$0 = toDouble(input);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.o11("Failed to parse type '" + 'double' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  var result = tmp$ret$0;
  var specialFp = this.ex_1.ww_1.uy_1;
  if (specialFp || isFinite_0(result))
    return result;
  throwInvalidFloatingPointDecoded(this.gx_1, result);
};
protoOf(StreamingJsonDecoder).vi = function () {
  var string = this.gx_1.t12();
  if (!(string.length === 1)) {
    this.gx_1.o11("Expected single char, but got '" + string + "'");
  }
  return charCodeAt(string, 0);
};
protoOf(StreamingJsonDecoder).wi = function () {
  var tmp;
  if (this.kx_1.my_1) {
    tmp = this.gx_1.l14();
  } else {
    tmp = this.gx_1.s12();
  }
  return tmp;
};
protoOf(StreamingJsonDecoder).xi = function (descriptor) {
  return get_isUnsignedNumber(descriptor) ? new JsonDecoderForUnsignedTypes(this.gx_1, this.ex_1) : protoOf(AbstractDecoder).xi.call(this, descriptor);
};
function JsonDecoderForUnsignedTypes(lexer, json) {
  AbstractDecoder.call(this);
  this.t14_1 = lexer;
  this.u14_1 = json.pj();
}
protoOf(JsonDecoderForUnsignedTypes).pj = function () {
  return this.u14_1;
};
protoOf(JsonDecoderForUnsignedTypes).rj = function (descriptor) {
  // Inline function 'kotlin.error' call
  var message = 'unsupported';
  throw IllegalStateException_init_$Create$(toString(message));
};
protoOf(JsonDecoderForUnsignedTypes).ri = function () {
  var tmp0 = this.t14_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.t12();
    try {
      // Inline function 'kotlin.UInt.toInt' call
      var this_0 = toUInt(input);
      tmp$ret$0 = _UInt___get_data__impl__f0vqqw(this_0);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.o11("Failed to parse type '" + 'UInt' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$0;
};
protoOf(JsonDecoderForUnsignedTypes).si = function () {
  var tmp0 = this.t14_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.t12();
    try {
      // Inline function 'kotlin.ULong.toLong' call
      var this_0 = toULong(input);
      tmp$ret$0 = _ULong___get_data__impl__fggpzb(this_0);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.o11("Failed to parse type '" + 'ULong' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$0;
};
protoOf(JsonDecoderForUnsignedTypes).pi = function () {
  var tmp0 = this.t14_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.t12();
    try {
      // Inline function 'kotlin.UByte.toByte' call
      var this_0 = toUByte(input);
      tmp$ret$0 = _UByte___get_data__impl__jof9qr(this_0);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.o11("Failed to parse type '" + 'UByte' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$0;
};
protoOf(JsonDecoderForUnsignedTypes).qi = function () {
  var tmp0 = this.t14_1;
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var input = tmp0.t12();
    try {
      // Inline function 'kotlin.UShort.toShort' call
      var this_0 = toUShort(input);
      tmp$ret$0 = _UShort___get_data__impl__g0245(this_0);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        tmp0.o11("Failed to parse type '" + 'UShort' + "' for input '" + input + "'");
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
  $this.c12_1.i10();
  $this.ek(discriminator);
  $this.c12_1.l10(_Char___init__impl__6a9atx(58));
  $this.c12_1.k10();
  $this.ek(serialName);
}
function StreamingJsonEncoder(composer, json, mode, modeReuseCache) {
  AbstractEncoder.call(this);
  this.c12_1 = composer;
  this.d12_1 = json;
  this.e12_1 = mode;
  this.f12_1 = modeReuseCache;
  this.g12_1 = this.d12_1.pj();
  this.h12_1 = this.d12_1.ww_1;
  this.i12_1 = false;
  this.j12_1 = null;
  this.k12_1 = null;
  var i = this.e12_1.w1_1;
  if (!(this.f12_1 == null)) {
    if (!(this.f12_1[i] === null) || !(this.f12_1[i] === this)) {
      this.f12_1[i] = this;
    }
  }
}
protoOf(StreamingJsonEncoder).bz = function () {
  return this.d12_1;
};
protoOf(StreamingJsonEncoder).pj = function () {
  return this.g12_1;
};
protoOf(StreamingJsonEncoder).wk = function (descriptor, index) {
  return this.h12_1.ky_1;
};
protoOf(StreamingJsonEncoder).rk = function (serializer, value) {
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.encodePolymorphically' call
    if (this.bz().ww_1.sy_1) {
      serializer.og(this, value);
      break $l$block;
    }
    var isPolymorphicSerializer = serializer instanceof AbstractPolymorphicSerializer;
    var tmp;
    if (isPolymorphicSerializer) {
      tmp = !this.bz().ww_1.az_1.equals(ClassDiscriminatorMode_NONE_getInstance());
    } else {
      var tmp_0;
      switch (this.bz().ww_1.az_1.w1_1) {
        case 0:
        case 2:
          tmp_0 = false;
          break;
        case 1:
          // Inline function 'kotlin.let' call

          var it = serializer.ng().ch();
          tmp_0 = equals(it, CLASS_getInstance()) || equals(it, OBJECT_getInstance());
          break;
        default:
          noWhenBranchMatchedException();
          break;
      }
      tmp = tmp_0;
    }
    var needDiscriminator = tmp;
    var baseClassDiscriminator = needDiscriminator ? classDiscriminator(serializer.ng(), this.bz()) : null;
    var tmp_1;
    if (isPolymorphicSerializer) {
      var casted = serializer instanceof AbstractPolymorphicSerializer ? serializer : THROW_CCE();
      $l$block_0: {
        // Inline function 'kotlin.requireNotNull' call
        if (value == null) {
          var message = 'Value for serializer ' + toString(serializer.ng()) + ' should always be non-null. Please report issue to the kotlinx.serialization tracker.';
          throw IllegalArgumentException_init_$Create$(toString(message));
        } else {
          break $l$block_0;
        }
      }
      var actual = findPolymorphicSerializer_0(casted, this, value);
      if (!(baseClassDiscriminator == null)) {
        access$validateIfSealed$tPolymorphicKt(serializer, actual, baseClassDiscriminator);
      }
      checkKind(actual.ng().ch());
      tmp_1 = isInterface(actual, SerializationStrategy) ? actual : THROW_CCE();
    } else {
      tmp_1 = serializer;
    }
    var actualSerializer = tmp_1;
    if (!(baseClassDiscriminator == null)) {
      var serialName = actualSerializer.ng().bh();
      this.j12_1 = baseClassDiscriminator;
      this.k12_1 = serialName;
    }
    actualSerializer.og(this, value);
  }
};
protoOf(StreamingJsonEncoder).aj = function (descriptor) {
  var newMode = switchMode(this.d12_1, descriptor);
  if (!(newMode.p14_1 === _Char___init__impl__6a9atx(0))) {
    this.c12_1.l10(newMode.p14_1);
    this.c12_1.g10();
  }
  var discriminator = this.j12_1;
  if (!(discriminator == null)) {
    var tmp0_elvis_lhs = this.k12_1;
    encodeTypeInfo(this, discriminator, tmp0_elvis_lhs == null ? descriptor.bh() : tmp0_elvis_lhs);
    this.j12_1 = null;
    this.k12_1 = null;
  }
  if (this.e12_1.equals(newMode)) {
    return this;
  }
  var tmp1_safe_receiver = this.f12_1;
  var tmp2_elvis_lhs = tmp1_safe_receiver == null ? null : tmp1_safe_receiver[newMode.w1_1];
  return tmp2_elvis_lhs == null ? new StreamingJsonEncoder(this.c12_1, this.d12_1, newMode, this.f12_1) : tmp2_elvis_lhs;
};
protoOf(StreamingJsonEncoder).bj = function (descriptor) {
  if (!(this.e12_1.q14_1 === _Char___init__impl__6a9atx(0))) {
    this.c12_1.h10();
    this.c12_1.j10();
    this.c12_1.l10(this.e12_1.q14_1);
  }
};
protoOf(StreamingJsonEncoder).tj = function (descriptor, index) {
  switch (this.e12_1.w1_1) {
    case 1:
      if (!this.c12_1.f10_1) {
        this.c12_1.l10(_Char___init__impl__6a9atx(44));
      }

      this.c12_1.i10();
      break;
    case 2:
      if (!this.c12_1.f10_1) {
        var tmp = this;
        var tmp_0;
        if ((index % 2 | 0) === 0) {
          this.c12_1.l10(_Char___init__impl__6a9atx(44));
          this.c12_1.i10();
          tmp_0 = true;
        } else {
          this.c12_1.l10(_Char___init__impl__6a9atx(58));
          this.c12_1.k10();
          tmp_0 = false;
        }
        tmp.i12_1 = tmp_0;
      } else {
        this.i12_1 = true;
        this.c12_1.i10();
      }

      break;
    case 3:
      if (index === 0)
        this.i12_1 = true;
      if (index === 1) {
        this.c12_1.l10(_Char___init__impl__6a9atx(44));
        this.c12_1.k10();
        this.i12_1 = false;
      }

      break;
    default:
      if (!this.c12_1.f10_1) {
        this.c12_1.l10(_Char___init__impl__6a9atx(44));
      }

      this.c12_1.i10();
      this.ek(getJsonElementName(descriptor, this.d12_1, index));
      this.c12_1.l10(_Char___init__impl__6a9atx(58));
      this.c12_1.k10();
      break;
  }
  return true;
};
protoOf(StreamingJsonEncoder).sk = function (descriptor, index, serializer, value) {
  if (!(value == null) || this.h12_1.py_1) {
    protoOf(AbstractEncoder).sk.call(this, descriptor, index, serializer, value);
  }
};
protoOf(StreamingJsonEncoder).fk = function (descriptor) {
  var tmp;
  if (get_isUnsignedNumber(descriptor)) {
    // Inline function 'kotlinx.serialization.json.internal.StreamingJsonEncoder.composerAs' call
    var tmp_0;
    var tmp_1 = this.c12_1;
    if (tmp_1 instanceof ComposerForUnsignedNumbers) {
      tmp_0 = this.c12_1;
    } else {
      var tmp0 = this.c12_1.e10_1;
      var p1 = this.i12_1;
      tmp_0 = new ComposerForUnsignedNumbers(tmp0, p1);
    }
    var tmp$ret$0 = tmp_0;
    tmp = new StreamingJsonEncoder(tmp$ret$0, this.d12_1, this.e12_1, null);
  } else if (get_isUnquotedLiteral(descriptor)) {
    // Inline function 'kotlinx.serialization.json.internal.StreamingJsonEncoder.composerAs' call
    var tmp_2;
    var tmp_3 = this.c12_1;
    if (tmp_3 instanceof ComposerForUnquotedLiterals) {
      tmp_2 = this.c12_1;
    } else {
      var tmp0_0 = this.c12_1.e10_1;
      var p1_0 = this.i12_1;
      tmp_2 = new ComposerForUnquotedLiterals(tmp0_0, p1_0);
    }
    var tmp$ret$2 = tmp_2;
    tmp = new StreamingJsonEncoder(tmp$ret$2, this.d12_1, this.e12_1, null);
  } else if (!(this.j12_1 == null)) {
    // Inline function 'kotlin.apply' call
    this.k12_1 = descriptor.bh();
    tmp = this;
  } else {
    tmp = protoOf(AbstractEncoder).fk.call(this, descriptor);
  }
  return tmp;
};
protoOf(StreamingJsonEncoder).vj = function () {
  this.c12_1.n10('null');
};
protoOf(StreamingJsonEncoder).wj = function (value) {
  if (this.i12_1) {
    this.ek(value.toString());
  } else {
    this.c12_1.w10(value);
  }
};
protoOf(StreamingJsonEncoder).xj = function (value) {
  if (this.i12_1) {
    this.ek(value.toString());
  } else {
    this.c12_1.r10(value);
  }
};
protoOf(StreamingJsonEncoder).yj = function (value) {
  if (this.i12_1) {
    this.ek(value.toString());
  } else {
    this.c12_1.t10(value);
  }
};
protoOf(StreamingJsonEncoder).zj = function (value) {
  if (this.i12_1) {
    this.ek(value.toString());
  } else {
    this.c12_1.u10(value);
  }
};
protoOf(StreamingJsonEncoder).ak = function (value) {
  if (this.i12_1) {
    this.ek(value.toString());
  } else {
    this.c12_1.v10(value);
  }
};
protoOf(StreamingJsonEncoder).bk = function (value) {
  if (this.i12_1) {
    this.ek(value.toString());
  } else {
    this.c12_1.p10(value);
  }
  if (!this.h12_1.uy_1 && !isFinite(value)) {
    throw InvalidFloatingPointEncoded(value, toString(this.c12_1.e10_1));
  }
};
protoOf(StreamingJsonEncoder).ck = function (value) {
  if (this.i12_1) {
    this.ek(value.toString());
  } else {
    this.c12_1.q10(value);
  }
  if (!this.h12_1.uy_1 && !isFinite_0(value)) {
    throw InvalidFloatingPointEncoded(value, toString(this.c12_1.e10_1));
  }
};
protoOf(StreamingJsonEncoder).dk = function (value) {
  this.ek(toString_1(value));
};
protoOf(StreamingJsonEncoder).ek = function (value) {
  return this.c12_1.x10(value);
};
function get_isUnsignedNumber(_this__u8e3s4) {
  _init_properties_StreamingJsonEncoder_kt__pn1bsi();
  return _this__u8e3s4.dh() && get_unsignedNumberDescriptors().w(_this__u8e3s4);
}
function get_isUnquotedLiteral(_this__u8e3s4) {
  _init_properties_StreamingJsonEncoder_kt__pn1bsi();
  return _this__u8e3s4.dh() && equals(_this__u8e3s4, get_jsonUnquotedLiteralDescriptor());
}
var properties_initialized_StreamingJsonEncoder_kt_6ifwwk;
function _init_properties_StreamingJsonEncoder_kt__pn1bsi() {
  if (!properties_initialized_StreamingJsonEncoder_kt_6ifwwk) {
    properties_initialized_StreamingJsonEncoder_kt_6ifwwk = true;
    unsignedNumberDescriptors = setOf([serializer_1(Companion_getInstance_0()).ng(), serializer_0(Companion_getInstance()).ng(), serializer_2(Companion_getInstance_1()).ng(), serializer_3(Companion_getInstance_2()).ng()]);
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
  _this__u8e3s4.y6(_Char___init__impl__6a9atx(34));
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
        _this__u8e3s4.l9(value, lastPos, i);
        _this__u8e3s4.x6(get_ESCAPE_STRINGS()[c]);
        lastPos = i + 1 | 0;
      }
    }
     while (inductionVariable <= last);
  if (!(lastPos === 0))
    _this__u8e3s4.l9(value, lastPos, value.length);
  else
    _this__u8e3s4.x6(value);
  _this__u8e3s4.y6(_Char___init__impl__6a9atx(34));
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
  throw JsonDecodingException_0(-1, "Failed to parse literal '" + literal.toString() + "' as " + type + ' value at element: ' + $this.b15(tag), toString($this.c15()));
}
function AbstractJsonTreeDecoder(json, value, polymorphicDiscriminator) {
  polymorphicDiscriminator = polymorphicDiscriminator === VOID ? null : polymorphicDiscriminator;
  NamedValueDecoder.call(this);
  this.x14_1 = json;
  this.y14_1 = value;
  this.z14_1 = polymorphicDiscriminator;
  this.a15_1 = this.bz().ww_1;
}
protoOf(AbstractJsonTreeDecoder).bz = function () {
  return this.x14_1;
};
protoOf(AbstractJsonTreeDecoder).n1 = function () {
  return this.y14_1;
};
protoOf(AbstractJsonTreeDecoder).pj = function () {
  return this.bz().pj();
};
protoOf(AbstractJsonTreeDecoder).c15 = function () {
  var tmp0_safe_receiver = this.dv();
  var tmp;
  if (tmp0_safe_receiver == null) {
    tmp = null;
  } else {
    // Inline function 'kotlin.let' call
    tmp = this.d15(tmp0_safe_receiver);
  }
  var tmp1_elvis_lhs = tmp;
  return tmp1_elvis_lhs == null ? this.n1() : tmp1_elvis_lhs;
};
protoOf(AbstractJsonTreeDecoder).b15 = function (currentTag) {
  return this.fv() + ('.' + currentTag);
};
protoOf(AbstractJsonTreeDecoder).cz = function () {
  return this.c15();
};
protoOf(AbstractJsonTreeDecoder).zi = function (deserializer) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.decodeSerializableValuePolymorphic' call
    var tmp;
    if (!(deserializer instanceof AbstractPolymorphicSerializer)) {
      tmp = true;
    } else {
      tmp = this.bz().ww_1.sy_1;
    }
    if (tmp) {
      tmp$ret$0 = deserializer.pg(this);
      break $l$block;
    }
    var discriminator = classDiscriminator(deserializer.ng(), this.bz());
    var tmp0 = this.cz();
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var serialName = deserializer.ng().bh();
    if (!(tmp0 instanceof JsonObject)) {
      var tmp_0 = getKClass(JsonObject).i8();
      var tmp_1 = getKClassFromExpression(tmp0).i8();
      var tmp$ret$2 = this.fv();
      throw JsonDecodingException_0(-1, 'Expected ' + tmp_0 + ', but had ' + tmp_1 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$2, toString(tmp0));
    }
    var jsonTree = tmp0;
    var tmp0_safe_receiver = jsonTree.na(discriminator);
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
    tmp$ret$0 = readPolymorphicJson(this.bz(), discriminator, jsonTree, actualSerializer);
  }
  return tmp$ret$0;
};
protoOf(AbstractJsonTreeDecoder).ev = function (parentName, childName) {
  return childName;
};
protoOf(AbstractJsonTreeDecoder).aj = function (descriptor) {
  var currentObject = this.c15();
  var tmp0_subject = descriptor.ch();
  var tmp;
  var tmp_0;
  if (equals(tmp0_subject, LIST_getInstance())) {
    tmp_0 = true;
  } else {
    tmp_0 = tmp0_subject instanceof PolymorphicKind;
  }
  if (tmp_0) {
    var tmp_1 = this.bz();
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var serialName = descriptor.bh();
    if (!(currentObject instanceof JsonArray)) {
      var tmp_2 = getKClass(JsonArray).i8();
      var tmp_3 = getKClassFromExpression(currentObject).i8();
      var tmp$ret$2 = this.fv();
      throw JsonDecodingException_0(-1, 'Expected ' + tmp_2 + ', but had ' + tmp_3 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$2, toString(currentObject));
    }
    tmp = new JsonTreeListDecoder(tmp_1, currentObject);
  } else {
    if (equals(tmp0_subject, MAP_getInstance())) {
      // Inline function 'kotlinx.serialization.json.internal.selectMapMode' call
      var this_0 = this.bz();
      var keyDescriptor = carrierDescriptor(descriptor.jh(0), this_0.pj());
      var keyKind = keyDescriptor.ch();
      var tmp_4;
      var tmp_5;
      if (keyKind instanceof PrimitiveKind) {
        tmp_5 = true;
      } else {
        tmp_5 = equals(keyKind, ENUM_getInstance());
      }
      if (tmp_5) {
        var tmp_6 = this.bz();
        // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
        // Inline function 'kotlinx.serialization.json.internal.cast' call
        var serialName_0 = descriptor.bh();
        if (!(currentObject instanceof JsonObject)) {
          var tmp_7 = getKClass(JsonObject).i8();
          var tmp_8 = getKClassFromExpression(currentObject).i8();
          var tmp$ret$7 = this.fv();
          throw JsonDecodingException_0(-1, 'Expected ' + tmp_7 + ', but had ' + tmp_8 + ' as the serialized body of ' + serialName_0 + ' at element: ' + tmp$ret$7, toString(currentObject));
        }
        tmp_4 = new JsonTreeMapDecoder(tmp_6, currentObject);
      } else {
        if (this_0.ww_1.ny_1) {
          var tmp_9 = this.bz();
          // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
          // Inline function 'kotlinx.serialization.json.internal.cast' call
          var serialName_1 = descriptor.bh();
          if (!(currentObject instanceof JsonArray)) {
            var tmp_10 = getKClass(JsonArray).i8();
            var tmp_11 = getKClassFromExpression(currentObject).i8();
            var tmp$ret$11 = this.fv();
            throw JsonDecodingException_0(-1, 'Expected ' + tmp_10 + ', but had ' + tmp_11 + ' as the serialized body of ' + serialName_1 + ' at element: ' + tmp$ret$11, toString(currentObject));
          }
          tmp_4 = new JsonTreeListDecoder(tmp_9, currentObject);
        } else {
          throw InvalidKeyKindException(keyDescriptor);
        }
      }
      tmp = tmp_4;
    } else {
      var tmp_12 = this.bz();
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
      // Inline function 'kotlinx.serialization.json.internal.cast' call
      var serialName_2 = descriptor.bh();
      if (!(currentObject instanceof JsonObject)) {
        var tmp_13 = getKClass(JsonObject).i8();
        var tmp_14 = getKClassFromExpression(currentObject).i8();
        var tmp$ret$14 = this.fv();
        throw JsonDecodingException_0(-1, 'Expected ' + tmp_13 + ', but had ' + tmp_14 + ' as the serialized body of ' + serialName_2 + ' at element: ' + tmp$ret$14, toString(currentObject));
      }
      tmp = new JsonTreeDecoder(tmp_12, currentObject, this.z14_1);
    }
  }
  return tmp;
};
protoOf(AbstractJsonTreeDecoder).bj = function (descriptor) {
};
protoOf(AbstractJsonTreeDecoder).mi = function () {
  var tmp = this.c15();
  return !(tmp instanceof JsonNull);
};
protoOf(AbstractJsonTreeDecoder).e15 = function (tag) {
  return !(this.d15(tag) === JsonNull_getInstance());
};
protoOf(AbstractJsonTreeDecoder).hv = function (tag) {
  return this.e15((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).f15 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.d15(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).i8();
      var tmp_0 = getKClassFromExpression(value).i8();
      var tmp$ret$3 = this.b15(tag);
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
protoOf(AbstractJsonTreeDecoder).iv = function (tag) {
  return this.f15((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).g15 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.d15(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).i8();
      var tmp_0 = getKClassFromExpression(value).i8();
      var tmp$ret$3 = this.b15(tag);
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
protoOf(AbstractJsonTreeDecoder).jv = function (tag) {
  return this.g15((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).h15 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.d15(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).i8();
      var tmp_0 = getKClassFromExpression(value).i8();
      var tmp$ret$3 = this.b15(tag);
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
protoOf(AbstractJsonTreeDecoder).kv = function (tag) {
  return this.h15((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).i15 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.d15(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).i8();
      var tmp_0 = getKClassFromExpression(value).i8();
      var tmp$ret$3 = this.b15(tag);
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
protoOf(AbstractJsonTreeDecoder).lv = function (tag) {
  return this.i15((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).j15 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.d15(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).i8();
      var tmp_0 = getKClassFromExpression(value).i8();
      var tmp$ret$3 = this.b15(tag);
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
protoOf(AbstractJsonTreeDecoder).mv = function (tag) {
  return this.j15((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).k15 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.d15(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).i8();
      var tmp_0 = getKClassFromExpression(value).i8();
      var tmp$ret$3 = this.b15(tag);
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
  var specialFp = this.bz().ww_1.uy_1;
  if (specialFp || isFinite(result))
    return result;
  throw InvalidFloatingPointDecoded(result, tag, toString(this.c15()));
};
protoOf(AbstractJsonTreeDecoder).nv = function (tag) {
  return this.k15((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).l15 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.d15(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).i8();
      var tmp_0 = getKClassFromExpression(value).i8();
      var tmp$ret$3 = this.b15(tag);
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
  var specialFp = this.bz().ww_1.uy_1;
  if (specialFp || isFinite_0(result))
    return result;
  throw InvalidFloatingPointDecoded(result, tag, toString(this.c15()));
};
protoOf(AbstractJsonTreeDecoder).ov = function (tag) {
  return this.l15((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).m15 = function (tag) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.d15(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).i8();
      var tmp_0 = getKClassFromExpression(value).i8();
      var tmp$ret$3 = this.b15(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'char' + ' at element: ' + tmp$ret$3, toString(value));
    }
    var literal = value;
    try {
      var tmp0_elvis_lhs = new Char(single(literal.hz()));
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
protoOf(AbstractJsonTreeDecoder).pv = function (tag) {
  return this.m15((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).n15 = function (tag) {
  // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
  // Inline function 'kotlinx.serialization.json.internal.cast' call
  var value = this.d15(tag);
  if (!(value instanceof JsonPrimitive)) {
    var tmp = getKClass(JsonPrimitive).i8();
    var tmp_0 = getKClassFromExpression(value).i8();
    var tmp$ret$2 = this.b15(tag);
    throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'string' + ' at element: ' + tmp$ret$2, toString(value));
  }
  var value_0 = value;
  if (!(value_0 instanceof JsonLiteral))
    throw JsonDecodingException_0(-1, "Expected string value for a non-null key '" + tag + "', got null literal instead at element: " + this.b15(tag), toString(this.c15()));
  if (!value_0.jz_1 && !this.bz().ww_1.my_1) {
    throw JsonDecodingException_0(-1, "String literal for key '" + tag + "' should be quoted at element: " + this.b15(tag) + ".\nUse 'isLenient = true' in 'Json {}' builder to accept non-compliant JSON.", toString(this.c15()));
  }
  return value_0.lz_1;
};
protoOf(AbstractJsonTreeDecoder).qv = function (tag) {
  return this.n15((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).o15 = function (tag, inlineDescriptor) {
  var tmp;
  if (get_isUnsignedNumber(inlineDescriptor)) {
    var tmp_0 = this.bz();
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    var tmp2 = this.d15(tag);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var serialName = inlineDescriptor.bh();
    if (!(tmp2 instanceof JsonPrimitive)) {
      var tmp_1 = getKClass(JsonPrimitive).i8();
      var tmp_2 = getKClassFromExpression(tmp2).i8();
      var tmp$ret$3 = this.b15(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp_1 + ', but had ' + tmp_2 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$3, toString(tmp2));
    }
    var lexer = StringJsonLexer_0(tmp_0, tmp2.hz());
    tmp = new JsonDecoderForUnsignedTypes(lexer, this.bz());
  } else {
    tmp = protoOf(NamedValueDecoder).rv.call(this, tag, inlineDescriptor);
  }
  return tmp;
};
protoOf(AbstractJsonTreeDecoder).rv = function (tag, inlineDescriptor) {
  return this.o15((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE(), inlineDescriptor);
};
protoOf(AbstractJsonTreeDecoder).xi = function (descriptor) {
  return !(this.dv() == null) ? protoOf(NamedValueDecoder).xi.call(this, descriptor) : (new JsonPrimitiveDecoder(this.bz(), this.n1(), this.z14_1)).xi(descriptor);
};
function coerceInputValue_0($this, descriptor, index, tag) {
  var tmp0 = $this.bz();
  var tmp$ret$0;
  $l$block_2: {
    // Inline function 'kotlinx.serialization.json.internal.tryCoerceValue' call
    var isOptional = descriptor.kh(index);
    var elementDescriptor = descriptor.jh(index);
    var tmp;
    if (isOptional && !elementDescriptor.vg()) {
      var tmp_0 = $this.d15(tag);
      tmp = tmp_0 instanceof JsonNull;
    } else {
      tmp = false;
    }
    if (tmp) {
      tmp$ret$0 = true;
      break $l$block_2;
    }
    if (equals(elementDescriptor.ch(), ENUM_getInstance())) {
      var tmp_1;
      if (elementDescriptor.vg()) {
        var tmp_2 = $this.d15(tag);
        tmp_1 = tmp_2 instanceof JsonNull;
      } else {
        tmp_1 = false;
      }
      if (tmp_1) {
        tmp$ret$0 = false;
        break $l$block_2;
      }
      var tmp_3 = $this.d15(tag);
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
      var coerceToNull = !tmp0.ww_1.py_1 && elementDescriptor.vg();
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
  $this.y15_1 = (!$this.bz().ww_1.py_1 && !descriptor.kh(index) && descriptor.jh(index).vg());
  return $this.y15_1;
}
function JsonTreeDecoder(json, value, polymorphicDiscriminator, polyDescriptor) {
  polymorphicDiscriminator = polymorphicDiscriminator === VOID ? null : polymorphicDiscriminator;
  polyDescriptor = polyDescriptor === VOID ? null : polyDescriptor;
  AbstractJsonTreeDecoder.call(this, json, value, polymorphicDiscriminator);
  this.v15_1 = value;
  this.w15_1 = polyDescriptor;
  this.x15_1 = 0;
  this.y15_1 = false;
}
protoOf(JsonTreeDecoder).n1 = function () {
  return this.v15_1;
};
protoOf(JsonTreeDecoder).rj = function (descriptor) {
  while (this.x15_1 < descriptor.eh()) {
    var _unary__edvuaz = this.x15_1;
    this.x15_1 = _unary__edvuaz + 1 | 0;
    var name = this.yu(descriptor, _unary__edvuaz);
    var index = this.x15_1 - 1 | 0;
    this.y15_1 = false;
    var tmp;
    var tmp_0;
    // Inline function 'kotlin.collections.contains' call
    // Inline function 'kotlin.collections.containsKey' call
    var this_0 = this.n1();
    if ((isInterface(this_0, KtMap) ? this_0 : THROW_CCE()).o1(name)) {
      tmp_0 = true;
    } else {
      tmp_0 = absenceIsNull(this, descriptor, index);
    }
    if (tmp_0) {
      tmp = !this.a15_1.ry_1 || !coerceInputValue_0(this, descriptor, index, name);
    } else {
      tmp = false;
    }
    if (tmp) {
      return index;
    }
  }
  return -1;
};
protoOf(JsonTreeDecoder).mi = function () {
  return !this.y15_1 && protoOf(AbstractJsonTreeDecoder).mi.call(this);
};
protoOf(JsonTreeDecoder).zu = function (descriptor, index) {
  var strategy = namingStrategy(descriptor, this.bz());
  var baseName = descriptor.gh(index);
  if (strategy == null) {
    if (!this.a15_1.vy_1)
      return baseName;
    if (this.n1().r1().w(baseName))
      return baseName;
  }
  var deserializationNamesMap_0 = deserializationNamesMap(this.bz(), descriptor);
  // Inline function 'kotlin.collections.find' call
  var tmp0 = this.n1().r1();
  var tmp$ret$1;
  $l$block: {
    // Inline function 'kotlin.collections.firstOrNull' call
    var _iterator__ex2g4s = tmp0.g();
    while (_iterator__ex2g4s.h()) {
      var element = _iterator__ex2g4s.i();
      if (deserializationNamesMap_0.q1(element) === index) {
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
  var fallbackName = strategy == null ? null : strategy.s11(descriptor, index, baseName);
  return fallbackName == null ? baseName : fallbackName;
};
protoOf(JsonTreeDecoder).d15 = function (tag) {
  return getValue(this.n1(), tag);
};
protoOf(JsonTreeDecoder).aj = function (descriptor) {
  if (descriptor === this.w15_1) {
    var tmp = this.bz();
    var tmp2 = this.c15();
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var serialName = this.w15_1.bh();
    if (!(tmp2 instanceof JsonObject)) {
      var tmp_0 = getKClass(JsonObject).i8();
      var tmp_1 = getKClassFromExpression(tmp2).i8();
      var tmp$ret$2 = this.fv();
      throw JsonDecodingException_0(-1, 'Expected ' + tmp_0 + ', but had ' + tmp_1 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$2, toString(tmp2));
    }
    return new JsonTreeDecoder(tmp, tmp2, this.z14_1, this.w15_1);
  }
  return protoOf(AbstractJsonTreeDecoder).aj.call(this, descriptor);
};
protoOf(JsonTreeDecoder).bj = function (descriptor) {
  var tmp;
  if (this.a15_1.ly_1) {
    tmp = true;
  } else {
    var tmp_0 = descriptor.ch();
    tmp = tmp_0 instanceof PolymorphicKind;
  }
  if (tmp)
    return Unit_instance;
  var strategy = namingStrategy(descriptor, this.bz());
  var tmp_1;
  if (strategy == null && !this.a15_1.vy_1) {
    tmp_1 = jsonCachedSerialNames(descriptor);
  } else if (!(strategy == null)) {
    tmp_1 = deserializationNamesMap(this.bz(), descriptor).r1();
  } else {
    var tmp_2 = jsonCachedSerialNames(descriptor);
    var tmp0_safe_receiver = get_schemaCache(this.bz()).c14(descriptor, get_JsonDeserializationNamesKey());
    // Inline function 'kotlin.collections.orEmpty' call
    var tmp0_elvis_lhs = tmp0_safe_receiver == null ? null : tmp0_safe_receiver.r1();
    var tmp$ret$0 = tmp0_elvis_lhs == null ? emptySet() : tmp0_elvis_lhs;
    tmp_1 = plus_0(tmp_2, tmp$ret$0);
  }
  var names = tmp_1;
  var _iterator__ex2g4s = this.n1().r1().g();
  while (_iterator__ex2g4s.h()) {
    var key = _iterator__ex2g4s.i();
    if (!names.w(key) && !(key === this.z14_1)) {
      throw UnknownKeyException(key, this.n1().toString());
    }
  }
};
function JsonTreeListDecoder(json, value) {
  AbstractJsonTreeDecoder.call(this, json, value);
  this.f16_1 = value;
  this.g16_1 = this.f16_1.j();
  this.h16_1 = -1;
}
protoOf(JsonTreeListDecoder).n1 = function () {
  return this.f16_1;
};
protoOf(JsonTreeListDecoder).zu = function (descriptor, index) {
  return index.toString();
};
protoOf(JsonTreeListDecoder).d15 = function (tag) {
  return this.f16_1.k(toInt(tag));
};
protoOf(JsonTreeListDecoder).rj = function (descriptor) {
  while (this.h16_1 < (this.g16_1 - 1 | 0)) {
    this.h16_1 = this.h16_1 + 1 | 0;
    return this.h16_1;
  }
  return -1;
};
function JsonPrimitiveDecoder(json, value, polymorphicDiscriminator) {
  polymorphicDiscriminator = polymorphicDiscriminator === VOID ? null : polymorphicDiscriminator;
  AbstractJsonTreeDecoder.call(this, json, value, polymorphicDiscriminator);
  this.o16_1 = value;
  this.sv('primitive');
}
protoOf(JsonPrimitiveDecoder).n1 = function () {
  return this.o16_1;
};
protoOf(JsonPrimitiveDecoder).rj = function (descriptor) {
  return 0;
};
protoOf(JsonPrimitiveDecoder).d15 = function (tag) {
  // Inline function 'kotlin.require' call
  if (!(tag === 'primitive')) {
    var message = "This input can only handle primitives with 'primitive' tag";
    throw IllegalArgumentException_init_$Create$(toString(message));
  }
  return this.o16_1;
};
function JsonTreeMapDecoder(json, value) {
  JsonTreeDecoder.call(this, json, value);
  this.z16_1 = value;
  this.a17_1 = toList(this.z16_1.r1());
  this.b17_1 = imul(this.a17_1.j(), 2);
  this.c17_1 = -1;
}
protoOf(JsonTreeMapDecoder).n1 = function () {
  return this.z16_1;
};
protoOf(JsonTreeMapDecoder).zu = function (descriptor, index) {
  var i = index / 2 | 0;
  return this.a17_1.k(i);
};
protoOf(JsonTreeMapDecoder).rj = function (descriptor) {
  while (this.c17_1 < (this.b17_1 - 1 | 0)) {
    this.c17_1 = this.c17_1 + 1 | 0;
    return this.c17_1;
  }
  return -1;
};
protoOf(JsonTreeMapDecoder).d15 = function (tag) {
  return (this.c17_1 % 2 | 0) === 0 ? JsonPrimitive_0(tag) : getValue(this.z16_1, tag);
};
protoOf(JsonTreeMapDecoder).bj = function (descriptor) {
};
function readPolymorphicJson(_this__u8e3s4, discriminator, element, deserializer) {
  return (new JsonTreeDecoder(_this__u8e3s4, element, discriminator, deserializer.ng())).zi(deserializer);
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
  this.p14_1 = begin;
  this.q14_1 = end;
}
function switchMode(_this__u8e3s4, desc) {
  var tmp0_subject = desc.ch();
  var tmp;
  if (tmp0_subject instanceof PolymorphicKind) {
    tmp = WriteMode_POLY_OBJ_getInstance();
  } else {
    if (equals(tmp0_subject, LIST_getInstance())) {
      tmp = WriteMode_LIST_getInstance();
    } else {
      if (equals(tmp0_subject, MAP_getInstance())) {
        // Inline function 'kotlinx.serialization.json.internal.selectMapMode' call
        var keyDescriptor = carrierDescriptor(desc.jh(0), _this__u8e3s4.pj());
        var keyKind = keyDescriptor.ch();
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
          if (_this__u8e3s4.ww_1.ny_1) {
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
  if (equals(_this__u8e3s4.ch(), CONTEXTUAL_getInstance())) {
    var tmp0_safe_receiver = getContextualDescriptor(module_0, _this__u8e3s4);
    var tmp1_elvis_lhs = tmp0_safe_receiver == null ? null : carrierDescriptor(tmp0_safe_receiver, module_0);
    tmp = tmp1_elvis_lhs == null ? _this__u8e3s4 : tmp1_elvis_lhs;
  } else if (_this__u8e3s4.dh()) {
    tmp = carrierDescriptor(_this__u8e3s4.jh(0), module_0);
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
  $this.d17(lastPosition, current);
  return appendEsc($this, current + 1 | 0);
}
function decodedString($this, lastPosition, currentPosition) {
  $this.d17(lastPosition, currentPosition);
  var result = $this.px_1.toString();
  $this.px_1.q9(0);
  return result;
}
function takePeeked($this) {
  // Inline function 'kotlin.also' call
  var this_0 = ensureNotNull($this.ox_1);
  $this.ox_1 = null;
  return this_0;
}
function wasUnquotedString($this) {
  return !(charSequenceGet($this.e17(), $this.mx_1 - 1 | 0) === _Char___init__impl__6a9atx(34));
}
function appendEsc($this, startPosition) {
  var currentPosition = startPosition;
  currentPosition = $this.f17(currentPosition);
  if (currentPosition === -1) {
    $this.o11('Expected escape sequence to continue, got EOF');
  }
  var tmp = $this.e17();
  var _unary__edvuaz = currentPosition;
  currentPosition = _unary__edvuaz + 1 | 0;
  var currentChar = charSequenceGet(tmp, _unary__edvuaz);
  if (currentChar === _Char___init__impl__6a9atx(117)) {
    return appendHex($this, $this.e17(), currentPosition);
  }
  // Inline function 'kotlin.code' call
  var tmp$ret$0 = Char__toInt_impl_vasixd(currentChar);
  var c = escapeToChar(tmp$ret$0);
  if (c === _Char___init__impl__6a9atx(0)) {
    $this.o11("Invalid escaped char '" + toString_1(currentChar) + "'");
  }
  $this.px_1.y6(c);
  return currentPosition;
}
function appendHex($this, source, startPos) {
  if ((startPos + 4 | 0) >= charSequenceLength(source)) {
    $this.mx_1 = startPos;
    $this.g17();
    if (($this.mx_1 + 4 | 0) >= charSequenceLength(source)) {
      $this.o11('Unexpected EOF during unicode escape');
    }
    return appendHex($this, source, $this.mx_1);
  }
  $this.px_1.y6(numberToChar((((fromHexChar($this, source, startPos) << 12) + (fromHexChar($this, source, startPos + 1 | 0) << 8) | 0) + (fromHexChar($this, source, startPos + 2 | 0) << 4) | 0) + fromHexChar($this, source, startPos + 3 | 0) | 0));
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
    $this.o11("Invalid toHexChar char '" + toString_1(character) + "' in unicode escape");
  }
  return tmp;
}
function consumeBoolean2($this, start) {
  var current = $this.f17(start);
  if (current >= charSequenceLength($this.e17()) || current === -1) {
    $this.o11('EOF');
  }
  var tmp = $this.e17();
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
      $this.o11("Expected valid boolean literal prefix, but had '" + $this.t12() + "'");
    }
  }
  return tmp_0;
}
function consumeBooleanLiteral($this, literalSuffix, current) {
  if ((charSequenceLength($this.e17()) - current | 0) < literalSuffix.length) {
    $this.o11('Unexpected end of boolean literal');
  }
  var inductionVariable = 0;
  var last = charSequenceLength(literalSuffix) - 1 | 0;
  if (inductionVariable <= last)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      var expected = charCodeAt(literalSuffix, i);
      var actual = charSequenceGet($this.e17(), current + i | 0);
      // Inline function 'kotlin.code' call
      var tmp = Char__toInt_impl_vasixd(expected);
      // Inline function 'kotlin.code' call
      if (!(tmp === (Char__toInt_impl_vasixd(actual) | 32))) {
        $this.o11("Expected valid boolean literal prefix, but had '" + $this.t12() + "'");
      }
    }
     while (inductionVariable <= last);
  $this.mx_1 = current + literalSuffix.length | 0;
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
  this.mx_1 = 0;
  this.nx_1 = new JsonPath();
  this.ox_1 = null;
  this.px_1 = StringBuilder_init_$Create$();
}
protoOf(AbstractJsonLexer).g17 = function () {
};
protoOf(AbstractJsonLexer).f14 = function () {
  var current = this.h17();
  var source = this.e17();
  if (current >= charSequenceLength(source) || current === -1)
    return false;
  if (charSequenceGet(source, current) === _Char___init__impl__6a9atx(44)) {
    this.mx_1 = this.mx_1 + 1 | 0;
    return true;
  }
  return false;
};
protoOf(AbstractJsonLexer).i17 = function (c) {
  return c === _Char___init__impl__6a9atx(125) || c === _Char___init__impl__6a9atx(93) || (c === _Char___init__impl__6a9atx(58) || c === _Char___init__impl__6a9atx(44)) ? false : true;
};
protoOf(AbstractJsonLexer).qx = function () {
  var nextToken = this.v12();
  if (!(nextToken === 10)) {
    this.o11('Expected EOF after parsing, but had ' + toString_1(charSequenceGet(this.e17(), this.mx_1 - 1 | 0)) + ' instead');
  }
};
protoOf(AbstractJsonLexer).p12 = function (expected) {
  var token = this.v12();
  if (!(token === expected)) {
    this.j17(expected);
  }
  return token;
};
protoOf(AbstractJsonLexer).k17 = function (expected) {
  if (this.mx_1 > 0 && expected === _Char___init__impl__6a9atx(34)) {
    var tmp$ret$0;
    $l$block: {
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.withPositionRollback' call
      var snapshot = this.mx_1;
      try {
        this.mx_1 = this.mx_1 - 1 | 0;
        tmp$ret$0 = this.t12();
        break $l$block;
      }finally {
        this.mx_1 = snapshot;
      }
    }
    var inputLiteral = tmp$ret$0;
    if (inputLiteral === 'null') {
      this.n11("Expected string literal but 'null' literal was found", this.mx_1 - 1 | 0, "Use 'coerceInputValues = true' in 'Json {}' builder to coerce nulls if property has a default value.");
    }
  }
  this.j17(charToTokenClass(expected));
};
protoOf(AbstractJsonLexer).l17 = function (expectedToken, wasConsumed) {
  var expected = tokenDescription(expectedToken);
  var position = wasConsumed ? this.mx_1 - 1 | 0 : this.mx_1;
  var s = this.mx_1 === charSequenceLength(this.e17()) || position < 0 ? 'EOF' : toString_1(charSequenceGet(this.e17(), position));
  this.o11('Expected ' + expected + ", but had '" + s + "' instead", position);
};
protoOf(AbstractJsonLexer).j17 = function (expectedToken, wasConsumed, $super) {
  wasConsumed = wasConsumed === VOID ? true : wasConsumed;
  return $super === VOID ? this.l17(expectedToken, wasConsumed) : $super.l17.call(this, expectedToken, wasConsumed);
};
protoOf(AbstractJsonLexer).q12 = function () {
  var source = this.e17();
  var cpos = this.mx_1;
  $l$loop_0: while (true) {
    cpos = this.f17(cpos);
    if (cpos === -1)
      break $l$loop_0;
    var ch = charSequenceGet(source, cpos);
    if (ch === _Char___init__impl__6a9atx(32) || ch === _Char___init__impl__6a9atx(10) || ch === _Char___init__impl__6a9atx(13) || ch === _Char___init__impl__6a9atx(9)) {
      cpos = cpos + 1 | 0;
      continue $l$loop_0;
    }
    this.mx_1 = cpos;
    return charToTokenClass(ch);
  }
  this.mx_1 = cpos;
  return 10;
};
protoOf(AbstractJsonLexer).g14 = function (doConsume) {
  var current = this.h17();
  current = this.f17(current);
  var len = charSequenceLength(this.e17()) - current | 0;
  if (len < 4 || current === -1)
    return false;
  var inductionVariable = 0;
  if (inductionVariable <= 3)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      if (!(charCodeAt('null', i) === charSequenceGet(this.e17(), current + i | 0)))
        return false;
    }
     while (inductionVariable <= 3);
  if (len > 4 && charToTokenClass(charSequenceGet(this.e17(), current + 4 | 0)) === 0)
    return false;
  if (doConsume) {
    this.mx_1 = current + 4 | 0;
  }
  return true;
};
protoOf(AbstractJsonLexer).r14 = function (doConsume, $super) {
  doConsume = doConsume === VOID ? true : doConsume;
  return $super === VOID ? this.g14(doConsume) : $super.g14.call(this, doConsume);
};
protoOf(AbstractJsonLexer).h14 = function (isLenient) {
  var token = this.q12();
  var tmp;
  if (isLenient) {
    if (!(token === 1) && !(token === 0))
      return null;
    tmp = this.t12();
  } else {
    if (!(token === 1))
      return null;
    tmp = this.s12();
  }
  var string = tmp;
  this.ox_1 = string;
  return string;
};
protoOf(AbstractJsonLexer).m17 = function () {
  this.ox_1 = null;
};
protoOf(AbstractJsonLexer).n17 = function (startPos, endPos) {
  // Inline function 'kotlin.text.substring' call
  var this_0 = this.e17();
  return toString(charSequenceSubSequence(this_0, startPos, endPos));
};
protoOf(AbstractJsonLexer).s12 = function () {
  if (!(this.ox_1 == null)) {
    return takePeeked(this);
  }
  return this.k14();
};
protoOf(AbstractJsonLexer).consumeString2 = function (source, startPosition, current) {
  var currentPosition = current;
  var lastPosition = startPosition;
  var char = charSequenceGet(source, currentPosition);
  var usedAppend = false;
  while (!(char === _Char___init__impl__6a9atx(34))) {
    if (char === _Char___init__impl__6a9atx(92)) {
      usedAppend = true;
      currentPosition = this.f17(appendEscape(this, lastPosition, currentPosition));
      if (currentPosition === -1) {
        this.o11('Unexpected EOF', currentPosition);
      }
      lastPosition = currentPosition;
    } else {
      currentPosition = currentPosition + 1 | 0;
      if (currentPosition >= charSequenceLength(source)) {
        usedAppend = true;
        this.d17(lastPosition, currentPosition);
        currentPosition = this.f17(currentPosition);
        if (currentPosition === -1) {
          this.o11('Unexpected EOF', currentPosition);
        }
        lastPosition = currentPosition;
      }
    }
    char = charSequenceGet(source, currentPosition);
  }
  var tmp;
  if (!usedAppend) {
    tmp = this.n17(lastPosition, currentPosition);
  } else {
    tmp = decodedString(this, lastPosition, currentPosition);
  }
  var string = tmp;
  this.mx_1 = currentPosition + 1 | 0;
  return string;
};
protoOf(AbstractJsonLexer).l14 = function () {
  var result = this.t12();
  if (result === 'null' && wasUnquotedString(this)) {
    this.o11("Unexpected 'null' value instead of string literal");
  }
  return result;
};
protoOf(AbstractJsonLexer).t12 = function () {
  if (!(this.ox_1 == null)) {
    return takePeeked(this);
  }
  var current = this.h17();
  if (current >= charSequenceLength(this.e17()) || current === -1) {
    this.o11('EOF', current);
  }
  var token = charToTokenClass(charSequenceGet(this.e17(), current));
  if (token === 1) {
    return this.s12();
  }
  if (!(token === 0)) {
    this.o11('Expected beginning of the string, but got ' + toString_1(charSequenceGet(this.e17(), current)));
  }
  var usedAppend = false;
  while (charToTokenClass(charSequenceGet(this.e17(), current)) === 0) {
    current = current + 1 | 0;
    if (current >= charSequenceLength(this.e17())) {
      usedAppend = true;
      this.d17(this.mx_1, current);
      var eof = this.f17(current);
      if (eof === -1) {
        this.mx_1 = current;
        return decodedString(this, 0, 0);
      } else {
        current = eof;
      }
    }
  }
  var tmp;
  if (!usedAppend) {
    tmp = this.n17(this.mx_1, current);
  } else {
    tmp = decodedString(this, this.mx_1, current);
  }
  var result = tmp;
  this.mx_1 = current;
  return result;
};
protoOf(AbstractJsonLexer).d17 = function (fromIndex, toIndex) {
  this.px_1.l9(this.e17(), fromIndex, toIndex);
};
protoOf(AbstractJsonLexer).j14 = function (allowLenientStrings) {
  // Inline function 'kotlin.collections.mutableListOf' call
  var tokenStack = ArrayList_init_$Create$();
  var lastToken = this.q12();
  if (!(lastToken === 8) && !(lastToken === 6)) {
    this.t12();
    return Unit_instance;
  }
  $l$loop: while (true) {
    lastToken = this.q12();
    if (lastToken === 1) {
      if (allowLenientStrings)
        this.t12();
      else
        this.k14();
      continue $l$loop;
    }
    var tmp0_subject = lastToken;
    if (tmp0_subject === 8 || tmp0_subject === 6) {
      tokenStack.e(lastToken);
    } else if (tmp0_subject === 9) {
      if (!(last(tokenStack) === 8))
        throw JsonDecodingException_0(this.mx_1, 'found ] instead of } at path: ' + this.nx_1.toString(), this.e17());
      removeLast(tokenStack);
    } else if (tmp0_subject === 7) {
      if (!(last(tokenStack) === 6))
        throw JsonDecodingException_0(this.mx_1, 'found } instead of ] at path: ' + this.nx_1.toString(), this.e17());
      removeLast(tokenStack);
    } else if (tmp0_subject === 10) {
      this.o11('Unexpected end of input due to malformed JSON during ignoring unknown keys');
    }
    this.v12();
    if (tokenStack.j() === 0)
      return Unit_instance;
  }
};
protoOf(AbstractJsonLexer).toString = function () {
  return "JsonReader(source='" + toString(this.e17()) + "', currentPosition=" + this.mx_1 + ')';
};
protoOf(AbstractJsonLexer).i14 = function (key) {
  var processed = this.n17(0, this.mx_1);
  var lastIndexOf_0 = lastIndexOf(processed, key);
  this.n11("Encountered an unknown key '" + key + "'", lastIndexOf_0, "Use 'ignoreUnknownKeys = true' in 'Json {}' builder to ignore unknown keys.");
};
protoOf(AbstractJsonLexer).n11 = function (message, position, hint) {
  var tmp;
  // Inline function 'kotlin.text.isEmpty' call
  if (charSequenceLength(hint) === 0) {
    tmp = '';
  } else {
    tmp = '\n' + hint;
  }
  var hintMessage = tmp;
  throw JsonDecodingException_0(position, message + ' at path: ' + this.nx_1.b12() + hintMessage, this.e17());
};
protoOf(AbstractJsonLexer).o11 = function (message, position, hint, $super) {
  position = position === VOID ? this.mx_1 : position;
  hint = hint === VOID ? '' : hint;
  return $super === VOID ? this.n11(message, position, hint) : $super.n11.call(this, message, position, hint);
};
protoOf(AbstractJsonLexer).mz = function () {
  var current = this.h17();
  current = this.f17(current);
  if (current >= charSequenceLength(this.e17()) || current === -1) {
    this.o11('EOF');
  }
  var tmp;
  if (charSequenceGet(this.e17(), current) === _Char___init__impl__6a9atx(34)) {
    current = current + 1 | 0;
    if (current === charSequenceLength(this.e17())) {
      this.o11('EOF');
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
  $l$loop_4: while (!(current === charSequenceLength(this.e17()))) {
    var ch = charSequenceGet(this.e17(), current);
    if ((ch === _Char___init__impl__6a9atx(101) || ch === _Char___init__impl__6a9atx(69)) && !hasExponent) {
      if (current === start) {
        this.o11('Unexpected symbol ' + toString_1(ch) + ' in numeric literal');
      }
      isExponentPositive = true;
      hasExponent = true;
      current = current + 1 | 0;
      continue $l$loop_4;
    }
    if (ch === _Char___init__impl__6a9atx(45) && hasExponent) {
      if (current === start) {
        this.o11("Unexpected symbol '-' in numeric literal");
      }
      isExponentPositive = false;
      current = current + 1 | 0;
      continue $l$loop_4;
    }
    if (ch === _Char___init__impl__6a9atx(43) && hasExponent) {
      if (current === start) {
        this.o11("Unexpected symbol '+' in numeric literal");
      }
      isExponentPositive = true;
      current = current + 1 | 0;
      continue $l$loop_4;
    }
    if (ch === _Char___init__impl__6a9atx(45)) {
      if (!(current === start)) {
        this.o11("Unexpected symbol '-' in numeric literal");
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
      this.o11("Unexpected symbol '" + toString_1(ch) + "' in numeric literal");
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
      this.o11('Numeric value overflow');
    }
  }
  var hasChars = !(current === start);
  if (start === current || (isNegative && start === (current - 1 | 0))) {
    this.o11('Expected numeric literal');
  }
  if (hasQuotation) {
    if (!hasChars) {
      this.o11('EOF');
    }
    if (!(charSequenceGet(this.e17(), current) === _Char___init__impl__6a9atx(34))) {
      this.o11('Expected closing quotation mark');
    }
    current = current + 1 | 0;
  }
  this.mx_1 = current;
  if (hasExponent) {
    var doubleAccumulator = toNumber(accumulator) * consumeNumericLiteral$calculateExponent(exponentAccumulator, isExponentPositive);
    if (doubleAccumulator > toNumber(new Long(-1, 2147483647)) || doubleAccumulator < toNumber(new Long(0, -2147483648))) {
      this.o11('Numeric value overflow');
    }
    // Inline function 'kotlin.math.floor' call
    if (!(Math.floor(doubleAccumulator) === doubleAccumulator)) {
      this.o11("Can't convert " + doubleAccumulator + ' to Long');
    }
    accumulator = numberToLong(doubleAccumulator);
  }
  var tmp_0;
  if (isNegative) {
    tmp_0 = accumulator;
  } else if (!equalsLong(accumulator, new Long(0, -2147483648))) {
    tmp_0 = negate(accumulator);
  } else {
    this.o11('Numeric value overflow');
  }
  return tmp_0;
};
protoOf(AbstractJsonLexer).s14 = function () {
  var current = this.h17();
  if (current === charSequenceLength(this.e17())) {
    this.o11('EOF');
  }
  var tmp;
  if (charSequenceGet(this.e17(), current) === _Char___init__impl__6a9atx(34)) {
    current = current + 1 | 0;
    tmp = true;
  } else {
    tmp = false;
  }
  var hasQuotation = tmp;
  var result = consumeBoolean2(this, current);
  if (hasQuotation) {
    if (this.mx_1 === charSequenceLength(this.e17())) {
      this.o11('EOF');
    }
    if (!(charSequenceGet(this.e17(), this.mx_1) === _Char___init__impl__6a9atx(34))) {
      this.o11('Expected closing quotation mark');
    }
    this.mx_1 = this.mx_1 + 1 | 0;
  }
  return result;
};
function charToTokenClass(c) {
  var tmp;
  // Inline function 'kotlin.code' call
  if (Char__toInt_impl_vasixd(c) < 126) {
    var tmp_0 = CharMappings_getInstance().p17_1;
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
  return c < 117 ? CharMappings_getInstance().o17_1[c] : _Char___init__impl__6a9atx(0);
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
    $this.o17_1[tmp$ret$0] = numberToChar(c);
  }
}
function initC2ESC_0($this, c, esc) {
  // Inline function 'kotlin.code' call
  var tmp$ret$0 = Char__toInt_impl_vasixd(c);
  return initC2ESC($this, tmp$ret$0, esc);
}
function initC2TC($this, c, cl) {
  $this.p17_1[c] = cl;
}
function initC2TC_0($this, c, cl) {
  // Inline function 'kotlin.code' call
  var tmp$ret$0 = Char__toInt_impl_vasixd(c);
  return initC2TC($this, tmp$ret$0, cl);
}
function CharMappings() {
  CharMappings_instance = this;
  this.o17_1 = charArray(117);
  this.p17_1 = new Int8Array(126);
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
protoOf(StringJsonLexerWithComments).v12 = function () {
  var source = this.e17();
  var cpos = this.h17();
  if (cpos >= source.length || cpos === -1)
    return 10;
  this.mx_1 = cpos + 1 | 0;
  return charToTokenClass(charCodeAt(source, cpos));
};
protoOf(StringJsonLexerWithComments).r12 = function () {
  var current = this.h17();
  if (current >= this.e17().length || current === -1)
    return false;
  return this.i17(charCodeAt(this.e17(), current));
};
protoOf(StringJsonLexerWithComments).e14 = function (expected) {
  var source = this.e17();
  var current = this.h17();
  if (current >= source.length || current === -1) {
    this.mx_1 = -1;
    this.k17(expected);
  }
  var c = charCodeAt(source, current);
  this.mx_1 = current + 1 | 0;
  if (c === expected)
    return Unit_instance;
  else {
    this.k17(expected);
  }
};
protoOf(StringJsonLexerWithComments).q12 = function () {
  var source = this.e17();
  var cpos = this.h17();
  if (cpos >= source.length || cpos === -1)
    return 10;
  this.mx_1 = cpos;
  return charToTokenClass(charCodeAt(source, cpos));
};
protoOf(StringJsonLexerWithComments).h17 = function () {
  var current = this.mx_1;
  if (current === -1)
    return current;
  var source = this.e17();
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
          this.mx_1 = source.length;
          this.o11('Expected end of the block comment: "*/", but had EOF instead');
        } else {
          current = current + 2 | 0;
        }
        continue $l$loop_1;
      }
    }
    break $l$loop_1;
  }
  this.mx_1 = current;
  return current;
};
function StringJsonLexer(source) {
  AbstractJsonLexer.call(this);
  this.z17_1 = source;
}
protoOf(StringJsonLexer).e17 = function () {
  return this.z17_1;
};
protoOf(StringJsonLexer).f17 = function (position) {
  return position < this.e17().length ? position : -1;
};
protoOf(StringJsonLexer).v12 = function () {
  var source = this.e17();
  var cpos = this.mx_1;
  $l$loop: while (!(cpos === -1) && cpos < source.length) {
    var _unary__edvuaz = cpos;
    cpos = _unary__edvuaz + 1 | 0;
    var c = charCodeAt(source, _unary__edvuaz);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.isWs' call
    if (c === _Char___init__impl__6a9atx(32) || c === _Char___init__impl__6a9atx(10) || c === _Char___init__impl__6a9atx(13) || c === _Char___init__impl__6a9atx(9))
      continue $l$loop;
    this.mx_1 = cpos;
    return charToTokenClass(c);
  }
  this.mx_1 = source.length;
  return 10;
};
protoOf(StringJsonLexer).r12 = function () {
  var current = this.mx_1;
  if (current === -1)
    return false;
  var source = this.e17();
  $l$loop: while (current < source.length) {
    var c = charCodeAt(source, current);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.isWs' call
    if (c === _Char___init__impl__6a9atx(32) || c === _Char___init__impl__6a9atx(10) || c === _Char___init__impl__6a9atx(13) || c === _Char___init__impl__6a9atx(9)) {
      current = current + 1 | 0;
      continue $l$loop;
    }
    this.mx_1 = current;
    return this.i17(c);
  }
  this.mx_1 = current;
  return false;
};
protoOf(StringJsonLexer).h17 = function () {
  var current = this.mx_1;
  if (current === -1)
    return current;
  var source = this.e17();
  $l$loop: while (current < source.length) {
    var c = charCodeAt(source, current);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.isWs' call
    if (c === _Char___init__impl__6a9atx(32) || c === _Char___init__impl__6a9atx(10) || c === _Char___init__impl__6a9atx(13) || c === _Char___init__impl__6a9atx(9)) {
      current = current + 1 | 0;
    } else {
      break $l$loop;
    }
  }
  this.mx_1 = current;
  return current;
};
protoOf(StringJsonLexer).e14 = function (expected) {
  if (this.mx_1 === -1) {
    this.k17(expected);
  }
  var source = this.e17();
  var cpos = this.mx_1;
  $l$loop: while (cpos < source.length) {
    var _unary__edvuaz = cpos;
    cpos = _unary__edvuaz + 1 | 0;
    var c = charCodeAt(source, _unary__edvuaz);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.isWs' call
    if (c === _Char___init__impl__6a9atx(32) || c === _Char___init__impl__6a9atx(10) || c === _Char___init__impl__6a9atx(13) || c === _Char___init__impl__6a9atx(9))
      continue $l$loop;
    this.mx_1 = cpos;
    if (c === expected)
      return Unit_instance;
    this.k17(expected);
  }
  this.mx_1 = -1;
  this.k17(expected);
};
protoOf(StringJsonLexer).k14 = function () {
  this.e14(_Char___init__impl__6a9atx(34));
  var current = this.mx_1;
  var closingQuote = indexOf_0(this.e17(), _Char___init__impl__6a9atx(34), current);
  if (closingQuote === -1) {
    this.t12();
    this.l17(1, false);
  }
  var inductionVariable = current;
  if (inductionVariable < closingQuote)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      if (charCodeAt(this.e17(), i) === _Char___init__impl__6a9atx(92)) {
        return this.consumeString2(this.e17(), this.mx_1, i);
      }
    }
     while (inductionVariable < closingQuote);
  this.mx_1 = closingQuote + 1 | 0;
  return substring(this.e17(), current, closingQuote);
};
protoOf(StringJsonLexer).m14 = function (keyToMatch, isLenient) {
  var positionSnapshot = this.mx_1;
  try {
    if (!(this.v12() === 6))
      return null;
    var firstKey = this.h14(isLenient);
    if (!(firstKey === keyToMatch))
      return null;
    this.m17();
    if (!(this.v12() === 5))
      return null;
    return this.h14(isLenient);
  }finally {
    this.mx_1 = positionSnapshot;
    this.m17();
  }
};
function StringJsonLexer_0(json, source) {
  return !json.ww_1.zy_1 ? new StringJsonLexer(source) : new StringJsonLexerWithComments(source);
}
function get_schemaCache(_this__u8e3s4) {
  return _this__u8e3s4.yw_1;
}
function JsonToStringWriter() {
  this.cx_1 = StringBuilder_init_$Create$_0(128);
}
protoOf(JsonToStringWriter).s10 = function (value) {
  this.cx_1.o9(value);
};
protoOf(JsonToStringWriter).m10 = function (char) {
  this.cx_1.y6(char);
};
protoOf(JsonToStringWriter).o10 = function (text) {
  this.cx_1.x6(text);
};
protoOf(JsonToStringWriter).y10 = function (text) {
  printQuoted(this.cx_1, text);
};
protoOf(JsonToStringWriter).dx = function () {
  this.cx_1.r9();
};
protoOf(JsonToStringWriter).toString = function () {
  return this.cx_1.toString();
};
function createMapForCache(initialCapacity) {
  return HashMap_init_$Create$(initialCapacity);
}
//region block: post-declaration
protoOf(defer$1).vg = get_isNullable;
protoOf(defer$1).dh = get_isInline;
protoOf(defer$1).fh = get_annotations;
protoOf(PolymorphismValidator).sw = contextual;
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


