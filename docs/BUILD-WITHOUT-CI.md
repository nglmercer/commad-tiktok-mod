# Build Paper 1.21.11 without GitHub Actions

GitHub Actions billing is **not required** to compile this plugin. Build it on
your own Linux machine with the included Gradle wrappers.

## Requirements

- A **JDK 21** installation (both `java` and `javac` must be version 21).
- `git`, `python3`, `sha256sum`, and internet access for the initial
  downloads (Gradle, Maven dependencies, TikTok SDK and Paper API).
- Do not install a system Gradle: the repository and SDK use separate,
  versioned Gradle wrappers.

On CachyOS / Arch Linux, set the JDK 21 environment, if installed at the usual
path:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export PATH="$JAVA_HOME/bin:$PATH"
java -version
javac -version
```

From the root of `commad-tiktok-mod`:

```bash
bash scripts/build-local.sh
```

The script:

1. Clones `nglmercer/tiktok-signer` into the ignored
   `.build-deps/tiktok-signer` folder and checks out a pinned SDK commit.
2. Executes `sdk/java/gradlew publishToMavenLocal` so the Java SDK
   dependencies are resolvable without a published Maven repository.
3. Runs `./gradlew clean build :paper-plugin:verifyGraalJs`, including
   both modules' unit tests and the plugin packaging check.
4. Runs the schema checks and exports the **shaded** (installable) JAR.

Outputs:

```text
dist/TikTokMinecraft-1.21.11.jar
dist/TikTokMinecraft-1.21.11.jar.sha256
```

Install the JAR in `plugins/` on a **Paper 1.21.11** server running
**Java 21**. The `TikTokMinecraft-0.1.0-thin.jar` file from Gradle is NOT
installable: it lacks the embedded TikTok Java dependencies.

### If you already cloned the SDK

Pass its path to avoid another clone:

```bash
bash scripts/build-local.sh ../tiktok-signer
```

The supplied checkout is not reset. Its SDK commit is checked and a warning
is printed if it differs from the pinned reference.

### Optional: runtime smoke test

On a disposable Paper 1.21.11 server, place the generated JAR in
`plugins/`, start the server, and confirm `Enabling TikTokMinecraft` in
the log, with no `NoClassDefFoundError` or `ClassNotFoundException`.
Use `/tiktok test chat hello` and `/tiktok status` for a quick check;
this needs no active TikTok LIVE.

The local build performs unit and packaging tests, but it cannot substitute
for a real Paper runtime test.

### Publishing with zero GitHub Actions minutes

In GitHub, open **Releases → Draft a new release**, set an appropriate tag,
and upload `dist/TikTokMinecraft-1.21.11.jar`. Do this only after local
build/tests and a Paper smoke test pass. No workflow run is involved.

### Troubleshooting

- **Could not resolve TikTok SDK**: ensure the SDK publish step succeeded;
  the plugin consumes the `0.1.0-SNAPSHOT` modules from Maven Local.
- **Could not download Gradle/Paper/GraalJS**: allow network access to GitHub,
  `services.gradle.org` and the Maven/Paper repositories on first build.
- **Wrong Java version**: set `JAVA_HOME` and put its `bin` first in `PATH`.
- **Build succeeds but plugin fails to load**: use the shaded JAR in `dist/`,
  not the `-thin.jar`, and inspect the Paper startup log.

The normal GitHub Actions workflow has been restricted to **manual trigger**
in this PR to avoid automatic runs while billing is unavailable.
