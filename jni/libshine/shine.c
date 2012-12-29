#include <stdio.h>

#include "types.h"

/* Scalefactor bands. */
static const int sfBandIndex[4][3][23] =
{
	{ /* MPEG-2.5 11.025 kHz */
		{0,6,12,18,24,30,36,44,54,66,80,96,116,140,168,200,238,284,336,396,464,522,576},
		/* MPEG-2.5 12 kHz */
		{0,6,12,18,24,30,36,44,54,66,80,96,116,140,168,200,238,284,336,396,464,522,576},
		/* MPEG-2.5 8 kHz */
		{0,12,24,36,48,60,72,88,108,132,160,192,232,280,336,400,476,566,568,570,572,574,576}
	},
	{
		{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
		{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
		{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}
	},
	{ /* Table B.2.b: 22.05 kHz */
		{0,6,12,18,24,30,36,44,54,66,80,96,116,140,168,200,238,284,336,396,464,522,576},
		/* Table B.2.c: 24 kHz */
		{0,6,12,18,24,30,36,44,54,66,80,96,114,136,162,194,232,278,332,394,464,540,576},
		/* Table B.2.a: 16 kHz */
		{0,6,12,18,24,30,36,44,54,66,80,96,116,140,168,200,238,284,336,396,464,522,576}
	},
	{ /* Table B.8.b: 44.1 kHz */
		{0,4,8,12,16,20,24,30,36,44,52,62,74,90,110,134,162,196,238,288,342,418,576},
		/* Table B.8.c: 48 kHz */
		{0,4,8,12,16,20,24,30,36,42,50,60,72,88,106,128,156,190,230,276,330,384,576},
		/* Table B.8.a: 32 kHz */
		{0,4,8,12,16,20,24,30,36,44,54,66,82,102,126,156,194,240,296,364,448,550,576}
	}
};

/*
 * find_samplerate_index:
 * ----------------------
 */
static int find_samplerate_index(struct shine_ctx *shine, int freq)
{
	int i, j;
	static int sr[4][3] = {
		{11025, 12000,  8000},   /* mpeg 2.5 */
		{    0,     0,     0},   /* reserved */
		{22050, 24000, 16000},   /* mpeg 2 */
		{44100, 48000, 32000}    /* mpeg 1 */
	};

	for (j = 0; j < 4; j++) {
		for(i = 0; i < 3; i++) {
			if((freq == sr[j][i]) && (j != 1)) {
				shine->type = j;
				return i;
			}
		}
	}

	error("Invalid samplerate");
	return 0;
}

/*
 * find_bitrate_index:
 * -------------------
 */
static int find_bitrate_index(struct shine_ctx *shine, int bitr)
{
	int i;
	static int br[2][15] = {
		{0, 8,16,24,32,40,48,56, 64, 80, 96,112,128,144,160},   /* mpeg 2/2.5 */
     	{0,32,40,48,56,64,80,96,112,128,160,192,224,256,320}};  /* mpeg 1 */

	for (i = 1; i < 15; i++)
    	if (bitr == br[shine->type & 1][i]) return i;

	error("Invalid bitrate");
	return 0;
}

static int set_cutoff(struct shine_ctx *shine)
{
	static int cutoff_tab[3][2][15] = {
		{ /* 44.1k, 22.05k, 11.025k */
      		{100,104,131,157,183,209,261,313,365,418,418,418,418,418,418}, /* stereo */
			{183,209,261,313,365,418,418,418,418,418,418,418,418,418,418}  /* mono */
		},
		{ /* 48k, 24k, 12k */
			{100,104,131,157,183,209,261,313,384,384,384,384,384,384,384}, /* stereo */
			{183,209,261,313,365,384,384,384,384,384,384,384,384,384,384}  /* mono */
		},
		{ /* 32k, 16k, 8k */
			{100,104,131,157,183,209,261,313,365,418,522,576,576,576,576}, /* stereo */
			{183,209,261,313,365,418,522,576,576,576,576,576,576,576,576}  /* mono */
		}
	};

	return cutoff_tab[shine->samplerate_index][shine->mode == MODE_MONO][shine->bitrate_index];
}

int shine_init(struct shine_ctx *shine)
{
	memset(shine, 0, sizeof(*shine));
	shine->type = MPEG1;
	shine->layr = LAYER_3;
	shine->mode = MODE_DUAL_CHANNEL;
	shine->bitr = 16; //128;
	shine->psyc = 0;
	shine->emph = 0;
	shine->crc  = 0;
	shine->ext  = 0;
	shine->mode_ext  = 0;
	shine->copyright = 0;
	shine->original  = 1;
	shine->channels = 2;
	shine->granules = 2;
	shine->samplerate = 44100;
	shine->cutoff = 418; /* 16KHz @ 44.1Ksps */
	shine->channels_in = 1;

	shine->channels = 1;
	shine->mode = MODE_MONO;
	shine->samplerate = 8000;
	return 0;
}

int shine_update(struct shine_ctx *shine)
{
	shine->samplerate_index = find_samplerate_index(shine, shine->samplerate);
	shine->bitrate_index    = find_bitrate_index(shine, shine->bitr);
	shine->cutoff = set_cutoff(shine);

	shine->lag = 0;
	if(shine->type == MPEG1) {
		shine->granules = 2;
		shine->samples_per_frame = samp_per_frame;
		shine->resv_limit = ((1 << 9) - 1) << 3;
		shine->sideinfo_len = (shine->channels == 1) ? 168 : 288;
	} else {
		 /* mpeg 2/2.5 */
		shine->granules = 1;
		shine->samples_per_frame = samp_per_frame2;
		shine->resv_limit = ((1 << 8) - 1) << 3;
		shine->sideinfo_len = (shine->channels == 1) ? 104 : 168;
	}

#ifdef NO_RESERVOIR
	shine->resv_limit = 0;
#endif

	{ /* find number of whole bytes per frame and the remainder */
		long x = shine->samples_per_frame * shine->bitr * (1000 / 8);
		shine->bytes_per_frame = x / shine->samplerate;
		shine->remainder  = x % shine->samplerate;
	}

	fprintf(stderr, "type %d, samplerate_index %d\n",
			shine->type, shine->samplerate_index);
	shine->scalefac_band_long = sfBandIndex[shine->type][shine->samplerate_index];
	return 0;
}

int shine_fini(struct shine_ctx *shine)
{
	return 0;
}

