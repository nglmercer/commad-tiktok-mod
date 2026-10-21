# Development

## Layout

```text
functions-core/   the reusable function engine; no Paper, no TikTok
paper-plugin/     the Paper plugin: connection, rules, actions
scripts/          build and packaging verification
examples/         complete example rules and functions
schemas/          the JSON schemas of the configuration formats
docs/             this documentation
```

`functions-core` is reused from
[nglmercer/commandclient](https://github.com/nglmercer/commandclient). It
knows nothing about Paper or TikTok: it parses function definitions,
compiles them into bounded plans and runs those plans through a
`MinecraftBridge`.

## Building

```bash
./gradlew clean build
./gradlew test
./gradlew :functions-core:test
./gradlew :paper-plugin:test
./gradlew :paper-plugin:assemble
./gradlew :paper-plugin:verifyPlugin
```

The build needs a JDK 21 toolchain. Gradle provisions it if it is missing.

The plugin JAR lands in `paper-plugin/build/libs/TikTokMinecraft-<version>.jar`.

## The TikTok SDK

The TikTok LIVE Java SDK is consumed as an external dependency. It is never
vendored into this repository.

```text
io.github.nglmercer:tiktok-live-client:0.1.0-SNAPSHOT
io.github.nglmercer:tiktok-live-jvm-signer:0.1.0-SNAPSHOT
```

For development, publish the SDK to Maven Local:

```bash
cd ../tiktok-signer/sdk/java
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew publishToMavenLocal
```

The build resolves the SDK from Maven Local by default. For a release,
publish the SDK to a Maven repository the project controls and point the
build at it:

```bash
./gradlew build -PtiktokSdkRepositoryUrl=https://repo.example.org/releases
```

The remote repository is consulted before Maven Local, so a release never
depends on a developer's machine.

## GraalJS

GraalJS is used by the SDK's embedded signer. It is not shaded into the
plugin: it is declared in `plugin.yml` and loaded by Paper's library loader
from Maven Central.

```yaml
libraries:
  - org.graalvm.polyglot:polyglot:${graalJsVersion}
  - org.graalvm.js:js-language:${graalJsVersion}
  - org.graalvm.truffle:truffle-runtime:${graalJsVersion}
```

The `js-community` coordinates are POM-only aggregators and cannot be
declared directly, which is why the concrete artifacts are used.

`./gradlew :paper-plugin:verifyGraalJs` resolves them, so a typo is caught
before a release.

## Packaging checks

`scripts/verify-plugin.py` inspects the built JAR the way the Paper server
sees it:

* no duplicate ZIP entries — Paper's plugin remapper refuses them
* a valid `plugin.yml` with the expected name, main class and api-version
* the classes the plugin needs are present
* nothing the server provides is bundled: no Bukkit, no Minecraft, no
  Gson, no GraalJS
* every notice with a licence obligation survived packaging
* the bytecode is not newer than Java 21

## Testing against a real server

```bash
./gradlew :paper-plugin:assemble
java -jar paper.jar --nogui
```

The plugin generates its configuration, the default rules and the default
functions on the first start. `/tiktok test chat hello` injects a synthetic
event through the whole pipeline without contacting TikTok.

A useful check is a function that spawns an entity with a relative
coordinate:

```json
{ "type": "summon", "entity": "minecraft:zombie", "position": "~ ~1 ~" }
```

With a player online, the entity must appear next to that player, not at the
world spawn.

## Threading rules

* The SDK's callbacks run on its own thread. They only normalize the event
  and hand it to the queue.
* Every Bukkit call happens on the server thread.
* A function runs on a worker thread and waits for the server thread to
  dispatch its commands. The server thread never waits for a function.
* A tick delay is timed by the Paper scheduler.

## Code style

* Four spaces, no tabs. Lines stay under 100 characters.
* Imports are grouped and never wildcarded.
* Every public type has a short javadoc that says what it is for and, where
  it matters, what it guarantees.
* Comments explain why, not what.
