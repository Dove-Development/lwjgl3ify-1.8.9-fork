package me.eigenraven.lwjgl3ify.mixins.early.game.openurl;

import net.minecraft.client.gui.stream.GuiStreamUnavailable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin({ GuiStreamUnavailable.class })
public class MixinGuiStreamUnavailable {

    @ModifyConstant(method = "func_152320_a", constant = @Constant(stringValue = "java.awt.Desktop"), require = 0)
    private String lwjgl3ify$openUrlInSdl(String original) {
        return "me.eigenraven.lwjgl3ify.redirects.Desktop";
    }
}
