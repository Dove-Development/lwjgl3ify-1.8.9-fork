package me.eigenraven.lwjgl3ify.client;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraftforge.common.config.ConfigElement;

import org.lwjglx.input.Mouse;

import com.google.common.collect.Lists;

import net.minecraftforge.fml.client.config.GuiConfig;
import me.eigenraven.lwjgl3ify.core.Config;

@SuppressWarnings("unused")
public class GuiConfigLwjgl3ify extends GuiConfig {

    public GuiConfigLwjgl3ify(GuiScreen parent) {
        super(
            parent,
            Lists.newArrayList(
                new ConfigElement(Config.config.getCategory(Config.CATEGORY_WINDOW)),
                new ConfigElement(Config.config.getCategory(Config.CATEGORY_INPUT)),
                new ConfigElement(Config.config.getCategory(Config.CATEGORY_GLCONTEXT))),
            "lwjgl3ify",
            "config",
            false,
            false,
            "LWJGL3ify");
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        super.actionPerformed(button);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        if (Config.config.hasChanged()) {
            Config.config.save();
            Config.reloadConfigObject();
        }

        final Block wool = Blocks.wool;
        final BlockModelShapes shapes = Minecraft.getMinecraft()
            .getBlockRendererDispatcher()
            .getBlockModelShapes();
        // Draw scroll test widget
        int ypos = 32 + (int) (16.0 * Mouse.totalScrollAmount);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(TextureMap.locationBlocksTexture);
        for (int i = 0; i < 8; i++) {
            while (ypos > height - 64) {
                ypos -= (height - 64);
            }
            while (ypos < 32) {
                ypos += (height - 64);
            }
            drawTexturedModalRect(16, ypos, shapes.getTexture(wool.getStateFromMeta(i)), 16, 16);
            ypos += 16;
        }
    }
}
