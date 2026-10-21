# The rule DSL

The DSL is optional. YAML and JSON rules are first-class and are enough for
almost everything. A script is a third way to write a rule, for the parts
that read better as a short block of text.

A script compiles into exactly the same internal rules as a YAML file, so
both formats share one engine, one set of limits and one set of diagnostics.
There is no second execution path.

Scripts live in `plugins/TikTokMinecraft/scripts/` and end in `.ttl`.

## A script

```text
on chat where chat.text == "!hello" {
    cooldown 10s per user
    broadcast "Hello from TikTok!"
}

on gift.final where gift.name == "Rose" {
    cooldown 2s per user
    call gifts:spawn_zombies(count = 1)
}

on like every 50 global {
    call gifts:spawn_chicken()
}

on social.follow {
    call effects:welcome()
}
```

## The grammar

```text
script     := rule*
rule       := 'on' TRIGGER ('where' condition)? ('every' NUMBER scope)? '{' statement* '}'
condition  := comparison (('and' | 'or') comparison)*
comparison := FIELD ('==' | '!=' | '>' | '>=' | '<' | '<=') VALUE
statement  := 'cooldown' DURATION scope?
            | 'call' FUNCTION_ID ('(' arguments ')')?
            | 'broadcast' STRING
arguments  := (NAME '=' VALUE (',' NAME '=' VALUE)*)?
```

| Construct | Meaning |
|---|---|
| `on <trigger>` | The event the rule listens for. |
| `where <condition>` | The field comparisons the event must satisfy. |
| `and` / `or` | How the comparisons are combined. |
| `every <number> [global\|user]` | A threshold over the like counter. |
| `cooldown <duration> [per user\|per rule]` | How long the rule stays quiet. |
| `call <function>(name = value)` | Runs a function. |
| `broadcast "text"` | Shows a message to every player. |

Durations accept `500ms`, `10s`, `2m` and `1h`.

## What it cannot do

The grammar has no escape hatch. A script cannot:

* evaluate an expression
* call Java, JavaScript or any other host language
* read or write a file
* reach the network
* import anything
* use reflection
* loop without a bound
* call itself
* run a shell command
* raise its own privileges

The only things a script can do are the three statements above.

## Errors

A script that does not compile is reported with its file, line and column
and the token that was expected:

```text
script example.ttl: script example.ttl:3:12: Unexpected 'nonsense'; expected 'cooldown', 'call' or 'broadcast'
```

A script that does not compile never replaces the working rules. The
previous rules stay active and the problem is reported by `/tiktok reload`.

## Rule ids

A script does not name its rules. The plugin generates the id from the file
name and the position in the file, for example `example.1` for the first
rule in `example.ttl`. `/tiktok rules list` shows the generated ids, and
they can be enabled and disabled like any other rule.
