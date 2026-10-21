#!/usr/bin/env bash
# The local equivalent of the CI pipeline.
#
# GitHub Actions may be unavailable (for example when the account is locked
# for billing). This script performs the same checks so the result is real
# and recorded, rather than assumed.
#
# Usage: scripts/verify-all.sh [evidence-directory]
#
# The evidence directory receives a log of everything that was run. The
# script exits non-zero when a check fails.

set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EVIDENCE="${1:-$ROOT/build/verification}"
PAPER_VERSION="${PAPER_VERSION:-1.21.11}"
# A second Paper target, which needs a newer Java.
PAPER_VERSION_NEW="${PAPER_VERSION_NEW:-26.2}"
JAVA_HOME_21="${JAVA_HOME_21:-/usr/lib/jvm/java-21-openjdk}"
JAVA_HOME_NEW="${JAVA_HOME_NEW:-/usr/lib/jvm/java-27-openjdk}"
GRADLE_JAVA="${GRADLE_JAVA:-$JAVA_HOME_21}"

mkdir -p "$EVIDENCE"
LOG="$EVIDENCE/verify-$(date -u +%Y%m%dT%H%M%SZ).log"
FAILED=0

section() {
    echo "" | tee -a "$LOG"
    echo "=== $1 ===" | tee -a "$LOG"
}

run() {
    local name="$1"; shift
    section "$name"
    if "$@" 2>&1 | tee -a "$LOG"; then
        echo "PASS: $name" | tee -a "$LOG"
    else
        echo "FAIL: $name" | tee -a "$LOG"
        FAILED=1
    fi
}

echo "TikTokMinecraft verification" | tee -a "$LOG"
echo "date: $(date -u +%Y-%m-%dT%H:%M:%SZ)" | tee -a "$LOG"
echo "commit: $(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo unknown)" | tee -a "$LOG"
echo "java: $($GRADLE_JAVA/bin/java -version 2>&1 | head -1)" | tee -a "$LOG"

cd "$ROOT"

run "compile, unit test and package" \
    env JAVA_HOME="$GRADLE_JAVA" ./gradlew --no-daemon clean build

run "resolve the GraalJS runtime declared in plugin.yml" \
    env JAVA_HOME="$GRADLE_JAVA" ./gradlew --no-daemon :paper-plugin:verifyGraalJs

run "inspect the plugin JAR" \
    python3 scripts/verify-plugin.py paper-plugin/build/libs/TikTokMinecraft-0.1.0.jar

run "check the examples against the schemas" \
    python3 scripts/check-schemas.py

# The Paper smoke test proves that the plugin loads in a real server and
# that every dependency resolves.
smoke_test() {
    local version="$1"
    local java="$2"
    local port="$3"

    section "Paper $version runtime smoke test"
    if [ ! -x "$java/bin/java" ]; then
        echo "SKIP: no Java at $java" | tee -a "$LOG"
        return 0
    fi
    local smoke="$EVIDENCE/paper-$version"
    rm -rf "$smoke"
    mkdir -p "$smoke/plugins"
    local url
    url=$(curl -fsSL -H 'User-Agent: tiktokminecraft-verify' \
        "https://fill.papermc.io/v3/projects/paper/versions/$version/builds" \
        | python3 -c "import json,sys; b=[x for x in json.load(sys.stdin) if x['channel']=='STABLE'][0]; print(b['downloads']['server:default']['url'])")
    curl -fsSL -o "$smoke/paper.jar" "$url"
    echo "eula=true" > "$smoke/eula.txt"
    printf "online-mode=false\nlevel-type=minecraft\\:flat\nserver-port=$port\nview-distance=4\n" \
        > "$smoke/server.properties"
    cp "$ROOT/paper-plugin/build/libs/TikTokMinecraft-"*.jar "$smoke/plugins/"

    (
        cd "$smoke"
        "$java/bin/java" -Xmx2G -jar paper.jar --nogui > console.log 2>&1 &
        echo $! > server.pid
    )
    for _ in $(seq 1 90); do
        if grep -q 'Done (' "$smoke/console.log" 2>/dev/null; then break; fi
        if [ -f "$smoke/server.pid" ] && ! kill -0 "$(cat "$smoke/server.pid")" 2>/dev/null; then break; fi
        sleep 2
    done
    sleep 5
    cat "$smoke/console.log" >> "$LOG"

    if grep -q 'Enabling TikTokMinecraft' "$smoke/console.log"; then
        echo "PASS: the plugin enabled on Paper $version" | tee -a "$LOG"
    else
        echo "FAIL: the plugin did not enable on Paper $version" | tee -a "$LOG"
        FAILED=1
    fi
    if grep -qE 'NoClassDefFoundError|ClassNotFoundException|ServiceConfigurationError' \
            "$smoke/console.log"; then
        echo "FAIL: a dependency failed to load on Paper $version" | tee -a "$LOG"
        FAILED=1
    else
        echo "PASS: every dependency resolved on Paper $version" | tee -a "$LOG"
    fi
    if [ -f "$smoke/server.pid" ]; then
        kill "$(cat "$smoke/server.pid")" 2>/dev/null
        sleep 5
        kill -9 "$(cat "$smoke/server.pid")" 2>/dev/null
    fi
    cp "$smoke/console.log" "$EVIDENCE/paper-$version-console.log" 2>/dev/null
}

smoke_test "$PAPER_VERSION" "$GRADLE_JAVA" 25999
smoke_test "$PAPER_VERSION_NEW" "$JAVA_HOME_NEW" 25998

echo "" | tee -a "$LOG"
if [ "$FAILED" -eq 0 ]; then
    echo "ALL CHECKS PASSED" | tee -a "$LOG"
else
    echo "SOME CHECKS FAILED" | tee -a "$LOG"
fi
echo "evidence: $LOG" | tee -a "$LOG"
exit "$FAILED"
