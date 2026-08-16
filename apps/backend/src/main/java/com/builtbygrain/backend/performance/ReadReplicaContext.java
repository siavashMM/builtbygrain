package com.builtbygrain.backend.performance;

final class ReadReplicaContext {

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private ReadReplicaContext() {}

    static boolean requested() {
        return DEPTH.get() > 0;
    }

    static void enter() {
        DEPTH.set(DEPTH.get() + 1);
    }

    static void exit() {
        int remaining = DEPTH.get() - 1;
        if (remaining <= 0) {
            DEPTH.remove();
        } else {
            DEPTH.set(remaining);
        }
    }
}
