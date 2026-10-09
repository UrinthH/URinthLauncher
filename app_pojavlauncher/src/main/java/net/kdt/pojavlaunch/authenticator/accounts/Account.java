package net.kdt.pojavlaunch.authenticator.accounts;


import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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

    private static synchronized Bitmap getSteveFace() {
        if (sSteveFace != null && !sSteveFace.isRecycled()) return sSteveFace;
        Bitmap face = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(face);
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(92, 58, 35));
        canvas.drawRect(0, 0, 100, 25, paint);
        canvas.drawRect(0, 20, 14, 78, paint);
        canvas.drawRect(86, 20, 100, 78, paint);
        paint.setColor(Color.rgb(198, 142, 103));
        canvas.drawRect(14, 20, 86, 84, paint);
        paint.setColor(Color.rgb(45, 29, 21));
        canvas.drawRect(14, 20, 30, 31, paint);
        canvas.drawRect(30, 25, 43, 31, paint);
        canvas.drawRect(57, 25, 70, 31, paint);
        canvas.drawRect(70, 20, 86, 31, paint);
        paint.setColor(Color.WHITE);
        canvas.drawRect(22, 39, 42, 51, paint);
        canvas.drawRect(58, 39, 78, 51, paint);
        paint.setColor(Color.rgb(55, 112, 180));
        canvas.drawRect(30, 39, 40, 51, paint);
        canvas.drawRect(60, 39, 70, 51, paint);
        paint.setColor(Color.rgb(40, 29, 24));
        canvas.drawRect(14, 58, 86, 84, paint);
        canvas.drawRect(30, 52, 70, 64, paint);
        paint.setColor(Color.rgb(198, 142, 103));
        canvas.drawRect(40, 52, 60, 62, paint);
        sSteveFace = face;
        return face;
    }
    private File getSkinFaceFile() {
        return new File(Tools.DIR_CACHE,  "skin-face-" + profileId +"-"+authType.name() + ".webp");
    }
}
