package com.yourname.yellowduck.client;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** 服务端同步到客户端的坐骑收藏缓存。 */
public final class MountClientState {
    private static final Set<String> OWNED = new HashSet<>();

    private MountClientState() {}

    public static void replace(Iterable<String> ids) {
        OWNED.clear();
        for (String id : ids) {
            if (id != null && !id.isBlank()) OWNED.add(id);
        }
    }

    public static boolean has(String id) {
        return OWNED.contains(id);
    }

    public static Set<String> snapshot() {
        return Collections.unmodifiableSet(new HashSet<>(OWNED));
    }

    public static void clear() {
        OWNED.clear();
    }
}
