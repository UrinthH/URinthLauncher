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

import git.artdeell.mojo.R;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    /**
     * true: the UrinthUltra home screen built from standard views (view_urinth_home.xml).
     * false: the previous hand-drawn home screen (ModrinthLauncherView). Set this to false to go back.
     */
    static final boolean USE_NEW_HOME = true;

    private ModrinthLauncherView launcherView;
    private UrinthHomeController homeController;
    private FrameLayout centerContainer;
    public static final int CENTER_CONTAINER_ID = 0x00f0a11;

    private final ActivityResultLauncher<Object> mModInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), data -> {
                if (data != null) Tools.launchModInstaller(requireContext(), data);
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        FrameLayout root = new FrameLayout(requireContext());
        AccountSpinner accountSpinner = new AccountSpinner(requireContext());
        accountSpinner.setAlpha(0f);
        FrameLayout.LayoutParams accountParams = new FrameLayout.LayoutParams(1, 1);
        accountParams.leftMargin = 1;
        accountParams.topMargin = 1;

        if (USE_NEW_HOME) {
            View home = inflater.inflate(R.layout.view_urinth_home, root, false);
            root.addView(home, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            // The centre host in the layout is where Settings, the editor and the browsers open.
            centerContainer = home.findViewById(R.id.urinth_center_container);
            centerContainer.setId(CENTER_CONTAINER_ID);
            root.addView(accountSpinner, accountParams);
            homeController = new UrinthHomeController(requireActivity(), this, home);
            addBackStackListener();
            return root;
        }

        com.kdt.mcgui.mcVersionSpinner spinner = new com.kdt.mcgui.mcVersionSpinner(requireContext());
        spinner.setVisibility(View.INVISIBLE);

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
        addBackStackListener();
        return root;
    }

    private void addBackStackListener() {
        getChildFragmentManager().addOnBackStackChangedListener(() -> {
            if (centerContainer != null && getChildFragmentManager().getBackStackEntryCount() == 0) {
                centerContainer.animate().cancel();
                centerContainer.setVisibility(View.GONE);
                centerContainer.setAlpha(1f);
                centerContainer.setTranslationY(0f);
                if (launcherView != null) launcherView.onCenterContentClosed();
                // Returning to the home screen from the editor, the installer or anywhere else
                // that could have added/removed/renamed an installation. onResume() alone isn't
                // enough here: closing a screen that lives inside this fragment's own child
                // FragmentManager doesn't trigger this fragment's onResume, so without this the
                // "Your Installations" card only caught up the next time the whole app resumed.
                if (homeController != null) {
                    homeController.refresh();
                    // Whatever screen was open (editor, installer, settings...) is gone now, so
                    // the sidebar highlight should land back on Home instead of staying stuck on
                    // whichever category opened it.
                    homeController.markHomeActive();
                }
            }
        });
    }

    /** Lets a child screen (e.g. the installation creator) ask the home card to catch up right away. */
    public void notifyInstancesChanged() {
        if (homeController != null) homeController.refresh();
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

    @Override
    public void onResume() {
        super.onResume();
        // Installations, accounts and activity can change while a game or a screen was open.
        if (homeController != null) {
            homeController.refresh();
            homeController.resumeMusic();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (homeController != null) homeController.pauseMusic();
    }

    @Override
    public void onDestroyView() {
        if (homeController != null) {
            homeController.release();
            homeController = null;
        }
        super.onDestroyView();
    }

    public void showAuthChooser() {
        if (launcherView != null) launcherView.showAuthChooser();
        else if (homeController != null) homeController.showAccountPicker();
    }

    public boolean isCenterContentVisible() {
        return centerContainer != null && centerContainer.getVisibility() == View.VISIBLE;
    }

    public void showCenterFragment(Class<? extends Fragment> fragmentClass, String tag, Bundle bundle) {
        if (centerContainer == null) return;

        // Animate the actual content surface, not the launcher canvas behind it.
        // This runs for first opens and for every category switch.
        centerContainer.animate().cancel();
        centerContainer.setVisibility(View.VISIBLE);
        centerContainer.setAlpha(0.72f);
        centerContainer.setTranslationY(dp(30));
        centerContainer.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(280L)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();

        getChildFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .addToBackStack(fragmentClass.getName())
                .replace(CENTER_CONTAINER_ID, fragmentClass, bundle, tag)
                .commit();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
