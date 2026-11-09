// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;

import android.app.Dialog;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Scope limited to Xiaomi PowerNoticeUI's low-battery warning. */
final class BatteryWarningHooks {
 private static final String TAG="HyperDuo";
 private static final String PREVIEW_ACTION="io.github.lxrylex.hyperlock.action.PREVIEW_BATTERY_WARNING";
 private static final String PREVIEW_PERMISSION="io.github.lxrylex.hyperlock.permission.PREVIEW_BATTERY_WARNING";
 private static volatile boolean receiverInstalled;
 private static final Uri IMAGE=Uri.parse("content://io.github.lxrylex.hyperlock.controlcenterimages/image/battery_warning");
 private static final ExecutorService IO=Executors.newSingleThreadExecutor();
 private BatteryWarningHooks(){}
 static void install(XposedModule module,ClassLoader loader) {
  try {
   Class<?> type=Refl.cls("ph.v",loader);
   Method show=Refl.method(type,"R0"); Field field=Refl.field(type,"p");
   if(show==null || field==null || !Dialog.class.isAssignableFrom(field.getType())) {
    Log.i(TAG,"Low battery warning hook unavailable on this Security version");return;
   }
   module.hook(show).setId("low-battery-warning").intercept(new XposedInterface.Hooker(){
    @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
     Object result=chain.proceed();
     try {
      Object value=field.get(chain.getThisObject());
      if(value instanceof Dialog) {
       Dialog dialog=(Dialog)value; Context c=dialog.getContext();
       TrioConfig.install(module);TrioConfig.installReceiver(c);
       BatteryWarningStyle style=BatteryWarningStyle.parse(TrioConfig.get().batteryWarningStyle);
       if(style.enabled) apply(dialog,style);
      }
     } catch(Throwable t){Log.w(TAG,"Low battery style skipped safely",t);}
     return result;
    }
   });
   Method init=Refl.method(type,"T0",Context.class);
   if(init!=null) module.hook(init).setId("low-battery-preview-init").intercept(new XposedInterface.Hooker(){
    @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
     Object result=chain.proceed();
     try { Object context=chain.getArg(0);if(context instanceof Context)installPreview((Context)context,chain.getThisObject(),show,field); }
     catch(Throwable t){Log.w(TAG,"Low battery preview initialization skipped",t);}
     return result;
    }
   });
   Log.i(TAG,"Low battery warning hook installed");
  } catch(Throwable t){Log.w(TAG,"Low battery warning hook unavailable",t);}
 }
 private static void apply(Dialog dialog,BatteryWarningStyle style) {
  if(dialog.getWindow()==null)return;
  View root=dialog.getWindow().getDecorView();
  int titleId=root.getResources().getIdentifier("level_percent","id","com.miui.securitycenter");
  int imageId=root.getResources().getIdentifier("image","id","com.miui.securitycenter");
  if(titleId==0 || root.findViewById(titleId)==null || imageId==0)return;
  int headingId=root.getResources().getIdentifier("title","id","com.miui.securitycenter");
  View heading=headingId==0?null:root.findViewById(headingId);
  View message=root.findViewById(titleId);
  if(heading instanceof TextView && !style.titleText.isEmpty())((TextView)heading).setText(style.titleText);
  if(message instanceof TextView && !style.messageText.isEmpty())((TextView)message).setText(style.messageText);
  View panel=findPanel(root);
  if(panel!=null) {
   float radius=30f*root.getResources().getDisplayMetrics().density;
   GradientDrawable background=new GradientDrawable();background.setColor(style.background);background.setCornerRadius(radius);
   panel.setBackground(background);
   if(style.image) loadImage(dialog,panel,radius,style.background>>>24);
  } else Log.i(TAG,"Low battery panel not found; native background retained");
  View left=root.findViewById(android.R.id.button2), right=root.findViewById(android.R.id.button1);
  styleButton(left,style.gotIt);
  styleButton(right,style.saver);
  int containerId=root.getResources().getIdentifier("padding","id","com.miui.securitycenter");
  View container=containerId==0?null:root.findViewById(containerId);
  View icon=container==null?null:container.findViewById(imageId);
  if(!(icon instanceof ImageView))icon=root.findViewById(imageId);
  if(icon instanceof ImageView) {
   ImageView image=(ImageView)icon;
   boolean hidden=(style.batteryBody>>>24)==0 && (style.batteryFill>>>24)==0;
   image.setImageAlpha(hidden?0:255);
   if(!hidden)tintBattery(image,style);
   Log.i(TAG,"Low battery icon styled; body alpha="+(style.batteryBody>>>24)+" fill alpha="+(style.batteryFill>>>24));
  } else Log.w(TAG,"Low battery icon not found in warning");
 }
 private static void styleButton(View button,int color) {
  if(button==null)return;
  // MIUI buttons can render a separate material/blur layer over their background tint.
  for(String method:new String[]{"applyBlur","setEnableBlur","setSupportBlur"}) {
   try {button.getClass().getMethod(method,boolean.class).invoke(button,false);}
   catch(ReflectiveOperationException | SecurityException ignored) { }
  }
  GradientDrawable background=new GradientDrawable();
  background.setColor(color);
  background.setCornerRadius(24f*button.getResources().getDisplayMetrics().density);
  button.setBackgroundTintList(null);
  button.setBackground(background);
 }
 private static synchronized void installPreview(Context context,Object owner,Method show,Field dialogField) {
  if(receiverInstalled || owner==null)return;
  Context app=context.getApplicationContext();if(app==null)app=context;
  final WeakReference<Object> target=new WeakReference<>(owner);
  BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent intent) {
   try {
    Object instance=target.get();if(instance==null)return;
    Object previous=dialogField.get(instance);
    if(previous instanceof Dialog && ((Dialog)previous).isShowing()) { setResultCode(android.app.Activity.RESULT_OK);return; }
    show.invoke(instance);
    Object shown=dialogField.get(instance);
    if(shown instanceof Dialog && ((Dialog)shown).isShowing())setResultCode(android.app.Activity.RESULT_OK);
   } catch(Throwable t){Log.w(TAG,"Low battery test warning unavailable",t);}
  }};
  try {
   IntentFilter filter=new IntentFilter(PREVIEW_ACTION);
   if(android.os.Build.VERSION.SDK_INT>=33)app.registerReceiver(receiver,filter,PREVIEW_PERMISSION,new Handler(Looper.getMainLooper()),Context.RECEIVER_EXPORTED);
   else app.registerReceiver(receiver,filter,PREVIEW_PERMISSION,new Handler(Looper.getMainLooper()));
   receiverInstalled=true;Log.i(TAG,"Low battery test warning receiver installed");
  } catch(Throwable t){Log.w(TAG,"Low battery test warning receiver unavailable",t);}
 }
 private static View findPanel(View view) {
  if(view.getClass().getName().equals("miuix.appcompat.internal.widget.DialogParentPanel2"))return view;
  if(view instanceof ViewGroup) {ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View v=findPanel(group.getChildAt(i));if(v!=null)return v;}}
  return null;
 }
 private static void tintBattery(ImageView view,BatteryWarningStyle style) {
  Drawable d=view.getDrawable();if(d==null)return;
  int w=d.getIntrinsicWidth(),h=d.getIntrinsicHeight();if(w<1 || h<1 || w>2048 || h>2048)return;
  Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);bitmap.setDensity(view.getResources().getDisplayMetrics().densityDpi);Canvas canvas=new Canvas(bitmap);
  Rect old=new Rect(d.getBounds());d.setBounds(0,0,w,h);try{d.draw(canvas);}finally{d.setBounds(old);}
  int[] pixels=new int[w*h];bitmap.getPixels(pixels,0,w,0,0,w,h);
  for(int i=0;i<pixels.length;i++)pixels[i]=BatteryWarningStyle.recolorPixel(pixels[i],style.batteryBody,style.batteryFill);
  bitmap.setPixels(pixels,0,w,0,0,w,h);view.setImageDrawable(new BitmapDrawable(view.getResources(),bitmap));
 }
 private static void loadImage(Dialog dialog,View panel,float radius,int alpha) {
  Context c=panel.getContext().getApplicationContext();if(c==null)c=panel.getContext();
  final Context context=c;WeakReference<Dialog> ref=new WeakReference<>(dialog);WeakReference<View> target=new WeakReference<>(panel);
  IO.execute(() -> {
   AnimatedMedia bitmap=null;try(InputStream in=context.getContentResolver().openInputStream(IMAGE)){if(in!=null)bitmap=AnimatedMedia.load(in);}catch(Throwable ignored){}
   if(bitmap==null)return;final AnimatedMedia image=bitmap;View v=target.get();if(v==null)return;
   v.post(() -> {try {Dialog d=ref.get();if(d!=null && d.isShowing())v.setBackground(new ImageBackground(image,radius,alpha,v));}catch(Throwable t){Log.w(TAG,"Low battery image skipped",t);}});
  });
 }
 private static final class ImageBackground extends Drawable {
  private final AnimatedMedia media;private final WeakReference<View> owner;private final float radius;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  ImageBackground(AnimatedMedia b,float r,int a,View v){media=b;owner=new WeakReference<>(v);radius=r;paint.setAlpha(a);}
  @Override public void draw(Canvas c){Rect b=getBounds();Bitmap image=media.frame(owner.get());if(b.isEmpty()||image==null)return;float scale=Math.max((float)b.width()/image.getWidth(),(float)b.height()/image.getHeight());float w=image.getWidth()*scale,h=image.getHeight()*scale;float x=b.exactCenterX()-w/2,y=b.exactCenterY()-h/2;int save=c.save();Path path=new Path();path.addRoundRect(new RectF(b),radius,radius,Path.Direction.CW);c.clipPath(path);c.drawBitmap(image,null,new RectF(x,y,x+w,y+h),paint);c.restoreToCount(save);}
  @Override public void setAlpha(int a){paint.setAlpha(a);invalidateSelf();}
  @Override public void setColorFilter(android.graphics.ColorFilter f){paint.setColorFilter(f);invalidateSelf();}
  @SuppressWarnings("deprecation") @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
 }
}


