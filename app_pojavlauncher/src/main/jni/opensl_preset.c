#include "opensl_preset.h"

#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>

bool urinth_preset_rewrite(const char *key, const void *value, uint32_t size,
                           const char *preset_key, uint32_t from, uint32_t to, uint32_t *out) {
    if (key == NULL || value == NULL || out == NULL || preset_key == NULL) return false;
    if (strcmp(key, preset_key) != 0) return false;
    if (size != sizeof(uint32_t)) return false;
    uint32_t current;
    memcpy(&current, value, sizeof(current));
    if (current != from) return false;
    *out = to;
    return true;
}

bool urinth_parse_maps_line(const char *line, uintptr_t *start, uintptr_t *end, int *prot) {
    if (line == NULL) return false;
    unsigned long long a, b;
    char perms[8] = {0};
    if (sscanf(line, "%llx-%llx %7s", &a, &b, perms) != 3) return false;
    if (strlen(perms) < 4 || b < a) return false;
    int p = 0;
    if (perms[0] == 'r') p |= PROT_READ;
    if (perms[1] == 'w') p |= PROT_WRITE;
    if (perms[2] == 'x') p |= PROT_EXEC;
    if (start) *start = (uintptr_t) a;
    if (end) *end = (uintptr_t) b;
    if (prot) *prot = p;
    return true;
}

int urinth_page_protection(uintptr_t addr) {
    FILE *maps = fopen("/proc/self/maps", "r");
    if (maps == NULL) return -1;
    char line[512];
    int result = -1;
    while (fgets(line, sizeof(line), maps) != NULL) {
        uintptr_t start, end;
        int prot;
        if (!urinth_parse_maps_line(line, &start, &end, &prot)) continue;
        if (addr >= start && addr < end) {
            result = prot;
            break;
        }
    }
    fclose(maps);
    return result;
}

int urinth_patch_slot(void **slot, void *replacement, void **original) {
    if (slot == NULL || replacement == NULL || original == NULL) return URINTH_PATCH_BAD_ARGUMENT;
    if (*slot == replacement) return URINTH_PATCH_ALREADY;

    long page_size = sysconf(_SC_PAGESIZE);
    if (page_size <= 0) return URINTH_PATCH_BAD_ARGUMENT;
    uintptr_t address = (uintptr_t) slot;
    uintptr_t page = address & ~((uintptr_t) page_size - 1);
    // A pointer that is not aligned could straddle two pages; refuse rather than guess.
    if (address % sizeof(void *) != 0) return URINTH_PATCH_BAD_ARGUMENT;

    int original_prot = urinth_page_protection(address);
    if (original_prot < 0) return URINTH_PATCH_NO_PROTECTION;

    bool restore = (original_prot & PROT_WRITE) == 0;
    if (restore && mprotect((void *) page, (size_t) page_size, original_prot | PROT_WRITE) != 0) {
        return URINTH_PATCH_MPROTECT_FAILED;
    }
    *original = *slot;
    __atomic_store_n(slot, replacement, __ATOMIC_RELEASE);
    if (restore) mprotect((void *) page, (size_t) page_size, original_prot);
    return URINTH_PATCH_OK;
}
