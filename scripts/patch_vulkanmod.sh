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

for tool in unzip zip wget mktemp; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "Required command not found: $tool" >&2
    exit 1
  fi
done

tmp_base=${TMPDIR:-/tmp}
workdir=$(mktemp -d "${tmp_base%/}/vkmodpatch.XXXXXX")
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
unzip -q "$jar_path" 'META-INF/jars/lwjgl-*-3.3.1-natives-linux.jar' META-INF/jars/lwjgl-vulkan-3.3.1.jar

# Replace LWJGL Vulkan bindings with the Android-compatible Pojav module.
mkdir -p lwjgl-vulkan
unzip -q META-INF/jars/lwjgl-vulkan-3.3.1.jar 'META-INF/*' fabric.mod.json -d lwjgl-vulkan
wget -q -O lwjgl3-android-modules.zip https://nightly.link/PojavLauncherTeam/lwjgl3/workflows/build-android/3.3.1/lwjgl3-android-modules.zip
unzip -q lwjgl3-android-modules.zip lwjgl-vulkan/lwjgl-vulkan.jar
rm -f lwjgl3-android-modules.zip
mv lwjgl-vulkan/lwjgl-vulkan.jar META-INF/jars/lwjgl-vulkan-3.3.1.jar
(cd lwjgl-vulkan && zip -qr ../META-INF/jars/lwjgl-vulkan-3.3.1.jar META-INF fabric.mod.json)
rm -rf lwjgl-vulkan

copy_libs() {
  local arch=$1
  echo "Copying Android native libraries for $arch"

  mkdir -p "linux/$arch/org/lwjgl/shaderc" "linux/$arch/org/lwjgl/vma"
  wget -q -O "lwjgl3-android-natives-$arch.zip" "https://nightly.link/PojavLauncherTeam/lwjgl3/workflows/build-android/3.3.1/lwjgl3-android-natives-$arch.zip"
  unzip -q "lwjgl3-android-natives-$arch.zip" libshaderc.so liblwjgl_vma.so
  rm -f "lwjgl3-android-natives-$arch.zip"

  mv libshaderc.so "linux/$arch/org/lwjgl/shaderc/"
  mv liblwjgl_vma.so "linux/$arch/org/lwjgl/vma/"
  zip -q -gr "META-INF/jars/lwjgl-shaderc-3.3.1-natives-linux.jar" "linux/$arch/org/lwjgl/shaderc"
  zip -q -gr "META-INF/jars/lwjgl-vma-3.3.1-natives-linux.jar" "linux/$arch/org/lwjgl/vma"
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
