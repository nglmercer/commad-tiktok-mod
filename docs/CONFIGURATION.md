# Configuration

`plugins/TikTokMinecraft/config.yml` is the single runtime configuration.
Every value is validated when it is loaded; a reload that fails validation
keeps the previous, working configuration.

## tiktok

```yaml
tiktok:
  unique-id: "@elpadredomingooficial"
  auto-connect: true
  reconnect: true
  fetch-gifts: true
  ignore-history: true
```

| Key | Meaning |
|---|---|
| `unique-id` | The TikTok username of the creator. The leading `@` is optional. |
| `auto-connect` | Connect as soon as the plugin starts. |
| `reconnect` | Retry after a failure, and check again every minute while the creator is offline. |
| `fetch-gifts` | Download the gift catalog so a gift can be matched by its exact id. |
| `ignore-history` | Ignore the room's history replay. |

## chat

```yaml
chat:
  enabled: true
  console: true
  minecraft: true
  prefix: "[TikTok]"
  max-length: 200
  messages-per-second: 5
  max-queue: 128
```

| Key | Meaning |
|---|---|
| `enabled` | Mirror chat at all. |
| `console` | Print every message to the server console. |
| `minecraft` | Show every message in Minecraft chat. |
| `prefix` | The prefix shown in Minecraft chat. |
| `max-length` | The maximum number of characters of a message. |
| `messages-per-second` | The ingest rate limit. Messages above it are suppressed and counted. |
| `max-queue` | The maximum number of messages waiting to be shown. |

A message is never interpreted as markup and never turned into a command.
Unicode and emoji are preserved.

## events

```yaml
events:
  enabled: true
  default-enabled: false
  deduplication-window-seconds: 300
  max-pending-events: 512
  ignore-history: true
```

| Key | Meaning |
|---|---|
| `enabled` | Process LIVE events at all. |
| `default-enabled` | Whether a rule that does not say otherwise is enabled. |
| `deduplication-window-seconds` | How long an event identifier is remembered. |
| `max-pending-events` | The maximum number of events waiting to be processed. |
| `ignore-history` | Historical events never trigger an action. |

## functions

```yaml
functions:
  enabled: true
  max-concurrent: 2
  max-queued: 64
  max-commands-per-tick: 4
  max-execution-seconds: 60
  max-repeat: 20
  max-depth: 8
  allowed-commands:
    - summon
    - title
    - playsound
    - particle
    - effect
```

| Key | Meaning |
|---|---|
| `enabled` | Allow functions to run. |
| `max-concurrent` | How many functions run at the same time. |
| `max-queued` | How many functions may wait. |
| `max-commands-per-tick` | How many Minecraft commands are dispatched per server tick, across all functions. |
| `max-execution-seconds` | The wall-clock budget for one function run. |
| `max-repeat` | The maximum number of repetitions of a single step. |
| `max-depth` | The maximum nesting depth of nested functions. |
| `allowed-commands` | The command roots a function may execute. Everything else is rejected. |

## actions

```yaml
actions:
  target-policy: nearest-online
  no-player-policy: skip
  max-actions-per-event: 8
  max-entities-per-event: 5
  max-entities-per-minute: 60
  allowed-entities:
    - zombie
    - chicken
```

| Key | Meaning |
|---|---|
| `target-policy` | The default target: `all-online`, `random-online`, `nearest-online`, `specific-player` or `specific-world`. |
| `no-player-policy` | What to do when nobody is eligible: `skip` or `console`. |
| `max-actions-per-event` | The maximum number of actions one event may run. |
| `max-entities-per-event` | The maximum number of entities one event may spawn. |
| `max-entities-per-minute` | The maximum number of entities per minute, across all events. |
| `allowed-entities` | The entity types an action may spawn. Empty denies all spawning. |

## security

```yaml
security:
  execute-live-actions: false
  allow-console-commands: false
  allow-raw-commands: false
```

| Key | Meaning |
|---|---|
| `execute-live-actions` | The master switch. While it is `false`, events are received, shown and matched, but nothing an event triggers may change the world. |
| `allow-console-commands` | Allow a function to run a server command. |
| `allow-raw-commands` | Allow the `command` action. |

Keep `execute-live-actions` off until the rules and functions have been
tested with `/tiktok test`.

## debug

```yaml
debug:
  log-events: false
  log-connection: true
  log-rule-matches: false
```

`/tiktok debug on` turns every diagnostic category on at runtime, and
`/tiktok debug off` turns them off again.

## Reloading

`/tiktok reload` re-reads the configuration, the functions and the rules. It
validates everything first and only swaps the new set in when all of it is
valid. A problem is reported with the file, the rule and the configuration
path:

```text
Reload failed; the previous configuration is still active:
  - rule file chat.yml: chat.yml rules[0] (rule 'broken'): unknown trigger 'not-a-trigger'
```

The TikTok connection is only restarted when a connection relevant setting
actually changed. A rule or function change never disturbs a live session.
