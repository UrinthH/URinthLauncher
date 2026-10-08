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
    private static final float H = 650f;
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
        p.setShader(new LinearGradient(x,y,x+w,y+h,
                Color.rgb(20,45,105),Color.rgb(236,92,90),Shader.TileMode.CLAMP));
        c.drawRect(clip,p); p.setShader(null);
        // ocean
        p.setColor(Color.rgb(21,93,128)); c.drawRect(x,y+h*0.63f,x+w,y+h,p);
        for(int i=0;i<7;i++){
            p.setColor(Color.argb(70,255,220,190));
            c.drawRect(x+360+i*90,y+h*0.72f+i%2*7,x+430+i*90,y+h*0.73f+i%2*7,p);
        }
        // sun
        p.setColor(Color.rgb(255,239,167)); c.drawCircle(x+w*0.70f,y+h*0.48f,24,p);
        // distant islands
        p.setColor(Color.rgb(24,53,58));
        Path island=new Path(); island.moveTo(x+330,y+h*.64f); island.lineTo(x+420,y+h*.44f); island.lineTo(x+510,y+h*.64f); island.close(); c.drawPath(island,p);
        island.reset(); island.moveTo(x+w-300,y+h*.65f); island.lineTo(x+w-230,y+h*.40f); island.lineTo(x+w-140,y+h*.65f); island.close(); c.drawPath(island,p);
        // boat silhouette
        p.setColor(Color.rgb(35,29,36)); c.drawOval(x+w*.59f,y+h*.69f,x+w*.68f,y+h*.76f,p);
        p.setColor(Color.rgb(244,228,193)); Path sail=new Path();
        sail.moveTo(x+w*.635f,y+h*.70f); sail.lineTo(x+w*.635f,y+h*.39f); sail.lineTo(x+w*.70f,y+h*.68f); sail.close(); c.drawPath(sail,p);
        c.restore();
        text(c,"Play, Explore,",x+22,y+53,27,TEXT,true);
        text(c,"Create, Together.",x+22,y+82,27,TEXT,true);
        text(c,"The best Minecraft experience,",x+22,y+110,13,TEXT,false);
        text(c,"now on your Android device.",x+22,y+128,13,TEXT,false);
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
        int top=type==0?Color.rgb(99,42,128):type==1?Color.rgb(70,132,193):Color.rgb(205,104,42);
        int bottom=type==0?Color.rgb(242,124,155):type==1?Color.rgb(192,226,255):Color.rgb(250,174,70);
        p.setShader(new LinearGradient(x,y,x,y+h,top,bottom,Shader.TileMode.CLAMP)); c.drawRect(x,y,x+w,y+h,p); p.setShader(null);
        p.setColor(Color.argb(110,255,235,205)); c.drawCircle(x+w*.72f,y+h*.40f,19,p);
        if(type==0) {
            for(int i=0;i<9;i++){float tx=x+15+i*(w/8f), ty=y+100-(i%3)*8; p.setColor(Color.rgb(72,38,72)); c.drawRect(tx,ty,tx+8,y+130,p); p.setColor(Color.rgb(231,82,155)); c.drawCircle(tx+4,ty-8,18,p);}
        } else if(type==1) {
            p.setColor(Color.rgb(238,247,255)); Path m=new Path(); m.moveTo(x+15,y+112);m.lineTo(x+75,y+45);m.lineTo(x+125,y+110);m.lineTo(x+190,y+30);m.lineTo(x+w-10,y+112);m.close();c.drawPath(m,p);
            p.setColor(Color.rgb(44,94,125)); c.drawRect(x,y+112,x+w,y+160,p);
            for(int i=0;i<8;i++){p.setColor(Color.rgb(22,77,62)); c.drawCircle(x+20+i*40,y+110-(i%2)*8,14,p);}
        } else {
            p.setColor(Color.rgb(125,61,36)); for(int i=0;i<7;i++){float tx=x+20+i*55;Path m=new Path();m.moveTo(tx,y+115);m.lineTo(tx+22,y+58-(i%2)*15);m.lineTo(tx+45,y+115);m.close();c.drawPath(m,p);}
            p.setColor(Color.rgb(225,86,45)); c.drawRect(x,y+115,x+w,y+160,p);
        }
        c.restore();
        round(c,x,y,x+w,y+h,12,Color.TRANSPARENT,Color.rgb(9,156,186),1.5f);
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
            else if(idx==5){Tools.swapFragment((android.app.Activity)c,LauncherPreferenceFragment.class,LauncherActivity.SETTING_FRAGMENT_TAG,null);}
            invalidate(); return true;
        }
        if(menuOpen && x<228 && y>=378 && y<522){return true;}
        if(x>242 && x<1215 && y>=505 && y<545){
            Tools.swapFragment((android.app.Activity)c,SearchModFragment.class,SearchModFragment.TAG,null);return true;
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