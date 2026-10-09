package net.kdt.pojavlaunch.game.renderer;

import android.content.Context;
import android.util.Log;

import java.util.Map;

/**
 * URinthUltra Wrapper: the launch-time integration layer used while Ultra Mode is enabled.
 *
 * It wraps the user's currently selected RenderSpec rather than pretending to be a new
 * EGL/Vulkan driver. Native library loading and EGL setup are delegated to the selected
 * backend; this class adds a stable URinthUltra identity, capability-aware profile
 * environment, and explicit diagnostics around that backend.
 */
public final class UrinthUltraWrapperRenderSpec implements RenderSpec {
    private static final String TAG = "UrinthUltraWrapper";
    private static final String WRAPPER_ACTIVE = "URINTH_ULTRA_WRAPPER";
    private static final String WRAPPER_VERSION = "URINTH_ULTRA_WRAPPER_VERSION";
    private final RenderSpec delegate;

    public UrinthUltraWrapperRenderSpec(RenderSpec delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("A real renderer backend is required");
        }
        if (delegate instanceof UrinthUltraWrapperRenderSpec) {
            this.delegate = ((UrinthUltraWrapperRenderSpec) delegate).delegate;
        } else {
            this.delegate = delegate;
        }
    }

    public RenderSpec getDelegate() {
        return delegate;
    }

    @Override
    public boolean compatibleDevice(Context context) {
        return delegate.compatibleDevice(context);
    }

    @Override
    public String name() {
        return "UrinthUltra Wrapper (" + delegate.name() + ")";
    }

    @Override
    public int displayName() {
        return delegate.displayName();
    }

    /** Keep the real backend tag so its compatibility and Mesa/Zink profile remain accurate. */
    @Override
    public String tag() {
        return delegate.tag();
    }

    @Override
    public String library() {
        return delegate.library();
    }

    @Override
    public String librarySearchPath() {
        return delegate.librarySearchPath();
    }

    @Override
    public void setupEnvironment(Context context, Map<String, String> envMap) {
        delegate.setupEnvironment(context, envMap);
        envMap.put(WRAPPER_ACTIVE, "1");
        envMap.put(WRAPPER_VERSION, "1");
        Log.i(TAG, "URinthUltra Wrapper v1 active; delegated backend="
                + delegate.name() + ", tag=" + delegate.tag()
                + ", nativeDriverReplacement=false");
    }

    @Override
    public boolean setupRenderer() {
        boolean ready = delegate.setupRenderer();
        Log.i(TAG, "Backend setup result: backend=" + delegate.name() + ", ready=" + ready);
        return ready;
    }
}
