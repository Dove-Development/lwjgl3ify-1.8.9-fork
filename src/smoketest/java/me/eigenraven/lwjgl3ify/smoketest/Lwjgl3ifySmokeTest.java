package me.eigenraven.lwjgl3ify.smoketest;

import java.io.File;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjglx.opengl.Display;

/**
 * Development-only smoke test (never packaged): drives the client through the main menu into a freshly generated
 * world, saving a screenshot of each to {@code run/screenshots/}, checks the SDL clipboard round trip, then quits.
 * Every result line is logged with a {@code SMOKETEST} prefix. Run it with {@code ./gradlew runSmokeTest}.
 */
@Mod(modid = "lwjgl3ify_smoketest", name = "lwjgl3ify smoke test", version = "1", clientSideOnly = true)
public class Lwjgl3ifySmokeTest {

    private static final Logger LOG = LogManager.getLogger("SMOKETEST");
    private static final int MENU_TICKS = 60;
    private static final int WORLD_TICKS = 300;
    private static final int TIMEOUT_TICKS = 20 * 240;

    private enum Stage {
        WAIT_MENU,
        WAIT_WORLD,
        DONE
    }

    private Stage stage = Stage.WAIT_MENU;
    private int stageTicks = 0;
    private int totalTicks = 0;
    private boolean failed = false;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        LOG.info("SMOKETEST registered");
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) {
            return;
        }
        final Minecraft mc = Minecraft.getMinecraft();
        totalTicks++;
        if (totalTicks > TIMEOUT_TICKS) {
            fail("timed out in stage " + stage + " (screen: " + mc.currentScreen + ")");
            finish(mc);
            return;
        }
        switch (stage) {
            case WAIT_MENU -> {
                if (!(mc.currentScreen instanceof GuiMainMenu)) {
                    stageTicks = 0;
                    return;
                }
                if (++stageTicks < MENU_TICKS) {
                    return;
                }
                LOG.info(
                    "SMOKETEST main menu up: display {}x{}, framebuffer {}x{}",
                    Display.getWidth(),
                    Display.getHeight(),
                    mc.displayWidth,
                    mc.displayHeight);
                screenshot(mc, "smoketest-mainmenu.png");
                checkClipboard();
                checkMixins(mc);
                LOG.info("SMOKETEST creating world");
                mc.launchIntegratedServer(
                    "lwjgl3ify-smoketest",
                    "lwjgl3ify smoke test",
                    new WorldSettings(8989L, WorldSettings.GameType.CREATIVE, true, false, WorldType.DEFAULT));
                stage = Stage.WAIT_WORLD;
                stageTicks = 0;
            }
            case WAIT_WORLD -> {
                // The run is unattended: don't let a window losing focus pause the game
                mc.gameSettings.pauseOnLostFocus = false;
                if (mc.currentScreen instanceof GuiIngameMenu) {
                    mc.displayGuiScreen(null);
                }
                if (mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null) {
                    stageTicks = 0;
                    return;
                }
                if (++stageTicks < WORLD_TICKS) {
                    return;
                }
                LOG.info(
                    "SMOKETEST in world: player at {}, {} chunks rendered, {}",
                    mc.thePlayer.getPosition(),
                    mc.renderGlobal.getDebugInfoRenders(),
                    mc.debug);
                screenshot(mc, "smoketest-world.png");
                finish(mc);
            }
            default -> {}
        }
    }

    /** Classes with lwjgl3ify mixins that the test does not otherwise open; loading them applies their mixins. */
    private static final String[] MIXIN_TARGETS = { "net.minecraft.client.gui.inventory.GuiEditSign",
        "net.minecraft.client.gui.GuiScreenBook", "net.minecraft.client.gui.GuiScreenDemo",
        "net.minecraft.client.gui.GuiScreenResourcePacks", "net.minecraft.client.gui.stream.GuiStreamUnavailable",
        "net.minecraftforge.fml.common.registry.ObjectHolderRef",
        "net.minecraftforge.fml.common.registry.ItemStackHolderRef" };

    private void checkMixins(Minecraft mc) {
        for (String target : MIXIN_TARGETS) {
            try {
                Class.forName(target, false, Lwjgl3ifySmokeTest.class.getClassLoader());
            } catch (Throwable t) {
                fail("loading mixin target " + target + " threw " + t);
            }
        }
        if (mc.mouseHelper instanceof me.eigenraven.lwjgl3ify.api.FloatMouseHelper) {
            LOG.info("SMOKETEST MouseHelper mixin applied");
        } else {
            fail("MouseHelper mixin not applied");
        }
        if (me.eigenraven.lwjgl3ify.api.InputEvents.KeyboardListener.class
            .isAssignableFrom(net.minecraft.client.gui.inventory.GuiEditSign.class)) {
            LOG.info("SMOKETEST GuiEditSign mixin applied");
        } else {
            fail("GuiEditSign mixin not applied");
        }
    }

    private void checkClipboard() {
        final String probe = "lwjgl3ify clipboard ✓ " + System.nanoTime();
        try {
            GuiScreen.setClipboardString(probe);
            final String back = GuiScreen.getClipboardString();
            if (probe.equals(back)) {
                LOG.info("SMOKETEST clipboard round trip OK");
            } else {
                fail("clipboard round trip returned '" + back + "'");
            }
        } catch (Throwable t) {
            fail("clipboard round trip threw " + t);
        }
    }

    private void screenshot(Minecraft mc, String name) {
        try {
            ScreenShotHelper.saveScreenshot(mc.mcDataDir, name, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            LOG.info("SMOKETEST saved {}", new File(new File(mc.mcDataDir, "screenshots"), name).getAbsolutePath());
        } catch (Throwable t) {
            fail("screenshot " + name + " threw " + t);
        }
    }

    private void fail(String what) {
        failed = true;
        LOG.error("SMOKETEST FAILED: {}", what);
    }

    private void finish(Minecraft mc) {
        stage = Stage.DONE;
        LOG.info("SMOKETEST {}", failed ? "FINISHED WITH FAILURES" : "PASSED");
        mc.shutdown();
    }
}
