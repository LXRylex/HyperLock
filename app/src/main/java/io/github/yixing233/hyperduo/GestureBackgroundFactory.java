// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Builds masked gesture art off the launcher drawing thread. */
final class GestureBackgroundFactory {
    private static final Uri IMAGE = Uri.parse("content://io.github.lxrylex.hyperlock.controlcenterimages/image/gesture");
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final WeakHashMap<Bitmap, Entry> CACHE = new WeakHashMap<>();
    private static final class Entry { String key; Bitmap output, lastFrame; AnimatedMedia media; boolean pending; }
    private GestureBackgroundFactory() {}

    static Bitmap resolve(Bitmap mask, TrioSettings settings, View view) {
        int mode=GestureStylePolicy.mode(settings.gestureBackgroundMode);
        if(mode==0 || mask==null || mask.isRecycled())return mask;
        String key=mode+":"+settings.gestureBackgroundStart+":"+settings.gestureBackgroundEnd+":"+settings.ccImageRevision;
        synchronized(CACHE) {
            Entry entry=CACHE.get(mask);
            if(entry==null || !key.equals(entry.key)) {
                entry=new Entry();entry.key=key;entry.pending=true;CACHE.put(mask,entry);
                final Entry request=entry;final int start=settings.gestureBackgroundStart,end=settings.gestureBackgroundEnd;
                final android.content.Context context=view.getContext().getApplicationContext()!=null?view.getContext().getApplicationContext():view.getContext();
                final WeakReference<View> target=new WeakReference<>(view);
                IO.execute(()->{try {
                    if(mode==3)try(InputStream in=context.getContentResolver().openInputStream(IMAGE)){request.media=AnimatedMedia.load(in);}
                    Bitmap first=request.media==null?null:request.media.frame(null);
                    Bitmap result=build(mask,mode,start,end,first);
                    synchronized(CACHE){if(CACHE.get(mask)==request){request.lastFrame=first;request.output=result;request.pending=false;}}
                }catch(Throwable t){synchronized(CACHE){request.pending=false;}Log.w("HyperDuo","Gesture image unavailable",t);}
                View v=target.get();if(v!=null)v.postInvalidate();});
            } else if(mode==3 && entry.media!=null && entry.media.animated()) {
                Bitmap frame=entry.media.frame(view);
                if(frame!=null && frame!=entry.lastFrame && !entry.pending) {
                    entry.pending=true;final Entry request=entry;final Bitmap image=frame;final WeakReference<View> target=new WeakReference<>(view);
                    IO.execute(()->{try{Bitmap result=build(mask,3,0,0,image);synchronized(CACHE){if(CACHE.get(mask)==request){request.output=result;request.lastFrame=image;}}}catch(Throwable ignored){}finally{synchronized(CACHE){request.pending=false;}View v=target.get();if(v!=null)v.postInvalidate();}});
                }
            }
            return entry.output!=null?entry.output:mask;
        }
    }

    private static Bitmap build(Bitmap mask, int mode, int start, int end, Bitmap image) throws Exception {
        int w = mask.getWidth(), h = mask.getHeight();
        if (w <= 0 || h <= 0 || w > 2048 || h > 4096) return null;
        if (mode == 3 && image == null) return null;
        Bitmap output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawBitmap(mask, 0, 0, null);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        if (mode == 3) {
            float targetRatio = w / (float) h, sourceRatio = image.getWidth() / (float) image.getHeight();
            int cropW = image.getWidth(), cropH = image.getHeight();
            if (sourceRatio > targetRatio) cropW = Math.max(1, Math.round(cropH * targetRatio));
            else cropH = Math.max(1, Math.round(cropW / targetRatio));
            int x = (image.getWidth() - cropW) / 2, y = (image.getHeight() - cropH) / 2;
            canvas.drawBitmap(image, new Rect(x,y,x+cropW,y+cropH), new RectF(0,0,w,h), paint);

        } else {
            if (mode == 2) paint.setShader(new LinearGradient(0,0,0,h,start | 0xff000000,end | 0xff000000,Shader.TileMode.CLAMP));
            else paint.setColor(start | 0xff000000);
            canvas.drawRect(0,0,w,h,paint);
        }
        paint.setXfermode(null);
        return output;
    }
}
