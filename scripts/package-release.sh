#!/usr/bin/env bash
# Collects signed release APKs into build/release/<app>/ under their public names, with SHA-256
# checksums and the signing certificate's fingerprint. Run after the release builds, e.g.:
#
#   ./gradlew assembleRelease
#   scripts/package-release.sh
#
# Fails if any APK is missing or unsigned, so an unsigned build can't be released by mistake.
set -euo pipefail
trap 'echo "package-release: failed at line $LINENO" >&2' ERR

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
sdk="${ANDROID_HOME:-${LOCALAPPDATA:-}/Android/Sdk}"
sdk="$(cygpath -u "$sdk" 2>/dev/null || echo "$sdk")"
build_tools="$(ls -d "$sdk"/build-tools/* | sort -V | tail -1)"
apksigner="$build_tools/apksigner"
[ -e "$apksigner" ] || apksigner="$build_tools/apksigner.bat"

version_of() { grep '^app.version=' "$repo_root/app-$1/gradle.properties" | cut -d= -f2; }

out="$repo_root/build/release"
rm -rf "$out"

# app  source APK (relative to the module's apk output dir)  public name
package() {
    local app="$1" source="$2" name="$3"
    local apk="$repo_root/app-$app/build/outputs/apk/$source"
    [ -f "$apk" ] || { echo "Missing $apk. Build the release first (and run scripts/create-signing-keys.sh)." >&2; exit 1; }
    mkdir -p "$out/$app"
    cp "$apk" "$out/$app/$name"
    (cd "$out/$app" && sha256sum "$name" > "$name.sha256")
    local cert
    cert="$("$apksigner" verify --print-certs "$out/$app/$name" | grep -m1 'certificate SHA-256 digest' | sed 's/.*digest: //')"
    echo "$name"
    echo "  sha256:      $(cut -d' ' -f1 "$out/$app/$name.sha256")"
    echo "  certificate: $cert"
}

v="$(version_of audiovault)"
package audiovault "release/audiovault-v$v-release.apk" "AudioVault-$v.apk"
v="$(version_of bookvault)"
package bookvault "release/bookvault-v$v-release.apk" "BookVault-$v.apk"
v="$(version_of tunevault)"
package tunevault "release/tunevault-v$v-release.apk" "TuneVault-$v.apk"
v="$(version_of livevault)"
package livevault "release/livevault-v$v-release.apk" "LiveVault-$v.apk"

echo
echo "Packaged into $out"
