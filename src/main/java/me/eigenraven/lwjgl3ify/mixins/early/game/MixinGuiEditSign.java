package me.eigenraven.lwjgl3ify.mixins.early.game;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiEditSign;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import me.eigenraven.lwjgl3ify.api.InputEvents;
import me.eigenraven.lwjgl3ify.client.TextFieldHandler;

@Mixin(GuiEditSign.class)
public abstract class MixinGuiEditSign extends GuiScreen implements InputEvents.KeyboardListener {

    @Shadow
    private TileEntitySign tileSign;

    @Shadow
    private int editLine;

    @Inject(method = "initGui", at = @At("HEAD"))
    private void lwjgl3ify$onOpen(CallbackInfo ci) {
        TextFieldHandler.beginTextInput();
    }

    @Inject(method = "onGuiClosed", at = @At("HEAD"))
    private void lwjgl3ify$onClose(CallbackInfo ci) {
        TextFieldHandler.endTextInput(null);
    }

    @Redirect(
        method = "keyTyped",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ChatAllowedCharacters;isAllowedCharacter(C)Z"))
    private boolean lwjgl3ify$denyStandardTextInput(char character) {
        // Text arrives through onTextEvent (SDL text input, IME-aware) instead
        return false;
    }

    @Override
    public void onTextEvent(InputEvents.TextEvent event) {
        // Vanilla: if (ChatAllowedCharacters.isAllowedCharacter(typedChar)
        // && this.fontRendererObj.getStringWidth(s + typedChar) <= 90) s = s + typedChar;
        if (!event.text.chars()
            .allMatch(chr -> ChatAllowedCharacters.isAllowedCharacter((char) chr))) {
            return;
        }
        final IChatComponent[] signText = tileSign.signText;
        String line = signText[editLine].getUnformattedText();
        for (int i = 0; i < event.text.length(); i++) {
            final String extended = line + event.text.charAt(i);
            if (this.fontRendererObj.getStringWidth(extended) > 90) {
                break;
            }
            line = extended;
        }
        signText[editLine] = new ChatComponentText(line);
    }
}
