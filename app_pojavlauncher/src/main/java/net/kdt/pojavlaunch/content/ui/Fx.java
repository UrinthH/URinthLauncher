package net.kdt.pojavlaunch.content.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** The light, clean look of the installation screens, shared by the settings and the content manager. */
public final class Fx {
    public static final int BG = 0xFFF3F6FB;
    public static final int PANEL_TOP = 0xFFFFFFFF;
    public static final int PANEL_BOTTOM = 0xFFFFFFFF;
    public static final int EDGE = 0xFFE1E8EF;
    public static final int EDGE_SOFT = 0xFFEDF1F5;
    public static final int ACCENT = 0xFF1FC77A;
    public static final int ACCENT_DEEP = 0xFF14A765;
    public static final int ON_ACCENT = 0xFFFFFFFF;
    public static final int TEXT = 0xFF1B2733;
    public static final int MUTED = 0xFF6B7A87;
    public static final int DANGER = 0xFFE5484D;
    public static final int DANGER_FILL = 0xFFFFF0F1;

    private Fx() {}

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** A white rounded panel with a hairline edge. */
    public static GradientDrawable glass(Context context, float radiusDp, int edge) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{PANEL_TOP, PANEL_BOTTOM});
        drawable.setCornerRadius(dp(context, radiusDp));
        drawable.setStroke(Math.max(1, dp(context, 1)), edge);
        return drawable;
    }

    public static GradientDrawable fill(Context context, int color, float radiusDp, int edge) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        if (edge != 0) drawable.setStroke(Math.max(1, dp(context, 1)), edge);
        return drawable;
    }

    public static GradientDrawable accentGradient(Context context, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF1FC77A, 0xFF19B56C});
        drawable.setCornerRadius(dp(context, radiusDp));
                return drawable;
    }

    public static void ripple(View view, android.graphics.drawable.Drawable shape) {
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x261FC77A), shape, null));
        view.setClickable(true);
    }

    public static TextView text(Context context, CharSequence value, float sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        view.setIncludeFontPadding(false);
        return view;
    }

    public static ImageView icon(Context context, int resource, int tint, float sizeDp) {
        ImageView view = new ImageView(context);
        view.setImageResource(resource);
        view.setColorFilter(tint);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp)));
        return view;
    }

    public static LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    public static LinearLayout.LayoutParams lp(Context context, float widthDp, float heightDp) {
        return new LinearLayout.LayoutParams(widthDp < 0 ? (int) widthDp : dp(context, widthDp), heightDp < 0 ? (int) heightDp : dp(context, heightDp));
    }

    public static LinearLayout.LayoutParams margins(LinearLayout.LayoutParams params, Context context, float left, float top, float right, float bottom) {
        params.setMargins(dp(context, left), dp(context, top), dp(context, right), dp(context, bottom));
        return params;
    }

    public static final int PRIMARY = 0, GHOST = 1, DANGER_STYLE = 2;

    public static TextView button(Context context, CharSequence label, int style) {
        TextView button = text(context, label, 15, style == PRIMARY ? ON_ACCENT : style == DANGER_STYLE ? DANGER : TEXT, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(context, 18), dp(context, 11), dp(context, 18), dp(context, 11));
        ripple(button, style == PRIMARY ? accentGradient(context, 14)
                : style == DANGER_STYLE ? fill(context, DANGER_FILL, 14, 0xFFF3C4C8)
                : glass(context, 14, EDGE));
        return button;
    }

    /** A small round-cornered button holding only an icon. */
    public static FrameLayout iconButton(Context context, int iconRes, int tint, int edge, float sizeDp) {
        FrameLayout box = new FrameLayout(context);
        ripple(box, fill(context, 0xFFF6F8FB, 12, edge));
        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setColorFilter(tint);
        int pad = dp(context, 9);
        icon.setPadding(pad, pad, pad, pad);
        box.addView(icon, new FrameLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp)));
        return box;
    }

    /** A pill switch: green track with a light knob when on. */
    static final class Toggle extends FrameLayout {
        interface Listener {
            void changed(boolean checked);
        }

        private final View track, knob;
        private boolean checked;
        private Listener listener;

        Toggle(Context context, boolean checked) {
            super(context);
            this.checked = checked;
            track = new View(context);
            knob = new View(context);
            addView(track, new LayoutParams(dp(context, 46), dp(context, 26), Gravity.CENTER));
            addView(knob, new LayoutParams(dp(context, 20), dp(context, 20), Gravity.CENTER_VERTICAL | Gravity.START));
            setMinimumWidth(dp(context, 54));
            setMinimumHeight(dp(context, 40));
            setClickable(true);
            apply(false);
            setOnClickListener(v -> {
                setChecked(!this.checked, true);
                if (listener != null) listener.changed(this.checked);
            });
        }

        void setListener(Listener listener) {
            this.listener = listener;
        }

        void setChecked(boolean value, boolean animate) {
            checked = value;
            apply(animate);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(dp(getContext(), 54), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(dp(getContext(), 40), MeasureSpec.EXACTLY));
        }

        private void apply(boolean animate) {
            Context context = getContext();
            track.setBackground(fill(context, checked ? ACCENT : 0xFFDCE3EA, 13, 0));
            knob.setBackground(fill(context, 0xFFFFFFFF, 10, checked ? 0 : 0xFFC9D3DC));
            float offset = (dp(context, 54) - dp(context, 46)) / 2f + dp(context, 3) + (checked ? dp(context, 20) : 0);
            if (animate) knob.animate().translationX(offset).setDuration(260).setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0.9f, 0.2f, 1f)).start();
            else knob.setTranslationX(offset);
        }
    }

    /** A white rounded dialog around {@code content}. */
    public static Dialog dialog(Context context, View content) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll = new ScrollView(context);
        FrameLayout card = new FrameLayout(context);
        card.setBackground(glass(context, 22, EDGE));
        card.setPadding(dp(context, 20), dp(context, 18), dp(context, 20), dp(context, 18));
        card.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.addView(card, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(scroll);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = Math.min(dp(context, 520), Math.round(context.getResources().getDisplayMetrics().widthPixels * 0.9f));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
            window.setWindowAnimations(git.artdeell.mojo.R.style.UrinthDialogAnim);
            window.setDimAmount(0.32f);
        }
        return dialog;
    }
}
