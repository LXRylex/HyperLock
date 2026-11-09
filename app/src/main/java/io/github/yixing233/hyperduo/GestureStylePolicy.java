// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;
final class GestureStylePolicy {
    private GestureStylePolicy() {}
    static int opacityAlpha(int percent) { return (Math.max(0, Math.min(100, percent)) * 255 + 50) / 100; }
    static int mode(int value) { return value >= 0 && value <= 3 ? value : 0; }
    static int animation(int value) { return value >= 0 && value <= 4 ? value : 0; }
    static float progress(float value) { return Float.isNaN(value) ? 0f : Math.max(0f, Math.min(1f, value)); }
    static int shape(int value) { return value >= 0 && value <= 6 ? value : 0; }
    static int symbolHeight(int nativeHeight) { return nativeHeight > 0 ? Math.max(1, Math.round(nativeHeight * 1.30f)) : 0; }
    static int symbolWidth(int height, int shape) { return height > 0 ? Math.max(1, Math.round(height * (shape == 3 ? 1.25f : 1f))) : 0; }
    static int tint(int original, int color) { return (original & 0xff000000) | (color & 0x00ffffff); }
}
