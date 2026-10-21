# Security

A TikTok LIVE is an untrusted, public input source. A room can be flooded
with messages in a second, and any viewer can type anything into the chat.
The plugin is built so that a viewer can never become a Minecraft
administrator.

## The master switch

```yaml
security:
  execute-live-actions: false
```

While this is `false`, events are still received, shown and matched, but
nothing an event triggers may change the world. It is the default on a fresh
install. Turn it on only after the rules and the functions have been tested
with `/tiktok test`.

## Untrusted text

These are all controlled by a viewer and are never treated as anything but
text:

```text
nickname
uniqueId
chat.text
gift.name
social metadata
```

* Every message is built with the component API. A message containing
  `<red>` is shown literally, because the component API does not parse
  markup.
* MiniMessage is never used, so a message can never inject a tag.
* A message is never turned into a command. A chat message that starts with
  `/` is still a chat message.
* Text is never interpolated into command syntax. A function's parameters
  are typed and validated, and a value that would break a command is
  rejected.
* Titles and JSON strings are serialized, never concatenated.

## The command allowlist

A function may only run the command roots in `functions.allowed-commands`:

```yaml
functions:
  allowed-commands:
    - summon
    - title
    - playsound
    - particle
    - effect
```

The check runs twice: when the function is compiled, and again immediately
before a command is dispatched. A command that is not on the list never
runs.

The `command` action needs both `security.allow-raw-commands` and
`security.allow-console-commands`. Both are off by default.

## Entity spawning

`actions.allowed-entities` is empty by default, which denies all spawning.
Only the listed types may be spawned, and both a per-event and a per-minute
cap apply:

```yaml
actions:
  max-entities-per-event: 5
  max-entities-per-minute: 60
```

An entity is never spawned into an unloaded chunk.

## Bounds

Every path into the world is bounded. Nothing waits without a limit.

| Limit | Setting |
|---|---|
| Events waiting to be processed | `events.max-pending-events` |
| Chat messages waiting to be shown | `chat.max-queue` |
| Chat ingest rate | `chat.messages-per-second` |
| Actions per event | `actions.max-actions-per-event` |
| Entities per event | `actions.max-entities-per-event` |
| Entities per minute | `actions.max-entities-per-minute` |
| Functions running at once | `functions.max-concurrent` |
| Functions waiting | `functions.max-queued` |
| Commands per tick | `functions.max-commands-per-tick` |
| Duration of a function run | `functions.max-execution-seconds` |
| Repetitions of a step | `functions.max-repeat` |
| Nesting depth | `functions.max-depth` |
| Deduplication cache | derived from `events.deduplication-window-seconds` |
| Cooldown keys | bounded, oldest entries are dropped |

When a limit is reached the work is dropped and counted. `/tiktok status`
reports the counters.

## Deduplication

A message that arrives twice is only processed once. The identifier is the
room, the event type and the message id, plus the gift and group
identifiers for a gift streak.

A replayed message is therefore never a second world action.

## Failure policy

| Situation | What happens |
|---|---|
| TikTok is unreachable | The server keeps running. The plugin retries with a backoff. |
| The creator is offline | The plugin checks again every minute and connects on its own. |
| The signer cannot start | The LIVE connection fails. The rest of the server is unaffected. |
| A rule file is broken | The previous rules stay active and the problem is reported. |
| An action fails | The failure is logged and counted. The other actions still run. |
| The queue is full | The event is dropped and counted. |
| The target is offline | The action is skipped. |
| The plugin is disabled | Every job is cancelled and the queues are cleared. |

There is no unbounded retry loop anywhere.

## The script format

The DSL cannot evaluate an expression, call the host language, read a file,
reach the network, import, reflect, loop without a bound, recurse, run a
shell command or raise its own privileges. See [DSL.md](DSL.md).

## The network

The plugin talks to TikTok and to nothing else. It opens no listening port
and accepts no connection. The signing bundle it downloads is verified
against a pinned SHA-256 before it is used.

## Reporting a problem

Please include the server log from the start of the plugin's startup, the
`config.yml` and the rule files. Never include cookies or credentials: the
plugin does not log them, and if you added a session with cookies yourself,
remove them from the report.
