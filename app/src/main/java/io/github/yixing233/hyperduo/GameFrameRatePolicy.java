// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;

final class GameFrameRatePolicy {
    private GameFrameRatePolicy() {}
    static boolean needsUpdate(boolean keepEnabled, boolean developerOptionEnabled) {
        return keepEnabled && !developerOptionEnabled;
    }
}
