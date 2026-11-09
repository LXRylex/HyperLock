// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;
/** Deterministic, allocation-free charging motion in normalized heart coordinates. */
final class HeartChargeMotion {
    private static final double TAU=Math.PI*2d;
    private static final long PERIOD_MS=1600L;
    private HeartChargeMotion() {}
    private static double phase(long time) {return Math.floorMod(time,PERIOD_MS)*TAU/PERIOD_MS;}
    static int alpha(long time) {
        // Smooth 51%-100% breathing, visibly stronger than the former 80%-100% pulse.
        return (int)Math.round(192.5d+62.5d*Math.sin(phase(time)));
    }
    static float surfaceY(float x,int level,long time) {
        float top=HeartBattery.fillTop(level);
        // Reduce the wave near 0/100 so motion never fabricates an empty/full reading.
        float amplitude=Math.min(6.5f,Math.min(90f-top,top-6f)*.8f);
        return top+Math.max(0f,amplitude)*(float)Math.sin(x*TAU/80d-phase(time));
    }
}
