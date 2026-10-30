#include "glibc_shim.h"

#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>

#define TAG "glibc_shim"
#include <log.h>

/* The shim libraries built by CMake; each one announces itself under a glibc name. */
static const char *const SHIMS[] = {
    "libglibc_shim_m.so",   /* libm.so.6 */
    "libglibc_shim_c.so",   /* libc.so.6 */
    "libglibc_shim_dl.so",  /* libdl.so.2 */
    "libglibc_shim_pthread.so", /* libpthread.so.0 */
};

int urinth_install_glibc_shims(void) {
    static int installed = -1;
    if (installed >= 0) return installed;
    const char *asked = getenv("URINTH_GLIBC_SHIMS");
    if (asked == NULL || asked[0] != '1') return 0; /* not asked for: try again if asked later */
    installed = 0;
    for (size_t i = 0; i < sizeof(SHIMS) / sizeof(SHIMS[0]); i++) {
        // Never closed: the voice chat libraries keep using them.
        if (dlopen(SHIMS[i], RTLD_NOW | RTLD_GLOBAL) != NULL) {
            installed++;
        } else {
            LOGW("could not load %s: %s", SHIMS[i], dlerror());
        }
    }
    LOGI("glibc name shims loaded: %d of %zu", installed, sizeof(SHIMS) / sizeof(SHIMS[0]));
    return installed;
}
