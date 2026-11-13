package ru.dslegs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;

/** Оглушение: нельзя ходить (падать можно), бить, использовать предметы. Мобам отключается ИИ. */
public final class Stun implements Listener {
    private final DSLegsPlugin plugin;
    private final Map<UUID, Long> until = new HashMap<>();
    private final Map<UUID, Particle> style = new HashMap<>();
    private final Set<UUID> aiOff = new HashSet<>();

    public Stun(DSLegsPlugin plugin) { this.plugin = plugin; }

    public boolean isStunned(Entity e) {
        Long u = until.get(e.getUniqueId());
        return u != null && u > System.currentTimeMillis();
    }

    public void apply(LivingEntity e, int ticks, Particle particle) {
        long end = System.currentTimeMillis() + ticks * 50L;
        until.merge(e.getUniqueId(), end, Math::max);
        style.put(e.getUniqueId(), particle == null ? Particle.CRIT : particle);
        if (!(e instanceof Player) && e.hasAI()) {
            e.setAI(false);
            aiOff.add(e.getUniqueId());
        }
        UUID id = e.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> release(id), ticks + 2L);
    }

    private void release(UUID id) {
        Long u = until.get(id);
        if (u != null && u > System.currentTimeMillis()) return;
        until.remove(id);
        style.remove(id);
        if (aiOff.remove(id)) {
            Entity en = Bukkit.getEntity(id);
            if (en instanceof LivingEntity le) le.setAI(true);
        }
    }

    public void releaseAll() {
        for (UUID id : new ArrayList<>(aiOff)) {
            Entity en = Bukkit.getEntity(id);
            if (en instanceof LivingEntity le) le.setAI(true);
        }
        aiOff.clear(); until.clear(); style.clear();
    }

    /** Визуал: звёздочки/снежинки над головой. */
    public void start() {
        new BukkitRunnable() {
            int tick;
            @Override public void run() {
                tick++;
                long now = System.currentTimeMillis();
                for (UUID id : new ArrayList<>(until.keySet())) {
                    Long u = until.get(id);
                    if (u == null || u <= now) continue;
                    Entity en = Bukkit.getEntity(id);
                    if (!(en instanceof LivingEntity le) || !le.isValid()) continue;
                    Particle pt = style.getOrDefault(id, Particle.CRIT);
                    Location h = le.getLocation().add(0, le.getHeight() + 0.35, 0);
                    for (int k = 0; k < 3; k++) {
                        double a = tick * 0.5 + k * 2.094;
                        le.getWorld().spawnParticle(pt, h.getX() + Math.cos(a) * 0.4, h.getY(),
                                h.getZ() + Math.sin(a) * 0.4, 1, 0, 0, 0, 0);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!isStunned(p)) return;
        Location f = e.getFrom(), t = e.getTo();
        if (t == null) return;
        if (f.getX() == t.getX() && f.getZ() == t.getZ() && t.getY() <= f.getY()) return;
        Location n = f.clone();
        n.setY(Math.min(f.getY(), t.getY()));
        n.setYaw(t.getYaw());
        n.setPitch(t.getPitch());
        e.setTo(n);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        if (isStunned(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (isStunned(e.getDamager())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (isStunned(e.getPlayer())) e.setCancelled(true);
    }
}
