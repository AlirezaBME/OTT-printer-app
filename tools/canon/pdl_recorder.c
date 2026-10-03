/* Native reference framing oracle. Replaces pdWrite with an in-memory recorder. */
#include <string.h>
static unsigned char captured[8192];
static int used;
void recorder_reset(void) { used = 0; }
int recorder_size(void) { return used; }
const unsigned char *recorder_bytes(void) { return captured; }
unsigned char pdWrite(void *context, const void *bytes, int length) {
    if (!bytes || length < 0 || length > (int)sizeof(captured) - used) return 0;
    memcpy(captured + used, bytes, length); used += length; return 1;
}
unsigned char pdFlush(void *context) { return 1; }
