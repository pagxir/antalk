#ifndef TYPES_H
#define TYPES_H

#include <stdio.h>
#include <time.h>
#include <string.h>
#include <ctype.h>
#include <stdlib.h>
#include <math.h>

#define MPEG2_5 0
#define MPEG2   2
#define MPEG1   3

#define MODE_STEREO       0
#define MODE_MS_STEREO    1
#define MODE_DUAL_CHANNEL 2
#define MODE_MONO         3

#define LAYER_3 1
#define LAYER_2 2
#define LAYER_1 3
#define samp_per_frame  1152
#define samp_per_frame2 576
#define PI              3.14159265358979
#define HAN_SIZE        512
#define SBLIMIT         32

struct rtphdr_ctx {
    unsigned char total;
    unsigned char silen;
    unsigned char seglen;
};

struct bitstream_ctx {
    int bits;
    int bitcnt;
    unsigned int bitval;
    unsigned char *bufptr;
};

struct shine_ctx {
	int  lag;
	int  type;
	int  layr;
	int  mode;
	int  bitr;
	int  psyc;
	int  emph;
	int  padding;
	int  remainder;
	int  bytes_per_frame;
	long samples_per_frame;
	long bits_per_frame;
	long bits_per_slot;
	long total_frames;
	int  bitrate_index;
	int  samplerate_index;
	int crc;
	int ext;
	int mode_ext;
	int copyright;
	int original;
	int channels;
	int channels_in;
	int granules;
	int resv_limit;
	int samplerate;
	int cutoff;
	int sideinfo_len;
	int off[2];
	int z[512];
	int x[2][HAN_SIZE];
	int main_data_begin;
	int *scalefac_band_long;

	int *xr;                    /* magnitudes of the spectral values */
    int xrmax;                  /* maximum of xrabs array */
    int xrabs[samp_per_frame2]; /* xr absolute */

	int main_silen;
	unsigned char header[4];

	int l3_enc[2][2][samp_per_frame2];
	int l3_sb_sample[2][3][18][SBLIMIT];
	int mdct_freq[2][2][samp_per_frame2];
};

#define HUFFBITS unsigned int
#define HTN     34
#define MXOFF   250

struct huffcodetab {
	unsigned int xlen;    /*max. x-index+                         */
	unsigned int ylen;    /*max. y-index+                         */
	unsigned int linbits; /*number of linbits                     */
	unsigned int linmax;  /*max number to be stored in linbits    */
	const HUFFBITS *table;      /*pointer to array[xlen][ylen]          */
	const unsigned char *hlen;  /*pointer to array[xlen][ylen]          */
};

extern const struct huffcodetab ht[HTN];/* global memory block                */
/* array of all huffcodtable headers    */
/* 0..31 Huffman code table 0..31       */
/* 32,33 count1-tables                  */

/* Side information */
typedef struct {
	unsigned part2_3_length;
	unsigned big_values;
	unsigned count1;
	unsigned global_gain;
	unsigned table_select[3];
	unsigned region0_count;
	unsigned region1_count;
	unsigned count1table_select;
	unsigned address1;
	unsigned address2;
	unsigned address3;
	int quantizerStepSize;
} gr_info;

typedef struct {
	int main_data_begin;
	struct
	{
		struct
		{
			gr_info tt;
		} ch[2];
	} gr[2];
	int resv_drain;
} L3_side_info_t;

/* function prototypes */

void error(char* s);

/* bitstream.c */
void open_bit_stream(struct shine_ctx *shine, char *bs_filenam);
int L3_format_bitstream(struct bitstream_ctx *bs, struct rtphdr_ctx *rtphdr,
        int l3_enc[2][2][samp_per_frame2], L3_side_info_t  *l3_side, struct shine_ctx *shine);
void close_bit_stream(struct shine_ctx *shine);

/* l3loop.c */
void L3_iteration_loop(struct shine_ctx *shine,
		int mdct_freq_org[2][2][samp_per_frame2],
		L3_side_info_t *side_info,
		int l3_enc[2][2][samp_per_frame2],
		int mean_bits);

/* coder.c */
void L3_window_filter_subband(struct shine_ctx *shine,
		 unsigned int **buffer, int s[SBLIMIT], int k);
void L3_mdct_sub(struct shine_ctx *shine, int sb_sample[2][3][18][SBLIMIT], int mdct_freq[2][2][samp_per_frame2]);

/* shine.c */
int shine_init(struct shine_ctx *shine);
int shine_update(struct shine_ctx *shine);
int shine_fini(struct shine_ctx *shine);
int shine_frame(struct shine_ctx *shine, 
		const void *src, size_t len, void *dst, size_t size);

#endif

