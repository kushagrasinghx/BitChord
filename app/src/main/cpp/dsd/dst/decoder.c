/*
 * Direct Stream Transfer (DST) decoder
 * Copyright (c) 2014 Peter Ross <pross@xvid.org>
 *
 * This file is part of FFmpeg.
 *
 * FFmpeg is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * FFmpeg is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with FFmpeg; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

/**
 * @file decoder.c
 * @brief DST decoder - single-frame public API and core decoding algorithm
 *
 * ISO/IEC 14496-3 Part 3 Subpart 10: Technical description of lossless
 * coding of oversampled audio
 *
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */

#include "decoder.h"
#include "compat.h"


#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

/*============================================================================
 * Constants
 *============================================================================*/

#define DST_MAX_CHANNELS 6
#define DST_MAX_ELEMENTS (2 * DST_MAX_CHANNELS)

#define DSD_FS44(sample_rate) (sample_rate / 44100)
#define DST_SAMPLES_PER_FRAME(sample_rate) (588 * DSD_FS44(sample_rate))

/*============================================================================
 * Internal types
 *============================================================================*/

typedef struct dst_arith_coder_s {
    unsigned int a;
    unsigned int c;
} dst_arith_coder_t;

typedef struct dst_table_s {
    unsigned int elements;
    unsigned int length[DST_MAX_ELEMENTS];
    int coeff[DST_MAX_ELEMENTS][128];
} dst_table_t;

#ifdef _MSC_VER
#pragma warning(push)
#pragma warning(disable: 4324) /* structure was padded due to alignment specifier */
#endif

struct dst_decoder_s {
    DECLARE_ALIGNED(64, uint8_t, status)[DST_MAX_CHANNELS][16];
    DECLARE_ALIGNED(16, int16_t, filter)[DST_MAX_ELEMENTS][16][256];
    int channels;
    int sample_rate;
    GetBitContext gb;
    dst_arith_coder_t ac;
    dst_table_t fsets, probs;
};

#ifdef _MSC_VER
#pragma warning(pop)
#endif

/*============================================================================
 * Prediction coefficient tables
 *============================================================================*/

static const int8_t fsets_code_pred_coeff[3][3] = {
    {  -8 },
    { -16,  8 },
    {  -9, -5, 6 },
};

static const int8_t probs_code_pred_coeff[3][3] = {
    {  -8 },
    { -16,  8 },
    { -24, 24, -8 },
};

/*============================================================================
 * Internal decoding functions
 *============================================================================*/

static int read_map(GetBitContext *gb, dst_table_t *t, unsigned int map[DST_MAX_CHANNELS], int channels)
{
    int ch;
    t->elements = 1;
    map[0] = 0;
    if (!get_bits1(gb)) {
        for (ch = 1; ch < channels; ch++) {
            int bits = sa_log2(t->elements) + 1;
            map[ch] = get_bits(gb, bits);
            if (map[ch] == t->elements) {
                t->elements++;
                if (t->elements >= DST_MAX_ELEMENTS)
                    return AVERROR_INVALIDDATA;
            } else if (map[ch] > t->elements) {
                return AVERROR_INVALIDDATA;
            }
        }
    } else {
        memset(map, 0, sizeof(*map) * DST_MAX_CHANNELS);
    }
    return 0;
}

/**
 * Read unsigned Golomb-Rice code (FFV1).
 */
static inline int get_ur_golomb(GetBitContext *gb, int k, int limit, int esc_len)
{
    unsigned zeros = 0;
    int terminated = 0;
    (void)limit; (void)esc_len;
    while (get_bits_left(gb) > 0) {
        if (get_bits1(gb)) { terminated = 1; break; }
        if (++zeros > 65535) { gb->error = 1; return 0; }
    }
    if (!terminated || get_bits_left(gb) < k || gb->error) { gb->error = 1; return 0; }
    return (int)((zeros << k) | get_bits(gb, k));
}

static sa_always_inline int get_sr_golomb_dst(GetBitContext *gb, unsigned int k)
{
    int v = get_ur_golomb(gb, k, get_bits_left(gb), 0);
    if (v && get_bits1(gb))
        v = -v;
    return v;
}

static void read_uncoded_coeff(GetBitContext *gb, int *dst, unsigned int elements,
                               int coeff_bits, int is_signed, int offset)
{
    unsigned int i;

    for (i = 0; i < elements; i++) {
        dst[i] = (is_signed ? get_sbits(gb, coeff_bits) : get_bits(gb, coeff_bits)) + offset;
    }
}

static int read_table(GetBitContext *gb, dst_table_t *t, const int8_t code_pred_coeff[3][3],
                      int length_bits, int coeff_bits, int is_signed, int offset)
{
    unsigned int i, j, k;
    for (i = 0; i < t->elements; i++) {
        t->length[i] = get_bits(gb, length_bits) + 1;
        if (!get_bits1(gb)) {
            read_uncoded_coeff(gb, t->coeff[i], t->length[i], coeff_bits, is_signed, offset);
        } else {
            int method = get_bits(gb, 2), lsb_size;
            if (method == 3)
                return AVERROR_INVALIDDATA;

            if ((unsigned)(method + 1) > t->length[i]) return AVERROR_INVALIDDATA;
            read_uncoded_coeff(gb, t->coeff[i], method + 1, coeff_bits, is_signed, offset);

            lsb_size  = get_bits(gb, 3);
            for (j = method + 1; j < t->length[i]; j++) {
                int c, x = 0;
                for (k = 0; k < method + 1; k++)
                    x += code_pred_coeff[method][k] * (unsigned)t->coeff[i][j - k - 1];
                c = get_sr_golomb_dst(gb, lsb_size);
                if (x >= 0)
                    c -= (x + 4) / 8;
                else
                    c += (-x + 3) / 8;
                if (!is_signed) {
                    if (c < offset || c >= offset + (1<<coeff_bits))
                        return AVERROR_INVALIDDATA;
                }
                if (gb->error || c < -32768 || c > 32767) return AVERROR_INVALIDDATA;
                t->coeff[i][j] = c;
            }
        }
    }
    return 0;
}

static void ac_init(dst_arith_coder_t *ac, GetBitContext *gb)
{
    ac->a = 4095;
    ac->c = get_bits(gb, 12);
}

static sa_always_inline void ac_get(dst_arith_coder_t *ac, GetBitContext *gb, int p, int *e)
{
    unsigned int k = (ac->a >> 8) | ((ac->a >> 7) & 1);
    unsigned int q = k * p;
    unsigned int a_q = ac->a - q;

    *e = ac->c < a_q;
    if (*e) {
        ac->a  = a_q;
    } else {
        ac->a  = q;
        ac->c -= a_q;
    }

    if (ac->a < 2048) {
        int n = 11 - sa_log2(ac->a);
        int left = get_bits_left(gb);
        ac->a <<= n;
        if (left >= n) {
            ac->c = (ac->c << n) | get_bits(gb, n);
        } else {
            gb->padding += n - left;
            if (gb->padding > 32) gb->error = 1;
            ac->c <<= n;
            if (left > 0)
                ac->c |= get_bits(gb, left) << (n - left);
        }
    }
}

static uint8_t prob_dst_x_bit(int c)
{
    return (reverse_byte(c & 127) >> 1) + 1;
}

static int build_filter(int16_t table[DST_MAX_ELEMENTS][16][256], const dst_table_t *fsets)
{
    int i, j, k, l;

    for (i = 0; i < fsets->elements; i++) {
        int length = fsets->length[i];

        for (j = 0; j < 16; j++) {
            int total = sa_clip(length - j * 8, 0, 8);

            for (k = 0; k < 256; k++) {
                int64_t v = 0;

                for (l = 0; l < total; l++)
                    v += (((k >> l) & 1) * 2 - 1) * fsets->coeff[i][j * 8 + l];
                if ((int16_t)v != v)
                    return AVERROR_INVALIDDATA;

                table[i][j][k] = (int16_t) v;
            }
        }
    }
    return 0;
}

/*============================================================================
 * Public API
 *============================================================================*/

int dst_decoder_init(dst_decoder_t **decoder, int channel_count, int sample_rate)
{
    dst_decoder_t *dec;

    if (!decoder) {
        return -1;
    }

    *decoder = NULL;

    if (channel_count < 1 || channel_count > 2 || sample_rate != 2822400) {
        fprintf(stderr, "Channel count %d", channel_count);
        return -1;
    }

    dec = (dst_decoder_t *)sa_calloc(1, sizeof(dst_decoder_t));
    if (!dec) {
        return -1;
    }

    dec->channels = channel_count;
    dec->sample_rate = sample_rate;

    *decoder = dec;
    return 0;
}

int dst_decoder_close(dst_decoder_t *decoder)
{
    if (decoder) {
        sa_free(decoder);
    }
    return 0;
}

int dst_decoder_decode(dst_decoder_t *decoder,
                       uint8_t *dst_data, int frame_size,
                       uint8_t *dsd_output, int *dsd_output_len)
{
    unsigned map_ch_to_felem[DST_MAX_CHANNELS];
    unsigned map_ch_to_pelem[DST_MAX_CHANNELS];
    unsigned i, ch, same_map;
    int dst_x_bit;
    unsigned half_prob[DST_MAX_CHANNELS];
    unsigned samples_per_frame;
    unsigned channels;
    GetBitContext *gb;
    dst_arith_coder_t *ac;
    int ret, nb_samples;

    if (!decoder || !dst_data || !dsd_output || !dsd_output_len) {
        return -1;
    }

    samples_per_frame = DST_SAMPLES_PER_FRAME(decoder->sample_rate);
    channels = decoder->channels;
    gb = &decoder->gb;
    ac = &decoder->ac;

    if (frame_size <= 1 || frame_size > 1048576 || *dsd_output_len < (int)(samples_per_frame / 8 * channels))
        return AVERROR_INVALIDDATA;

    nb_samples = samples_per_frame / 8;
    if ((ret = init_get_bits8(gb, dst_data, frame_size)) < 0)
        return ret;

    if (!get_bits1(gb)) {
        skip_bits1(gb);
        if (get_bits(gb, 6))
            return AVERROR_INVALIDDATA;
        if (frame_size - 1 != nb_samples * channels) return AVERROR_INVALIDDATA;
        memcpy(dsd_output, dst_data + 1, nb_samples * channels);
        *dsd_output_len = nb_samples * channels;
        return 0;
    }

    /* Segmentation (10.4, 10.5, 10.6) */

    if (!get_bits1(gb)) {
        fprintf(stderr, "Not Same Segmentation");
        return AVERROR_PATCHWELCOME;
    }

    if (!get_bits1(gb)) {
        fprintf(stderr, "Not Same Segmentation For All Channels");
        return AVERROR_PATCHWELCOME;
    }

    if (!get_bits1(gb)) {
        fprintf(stderr, "Not End Of Channel Segmentation");
        return AVERROR_PATCHWELCOME;
    }

    /* Mapping (10.7, 10.8, 10.9) */

    same_map = get_bits1(gb);

    if ((ret = read_map(gb, &decoder->fsets, map_ch_to_felem, channels)) < 0)
        return ret;

    if (same_map) {
        decoder->probs.elements = decoder->fsets.elements;
        memcpy(map_ch_to_pelem, map_ch_to_felem, sizeof(map_ch_to_felem));
    } else {
        fprintf(stderr, "Not Same Mapping");
        if ((ret = read_map(gb, &decoder->probs, map_ch_to_pelem, channels)) < 0)
            return ret;
    }

    /* Half Probability (10.10) */

    for (ch = 0; ch < channels; ch++)
        half_prob[ch] = get_bits1(gb);

    /* Filter Coef Sets (10.12) */

    if (read_table(gb, &decoder->fsets, fsets_code_pred_coeff, 7, 9, 1, 0) < 0 || gb->error)
        return AVERROR_INVALIDDATA;

    /* Probability Tables (10.13) */

    if (read_table(gb, &decoder->probs, probs_code_pred_coeff, 6, 7, 0, 1) < 0 || gb->error)
        return AVERROR_INVALIDDATA;

    /* Arithmetic Coded Data (10.11) */

    if (get_bits1(gb))
        return AVERROR_INVALIDDATA;
    ac_init(ac, gb);

    if (gb->error || build_filter(decoder->filter, &decoder->fsets) < 0) return AVERROR_INVALIDDATA;

    memset(decoder->status, 0xAA, sizeof(decoder->status));
    memset(dsd_output, 0, nb_samples * channels);

    ac_get(ac, gb, prob_dst_x_bit(decoder->fsets.coeff[0][0]), &dst_x_bit);

    for (i = 0; i < samples_per_frame; i++) {
        if (gb->error) return AVERROR_INVALIDDATA;
        for (ch = 0; ch < channels; ch++) {
            const unsigned felem = map_ch_to_felem[ch];
            int16_t (*filter)[256] = decoder->filter[felem];
            uint8_t *status = decoder->status[ch];
            int prob, residual, v;

#define F(x) filter[(x)][status[(x)]]
            const int16_t predict = F( 0) + F( 1) + F( 2) + F( 3) +
                                    F( 4) + F( 5) + F( 6) + F( 7) +
                                    F( 8) + F( 9) + F(10) + F(11) +
                                    F(12) + F(13) + F(14) + F(15);
#undef F

            if (!half_prob[ch] || i >= decoder->fsets.length[felem]) {
                unsigned pelem = map_ch_to_pelem[ch];
                unsigned index = FFABS(predict) >> 3;
                prob = decoder->probs.coeff[pelem][SAMIN(index, decoder->probs.length[pelem] - 1)];
            } else {
                prob = 128;
            }

            ac_get(ac, gb, prob, &residual);
            v = ((predict >> 15) ^ residual) & 1;
            dsd_output[(i >> 3) * channels + ch] |= v << (7 - (i & 0x7 ));

            SA_WL64A(status + 8, (SA_RL64A(status + 8) << 1) | ((SA_RL64A(status) >> 63) & 1));
            SA_WL64A(status, (SA_RL64A(status) << 1) | v);
        }
    }

    if (gb->error) return AVERROR_INVALIDDATA;
    *dsd_output_len = nb_samples * channels;
    return 0;
}
