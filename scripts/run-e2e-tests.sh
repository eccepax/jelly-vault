#!/usr/bin/env bash
# Runs an instrumented e2e suite end-to-end: boots the emulator if needed, waits for it, disables
# animations (required by Espresso, and reset on every emulator boot), checks the Vault server is
# reachable, then runs the whole e2e package for the chosen module.
#
# Prerequisites (see common-testing's org/jellyfin/mobile/testing/endtoend/VaultE2ETest.kt):
#   - local.properties must have e2e.username / e2e.password (never commit these) — the tests
#     run against a real local Jellyfin server (the Vault), not a mock.
#   - The Vault server itself must be running and reachable.
#   - An Android emulator AVD must exist (default name below, override with E2E_AVD_NAME).
#
# Usage:
#   scripts/run-e2e-tests.sh                                    # app-audiovault, full e2e package
#   E2E_MODULE=app-bookvault scripts/run-e2e-tests.sh            # app-bookvault, full e2e package
#   scripts/run-e2e-tests.sh -Pandroid.testInstrumentationRunnerArguments.class=org.jellyfin.mobile.e2e.LoginFlowE2ETest
#     (any extra args are passed straight through to the gradlew invocation, e.g. to isolate one test)
#   E2E_MODULE=app-tunevault E2E_SUITE=smoke scripts/run-e2e-tests.sh   # app-tunevault, critical paths only
#
# Env vars:
#   E2E_MODULE            Which app module to test: app-audiovault (default), app-bookvault, app-tunevault
#                         or app-livevault
#   E2E_SUITE             Part of the suite to run (app-tunevault only; tags in its e2e/E2ESuites.kt):
#                           full (default), smoke (~2 min), or one area: browse, playback,
#                           collections (favorites/playlists), account, downloads (the slowest)
#   E2E_AVD_NAME         AVD to boot if none is already running (default: A52ish)
#   E2E_VAULT_CHECK_URL   URL polled to confirm the Vault is up (default: http://localhost:8096/System/Info/Public)
#   ANDROID_HOME          Android SDK root (falls back to the common Windows default if unset)
#   ANDROID_SERIAL        Device to test on; required when more than one is connected

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

MODULE="${E2E_MODULE:-app-audiovault}"
SUITE="${E2E_SUITE:-full}"
AVD_NAME="${E2E_AVD_NAME:-A52ish}"
VAULT_CHECK_URL="${E2E_VAULT_CHECK_URL:-http://localhost:8096/System/Info/Public}"
: "${ANDROID_HOME:=$HOME/AppData/Local/Android/Sdk}"
ADB="$ANDROID_HOME/platform-tools/adb.exe"
EMULATOR="$ANDROID_HOME/emulator/emulator.exe"

case "$MODULE" in
    app-audiovault)
        GRADLE_TASK=":app-audiovault:connectedDebugAndroidTest"
        E2E_PACKAGE="org.jellyfin.mobile.e2e"
        ;;
    app-bookvault)
        GRADLE_TASK=":app-bookvault:connectedDebugAndroidTest"
        E2E_PACKAGE="org.jellyfin.mobile.books.e2e"
        ;;
    app-tunevault)
        GRADLE_TASK=":app-tunevault:connectedDebugAndroidTest"
        E2E_PACKAGE="org.jellyfin.mobile.music.e2e"
        ;;
    app-livevault)
        GRADLE_TASK=":app-livevault:connectedDebugAndroidTest"
        E2E_PACKAGE="org.jellyfin.mobile.livetv.e2e"
        ;;
    *)
        echo "error: unknown E2E_MODULE '$MODULE' — expected app-audiovault, app-bookvault, app-tunevault or app-livevault" >&2
        exit 1
        ;;
esac

log() { printf '\n\033[1;34m==>\033[0m %s\n' "$1"; }
warn() { printf '\033[1;33mwarning:\033[0m %s\n' "$1" >&2; }
die() { printf '\033[1;31merror:\033[0m %s\n' "$1" >&2; exit 1; }

[ -x "$ADB" ] || die "adb not found at $ADB — set ANDROID_HOME"

log "Checking for e2e credentials in local.properties"
if ! grep -q '^e2e\.username=' "$REPO_ROOT/local.properties" 2>/dev/null \
    || ! grep -q '^e2e\.password=' "$REPO_ROOT/local.properties" 2>/dev/null; then
    die "local.properties is missing e2e.username / e2e.password — add them (never commit) before running e2e tests."
fi

log "Checking the Vault server is reachable ($VAULT_CHECK_URL)"
if ! curl -s -m 5 -o /dev/null -w '' "$VAULT_CHECK_URL"; then
    warn "Could not reach $VAULT_CHECK_URL — the e2e suite runs against a real server and will fail without it."
fi

log "Checking for a connected device/emulator"
# The tests wipe the app's data, so never guess between devices (e.g. an emulator and a personal phone)
DEVICE_COUNT="$("$ADB" devices | awk 'NR>1 && $2=="device"' | wc -l)"
if [ "$DEVICE_COUNT" -gt 1 ] && [ -z "${ANDROID_SERIAL:-}" ]; then
    die "$DEVICE_COUNT devices connected — set ANDROID_SERIAL to the one to test on"
fi
DEVICE_LINE="$("$ADB" devices | awk -v s="${ANDROID_SERIAL:-}" 'NR>1 && $2=="device" && (s=="" || $1==s) {print; exit}')"
if [ -z "$DEVICE_LINE" ]; then
    [ -x "$EMULATOR" ] || die "No device connected and emulator not found at $EMULATOR"
    log "No device connected — booting AVD '$AVD_NAME' (override with E2E_AVD_NAME)"
    nohup "$EMULATOR" -avd "$AVD_NAME" -no-snapshot-load >/tmp/e2e-emulator-boot.log 2>&1 &
    disown

    log "Waiting for boot to complete"
    for _ in $(seq 1 60); do
        BOOTED="$("$ADB" shell getprop sys.boot_completed 2>/dev/null || true)"
        [ "$BOOTED" = "1" ] && break
        sleep 3
    done
    [ "$BOOTED" = "1" ] || die "Emulator did not finish booting in time — check /tmp/e2e-emulator-boot.log"
else
    log "Using already-connected device: $DEVICE_LINE"
fi

log "Disabling animations (required by Espresso; resets on every emulator boot)"
"$ADB" shell settings put global window_animation_scale 0
"$ADB" shell settings put global transition_animation_scale 0
"$ADB" shell settings put global animator_duration_scale 0

SUITE_ARGS=()
if [ "$SUITE" != "full" ]; then
    [ "$MODULE" = "app-tunevault" ] || die "E2E_SUITE is only set up for app-tunevault; other modules run in full."
    case "$SUITE" in
        smoke) TAG="Smoke" ;;
        browse) TAG="BrowseArea" ;;
        playback) TAG="PlaybackArea" ;;
        collections) TAG="CollectionsArea" ;;
        account) TAG="AccountArea" ;;
        downloads) TAG="DownloadsArea" ;;
        *) die "unknown E2E_SUITE '$SUITE' — expected full, smoke, browse, playback, collections, account or downloads" ;;
    esac
    SUITE_ARGS=(-Pandroid.testInstrumentationRunnerArguments.annotation="$E2E_PACKAGE.$TAG")
fi

log "Running the $MODULE e2e suite ($SUITE)"
cd "$REPO_ROOT"
./gradlew "$GRADLE_TASK" \
    -Pandroid.testInstrumentationRunnerArguments.package="$E2E_PACKAGE" \
    ${SUITE_ARGS[@]+"${SUITE_ARGS[@]}"} \
    "$@"
