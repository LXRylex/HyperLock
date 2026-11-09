// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.libxposed.api.XposedModule;

/** Keeps the Developer Options game frame-rate switch on from scoped System UI. */
final class GameFrameRateKeeper {
    private static final String TAG = "HyperDuo";
    private static final String PROPERTY = "debug.graphics.game_default_frame_rate.disabled";
    private static final AtomicBoolean PENDING = new AtomicBoolean();
    private static final AtomicBoolean POLLING = new AtomicBoolean();
    private static volatile boolean started;
    private static volatile boolean warned;
    private static Handler worker;
    private static Method getBoolean;
    private static Method getService;
    private static Method asInterface;
    private static Method toggle;

    private GameFrameRateKeeper() {}

    static synchronized void install(XposedModule module) {
        if (started) return;
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            getBoolean = properties.getDeclaredMethod("getBoolean", String.class, boolean.class);
            Method addCallback = properties.getDeclaredMethod("addChangeCallback", Runnable.class);
            getBoolean.setAccessible(true);
            addCallback.setAccessible(true);
            Class<?> serviceManager = Class.forName("android.os.ServiceManager");
            getService = serviceManager.getDeclaredMethod("getService", String.class);
            getService.setAccessible(true);
            Class<?> stub = Class.forName("android.app.IGameManagerService$Stub");
            asInterface = stub.getDeclaredMethod("asInterface", IBinder.class);
            asInterface.setAccessible(true);
            Class<?> api = Class.forName("android.app.IGameManagerService");
            toggle = api.getDeclaredMethod("toggleGameDefaultFrameRate", boolean.class);
            toggle.setAccessible(true);
            HandlerThread thread = new HandlerThread("HyperLock-GameFPS");
            thread.start();
            worker = new Handler(thread.getLooper());
            TrioConfig.addListener(GameFrameRateKeeper::scheduleIfNeeded);
            addCallback.invoke(null, (Runnable) GameFrameRateKeeper::scheduleIfNeeded);
            started = true;
            TrioConfig.install(module);
            scheduleIfNeeded();
            Log.i(TAG, "Game frame-rate keeper installed");
        } catch (Throwable t) {
            Log.w(TAG, "Game frame-rate keeper unavailable; system setting unchanged", t);
        }
    }

    private static void scheduleIfNeeded() {
        try {
            TrioSettings settings = TrioConfig.get();
            boolean enabled = settings != null && settings.keepGameFrameRateUnlocked;
            if (!enabled || worker == null) return;
            // Some HyperOS builds do not broadcast system-property callbacks
            // across processes. Recheck only while the feature is enabled.
            if (POLLING.compareAndSet(false, true)) {
                worker.postDelayed(() -> {
                    POLLING.set(false);
                    scheduleIfNeeded();
                }, 2000L);
            }
            boolean developerOptionOn = (Boolean) getBoolean.invoke(null, PROPERTY, false);
            if (!GameFrameRatePolicy.needsUpdate(enabled, developerOptionOn)) return;
            if (PENDING.compareAndSet(false, true)) worker.postDelayed(GameFrameRateKeeper::enforce, 800L);
        } catch (Throwable t) { warnOnce(t); }
    }

    private static void enforce() {
        PENDING.set(false);
        try {
            TrioSettings settings = TrioConfig.get();
            boolean enabled = settings != null && settings.keepGameFrameRateUnlocked;
            boolean developerOptionOn = (Boolean) getBoolean.invoke(null, PROPERTY, false);
            if (!GameFrameRatePolicy.needsUpdate(enabled, developerOptionOn)) return;
            Object binder = getService.invoke(null, "game");
            if (!(binder instanceof IBinder)) { warnOnce(new IllegalStateException("Game Manager unavailable")); return; }
            Object service = asInterface.invoke(null, binder);
            if (service == null) { warnOnce(new IllegalStateException("Game Manager unavailable")); return; }
            // Developer Options calls toggleGameDefaultFrameRate(false) when its
            // "Disable default frame rate" switch is checked.
            toggle.invoke(service, false);
            if (!(Boolean) getBoolean.invoke(null, PROPERTY, false)) {
                warnOnce(new IllegalStateException("Game Manager did not update the setting"));
                return;
            }
            warned = false;
            Log.i(TAG, "Restored Developer Options game frame-rate setting");
        } catch (Throwable t) { warnOnce(t); }
    }

    private static void warnOnce(Throwable t) {
        if (!warned) { warned = true; Log.w(TAG, "Could not enforce game frame-rate setting", t); }
    }
}
