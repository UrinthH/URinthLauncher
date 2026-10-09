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

echo "VulkanMod patcher argument and LWJGL version-validation tests passed"
