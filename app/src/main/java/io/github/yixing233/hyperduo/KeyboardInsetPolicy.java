// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;
final class KeyboardInsetPolicy {
 private KeyboardInsetPolicy() {}
 static boolean shouldAdjust(boolean enabled, boolean ime, boolean gestures, boolean bottom, int rotation) {
  return enabled && ime && gestures && bottom && (rotation == 0 || rotation == 2);
 }
 static boolean shouldAdjustSystem(boolean enabled, boolean gestures, boolean bottom, int rotation) {
  return enabled && gestures && bottom && (rotation == 0 || rotation == 2);
 }
 static int compactHeight(int original, float density) {
  if(original <= 0 || density <= 0 || Float.isNaN(density) || Float.isInfinite(density)) return original;
  return Math.min(original, Math.max(1, Math.round(10f * density)));
 }
}

