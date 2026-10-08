package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.Tools;
import com.kdt.mcgui.AccountSpinner;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private final ActivityResultLauncher<Object> mModInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), data -> {
                if (data != null) Tools.launchModInstaller(requireContext(), data);
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        com.kdt.mcgui.mcVersionSpinner spinner = new com.kdt.mcgui.mcVersionSpinner(requireContext());
        spinner.setVisibility(View.INVISIBLE);

        FrameLayout root = new FrameLayout(requireContext());
        ModrinthLauncherView launcherView = new ModrinthLauncherView(
                requireActivity(), spinner, () -> {
                    if (net.kdt.pojavlaunch.progresskeeper.ProgressKeeper.getTaskCount() == 0) {
                        mModInstallerLauncher.launch(null);
                    }
                });

        AccountSpinner accountSpinner = new AccountSpinner(requireContext());
        accountSpinner.setAlpha(0f);
        FrameLayout.LayoutParams accountParams = new FrameLayout.LayoutParams(1, 1);
        accountParams.leftMargin = 1;
        accountParams.topMargin = 1;

        root.addView(launcherView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(accountSpinner, accountParams);
        return root;
    }
}