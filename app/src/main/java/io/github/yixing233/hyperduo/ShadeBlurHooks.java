// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;

import android.util.Log;
import android.view.View;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedModule;

/** Adjusts MIUI's shade blur providers after their normal radius update. */
final class ShadeBlurHooks {
    private static final String TAG = "HyperDuo";
    private static final Map<Object, WeakReference<View>> PROVIDERS = new WeakHashMap<>();
    private static final Map<Object, WeakReference<View>> BLENDS = new WeakHashMap<>();
    private static volatile boolean logged;
    private static volatile int lastLoggedRadius = -1;
    private static boolean installed;
    private ShadeBlurHooks() {}

    static synchronized void install(XposedModule module, ClassLoader loader) {
        if (installed) return;
        try {
            Class<?> provider = Refl.cls("com.miui.systemui.shade.blur.ShadeBlendBlurController$BlurProvider", loader);
            if (provider == null) { Log.i(TAG, "Shade blur provider unavailable; using system blur"); return; }
            Method update = provider.getDeclaredMethod("updateMaxRadius");
            Field viewField = provider.getDeclaredField("view");
            Field radiusField = provider.getDeclaredField("maxRadius");
            Field ratioField = provider.getDeclaredField("blurRatio");
            update.setAccessible(true);
            viewField.setAccessible(true);
            radiusField.setAccessible(true);
            ratioField.setAccessible(true);
            Method setRadius = Refl.methodDeep(View.class, "setMiBackgroundBlurRadius", int.class);
            if (setRadius == null) { Log.i(TAG, "MIUI blur method unavailable; using system blur"); return; }
            Method setScale = Refl.methodDeep(View.class, "setMiBackgroundBlurScaleRatio", float.class);
            if (setScale != null) {
                try {
                    Method setBlurRatio = provider.getDeclaredMethod("setBlurRatio", float.class);
                    setBlurRatio.setAccessible(true);
                    module.hook(setBlurRatio).setId("hyperlock-shade-background-scale").intercept(chain -> {
                        Object instance = chain.getThisObject();
                        Object result = chain.proceed();
                        try {
                            TrioSettings settings = TrioConfig.get();
                            Object target = viewField.get(instance);
                            if (settings != null && settings.shadeBlurEnabled && target instanceof View) {
                                setScale.invoke(target, ShadeBlurPolicy.backgroundScale(true, ratioField.getFloat(instance)));
                            }
                        } catch (Throwable t) { Log.w(TAG, "Shade background scale update skipped", t); }
                        return result;
                    });
                } catch (Throwable t) { Log.w(TAG, "Shade background scale hook unavailable", t); }
            }
            module.hook(update).setId("hyperlock-shade-blur-provider").intercept(chain -> {
                Object instance = chain.getThisObject();
                Object result = chain.proceed();
                try {
                    Object view = viewField.get(instance);
                    if (view instanceof View) {
                        View target = (View) view;
                        synchronized (PROVIDERS) { PROVIDERS.put(instance, new WeakReference<>(target)); }
                        TrioSettings settings = TrioConfig.get();
                        if (setScale != null && settings != null) {
                            try { setScale.invoke(target, ShadeBlurPolicy.backgroundScale(settings.shadeBlurEnabled, ratioField.getFloat(instance))); }
                            catch (Throwable t) { Log.w(TAG, "Shade background scale refresh skipped", t); }
                        }
                        if (settings != null && settings.shadeBlurEnabled) {
                            float density = target.getResources().getDisplayMetrics().density;
                            int radius = ShadeBlurPolicy.resolve(true, settings.shadeBlurRadius, density, radiusField.getInt(instance), settings.shadeBlurLite);
                            radius = ShadeBlurPolicy.stableRadius(radius, density, "emerald".equalsIgnoreCase(android.os.Build.DEVICE));
                            radiusField.setInt(instance, radius);
                            int effectiveRadius = Math.max(0, (int) (ratioField.getFloat(instance) * radius));
                            setRadius.invoke(target, effectiveRadius);
                            if (!logged || lastLoggedRadius != settings.shadeBlurRadius) {
                                logged = true;
                                lastLoggedRadius = settings.shadeBlurRadius;
                                Log.i(TAG, "Shade blur applied setting=" + settings.shadeBlurRadius + " maxPixels=" + radius + " ratio=" + ratioField.getFloat(instance));
                            }
                        }
                    }
                } catch (Throwable t) { Log.w(TAG, "Shade blur update skipped", t); }
                return result;
            });
            installTint(module, loader);
            TrioConfig.addListener(() -> {
                ArrayList<Map.Entry<Object, WeakReference<View>>> entries;
                synchronized (PROVIDERS) { entries = new ArrayList<>(PROVIDERS.entrySet()); }
                for (Map.Entry<Object, WeakReference<View>> entry : entries) {
                    View view = entry.getValue().get();
                    WeakReference<Object> ref = new WeakReference<>(entry.getKey());
                    if (view != null) view.post(() -> {
                        Object current = ref.get();
                        if (current != null) try { update.invoke(current); }
                        catch (Throwable t) { Log.w(TAG, "Shade blur refresh skipped", t); }
                    });
                }
            });
            TrioConfig.install(module);
            installed = true;
            Log.i(TAG, "Shade blur hooks installed");
        } catch (Throwable t) { Log.w(TAG, "Shade blur hook unavailable; using system blur", t); }
    }

    private static void installTint(XposedModule module, ClassLoader loader) {
        try {
            Class<?> cls = Refl.cls("com.miui.systemui.shade.blur.ShadeBlendBlurController$BlendBackground", loader);
            if (cls == null) { Log.i(TAG, "Shade tint unavailable; using system tint"); return; }
            Method update = cls.getDeclaredMethod("updateBlendColors");
            Method apply = cls.getDeclaredMethod("applyBlendColors");
            Field viewField = cls.getDeclaredField("view");
            Field colorsField = cls.getDeclaredField("blendColors");
            update.setAccessible(true); apply.setAccessible(true); viewField.setAccessible(true); colorsField.setAccessible(true);
            module.hook(update).setId("hyperlock-shade-tint").intercept(chain -> {
                Object instance = chain.getThisObject();
                Object result = chain.proceed();
                try {
                    Object target = viewField.get(instance);
                    if (target instanceof View) synchronized (BLENDS) { BLENDS.put(instance, new WeakReference<>((View) target)); }
                    TrioSettings settings = TrioConfig.get();
                    Object colors = colorsField.get(instance);
                    if (settings != null && colors instanceof int[] && settings.shadeTintEnabled) {
                        int[] changed = ShadeBlurPolicy.blendColors(true, (int[]) colors, settings.shadeTintColor);
                        if (changed != colors) { colorsField.set(instance, changed); apply.invoke(instance); }
                    }
                } catch (Throwable t) { Log.w(TAG, "Shade tint update skipped", t); }
                return result;
            });
            TrioConfig.addListener(() -> {
                ArrayList<Map.Entry<Object, WeakReference<View>>> entries;
                synchronized (BLENDS) { entries = new ArrayList<>(BLENDS.entrySet()); }
                for (Map.Entry<Object, WeakReference<View>> entry : entries) {
                    View view = entry.getValue().get();
                    WeakReference<Object> ref = new WeakReference<>(entry.getKey());
                    if (view != null) view.post(() -> {
                        Object current = ref.get();
                        if (current != null) try { update.invoke(current); }
                        catch (Throwable t) { Log.w(TAG, "Shade tint refresh skipped", t); }
                    });
                }
            });
            Log.i(TAG, "Shade tint hooks installed");
        } catch (Throwable t) { Log.w(TAG, "Shade tint unavailable; using system tint", t); }
    }
}
