package net.kdt.pojavlaunch.lifecycle;

import static net.kdt.pojavlaunch.game.GameActivity.INTENT_LAUNCH_CLASSPATH;
import static net.kdt.pojavlaunch.game.GameActivity.INTENT_LAUNCH_VERSION;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import net.kdt.pojavlaunch.game.GameActivity;
import net.kdt.pojavlaunch.game.LaunchTransition;
import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.tasks.MoJsonExtras;
import net.kdt.pojavlaunch.utils.NotificationUtils;

import java.io.File;

public class ContextAwareDoneListener implements MoJsonExtras.DoneListener, ContextExecutorTask {
    private final String mErrorString;
    private final String mNormalizedVersionid;
    private final long mTapTimeMs;
    private File[] classpath;

    public ContextAwareDoneListener(Context baseContext, String versionId) {
        this.mErrorString = baseContext.getString(R.string.mc_download_failed);
        this.mNormalizedVersionid = versionId;
        // Created the moment the player taps Play. The game process measures its start-up from here.
        this.mTapTimeMs = System.currentTimeMillis();
    }

    private Intent createGameStartIntent(Context context) {
        Intent mainIntent = new Intent(context, GameActivity.class);
        mainIntent.putExtra(GameActivity.INTENT_LAUNCH_TAP_MS, mTapTimeMs);
        mainIntent.putExtra(INTENT_LAUNCH_VERSION, mNormalizedVersionid);
        mainIntent.putExtra(INTENT_LAUNCH_CLASSPATH, classpath);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return mainIntent;
    }

    @Override
    public void onDownloadDone(File[] classpath) {
        this.classpath = classpath;
        ProgressKeeper.waitUntilDone(()->ContextExecutor.execute(this));
    }

    @Override
    public void onDownloadFailed(Throwable throwable) {
        // The Play animation is over the launcher; take it away so the error can be seen.
        LaunchTransition.cancel();
        Tools.showErrorRemote(mErrorString, throwable);
    }

    @Override
    public void executeWithActivity(Activity activity) {
        // If the Play animation is still zooming in, let it land before the game takes over.
        LaunchTransition.runAfterZoom(() -> startGame(activity));
    }

    private void startGame(Activity activity) {
        try {
            Intent gameStartIntent = createGameStartIntent(activity);
            activity.startActivity(gameStartIntent);
            activity.finish();
            android.os.Process.killProcess(android.os.Process.myPid()); //You should kill yourself, NOW!
        } catch (Throwable e) {
            Tools.showError(activity.getBaseContext(), e);
        }
    }

    @Override
    public void executeWithApplication(Context context) {
        Intent gameStartIntent = createGameStartIntent(context);
        // Since the game is a separate process anyway, it does not matter if it gets invoked
        // from somewhere other than the launcher activity.
        // The only problem may arise if the launcher starts doing something when the user starts the notification.
        // So, the notification is automatically removed once there are tasks ongoing in the ProgressKeeper
        NotificationUtils.sendBasicNotification(context,
                R.string.notif_download_finished,
                R.string.notif_download_finished_desc,
                gameStartIntent,
                NotificationUtils.PENDINGINTENT_CODE_GAME_START,
                NotificationUtils.NOTIFICATION_ID_GAME_START
        );
        // You should keep yourself safe, NOW!
        // otherwise android does weird things...
    }
}
