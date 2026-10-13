#!/usr/bin/env bash
# Creates one release signing key per app, each with its own random password.
#
# Keys and passwords go in KEY_DIR (default: ../vault-keys next to this repo), outside version
# control. Each app module gets a keystore.properties pointing at its key; git ignores both.
# Passwords are never printed. Back up KEY_DIR somewhere safe: an app whose key is lost can never be
# updated again without users uninstalling it.
#
# Usage: scripts/create-signing-keys.sh            (KEY_DIR=/some/dir to put the keys elsewhere)
set -euo pipefail
trap 'echo "create-signing-keys: failed at line $LINENO" >&2' ERR

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
key_dir="${KEY_DIR:-$repo_root/../vault-keys}"
mkdir -p "$key_dir"
key_dir="$(cd "$key_dir" && pwd)"

if [ -n "${JAVA_HOME:-}" ]; then
    keytool="$(cygpath -u "$JAVA_HOME" 2>/dev/null || echo "$JAVA_HOME")/bin/keytool"
else
    keytool="$(command -v keytool)"
fi

# Java properties files treat backslashes as escapes, so store paths with forward slashes
props_path() { cygpath -m "$1" 2>/dev/null || echo "$1"; }

for name in AudioVault BookVault TuneVault; do
    app="$(tr '[:upper:]' '[:lower:]' <<< "$name")"
    keystore="$key_dir/$app.jks"
    if [ -e "$keystore" ]; then
        echo "$name: $keystore already exists, leaving it alone"
        continue
    fi

    # A bounded read, then a substring: piping /dev/urandom into head would trip pipefail
    random_chars="$(head -c 1024 /dev/urandom | LC_ALL=C tr -dc 'A-Za-z0-9')"
    password="${random_chars:0:32}"
    [ "${#password}" -eq 32 ] || { echo "Could not generate a password" >&2; exit 1; }
    "$keytool" -genkeypair -noprompt -storetype PKCS12 -keystore "$keystore" -alias "$app" \
        -keyalg RSA -keysize 4096 -validity 10950 -dname "CN=$name" \
        -storepass "$password" -keypass "$password" 2> /dev/null

    umask 077
    cat > "$key_dir/$app.properties" << EOF
storeFile=$(props_path "$keystore")
storePassword=$password
keyAlias=$app
keyPassword=$password
EOF
    cp "$key_dir/$app.properties" "$repo_root/app-$app/keystore.properties"

    fingerprint="$("$keytool" -list -v -keystore "$keystore" -storepass "$password" | grep -m1 'SHA256:' | sed 's/.*SHA256: //')"
    echo "$name: created $keystore"
    echo "  certificate SHA-256: $fingerprint"
done

echo
echo "Done. Back up $key_dir (keys + passwords) somewhere safe, e.g. a password manager or offline drive."
