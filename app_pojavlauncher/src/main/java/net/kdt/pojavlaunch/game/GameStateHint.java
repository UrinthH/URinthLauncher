package net.kdt.pojavlaunch.game;

import android.app.GameManager;
import android.app.GameState;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import net.kdt.pojavlaunch.Logger;

/**
 * Tells Android what the game is doing, through the Game Mode API of Android 13 ({@code GameManager.setGameState}):
 * first "loading" while Java and Minecraft start, then "playing" once the game window is up, and "not playing"
 * when the game screen goes away. The system (and, on phones that have one, the maker's game booster) may use
 * that to give the game the right resources while it loads and while it is played, and to leave it alone
 * otherwise.
 *
 * It is a hint and nothing more: this class asks for no resources itself, and whether anything changes
 * depends on the phone maker's software. Where the call does not exist (before Android 13), or the system
 * refuses it, nothing happens and nothing is logged as an error. Only used while Performance Mode is on and
 * "Tell Android the game is playing" is on, on the Advanced settings screen.
 */
public final class GameStateHint {
    private static final String TAG = "GameStateHint";

    private static final int PHASE_LOADING = 0;
    private static final int PHASE_PLAYING = 1;
    private static final int PHASE_ENDED = 2;

    private static boolean sActive;

    private GameStateHint() {}

    /** True when this Android version has the call at all (Android 13 and newer). */
    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
    }

    /** The game process is starting: the game is loading. Call from the main thread. */
    public static void loading(Context context) {
        send(context, PHASE_LOADING, "loading");
    }

    /** The game window is up: the player is in the game. Call from the main thread. */
    public static void playing(Context context) {
        send(context, PHASE_PLAYING, "playing");
    }

    /** The game screen is going away. Does nothing unless a state was sent before. */
    public static void ended(Context context) {
        if (!sActive) return;
        send(context, PHASE_ENDED, "not playing");
        sActive = false;
    }

    private static void send(Context context, int phase, String label) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        try {
            if (Api33.set(context, phase)) {
                sActive = phase != PHASE_ENDED;
                log("told Android the game is " + label);
            }
        } catch (Throwable error) {
            // A hint: the system may refuse it, and the game does not care.
            Log.w(TAG, "Could not set the game state", error);
        }
    }

    private static void log(String message) {
        Log.i(TAG, message);
        try {
            Logger.appendToLog("[GameState] " + message);
        } catch (Throwable ignored) {
            // The native logger is not available yet, or already gone. The Android log has it.
        }
    }

    /** Kept apart so the classes of Android 13 are only touched on Android 13. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static final class Api33 {
        private Api33() {}

        static boolean set(@NonNull Context context, int phase) {
            GameManager manager = context.getSystemService(GameManager.class);
            if (manager == null) return false;
            switch (phase) {
                case PHASE_LOADING:
                    manager.setGameState(new GameState(true, GameState.MODE_NONE));
                    break;
                case PHASE_PLAYING:
                    manager.setGameState(new GameState(false, GameState.MODE_GAMEPLAY_UNINTERRUPTIBLE));
                    break;
                default:
                    manager.setGameState(new GameState(false, GameState.MODE_NONE));
                    break;
            }
            return true;
        }
    }
}
