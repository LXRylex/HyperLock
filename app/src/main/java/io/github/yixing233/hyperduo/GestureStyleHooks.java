// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.Log;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Visual-only hooks for Xiaomi's back indicators and bottom caption. */
final class GestureStyleHooks {
    private static final String TAG = "HyperDuo";
    private static final Object LOCK = new Object();
    private static final WeakHashMap<View, Boolean> VIEWS = new WeakHashMap<>();
    private static boolean watching;
    private GestureStyleHooks() {}

    static void installLauncher(XposedModule module, ClassLoader loader) {
        install(module, loader, "com.miui.home.recents.GestureBackArrowView", true);
    }
    static void installSystemUi(XposedModule module, ClassLoader loader) {
        install(module, loader,
          "com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.decoration.MiuiDecorationBottomView", false);
        // Other builds can use the AOSP navigation handle instead of Xiaomi's caption.
        install(module, loader, "com.android.systemui.navigationbar.gestural.NavigationHandle", false);
    }
    /** Draw the pulled-off droplet and carry the original indicator with it. */
    private static void drawPop(Canvas canvas, View view, Rect bounds, Bitmap bitmap, Paint background,
                                Rect arrowBounds, Bitmap arrow, Paint arrowPaint, boolean showArrow,
                                boolean left, float progress) {
        float t = Math.max(0f, Math.min(1f, (progress - 0.20f) / 0.65f));
        if (t <= 0f || bounds.isEmpty() || bitmap.isRecycled()) return;
        t = t * t * (3f - 2f * t);
        float radius = Math.min(bounds.height() * 0.24f, 15f * view.getResources().getDisplayMetrics().density);
        if (radius <= 0f) return;
        int sample = bitmap.getPixel(bitmap.getWidth() / 2, bitmap.getHeight() / 2);
        Paint drop = new Paint(Paint.ANTI_ALIAS_FLAG);
        drop.setColor(sample);
        drop.setAlpha((android.graphics.Color.alpha(sample) * background.getAlpha() + 127) / 255);
        float tip = left ? bounds.right : bounds.left;
        float direction = left ? 1f : -1f;
        float y = bounds.exactCenterY();
        float x = tip + direction * radius * (0.12f + 3.0f * t);
        // Start as a bulge, stretch into a teardrop, then settle into an oval.
        float swell = Math.min(1f, t * 4f);
        float stretch = (float) Math.sin(Math.PI * Math.min(1f, t / 0.86f));
        float rx = radius * swell * (0.75f + 0.4f * stretch);
        float ry = radius * swell * (1f - 0.22f * stretch);
        float split = 0.84f;
        if (t < split) {
            float neck = radius * Math.max(0f, 1f - t / split) * 0.42f;
            float base = radius * (0.45f + 0.4f * swell);
            float pull = Math.abs(x - tip);
            Path liquid = new Path();
            liquid.moveTo(tip, y - base);
            liquid.cubicTo(tip + direction * pull * 0.30f, y - base,
                    x - direction * rx * 1.4f, y - neck,
                    x - direction * rx * 0.8f, y - ry * 0.6f);
            liquid.cubicTo(x - direction * rx * 0.4f, y - ry * 1.1f,
                    x + direction * rx * 0.45f, y - ry * 1.1f, x + direction * rx, y);
            liquid.cubicTo(x + direction * rx * 0.45f, y + ry * 1.1f,
                    x - direction * rx * 0.4f, y + ry * 1.1f, x - direction * rx * 0.8f, y + ry * 0.6f);
            liquid.cubicTo(x - direction * rx * 1.4f, y + neck,
                    tip + direction * pull * 0.30f, y + base, tip, y + base);
            liquid.close();
            canvas.drawPath(liquid, drop);
        } else {
            canvas.drawOval(x - rx, y - ry, x + rx, y + ry, drop);
        }
        if (showArrow && arrow != null && !arrow.isRecycled() && !arrowBounds.isEmpty()) {
            float cx = arrowBounds.exactCenterX() + (x - arrowBounds.exactCenterX()) * t;
            float cy = arrowBounds.exactCenterY() + (y - arrowBounds.exactCenterY()) * t;
            float fit = Math.min(1f, radius * 1.25f / Math.max(arrowBounds.width(), arrowBounds.height()));
            float scale = 1f + (fit - 1f) * t;
            float halfW = arrowBounds.width() * scale / 2f;
            float halfH = arrowBounds.height() * scale / 2f;
            canvas.drawBitmap(arrow, null, new RectF(cx-halfW, cy-halfH, cx+halfW, cy+halfH), arrowPaint);
        }
    }
    private static void install(XposedModule module, ClassLoader loader, String name, boolean back) {
        try {
            Class<?> cls = Refl.cls(name, loader);
            Method draw = Refl.method(cls, "onDraw", Canvas.class);
            Field foreground = Refl.field(cls, back ? "mArrowPaint" : "mPaint");
            Field background = back ? Refl.field(cls, "mBgPaint") : null;
            Field backgroundBitmap = back ? Refl.field(cls, "mBackground") : null;
            Field arrowBitmap = back ? Refl.field(cls, "mArrow") : null;
            Field arrowWidth = back ? Refl.field(cls, "mArrowWidth") : null;
            Field arrowHeight = back ? Refl.field(cls, "mArrowHeight") : null;
            Field position = back ? Refl.field(cls, "mPosition") : null;
            Field progress = back ? Refl.field(cls, "mBackgroundScale") : null;
            Field backgroundRect = back ? Refl.field(cls, "mBackgroundDstRect") : null;
            Field arrowRect = back ? Refl.field(cls, "mArrowDstRect") : null;
            Field iconNeedDraw = back ? Refl.field(cls, "mIconNeedDraw") : null;
            if (draw == null || foreground == null || (back && background == null)) {
                Log.i(TAG, "Gesture visuals unavailable: " + name); return;
            }
            synchronized (LOCK) {
                if (!watching) {
                    watching = true;
                    TrioConfig.addListener(() -> {
                        synchronized (LOCK) { for (View view : VIEWS.keySet()) if (view != null) view.postInvalidate(); }
                    });
                }
            }
            module.hook(draw).setId("gesture-visual-" + name).intercept(new XposedInterface.Hooker() {
                @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Object host = chain.getThisObject();
                    Paint front = null, bg = null;
                    ColorFilter oldFilter = null;
                    int oldColor = 0, oldAlpha = 255;
                    Bitmap oldBitmap = null, oldArrow = null;
                    int oldArrowWidth = 0, oldArrowHeight = 0;
                    boolean arrowChanged = false;
                    boolean bitmapChanged = false;
                    boolean changed = false;
                    Canvas canvas = back && chain.getArgs().get(0) instanceof Canvas ? (Canvas) chain.getArgs().get(0) : null;
                    int canvasSave = -1;
                    boolean drawPop = false;
                    float popProgress = 0f;
                    boolean hideNativeArrow = false;
                    int arrowAlpha = 0;
                    try {
                        if (host instanceof View) {
                            View view = (View) host;
                            synchronized (LOCK) { VIEWS.put(view, Boolean.TRUE); }
                            TrioConfig.install(module);
                            TrioConfig.installReceiver(view.getContext());
                        }
                        TrioSettings settings = TrioConfig.get();
                        if (settings != null && settings.gestureCustom) {
                            int animation = GestureStylePolicy.animation(settings.gestureAnimation);
                            if (back && canvas != null && progress != null && host instanceof View && animation == 4) {
                                try { popProgress = GestureStylePolicy.progress(progress.getFloat(host)); drawPop = true; } catch (Throwable ignored) { }
                            }
                            if (back && canvas != null && progress != null && host instanceof View && animation > 0 && animation < 4) {
                                try {
                                    View v = (View) host;
                                    float p = GestureStylePolicy.progress(progress.getFloat(host));
                                    canvasSave = canvas.save();
                                    float edge = Refl.getInt(position, host, 0) == 0 ? 0f : v.getWidth();
                                    if (animation == 1) {
                                        float scale = 0.65f + 0.35f * p;
                                        canvas.scale(scale, scale, edge, v.getHeight() / 2f);
                                    } else if (animation == 2) {
                                        canvas.saveLayerAlpha(0f, 0f, v.getWidth(), v.getHeight(), Math.round(255f * p));
                                    } else {
                                        float direction = edge == 0f ? -1f : 1f;
                                        canvas.translate(direction * v.getWidth() * 0.12f * (1f - p), 0f);
                                    }
                                } catch (Throwable failure) { Log.w(TAG, "Gesture animation skipped safely", failure); }
                            }
                            Object f = Refl.get(foreground, host);
                            Object b = back ? Refl.get(background, host) : null;
                            if (f instanceof Paint && (!back || b instanceof Paint)) {
                                front = (Paint) f; bg = (Paint) b;
                                oldColor = front.getColor(); oldFilter = front.getColorFilter();
                                if (back) oldAlpha = bg.getAlpha();
                                changed = true;
                                if (back) {
                                    front.setColorFilter(new PorterDuffColorFilter(settings.gestureColor | 0xff000000, PorterDuff.Mode.SRC_IN));
                                    bg.setAlpha(GestureStylePolicy.opacityAlpha(settings.gestureBackOpacity));
                                    Object nativeArrow = Refl.get(arrowBitmap, host);
                                    int height = Refl.getInt(arrowHeight, host, 0);
                                    if (settings.gestureShape != 0 && nativeArrow instanceof Bitmap && arrowWidth != null && height > 0) {
                                        Bitmap glyph = GestureSymbolFactory.symbol(settings.gestureShape, Refl.getInt(position, host, -1) == 0);
                                        if (glyph != null) {
                                            oldArrow = (Bitmap) nativeArrow;
                                            oldArrowWidth = Refl.getInt(arrowWidth, host, 0);
                                            oldArrowHeight = height;
                                            if (oldArrowWidth > 0) {
                                                arrowChanged = true;
                                                arrowBitmap.set(host, glyph);
                                                int largerHeight = GestureStylePolicy.symbolHeight(height);
                                                arrowHeight.setInt(host, largerHeight);
                                                arrowWidth.setInt(host, GestureStylePolicy.symbolWidth(largerHeight, settings.gestureShape));
                                            }
                                        }
                                    }
                                    Object original = Refl.get(backgroundBitmap, host);
                                    if (original instanceof Bitmap && host instanceof View) {
                                        oldBitmap = (Bitmap) original;
                                        Bitmap replacement = GestureBackgroundFactory.resolve(oldBitmap, settings, (View)host);
                                        if (replacement != oldBitmap && backgroundBitmap != null) {
                                            backgroundBitmap.set(host, replacement);
                                            bitmapChanged = true;
                                        }
                                    }
                                } else {
                                    front.setColor(GestureStylePolicy.tint(oldColor, settings.gestureColor));
                                }
                            }
                            // Suppress only the native arrow drawing; its original paint and bitmap are restored below.
                            if (drawPop && popProgress > 0.20f && front != null && arrowRect != null && iconNeedDraw != null
                                    && Refl.get(arrowBitmap, host) instanceof Bitmap && Refl.get(backgroundBitmap, host) instanceof Bitmap) {
                                arrowAlpha = front.getAlpha();
                                front.setAlpha(0);
                                hideNativeArrow = true;
                            }
                        }
                    } catch (Throwable failure) {
                        Log.w(TAG, "Gesture style skipped safely", failure);
                    }
                    try {
                        Object result = chain.proceed();
                        if (hideNativeArrow) front.setAlpha(arrowAlpha);
                        if (hideNativeArrow && bg != null && backgroundRect != null) {
                            try {
                                Object bounds = Refl.get(backgroundRect, host);
                                Object bitmap = Refl.get(backgroundBitmap, host);
                                Object glyph = Refl.get(arrowBitmap, host);
                                Object glyphBounds = Refl.get(arrowRect, host);
                                Object visible = Refl.get(iconNeedDraw, host);
                                if (bounds instanceof Rect && bitmap instanceof Bitmap && glyph instanceof Bitmap && glyphBounds instanceof Rect) {
                                    drawPop(canvas, (View) host, (Rect) bounds, (Bitmap) bitmap, bg,
                                            (Rect) glyphBounds, (Bitmap) glyph, front, Boolean.TRUE.equals(visible),
                                            Refl.getInt(position, host, 0) == 0, popProgress);
                                }
                            } catch (Throwable failure) { Log.w(TAG, "Gesture pop skipped safely", failure); }
                        }
                        return result;
                    }
                    finally {
                        if (hideNativeArrow) try { front.setAlpha(arrowAlpha); } catch (Throwable ignored) { }
                        if (canvasSave >= 0) try { canvas.restoreToCount(canvasSave); } catch (Throwable ignored) { }
                        if (arrowChanged) try { arrowBitmap.set(host, oldArrow); arrowWidth.setInt(host, oldArrowWidth); arrowHeight.setInt(host, oldArrowHeight); } catch (Throwable ignored) { }
                        if (bitmapChanged) try { backgroundBitmap.set(host, oldBitmap); } catch (Throwable ignored) { }
                        if (changed) try {
                            front.setColor(oldColor); front.setColorFilter(oldFilter);
                            if (bg != null) bg.setAlpha(oldAlpha);
                        } catch (Throwable failure) { Log.w(TAG, "Gesture paint restore skipped", failure); }
                    }
                }
            });
            Log.i(TAG, "Gesture visuals installed: " + name);
        } catch (Throwable failure) { Log.w(TAG, "Gesture visual hook skipped: " + name, failure); }
    }
}

