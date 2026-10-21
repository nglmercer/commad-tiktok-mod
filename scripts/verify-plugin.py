#!/usr/bin/env python3
"""Verify a built TikTokMinecraft plugin JAR.

The checks mirror what a Paper server actually requires, plus the packaging
rules this project has committed to:

* no duplicate ZIP entries (Paper's plugin remapper rejects them),
* a valid plugin.yml with the expected name, main class and api-version,
* the classes the plugin needs at runtime are present,
* nothing that the server already provides is bundled (Bukkit, Minecraft,
  Gson, GraalJS), so the plugin cannot collide with the server or with
  another plugin,
* every notice that carries licence obligations survived packaging,
* the bytecode is not newer than Java 21.

Usage: verify-plugin.py <plugin.jar>
"""

import sys
import zipfile
from collections import Counter

EXPECTED_NAME = "TikTokMinecraft"
EXPECTED_MAIN = "dev.nglmercer.tiktokminecraft.TikTokMinecraftPlugin"
EXPECTED_API_VERSION = "1.21.11"

# Bundling any of these is a packaging error: the server supplies them, and a
# second copy inside the plugin shadows the server's classes. The Kotlin
# standard library is deliberately absent from this list: Paper does not ship
# it and OkHttp needs it, so it has to be bundled.
FORBIDDEN_PREFIXES = (
    "org/bukkit/",
    "net/minecraft/",
    "com/google/gson/",
    "org/graalvm/",
)

REQUIRED_ENTRIES = (
    "plugin.yml",
    "config.yml",
    "THIRD-PARTY-NOTICES.md",
    "META-INF/LICENSE.protobuf",
    "META-INF/THIRD_PARTY_NOTICES.md",
    "dev/nglmercer/tiktokminecraft/TikTokMinecraftPlugin.class",
    "dev/nglmercer/tiktokminecraft/tiktok/TikTokLiveService.class",
    "dev/nglmercer/tiktokminecraft/functions/FunctionCompiler.class",
)

# Java 21 is class file major version 65.
MAX_CLASS_MAJOR = 65


def fail(problems, message):
    problems.append(message)


def main(argv):
    if len(argv) != 2:
        print("usage: verify-plugin.py <plugin.jar>", file=sys.stderr)
        return 2
    path = argv[1]
    problems = []

    with zipfile.ZipFile(path) as jar:
        names = jar.namelist()

        duplicates = [
            name for name, count in Counter(names).items() if count > 1
        ]
        for name in sorted(duplicates):
            fail(problems, "duplicate ZIP entry: " + name)

        for required in REQUIRED_ENTRIES:
            if required not in names:
                fail(problems, "missing entry: " + required)

        for name in names:
            for prefix in FORBIDDEN_PREFIXES:
                if name.startswith(prefix):
                    fail(
                        problems,
                        "bundles %s, which the server or Paper's library "
                        "loader already provides: %s" % (prefix, name),
                    )
                    break

        plugin_yml = jar.read("plugin.yml").decode("utf-8")
        if ("name: " + EXPECTED_NAME) not in plugin_yml:
            fail(problems, "plugin.yml does not declare the name " + EXPECTED_NAME)
        if ("main: " + EXPECTED_MAIN) not in plugin_yml:
            fail(problems, "plugin.yml does not declare the main class " + EXPECTED_MAIN)
        if ("api-version: '" + EXPECTED_API_VERSION + "'") not in plugin_yml:
            fail(problems, "plugin.yml does not declare api-version " + EXPECTED_API_VERSION)
        if "${version}" in plugin_yml or "${graalJsVersion}" in plugin_yml:
            fail(problems, "plugin.yml still contains an unexpanded placeholder")

        highest = 0
        for name in names:
            if not name.endswith(".class"):
                continue
            with jar.open(name) as stream:
                header = stream.read(8)
            if len(header) < 8 or header[:4] != b"\xca\xfe\xba\xbe":
                continue
            major = int.from_bytes(header[6:8], "big")
            highest = max(highest, major)
        if highest > MAX_CLASS_MAJOR:
            fail(
                problems,
                "class file major version %d is newer than Java %d"
                % (highest, MAX_CLASS_MAJOR),
            )

    if problems:
        print("Plugin verification FAILED:")
        for problem in problems:
            print("  - " + problem)
        return 1

    print("Plugin verification passed: " + path)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
