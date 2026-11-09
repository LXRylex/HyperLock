// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;
import android.graphics.Path;
/** Rounded lobes and a slightly softened point remain legible at status bar size. */
final class HeartShape implements BatteryShape {
    private final Path path=new Path();
    HeartShape() {
        path.moveTo(50f,90f);
        path.cubicTo(43f,82f,6f,55f,6f,30f);
        path.cubicTo(6f,8f,35f,1f,50f,22f);
        path.cubicTo(65f,1f,94f,8f,94f,30f);
        path.cubicTo(94f,55f,57f,82f,50f,90f);
        path.close();
    }
    public Path outline() { return path; }
    public float top() { return 6f; }
    public float bottom() { return 90f; }
}
