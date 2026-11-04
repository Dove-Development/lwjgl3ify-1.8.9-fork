package me.eigenraven.lwjgl3ify.relauncher;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import org.apache.logging.log4j.LogManager;

import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.CoreModManager;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.FMLRelaunchLog;

public class Lwjgl3ifyRelauncherTweaker implements ITweaker {

    private static boolean coremodInjected = false;

    public Lwjgl3ifyRelauncherTweaker() {
        // Load early in prod, late in dev to avoid a crash
        if (FMLLaunchHandler.side() != null) {
            injectMyCoremod();
        }
    }

    private void injectMyCoremod() {
        if (coremodInjected) {
            return;
        }
        try {
            URL mySrc = Lwjgl3ifyRelauncherTweaker.class.getProtectionDomain()
                .getCodeSource()
                .getLocation();
            while (mySrc.getProtocol()
                .equals("jar")) {
                final String all = mySrc.toString();
                mySrc = new URL(all.substring("jar:".length(), all.lastIndexOf('!')));
            }
            File myFile = new File(".");
            if (mySrc.getProtocol()
                .equals("file")) {
                myFile = Paths.get(mySrc.toURI())
                    .toFile();
            }
            final String coreModName = myFile.getName();
            boolean isReparseable = !System.getProperty("java.class.path")
                .contains(coreModName);
            FMLRelaunchLog.log.getLogger()
                .info("Lwjgl3ify reparseable status: {} (name: {})", isReparseable, coreModName);
            // If this tweaker is loaded, FML skips loading the coremod by default.
            Launch.classLoader.addTransformerExclusion("me.eigenraven.lwjgl3ify.core.Lwjgl3ifyCoremod");
            final Class<CoreModManager> cmmClass = (Class<CoreModManager>) Class
                .forName("net.minecraftforge.fml.relauncher.CoreModManager", true, Launch.classLoader);

            if (isReparseable) {
                // FML 1.8.9 puts jars with a TweakClass on its ignored list, so their @Mod classes would never be
                // found. Move this jar to the reparseable list instead, like Mixin does for MixinTweaker jars.
                final Method cmmGetIgnoredMods = cmmClass.getMethod("getIgnoredMods");
                final Method cmmGetReparseableCoremods = cmmClass.getMethod("getReparseableCoremods");
                final List<String> ignoredMods = (List<String>) cmmGetIgnoredMods.invoke(null);
                final List<String> reparseableCoremods = (List<String>) cmmGetReparseableCoremods.invoke(null);
                if (ignoredMods.remove(coreModName)) {
                    FMLRelaunchLog.log.getLogger()
                        .info("Removed lwjgl3ify from the list of ignored mod files");
                }
                if (!reparseableCoremods.contains(coreModName)) {
                    reparseableCoremods.add(coreModName);
                }
            }

            final Method cmmAddCoremod = cmmClass
                .getDeclaredMethod("loadCoreMod", LaunchClassLoader.class, String.class, File.class);
            cmmAddCoremod.setAccessible(true);
            cmmAddCoremod.invoke(null, Launch.classLoader, "me.eigenraven.lwjgl3ify.core.Lwjgl3ifyCoremod", myFile);
            coremodInjected = true;
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            } else {
                throw new RuntimeException(e);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        if (Launch.blackboard.get("lwjgl3ify:rfb-booted") != Boolean.TRUE) {
            if (FMLLaunchHandler.side()
                .isServer()) {
                LogManager.getLogger()
                    .fatal(
                        "lwjgl3ify relauncher does not support server launches, install according to lwjgl3ify's README.");
                FMLCommonHandler.instance()
                    .exitJava(1, false);
            }
            // Not launched via RFB, transfer control to the relauncher.
            try {
                new Relauncher(args.toArray(new String[0]), profile).run();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        injectMyCoremod();
        if (Launch.blackboard.get("lwjgl3ify:rfb-booted") == Boolean.TRUE) {
            // There is no UniMixins on 1.8.9: start the bundled Mixin 0.7 (or another mod's copy of it) and register
            // lwjgl3ify's mixin config. Tweakers can only be queued here, not while the tweaker list is being
            // iterated in the constructors.
            final List<String> tweakClasses = (List<String>) Launch.blackboard.get("TweakClasses");
            tweakClasses.add("org.spongepowered.asm.launch.MixinTweaker");
            tweakClasses.add("me.eigenraven.lwjgl3ify.core.Lwjgl3ifyMixinTweaker");
        }
    }

    @Override
    public String getLaunchTarget() {
        return null;
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }
}
