import net.fabricmc.loom.task.RemapJarTask
import org.apache.tools.ant.filters.ReplaceTokens
import java.security.MessageDigest

plugins {
    idea
    java
    // architectury-loom fork with legacy (1.8.9) Forge support
    id("gg.essential.loom") version "1.15.50"
    // Pack200 for Forge's binpatches when Gradle itself runs on Java 14+
    id("dev.architectury.architectury-pack200") version "0.1.3"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

val modId: String by project
val modName: String by project
val modGroup: String by project
val modVersion: String by project
val minecraftVersion: String by project
val forgeVersion: String by project
val mcpMappings: String by project

group = modGroup
version = modVersion
base.archivesName = modId

val taskGroup = "lwjgl3ify"

// The mod itself targets Java 8 bytecode (the relauncher tweaker runs on the Java 8 the launcher starts),
// but is written with Java 17 syntax, which Jabel lowers to Java 8.
val modernSyntaxToolchain: JavaToolchainSpec.() -> Unit = {
    languageVersion = JavaLanguageVersion.of(17)
}
// Classes that only ever run on modern Java (the relauncher stub, the hotswap plugin).
val java17Toolchain: JavaToolchainSpec.() -> Unit = {
    languageVersion = JavaLanguageVersion.of(17)
}
// The game runs on this in development.
val runtimeToolchain: JavaToolchainSpec.() -> Unit = {
    languageVersion = JavaLanguageVersion.of(21)
}

java {
    toolchain(modernSyntaxToolchain)
}

// ---------------------------------------------------------------------------------------------------------------------
// JVM arguments needed by any modern-Java launch of 1.8.9 Forge through RetroFuturaBootstrap
// ---------------------------------------------------------------------------------------------------------------------

val addOpens = listOf(
    "java.base/java.io=ALL-UNNAMED",
    "java.base/java.lang.invoke=ALL-UNNAMED",
    "java.base/java.lang.ref=ALL-UNNAMED",
    "java.base/java.lang.reflect=ALL-UNNAMED",
    "java.base/java.lang=ALL-UNNAMED",
    "java.base/java.net.spi=ALL-UNNAMED",
    "java.base/java.net=ALL-UNNAMED",
    "java.base/java.nio.channels=ALL-UNNAMED",
    "java.base/java.nio.charset=ALL-UNNAMED",
    "java.base/java.nio.file=ALL-UNNAMED",
    "java.base/java.nio=ALL-UNNAMED",
    "java.base/java.text=ALL-UNNAMED",
    "java.base/java.time.chrono=ALL-UNNAMED",
    "java.base/java.time.format=ALL-UNNAMED",
    "java.base/java.time.temporal=ALL-UNNAMED",
    "java.base/java.time.zone=ALL-UNNAMED",
    "java.base/java.time=ALL-UNNAMED",
    "java.base/java.util.concurrent.atomic=ALL-UNNAMED",
    "java.base/java.util.concurrent.locks=ALL-UNNAMED",
    "java.base/java.util.jar=ALL-UNNAMED",
    "java.base/java.util.zip=ALL-UNNAMED",
    "java.base/java.util=ALL-UNNAMED",
    "java.base/jdk.internal.loader=ALL-UNNAMED",
    "java.base/jdk.internal.misc=ALL-UNNAMED",
    "java.base/jdk.internal.ref=ALL-UNNAMED",
    "java.base/jdk.internal.reflect=ALL-UNNAMED",
    "java.base/sun.nio.ch=ALL-UNNAMED",
    "java.desktop/com.sun.imageio.plugins.png=ALL-UNNAMED",
    "java.desktop/sun.awt.image=ALL-UNNAMED",
    "java.desktop/sun.awt=ALL-UNNAMED",
    "java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
    "java.sql.rowset/javax.sql.rowset.serial=ALL-UNNAMED",
    "jdk.dynalink/jdk.dynalink.beans=ALL-UNNAMED",
    "jdk.naming.dns/com.sun.jndi.dns=ALL-UNNAMED,java.naming",
)

val extraJavaArgs = mutableListOf(
    "-Dfile.encoding=UTF-8",
    "-Djava.system.class.loader=com.gtnewhorizons.retrofuturabootstrap.RfbSystemClassLoader",
    "--enable-native-access",
    "ALL-UNNAMED"
)
for (openSpec in addOpens) {
    extraJavaArgs += listOf("--add-opens", openSpec)
}

tasks.register("updateJava9ArgsTxt") {
    group = taskGroup
    description = "Updates java9args.txt with the current argument list"
    val outFile = file("java9args.txt")
    outputs.file(outFile)
    val writtenText = extraJavaArgs.joinToString("\n") + "\n"
    doLast {
        outFile.writeText(writtenText, Charsets.UTF_8)
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Repositories and dependencies
// ---------------------------------------------------------------------------------------------------------------------

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/maven/") {
        content { includeGroup("org.spongepowered") }
    }
    maven("https://nexus.gtnewhorizons.com/repository/public/") {
        name = "GTNH Maven"
        content {
            includeGroup("com.gtnewhorizons.retrofuturabootstrap")
            includeGroup("me.eigenraven.java8unsupported")
        }
    }
    maven("https://files.prismlauncher.org/maven/") {
        name = "Prism Launcher (Log4Shell-fixed log4j 2.0-beta9)"
        content { includeVersionByRegex("org\\.apache\\.logging\\.log4j", ".*", ".*-fixed") }
        // Only the jars are published there, no POMs
        metadataSources { artifact() }
    }
    maven("https://nexus.gtnewhorizons.com/repository/central-sonatype-snapshots/") {
        name = "LWJGL snapshots"
        content { includeGroup("org.lwjgl") }
        mavenContent { snapshotsOnly() }
    }
}

val lwjglVersion: String = libs.versions.lwjgl.get()

// LWJGL3 native classifier for the machine running the build (used for the dev classpath).
val lwjgl3Natives: String = run {
    val os = System.getProperty("os.name").lowercase()
    val arm = System.getProperty("os.arch").let { it == "aarch64" || it == "arm64" }
    when {
        os.contains("win") -> if (arm) "natives-windows-arm64" else "natives-windows"
        os.contains("mac") -> if (arm) "natives-macos-arm64" else "natives-macos"
        else -> if (arm) "natives-linux-arm64" else "natives-linux"
    }
}

data class LwjglBinding(val name: String, val hasNatives: Boolean = true) {
    val module: String get() = if (name.isEmpty()) "lwjgl" else "lwjgl-$name"
}

val lwjgl3Bindings = listOf(
    LwjglBinding(""),
    LwjglBinding("freetype"),
    LwjglBinding("harfbuzz", hasNatives = false),
    LwjglBinding("jemalloc"),
    LwjglBinding("nuklear"),
    LwjglBinding("openal"),
    LwjglBinding("opengl"),
    LwjglBinding("sdl"),
    LwjglBinding("shaderc"),
    LwjglBinding("spng"),
    LwjglBinding("spvc"),
    LwjglBinding("stb"),
    LwjglBinding("tinyfd"),
    LwjglBinding("zstd"),
)

// Launcher platform name(s) for each LWJGL3 natives classifier.
val lwjglNativePlatforms: Map<String, List<String>> = linkedMapOf(
    "freebsd" to listOf("freebsd"),
    "linux-arm32" to listOf("linux-arm32"),
    "linux-arm64" to listOf("linux-arm64"),
    "linux-ppc64le" to listOf("linux-ppc64le"),
    "linux" to listOf("linux"),
    "macos-arm64" to listOf("osx", "osx-arm64"),
    "macos" to listOf("osx"),
    "windows-arm64" to listOf("windows", "windows-arm64"),
    "windows-x86" to listOf("windows"),
    "windows" to listOf("windows"),
)

lateinit var forgePatchesEmbedded: Configuration
lateinit var lwjgl3Classpath: Configuration
lateinit var allOfLwjgl3: Configuration
lateinit var lwjgl2Classpath: Configuration
lateinit var shadowImplementation: Configuration
lateinit var log4jFixed: Configuration

configurations {
    // Everything packed into the forgePatches jar, which goes on the game's classpath ahead of Minecraft's own
    // (older) libraries: RetroFuturaBootstrap (replacing launchwrapper), ASM 9, newer commons/gson, JAXB, Nashorn.
    forgePatchesEmbedded = create("forgePatchesEmbedded") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
    // LWJGL3 for this machine (dev classpath)
    lwjgl3Classpath = create("lwjgl3Classpath") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
    // LWJGL3 for every platform (launcher metadata)
    allOfLwjgl3 = create("allOfLwjgl3") {
        isCanBeConsumed = false
        isCanBeResolved = true
        isTransitive = false
    }
    // LWJGL2, only for the LWJGL2<->3 comparison tool
    lwjgl2Classpath = create("lwjgl2Classpath") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
    // Shaded into the mod jar
    shadowImplementation = create("shadowImplementation") {
        isCanBeConsumed = false
        isCanBeResolved = true
        isTransitive = false
    }
    // Bundled in the CurseForge zip, whose patches reference it without a download URL
    log4jFixed = create("log4jFixed") {
        isCanBeConsumed = false
        isCanBeResolved = true
        isTransitive = false
    }
    implementation { extendsFrom(shadowImplementation) }
    compileClasspath { extendsFrom(forgePatchesEmbedded, lwjgl3Classpath) }
    runtimeClasspath { extendsFrom(forgePatchesEmbedded, lwjgl3Classpath) }
}

configurations.configureEach {
    if (name == "lwjgl2Classpath") {
        return@configureEach
    }
    // Minecraft's LWJGL2 is replaced by LWJGL3 plus lwjgl3ify's org.lwjglx compatibility layer
    exclude(group = "org.lwjgl.lwjgl")
    // Forge's ASM 5 is replaced by ASM 9 from the forgePatches jar
    exclude(group = "org.ow2.asm", module = "asm-debug-all")
    resolutionStrategy.dependencySubstitution {
        substitute(module("net.minecraft:launchwrapper"))
            .using(module(libs.rfb.get().toString()))
            .because("RetroFuturaBootstrap replaces LaunchWrapper for Java 9+ compatibility")
    }
}

lateinit var hotswapSet: SourceSet
lateinit var relauncherStubSet: SourceSet
lateinit var stubsSet: SourceSet
lateinit var utilSet: SourceSet
lateinit var smoketestSet: SourceSet

sourceSets {
    stubsSet = create("stubs")
    utilSet = create("util")
    hotswapSet = create("hotswap")
    relauncherStubSet = create("relauncherStub") {
        compileClasspath += forgePatchesEmbedded
    }
    // Development-only client smoke test mod, run with `runSmokeTest`; never packaged
    smoketestSet = create("smoketest")
    main {
        java {
            srcDir("src/generated/java")
            srcDir(layout.buildDirectory.dir("generated/sources/tags"))
        }
        compileClasspath += stubsSet.output
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("de.oceanlabs.mcp:mcp_stable:$mcpMappings")
    forge("net.minecraftforge:forge:$minecraftVersion-$forgeVersion-$minecraftVersion")

    // Mixin for LaunchWrapper, the version 1.8.9 mods ship. Shaded into the mod jar.
    shadowImplementation("org.spongepowered:mixin:0.7.11-SNAPSHOT")
    // Refmap generator (must run on modern ASM)
    annotationProcessor("org.spongepowered:mixin:0.8.5-SNAPSHOT")
    annotationProcessor("com.google.code.gson:gson:2.8.9")
    annotationProcessor("com.google.guava:guava:21.0")
    for (module in listOf("asm", "asm-tree", "asm-commons", "asm-util", "asm-analysis")) {
        annotationProcessor("org.ow2.asm:$module:${libs.versions.asm.get()}")
    }

    // Java 17 syntax compiled to Java 8 bytecode
    annotationProcessor("com.github.bsideup.jabel:jabel-javac-plugin:1.0.0")
    compileOnly("com.github.bsideup.jabel:jabel-javac-plugin:1.0.0") { isTransitive = false }
    // Allow using Unsafe with --release 8
    compileOnly("me.eigenraven.java8unsupported:java-8-unsupported-shim:1.0.0")
    compileOnly("org.jetbrains:annotations:26.0.1")
    "stubsCompileOnly"("org.jetbrains:annotations:26.0.1")

    forgePatchesEmbedded(libs.rfb) { isTransitive = false }
    forgePatchesEmbedded(libs.gson)
    forgePatchesEmbedded(libs.asm)
    forgePatchesEmbedded(libs.asmCommons)
    forgePatchesEmbedded(libs.asmTree)
    forgePatchesEmbedded(libs.asmAnalysis)
    forgePatchesEmbedded(libs.asmUtil)
    forgePatchesEmbedded("org.apache.commons:commons-lang3:3.17.0")
    forgePatchesEmbedded("org.apache.commons:commons-compress:1.27.1")
    forgePatchesEmbedded("commons-io:commons-io:2.18.0")
    forgePatchesEmbedded("jakarta.xml.bind:jakarta.xml.bind-api:3.0.1")
    forgePatchesEmbedded("javax.servlet:javax.servlet-api:4.0.1")
    forgePatchesEmbedded("com.sun.xml.bind:jaxb-impl:3.0.2")
    forgePatchesEmbedded("org.openjdk.nashorn:nashorn-core:15.4")
    // javax.annotation.Resource (removed from the JDK in Java 11), which Mixin 0.7's LaunchWrapper service reads
    forgePatchesEmbedded("javax.annotation:javax.annotation-api:1.3.2")

    for (binding in lwjgl3Bindings) {
        lwjgl3Classpath("org.lwjgl:${binding.module}:$lwjglVersion")
        allOfLwjgl3("org.lwjgl:${binding.module}:$lwjglVersion")
        if (binding.hasNatives) {
            lwjgl3Classpath("org.lwjgl:${binding.module}:$lwjglVersion:$lwjgl3Natives")
            for (platform in lwjglNativePlatforms.keys) {
                allOfLwjgl3("org.lwjgl:${binding.module}:$lwjglVersion:natives-$platform")
            }
        }
    }

    log4jFixed("org.apache.logging.log4j:log4j-api:2.0-beta9-fixed")
    log4jFixed("org.apache.logging.log4j:log4j-core:2.0-beta9-fixed")

    lwjgl2Classpath("org.lwjgl.lwjgl:lwjgl:2.9.4-nightly-20150209")
    lwjgl2Classpath("org.lwjgl.lwjgl:lwjgl_util:2.9.4-nightly-20150209")

    "utilImplementation"(libs.asm)
    "utilImplementation"(libs.asmTree)
    "utilImplementation"("org.apache.commons:commons-lang3:3.17.0")
    "utilImplementation"("commons-io:commons-io:2.18.0")
    "utilImplementation"("commons-collections:commons-collections:3.2.2")
    "utilImplementation"("com.google.guava:guava:33.4.0-jre")

    "hotswapCompileOnly"("org.hotswapagent:hotswap-agent-core:1.4.1")
    "hotswapCompileOnly"(libs.rfb) { isTransitive = false }
}

configurations.named(smoketestSet.compileClasspathConfigurationName) { extendsFrom(configurations.compileClasspath.get()) }
configurations.named(smoketestSet.runtimeClasspathConfigurationName) { extendsFrom(configurations.runtimeClasspath.get()) }
configurations.named(smoketestSet.annotationProcessorConfigurationName) { extendsFrom(configurations.annotationProcessor.get()) }
smoketestSet.compileClasspath += sourceSets.main.get().output
smoketestSet.runtimeClasspath += sourceSets.main.get().output

// ---------------------------------------------------------------------------------------------------------------------
// Compilation
// ---------------------------------------------------------------------------------------------------------------------

val generateTags = tasks.register("generateTags") {
    val outDir = layout.buildDirectory.dir("generated/sources/tags")
    val versionString = project.version.toString()
    // Tab-separated, read by the relauncher
    val javaArgsString = extraJavaArgs.joinToString("\t")
    inputs.property("version", versionString)
    inputs.property("javaArgs", javaArgsString)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("me/eigenraven/lwjgl3ify/Tags.java").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package me.eigenraven.lwjgl3ify;

            // Generated by the generateTags Gradle task
            public class Tags {

                public static final String VERSION = "$versionString";
                public static final String RECOMMENDED_JAVA_ARGS = "$javaArgsString";
            }

            """.trimIndent(),
            Charsets.UTF_8
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.compileJava {
    dependsOn(generateTags)
    sourceCompatibility = "17"
    options.release = 8
    javaCompiler = javaToolchains.compilerFor(modernSyntaxToolchain)
}

tasks.named<JavaCompile>(smoketestSet.compileJavaTaskName) {
    sourceCompatibility = "17"
    options.release = 8
    javaCompiler = javaToolchains.compilerFor(modernSyntaxToolchain)
}

tasks.named<JavaCompile>(stubsSet.compileJavaTaskName) {
    options.release = 8
}

tasks.named<JavaCompile>(hotswapSet.compileJavaTaskName) {
    javaCompiler = javaToolchains.compilerFor(java17Toolchain)
    options.release = 17
}

tasks.named<JavaCompile>(relauncherStubSet.compileJavaTaskName) {
    javaCompiler = javaToolchains.compilerFor(java17Toolchain)
    options.release = 17
}

tasks.named<JavaCompile>(utilSet.compileJavaTaskName) {
    javaCompiler = javaToolchains.compilerFor(java17Toolchain)
    options.release = 17
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version.toString(),
        "modId" to modId,
        "modName" to modName,
        "modVersion" to project.version.toString(),
        "minecraftVersion" to minecraftVersion,
    )
    inputs.properties(props)
    filesMatching(listOf("META-INF/rfb-plugin/*", "mcmod.info")) {
        expand(props)
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Minecraft / Loom
// ---------------------------------------------------------------------------------------------------------------------

loom {
    forge {
        pack200Provider.set(dev.architectury.pack200.java.Pack200Adapter())
    }
    mixin {
        useLegacyMixinAp.set(true)
        defaultRefmapName.set("mixins.lwjgl3ify.refmap.json")
        add(sourceSets.main.get(), "mixins.lwjgl3ify.refmap.json")
    }
    runConfigs {
        named("client") {
            // RetroFuturaBootstrap has to be the entry point (loom's dev launch injector calls this). The client
            // variant keeps the JVM's first thread for SDL's event loop and runs the game on another thread.
            mainClass.set("com.gtnewhorizons.retrofuturabootstrap.MainStartOnFirstThread")
            vmArgs(extraJavaArgs)
            // Dev counterpart of what the relauncher tweaker does in production: the coremod (which brings in
            // the RFB plugin's late init) and Mixin.
            property("fml.coreMods.load", "me.eigenraven.lwjgl3ify.core.Lwjgl3ifyCoremod")
            programArgs("--tweakClass", "org.spongepowered.asm.launch.MixinTweaker", "--mixin", "mixins.lwjgl3ify.json")
        }
        create("smokeTest") {
            inherit(getByName("client"))
            configName = "Smoke Test"
            source(smoketestSet)
            mainClass.set("com.gtnewhorizons.retrofuturabootstrap.MainStartOnFirstThread")
            // Log every mixin as it is applied, and warn about injectors that matched nothing
            property("mixin.debug.verbose", "true")
            property("mixin.debug.countInjections", "true")
        }
        named("server") {
            mainClass.set("com.gtnewhorizons.retrofuturabootstrap.Main")
            vmArgs(extraJavaArgs)
            property("fml.coreMods.load", "me.eigenraven.lwjgl3ify.core.Lwjgl3ifyCoremod")
            programArgs("--tweakClass", "org.spongepowered.asm.launch.MixinTweaker", "--mixin", "mixins.lwjgl3ify.json")
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// forgePatches jar: put on the game classpath before Minecraft's libraries
// ---------------------------------------------------------------------------------------------------------------------

val forgePatchesJar = tasks.register<Jar>("forgePatchesJar") {
    dependsOn(tasks.compileJava)
    group = taskGroup
    description = "Packages the forgePatches jar"
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // Bootleg shadow jar
    forgePatchesEmbedded.resolve().forEach { dep ->
        from(zipTree(dep)) {
            // Keep each library's top-level META-INF files (licenses etc.) without clobbering each other
            filesMatching("META-INF/*") {
                name = "${dep.name}-$name"
            }
            exclude("META-INF/services/javax.script.ScriptEngineFactory")
        }
    }
    from(project.file("src/forgePatches/ScriptEngineServices.txt")) {
        rename { "META-INF/services/javax.script.ScriptEngineFactory" }
    }
    exclude("module-info.class")
    exclude("META-INF/versions/9/module-info.class")
    exclude("log4j2.xml")
    from(sourceSets.main.map { it.output.classesDirs }) {
        include("me/eigenraven/lwjgl3ify/rfb/entry/ServerMain.class")
    }
    from(relauncherStubSet.output)
    val versionString = project.version.toString()
    inputs.property("version", versionString)
    from("src/forgePatches/lwjgl3ify-forgePatches-version.txt") {
        expand(mapOf("version" to versionString))
        rename { "META-INF/lwjgl3ify-forgePatches-version.txt" }
    }
    archiveClassifier.set("forgePatches")
    manifest {
        // Dedicated server launches use `java @java9args.txt -jar lwjgl3ify-forgePatches.jar`
        val libraryList = listOf(
            "libraries/jline/jline/2.13/jline-2.13.jar",
            "libraries/com/typesafe/akka/akka-actor_2.11/2.3.3/akka-actor_2.11-2.3.3.jar",
            "libraries/com/typesafe/config/1.2.1/config-1.2.1.jar",
            "libraries/org/scala-lang/scala-actors-migration_2.11/1.1.0/scala-actors-migration_2.11-1.1.0.jar",
            "libraries/org/scala-lang/scala-compiler/2.11.1/scala-compiler-2.11.1.jar",
            "libraries/org/scala-lang/plugins/scala-continuations-library_2.11/1.0.2/scala-continuations-library_2.11-1.0.2.jar",
            "libraries/org/scala-lang/plugins/scala-continuations-plugin_2.11.1/1.0.2/scala-continuations-plugin_2.11.1-1.0.2.jar",
            "libraries/org/scala-lang/scala-library/2.11.1/scala-library-2.11.1.jar",
            "libraries/org/scala-lang/scala-parser-combinators_2.11/1.0.1/scala-parser-combinators_2.11-1.0.1.jar",
            "libraries/org/scala-lang/scala-reflect/2.11.1/scala-reflect-2.11.1.jar",
            "libraries/org/scala-lang/scala-swing_2.11/1.0.1/scala-swing_2.11-1.0.1.jar",
            "libraries/org/scala-lang/scala-xml_2.11/1.0.2/scala-xml_2.11-1.0.2.jar",
            "libraries/lzma/lzma/0.0.1/lzma-0.0.1.jar",
            "libraries/java3d/vecmath/1.5.2/vecmath-1.5.2.jar",
            "libraries/net/sf/trove4j/trove4j/3.0.3/trove4j-3.0.3.jar",
            "libraries/net/sf/jopt-simple/jopt-simple/4.6/jopt-simple-4.6.jar",
            "libraries/com/google/guava/guava/17.0/guava-17.0.jar",
            "forge-$minecraftVersion-$forgeVersion-$minecraftVersion-universal.jar",
            "minecraft_server.$minecraftVersion.jar"
        )
        attributes(
            mapOf(
                "Class-Path" to libraryList.joinToString(" "),
                "Add-Opens" to addOpens.map { it.split("=")[0] }.joinToString(" "),
                "Main-Class" to "me.eigenraven.lwjgl3ify.rfb.entry.ServerMain"
            )
        )
        attributes(
            mapOf("Implementation-Version" to libs.versions.asm.get()),
            "org/objectweb/asm/"
        )
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// LWJGL3 library list for the launcher metadata (Prism/MultiMC/CurseForge patches, vanilla launcher version.json)
// ---------------------------------------------------------------------------------------------------------------------

abstract class Lwjgl3JsonMaker : DefaultTask() {
    @get:Classpath
    abstract val libraries: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFileCurse: RegularFileProperty

    @get:Input
    abstract val lwjglVersionString: Property<String>

    @get:Input
    abstract val lwjglNativesTable: MapProperty<String, List<String>>

    @TaskAction
    fun makeJson() {
        val lwjglVersion = lwjglVersionString.get()
        val natives = lwjglNativesTable.get()
        val entries = mutableListOf<Pair<String, String>>() // gav to (json, curseJson)
        val curseEntries = mutableListOf<Pair<String, String>>()
        for (file in libraries.files) {
            val fileName = file.name
            val fileSize = file.length()
            val fileSha1 = MessageDigest.getInstance("SHA-1").digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }
            val versionInterlude = "-$lwjglVersion"
            val versionIdx = fileName.indexOf(versionInterlude)
            if (versionIdx == -1) {
                throw IllegalArgumentException("Could not determine lwjgl version in file $file")
            }
            val artifact = fileName.substring(0, versionIdx)
            val versionDash = lwjglVersion.indexOf('-')
            val urlVersion = if (versionDash >= 0) lwjglVersion.substring(0, versionDash) + "-SNAPSHOT" else lwjglVersion
            val classifier = fileName.substring(versionIdx + versionInterlude.length).removePrefix("-").removeSuffix(".jar")
            val dashClassifier = if (classifier.isEmpty()) "" else "-$classifier"
            val gav = "org.lwjgl:${artifact}${dashClassifier}:${lwjglVersion}"
            val filePath = "org/lwjgl/${artifact}/${lwjglVersion}/${artifact}${dashClassifier}-${lwjglVersion}.jar"
            val downloadUrl = if (urlVersion.contains("-SNAPSHOT")) {
                "https://nexus.gtnewhorizons.com/repository/central-sonatype-snapshots/org/lwjgl/${artifact}/${urlVersion}/${artifact}-${lwjglVersion}${dashClassifier}.jar"
            } else {
                "https://repo1.maven.org/maven2/org/lwjgl/${artifact}/${urlVersion}/${artifact}-${lwjglVersion}${dashClassifier}.jar"
            }
            val rules = if (classifier.isEmpty()) {
                null
            } else {
                val platforms = natives[classifier.removePrefix("natives-")]
                    ?: throw IllegalStateException("Could not determine native for $fileName, classifier $classifier")
                platforms.joinToString(",", "[", "]") { """{"action":"allow","os":{"name":"$it"}}""" }
            }
            val rulesJson = if (rules == null) "" else ""","rules":$rules"""
            entries += gav to """{"downloads":{"artifact":{"sha1":"$fileSha1","size":$fileSize,"url":"$downloadUrl"}},"name":"$gav"$rulesJson}"""
            curseEntries += gav to """{"downloads":{"artifact":{"path":"$filePath","sha1":"$fileSha1","size":$fileSize,"url":"$downloadUrl"}},"name":"$gav"$rulesJson}"""
        }
        outputFile.asFile.get().writeText(entries.sortedBy { it.first }.joinToString(",\n") { it.second }, Charsets.UTF_8)
        outputFileCurse.asFile.get().writeText(curseEntries.sortedBy { it.first }.joinToString(",\n") { it.second }, Charsets.UTF_8)
    }
}

val makeLwjgl3Json = tasks.register<Lwjgl3JsonMaker>("makeLwjgl3Json") {
    group = taskGroup
    libraries.from(allOfLwjgl3)
    outputFile = layout.buildDirectory.file("lwjgl3-libs.json")
    outputFileCurse = layout.buildDirectory.file("lwjgl3-libs-curse.json")
    lwjglVersionString = lwjglVersion
    lwjglNativesTable = lwjglNativePlatforms
}

abstract class MmcZip : Zip() {
    @get:InputFile
    abstract val lwjgl3Json: RegularFileProperty
}

val mmcInstanceFilesZip = tasks.register<MmcZip>("mmcInstanceFiles") {
    group = taskGroup
    description = "Packages the Prism Launcher / MultiMC instance patches"
    dependsOn(forgePatchesJar, makeLwjgl3Json)
    lwjgl3Json = makeLwjgl3Json.flatMap { it.outputFile }
    archiveClassifier.set("multimc")
    from(project.file("prism-libraries/"))
    from(forgePatchesJar) {
        into("libraries/")
    }
    exclude("META-INF", "META-INF/**")
    val projVersion = project.version
    val jvmArgs = extraJavaArgs.joinToString(", ") { '"' + it + '"' }
    val lwjglDownloadsFile = lwjgl3Json.asFile.get().absolutePath
    filesMatching(
        listOf(
            "mmc-pack.json",
            "patches/me.eigenraven.lwjgl3ify.forgepatches.json",
            "patches/me.eigenraven.lwjgl3ify.launchargs.json",
            "patches/org.lwjgl3.json"
        )
    ) {
        expand(
            mapOf(
                "version" to projVersion,
                "jvmArgs" to jvmArgs,
                "lwjglVersion" to lwjglVersion,
                "lwjglDownloadsFile" to lwjglDownloadsFile
            )
        )
    }
    filesMatching("patches/net.minecraft.json") {
        filter { it.replace("\${lwjglVersion}", lwjglVersion) }
    }
}

val curseInstanceFilesZip = tasks.register<MmcZip>("curseInstanceFiles") {
    group = taskGroup
    description = "Packages the CurseForge launcher patches"
    dependsOn(forgePatchesJar, makeLwjgl3Json)
    lwjgl3Json = makeLwjgl3Json.flatMap { it.outputFileCurse }
    archiveClassifier.set("curse")
    from(project.file("curse-metadata/"))
    from(forgePatchesJar) {
        into("overrides/libraries/")
    }
    from(log4jFixed) {
        into("overrides/libraries/")
    }
    exclude("META-INF", "META-INF/**")
    val projVersion = project.version
    val jvmArgs = extraJavaArgs.joinToString(", ") { '"' + it + '"' }
    val lwjglDownloadsFile = lwjgl3Json.asFile.get().absolutePath
    filesMatching(
        listOf(
            "manifest-additions.json",
            "overrides/patches/me.eigenraven.lwjgl3ify.forgepatches.json",
            "overrides/patches/me.eigenraven.lwjgl3ify.launchargs.json",
            "overrides/patches/org.lwjgl3.json"
        )
    ) {
        expand(
            mapOf(
                "version" to projVersion,
                "jvmArgs" to jvmArgs,
                "lwjglVersion" to lwjglVersion,
                "lwjglDownloadsFile" to lwjglDownloadsFile
            )
        )
    }
    filesMatching("overrides/patches/net.minecraft.json") {
        filter { it.replace("\${lwjglVersion}", lwjglVersion) }
    }
}

val versionJsonPath = layout.buildDirectory.file("libs/version.json").get().asFile

abstract class VersionJsonTask : DefaultTask() {
    @get:Inject
    abstract val fs: FileSystemOperations

    @get:InputFile
    abstract val lwjgl3Json: RegularFileProperty
}

val gitLastCommitDate: String = providers.exec {
    commandLine("git", "log", "-1", "--format=%aI", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.get().trim().ifEmpty { "2015-12-03T00:00:00+00:00" }

val versionJsonFile = tasks.register<VersionJsonTask>("versionJson") {
    group = taskGroup
    description = "Generates the vanilla launcher version.json file"
    dependsOn(makeLwjgl3Json, forgePatchesJar)
    val theForgePatchesJar = forgePatchesJar.map { it.outputs.files.first() }
    inputs.file("launcher-metadata/version.json")
    inputs.file(theForgePatchesJar)
    inputs.property("version", project.version)
    inputs.property("jvmArgs", extraJavaArgs)
    outputs.file(versionJsonPath)
    lwjgl3Json = makeLwjgl3Json.flatMap { it.outputFile }
    val projVersion = project.version.toString()
    val jvmArgs = extraJavaArgs.joinToString(", ") { '"' + it + '"' }
    val versionJsonPathLocal = versionJsonPath
    val lwjglDownloadsFile = lwjgl3Json.asFile.get()
    val gitLastCommitDateStr = gitLastCommitDate
    val lwjglVersionLocal = lwjglVersion
    doLast {
        versionJsonPathLocal.parentFile.mkdirs()
        val patchesJar = theForgePatchesJar.get()
        val patchesBytes = patchesJar.readBytes()
        val patchesJarHash = MessageDigest.getInstance("SHA-1").digest(patchesBytes)
            .joinToString("") { "%02x".format(it) }
        val lwjglDownloads = lwjglDownloadsFile.readText(Charsets.UTF_8)
        fs.copy {
            from("launcher-metadata/version.json")
            into(versionJsonPathLocal.parentFile)
            filter(
                mapOf(
                    "tokens" to mapOf(
                        "version" to projVersion,
                        "patchesJarSize" to patchesBytes.size.toString(),
                        "patchesJarHash" to patchesJarHash,
                        "jvmArgs" to jvmArgs,
                        "lwjglVersion" to lwjglVersionLocal,
                        "lwjglDownloads" to lwjglDownloads,
                        "time" to gitLastCommitDateStr
                    )
                ),
                ReplaceTokens::class.java
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Mod jar
// ---------------------------------------------------------------------------------------------------------------------

val modManifest: Map<String, String> = mapOf(
    // On Java 8 this hands control over to the relauncher; under RFB it loads the coremod and Mixin.
    // (The mixin config is registered by Lwjgl3ifyMixinTweaker, not through a MixinConfigs attribute.)
    "TweakClass" to "me.eigenraven.lwjgl3ify.relauncher.Lwjgl3ifyRelauncherTweaker",
    "FMLCorePluginContainsFMLMod" to "true",
    "ForceLoadAsMod" to "true",
    "Multi-Release" to "true",
    "Implementation-Title" to modName,
    "Implementation-Version" to project.version.toString(),
)

tasks.jar {
    archiveClassifier.set("dev-slim")
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
    manifest.attributes(modManifest)
}

tasks.shadowJar {
    dependsOn(forgePatchesJar, versionJsonFile)
    archiveClassifier.set("dev")
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
    configurations = listOf(shadowImplementation)
    from(hotswapSet.output)
    // Use .zip because shadow unpacks .jar archives into the parent jar
    from(forgePatchesJar) {
        rename { "me/eigenraven/lwjgl3ify/relauncher/forgePatches.zip" }
    }
    from(versionJsonFile) {
        rename { "me/eigenraven/lwjgl3ify/relauncher/version.json" }
    }
    exclude("module-info.class", "META-INF/versions/9/module-info.class")
    // Mixin's own manifest/jar signature files
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
    manifest.attributes(modManifest)
}

tasks.named<RemapJarTask>("remapJar") {
    inputFile.set(tasks.shadowJar.flatMap { it.archiveFile })
    archiveClassifier.set("")
    manifest.attributes(modManifest)
    // LWJGL3 has nothing to remap, and its Java 25+ multi-release classes are too new for tiny-remapper's ASM.
    val remapClasspath = configurations.compileClasspath.map { cp ->
        cp.filter { !it.path.replace('\\', '/').contains("/org.lwjgl/") }
    }
    classpath.setFrom(remapClasspath)
}

tasks.assemble {
    dependsOn(tasks.named("remapJar"), forgePatchesJar, mmcInstanceFilesZip, curseInstanceFilesZip, versionJsonFile)
}

// The smoke test mod, reobfuscated, for trying the production jars in a real mods folder
// (tools/run-production-test.py). Never published.
val smoketestJar = tasks.register<Jar>("smoketestJar") {
    from(smoketestSet.output)
    archiveClassifier.set("smoketest-dev")
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
}

tasks.register<RemapJarTask>("remapSmoketestJar") {
    group = taskGroup
    description = "Builds the reobfuscated smoke test mod used by tools/run-production-test.py"
    inputFile.set(smoketestJar.flatMap { it.archiveFile })
    archiveClassifier.set("smoketest")
    destinationDirectory.set(layout.buildDirectory.dir("smoketest"))
    val remapClasspath = configurations.compileClasspath.map { cp ->
        cp.filter { !it.path.replace('\\', '/').contains("/org.lwjgl/") }
    }
    classpath.setFrom(remapClasspath, sourceSets.main.map { it.output })
}

for (jarTask in listOf(forgePatchesJar)) {
    jarTask.configure {
        manifest {
            attributes(
                "Multi-Release" to true,
                "Implementation-Title" to modName,
                "Implementation-Version" to project.version.toString()
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Development runs: the game on Java 21 through RetroFuturaBootstrap, with LWJGL3 instead of LWJGL2
// ---------------------------------------------------------------------------------------------------------------------

val runtimeLauncher = javaToolchains.launcherFor(runtimeToolchain)

for (runTaskName in listOf("runClient", "runServer", "runSmokeTest")) {
    tasks.named<JavaExec>(runTaskName) {
        dependsOn(forgePatchesJar)
        javaLauncher.set(runtimeLauncher)
        // The forgePatches jar's newer libraries must win over Minecraft's old ones
        classpath = files(forgePatchesJar) + classpath
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// LWJGL2 <-> LWJGL3 API comparison tool
// ---------------------------------------------------------------------------------------------------------------------

val runComparisonTool = tasks.register<JavaExec>("runComparisonTool") {
    group = taskGroup
    description = "Runs the 2<->3 mapping generation"
    classpath(utilSet.runtimeClasspath)
    dependsOn(tasks.jar)
    mainClass.set("me.eigenraven.lwjgl3ify.ComparisonTool")
    javaLauncher.set(javaToolchains.launcherFor(java17Toolchain))
    minHeapSize = "2G"
    maxHeapSize = "2G"
    val lwjgl2 = lwjgl2Classpath
    val lwjgl3 = lwjgl3Classpath
    val modJar = tasks.jar.flatMap { it.archiveFile }
    argumentProviders.add(CommandLineArgumentProvider {
        lwjgl2.files.filter { !it.name.contains("natives") && !it.path.contains("net.java.j") }.map { "--2:$it" } +
            lwjgl3.files.filter { !it.name.contains("natives") }.map { "--3:$it" } +
            listOf("--M:" + modJar.get().asFile.path)
    })
}

