package io.github.yixing233.hyperduo;

public final class ShadeBlurPolicyTest {
    public static void main(String[] args) {
        check(ShadeBlurPolicy.resolve(false, 80, 2f, 234) == 234, "disabled preserves system");
        check(ShadeBlurPolicy.resolve(true, 100, 2.75f, 0) == 275, "matches dp conversion");
        check(ShadeBlurPolicy.resolve(true, 0, 2f, 0) == 0, "zero stays clear");
        check(ShadeBlurPolicy.resolve(true, 5, 2f, 0) == 1, "very low blur is subtle but nonzero");
        check(ShadeBlurPolicy.resolve(true, 25, 2f, 0) == 12, "low values are gentler");
        check(ShadeBlurPolicy.resolve(true, 50, 2f, 0) == 50, "mid-low values are gentler");
        check(ShadeBlurPolicy.resolve(true, 200, 2f, 0) == 400, "maximum unchanged");
        check(ShadeBlurPolicy.resolve(true, -2, 2f, 0) == 0, "minimum");
        check(ShadeBlurPolicy.resolve(true, 300, 2f, 0) == 400, "maximum");
        check(ShadeBlurPolicy.resolve(true, 50, Float.NaN, 31) == 31, "invalid density fallback");
        check(ShadeBlurPolicy.resolve(true, 100, 2f, 31, true) == 100, "lite halves radius");
        check(ShadeBlurPolicy.resolve(true, 100, 2f, 31, false) == 200, "normal unchanged");
        check(ShadeBlurPolicy.resolve(true, 0, 2f, 31, true) == 0, "lite preserves clear");
        check(ShadeBlurPolicy.resolve(true, 5, 2f, 31, true) == 1, "lite preserves subtle nonzero blur");
        check(ShadeBlurPolicy.resolve(false, 100, 2f, 31, true) == 31, "disabled retains native radius");
        check(Math.abs(ShadeBlurPolicy.backgroundScale(true, 1f) - 0.01875f) < 0.00001f, "custom blur reduces full-open background zoom");
        check(Math.abs(ShadeBlurPolicy.backgroundScale(false, 1f) - 0.075f) < 0.00001f, "disabled uses native zoom");
        check(ShadeBlurPolicy.backgroundScale(true, 0f) == 0f, "closed shade has no zoom");
        check(ShadeBlurPolicy.stableRadius(3, 2.75f, true) == 11, "emerald avoids unstable tiny blur");
        check(ShadeBlurPolicy.stableRadius(0, 2.75f, true) == 0, "clear setting stays clear");
        check(ShadeBlurPolicy.stableRadius(20, 2.75f, true) == 20, "stronger blur unchanged");
        check(ShadeBlurPolicy.stableRadius(3, 2.75f, false) == 3, "other devices unchanged");
        int[] original = {0x52808080, 28};
        check(ShadeBlurPolicy.blendColors(false, original, 0x00ff0000) == original, "disabled preserves original");
        int[] changed = ShadeBlurPolicy.blendColors(true, original, 0x00ff0000);
        check(changed != original && changed[0] == 0x00ff0000 && changed[1] == 28 && original[0] == 0x52808080, "replace color, preserve mode and original");
        check(ShadeBlurPolicy.blendColors(true, null, 0) == null, "null fallback");
        System.out.println("PASS: shade blur policy");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
