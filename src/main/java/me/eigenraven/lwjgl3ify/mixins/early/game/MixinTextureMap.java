package me.eigenraven.lwjgl3ify.mixins.early.game;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import net.minecraft.client.renderer.texture.TextureMap;

import org.apache.commons.io.IOUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import me.eigenraven.lwjgl3ify.textures.NativeBackedImage;

@Mixin(TextureMap.class)
public abstract class MixinTextureMap {

    /** Decode atlas sprites (and their custom mip levels) with spng instead of ImageIO. */
    @Redirect(
        method = "loadTextureAtlas",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureUtil;readBufferedImage(Ljava/io/InputStream;)Ljava/awt/image/BufferedImage;"))
    private BufferedImage lwjgl3ify$readImageWithSpng(InputStream stream) throws IOException {
        try {
            return NativeBackedImage.make(stream);
        } finally {
            IOUtils.closeQuietly(stream);
        }
    }
}
