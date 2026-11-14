package net.kdt.pojavlaunch.game;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.prefs.screens.SettingsStyle;

/**
 * The rows of the in-game side menu: white rows with an icon in a tinted chip, in the accent colour
 * chosen under Settings > Appearance. A row can be marked as dangerous (Force close is drawn red).
 * Only how the rows look changes; the menu items and what they do stay as they were.
 */
public final class GameMenuAdapter extends ArrayAdapter<String> {
    private static final int TEXT = 0xFF101828;
    private static final int DANGER = 0xFFD92D20;

    private final LayoutInflater mInflater;
    private final int[] mIcons;
    private final int mDangerPosition;
    private final int mAccent;

    /**
     * @param icons one drawable per label (0 for none), or null for a menu without icons
     * @param dangerPosition the row to draw in red, or -1 for none
     */
    public GameMenuAdapter(@NonNull Context context, @NonNull String[] labels,
                           @Nullable @DrawableRes int[] icons, int dangerPosition) {
        super(context, R.layout.item_game_menu, R.id.game_menu_text, labels);
        mInflater = LayoutInflater.from(context);
        mIcons = icons;
        mDangerPosition = dangerPosition;
        mAccent = SettingsStyle.accentColor(LauncherPreferences.DEFAULT_PREF);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View row = convertView != null ? convertView : mInflater.inflate(R.layout.item_game_menu, parent, false);
        TextView text = row.findViewById(R.id.game_menu_text);
        ImageView icon = row.findViewById(R.id.game_menu_icon);
        boolean danger = position == mDangerPosition;
        int tint = danger ? DANGER : mAccent;

        text.setText(getItem(position));
        text.setTextColor(danger ? DANGER : TEXT);

        int resource = mIcons != null && position < mIcons.length ? mIcons[position] : 0;
        if (resource == 0) {
            icon.setVisibility(View.GONE);
        } else {
            float density = row.getResources().getDisplayMetrics().density;
            GradientDrawable chip = new GradientDrawable();
            chip.setColor((tint & 0x00FFFFFF) | 0x1A000000);
            chip.setCornerRadius(10f * density);
            icon.setVisibility(View.VISIBLE);
            icon.setImageResource(resource);
            icon.setImageTintList(ColorStateList.valueOf(tint));
            icon.setBackground(chip);
        }
        return row;
    }
}
