package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;
import net.kdt.pojavlaunch.tasks.AsyncVersionList;
import net.kdt.pojavlaunch.utils.FileUtils;

import java.io.File;

import git.artdeell.mojo.R;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private static final int BG = Color.rgb(5, 20, 31);
    private static final int PANEL = Color.rgb(8, 30, 45);
    private static final int PANEL_2 = Color.rgb(10, 39, 58);
    private static final int TEXT = Color.rgb(240, 247, 250);
    private static final int MUTED = Color.rgb(160, 181, 193);
    private static final int ACCENT = Color.rgb(0, 219, 169);
    private static final int BLUE = Color.rgb(72, 188, 226);

    private final ActivityResultLauncher<Object> mModInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), data -> {
                if(data != null) Tools.launchModInstaller(requireContext(), data);
            });

    private mcVersionSpinner mVersionSpinner;

    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable android.view.ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context c = requireContext();
        mVersionSpinner = new mcVersionSpinner(c);
        mVersionSpinner.setVisibility(View.INVISIBLE);
        mVersionSpinner.setLayoutParams(new LinearLayout.LayoutParams(1, 1));

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(c);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(c, 16), 0, dp(c, 12), 0);
        header.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(c, 60)));

        ImageButton menu = iconButton(c, R.drawable.ic_px_gamepad, "Menu");
        header.addView(menu, new LinearLayout.LayoutParams(dp(c, 42), dp(c, 42)));

        ImageView logo = new ImageView(c);
        logo.setImageResource(R.drawable.ic_modrinth);
        logo.setPadding(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4));
        header.addView(logo, new LinearLayout.LayoutParams(dp(c, 46), dp(c, 46)));

        TextView title = text(c, "Modrinth", 23, TEXT, true);
        TextView launcher = text(c, "Launcher", 23, ACCENT, true);
        LinearLayout titleGroup = new LinearLayout(c);
        titleGroup.setGravity(Gravity.CENTER_VERTICAL);
        titleGroup.addView(title);
        titleGroup.addView(launcher);
        header.addView(titleGroup, new LinearLayout.LayoutParams(-2, -1));

        View divider = new View(c);
        divider.setBackgroundColor(Color.rgb(47, 93, 110));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(dp(c, 1), dp(c, 28));
        divLp.setMargins(dp(c, 14), 0, dp(c, 14), 0);
        header.addView(divider, divLp);

        header.addView(text(c, "Made By: Macase, Nile.", 13, ACCENT, true),
                new LinearLayout.LayoutParams(-2, -1));

        Space headerSpace = new Space(c);
        header.addView(headerSpace, new LinearLayout.LayoutParams(0, 1, 1));

        ImageButton settings = iconButton(c, R.drawable.ic_sharp_settings_24, "Settings");
        header.addView(settings, new LinearLayout.LayoutParams(dp(c, 42), dp(c, 42)));

        root.addView(header);

        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav = new LinearLayout(c);
        nav.setOrientation(LinearLayout.VERTICAL);
        nav.setPadding(dp(c, 12), dp(c, 8), dp(c, 8), dp(c, 10));
        nav.setBackgroundColor(Color.rgb(6, 24, 37));
        body.addView(nav, new LinearLayout.LayoutParams(dp(c, 188), -1));

        ScrollView navScroll = new ScrollView(c);
        LinearLayout navItems = new LinearLayout(c);
        navItems.setOrientation(LinearLayout.VERTICAL);
        String[] labels = {"Home", "Instances", "Mods", "Resource Packs", "Servers", "Settings", "Modpacks", "Shaders", "Worlds"};
        int[] icons = {R.drawable.ic_px_home, R.drawable.ic_px_folder, R.drawable.ic_px_gamepad, R.drawable.ic_px_folder,
                R.drawable.ic_px_folder, R.drawable.ic_sharp_settings_24, R.drawable.ic_px_folder, R.drawable.ic_px_book, R.drawable.ic_px_folder};

        for(int i = 0; i < labels.length; i++){
            final int index = i;
            Button b = navButton(c, labels[i], icons[i], i == 0);
            b.setOnClickListener(v -> onNavigation(index));
            navItems.addView(b);
            if(i == 5){
                View line = new View(c);
                line.setBackgroundColor(Color.rgb(26, 72, 87));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(c, 1));
                lp.setMargins(0, dp(c, 8), 0, dp(c, 8));
                navItems.addView(line, lp);
            }
        }

        LinearLayout brand = new LinearLayout(c);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setPadding(dp(c, 10), dp(c, 10), dp(c, 10), dp(c, 10));
        brand.setBackground(round(Color.rgb(7, 35, 47), ACCENT, 12));
        ImageView brandIcon = new ImageView(c);
        brandIcon.setImageResource(R.drawable.ic_modrinth);
        brand.addView(brandIcon, new LinearLayout.LayoutParams(dp(c, 38), dp(c, 38)));
        LinearLayout brandText = new LinearLayout(c);
        brandText.setOrientation(LinearLayout.VERTICAL);
        brandText.addView(text(c, "Modrinth", 14, TEXT, true));
        brandText.addView(text(c, "Better Minecraft", 11, MUTED, false));
        brandText.addView(text(c, "Together", 11, MUTED, false));
        brand.addView(brandText, new LinearLayout.LayoutParams(0, -2, 1));
        navItems.addView(brand, new LinearLayout.LayoutParams(-1, dp(c, 72)));

        navScroll.addView(navItems);
        nav.addView(navScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout center = new LinearLayout(c);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setPadding(dp(c, 12), dp(c, 8), dp(c, 8), dp(c, 8));

        ScrollView centerScroll = new ScrollView(c);
        LinearLayout centerContent = new LinearLayout(c);
        centerContent.setOrientation(LinearLayout.VERTICAL);
        centerScroll.addView(centerContent);

        TextView hero = text(c, "Play, Explore,\nCreate, Together.", 22, TEXT, true);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(c, 18), dp(c, 10), dp(c, 18), dp(c, 10));
        hero.setBackground(round(Color.rgb(20, 54, 68), BLUE, 14));
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(-1, dp(c, 128));
        heroLp.setMargins(0, 0, 0, dp(c, 10));
        centerContent.addView(hero, heroLp);

        TextView heroSub = text(c, "The best Minecraft experience,\nnow on your Android device.", 12, MUTED, false);
        heroSub.setPadding(dp(c, 20), dp(c, 0), dp(c, 20), dp(c, 10));
        heroSub.setTranslationY(dp(c, -66));
        centerContent.addView(heroSub, new LinearLayout.LayoutParams(-1, dp(c, 1)));

        TextView versionsTitle = text(c, "Your Instances", 19, TEXT, true);
        centerContent.addView(versionsTitle, new LinearLayout.LayoutParams(-1, dp(c, 34)));

        LinearLayout cards = new LinearLayout(c);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        for(int i=0;i<3;i++){
            LinearLayout card = versionCard(c, i);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(c, 150), 1);
            cp.setMargins(i == 0 ? 0 : dp(c, 5), 0, i == 2 ? 0 : dp(c, 5), 0);
            cards.addView(card, cp);
        }
        centerContent.addView(cards);

        LinearLayout modsHeader = new LinearLayout(c);
        modsHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView latest = text(c, "Latest Mods", 19, TEXT, true);
        modsHeader.addView(latest, new LinearLayout.LayoutParams(0, dp(c, 44), 1));
        Button viewAll = smallButton(c, "View All");
        viewAll.setOnClickListener(v -> Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG, null));
        modsHeader.addView(viewAll);
        centerContent.addView(modsHeader);

        HorizontalScrollView modScroll = new HorizontalScrollView(c);
        modScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout modRow = new LinearLayout(c);
        modRow.setOrientation(LinearLayout.HORIZONTAL);
        String[][] mods = {{"Sodium","NeoForge 1.21.1"},{"Lithium","Fabric 1.21.1"},{"Iris","Fabric 1.21.1"},{"Distant Horizons","Forge 1.21.1"},{"Xaero's Minimap","Forge 1.21.1"}};
        for(String[] m : mods){
            LinearLayout mc = modCard(c, m[0], m[1]);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(c, 180), dp(c, 92));
            mlp.setMargins(0, 0, dp(c, 6), 0);
            modRow.addView(mc, mlp);
        }
        modScroll.addView(modRow);
        centerContent.addView(modScroll, new LinearLayout.LayoutParams(-1, dp(c, 96)));

        center.addView(centerScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        body.addView(center, new LinearLayout.LayoutParams(0, -1, 1));

        LinearLayout right = new LinearLayout(c);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(c, 4), dp(c, 8), dp(c, 12), dp(c, 8));
        body.addView(right, new LinearLayout.LayoutParams(dp(c, 282), -1));

        LinearLayout account = panel(c);
        account.addView(text(c, "Add Account", 15, TEXT, true));
        account.addView(text(c, "Sign in to your Minecraft account", 11, MUTED, false));
        Button addAccount = smallButton(c, "＋  Add Account");
        addAccount.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true));
        account.addView(addAccount, new LinearLayout.LayoutParams(-1, dp(c, 40)));
        right.addView(account, new LinearLayout.LayoutParams(-1, dp(c, 100)));

        Button updates = panelButton(c, "⟳  Check Updates", "Check for new versions and fixes");
        updates.setOnClickListener(v -> {
            Toast.makeText(c, "Checking for updates…", Toast.LENGTH_SHORT).show();
            new AsyncVersionList().getVersionList(versions -> {
                ExtraCore.setValue(ExtraConstants.RELEASE_TABLE, versions);
                Tools.runOnUiThread(() -> Toast.makeText(c, "Update check finished.", Toast.LENGTH_SHORT).show());
            });
        });
        right.addView(updates, new LinearLayout.LayoutParams(-1, dp(c, 64)));

        LinearLayout ultra = panel(c);
        LinearLayout ultraTop = new LinearLayout(c);
        ultraTop.setGravity(Gravity.CENTER_VERTICAL);
        ultraTop.addView(text(c, "UrinthUltra Mode", 15, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        SwitchCompat toggle = new SwitchCompat(c);
        SharedPreferences prefs = c.getSharedPreferences("urinth_ui", Context.MODE_PRIVATE);
        toggle.setChecked(prefs.getBoolean("ultra", true));
        toggle.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean("ultra", isChecked).apply());
        ultraTop.addView(toggle, new LinearLayout.LayoutParams(dp(c, 54), dp(c, 42)));
        ultra.addView(ultraTop);
        ultra.addView(text(c, "Enable ultra performance mode", 11, MUTED, false));
        right.addView(ultra, new LinearLayout.LayoutParams(-1, dp(c, 82)));

        Button discord = new Button(c);
        discord.setAllCaps(false);
        discord.setGravity(Gravity.CENTER_VERTICAL);
        discord.setText("   Discord");
        discord.setTextSize(16);
        discord.setTextColor(TEXT);
        discord.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_discord, 0, 0, 0);
        discord.setCompoundDrawablePadding(dp(c, 16));
        discord.setPadding(dp(c, 14), 0, dp(c, 14), 0);
        discord.setBackground(round(Color.rgb(44, 64, 143), Color.rgb(92, 112, 255), 14));
        discord.setOnClickListener(v -> Tools.openURL(requireActivity(), getString(R.string.social_media_invite)));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, dp(c, 76));
        dlp.setMargins(0, dp(c, 8), 0, 0);
        right.addView(discord, dlp);

        Space fill = new Space(c);
        right.addView(fill, new LinearLayout.LayoutParams(-1, 0, 1));

        Button wiki = actionButton(c, "Wiki", R.drawable.ic_px_book);
        wiki.setOnClickListener(v -> Tools.openURL(requireActivity(), Tools.URL_HOME));
        Button controls = actionButton(c, "Custom Controls", R.drawable.ic_px_gamepad);
        controls.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        Button jar = actionButton(c, "Execute a .jar", R.drawable.ic_px_gamepad);
        jar.setOnClickListener(v -> runInstallerWithConfirmation());
        Button logs = actionButton(c, "Share Log File", R.drawable.ic_px_book);
        logs.setOnClickListener(v -> shareLog(requireContext()));
        Button directory = actionButton(c, "Open Game Directory", R.drawable.ic_px_folder);
        directory.setOnClickListener(v -> openGameDirectory(v.getContext()));

        root.addView(body);
        body.setOnTouchListener((v,e) -> false);

        settings.setOnClickListener(v -> Tools.swapFragment(requireActivity(), LauncherPreferenceFragment.class,
                net.kdt.pojavlaunch.LauncherActivity.SETTING_FRAGMENT_TAG, null));
        menu.setOnClickListener(v -> nav.setVisibility(nav.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));

        root.addView(mVersionSpinner, new LinearLayout.LayoutParams(1, 1));
        return root;
    }

    private void onNavigation(int index){
        switch(index){
            case 0: return;
            case 1:
                mVersionSpinner.openProfileEditor(requireActivity());
                return;
            case 5:
                Tools.swapFragment(requireActivity(), LauncherPreferenceFragment.class,
                        net.kdt.pojavlaunch.LauncherActivity.SETTING_FRAGMENT_TAG, null);
                return;
            case 2:
            case 6:
                Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG, null);
                return;
            default:
                Toast.makeText(requireContext(), "This section is ready for its launcher integration.", Toast.LENGTH_SHORT).show();
        }
    }

    private LinearLayout versionCard(Context c, int index){
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(c, 8), dp(c, 8), dp(c, 8), dp(c, 6));
        int[] fills = {Color.rgb(92, 39, 108), Color.rgb(40, 79, 115), Color.rgb(116, 67, 34)};
        card.setBackground(round(fills[index], ACCENT, 12));
        TextView scene = text(c, index == 0 ? "Cherry Grove" : index == 1 ? "Snowy Peaks" : "Badlands Sunset", 12, TEXT, true);
        scene.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        card.addView(scene, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bottom = new LinearLayout(c);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(c, "URinthH\nVannila 26.3.", 13, TEXT, true);
        bottom.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        Button play = smallButton(c, "▶ Play");
        play.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));
        bottom.addView(play);
        card.addView(bottom);
        return card;
    }

    private LinearLayout modCard(Context c, String name, String loader){
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(c, 8), dp(c, 7), dp(c, 8), dp(c, 5));
        card.setBackground(round(PANEL_2, Color.rgb(25, 102, 126), 12));
        card.addView(text(c, name, 12, TEXT, true));
        card.addView(text(c, loader, 10, MUTED, false), new LinearLayout.LayoutParams(-1, 0, 1));
        Button add = smallButton(c, "Add");
        add.setOnClickListener(v -> runInstallerWithConfirmation());
        card.addView(add, new LinearLayout.LayoutParams(-2, dp(c, 32)));
        return card;
    }

    private LinearLayout panel(Context c){
        LinearLayout p = new LinearLayout(c);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
        p.setBackground(round(PANEL, Color.rgb(22, 104, 123), 14));
        return p;
    }

    private Button panelButton(Context c, String title, String subtitle){
        Button b = new Button(c);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setText(title + "\n" + subtitle + "                         ›");
        b.setTextSize(12);
        b.setTextColor(TEXT);
        b.setPadding(dp(c, 14), 0, dp(c, 8), 0);
        b.setBackground(round(PANEL, Color.rgb(22, 104, 123), 14));
        return b;
    }

    private Button navButton(Context c, String label, int icon, boolean selected){
        Button b = new Button(c);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(13);
        b.setTextColor(selected ? ACCENT : TEXT);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
        b.setCompoundDrawablePadding(dp(c, 12));
        b.setPadding(dp(c, 10), 0, dp(c, 4), 0);
        b.setBackground(round(selected ? Color.rgb(10, 91, 78) : Color.TRANSPARENT,
                selected ? ACCENT : Color.TRANSPARENT, 12));
        return b;
    }

    private Button actionButton(Context c, String label, int icon){
        Button b = navButton(c, label, icon, false);
        b.setTextColor(TEXT);
        b.setBackground(round(Color.rgb(8, 31, 43), Color.rgb(23, 71, 85), 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(c, 42));
        b.setLayoutParams(lp);
        return b;
    }

    private Button smallButton(Context c, String label){
        Button b = new Button(c);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(11);
        b.setTextColor(Color.rgb(2, 28, 31));
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 10), 0, dp(c, 10), 0);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setBackground(round(ACCENT, ACCENT, 20));
        return b;
    }

    private ImageButton iconButton(Context c, int icon, String description){
        ImageButton b = new ImageButton(c);
        b.setImageResource(icon);
        b.setContentDescription(description);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setPadding(dp(c, 8), dp(c, 8), dp(c, 8), dp(c, 8));
        return b;
    }

    private TextView text(Context c, String value, float size, int color, boolean bold){
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if(bold) t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return t;
    }

    private View space(Context c, int height){
        Space s = new Space(c);
        s.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, height)));
        return s;
    }

    private GradientDrawable round(int fill, int stroke, int radius){
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        if(stroke != Color.TRANSPARENT) g.setStroke(dp(requireContext(), 1), stroke);
        g.setCornerRadius(dp(requireContext(), radius));
        return g;
    }

    private int dp(Context c, int value){
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    private void runInstallerWithConfirmation(){
        if (net.kdt.pojavlaunch.progresskeeper.ProgressKeeper.getTaskCount() == 0) {
            mModInstallerLauncher.launch(null);
        } else {
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
        }
    }

    private void openGameDirectory(Context context){
        Instance instance = Instances.loadSelectedInstance();
        if(instance == null){
            Toast.makeText(context, R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        File gameDirectory = instance.getGameDirectory();
        if(FileUtils.ensureDirectorySilently(gameDirectory)){
            openPath(context, gameDirectory, false);
        }else{
            Toast.makeText(context, R.string.gamedir_open_failed, Toast.LENGTH_LONG).show();
        }
    }
}
