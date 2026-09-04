#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
android_version="$(sed -nE 's/^[[:space:]]*versionName = "([^"]+)".*/\1/p' "$repo_root/app/build.gradle.kts")"
harmony_app_version="$(sed -nE 's/^[[:space:]]*"versionName": "([^"]+)".*/\1/p' "$repo_root/harmony/AppScope/app.json5")"
harmony_package_version="$(sed -nE 's/^[[:space:]]*"version": "([^"]+)".*/\1/p' "$repo_root/harmony/oh-package.json5")"
harmony_entry_version="$(sed -nE 's/^[[:space:]]*"version": "([^"]+)".*/\1/p' "$repo_root/harmony/entry/oh-package.json5")"

test -n "$android_version"
for version in "$harmony_app_version" "$harmony_package_version" "$harmony_entry_version"; do
  if [[ "$version" != "$android_version" ]]; then
    echo "Mobile version mismatch: Android=$android_version, HarmonyOS=$harmony_app_version/$harmony_package_version/$harmony_entry_version" >&2
    exit 1
  fi
done

if [[ -n "${EXPECTED_RELEASE_TAG:-}" && "$EXPECTED_RELEASE_TAG" != "v$android_version" ]]; then
  echo "Release tag $EXPECTED_RELEASE_TAG does not match mobile version v$android_version" >&2
  exit 1
fi

printf '%s\n' "$android_version"
