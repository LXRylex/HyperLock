// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.graphics.PorterDuff;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Native status-bar receivers retain their own drawing, alpha and signal semantics. */
final class StatusBarTintHooks {
    private static final String TAG="HyperDuo";
    private static final Object LOCK=new Object();
    private static final Map<Object,Boolean> DISPATCHERS=new WeakHashMap<>();
    private static final Map<Class<?>,List<Field>> VIEW_FIELDS=new HashMap<>();
    private static Field receivers;
    private static Method refresh,refreshIcon,refreshKeyguard;
    private static final Map<Object,Boolean> KEYGUARDS=new WeakHashMap<>();
    private static final Map<Object,Boolean> CLAIMED_MANAGERS=new WeakHashMap<>();
    private static final ThreadLocal<Boolean> KEYGUARD_PASS=new ThreadLocal<>();
    private static final Map<View,Boolean> ICONS=new WeakHashMap<>();
    private static Class<?> receiverApi;
    private static Handler main;
    private static boolean installed,queued,reported;
    private static volatile int level=-1,lastColor;
    private static volatile boolean charging;
    private StatusBarTintHooks() {}
    static synchronized void install(XposedModule module,ClassLoader loader) {
        if(installed)return;
        try {
            Class<?> dispatcher=Refl.cls("com.android.systemui.statusbar.phone.DarkIconDispatcherImpl",loader);
            receiverApi=Refl.cls("com.android.systemui.plugins.DarkIconDispatcher$DarkReceiver",loader);
            if(dispatcher==null || receiverApi==null)return;
            receivers=Refl.field(dispatcher,"mReceivers");refresh=Refl.method(dispatcher,"applyIconTint");
            if(receivers==null || refresh==null){Log.w(TAG,"Status tint: unsupported dispatcher");return;}
            main=new Handler(Looper.getMainLooper());
            int count=0;
            for(Method method:dispatcher.getDeclaredMethods()) {
                final boolean adding="addDarkReceiver".equals(method.getName());
                if(!adding && !"applyIconTint".equals(method.getName()))continue;
                try {
                    module.hook(method).setId("heart-status-tint-"+Integer.toHexString(method.toString().hashCode()))
                        .intercept(new XposedInterface.Hooker() {
                            @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
                                if(!adding)prepare(chain.getThisObject());
                                Object result=chain.proceed();
                                if(adding){prepare(chain.getThisObject());requestRefresh();}
                                return result;
                            }
                        });
                    count++;
                }catch(Throwable failure){Log.w(TAG,"Status tint hook skipped: "+method.getName(),failure);}
            }
            installed=count>0;
            if(installed){installKeyguard(module,loader,dispatcher);installNotificationIcons(module,loader);ShadeStatusTintHooks.install(module,loader);TrioConfig.addListener(StatusBarTintHooks::requestRefresh);}
            Log.i(TAG,"Status tint dispatcher hooks="+count);
        }catch(Throwable failure){Log.w(TAG,"Status tint installation skipped",failure);}
    }
    private static void installKeyguard(XposedModule module,ClassLoader loader,Class<?> dispatcher) {
        Class<?> root=Refl.cls("com.android.systemui.statusbar.phone.MiuiKeyguardStatusBarView",loader);
        if(root==null)root=Refl.cls("com.android.systemui.statusbar.phone.KeyguardStatusBarView",loader);
        refreshKeyguard=Refl.method(root,"updateIconsAndTextColors");
        if(refreshKeyguard==null)return;
        try {
            module.hook(refreshKeyguard).setId("heart-keyguard-colors").intercept(new XposedInterface.Hooker() {
                @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    synchronized(LOCK){KEYGUARDS.put(chain.getThisObject(),Boolean.TRUE);}
                    Boolean previous=KEYGUARD_PASS.get();KEYGUARD_PASS.set(Boolean.TRUE);
                    try {
                        Object result=chain.proceed();
                        Object owner=chain.getThisObject();
                        applyManagedIcons(Refl.get(Refl.field(owner.getClass(),"mTintedIconManager"),owner));
                        return result;
                    }
                    finally {if(previous==null)KEYGUARD_PASS.remove();else KEYGUARD_PASS.set(previous);}
                }
            });
            for(String name:new String[]{"getLightModeIconColorSingleTone","getDarkModeIconColorSingleTone"}) {
                Method getter=Refl.method(dispatcher,name);if(getter==null)continue;
                module.hook(getter).setId("heart-keyguard-"+name).intercept(new XposedInterface.Hooker() {
                    @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        Object nativeColor=chain.proceed();
                        try {
                            if(Boolean.TRUE.equals(KEYGUARD_PASS.get()) && StatusBarTintPolicy.enabled(TrioConfig.get(),HeartBattery.failed))
                                return selectedColor();
                        }catch(Throwable failure){Log.w(TAG,"Keyguard tint fell back",failure);}
                        return nativeColor;
                    }
                });
            }
            Class<?> manager=Refl.cls("com.android.systemui.statusbar.phone.MiuiLightDarkIconManager",loader);
            if(manager!=null)for(Method method:manager.getDeclaredMethods()) {
                if(!"onIconAdded".equals(method.getName()))continue;
                module.hook(method).setId("heart-keyguard-added-icon").intercept(new XposedInterface.Hooker() {
                    @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        Object result=chain.proceed();applyManagedIcons(chain.getThisObject());return result;
                    }
                });
            }
            Log.i(TAG,"Lock-screen status color hooks installed");
        }catch(Throwable failure){Log.w(TAG,"Lock-screen tint unavailable",failure);}
    }
    static void applyManagedIcons(Object manager) {
        try {
            if(manager==null)return;
            boolean enabled=StatusBarTintPolicy.enabled(TrioConfig.get(),HeartBattery.failed);
            synchronized(LOCK){if(!enabled && !CLAIMED_MANAGERS.containsKey(manager))return;}
            Object group=Refl.get(Refl.field(manager.getClass(),"mGroup"),manager);
            if(!(group instanceof ViewGroup) || !inBar((View)group))return;
            // The manager can skip setLight when native color already equals the
            // custom color. Explicitly restore its original resource/tint mode.
            int color=enabled?selectedColor():Refl.getInt(Refl.field(manager.getClass(),"mColor"),manager,-1);
            boolean nativeLight=Refl.getBool(Refl.field(manager.getClass(),"mLight"),manager,true);
            float intensity=enabled?0f:(nativeLight?0f:1f);
            ArrayList<Object> areas=new ArrayList<>();
            Method dark=Refl.method(receiverApi,"onDarkChanged",ArrayList.class,float.class,int.class);
            Method light=Refl.method(receiverApi,"onLightDarkTintChanged",int.class,int.class,boolean.class);
            ViewGroup views=(ViewGroup)group;
            for(int i=0;i<views.getChildCount();i++) {
                View child=views.getChildAt(i);
                if(!receiverApi.isInstance(child) || !inBar(child))continue;
                Refl.invokeArgs(dark,child,new Object[]{areas,intensity,color});
                // Xiaomi's lock manager forces resource-only mode. Enable the
                // existing tint path so Wi-Fi and mobile icons use our color.
                Refl.invokeArgs(light,child,new Object[]{color,color,enabled});
            }
            synchronized(LOCK){if(enabled)CLAIMED_MANAGERS.put(manager,Boolean.TRUE);else CLAIMED_MANAGERS.remove(manager);}
        }catch(Throwable failure){Log.w(TAG,"Lock-screen network tint skipped",failure);}
    }
    // HyperOS notification icons bypass the network/clock receiver tint. Run native
    // color rendering first, then apply a filter only to icons inside the status bar.
    // No native color fields are changed, so the same native method restores them.
    private static void installNotificationIcons(XposedModule module,ClassLoader loader) {
        Class<?> icon=Refl.cls("com.android.systemui.statusbar.StatusBarIconView",loader);
        if(icon==null)return;
        refreshIcon=Refl.method(icon,"updateIconColor");
        if(refreshIcon==null)return;
        for(Method method:icon.getDeclaredMethods()) {
            if(!"updateIconColor".equals(method.getName()) && !"updateLightDarkTint".equals(method.getName()))continue;
            try {
                module.hook(method).setId("heart-notification-tint-"+method.getName())
                    .intercept(new XposedInterface.Hooker() {
                        @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
                            Object result=chain.proceed();
                            try {
                                Object owner=chain.getThisObject();
                                if(owner instanceof ImageView) {
                                    ImageView view=(ImageView)owner;
                                    synchronized(LOCK){ICONS.put(view,Boolean.TRUE);}
                                    if(StatusBarTintPolicy.enabled(TrioConfig.get(),HeartBattery.failed) && inBar(view))
                                        view.setColorFilter(selectedColor(),PorterDuff.Mode.SRC_IN);
                                }
                            }catch(Throwable failure){Log.w(TAG,"Notification tint skipped",failure);}
                            return result;
                        }
                    });
            }catch(Throwable failure){Log.w(TAG,"Notification tint hook skipped: "+method.getName(),failure);}
        }
    }
    static int selectedColor(){return StatusBarTintPolicy.color(TrioConfig.get(),level,charging);}
    static void sampleBattery(int nextLevel,boolean nextCharging) {
        level=nextLevel;charging=nextCharging;
        int color=selectedColor();
        if(color!=lastColor){lastColor=color;requestRefresh();}
    }
    static void rootReady(){requestRefresh();}
    @SuppressWarnings("unchecked") private static void prepare(Object dispatcher) {
        if(dispatcher==null || receivers==null)return;
        try {
            synchronized(LOCK){DISPATCHERS.put(dispatcher,Boolean.TRUE);}
            Object value=Refl.get(receivers,dispatcher);
            if(!(value instanceof Map<?,?>))return;
            Map<Object,Object> map=(Map<Object,Object>)value;
            for(Map.Entry<Object,Object> entry:map.entrySet()) {
                final Object key=entry.getKey(),delegate=entry.getValue();
                if(delegate==null || StatusBarTintProxy.wrapped(delegate))continue;
                Object proxy=StatusBarTintProxy.wrap(receiverApi,delegate,
                    ()->StatusBarTintPolicy.enabled(TrioConfig.get(),HeartBattery.failed) && inStatusBar(key,delegate),
                    StatusBarTintHooks::selectedColor,
                    failure->Log.w(TAG,"Status tint receiver fell back: "+delegate.getClass().getName(),failure));
                entry.setValue(proxy);
            }
        }catch(Throwable failure){
            if(!reported){reported=true;Log.w(TAG,"Status tint receiver preparation skipped",failure);}
        }
    }
    private static boolean inStatusBar(Object key,Object receiver) {
        return ownsStatusView(key) || (key!=receiver && ownsStatusView(receiver));
    }
    private static boolean ownsStatusView(Object owner) {
        if(owner instanceof View)return inBar((View)owner);
        if(owner==null)return false;
        for(Field field:fieldsOf(owner.getClass())) {
            try {Object view=field.get(owner);if(view instanceof View && inBar((View)view))return true;}
            catch(Throwable ignored){}
        }
        return false;
    }
    private static List<Field> fieldsOf(Class<?> type) {
        synchronized(LOCK) {
            List<Field> cached=VIEW_FIELDS.get(type);if(cached!=null)return cached;
            List<Field> fields=new ArrayList<>();
            for(Class<?> c=type;c!=null && c!=Object.class;c=c.getSuperclass()) {
                for(Field f:c.getDeclaredFields()) {
                    if(java.lang.reflect.Modifier.isStatic(f.getModifiers()))continue;
                    if(!View.class.isAssignableFrom(f.getType()) && f.getType()!=Object.class)continue;
                    try {f.setAccessible(true);fields.add(f);}catch(Throwable ignored){}
                }
            }
            VIEW_FIELDS.put(type,fields);return fields;
        }
    }
    private static boolean inBar(View view) {
        for(View current=view;current!=null;) {
            String name=current.getClass().getName();
            if(StatusBarTintPolicy.protectedElement(name))return false;
            try {
                if(current.getId()!=View.NO_ID && StatusBarTintPolicy.protectedElement(
                    current.getResources().getResourceEntryName(current.getId())))return false;
            }catch(Throwable ignored){}
            if(StatusBarTintPolicy.isStatusBarRoot(name))return true;
            Object parent=current.getParent();current=parent instanceof View ? (View)parent : null;
        }
        return false;
    }
    static void requestRefresh() {
        Handler handler=main;if(handler==null)return;
        synchronized(LOCK){if(queued)return;queued=true;}
        handler.post(()->{
            List<Object> targets;
            synchronized(LOCK){queued=false;targets=new ArrayList<>(DISPATCHERS.keySet());}
            List<Object> keyguards;
            synchronized(LOCK){keyguards=new ArrayList<>(KEYGUARDS.keySet());}
            for(Object keyguard:keyguards)Refl.invoke(refreshKeyguard,keyguard);
            List<View> icons;
            synchronized(LOCK){icons=new ArrayList<>(ICONS.keySet());}
            for(View icon:icons) {
                // Native rendering restores icons even when the feature is switched off.
                if(inBar(icon))Refl.invoke(refreshIcon,icon);
            }
            for(Object target:targets) {
                try {prepare(target);Refl.invoke(refresh,target);}catch(Throwable failure){Log.w(TAG,"Status tint refresh skipped",failure);}
            }
            ShadeStatusTintHooks.refresh();
        });
    }
}
