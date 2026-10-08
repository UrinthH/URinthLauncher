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

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;
import net.kdt.pojavlaunch.tasks.AsyncVersionList;
import net.kdt.pojavlaunch.utils.FileUtils;

import java.io.File;

import git.artdeell.mojo.R;

public final class ModrinthLauncherView extends View {
    private static final float W = 1536f;
    private static final float H = 686f;
    private static final int BG = Color.rgb(3, 18, 29);
    private static final int PANEL = Color.rgb(5, 28, 43);
    private static final int PANEL_2 = Color.rgb(7, 37, 54);
    private static final int TEXT = Color.rgb(242, 248, 250);
    private static final int MUTED = Color.rgb(163, 184, 195);
    private static final int ACCENT = Color.rgb(0, 225, 180);
    private static final int CYAN = Color.rgb(34, 181, 224);
    private static final int LINE = Color.rgb(18, 112, 139);

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Bitmap logo;
    private final Bitmap discord;
    private final mcVersionSpinner versionSpinner;
    private final FragmentActivity activity;
    private final Runnable modInstaller;
    private boolean menuOpen = true;
    private boolean ultraOn;
    private float sx = 1f, sy = 1f;

    public ModrinthLauncherView(FragmentActivity activity, mcVersionSpinner spinner, Runnable installer) {
        super(activity);
        this.activity = activity;
        versionSpinner = spinner;
        modInstaller = installer;
        logo = bitmap(R.drawable.ic_modrinth);
        discord = bitmap(R.drawable.ic_discord);
        SharedPreferences prefs = context.getSharedPreferences("urinth_ui", Context.MODE_PRIVATE);
        ultraOn = prefs.getBoolean("ultra", true);
        p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f);
        setFocusable(true);
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
        sx = getWidth() / W;
        sy = getHeight() / H;
        canvas.save();
        canvas.scale(sx, sy);
        drawBackground(canvas);
        drawHeader(canvas);
        if (menuOpen) drawSidebar(canvas);
        drawMain(canvas);
        drawRight(canvas);
        canvas.restore();
    }

    private void drawBackground(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0,0,0,H,
                Color.rgb(3,19,31), Color.rgb(2,13,24), Shader.TileMode.CLAMP));
        c.drawRect(0,0,W,H,p);
        p.setShader(null);
        p.setColor(Color.rgb(5,25,38));
        c.drawRect(0,58,W,H,p);
    }

    private void drawHeader(Canvas c) {
        p.setColor(Color.rgb(3,17,28)); c.drawRect(0,0,W,62,p);
        drawHamburger(c,38,31);
        drawBitmap(c,logo,80,10,48,48);
        text(c,"Modrinth",143,39,27,TEXT,true);
        text(c,"Launcher",247,39,27,ACCENT,true);
        p.setColor(Color.rgb(36,99,116)); c.drawRect(370,17,372,45,p);
        text(c,"Made By: Macase, Nile.",395,37,14,ACCENT,true);
        drawSettings(c,1398,31);
    }

    private void drawSidebar(Canvas c) {
        float x=0, y=62, w=228;
        p.setColor(Color.rgb(4,25,39)); c.drawRect(x,y,w,H,p);
        String[] labels={"Home","Instances","Mods","Resource Packs","Servers","Settings"};
        for(int i=0;i<labels.length;i++) {
            float yy=72+i*48;
            if(i==0) round(c,19,yy,215,yy+42,12,Color.rgb(7,105,91),ACCENT,1.5f);
            drawNavIcon(c,43,yy+21,i);
            text(c,labels[i],75,yy+27,14,i==0?ACCENT:TEXT,true);
        }
        p.setColor(Color.rgb(21,94,112)); c.drawRect(20,365,205,366,p);
        String[] more={"Modpacks","Shaders","Worlds"};
        for(int i=0;i<3;i++) {
            float yy=378+i*48;
            drawNavIcon(c,43,yy+21,6+i);
            text(c,more[i],75,yy+27,14,TEXT,true);
        }
        round(c,19,522,215,602,12,Color.rgb(5,44,55),ACCENT,1.5f);
        drawBitmap(c,logo,31,536,42,42);
        text(c,"Modrinth",87,546,15,TEXT,true);
        text(c,"Better Minecraft",87,564,11,MUTED,false);
        text(c,"Together",87,580,11,MUTED,false);
        text(c,"›",196,564,26,ACCENT,true);
        p.setColor(Color.rgb(14,61,75)); c.drawRect(0,625,228,H,p);
        drawBitmap(c,logo,25,632,29,29);
        text(c,"Modrinth",67,651,14,TEXT,true);
    }

    private void drawMain(Canvas c) {
        float left=menuOpen?242:18, right=1215;
        float width=right-left;
        drawHero(c,left,75,width,170);
        drawInstances(c,left,258,width);
        drawMods(c,left,505,width);
    }

    private void drawHero(Canvas c,float x,float y,float w,float h) {
        round(c,x,y,x+w,y+h,14,Color.rgb(9,38,57),CYAN,1.5f);
        RectF clip=new RectF(x+1,y+1,x+w-1,y+h-1);
        c.save(); c.clipRect(clip);

        p.setShader(new LinearGradient(x,y,x+w,y+h*.75f,
                Color.rgb(38,57,133),Color.rgb(245,105,102),Shader.TileMode.CLAMP));
        c.drawRect(clip,p); p.setShader(null);

        // Soft blocky clouds.
        drawCloud(c,x+95,y+42,1.0f);
        drawCloud(c,x+w-360,y+34,0.8f);

        // Ocean and layered horizon.
        p.setColor(Color.rgb(19,103,139)); c.drawRect(x,y+h*.58f,x+w,y+h,p);
        p.setColor(Color.rgb(17,82,119)); c.drawRect(x,y+h*.70f,x+w,y+h,p);
        p.setColor(Color.rgb(15,69,102)); c.drawRect(x,y+h*.82f,x+w,y+h,p);

        // Sun glow and sun.
        p.setColor(Color.argb(55,255,241,177)); c.drawCircle(x+w*.72f,y+h*.45f,43,p);
        p.setColor(Color.rgb(255,239,167)); c.drawCircle(x+w*.72f,y+h*.45f,25,p);

        // Distant blocky islands.
        p.setColor(Color.rgb(31,67,74));
        Path island=new Path();
        island.moveTo(x+305,y+h*.62f); island.lineTo(x+395,y+h*.40f); island.lineTo(x+455,y+h*.55f);
        island.lineTo(x+520,y+h*.62f); island.close(); c.drawPath(island,p);
        island.reset();
        island.moveTo(x+w-330,y+h*.62f); island.lineTo(x+w-240,y+h*.38f); island.lineTo(x+w-175,y+h*.54f);
        island.lineTo(x+w-110,y+h*.62f); island.close(); c.drawPath(island,p);

        // Minecraft-like sailing boat.
        p.setColor(Color.rgb(39,31,37)); c.drawRoundRect(x+w*.585f,y+h*.70f,x+w*.685f,y+h*.76f,8,8,p);
        p.setColor(Color.rgb(119,72,44)); c.drawRect(x+w*.61f,y+h*.64f,x+w*.62f,y+h*.72f,p);
        p.setColor(Color.rgb(247,231,196));
        Path sail=new Path(); sail.moveTo(x+w*.62f,y+h*.70f); sail.lineTo(x+w*.62f,y+h*.39f); sail.lineTo(x+w*.69f,y+h*.68f); sail.close(); c.drawPath(sail,p);
        p.setColor(Color.rgb(218,191,151));
        Path sail2=new Path(); sail2.moveTo(x+w*.62f,y+h*.70f); sail2.lineTo(x+w*.62f,y+h*.48f); sail2.lineTo(x+w*.665f,y+h*.68f); sail2.close(); c.drawPath(sail2,p);

        // Water highlights.
        p.setColor(Color.argb(120,133,210,224));
        for(int i=0;i<8;i++){
            float yy=y+h*.72f+(i%3)*17;
            float xx=x+315+i*83;
            c.drawRect(xx,yy,xx+58,yy+2,p);
        }
        c.restore();

        // Text stays over the artwork like the reference.
        text(c,"Play, Explore,",x+22,y+53,27,TEXT,true);
        text(c,"Create, Together.",x+22,y+82,27,TEXT,true);
        text(c,"The best Minecraft experience,",x+22,y+110,13,TEXT,false);
        text(c,"now on your Android device.",x+22,y+128,13,TEXT,false);
    }

    private void drawCloud(Canvas c,float x,float y,float scale) {
        p.setColor(Color.argb(105,255,255,255));
        c.drawRect(x,y+13*scale,x+88*scale,y+31*scale,p);
        c.drawCircle(x+25*scale,y+16*scale,18*scale,p);
        c.drawCircle(x+50*scale,y+9*scale,24*scale,p);
        c.drawCircle(x+72*scale,y+17*scale,16*scale,p);
    }

    private void drawInstances(Canvas c,float x,float y,float w) {
        float gap=12, cw=(w-gap*2)/3f;
        for(int i=0;i<3;i++) {
            float xx=x+i*(cw+gap);
            drawBiome(c,xx,y,cw,160,i);
            round(c,xx,y+100,xx+cw,y+160,0,Color.argb(225,3,25,39),Color.TRANSPARENT,0);
            drawBitmap(c,blockIcon(),xx+15,y+112,42,42);
            text(c,"URinthH",xx+65,y+130,15,TEXT,true);
            text(c,"Vannila 26.3.",xx+65,y+151,12,TEXT,false);
            pill(c,xx+cw-105,y+125,xx+cw-37,y+153,"▶ Play");
            text(c,"⋮",xx+cw-20,y+140,24,TEXT,true);
        }
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

    private void drawBiome(Canvas c,float x,float y,float w,float h,int type) {
        c.save(); c.clipRect(x,y,x+w,y+h);

        int top=type==0?Color.rgb(86,39,122):type==1?Color.rgb(66,128,190):Color.rgb(194,91,37);
        int bottom=type==0?Color.rgb(224,104,157):type==1?Color.rgb(179,220,250):Color.rgb(242,156,55);
        p.setShader(new LinearGradient(x,y,x,y+h,top,bottom,Shader.TileMode.CLAMP));
        c.drawRect(x,y,x+w,y+h,p); p.setShader(null);

        // Soft sky light.
        p.setColor(Color.argb(100,255,241,197)); c.drawCircle(x+w*.72f,y+h*.30f,18,p);

        if(type==0) {
            // Cherry grove: trunks, branches, layered pink leaf clusters.
            p.setColor(Color.rgb(87,52,62));
            for(int i=0;i<7;i++){
                float tx=x+18+i*(w-36)/6f;
                float base=y+122-(i%2)*5;
                c.drawRect(tx,base-55,tx+9,base+28,p);
                c.drawRect(tx+5,base-47,tx+31,base-40,p);
                p.setColor(Color.rgb(245,104,166)); c.drawCircle(tx+6,base-61,17,p);
                p.setColor(Color.rgb(224,77,147)); c.drawCircle(tx+22,base-53,15,p);
                p.setColor(Color.rgb(255,139,186)); c.drawCircle(tx-5,base-47,13,p);
                p.setColor(Color.rgb(87,52,62));
            }
            p.setColor(Color.rgb(44,93,55)); c.drawRect(x,y+125,x+w,y+h,p);
            p.setColor(Color.rgb(117,70,91)); c.drawRect(x,y+118,x+w,y+125,p);
            for(int i=0;i<8;i++){p.setColor(Color.rgb(255,183,211));c.drawCircle(x+22+i*42,y+132+(i%2)*8,4,p);}
        } else if(type==1) {
            // Snowy mountains with spruce silhouettes.
            p.setColor(Color.rgb(244,249,255));
            Path m=new Path(); m.moveTo(x,y+120);m.lineTo(x+62,y+48);m.lineTo(x+111,y+104);m.lineTo(x+170,y+34);m.lineTo(x+w,y+112);m.lineTo(x+w,y+160);m.lineTo(x,y+160);m.close();c.drawPath(m,p);
            p.setColor(Color.rgb(203,226,245));
            Path shade=new Path(); shade.moveTo(x+62,y+48);shade.lineTo(x+86,y+93);shade.lineTo(x+74,y+90);shade.close();c.drawPath(shade,p);
            p.setColor(Color.rgb(38,81,76)); c.drawRect(x,y+119,x+w,y+160,p);
            for(int i=0;i<8;i++) drawPine(c,x+18+i*43,y+111-(i%3)*6,0.62f);
            p.setColor(Color.rgb(235,247,255));
            for(int i=0;i<14;i++) c.drawCircle(x+12+i*31,y+138+(i%2)*8,2,p);
        } else {
            // Badlands/desert: layered mesas and red-orange terrain.
            p.setColor(Color.rgb(137,61,40));
            for(int layer=0;layer<3;layer++){
                Path mesa=new Path();
                float yy=y+84+layer*15;
                mesa.moveTo(x,yy+30); mesa.lineTo(x+35,yy+8); mesa.lineTo(x+82,yy+20);
                mesa.lineTo(x+120,yy-20); mesa.lineTo(x+172,yy+15); mesa.lineTo(x+225,yy-5);
                mesa.lineTo(x+w,yy+22); mesa.lineTo(x+w,y+h); mesa.lineTo(x,y+h); mesa.close();
                c.drawPath(mesa,p);
                p.setColor(layer==0?Color.rgb(223,105,51):layer==1?Color.rgb(193,78,40):Color.rgb(157,64,38));
            }
            p.setColor(Color.rgb(241,143,61)); c.drawRect(x,y+128,x+w,y+h,p);
            p.setColor(Color.rgb(107,57,39));
            for(int i=0;i<5;i++){float tx=x+34+i*66; c.drawRect(tx,y+106,tx+7,y+133,p); c.drawCircle(tx+3,y+103,10,p);}
            p.setColor(Color.rgb(255,192,91)); c.drawCircle(x+w*.72f,y+h*.30f,20,p);
        }

        c.restore();
        round(c,x,y,x+w,y+h,12,Color.TRANSPARENT,Color.rgb(9,156,186),1.5f);
    }

    private void drawPine(Canvas c,float x,float y,float scale) {
        p.setColor(Color.rgb(86,58,40)); c.drawRect(x-2*scale,y,x+3*scale,y+30*scale,p);
        p.setColor(Color.rgb(25,78,63));
        Path q=new Path(); q.moveTo(x,y-28*scale); q.lineTo(x-17*scale,y+7*scale); q.lineTo(x+17*scale,y+7*scale); q.close(); c.drawPath(q,p);
        q.reset(); q.moveTo(x,y-16*scale); q.lineTo(x-21*scale,y+17*scale); q.lineTo(x+21*scale,y+17*scale); q.close(); c.drawPath(q,p);
    }

    private void drawMods(Canvas c,float x,float y,float w) {
        text(c,"★",x+3,y+30,31,TEXT,true);
        text(c,"Latest Mods",x+52,y+28,21,TEXT,true);
        pillOutline(c,x+w-95,y+2,x+w,y+40,"▦  View All");
        String[][] mods={{"Sodium","NeoForge 1.21.1"},{"Lithium","Fabric 1.21.1"},{"Iris","Fabric 1.21.1"},{"Distant Horizons","Forge 1.21.1"},{"Xaero's Minimap","Forge 1.21.1"}};
        float gap=10,cw=(w-gap*4)/5f;
        for(int i=0;i<5;i++) {
            float xx=x+i*(cw+gap);
            round(c,xx,y+48,xx+cw,y+130,11,PANEL_2,Color.rgb(11,122,154),1.2f);
            drawModIcon(c,xx+13,y+59,i);
            text(c,mods[i][0],xx+57,y+73,12,TEXT,true);
            text(c,mods[i][1],xx+57,y+94,10,MUTED,false);
            pill(c,xx+cw-63,y+101,xx+cw-12,y+125,"Add");
        }
    }

    private void drawModIcon(Canvas c,float x,float y,int i) {
        int[] colors={Color.rgb(142,209,79),Color.rgb(158,72,245),Color.rgb(245,91,176),Color.rgb(88,157,220),Color.rgb(112,125,151)};
        p.setColor(colors[i]); c.drawRoundRect(x,y,x+39,y+39,10,10,p);
        text(c,i==0?"S":i==1?"ϟ":i==2?"✿":i==3?"◉":"⊘",x+10,y+27,20,TEXT,true);
    }

    private void drawRight(Canvas c) {
        float x=1230,w=288;
        panel(c,x,75,w,113);
        drawBitmap(c,blockIcon(),x+14,88,43,43);
        text(c,"Add Account",x+67,101,15,TEXT,true);
        text(c,"Sign in to your Minecraft account",x+67,121,11,MUTED,false);
        pillOutline(c,x+15,140,x+w-15,177,"＋  Add Account");

        panel(c,x,196,w,67);
        drawCircleIcon(c,x+36,229,"↻");
        text(c,"Check Updates",x+67,224,14,TEXT,true);
        text(c,"Check for new versions and fixes",x+67,245,10,MUTED,false);
        text(c,"›",x+w-25,233,24,TEXT,true);

        panel(c,x,274,w,78);
        drawCircleIcon(c,x+36,310,"⚡");
        text(c,"UrinthUltra Mode",x+67,302,14,TEXT,true);
        text(c,"Enable ultra performance mode",x+67,323,10,MUTED,false);
        pill(c,x+w-86,292,x+w-52,322,"OFF");
        pill(c,x+w-56,292,x+w-16,322,ultraOn?"ON":"OFF");

        round(c,x,362,x+w,435,13,Color.rgb(49,66,165),Color.rgb(80,107,245),1.5f);
        drawBitmap(c,discord,x+18,377,48,48);
        text(c,"Discord",x+82,407,17,TEXT,true);
        text(c,"›",x+w-27,409,25,TEXT,true);
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

    private void drawHamburger(Canvas c,float x,float y){p.setColor(TEXT);p.setStrokeWidth(3);for(int i=-1;i<=1;i++)c.drawLine(x-13,y+i*8,x+13,y+i*8,p);}
    private void drawSettings(Canvas c,float x,float y){p.setColor(TEXT);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);c.drawCircle(x,y,10,p);for(int i=0;i<8;i++){double a=i*Math.PI/4; c.drawLine(x+(float)Math.cos(a)*12,y+(float)Math.sin(a)*12,x+(float)Math.cos(a)*16,y+(float)Math.sin(a)*16,p);}p.setStyle(Paint.Style.FILL);}

    private void drawCircleIcon(Canvas c,float x,float y,String s){p.setColor(Color.rgb(6,115,111));c.drawCircle(x,y,22,p);text(c,s,x-8,y+8,22,ACCENT,true);}
    private void drawNavIcon(Canvas c,float x,float y,int type){p.setColor(TEXT);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);if(type==0){c.drawRect(x-10,y-8,x+10,y+10,p);Path q=new Path();q.moveTo(x-14,y-8);q.lineTo(x,y-18);q.lineTo(x+14,y-8);q.close();c.drawPath(q,p);}else if(type==2){c.drawCircle(x,y,10,p);c.drawLine(x-15,y,x+15,y,p);c.drawLine(x,y-15,x,y+15,p);}else{c.drawRoundRect(x-12,y-9,x+12,y+9,3,3,p);c.drawLine(x-6,y-9,x-6,y-14,p);c.drawLine(x+6,y-9,x+6,y-14,p);}p.setStyle(Paint.Style.FILL);}

    @Override public boolean onTouchEvent(MotionEvent e) {
        if(e.getAction()!=MotionEvent.ACTION_UP) return true;
        float x=e.getX()/sx,y=e.getY()/sy;
        Context c=getContext();
        if(y<62 && x<75){menuOpen=!menuOpen;invalidate();return true;}
        if(y<62 && x>1350){Tools.swapFragment(activity,LauncherPreferenceFragment.class,LauncherActivity.SETTING_FRAGMENT_TAG,null);return true;}
        if(menuOpen && x<228 && y>=72 && y<365){
            int idx=(int)((y-72)/48);
            if(idx==1){versionSpinner.openProfileEditor(activity);}
            else if(idx==2){Tools.swapFragment(activity,SearchModFragment.class,SearchModFragment.TAG,null);}
            else if(idx==5){Tools.swapFragment(activity,LauncherPreferenceFragment.class,LauncherActivity.SETTING_FRAGMENT_TAG,null);}
            invalidate(); return true;
        }
        if(menuOpen && x<228 && y>=378 && y<522){return true;}
        if(x>242 && x<1215 && y>=505 && y<545){
            Tools.swapFragment(activity,SearchModFragment.class,SearchModFragment.TAG,null);return true;
        }
        if(x>242 && x<1215 && y>=553 && y<650){
            float gap=10f, cw=(1215f-242f-gap*4f)/5f;
            int mod=(int)((x-242f)/(cw+gap));
            if(mod>=0 && mod<5){ modInstaller.run(); }
            return true;
        }
        if(x>242 && x<1215 && y>=258 && y<418){
            int card=(int)((x-242)/((1215-242+12)/3f));
            if(card>=0&&card<3) ExtraCore.setValue(ExtraConstants.LAUNCH_GAME,true);
            return true;
        }
        if(x>=1230 && y>=75 && y<188){
            ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD,true);return true;
        }
        if(x>=1230 && y>=274 && y<352){
            ultraOn=!ultraOn;c.getSharedPreferences("urinth_ui",Context.MODE_PRIVATE).edit().putBoolean("ultra",ultraOn).apply();invalidate();return true;
        }
        if(x>=1230 && y>=362 && y<435){
            Tools.openURL(activity,c.getString(R.string.social_media_invite));return true;
        }
        return true;
    }
}