package online.toraka.dialogmenu;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PackLoadTracker {
    private final ConcurrentHashMap<UUID, Set<UUID>> loaded = new ConcurrentHashMap<>();

    public void record(UUID player, UUID pack, boolean success) {
        if (success) {
            loaded.computeIfAbsent(player, key -> ConcurrentHashMap.newKeySet()).add(pack);
        } else {
            Set<UUID> packs = loaded.get(player);
            if (packs != null) {
                packs.remove(pack);
            }
        }
    }

    public boolean contains(UUID player, Set<UUID> required) {
        if (required.isEmpty()) {
            return false;
        }
        Set<UUID> packs = loaded.get(player);
        if (packs == null) {
            packs = Collections.emptySet();
        }
        return packs.containsAll(required);
    }

    public void forget(UUID player) {
        loaded.remove(player);
    }

    public void clear() {
        loaded.clear();
    }

    public void invalidate(UUID pack) {
        for (Set<UUID> packs : loaded.values()) {
            packs.remove(pack);
        }
    }
}
