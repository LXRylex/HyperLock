// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;
/** Pure battery rules, shared by settings previews and the runtime renderer. */
public final class HeartBattery {
    private HeartBattery() {}
    /** Process-local circuit breaker. A failed Heart frame releases the native icon. */
    static volatile boolean failed;
    /** Scale display RGB without changing the user's selected color or alpha. */
    public static int dimColor(int color,int brightness) {
        int p=Math.max(20,Math.min(100,brightness));
        int red=(((color>>>16)&255)*p+50)/100;
        int green=(((color>>>8)&255)*p+50)/100;
        int blue=((color&255)*p+50)/100;
        return (color&0xff000000)|(red<<16)|(green<<8)|blue;
    }
    public static boolean shouldShowBolt(TrioSettings s,boolean charging) {
        return s!=null && s.trioStyle==Prefs.STYLE_HEART && s.heartBolt && charging;
    }
    public static boolean shouldAnimate(TrioSettings s,boolean charging,int level) {
        return s.heartAnimation && (charging || s.heartAnimateAlways) && level>0
                && (level<100 || s.heartAnimateAlways);
    }
    public static int level(int level) { return Math.max(0, Math.min(100, level)); }
    public static float fillTop(int level) { return 90f - 84f * level(level) / 100f; }
    public static int fillColor(TrioSettings s, int level, boolean charging) {
        level=level(level);
        int color=level==100 ? s.heartFull : charging ? s.heartCharging
                : level<=Math.max(1, Math.min(50, s.lowThreshold)) ? s.heartLow : s.heartNormal;
        // Transparent input would erase the only battery reading. Colors are opaque by design.
        return color | 0xff000000;
    }
}
