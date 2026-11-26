package dev.urinth.recordable.audio;

import dev.urinth.recordable.NativeFolders;
import dev.urinth.recordable.Settings;
import dev.urinth.recordable.nat.Mem;
import dev.urinth.recordable.nat.Native;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Everything about getting the game's sound to the recorder, apart from the calls into OpenAL themselves (those are in
 * {@code OpenAlBridge}, which only the sound engine hook uses).
 *
 * <p>How it works: when the game's sound engine starts, a hook (the mixin) asks this class whether the sound should go through the
 * recorder. If so, the engine gets an OpenAL "loopback" device instead of the phone's speaker; this class then plays what that device
 * renders through Android's AAudio and offers a copy to the recorder (see {@link AudioPump}). Whenever anything about this does not work,
 * the hook falls back to the game's normal sound and the recorder simply records without sound. The setting is read at the game's start,
 * because the sound engine starts only once.
 *
 * <p>A marker file is kept while the sound starts. If the game dies in that moment (a problem inside the phone's sound system that Java
 * cannot catch) the marker is still there at the next start, and the sound recording is switched off so the game starts normally.
 */
public final class GameAudio {
    private static final Logger LOG = LoggerFactory.getLogger("Record-able Lite");
    private static final AudioTap TAP = new AudioTap();
    private static final Object LOCK = new Object();

    private static boolean wanted;
    private static boolean blockedByMarker;
    private static Path gameDir;
    private static Path marker;
    private static String status = "sound: not set up yet";
    private static long device;
    private static AudioPump pump;
    private static AudioPump.Output output;

    private GameAudio() {
    }

    /**
     * Called once when the game starts, before the sound engine exists.
     *
     * @param recordSound the setting
     * @return false if the sound recording was switched off because the last start did not finish (the caller should save that)
     */
    public static boolean configure(boolean recordSound, Path gameDirectory) {
        synchronized (LOCK) {
            gameDir = gameDirectory;
            marker = Settings.configDir(gameDirectory).resolve("audio-starting.marker");
            blockedByMarker = false;
            wanted = recordSound;
            if (Files.exists(marker)) {
                delete(marker);
                if (recordSound) {
                    wanted = false;
                    blockedByMarker = true;
                    status = "sound: switched off, because the game stopped while the sound was starting the last time. "
                            + "Turn it on again in the recorder settings (F9) to try once more";
                    LOG.warn("Sound recording was switched off: the marker of an unfinished sound start was found");
                    return false;
                }
            }
            status = recordSound ? "sound: waiting for the game's sound engine to start" : "sound: switched off in the settings";
            return true;
        }
    }

    public static AudioTap tap() {
        return TAP;
    }

    /** One line about the sound, for the report and the chat. */
    public static String status() {
        synchronized (LOCK) {
            String s = status;
            AudioPump p = pump;
            if (p != null) {
                s += "; " + p.renderedChunks() + " pieces rendered, " + p.notPlayedChunks() + " not played"
                        + (p.problem() != null ? "; problem: " + p.problem() : "");
                if (output != null) {
                    s += "; " + output.describe();
                }
            }
            return s;
        }
    }

    /** True while the game's sound goes through the recorder and is being rendered. */
    public static boolean active() {
        synchronized (LOCK) {
            AudioPump p = pump;
            return p != null && p.isRunning();
        }
    }

    /** True if the sound engine got a loopback device from the hook (the sound can be recorded). */
    public static boolean loopbackDevice() {
        synchronized (LOCK) {
            return device != 0;
        }
    }

    public static boolean isLoopbackDevice(long dev) {
        synchronized (LOCK) {
            return dev != 0 && dev == device;
        }
    }

    /** True if the sound was asked for and was not switched off. */
    public static boolean wanted() {
        synchronized (LOCK) {
            return wanted;
        }
    }

    public static boolean blockedByMarker() {
        synchronized (LOCK) {
            return blockedByMarker;
        }
    }

    /**
     * Why the sound cannot be recorded right now, or null if it can. The sound engine starts once when the game starts, so the setting
     * only counts from then on.
     */
    public static String whyNotRecorded(boolean settingOn) {
        synchronized (LOCK) {
            AudioPump p = pump;
            if (p != null && p.isRunning()) {
                return null;
            }
            if (!settingOn) {
                return "game sound recording is switched off in the settings (F9)";
            }
            if (!wanted && !blockedByMarker) {
                return "the game was started with sound recording switched off; restart the game after turning it on";
            }
            String text = status.startsWith("sound: ") ? status.substring(7) : status;
            if (p == null && device == 0 && wanted && text.startsWith("waiting")) {
                return "the sound engine has not been taken over (the sound hook did not run); see the game's log";
            }
            return text;
        }
    }

    // ---- the hook ------------------------------------------------------------------------------------------------------------------

    /** Asked by the hook before the sound engine opens its device. */
    public static boolean wantsLoopback() {
        synchronized (LOCK) {
            if (!wanted) {
                return false;
            }
            if (!Mem.available()) {
                status = "sound: not recorded (this Java has no raw memory access)";
                return false;
            }
            if (!Native.init(NativeFolders.list(gameDir))) {
                status = "sound: not recorded (native calls are not available: " + Native.status() + ")";
                return false;
            }
            return true;
        }
    }

    /** The hook opened the loopback device; this opens the speaker. If that fails the hook gives the device back. */
    public static boolean deviceOpened(long dev) {
        synchronized (LOCK) {
            stopLocked();
            touch(marker);
            try {
                AAudioOutput o = AAudioOutput.open();
                output = o;
                device = dev;
                status = "sound: the game's sound goes through the recorder";
                return true;
            } catch (IOException | RuntimeException | LinkageError e) {
                delete(marker);
                status = "sound: not recorded (the phone's audio output could not be opened: " + e.getMessage() + ")";
                LOG.warn("Sound is not recorded: {}", status);
                return false;
            }
        }
    }

    /** Something went wrong in the hook; the game goes on with its own sound. */
    public static void failed(String what, Throwable t) {
        synchronized (LOCK) {
            status = "sound: not recorded (" + what + (t == null ? "" : ": " + t) + ")";
            LOG.warn("Sound is not recorded: {}", status, t);
            stopLocked();
            delete(marker);
        }
    }

    /** The sound engine is ready; start playing and offering the sound. */
    public static void startPump(AudioPump.Renderer renderer) {
        synchronized (LOCK) {
            if (output == null || device == 0 || pump != null) {
                return;
            }
            AudioPump p = new AudioPump(renderer, output, TAP, () -> delete(marker));
            pump = p;
            p.start();
            LOG.info("The game's sound now goes through the recorder ({})", output.describe());
        }
    }

    /** The sound engine is going away (shutdown or reload): nothing may touch its device after this returns. */
    public static void stopPump() {
        synchronized (LOCK) {
            stopLocked();
        }
    }

    private static void stopLocked() {
        AudioPump p = pump;
        pump = null;
        if (p != null && !p.stop(3000)) {
            LOG.warn("The sound thread did not stop in time");
        }
        AudioPump.Output o = output;
        output = null;
        if (o != null) {
            o.close();
        }
        device = 0;
    }

    private static void touch(Path file) {
        try {
            if (file != null) {
                Files.createDirectories(file.getParent());
                Files.write(file, new byte[]{'1'});
            }
        } catch (IOException | RuntimeException ignored) {
            // without the marker the crash protection is just missing
        }
    }

    private static void delete(Path file) {
        try {
            if (file != null) {
                Files.deleteIfExists(file);
            }
        } catch (IOException | RuntimeException ignored) {
            // see touch()
        }
    }
}
