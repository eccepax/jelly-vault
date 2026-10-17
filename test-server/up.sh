#!/usr/bin/env bash
# Brings up the test Jellyfin on :8097 (VAULT_TEST_PORT), seeded from fixtures.json.
# Media is generated once and reused; re-running this on a seeded server just re-checks it.
#   JELLYFIN_VERSION=latest ./up.sh     test against the newest Jellyfin (its own config volume)
#   SEED_TASK_TIMEOUT=900 ./up.sh       allow slow scans longer (seconds per scheduled task)
set -euo pipefail
cd "$(dirname "$0")"

# On Linux, write generated media as the calling user, not root, so it can be cleaned up.
user_flag=()
if [ "$(uname -s)" = "Linux" ]; then user_flag=(--user "$(id -u):$(id -g)"); fi

docker compose --profile tools build --quiet tools
# ${a[@]+...}: bash before 4.4 (macOS's) calls an empty array unbound under set -u.
docker compose run --rm ${user_flag[@]+"${user_flag[@]}"} tools python generate_media.py
docker compose up -d jellyfin media-host
docker compose run --rm tools python seed.py
