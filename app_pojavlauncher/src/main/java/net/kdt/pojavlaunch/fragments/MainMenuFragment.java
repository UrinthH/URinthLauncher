package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.graphics.Color;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.Tools;
import com.kdt.mcgui.AccountSpinner;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";
    private ModrinthLauncherView launcherView;
    private FrameLayout centerContainer;
    public static final int CENTER_CONTAINER_ID = 0x00f0a11;

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
        AccountSpinner accountSpinner = new AccountSpinner(requireContext());
        accountSpinner.setAlpha(0f);
        FrameLayout.LayoutParams accountParams = new FrameLayout.LayoutParams(1, 1);
        accountParams.leftMargin = 1;
        accountParams.topMargin = 1;

        launcherView = new ModrinthLauncherView(
                requireActivity(), spinner, accountSpinner, () -> {
                    if (net.kdt.pojavlaunch.progresskeeper.ProgressKeeper.getTaskCount() == 0) {
                        mModInstallerLauncher.launch(null);
                    }
                });

        root.addView(launcherView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        centerContainer = new FrameLayout(requireContext());
        centerContainer.setId(CENTER_CONTAINER_ID);
        centerContainer.setBackgroundColor(Color.rgb(4, 21, 34));
        centerContainer.setVisibility(View.GONE);
        FrameLayout.LayoutParams centerParams = new FrameLayout.LayoutParams(1, 1);
        root.addView(centerContainer, centerParams);
        root.addView(accountSpinner, accountParams);

        root.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int width = v.getWidth();
            int height = v.getHeight();
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) centerContainer.getLayoutParams();
            params.leftMargin = Math.round(width * 228f / 1536f);
            params.topMargin = Math.round(height * 62f / 686f);
            params.width = Math.round(width * (1215f - 228f) / 1536f);
            params.height = Math.round(height * (686f - 62f) / 686f);
            centerContainer.setLayoutParams(params);
        });
        getChildFragmentManager().addOnBackStackChangedListener(() -> {
            if (centerContainer != null && getChildFragmentManager().getBackStackEntryCount() == 0) {
                centerContainer.setVisibility(View.GONE);
            }
        });
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        OnBackPressedCallback callback = new OnBackPressedCallback(isCenterContentVisible()) {
            @Override
            public void handleOnBackPressed() {
                handleCenterBack();
                setEnabled(isCenterContentVisible());
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), callback);
        getChildFragmentManager().addOnBackStackChangedListener(() -> callback.setEnabled(isCenterContentVisible()));
    }

    public void showAuthChooser() {
        if (launcherView != null) launcherView.showAuthChooser();
    }

    public boolean isCenterContentVisible() {
        return centerContainer != null && centerContainer.getVisibility() == View.VISIBLE;
    }

    public void showCenterFragment(Class<? extends Fragment> fragmentClass, String tag, Bundle bundle) {
        if (centerContainer == null) return;
        centerContainer.setVisibility(View.VISIBLE);
        getChildFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .addToBackStack(fragmentClass.getName())
                .replace(CENTER_CONTAINER_ID, fragmentClass, bundle, tag)
                .commit();
    }

    public void handleCenterBack() {
        if (!isCenterContentVisible()) return;
        if (getChildFragmentManager().getBackStackEntryCount() > 1) {
            getChildFragmentManager().popBackStack();
        } else {
            closeCenterContent();
        }
    }

    public boolean closeCenterContent() {
        if (!isCenterContentVisible()) return false;
        getChildFragmentManager().popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
        centerContainer.setVisibility(View.GONE);
        return true;
    }
}