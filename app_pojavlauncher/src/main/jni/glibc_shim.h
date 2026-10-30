/**
 * Lets Linux-built (glibc) native libraries that name the standard C libraries by their glibc
 * file names load on Android.
 *
 * Simple Voice Chat ships its Opus encoder, noise remover (RNNoise), gain control (Speex) and
 * MP3 encoder as Linux libraries. On a phone they fail with {@code library "libm.so.6" not
 * found}, and the mod falls back to a much slower voice encoder with no noise removal. Android
 * has the same functions under other file names (libm.so, libc.so). The trick: tiny libraries
 * that announce themselves as "libm.so.6" / "libc.so.6" / ... and depend on the Android ones are
 * loaded first; when the voice chat library then asks for "libm.so.6" the system finds that
 * the name is already loaded.
 *
 * Only done for the voice chat libraries (see urinth_needs_glibc_shims), so no other native
 * library in the game is affected. No Android headers here, so the name check is unit tested.
 */
#ifndef URINTH_GLIBC_SHIM_H
#define URINTH_GLIBC_SHIM_H

#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

/** True if a library path/file name is one of Simple Voice Chat's natives (opus4j, rnnoise4j, speex4j, lame4j). */
bool urinth_needs_glibc_shims(const char *path);

/** Load the shims (once). Returns how many are in place, 0 if none (or not asked for). */
int urinth_install_glibc_shims(void);

#ifdef __cplusplus
}
#endif

#endif
