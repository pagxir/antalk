/*
 * coder.c
 *
 * 22/02/01
 * Calculation of coefficient tables for sub band windowing
 * analysis filters and mdct.
 */

#include "types.h"
#include "table1.h"

#include "utils.ch"

/*
 * L3_window_filter_subband:
 * -------------------------
 * Overlapping window on PCM samples
 * 32 16-bit pcm samples are scaled to fractional 2's complement and
 * concatenated to the end of the window buffer #x#. The updated window
 * buffer #x# is then windowed by the analysis window #enwindow# to produce
 * the windowed sample #z#
 * The windowed samples #z# is filtered by the digital filter matrix #filter#
 * to produce the subband samples #s#. This done by first selectively
 * picking out values from the windowed samples, and then multiplying
 * them by the filter matrix, producing 32 subband samples.
 */
void L3_window_filter_subband(struct shine_ctx *shine,
		unsigned int **buffer, int s[SBLIMIT] , int k)
{
	int i, j;
	int y[64], s1, s2;
	int *z = shine->z;
	int *off = shine->off;
	int (*x)[HAN_SIZE];

	x = shine->x;

	/* replace 32 oldest samples with 32 new samples */

	/* data format depends on mode */
	if (shine->channels == 1)
		if(shine->channels_in == 2) {
			/* mono from stereo, sum upper and lower */
			for (i = 31; i >= 0; i--) {
				x[k][i + off[k]] = (((**buffer) >> 16) + (((**buffer) << 16) >> 16)) << 15;
				(*buffer)++;
			}
		} else {
			/* mono data, use upper then lower */
			for (i = 15; i >= 0; i--) {
				x[k][(2 * i) + off[k] + 1] = (**buffer) << 16;
				x[k][(2 * i) + off[k]] = ((*(*buffer)++) >> 16) << 16;
			}
		} else if(k) { 
			/* stereo left, use upper */
			for (i = 31; i >= 0; i--)
				x[k][i + off[k]] = (*(*buffer)++) & 0xffff0000;
		} else {
			/* stereo right, use lower */
			for (i = 31; i >= 0; i--)
				x[k][i + off[k]] = (*(*buffer)++) << 16;
		}

	/* shift samples into proper window positions, and window data */
	for (i = HAN_SIZE; i--; )
		z[i] = mul(x[k][(i + off[k]) & (HAN_SIZE - 1)], ew[i]);

	off[k] = (off[k] + 480) & (HAN_SIZE - 1); /* offset is modulo (HAN_SIZE)*/

	/* sub sample the windowed data */
	for (i = 64; i--; )
		for (j = 8, y[i] = 0; j--; )
			y[i] += z[i + (j << 6)];

	/* combine sub samples for the simplified matrix calculation */
	for (i = 0; i < 16; i++)
		y[i + 17] += y[15 - i];

	for (i = 0; i < 15; i++)
		y[i + 33] -= y[63 - i];

	/* simlplified polyphase filter matrix multiplication */
	for (i = 16; i--; ) {
		for (j = 0, s[i] = 0, s[31 - i] = 0; j < 32; j += 2) {
			s1 = mul(fl[i][j], y[j + 16]);
			s2 = mul(fl[i][j + 1], y[j + 17]);
			s[i] += s1 + s2;
			s[31 - i] += s1 - s2;
		}
	}
}

/*
 * L3_mdct_sub:
 * ------------
 */
void L3_mdct_sub(struct shine_ctx *shine,
		int sb_sample[2][3][18][SBLIMIT], int mdct_freq[2][2][samp_per_frame2])
{
	/* note. we wish to access the array 'mdct_freq[2][2][576]' as
	 * [2][2][32][18]. (32*18=576),
	 */
	int (*mdct_enc)[18];

	int ch, gr, band, j, k;
	int mdct_in[36];
	int bu, bd, *m;

	for (gr = 0; gr < shine->granules; gr++) {
		for (ch = shine->channels; ch--; ) {
			/* set up pointer to the part of mdct_freq we're using */
			mdct_enc = (int (*)[18]) mdct_freq[gr][ch];

			/* Compensate for inversion in the analysis filter
			 * (every odd index of band AND k)
			 */
			for (band = 1; band <= 31; band += 2)
				for(k = 1; k <= 17; k += 2)
					sb_sample[ch][gr + 1][k][band] *= -1;

			/* Perform imdct of 18 previous subband samples + 18 current subband samples */
			for (band = 32; band--; ) {
				for(k = 18; k--; ) {
					mdct_in[k]    = sb_sample[ch][ gr ][k][band];
					mdct_in[k + 18] = sb_sample[ch][gr+1][k][band];
				}

				/* Calculation of the MDCT
				 * In the case of long blocks ( block_type 0,1,3 ) there are
				 * 36 coefficients in the time domain and 18 in the frequency
				 * domain.
				 */
				for (k = 18; k--; ) {
					m = &mdct_enc[band][k];
					for(j = 36, *m = 0; j--; )
						*m += mul(mdct_in[j], cos_l[k][j]);
				}
			}

			/* Perform aliasing reduction butterfly */
			for (band = 31; band--; )
				for (k = 8; k--; ) {
					/* must left justify result of multiplication here because the centre
					 * two values in each block are not touched.
					 */
					bu = muls(mdct_enc[band][17 - k], cs[k]) + muls(mdct_enc[band + 1][k], ca[k]);
					bd = muls(mdct_enc[band+1][k], cs[k]) - muls(mdct_enc[band][17 - k], ca[k]);
					mdct_enc[band][17 - k] = bu;
					mdct_enc[band + 1][k]  = bd;
				}
		}
	}

	/* Save latest granule's subband samples to be used in the next mdct call */
	for (ch = shine->channels; ch--; )
		for (j = 18; j--; )
			for (band = 32; band--; )
				sb_sample[ch][0][j][band] = sb_sample[ch][shine->granules][j][band];
}

