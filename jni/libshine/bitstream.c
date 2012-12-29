/* bitstream.c */

#include "types.h"

static int encode_side_info(struct bitstream_ctx *bs, L3_side_info_t  *si, struct shine_ctx *shine);
static void Huffmancodebits(struct bitstream_ctx *bs, int *ix, gr_info *gi, struct shine_ctx *shine);
static int L3_huffman_coder_count1(struct bitstream_ctx *bs, const struct huffcodetab *h, int v, int w, int x, int y);
static int HuffmanCode(int table_select, int x, int y,
		unsigned int *code, unsigned int *extword, int *codebits, int *extbits);
static int encode_main_data(struct bitstream_ctx *bs,
		int l3_enc[2][2][samp_per_frame2], L3_side_info_t  *si, struct shine_ctx *shine);

/*
 * open_bit_stream
 * ---------------
 * open the device to write the bit stream into it
 */
void open_bit_stream(struct shine_ctx *shine, char *filename)
{
	/* setup header (the only change between frames is the padding bit) */
	shine->header[0] = 0xff;

	shine->header[1] = 0xe0 |
		(shine->type << 3) |
		(shine->layr << 1) |
		!shine->crc;

	shine->header[2] = (shine->bitrate_index << 4) |
		(shine->samplerate_index << 2) |
		/* shine->padding inserted later */
		shine->ext;

	shine->header[3] = (shine->mode << 6) |
		(shine->mode_ext << 4) |
		(shine->copyright << 3) |
		(shine->original << 2) |
		shine->emph;
}

/*
 * close_bit_stream
 * ----------------
 * close the device containing the bit stream
 */
void close_bit_stream(struct shine_ctx *shine)
{

}

/*
 * L3_format_bitstream
 * -------------------
 * This is called after a frame of audio has been quantized and coded.
 * It will write the encoded audio to the bitstream. Note that
 * from a layer3 encoder's perspective the bit stream is primarily
 * a series of main_data() blocks, with header and side information
 * inserted at the proper locations to maintain framing. (See Figure A.7
 * in the IS).
 *
 * note. both header/sideinfo and main data are multiples of 8 bits.
 * this means that the encoded data can be manipulated as bytes
 * which is easier and quicker than bits.
 */

int L3_format_bitstream(struct bitstream_ctx *bs, struct rtphdr_ctx *rtphdr,
		int l3_enc[2][2][samp_per_frame2], L3_side_info_t  *l3_side, struct shine_ctx *shine)
{
	int frame_len, main_bytes;

	frame_len = encode_side_info(bs, l3_side, shine); /* store in fifo */
	main_bytes = encode_main_data(bs, l3_enc, l3_side, shine);

	rtphdr->seglen = frame_len;
	rtphdr->silen = shine->main_silen;
	rtphdr->total = main_bytes + shine->main_silen;

	return rtphdr->total;
}

/*
 * putbits:
 * --------
 * write N bits into the encoded data buffer.
 * buf = encoded data buffer
 * val = value to write into the buffer
 * n = number of bits of val
 *
 * Bits in value are assumed to be right-justified.
 */
static void putbits (struct bitstream_ctx *s, unsigned int val, unsigned int n)
{
	int m;

	if (n > 32)
		error("Cannot write more than 32 bits at a time");

	s->bits += n;
	if (n + s->bitcnt > sizeof(s->bitval) * 8) {
		unsigned int k = sizeof(s->bitval) * 8 - s->bitcnt; 
		putbits(s, val >> (n -= k), k);
		val >>= k;
	}

	m = (1 << n) - 1;
	s->bitval <<= n;
	s->bitval |= (val & m);
	s->bitcnt += n;
	while (s->bitcnt >= 8) {
		s->bitcnt -= 8;
		*s->bufptr++ = (s->bitval >> s->bitcnt);
	}
}

/*
 * encode_main_data
 * --------------
 * Encodes the spectrum and places the coded
 * main data in the buffer main.
 * Returns the number of bytes stored.
 */
static int encode_main_data(struct bitstream_ctx *s,
			int l3_enc[2][2][samp_per_frame2], L3_side_info_t  *si, struct shine_ctx *shine)
{
	int gr, ch, orig_bits;

	orig_bits = s->bits;

	/* huffmancodes plus reservoir stuffing */
	for (gr = 0; gr < shine->granules; gr++)
		for (ch = 0; ch < shine->channels; ch++)
			Huffmancodebits(s, l3_enc[gr][ch], &si->gr[gr].ch[ch].tt, shine);
	/* encode the spectrum */

	/* ancillary data, used for reservoir stuffing overflow */
	if (si->resv_drain) {
		int words = si->resv_drain >> 5;
		int remainder = si->resv_drain & 31;
		/* pad with zeros */
		while (words--)
			putbits(s, 0, 32);

		if (remainder)
			putbits(s, 0, remainder);
	}

	return (s->bits - orig_bits) >> 3;
}

/*
 * encode_side_info
 * --------------
 * Encodes the header and sideinfo and stores the coded data
 * in the side fifo for transmission at the appropriate place
 * in the bitstream.
 */
static int encode_side_info(struct bitstream_ctx *s, L3_side_info_t  *si, struct shine_ctx *shine)
{
	int orig_bits;
	int gr, ch, region;

	/* header */
	orig_bits = s->bits;
	putbits(s, shine->header[0], 8);
	putbits(s, shine->header[1], 8);
	putbits(s, shine->header[2] | (shine->padding << 1), 8);
	putbits(s, shine->header[3], 8);

	/* side info */
	if (shine->type == MPEG1) {
		//fprintf(stderr, "main_data_begin9: %d\n", si->main_data_begin);
		putbits(s, si->main_data_begin, 9);
		putbits(s, 0, shine->channels == 2? 3: 5) ; /* private bits */

		for (ch = 0; ch < shine->channels; ch++)
			putbits(s, 0, 4); /* scfsi */

		for (gr = 0; gr < 2; gr++)
			for (ch = 0; ch < shine->channels ; ch++) {
				gr_info *gi = &(si->gr[gr].ch[ch].tt);
				putbits(s, gi->part2_3_length,    12);
				putbits(s, gi->big_values,         9);
				putbits(s, gi->global_gain,        8);
				putbits(s, 0, 5); /* scalefac_compress, window switching flag */

				for (region = 0; region < 3; region++)
					putbits(s, gi->table_select[region], 5);

				putbits(s, gi->region0_count,      4);
				putbits(s, gi->region1_count,      3);
				putbits(s, 0, 2 ); /* preflag, scalefac_scale */
				putbits(s, gi->count1table_select, 1);
			}
	} else { /* mpeg 2/2.5 */
		//fprintf(stderr, "main_data_begin8: %d\n", si->main_data_begin);
		putbits(s, si->main_data_begin, 8 );
		putbits(s, 0, shine->channels == 1? 1: 2); /* private bits */

		for (ch = 0; ch < shine->channels; ch++) {
			gr_info *gi = &(si->gr[0].ch[ch].tt);
			putbits(s, gi->part2_3_length,    12);
			putbits(s, gi->big_values,         9);
			putbits(s, gi->global_gain,        8);
			putbits(s, 0, 10 ); /* scalefac_compress, window switching flag */

			for (region = 0; region < 3; region++)
				putbits(s, gi->table_select[region], 5);

			putbits(s, gi->region0_count,      4);
			putbits(s, gi->region1_count,      3);
			putbits(s, 0, 1); /* scalefac_scale */
			putbits(s, gi->count1table_select, 1);
		}
	}

	shine->main_silen = (s->bits - orig_bits) >> 3; /* bytes in side info */
	return (shine->bits_per_frame + orig_bits - s->bits) >> 3; /* data bytes in this frame */
}

/*
 * Huffmancodebits
 * ---------------
 * Note the discussion of huffmancodebits() on pages 28
 * and 29 of the IS, as well as the definitions of the side
 * information on pages 26 and 27.
 */
static void Huffmancodebits(struct bitstream_ctx *s, int *ix, gr_info *gi, struct shine_ctx *shine)
{
	int region1Start;
	int region2Start;

	int i, bigvalues, count1End;
	int v, w, x, y, cx_bits, cbits, xbits, stuffingBits;

	unsigned int code, ext;
	const struct huffcodetab *h;

	int bitsWritten = 0;
	int bvbits, c1bits, tablezeros, r0, r1, r2, rt, *pr;
	//  int idx = 0;

	tablezeros = 0;
	r0 = r1 = r2 = 0;

	/* 1: Write the bigvalues */
	bigvalues = gi->big_values <<1;
	{
		unsigned scalefac_index = 100;

		scalefac_index = gi->region0_count + 1;
		region1Start = shine->scalefac_band_long[scalefac_index];
		scalefac_index += gi->region1_count + 1;
		region2Start = shine->scalefac_band_long[scalefac_index];

		for (i = 0; i < bigvalues; i += 2) {
			unsigned tableindex = 100;

			/* get table pointer */
			if (i < region1Start) {
				tableindex = gi->table_select[0];
				pr = &r0;
			} else if (i < region2Start) {
				tableindex = gi->table_select[1];
				pr = &r1;
			} else {
				tableindex = gi->table_select[2];
				pr = &r2;
			}

			h = &ht[tableindex];
			/* get huffman code */
			x = ix[i];
			y = ix[i + 1];
			if (tableindex != 0) {
				cx_bits = HuffmanCode(tableindex, x, y, &code, &ext, &cbits, &xbits);
				putbits(s,  code, cbits);
				putbits(s,  ext, xbits);
				bitsWritten += rt = cx_bits;
				*pr += rt;
			} else {
				tablezeros += 1;
				*pr = 0;
			}
		}
	}

	bvbits = bitsWritten;

	/* 2: Write count1 area */
	h = &ht[gi->count1table_select + 32];
	count1End = bigvalues + (gi->count1 <<2);
	for (i = bigvalues; i < count1End; i += 4) {
		v = ix[i];
		w = ix[i + 1];
		x = ix[i + 2];
		y = ix[i + 3];
		bitsWritten += L3_huffman_coder_count1(s, h, v, w, x, y);
	}

	c1bits = bitsWritten - bvbits;
	if ((stuffingBits = gi->part2_3_length - bitsWritten) != 0) {
		int words = stuffingBits >> 5;
		int remainder = stuffingBits & 31;

		/*
		 * Due to the nature of the Huffman code
		 * tables, we will pad with ones
		 */
		while (words--)
			putbits(s, ~0, 32);

		if (remainder)
			putbits(s, ~0, remainder);
	}
}

/*
 * abs_and_sign
 * ------------
 */
static int abs_and_sign( int *x )
{
	if ( *x > 0 ) return 0;
	*x *= -1;
	return 1;
}

/*
 * L3_huffman_coder_count1
 * -----------------------
 */
static int L3_huffman_coder_count1(struct bitstream_ctx *s,
		const struct huffcodetab *h, int v, int w, int x, int y)
{
	int len;
	int totalBits;
	HUFFBITS huffbits;
	unsigned int signv, signw, signx, signy, p;

	signv = abs_and_sign(&v);
	signw = abs_and_sign(&w);
	signx = abs_and_sign(&x);
	signy = abs_and_sign(&y);

	p = v + (w << 1) + (x << 2) + (y << 3);
	huffbits = h->table[p];
	len = h->hlen[p];
	putbits(s, huffbits, len);
	totalBits = len;

	if (v != 0) {
		putbits(s,  signv, 1);
		totalBits++;
	}

	if (w != 0) {
		putbits(s,  signw, 1);
		totalBits++;
	}

	if (x != 0) {
		putbits(s,  signx, 1);
		totalBits++;
	}

	if (y != 0) {
		putbits(s,  signy, 1);
		totalBits++;
	}

	return totalBits;
}

/*
 * HuffmanCode
 * -----------
 * Implements the pseudocode of page 98 of the IS
 */
static int HuffmanCode(int table_select, int x, int y,
		unsigned int *code, unsigned int *ext, int *cbits, int *xbits)
{
	const struct huffcodetab *h;
	unsigned signx, signy, linbitsx, linbitsy, linbits, xlen, ylen, idx;

	*cbits = 0;
	*xbits = 0;
	*code  = 0;
	*ext   = 0;

	if (table_select == 0)
		return 0;

	signx = abs_and_sign(&x);
	signy = abs_and_sign(&y);
	h = &(ht[table_select]);
	xlen = h->xlen;
	ylen = h->ylen;
	linbits = h->linbits;
	linbitsx = linbitsy = 0;

	if (table_select > 15) { /* ESC-table is used */
		if (x > 14) {
			linbitsx = x - 15;
			x = 15;
		}

		if (y > 14) {
			linbitsy = y - 15;
			y = 15;
		}

		idx = (x * ylen) + y;
		*code  = h->table[idx];
		*cbits = h->hlen [idx];
		if (x > 14) {
			*ext   |= linbitsx;
			*xbits += linbits;
		}

		if (x != 0) {
			*ext = ((*ext) << 1) | signx;
			*xbits += 1;
		}

		if (y > 14) {
			*ext = ((*ext) << linbits) | linbitsy;
			*xbits += linbits;
		}

		if (y != 0) {
			*ext = ((*ext) << 1) | signy;
			*xbits += 1;
		}
	} else {
		/* No ESC-words */
		idx = (x * ylen) + y;
		*code = h->table[idx];
		*cbits += h->hlen[ idx ];

		if (x != 0) {
			*code = ((*code) << 1) | signx;
			*cbits += 1;
		}

		if (y != 0) {
			*code = ((*code) << 1) | signy;
			*cbits += 1;
		}
	}

	return *cbits + *xbits;
}

