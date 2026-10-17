#!/usr/bin/env bash
# Throws away the test server's database (users, play state, playlists) and seeds a fresh one.
# Keeps the generated media; delete .data/ to rebuild that too.
set -euo pipefail
cd "$(dirname "$0")"
docker compose down -v
exec ./up.sh
