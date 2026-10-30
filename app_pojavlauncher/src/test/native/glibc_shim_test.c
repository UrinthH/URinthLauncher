/* Host-side test for the voice chat library name check. Build and run:
 *   gcc -Wall -Wextra -fsanitize=address,undefined -g -I src/main/jni src/test/native/glibc_shim_test.c \
 *       src/main/jni/glibc_shim_names.c -o /tmp/glibc_shim_test && /tmp/glibc_shim_test */
#include "glibc_shim.h"
#include <stdio.h>

static int failures;
#define CHECK(cond) do { if (!(cond)) { printf("FAIL %s:%d  %s\n", __FILE__, __LINE__, #cond); failures++; } } while (0)

int main(void) {
    CHECK(urinth_needs_glibc_shims("/data/data/app/cache/libopus4j-0c14ef959ab7d20b13b3d22401544f72/libopus4j.so"));
    CHECK(urinth_needs_glibc_shims("/cache/librnnoise4j-9770d98d/librnnoise4j.so"));
    CHECK(urinth_needs_glibc_shims("/cache/libspeex4j-f00bcd09/libspeex4j.so"));
    CHECK(urinth_needs_glibc_shims("/cache/liblame4j-79a066a7/liblame4j.so"));
    CHECK(urinth_needs_glibc_shims("libopus4j.so"));
    CHECK(urinth_needs_glibc_shims("libopus4j-abc.so"));
    CHECK(!urinth_needs_glibc_shims("/lib/liblwjgl.so"));
    CHECK(!urinth_needs_glibc_shims("/lib/libopus.so"));
    CHECK(!urinth_needs_glibc_shims("/lib/libopus4jx.so"));
    CHECK(!urinth_needs_glibc_shims("/lib/libsqlitejdbc.so"));
    CHECK(!urinth_needs_glibc_shims("/cache/libopus4j-hash/other.so"));
    CHECK(!urinth_needs_glibc_shims(""));
    CHECK(!urinth_needs_glibc_shims(NULL));
    if (failures) { printf("%d failure(s)\n", failures); return 1; }
    printf("glibc_shim: all tests passed\n");
    return 0;
}
