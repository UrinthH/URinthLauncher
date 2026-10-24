package net.kdt.pojavlaunch.authenticator.accounts;


import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Log;

import net.kdt.pojavlaunch.*;
import net.kdt.pojavlaunch.authenticator.AuthType;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.utils.JSONUtils;

import java.io.*;
import java.net.URL;

import android.graphics.Bitmap;

import androidx.annotation.Keep;

import com.google.gson.JsonParseException;

import org.apache.commons.io.IOUtils;

@Keep
public class Account {
    public transient File mSaveLocation;
    public String accessToken = "0"; // access token
    public String profileId = "00000000-0000-0000-0000-000000000000"; // profile UUID, for obtaining skin
    public String username = "Steve";
    public AuthType authType = AuthType.LOCAL;
    public boolean isMicrosoft = false;
    public String refreshToken = "0";
    public String xuid;
    public long expiresAt;
    private transient Bitmap mFaceCache;
    private static Bitmap sSteveFace;

    protected Account() {}

    public void updateSkinFace() {
        String skinFaceUrlTemplate = authType.skinUrl;
        if(skinFaceUrlTemplate == null) return;
        String skinFaceUrl = String.format(skinFaceUrlTemplate, username);
        try {
            Log.i("SkinLoader", "Updating skin face...");
            File skinFile = getSkinFaceFile();
            // Streaming it directly breaks on some devices
            byte[] skinBytes = IOUtils.toByteArray(new URL(skinFaceUrl));
            Bitmap skinBitmap = BitmapFactory.decodeByteArray(skinBytes, 0, skinBytes.length);
            if(skinBitmap == null) return;
            Bitmap skinFace = new SkinHeadRenderer().render(100, skinBitmap);
            skinBitmap.recycle();
            if(skinFace == null) return;
            try(FileOutputStream fileOutputStream = new FileOutputStream(skinFile)) {
                skinFace.compress(Bitmap.CompressFormat.WEBP, 90, fileOutputStream);
            }
            Log.i("SkinLoader", "Update skin face success");
        } catch (IOException e) {
            // Skin refresh limit, no internet connection, etc...
            // Simply ignore updating skin face
            Log.w("SkinLoader", "Could not update skin face", e);
        }
    }

    public boolean isLocal(){
        return accessToken.equals("0");
    }
    
    public void save() throws IOException {
        FileUtils.ensureParentDirectory(mSaveLocation);
        JSONUtils.writeToFile(mSaveLocation, this);
    }

    public Account reload() {
        try {
            Account account = JSONUtils.readFromFile(mSaveLocation, Account.class);
            if(account == null) return null;
            account.mSaveLocation = mSaveLocation;
            return account;
        }catch (IOException | JsonParseException e) {
            return null;
        }
     }

    public Bitmap getSkinFace(){
        if(isLocal()) return getSteveFace();
        File skinFaceFile = getSkinFaceFile();
        if(!skinFaceFile.exists()) return null;
        if(mFaceCache == null) {
            mFaceCache = BitmapFactory.decodeFile(skinFaceFile.getAbsolutePath());
        }
        return mFaceCache;
    }

    /**
     * Face shown for offline (local) accounts: a small original 8x8 pixel-art head, scaled up with
     * hard edges so every pixel stays sharp. Premium accounts show their real skin instead.
     */
    private static final String[] LOCAL_FACE = {
            "HHHHHHHH",
            "HHHHHHHH",
            "HHSSSSHH",
            "SWGSSWGS",
            "SSSSSSSS",
            "SCSSSSCS",
            "SSMMMMSS",
            "DSSSSSSD",
    };

    private static int localFaceColor(char code) {
        switch (code) {
            case 'H': return Color.rgb(54, 44, 40);
            case 'S': return Color.rgb(224, 172, 128);
            case 'W': return Color.rgb(245, 247, 250);
            case 'G': return Color.rgb(46, 125, 90);
            case 'C': return Color.rgb(232, 148, 120);
            case 'M': return Color.rgb(150, 76, 62);
            case 'D': return Color.rgb(204, 152, 110);
            default: return Color.TRANSPARENT;
        }
    }

    private static synchronized Bitmap getSteveFace() {
        if (sSteveFace != null && !sSteveFace.isRecycled()) return sSteveFace;
        int cells = LOCAL_FACE.length;
        Bitmap small = Bitmap.createBitmap(cells, cells, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < cells; y++) {
            for (int x = 0; x < cells; x++) {
                small.setPixel(x, y, localFaceColor(LOCAL_FACE[y].charAt(x)));
            }
        }
        // filter = false keeps hard pixel edges instead of blurring them
        Bitmap face = Bitmap.createScaledBitmap(small, 96, 96, false);
        if (face != small) small.recycle();
        sSteveFace = face;
        return face;
    }
    private File getSkinFaceFile() {
        return new File(Tools.DIR_CACHE,  "skin-face-" + profileId +"-"+authType.name() + ".webp");
    }
}
