#!/bin/sh
# Rebuilds the two native shim libraries that are bundled in the mod jar.
#   linux-aarch64: for phones (built with clang for the Android ABI, 16 KB page aligned for Android 15+)
#   linux-x86_64 : for x86 devices and for testing on a computer
set -e
here="$(cd "$(dirname "$0")" && pwd)"
res="$here/../src/main/resources/natives"
mkdir -p "$res/linux-aarch64" "$res/linux-x86_64"

clang --target=aarch64-linux-android21 -O2 -fPIC -shared -nostdlib -ffreestanding -fno-stack-protector \
  -fno-unwind-tables -fno-asynchronous-unwind-tables -fuse-ld=lld \
  -Wl,--hash-style=both -Wl,-z,max-page-size=16384 -Wl,-z,noexecstack -Wl,--build-id=none -Wl,-soname,libreclite.so \
  -Wall -Wextra -o "$res/linux-aarch64/libreclite.so" "$here/reclite.c"

gcc -O2 -fPIC -shared -nostdlib -ffreestanding -fno-stack-protector \
  -fno-unwind-tables -fno-asynchronous-unwind-tables \
  -Wl,--hash-style=both -Wl,-z,noexecstack -Wl,--build-id=none -Wl,-soname,libreclite.so \
  -Wall -Wextra -o "$res/linux-x86_64/libreclite.so" "$here/reclite.c"

strip --strip-unneeded "$res/linux-x86_64/libreclite.so" 2>/dev/null || true
echo built
