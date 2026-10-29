---
name: tiktok-minecraft-config
description: Configure the TikTokMinecraft Paper plugin — rules, functions, actions, and live gifts.
---

# TikTokMinecraft Configuration

Use this when the user wants to change what TikTok LIVE events do on their
Minecraft server: gift/chat/like/follow reactions, announcements, pranks, or
functions. All paths below are under the server's `plugins/TikTokMinecraft/`
data folder, seeded from `paper-plugin/src/main/resources/defaults/` in this
repo. Edits are hot-reloaded and validated; a failed reload keeps the previous
working state. Confirm with `/tiktok reload` and `/tiktok rules list`.

## Rules (`events/*.yml`)

```yaml
rules:
  - id: my-rule                 # required, [A-Za-z0-9_.-]{1,64}
    enabled: true               # default true
    trigger: gift.final         # required
    match:                      # omit to fire on every event of the trigger
      gift-id: "5655"
    cooldown: { duration: 10s, scope: rule }  # rule|user|global
    actions:
      - type: title
        target: all-online
        title: "{gift.name}!"
        subtitle: "Thanks {viewer}!"
```

Triggers: `chat`, `chat.emote`, `gift`, `gift.final` (once per finished
streak — prefer it for gifts), `like`, `member`, `social.follow`,
`social.share`, `social.other`, `social.unknown`, `room.user`, `unknown`.

Match fields: `event.type/id/roomId/timestamp/isHistory`,
`user.id`, `user.uniqueId`, `user.nickname`, `chat.text`,
`gift.id/name/diamonds/repeatCount/comboCount/groupId/repeatEnd`,
`like.count/total`, `social.kind/action/followCount/shareCount`,
`room.viewers`, `member.count`. Friendly aliases exist (`text`,
`gift-name`, `gift-id`, `diamonds`, `nickname`, `unique-id`, ...).
Comparisons include `equals`, `equals-ignore-case`, `contains`,
`greater-or-equal`. Test with `/tiktok test gift Rose`,
`/tiktok test chat hello`, `/tiktok test like 5`, and friends.

Targets: `all-online`, `random-online`, `nearest-online`,
`specific-player:<name>`, `specific-world:<name>` (default policy:
`nearest-online`).

## Actions

`broadcast(message)`, `sound`, `particle`, `spawn-entity`,
`give-item`, `effect`, `heal`, `teleport`, `prime-tnt`,
`lightning` / `lightning-effect`, `place-cobweb` (count 1..32),
`remove-item`, `temporary-clear-inventory`.

- `title`: `title` (required), `subtitle`, `target`, `fade-in/stay/fade-out`.
  Both texts render `{field}` placeholders from the event (`{gift.name}`,
  `{nickname}`, `{user.uniqueId}`, any match field or alias) plus `{viewer}`
  (nickname, else `@username`, else `unknown`). Unknown names stay literal;
  missing fields render empty. One `gift.final` rule without `match` announces
  every gift.
- `block-cage` / `tnt-cage`: `material` (any solid block; TNT default for the
  alias), `radius` 1..3, `height` 3..6, `restore-seconds` 0..120 (0 permanent),
  optional `ignite` (TNT only), `occupant` mob.
- `void-shaft`: column under the player to air, `radius` 0..1,
  `max-blocks` 1..4096, `blocks-per-tick` 1..128, `restore-seconds` 0..180.
- `chunk-replace`: converts or cleans the player's chunk (`material:
  minecraft:air` cleans). `material` required; optional `from` converts one
  block type only, `radius-chunks` 0 (own chunk) or 1 (3x3), `min-y`/`max-y`,
  `blocks-per-tick` 1..4096 (default 512), `restore-seconds` 0..180 (default 0,
  permanent). `max-blocks` defaults to 0 (unlimited); set it above zero to skip
  untouched when more blocks would change. Containers and unloaded chunks are
  never touched.
- `function`: runs `name` (id like `gifts:spawn_zombies`) with literal `args`.
  Function arguments are fixed values, never event data — per-gift names need
  the `title` placeholders above, not a function.
- `command`: raw console command (no leading `/`, max 256 chars, no `;` or
  line breaks). Disabled by default; needs `security.allow-raw-commands: true`,
  `security.allow-console-commands: true`, and the root in
  `functions.allowed-commands`. Never put viewer-controlled text in one.

World actions only run with `security.execute-live-actions: true`.

## Functions (`functions/**/*.json`)

`functions/gifts/spawn_zombies.json` becomes `gifts:spawn_zombies`. Shape:
`schemaVersion`, `name`, `description`, `parameters` (types `string`,
`integer`, `number`, `boolean`, `entity`, `player`, `selector`, `position`,
`enum`, `resource_location`, with `min`/`max`/`default`/`values`), `steps`
(`command`, `summon`, `title`, `sound`, `particle`, `repeat`, `delay`,
`function`, `if`). Interpolate with `${name}`; unknown or unsafe values are
errors. Every expanded command must be in `functions.allowed-commands`.
Preview safely with `/tiktok functions preview <id>`, run with
`/tiktok functions run <id>`. A `.mcfunction` variant exists (see
`docs/FUNCTIONS.md`).

## Reference

- Full field/action docs: `docs/EVENTS.md`, `docs/FUNCTIONS.md`,
  `docs/CONFIGURATION.md`, preset catalog `docs/MEME_PRESET.md`.
- Rule/function schemas: `schemas/event-rule.schema.json`,
  `schemas/function.schema.json`; check examples with
  `python3 scripts/check-schemas.py`.
- Copyable examples: `examples/` (`chat.yml`, `gifts.yml`, `likes.yml`,
  `obsidian-cage.yml`, `functions/`).
