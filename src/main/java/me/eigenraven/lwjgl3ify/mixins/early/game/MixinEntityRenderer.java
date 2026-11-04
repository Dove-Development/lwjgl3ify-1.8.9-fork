package me.eigenraven.lwjgl3ify.mixins.early.game;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.EntityRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import me.eigenraven.lwjgl3ify.api.FloatMouseHelper;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {

    @Shadow
    private Minecraft mc;

    /**
     * Turn the camera by the sub-pixel mouse delta SDL reports instead of the whole-pixel one in MouseHelper. The
     * second setAngles call in updateCameraAndRender is the non-smooth-camera branch, which applies the raw delta.
     */
    @Redirect(
        method = "updateCameraAndRender",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;setAngles(FF)V", ordinal = 1),
        require = 0)
    private void lwjgl3ify$useFloatingMouseDelta(EntityPlayerSP player, float yaw, float pitch) {
        final FloatMouseHelper helper = (FloatMouseHelper) this.mc.mouseHelper;
        final float sensitivity = this.mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        final float factor = sensitivity * sensitivity * sensitivity * 8.0F;
        final float invert = this.mc.gameSettings.invertMouse ? -1.0F : 1.0F;
        player.setAngles(helper.lwjgl3ify$getFloatDX() * factor, helper.lwjgl3ify$getFloatDY() * factor * invert);
    }
}
