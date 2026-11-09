// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;

import android.content.*;
import android.database.ContentObserver;
import android.media.*;
import android.media.audiofx.LoudnessEnhancer;
import android.net.Uri;
import android.os.*;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.*;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Optional media-only overdrag on Xiaomi's native Control Center slider. */
final class MediaBoostHooks {
 private static final String TAG="HyperDuo";
 private static final Uri CONFIG=Uri.parse("content://io.github.lxrylex.hyperlock.controlcenterimages/volume_boost");
 private static final Map<View,WeakReference<Object>> SLIDERS=Collections.synchronizedMap(new WeakHashMap<>());
 private static final Map<Integer,LoudnessEnhancer> EFFECTS=new HashMap<>();
 private static final Map<View,DragState> DRAGS=new WeakHashMap<>();
 private static final class DragState { float anchor; int startBoost; boolean anchored; }
 private static final Map<Object,BoostLabel> LABELS=new WeakHashMap<>();
 private static final class BoostLabel {
  final android.widget.FrameLayout parent; final TextView text;
  BoostLabel(android.widget.FrameLayout container) {
   parent=container;text=new TextView(parent.getContext());
   text.setTextColor(android.graphics.Color.WHITE);text.setTextSize(14);text.setTypeface(null,android.graphics.Typeface.BOLD);
   text.setGravity(android.view.Gravity.CENTER);text.setClickable(false);text.setFocusable(false);
   text.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
   float density=parent.getResources().getDisplayMetrics().density;
   android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();
   background.setColor(0xd9000000);background.setCornerRadius(14*density);text.setBackground(background);
   android.widget.FrameLayout.LayoutParams params=new android.widget.FrameLayout.LayoutParams(Math.round(52*density),Math.round(28*density),android.view.Gravity.TOP|android.view.Gravity.CENTER_HORIZONTAL);
   params.topMargin=Math.round(8*density);text.setVisibility(View.GONE);text.setElevation(1000*density);
   parent.addView(text,params);
  }
 }
 private static final Handler MAIN=new Handler(Looper.getMainLooper());
 private static Handler worker;
 private static Context context;
 private static AudioManager audio;
 private static volatile int boost=100;
 private static volatile boolean enabled;
 private static boolean initialized;
 private static final Set<ClassLoader> LOADERS=Collections.newSetFromMap(new WeakHashMap<>());
 private MediaBoostHooks() {}
 static synchronized void install(XposedModule module,ClassLoader loader) {
  if(LOADERS.contains(loader))return;
  try {
   Class<?> controller=Refl.cls("miui.systemui.controlcenter.panel.main.volume.VolumeSliderController",loader);
   Class<?> slider=Refl.cls("miui.systemui.controlcenter.widget.VerticalSeekBar",loader);
   if(controller==null || slider==null)return;
   Method touch=Refl.method(slider,"onTouchEvent",MotionEvent.class);
   if(touch==null) {Log.i(TAG,"Media boost slider unsupported");return;}
   module.hook(touch).setId("media-boost-touch").intercept(new XposedInterface.Hooker(){
    public Object intercept(XposedInterface.Chain chain)throws Throwable {
     Object obj=chain.getThisObject();Object arg=chain.getArg(0);
     int action=arg instanceof MotionEvent?((MotionEvent)arg).getActionMasked():-1;
     float screenY=arg instanceof MotionEvent?((MotionEvent)arg).getRawY():0f;
     Object result=chain.proceed();
     try { if(obj instanceof View)handleTouch((View)obj,action,screenY); }
     catch(Throwable t){Log.w(TAG,"Media boost gesture skipped",t);}return result;
    }
   });
   LOADERS.add(loader);Log.i(TAG,"Media boost hooks installed");
  }catch(Throwable t){Log.w(TAG,"Media boost unavailable on this ROM",t);}
 }
 static void registerHolder(Object holder) {
  try {
   Object binding=Refl.callByName(holder,"getBinding");
   Object value=binding==null?null:Refl.get(Refl.field(binding.getClass(),"slider"),binding);
   if(value instanceof View) {
    View view=(View)value;SLIDERS.put(view,new WeakReference<>(holder));
    Object item=Refl.get(Refl.field(holder.getClass(),"itemView"),holder);
    if(item instanceof android.view.ViewGroup && !LABELS.containsKey(holder)) {
     View root=(View)item;int id=root.getResources().getIdentifier("toggle_slider_inner","id","miui.systemui.plugin");
     View inner=id==0?null:root.findViewById(id);
     if(inner instanceof android.widget.FrameLayout) {LABELS.put(holder,new BoostLabel((android.widget.FrameLayout)inner));Log.i(TAG,"Media boost label attached above slider image");}
     else Log.w(TAG,"Media boost label container unavailable");
    }
    initialize(view.getContext());updateText(holder);Log.i(TAG,"Media boost slider registered");
   } else Log.w(TAG,"Media boost holder has no slider");
  } catch(Throwable t){Log.w(TAG,"Media boost binding skipped",t);}
 }
 private static synchronized void initialize(Context c) {
  if(initialized)return;
  context=c.getApplicationContext()!=null?c.getApplicationContext():c;
  audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);if(audio==null)return;
  HandlerThread thread=new HandlerThread("HyperLockMediaBoost");thread.start();worker=new Handler(thread.getLooper());
  try {
   context.getContentResolver().registerContentObserver(CONFIG,false,new ContentObserver(MAIN){@Override public void onChange(boolean self){readEnabled();}});
   audio.registerAudioPlaybackCallback(new AudioManager.AudioPlaybackCallback(){@Override public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs){refreshEffects();}},worker);
   BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
    if(boost<=100)return;
    if(AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(i.getAction()))setBoost(100);
    else scheduleNormalVolumeCheck();
   }};
   IntentFilter filter=new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
   filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
   if(Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED);
   else context.registerReceiver(receiver,filter);
   initialized=true;readEnabled();Log.i(TAG,"Media boost listener ready");
  }catch(Throwable t){Log.w(TAG,"Media boost initialization failed",t);thread.quitSafely();worker=null;}
 }
 private static void readEnabled(){
  try {Bundle b=context.getContentResolver().call(CONFIG,"media_boost_enabled",null,null);enabled=b!=null && b.getBoolean("enabled",false);}
  catch(Throwable t){enabled=false;Log.w(TAG,"Media boost setting unavailable",t);}
  Log.i(TAG,"Media boost setting enabled="+enabled);
  if(!enabled)setBoost(100);
 }
 private static void handleTouch(View view,int action,float screenY) {
  WeakReference<Object> ref=SLIDERS.get(view);Object holder=ref==null?null:ref.get();
  if(holder==null || !enabled || audio==null)return;
  if(action==MotionEvent.ACTION_DOWN){
   DragState state=new DragState();state.startBoost=boost;
   if(audio.getStreamVolume(AudioManager.STREAM_MUSIC)>=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)){
    state.anchor=screenY;state.anchored=true;
   }
   DRAGS.put(view,state);return;
  }
  if(action==MotionEvent.ACTION_CANCEL || action==MotionEvent.ACTION_UP){DRAGS.remove(view);scheduleNormalVolumeCheck();return;}
  DragState state=DRAGS.get(view);
  if(state==null)return;
  if(action==MotionEvent.ACTION_MOVE){
   boolean atMax=audio.getStreamVolume(AudioManager.STREAM_MUSIC)>=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
       || (view instanceof android.widget.SeekBar && ((android.widget.SeekBar)view).getProgress()>=((android.widget.SeekBar)view).getMax());
   if(atMax){
    if(!state.anchored){state.anchor=screenY;state.startBoost=boost;state.anchored=true;}
    float range=64f*view.getResources().getDisplayMetrics().density;
    int desired=state.startBoost+Math.round((state.anchor-screenY)/range*100f);
    // Small finger movements at the maximum must not erase an active boost.
    if(desired>100 || boost<=100)setBoost(desired);
   }
   updateText(holder);
  }
 }
 private static final Runnable CHECK_VOLUME=()->{
  if(boost>100 && DRAGS.isEmpty() && audio!=null && audio.getStreamVolume(AudioManager.STREAM_MUSIC)<audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))setBoost(100);
 };
 private static void scheduleNormalVolumeCheck(){MAIN.removeCallbacks(CHECK_VOLUME);MAIN.postDelayed(CHECK_VOLUME,300);}
 private static void setBoost(int value){
  value=Math.max(100,Math.min(200,value));if(boost==value)return;
  if((boost==100)!=(value==100))Log.i(TAG,"Media boost "+(value>100?"activated":"reset"));boost=value;
  if(worker!=null){worker.removeCallbacks(UPDATE);worker.postDelayed(UPDATE,45);}
  MAIN.post(()->{synchronized(SLIDERS){for(WeakReference<Object> r:new ArrayList<>(SLIDERS.values())){Object o=r.get();if(o!=null)updateText(o);}}});
 }
 private static final Runnable UPDATE=MediaBoostHooks::refreshEffects;
 private static void refreshEffects(){
  if(audio==null)return;
  try {
   Set<Integer> active=new HashSet<>();
   if(enabled && boost>100){
    Method session=AudioPlaybackConfiguration.class.getMethod("getSessionId");session.setAccessible(true);
    for(AudioPlaybackConfiguration p:audio.getActivePlaybackConfigurations()){
     int usage=p.getAudioAttributes().getUsage();
     if(usage!=AudioAttributes.USAGE_MEDIA && usage!=AudioAttributes.USAGE_GAME)continue;
     int id=(Integer)session.invoke(p);if(id>0)active.add(id);
    }
   }
   Iterator<Map.Entry<Integer,LoudnessEnhancer>> it=EFFECTS.entrySet().iterator();
   while(it.hasNext()){Map.Entry<Integer,LoudnessEnhancer> e=it.next();if(!active.contains(e.getKey())){try{e.getValue().release();}catch(Throwable ignored){}it.remove();}}
   int gain=(int)Math.round(2000*Math.log10(boost/100.0));
   for(Integer id:active)try {
    LoudnessEnhancer effect=EFFECTS.get(id);
    if(effect==null){effect=new LoudnessEnhancer(id);if(!effect.hasControl()){effect.release();Log.w(TAG,"Media boost could not control session");continue;}EFFECTS.put(id,effect);Log.i(TAG,"Media boost attached to media session");}
    effect.setTargetGain(gain);effect.setEnabled(true);
   }catch(Throwable e){LoudnessEnhancer effect=EFFECTS.remove(id);if(effect!=null)try{effect.release();}catch(Throwable ignored){}Log.w(TAG,"Media boost unavailable for session",e);}
  }catch(Throwable t){Log.w(TAG,"Media boost playback monitor failed",t);for(LoudnessEnhancer effect:EFFECTS.values())try{effect.release();}catch(Throwable ignored){}EFFECTS.clear();}
 }
 private static void updateText(Object holder){
  try {BoostLabel label=LABELS.get(holder);if(label==null)return;
   if(enabled && boost>100){label.text.setText(boost+"%");label.text.setVisibility(View.VISIBLE);label.text.bringToFront();label.text.invalidate();}
   else label.text.setVisibility(View.GONE);
  }catch(Throwable t){Log.w(TAG,"Media boost label skipped",t);}
 }
}
