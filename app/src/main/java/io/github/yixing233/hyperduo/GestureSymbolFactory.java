// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import java.util.HashMap;

/** Cached, monochrome glyph bitmaps; Xiaomi applies its existing fade and tint. */
final class GestureSymbolFactory {
    private static final String[] SYMBOLS = {
        "", "\ud81b\ude6d", "\u2726", "\u2039\ud835\udff9", "\u2671", "\ud81a\udd10", "\uaa04\ufe0e"
    };
    private static final HashMap<Integer, Bitmap> CACHE = new HashMap<>();
    private GestureSymbolFactory() {}
    static synchronized Bitmap symbol(int style, boolean isLeft) {
        style = GestureStylePolicy.shape(style);
        if (style == 0) return null;
        boolean flip = style == 3 && isLeft;
        int key = style * 2 + (flip ? 1 : 0);
        Bitmap cached = CACHE.get(key);
        if (cached != null && !cached.isRecycled()) return cached;
        String text = SYMBOLS[style];
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTextSize(100f);
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i); i += Character.charCount(cp);
            if (cp != 0xfe0e && !paint.hasGlyph(new String(Character.toChars(cp)))) return null;
        }
        Rect bounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);
        if (bounds.width() <= 0 || bounds.height() <= 0) return null;
        int w = style == 3 ? 160 : 128, h = 128;
        float scale = Math.min(w * .86f / bounds.width(), h * .86f / bounds.height());
        Bitmap result = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        if (flip) { canvas.translate(w, 0); canvas.scale(-1, 1); }
        canvas.translate(w/2f, h/2f);
        canvas.scale(scale, scale);
        canvas.drawText(text, -(bounds.left+bounds.right)/2f, -(bounds.top+bounds.bottom)/2f, paint);
        CACHE.put(key,result);
        return result;
    }
}
