package me.eigenraven.lwjgl3ify.rfb;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.gtnewhorizons.retrofuturabootstrap.api.RetroFuturaBootstrap;

public class EarlyConfig {

    public static final String[] DEFAULT_EXTENSIBLE_ENUMS = new String[] {
        // From Forge 1.8.9's EnumHelper
        "net.minecraft.item.EnumAction", "net.minecraft.item.ItemArmor$ArmorMaterial",
        "net.minecraft.entity.item.EntityPainting$EnumArt", "net.minecraft.entity.EnumCreatureAttribute",
        "net.minecraft.entity.EnumCreatureType",
        "net.minecraft.world.gen.structure.StructureStrongholdPieces$Stronghold$Door",
        "net.minecraft.enchantment.EnumEnchantmentType", "net.minecraft.block.BlockPressurePlate$Sensitivity",
        "net.minecraft.util.MovingObjectPosition$MovingObjectType", "net.minecraft.world.EnumSkyBlock",
        "net.minecraft.entity.player.EntityPlayer$EnumStatus", "net.minecraft.item.Item$ToolMaterial",
        "net.minecraft.item.EnumRarity",
        // Forge enums mods commonly extend through EnumHelper.addEnum
        "net.minecraftforge.event.terraingen.PopulateChunkEvent$Populate$EventType",
        "net.minecraftforge.event.terraingen.InitMapGenEvent$EventType",
        "net.minecraftforge.event.terraingen.OreGenEvent$GenerateMinable$EventType",
        "net.minecraftforge.event.terraingen.DecorateBiomeEvent$Decorate$EventType",
        "net.minecraftforge.common.BiomeDictionary$Type",
        // Vanilla enums mods have been seen extending
        "net.minecraft.client.audio.MusicTicker$MusicType", "net.minecraft.client.audio.SoundCategory",
        "net.minecraft.event.HoverEvent$Action", "net.minecraft.world.WorldSettings$GameType",
        //
    };

    public static final Set<String> EXTENSIBLE_ENUMS = new HashSet<>(Arrays.asList(DEFAULT_EXTENSIBLE_ENUMS));

    public static class ConfigObject {

        public String[] extensibleEnums;
    }

    private static boolean isLoaded = false;

    public static void load() {
        if (isLoaded) {
            return;
        }
        isLoaded = true;

        final Path gamePath = RetroFuturaBootstrap.API.gameDirectory();
        final Path earlyConfigPath = gamePath.resolve("config")
            .resolve("lwjgl3ify-early.json");
        final Gson gson = new GsonBuilder().disableJdkUnsafe()
            .setPrettyPrinting()
            .create();
        ConfigObject cfg = new ConfigObject();
        if (Files.exists(earlyConfigPath)) {
            try {
                final String earlyConfigContents = new String(
                    Files.readAllBytes(earlyConfigPath),
                    StandardCharsets.UTF_8);
                ConfigObject parsedConfig = gson.fromJson(earlyConfigContents, ConfigObject.class);
                if (Objects.nonNull(parsedConfig)) {
                    cfg = parsedConfig;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (cfg.extensibleEnums != null) {
            EXTENSIBLE_ENUMS.addAll(Arrays.asList(cfg.extensibleEnums));
        }
        cfg.extensibleEnums = EXTENSIBLE_ENUMS.toArray(new String[0]);

        final String jsonCfg = gson.toJson(cfg);
        try {
            Files.createDirectories(earlyConfigPath.getParent());
            Files.write(earlyConfigPath, jsonCfg.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
