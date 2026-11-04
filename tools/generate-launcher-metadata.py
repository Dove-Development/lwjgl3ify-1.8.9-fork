#!/usr/bin/env python3
"""
Regenerates the Minecraft 1.8.9 / Forge 11.15.1.2318 launcher metadata lwjgl3ify ships:

  prism-libraries/mmc-pack.json
  prism-libraries/patches/net.minecraft.json       (Prism Launcher / MultiMC component)
  prism-libraries/patches/net.minecraftforge.json  (Prism Launcher / MultiMC component)
  launcher-metadata/version.json                   (vanilla launcher profile, also read by the relauncher)

It starts from Mojang's 1.8.9 version JSON and Forge's library list, then applies lwjgl3ify's changes:
  - LWJGL 2 is dropped (the org.lwjgl3 component / @lwjglDownloads@ supplies LWJGL 3),
  - launchwrapper, ASM 5 and the old commons-compress/-lang3/-io are dropped (the forgePatches jar supplies
    RetroFuturaBootstrap, ASM 9 and newer commons),
  - gson is upgraded to 2.10.1, and Prism gets the Log4Shell-fixed log4j 2.0-beta9 build.

Usage: python tools/generate-launcher-metadata.py   (needs network access; downloads ~25 MB of Forge libraries
to compute their hashes, cached in build/launcher-metadata-cache)
"""

import hashlib
import json
import os
import urllib.request
from collections import OrderedDict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, "build", "launcher-metadata-cache")

MC_VERSION = "1.8.9"
FORGE_VERSION = "11.15.1.2318"
FORGE_FULL = f"{MC_VERSION}-{FORGE_VERSION}-{MC_VERSION}"
VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
FORGE_MAVEN = "https://maven.minecraftforge.net/"
MOJANG_LIBRARIES = "https://libraries.minecraft.net/"
JAVA_MAJORS = [17, 21, 25, 26]

# Removed from Minecraft's own library list (artifact coordinates without version)
DROPPED_MC_LIBRARIES = {
    "org.lwjgl.lwjgl:lwjgl",
    "org.lwjgl.lwjgl:lwjgl_util",
    "org.lwjgl.lwjgl:lwjgl-platform",
    "org.apache.commons:commons-compress",
    "org.apache.commons:commons-lang3",
    "commons-io:commons-io",
}

GSON_GAV = "com.google.code.gson:gson:2.10.1"
# Prism Launcher's Log4Shell-fixed build of Minecraft's log4j
PRISM_MAVEN = "https://files.prismlauncher.org/maven/"
LOG4J_FIXED_GAVS = {
    "org.apache.logging.log4j:log4j-api": "org.apache.logging.log4j:log4j-api:2.0-beta9-fixed",
    "org.apache.logging.log4j:log4j-core": "org.apache.logging.log4j:log4j-core:2.0-beta9-fixed",
}

# Forge 1.8.9's own libraries (from the universal jar's version.json), minus the ones lwjgl3ify replaces
# (launchwrapper, asm-all) or that Minecraft already provides (jopt-simple 4.6) or that only the server
# needs (jline).
FORGE_LIBRARIES = [
    ("net.minecraftforge:forge:" + FORGE_FULL + ":universal", FORGE_MAVEN),
    ("com.typesafe.akka:akka-actor_2.11:2.3.3", FORGE_MAVEN),
    ("com.typesafe:config:1.2.1", FORGE_MAVEN),
    ("org.scala-lang:scala-actors-migration_2.11:1.1.0", FORGE_MAVEN),
    ("org.scala-lang:scala-compiler:2.11.1", FORGE_MAVEN),
    ("org.scala-lang.plugins:scala-continuations-library_2.11:1.0.2", FORGE_MAVEN),
    ("org.scala-lang.plugins:scala-continuations-plugin_2.11.1:1.0.2", FORGE_MAVEN),
    ("org.scala-lang:scala-library:2.11.1", FORGE_MAVEN),
    ("org.scala-lang:scala-parser-combinators_2.11:1.0.1", FORGE_MAVEN),
    ("org.scala-lang:scala-reflect:2.11.1", FORGE_MAVEN),
    ("org.scala-lang:scala-swing_2.11:1.0.1", FORGE_MAVEN),
    ("org.scala-lang:scala-xml_2.11:1.0.2", FORGE_MAVEN),
    ("lzma:lzma:0.0.1", MOJANG_LIBRARIES),
    ("java3d:vecmath:1.5.2", MOJANG_LIBRARIES),
    ("net.sf.trove4j:trove4j:3.0.3", MOJANG_LIBRARIES),
]


def fetch(url, cache_name=None):
    path = os.path.join(CACHE, cache_name) if cache_name else None
    if path and os.path.isfile(path):
        with open(path, "rb") as f:
            return f.read()
    req = urllib.request.Request(url, headers={"User-Agent": "lwjgl3ify-metadata-generator"})
    with urllib.request.urlopen(req, timeout=120) as resp:
        data = resp.read()
    if path:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as f:
            f.write(data)
    return data


def maven_path(gav):
    parts = gav.split(":")
    group, artifact, version = parts[0], parts[1], parts[2]
    classifier = "-" + parts[3] if len(parts) > 3 else ""
    return f"{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}{classifier}.jar"


def artifact_key(name):
    parts = name.split(":")
    return parts[0] + ":" + parts[1]


def download_entry(gav, repo, with_path):
    path = maven_path(gav)
    url = repo + path
    data = fetch(url, path)
    artifact = OrderedDict()
    if with_path:
        artifact["path"] = path
    artifact["sha1"] = hashlib.sha1(data).hexdigest()
    artifact["size"] = len(data)
    artifact["url"] = url
    return OrderedDict(downloads=OrderedDict(artifact=artifact), name=gav)


def strip_path(entry):
    """Prism component libraries carry no 'path' (it is derived from the name)."""
    entry = json.loads(json.dumps(entry), object_pairs_hook=OrderedDict)
    downloads = entry.get("downloads", {})
    if "artifact" in downloads:
        downloads["artifact"].pop("path", None)
    for classifier in downloads.get("classifiers", {}).values():
        classifier.pop("path", None)
    return entry


def with_path(entry):
    entry = json.loads(json.dumps(entry), object_pairs_hook=OrderedDict)
    if "artifact" in entry.get("downloads", {}) and "path" not in entry["downloads"]["artifact"]:
        artifact = entry["downloads"]["artifact"]
        new_artifact = OrderedDict(path=maven_path(entry["name"]))
        new_artifact.update(artifact)
        entry["downloads"]["artifact"] = new_artifact
    return entry


def minecraft_libraries(mojang, log4j_fixed):
    libs = []
    for lib in mojang["libraries"]:
        key = artifact_key(lib["name"])
        if key in DROPPED_MC_LIBRARIES:
            continue
        if key == "com.google.code.gson:gson":
            libs.append(download_entry(GSON_GAV, MOJANG_LIBRARIES, with_path=True))
            continue
        if log4j_fixed and key in LOG4J_FIXED_GAVS:
            libs.append(download_entry(LOG4J_FIXED_GAVS[key], PRISM_MAVEN, with_path=False))
            continue
        libs.append(lib)
    return libs


def dump(path, obj, raw_tokens=None):
    text = json.dumps(obj, indent=4, ensure_ascii=False)
    for placeholder, raw in (raw_tokens or {}).items():
        text = text.replace(json.dumps(placeholder), raw)
    with open(os.path.join(ROOT, path), "w", encoding="utf-8", newline="\n") as f:
        f.write(text + "\n")
    print("wrote", path)


def main():
    manifest = json.loads(fetch(VERSION_MANIFEST))
    version_url = next(v["url"] for v in manifest["versions"] if v["id"] == MC_VERSION)
    mojang = json.loads(fetch(version_url, f"mojang-{MC_VERSION}.json"), object_pairs_hook=OrderedDict)

    forge_libs = [download_entry(gav, repo, with_path=True) for gav, repo in FORGE_LIBRARIES]
    forge_universal = fetch(FORGE_MAVEN + maven_path(FORGE_LIBRARIES[0][0]), maven_path(FORGE_LIBRARIES[0][0]))
    import io
    import zipfile

    forge_version_json = json.loads(zipfile.ZipFile(io.BytesIO(forge_universal)).read("version.json"))
    client = mojang["downloads"]["client"]

    # ---- Prism Launcher / MultiMC ------------------------------------------------------------------------------
    mc_component = OrderedDict()
    mc_component["assetIndex"] = mojang["assetIndex"]
    mc_component["compatibleJavaMajors"] = JAVA_MAJORS
    mc_component["compatibleJavaName"] = "java-runtime-epsilon"
    mc_component["formatVersion"] = 1
    mc_component["libraries"] = [strip_path(lib) for lib in minecraft_libraries(mojang, log4j_fixed=True)]
    mc_component["mainClass"] = mojang["mainClass"]
    mc_component["mainJar"] = OrderedDict(
        downloads=OrderedDict(artifact=OrderedDict(sha1=client["sha1"], size=client["size"], url=client["url"])),
        name=f"com.mojang:minecraft:{MC_VERSION}:client",
    )
    mc_component["minecraftArguments"] = mojang["minecraftArguments"]
    mc_component["name"] = "Minecraft with LWJGL3"
    mc_component["order"] = -2
    mc_component["releaseTime"] = mojang["releaseTime"]
    mc_component["requires"] = [OrderedDict(suggests="${lwjglVersion}", uid="org.lwjgl3")]
    mc_component["type"] = mojang["type"]
    mc_component["uid"] = "net.minecraft"
    mc_component["version"] = MC_VERSION
    dump("prism-libraries/patches/net.minecraft.json", mc_component)

    forge_component = OrderedDict()
    forge_component["+tweakers"] = ["net.minecraftforge.fml.common.launcher.FMLTweaker"]
    forge_component["formatVersion"] = 1
    forge_component["libraries"] = [strip_path(lib) for lib in forge_libs]
    forge_component["mainClass"] = "com.gtnewhorizons.retrofuturabootstrap.Main"
    forge_component["name"] = "Forge-LWJGL3"
    forge_component["order"] = 5
    forge_component["releaseTime"] = forge_version_json["time"]
    forge_component["requires"] = [OrderedDict(equals=MC_VERSION, uid="net.minecraft")]
    forge_component["uid"] = "net.minecraftforge"
    forge_component["version"] = FORGE_VERSION
    dump("prism-libraries/patches/net.minecraftforge.json", forge_component)

    mmc_pack = OrderedDict(
        components=[
            OrderedDict(
                cachedName="LWJGL 3",
                cachedVersion="${lwjglVersion}",
                cachedVolatile=True,
                dependencyOnly=True,
                uid="org.lwjgl3",
                version="${lwjglVersion}",
            ),
            OrderedDict(
                cachedName="Minecraft with LWJGL3",
                cachedRequires=[OrderedDict(suggests="${lwjglVersion}", uid="org.lwjgl3")],
                cachedVersion=MC_VERSION,
                important=True,
                uid="net.minecraft",
                version=MC_VERSION,
            ),
            OrderedDict(
                cachedName="LWJGL3ify Early Classpath",
                cachedVersion="${version}",
                uid="me.eigenraven.lwjgl3ify.forgepatches",
            ),
            OrderedDict(
                cachedName="Forge-LWJGL3",
                cachedRequires=[OrderedDict(equals=MC_VERSION, uid="net.minecraft")],
                cachedVersion=FORGE_VERSION,
                uid="net.minecraftforge",
                version=FORGE_VERSION,
            ),
            OrderedDict(
                cachedName="LWJGL3ify launch args",
                cachedVersion="${version}",
                uid="me.eigenraven.lwjgl3ify.launchargs",
            ),
        ],
        formatVersion=1,
    )
    dump("prism-libraries/mmc-pack.json", mmc_pack)

    # ---- Vanilla launcher ---------------------------------------------------------------------------------------
    game_args = mojang["minecraftArguments"].split(" ") + [
        "--tweakClass",
        "net.minecraftforge.fml.common.launcher.FMLTweaker",
        OrderedDict(rules=[OrderedDict(action="allow", features=OrderedDict(is_demo_user=True))], value="--demo"),
        OrderedDict(
            rules=[OrderedDict(action="allow", features=OrderedDict(has_custom_resolution=True))],
            value=["--width", "${resolution_width}", "--height", "${resolution_height}"],
        ),
    ]
    jvm_args = [
        OrderedDict(
            rules=[OrderedDict(action="allow", os=OrderedDict(name="windows"))],
            value="-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump",
        ),
        OrderedDict(rules=[OrderedDict(action="allow", os=OrderedDict(arch="x86"))], value="-Xss1M"),
        OrderedDict(rules=[OrderedDict(action="allow", os=OrderedDict(name="osx"))], value=["-XstartOnFirstThread"]),
        "-Djava.library.path=${natives_directory}",
        "-cp",
        "${classpath}",
        "@@JVM_ARGS@@",
    ]
    forge_patches = OrderedDict(
        downloads=OrderedDict(
            artifact=OrderedDict(
                path="com/github/Dove-Development/lwjgl3ify/@version@/lwjgl3ify-@version@-forgePatches.jar",
                sha1="@patchesJarHash@",
                size="@@PATCHES_SIZE@@",
                url="https://github.com/Dove-Development/lwjgl3ify-1.8.9-fork/releases/download/@version@/lwjgl3ify-@version@-forgePatches.jar",
            )
        ),
        name="com.github.Dove-Development:lwjgl3ify:@version@:forgePatches",
    )
    libraries = [with_path(lib) for lib in minecraft_libraries(mojang, log4j_fixed=False)]
    libraries.append("@@LWJGL_DOWNLOADS@@")
    libraries.append(forge_patches)
    libraries.extend(forge_libs)

    version = OrderedDict()
    version["arguments"] = OrderedDict(game=game_args, jvm=jvm_args)
    version["assetIndex"] = mojang["assetIndex"]
    version["assets"] = mojang["assets"]
    version["complianceLevel"] = 0
    version["downloads"] = mojang["downloads"]
    version["id"] = f"{MC_VERSION}-Forge{FORGE_VERSION}-{MC_VERSION}-lwjgl3ify-@version@"
    version["javaVersion"] = OrderedDict(component="java-runtime-epsilon", majorVersion=25)
    version["libraries"] = libraries
    version["logging"] = mojang["logging"]
    version["mainClass"] = "com.gtnewhorizons.retrofuturabootstrap.MainStartOnFirstThread"
    version["minimumLauncherVersion"] = 21
    version["releaseTime"] = "@time@"
    version["time"] = "@time@"
    version["type"] = "release"
    dump(
        "launcher-metadata/version.json",
        version,
        raw_tokens={
            "@@JVM_ARGS@@": "@jvmArgs@",
            "@@LWJGL_DOWNLOADS@@": "@lwjglDownloads@",
            "@@PATCHES_SIZE@@": "@patchesJarSize@",
        },
    )


if __name__ == "__main__":
    main()
