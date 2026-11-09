// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;

import android.content.res.ColorStateList;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import java.lang.reflect.Method;
import java.util.*;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** HyperOS replaces the collapsed status bar with separate shade header views. */
final class ShadeStatusTintHooks {
    private static final String TAG="HyperDuo";
    private static final Map<View,Boolean> HEADERS=new WeakHashMap<>();
    private static final Map<Object,Method> OWNERS=new WeakHashMap<>();
    private static final StatusBarTintOverlay<ColorStateList> TEXT=new StatusBarTintOverlay<>();
    private static final StatusBarTintOverlay<ColorFilter> FILTERS=new StatusBarTintOverlay<>();
    private static boolean installed,reported;
    private ShadeStatusTintHooks(){}

    static synchronized void install(XposedModule module,ClassLoader loader) {
        if(installed)return;installed=true;
        // Match declared methods by verified names/signatures; suffixes vary across HyperOS builds.
        hookViews(module,loader,"com.android.systemui.statusbar.views.MiuiClock",true);
        hookViews(module,loader,"com.android.systemui.statusbar.views.MiuiNotificationHeaderClock",true);
        hookViews(module,loader,"com.android.systemui.qs.MiuiNotificationHeaderView",false);
        hookViews(module,loader,"com.android.systemui.controlcenter.shade.ControlCenterHeaderView",false);
        hookViews(module,loader,"com.android.systemui.controlcenter.shade.ControlCenterCarrierText",false);
        for(String name:new String[]{"CcStatusBarIcons","CcFakeStatusBarIcons"}) {
            Class<?> type=Refl.cls("com.android.systemui.controlcenter.header."+name,loader);
            if(type==null)continue;
            for(Method method:type.getDeclaredMethods()) {
                if(!method.getName().startsWith("updateHeaderColor") || method.getParameterCount()!=0)continue;
                try {
                    module.hook(method).setId("heart-shade-"+name).intercept(new XposedInterface.Hooker() {
                        @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
                            Object result=chain.proceed();
                            try {
                                Object owner=chain.getThisObject();
                                synchronized(OWNERS){OWNERS.put(owner,method);}
                                Object manager=Refl.get(Refl.field(owner.getClass(),"iconManager"),owner);
                                StatusBarTintHooks.applyManagedIcons(manager);
                            }catch(Throwable failure){report(failure);}
                            return result;
                        }
                    });
                }catch(Throwable failure){report(failure);}
            }
        }
        Log.i(TAG,"Shade header color hooks installed");
    }

    private static void hookViews(XposedModule module,ClassLoader loader,String name,boolean clock) {
        Class<?> type=Refl.cls(name,loader);if(type==null)return;
        for(Method method:type.getDeclaredMethods()) {
            String n=method.getName();
            boolean nativeClock=clock && n.startsWith("updateLightDarkTint") && method.getParameterCount()==6;
            boolean resources=!clock && (n.startsWith("updateResources") || n.equals("setTextAppearance")
                || n.equals("updateFlipResources") || n.equals("updateBigTimeColor") || n.equals("themeChanged"));
            boolean attach=n.equals("onAttachedToWindow") || n.equals("onFinishInflate");
            if(!nativeClock && !resources && !attach)continue;
            try {
                module.hook(method).setId("heart-shade-view-"+Integer.toHexString(method.toString().hashCode()))
                    .intercept(new XposedInterface.Hooker() {
                        @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
                            Object object=chain.getThisObject();View view=object instanceof View?(View)object:null;
                            // Release only this method's subtree before native resource writes.
                            // Nested methods can then capture their own newly computed baseline.
                            if(resources && view!=null && headerOf(view)!=null)safelyVisit(view,false);
                            Object result=chain.proceed();
                            try {
                                if(view==null)return result;
                                View header=headerOf(view);if(header==null)return result;
                                synchronized(HEADERS){HEADERS.put(header,Boolean.TRUE);}
                                if(nativeClock && view instanceof TextView) {
                                    TEXT.nativeUpdated(view);tintText((TextView)view,enabled());
                                } else safelyVisit(resources?view:header,enabled());
                            }catch(Throwable failure){report(failure);}
                            return result;
                        }
                    });
            }catch(Throwable failure){report(failure);}
        }
    }
    private static boolean enabled(){return StatusBarTintPolicy.enabled(TrioConfig.get(),HeartBattery.failed);}
    private static void report(Throwable failure){if(!reported){reported=true;Log.w(TAG,"Shade tint skipped",failure);}}
    private static boolean protectedView(View view) {
        if(StatusBarTintPolicy.protectedElement(view.getClass().getName()))return true;
        try {return view.getId()!=View.NO_ID && StatusBarTintPolicy.protectedElement(view.getResources().getResourceEntryName(view.getId()));}
        catch(Throwable ignored){return false;}
    }
    private static View headerOf(View view) {
        for(View current=view;current!=null;) {
            if(protectedView(current))return null;
            if(StatusBarTintPolicy.isShadeHeaderRoot(current.getClass().getName()))return current;
            Object parent=current.getParent();current=parent instanceof View?(View)parent:null;
        }
        return null;
    }
    private static void tintText(TextView view,boolean enabled) {
        if(enabled) {
            TEXT.remember(view,view.getTextColors());view.setTextColor(StatusBarTintHooks.selectedColor());
        }else if(TEXT.contains(view))view.setTextColor(TEXT.release(view));
    }
    private static void tintShortcut(Object owner,String field,boolean enabled) {
        Object object=Refl.get(Refl.field(owner.getClass(),field),owner);
        if(!(object instanceof ImageView))return;
        ImageView icon=(ImageView)object;
        if(headerOf(icon)==null)return;
        if(enabled) {
            FILTERS.remember(icon,icon.getColorFilter());icon.setColorFilter(StatusBarTintHooks.selectedColor(),PorterDuff.Mode.SRC_IN);
        }else if(FILTERS.contains(icon))icon.setColorFilter(FILTERS.release(icon));
    }
    private static void safelyVisit(View root,boolean enabled) {
        try {visit(root,enabled);}catch(Throwable failure){report(failure);}
    }
    private static void visit(View view,boolean enabled) {
        if(protectedView(view))return;
        if(view instanceof TextView)tintText((TextView)view,enabled);
        // Only the notification-management shortcut; never tint arbitrary drawables/tiles.
        if("com.android.systemui.qs.MiuiNotificationHeaderView".equals(view.getClass().getName())) {
            tintShortcut(view,"mShortCutTop",enabled);tintShortcut(view,"mShortCutBottom",enabled);
        }
        if(view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)visit(group.getChildAt(i),enabled);
        }
    }
    static void refresh() {
        List<Map.Entry<Object,Method>> owners;
        synchronized(OWNERS){owners=new ArrayList<>();for(Map.Entry<Object,Method> e:OWNERS.entrySet())owners.add(new AbstractMap.SimpleImmutableEntry<>(e));}
        for(Map.Entry<Object,Method> owner:owners)Refl.invoke(owner.getValue(),owner.getKey());
        List<View> headers;synchronized(HEADERS){headers=new ArrayList<>(HEADERS.keySet());}
        for(View header:headers)safelyVisit(header,enabled());
    }
}
