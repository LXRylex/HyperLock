// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;
import java.util.WeakHashMap;
/** Keeps native view colors separate from our temporary overlay, including null filters. */
final class StatusBarTintOverlay<T> {
    private final WeakHashMap<Object,T> originals=new WeakHashMap<>();
    synchronized boolean contains(Object view){return originals.containsKey(view);}
    synchronized void remember(Object view,T nativeValue){if(!originals.containsKey(view))originals.put(view,nativeValue);}
    synchronized void nativeUpdated(Object view){originals.remove(view);}
    synchronized T release(Object view){return originals.remove(view);}
}
