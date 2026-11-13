package ru.dslegs;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;

public final class Cooldowns {
    private final DSLegsPlugin plugin;
    private final Map<UUID, Map<String, Long>> map = new HashMap<>();

    public Cooldowns(DSLegsPlugin plugin) { this.plugin = plugin; }

    public long remaining(Player p, String key) {
        Map<String, Long> m = map.get(p.getUniqueId());
        if (m == null) return 0;
        Long end = m.get(key);
        if (end == null) return 0;
        return Math.max(0, end - System.currentTimeMillis());
    }

    public void start(Player p, String key, int seconds) {
        map.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()).put(key, System.currentTimeMillis() + seconds * 1000L);
    }

    public long remaining(Player p, LegendaryType t) { return remaining(p, t.key); }

    public void start(Player p, LegendaryType t) { start(p, t.key, plugin.cooldownSec(t)); }
}
