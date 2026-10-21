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

## Changing the creator while the server runs

```text
/tiktok creator
/tiktok creator <username>
```

The first form shows the current creator. The second switches to another
one, writes it to `config.yml` and reconnects at once. The plugin does not
have to be stopped and started again, and the file does not have to be
edited by hand.

Changing `unique-id` in the file also reconnects automatically when hot
reload is enabled. Use `/tiktok reload` to apply changes manually if hot
reload is disabled. Only connection-relevant settings cause a reconnect.

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

## Automatic hot reload

File edits are detected automatically, including atomic editor saves that
replace the original file. There is no need to restart Paper or disconnect
from a LIVE session when adjusting gifts and rules.

```yaml
hot-reload:
  enabled: true
  debounce-ms: 750
```

The background watcher polls every 500 ms and waits for the files to stop
changing for `debounce-ms` (250–5000 ms). It watches:

- `config.yml`
- `events/*.yml`
- `functions/**/*.json` and `functions/**/*.mcfunction`
- `scripts/**/*.ttl`

New files, deletions and modifications all trigger validation. Backup files,
editor swap files and generated `cache/` and `exports/` are ignored.

**Every reload is transactional:** a malformed config, missing function
reference, or invalid rule keeps the last working config, function registry
and rule snapshot in use. It reports the error in the Paper console.
Subsequent file saves retry automatically.

Rules-only edits preserve deduplication state and do not restart TikTok.
Changing the creator or gift-catalog setting causes a reconnect.
Editing `hot-reload.enabled: false` disables further automatic reloads
after that change is applied; run `/tiktok reload` manually to re-enable it.

`/tiktok status` shows whether hot reload is enabled and the last outcome.
The watcher is stopped when the plugin is disabled; it has no Bukkit access
from its background scanning thread.

## Manual reload

`/tiktok reload` re-reads the configuration, the functions and the rules.
It uses the same strict validation as automatic reload. A problem is
reported with the file, the rule and the configuration path:

```text
Reload failed; the previous configuration is still active:
  - rule file chat.yml: chat.yml rules[0] (rule 'broken'): unknown trigger 'not-a-trigger'
```

The TikTok connection is only restarted when a connection relevant setting
actually changed. A rule or function change never disturbs a live session.

### Collaborating with multiple streamers

The existing `tiktok.unique-id` remains the primary creator. Add collaborators
without restarting the server:

```text
/tiktok account add @friend FriendMinecraftName
/tiktok account target @your_creator YourMinecraftName
/tiktok account list
/tiktok connect all
/tiktok disconnect friend
/tiktok reconnect friend
/tiktok account target friend NewPlayerName
/tiktok account target friend clear
/tiktok account remove friend
```

Account edits are saved to `config.yml`. Additional accounts live in
`tiktok.accounts`, a list of maps with `unique-id` and optional `target-player`.
The primary creator uses `tiktok.target-player`. Names and player UUIDs are
accepted. With auto-connect enabled, new accounts connect immediately; otherwise
use `/tiktok connect <username>`. Connection commands without an account still
operate on the primary creator. The primary creator can be changed with
`/tiktok creator <username>`; it cannot be removed.

The account player is the default target for actions and functions triggered by
that account. The account assignment also replaces `nearest-online` targets used by the
shipped interactions. Other explicit action targets (for example `all-online` or
`specific-player:Name`) take precedence. If the assigned player is offline,
player actions have no target rather than selecting another streamer. Clearing
an assignment restores the shared target policy. Target edits do not reconnect
the account. Connection options and event/action limits are shared across all
accounts. Chat continues to mirror through the shared chat bridge.
