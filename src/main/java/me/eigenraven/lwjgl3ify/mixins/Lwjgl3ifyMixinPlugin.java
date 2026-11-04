package me.eigenraven.lwjgl3ify.mixins;

import java.util.List;
import java.util.Set;

import net.minecraft.launchwrapper.Launch;

import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import me.eigenraven.lwjgl3ify.core.Config;
import me.eigenraven.lwjgl3ify.core.Lwjgl3ifyCoremod;

/**
 * Applies lwjgl3ify's optional mixins according to its config, the job GTNHMixins' builders did on 1.7.10.
 */
public class Lwjgl3ifyMixinPlugin implements IMixinConfigPlugin {

    private static final String PREFIX = "me.eigenraven.lwjgl3ify.mixins.early.";

    private boolean optifinePresent;

    @Override
    public void onLoad(String mixinPackage) {
        Config.loadConfig();
        optifinePresent = classExists("optifine.OptiFineForgeTweaker") || classExists("optifine.Installer");
        if (optifinePresent) {
            Lwjgl3ifyCoremod.LOGGER.info("OptiFine detected, disabling the STB texture loading/stitching mixins");
        }
    }

    private static boolean classExists(String name) {
        try {
            return Launch.classLoader != null && Launch.classLoader.getClassBytes(name) != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        final String mixin = mixinClassName.startsWith(PREFIX) ? mixinClassName.substring(PREFIX.length())
            : mixinClassName;
        switch (mixin) {
            case "game.MixinTextureAtlasSprite":
            case "game.MixinTextureMap":
                // OptiFine replaces TextureMap's sprite loading
                return Config.MIXIN_STBI_TEXTURE_LOADING && !optifinePresent;
            case "game.MixinStitcher":
                // OptiFine replaces the stitcher; overwriting it breaks connected textures/CTM
                return Config.MIXIN_STBI_TEXTURE_STITCHING && !optifinePresent;
            default:
                return true;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
        IMixinInfo mixinInfo) {}
}
