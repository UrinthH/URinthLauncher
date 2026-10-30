#include "glibc_shim.h"

#include <string.h>

bool urinth_needs_glibc_shims(const char *path) {
    if (path == NULL) return false;
    const char *name = strrchr(path, '/');
    name = name == NULL ? path : name + 1;
    static const char *const prefixes[] = {"libopus4j", "librnnoise4j", "libspeex4j", "liblame4j"};
    for (size_t i = 0; i < sizeof(prefixes) / sizeof(prefixes[0]); i++) {
        size_t length = strlen(prefixes[i]);
        // libopus4j.so, but also the "-<hash>" style names the mod may give them.
        if (strncmp(name, prefixes[i], length) == 0 && (name[length] == '.' || name[length] == '-')) return true;
    }
    return false;
}
