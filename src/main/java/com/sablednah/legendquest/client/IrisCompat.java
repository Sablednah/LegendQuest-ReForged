package com.sablednah.legendquest.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Asks Iris, when it is installed, whether it is drawing its shadow pass. By
 * reflection, so Iris stays optional and nothing here loads its classes. The
 * method is IrisApi v0's public API (Standards read it out of Iris 1.11.4 for
 * 26.2); if it is ever missing, the answer is simply "no", and plates cast a
 * shadow again rather than anything breaking.
 */
final class IrisCompat {

    private static volatile boolean resolved;
    private static MethodHandle shadowPass;
    private static Object api;

    private IrisCompat() {
    }

    static boolean shadowPass() {
        if (!resolved) {
            resolve();
        }
        if (shadowPass == null) {
            return false;
        }
        try {
            return (boolean) shadowPass.invoke(api);
        } catch (Throwable t) {
            shadowPass = null;
            return false;
        }
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        try {
            if (net.neoforged.fml.ModList.get().isLoaded("iris")) {
                Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = c.getMethod("getInstance").invoke(null);
                shadowPass = MethodHandles.publicLookup().findVirtual(c, "isRenderingShadowPass", MethodType.methodType(boolean.class));
            }
        } catch (Throwable t) {
            shadowPass = null;
        }
        resolved = true;
    }
}
