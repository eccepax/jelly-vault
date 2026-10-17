#!/usr/bin/env bash
# Runs an instrumented e2e suite end-to-end: boots the emulator if needed, waits for it, disables
# animations (required by Espresso, and reset on every emulator boot), checks the test server is
# reachable, then runs the whole e2e package for the chosen module.
#
# Prerequisites (see common-testing's org/jellyfin/mobile/testing/endtoend/VaultE2ETest.kt):
#   - The test server running: test-server/up.sh (or E2E_START_SERVER=1 here). The tests run against
#     a real Jellyfin seeded with the fixtures in test-server/fixtures.json, not a mock.
#   - Its credentials: VAULT_TEST_USERNAME / VAULT_TEST_PASSWORD, or e2e.username / e2e.password in
#     local.properties (never commit these). The test server's are vault-test / vault-test.
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
#   E2E_START_SERVER      1 to bring up (and seed) the test server first, with test-server/up.sh
#   E2E_VAULT_CHECK_URL   URL polled to confirm the server is up (default: http://localhost:8097/System/Info/Public)
#   ANDROID_HOME          Android SDK root (falls back to the common Windows default if unset)
#   ANDROID_SERIAL        Device to test on; required when more than one is connected

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

MODULE="${E2E_MODULE:-app-audiovault}"
SUITE="${E2E_SUITE:-full}"
AVD_NAME="${E2E_AVD_NAME:-A52ish}"
VAULT_CHECK_URL="${E2E_VAULT_CHECK_URL:-http://localhost:8097/System/Info/Public}"
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

log "Checking for test server credentials"
has_property() { grep -q "^$1=" "$REPO_ROOT/local.properties" 2>/dev/null; }
if { [ -z "${VAULT_TEST_USERNAME:-}" ] && ! has_property 'e2e\.username'; } \
    || { [ -z "${VAULT_TEST_PASSWORD:-}" ] && ! has_property 'e2e\.password'; }; then
    die "No test server credentials — set VAULT_TEST_USERNAME / VAULT_TEST_PASSWORD (vault-test / vault-test for test-server/), or e2e.username / e2e.password in local.properties (never commit)."
fi

if [ "${E2E_START_SERVER:-0}" = "1" ]; then
    log "Bringing up the test server"
    "$REPO_ROOT/test-server/up.sh"
fi

log "Checking the test server is reachable ($VAULT_CHECK_URL)"
if ! curl -s -m 5 -o /dev/null -w '' "$VAULT_CHECK_URL"; then
    warn "Could not reach $VAULT_CHECK_URL — run test-server/up.sh (or E2E_START_SERVER=1); the e2e suite fails without a server."
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
