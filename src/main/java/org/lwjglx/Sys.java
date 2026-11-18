package org.lwjglx;

import static org.lwjgl.sdl.SDLHints.*;
import static org.lwjgl.sdl.SDLInit.*;
import static org.lwjgl.sdl.SDLMessageBox.*;
import static org.lwjgl.sdl.SDLStdinc.*;
import static org.lwjgl.sdl.SDLTimer.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.Version;
import org.lwjgl.sdl.SDLClipboard;
import org.lwjgl.sdl.SDLMisc;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Platform;
import org.lwjgl.system.Pointer;
import org.lwjglx.opengl.Display;

import me.eigenraven.lwjgl3ify.client.MainThreadExec;
import me.eigenraven.lwjgl3ify.core.Config;

public class Sys {

    static private void firstTimeInit() {
        Configuration.OPENGL_EXPLICIT_INIT.set(true);
        Configuration.HARFBUZZ_LIBRARY_NAME.set("freetype");
        if (Platform.get() == Platform.MACOSX) {
            if (!GraphicsEnvironment.isHeadless()) {
                throw new IllegalStateException(
                    "java.awt.headless must be set to true on macOS, this is normally set by RFB!");
            }
        }
        MainThreadExec.ensureInitialised();
        MainThreadExec.runOnMainThread(() -> {
            SDL_SetMemoryFunctions(
                MemoryUtil::nmemAllocChecked,
                MemoryUtil::nmemCallocChecked,
                MemoryUtil::nmemReallocChecked,
                MemoryUtil::nmemFree);
            checkSdl(SDL_SetAppMetadata("Lwjgl3ify Minecraft", "1.8.9", Config.APP_ID));
            checkSdl(
                SDL_SetAppMetadataProperty(
                    SDL_PROP_APP_METADATA_URL_STRING,
                    "https://github.com/Dove-Development/lwjgl3ify-1.8.9-fork"));
            checkSdl(SDL_SetAppMetadataProperty(SDL_PROP_APP_METADATA_CREATOR_STRING, "Mojang, GTNH Team"));
            checkSdl(
                SDL_SetAppMetadataProperty(
                    SDL_PROP_APP_METADATA_COPYRIGHT_STRING,
                    "https://www.minecraft.net/en-us/eula"));
            checkSdl(SDL_SetAppMetadataProperty(SDL_PROP_APP_METADATA_TYPE_STRING, "game"));

            Platform currentPlatform = Platform.get();

            if (currentPlatform == Platform.LINUX && Config.WINDOW_LINUX_DESKTOP_ENTRY) {
                // Only improves how Wayland groups the window: a system where it cannot be written
                // (no home folder, read-only, ...) still plays.
                try {
                    createLinuxDesktopEntry();
                } catch (IOException | RuntimeException e) {
                    System.err.println("lwjgl3ify: Could not create the .desktop file for linuxCreateAppDesktopEntry: " + e);
                }
            }

            if (currentPlatform == Platform.LINUX) {
                SDL_SetHint(SDL_HINT_VIDEO_FORCE_EGL, "1");
                // ChromeOS's Linux gives SDL's mouse capture none of the input extensions it needs; on X11 the
                // pointer can be moved back to the window's centre instead, as LWJGL 2 does (see Mouse).
                if (isChromeOsLinux() && System.getenv("SDL_VIDEO_DRIVER") == null) {
                    SDL_SetHint(SDL_HINT_VIDEO_DRIVER, "x11");
                }
            }

            if (!SDL_Init(
                SDL_INIT_VIDEO | SDL_INIT_EVENTS
                    | SDL_INIT_JOYSTICK
                    | SDL_INIT_GAMEPAD
                    | SDL_INIT_HAPTIC
                    | SDL_INIT_SENSOR)) {
                throw new SDLException("Could not initialize SDL.");
            }
            // Platform-specific tricks
            switch (currentPlatform) {
                case MACOSX -> {
                    SDL_SetHint(SDL_HINT_MAC_OPENGL_ASYNC_DISPATCH, "1");
                }
                case LINUX -> {
                    // This is buggy on NVIDIA and crashes on secondary context creation on Wayland
                    SDL_setenv_unsafe("__GL_THREADED_OPTIMIZATIONS", "0", 1);
                }
                default -> {}
            }
            SDL_SetHint(SDL_HINT_MOUSE_FOCUS_CLICKTHROUGH, "1");
        });
    }

    /** ChromeOS's Linux (Crostini), which ships its integration in this folder. */
    public static boolean isChromeOsLinux() {
        return Files.isDirectory(FileSystems.getDefault().getPath("/opt/google/cros-containers"));
    }

    private static void createLinuxDesktopEntry() throws IOException {
        final FileSystem fs = FileSystems.getDefault();
        // $XDG_DATA_HOME, else ~/.local/share (its default), created if it is not there yet as on a
        // fresh or minimal system.
        Path XDG_DATA_HOME = null;
        String XDH_ENV = System.getenv("XDG_DATA_HOME");
        if (XDH_ENV != null && !XDH_ENV.isEmpty() && fs.getPath(XDH_ENV).isAbsolute()) {
            XDG_DATA_HOME = fs.getPath(XDH_ENV);
        } else {
            String HOME = System.getenv("HOME");
            if (HOME == null || HOME.isEmpty() || !Files.isDirectory(fs.getPath(HOME))) {
                HOME = System.getProperty("user.home");
            }
            if (HOME != null && !HOME.isEmpty() && Files.isDirectory(fs.getPath(HOME))) {
                XDG_DATA_HOME = fs.getPath(HOME, ".local", "share");
            }
        }
        if (XDG_DATA_HOME == null) {
            throw new IOException("neither $XDG_DATA_HOME nor a home folder is set");
        }
        Files.createDirectories(XDG_DATA_HOME);
        final Path applicationsPath = XDG_DATA_HOME.resolve("applications");
        final Path appDesktopPath = applicationsPath.resolve(Config.APP_ID + ".desktop");
        if (Files.exists(appDesktopPath)) {
            return;
        }
        Files.createDirectories(applicationsPath);
        final String desktopContents = """
            # Autogenerated by lwjgl3ify because linuxCreateAppDesktopEntry was set to true in its config
            [Desktop Entry]
            Version=1.0
            Type=Application
            Name=Lwjgl3ify Minecraft
            NoDisplay=true

            """;
        Files.write(appDesktopPath, desktopContents.getBytes(StandardCharsets.UTF_8));
        System.out.println(
            "lwjgl3ify: Created a .desktop file at " + appDesktopPath
                + " for better Wayland support, because linuxCreateAppDesktopEntry=true in lwjgl3ify.cfg");
    }

    private static final AtomicBoolean isFirstInit = new AtomicBoolean(true);

    public static void initialize() {
        if (!isFirstInit.compareAndSet(true, false)) {
            return;
        } else {
            firstTimeInit();
        }
    }

    /** Returns the LWJGL version. */
    public static String getVersion() {
        return Version.getVersion();
    }

    /**
     * Obtains the number of ticks that the hires timer does in a second. This method is fast; it should be called as
     * frequently as possible, as it recalibrates the timer.
     *
     * @return timer resolution in ticks per second or 0 if no timer is present.
     */
    public static long getTimerResolution() {
        return 1_000;
    }

    /**
     * Gets the current value of the hires timer, in ticks. When the Sys class is first loaded the hires timer is reset
     * to 0. If no hires timer is present then this method will always return 0.
     * <p>
     * <strong>NOTEZ BIEN</strong> that the hires timer WILL wrap around.
     *
     * Note: LWJGL2 always returned milliseconds here, so we do the same.
     *
     * @return the current hires time, in ticks (always >= 0)
     */
    public static long getTime() {
        return SDL_GetTicksNS() / 1_000_000;
    }

    public static long getNanoTime() {
        return SDL_GetTicksNS();
    }

    public static boolean openURL(String url) {
        return MainThreadExec.runOnMainThread(() -> SDLMisc.SDL_OpenURL(url));
    }

    public static void alert(String title, String message) {
        MainThreadExec.runOnMainThread(
            () -> { SDL_ShowSimpleMessageBox(SDL_MESSAGEBOX_WARNING, title, message, Display.getWindow()); });
    }

    public static boolean is64Bit() {
        return Pointer.BITS64;
    }

    public static String getClipboard() {
        return MainThreadExec.runOnMainThread(SDLClipboard::SDL_GetClipboardText);
    }

    public static void checkSdl(boolean result) {
        if (!result) {
            throw new SDLException();
        }
    }

    public static <T> @NotNull T checkSdl(@Nullable T result) {
        if (result == null) {
            throw new SDLException();
        }
        return result;
    }

    public static int checkSdl(int result) {
        if (result == NULL) {
            throw new SDLException();
        }
        return result;
    }

    public static long checkSdl(long result) {
        if (result == NULL) {
            throw new SDLException();
        }
        return result;
    }
}
