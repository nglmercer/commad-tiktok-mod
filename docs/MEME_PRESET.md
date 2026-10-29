# Complete TikTok LIVE → Minecraft meme preset

This project ships a 12-interaction Minecraft meme panel and a configurable
`block-cage` action. The same code handles TNT, obsidian, glass and other
placeable solid Minecraft blocks.
The preset is in `paper-plugin/src/main/resources/defaults/events/interactions.yml`.
All rules are enabled in the new default. **Use this on a disposable Minecraft world**.

## Install / upgrade

On a fresh server, `interactions.yml` is copied into `plugins/TikTokMinecraft/events/` on startup.

On an existing server, the plugin **never silently overwrites** your rules.
After updating the JAR, run from the console or as a server operator:

```text
/tiktok preset install
/tiktok rules list
/tiktok status
```

`/tiktok preset install` backs up the existing `interactions.yml` to
`interactions.yml.bak-<timestamp>`, writes the shipped preset, enables live actions,
validates/reloads rules, and restores the old rules if validation fails.

Your existing `config.yml` does not automatically acquire new defaults. The command
also enables `security.execute-live-actions` and `events.enabled`, allows
the configured entity types, and raises older per-event/per-minute entity budgets
to at least 64 / 240. These changes are an **explicit operator opt-in**.
Raw console command permissions are **not** required by the two new native Paper actions.

## The 13 panel interactions

| TikTok gift/event | Rule | Minecraft outcome |
|---|---|---|
| 150 likes | `meme-likes-zombie` | Spawn 1 zombie per threshold |
| Any gift | `meme-gift-thanks-title` | Title naming the gift and the viewer, for every gift |
| Rose (5655) | `meme-rose-charged-creeper-cage` | Small TNT cage with a charged creeper inside |
| Confetti (5585) | `meme-confetti-tnt` | Request 30,000 TNT, bounded by server entity budgets |
| Rosa (8913) | `meme-rosa-charged-creeper` | Spawn a charged creeper |
| Finger Heart (5487) | `meme-finger-heart-webs` | Place 6 temporary cobwebs |
| Share event | `meme-share-apples` | Give 3 enchanted golden apples |
| Cap (6104) | `meme-cap-wardens` | Spawn 10 wardens |
| Corgi (6267) | `meme-corgi-inventory` | Temporarily hide and restore inventory |
| Capybara (14488) | `meme-capybara-evokers` | Spawn 15 evokers |
| Doughnut (5879) | `meme-doughnut-void` | Open a temporary shaft through blocks and bedrock to world void |
| Fireworks (6090) | `meme-fireworks-skeletons` | Spawn 10 wither skeletons |
| Money Gun (7168) | `meme-money-gun-withers` | Request 500 Withers, capped to 3 per activation |

### Generic block cages: TNT, obsidian, glass, etc.

Use `block-cage` in any gift rule and set `material` to a Minecraft **block**.
It does not require console `/fill` or extra permissions.

- `material`: e.g. `minecraft:tnt`, `minecraft:obsidian`, `minecraft:glass`
- `radius`: 1–3 blocks around the player's center; `radius: 1` is a 3×3 cage
- `height`: 3–6 blocks above the player's feet (floor/roof included)
- `restore-seconds`: 1–120 for temporary cages, or 0 to keep the cage
- `ignite`: optional, **TNT only**; primes a block after `ignite-delay-ticks`
- `occupant`: optional entity to spawn inside the cage
- `occupant-charged`: optional true for `minecraft:creeper`, false otherwise
- `target`: optional player selector, such as `nearest-online`

`tnt-cage` remains a compatible alias of `block-cage`, with default
`material: minecraft:tnt`. Both aliases share temporary block tracking, so
a second cage cannot silently overwrite an active first cage.

**Rose (5655)** now creates a small TNT cage with a charged creeper,
as one atomic action on the same selected player:

```yaml
- id: meme-rose-charged-creeper-cage
  enabled: true
  trigger: gift.final
  match:
    gift-id: '5655'
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

**Obsidian cage example** (copy into a separate rule file or edit
`interactions.yml`; a complete optional example is in
`examples/obsidian-cage.yml`):

```yaml
- id: demo-obsidian-cage
  enabled: true
  trigger: gift.final
  match:
    gift-id: '5655'
  actions:
    - type: block-cage
      material: minecraft:obsidian
      radius: 2
      height: 3
      restore-seconds: 30
      target: nearest-online
```

The high-value bonus rule `meme-high-value-tnt-cage` listens for gifts costing
**100+ diamonds per unit** and uses the same general-purpose `block-cage` with TNT.
By default, it creates a 5×5×5 hollow TNT shell centered on the target
and ignites one block after 40 ticks. The remaining intact TNT blocks are
restored after 25 seconds. Exploded blocks and collateral terrain are not
automatically rolled back.

Example:

```yaml
- id: donation-tnt-cage
  enabled: true
  trigger: gift.final
  match:
    diamonds:
      greater-or-equal: 100
  actions:
    - type: block-cage
      material: minecraft:tnt
      radius: 2
      height: 3
      ignite: true
      ignite-delay-ticks: 40
      fuse-ticks: 80
      restore-seconds: 25
      target: nearest-online
```

### Native `void-shaft` action

`void-shaft` removes blocks in a 3×3 shaft directly beneath the chosen player
from the player's feet down to `World#getMinHeight()`, **including bedrock**.
It clears from bottom to top in bounded per-tick batches, then centers the
player over the completed shaft. The hole is restored after a delay, as long
as its chunks remain loaded and nobody has replaced its blocks.
The action will not overwrite container blocks or unloaded chunks; it skips
a shaft requiring more than `max-blocks`.

Example:

```yaml
- id: doughnut-to-void
  enabled: true
  trigger: gift.final
  match:
    gift-id: '5879'
  actions:
    - type: void-shaft
      radius: 1
      max-blocks: 4096
      blocks-per-tick: 96
      restore-seconds: 30
      target: nearest-online
```

**Not a complete anti-grief or region protection system:** native world changes
do not consult third-party claim plugins. Do not use these actions in valuable
survival worlds, where even a temporary void pit or TNT explosion can cause
irreversible deaths, item loss, and terrain damage.

## Synthetic testing

Use a backup or disposable test world with at least one player connected.

```text
/tiktok test like 150
/tiktok test gift Rose 1
/tiktok test gift Confetti 1
/tiktok test gift Rosa 1
/tiktok test gift Capybara 1
/tiktok test gift Doughnut 1
/tiktok test gift MoneyGun 1
/tiktok status
```

`/tiktok test gift` uses local synthetic gift metadata. It never sends a gift
or uses TikTok coins. The real donation cage depends on the SDK's gift
catalog for the correct `gift.diamonds` value.

## Notes

- Gift `xN` is the number of Minecraft effects/items **per gift**.
- Nonstreak gifts and finished streak gifts are consumed as `gift.final`.
- Repeated gifts multiply effect counts where the rule specifies `multiply-by-repeat`.
- `prime-tnt` requests are intentionally capped at 128 and also obey
  `actions.max-entities-per-event` / per-minute budgets.
- Money Gun requests 500 Withers but deliberately spawns at most 3 per event.
- Event commands are only available to operators (admin permission);
  ordinary TikTok viewers cannot run server commands.
- `void-shaft` and `block-cage` restore their own unchanged blocks on
  scheduled cleanup and plugin disable, but cannot guarantee restoration
  across server crashes, unloaded chunks, or explosion-caused terrain changes.

## Editing interactions without restarting Minecraft

Hot reload is enabled by default. Edit
`plugins/TikTokMinecraft/events/interactions.yml` directly in a text editor,
save, and watch the Paper console for `Hot reload applied`.
There is no need to restart Minecraft or disconnect from the active LIVE.

Edits to `config.yml`, function JSON files, `.mcfunction` files and
`.ttl` DSL scripts are also watched. Updates are debounced and validated
before being applied. A malformed rule or function is rejected without
replacing the last working one. Check `/tiktok status` for the last
hot-reload outcome, or run `/tiktok reload` manually.

Use `hot-reload.enabled: false` in `config.yml` to disable automatic reload.
After disabling it, you must run `/tiktok reload` to re-enable it.
