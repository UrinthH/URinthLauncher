package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.Tools;

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
        return new ModrinthLauncherView(requireContext(), spinner, () -> {
            if (net.kdt.pojavlaunch.progresskeeper.ProgressKeeper.getTaskCount() == 0) {
                mModInstallerLauncher.launch(null);
            }
        });
    }
}