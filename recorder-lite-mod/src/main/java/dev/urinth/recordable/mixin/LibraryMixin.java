package dev.urinth.recordable.mixin;

import com.mojang.blaze3d.audio.Library;
import dev.urinth.recordable.audio.GameAudio;
import dev.urinth.recordable.audio.OpenAlBridge;
import org.lwjgl.openal.ALC10;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.IntBuffer;

/**
 * Lets the recorder hear the game's sound. The game's sound engine ({@link Library}) opens the phone's audio device itself; here it is
 * given an OpenAL "loopback" device instead (one that renders into memory), and the mod plays that sound through the phone's speaker
 * and keeps a copy for the recording. This is the same approach the PC version of Record-able uses.
 *
 * <p>Everything is guarded: when the sound is switched off, or anything fails, the game gets exactly what it would have got without
 * this class. The hooks are not required, so a game version they do not fit simply runs without sound recording.
 */
@Mixin(Library.class)
public abstract class LibraryMixin {
    @Shadow
    private static long openDeviceOrFallback(String deviceName) {
        throw new AssertionError();
    }

    /** The sound engine asks for its device. */
    @Redirect(method = "init", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/audio/Library;openDeviceOrFallback(Ljava/lang/String;)J"))
    private static long recordableLite$openDevice(String deviceName) {
        try {
            if (GameAudio.wantsLoopback()) {
                long dev = OpenAlBridge.openLoopbackDevice();
                if (dev != 0L) {
                    if (GameAudio.deviceOpened(dev)) {
                        return dev;
                    }
                    OpenAlBridge.closeDevice(dev);
                } else {
                    GameAudio.failed("the sound library cannot render into memory (no loopback device)", null);
                }
            }
        } catch (Throwable t) {
            GameAudio.failed("the sound hook failed", t);
        }
        return openDeviceOrFallback(deviceName);
    }

    /** A loopback device needs to be told the format of the sound when its context is created. */
    @Redirect(method = "init", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/openal/ALC10;alcCreateContext(JLjava/nio/IntBuffer;)J", remap = false))
    private long recordableLite$createContext(long device, IntBuffer attributes) {
        if (GameAudio.isLoopbackDevice(device)) {
            try {
                return OpenAlBridge.createLoopbackContext(device);
            } catch (Throwable t) {
                GameAudio.failed("the sound context could not be created", t);
            }
        }
        return ALC10.alcCreateContext(device, attributes);
    }

    /** Directional audio (HRTF) is a setting of a real device and does not apply to a loopback device. */
    @Inject(method = "setHrtf", at = @At("HEAD"), cancellable = true)
    private void recordableLite$skipHrtf(boolean enabled, CallbackInfo ci) {
        if (GameAudio.loopbackDevice()) {
            ci.cancel();
        }
    }

    /** The sound engine is ready: start playing and offering the sound. */
    @Inject(method = "init", at = @At("TAIL"))
    private void recordableLite$afterInit(String deviceName, boolean hrtf, CallbackInfo ci) {
        try {
            if (GameAudio.loopbackDevice()) {
                GameAudio.startPump(OpenAlBridge.renderer(currentDeviceForHook()));
            }
        } catch (Throwable t) {
            GameAudio.failed("the sound thread could not start", t);
        }
    }

    /** The sound engine is going away: the thread that uses its device has to be gone before it closes. */
    @Inject(method = "cleanup", at = @At("HEAD"))
    private void recordableLite$beforeCleanup(CallbackInfo ci) {
        try {
            GameAudio.stopPump();
        } catch (Throwable t) {
            GameAudio.failed("the sound thread could not be stopped", t);
        }
    }

    @Shadow
    private long currentDevice;

    private long currentDeviceForHook() {
        return this.currentDevice;
    }
}
