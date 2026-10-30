package net.kdt.pojavlaunch.downloads.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Colours and small view builders for the Downloads screens, in Modrinth's light look. */
final class Mr {
    private Mr() {}

    static final int PAGE = 0xFFEBEBEB;
    static final int CARD = 0xFFF7F7F7;
    static final int WHITE = 0xFFFFFFFF;
    static final int BRAND = 0xFF00AF5C;
    static final int BRAND_SOFT = 0xFFB7E4CD;
    static final int TEXT = 0xFF2C2E31;
    static final int TEXT_SECOND = 0xFF5A5D63;
    static final int TEXT_MUTED = 0xFF7E8187;
    static final int LINE = 0xFFDDDDDD;
    static final int DANGER = 0xFFC62828;

    static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics()));
    }

    static GradientDrawable round(Context context, int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    static GradientDrawable roundStroke(Context context, int color, float radiusDp, int strokeColor, float strokeDp) {
        GradientDrawable drawable = round(context, color, radiusDp);
        drawable.setStroke(Math.max(1, dp(context, strokeDp)), strokeColor);
        return drawable;
    }

    /** Sets a rounded background with a touch ripple. */
    static void background(View view, int color, float radiusDp) {
        Context context = view.getContext();
        GradientDrawable shape = round(context, color, radiusDp);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), shape, round(context, Color.WHITE, radiusDp)));
    }

    static TextView text(Context context, CharSequence value, float sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        view.setIncludeFontPadding(false);
        return view;
    }

    static ImageView icon(Context context, int resource, int tint, float sizeDp) {
        ImageView view = new ImageView(context);
        view.setImageResource(resource);
        view.setColorFilter(tint);
        int size = dp(context, sizeDp);
        view.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return view;
    }

    static LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    static LinearLayout.LayoutParams lp(Context context, float widthDp, float heightDp) {
        return new LinearLayout.LayoutParams(widthDp < 0 ? (int) widthDp : dp(context, widthDp), heightDp < 0 ? (int) heightDp : dp(context, heightDp));
    }

    static LinearLayout.LayoutParams margins(LinearLayout.LayoutParams params, Context context, float left, float top, float right, float bottom) {
        params.setMargins(dp(context, left), dp(context, top), dp(context, right), dp(context, bottom));
        return params;
    }

    /** A white rounded button-like row: optional label, then a chevron. For Sort / View / pickers. */
    static LinearLayout pillButton(Context context, CharSequence label, boolean chevron) {
        LinearLayout pill = new LinearLayout(context);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(context, 16), 0, dp(context, 14), 0);
        background(pill, WHITE, 24);
        pill.setElevation(dp(context, 2));
        TextView title = text(context, label, 15, TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        pill.addView(title, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (chevron) {
            ImageView arrow = icon(context, git.artdeell.mojo.R.drawable.ic_dl_chevron_down, TEXT_SECOND, 18);
            pill.addView(arrow, margins(lp(context, 18, 18), context, 8, 0, 0, 0));
        }
        pill.setClickable(true);
        return pill;
    }

    /** The label of a {@link #pillButton}. */
    static TextView pillLabel(LinearLayout pill) {
        return (TextView) pill.getChildAt(0);
    }

    static TextView chip(Context context, CharSequence label, boolean selected) {
        TextView chip = text(context, label, 14, selected ? Color.WHITE : TEXT, true);
        chip.setPadding(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 8));
        background(chip, selected ? BRAND : WHITE, 18);
        chip.setClickable(true);
        return chip;
    }

    /** A green or white button with centred text. */
    static TextView button(Context context, CharSequence label, boolean primary) {
        TextView button = text(context, label, 15, primary ? Color.WHITE : TEXT, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(context, 20), dp(context, 12), dp(context, 20), dp(context, 12));
        background(button, primary ? BRAND : WHITE, 22);
        if (!primary) button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), roundStroke(context, WHITE, 22, LINE, 1), null));
        button.setClickable(true);
        return button;
    }

    interface Pick {
        void picked(int index);
    }

    /** A light drop-down list under {@code anchor}. */
    static PopupWindow menu(View anchor, List<String> items, int selected, final Pick onPick) {
        final Context context = anchor.getContext();
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(context, 6), 0, dp(context, 6));
        ScrollView scroll = new ScrollView(context);
        scroll.addView(list);
        FrameLayout frame = new FrameLayout(context);
        frame.setBackground(roundStroke(context, WHITE, 14, LINE, 1));
        frame.addView(scroll);
        frame.setElevation(dp(context, 8));
        int maxHeight = Math.round(context.getResources().getDisplayMetrics().heightPixels * 0.6f);
        int width = Math.max(anchor.getWidth(), dp(context, 180));
        final PopupWindow popup = new PopupWindow(frame, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(context, 8));
        for (int i = 0; i < items.size(); i++) {
            final int index = i;
            TextView row = text(context, items.get(i), 15, i == selected ? BRAND : TEXT, i == selected);
            row.setPadding(dp(context, 18), dp(context, 12), dp(context, 18), dp(context, 12));
            row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), null, new ColorDrawable(Color.WHITE)));
            row.setClickable(true);
            row.setOnClickListener(v -> {
                popup.dismiss();
                onPick.picked(index);
            });
            list.addView(row, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        list.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        popup.setHeight(Math.min(maxHeight, list.getMeasuredHeight() + dp(context, 2)));
        popup.showAsDropDown(anchor, 0, dp(context, 4));
        return popup;
    }

    /** A rounded white dialog around {@code content}, no title bar. */
    static Dialog dialog(Context context, View content) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        FrameLayout card = new FrameLayout(context);
        card.setBackground(round(context, PAGE, 22));
        card.setPadding(dp(context, 20), dp(context, 18), dp(context, 20), dp(context, 18));
        card.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.addView(card, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(scroll);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = Math.min(dp(context, 560), Math.round(context.getResources().getDisplayMetrics().widthPixels * 0.92f));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }
        return dialog;
    }
}
