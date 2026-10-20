# Installation

## Requirements

| | |
|---|---|
| Server | Paper 1.21.11 or a later 1.21.x release |
| Java | 21 |
| Disk | about 20 MB for the plugin |
| Network | internet access on the first start |

Folia and legacy Spigot are **not** supported. The plugin uses the Paper API's
main-thread scheduler and the Bukkit entity API directly, and it has not been
tested on either.

## Install the plugin

1. Put `TikTokMinecraft-<version>.jar` into the server's `plugins/` directory.
2. Start the server. The plugin creates `plugins/TikTokMinecraft/` and prints:

   ```text
   [TikTokMinecraft] Enabled. Creator: @elpadredomingooficial
   ```

3. Edit `plugins/TikTokMinecraft/config.yml` and set the creator:

   ```yaml
   tiktok:
     unique-id: "your_creator_here"
   ```

4. Run `/tiktok connect`, or restart the server with `auto-connect: true`.

## The first start needs internet access

The TikTok protocol needs a JavaScript engine to sign its requests. The
plugin asks Paper to load GraalJS through Paper's own library loader, which
downloads it from Maven Central and caches it in the server's `libraries/`
directory:

```text
[SpigotLibraryLoader] [TikTokMinecraft] Loading 3 libraries... please wait
[SpigotLibraryLoader] [TikTokMinecraft] Loaded library .../js-language-25.3.4.1.jar
```

After that the plugin needs no downloads of its own.

The first connection to a room also fetches TikTok's signing bundle once. It
is stored in `plugins/TikTokMinecraft/cache/` and verified against a pinned
SHA-256 before it is used. A bundle that does not match is refused.

## What the plugin does not need

The plugin runs entirely inside the server process. It does **not** need:

* Rust or Cargo
* a separate signer service
* Node.js, Bun or Python
* a browser
* a WebSocket broker
* a database
* a process supervisor

## Uninstalling

Stop the server and delete `plugins/TikTokMinecraft-*.jar` and the
`plugins/TikTokMinecraft/` directory.

## Troubleshooting

**"Invalid configuration; the plugin will not start"**
The console lists what is wrong. The most common cause is an empty
`tiktok.unique-id`.

**"TikTok discovery requires authentication or verification"**
TikTok asked for a captcha or a sign-in for that address. This is a
TikTok-side rate limit or verification, not a plugin error. Wait and try
again, or point the plugin at a session with cookies (see
[DEVELOPMENT.md](DEVELOPMENT.md)).

**"@creator is not live right now"**
The creator simply is not live. The plugin checks again every minute and
connects on its own as soon as the room opens.

**"GraalJS" or "NoClassDefFoundError" in the log**
Paper's library loader could not download the runtime. Check that the server
can reach `repo1.maven.org` and that no other plugin already locked a
different GraalJS version.
