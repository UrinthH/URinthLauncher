package net.kdt.pojavlaunch.game;

import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import net.kdt.pojavlaunch.customcontrols.mouse.LeftClickGesture;
import net.kdt.pojavlaunch.customcontrols.mouse.PointerTracker;
import net.kdt.pojavlaunch.customcontrols.mouse.RightClickGesture;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

public class InGameEventProcessor extends TouchEventProcessor {
    private final Handler mGestureHandler = new Handler(Looper.getMainLooper());
    private final double mSensitivity;
    private boolean mEventTransitioned = true;
    private final PointerTracker mTracker = new PointerTracker();
    private final LeftClickGesture mLeftClickGesture = new LeftClickGesture(mGestureHandler);
    private final RightClickGesture mRightClickGesture = new RightClickGesture(mGestureHandler);

    public InGameEventProcessor(GameView hostView, double sensitivity) {
        super(hostView);
        mSensitivity = sensitivity;
    }

    @Override
    public boolean processTouchEvent(MotionEvent motionEvent) {
        switch (motionEvent.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mTracker.startTracking(motionEvent);
                if(LauncherPreferences.PREF_DISABLE_GESTURES) break;
                mEventTransitioned = false;
                checkGestures();
                break;
            case MotionEvent.ACTION_MOVE:
                // Every touch sample Android batched into this event turns the camera by its own
                // small step, instead of one jump per screen refresh. The camera then follows the
                // finger at the touch panel's rate, which is what makes rotation feel smooth when
                // the game draws faster than the screen refreshes.
                int steps = mTracker.stepCount(motionEvent);
                float deltaX = 0f, deltaY = 0f;
                for(int step = 0; step < steps; step++) {
                    float[] motionVector = mTracker.trackStep(motionEvent, step);
                    float stepX = (float) (motionVector[0] * mSensitivity);
                    float stepY = (float) (motionVector[1] * mSensitivity);
                    deltaX += stepX;
                    deltaY += stepY;
                    // A step that did not move adds nothing; skip the call into the game for it.
                    if(steps == 1 || stepX != 0f || stepY != 0f) applyMoveVector(stepX, stepY);
                }
                mLeftClickGesture.setMotion(deltaX, deltaY);
                mRightClickGesture.setMotion(deltaX, deltaY);
                if(LauncherPreferences.PREF_DISABLE_GESTURES) break;
                checkGestures();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mTracker.cancelTracking();
                cancelGestures(false);
        }
        return true;
    }

    @Override
    public void cancelPendingActions() {
        cancelGestures(true);
    }

    private void checkGestures() {
        mLeftClickGesture.inputEvent();
        // Only register right click events if it's a fresh event stream, not one after a transition.
        // This is done to avoid problems when people hold the button for just a bit too long after
        // exiting a menu for example.
        if(!mEventTransitioned) mRightClickGesture.inputEvent();
    }

    private void cancelGestures(boolean isSwitching) {
        mEventTransitioned = true;
        mLeftClickGesture.cancel(isSwitching);
        mRightClickGesture.cancel(isSwitching);
    }
}
