#!/usr/bin/env bash
set -Eeuo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
patcher="$script_dir/patch_vulkanmod.sh"
tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT
fake_jar="$tmpdir/fake.jar"
: > "$fake_jar"

set +e
"$patcher" >/dev/null 2>&1
status=$?
set -e
if [ "$status" -ne 2 ]; then
  echo "Expected usage error exit code 2, got $status" >&2
  exit 1
fi

set +e
"$patcher" "$fake_jar" invalid-architecture >/dev/null 2>&1
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

echo "VulkanMod patcher argument-validation tests passed"
