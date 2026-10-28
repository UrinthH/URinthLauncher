package net.kdt.pojavlaunch.game;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import net.kdt.pojavlaunch.Logger;

import java.io.File;
import java.util.Map;

/**
 * Cleaner voice chat when the phone's own speaker and microphone are used (no headset).
 *
 * <p>Without a headset the microphone hears the game and the other players coming out of the
 * speaker. With nothing to cancel that, it goes round in a loop: echo, a high whistle, and a
 * voice that cuts in and out. A headset fixes it only because the speaker is no longer in the air.
 * Android has the tools to cancel it (the phone's "voice call" audio mode, the voice
 * communication microphone setting with echo cancelling and noise reduction), but a game does not
 * use them by itself. This class turns them on, and only when all of these are true:
 * <ul>
 *   <li>the player left "Voice chat clean-up" on in Settings,</li>
 *   <li>a voice chat mod is in the game's mods folder,</li>
 *   <li>no headset, earphones or Bluetooth audio device is connected.</li>
 * </ul>
 * When a headset is plugged in, everything goes back to normal, because it already sounds right
 * there. Nothing in the game's own files is touched.
 *
 * <p>Not every phone applies echo cancelling in the same way, and what the voice chat mod uses
 * to record is up to the mod, so this raises the odds a lot but cannot promise a perfect result on
 * every phone. What it did is written to the game log with an {@code [Audio]} prefix.
 */
public final class VoiceChatAudio {
    private static final String TAG = "[Audio] ";
    /** AAUDIO_INPUT_PRESET_VOICE_COMMUNICATION: microphone tuned for calls, with echo cancelling. */
    private static final String INPUT_PRESET_VOICE_COMMUNICATION = "7";

    private static boolean sModPresent;
    private static Runnable sOnDetected;

    private final Context mContext;
    private final AudioManager mAudioManager;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mStarted;
    private boolean mActive;
    private int mSavedMode;
    private boolean mSavedSpeaker;
    private AudioDeviceCallback mCallback;

    public VoiceChatAudio(Context context) {
        mContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
    }

    /** Called before the game starts: look for the mod and remember the answer. */
    public static void detect(File gameDir, boolean enabled) {
        sModPresent = enabled && VoiceChatMods.isInstalled(gameDir);
        Logger.appendToLog(TAG + "voice chat clean-up " + (enabled ? "on" : "off")
                + ", voice chat mod " + (sModPresent ? "found" : "not found"));
        Runnable listener = sOnDetected;
        // The game screen is already showing by now, so tell it to start the audio set-up.
        if (sModPresent && listener != null) new Handler(Looper.getMainLooper()).post(listener);
    }

    /** The game screen asks to be told when a voice chat mod has been found. */
    public static void setOnDetected(Runnable listener) {
        sOnDetected = listener;
    }

    public static boolean isModPresent() {
        return sModPresent;
    }

    /** Environment for the game's audio libraries. Only when a voice chat mod is present. */
    public static void addEnvironment(Map<String, String> envMap) {
        if (!sModPresent) return;
        // For anything that records through SDL (the launcher's audio library): ask for the
        // microphone that is tuned for calls, so the phone's echo cancelling is applied.
        envMap.put("SDL_ANDROID_AAUDIO_INPUT_PRESET", INPUT_PRESET_VOICE_COMMUNICATION);
        // The mod's Linux-built Opus / noise remover / gain control libraries cannot load on
        // Android by themselves (they ask for libm.so.6); the launcher gives them that name.
        envMap.put("URINTH_GLIBC_SHIMS", "1");
    }

    /** Start watching for headsets and set the phone's audio up. Call from the game's onResume. */
    public void start() {
        if (!sModPresent || mAudioManager == null || mStarted) return;
        mStarted = true;
        if (Build.VERSION.SDK_INT >= 23) {
            mCallback = new AudioDeviceCallback() {
                @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) { refresh(); }
                @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) { refresh(); }
            };
            mAudioManager.registerAudioDeviceCallback(mCallback, mHandler);
        }
        refresh();
    }

    /** Put everything back as it was. Call from the game's onPause / onDestroy. */
    public void stop() {
        if (!mStarted) return;
        mStarted = false;
        if (Build.VERSION.SDK_INT >= 23 && mCallback != null) {
            mAudioManager.unregisterAudioDeviceCallback(mCallback);
            mCallback = null;
        }
        deactivate();
    }

    private void refresh() {
        if (!mStarted) return;
        boolean headset = headsetConnected();
        if (headset) {
            if (mActive) Logger.appendToLog(TAG + "headset connected, back to normal audio");
            deactivate();
        } else {
            activate();
        }
    }

    private void activate() {
        if (mActive) return;
        try {
            mSavedMode = mAudioManager.getMode();
            mSavedSpeaker = mAudioManager.isSpeakerphoneOn();
            // The call mode: the phone cancels echo and noise for the microphone.
            mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            // In call mode the sound would go to the earpiece; keep it on the loudspeaker.
            routeToSpeaker(true);
            mActive = true;
            Logger.appendToLog(TAG + "no headset: call mode on, loudspeaker route, echo cancelling asked for");
        } catch (Throwable t) {
            Logger.appendToLog(TAG + "could not set call mode: " + t);
        }
    }

    private void deactivate() {
        if (!mActive) return;
        mActive = false;
        try {
            routeToSpeaker(false);
            mAudioManager.setMode(mSavedMode);
            if (mSavedSpeaker) mAudioManager.setSpeakerphoneOn(true);
            Logger.appendToLog(TAG + "call mode off");
        } catch (Throwable t) {
            Logger.appendToLog(TAG + "could not restore audio mode: " + t);
        }
    }

    private void routeToSpeaker(boolean on) {
        if (Build.VERSION.SDK_INT >= 31) {
            if (on) {
                for (AudioDeviceInfo device : mAudioManager.getAvailableCommunicationDevices()) {
                    if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                        mAudioManager.setCommunicationDevice(device);
                        return;
                    }
                }
                mAudioManager.setSpeakerphoneOn(true);
            } else {
                mAudioManager.clearCommunicationDevice();
            }
        } else {
            mAudioManager.setSpeakerphoneOn(on);
        }
    }

    private boolean headsetConnected() {
        if (Build.VERSION.SDK_INT < 23) {
            return mAudioManager.isWiredHeadsetOn() || mAudioManager.isBluetoothA2dpOn()
                    || mAudioManager.isBluetoothScoOn();
        }
        for (AudioDeviceInfo device : mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (isHeadsetType(device.getType())) return true;
        }
        return false;
    }

    @androidx.annotation.RequiresApi(23)
    private static boolean isHeadsetType(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
            case AudioDeviceInfo.TYPE_HEARING_AID:
                return true;
            default:
                // These two types were added in later Android versions.
                if (Build.VERSION.SDK_INT >= 26 && type == AudioDeviceInfo.TYPE_USB_HEADSET) return true;
                return Build.VERSION.SDK_INT >= 31 && type == AudioDeviceInfo.TYPE_BLE_HEADSET;
        }
    }
}
