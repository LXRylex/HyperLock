// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;

import android.graphics.ColorFilter;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.*;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Optional icon-only tints. Tile backgrounds and imported bitmaps are never recolored. */
final class ControlCenterIconColors {
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final WeakHashMap<ImageView,Entry> VIEWS=new WeakHashMap<>();
    private static final WeakHashMap<ImageView,WeakReference<Object>> TILE_OWNERS=new WeakHashMap<>();
    private static final WeakHashMap<TextView,TextEntry> TEXTS=new WeakHashMap<>();
    private static final class TextEntry {
        ColorStateList nativeColors;
        boolean active, overridden;
    }
    private static final WeakHashMap<Object,WeakReference<View>> CONTROLLERS=new WeakHashMap<>();
    private static final WeakHashMap<Drawable,ColorFilter> ORIGINAL_DRAWABLES=new WeakHashMap<>();
    private static final WeakHashMap<LayerDrawable,TileBackground> TILE_BACKGROUNDS=new WeakHashMap<>();
    private static final class TileBackground {
        final int index; final Drawable original,replacement;
        TileBackground(int i,Drawable original,Drawable replacement){this.index=i;this.original=original;this.replacement=replacement;}
    }
    private static final class Entry {
        boolean tileLayer,active;
        ColorFilter original;
        boolean saved, blendForced;
        Entry(boolean layer,boolean active){this.tileLayer=layer;this.active=active;}
    }
    private static Object commonUtils;
    private static Method clearBlend;
    static void install(XposedModule module,ClassLoader loader) {
        try {
            Class<?> utils=Refl.cls("miui.systemui.util.CommonUtils",loader);
            if(utils!=null){java.lang.reflect.Field f=Refl.field(utils,"INSTANCE");if(f!=null)commonUtils=f.get(null);clearBlend=Refl.method(utils,"clearMiBlurBlendEffect",View.class);}
        }catch(Throwable t){Log.w("HyperDuo","slider blend helper unavailable",t);}
        Class<?> animated=Refl.cls("miui.systemui.controlcenter.widget.AnimateColorView",loader);
        if(animated!=null)for(Method m:animated.getDeclaredMethods())if(m.getName().equals("updateIconColor") && (m.getParameterCount()==5 || m.getParameterCount()==2))try{
            module.hook(m).setId("cc-slider-icon-color-"+m.getParameterCount()).intercept(new XposedInterface.Hooker(){
                @Override public Object intercept(XposedInterface.Chain chain)throws Throwable {
                    Object owner=chain.getThisObject();
                    try {
                        if(owner instanceof ImageView && Looper.myLooper()==Looper.getMainLooper()){
                            ImageView image=(ImageView)owner;Entry e=VIEWS.get(image);
                            if(e==null){discoverSlider(image);e=VIEWS.get(image);}
                            if(e!=null && TrioConfig.get().ccIconColorsEnabled){
                                updateSliderState(image,e);apply(image,e);return null;
                            }
                            if(e==null)image.post(()->discoverSlider(image));
                        }
                    }catch(Throwable t){Log.w("HyperDuo","animated slider tint skipped",t);}
                    return chain.proceed();
                }
            });
        }catch(Throwable t){Log.w("HyperDuo","animated slider hook unavailable",t);}
        Class<?> smallIcon=Refl.cls("miui.systemui.controlcenter.qs.tileview.QSTileItemIconView",loader);
        Method progress=smallIcon==null?null:Refl.method(smallIcon,"setIconProgress",float.class);
        if(progress!=null)try{
            module.hook(progress).setId("cc-light-icon-tile-backdrop").intercept(new XposedInterface.Hooker(){
                @Override public Object intercept(XposedInterface.Chain chain)throws Throwable {
                    try {
                        Object owner=chain.getThisObject();Object state=Refl.get(Refl.field(owner.getClass(),"state"),owner);
                        int value=state==null?0:Refl.getInt(Refl.field(state.getClass(),"state"),state,0);
                        Object icon=Refl.callByName(owner,"getIcon");TrioSettings settings=TrioConfig.get();
                        if(icon instanceof ImageView && ((ImageView)icon).getBackground()==null && ControlCenterImagePolicy.needsDarkTileBackground(value==2,settings.ccIconColorsEnabled,settings.ccIconActiveColor))return chain.proceed(new Object[]{0.18f});
                    }catch(Throwable t){Log.w("HyperDuo","tile backdrop hook skipped",t);}
                    return chain.proceed();
                }
            });
        }catch(Throwable t){Log.w("HyperDuo","tile backdrop hook unavailable",t);}
        for(String name:new String[]{"miui.systemui.controlcenter.panel.main.brightness.BrightnessSliderController","miui.systemui.controlcenter.panel.main.volume.VolumeSliderController"}) {
            Class<?> type=Refl.cls(name,loader);if(type==null)continue;
            for(Method m:type.getDeclaredMethods())if(m.getName().equals("updateIcon") || m.getName().equals("updateIconProgress"))try{
                module.hook(m).setId("cc-color-"+type.getSimpleName()+"-"+m.getName()+"-"+m.getParameterCount()).intercept(new XposedInterface.Hooker(){
                    @Override public Object intercept(XposedInterface.Chain chain)throws Throwable {
                        Object result=chain.proceed();
                        try{Object owner=chain.getThisObject();WeakReference<View> ref=CONTROLLERS.get(owner);View root=ref==null?null:ref.get();if(root!=null)registerRoot(owner,root,name.contains("Volume")?"volume":"brightness");}catch(Throwable t){Log.w("HyperDuo","slider color update skipped",t);}
                        return result;
                    }
                });
            }catch(Throwable t){Log.w("HyperDuo","slider color hook unavailable",t);}
        }
    }
    static void tile(Object state,Object owner,boolean small){
        try {
            int v=state==null?0:Refl.getInt(Refl.field(state.getClass(),"state"),state,0);
            Object icon=small?owner:Refl.callByName(owner,"getIcon");
            Object direct=Refl.callByName(icon,"getIcon");
            if(direct instanceof ImageView){if(small)TILE_OWNERS.put((ImageView)direct,new WeakReference<>(owner));track((ImageView)direct,small,v==2);}
            else if(icon instanceof View)findImages((View)icon,small,v==2);
        }catch(Throwable t){Log.w("HyperDuo","tile color skipped",t);}
    }
    /** Called after Xiaomi updates card text so the latest native colors can be restored. */
    static void cardText(Object state,Object owner) {
        if(!(owner instanceof View) || state==null)return;
        try {
            Object spec=Refl.get(Refl.field(state.getClass(),"spec"),state);
            String slot=ControlCenterImagePolicy.tileSlot(spec instanceof String?(String)spec:null);
            if(!"wifi".equals(slot) && !"mobile".equals(slot) && !"bluetooth".equals(slot) && !"airplane".equals(slot))return;
            View card=(View)owner;
            boolean active=Refl.getInt(Refl.field(state.getClass(),"state"),state,0)==2;
            for(String name:new String[]{"title","status"}) {
                int id=card.getResources().getIdentifier(name,"id","miui.systemui.plugin");
                View found=id==0?null:card.findViewById(id);
                if(!(found instanceof TextView))continue;
                TextView text=(TextView)found;TextEntry entry=TEXTS.get(text);
                if(entry==null){entry=new TextEntry();TEXTS.put(text,entry);}
                entry.nativeColors=text.getTextColors();entry.active=active;entry.overridden=false;
                applyText(text,entry);
            }
        }catch(Throwable t){Log.w("HyperDuo","card text color skipped",t);}
    }
    private static void applyText(TextView text,TextEntry entry){
        TrioSettings settings=TrioConfig.get();
        if(settings.ccTextColorsEnabled){
            if(!entry.overridden)entry.nativeColors=text.getTextColors();
            text.setTextColor(entry.active?settings.ccTextActiveColor:settings.ccTextInactiveColor);entry.overridden=true;
        }else if(entry.overridden){
            if(entry.nativeColors!=null)text.setTextColor(entry.nativeColors);
            entry.overridden=false;
        }
    }
    private static void updateSliderState(ImageView image,Entry entry){
        for(Map.Entry<Object,WeakReference<View>> item:new ArrayList<>(CONTROLLERS.entrySet())){
            Object controller=item.getKey();View root=item.getValue()==null?null:item.getValue().get();
            if(controller==null || root==null)continue;
            String name=controller.getClass().getName();
            if(!name.endsWith("VolumeSliderController") && !name.endsWith("BrightnessSliderController"))continue;
            int id=root.getResources().getIdentifier("icon","id","miui.systemui.plugin");
            if(id!=0 && root.findViewById(id)==image){
                entry.active=image.isEnabled() && (!name.endsWith("VolumeSliderController") || !Refl.getBool(Refl.field(controller.getClass(),"muted"),controller,false));
                return;
            }
        }
    }
    private static void discoverSlider(ImageView image){
        try {
            int id=image.getResources().getIdentifier("toggle_slider_inner","id","miui.systemui.plugin");
            android.view.ViewParent parent=image.getParent();
            for(int i=0;i<8 && parent instanceof View;i++){
                View v=(View)parent;
                if(id!=0 && v.getId()==id){track(image,false,image.isEnabled());return;}
                parent=v.getParent();
            }
        }catch(Throwable t){Log.w("HyperDuo","slider icon discovery skipped",t);}
    }
    private static void findImages(View view,boolean layer,boolean active){
        if(view instanceof ImageView){track((ImageView)view,layer,active);return;}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)findImages(group.getChildAt(i),layer,active);}
    }
    static void registerRoot(Object controller,View root,String slot){
        if(Looper.myLooper()!=Looper.getMainLooper()){MAIN.post(()->registerRoot(controller,root,slot));return;}
        CONTROLLERS.put(controller,new WeakReference<>(root));
        if("brightness".equals(slot)||"volume".equals(slot)){
            int id=root.getResources().getIdentifier("icon","id","miui.systemui.plugin");View icon=root.findViewById(id);
            boolean active=icon!=null && icon.isEnabled();
            if("volume".equals(slot))active=active && !Refl.getBool(Refl.field(controller.getClass(),"muted"),controller,false);
            if(icon instanceof ImageView)track((ImageView)icon,false,active);
        } else if("music".equals(slot)){
            for(String name:new String[]{"prev","play","next","device_icon"}){
                int id=root.getResources().getIdentifier(name,"id","miui.systemui.plugin");View v=root.findViewById(id);
                if(v instanceof ImageView)track((ImageView)v,false,v.isEnabled());
            }
        }
    }
    private static void track(ImageView view,boolean layer,boolean active){
        if(Looper.myLooper()!=Looper.getMainLooper()){MAIN.post(()->track(view,layer,active));return;}
        Entry e=VIEWS.get(view);if(e==null){e=new Entry(layer,active);VIEWS.put(view,e);}e.tileLayer=layer;e.active=active;apply(view,e);
    }
    static void refresh(){
        if(Looper.myLooper()!=Looper.getMainLooper()){MAIN.post(ControlCenterIconColors::refresh);return;}
        for(Map.Entry<TextView,TextEntry> item:new ArrayList<>(TEXTS.entrySet()))if(item.getKey()!=null)try{applyText(item.getKey(),item.getValue());}catch(Throwable t){Log.w("HyperDuo","card text refresh skipped",t);}
        for(Map.Entry<ImageView,Entry> item:new ArrayList<>(VIEWS.entrySet()))if(item.getKey()!=null)try{apply(item.getKey(),item.getValue());}catch(Throwable t){Log.w("HyperDuo","icon color refresh skipped",t);}
        if(!TrioConfig.get().ccIconColorsEnabled)for(Object controller:new ArrayList<>(CONTROLLERS.keySet()))try{
            if(controller==null)continue;String name=controller.getClass().getName();
            if(name.endsWith("BrightnessSliderController")){Method m=Refl.method(controller.getClass(),"updateIconProgress",boolean.class);if(m!=null)m.invoke(controller,true);}
            else if(name.endsWith("VolumeSliderController")){Method m=Refl.method(controller.getClass(),"updateIconProgress",boolean.class,boolean.class);if(m!=null)m.invoke(controller,false,true);}
        }catch(Throwable t){Log.w("HyperDuo","native slider color restore skipped",t);}
    }
    private static void updateBlurBackdrop(ImageView view,Entry e,TrioSettings settings){
        boolean dark=ControlCenterImagePolicy.needsDarkTileBackground(e.active,settings.ccIconColorsEnabled,settings.ccIconActiveColor);
        if(view.getBackground()!=null)dark=false; // Preserve an imported small-tile image.
        if(dark==e.blendForced)return;
        WeakReference<Object> ref=TILE_OWNERS.get(view);Object owner=ref==null?null:ref.get();
        Method progress=owner==null?null:Refl.method(owner.getClass(),"setIconProgress",float.class);
        if(progress==null)return;
        try{progress.invoke(owner,dark?0.18f:(e.active?1f:0f));e.blendForced=dark;}
        catch(Throwable t){Log.w("HyperDuo","tile backdrop update skipped",t);}
    }
    private static void tileBackground(ImageView view,LayerDrawable layers,boolean dark){
        TileBackground saved=TILE_BACKGROUNDS.get(layers);
        if(!dark){
            if(saved!=null && saved.index<layers.getNumberOfLayers() && layers.getDrawable(saved.index)==saved.replacement)layers.setDrawable(saved.index,saved.original);
            TILE_BACKGROUNDS.remove(layers);return;
        }
        if(saved!=null)return;
        int count=layers.getNumberOfLayers();if(count<2)return;
        int index=count-2;Drawable original=layers.getDrawable(index);
        // A nested layer can contain the user's custom Bluetooth image: leave it untouched.
        if(original instanceof LayerDrawable)return;
        Drawable replacement;
        if(count>=3)replacement=new ColorDrawable(android.graphics.Color.TRANSPARENT);
        else {
            int id=view.getResources().getIdentifier("qs_background_disabled","drawable","miui.systemui.plugin");
            if(id==0)return;
            replacement=view.getContext().getTheme().getDrawable(id);
            if(replacement==null)return;
        }
        layers.setDrawable(index,replacement);TILE_BACKGROUNDS.put(layers,new TileBackground(index,original,replacement));
    }
    private static void apply(ImageView view,Entry e){
        TrioSettings s=TrioConfig.get();
        if(e.tileLayer){
            Drawable d=view.getDrawable();if(d instanceof LayerDrawable){LayerDrawable layers=(LayerDrawable)d;
                if(layers.getNumberOfLayers()==1)updateBlurBackdrop(view,e,s);
                tileBackground(view,layers,ControlCenterImagePolicy.needsDarkTileBackground(e.active,s.ccIconColorsEnabled,s.ccIconActiveColor));
                int n=layers.getNumberOfLayers();d=n==0?null:layers.getDrawable(n-1);}
            if(d==null)return;
            if(s.ccIconColorsEnabled){if(!ORIGINAL_DRAWABLES.containsKey(d))ORIGINAL_DRAWABLES.put(d,d.getColorFilter());d.setColorFilter(e.active?s.ccIconActiveColor:s.ccIconInactiveColor,PorterDuff.Mode.SRC_IN);}
            else if(ORIGINAL_DRAWABLES.containsKey(d)){ColorFilter original=ORIGINAL_DRAWABLES.remove(d);if(original==null)d.clearColorFilter();else d.setColorFilter(original);}
            view.invalidate();return;
        }
        if(s.ccIconColorsEnabled){
            if(!e.saved){e.original=view.getColorFilter();e.saved=true;}
            int color=e.active?s.ccIconActiveColor:s.ccIconInactiveColor;
            if(view.getClass().getName().equals("miui.systemui.controlcenter.widget.AnimateColorView")){
                Refl.callByName(view,"cancelNormalAnimation");
                try{if(commonUtils!=null && clearBlend!=null)clearBlend.invoke(commonUtils,view);}catch(Throwable ignored){}
                Drawable drawable=view.getDrawable();if(drawable!=null)drawable.setTint(color);
            }
            view.setColorFilter(color,PorterDuff.Mode.SRC_IN);
        } else if(e.saved){if(e.original==null)view.clearColorFilter();else view.setColorFilter(e.original);e.saved=false;e.original=null;}
    }
}
