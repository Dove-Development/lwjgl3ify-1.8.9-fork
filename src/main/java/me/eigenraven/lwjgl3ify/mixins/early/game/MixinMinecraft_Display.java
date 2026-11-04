package me.eigenraven.lwjgl3ify.mixins.early.game;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public class MixinMinecraft_Display {

    /**
     * {@code if (!this.fullscreen && Display.wasResized())}: SDL also reports size changes in fullscreen (borderless
     * fullscreen, display mode changes), so always pass the first half of the check.
     */
    @Redirect(
        method = "checkWindowResize",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;fullscreen:Z", opcode = Opcodes.GETFIELD))
    private boolean lwjgl3ify$alwaysUpdateScreenSize(Minecraft self) {
        return false;
    }
}
