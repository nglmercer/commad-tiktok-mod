# Events

Every TikTok LIVE event is normalized into one shape before a rule ever sees
it. Rules never touch a protobuf message or an SDK object.

## Triggers

| Trigger | Source | Meaning |
|---|---|---|
| `chat` | a chat message | The viewer sent text. |
| `chat.emote` | a reaction | The viewer sent only graphical emotes. |
| `gift` | a gift update | A gift was sent or repeated. |
| `gift.final` | a completed streak | A gift streak finished. |
| `like` | a like | The room received likes. |
| `member` | a membership | A viewer subscribed to the creator's membership. |
| `social.follow` | a confirmed follow | The SDK confirmed action code 1. |
| `social.share` | a confirmed share | The SDK confirmed action code 3. |
| `social.other` | an unknown social code | A social action the SDK did not confirm. |
| `social.unknown` | no usable code | The message carried no usable action code. |
| `room.user` | the room user list | The room's viewer count changed. |
| `unknown` | an unrecognized message | The SDK could not decode the message. |

`social.other` and `social.unknown` are never folded into a follow or a
share. A rule that listens for `social.follow` only fires on a confirmed
follow.

`chat.emote` is a separate trigger because a reaction has no message body.
Treating it as a chat message would publish an empty line for every reaction
in the room.

## Fields

```text
event.type            the trigger name
event.id              the message identifier
event.roomId          the room the event came from
event.timestamp       when the event was received
event.isHistory       whether the event is a history replay

user.id               the viewer's identifier
user.uniqueId         the viewer's TikTok username
user.nickname         the viewer's display name

chat.text             the message body
chat.emotes           the graphical emotes in the message

gift.id               the exact gift identifier
gift.name             the gift name (localized, and may change)
gift.diamonds         the value of the gift
gift.repeatCount      how often the gift was repeated
gift.comboCount       the combo count
gift.groupId          the streak group
gift.repeatEnd        whether the streak ended

like.count            the likes this event carries
like.total            the room's cumulative like count

social.kind           the confirmed classification
social.action         the raw action code
social.followCount    the room's cumulative follow count
social.shareCount     the room's cumulative share count

member.count          the membership count
room.viewers          the number of viewers in the room
```

A rule may use a friendly alias instead of the full path: `text`,
`gift-name`, `gift-id`, `nickname`, `unique-id`, `user-id`, `diamonds`,
`like-count`, `kind`, `viewers` and a few more.

## Rules

A rule lives in `plugins/TikTokMinecraft/events/*.yml`:

```yaml
rules:
  - id: chat-hello
    enabled: true
    trigger: chat

    match:
      text:
        equals-ignore-case: "!hello"

    cooldown:
      duration: 10s
      scope: user

    actions:
      - type: broadcast
        message: "Hello from TikTok!"
```

| Key | Meaning |
|---|---|
| `id` | The rule's name. It must be unique. |
| `enabled` | Whether the rule runs. `/tiktok rules enable <id>` overrides it at runtime. |
| `trigger` | The event the rule listens for. |
| `match` | The fields the event must have. |
| `aggregate` | A threshold over a counter. |
| `cooldown` | How long the rule stays quiet after it fired. |
| `conditions` | A boolean tree over field comparisons. |
| `actions` | What the rule does. |
| `priority` | Higher priority rules are evaluated first. |
| `stop-processing` | Whether later rules are skipped once this one matched. |

### Matching

A field is matched with a comparison:

```yaml
match:
  text:
    equals: "!hello"
  gift-name:
    contains: "Rose"
  diamonds:
    greater-or-equal: 100
```

A plain value is shorthand for `equals`.

| Comparison | Meaning |
|---|---|
| `equals` | Equal. Numbers are compared numerically. |
| `equals-ignore-case` | Equal, ignoring case. |
| `not-equal` | Not equal. |
| `contains` | Contains the text. |
| `starts-with` | Starts with the text. |
| `ends-with` | Ends with the text. |
| `greater-than` | Numerically greater. |
| `greater-or-equal` | Numerically greater or equal. |
| `less-than` | Numerically smaller. |
| `less-or-equal` | Numerically smaller or equal. |
| `in` | The value is one of a list. |

A numeric comparison never matches a value that is not a number.

### Conditions

```yaml
conditions:
  - all:
      - field: chat.text
        operator: contains
        value: "hola"
      - field: gift.diamonds
        operator: greater-or-equal
        value: 10
  - any:
      - field: gift.id
        operator: equals
        value: "5655"
```

`all`, `any` and `not` may be combined freely. A condition never evaluates
an expression.

### Thresholds

```yaml
aggregate:
  metric: count
  scope: global
  threshold: 50
  carry-remainder: true
```

| Key | Meaning |
|---|---|
| `metric` | `count` uses the likes the event carries, `total` uses the room's cumulative count. |
| `scope` | `event`, `user` or `global`. |
| `threshold` | When the rule fires. |
| `carry-remainder` | Keep the remainder for the next event. |

The counter always adds the event's delta. The cumulative room total is
never used as the counter, so the same likes cannot be counted twice.

A threshold may only be used with the `like` trigger.

### Cooldowns

```yaml
cooldown:
  duration: 10s
  scope: user
```

`duration` accepts `500ms`, `10s`, `2m`, `1h` or a plain number of
milliseconds.

| Scope | Meaning |
|---|---|
| `rule` | One timer for the rule, shared by every viewer. |
| `user` | One timer per viewer, for this rule. |
| `user-and-rule` | The same as `user`. |
| `global` | One timer shared by every rule and every viewer. |

Every scope but `global` belongs to its rule, so one rule can never silence
another.

## Actions

| Type | What it does |
|---|---|
| `broadcast` | Shows a message to every player. |
| `title` | Shows a title and a subtitle. Both support `{field}` placeholders from the event. |
| `sound` | Plays a sound. |
| `particle` | Spawns particles. |
| `spawn-entity` | Spawns an entity next to the target. |
| `block-cage` | Builds a temporary cage from a configurable solid block, optionally with a mob inside. |
| `tnt-cage` | Backward-compatible alias for `block-cage` with TNT as default. |
| `prime-tnt` | Spawns a bounded number of ignited TNT entities. |
| `void-shaft` | Opens a temporary shaft beneath the player. |
| `chunk-replace` | Replaces blocks in the player's chunk (or 3x3 chunks) with one material. |
| `give-item` | Gives an item to the target. |
| `effect` | Applies a potion effect. |
| `heal` | Heals the target. |
| `teleport` | Teleports the target to an explicit place. |
| `function` | Runs a reusable function. |
| `command` | Runs a server command. Disabled by default. |

```yaml
actions:
  - type: title
    target: all-online
    title: "New follower!"
    subtitle: "Thank you for following!"

  - type: function
    name: gifts:spawn_zombies
    args:
      count: 1
```

### Event placeholders

`title` and `subtitle` may contain `{field}` placeholders, using any
documented event field or alias: `{gift.name}`, `{gift-name}`,
`{user.nickname}`, `{nickname}`, `{user.uniqueId}` and so on. `{viewer}`
is the friendly display name: the nickname, `@username` when no nickname
is set, or `unknown`. A field the event does not carry renders as an
empty string; an unknown name is left untouched so typos stay visible.

```yaml
actions:
  - type: title
    target: all-online
    title: "{gift.name}!"
    subtitle: "Thanks {viewer}!"
```

A rule without a `match` section fires for every event of its trigger,
so one `gift.final` rule with this title covers all gifts. A function
cannot do this: function arguments are fixed values, never event data.

### Configurable block cages

A rule can trap the selected player inside a small cage made from any
placeable solid vanilla Minecraft **block**, for example TNT, obsidian,
glass or iron bars. Inventory-only items such as swords are not blocks.

```yaml
actions:
  - type: block-cage
    material: minecraft:obsidian
    radius: 2
    height: 3
    restore-seconds: 30
    target: nearest-online
```

The Rose (gift ID `5655`) preset combines the cage and the creeper
in **one action**, ensuring the same player is selected for both:

```yaml
actions:
  - type: block-cage
    material: minecraft:tnt
    radius: 1
    height: 3
    occupant: minecraft:creeper
    occupant-charged: true
    ignite: false
    restore-seconds: 20
    target: nearest-online
```

To ignite TNT, set `ignite: true` and optionally specify
`ignite-delay-ticks` and `fuse-ticks`. Ignition is rejected for any
non-TNT material. `tnt-cage` remains a valid legacy alias for
`block-cage` and defaults to TNT when `material` is omitted.

`radius` is 1–3, `height` is 3–6, `restore-seconds` is 0–120
(`0` means a permanent cage), and `occupant` is optional. Spawning
occupants and ignited TNT counts toward the entity rate limits.
Temporary cages avoid container blocks and overlapping active cages.
Restoration is best-effort; explosions, plugin crashes and unloaded
chunks can leave terrain modifications behind.

### Chunk replace

A rule can convert or clean the chunk the selected player stands in.
`material` is placed on every matching block: use `minecraft:air` to
clean a chunk, or any other block to convert it.

```yaml
actions:
  - type: chunk-replace
    material: minecraft:air
    target: nearest-online
```

Set `from` to convert one block type block by block while everything
else is left alone:

```yaml
actions:
  - type: chunk-replace
    material: minecraft:stone
    from: minecraft:dirt
    target: nearest-online
```

Optional parameters: `radius-chunks` (`0` for the player's chunk only,
`1` for 3x3 chunks, default `0`), `min-y` / `max-y` to limit the
vertical range (default is the full world height), `blocks-per-tick`
(`1`–`4096`, default `512`) to spread the work, and `restore-seconds`
(`0`–`180`, default `0` for a permanent change).

`max-blocks` is optional and unlimited by default (`0`). Set it above
zero to make the action skip without changing anything when more blocks
would change than the limit allows.

Container blocks are never modified, unloaded chunks are skipped, and
only loaded chunks are touched. Restoration is best-effort, like the
other terrain pranks.

`target` accepts `all-online`, `random-online`, `nearest-online`,
`specific-player:<name>` and `specific-world:<name>`. When it is omitted,
the configured `actions.target-policy` is used.

`spawn-entity` and `give-item` only work with an entity or material that is
in `actions.allowed-entities` respectively `actions.allowed-entities`.

## Deduplication

An event is identified by its room, its type and its message identifier. A
gift streak completion is identified by its gift and group identifiers as
well, because a streak emits several messages.

A message that arrives twice — after a reconnect, for example — is only
processed once inside the deduplication window.

## History

The room's history replay is marked with `event.isHistory`. Historical
events never trigger an action, and with `tiktok.ignore-history` they are
not even shown.
