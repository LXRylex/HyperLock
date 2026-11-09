// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;

import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bounded frame decoding off the UI thread. Call frame only while drawing a visible view. */
final class AnimatedMedia {
 private static final ExecutorService IO=Executors.newSingleThreadExecutor();
 private final Movie movie;private final AnimatedAssetFormat.Asset crop;private final GifFrameTiming timing;private final int width,height;
 private volatile Bitmap frame;private boolean pending;private long next;
 private AnimatedMedia(Bitmap bitmap){frame=bitmap;movie=null;crop=null;timing=null;width=bitmap.getWidth();height=bitmap.getHeight();}
 private AnimatedMedia(Movie m,AnimatedAssetFormat.Asset a){movie=m;crop=a;timing=GifFrameTiming.parse(a.bytes);float cw=m.width()*(a.right-a.left)/100000f,ch=m.height()*(a.bottom-a.top)/100000f;float scale=Math.min(1f,512f/Math.max(cw,ch));width=Math.max(1,Math.round(cw*scale));height=Math.max(1,Math.round(ch*scale));frame=render(0);}
 static AnimatedMedia load(InputStream in) throws Exception {
  if(in==null)return null;ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[16384];int n;
  while((n=in.read(buf))!=-1){if(out.size()+n>16*1024*1024)throw new IllegalArgumentException("Image too large");out.write(buf,0,n);}
  byte[] bytes=out.toByteArray();AnimatedAssetFormat.Asset asset=AnimatedAssetFormat.parse(bytes);
  if(asset!=null){Movie movie=Movie.decodeByteArray(asset.bytes,0,asset.bytes.length);if(movie==null||movie.width()<1||movie.height()<1||movie.width()>2048||movie.height()>2048)return null;return new AnimatedMedia(movie,asset);}
  Bitmap b=BitmapFactory.decodeByteArray(bytes,0,bytes.length);return b!=null && b.getWidth()<=2048 && b.getHeight()<=2048?new AnimatedMedia(b):null;
 }
 boolean animated(){return movie!=null && movie.duration()>0;}
 Bitmap frame(View view){
  if(animated() && visible(view)){
   final long now=SystemClock.uptimeMillis();int delay=timing!=null && timing.duration()==movie.duration()?timing.nextDelay(now):8;boolean start=false;
   synchronized(this){if(!pending && now>=next){pending=true;next=now+delay;start=true;}}
   if(start){WeakReference<View> ref=new WeakReference<>(view);IO.execute(()->{try{Bitmap b=render(now);if(b!=null)frame=b;}catch(Throwable ignored){}finally{synchronized(AnimatedMedia.this){pending=false;}View v=ref.get();if(visible(v))v.postInvalidate();}});}
   view.postInvalidateDelayed(delay);
  }
  return frame;
 }
 private static boolean visible(View view){
  if(view==null || !view.isAttachedToWindow() || !view.isShown() || view.getWindowVisibility()!=View.VISIBLE)return false;
  for(View v=view;v!=null;){if(v.getAlpha()<=0.01f)return false;android.view.ViewParent parent=v.getParent();v=parent instanceof View?(View)parent:null;}
  android.os.PowerManager power=(android.os.PowerManager)view.getContext().getSystemService(android.content.Context.POWER_SERVICE);
  return power==null || power.isInteractive();
 }
 private Bitmap render(long time){
  if(movie==null)return frame;Bitmap b=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);
  float x=movie.width()*crop.left/100000f,y=movie.height()*crop.top/100000f;
  float w=movie.width()*(crop.right-crop.left)/100000f,h=movie.height()*(crop.bottom-crop.top)/100000f;
  c.scale(width/w,height/h);c.translate(-x,-y);int duration=movie.duration();movie.setTime(duration>0?(int)(time%duration):0);movie.draw(c,0,0);return b;
 }
}


