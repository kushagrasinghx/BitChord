#pragma once
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif
typedef struct dst_decoder_s dst_decoder_t;
int dst_decoder_init(dst_decoder_t **decoder, int channel_count, int sample_rate);
int dst_decoder_close(dst_decoder_t *decoder);
int dst_decoder_decode(dst_decoder_t *decoder, uint8_t *input, int size, uint8_t *output, int *output_size);
#ifdef __cplusplus
}
#endif
