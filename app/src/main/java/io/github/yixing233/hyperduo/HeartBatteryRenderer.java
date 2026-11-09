// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.SystemClock;

/** UI-thread-only renderer. A completed offscreen frame is committed atomically over native pixels. */
final class HeartBatteryRenderer {
    private static final BatteryShape SHAPE=new HeartShape();
    private static final Paint FILL=new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint OUTLINE=new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint TEXT=new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint CREST=new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint BOLT_PAINT=new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path BOLT=new Path();
    private static final Path LIQUID=new Path();
    private static final Path SURFACE=new Path();
    private static final java.util.Map<Object,Frame> FRAMES=new java.util.WeakHashMap<>();
    private static final class Frame {
        final Bitmap bitmap;
        final Canvas canvas;
        Frame(int w,int h) {
            bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            canvas=new Canvas(bitmap);
        }
    }
    static {
        BOLT.moveTo(26f,14f); BOLT.lineTo(7f,52f); BOLT.lineTo(20f,52f);
        BOLT.lineTo(16f,84f); BOLT.lineTo(43f,42f); BOLT.lineTo(29f,42f); BOLT.close();
        BOLT_PAINT.setStyle(Paint.Style.FILL);
        OUTLINE.setStyle(Paint.Style.STROKE);
        OUTLINE.setStrokeWidth(5.5f);
        OUTLINE.setStrokeJoin(Paint.Join.ROUND);
        CREST.setStyle(Paint.Style.STROKE);
        CREST.setStrokeWidth(3.2f);
        CREST.setStrokeJoin(Paint.Join.ROUND);
        CREST.setStrokeCap(Paint.Cap.ROUND);
        TEXT.setTextAlign(Paint.Align.CENTER);
        TEXT.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
    }
    private HeartBatteryRenderer() {}
    static void draw(Canvas target,int w,int h,int level,boolean charging,int tint,
                     TrioSettings cfg,boolean replaceNative) {
        draw(target,w,h,level,charging,tint,cfg,replaceNative,cfg);
    }
    static void draw(Canvas target,int w,int h,int level,boolean charging,int tint,
                     TrioSettings cfg,boolean replaceNative,Object owner) {
        drawAtTime(target,w,h,level,charging,tint,cfg,replaceNative,owner,SystemClock.uptimeMillis());
    }
    /** Explicit frame time keeps the exact production animation reproducible in raster tests. */
    static void drawAtTime(Canvas target,int w,int h,int level,boolean charging,int tint,
                           TrioSettings cfg,boolean replaceNative,Object owner,long time) {
        if(w<=0 || h<=0) return;
        if(w>2048 || h>2048) throw new IllegalArgumentException("Unexpected battery frame size");
        // Previews draw directly into their own clipped Canvas. They can share a settings
        // snapshot, so that snapshot must never own a hardware-retained frame cache.
        if (!replaceNative) {
            paint(target,w,h,level,charging,tint,cfg,time);
            return;
        }
        Frame frame=FRAMES.get(owner);
        if(frame==null || frame.bitmap.getWidth()!=w || frame.bitmap.getHeight()!=h) {
            frame=new Frame(w,h);
            FRAMES.put(owner,frame);
        }
        frame.bitmap.eraseColor(Color.TRANSPARENT);
        paint(frame.canvas,w,h,level,charging,tint,cfg,time);
        // No native pixels have been touched until the entire replacement is ready.
        if(replaceNative) target.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
        target.drawBitmap(frame.bitmap,0f,0f,null);
    }
    private static void paint(Canvas c,int w,int h,int level,boolean charging,int tint,TrioSettings cfg,long time) {
        level=HeartBattery.level(level);
        float designWidth=HeartBatteryLayout.designWidth(cfg,charging);
        float scale=Math.min(w/designWidth,h/100f)*Prefs.clamp(cfg.glyphScale,45,100)/100f;
        int save=c.save();
        try {
            c.translate((w-designWidth*scale)*.5f,(h-96f*scale)*.5f);
            c.scale(scale,scale);
            int fill=HeartBattery.fillColor(cfg,level,charging);
            // Match the charging (or full) fill unless automatic outline tint is selected.
            int ink=cfg.heartAutoTint ? (tint==0 ? Color.WHITE : tint)
                    : charging ? fill : cfg.heartNormal|0xff000000;
            if (!charging && level <= cfg.lowThreshold) ink=cfg.heartLow|0xff000000;
            ink=HeartBattery.dimColor(ink,cfg.heartBrightness);
            fill=HeartBattery.dimColor(fill,cfg.heartBrightness);
            OUTLINE.setColor(ink);
            FILL.setColor(fill);
            boolean animated=HeartBattery.shouldAnimate(cfg,charging,level);
            FILL.setAlpha(animated ? HeartChargeMotion.alpha(time) : 255);
            int clip=c.save();
            try {
                c.clipPath(SHAPE.outline());
                if (animated) {
                    LIQUID.reset(); SURFACE.reset();
                    float first=HeartChargeMotion.surfaceY(-6f,level,time);
                    LIQUID.moveTo(-6f,93f); LIQUID.lineTo(-6f,first);
                    SURFACE.moveTo(-6f,first);
                    for(int x=-2;x<=106;x+=4) {
                        float y=HeartChargeMotion.surfaceY(x,level,time);
                        LIQUID.lineTo(x,y); SURFACE.lineTo(x,y);
                    }
                    LIQUID.lineTo(106f,93f); LIQUID.close();
                    c.drawPath(LIQUID,FILL);
                    // A bright narrow crest makes the moving fill boundary readable at 20dp.
                    int highlight=HeartBattery.dimColor(Color.WHITE,cfg.heartBrightness);
                    CREST.setColor(Color.argb(225,(Color.red(fill)+Color.red(highlight))/2,
                            (Color.green(fill)+Color.green(highlight))/2,(Color.blue(fill)+Color.blue(highlight))/2));
                    c.drawPath(SURFACE,CREST);
                } else {
                    c.drawRect(0f,HeartBattery.fillTop(level),100f,SHAPE.bottom()+3f,FILL);
                }
            } finally { c.restoreToCount(clip); }
            c.drawPath(SHAPE.outline(),OUTLINE);
            if(cfg.heartPercentage) {
                String text=Integer.toString(level);
                if (HeartBatteryLayout.outside(cfg)) {
                    TEXT.setTextAlign(Paint.Align.LEFT);
                    TEXT.setTextSize(52f);
                    float needed=TEXT.measureText(text);
                    float available=HeartBatteryLayout.contentWidth(cfg)-112f-4f;
                    if (needed>available) TEXT.setTextSize(52f*available/needed);
                    float y=49f-(TEXT.ascent()+TEXT.descent())*.5f;
                    TEXT.setStyle(Paint.Style.FILL); TEXT.setColor(ink);
                    c.drawText(text,112f,y,TEXT);
                } else {
                    TEXT.setTextAlign(Paint.Align.CENTER);
                    TEXT.setTextSize(level==100 ? 27f : 32f);
                    float y=49f-(TEXT.ascent()+TEXT.descent())*.5f;
                    TEXT.setStyle(Paint.Style.STROKE); TEXT.setStrokeWidth(4f);
                    TEXT.setColor((ink & 0x00ffffff)==0 ? HeartBattery.dimColor(Color.WHITE,cfg.heartBrightness) : Color.BLACK);
                    c.drawText(text,50f,y,TEXT);
                    TEXT.setStyle(Paint.Style.FILL); TEXT.setColor(ink);
                    c.drawText(text,50f,y,TEXT);
                }
            }
            if (HeartBattery.shouldShowBolt(cfg,charging)) {
                int boltSave=c.save();
                try {
                    c.translate(HeartBatteryLayout.boltLeft(cfg),0f);
                    BOLT_PAINT.setColor(ink);
                    c.drawPath(BOLT,BOLT_PAINT);
                } finally { c.restoreToCount(boltSave); }
            }
        } finally { c.restoreToCount(save); }
    }
}
