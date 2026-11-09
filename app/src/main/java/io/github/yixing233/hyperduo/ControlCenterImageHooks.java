// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;

import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Optional, plugin-scoped backgrounds. A missing plugin or image leaves native views alone. */
final class ControlCenterImageHooks {
    private static final String TAG="HyperDuo";
    private static final String AUTH="content://io.github.lxrylex.hyperlock.controlcenterimages/image/";
    private static final String[] SLOTS={"wifi","mobile","bluetooth","airplane","music","brightness","volume"};
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final WeakHashMap<View,Target> TARGETS=new WeakHashMap<>();
    private static final WeakHashMap<View,Boolean> OBSERVED=new WeakHashMap<>();
    private static final WeakHashMap<ClassLoader,Boolean> LOADERS=new WeakHashMap<>();
    private static volatile Map<String,AnimatedMedia> images=Collections.emptyMap();
    private static volatile int loadedRevision=-1, requestedRevision=-1;
    private static Context context;
    private static boolean installed, listenerInstalled, unlockReceiverInstalled;
    private ControlCenterImageHooks(){}
    private static final class Target {
        String slot;
        Drawable originalBackground,wrappedBackground;
        LayerDrawable layer;
        Drawable originalLayer,wrappedLayer;
        int layerIndex=-1;
        WeakReference<ImageView> overlay;
        WeakReference<View> progress;
        Float progressAlpha;
        Target(String slot){this.slot=slot;}
    }
    static synchronized void install(XposedModule module,ClassLoader loader) {
        if(installed)return;
        Class<?> wrapper=Refl.cls("com.android.systemui.shared.plugins.PluginActionManager$PluginContextWrapper",loader);
        Method method=wrapper==null?null:Refl.method(wrapper,"getClassLoader");
        if(method==null){Log.i(TAG,"Control Center images: plugin loader unavailable");return;}
        try {
            module.hook(method).setId("cc-image-plugin-loader").intercept(new XposedInterface.Hooker(){
                @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Object result=chain.proceed();
                    try {
                        Object owner=chain.getThisObject();
                        if(owner instanceof Context && result instanceof ClassLoader && "miui.systemui.plugin".equals(((Context)owner).getPackageName())) {
                            context=((Context)owner).getApplicationContext();
                            if(context==null)context=(Context)owner;
                            installUnlockReceiver();
                            installPlugin(module,(ClassLoader)result);
                        }
                    }catch(Throwable t){Log.w(TAG,"Control Center plugin skipped",t);}
                    return result;
                }
            });
            installed=true;
            if(!listenerInstalled){listenerInstalled=true;TrioConfig.addListener(ControlCenterImageHooks::refresh);}
            Log.i(TAG,"Control Center image loader hook installed");
        }catch(Throwable t){Log.w(TAG,"Control Center image loader unavailable",t);}
    }
    private static synchronized void installUnlockReceiver() {
        if(unlockReceiverInstalled || context==null)return;
        try {
            BroadcastReceiver receiver=new BroadcastReceiver(){
                @Override public void onReceive(Context c,Intent i){requestedRevision=-1;refresh();}
            };
            IntentFilter filter=new IntentFilter(Intent.ACTION_USER_UNLOCKED);
            if(Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED);
            else context.registerReceiver(receiver,filter);
            unlockReceiverInstalled=true;
        }catch(Throwable t){Log.w(TAG,"Control Center unlock refresh unavailable",t);}
    }
    private static synchronized void installPlugin(XposedModule module,ClassLoader loader) {
        if(LOADERS.containsKey(loader))return;
        Class<?> wifi=Refl.cls("miui.systemui.controlcenter.qs.tileview.QSCardItemView",loader);
        Class<?> tile=Refl.cls("miui.systemui.controlcenter.qs.tileview.QSTileItemView",loader);
        if(wifi==null || tile==null)return;
        LOADERS.put(loader,Boolean.TRUE);
        hookTile(module,wifi,false);hookTile(module,tile,true);
        hookBluetoothIcon(module,loader);
        ControlCenterBluetoothCard.install(module,loader);
        ControlCenterIconColors.install(module,loader);
        MediaBoostHooks.install(module,loader);
        hookHolder(module,loader,"miui.systemui.controlcenter.panel.main.media.MediaPlayerController","music");
        hookHolder(module,loader,"miui.systemui.controlcenter.panel.main.brightness.BrightnessSliderController","brightness");
        hookHolder(module,loader,"miui.systemui.controlcenter.panel.main.volume.VolumeSliderController","volume");
        Log.i(TAG,"Control Center image plugin hooks installed");refresh();
    }
    private static void hookTile(XposedModule module,Class<?> type,boolean small) {
        for(Method method:type.getDeclaredMethods()) {
            if(!"updateState".equals(method.getName()) || method.getParameterCount()!=3)continue;
            try {module.hook(method).setId("cc-image-"+type.getSimpleName()).intercept(new XposedInterface.Hooker(){
                @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Object result=chain.proceed();
                    try {
                        Object state=chain.getArg(0),owner=chain.getThisObject();
                        Object spec=state==null?null:Refl.get(Refl.field(state.getClass(),"spec"),state);
                        String slot=ControlCenterImagePolicy.tileSlot(spec instanceof String?(String)spec:null);
                        if(!small){ControlCenterIconColors.tile(state,owner,false);ControlCenterIconColors.cardText(state,owner);}
                        if(small) {
                            Object icon=Refl.callByName(owner,"getIcon");Object image=Refl.callByName(icon,"getIcon");
                            if(image instanceof ImageView)register((View)image,"bluetooth".equals(slot)?slot:null);
                        }else if(owner instanceof View)register((View)owner,slot);
                    }catch(Throwable t){Log.w(TAG,"Control Center tile image skipped",t);}
                    return result;
                }
            });}catch(Throwable t){Log.w(TAG,"Control Center tile hook unavailable",t);}
        }
    }
    // Xiaomi can asynchronously replace the layered Bluetooth drawable after
    // updateState returns. Reapply after its actual drawable update as well.
    private static void hookBluetoothIcon(XposedModule module,ClassLoader loader) {
        Class<?> type=Refl.cls("miui.systemui.controlcenter.qs.tileview.QSTileItemIconView",loader);if(type==null)return;
        for(Method method:type.getDeclaredMethods()) {
            if(!"updateIconInternal".equals(method.getName()) || method.getParameterCount()!=5)continue;
            try {module.hook(method).setId("cc-image-bluetooth-drawable").intercept(new XposedInterface.Hooker(){
                @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Object result=chain.proceed();
                    try {
                        Object state=chain.getArg(0),owner=chain.getThisObject();
                        Object spec=state==null?null:Refl.get(Refl.field(state.getClass(),"spec"),state);
                        ControlCenterIconColors.tile(state,owner,true);
                        Object icon=Refl.callByName(owner,"getIcon");
                        if(icon instanceof ImageView)register((View)icon,"bluetooth".equals(ControlCenterImagePolicy.tileSlot(spec instanceof String?(String)spec:null))?"bluetooth":null);
                    }catch(Throwable t){Log.w(TAG,"Control Center Bluetooth image skipped",t);}
                    return result;
                }
            });}catch(Throwable t){Log.w(TAG,"Control Center Bluetooth drawable hook unavailable",t);}
        }
    }
    private static void hookHolder(XposedModule module,ClassLoader loader,String name,String slot) {
        Class<?> type=Refl.cls(name,loader);if(type==null)return;
        for(Method method:type.getDeclaredMethods()) {
            if(!"createViewHolder".equals(method.getName()) || method.getParameterCount()!=2)continue;
            try {module.hook(method).setId("cc-image-holder-"+slot).intercept(new XposedInterface.Hooker(){
                @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Object result=chain.proceed();
                    try {
                        Object item=result==null?null:Refl.get(Refl.field(result.getClass(),"itemView"),result);
                        if(item instanceof View){register((View)item,slot);ControlCenterIconColors.registerRoot(chain.getThisObject(),(View)item,slot);}
                        if("volume".equals(slot) && result!=null)MediaBoostHooks.registerHolder(result);
                    }catch(Throwable t){Log.w(TAG,"Control Center holder image skipped",t);}
                    return result;
                }
            });}catch(Throwable t){Log.w(TAG,"Control Center holder hook unavailable",t);}
        }
    }
    private static void register(View view,String slot) {
        if(Looper.myLooper()!=Looper.getMainLooper()){MAIN.post(()->register(view,slot));return;}
        Target target=TARGETS.get(view);
        if(slot==null){if(target!=null){restore(view,target);TARGETS.remove(view);}return;}
        if(target==null){target=new Target(slot);TARGETS.put(view,target);Log.i(TAG,"Control Center target found: "+slot);}
        if(!OBSERVED.containsKey(view)) {
            OBSERVED.put(view,Boolean.TRUE);
            view.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,oright,ob)->{
                if((r-l)!=(oright-ol)||(b-t)!=(ob-ot)) {
                    Target current=TARGETS.get(v);if(current!=null)try{apply(v,current);}catch(Throwable failure){Log.w(TAG,"Control Center image layout skipped",failure);}
                }
            });
        }
        else if(!slot.equals(target.slot)){restore(view,target);target=new Target(slot);TARGETS.put(view,target);}
        apply(view,target);loadIfNeeded();
    }
    private static void refresh() {
        MAIN.post(()->{
            try {ControlCenterBluetoothCard.refresh();ControlCenterIconColors.refresh();loadIfNeeded();for(Map.Entry<View,Target> e:new ArrayList<>(TARGETS.entrySet()))if(e.getKey()!=null)apply(e.getKey(),e.getValue());}
            catch(Throwable t){Log.w(TAG,"Control Center image refresh skipped",t);}
        });
    }
    private static void loadIfNeeded() {
        TrioSettings cfg=TrioConfig.get();
        if(!cfg.ccImagesEnabled || context==null || requestedRevision==cfg.ccImageRevision)return;
        final int revision=cfg.ccImageRevision;requestedRevision=revision;final Context ctx=context;
        IO.execute(()->{
            Map<String,AnimatedMedia> next=new HashMap<>();
            for(String slot:SLOTS)try(InputStream in=ctx.getContentResolver().openInputStream(Uri.parse(AUTH+slot))) {
                if(in!=null){AnimatedMedia media=AnimatedMedia.load(in);if(media!=null)next.put(slot,media);}
            }catch(java.io.FileNotFoundException ignored){}catch(Throwable t){Log.w(TAG,"Control Center image unavailable: "+slot,t);}
            MAIN.post(()->{
                if(TrioConfig.get().ccImageRevision!=revision)return;
                images=next;loadedRevision=revision;
                Log.i(TAG,"Control Center images loaded: "+next.size());
                for(Map.Entry<View,Target> e:new ArrayList<>(TARGETS.entrySet()))if(e.getKey()!=null)try{apply(e.getKey(),e.getValue());}catch(Throwable t){Log.w(TAG,"Control Center image apply skipped",t);}
            });
        });
    }
    private static void apply(View view,Target target) {
        TrioSettings cfg=TrioConfig.get();AnimatedMedia bitmap=cfg.ccImagesEnabled && loadedRevision==cfg.ccImageRevision?images.get(target.slot):null;
        if(bitmap==null){restore(view,target);return;}
        int brightness=ControlCenterImagePolicy.brightness(cfg.ccImageBrightness);
        if("bluetooth".equals(target.slot) && view instanceof ImageView){applyBluetooth((ImageView)view,target,bitmap,brightness);return;}
        if(("brightness".equals(target.slot)||"volume".equals(target.slot)) && view instanceof ViewGroup){applySlider((ViewGroup)view,target,bitmap,brightness);return;}
        if("music".equals(target.slot) && view instanceof ViewGroup){applyMusic((ViewGroup)view,target,bitmap,brightness);return;}
        Drawable bg=view.getBackground();
        if(bg==target.wrappedBackground)bg=target.originalBackground;
        target.originalBackground=bg;
        Object nativeRadius=Refl.callByName(view,"getCornerRadius");
        float radius=nativeRadius instanceof Number?((Number)nativeRadius).floatValue():0f;
        if(radius<=0f){
            int id=view.getResources().getIdentifier("control_center_universal_corner_radius","dimen","miui.systemui.plugin");
            if(id!=0)radius=view.getResources().getDimensionPixelSize(id);
        }
        if(radius<=0f)radius=Math.max(1f,view.getHeight()/2f);
        ImageDrawable photo=new ImageDrawable(bitmap,brightness,radius,view);
        target.wrappedBackground=bg==null?photo:new LayerDrawable(new Drawable[]{bg,photo});
        view.setBackground(target.wrappedBackground);
    }
    private static void applyBluetooth(ImageView view,Target t,AnimatedMedia bitmap,int brightness) {
        Drawable d=view.getDrawable();
        if(!(d instanceof LayerDrawable)){applyBluetoothBackground(view,t,bitmap,brightness);return;}
        if(t.wrappedBackground!=null) {
            if(view.getBackground()==t.wrappedBackground)view.setBackground(t.originalBackground);
            t.wrappedBackground=null;t.originalBackground=null;
        }
        LayerDrawable layer=(LayerDrawable)d;int n=layer.getNumberOfLayers();
        if(n<2){applyBluetoothBackground(view,t,bitmap,brightness);return;}
        if(t.layer!=null && t.layer!=layer)restoreLayer(t);
        int index=n-2;Drawable current=layer.getDrawable(index);
        if(t.layer==layer && current==t.wrappedLayer)current=t.originalLayer;
        t.layer=layer;t.layerIndex=index;t.originalLayer=current;
        float radius=Math.max(2f,view.getWidth()*.12f);
        t.wrappedLayer=new LayerDrawable(new Drawable[]{current,new ImageDrawable(bitmap,brightness,radius,view)});
        layer.setDrawable(index,t.wrappedLayer);view.invalidate();
    }
    private static void applyBluetoothBackground(ImageView view,Target t,AnimatedMedia bitmap,int brightness){
        restoreLayer(t);
        Drawable bg=view.getBackground();if(bg==t.wrappedBackground)bg=t.originalBackground;
        t.originalBackground=bg;Drawable image=new ImageDrawable(bitmap,brightness,Math.max(2f,view.getWidth()*.12f),view);
        t.wrappedBackground=bg==null?image:new LayerDrawable(new Drawable[]{bg,image});view.setBackground(t.wrappedBackground);
    }
    private static void applySlider(ViewGroup root,Target t,AnimatedMedia bitmap,int brightness) {
        int innerId=root.getResources().getIdentifier("toggle_slider_inner","id","miui.systemui.plugin");
        int progressId=root.getResources().getIdentifier("progress","id","miui.systemui.plugin");
        View found=root.findViewById(innerId),progress=root.findViewById(progressId);
        if(!(found instanceof FrameLayout) || progress==null)return;
        FrameLayout inner=(FrameLayout)found;ImageView image=t.overlay==null?null:t.overlay.get();
        if(image==null || image.getParent()!=inner){
            image=new ImageView(root.getContext());image.setClickable(false);image.setFocusable(false);image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setClipToOutline(true);
            image.setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View v,Outline o){
                float radius=0f;
                int bgId=root.getResources().getIdentifier("progress_bg","id","miui.systemui.plugin");
                View bg=root.findViewById(bgId);
                if(bg!=null && bg.getBackground() instanceof GradientDrawable)radius=((GradientDrawable)bg.getBackground()).getCornerRadius();
                if(radius<=0f){int id=root.getResources().getIdentifier("control_center_universal_corner_radius","dimen","miui.systemui.plugin");if(id!=0)radius=root.getResources().getDimensionPixelSize(id);}
                if(radius<=0f)radius=v.getWidth()/2f;
                o.setRoundRect(0,0,v.getWidth(),v.getHeight(),radius);
            }});
            int index=inner.indexOfChild(progress);inner.addView(image,index<0?inner.getChildCount():index,new FrameLayout.LayoutParams(-1,-1));
            t.overlay=new WeakReference<>(image);t.progress=new WeakReference<>(progress);t.progressAlpha=progress.getAlpha();
        }
        image.clearColorFilter();image.setImageDrawable(new ImageDrawable(bitmap,brightness,0f,image));progress.setAlpha(.22f);
    }
    private static void applyMusic(ViewGroup root,Target t,AnimatedMedia bitmap,int brightness) {
        ImageView image=t.overlay==null?null:t.overlay.get();
        if(image==null || image.getParent()!=root){
            image=new ImageView(root.getContext());image.setClickable(false);image.setFocusable(false);image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setClipToOutline(true);
            image.setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View v,Outline o){o.setRoundRect(0,0,v.getWidth(),v.getHeight(),Math.min(v.getWidth(),v.getHeight())*.16f);}});
            int coverId=root.getResources().getIdentifier("cover","id","miui.systemui.plugin");View cover=root.findViewById(coverId);
            int index=cover==null?-1:root.indexOfChild(cover);
            root.addView(image,index<0?0:index+1,new ViewGroup.LayoutParams(-1,-1));t.overlay=new WeakReference<>(image);
        }
        image.clearColorFilter();image.setImageDrawable(new ImageDrawable(bitmap,brightness,0f,image));
    }
    private static ColorMatrixColorFilter colorFilter(int brightness){float p=brightness/100f;return new ColorMatrixColorFilter(new float[]{p,0,0,0,0, 0,p,0,0,0, 0,0,p,0,0, 0,0,0,1,0});}
    private static void restoreLayer(Target t){if(t.layer!=null && t.layerIndex>=0 && t.layerIndex<t.layer.getNumberOfLayers() && t.layer.getDrawable(t.layerIndex)==t.wrappedLayer)t.layer.setDrawable(t.layerIndex,t.originalLayer);t.layer=null;t.wrappedLayer=null;t.originalLayer=null;t.layerIndex=-1;}
    private static void restore(View view,Target t) {
        try {
            if(t.wrappedBackground!=null && view.getBackground()==t.wrappedBackground)view.setBackground(t.originalBackground);
            t.originalBackground=null;t.wrappedBackground=null;restoreLayer(t);
            ImageView image=t.overlay==null?null:t.overlay.get();if(image!=null && image.getParent() instanceof ViewGroup)((ViewGroup)image.getParent()).removeView(image);
            View progress=t.progress==null?null:t.progress.get();if(progress!=null && t.progressAlpha!=null)progress.setAlpha(t.progressAlpha);
            t.overlay=null;t.progress=null;t.progressAlpha=null;
        }catch(Throwable failure){Log.w(TAG,"Control Center image restore skipped",failure);}
    }
    private static final class ImageDrawable extends Drawable {
        private final AnimatedMedia media;private final WeakReference<View> owner;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final Matrix matrix=new Matrix();private final float radius;private Bitmap last;private BitmapShader shader;
        ImageDrawable(AnimatedMedia media,int brightness,float radius,View owner){this.media=media;this.radius=radius;this.owner=new WeakReference<>(owner);paint.setColorFilter(colorFilter(brightness));}
        @Override public void draw(Canvas c){Rect b=getBounds();Bitmap bitmap=media.frame(owner.get());if(b.isEmpty()||bitmap==null||bitmap.isRecycled())return;if(bitmap!=last){last=bitmap;shader=new BitmapShader(bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);paint.setShader(shader);}float scale=Math.max((float)b.width()/bitmap.getWidth(),(float)b.height()/bitmap.getHeight());matrix.setScale(scale,scale);matrix.postTranslate(b.left+(b.width()-bitmap.getWidth()*scale)/2f,b.top+(b.height()-bitmap.getHeight()*scale)/2f);shader.setLocalMatrix(matrix);float rad=Math.min(radius,Math.min(b.width(),b.height())/2f);c.drawRoundRect(new RectF(b),rad,rad,paint);}
        @Override public void getOutline(Outline outline){Rect b=getBounds();outline.setRoundRect(b,Math.min(radius,Math.min(b.width(),b.height())/2f));}
        @Override public void setAlpha(int a){paint.setAlpha(a);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
