package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.kdt.mcgui.mcVersionSpinner;
import com.kdt.mcgui.AccountSpinner;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.authenticator.accounts.Account;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.extra.ExtraListener;
import net.kdt.pojavlaunch.instances.DisplayInstance;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.fragments.InstanceEditorFragment;
import net.kdt.pojavlaunch.fragments.ForgeInstallFragment;
import net.kdt.pojavlaunch.fragments.LegacyFabricInstallFragment;
import net.kdt.pojavlaunch.fragments.ModrinthBrowserFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;
import net.kdt.pojavlaunch.tasks.AsyncVersionList;
import net.kdt.pojavlaunch.profiles.VersionSelectorDialog;
import net.kdt.pojavlaunch.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

import git.artdeell.mojo.R;

public final class ModrinthLauncherView extends View {
    private static final float W = 1536f;
    private static final float H = 686f;
    private static final int BG = Color.rgb(3, 16, 27);
    private static final int PANEL = Color.argb(102, 7, 29, 44);
    private static final int PANEL_2 = Color.argb(102, 9, 39, 55);
    private static final int TEXT = Color.rgb(242, 248, 250);
    private static final int MUTED = Color.rgb(171, 192, 203);
    private static final int ACCENT = Color.rgb(0, 230, 170);
    private static final int CYAN = Color.rgb(42, 190, 220);
    private static final int LINE = Color.rgb(24, 94, 118);

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Bitmap logo;
    private final Bitmap heroArtwork;
    private final Bitmap cherryArtwork;
    private final Bitmap snowArtwork;
    private final Bitmap badlandsArtwork;
    private final Bitmap updateIcon;
    private final Bitmap ultraIcon;
    private final Bitmap optifineIcon;
    private final Bitmap fabricIcon;
    private final Bitmap quiltIcon;
    private final Bitmap forgeIcon;
    private final Bitmap neoforgeIcon;
    private Bitmap backgroundArtwork;
    private Bitmap heroBannerArtwork;
    private float skinRotationDegrees;
    private boolean draggingSkinViewer;
    private final Bitmap[] versionBiomeArtworks = new Bitmap[5];
    private final mcVersionSpinner versionSpinner;
    private final AccountSpinner accountSpinner;
    private final FragmentActivity activity;
    private final Runnable modInstaller;
    private boolean menuOpen = true;
    private int selectedPage = 0;
    private boolean ultraOn;
    private float sx = 1f, sy = 1f;
    private float instanceScrollX;
    private float touchDownX;
    private float lastTouchX;
    private boolean draggingInstances;
    private Account currentAccount;
    private Account[] savedAccounts = new Account[0];
    private boolean accountChooserOpen;
    private boolean authChooserOpen;
    private DisplayInstance[] instanceCards = new DisplayInstance[0];

    private final ExtraListener<Boolean> accountRefreshListener = (key, value) -> {
        reloadLauncherData();
        return false;
    };

    public ModrinthLauncherView(FragmentActivity activity, mcVersionSpinner spinner, AccountSpinner accountSpinner, Runnable installer) {
        super(activity);
        this.activity = activity;
        versionSpinner = spinner;
        this.accountSpinner = accountSpinner;
        modInstaller = installer;
        logo = bitmap(R.drawable.ic_modrinth);
        heroArtwork = bitmap(R.drawable.urinth_hero);
        cherryArtwork = bitmap(R.drawable.urinth_cherry);
        snowArtwork = bitmap(R.drawable.urinth_snow);
        badlandsArtwork = bitmap(R.drawable.urinth_badlands);
        updateIcon = bitmap(R.drawable.ic_px_verify_hash);
        ultraIcon = bitmap(R.drawable.ic_px_speed);
        optifineIcon = bitmap(R.drawable.ic_optifine);
        fabricIcon = bitmap(R.drawable.ic_fabric);
        quiltIcon = bitmap(R.drawable.ic_quilt);
        forgeIcon = bitmap(R.drawable.ic_forge);
        neoforgeIcon = bitmap(R.drawable.ic_neoforge);
        loadBackgroundArtwork();
        SharedPreferences prefs = activity.getSharedPreferences("urinth_ui", Context.MODE_PRIVATE);
        ultraOn = prefs.getBoolean("ultra", true);
        p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        p.setFilterBitmap(true);
        p.setDither(true);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f);
        setFocusable(true);
        reloadLauncherData();
    }

    private void loadBackgroundArtwork() {
        // One continuous mountain/tundra scene is used as the launcher background.
        // Warm grading below gives it a sunset look without splitting or layering
        // a second screenshot over the background.
        loadRemoteArtwork(
                "https://www.minecraft.net/content/dam/minecraftnet/games/minecraft/screenshots/vv_Tundra_AG_02_1280x720.jpg",
                bitmap -> { backgroundArtwork = bitmap; invalidate(); }
        );
        // The hero banner has its own single, continuous ocean scene; it is not
        // cropped from the full-screen launcher background or composed from halves.
        loadRemoteArtwork(
                "https://www.minecraft.net/content/dam/minecraftnet/games/minecraft/screenshots/ATB_WarmOcean_header.jpg",
                bitmap -> { heroBannerArtwork = bitmap; invalidate(); }
        );

        // Verified official Minecraft biome artwork. Each card gets a different biome
        // so the three visible instances never look like duplicate screenshots.
        loadRemoteArtwork(
                "https://www.minecraft.net/content/dam/minecraftnet/games/minecraft/screenshots/MCV_ChaseTheSkies_Swamp02_VV_.net_1280x720.jpg",
                bitmap -> { versionBiomeArtworks[0] = bitmap; invalidate(); }
        );
        loadRemoteArtwork(
                "https://www.minecraft.net/content/dam/minecraftnet/games/minecraft/screenshots/vv_Tundra_AG_02_1280x720.jpg",
                bitmap -> { versionBiomeArtworks[1] = bitmap; invalidate(); }
        );
        loadRemoteArtwork(
                "https://www.minecraft.net/content/dam/minecraftnet/games/minecraft/screenshots/vv_Mesa_AG_01_1280x720.jpg",
                bitmap -> { versionBiomeArtworks[2] = bitmap; invalidate(); }
        );
        loadRemoteArtwork(
                "https://www.minecraft.net/content/dam/minecraftnet/games/minecraft/screenshots/ATB_WarmOcean_header.jpg",
                bitmap -> { versionBiomeArtworks[3] = bitmap; invalidate(); }
        );
        // Use the bundled cherry-grove artwork as a fifth distinct, offline-safe biome.
        versionBiomeArtworks[4] = cherryArtwork;
    }

    private void loadRemoteArtwork(String imageUrl, java.util.function.Consumer<Bitmap> onLoaded) {
        PojavApplication.sExecutorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(imageUrl).openConnection();
                connection.setInstanceFollowRedirects(true);
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("User-Agent", "URinthLauncher/1.0 (Android)");
                connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) throw new IOException("Artwork HTTP " + status);
                Bitmap loaded;
                try (java.io.InputStream stream = connection.getInputStream()) {
                    loaded = BitmapFactory.decodeStream(stream);
                }
                if (loaded != null) {
                    Tools.runOnUiThread(() -> onLoaded.accept(loaded));
                }
            } catch (Exception ignored) {
                // Keep the launcher usable if a remote artwork asset is unavailable.
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void reloadLauncherData() {
        PojavApplication.sExecutorService.execute(() -> {
            Account account = Accounts.getCurrent();
            Account[] accounts = new Account[0];
            try {
                accounts = Accounts.load().accounts.toArray(new Account[0]);
            } catch (Exception ignored) {
                // Keep the launcher usable if account storage is temporarily unavailable.
            }
            final Account[] accountResult = accounts;
            DisplayInstance[] loaded = new DisplayInstance[0];
            try {
                loaded = Instances.loadDisplay().list.toArray(new DisplayInstance[0]);
            } catch (Exception ignored) {
                // Keep the launcher usable if the instance directory is temporarily unavailable.
            }
            final DisplayInstance[] result = loaded;
            Tools.runOnUiThread(() -> {
                currentAccount = account;
                savedAccounts = accountResult;
                instanceCards = result;
                invalidate();
            });
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ExtraCore.addExtraListener(ExtraConstants.REFRESH_ACCOUNT_SPINNER, accountRefreshListener);
        reloadLauncherData();
    }

    @Override
    protected void onDetachedFromWindow() {
        ExtraCore.removeExtraListenerFromValue(
                ExtraConstants.REFRESH_ACCOUNT_SPINNER, accountRefreshListener
        );
        super.onDetachedFromWindow();
    }

    private Bitmap bitmap(int id) {
        Drawable d = ContextCompat.getDrawable(getContext(), id);
        if (d == null) return null;
        Bitmap b = Bitmap.createBitmap(Math.max(1,d.getIntrinsicWidth()), Math.max(1,d.getIntrinsicHeight()), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0,0,b.getWidth(),b.getHeight());
        d.draw(c);
        return b;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Fill the entire available display area without a left/right letterbox.
        sx = getWidth() / W;
        sy = getHeight() / H;
        canvas.save();
        canvas.scale(sx, sy);
        drawBackground(canvas);
        drawHeader(canvas);
        if (menuOpen) drawSidebar(canvas);
        drawMain(canvas);
        drawRight(canvas);
        if (accountChooserOpen) drawAccountChooser(canvas);
        if (authChooserOpen) drawAuthChooser(canvas);
        canvas.restore();
    }

    private void drawBackground(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        if (backgroundArtwork != null) {
            android.graphics.ColorMatrix sunsetGrade = new android.graphics.ColorMatrix(new float[]{
                    1.13f, 0.02f, 0, 0, 10,
                    0.01f, 1.02f, 0, 0, 2,
                    0, 0, 0.86f, 0, 0,
                    0, 0, 0, 1, 0
            });
            p.setColorFilter(new android.graphics.ColorMatrixColorFilter(sunsetGrade));
            drawCoverBitmap(c, backgroundArtwork, 0, 0, W, H);
            p.setColorFilter(null);

            // Warm sunset sky/highlights, while preserving a single full-screen image.
            p.setShader(new LinearGradient(0, 0, 0, H,
                    Color.argb(8, 255, 168, 83),
                    Color.argb(92, 255, 102, 38),
                    Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H, p);
            p.setShader(null);

            // Keep the launcher panels readable while retaining the scenery underneath.
            p.setColor(Color.argb(94, 2, 12, 22));
            c.drawRect(0, 0, W, H, p);
        } else {
            p.setShader(new LinearGradient(0,0,0,H,
                    Color.rgb(3,19,31), Color.rgb(2,13,24), Shader.TileMode.CLAMP));
            c.drawRect(0,0,W,H,p);
            p.setShader(null);
        }
        p.setColor(Color.argb(58, 3, 16, 27));
        c.drawRect(0,58,W,H,p);
    }

    private void drawHeader(Canvas c) {
        p.setShader(new LinearGradient(0,0,W,62,Color.rgb(3,17,28),Color.rgb(5,28,42),Shader.TileMode.CLAMP)); c.drawRect(0,0,W,62,p); p.setShader(null);
        drawHamburger(c,38,31);
        drawBitmap(c,logo,80,10,48,48);
        text(c,"Modrinth",143,39,27,TEXT,true);
        // Keep the H in Modrinth and the L in Launcher clearly separated.
        text(c,"Launcher",272,39,27,ACCENT,true);
        p.setColor(Color.rgb(36,99,116)); c.drawRect(388,17,390,45,p);
        text(c,"Made By: Macase, Nile.",410,37,14,ACCENT,true);
    }

    private void drawSidebar(Canvas c) {
        float x=0, y=62, w=228;
        p.setShader(new LinearGradient(0,y,w,H,Color.argb(102,5,28,43),Color.argb(102,3,17,29),Shader.TileMode.CLAMP));
        c.drawRect(x,y,w,H,p); p.setShader(null);
        p.setColor(Color.argb(35,35,160,180)); c.drawRect(227,y,228,H,p);
        String[] labels={"Home","Instances","Mods","Resource Packs","Servers","Settings"};
        for(int i=0;i<labels.length;i++) {
            float yy=72+i*48;
            if(i==selectedPage) {
                p.setShader(new LinearGradient(19,yy,215,yy+42,Color.rgb(8,102,86),Color.rgb(5,65,68),Shader.TileMode.CLAMP));
                c.drawRoundRect(19,yy,215,yy+42,12,12,p); p.setShader(null);
                round(c,19,yy,215,yy+42,12,Color.TRANSPARENT,Color.rgb(0,230,170),1.3f);
                p.setShader(new LinearGradient(19,yy,25,yy+42,ACCENT,Color.rgb(0,115,125),Shader.TileMode.CLAMP));
                c.drawRoundRect(19,yy+5,24,yy+37,3,3,p); p.setShader(null);
                p.setColor(Color.argb(22,0,230,170)); c.drawCircle(195,yy+10,22,p);
            } else {
                round(c,19,yy,215,yy+42,12,Color.argb(14,105,175,190),Color.TRANSPARENT,0);
            }
            drawNavIcon(c,43,yy+21,i);
            text(c,labels[i],75,yy+27,14,i==selectedPage?ACCENT:TEXT,true);
        }
        p.setShader(new LinearGradient(20,365,205,365,Color.TRANSPARENT,Color.rgb(34,115,130),Shader.TileMode.CLAMP));
        c.drawRect(20,365,205,366,p); p.setShader(null);
        String[] more={"Modpacks","Shaders","Worlds"};
        for(int i=0;i<3;i++) {
            float yy=378+i*48;
            if(selectedPage==6+i) {
                p.setShader(new LinearGradient(19,yy,215,yy+42,Color.rgb(8,102,86),Color.rgb(5,65,68),Shader.TileMode.CLAMP));
                c.drawRoundRect(19,yy,215,yy+42,12,12,p); p.setShader(null);
                round(c,19,yy,215,yy+42,12,Color.TRANSPARENT,Color.rgb(0,230,170),1.3f);
                p.setColor(Color.argb(22,0,230,170)); c.drawCircle(195,yy+10,22,p);
            } else {
                round(c,19,yy,215,yy+42,12,Color.argb(14,105,175,190),Color.TRANSPARENT,0);
            }
            drawNavIcon(c,43,yy+21,6+i);
            text(c,more[i],75,yy+27,14,selectedPage==6+i?ACCENT:TEXT,true);
        }
        p.setShader(new LinearGradient(19,522,215,602,Color.rgb(8,60,69),Color.rgb(5,35,49),Shader.TileMode.CLAMP));
        c.drawRoundRect(19,522,215,602,15,15,p); p.setShader(null);
        round(c,19,522,215,602,15,Color.TRANSPARENT,Color.rgb(0,174,148),1.1f);
        p.setColor(Color.argb(24,0,230,170)); c.drawCircle(48,544,28,p);
        drawBitmap(c,logo,31,536,42,42);
        text(c,"Modrinth",87,546,15,TEXT,true);
        text(c,"Better Minecraft",87,564,11,MUTED,false);
        text(c,"Together",87,580,11,MUTED,false);
        text(c,"›",196,564,26,ACCENT,true);
        p.setShader(new LinearGradient(0,625,228,H,Color.rgb(9,45,59),Color.rgb(4,27,41),Shader.TileMode.CLAMP));
        c.drawRect(0,625,228,H,p); p.setShader(null);
        p.setColor(Color.rgb(18,79,95)); c.drawRect(0,625,228,626,p);
        drawBitmap(c,logo,25,632,29,29);
        text(c,"Modrinth",67,651,14,TEXT,true);
    }

    private void drawMain(Canvas c) {
        float left=menuOpen?242:18, right=1215;
        float width=right-left;
        if (selectedPage == 1) {
            drawInstancesPage(c, left, 75, width, 585);
            return;
        }
        drawHero(c,left,75,width,170);
        drawInstances(c,left,258,width);
        if (selectedPage == 0) drawUltraMode(c, left, 435, Math.min(width, 420), 66);
    }

    private void drawInstancesPage(Canvas c, float x, float y, float w, float h) {
        p.setShader(new LinearGradient(x,y,x+w,y+h,Color.rgb(8,35,51),Color.rgb(4,22,36),Shader.TileMode.CLAMP));
        c.drawRoundRect(x,y,x+w,y+h,20,20,p); p.setShader(null);
        round(c,x,y,x+w,y+h,20,Color.TRANSPARENT,Color.rgb(27,89,108),1.1f);
        p.setColor(Color.argb(26,0,230,170)); c.drawRoundRect(x+1,y+1,x+w-1,y+4,2,2,p);
        text(c, "Instances", x+28, y+42, 27, TEXT, true);
        text(c, "Create and manage your Minecraft profiles", x+28, y+66, 13, MUTED, false);

        // Three-column layout keeps every supported installer visible without
        // adding a redundant Home button that duplicates the sidebar.
        float gap=14f, cardW=(w-4*gap)/3f, cardH=140f;
        float startX=x+gap, startY=y+88f;
        drawInstanceActionCard(c,startX,startY,cardW,cardH,"Vanilla","Official Minecraft releases","Clean, unmodded profiles","Create Vanilla",0);
        drawInstanceActionCard(c,startX+cardW+gap,startY,cardW,cardH,"OptiFine","Graphics and performance","Shader and FPS support","Install OptiFine",1);
        drawInstanceActionCard(c,startX+2*(cardW+gap),startY,cardW,cardH,"Fabric","Lightweight mod loader","Fast, modern mod support","Create Fabric",2);

        float row2=startY+cardH+14f;
        drawInstanceActionCard(c,startX,row2,cardW,cardH,"Quilt","Community mod loader","Flexible modded profiles","Create Quilt",3);
        drawInstanceActionCard(c,startX+cardW+gap,row2,cardW,cardH,"Forge","Classic mod loader","Huge mod ecosystem","Install Forge",4);
        drawInstanceActionCard(c,startX+2*(cardW+gap),row2,cardW,cardH,"NeoForge","Modern Forge successor","For newer modded versions","Install NeoForge",5);

        float row3=row2+cardH+14f;
        drawInstanceActionCard(c,startX,row3,cardW,cardH,"Legacy Fabric","Older Minecraft versions","Legacy Fabric mod support","Install Legacy Fabric",6);
    }

    private void drawInstanceActionCard(Canvas c,float x,float y,float w,float h,String title,
                                        String subtitle,String detail,String action,int option) {
        round(c,x,y,x+w,y+h,16,Color.rgb(7,34,50),Color.rgb(25,94,117),1.1f);
        p.setColor(option==2?ACCENT:CYAN);
        c.drawRoundRect(x+1,y+16,x+5,y+h-16,2,2,p);
        round(c,x+18,y+18,x+58,y+58,12,Color.rgb(6,56,70),Color.rgb(20,111,129),0.8f);
        Bitmap loaderIcon = option==1 ? optifineIcon : option==2 ? fabricIcon
                : option==3 ? quiltIcon : option==4 ? forgeIcon
                : option==5 ? neoforgeIcon : null;
        if (loaderIcon != null) {
            drawBitmap(c, loaderIcon, x+20, y+20, 36, 36);
        } else {
            drawBitmap(c, blockIcon(), x+20, y+20, 36, 36);
        }
        text(c,title,x+72,y+34,19,TEXT,true);
        text(c,subtitle,x+72,y+56,12,MUTED,false);
        text(c,detail,x+20,y+86,13,TEXT,false);
        pill(c,x+w-152,y+h-47,x+w-18,y+h-15,action);
    }

    public void onCenterContentClosed() {
        if (selectedPage > 1) {
            selectedPage = 0;
            invalidate();
        }
    }

    private void openContentCategory(String category) {
        openContentCategory(category, "");
    }

    private void openContentCategory(String category, String initialQuery) {
        MainMenuFragment host = getMainMenuHost();
        if (host != null) {
            android.os.Bundle args = new android.os.Bundle();
            args.putString(ModrinthBrowserFragment.ARG_CATEGORY, category);
            args.putString(ModrinthBrowserFragment.ARG_QUERY, initialQuery);
            host.showCenterFragment(ModrinthBrowserFragment.class, ModrinthBrowserFragment.TAG, args);
        } else {
            Toast.makeText(getContext(), "Launcher panel is not ready yet", Toast.LENGTH_SHORT).show();
        }
    }

    private MainMenuFragment getMainMenuHost() {
        for (androidx.fragment.app.Fragment fragment : activity.getSupportFragmentManager().getFragments()) {
            if (fragment instanceof MainMenuFragment) return (MainMenuFragment) fragment;
        }
        return null;
    }

    private void launchInstanceOption(int option) {
        MainMenuFragment host = getMainMenuHost();
        if (option == 0) {
            VersionSelectorDialog.open(getContext(), false, (id, snapshot) -> {
                try {
                    Instances.createInstance(instance -> {
                        instance.name = "Vanilla " + id;
                        instance.versionId = id;
                        instance.sharedData = true;
                    }, "Vanilla");
                    reloadLauncherData();
                    Toast.makeText(getContext(), "Vanilla instance created", Toast.LENGTH_SHORT).show();
                    selectedPage = 0;
                    invalidate();
                } catch (IOException e) {
                    Toast.makeText(getContext(), "Could not create instance: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        } else if (host != null) {
            if (option == 1) host.showCenterFragment(OptiFineInstallFragment.class, OptiFineInstallFragment.TAG, null);
            else if (option == 2) host.showCenterFragment(FabricInstallFragment.class, FabricInstallFragment.TAG, null);
            else if (option == 3) host.showCenterFragment(QuiltInstallFragment.class, QuiltInstallFragment.TAG, null);
            else if (option == 4) host.showCenterFragment(ForgeInstallFragment.class, ForgeInstallFragment.TAG, null);
            else if (option == 5) host.showCenterFragment(NeoForgeInstallFragment.class, NeoForgeInstallFragment.TAG, null);
            else if (option == 6) host.showCenterFragment(LegacyFabricInstallFragment.class, LegacyFabricInstallFragment.TAG, null);
        } else {
            Toast.makeText(getContext(), "Launcher panel is not ready yet", Toast.LENGTH_SHORT).show();
        }
    }

    private void drawHero(Canvas c,float x,float y,float w,float h) {
        p.setShader(new LinearGradient(x,y,x+w,y+h,Color.rgb(8,38,54),Color.rgb(4,24,39),Shader.TileMode.CLAMP));
        c.drawRoundRect(x,y,x+w,y+h,19,19,p); p.setShader(null);
        round(c,x,y,x+w,y+h,19,Color.TRANSPARENT,Color.rgb(29,101,122),1.1f);
        c.save();
        c.clipRect(x+1,y+1,x+w-1,y+h-1);
        drawRealisticHeroImage(c,x+1,y+1,w-2,h-2);
        c.restore();

        // Keep the reference-style copy readable over the artwork.
        p.setColor(Color.argb(80,0,15,28));
        c.drawRoundRect(x+12,y+14,x+365,y+h-14,12,12,p);
        text(c,"Play, Explore,",x+28,y+56,27,TEXT,true);
        text(c,"Create, Together.",x+28,y+85,27,TEXT,true);
        text(c,"The best Minecraft experience,",x+28,y+113,13,TEXT,false);
        text(c,"now on your Android device.",x+28,y+131,13,TEXT,false);
    }
    private void drawCloud(Canvas c,float x,float y,float scale) {
        p.setColor(Color.argb(105,255,255,255));
        c.drawRect(x,y+13*scale,x+88*scale,y+31*scale,p);
        c.drawCircle(x+25*scale,y+16*scale,18*scale,p);
        c.drawCircle(x+50*scale,y+9*scale,24*scale,p);
        c.drawCircle(x+72*scale,y+17*scale,16*scale,p);
    }

    private boolean isUsableArtwork(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() < 2 || bitmap.getHeight() < 2) {
            return false;
        }
        long luminanceTotal = 0;
        int minLuminance = 255;
        int maxLuminance = 0;
        int samples = 0;
        for (int row = 1; row <= 4; row++) {
            int py = Math.min(bitmap.getHeight() - 1, row * bitmap.getHeight() / 5);
            for (int col = 1; col <= 6; col++) {
                int px = Math.min(bitmap.getWidth() - 1, col * bitmap.getWidth() / 7);
                int color = bitmap.getPixel(px, py);
                int luminance = (Color.red(color) * 299 + Color.green(color) * 587
                        + Color.blue(color) * 114) / 1000;
                luminanceTotal += luminance;
                minLuminance = Math.min(minLuminance, luminance);
                maxLuminance = Math.max(maxLuminance, luminance);
                samples++;
            }
        }
        long averageLuminance = samples == 0 ? 0 : luminanceTotal / samples;
        return averageLuminance > 18 && maxLuminance - minLuminance > 12;
    }

    private void drawRealisticHeroImage(Canvas c,float x,float y,float w,float h) {
        // Use one complete dedicated banner image, never a split comparison or
        // the full-screen launcher background as a substitute unless offline.
        Bitmap artwork = isUsableArtwork(heroBannerArtwork) ? heroBannerArtwork
                : isUsableArtwork(heroArtwork) ? heroArtwork : backgroundArtwork;
        if (artwork != null) {
            android.graphics.ColorMatrix shaderTextureGrade = new android.graphics.ColorMatrix(new float[]{
                    1.18f, 0.02f, 0, 0, 5,
                    0.01f, 1.12f, 0, 0, 3,
                    0, 0.02f, 1.08f, 0, 2,
                    0, 0, 0, 1, 0
            });
            p.setColorFilter(new android.graphics.ColorMatrixColorFilter(shaderTextureGrade));
            drawCoverBitmap(c, artwork, x, y, w, h);
            p.setColorFilter(null);
            p.setShader(new LinearGradient(x, y, x, y+h,
                    Color.argb(12, 255, 180, 95),
                    Color.argb(82, 0, 12, 25),
                    Shader.TileMode.CLAMP));
            c.drawRect(x, y, x+w, y+h, p);
            p.setShader(null);
        } else {
            p.setColor(Color.rgb(24, 52, 60));
            c.drawRect(x, y, x+w, y+h, p);
        }
    }

    private void drawRealisticInstanceImage(Canvas c,float x,float y,float w,float h,int index) {
        Bitmap b = index >= 0 && index < versionBiomeArtworks.length
                ? versionBiomeArtworks[index] : null;
        if (b == null && versionBiomeArtworks.length > 0) {
            // If a newly added instance is beyond the initial pool, keep it
            // populated by deterministically reusing a different biome instead
            // of ever showing an empty version card.
            b = versionBiomeArtworks[Math.abs(index) % versionBiomeArtworks.length];
        }

        if (b != null) {
            // Cinematic shader-style grade: stronger contrast, saturation,
            // cool shadows, warm highlights and a soft vignette.
            android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix(new float[]{
                    1.16f, 0, 0, 0, 2,
                    0, 1.13f, 0, 0, 2,
                    0, 0, 1.20f, 0, 5,
                    0, 0, 0, 1, 0
            });
            p.setColorFilter(new android.graphics.ColorMatrixColorFilter(cm));
            // Draw the complete screenshot. Never crop to one half of a split comparison.
            drawCoverBitmap(c,b,x,y,w,h);
            p.setColorFilter(null);

            LinearGradient light = new LinearGradient(
                    x, y, x+w, y+h,
                    Color.argb(38,255,255,255),
                    Color.argb(95,0,8,20),
                    Shader.TileMode.CLAMP
            );
            p.setShader(light);
            c.drawRect(x,y,x+w,y+h,p);
            p.setShader(null);

            // Subtle cinematic vignette so the card reads like a shader-rendered
            // Minecraft screenshot rather than a flat thumbnail.
            RadialGradient vignette = new RadialGradient(
                    x+w*0.5f, y+h*0.42f, Math.max(w,h)*0.72f,
                    new int[]{Color.TRANSPARENT, Color.argb(105,0,0,0)},
                    new float[]{0.48f, 1f},
                    Shader.TileMode.CLAMP
            );
            p.setShader(vignette);
            c.drawRect(x,y,x+w,y+h,p);
            p.setShader(null);
        } else {
            round(c,x,y,x+w,y+h,18,Color.rgb(24,52,60),Color.TRANSPARENT,0);
        }
    }

    private void drawInstances(Canvas c,float x,float y,float w) {
        float gap=12, cw=(w-gap*2)/3f;
        float contentWidth = instanceCards.length * (cw + gap) - gap;
        float maxScroll = Math.max(0f, contentWidth - w);
        instanceScrollX = Math.max(0f, Math.min(instanceScrollX, maxScroll));

        c.save();
        c.clipRect(x, y, x+w, y+160);
        c.translate(-instanceScrollX, 0);

        int count = Math.max(3, instanceCards.length);
        for(int i=0;i<count;i++) {
            float xx=x+i*(cw+gap);
            // Keep every artwork strictly inside its own rounded card.
            c.save();
            Path cardClip = new Path();
            cardClip.addRoundRect(new RectF(xx, y, xx+cw, y+160), 12, 12, Path.Direction.CW);
            c.clipPath(cardClip);
            drawRealisticInstanceImage(c, xx, y, cw, 160, i);
            round(c,xx,y+100,xx+cw,y+160,0,Color.argb(215,3,25,39),Color.TRANSPARENT,0);
            c.restore();
            p.setShader(new LinearGradient(xx,y,xx+cw,y+160,Color.argb(28,0,230,170),Color.TRANSPARENT,Shader.TileMode.CLAMP));
            c.drawRoundRect(xx+1,y+1,xx+cw-1,y+159,14,14,p); p.setShader(null);
            round(c,xx,y,xx+cw,y+160,14,Color.TRANSPARENT,Color.rgb(28,91,113),1.1f);

            DisplayInstance instance = i < instanceCards.length ? instanceCards[i] : null;
            String name = instance != null && Tools.isValidString(instance.name)
                    ? instance.name : "No instance";
            String version = instance != null && Tools.isValidString(instance.versionId)
                    ? instance.versionId : "";

            drawInstanceIcon(c, instance, xx+15, y+112, 42, 42);
            text(c,name,xx+65,y+130,15,TEXT,true);
            if (!version.isEmpty()) {
                text(c,version,xx+65,y+151,12,TEXT,false);
            }

            pill(c,xx+cw-105,y+125,xx+cw-37,y+153,"▶ Play");
            // Protected three-dot menu zone, always above the artwork.
            round(c,xx+cw-35,y+118,xx+cw-7,y+153,10,Color.argb(225,3,20,30),Color.TRANSPARENT,0);
            text(c,"⋮",xx+cw-27,y+143,24,TEXT,true);
        }
        c.restore();
    }

    private void drawInstanceIcon(Canvas c, DisplayInstance instance,
                                  float x, float y, float w, float h) {
        if(instance != null) {
            Drawable icon = net.kdt.pojavlaunch.instances.InstanceIconProvider
                    .fetchIcon(getResources(), instance);
            if(icon != null) {
                icon.setBounds((int)x,(int)y,(int)(x+w),(int)(y+h));
                icon.draw(c);
                return;
            }
        }
        drawBitmap(c,blockIcon(),x,y,w,h);
    }

    private Bitmap blockCache;
    private Bitmap blockIcon() {
        if(blockCache!=null) return blockCache;
        Bitmap b=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(b); Paint q=new Paint(Paint.ANTI_ALIAS_FLAG);
        q.setColor(Color.rgb(84,52,31)); c.drawRect(8,25,56,57,q);
        q.setColor(Color.rgb(80,200,82)); Path top=new Path(); top.moveTo(8,25); top.lineTo(32,8); top.lineTo(56,25); top.close(); c.drawPath(top,q);
        q.setColor(Color.rgb(129,80,44)); c.drawRect(13,28,51,54,q);
        q.setColor(Color.rgb(94,197,73)); c.drawCircle(30,25,13,q);
        blockCache=b; return b;
    }

    private void openInstanceEditor(int card) {
        if(card < 0 || card >= instanceCards.length) return;
        DisplayInstance display = instanceCards[card];
        Instances.setSelectedInstance(display);
        MainMenuFragment host = getMainMenuHost();
        if (host != null) host.showCenterFragment(InstanceEditorFragment.class, InstanceEditorFragment.TAG, null);
        else Tools.swapFragment(activity, InstanceEditorFragment.class, InstanceEditorFragment.TAG, null);
    }

    private void drawBiome(Canvas c,float x,float y,float w,float h,int type) {
        Bitmap artwork = type == 0 ? cherryArtwork : type == 1 ? snowArtwork : badlandsArtwork;
        c.save();
        c.clipRect(x,y,x+w,y+h);
        drawBitmap(c,artwork,x,y,w,h);
        c.restore();
        round(c,x,y,x+w,y+h,12,Color.TRANSPARENT,Color.rgb(9,156,186),1.5f);
    }

    private void drawPine(Canvas c,float x,float y,float scale) {
        p.setColor(Color.rgb(86,58,40)); c.drawRect(x-2*scale,y,x+3*scale,y+30*scale,p);
        p.setColor(Color.rgb(25,78,63));
        Path q=new Path(); q.moveTo(x,y-28*scale); q.lineTo(x-17*scale,y+7*scale); q.lineTo(x+17*scale,y+7*scale); q.close(); c.drawPath(q,p);
        q.reset(); q.moveTo(x,y-16*scale); q.lineTo(x-21*scale,y+17*scale); q.lineTo(x+21*scale,y+17*scale); q.close(); c.drawPath(q,p);
    }

    private void drawRight(Canvas c) {
        float x=1230,w=288;
        panel(c,x,75,w,113);
        if(currentAccount != null) {
            Bitmap face = currentAccount.getSkinFace();
            drawBitmap(c,face != null ? face : blockIcon(),x+14,88,43,43);
            String username = Tools.isValidString(currentAccount.username)
                    ? currentAccount.username : "Minecraft account";
            text(c,username,x+67,101,15,TEXT,true);
            text(c,"Minecraft profile",x+67,121,11,MUTED,false);
            pillOutline(c,x+15,140,x+w-15,177,"Profile");
        } else {
            drawBitmap(c,blockIcon(),x+14,88,43,43);
            text(c,"Add Account",x+67,101,15,TEXT,true);
            text(c,"Sign in to your Minecraft account",x+67,121,11,MUTED,false);
            pillOutline(c,x+15,140,x+w-15,177,"＋  Add Account");
        }

        panel(c,x,196,w,67);
        drawBitmap(c,updateIcon,x+16,209,40,40);
        text(c,"Check Updates",x+67,224,14,TEXT,true);
        text(c,"Check for new versions and fixes",x+67,245,10,MUTED,false);
        text(c,"›",x+w-25,233,24,TEXT,true);

        drawSkinViewer(c, x, 274, w, 270);
    }

    private void drawUltraMode(Canvas c, float x, float y, float w, float h) {
        panel(c, x, y, w, h);
        drawBitmap(c, ultraIcon, x+16, y+13, 40, 40);
        text(c, "UrinthUltra Mode", x+68, y+27, 15, TEXT, true);
        text(c, "Enhanced performance mode", x+68, y+47, 11, MUTED, false);
        round(c, x+w-88, y+15, x+w-16, y+51, 18,
                ultraOn ? Color.rgb(7,126,105) : Color.rgb(34,51,61),
                ultraOn ? ACCENT : Color.rgb(87,108,118), 1.5f);
        p.setColor(ultraOn ? ACCENT : Color.rgb(142,161,169));
        c.drawCircle(ultraOn ? x+w-34 : x+w-70, y+33, 12, p);
    }

    private void drawSkinViewer(Canvas c, float x, float y, float w, float h) {
        panel(c, x, y, w, h);
        text(c, "Skin Viewer", x+16, y+25, 15, TEXT, true);
        text(c, currentAccount != null && Tools.isValidString(currentAccount.username)
                ? currentAccount.username : "Steve", x+16, y+44, 10, MUTED, false);
        float cx = x+w*0.5f;
        drawRotatingSkinModel(c, cx, y+58, 1.45f, skinRotationDegrees);
        round(c, x+14, y+h-37, x+w-14, y+h-12, 12,
                Color.argb(100,0,230,170), Color.rgb(0,174,148), 1f);
        text(c, "↔  DRAG TO ROTATE  ·  360°", x+31, y+h-21, 10, ACCENT, true);
    }

    private void drawRotatingSkinModel(Canvas c, float cx, float top, float scale, float angle) {
        float radians = (float) Math.toRadians(angle);
        float projectedWidth = 0.30f + 0.70f * Math.abs((float)Math.cos(radians));
        boolean frontFacing = (float)Math.cos(radians) >= 0;
        c.save();
        c.translate(cx, top);
        c.scale(projectedWidth, 1f);
        c.scale(scale, scale);

        // Simple block-model proportions matching Minecraft's classic Steve skin.
        float headL=-18, headR=18, headT=0, headB=36;
        float torsoL=-13, torsoR=13, torsoT=39, torsoB=73;
        float legT=76, legB=112;
        p.setColor(Color.rgb(52, 37, 28));
        c.drawRect(headL-1, headT-1, headR+1, headB+1, p);
        p.setColor(Color.rgb(115, 76, 48));
        c.drawRect(headL, headT, headR, headB, p);
        if (frontFacing) {
            Bitmap face = currentAccount == null ? null : currentAccount.getSkinFace();
            if (face != null && !face.isRecycled()) {
                drawBitmap(c, face, headL, headT, headR-headL, headB-headT);
            } else {
                drawSteveFaceOnModel(c, headL, headT, headR, headB);
            }
        } else {
            p.setColor(Color.rgb(87, 55, 36));
            c.drawRect(headL+2, headT+2, headR-2, headB-2, p);
            p.setColor(Color.rgb(43, 29, 23));
            c.drawRect(headL+2, headT+2, headR-2, headT+10, p);
        }

        // Arms and torso receive subtle shading to read as a rotating 3D model.
        p.setShader(new LinearGradient(torsoL, torsoT, torsoR, torsoT,
                Color.rgb(65, 145, 205), Color.rgb(28, 72, 139), Shader.TileMode.CLAMP));
        c.drawRect(torsoL, torsoT, torsoR, torsoB, p);
        p.setShader(null);
        p.setColor(Color.rgb(177, 123, 86));
        c.drawRect(-21, torsoT+2, torsoL-2, torsoB-2, p);
        c.drawRect(torsoR+2, torsoT+2, 21, torsoB-2, p);
        p.setColor(Color.rgb(38, 67, 151));
        c.drawRect(-13, legT, -1, legB, p);
        c.drawRect(1, legT, 13, legB, p);
        p.setColor(Color.rgb(30, 43, 67));
        c.drawRect(-13, legB-6, -1, legB, p);
        c.drawRect(1, legB-6, 13, legB, p);
        // Rotate the torso/limbs' visible lighting with the user's drag angle.
        int shade = (int)(25 * (1f - Math.abs((float)Math.cos(radians))));
        p.setColor(Color.argb(shade, 0, 0, 0));
        c.drawRect(-22, 0, 22, legB, p);
        c.restore();
    }

    private void drawSteveFaceOnModel(Canvas c, float l, float t, float r, float b) {
        p.setColor(Color.rgb(198,142,103));
        c.drawRect(l,t,r,b,p);
        p.setColor(Color.rgb(53,35,26));
        c.drawRect(l,t,r,t+8,p);
        p.setColor(Color.WHITE);
        c.drawRect(l+5,t+14,l+13,t+21,p);
        c.drawRect(r-13,t+14,r-5,t+21,p);
        p.setColor(Color.rgb(50,100,177));
        c.drawRect(l+7,t+15,l+11,t+21,p);
        c.drawRect(r-11,t+15,r-7,t+21,p);
        p.setColor(Color.rgb(87,47,34));
        c.drawRect(l+7,b-8,r-7,b-3,p);
    }


    public void showAuthChooser() {
        accountChooserOpen = false;
        authChooserOpen = true;
        invalidate();
    }

    private void drawOverlayDim(Canvas c) {
        p.setColor(Color.argb(135, 0, 8, 15));
        c.drawRect(0, 0, W, H, p);
    }

    private void drawAuthChooser(Canvas c) {
        drawOverlayDim(c);
        float w = 500, h = 330;
        float x = (W - w) / 2f, y = (H - h) / 2f;
        round(c, x, y, x+w, y+h, 18, Color.rgb(6,31,47), Color.rgb(28,154,177), 2f);
        drawBitmap(c, logo, x+28, y+25, 42, 42);
        text(c, "Add Account", x+82, y+53, 22, TEXT, true);
        text(c, "Choose a real authentication method", x+82, y+76, 11, MUTED, false);
        drawAuthButton(c, x+28, y+102, x+w-28, y+159, "Microsoft", "Official Microsoft account", 0);
        drawAuthButton(c, x+28, y+170, x+w-28, y+227, "Ely.by", "Ely.by account", 1);
        drawAuthButton(c, x+28, y+238, x+w-28, y+295, "Local", "Offline local account", 2);
        text(c, "×", x+w-30, y+28, 22, MUTED, true);
    }

    private void drawAuthButton(Canvas c, float x, float y, float x2, float y2, String title, String sub, int type) {
        round(c, x, y, x2, y2, 12, PANEL_2, Color.rgb(19,91,112), 1.2f);
        p.setColor(type == 0 ? Color.rgb(44,117,224) : type == 1 ? Color.rgb(115,70,188) : Color.rgb(78,102,113));
        c.drawCircle(x+29, (y+y2)/2f, 16, p);
        text(c, type == 0 ? "M" : type == 1 ? "E" : "L", x+22, (y+y2)/2f+6, 15, TEXT, true);
        text(c, title, x+58, y+25, 14, TEXT, true);
        text(c, sub, x+58, y+44, 10, MUTED, false);
        text(c, "›", x2-25, (y+y2)/2f+7, 23, ACCENT, true);
    }

    private void drawAccountChooser(Canvas c) {
        drawOverlayDim(c);
        float x = 24, y = 72, w = 430;
        int count = Math.max(1, savedAccounts.length);
        float h = 92 + count * 62;
        round(c, x, y, x+w, y+h, 16, Color.rgb(6,31,47), Color.rgb(28,154,177), 2f);
        text(c, "Accounts", x+22, y+32, 20, TEXT, true);
        text(c, "Switch Minecraft profile", x+22, y+51, 10, MUTED, false);
        if (savedAccounts.length == 0) {
            text(c, "No saved accounts", x+22, y+86, 12, MUTED, false);
        } else {
            for (int i=0; i<savedAccounts.length; i++) {
                Account a = savedAccounts[i];
                float yy = y+66+i*62;
                if (a == currentAccount || (currentAccount != null && a.username.equals(currentAccount.username)))
                    round(c, x+14, yy, x+w-14, yy+52, 11, Color.rgb(7,79,76), Color.rgb(0,225,180), 1.2f);
                Bitmap face = a.getSkinFace();
                drawBitmap(c, face != null ? face : blockIcon(), x+25, yy+7, 38, 38);
                text(c, Tools.isValidString(a.username) ? a.username : "Minecraft account", x+76, yy+22, 13, TEXT, true);
                text(c, a.authType != null ? a.authType.name() : "Account", x+76, yy+40, 9, MUTED, false);
            }
        }
        float addY = y+66+count*62;
        round(c, x+14, addY, x+w-14, addY+52, 11, PANEL_2, ACCENT, 1.2f);
        text(c, "+", x+30, addY+34, 23, ACCENT, true);
        text(c, "Add Account", x+65, addY+32, 13, TEXT, true);
    }

    private void panel(Canvas c,float x,float y,float w,float h) {
        round(c,x,y,x+w,y+h,13,PANEL,Color.rgb(13,112,141),1.5f);
    }

    private void pill(Canvas c,float x,float y,float x2,float y2,String s) {
        round(c,x,y,x2,y2,22,ACCENT,ACCENT,1);
        float tw=p.measureText(s); text(c,s,(x+x2)/2f-tw/2f,y+(y2-y)*.68f,11,Color.rgb(0,34,36),true);
    }

    private void pillOutline(Canvas c,float x,float y,float x2,float y2,String s) {
        round(c,x,y,x2,y2,18,Color.TRANSPARENT,ACCENT,1.5f);
        float tw=measure(s,11,true); text(c,s,(x+x2)/2f-tw/2f,y+(y2-y)*.68f,11,ACCENT,true);
    }

    private void round(Canvas c,float l,float t,float rr,float bb,float rad,int fill,int edge,float sw) {
        r.set(l,t,rr,bb);
        p.setStyle(Paint.Style.FILL); p.setColor(fill); p.setShader(null); c.drawRoundRect(r,rad,rad,p);
        if(edge!=Color.TRANSPARENT && sw>0){stroke.setColor(edge);stroke.setStrokeWidth(sw);c.drawRoundRect(r,rad,rad,stroke);}
    }

    private void text(Canvas c,String s,float x,float baseline,float size,int color,boolean bold) {
        p.setStyle(Paint.Style.FILL);p.setShader(null);p.setColor(color);p.setTextSize(size);p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));c.drawText(s,x,baseline,p);
    }

    private float measure(String s,float size,boolean bold){p.setTextSize(size);p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));return p.measureText(s);}

    private void drawBitmap(Canvas c,Bitmap b,float x,float y,float w,float h){if(b!=null)c.drawBitmap(b,null,new RectF(x,y,x+w,y+h),p);}

    private void drawCoverBitmap(Canvas c, Bitmap b, float x, float y, float w, float h) {
        if (b == null) return;
        float scale = Math.max(w / b.getWidth(), h / b.getHeight());
        float dw = b.getWidth() * scale;
        float dh = b.getHeight() * scale;
        float dx = x + (w - dw) * 0.5f;
        float dy = y + (h - dh) * 0.5f;
        c.drawBitmap(b, null, new RectF(dx, dy, dx + dw, dy + dh), p);
    }

    private void drawHamburger(Canvas c,float x,float y){p.setColor(TEXT);p.setStrokeWidth(3);for(int i=-1;i<=1;i++)c.drawLine(x-13,y+i*8,x+13,y+i*8,p);}

    private void drawCircleIcon(Canvas c,float x,float y,String s){p.setColor(Color.rgb(6,115,111));c.drawCircle(x,y,22,p);text(c,s,x-8,y+8,22,ACCENT,true);}
    private void drawNavIcon(Canvas c,float x,float y,int type){
        p.setColor(TEXT); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f);
        Path q = new Path();
        switch(type){
            case 0: // Home
                q.moveTo(x-13,y-2); q.lineTo(x,y-14); q.lineTo(x+13,y-2);
                q.moveTo(x-10,y-3); q.lineTo(x-10,y+11); q.lineTo(x+10,y+11); q.lineTo(x+10,y-3);
                c.drawPath(q,p); c.drawRect(x-3,y+3,x+3,y+11,p); break;
            case 1: // Instances
                c.drawRoundRect(x-12,y-10,x+12,y+10,3,3,p);
                c.drawLine(x-7,y-5,x+7,y-5,p); c.drawLine(x-7,y,x+7,y,p); c.drawLine(x-7,y+5,x+3,y+5,p); break;
            case 2: // Mods
                c.drawCircle(x,y,11,p); c.drawLine(x-16,y,x+16,y,p); c.drawLine(x,y-16,x,y+16,p);
                c.drawCircle(x,y,3,p); break;
            case 3: // Resource Packs
                c.drawRect(x-12,y-10,x+12,y+10,p); c.drawLine(x-4,y-10,x-4,y+10,p);
                c.drawLine(x+4,y-10,x+4,y+10,p); break;
            case 4: // Servers
                c.drawRoundRect(x-13,y-11,x+13,y+11,3,3,p); c.drawLine(x-9,y-4,x+9,y-4,p);
                c.drawCircle(x-7,y+4,1,p); c.drawCircle(x-1,y+4,1,p); c.drawCircle(x+5,y+4,1,p); break;
            case 5: // Settings
                c.drawCircle(x,y,6,p); for(int i=0;i<8;i++){double a=i*Math.PI/4; c.drawLine(x+(float)Math.cos(a)*9,y+(float)Math.sin(a)*9,x+(float)Math.cos(a)*14,y+(float)Math.sin(a)*14,p);} break;
            case 6: // Modpacks
                c.drawRect(x-13,y-9,x-1,y+8,p); c.drawRect(x+1,y-9,x+13,y+8,p); c.drawLine(x-13,y-2,x-1,y-2,p); c.drawLine(x+1,y-2,x+13,y-2,p); break;
            case 7: // Shaders
                c.drawLine(x-13,y+9,x+13,y-9,p); c.drawCircle(x-7,y-7,3,p); c.drawCircle(x+7,y+7,3,p); break;
            default: // Worlds
                c.drawCircle(x,y,12,p); c.drawLine(x-12,y,x+12,y,p); c.drawLine(x,y-12,x,y+12,p);
                c.drawArc(x-7,y-12,x+7,y+12,0,180,false,p); break;
        }
        p.setStyle(Paint.Style.FILL);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x=e.getX()/sx,y=e.getY()/sy;
        Context c=getContext();

        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            touchDownX = x;
            lastTouchX = x;
            draggingSkinViewer = x >= 1230 && x < 1536 && y >= 274 && y < 544;
            draggingInstances = selectedPage == 0 && x >= 242 && x < 1215 && y >= 258 && y < 418;
            return true;
        }

        if (e.getAction() == MotionEvent.ACTION_MOVE) {
            if (draggingSkinViewer) {
                skinRotationDegrees = (skinRotationDegrees + (x - lastTouchX) * 1.35f) % 360f;
                if (skinRotationDegrees < 0) skinRotationDegrees += 360f;
                lastTouchX = x;
                invalidate();
                return true;
            }
            if (draggingInstances && selectedPage == 0) {
                float delta = x - lastTouchX;
                float gap = 12f, cw = (1215f-242f-gap*2f)/3f;
                float contentWidth = instanceCards.length * (cw + gap) - gap;
                float maxScroll = Math.max(0f, contentWidth - (1215f-242f));
                instanceScrollX = Math.max(0f, Math.min(maxScroll, instanceScrollX - delta));
                lastTouchX = x;
                invalidate();
            }
            return true;
        }

        if (e.getAction() != MotionEvent.ACTION_UP) return true;
        if (draggingSkinViewer) {
            draggingSkinViewer = false;
            return true;
        }

        if (authChooserOpen) {
            if (x >= 488 && x <= 1048 && y >= 178 && y < 550) {
                float dialogY = (H-330f)/2f;
                if (y >= dialogY+102 && y < dialogY+159) {
                    authChooserOpen = false; invalidate();
                    Tools.swapFragment(activity, MicrosoftLoginFragment.class, MicrosoftLoginFragment.TAG, null);
                    return true;
                }
                if (y >= dialogY+170 && y < dialogY+227) {
                    authChooserOpen = false; invalidate();
                    Tools.swapFragment(activity, ElyByLoginFragment.class, ElyByLoginFragment.TAG, null);
                    return true;
                }
                if (y >= dialogY+238 && y < dialogY+295) {
                    authChooserOpen = false; invalidate();
                    Tools.swapFragment(activity, LocalLoginFragment.class, LocalLoginFragment.TAG, null);
                    return true;
                }
            }
            authChooserOpen = false; invalidate(); return true;
        }

        if (accountChooserOpen) {
            float ax=24, ay=72, aw=430;
            int count=Math.max(1,savedAccounts.length);
            for(int i=0;i<savedAccounts.length;i++) {
                float yy=ay+66+i*62;
                if(x>=ax+14 && x<=ax+aw-14 && y>=yy && y<yy+52) {
                    Accounts.setCurrent(savedAccounts[i]);
                    currentAccount=savedAccounts[i];
                    accountChooserOpen=false; invalidate(); return true;
                }
            }
            float addY=ay+66+count*62;
            if(x>=ax+14 && x<=ax+aw-14 && y>=addY && y<addY+52) {
                accountChooserOpen=false; authChooserOpen=true; invalidate(); return true;
            }
            accountChooserOpen=false; invalidate(); return true;
        }

        if (draggingInstances && Math.abs(x - touchDownX) > 12f) {
            draggingInstances = false;
            return true;
        }
        draggingInstances = false;
        if(y<62 && x<75){menuOpen=!menuOpen;invalidate();return true;}
        if(menuOpen && x<228 && y>=72 && y<365){
            int idx=(int)((y-72)/48);
            if(idx==0 || idx==1) {
                MainMenuFragment host = getMainMenuHost();
                if (host != null && host.isCenterContentVisible()) host.closeCenterContent();
                selectedPage = idx;
            }
            else if(idx==2){selectedPage=2;openContentCategory("mod");}
            else if(idx==3){selectedPage=3;openContentCategory("resourcepack");}
            else if(idx==4){selectedPage=4;}
            else if(idx==5){selectedPage=5; MainMenuFragment host = getMainMenuHost(); if (host != null) host.showCenterFragment(LauncherPreferenceFragment.class, LauncherActivity.SETTING_FRAGMENT_TAG, null);}
            invalidate(); return true;
        }
        if(menuOpen && x<228 && y>=378 && y<522){
            int idx=(int)((y-378)/48);
            if(idx==0){selectedPage=6;openContentCategory("modpack");}
            else if(idx==1){selectedPage=7;openContentCategory("shader");}
            else if(idx==2){selectedPage=8;openContentCategory("world");}
            invalidate();
            return true;
        }
        if (selectedPage == 1 && x >= 242 && x < 1215 && y >= 75 && y < 660) {
            float left=menuOpen?242:18f, width=1215f-left, gap=14f, cardW=(width-4*gap)/3f;
            float startX=left+gap, startY=163f, cardH=140f, rowGap=14f;
            if (y >= startY && y < startY+cardH) {
                int col=(int)((x-startX)/(cardW+gap));
                if (col>=0 && col<3 && x < startX+3*cardW+2*gap) {
                    launchInstanceOption(col); return true;
                }
            }
            float row2=startY+cardH+rowGap;
            if (y >= row2 && y < row2+cardH) {
                int col=(int)((x-startX)/(cardW+gap));
                if (col>=0 && col<3 && x < startX+3*cardW+2*gap) {
                    launchInstanceOption(3+col); return true;
                }
            }
            float row3=row2+cardH+rowGap;
            if (y >= row3 && y < row3+cardH && x >= startX && x < startX+cardW) {
                launchInstanceOption(6); return true;
            }
            return true;
        }
        if(x>(menuOpen ? 242f : 18f) && x<1215 && y>=258 && y<418){
            float left = menuOpen ? 242f : 18f;
            float gap=12f, cw=(1215f-left-gap*2f)/3f;
            float contentX = x + instanceScrollX;
            int card=(int)((contentX-left)/(cw+gap));
            if(card>=0 && card<instanceCards.length) {
                float cardX=left+card*(cw+gap);
                // Only the explicit Play pill launches Minecraft. Artwork/title taps
                // must never launch a version accidentally.
                boolean playPressed = contentX >= cardX+cw-105f
                        && contentX <= cardX+cw-37f && y >= 383f && y < 411f;
                boolean menuPressed = contentX >= cardX+cw-35f
                        && contentX <= cardX+cw-7f && y >= 376f && y < 411f;
                if (menuPressed) {
                    openInstanceEditor(card);
                } else if (playPressed) {
                    Instances.setSelectedInstance(instanceCards[card]);
                    ExtraCore.setValue(ExtraConstants.LAUNCH_GAME,true);
                }
            }
            return true;
        }
        if(x>=1230 && y>=75 && y<188){
            if(currentAccount != null) {
                accountChooserOpen = true;
            } else {
                authChooserOpen = true;
            }
            invalidate();
            return true;
        }
        float mainLeft = menuOpen ? 242f : 18f;
        if(selectedPage == 0 && x >= mainLeft && x < mainLeft + Math.min(1215f-mainLeft, 420f)
                && y >= 435f && y < 501f) {
            ultraOn=!ultraOn;
            c.getSharedPreferences("urinth_ui",Context.MODE_PRIVATE).edit().putBoolean("ultra",ultraOn).apply();
            invalidate();
            return true;
        }
        return true;
    }
}