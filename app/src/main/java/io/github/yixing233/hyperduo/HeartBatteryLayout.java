// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;
/** Geometry shared by the native measurement hook and the Heart renderer. */
final class HeartBatteryLayout {
    private HeartBatteryLayout() {}
    static boolean outside(TrioSettings cfg) {
        return cfg!=null && cfg.trioStyle==Prefs.STYLE_HEART && cfg.heartPercentage && cfg.heartPercentageRight;
    }
    static float contentWidth(TrioSettings cfg) { return outside(cfg) ? 210f : 100f; }
    static float boltLeft(TrioSettings cfg) { return contentWidth(cfg)+4f; }
    static float designWidth(TrioSettings cfg,boolean charging) {
        return contentWidth(cfg)+(HeartBattery.shouldShowBolt(cfg,charging) ? 50f : 0f);
    }
    static int measuredWidth(int nativeWidth,int height,TrioSettings cfg) {
        return measuredWidth(nativeWidth,height,cfg,false);
    }
    static int measuredWidth(int nativeWidth,int height,TrioSettings cfg,boolean charging) {
        float width=designWidth(cfg,charging);
        if (width<=100f || height<=0) return nativeWidth;
        return Math.max(nativeWidth,(int)Math.ceil(height*width/100f));
    }
}
