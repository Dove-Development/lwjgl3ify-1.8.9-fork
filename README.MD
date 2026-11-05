# LWJGL3ify for Minecraft 1.8.9

Brings LWJGL3 (with SDL windowing and input) and modern Java (17, 21, 25) to Minecraft **1.8.9 Forge 11.15.1.2318**.

This is a port of [GTNewHorizons/lwjgl3ify](https://github.com/GTNewHorizons/lwjgl3ify), which does the same for 1.7.10.
It uses [RetroFuturaBootstrap](https://github.com/GTNewHorizons/RetroFuturaBootstrap) (RFB) in place of
LaunchWrapper, like upstream.

> [!WARNING]
> Alpha. Verified on Windows 11: a Prism Launcher-style production launch on Java 17, 21 and 25, plus the client in the
> development environment and the Java 8 drop-in relauncher on Java 21 (all reaching the main menu and a freshly
> generated world), and the dedicated server on Java 21 up to its EULA prompt. Not yet verified: macOS, Linux,
> OptiFine and other mods, and interactive input (typing/IME, mouse feel, fullscreen toggling).

## Differences from upstream (1.7.10)

- Built with [essential-loom](https://github.com/EssentialGG/architectury-loom) (Forge 11.15.1.2318, MCP `stable_22`)
  instead of RetroFuturaGradle, which only supports 1.7.10/1.12.2.
- **No UniMixins dependency.** 1.8.9 has no UniMixins, so lwjgl3ify bundles SpongePowered Mixin 0.7.11 (the version
  1.8.9 mods ship) and starts it itself. Mixin 0.7's bundled ASM 5 cannot read Java 9+ class files; lwjgl3ify lifts that
  check at load time, which also fixes other mods' copies of Mixin 0.7 on modern Java.
- Mixins were rewritten against 1.8.9 (e.g. the chat link opener moved to `GuiScreen`, signs store chat components).
  The 1.7.10 mod compatibility mixins (Xaero's, BetterQuesting, BiblioCraft, OpenComputers, Steve's, Logistics Pipes,
  Et Futurum) were dropped, since those mods target 1.7.10.
- The STB texture loading/stitching mixins are skipped automatically when OptiFine is installed.
- Forge patches and `EnumHelper` follow Forge 1.8.9 (`net.minecraftforge.fml` packages, 1.8.9's `EnumHelper` methods).

## Installation

Use Java 17.0.6+, 21 or 25. Download the files from this repository's GitHub releases.

### Prism Launcher / MultiMC

1. Create a Minecraft 1.8.9 instance (no need to install Forge).
2. Copy the contents of `lwjgl3ify-VERSION-multimc.zip` into the instance folder (next to `instance.cfg`),
   overwriting `mmc-pack.json`. Reload the launcher: the instance now shows "Minecraft with LWJGL3", "Forge-LWJGL3",
   "LWJGL 3" and the lwjgl3ify components.
3. Put the mod jar (`lwjgl3ify-VERSION.jar`) in the instance's `mods/` folder.
4. Set the instance's Java to 17.0.6+, 21 or 25.

### Any launcher (experimental relauncher)

Install Forge 1.8.9 as usual, launch it with Java 8 and drop `lwjgl3ify-VERSION.jar` into `mods/`. On startup, a window
asks which modern Java to relaunch the game with (and its memory settings); lwjgl3ify then downloads LWJGL3 and the
other libraries it needs into a cache folder and restarts the game on that Java. The settings are saved in
`config/lwjgl3ify-relauncher.json`.

### Vanilla launcher

`version.json` from a release is a complete launcher profile (Minecraft 1.8.9, Forge, LWJGL3 and lwjgl3ify's early
classpath jar). Put it in `.minecraft/versions/<id>/<id>.json`, where `<id>` is the `id` inside the file, and pick
that version in the launcher. Then put the mod jar in `mods/`.

### Server

1. Install a Forge 1.8.9 (11.15.1.2318) server with Forge's installer.
2. Put the mod jar in `mods/`.
3. Put the `forgePatches` jar next to the Forge universal and `minecraft_server.1.8.9.jar` jars and rename it to
   `lwjgl3ify-forgePatches.jar`. Copy [java9args.txt](java9args.txt) next to it.
4. Start the server with a modern Java:

```
java -Xmx4G -Xms4G @java9args.txt -jar lwjgl3ify-forgePatches.jar nogui
```

Thermos/Crucible-style hybrid servers are not supported.

### MultiMC: manual JVM arguments

Prism Launcher reads these from the instance patches; MultiMC needs them added to the instance's JVM arguments:

```
-Dfile.encoding=UTF-8 -Djava.system.class.loader=com.gtnewhorizons.retrofuturabootstrap.RfbSystemClassLoader --enable-native-access ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED --add-opens java.base/java.lang.invoke=ALL-UNNAMED --add-opens java.base/java.lang.ref=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.net.spi=ALL-UNNAMED --add-opens java.base/java.net=ALL-UNNAMED --add-opens java.base/java.nio.channels=ALL-UNNAMED --add-opens java.base/java.nio.charset=ALL-UNNAMED --add-opens java.base/java.nio.file=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/java.text=ALL-UNNAMED --add-opens java.base/java.time.chrono=ALL-UNNAMED --add-opens java.base/java.time.format=ALL-UNNAMED --add-opens java.base/java.time.temporal=ALL-UNNAMED --add-opens java.base/java.time.zone=ALL-UNNAMED --add-opens java.base/java.time=ALL-UNNAMED --add-opens java.base/java.util.concurrent.atomic=ALL-UNNAMED --add-opens java.base/java.util.concurrent.locks=ALL-UNNAMED --add-opens java.base/java.util.jar=ALL-UNNAMED --add-opens java.base/java.util.zip=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/jdk.internal.loader=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/jdk.internal.ref=ALL-UNNAMED --add-opens java.base/jdk.internal.reflect=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.desktop/com.sun.imageio.plugins.png=ALL-UNNAMED --add-opens java.desktop/sun.awt.image=ALL-UNNAMED --add-opens java.desktop/sun.awt=ALL-UNNAMED --add-opens java.desktop/sun.lwawt.macosx=ALL-UNNAMED --add-opens java.sql.rowset/javax.sql.rowset.serial=ALL-UNNAMED --add-opens jdk.dynalink/jdk.dynalink.beans=ALL-UNNAMED --add-opens jdk.naming.dns/com.sun.jndi.dns=ALL-UNNAMED,java.naming
```

### Tweaking configs

Window size, OpenGL context properties, input options and more are in `config/lwjgl3ify.cfg` after the first start.

## Development

You need JDK 17 (compiles the mod, Java 17 syntax lowered to Java 8 bytecode by Jabel) and JDK 21 (runs Gradle,
essential-loom and the game). Gradle finds both automatically if they are installed.

| Command | What it does |
| --- | --- |
| `./gradlew assemble` | Builds `build/libs/lwjgl3ify-VERSION.jar` (mod), `-forgePatches.jar`, `version.json`, and the Prism/CurseForge zips in `build/distributions/` |
| `./gradlew runClient` / `runServer` | Runs the game from source on Java 21 through RFB with LWJGL3 |
| `./gradlew runSmokeTest` | Runs the client with a test mod that opens a world, saves screenshots to `run/screenshots/`, checks the mixins and the SDL clipboard, then quits; look for `SMOKETEST PASSED` |
| `./gradlew remapSmoketestJar` then `python tools/run-production-test.py` | Launches the built Prism instance zip with the production jars the way Prism would (verifying every library hash), plus the smoke test |
| `python tools/run-production-test.py --relauncher --java8 <path to Java 8 java>` | Starts a stock Forge 1.8.9 install on Java 8 with just the mod jar added and lets the relauncher switch to modern Java |
| `python tools/generate-launcher-metadata.py` | Regenerates `prism-libraries/` and `launcher-metadata/version.json` from Mojang's and Forge's metadata |
| `./gradlew genSourcesWithVineflower` | Decompiles Minecraft + Forge (MCP names) for reading |

### Versions and releases

The fork has its own `MAJOR.MINOR.PATCH` version, `modVersion` in `gradle.properties` (the upstream version it is
based on is `upstreamVersion`). `python tools/bump-version.py patch|minor|major` bumps it locally.

To publish, run **Actions > Release > Run workflow** (`.github/workflows/release.yml`) and pick the bump: it updates
`gradle.properties`, commits, tags the commit with the version (no `v` prefix), builds, and publishes a GitHub release
with the mod jar, the forgePatches jar, `version.json`, the Prism/CurseForge zips and a `SHA256SUMS` file. Pushing a
tag yourself releases that tag as the version instead. Releases before 1.0.0 are marked as pre-releases.

The vanilla launcher `version.json` and the Dove launcher download the release files from the tag, so a release's
tag always equals its version.

# How does it work?

There are a few components to enabling modern Java and LWJGL3 on old Minecraft versions; lwjgl3ify implements them for
1.8.9 here:

 - A modified launcher profile (templates in `prism-libraries`, `curse-metadata` and `launcher-metadata`) that changes
   the libraries loaded onto the classpath: LWJGL2 is replaced by LWJGL3, and the forgePatches jar brings
   RetroFuturaBootstrap and newer libraries such as ASM 9, Apache Commons, JAXB and Nashorn.
 - A custom server launcher with the modified `Class-Path` manifest entry (integrated into the forgePatches jar).
 - A FOSS replacement for LaunchWrapper with a Java 9+-compatible classpath discovery mechanism and wider-reaching
   transformer capabilities: <https://github.com/GTNewHorizons/RetroFuturaBootstrap>.
 - A lot of add-opens flags for the JVM to allow reflection into JVM internals, which many libraries and mods rely on.
 - A partially auto-generated compatibility shim redirecting LWJGL2 API calls to the matching LWJGL3 calls
   (`src/main/java/org/lwjglx`, `src/generated/java/org/lwjglx`); the generator is in `src/util/java`.
 - Dummy `org/lwjgl/*` classes matching the classes removed from LWJGL3 (e.g. `Display`) for mods that look for them
   on the classpath.
 - RFB transformers:
   - Renaming any access to `org/lwjgl` to `org/lwjglx` after all other transformations (`LwjglRedirectTransformer`).
   - `javax/xml/bind` → `jakarta/xml/bind`, `java/util/jar/Pack200` → a Commons Compress implementation, `jdk/nashorn`
     → the standalone Nashorn, for APIs removed from the JDK.
   - Extensible enums (adapted under the LGPL from Forge for Minecraft 1.19.3) to replace `EnumHelper`'s reflection.
   - Object holder unfinalizer, to remove the need for overwriting `static final` fields in Forge.
   - Forge patches (`ForgePatchTransformer`): the class patch loader's EOF loop, `TracingPrintStream` closing
     `System.out`, `FMLSecurityManager` array bounds, `EnumHelper` redirected to lwjgl3ify's implementation, and loading
     progress forwarded to the window's taskbar progress.
   - Mixin 0.7's relocated ASM accepting Java 9+ class files (`MixinAsmCompatTransformer`).
 - Mixins:
   - Object holder application without writing to `static final` fields.
   - `JarDiscoverer` ignoring `module-info.class` and `META-INF/versions/*`, since it is not Java 9-aware.
   - Clipboard, URL opening and IME text input through SDL instead of AWT.
   - Sub-pixel mouse movement, and window resizing in borderless fullscreen.
 - Performance improvements using LWJGL3 libraries:
   - spng-based texture loading, much faster than ImageIO.
   - An STB rectpack-based texture stitcher, nearly instant with tens of thousands of textures.

## Fixing mods

This mod is not enough to make every mod work on Java 9+; some mods need code changes. A potentially incomplete list
of breaking changes:

#### (NOT IN lwjgl3ify) The system class loader is no longer a URLClassLoader

Lwjgl3ify replaces the system classloader with its own URLClassLoader-derived class that supports addURL and getURLs
via public method calls compatible with the old reflection access to them.

To get the classpath, split `System.getProperty("java.class.path")` by `File.pathSeparator`.

For most mods, their classloader is still a `LaunchClassLoader`, which does extend `URLClassLoader`.

To modify the URLClassPath you can use the following code:
```java
public static void addUrlToClassloader(ClassLoader loader, URL coreModUrl) {
    try {
        if (loader instanceof URLClassLoader) {
            if (ADDURL == null) {
                ADDURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                ADDURL.setAccessible(true);
            }
            ADDURL.invoke(loader, coreModUrl);
        } else {
            Field ucpField;
            try {
                // Java 8-11
                ucpField = loader.getClass().getDeclaredField("ucp");
            } catch (NoSuchFieldException e) {
                // Java 17
                ucpField = loader.getClass().getSuperclass().getDeclaredField("ucp");
            }
            ucpField.setAccessible(true);
            // URLClassPath is in different packages in different Java versions, so we use Object.
            final Object ucp = ucpField.get(loader);
            final Method urlAdder = ucp.getClass().getDeclaredMethod("addURL", URL.class);
            urlAdder.invoke(ucp, coreModUrl);
        }
    } catch (ReflectiveOperationException e) {
        throw new RuntimeException("Couldn't add url to classpath in loader " + loader.getClass(), e);
    }
}
```

#### `java.base/java.lang.reflect` classes are protected from reflective access

E.g. `Field.class.getDeclaredFields()` returns an empty list. The most common use of this was to change the
`modifiers` field to strip `final` from a field so `static final` fields could be written.
Instead, use an access transformer (`public-f class field_srg # field_mcp`) to remove `final` at class load time, or
inject a different initializer via Mixins/ASM into the class's static initializer `<clinit>`.

If that is not feasible (e.g. runtime reflection discovering fields), `sun.misc.Unsafe` can write the field's memory
directly:
```java
final Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
theUnsafe.setAccessible(true);
final Unsafe UNSAFE = (Unsafe) theUnsafe.get(null); // Cache this

public static void setField(Field data, Object object, Object value) {
    if (object == null) {
        long offset = UNSAFE.staticFieldOffset(data);
        Object base = UNSAFE.staticFieldBase(data);
        UNSAFE.putObject(base, offset, value);
    } else {
        long offset = UNSAFE.objectFieldOffset(data);
        UNSAFE.putObject(object, offset, value);
    }
}
```

**Beware**: the JIT compiler assumes final fields don't change at runtime, so a value written through Unsafe might not
be read back by some code, leading to inconsistent states.

#### Mixin 0.8+ features

1.8.9 mods bundle Mixin 0.7.x, and so does lwjgl3ify. Mods that need Mixin 0.8 (MixinExtras, newer injectors) ship
their own Mixin loader and are outside what lwjgl3ify sets up.

# Credits and license

LWJGL3ify was created by eigenraven and the GTNewHorizons contributors; see the upstream repository for its history.
This 1.8.9 port is maintained by Dove-Development. Licensed under the [LGPL-3.0](LICENSE), like upstream.
