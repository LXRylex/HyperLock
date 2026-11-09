// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;

import android.content.Context;
import android.provider.Settings;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.MediaPlayer;
import android.media.audiofx.LoudnessEnhancer;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Replace only Xiaomi Security's wired charging file, keeping its playback and sound policy. */
final class ChargingSoundHooks {
 private static final String TAG="HyperDuo";
 private static final String ORIGINAL="/system/media/audio/ui/charging.ogg";
 private static final String CUSTOM_DIR="/storage/emulated/0/Notifications/HyperLock/HyperLock_charging_";
 private static volatile Context context;
 private static final Map<Ringtone,Integer> levels=Collections.synchronizedMap(new WeakHashMap<>());
 private static final Map<Ringtone,LoudnessEnhancer> effects=Collections.synchronizedMap(new WeakHashMap<>());
 private ChargingSoundHooks() {}
 static void install(XposedModule module, ClassLoader loader) {
  try {
   Class<?> owner=Refl.cls("ph.v",loader);
   Method init=Refl.method(owner,"T0",Context.class);
   if (init != null) module.hook(init).setId("charging-sound-context").intercept(new XposedInterface.Hooker() {
    @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
     Object arg=chain.getArg(0);
     if (arg instanceof Context) { Context c=(Context)arg; context=c.getApplicationContext()!=null?c.getApplicationContext():c; }
     return chain.proceed();
    }
   });
   Method ringtone=RingtoneManager.class.getMethod("getRingtone",Context.class,Uri.class);
   module.hook(ringtone).setId("charging-sound-volume").intercept(new XposedInterface.Hooker() {
    @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
     Object result=chain.proceed();
     try {
      Object arg=chain.getArg(1);
      if (!(result instanceof Ringtone) || !(arg instanceof Uri)) return result;
      Uri uri=(Uri)arg;
      String path=uri.getPath();
      Context app=context;
      if (app==null || !"file".equals(uri.getScheme()) || path==null || !path.startsWith(CUSTOM_DIR)) return result;
      Bundle b=app.getContentResolver().call(Uri.parse("content://io.github.lxrylex.hyperlock.controlcenterimages"),"charging_sound_volume",null,null);
      int volume=b==null?100:Math.max(0,Math.min(300,b.getInt("volume",100)));
      ((Ringtone)result).setVolume(Math.min(100,volume)/100f);
      levels.put((Ringtone)result,volume);
      Log.i(TAG,"Custom charging sound volume: "+volume+"%");
     } catch(Throwable t) {Log.w(TAG,"Charging volume skipped safely",t);}
     return result;
    }
   });
   module.hook(Ringtone.class.getMethod("play")).setId("charging-sound-boost-play").intercept(new XposedInterface.Hooker() {
    @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
     Ringtone ringtone=(Ringtone)chain.getThisObject();
     Integer volume=levels.get(ringtone);
     if (volume!=null && volume>100) try {
      Field field=Refl.field(Ringtone.class,"mLocalPlayer");
      Object player=field==null?null:field.get(ringtone);
      if (player instanceof MediaPlayer) {
       LoudnessEnhancer effect=new LoudnessEnhancer(((MediaPlayer)player).getAudioSessionId());
       effect.setTargetGain((int)Math.round(2000.0*Math.log10(volume/100.0)));
       effect.setEnabled(true);
       LoudnessEnhancer old=effects.put(ringtone,effect); if(old!=null)old.release();
       Log.i(TAG,"Charging sound boost enabled: "+volume+"%");
      } else Log.w(TAG,"Charging sound boost unavailable: no local player");
     } catch(Throwable t) {Log.w(TAG,"Charging sound boost unavailable",t);}
     return chain.proceed();
    }
   });
   module.hook(Ringtone.class.getMethod("stop")).setId("charging-sound-boost-stop").intercept(new XposedInterface.Hooker() {
    @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
     try {return chain.proceed();} finally {
      try {Ringtone ringtone=(Ringtone)chain.getThisObject(); LoudnessEnhancer effect=effects.remove(ringtone); if(effect!=null)effect.release(); levels.remove(ringtone);} catch(Throwable ignored){}
     }
    }
   });
   int count=0;
   for (Method method:owner.getDeclaredMethods()) {
    if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount()!=0 || method.getReturnType()!=File.class) continue;
    module.hook(method).setId("charging-sound-"+method.getName()).intercept(new XposedInterface.Hooker() {
     @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
      Object result=chain.proceed();
      try {
       if (!(result instanceof File) || !ORIGINAL.equals(((File)result).getPath())) return result;
       Context app=context;
       if (app==null) return result;
       String path=Settings.Global.getString(app.getContentResolver(),"charging_started_sound");
       if (path==null || !path.startsWith(CUSTOM_DIR)) return result;
       File custom=new File(path);
       if (!custom.isFile() || !custom.canRead()) {Log.w(TAG,"Custom charging sound is not readable; keeping Xiaomi sound"); return result;}
       Log.i(TAG,"Using custom wired charging sound in Security");
       return custom;
      } catch (Throwable t) {Log.w(TAG,"Charging sound replacement skipped",t);return result;}
     }
    });
    count++;
   }
   Log.i(TAG,"Charging sound hook installed on "+count+" Security file accessors");
  } catch(Throwable t) {Log.w(TAG,"Charging sound hook unavailable on this Security version",t);}
 }
}
