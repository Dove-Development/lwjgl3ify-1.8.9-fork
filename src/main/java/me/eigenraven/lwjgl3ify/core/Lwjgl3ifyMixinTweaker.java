package me.eigenraven.lwjgl3ify.core;

import java.io.File;
import java.util.List;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

import org.spongepowered.asm.mixin.Mixins;

/**
 * Registers lwjgl3ify's mixin config. Queued by the relauncher tweaker right after Mixin's own tweaker, so Mixin has
 * started by the time this is constructed, whichever mod's copy of Mixin 0.7 ended up being used.
 */
public class Lwjgl3ifyMixinTweaker implements ITweaker {

    public static final String MIXIN_CONFIG = "mixins.lwjgl3ify.json";

    public Lwjgl3ifyMixinTweaker() {
        Mixins.addConfiguration(MIXIN_CONFIG);
    }

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {}

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {}

    @Override
    public String getLaunchTarget() {
        return null;
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }
}
