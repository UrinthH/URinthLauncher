package net.kdt.pojavlaunch.game;

import android.app.GameManager;
import android.app.GameState;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import net.kdt.pojavlaunch.Logger;

/**
 * Tells Android what the game is doing: still loading, or being played (Android 13 and newer).
 *
 * The system can only help if it knows. While a game says it is loading, phones that support it
 * give the CPU and storage a boost, so the long start-up of a modded Minecraft (Java start,
 * mod loading, resource reload) gets shorter; once it says the game is being played, that boost
 * ends and the system can treat the app as a game in play. On Android 12 and older, and on phones
 * whose system ignores the hint, this does nothing. It never changes anything in the game or in
 * the launcher's settings, and any failure is swallowed: the game starts either way.
 */
public final class GameStateSignal {
    private static final String TAG = "GameStateSignal";

    private GameStateSignal() {}

    /** The game is starting up: boost loading where the phone allows it. */
    public static void loading(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) send(context, true, false);
    }

    /** Start-up is over and the game is being played. */
    public static void playing(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) send(context, false, true);
    }

    /** The game screen is closing: nothing is being loaded or played any more. */
    public static void stopped(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) send(context, false, false);
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static void send(Context context, boolean isLoading, boolean isPlaying) {
        try {
            GameManager manager = context.getSystemService(GameManager.class);
            if (manager == null) return;
            int mode = isPlaying ? GameState.MODE_GAMEPLAY_INTERRUPTIBLE : GameState.MODE_NONE;
            manager.setGameState(new GameState(isLoading, mode));
            Logger.appendToLog("[GameState] " + (isLoading ? "loading" : isPlaying ? "playing" : "stopped"));
        } catch (Throwable error) {
            // A phone whose system rejects the call, or a logger that is not ready yet: ignore.
            Log.w(TAG, "Could not tell the system the game state", error);
        }
    }
}
