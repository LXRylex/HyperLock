// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;
import android.graphics.Path;
/** A normalized shape supplies geometry; level, tint and animation belong to its renderer. */
interface BatteryShape {
    Path outline();
    float top();
    float bottom();
}
