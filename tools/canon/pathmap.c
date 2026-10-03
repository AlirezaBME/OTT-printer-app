/* Local reference harness only. No Canon code or binaries are bundled here. */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static const char *map_path(const char *path, char *buffer) {
    const char *root = getenv("CANON_REFERENCE_ROOT");
    if (!path || !root) return path;
    if (!strcmp(path, "/usr/bin/cnrsdrvsfp") ||
        !strcmp(path, "/usr/bin/cnjbigsfp") ||
        !strncmp(path, "/usr/share/caepcm", 17) ||
        !strncmp(path, "/usr/share/ncapfilterr", 21) ||
        !strncmp(path, "/usr/lib/Canon/", 15)) {
        int count = snprintf(buffer, PATH_MAX, "%s/binary%s", root, path);
        if (count > 0 && count < PATH_MAX) return buffer;
    }
    return path;
}

int execv(const char *path, char *const argv[]) {
    /* Force the documented no-session fallback even if Canon is installed globally.
       The output is NCAP PDL only, not a captured CPCA/USB print job. */
    if (!strcmp(path, "/usr/bin/cnpkmodulencapr")) {
        errno = ENOENT;
        return -1;
    }
    char buffer[PATH_MAX];
    int (*original)(const char *, char *const[]) = dlsym(RTLD_NEXT, "execv");
    return original(map_path(path, buffer), argv);
}

FILE *fopen(const char *path, const char *mode) {
    char buffer[PATH_MAX];
    FILE *(*original)(const char *, const char *) = dlsym(RTLD_NEXT, "fopen");
    return original(map_path(path, buffer), mode);
}

int open(const char *path, int flags, ...) {
    char buffer[PATH_MAX];
    int mode = 0;
    if (flags & O_CREAT) {
        va_list args;
        va_start(args, flags);
        mode = va_arg(args, int);
        va_end(args);
    }
    int (*original)(const char *, int, ...) = dlsym(RTLD_NEXT, "open");
    return original(map_path(path, buffer), flags, mode);
}
