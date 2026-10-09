#!/usr/bin/env bash
set -Eeuo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
patcher="$script_dir/patch_vulkanmod.sh"
tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT
fake_jar="$tmpdir/fake.jar"
: > "$fake_jar"

set +e
bash "$patcher" >/dev/null 2>&1
status=$?
set -e
if [ "$status" -ne 2 ]; then
  echo "Expected usage error exit code 2, got $status" >&2
  exit 1
fi

set +e
bash "$patcher" "$fake_jar" invalid-architecture >/dev/null 2>&1
status=$?
set -e
if [ "$status" -ne 2 ]; then
  echo "Expected invalid-architecture exit code 2, got $status" >&2
  exit 1
fi

if [ -s "$fake_jar" ]; then
  echo "Argument validation unexpectedly modified the input jar" >&2
  exit 1
fi

# The check-only mode verifies supported versions without downloading or patching.
for version in 3.3.1 3.3.3; do
  rm -rf "$tmpdir/content"
  mkdir -p "$tmpdir/content/META-INF/jars"
  : > "$tmpdir/content/META-INF/jars/lwjgl-vulkan-$version.jar"
  rm -f "$fake_jar"
  (cd "$tmpdir/content" && zip -q -r "$fake_jar" META-INF)
  cp "$fake_jar" "$tmpdir/before.jar"
  output=$(bash "$patcher" "$fake_jar" --check 2>&1)
  if [[ "$output" != *"Version check passed for LWJGL $version"* ]]; then
    echo "Expected successful check for LWJGL $version; got: $output" >&2
    exit 1
  fi
  if ! cmp -s "$fake_jar" "$tmpdir/before.jar"; then
    echo "Check-only mode modified the LWJGL $version jar" >&2
    exit 1
  fi
done

# A structurally valid jar with an unsupported LWJGL version must fail before
# any downloads and must leave the original artifact untouched.
rm -rf "$tmpdir/content"
mkdir -p "$tmpdir/content/META-INF/jars"
: > "$tmpdir/content/META-INF/jars/lwjgl-vulkan-3.3.4.jar"
rm -f "$fake_jar"
(cd "$tmpdir/content" && zip -q -r "$fake_jar" META-INF)
cp "$fake_jar" "$tmpdir/before.jar"
set +e
output=$(bash "$patcher" "$fake_jar" arm64 2>&1)
status=$?
set -e
if [ "$status" -ne 1 ] || [[ "$output" != *"Unsupported LWJGL version: 3.3.4"* ]]; then
  echo "Expected clear unsupported-version failure; got exit=$status output=$output" >&2
  exit 1
fi
if ! cmp -s "$fake_jar" "$tmpdir/before.jar"; then
  echo "Unsupported-version validation modified the original jar" >&2
  exit 1
fi


# Exercise a complete offline patch using a fake downloader. This catches ZIP
# layout errors and duplicate native entries without relying on nightly.link.
fakebin="$tmpdir/fakebin"
mkdir -p "$fakebin"
cat > "$fakebin/wget" <<'FAKE_WGET'
#!/usr/bin/env bash
set -Eeuo pipefail
out=""
url=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    -O) out=$2; shift 2 ;;
    -q) shift ;;
    *) url=$1; shift ;;
  esac
done
[ -n "$out" ] && [ -n "$url" ]
case "$out" in /*) ;; *) out="$PWD/$out" ;; esac
case "$url" in
  *lwjgl3-android-modules.zip)
    mkdir -p fake-module/lwjgl-vulkan/META-INF
    printf 'android-module\n' > fake-module/lwjgl-vulkan/META-INF/module.marker
    printf '{"id":"lwjgl-vulkan"}\n' > fake-module/lwjgl-vulkan/fabric.mod.json
    module_zip="$PWD/fake-module/lwjgl-vulkan.jar"
    (cd fake-module/lwjgl-vulkan && zip -qr "$module_zip" META-INF fabric.mod.json)
    mkdir -p lwjgl-vulkan
    cp "$module_zip" lwjgl-vulkan/lwjgl-vulkan.jar
    zip -qr "$out" lwjgl-vulkan/lwjgl-vulkan.jar
    ;;
  *lwjgl3-android-natives-*.zip)
    printf 'new-shaderc-native\n' > libshaderc.so
    printf 'new-vma-native\n' > liblwjgl_vma.so
    zip -q "$out" libshaderc.so liblwjgl_vma.so
    ;;
  *) echo "Unexpected fake download URL: $url" >&2; exit 1 ;;
esac
FAKE_WGET
chmod +x "$fakebin/wget"

fixture="$tmpdir/fixture"
mkdir -p "$fixture/META-INF/jars" "$fixture/seed/META-INF"
printf 'original-module\n' > "$fixture/seed/module.txt"
printf 'original-metadata\n' > "$fixture/seed/META-INF/module.marker"
printf '{"id":"original-vulkan"}\n' > "$fixture/seed/fabric.mod.json"
(cd "$fixture/seed" && zip -q -r "$fixture/META-INF/jars/lwjgl-vulkan-3.3.3.jar" module.txt META-INF fabric.mod.json)
printf '{"id":"outer-vulkanmod"}\n' > "$fixture/META-INF/jars/fabric.mod.json"
for module in shaderc vma; do
  case "$module" in
    shaderc) native_path="linux/arm64/org/lwjgl/shaderc/libshaderc.so" ;;
    vma) native_path="linux/arm64/org/lwjgl/vma/liblwjgl_vma.so" ;;
  esac
  mkdir -p "$fixture/old/$module/$(dirname "$native_path")"
  printf 'old-native\n' > "$fixture/old/$module/$native_path"
  (cd "$fixture/old/$module" && zip -q -r "$fixture/META-INF/jars/lwjgl-$module-3.3.3-natives-linux.jar" linux)
done
(cd "$fixture" && zip -q -r "$tmpdir/full.jar" META-INF)
PATH="$fakebin:$PATH" bash "$patcher" "$tmpdir/full.jar" arm64 > "$tmpdir/full-patch.log" 2>&1 || {
  cat "$tmpdir/full-patch.log" >&2
  exit 1
}

# The outer archive should contain one current module entry, not duplicate paths.
if [ "$(unzip -Z1 "$tmpdir/full.jar" | grep -Fxc 'META-INF/jars/lwjgl-vulkan-3.3.3.jar')" -ne 1 ]; then
  echo "Patched jar has a duplicate or missing Vulkan module entry" >&2
  exit 1
fi
mkdir -p "$tmpdir/verify"
unzip -q "$tmpdir/full.jar" 'META-INF/jars/lwjgl-shaderc-3.3.3-natives-linux.jar' 'META-INF/jars/lwjgl-vma-3.3.3-natives-linux.jar' -d "$tmpdir/verify"
shaderc_jar="$tmpdir/verify/META-INF/jars/lwjgl-shaderc-3.3.3-natives-linux.jar"
vma_jar="$tmpdir/verify/META-INF/jars/lwjgl-vma-3.3.3-natives-linux.jar"
if [ "$(unzip -Z1 "$shaderc_jar" | grep -Fxc 'linux/arm64/org/lwjgl/shaderc/libshaderc.so')" -ne 1 ]; then
  echo "Patched shaderc jar has a duplicate or missing arm64 native entry" >&2
  exit 1
fi
if [ "$(unzip -Z1 "$vma_jar" | grep -Fxc 'linux/arm64/org/lwjgl/vma/liblwjgl_vma.so')" -ne 1 ]; then
  echo "Patched VMA jar has a duplicate or missing arm64 native entry" >&2
  exit 1
fi
if [ "$(unzip -p "$shaderc_jar" linux/arm64/org/lwjgl/shaderc/libshaderc.so)" != "new-shaderc-native" ]; then
  echo "Patched shaderc jar does not contain the replacement native library" >&2
  exit 1
fi
if [ "$(unzip -p "$vma_jar" linux/arm64/org/lwjgl/vma/liblwjgl_vma.so)" != "new-vma-native" ]; then
  echo "Patched VMA jar does not contain the replacement native library" >&2
  exit 1
fi

echo "VulkanMod patcher argument, version, archive-repackaging, and duplicate-entry tests passed"
