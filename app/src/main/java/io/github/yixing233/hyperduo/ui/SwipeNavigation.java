package io.github.yixing233.hyperduo.ui;

/** Resolves a completed page swipe without wrapping at either end. */
public final class SwipeNavigation {
    private SwipeNavigation() {}
    public static int targetIndex(int current, int count, float dx, float width, float density) {
        if (count <= 0 || current < 0 || current >= count || width <= 0 || density <= 0
                || !Float.isFinite(dx) || !Float.isFinite(width) || !Float.isFinite(density)) return current;
        float threshold = Math.max(width * 0.18f, 48f * density);
        if (Math.abs(dx) < threshold) return current;
        return Math.max(0, Math.min(count - 1, current + (dx < 0 ? 1 : -1)));
    }
}
