package com.yourname.yellowduck.dungeon;

/**
 * 标记由 YellowDuck 自己发起的“副本必要传送”。
 *
 * Mohist/Bukkit 插件传送会触发 PlayerTeleportEvent；副本保护默认全部拦截。
 * YellowDuck 自己的进本、回程、Boss 抓取/定身等传送在执行时进入这个作用域，
 * 这样不会误伤副本机制。
 */
public final class DungeonTeleportGuard {
    private static final ThreadLocal<Integer> INTERNAL_DEPTH = ThreadLocal.withInitial(() -> 0);

    private DungeonTeleportGuard() {
    }

    public static boolean isInternalTeleport() {
        return INTERNAL_DEPTH.get() > 0;
    }

    public static void runInternal(Runnable action) {
        if (action == null) return;
        INTERNAL_DEPTH.set(INTERNAL_DEPTH.get() + 1);
        try {
            action.run();
        } finally {
            int next = INTERNAL_DEPTH.get() - 1;
            if (next <= 0) INTERNAL_DEPTH.remove();
            else INTERNAL_DEPTH.set(next);
        }
    }
}
