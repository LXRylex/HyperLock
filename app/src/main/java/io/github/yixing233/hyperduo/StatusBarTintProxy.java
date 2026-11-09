// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.function.*;
/** Changes callback colors only. Native dispatcher fields and registration keys remain untouched. */
final class StatusBarTintProxy implements InvocationHandler {
    private final Object delegate;
    private final BooleanSupplier enabled;
    private final IntSupplier color;
    private final Consumer<Throwable> errors;
    private final ArrayList<Object> emptyAreas=new ArrayList<>();
    private boolean reported;
    private StatusBarTintProxy(Object delegate,BooleanSupplier enabled,IntSupplier color,Consumer<Throwable> errors) {
        this.delegate=delegate;this.enabled=enabled;this.color=color;this.errors=errors;
    }
    static boolean wrapped(Object receiver) {
        return receiver!=null && Proxy.isProxyClass(receiver.getClass())
                && Proxy.getInvocationHandler(receiver) instanceof StatusBarTintProxy;
    }
    static Object wrap(Class<?> api,Object delegate,BooleanSupplier enabled,IntSupplier color,Consumer<Throwable> errors) {
        if(wrapped(delegate))return delegate;
        if(!api.isInstance(delegate))throw new IllegalArgumentException("Unexpected native dark receiver");
        return Proxy.newProxyInstance(api.getClassLoader(),new Class<?>[]{api},
                new StatusBarTintProxy(delegate,enabled,color,errors));
    }
    private void report(Throwable error) {
        if(reported)return;reported=true;
        try {errors.accept(error);}catch(Throwable ignored){}
    }
    @Override public Object invoke(Object proxy,Method method,Object[] args) throws Throwable {
        String name=method.getName();
        if(method.getDeclaringClass()==Object.class) {
            if("hashCode".equals(name))return System.identityHashCode(proxy);
            if("equals".equals(name))return args!=null && args.length==1 && proxy==args[0];
            if("toString".equals(name))return "HeartStatusTint("+delegate.getClass().getName()+")";
        }
        Object[] forwarded=args;
        try {
            if(enabled.getAsBoolean())forwarded=StatusBarTintPolicy.arguments(name,args,color.getAsInt(),emptyAreas);
        }catch(Throwable failure){report(failure);}
        try {return method.invoke(delegate,forwarded);}
        catch(Throwable failure) {
            Throwable cause=failure instanceof InvocationTargetException ? ((InvocationTargetException)failure).getCause() : failure;
            report(cause);
            if(forwarded!=args) {
                try {return method.invoke(delegate,args);}catch(Throwable ignored){}
            }
            // DarkReceiver callbacks are void; an unsupported callback must not crash SystemUI.
            if(method.getReturnType()==Void.TYPE)return null;
            throw cause;
        }
    }
}
