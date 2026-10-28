package net.kdt.pojavlaunch.game;

/**
 * The microphone mode used for voice chat, as chosen in Settings. Plain Java with no Android
 * classes, so it is covered by JVM unit tests.
 *
 * <ul>
 *   <li>{@link #COMMUNICATION}: echo cancelling, noise reduction and automatic gain, as in a phone
 *       call. Needed to talk through the phone's loudspeaker without echo. Some phones also treat
 *       a steady sound (humming) or a quiet voice as noise and cut it.</li>
 *   <li>{@link #RECOGNITION}: a lighter treatment, without echo cancelling on most phones.</li>
 *   <li>{@link #UNPROCESSED}: the microphone as it is, nothing removed or cut by the phone.
 *       Best with a headset.</li>
 * </ul>
 */
public final class VoiceChatMicMode {
    public static final String COMMUNICATION = "communication";
    public static final String RECOGNITION = "recognition";
    public static final String UNPROCESSED = "unprocessed";

    private VoiceChatMicMode() {}

    /** The mode for a stored setting; anything unknown means the default, communication. */
    public static String normalize(String stored) {
        if (RECOGNITION.equals(stored) || UNPROCESSED.equals(stored)) return stored;
        return COMMUNICATION;
    }

    /** OpenSL ES recording preset (SL_ANDROID_RECORDING_PRESET_*): voice communication 4, recognition 3, unprocessed 5. */
    public static int openSlPreset(String mode) {
        switch (normalize(mode)) {
            case RECOGNITION: return 3;
            case UNPROCESSED: return 5;
            default: return 4;
        }
    }

    /** AAudio input preset (AAUDIO_INPUT_PRESET_*): voice communication 7, voice recognition 6, unprocessed 9. */
    public static int aaudioPreset(String mode) {
        switch (normalize(mode)) {
            case RECOGNITION: return 6;
            case UNPROCESSED: return 9;
            default: return 7;
        }
    }

    /** Only the echo cancelling mode needs the phone in its call audio mode. */
    public static boolean usesCallMode(String mode) {
        return COMMUNICATION.equals(normalize(mode));
    }
}
