/* utils.c
 *
 * 32 bit fractional multiplication. Requires 64 bit integer support.
 */

/* Fractional multiply. */
static inline int mul(int x, int y)
{
	return (int)(((int64_t)x * (int64_t)y) >> 32);
}

/* Left justified fractional multiply. */
static inline int muls(int x, int y)
{
	return (int)(((int64_t)x * (int64_t)y) >> 31);
}

/* Fractional multiply with rounding. */
static inline int mulr(int x, int y)
{
	return (int)((((int64_t)x * (int64_t)y) + 0x80000000) >> 32);
}

/* Left justified fractional multiply with rounding. */
static inline int mulsr(int x, int y)
{
	return (int)((((int64_t)x * (int64_t)y) + 0x40000000) >> 31);
}

