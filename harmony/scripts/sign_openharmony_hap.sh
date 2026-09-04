#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <unsigned.hap> <signed.hap>" >&2
  exit 2
fi

unsigned_hap="$(realpath "$1")"
signed_hap="$(realpath -m "$2")"
if [[ ! -f "$unsigned_hap" ]]; then
  echo "Unsigned HAP not found: $unsigned_hap" >&2
  exit 3
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
app_config="$script_dir/../AppScope/app.json5"
bundle_name="$(jq -r '.app.bundleName' "$app_config")"
version_name="$(jq -r '.app.versionName' "$app_config")"
version_code="$(jq -r '.app.versionCode' "$app_config")"
test -n "$bundle_name"
test -n "$version_name"
test "$version_code" -gt 0

signer_jar="${HAP_SIGN_TOOL_JAR:-}"
if [[ -z "$signer_jar" && -n "${HOS_SDK_HOME:-}" ]]; then
  signer_jar="$(find "$HOS_SDK_HOME" -type f -name 'hap-sign-tool.jar' -print -quit)"
fi
if [[ -z "$signer_jar" || ! -f "$signer_jar" ]]; then
  echo "hap-sign-tool.jar not found. Set HAP_SIGN_TOOL_JAR or HOS_SDK_HOME." >&2
  exit 4
fi

work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT
git clone --depth 1 https://github.com/openharmony/developtools_hapsigner.git "$work_dir/hapsigner"

autosign_dir="$work_dir/hapsigner/autosign"
mkdir -p "$work_dir/hapsigner/hapsigntool/hap_sign_tool/build/libs"
cp "$signer_jar" "$work_dir/hapsigner/hapsigntool/hap_sign_tool/build/libs/hap-sign-tool.jar"
cp "$unsigned_hap" "$autosign_dir/app1-unsigned.hap"

now_epoch="$(date +%s)"
not_before="$((now_epoch - 3600))"
not_after="$((now_epoch + 31536000))"
profile="$autosign_dir/UnsgnedReleasedProfileTemplate.json"
jq \
  --arg bundle "$bundle_name" \
  --arg versionName "$version_name" \
  --argjson versionCode "$version_code" \
  --argjson before "$not_before" \
  --argjson after "$not_after" \
  '."version-name" = $versionName |
   ."version-code" = $versionCode |
   ."app-distribution-type" = "none" |
   .validity."not-before" = $before |
   .validity."not-after" = $after |
   .type = "release" |
   ."bundle-info"."bundle-name" = $bundle |
   ."bundle-info".apl = "normal" |
   ."bundle-info"."app-feature" = "hos_normal_app"' \
  "$profile" > "$profile.tmp"
mv "$profile.tmp" "$profile"

pushd "$autosign_dir" >/dev/null
python3 autosign.py createAppCertAndProfile
python3 autosign.py signHap
java -jar "$signer_jar" verify-app \
  -inFile result/app1-signed.hap \
  -outCertChain "$work_dir/cert-chain.cer" \
  -outProfile "$work_dir/profile.p7b"
popd >/dev/null

mkdir -p "$(dirname "$signed_hap")"
cp "$autosign_dir/result/app1-signed.hap" "$signed_hap"
echo "OpenHarmony signed HAP: $signed_hap"
