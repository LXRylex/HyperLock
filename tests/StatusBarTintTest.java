package io.github.yixing233.hyperduo;
import java.util.*;
public class StatusBarTintTest {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public interface Receiver {
        void onDarkChanged(ArrayList<?> areas,float intensity,int tint);
        void onDarkChangedWithContrast(ArrayList<?> areas,int tint,int contrast);
        void onLightDarkTintChanged(int light,int dark,boolean useTint);
    }
    public static class NativeReceiver implements Receiver {
        ArrayList<?> areas;int tint,light,dark,contrast;boolean useTint;int calls;
        public void onDarkChanged(ArrayList<?> areas,float intensity,int tint){this.areas=areas;this.tint=tint;calls++;}
        public void onDarkChangedWithContrast(ArrayList<?> areas,int tint,int contrast){this.areas=areas;this.tint=tint;this.contrast=contrast;}
        public void onLightDarkTintChanged(int light,int dark,boolean useTint){this.light=light;this.dark=dark;this.useTint=useTint;}
    }
    public static void main(String[] args){
        TrioSettings s=TrioSettings.defaults();s.trioStyle=Prefs.STYLE_HEART;s.enabled=true;s.heartStatusTint=true;
        check(StatusBarTintPolicy.enabled(s,false),"Heart may match the status bar");
        s.trioStyle=Prefs.STYLE_DEFAULT;check(!StatusBarTintPolicy.enabled(s,false),"Default restores native tint");
        s.trioStyle=Prefs.STYLE_HEART;s.enabled=false;check(!StatusBarTintPolicy.enabled(s,false),"master off restores native tint");
        s.enabled=true;s.heartStatusTint=false;check(!StatusBarTintPolicy.enabled(s,false),"theme toggle off restores native tint");
        s.heartStatusTint=true;check(!StatusBarTintPolicy.enabled(s,true),"Heart failure also releases native tint");
        check(s.copy().heartStatusTint && TrioSettings.fromBundle(s.toBundle()).heartStatusTint,"theme preference survives copy/broadcast");
        TrioSettings off=s.copy();off.heartStatusTint=false;
        check(!s.equals(off) && s.applyKeyFrom(off,Prefs.KEY_HEART_STATUS_TINT) && !s.heartStatusTint,"theme preference updates live");
        s.heartNormal=0xff93bbfa;s.heartCharging=0xfffcb8e2;
        check(StatusBarTintPolicy.color(s,48,false)==0xff93bbfa,"normal color matches the chosen Heart fill");
        check(StatusBarTintPolicy.color(s,48,true)==0xfffcb8e2,"charging color follows battery state");
        check(StatusBarTintPolicy.color(s,-1,false)==0xff93bbfa,"unknown battery level keeps normal color");
        check(StatusBarTintPolicy.isStatusBarRoot("com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView"),"Xiaomi status bar is included");
        check(!StatusBarTintPolicy.isStatusBarRoot("com.android.systemui.controlcenter.phone.ControlCenterPanelView"),"control center content is excluded");
        check(StatusBarTintPolicy.protectedElement("privacy_dot") && StatusBarTintPolicy.protectedElement("ongoing_call_chip"),"privacy/call indicators retain semantic colors");
        check(StatusBarTintPolicy.isStatusBarRoot("com.android.systemui.statusbar.phone.MiuiKeyguardStatusBarView"),"HyperOS lock status bar is included");
        check(StatusBarTintPolicy.isStatusBarRoot("com.android.systemui.statusbar.phone.KeyguardStatusBarView"),"base lock status bar is included");
        check(!StatusBarTintPolicy.isStatusBarRoot("com.android.keyguard.clock.KeyguardClockContainer"),"large lock clock is excluded");
        check(StatusBarTintPolicy.isShadeHeaderRoot("com.android.systemui.qs.MiuiNotificationHeaderView"),"notification header follows selected color");
        check(StatusBarTintPolicy.isShadeHeaderRoot("com.android.systemui.controlcenter.shade.ControlCenterHeaderView"),"control center header follows selected color");
        check(StatusBarTintPolicy.isStatusBarRoot("com.android.systemui.controlcenter.phone.widget.ControlCenterStatusBarIcon"),"expanded native icon group is included");
        check(StatusBarTintPolicy.isStatusBarRoot("com.android.systemui.controlcenter.phone.widget.ControlCenterFakeStatusIcons"),"transition native icon group is included");
        check(!StatusBarTintPolicy.isShadeHeaderRoot("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow"),"notification card content stays native");
        check(!StatusBarTintPolicy.isShadeHeaderRoot("com.android.systemui.controlcenter.phone.ControlCenterPanelView"),"tiles and panel content stay native");
        StatusBarTintOverlay<Integer> overlay=new StatusBarTintOverlay<>();Object clock=new Object();
        overlay.remember(clock,0xffffffff);overlay.remember(clock,0xff93bbfa);
        check(overlay.release(clock)==0xffffffff && !overlay.contains(clock),"repeated tinting keeps original native white for restoration");
        overlay.remember(clock,0xffffffff);overlay.nativeUpdated(clock);overlay.remember(clock,0xff000000);
        check(overlay.release(clock)==0xff000000,"native theme updates replace stale baselines");
        overlay.remember(clock,0xffffffff);check(overlay.release(clock)==0xffffffff,"equal native and chosen white still restores");
        StatusBarTintOverlay<Object> filter=new StatusBarTintOverlay<>();filter.remember(clock,null);
        check(filter.contains(clock) && filter.release(clock)==null && !filter.contains(clock),"an originally absent image filter restores to null");
        s.heartBrightness=50;s.heartAnimateAlways=true;
        check(StatusBarTintPolicy.color(s,48,false)==0xff4a5e7d,"matching icons use 50 percent brightness");
        check(HeartBattery.dimColor(0xffffffff,20)==0xff333333 && HeartBattery.dimColor(0xff123456,100)==0xff123456,"brightness scales RGB and preserves full brightness");
        check(HeartBattery.dimColor(0xffffffff,-5)==0xff333333 && HeartBattery.dimColor(0xffffffff,500)==0xffffffff,"brightness bounds protect readability");
        TrioSettings effects=TrioSettings.fromBundle(s.toBundle());
        check(effects.heartBrightness==50 && effects.heartAnimateAlways && effects.copy().equals(effects),"brightness and animation survive copy and broadcast");
        TrioSettings updated=effects.copy();updated.heartBrightness=75;updated.heartAnimateAlways=false;
        check(effects.applyKeyFrom(updated,Prefs.KEY_HEART_BRIGHTNESS) && effects.applyKeyFrom(updated,Prefs.KEY_HEART_ANIMATE_ALWAYS) && effects.heartBrightness==75 && !effects.heartAnimateAlways,"live updates carry both controls");
        s.heartBrightness=100;
        check("wifi".equals(ControlCenterImagePolicy.tileSlot("wifi")) && "bluetooth".equals(ControlCenterImagePolicy.tileSlot("bt")),"tile routing is based on stable specs");
        check("mobile".equals(ControlCenterImagePolicy.tileSlot("cell")) && "airplane".equals(ControlCenterImagePolicy.tileSlot("airplane")),"mobile data and airplane route to separate card images");
        check(ControlCenterImagePolicy.tileSlot("nfc")==null && ControlCenterImagePolicy.tileSlot(null)==null,"unrelated tiles remain unchanged");
        check(ControlCenterImagePolicy.validSlot("music") && ControlCenterImagePolicy.validSlot("mobile") && ControlCenterImagePolicy.validSlot("airplane") && !ControlCenterImagePolicy.validSlot("../wifi"),"provider accepts only exact image names");
        check(ControlCenterImagePolicy.brightness(-1)==0 && ControlCenterImagePolicy.brightness(150)==100,"image brightness clamps to safe bounds");
        TrioSettings cc=TrioSettings.defaults();check(!cc.ccImagesEnabled && cc.ccImageBrightness==100 && cc.ccImageRevision==0,"images are off by default");
        cc.ccImagesEnabled=true;cc.ccImageBrightness=55;cc.ccImageRevision=42;
        TrioSettings ccCopy=TrioSettings.fromBundle(cc.toBundle());
        check(ccCopy.equals(cc) && ccCopy.copy().ccImageRevision==42,"image controls survive bundle and copy");
        TrioSettings ccNext=cc.copy();ccNext.ccImagesEnabled=false;ccNext.ccImageBrightness=25;ccNext.ccImageRevision=43;
        check(cc.applyKeyFrom(ccNext,Prefs.KEY_CC_IMAGES_ENABLED) && cc.applyKeyFrom(ccNext,Prefs.KEY_CC_IMAGE_BRIGHTNESS) && cc.applyKeyFrom(ccNext,Prefs.KEY_CC_IMAGE_REVISION) && cc.equals(ccNext),"live image control updates preserve values");
        java.util.List<String> nativeCards=java.util.Arrays.asList("wifi","cell");
        java.util.List<String> extraCard=ControlCenterImagePolicy.cardSpecs(nativeCards,true);
        check(extraCard.equals(java.util.Arrays.asList("wifi","cell","bt","airplane")) && nativeCards.size()==2,"Bluetooth and airplane cards follow Wi-Fi and mobile without changing native list");
        check(ControlCenterImagePolicy.cardSpecs(extraCard,true).size()==4 && ControlCenterImagePolicy.cardSpecs(nativeCards,false)==nativeCards,"Bluetooth card avoids duplicates and restores native list when off");
        check(ControlCenterImagePolicy.cardSpecs(java.util.Arrays.asList("wifi","cell","airplane","bt"),true).equals(java.util.Arrays.asList("wifi","cell","airplane","bt")),"existing Bluetooth and airplane cards are not duplicated or reordered");
        TrioSettings textColors=TrioSettings.defaults();
        check(!textColors.ccTextColorsEnabled,"card text colors are opt-in");
        textColors.ccTextColorsEnabled=true;textColors.ccTextActiveColor=0xff123456;textColors.ccTextInactiveColor=0xffabcdef;
        check(TrioSettings.fromBundle(textColors.toBundle()).equals(textColors),"card text colors survive transport");
        check(ControlCenterImagePolicy.needsDarkTileBackground(true,true,0xffffffff),"white active icons require a contrasting tile");
        check(!ControlCenterImagePolicy.needsDarkTileBackground(false,true,0xffffffff) && !ControlCenterImagePolicy.needsDarkTileBackground(true,false,0xffffffff) && !ControlCenterImagePolicy.needsDarkTileBackground(true,true,0xff101010),"dark backgrounds only apply when needed");
        TrioSettings iconColors=TrioSettings.defaults();
        check(!iconColors.ccIconColorsEnabled,"Control Center icon colors are opt-in");
        iconColors.ccIconColorsEnabled=true;iconColors.ccIconActiveColor=0xff123456;iconColors.ccIconInactiveColor=0xffabcdef;
        check(TrioSettings.fromBundle(iconColors.toBundle()).equals(iconColors),"Control Center icon colors survive transport");
        TrioSettings bt=TrioSettings.defaults();check(!bt.ccBluetoothPill,"large Bluetooth card is opt-in");bt.ccBluetoothPill=true;
        check(TrioSettings.fromBundle(bt.toBundle()).ccBluetoothPill && bt.copy().equals(bt),"large Bluetooth card setting survives transport");
        NativeReceiver nativeReceiver=new NativeReceiver();boolean[] enabled={false};List<Throwable> errors=new ArrayList<>();
        Receiver proxy=(Receiver)StatusBarTintProxy.wrap(Receiver.class,nativeReceiver,()->enabled[0],()->0xff93bbfa,errors::add);
        ArrayList<String> originalAreas=new ArrayList<>();originalAreas.add("native partial tint area");
        proxy.onDarkChanged(originalAreas,.4f,0xffffffff);
        check(nativeReceiver.tint==0xffffffff && nativeReceiver.areas==originalAreas,"out-of-scope/off callbacks remain native");
        enabled[0]=true;proxy.onDarkChanged(originalAreas,.4f,0xffffffff);
        check(nativeReceiver.tint==0xff93bbfa && nativeReceiver.areas.isEmpty(),"all eligible status icons receive selected tint regardless of partial dark area");
        check(originalAreas.size()==1,"native tint regions are never mutated");
        proxy.onDarkChangedWithContrast(originalAreas,0xffffffff,0xff000000);
        check(nativeReceiver.tint==0xff93bbfa && nativeReceiver.contrast==0xff000000,"contrast outline is preserved");
        proxy.onLightDarkTintChanged(0xffffffff,0xff000000,true);
        check(nativeReceiver.light==0xff93bbfa && nativeReceiver.dark==0xff93bbfa && nativeReceiver.useTint,"both Xiaomi tint paths match chosen color");
        enabled[0]=false;proxy.onDarkChanged(originalAreas,.4f,0xff000000);
        proxy.onLightDarkTintChanged(0xffffffff,0xff000000,false);
        check(nativeReceiver.tint==0xff000000 && nativeReceiver.light==0xffffffff && nativeReceiver.dark==0xff000000,"disable replays original native light/dark colors");
        Map<Object,Object> map=new IdentityHashMap<>();map.put(nativeReceiver,proxy);
        check(map.remove(nativeReceiver)==proxy,"native receiver key/removal is unchanged");
        check(proxy.equals(proxy) && !proxy.equals(nativeReceiver),"proxy identity methods remain stable");
        check(errors.isEmpty(),"normal receiver forwarding has no exceptions");
        NativeReceiver refusing=new NativeReceiver(){public void onDarkChanged(ArrayList<?> a,float i,int c){if(c==0xff93bbfa)throw new IllegalArgumentException("native receiver rejects custom tint");super.onDarkChanged(a,i,c);}};
        Receiver safe=(Receiver)StatusBarTintProxy.wrap(Receiver.class,refusing,()->true,()->0xff93bbfa,errors::add);
        safe.onDarkChanged(originalAreas,0f,0xffffffff);
        check(refusing.tint==0xffffffff && !errors.isEmpty(),"a custom-tint failure falls back to native arguments");
        System.out.println("PASS: "+checks+" native status-bar color/restore checks");
    }
}
