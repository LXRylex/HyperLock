// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;

import java.lang.reflect.Method;

public final class NotificationColorPolicy {
    private NotificationColorPolicy() {}
    static boolean drawBackground(boolean enabled, int color) { return enabled && (color >>> 24) != 0; }
    static boolean tintMediaBackground(String resourceName, boolean enabled, int color) {
        return "media_bg".equals(resourceName) && drawBackground(enabled, color);
    }
    public static int keepAlpha(int existing, int color) { return (existing & 0xff000000) | (color & 0x00ffffff); }
    static Method findWidthGetter(Class<?> type) { return Refl.methodDeep(type, "getActualWidth"); }
    static int readWidth(Method getter, Object target, int fallback) {
        if (getter == null) return fallback;
        try {
            Object value = getter.invoke(target);
            return value instanceof Number ? ((Number) value).intValue() : fallback;
        } catch (Throwable ignored) { return fallback; }
    }
}
