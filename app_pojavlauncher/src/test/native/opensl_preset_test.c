/* Host-side test for the OpenSL preset helpers. Build and run:
 *   gcc -Wall -Wextra -fsanitize=address,undefined -g -I src/main/jni src/test/native/opensl_preset_test.c \
 *       src/main/jni/opensl_preset.c -o /tmp/opensl_preset_test && /tmp/opensl_preset_test */
#include "opensl_preset.h"
#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>

static int failures;
#define CHECK(cond) do { if (!(cond)) { printf("FAIL %s:%d  %s\n", __FILE__, __LINE__, #cond); failures++; } } while (0)

#define KEY "androidRecordingPreset"

static void test_rewrite(void) {
    uint32_t out = 0, v = 1;
    CHECK(urinth_preset_rewrite(KEY, &v, 4, KEY, 1, 4, &out) && out == 4);
    v = 3; out = 0; /* an explicit choice by the app stays */
    CHECK(!urinth_preset_rewrite(KEY, &v, 4, KEY, 1, 4, &out) && out == 0);
    v = 1;
    CHECK(!urinth_preset_rewrite("androidPerformanceMode", &v, 4, KEY, 1, 4, &out)); /* other key */
    CHECK(!urinth_preset_rewrite(KEY, &v, 2, KEY, 1, 4, &out));                      /* wrong size */
    CHECK(!urinth_preset_rewrite(KEY, &v, 8, KEY, 1, 4, &out));
    CHECK(!urinth_preset_rewrite(NULL, &v, 4, KEY, 1, 4, &out));
    CHECK(!urinth_preset_rewrite(KEY, NULL, 4, KEY, 1, 4, &out));
    CHECK(!urinth_preset_rewrite(KEY, &v, 4, KEY, 1, 4, NULL));
}

static void test_parse(void) {
    uintptr_t s = 0, e = 0; int p = -1;
    CHECK(urinth_parse_maps_line("7f1000-7f2000 r--p 00000000 08:01 123 /lib/libx.so\n", &s, &e, &p));
    CHECK(s == 0x7f1000 && e == 0x7f2000 && p == PROT_READ);
    CHECK(urinth_parse_maps_line("7f1000-7f2000 rw-p 0 0:0 0\n", &s, &e, &p) && p == (PROT_READ | PROT_WRITE));
    CHECK(urinth_parse_maps_line("7f1000-7f2000 r-xp 0 0:0 0\n", &s, &e, &p) && p == (PROT_READ | PROT_EXEC));
    CHECK(urinth_parse_maps_line("7f1000-7f2000 ---p 0 0:0 0\n", &s, &e, &p) && p == 0);
    CHECK(!urinth_parse_maps_line("garbage\n", &s, &e, &p));
    CHECK(!urinth_parse_maps_line("7f2000-7f1000 r--p 0 0:0 0\n", &s, &e, &p));
    CHECK(!urinth_parse_maps_line(NULL, &s, &e, &p));
}

static int a_fn(void) { return 1; }
static int b_fn(void) { return 2; }

static void test_patch_read_only_page(void) {
    long ps = sysconf(_SC_PAGESIZE);
    void **table = mmap(NULL, (size_t) ps, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    CHECK(table != MAP_FAILED);
    table[0] = (void *) a_fn;
    table[1] = (void *) a_fn; /* a neighbour that must stay as it is */
    CHECK(mprotect(table, (size_t) ps, PROT_READ) == 0);
    CHECK(urinth_page_protection((uintptr_t) table) == PROT_READ);

    void *original = NULL;
    CHECK(urinth_patch_slot(&table[0], (void *) b_fn, &original) == URINTH_PATCH_OK);
    CHECK(table[0] == (void *) b_fn);
    CHECK(original == (void *) a_fn);
    CHECK(table[1] == (void *) a_fn);
    /* the page is read-only again, exactly as before */
    CHECK(urinth_page_protection((uintptr_t) table) == PROT_READ);

    /* patching twice does nothing the second time */
    void *again = NULL;
    CHECK(urinth_patch_slot(&table[0], (void *) b_fn, &again) == URINTH_PATCH_ALREADY);
    CHECK(again == NULL);
    munmap(table, (size_t) ps);
}

static void test_patch_writable_page_stays_writable(void) {
    long ps = sysconf(_SC_PAGESIZE);
    void **table = mmap(NULL, (size_t) ps, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    CHECK(table != MAP_FAILED);
    table[0] = (void *) a_fn;
    void *original = NULL;
    CHECK(urinth_patch_slot(&table[0], (void *) b_fn, &original) == URINTH_PATCH_OK);
    CHECK(table[0] == (void *) b_fn && original == (void *) a_fn);
    CHECK(urinth_page_protection((uintptr_t) table) == (PROT_READ | PROT_WRITE));
    table[1] = (void *) a_fn; /* still writable: other data in the page is not broken */
    munmap(table, (size_t) ps);
}

static void test_bad_arguments(void) {
    void *slot = (void *) a_fn, *original = NULL;
    CHECK(urinth_patch_slot(NULL, (void *) b_fn, &original) == URINTH_PATCH_BAD_ARGUMENT);
    CHECK(urinth_patch_slot(&slot, NULL, &original) == URINTH_PATCH_BAD_ARGUMENT);
    CHECK(urinth_patch_slot(&slot, (void *) b_fn, NULL) == URINTH_PATCH_BAD_ARGUMENT);
    CHECK(urinth_page_protection(0) == -1);
}

int main(void) {
    test_rewrite();
    test_parse();
    test_patch_read_only_page();
    test_patch_writable_page_stays_writable();
    test_bad_arguments();
    if (failures) { printf("%d failure(s)\n", failures); return 1; }
    printf("opensl_preset: all tests passed\n");
    return 0;
}
