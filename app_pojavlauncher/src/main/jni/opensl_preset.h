/**
 * Helpers for the OpenSL ES microphone preset fix. No Android headers, so the logic can be tested
 * on a normal computer (see src/test/native/opensl_preset_test.c).
 *
 * Why this exists: OpenAL Soft (what the game's audio runs on) records through OpenSL ES with the
 * "generic" preset, which switches off the phone's echo cancelling and noise reduction. A voice
 * chat mod without a headset then records its own loudspeaker. The launcher swaps that preset for
 * the "voice communication" one when it is asked to (see opensl_preset_hook.c).
 */
#ifndef URINTH_OPENSL_PRESET_H
#define URINTH_OPENSL_PRESET_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Decide whether a SetConfiguration(key, value, size) call is "set the recording preset to
 * {@code from}", and if so give the value to use instead. Anything else (another key, another
 * preset the app chose on purpose, a wrong size) is left alone and false is returned.
 */
bool urinth_preset_rewrite(const char *key, const void *value, uint32_t size,
                           const char *preset_key, uint32_t from, uint32_t to, uint32_t *out);

/**
 * Read one line of /proc/self/maps ("start-end perms ...") and give the range and its
 * protection as PROT_* bits. False if the line is not understood.
 */
bool urinth_parse_maps_line(const char *line, uintptr_t *start, uintptr_t *end, int *prot);

/**
 * Protection (PROT_* bits) of the page that holds {@code addr}, read from the process's maps.
 * -1 if it cannot be found.
 */
int urinth_page_protection(uintptr_t addr);

/** Results of urinth_patch_slot. */
enum {
    URINTH_PATCH_OK = 0,
    URINTH_PATCH_ALREADY = 1,
    URINTH_PATCH_BAD_ARGUMENT = -1,
    URINTH_PATCH_NO_PROTECTION = -2,
    URINTH_PATCH_MPROTECT_FAILED = -3,
};

/**
 * Replace the function pointer stored at {@code slot} with {@code replacement}, handing back the
 * old one in {@code original}. The memory page is made writable only for the single store and is
 * put back to exactly the protection it had before. Does nothing if the slot is already patched.
 */
int urinth_patch_slot(void **slot, void *replacement, void **original);

#ifdef __cplusplus
}
#endif

#endif
