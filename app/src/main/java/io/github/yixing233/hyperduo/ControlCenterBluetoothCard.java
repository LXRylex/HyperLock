// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.lang.reflect.Method;
import java.util.*;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
/** Extend the native card list while the user enables the extra cards. */
final class ControlCenterBluetoothCard {
    private static final String TAG="HyperDuo";
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final WeakHashMap<Object,Boolean> CONTROLLERS=new WeakHashMap<>();
    private static Method create,distribute;
    private ControlCenterBluetoothCard(){}
    static void install(XposedModule module,ClassLoader loader) {
        try {
            Class<?> qs=Refl.cls("miui.systemui.controlcenter.qs.QSController",loader);
            Class<?> cards=Refl.cls("miui.systemui.controlcenter.panel.main.qs.QSCardsController",loader);
            if(qs==null || cards==null)return;
            Method specs=Refl.method(qs,"getCardStyleTileSpecs");create=Refl.method(cards,"createCardTiles");distribute=Refl.method(cards,"distributeTiles");
            if(specs==null || create==null || distribute==null)return;
            module.hook(specs).setId("cc-bluetooth-card-specs").intercept(new XposedInterface.Hooker(){
                @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Object nativeList=chain.proceed();
                    try {
                        if(TrioConfig.get().ccBluetoothPill && nativeList instanceof List<?>) {
                            @SuppressWarnings("unchecked") List<String> list=(List<String>)nativeList;
                            return ControlCenterImagePolicy.cardSpecs(list,true);
                        }
                    }catch(Throwable t){Log.w(TAG,"Bluetooth card list fell back",t);}
                    return nativeList;
                }
            });
            Method onCreate=Refl.method(cards,"onCreate");
            if(onCreate!=null)module.hook(onCreate).setId("cc-card-controller-lifecycle").intercept(new XposedInterface.Hooker(){
                @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Object result=chain.proceed();
                    synchronized(CONTROLLERS){CONTROLLERS.put(chain.getThisObject(),TrioConfig.get().ccBluetoothPill);}
                    return result;
                }
            });
            Class<?> cardView=Refl.cls("miui.systemui.controlcenter.qs.tileview.QSCardItemView",loader);
            Method background=cardView==null?null:Refl.method(cardView,"updateBackground",boolean.class);
            if(background!=null)module.hook(background).setId("cc-airplane-card-background").intercept(new XposedInterface.Hooker(){
                @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Object result=chain.proceed();
                    try {
                        if(!TrioConfig.get().ccBluetoothPill || !(chain.getThisObject() instanceof View))return result;
                        View view=(View)chain.getThisObject();
                        Object state=Refl.get(Refl.field(view.getClass(),"state"),view);
                        Object spec=state==null?null:Refl.get(Refl.field(state.getClass(),"spec"),state);
                        int value=state==null?0:Refl.getInt(Refl.field(state.getClass(),"state"),state,0);
                        if(!"airplane".equals(spec) || value<1 || value>2)return result;
                        Context c=view.getContext();String name=value==2?"qs_card_wifi_background_enabled":"qs_card_wifi_background_disabled";
                        int id=c.getResources().getIdentifier(name,"drawable","miui.systemui.plugin");
                        if(id!=0){Drawable d=c.getResources().getDrawable(id,c.getTheme());view.setBackground(d);}
                    }catch(Throwable t){Log.w(TAG,"Airplane card background skipped",t);}
                    return result;
                }
            });
            Log.i(TAG,"Large Bluetooth card hooks installed");
        }catch(Throwable t){Log.w(TAG,"Large Bluetooth card unsupported",t);}
    }
    static void refresh() {
        MAIN.post(()->{
            List<Map.Entry<Object,Boolean>> list=new ArrayList<>();
            synchronized(CONTROLLERS){for(Map.Entry<Object,Boolean> e:CONTROLLERS.entrySet())list.add(new AbstractMap.SimpleImmutableEntry<>(e));}
            boolean desired=TrioConfig.get().ccBluetoothPill;
            for(Map.Entry<Object,Boolean> e:list)if(e.getKey()!=null && e.getValue()!=desired) {
                try {create.invoke(e.getKey());distribute.invoke(e.getKey());}
                catch(Throwable t){Log.w(TAG,"Large Bluetooth card refresh skipped",t);}
            }
        });
    }
}
