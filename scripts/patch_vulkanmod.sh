#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  echo "Usage: $0 /path/to/VulkanMod.jar [arm64 arm32 x64 x86]" >&2
  echo "Valid architectures: arm64 arm32 x64 x86" >&2
}

if [ "$#" -lt 2 ]; then
  usage
  exit 2
fi

jar_arg=$1
shift
case "$jar_arg" in
  /*) jar_path=$jar_arg ;;
  *) jar_path="$PWD/$jar_arg" ;;
esac

if [ ! -f "$jar_path" ]; then
  echo "VulkanMod jar not found: $jar_path" >&2
  exit 1
fi

# Validate every requested architecture before modifying any files.
for arch in "$@"; do
  case "$arch" in
    arm64|arm32|x64|x86) ;;
    *)
      echo "Unsupported architecture: $arch" >&2
      usage
      exit 2
      ;;
  esac
done

for tool in unzip zip wget mktemp grep head; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "Required command not found: $tool" >&2
    exit 1
  fi
done

# Match the Android native modules to the exact LWJGL version bundled by the mod.
# Never mix Java bindings and native libraries from different LWJGL versions.
if ! jar_entries=$(unzip -Z1 "$jar_path" 2>/dev/null); then
  echo "Cannot read VulkanMod jar as a ZIP archive: $jar_path" >&2
  exit 1
fi
vulkan_jar=$(printf '%s\n' "$jar_entries" | grep -E '^META-INF/jars/lwjgl-vulkan-[0-9]+\.[0-9]+\.[0-9]+\.jar$' | head -n 1 || true)
if [ -z "$vulkan_jar" ]; then
  echo "Could not identify the bundled LWJGL Vulkan module version in: $jar_path" >&2
  exit 1
fi
lwjgl_version=${vulkan_jar##*lwjgl-vulkan-}
lwjgl_version=${lwjgl_version%.jar}
case "$lwjgl_version" in
  3.3.1|3.3.3) ;;
  *)
    echo "Unsupported LWJGL version: $lwjgl_version. Supported patch builds: 3.3.1 and 3.3.3." >&2
    echo "No files were changed. Do not mix Android native libraries across LWJGL versions." >&2
    exit 1
    ;;
esac
echo "Detected LWJGL $lwjgl_version; selecting matching Android modules and natives."

tmp_base=${TMPDIR:-/tmp}
workdir=$(mktemp "${tmp_base%/}/vkmodpatch.XXXXXX")
patch_tmp=""
cleanup() {
  if [ -n "$patch_tmp" ] && [ -e "$patch_tmp" ]; then
    rm -f "$patch_tmp"
  fi
  rm -rf "$workdir"
}
trap cleanup EXIT

cd "$workdir"

echo "Extracting VulkanMod libraries from $jar_path"
unzip -q "$jar_path" "META-INF/jars/lwjgl-*-$lwjgl_version-natives-linux.jar" "META-INF/jars/lwjgl-vulkan-$lwjgl_version.jar"

# Replace LWJGL Vulkan bindings with the Android-compatible Pojav module of the same version.
mkdir -p lwjgl-vulkan
unzip -q "META-INF/jars/lwjgl-vulkan-$lwjgl_version.jar" 'META-INF/*' fabric.mod.json -d lwjgl-vulkan
wget -q -O lwjgl3-android-modules.zip "https://nightly.link/PojavLauncherTeam/lwjgl3/workflows/build-android/$lwjgl_version/lwjgl3-android-modules.zip"
unzip -q lwjgl3-android-modules.zip lwjgl-vulkan/lwjgl-vulkan.jar
rm -f lwjgl3-android-modules.zip
mv lwjgl-vulkan/lwjgl-vulkan.jar "META-INF/jars/lwjgl-vulkan-$lwjgl_version.jar"
(cd lwjgl-vulkan && zip -qr "../META-INF/jars/lwjgl-vulkan-$lwjgl_version.jar" META-INF fabric.mod.json)
rm -rf lwjgl-vulkan

copy_libs() {
  local arch=$1
  echo "Copying Android native libraries for $arch"

  mkdir -p "linux/$arch/org/lwjgl/shaderc" "linux/$arch/org/lwjgl/vma"
  wget -q -O "lwjgl3-android-natives-$arch.zip" "https://nightly.link/PojavLauncherTeam/lwjgl3/workflows/build-android/$lwjgl_version/lwjgl3-android-natives-$arch.zip"
  unzip -q "lwjgl3-android-natives-$arch.zip" libshaderc.so liblwjgl_vma.so
  rm -f "lwjgl3-android-natives-$arch.zip"

  mv libshaderc.so "linux/$arch/org/lwjgl/shaderc/"
  mv liblwjgl_vma.so "linux/$arch/org/lwjgl/vma/"
  zip -q -gr "META-INF/jars/lwjgl-shaderc-$lwjgl_version-natives-linux.jar" "linux/$arch/org/lwjgl/shaderc"
  zip -q -gr "META-INF/jars/lwjgl-vma-$lwjgl_version-natives-linux.jar" "linux/$arch/org/lwjgl/vma"
  rm -rf linux
}

for arch in "$@"; do
  copy_libs "$arch"
done

# Patch a temporary copy first. Keep the source jar intact if extraction,
# download, or repackaging fails; replace it only after the archive is ready.
patch_tmp=$(mktemp "${jar_path}.tmp.XXXXXX")
cp "$jar_path" "$patch_tmp"
zip -q -gr "$patch_tmp" META-INF
mv -f "$patch_tmp" "$jar_path"
patch_tmp=""

echo "Done: patched $jar_path"
