package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.content.ui.Fx;
import net.kdt.pojavlaunch.content.ui.InstallationChrome;
import net.kdt.pojavlaunch.content.ui.Motion;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.game.renderer.RendererCache;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.RTSpinnerAdapter;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.instances.InstanceIconProvider;
import net.kdt.pojavlaunch.profiles.VersionSelectorDialog;
import net.kdt.pojavlaunch.game.renderer.GameRenderer;
import net.kdt.pojavlaunch.utils.CropperUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InstanceEditorFragment extends Fragment implements CropperUtils.CropperReceiver {
    public static final String TAG = "InstanceEditorFragment";

    private Instance mInstance;
    private String mSelectedControlLayout;
    private Button mSaveButton, mDeleteButton, mControlSelectButton, mVersionSelectButton;
    private Spinner mDefaultRuntime, mDefaultRenderer;
    private EditText mDefaultName, mDefaultJvmArgument;
    private TextView mDefaultVersion, mDefaultControl;
    private ImageView mInstanceIcon;
    private CheckBox mSharedDataCheckbox;
    private int mRecommendedIconSize;
    private final ActivityResultLauncher<?> mCropperLauncher = CropperUtils.registerCropper(this, this);

    private List<String> mRenderNames;

    public InstanceEditorFragment(){
        super(R.layout.fragment_instance_editor);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Paths, which can be changed
        String value = (String) ExtraCore.consumeValue(ExtraConstants.FILE_SELECTOR);
        if(value != null){
            mSelectedControlLayout = value;
        }
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        bindViews(view);
        styleModrinthEditor(view);

        RendererCache list = RendererCache.getCompatibleRenderers(view.getContext());
        mRenderNames = list.rendererIds;
        List<String> renderList = new ArrayList<>(list.rendererDisplayNames.length + 1);
        renderList.addAll(Arrays.asList(list.rendererDisplayNames));
        renderList.add(view.getContext().getString(R.string.global_default));
        mDefaultRenderer.setAdapter(new ArrayAdapter<>(view.getContext(), R.layout.item_simple_list_1, renderList));

        // Set up behaviors
        mSaveButton.setOnClickListener(v -> {
            InstanceIconProvider.dropIcon(mInstance);
            save();
            Tools.backToMainMenu(requireActivity());
        });

        mDeleteButton.setOnClickListener(v -> {
            DeleteConfirmDialogFragment dialogFragment = new DeleteConfirmDialogFragment();
            dialogFragment.show(getChildFragmentManager(), "delete_dialog_confirm");
        });

        View.OnClickListener controlSelectListener = getControlSelectListener();
        mControlSelectButton.setOnClickListener(controlSelectListener);
        mDefaultControl.setOnClickListener(controlSelectListener);

        // Setup the expendable list behavior
        View.OnClickListener versionSelectListener = getVersionSelectListener();
        mVersionSelectButton.setOnClickListener(versionSelectListener);
        mDefaultVersion.setOnClickListener(versionSelectListener);

        // Set up the icon change click listener
        mInstanceIcon.setOnClickListener(v -> {
            // Fill recommended size on click to ge the most up to date data
            mRecommendedIconSize = Math.max(v.getWidth(), v.getHeight());
            CropperUtils.startCropper(mCropperLauncher);
        });

        mSharedDataCheckbox.setOnCheckedChangeListener((v,checked) ->{
            mInstance.sharedData = checked;
            int text = R.string.instance_shared_data_off;
            if(checked) text = R.string.instance_shared_data_on;
            mSharedDataCheckbox.setText(text);
        });

        Instance selectedInstance = Instances.loadSelectedInstance();
        Context context = view.getContext();
        if(selectedInstance == null) {
            Toast.makeText(context, R.string.no_instance, Toast.LENGTH_LONG).show();
            getParentFragmentManager().popBackStack();
        }else {
            loadValues(selectedInstance, context);
            // Back button, the installation's name, and the Mods / Resource Packs / Shaders buttons.
            if (view instanceof android.widget.LinearLayout) {
                InstallationChrome.attach(this, (android.widget.LinearLayout) view, selectedInstance);
            }
        }
    }

    private View.OnClickListener getControlSelectListener() {
        return v -> {
            Bundle bundle = new Bundle(3);
            bundle.putBoolean(FileSelectorFragment.BUNDLE_SELECT_FOLDER, false);
            bundle.putString(FileSelectorFragment.BUNDLE_ROOT_PATH, Tools.CTRLMAP_PATH);

            Tools.swapFragment(requireActivity(),
                    FileSelectorFragment.class, FileSelectorFragment.TAG, bundle);
        };
    }

    private View.OnClickListener getVersionSelectListener() {
        return v -> VersionSelectorDialog.open(v.getContext(), false, (id, snapshot)-> mDefaultVersion.setText(id));
    }

    private static String nullToEmpty(String in) {
        if(in == null) return "";
        return in;
    }

    private void loadValues(@NonNull Instance instance, @NonNull Context context){
        mInstance = instance;
        mInstanceIcon.setImageDrawable(
                InstanceIconProvider.fetchIcon(getResources(), instance)
        );

        // Runtime spinner
        List<Runtime> runtimes = MultiRTUtils.getRuntimes();
        int jvmIndex = -1;
        if(instance.selectedRuntime != null) {
            jvmIndex = runtimes.indexOf(new Runtime(instance.selectedRuntime));
        }
        mDefaultRuntime.setAdapter(new RTSpinnerAdapter(context, runtimes));
        if(jvmIndex == -1) jvmIndex = runtimes.size() - 1;
        mDefaultRuntime.setSelection(jvmIndex);

        // Renderer spinner
        // Use the instance's own stored renderer here, not the effective one: otherwise an
        // instance with no renderer set shows (and on Save pins) the global renderer.
        int rendererIndex = Tools.isValidString(instance.renderer)
                ? mRenderNames.indexOf(instance.renderer) : -1;
        if(rendererIndex == -1) {
            rendererIndex = mDefaultRenderer.getAdapter().getCount() - 1;
        }
        mDefaultRenderer.setSelection(rendererIndex);

        mDefaultVersion.setText(instance.versionId);
        mDefaultJvmArgument.setText(nullToEmpty(instance.jvmArgs));
        mDefaultName.setText(nullToEmpty(instance.name));
        mDefaultControl.setText(mSelectedControlLayout == null ? nullToEmpty(instance.controlLayout) : mSelectedControlLayout);
        mSharedDataCheckbox.setChecked(instance.sharedData);
    }

    private void bindViews(@NonNull View view){
        mDefaultControl = view.findViewById(R.id.vprof_editor_ctrl_spinner);
        mDefaultRuntime = view.findViewById(R.id.vprof_editor_spinner_runtime);
        mDefaultRenderer = view.findViewById(R.id.vprof_editor_instance_renderer);
        mDefaultVersion = view.findViewById(R.id.vprof_editor_version_spinner);

        mDefaultName = view.findViewById(R.id.vprof_editor_instance_name);
        mDefaultJvmArgument = view.findViewById(R.id.vprof_editor_jre_args);

        mSaveButton = view.findViewById(R.id.vprof_editor_save_button);
        mDeleteButton = view.findViewById(R.id.vprof_editor_delete_button);
        mControlSelectButton = view.findViewById(R.id.vprof_editor_ctrl_button);
        mVersionSelectButton = view.findViewById(R.id.vprof_editor_version_button);
        mInstanceIcon = view.findViewById(R.id.vprof_editor_instance_icon);
        mSharedDataCheckbox = view.findViewById(R.id.vprof_editor_data_checkbox_container);
    }


    private GradientDrawable editorShape(int fill, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{fill, Color.argb(102, 4, 24, 38)});
        drawable.setCornerRadius(dpEditor(12));
        drawable.setStroke(dpEditor(1), strokeColor);
        return drawable;
    }

    private int dpEditor(float value) {
        return Math.round(value * requireContext().getResources().getDisplayMetrics().density);
    }

    private void styleModrinthEditor(@NonNull View root) {
        // Re-skin the existing editor controls (dark glass, teal edges) without changing their behavior or IDs.
        Context context = root.getContext();
        root.setBackground(Fx.glass(context, 18, 0xAA00E6AA));

        int[] fieldIds = {
                R.id.vprof_editor_instance_name,
                R.id.vprof_editor_version_spinner,
                R.id.vprof_editor_ctrl_spinner,
                R.id.vprof_editor_data_checkbox_container,
                R.id.vprof_editor_jre_args,
                R.id.vprof_editor_spinner_runtime,
                R.id.vprof_editor_instance_renderer
        };
        for (int id : fieldIds) {
            View field = root.findViewById(id);
            if (field != null) {
                field.setBackground(Fx.fill(context, 0x88051A28, 14, 0xFF1D697E));
                field.setPadding(dpEditor(14), field.getPaddingTop(), dpEditor(14), field.getPaddingBottom());
            }
        }

        // Small teal captions above the fields, like a control panel.
        int[] labelIds = {
                R.id.vprof_editor_profile_name_sub, R.id.textView4, R.id.textView7,
                R.id.textView6, R.id.textView2, R.id.textView3
        };
        for (int id : labelIds) {
            TextView label = root.findViewById(id);
            if (label != null) {
                label.setTextColor(0xFF5FE3C4);
                label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
                label.setAllCaps(true);
                label.setLetterSpacing(0.14f);
            }
        }

        mInstanceIcon.setBackground(Fx.fill(context, 0x88051A28, 18, 0xFF00E6AA));

        mDefaultName.setTextColor(Fx.TEXT);
        mDefaultName.setHintTextColor(Fx.MUTED);
        mDefaultJvmArgument.setTextColor(Fx.TEXT);
        mDefaultJvmArgument.setHintTextColor(Fx.MUTED);
        mDefaultVersion.setTextColor(Fx.TEXT);
        mDefaultControl.setTextColor(Fx.TEXT);
        mSharedDataCheckbox.setTextColor(Fx.TEXT);

        Button[] selectButtons = {mControlSelectButton, mVersionSelectButton};
        for (Button button : selectButtons) {
            button.setBackgroundTintList(null);
            button.setTextColor(Fx.ON_ACCENT);
            button.setBackground(Fx.accentGradient(context, 14));
            button.setAllCaps(false);
            button.setElevation(0);
        }
        mSaveButton.setBackgroundTintList(null);
        mSaveButton.setTextColor(Fx.ON_ACCENT);
        mSaveButton.setBackground(Fx.accentGradient(context, 16));
        mSaveButton.setAllCaps(false);
        mDeleteButton.setBackgroundTintList(null);
        mDeleteButton.setTextColor(0xFFFFE0E2);
        mDeleteButton.setBackground(Fx.fill(context, Fx.DANGER_FILL, 16, 0xFFC44857));
        mDeleteButton.setAllCaps(false);

        mDefaultRuntime.setPopupBackgroundDrawable(Fx.fill(context, 0xFF071F2E, 12, 0xFF1D697E));
        mDefaultRenderer.setPopupBackgroundDrawable(Fx.fill(context, 0xFF071F2E, 12, 0xFF1D697E));

        // Alive: fields light up when touched, buttons press in, and everything slides in once.
        EditText[] typed = {mDefaultName, mDefaultJvmArgument};
        for (final EditText field : typed) {
            field.setOnFocusChangeListener((v, focused) -> v.setBackground(focused
                    ? Fx.fill(context, 0xAA062433, 14, 0xFF00E6AA)
                    : Fx.fill(context, 0x88051A28, 14, 0xFF1D697E)));
        }
        View[] pressable = {mSaveButton, mDeleteButton, mControlSelectButton, mVersionSelectButton, mInstanceIcon};
        for (View view : pressable) Motion.press(view);
        View iconView = root.findViewById(R.id.vprof_editor_instance_icon);
        if (iconView != null && iconView.getParent() instanceof ViewGroup) {
            ViewGroup form = (ViewGroup) iconView.getParent();
            for (int i = 0; i < form.getChildCount(); i++) Motion.enter(form.getChildAt(i), i / 2);
        }
        if (mSaveButton.getParent() instanceof View) Motion.enter((View) mSaveButton.getParent(), 4);
    }

    private void save(){
        //First, check for potential issues in the inputs
        mInstance.versionId = mDefaultVersion.getText().toString();
        mInstance.controlLayout = mDefaultControl.getText().toString();
        mInstance.jvmArgs = mDefaultJvmArgument.getText().toString();

        String newName = mDefaultName.getText().toString();
        if(mInstance.controlLayout.isEmpty()) mInstance.controlLayout = null;
        if(mInstance.jvmArgs.isEmpty()) mInstance.jvmArgs = null;

        Runtime selectedRuntime = (Runtime) mDefaultRuntime.getSelectedItem();
        mInstance.selectedRuntime = (selectedRuntime.name.equals("<Default>") || selectedRuntime.versionString == null)
                ? null : selectedRuntime.name;

        if(mDefaultRenderer.getSelectedItemPosition() == mRenderNames.size()) mInstance.renderer = null;
        else mInstance.renderer = mRenderNames.get(mDefaultRenderer.getSelectedItemPosition());

        try {
            if(!newName.isEmpty() && !newName.equals(mInstance.name))
                Instances.renameInstanceDirectory(mInstance, newName);
            mInstance.name = newName;
            mInstance.write();
        }catch (Exception e) {
            Tools.showErrorRemote(e);
        }
    }

    @Override
    public float getAspectRatio() {
        return 1f;
    }

    @Override
    public int getTargetMaxSide() {
        return mRecommendedIconSize;
    }

    @Override
    public void onCropped(Bitmap contentBitmap) {
        mInstanceIcon.setImageBitmap(contentBitmap);
        Log.i("bitmap", "w="+contentBitmap.getWidth() +" h="+contentBitmap.getHeight());
        try {
            mInstance.encodeNewIcon(contentBitmap);
        }catch (IOException e) {
            Tools.showErrorRemote(e);
        }
    }

    @Override
    public void onFailed(Exception exception) {
        Tools.showErrorRemote(exception);
    }
}
