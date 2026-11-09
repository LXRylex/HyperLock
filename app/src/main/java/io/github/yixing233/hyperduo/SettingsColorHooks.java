// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;

import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Recolors only the MIUI Settings card decorations and main search background. */
final class SettingsColorHooks {
    private static final String TAG = "HyperDuo";
    private static final Object LOCK = new Object();
    private static final WeakHashMap<View, Boolean> CARDS = new WeakHashMap<>();
    private static final class SearchOriginal {
        final ColorStateList tint;
        final int alpha;
        SearchOriginal(View view) { tint = view.getBackgroundTintList(); alpha = view.getBackground().getAlpha(); }
    }
    private static final WeakHashMap<View, SearchOriginal> SEARCH = new WeakHashMap<>();
    private SettingsColorHooks() {}

    static void install(XposedModule module, ClassLoader loader) {
        TrioConfig.addListener(() -> {
            synchronized (LOCK) {
                for (View view : CARDS.keySet()) if (view != null) view.postInvalidate();
                for (View view : SEARCH.keySet()) if (view != null) view.post(() -> applySearch(view));
            }
        });
        installDecoration(module, loader, "miuix.recyclerview.card.CardItemDecoration", "mGroupDrawable");
        installDecoration(module, loader, "miuix.preference.PreferenceFragment$FrameDecoration", "mCardGroupBackground");
        installSearch(module, loader);
    }

    private static void installDecoration(XposedModule module, ClassLoader loader, String name, String fieldName) {
        try {
            Class<?> cls = Refl.cls(name, loader);
            Field background = Refl.field(cls, fieldName);
            Field paintField = Refl.field(cls, "mPaint");
            Method draw = null;
            if (cls != null) for (Method m : cls.getDeclaredMethods())
                if (m.getName().equals("calculateGroupRectAndDraw") && m.getParameterCount() == 4) { draw = m; break; }
            if (draw == null || background == null) { Log.i(TAG, "Settings cards unavailable: " + name); return; }
            draw.setAccessible(true);
            module.hook(draw).setId("settings-cards-" + name).intercept(new XposedInterface.Hooker() {
                @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Object host = chain.getThisObject();
                    Object old = null;
                    Paint paint = null;
                    int oldColor = 0;
                    boolean changed = false;
                    try {
                        Object arg = chain.getArg(1);
                        if (arg instanceof View) {
                            View view = (View) arg;
                            synchronized (LOCK) { CARDS.put(view, Boolean.TRUE); }
                            TrioConfig.install(module);
                            TrioConfig.installReceiver(view.getContext());
                        }
                        TrioSettings s = TrioConfig.get();
                        old = background.get(host);
                        if (s != null && old instanceof Drawable) {
                            Drawable original = (Drawable) old;
                            int opacity = SettingsColorChannels.opacity(s.settingsCardColor);
                            Drawable replacement = null;
                            if (s.settingsColorsEnabled) {
                                replacement = new ColorDrawable(s.settingsCardColor);
                            } else if (opacity < 100 && original instanceof ColorDrawable) {
                                replacement = new ColorDrawable(SettingsColorChannels.applyOpacity(((ColorDrawable) original).getColor(), opacity));
                            } else if (opacity < 100 && original.getConstantState() != null) {
                                replacement = original.getConstantState().newDrawable().mutate();
                                replacement.setAlpha(Math.round(original.getAlpha() * opacity / 100f));
                            }
                            if (replacement != null) {
                                Object p = Refl.get(paintField, host);
                                if (p instanceof Paint) { paint = (Paint) p; oldColor = paint.getColor(); }
                                background.set(host, replacement);
                                changed = true;
                            }
                        }
                    } catch (Throwable t) { Log.w(TAG, "Settings card tint skipped", t); }
                    try { return chain.proceed(); }
                    finally {
                        if (changed) try {
                            background.set(host, old);
                            if (paint != null) paint.setColor(oldColor);
                        } catch (Throwable t) { Log.w(TAG, "Settings card restoration skipped", t); }
                    }
                }
            });
            Log.i(TAG, "Settings card hook installed: " + name);
        } catch (Throwable t) { Log.w(TAG, "Settings card hook unavailable: " + name, t); }
    }

    private static void installSearch(XposedModule module, ClassLoader loader) {
        try {
            Class<?> cls = Refl.cls("com.android.settings.SettingsFragment", loader);
            Method method = Refl.method(cls, "onViewCreated", View.class, Bundle.class);
            if (method == null) { Log.i(TAG, "Settings search hook unavailable"); return; }
            module.hook(method).setId("settings-search-background").intercept(new XposedInterface.Hooker() {
                @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Object result = chain.proceed();
                    try {
                        View root = (View) chain.getArg(0);
                        int id = root.getResources().getIdentifier("inputArea", "id", "android");
                        View search = id == 0 ? null : root.findViewById(id);
                        if (search != null && search.getBackground() != null) {
                            synchronized (LOCK) { if (!SEARCH.containsKey(search)) SEARCH.put(search, new SearchOriginal(search)); }
                            TrioConfig.install(module);
                            TrioConfig.installReceiver(root.getContext());
                            applySearch(search);
                        }
                    } catch (Throwable t) { Log.w(TAG, "Settings search tint skipped", t); }
                    return result;
                }
            });
            Log.i(TAG, "Settings search hook installed");
        } catch (Throwable t) { Log.w(TAG, "Settings search hook unavailable", t); }
    }

    private static void applySearch(View view) {
        try {
            SearchOriginal original;
            synchronized (LOCK) { if (!SEARCH.containsKey(view)) return; original = SEARCH.get(view); }
            TrioSettings s = TrioConfig.get();
            boolean custom = s != null && s.settingsColorsEnabled;
            int opacity = s == null ? 100 : SettingsColorChannels.opacity(s.settingsCardColor);
            view.setBackgroundTintList(custom ? ColorStateList.valueOf(s.settingsCardColor) : original.tint);
            Drawable background = view.getBackground();
            if (background != null) background.mutate().setAlpha(custom ? original.alpha : Math.round(original.alpha * opacity / 100f));
        } catch (Throwable t) { Log.w(TAG, "Settings search tint skipped", t); }
    }
}
