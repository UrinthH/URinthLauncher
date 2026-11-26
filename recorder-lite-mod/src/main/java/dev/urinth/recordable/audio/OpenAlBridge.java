package dev.urinth.recordable.audio;

import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.SOFTLoopback;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * The calls into OpenAL (through LWJGL, the same library the game uses) that the sound hook needs. Kept apart from {@link GameAudio} so
 * that class has no dependency on LWJGL. Every method is only called from the hook, which catches anything that goes wrong.
 *
 * <p>The three "render into memory" functions (the OpenAL loopback extension) are looked up by name and called through their address,
 * and nothing is called unless the address was found. LWJGL's own wrappers for them read the address from a table that is only filled
 * when LWJGL itself saw the extension, and calling an empty entry would end the whole game; this way the worst that can happen is that
 * the sound is not recorded.
 */
public final class OpenAlBridge {
    private static volatile long renderFunction;

    private OpenAlBridge() {
    }

    /** True if OpenAL can render into memory (the loopback extension). */
    public static boolean loopbackSupported() {
        try {
            return ALC10.alcIsExtensionPresent(0L, "ALC_SOFT_loopback");
        } catch (Throwable t) {
            return false;
        }
    }

    /** The address of an OpenAL function: from the library's lookup function first, from its symbol table second; 0 if there is none. */
    private static long address(String name) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer text = stack.ASCII(name);
            long a = ALC.getFunctionProvider().getFunctionAddress(0L, text);
            if (a == 0L) {
                a = ALC.getFunctionProvider().getFunctionAddress(text);
            }
            return a;
        }
    }

    /** Opens a loopback device that renders 48 kHz stereo 16-bit sound; 0 if that is not possible. */
    public static long openLoopbackDevice() {
        if (!loopbackSupported()) {
            return 0L;
        }
        long open = address("alcLoopbackOpenDeviceSOFT");
        long render = address("alcRenderSamplesSOFT");
        long supported = address("alcIsRenderFormatSupportedSOFT");
        if (open == 0L || render == 0L) {
            return 0L;
        }
        long dev = JNI.invokePP(0L, open);
        if (dev == 0L) {
            return 0L;
        }
        if (supported != 0L) {
            long answer = JNI.invokePPPPP(dev, (long) AudioTap.RATE, (long) SOFTLoopback.ALC_STEREO_SOFT, (long) SOFTLoopback.ALC_SHORT_SOFT, supported);
            if ((answer & 0xFFL) == 0L) {                       // an OpenAL boolean is one byte; the rest of the register is not defined
                ALC10.alcCloseDevice(dev);
                return 0L;
            }
        }
        renderFunction = render;
        return dev;
    }

    public static void closeDevice(long dev) {
        ALC10.alcCloseDevice(dev);
    }

    /** What the sound engine asks for with its own settings is not valid on a loopback device; this is: the format of the rendered sound. */
    public static long createLoopbackContext(long dev) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer attrs = stack.callocInt(7);
            attrs.put(SOFTLoopback.ALC_FORMAT_CHANNELS_SOFT).put(SOFTLoopback.ALC_STEREO_SOFT);
            attrs.put(SOFTLoopback.ALC_FORMAT_TYPE_SOFT).put(SOFTLoopback.ALC_SHORT_SOFT);
            attrs.put(ALC10.ALC_FREQUENCY).put(AudioTap.RATE);
            attrs.put(0);
            attrs.flip();
            return ALC10.alcCreateContext(dev, attrs);
        }
    }

    /** The renderer for the pump: asks the loopback device for sound. */
    public static AudioPump.Renderer renderer(long dev) {
        final long fn = renderFunction;
        if (fn == 0L) {
            throw new IllegalStateException("the render function of the sound library is not known");
        }
        return new AudioPump.Renderer() {
            @Override
            public boolean ready() {
                return ALC10.alcGetCurrentContext() != 0L;
            }

            @Override
            public void render(long ptr, int frames) {
                JNI.invokePPPP(dev, ptr, (long) frames, fn);                  // the number of frames is an int for the callee; a long holds it just as well
            }
        };
    }
}
