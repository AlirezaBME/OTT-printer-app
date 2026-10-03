/* Offline protocol oracle. Records the official module's output before transport.
 * This helper never opens a printer; it substitutes the complete Info I/O boundary.
 * No Canon implementation is included in this source. */
#define CANON_SESSION_CAPTURE
#include "pathmap.c"

// The selection oracle retains real initialization and stubs only external I/O.
#ifndef CANON_RECORD_REAL_INITIALIZATION
void *Info_Initialize_FilterCalled(const char *root, const char *queue, const char *mode, int flags) {
    return (void *)1;
}
#endif
long Info_commJobWrite(void *handle, const void *data, unsigned long *length) {
    const char *path = getenv("CANON_CPCA_CAPTURE");
    if (!path || !length || *length > 65556) return -1;
    FILE *output = fopen(path, "ab");
    if (!output) return -1;
    size_t written = fwrite(data, 1, *length, output);
    fclose(output);
    return written == *length ? 0 : -1;
}
long Info_SendXmlData(void *handle, void *a, void *b, void *c, void *d, int operation) { return 0; }
void Info_Terminate(void *handle) {}
long Info_Duplex_GetDigregData(void) { return -1; }
long Info_SimplexDuplex_GetCalib4Info(void) { return -1; }
void Info_FreeCalib(void) {}
const char *cupsGetPPD(const char *name) { return getenv("CANON_CAPTURE_PPD"); }
int unlink(const char *path) { return 0; } // Preserve the supplied PPD; no system files touched.
