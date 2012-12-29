#include <stdio.h>
#include <unistd.h>
#include <assert.h>

struct {
	int total;
	int silen;
	int seglen;
} rtphdr;

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

int main(int argc, char *argv[])
{
	int n, len, begin;
	char *data, buf[8192];

	begin = 0;
	n = read(0, &rtphdr, sizeof(rtphdr));
	while (n > 0) {
		len = read(0, buf, rtphdr.total);
		assert(len == rtphdr.total);

		len = rtphdr.total - rtphdr.silen;
		data = buf + rtphdr.silen;

		fixed_begin(buf, begin);
		if (len < begin) {
			write(1, data, len);
			begin -= len;
			data += len;
			len = 0;
			fprintf(stderr, "padding byte: %d\n", begin);
			write(1, data, begin);
			begin = 0;
		} else {
			write(1, data, begin);
			data += begin;
			len -= begin;
			begin = 0;
		}

		write(1, buf, rtphdr.silen);

		if (len > rtphdr.seglen) {
			fprintf(stderr, "drop byte: %d\n", len - rtphdr.seglen);
			write(1, data, rtphdr.seglen);
			begin = 0;
		} else {
			write(1, data, len);
			begin += rtphdr.seglen - len;
		}

		n = read(0, &rtphdr, sizeof(rtphdr));
	}

	return 0;
}

