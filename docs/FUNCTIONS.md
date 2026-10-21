# Functions

A function is a reusable, parameterised sequence of Minecraft commands. It
lives in `plugins/TikTokMinecraft/functions/`, and the folder structure
becomes the function id:

```text
functions/
└── gifts/
    ├── spawn_zombies.json
    └── spawn_chicken.json
```

resolve to

```text
gifts:spawn_zombies
gifts:spawn_chicken
```

A file directly in `functions/` resolves to `minecraft:<name>`.

The engine is reused from
[nglmercer/commandclient](https://github.com/nglmercer/commandclient); the
JSON format and the `schemaVersion` are unchanged.

## JSON format

```json
{
  "schemaVersion": 1,
  "name": "gifts:spawn_zombies",
  "description": "Spawn a limited number of zombies next to the target.",
  "parameters": {
    "count": {
      "type": "integer",
      "min": 1,
      "max": 5,
      "default": 1
    }
  },
  "steps": [
    {
      "type": "repeat",
      "times": "${count}",
      "steps": [
        { "type": "summon", "entity": "minecraft:zombie", "position": "~ ~1 ~" }
      ]
    },
    {
      "type": "title",
      "target": "@a",
      "title": "Zombie Attack!",
      "subtitle": "A TikTok viewer summoned zombies!"
    }
  ]
}
```

## Step types

| Step | Fields |
|---|---|
| `command` | `value` — the command, without the leading `/`. |
| `summon` | `entity`, `position`, `nbt`. |
| `title` | `target`, `title`, `subtitle`, `fadeInTicks`, `stayTicks`, `fadeOutTicks`. |
| `sound` | `sound`, `source`, `target`, `volume`, `pitch`, `position`. |
| `particle` | `particle`, `position`, `count`. |
| `repeat` | `times`, `steps`. |
| `delay` | `ticks`. |
| `function` | `name`, `args`. |
| `if` | `condition`, `then`, `else`. |

## Parameters

```json
"parameters": {
  "count": { "type": "integer", "min": 1, "max": 5, "default": 1 },
  "mood": { "type": "string", "values": ["happy", "angry"] },
  "player": { "type": "player" }
}
```

Types: `string`, `integer`, `number`, `boolean`, `entity`, `player`,
`selector`, `position`, `enum` and `resource_location`.

A value is interpolated with `${name}`. An unknown or missing parameter is an
error, never an empty string. A value that would break a command — a line
break, a `;`, a quote — is rejected.

## Relative coordinates

A step that uses `~` or `^` is resolved against the target player before it
runs. Without that, a `~ ~1 ~` step would act at the world spawn, because
the console's position is the spawn point.

```json
{ "type": "summon", "entity": "minecraft:zombie", "position": "~ ~1 ~" }
```

runs as

```text
/summon minecraft:zombie -5.5 -59.0 -1.5
```

when the target player stands at `-5.5 -60 -1.5`. Text inside a quoted
argument is never touched.

A command that uses relative coordinates but has no target is refused rather
than silently redirected to the world spawn.

## The command allowlist

Every expanded command is checked against `functions.allowed-commands`. A
command that is not on the list is rejected when the function is compiled,
with the offending command named.

The check is repeated immediately before the command is dispatched.

## Execution

* A function is compiled on a worker thread, never on the server thread.
* The commands are dispatched on the server thread, under the
  `max-commands-per-tick` budget.
* A `delay` is timed by the Paper scheduler, so it never blocks a tick.
* A run that is cancelled stops at the next step. A command that was already
  dispatched cannot be undone.
* A run whose target player leaves is cancelled.
* A run never retries a command whose outcome is unknown, so a `summon` is
  never duplicated.

## Administration

```text
/tiktok functions list
/tiktok functions preview <id>
/tiktok functions run <id>
/tiktok jobs list
/tiktok jobs cancel <id>
```

`preview` shows the commands a function would run, with its parameters
resolved, without running anything. It is the safe way to check a function
you just edited.

## The `.mcfunction` format

A function may also be written in the extended `.mcfunction` format:

```text
# utilities:weather_change
# @param mode string clear

weather ${mode}
say Weather updated
```

A `# name` line at the top sets the id, and `# @param <name> <type> [default]`
declares a parameter. Every other line is a command.
