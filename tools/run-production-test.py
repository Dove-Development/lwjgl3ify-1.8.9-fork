#!/usr/bin/env python3
"""
Runs the built (reobfuscated) lwjgl3ify outside the development environment, in one of two ways:

  default       Launches the Prism Launcher / MultiMC instance (build/distributions/lwjgl3ify-*-multimc.zip) the way
                Prism would, on modern Java, with the mod jar in a real mods/ folder. Every library listed in the
                instance metadata is downloaded and its SHA-1 verified, so this also validates prism-libraries/.

  --relauncher  Launches a stock Forge 1.8.9 install (launchwrapper, ASM 5, LWJGL 2) on Java 8 with only the mod jar
                added to mods/, the way any launcher would. lwjgl3ify's relauncher then downloads what it needs and
                restarts the game on modern Java by itself. Its download cache goes to <work>/relauncher-cache.

By default the reobfuscated smoke test mod (./gradlew remapSmoketestJar) is installed too: it opens a world, saves
screenshots to <work>/game/screenshots and quits, and this script exits non-zero unless it logged SMOKETEST PASSED.

Usage:
  ./gradlew assemble remapSmoketestJar
  python tools/run-production-test.py [--java PATH] [--assets DIR] [--no-smoketest] [--work DIR]
  python tools/run-production-test.py --relauncher --java8 PATH [--java PATH]
"""

import argparse
import glob
import hashlib
import json
import os
import platform
import shutil
import subprocess
import sys
import urllib.request
import uuid
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MC_VERSION = "1.8.9"
FORGE_FULL = "1.8.9-11.15.1.2318-1.8.9"
FORGE_MAVEN = "https://maven.minecraftforge.net/"
MOJANG_LIBRARIES = "https://libraries.minecraft.net/"
VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
# Forge 1.8.9's own libraries, as a stock install has them (see the universal jar's version.json)
STOCK_FORGE_LIBRARIES = [
    ("net.minecraftforge:forge:" + FORGE_FULL + ":universal", FORGE_MAVEN),
    ("net.minecraft:launchwrapper:1.12", MOJANG_LIBRARIES),
    ("org.ow2.asm:asm-all:5.0.3", MOJANG_LIBRARIES),
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


def current_os():
    system = platform.system().lower()
    if system.startswith("win"):
        return "windows"
    if system == "darwin":
        return "osx"
    return "linux"


def current_arch():
    """Architecture names as Prism Launcher maps them (RuntimeContext::mappedJavaRealArchitecture)."""
    machine = platform.machine().lower()
    if machine in ("amd64", "x86_64"):
        return "x86_64"
    if machine in ("i386", "i686", "x86"):
        return "x86"
    if machine in ("arm64", "aarch64"):
        return "arm64"
    if machine.startswith("arm"):
        return "arm32"
    return machine


def os_rule_matches(name):
    """Prism Launcher's RuntimeContext::classifierMatches: "<os>-<arch>" always, a bare "<os>" only on x86/x86_64."""
    if name == current_os() + "-" + current_arch():
        return True
    return current_arch() in ("x86_64", "x86") and name == current_os()


def rules_allow(rules):
    if not rules:
        return True
    allowed = False
    for rule in rules:
        matches = True
        if "os" in rule:
            want = rule["os"].get("name")
            if want is not None and not os_rule_matches(want):
                matches = False
            if rule["os"].get("arch") == "x86" and platform.architecture()[0] != "32bit":
                matches = False
        if "features" in rule:
            matches = False
        if matches:
            allowed = rule["action"] == "allow"
    return allowed


def maven_path(name):
    parts = name.split(":")
    group, artifact, version = parts[0], parts[1], parts[2]
    classifier = "-" + parts[3] if len(parts) > 3 else ""
    return f"{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}{classifier}.jar"


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "lwjgl3ify-production-test"})
    with urllib.request.urlopen(req, timeout=300) as resp:
        return resp.read()


def download(url, target, sha1):
    if os.path.isfile(target):
        with open(target, "rb") as f:
            if sha1 is None or hashlib.sha1(f.read()).hexdigest() == sha1:
                return target
    data = fetch(url)
    actual = hashlib.sha1(data).hexdigest()
    if sha1 is not None and actual != sha1:
        raise RuntimeError(f"SHA-1 mismatch for {url}: metadata says {sha1}, got {actual}")
    os.makedirs(os.path.dirname(target), exist_ok=True)
    with open(target, "wb") as f:
        f.write(data)
    return target


def resolve_libraries(libs, libraries, natives, local_dir=None):
    """Downloads launcher-format library entries; returns classpath jars and extracts natives."""
    classpath = []
    arch_bits = "64" if sys.maxsize > 2**32 else "32"
    for lib in libs:
        if not rules_allow(lib.get("rules")):
            continue
        name = lib["name"]
        if lib.get("MMC-hint") == "local":
            local = os.path.join(local_dir, os.path.basename(maven_path(name)))
            if not os.path.isfile(local):
                raise RuntimeError(f"Local library {local} missing from the instance zip")
            classpath.append(local)
            continue
        downloads = lib.get("downloads", {})
        if "natives" in lib:
            classifier = lib["natives"].get(current_os())
            if classifier is not None:
                classifier = classifier.replace("${arch}", arch_bits)
                art = downloads["classifiers"][classifier]
                jar = download(art["url"], os.path.join(libraries, maven_path(name + ":" + classifier)), art.get("sha1"))
                excludes = lib.get("extract", {}).get("exclude", [])
                with zipfile.ZipFile(jar) as z:
                    for entry in z.namelist():
                        if not entry.endswith("/") and not any(entry.startswith(e) for e in excludes):
                            z.extract(entry, natives)
        if "artifact" in downloads:
            art = downloads["artifact"]
            classpath.append(download(art["url"], os.path.join(libraries, maven_path(name)), art.get("sha1")))
        elif "url" in lib:
            classpath.append(download(lib["url"] + maven_path(name), os.path.join(libraries, maven_path(name)), None))
    return classpath


def game_arguments(mc_args, game, assets, asset_index, tweakers):
    index_name = asset_index
    if not os.path.isfile(os.path.join(assets, "indexes", index_name + ".json")):
        # loom names its copy of the 1.8 index "1.8.9-1.8"
        candidates = glob.glob(os.path.join(assets, "indexes", "*" + index_name + ".json"))
        if not candidates:
            sys.exit(f"No {index_name} asset index in {assets}")
        index_name = os.path.basename(candidates[0])[: -len(".json")]
    substitutions = {
        "${auth_player_name}": "ProdTest",
        "${version_name}": MC_VERSION,
        "${game_directory}": game,
        "${assets_root}": assets,
        "${assets_index_name}": index_name,
        "${auth_uuid}": str(uuid.UUID(int=0)).replace("-", ""),
        "${auth_access_token}": "0",
        "${user_properties}": "{}",
        "${user_type}": "legacy",
    }
    out = [substitutions.get(a, a) for a in mc_args.split(" ")]
    for tweaker in tweakers:
        out += ["--tweakClass", tweaker]
    return out


def prism_command(args, work, game, libraries, natives):
    zips = glob.glob(os.path.join(ROOT, "build", "distributions", "*-multimc.zip"))
    if len(zips) != 1:
        sys.exit("Run ./gradlew assemble first (expected exactly one multimc zip)")
    instance = os.path.join(work, "instance")
    shutil.rmtree(instance, ignore_errors=True)
    with zipfile.ZipFile(zips[0]) as z:
        z.extractall(instance)
    with open(os.path.join(instance, "mmc-pack.json"), encoding="utf-8") as f:
        pack = json.load(f)
    components = []
    for component in pack["components"]:
        with open(os.path.join(instance, "patches", component["uid"] + ".json"), encoding="utf-8") as f:
            components.append(json.load(f))
    components.sort(key=lambda c: c.get("order", 0))

    classpath, jvm_args, tweakers = [], [], []
    main_class = mc_args = main_jar = asset_index = None
    for component in components:
        jvm_args += component.get("+jvmArgs", [])
        if "FirstThreadOnMacOS" in component.get("+traits", []) and current_os() == "osx":
            # What Prism does for this trait: macOS only lets the JVM's first thread drive windows
            jvm_args.append("-XstartOnFirstThread")
        tweakers += component.get("+tweakers", [])
        main_class = component.get("mainClass", main_class)
        mc_args = component.get("minecraftArguments", mc_args)
        main_jar = component.get("mainJar", main_jar)
        asset_index = component.get("assetIndex", asset_index)
        classpath += resolve_libraries(component.get("libraries", []), libraries, natives,
                                       os.path.join(instance, "libraries"))
    art = main_jar["downloads"]["artifact"]
    classpath.append(download(art["url"], os.path.join(libraries, maven_path(main_jar["name"])), art.get("sha1")))
    cmd = [args.java] + jvm_args + ["-Djava.library.path=" + natives, "-cp", os.pathsep.join(classpath), main_class]
    return cmd + game_arguments(mc_args, game, args.assets, asset_index["id"], tweakers), classpath


def stock_forge_command(args, work, game, libraries, natives):
    if not args.java8:
        sys.exit("--relauncher needs --java8 pointing at a Java 8 java executable")
    manifest = json.loads(fetch(VERSION_MANIFEST))
    version_url = next(v["url"] for v in manifest["versions"] if v["id"] == MC_VERSION)
    mojang = json.loads(fetch(version_url))
    forge_libs = [{"name": gav, "url": repo} for gav, repo in STOCK_FORGE_LIBRARIES]
    classpath = resolve_libraries(forge_libs + mojang["libraries"], libraries, natives)
    client = mojang["downloads"]["client"]
    classpath.append(download(client["url"], os.path.join(libraries, maven_path("com.mojang:minecraft:1.8.9:client")),
                              client["sha1"]))
    # Skip the relauncher's settings window (nobody is there to press Run) and pick the modern Java
    config_dir = os.path.join(game, "config")
    os.makedirs(config_dir, exist_ok=True)
    with open(os.path.join(config_dir, "lwjgl3ify-relauncher.json"), "w", encoding="utf-8") as f:
        json.dump({"javaInstallationsCache": [args.java], "javaInstallation": 0, "hideSettingsOnLaunch": True,
                   "forwardLogs": True}, f)
    cache = os.path.join(work, "relauncher-cache", "maven")
    first_thread = ["-XstartOnFirstThread"] if current_os() == "osx" else []  # as the vanilla launcher does
    cmd = [args.java8] + first_thread + ["-Dlwjgl3ify.relauncher.mavenDownloadRoot=" + cache,
                                         "-Djava.library.path=" + natives, "-cp", os.pathsep.join(classpath),
                                         "net.minecraft.launchwrapper.Launch"]
    return cmd + game_arguments(mojang["minecraftArguments"], game, args.assets, mojang["assetIndex"]["id"],
                                ["net.minecraftforge.fml.common.launcher.FMLTweaker"]), classpath


def main():
    ap = argparse.ArgumentParser()
    java_home = os.environ.get("JAVA_HOME")
    ap.add_argument("--java", default=os.path.join(java_home, "bin", "java") if java_home else "java",
                    help="Modern (17+) java executable")
    ap.add_argument("--java8", help="Java 8 java executable, for --relauncher")
    ap.add_argument("--relauncher", action="store_true",
                    help="Start a stock Forge install on Java 8 and let lwjgl3ify relaunch it")
    ap.add_argument("--work", help="Work directory (default build/prodtest or build/relaunchtest)")
    ap.add_argument(
        "--assets",
        default=os.path.join(os.path.expanduser("~"), ".gradle", "caches", "essential-loom", "assets"),
        help="An assets directory containing the 1.8 asset index (a Gradle/loom cache or a .minecraft/assets)")
    ap.add_argument("--no-smoketest", action="store_true", help="Just start the game, without the smoke test mod")
    ap.add_argument("--timeout", type=int, default=900)
    args = ap.parse_args()

    mods = [j for j in glob.glob(os.path.join(ROOT, "build", "libs", "lwjgl3ify-*.jar")) if "forgePatches" not in j]
    if len(mods) != 1:
        sys.exit("Run ./gradlew assemble first (expected exactly one mod jar in build/libs)")
    if not args.no_smoketest:
        smoketest = glob.glob(os.path.join(ROOT, "build", "smoketest", "*-smoketest.jar"))
        if len(smoketest) != 1:
            sys.exit("Run ./gradlew remapSmoketestJar first, or pass --no-smoketest")
        mods += smoketest

    work = os.path.abspath(args.work or os.path.join(ROOT, "build", "relaunchtest" if args.relauncher else "prodtest"))
    libraries = os.path.join(work, "libraries")
    natives = os.path.join(work, "natives")
    game = os.path.join(work, "game")
    shutil.rmtree(natives, ignore_errors=True)
    shutil.rmtree(os.path.join(game, "mods"), ignore_errors=True)
    shutil.rmtree(os.path.join(game, "saves", "lwjgl3ify-smoketest"), ignore_errors=True)
    os.makedirs(natives)
    os.makedirs(os.path.join(game, "mods"))
    for mod in mods:
        shutil.copy(mod, os.path.join(game, "mods"))

    build = stock_forge_command if args.relauncher else prism_command
    cmd, classpath = build(args, work, game, libraries, natives)
    print("Command:", cmd[0], "...", cmd[cmd.index("-cp") + 2], " ".join(cmd[cmd.index("-cp") + 3:]))
    print("Classpath:", *[os.path.relpath(p, work) for p in classpath], sep="\n  ")
    log_path = os.path.join(work, "prodtest.log")
    with open(log_path, "w", encoding="utf-8", errors="replace") as log:
        proc = subprocess.Popen(cmd, cwd=game, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
                                encoding="utf-8", errors="replace")
        try:
            for line in proc.stdout:
                log.write(line)
                if any(m in line for m in ("SMOKETEST", "FATAL", "Exception in thread", "Relauncher", "relaunch")):
                    print(line.rstrip())
            proc.wait(timeout=args.timeout)
        except subprocess.TimeoutExpired:
            proc.kill()
            print("Timed out")
    print("Exit code:", proc.returncode, "- full log:", log_path)
    if not args.no_smoketest:
        with open(log_path, encoding="utf-8", errors="replace") as f:
            passed = "SMOKETEST PASSED" in f.read()
        print("Smoke test", "PASSED" if passed else "FAILED")
        sys.exit(0 if passed else 1)
    sys.exit(proc.returncode)


if __name__ == "__main__":
    main()
