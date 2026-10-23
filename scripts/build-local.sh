#!/usr/bin/env bash
# Offline-CI alternative: locally build the installable Paper 1.21.11 plugin.
# Internet is required on first use for Git, Gradle, Maven and Paper dependencies.
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK_REF="82e25287f30851f53003da2daf63b0b69563199a"
SDK_DIR="$ROOT/.build-deps/tiktok-signer"
if [ "$#" -gt 1 ]; then
    echo "Usage: bash scripts/build-local.sh [path/to/tiktok-signer]" >&2
    exit 2
fi
if [ "$#" -eq 1 ]; then
    SDK_DIR="$(cd "$1" && pwd)"
fi

for cmd in git java javac python3; do
    command -v "$cmd" >/dev/null || { echo "Missing: $cmd" >&2; exit 1; }
done
JAVA_MAJOR="$(java -version 2>&1 | sed -nE '1s/.*version "?([0-9]+).*/\1/p')"
JAVAC_MAJOR="$(javac -version 2>&1 | sed -nE 's/^javac ([0-9]+).*/\1/p')"
if [ "$JAVA_MAJOR" != "21" ] || [ "$JAVAC_MAJOR" != "21" ]; then
    echo "JDK 21 required (java=$JAVA_MAJOR, javac=$JAVAC_MAJOR)." >&2
    echo "Set JAVA_HOME and PATH to the Java 21 JDK." >&2
    exit 1
fi

if [ "$#" -eq 0 ]; then
    if [ ! -d "$SDK_DIR/.git" ]; then
        if [ -e "$SDK_DIR" ]; then
            echo "Existing non-git SDK directory: $SDK_DIR" >&2
            exit 1
        fi
        mkdir -p "$(dirname "$SDK_DIR")"
        git clone --quiet --depth 1 --branch main \
            https://github.com/nglmercer/tiktok-signer.git "$SDK_DIR"
    fi
    if [ -n "$(git -C "$SDK_DIR" status --porcelain)" ]; then
        echo "SDK checkout contains local changes, not changing it." >&2
        exit 1
    fi
    git -C "$SDK_DIR" fetch --quiet origin "$SDK_REF"
    git -C "$SDK_DIR" checkout --quiet --detach "$SDK_REF"
else
    [ -f "$SDK_DIR/sdk/java/gradlew" ] || {
        echo "Not a tiktok-signer checkout: $SDK_DIR" >&2
        exit 1
    }
    if [ "$(git -C "$SDK_DIR" rev-parse HEAD)" != "$SDK_REF" ]; then
        echo "WARNING: Custom SDK checkout is not the pinned commit." >&2
    fi
fi

echo "1/4 - Publish TikTok Java SDK to Maven Local"
(cd "$SDK_DIR/sdk/java" && bash ./gradlew publishToMavenLocal --no-daemon --stacktrace)

echo "2/4 - Build and test Paper 1.21.11 plugin"
(cd "$ROOT" && bash ./gradlew clean build :paper-plugin:verifyGraalJs --no-daemon --stacktrace)

echo "3/4 - Validate the installable shaded JAR and schemas"
JAR="$ROOT/paper-plugin/build/libs/TikTokMinecraft-0.1.0.jar"
[ -f "$JAR" ] || { echo "Missing JAR: $JAR" >&2; exit 1; }
python3 "$ROOT/scripts/verify-plugin.py" "$JAR"
python3 "$ROOT/scripts/check-schemas.py"

echo "4/4 - Export the installable JAR"
mkdir -p "$ROOT/dist"
cp "$JAR" "$ROOT/dist/TikTokMinecraft-1.21.11.jar"
(cd "$ROOT/dist" && sha256sum TikTokMinecraft-1.21.11.jar > TikTokMinecraft-1.21.11.jar.sha256)
echo "Ready: $ROOT/dist/TikTokMinecraft-1.21.11.jar"
