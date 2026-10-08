package net.kdt.pojavlaunch.fragments;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.modloaders.ForgelikeUtils;

/**
 * NeoForge version picker, rendered inside the persistent Modrinth center panel.
 */
public class NeoForgeInstallFragment extends ForgelikeInstallFragment {
    public static final String TAG = "NeoForgeInstallFragment";

    public NeoForgeInstallFragment() {
        super(ForgelikeUtils.NEOFORGE_UTILS, TAG);
    }

    @Override
    public int getTitleText() {
        return R.string.forge_dl_select_version;
    }

    @Override
    public int getNoDataMsg() {
        return R.string.forge_dl_no_installer;
    }
}
