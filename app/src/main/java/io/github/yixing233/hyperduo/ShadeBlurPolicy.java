// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;

final class ShadeBlurPolicy {
    private ShadeBlurPolicy() {}
    static int resolve(boolean enabled, int radius, float density, int original) {
        return resolve(enabled, radius, density, original, false);
    }
    static int resolve(boolean enabled, int radius, float density, int original, boolean lite) {
        if (!enabled || !Float.isFinite(density) || density <= 0) return original;
        int safe = Math.max(0, Math.min(200, radius));
        if (safe == 0) return 0;
        float adjusted = safe < 100 ? safe * safe / 100f : safe;
        return Math.max(1, (int) (adjusted * density * (lite ? 0.5f : 1f)));
    }
    // HyperOS on emerald flashes when its backdrop blur is only a few pixels wide.
    // Keep zero transparent and leave other devices and invalid densities untouched.
    static int stableRadius(int radius, float density, boolean emerald) {
        if (!emerald || radius <= 0 || !Float.isFinite(density) || density <= 0f) return radius;
        return Math.max(radius, Math.max(1, (int)(4f * density)));
    }
    static int[] blendColors(boolean enabled, int[] original, int color) {
        if (!enabled || original == null || original.length < 2) return original;
        int[] copy = original.clone();
        copy[0] = color;
        return copy;
    }
    static float backgroundScale(boolean customBlur, float ratio) {
        return ratio * (customBlur ? 0.01875f : 0.075f);
    }
}
