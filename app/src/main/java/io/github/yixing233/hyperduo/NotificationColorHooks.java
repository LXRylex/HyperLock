// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedModule;

/** Optional notification-card colors, isolated from status and panel colors. */
final class NotificationColorHooks {
    private static final String TAG = "HyperDuo";
    private static final Map<View, Boolean> BACKGROUNDS = new WeakHashMap<>();
    private static final Map<View, Boolean> CONTENTS = new WeakHashMap<>();
    private static final Map<View, Boolean> MEDIA_CARDS = new WeakHashMap<>();
    private static final Map<View, MediaState> MEDIA_BACKGROUNDS = new WeakHashMap<>();
    private static final Map<TextView, TextState> TEXTS = new WeakHashMap<>();
    private static boolean installed;
    private static volatile boolean backgroundLogged;
    private static volatile boolean textLogged;
    private static boolean lastBgEnabled, lastTextEnabled;
    private static int lastBgColor, lastTextColor;
    private static boolean lastSnapshotSet;
    private static final class TextState {
        ColorStateList original;
        int applied;
        TextState(ColorStateList original, int applied) { this.original = original; this.applied = applied; }
    }
    private static final class MediaState {
        Drawable original;
        Drawable installed;
        int color;
    }
    private NotificationColorHooks() {}

    static synchronized void install(XposedModule module, ClassLoader loader) {
        if (installed) return;
        installBackground(module, loader);
        installContents(module, loader);
        installMedia(module, loader);
        TrioConfig.addListener(NotificationColorHooks::refresh);
        TrioConfig.install(module);
        installed = true;
    }

    private static void installBackground(XposedModule module, ClassLoader loader) {
        try {
            Class<?> cls = Refl.cls("com.android.systemui.statusbar.notification.row.NotificationBackgroundView", loader);
            if (cls == null) { Log.i(TAG, "Notification background view unavailable"); return; }
            Method draw = cls.getDeclaredMethod("onDraw", Canvas.class);
            Method height = cls.getMethod("getActualHeight");
            Method width = NotificationColorPolicy.findWidthGetter(cls);
            Field corners = Refl.field(cls, "mCornerRadii");
            Field clipTop = Refl.field(cls, "mClipTopAmount");
            Field clipBottom = Refl.field(cls, "mClipBottomAmount");
            draw.setAccessible(true);
            module.hook(draw).setId("hyperlock-notification-background-color").intercept(chain -> {
                Object result = chain.proceed();
                try {
                    View view = (View) chain.getThisObject();
                    synchronized (BACKGROUNDS) { BACKGROUNDS.put(view, Boolean.TRUE); }
                    TrioSettings s = TrioConfig.get();
                    if (s == null || !NotificationColorPolicy.drawBackground(s.notificationBgEnabled, s.notificationBgColor)) return result;
                    Canvas canvas = (Canvas) chain.getArg(0);
                    int h = Math.max(0, (Integer) height.invoke(view));
                    int w = Math.max(0, Math.min(view.getWidth(), NotificationColorPolicy.readWidth(width, view, view.getWidth())));
                    if (w == 0 || h == 0) return result;
                    int top = Math.max(0, Refl.getInt(clipTop, view, 0));
                    int bottom = Math.max(0, Refl.getInt(clipBottom, view, 0));
                    Object radii = Refl.get(corners, view);
                    Path path = new Path();
                    RectF rect = new RectF(0, 0, w, h);
                    if (radii instanceof float[] && ((float[]) radii).length >= 8) path.addRoundRect(rect, (float[]) radii, Path.Direction.CW);
                    else path.addRect(rect, Path.Direction.CW);
                    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    paint.setColor(s.notificationBgColor);
                    int save = canvas.save();
                    try { canvas.clipRect(0, top, w, Math.max(top, h - bottom)); canvas.drawPath(path, paint); }
                    finally { canvas.restoreToCount(save); }
                    if (!backgroundLogged) { backgroundLogged = true; Log.i(TAG, "Notification background color applied"); }
                } catch (Throwable t) { Log.w(TAG, "Notification background color skipped", t); }
                return result;
            });
            Log.i(TAG, "Notification background color hook installed");
        } catch (Throwable t) { Log.w(TAG, "Notification background color unavailable", t); }
    }

    private static void installContents(XposedModule module, ClassLoader loader) {
        try {
            Class<?> cls = Refl.cls("com.android.systemui.statusbar.notification.row.NotificationContentView", loader);
            if (cls == null) { Log.i(TAG, "Notification content view unavailable"); return; }
            Method layout = cls.getDeclaredMethod("onLayout", boolean.class, int.class, int.class, int.class, int.class);
            layout.setAccessible(true);
            module.hook(layout).setId("hyperlock-notification-text-color").intercept(chain -> {
                Object result = chain.proceed();
                try {
                    View view = (View) chain.getThisObject();
                    synchronized (CONTENTS) { CONTENTS.put(view, Boolean.TRUE); }
                    applyTextTree(view, TrioConfig.get());
                } catch (Throwable t) { Log.w(TAG, "Notification text color skipped", t); }
                return result;
            });
            Log.i(TAG, "Notification text color hook installed");
        } catch (Throwable t) { Log.w(TAG, "Notification text color unavailable", t); }
    }

    private static void installMedia(XposedModule module, ClassLoader loader) {
        try {
            Class<?> cls = Refl.cls("com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaHeaderView", loader);
            if (cls == null) { Log.i(TAG, "Notification media player unavailable"); return; }
            Method attach = cls.getDeclaredMethod("onAttachedToWindow");
            attach.setAccessible(true);
            module.hook(attach).setId("hyperlock-notification-media-background").intercept(chain -> {
                Object result = chain.proceed();
                try {
                    View root = (View) chain.getThisObject();
                    synchronized (MEDIA_CARDS) { MEDIA_CARDS.put(root, Boolean.TRUE); }
                    WeakReference<View> ref = new WeakReference<>(root);
                    root.post(() -> { View current = ref.get(); if (current != null) applyMedia(current, TrioConfig.get()); });
                } catch (Throwable t) { Log.w(TAG, "Notification media background skipped", t); }
                return result;
            });
            Method layout = Refl.methodDeep(cls, "onLayout", boolean.class, int.class, int.class, int.class, int.class);
            if (layout != null) {
                module.hook(layout).setId("hyperlock-notification-media-layout").intercept(chain -> {
                    Object result = chain.proceed();
                    Object target = chain.getThisObject();
                    if (cls.isInstance(target)) {
                        try {
                            View root = (View) target;
                            synchronized (MEDIA_CARDS) { MEDIA_CARDS.put(root, Boolean.TRUE); }
                            applyMedia(root, TrioConfig.get());
                        } catch (Throwable t) { Log.w(TAG, "Notification media layout skipped", t); }
                    }
                    return result;
                });
            }
            Log.i(TAG, "Notification media background hook installed");
        } catch (Throwable t) { Log.w(TAG, "Notification media background unavailable", t); }
    }

    private static void applyMedia(View root, TrioSettings settings) {
        try {
            int id = root.getResources().getIdentifier("media_bg", "id", "com.android.systemui");
            View background = id == 0 ? null : root.findViewById(id);
            if (background == null) return;
            String name = background.getResources().getResourceEntryName(background.getId());
            boolean enabled = settings != null && NotificationColorPolicy.tintMediaBackground(name, settings.notificationBgEnabled, settings.notificationBgColor);
            MediaState state;
            synchronized (MEDIA_BACKGROUNDS) { state = MEDIA_BACKGROUNDS.get(background); }
            Drawable current = background.getForeground();
            if (!enabled) {
                if (state != null) {
                    if (current == state.installed) background.setForeground(state.original);
                    synchronized (MEDIA_BACKGROUNDS) { MEDIA_BACKGROUNDS.remove(background); }
                }
                return;
            }
            if (state == null) { state = new MediaState(); state.original = current; }
            else if (current != state.installed) state.original = current;
            else if (state.color == settings.notificationBgColor) return;
            int radiusId = root.getResources().getIdentifier("media_control_bg_radius", "dimen", "com.android.systemui");
            float radius = radiusId == 0 ? 20f * root.getResources().getDisplayMetrics().density : root.getResources().getDimension(radiusId);
            GradientDrawable overlay = new GradientDrawable();
            overlay.setColor(settings.notificationBgColor);
            overlay.setCornerRadius(radius);
            state.installed = state.original == null ? overlay : new LayerDrawable(new Drawable[] { state.original, overlay });
            state.color = settings.notificationBgColor;
            synchronized (MEDIA_BACKGROUNDS) { MEDIA_BACKGROUNDS.put(background, state); }
            background.setForeground(state.installed);
            background.invalidate();
            Log.i(TAG, "Notification media background color applied");
        } catch (Throwable t) { Log.w(TAG, "Notification media background update skipped", t); }
    }

    private static void applyTextTree(View view, TrioSettings settings) {
        if (settings == null || !settings.notificationTextEnabled) {
            synchronized (TEXTS) { if (TEXTS.isEmpty()) return; }
        }
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            TextState previous;
            synchronized (TEXTS) { previous = TEXTS.get(text); }
            if (settings != null && settings.notificationTextEnabled) {
                ColorStateList current = text.getTextColors();
                if (previous == null) previous = new TextState(current, settings.notificationTextColor);
                else if (current != null && current.getDefaultColor() != previous.applied) previous.original = current;
                previous.applied = settings.notificationTextColor;
                synchronized (TEXTS) { TEXTS.put(text, previous); }
                if (current == null || current.isStateful() || current.getDefaultColor() != previous.applied) text.setTextColor(previous.applied);
                if (!textLogged) { textLogged = true; Log.i(TAG, "Notification text color applied"); }
            } else if (previous != null) {
                synchronized (TEXTS) { TEXTS.remove(text); }
                if (previous.original != null) text.setTextColor(previous.original);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child != null) applyTextTree(child, settings);
            }
        }
    }

    private static synchronized void refresh() {
        TrioSettings settings = TrioConfig.get();
        if (settings == null) return;
        if (lastSnapshotSet && lastBgEnabled == settings.notificationBgEnabled && lastTextEnabled == settings.notificationTextEnabled
                && lastBgColor == settings.notificationBgColor && lastTextColor == settings.notificationTextColor) return;
        lastSnapshotSet = true;
        lastBgEnabled = settings.notificationBgEnabled;
        lastTextEnabled = settings.notificationTextEnabled;
        lastBgColor = settings.notificationBgColor;
        lastTextColor = settings.notificationTextColor;
        ArrayList<WeakReference<View>> views = new ArrayList<>();
        synchronized (BACKGROUNDS) { for (View view : BACKGROUNDS.keySet()) if (view != null) views.add(new WeakReference<>(view)); }
        synchronized (CONTENTS) { for (View view : CONTENTS.keySet()) if (view != null) views.add(new WeakReference<>(view)); }
        synchronized (MEDIA_CARDS) { for (View view : MEDIA_CARDS.keySet()) if (view != null) views.add(new WeakReference<>(view)); }
        for (WeakReference<View> ref : views) {
            View view = ref.get();
            if (view != null) view.post(() -> {
                View current = ref.get();
                if (current == null) return;
                try {
                    synchronized (CONTENTS) { if (CONTENTS.containsKey(current)) applyTextTree(current, TrioConfig.get()); }
                    synchronized (MEDIA_CARDS) { if (MEDIA_CARDS.containsKey(current)) applyMedia(current, TrioConfig.get()); }
                    current.invalidate();
                } catch (Throwable t) { Log.w(TAG, "Notification color refresh skipped", t); }
            });
        }
    }
}
