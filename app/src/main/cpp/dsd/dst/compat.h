#pragma once
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <limits.h>

#define AVERROR_INVALIDDATA (-1)
#define AVERROR_PATCHWELCOME (-2)
#define SAMIN(a,b) ((a) < (b) ? (a) : (b))
#define FFABS(a) ((a) < 0 ? -(a) : (a))
#define sa_always_inline inline
#define sa_free free
#define DECLARE_ALIGNED(n,t,v) t __attribute__((aligned(n))) v
static inline void* sa_calloc(size_t count, size_t size) {
    if (!count || !size || count > SIZE_MAX / size) return NULL;
    void* memory = NULL;
    if (posix_memalign(&memory, 64, count * size)) return NULL;
    memset(memory, 0, count * size);
    return memory;
}
static inline int sa_clip(int v, int low, int high) { return v < low ? low : v > high ? high : v; }
static inline int sa_log2(unsigned v) { return v ? 31 - __builtin_clz(v) : 0; }
static inline uint64_t SA_RL64A(const void* p) { uint64_t v; memcpy(&v, p, 8); return v; }
static inline void SA_WL64A(void* p, uint64_t v) { memcpy(p, &v, 8); }

typedef struct { const uint8_t* data; int bits, at, error, padding; } GetBitContext;
static inline int init_get_bits8(GetBitContext* b, const uint8_t* data, int bytes) {
    if (!data || bytes < 1 || bytes > 1048576) return -1;
    b->data = data; b->bits = bytes * 8; b->at = 0; b->error = 0; b->padding = 0;
    return 0;
}
static inline int get_bits_left(GetBitContext* b) { return b->bits - b->at; }
static inline unsigned get_bits(GetBitContext* b, int count) {
    if (count < 0 || count > 24 || count > get_bits_left(b)) { b->error = 1; return 0; }
    unsigned value = 0;
    for (int i = 0; i < count; ++i, ++b->at) value = (value << 1) | ((b->data[b->at / 8] >> (7 - b->at % 8)) & 1);
    return value;
}
static inline unsigned get_bits1(GetBitContext* b) { return get_bits(b, 1); }
static inline void skip_bits1(GetBitContext* b) { (void)get_bits1(b); }
static inline int get_sbits(GetBitContext* b, int count) {
    const unsigned value = get_bits(b, count), sign = 1u << (count - 1);
    return (int)(value ^ sign) - (int)sign;
}
static inline uint8_t reverse_byte(unsigned value) {
    value = ((value & 0x55) << 1) | ((value >> 1) & 0x55);
    value = ((value & 0x33) << 2) | ((value >> 2) & 0x33);
    return (uint8_t)((value << 4) | (value >> 4));
}
