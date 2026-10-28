package net.kdt.pojavlaunch.customcontrols.mouse;

import android.view.MotionEvent;

public class PointerTracker {
    private boolean mColdStart = true;
    private int mTrackedPointerId;
    private int mPointerCount;
    private float mLastX, mLastY;
    private final float[] mMotionVector = new float[2];

    public void startTracking(MotionEvent motionEvent) {
        mColdStart = false;
        mTrackedPointerId = motionEvent.getPointerId(0);
        mPointerCount = motionEvent.getPointerCount();
        mLastX = motionEvent.getX();
        mLastY = motionEvent.getY();
    }

    public void cancelTracking() {
        mColdStart = true;
    }

    public int trackEvent(MotionEvent motionEvent) {
        int trackedPointerIndex = motionEvent.findPointerIndex(mTrackedPointerId);
        int pointerCount = motionEvent.getPointerCount();
        if(trackedPointerIndex == -1 || mPointerCount != pointerCount || mColdStart) {
            startTracking(motionEvent);
            trackedPointerIndex = 0;
        }
        float trackedX = motionEvent.getX(trackedPointerIndex);
        float trackedY = motionEvent.getY(trackedPointerIndex);
        mMotionVector[0] = trackedX - mLastX;
        mMotionVector[1] = trackedY - mLastY;
        mLastX = trackedX;
        mLastY = trackedY;
        return trackedPointerIndex;
    }

    /**
     * How many separate touch samples this move event carries: the ones Android batched since the
     * last event ("historical" samples) plus the current one. Android hands over touch moves once per
     * screen refresh, bundled together; reading only the last one makes the camera turn in
     * steps at the screen's rate, which looks jerky when the game draws faster than that.
     */
    public int stepCount(MotionEvent motionEvent) {
        return motionEvent.getHistorySize() + 1;
    }

    /**
     * Motion vector of one sample of a move event: {@code step} 0 is the oldest batched sample and
     * {@link #stepCount} - 1 is the current one. Call for each step in order. Gives the same total
     * movement as {@link #trackEvent}, only in smaller pieces.
     */
    public float[] trackStep(MotionEvent motionEvent, int step) {
        int history = motionEvent.getHistorySize();
        int trackedPointerIndex = motionEvent.findPointerIndex(mTrackedPointerId);
        int pointerCount = motionEvent.getPointerCount();
        if(trackedPointerIndex == -1 || mPointerCount != pointerCount || mColdStart) {
            // Start again from this very sample (not from the current one), so later steps of the
            // same event do not run backwards.
            mColdStart = false;
            mTrackedPointerId = motionEvent.getPointerId(0);
            mPointerCount = pointerCount;
            trackedPointerIndex = 0;
            mLastX = step < history ? motionEvent.getHistoricalX(0, step) : motionEvent.getX(0);
            mLastY = step < history ? motionEvent.getHistoricalY(0, step) : motionEvent.getY(0);
        }
        float trackedX = step < history ? motionEvent.getHistoricalX(trackedPointerIndex, step)
                : motionEvent.getX(trackedPointerIndex);
        float trackedY = step < history ? motionEvent.getHistoricalY(trackedPointerIndex, step)
                : motionEvent.getY(trackedPointerIndex);
        mMotionVector[0] = trackedX - mLastX;
        mMotionVector[1] = trackedY - mLastY;
        mLastX = trackedX;
        mLastY = trackedY;
        return mMotionVector;
    }

    public float[] getMotionVector() {
        return mMotionVector;
    }
}
