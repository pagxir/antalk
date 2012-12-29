/* layer3.c */

#include "types.h"

//#define NO_RESERVOIR

int shine_frame(struct shine_ctx *shine,
		const void *src, size_t len, void *dst, size_t size)
{
	int ch, i, gr;
	int total, mean_bits;
	struct rtphdr_ctx *rtphdr;
	struct bitstream_ctx stream;

	unsigned int *buffer[2];

	int (*l3_enc)[2][samp_per_frame2] = shine->l3_enc;
	int (*l3_sb_sample)[3][18][SBLIMIT] = shine->l3_sb_sample;
	int (*mdct_freq)[2][samp_per_frame2] = shine->mdct_freq;
	static L3_side_info_t side_info = {0};

	buffer[0] = buffer[1] = (unsigned int *)src;

	/* sort out padding */
	shine->lag += shine->remainder;
	shine->padding = 0;
	if (shine->lag >= shine->samplerate) {
		shine->lag -= shine->samplerate;
		shine->padding = 1;
	}

	shine->bits_per_frame = 8 * (shine->bytes_per_frame + shine->padding);

	/* bits per channel per granule */
	mean_bits = (shine->bits_per_frame - shine->sideinfo_len) >>
		(shine->granules + shine->channels - 2);

	/* polyphase filtering */
	for (gr = 0; gr < shine->granules; gr++)
		for(ch = 0; ch < shine->channels; ch++)
			for(i = 0;i < 18; i++)
				L3_window_filter_subband(shine,
						&buffer[ch], &l3_sb_sample[ch][gr + 1][i][0], ch);

	/* apply mdct to the polyphase output */
	L3_mdct_sub(shine, l3_sb_sample, mdct_freq);

	/* bit and noise allocation */
	L3_iteration_loop(shine, mdct_freq, &side_info, l3_enc, mean_bits);

	rtphdr = (struct rtphdr_ctx *)dst;
	memset(&stream, 0, sizeof(stream));
	stream.bufptr = dst + sizeof(*rtphdr);

	/* write the frame to the bitstream */
	total = L3_format_bitstream(&stream, rtphdr, l3_enc, &side_info, shine);

	return total + sizeof(*rtphdr);
}

