// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;

import android.content.Context;
import android.graphics.Insets;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.WindowManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Opt-in, portrait-only reduction of SystemUI's navigation inset. */
final class KeyboardInsetHooks {
 private static final String TAG="HyperDuo";
 private static volatile boolean reported;
 private KeyboardInsetHooks() {}
 static void install(XposedModule module, ClassLoader loader) {
  try {
   Class<?> nav=Refl.cls("com.android.systemui.navigationbar.views.NavigationBar",loader);
   Method layout=Refl.method(nav,"getBarLayoutParamsForRotation",int.class);
   Field ime=Refl.field(nav,"mImeVisible"), context=Refl.field(nav,"mContext");
   Field supplied=Refl.field(WindowManager.LayoutParams.class,"providedInsets");
   Class<?> provider=Refl.cls("android.view.InsetsFrameProvider",loader);
   Method type=Refl.method(provider,"getType"), getSize=Refl.method(provider,"getInsetsSize"), setSize=Refl.method(provider,"setInsetsSize",Insets.class);
   if(layout==null||ime==null||context==null||supplied==null||type==null||getSize==null||setSize==null) {
    Log.w(TAG,"Keyboard gap hook unavailable; using system insets"); return;
   }
   module.hook(layout).setId("keyboard-gap-layout").intercept(new XposedInterface.Hooker() {
    @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
     Object result=chain.proceed();
     Object source=null; Insets prior=null;
     try {
      Object owner=chain.getThisObject();
      if(!(result instanceof WindowManager.LayoutParams))return result;
      WindowManager.LayoutParams params=(WindowManager.LayoutParams)result;
      Object c=Refl.get(context,owner);
      Object arg=chain.getArgs().get(0);
      int rotation=arg instanceof Number?((Number)arg).intValue():-1;
      if(!(c instanceof Context))return result;
      Context ctx=(Context)c;
      TrioSettings cfg=TrioConfig.get();
      boolean gestures=Settings.Global.getInt(ctx.getContentResolver(),"force_fsg_nav_bar",0)==1;
      boolean bottom=(params.gravity & Gravity.BOTTOM)==Gravity.BOTTOM && params.width==WindowManager.LayoutParams.MATCH_PARENT;
      boolean system = KeyboardInsetPolicy.shouldAdjustSystem(cfg!=null && cfg.compactNavigation,gestures,bottom,rotation);
      boolean keyboard = KeyboardInsetPolicy.shouldAdjust(cfg!=null && cfg.compactKeyboard,Refl.getBool(ime,owner,false),gestures,bottom,rotation);
      if(!system && !keyboard)return result;
      if(params.height<=0)return result;
      int height=KeyboardInsetPolicy.compactHeight(params.height,ctx.getResources().getDisplayMetrics().density);
      if(height>=params.height && !system)return result;
      Object candidates=Refl.get(supplied,params);
      if(!(candidates instanceof Object[]))return result;
      for(Object candidate:(Object[])candidates) {
       if(candidate==null || !provider.isInstance(candidate))continue;
       Object foundType=type.invoke(candidate);
       if(!(foundType instanceof Integer) || ((Integer)foundType)!=WindowInsets.Type.navigationBars())continue;
       Object size=getSize.invoke(candidate);
       if(!(size instanceof Insets))return result;
       prior=(Insets)size;
       if(prior.left!=0||prior.right!=0||prior.top!=0||prior.bottom<height)return result;
       source=candidate;
       // Keep the keyboard clearance the user already chose, even with the
       // system-wide option enabled; elsewhere allow content to reach the edge.
       boolean keyboardVisible = Refl.getBool(ime,owner,false);
       setSize.invoke(source,system && !keyboardVisible ? Insets.NONE : Insets.of(0,0,0,height));
       params.height=height;
       if(!reported){reported=true;Log.i(TAG,system ? "System navigation inset reduced; keeping gesture strip" : "Keyboard gap reduced to "+height+"px while IME is visible");}
       break;
      }
     } catch(Throwable failure) {
      if(source!=null && prior!=null)try{setSize.invoke(source,prior);}catch(Throwable ignored){}
      if(!reported){reported=true;Log.w(TAG,"Keyboard gap adjustment skipped safely",failure);}
     }
     return result;
    }
   });
   Log.i(TAG,"Keyboard gap hook installed");
  }catch(Throwable failure){Log.w(TAG,"Keyboard gap hook unavailable; using system insets",failure);}
 }
}
