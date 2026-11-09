package io.github.yixing233.hyperduo;

public final class GameFrameRatePolicyTest {
    public static void main(String[] args) {
        check(!GameFrameRatePolicy.needsUpdate(false, false), "off never enables the option");
        check(!GameFrameRatePolicy.needsUpdate(false, true), "off does not change an enabled option");
        check(GameFrameRatePolicy.needsUpdate(true, false), "on restores a disabled developer option");
        check(!GameFrameRatePolicy.needsUpdate(true, true), "on avoids redundant service calls");
        System.out.println("PASS: game frame-rate enforcement policy");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
