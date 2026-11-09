// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;
/** Color policy independent of Android and of the native callback bridge. */
final class StatusBarTintPolicy {
    private StatusBarTintPolicy() {}
    static boolean enabled(TrioSettings cfg,boolean failed) {
        return cfg!=null && cfg.enabled && cfg.trioStyle==Prefs.STYLE_HEART && cfg.heartStatusTint && !failed;
    }
    static int color(TrioSettings cfg,int level,boolean charging) {
        int color=level<0 ? ((charging?cfg.heartCharging:cfg.heartNormal)|0xff000000)
                : HeartBattery.fillColor(cfg,level,charging);
        return HeartBattery.dimColor(color,cfg.heartBrightness);
    }
    static boolean isStatusBarRoot(String name) {
        return "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView".equals(name)
                || "com.android.systemui.statusbar.phone.PhoneStatusBarView".equals(name)
                || "com.android.systemui.statusbar.phone.MiuiKeyguardStatusBarView".equals(name)
                || "com.android.systemui.statusbar.phone.KeyguardStatusBarView".equals(name)
                || isShadeHeaderRoot(name);
    }
    static boolean isShadeHeaderRoot(String name) {
        return "com.android.systemui.qs.MiuiNotificationHeaderView".equals(name)
            || "com.android.systemui.controlcenter.shade.ControlCenterHeaderView".equals(name)
            || "com.android.systemui.controlcenter.phone.widget.ControlCenterStatusBarIcon".equals(name)
            || "com.android.systemui.controlcenter.phone.widget.ControlCenterFakeStatusIcons".equals(name);
    }
    static boolean protectedElement(String name) {
        if(name==null)return false;
        String n=name.toLowerCase(java.util.Locale.ROOT);
        return n.contains("privacy") || n.contains("ongoing_call") || n.contains("call_chip");
    }
    static Object[] arguments(String name,Object[] args,int color,Object emptyAreas) {
        if(args==null || args.length!=3)return args;
        Object[] next;
        if("onDarkChanged".equals(name) && args[1] instanceof Float && args[2] instanceof Integer) {
            next=args.clone();next[0]=emptyAreas;next[2]=color;return next;
        }
        if("onDarkChangedWithContrast".equals(name) && args[1] instanceof Integer && args[2] instanceof Integer) {
            next=args.clone();next[0]=emptyAreas;next[1]=color;return next;
        }
        if("onLightDarkTintChanged".equals(name) && args[0] instanceof Integer
                && args[1] instanceof Integer && args[2] instanceof Boolean) {
            next=args.clone();next[0]=color;next[1]=color;return next;
        }
        return args;
    }
}
