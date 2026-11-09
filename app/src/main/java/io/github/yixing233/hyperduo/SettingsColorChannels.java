// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;
/** RGB editing preserves alpha; opacity editing preserves the RGB channels. */
public final class SettingsColorChannels {
    private SettingsColorChannels() {}
    public static int channel(int color, int shift) { return (color >>> shift) & 255; }
    public static int withChannel(int color, int shift, int value) {
        int safe = Math.max(0, Math.min(255, value));
        return (color & ~(255 << shift)) | (safe << shift);
    }
    public static int applyOpacity(int color, int percent) {
        int safe = Math.max(0, Math.min(100, percent));
        return withChannel(color, 24, Math.round(channel(color, 24) * safe / 100f));
    }
    public static int opacity(int color) { return Math.round(channel(color, 24) * 100f / 255f); }
    public static int withOpacity(int color, int percent) {
        return withChannel(color, 24, Math.round(Math.max(0, Math.min(100, percent)) * 255f / 100f));
    }
}
