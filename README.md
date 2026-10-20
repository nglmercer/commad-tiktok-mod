# TikTokMinecraft

A standalone **Paper** plugin that connects a Minecraft server directly to a
TikTok LIVE creator and turns LIVE events into bounded, configurable actions.

The plugin speaks to TikTok itself: connection discovery and request signing
run inside the server process through a Java SDK. There is **no** Rust broker,
no native library, no sidecar service, no Node/Python runtime and no database.
You install one JAR and you are done.

> **Status: early development.** Chat connectivity is implemented and verified
> against a real TikTok LIVE. The rule engine, the function engine and the
> action system are being built on top of it. Live actions stay disabled until
> you explicitly enable them.

## Supported versions

| Component | Supported |
|---|---|
| Minecraft | Paper 1.21.11 (and later 1.21.x) |
| Java | 21 |
| Paper API | `1.21.11-R0.1-SNAPSHOT` |

Folia and legacy Spigot are **not** supported. The plugin uses the Paper API's
main-thread scheduler and the Bukkit entity API directly.

## Install

1. Download `TikTokMinecraft-<version>.jar`.
2. Put it in `plugins/`.
3. Start the server once to generate `plugins/TikTokMinecraft/`.
4. Set `tiktok.unique-id` in `plugins/TikTokMinecraft/config.yml` to the
   creator's TikTok username.
5. Run `/tiktok connect`.

The first start of the server needs internet access: Paper's library loader
downloads the GraalJS runtime (the JavaScript engine the TikTok signer uses)
from Maven Central and caches it in the server's `libraries/` directory. After
that the plugin needs no network access except to reach TikTok.

The first connection to a room also downloads TikTok's signing bundle once
into `plugins/TikTokMinecraft/cache/`. It is verified against a pinned SHA-256
before it is used.

## Configuration

Everything lives in `plugins/TikTokMinecraft/`:

```text
plugins/TikTokMinecraft/
├── config.yml        connection, chat, limits, security
├── events/           event rules (YAML)
├── functions/        reusable Minecraft functions (JSON or .mcfunction)
├── scripts/          optional rule scripts (.ttl)
└── cache/            the verified TikTok signing bundle
```

### Show TikTok chat in Minecraft

```yaml
chat:
  enabled: true
  console: true      # print every message to the server console
  minecraft: true    # mirror every message into Minecraft chat
  prefix: "[TikTok]"
  max-length: 200
  messages-per-second: 5
```

A message appears in Minecraft as:

```text
[TikTok] CJ.~: Oyeee
```

and in the console as:

```text
[TikTokMinecraft] Chat CJ.~: Oyeee
```

Viewer text is never interpreted as markup and never turned into a command.

## Commands

```text
/tiktok help
/tiktok status
/tiktok connect
/tiktok disconnect
/tiktok reconnect
/tiktok reload
/tiktok chat on|off
/tiktok test chat <message>
/tiktok debug on|off
```

`/tiktok test chat` injects a synthetic event into the normal pipeline. It
never contacts TikTok, so you can develop and test without going live.

## Security defaults

A fresh install is safe:

```yaml
security:
  execute-live-actions: false   # no live event may change the world
  allow-console-commands: false
  allow-raw-commands: false
```

`execute-live-actions` is the master switch. While it is `false`, events are
still received, displayed and matched, but nothing an event triggers can
change the world.

A TikTok viewer is not a Minecraft administrator. Viewer-controlled text is
never interpolated into command syntax, entity names or selector arguments.

Every inbound path is bounded: a maximum queue depth, a maximum number of
actions per event, a per-tick command budget, per-minute entity limits and a
global rate limit. Overflow is dropped and counted, never queued without
limit.

## Building from source

```bash
./gradlew clean build
./gradlew test
./gradlew :functions-core:test
./gradlew :paper-plugin:test
./gradlew :paper-plugin:assemble
```

The build produces `paper-plugin/build/libs/TikTokMinecraft-<version>.jar`.

### Dependencies

| Dependency | Why | How it is delivered |
|---|---|---|
| `io.github.nglmercer:tiktok-live-client` | TikTok LIVE protocol | shaded into the JAR |
| `io.github.nglmercer:tiktok-live-jvm-signer` | embedded Java signer | shaded into the JAR |
| `org.graalvm.polyglot`, `org.graalvm.js`, `org.graalvm.truffle` | GraalJS for the signer | Paper's library loader (`plugin.yml`) |

The TikTok SDK is consumed as an external dependency and is never vendored
into this repository. It publishes to Maven Local:

```bash
cd ../tiktok-signer/sdk/java
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew publishToMavenLocal
```

For a reproducible release, publish the SDK to a Maven repository the project
controls and point the build at it:

```bash
./gradlew build -PtiktokSdkRepositoryUrl=https://repo.example.org/releases
```

See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

## Acknowledgements

* The function engine (`functions-core/`) is reused from
  [nglmercer/commandclient](https://github.com/nglmercer/commandclient).
* The TikTok LIVE Java SDK comes from
  [nglmercer/tiktok-signer](https://github.com/nglmercer/tiktok-signer).

## Licence

The plugin is MIT licensed (see [LICENSE](LICENSE)). The distribution bundles
the TikTok SDK and its dependencies. **The TikTok webcast protobuf schemas are
not MIT licensed**: they are covered by a modified AGPL-3.0, and the full text
and the SDK's own analysis ship inside the JAR as `META-INF/LICENSE.protobuf`
and `META-INF/THIRD_PARTY_NOTICES.md`. Read
[THIRD-PARTY-NOTICES.md](paper-plugin/src/main/resources/THIRD-PARTY-NOTICES.md)
before you redistribute the plugin.
