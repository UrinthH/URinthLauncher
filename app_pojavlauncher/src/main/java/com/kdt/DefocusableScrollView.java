package com.kdt;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.ScrollView;

/**
    Class allowing to ignore the focusing from an item such an EditText within it.
    Ignoring it will stop the scrollView from refocusing on the view
*/
public class DefocusableScrollView extends ScrollView {



    private boolean mKeepFocusing = false;


    public DefocusableScrollView(Context context) {
        super(context);
        init();
    }

    public DefocusableScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DefocusableScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public DefocusableScrollView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    /** Smooth scrolling: a softer fling (a bit lower speed cap, so it glides instead of racing) and no edge fade. */
    private void init() {
        setSmoothScrollingEnabled(true);
        setVerticalFadingEdgeEnabled(false);
    }

    @Override
    public void fling(int velocityY) {
        // Cap and soften the fling so long lists glide to a stop instead of overshooting.
        super.fling((int) (Math.signum(velocityY) * Math.min(Math.abs(velocityY) * 0.85f, 6500f)));
    }

    public void setKeepFocusing(boolean shouldKeepFocusing){
        mKeepFocusing = shouldKeepFocusing;
    }

    public boolean isKeepFocusing(){
        return mKeepFocusing;
    }

    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        if(!mKeepFocusing) return 0;
        return super.computeScrollDeltaToGetChildRectOnScreen(rect);
    }


}
