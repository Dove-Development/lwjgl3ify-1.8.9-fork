package me.eigenraven.lwjgl3ify.mixins.early.game;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import me.eigenraven.lwjgl3ify.client.TextFieldHandler;

@Mixin(GuiScreen.class)
public class MixinGuiScreen extends Gui {

    /**
     * @author eigenraven
     * @reason Replace AWT reliance with SDL APIs.
     */
    @Overwrite
    public static String getClipboardString() {
        return TextFieldHandler.getClipboardText();
    }

    /**
     * @author eigenraven
     * @reason Replace AWT reliance with SDL APIs.
     */
    @Overwrite
    public static void setClipboardString(final String newText) {
        TextFieldHandler.setClipboardText(newText);
    }

    /** Chat links: 1.8 moved the link opener from GuiChat into GuiScreen. */
    @ModifyConstant(method = "openWebLink", constant = @Constant(stringValue = "java.awt.Desktop"), require = 0)
    private String lwjgl3ify$openUrlInSdl(String original) {
        return "me.eigenraven.lwjgl3ify.redirects.Desktop";
    }
}
