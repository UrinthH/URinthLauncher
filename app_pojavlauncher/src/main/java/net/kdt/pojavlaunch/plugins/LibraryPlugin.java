package net.kdt.pojavlaunch.plugins;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class LibraryPlugin {
    private static final String TAG = "LibraryPlugin";

    // Known plugins constants
    public static final String ID_ANGLE_PLUGIN = "git.mojo.angle";
    public static final String ID_FFMPEG_PLUGIN = "git.mojo.ffmpeg";
    public static final String ID_ZINK_PLUGIN = "git.mojo.zink";
    public static final String ID_MESA_PLUGIN = "git.mojo.mesa";
    public static final String ID_MOBILEGLUES_PLUGIN = "com.fcl.plugin.mobileglues";
    public static final String ID_MOBILEGLUES_PLUGIN_FCL = "com.fcl.plugin.renderer.mobileglues";
    public static final String ID_MOBILEGLUES_PLUGIN_MIO = "com.mio.plugin.renderer.mobileglues";
    public static final String ID_KRYPTON_PLUGIN = "com.bzlzhh.plugin.ngg";
    public static final String ID_KRYPTON_ANGLELESS_PLUGIN = "com.bzlzhh.plugin.ngg.angleless";
    public static final String ID_MESA_PLUGIN_FCL = "com.fcl.plugin.renderer.mesa";
    public static final String ID_MESA_PLUGIN_MIO_2319 = "com.mio.plugin.renderer.mesa2319";
    public static final String ID_MESA_PLUGIN_MIO_2427 = "com.mio.plugin.renderer.mesa2427";
    public static final String ID_MESA_PLUGIN_MIO_2434 = "com.mio.plugin.renderer.mesa2434";
    public static final String ID_MESA_PLUGIN_MIO_2500 = "com.mio.plugin.renderer.mesa2500";
    public static final String ID_MESA_PLUGIN_MIO_2500_RC1 = "com.mio.plugin.renderer.mesa2500.rc1";

    private String appId;
    private String libraryPath;
    private LibraryPlugin(String app, String libraryPath){
        this.appId = app;
        this.libraryPath = libraryPath;
    }
    public static LibraryPlugin discoverPlugin(Context ctx, String appId){

        String libraryPath;
        try {
            PackageInfo pluginPackage = ctx.getPackageManager().getPackageInfo(appId, PackageManager.GET_SHARED_LIBRARY_FILES);
            libraryPath = pluginPackage.applicationInfo.nativeLibraryDir;

        } catch (Exception e){
            Log.e(TAG, "Plugin discover failed: " + e.getMessage());
            return null;
        }
       return new LibraryPlugin(appId, libraryPath);
    }

    public String getId(){
        return appId;
    }

    public String getLibraryPath(){
        return libraryPath;
    }
    public String resolveAbsolutePath(String library) {
        return new File(libraryPath, library).getAbsolutePath();
    }
    public File resolve(String library) {
        return new File(libraryPath, library);
    }

    public boolean checkLibraries(String... libs){
        for(String lib : libs){
            if(!(new File(libraryPath, lib).exists())) return false;
        }
        return true;
    }
}
