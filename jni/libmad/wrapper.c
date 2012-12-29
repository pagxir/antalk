#include <string.h>
#include <assert.h>
#include <stdio.h>

#include "wrapper.h"
#include "mad/synth.h"
#include "mad/frame.h"
#include "mad/stream.h"

#ifndef u_long
#define u_long unsigned long
#endif

#define INPUT_BUFFER_SIZE (5 * 8192)

struct rtphdr_ctx {
    unsigned char total;
    unsigned char silen;
    unsigned char seglen;
};

struct audio_dither {
	mad_fixed_t error[3];
	mad_fixed_t random;
};

struct byte_channel {
	int flag;
	unsigned char *inptr;
	unsigned char *outptr;
	unsigned char *bufbase;
	unsigned char *buflimit;
};

struct mp3play_ctx {
	int space;
	struct mad_synth synth;
	struct mad_frame frame;
	struct mad_stream stream;
	struct audio_dither ditherl;
	struct audio_dither ditherr;

	unsigned char *guard_ptr;
	unsigned char *input_buffer;
	struct byte_channel channel;
};

static u_long prng(u_long state)
{
	/* 32-bit pseudo-random number generator. */
	return (state * 0x0019660dL + 0x3c6ef35fL) & 0xffffffffL;
}

static int wrap_error(int error)
{
	if (error == 0)
		return -1;
	if (error > 0)
		return -error;
	return error;
}

static void check_stream_error(struct mad_stream *s, unsigned char *guard_ptr)
{ 
	assert(s->error != MAD_ERROR_BUFLEN);
	if (s->error != MAD_ERROR_LOSTSYNC ||
			s->this_frame != guard_ptr) {
#if 0
		fprintf(stderr, "recoverable frame level error\n");
		fflush(stderr);
#endif
		return;
	}
}

void fixed_begin(unsigned char *buf, int begin)
{
    int type = (buf[1] >> 3) & 0x3;

    if (type == 3) {
        buf[4] = (begin >> 1);
        buf[5] &= 0x7F;
        buf[5] |= (begin << 7) & 0x80;
        return;
    }

    buf[4] = begin;
}

static void byte_channel_init(struct byte_channel *channel, void *buf, size_t len)
{
	channel->flag = 0;
	channel->inptr = (char *)buf;
	channel->outptr = (char *)buf;
	channel->bufbase = (char *)buf;
	channel->buflimit = (channel->bufbase + len);
	return;
}

static int byte_channel_write(struct byte_channel *channel, const void *buf, size_t len)
{
	int count = (channel->inptr - channel->outptr);
	int limit = (channel->buflimit - channel->bufbase);

	if (count + len > limit) {
		channel->flag |= 2;
		return -(count + len - limit);
	}

	if (channel->inptr + len > channel->buflimit) {
		memmove(channel->bufbase, channel->outptr, count);
		channel->inptr  = channel->bufbase + count;
		channel->outptr = channel->bufbase;
		channel->flag |= 1;
	}

	memcpy(channel->inptr, buf, len);
	channel->inptr += len;
	return len;
}

static long audio_linear_dither(unsigned bits,
		mad_fixed_t sample, struct audio_dither *dither)
{
	unsigned int scalebits;
	mad_fixed_t output, mask, random;

	enum {
		MIN = -MAD_F_ONE,
		MAX = MAD_F_ONE - 1
	};

	/* noise shape */
	sample += dither->error[0] - dither->error[1] + dither->error[2];

	dither->error[2] = dither->error[1];
	dither->error[1] = dither->error[0] / 2;

	/* bias */
	output = sample + (1L << (MAD_F_FRACBITS + 1 - bits - 1));

	scalebits = MAD_F_FRACBITS + 1 - bits;
	mask = (1L << scalebits) - 1;

	/* dither */
	random = prng(dither->random);
	output += (random & mask) - (dither->random & mask);

	dither->random = random;

	/* clip */
	if (output > MAX) {
		output = MAX;

		if (sample > MAX)
			sample = MAX;
	}
	else if (output < MIN) {
		output = MIN;

		if (sample < MIN)
			sample = MIN;
	}

	/* quantize */
	output &= ~mask;

	/* error feedback */
	dither->error[0] = sample - output;

	/* scale */
	return output >> scalebits;
}

static void wrapper_init(struct mp3play_ctx *ctx)
{
	mad_synth_init(&ctx->synth);
	mad_frame_init(&ctx->frame);
	mad_stream_init(&ctx->stream);

	ctx->space = 0;
	ctx->guard_ptr = NULL;
	ctx->input_buffer = (unsigned char *)malloc(INPUT_BUFFER_SIZE + MAD_BUFFER_MDLEN);
	assert(ctx->input_buffer != NULL);
	byte_channel_init(&ctx->channel, ctx->input_buffer, INPUT_BUFFER_SIZE);
	return;
}

static int wrapper_frame(struct mp3play_ctx *ctx, jbyte *buf, int len)
{
	int i;
	int error;
	char *p = (char *)buf;
	struct mad_synth *h = &ctx->synth;
	struct mad_frame *f = &ctx->frame;
	struct mad_stream *s = &ctx->stream;
	struct byte_channel *c = &ctx->channel;

	mad_stream_buffer(s, c->outptr, c->inptr - c->outptr);
	s->error = 0;

	do {
		error = mad_frame_decode(f, s);
		if (error) {
			if (MAD_RECOVERABLE(s->error)) {
				check_stream_error(s, ctx->guard_ptr);
				continue;
			} else if (s->error == MAD_ERROR_BUFLEN) {
				return wrap_error(s->error);
			} else {
				return wrap_error(s->error);
			}
		}

	} while (error != 0);

	assert(s->next_frame >= s->outptr);
	assert(s->next_frame <= s->inptr);
	c->outptr = (unsigned char *)s->next_frame;

	mad_synth_frame(h, f);
    for(i = 0; i < (int)h->pcm.length && len > 4; i++, len -= 4) {
        int sample0 = audio_linear_dither(16, h->pcm.samples[0][i], &ctx->ditherl);
        //int sample1 = audio_linear_dither(16, h->pcm.samples[1][i], &ctx->ditherr);
        *p++ = (sample0 >> 0); *p++ = (sample0 >> 8);
        //*p++ = (sample1 >> 0); *p++ = (sample1 >> 8);
    }

	error = p - (char *)buf;
	return error;
}

static int wrapper_feed(struct mp3play_ctx *ctx, const jbyte *buf, int len)
{
	int sil;
	int pat;
	int error;
	char *dat;
	char begin[64];
	char cache[8192];
	struct rtphdr_ctx *rtphdr;
	struct byte_channel cchannel;

	rtphdr = (struct rtphdr_ctx *)buf;
	buf    = (char *)(rtphdr + 1);
	if (rtphdr->total + sizeof(*rtphdr) > len) {
		return -1;
	}

	pat = rtphdr->total;
	sil = rtphdr->silen;
	dat = (char *)(buf + rtphdr->silen);
	len = rtphdr->total - rtphdr->silen;

	memcpy(begin, buf, sil);
	fixed_begin(begin, ctx->space);
	if (len < ctx->space) {
		byte_channel_write(&ctx->channel, dat, len);
		ctx->space -= len;
		memset(cache, 0, ctx->space);
		byte_channel_write(&ctx->channel, cache, ctx->space);
		ctx->space = len = 0;
	} else {
		byte_channel_write(&ctx->channel, dat, ctx->space);
		dat += ctx->space;
		len -= ctx->space;
		ctx->space = 0;
	}

	byte_channel_write(&ctx->channel, begin, sil);
	if (len > rtphdr->seglen) {
		byte_channel_write(&ctx->channel, dat, rtphdr->seglen);
		ctx->space = 0;
	} else {
		byte_channel_write(&ctx->channel, dat, len);
		ctx->space += (rtphdr->seglen - len);
	}

	error = ctx->channel.flag;
	/* error = byte_channel_write(&ctx->channel, buf, len); */
#if 0
	guard_ptr = read_start + read_size;
	memset(guard_ptr, 0, MAD_BUFFER_GUARD);
	read_size += MAD_BUFFER_GUARD;
#endif
	return (error & 2)? -1: sizeof(*rtphdr) + pat;
}

static void wrapper_clean(struct mp3play_ctx *ctx)
{
	mad_stream_finish(&ctx->stream);
	mad_frame_finish(&ctx->frame);
	mad_synth_finish(&ctx->synth);
	free(ctx->input_buffer);
}

JNIEXPORT jint JNICALL
Java_wave_util_MadPlayer_length(JNIEnv *env, jclass _class)
{
	struct mp3play_ctx context;
	return sizeof(context);
}

JNIEXPORT void JNICALL
Java_wave_util_MadPlayer_init(JNIEnv *env, jobject _class, jbyteArray context)
{
	jbyte *ctx;
	ctx = (*env)->GetByteArrayElements(env, context, NULL);
	wrapper_init((struct mp3play_ctx *)ctx);
	(*env)->ReleaseByteArrayElements(env, context, ctx, 0);
}

JNIEXPORT void JNICALL
Java_wave_util_MadPlayer_fini(JNIEnv *env, jobject _class, jbyteArray context)
{
	jbyte *ctx;
	ctx = (*env)->GetByteArrayElements(env, context, NULL);
	wrapper_clean((struct mp3play_ctx *)ctx);
	(*env)->ReleaseByteArrayElements(env, context, ctx, 0);
}

JNIEXPORT jint JNICALL
Java_wave_util_MadPlayer_feed(JNIEnv *env, jobject _class, jbyteArray context, jbyteArray input, jint off, jint len)
{
	int retval;
	jbyte *ctx, *bufin;
	ctx = (*env)->GetByteArrayElements(env, context, NULL);
	bufin = (*env)->GetByteArrayElements(env, input, NULL);

	while (len > 0) {
		retval = wrapper_feed((struct mp3play_ctx *)ctx, bufin + off, len);
		if (retval <= 0)
			return retval;
		off += retval;
		len -= retval;
	}

	(*env)->ReleaseByteArrayElements(env, input, bufin, 0);
	(*env)->ReleaseByteArrayElements(env, context, ctx, 0);
	return retval;
}

JNIEXPORT jint JNICALL
Java_wave_util_MadPlayer_frame(JNIEnv *env, jobject _class, jbyteArray context, jbyteArray out, jint off, jint len)
{
	int retval;
	jbyte *ctx, *bufout;
	ctx = (*env)->GetByteArrayElements(env, context, NULL);
	bufout = (*env)->GetByteArrayElements(env, out, NULL);
	retval = wrapper_frame((struct mp3play_ctx *)ctx, bufout + off, len);
	(*env)->ReleaseByteArrayElements(env, out, bufout, 0);
	(*env)->ReleaseByteArrayElements(env, context, ctx, 0);
	return retval;
}

