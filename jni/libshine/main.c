/* main.c
 * Command line interface.
 *
 * This fixed point version of shine is based on Gabriel Bouvigne's original
 * source, version 0.1.2
 */

#include <fcntl.h>
#include <unistd.h>

#include "types.h"

static struct shine_ctx config;

/*
 * error:
 * ------
 */
void error(char *s)
{
	fprintf(stderr, "[ERROR] %s\n", s);
	exit(1);
}

/*
 * print_usage:
 * ------------
 */
static void print_usage()
{
	fprintf(stderr, "\nUSAGE   :  Shine [options] <infile> <outfile>\n"
			"options : -h            this help message\n"
			"          -b <bitrate>  set the bitrate [32-320], default 128kbit\n"
			"          -c            set copyright flag, default off\n"
			"          -o            reset original flag, default on\n"
			"          -r [sample rate]  raw cd data file instead of wave\n"
			"          -m            mono from stereo, raw mono with -r\n"
			"The sample rate is optional and defaults to 44100.\n\n");
	exit(0);
}

/*
 * parse_command:
 * --------------
 */
static int parse_command(int argc, char **argv, int *raw, int *mono_from_stereo)
{
	int i = 0, x;

	if (argc == 1)
		return 0;

	while (argv[++i][0] == '-') {
		switch(argv[i][1]) {
			case 'b':
				config.bitr = atoi(argv[++i]);
				break;

			case 'c':
				config.copyright = 1;
				break;

			case 'o':
				config.original = 0;
				break;

			case 'r':
				*raw = 1;
				x = atoi(argv[i + 1]);
				if(x > 7999) {
					config.samplerate = x;
					i++;
				}
				break;

			case 'm':
				*mono_from_stereo = 1;
				break;

			default :
				return 0;
		}
	}

	return 1;
}

static char ignore[16384];

/*
 * main:
 * -----
 */
int main(int argc, char **argv)
{
	int raw = 0;
	int n, p, total;
	int mono_from_stereo = 0;
	time_t start, end;
	static unsigned int buff[samp_per_frame];

	fprintf(stderr, "Shine v1.08 19/06/03\n");

	time(&start);
	shine_init(&config);
	if (!parse_command(argc, argv, &raw, &mono_from_stereo))
		print_usage();
	shine_update(&config);
	open_bit_stream(&config, "a.mp3");
	fprintf(stderr, "Shine %p\n", config.scalefac_band_long);

	n = config.samples_per_frame >> (2 - config.channels);
	for ( ; ; ) {
  		p = read(0, buff, sizeof(unsigned int) * n);
		if (p <= 0)
			break;

		p = p / sizeof(unsigned int);
		while (p < n)
      		buff[p++] = 0;

		total = shine_frame(&config, buff, n * sizeof(unsigned int), ignore, sizeof(ignore));
		write(1, ignore, total);
	}

	close_bit_stream(&config);
	shine_fini(&config);
	time(&end);
	fprintf(stderr, "use %ld\n", end - start);
	return 0;
}

