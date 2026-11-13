package ru.dslegs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

/** Зомби посоха монарха: атакуют всех, кроме владельца и друг друга. */
public final class Minions implements Listener {
    private final DSLegsPlugin plugin;
    private final Map<UUID, UUID> ownerOf = new HashMap<>();   // зомби -> владелец

    public Minions(DSLegsPlugin plugin) { this.plugin = plugin; }

    private static void attr(LivingEntity le, Attribute a, double v) {
        AttributeInstance ai = le.getAttribute(a);
        if (ai != null) ai.setBaseValue(v);
    }

    public void summon(Player p) {
        int n = plugin.getConfig().getInt("monarch.zombie-count", 5);
        int life = plugin.getConfig().getInt("monarch.zombie-lifetime-seconds", 25) * 20;
        Location c = p.getLocation();
        World w = c.getWorld();
        Util.sound(c, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.5f, 0.7f);
        Util.sound(c, Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.6f);
        Util.sound(c, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1.2f, 0.6f);

        List<Zombie> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            Location sp = c.clone().add(Math.cos(a) * 2.5, 0, Math.sin(a) * 2.5);
            if (!sp.getBlock().isPassable() || !sp.clone().add(0, 1, 0).getBlock().isPassable()) sp = c.clone();
            Zombie z = w.spawn(sp, Zombie.class, zz -> {
                zz.setBaby(false);
                zz.setShouldBurnInDay(false);
                zz.setCanPickupItems(false);
                zz.setGlowing(true);
                zz.customName(Util.mm("<#B04DFF><bold>Тень монарха"));
                zz.setCustomNameVisible(true);
                attr(zz, Attribute.MAX_HEALTH, 40);
                zz.setHealth(40);
                attr(zz, Attribute.ATTACK_DAMAGE, 8);
                attr(zz, Attribute.MOVEMENT_SPEED, 0.3);
                attr(zz, Attribute.FOLLOW_RANGE, 40);
                attr(zz, Attribute.SPAWN_REINFORCEMENTS, 0);
                EntityEquipment eq = zz.getEquipment();
                eq.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
                eq.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                eq.setHelmetDropChance(0f);
                eq.setChestplateDropChance(0f);
            });
            ownerOf.put(z.getUniqueId(), p.getUniqueId());
            plugin.track(z);
            list.add(z);
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, sp.clone().add(0, 1, 0), 30, 0.3, 0.8, 0.3, 0.05);
            w.spawnParticle(Particle.SOUL, sp.clone().add(0, 0.3, 0), 15, 0.3, 0.2, 0.3, 0.05);
        }
        new BukkitRunnable() {   // круг призыва + столбы душ
            int t;
            @Override public void run() {
                if (t % 2 == 0) Util.sigil(c.clone().add(0, 0.1, 0), 3.2, t * 0.12, l -> w.spawnParticle(Particle.SOUL_FIRE_FLAME, l, 1, 0, 0, 0, 0));
                for (int i = 0; i < n; i++) {
                    double a = 2 * Math.PI * i / n;
                    for (double y = 0; y < Math.min(4.0, t * 0.4); y += 0.5)
                        w.spawnParticle(Particle.SOUL, c.getX() + Math.cos(a) * 2.5, c.getY() + y, c.getZ() + Math.sin(a) * 2.5, 1, 0.1, 0, 0.1, 0.01);
                }
                if (++t >= 20) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);

        new BukkitRunnable() {
            int t;
            @Override public void run() {
                t += 5;
                list.removeIf(z -> !z.isValid());
                if (list.isEmpty() || t >= life) {
                    for (Zombie z : list) vanish(z);
                    cancel();
                    return;
                }
                for (Zombie z : list) {
                    LivingEntity cur = z.getTarget();
                    if (cur == null || !Util.valid(cur, p) || ownerOf.containsKey(cur.getUniqueId())) {
                        z.setTarget(nearest(z, p));
                    }
                    z.getWorld().spawnParticle(Particle.SOUL, z.getLocation().add(0, 1, 0), 1, 0.3, 0.5, 0.3, 0.01);
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private LivingEntity nearest(Zombie z, Player owner) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (Entity e : z.getNearbyEntities(24, 12, 24)) {
            if (!(e instanceof LivingEntity le) || !Util.valid(le, owner) || ownerOf.containsKey(le.getUniqueId())) continue;
            double d = le.getLocation().distanceSquared(z.getLocation());
            if (d < bd) { bd = d; best = le; }
        }
        return best;
    }

    private void vanish(Zombie z) {
        z.getWorld().spawnParticle(Particle.SOUL, z.getLocation().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.05);
        ownerOf.remove(z.getUniqueId());
        plugin.untrack(z);
        z.remove();
    }

    public void removeAll() {
        for (UUID id : new ArrayList<>(ownerOf.keySet())) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        ownerOf.clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        UUID owner = ownerOf.get(e.getEntity().getUniqueId());
        if (owner == null || e.getTarget() == null) return;
        UUID t = e.getTarget().getUniqueId();
        if (t.equals(owner) || ownerOf.containsKey(t)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        UUID owner = ownerOf.get(e.getDamager().getUniqueId());
        if (owner == null) return;
        UUID v = e.getEntity().getUniqueId();
        if (v.equals(owner) || ownerOf.containsKey(v)) e.setCancelled(true);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (ownerOf.remove(e.getEntity().getUniqueId()) != null) {
            e.getDrops().clear();
            e.setDroppedExp(0);
            plugin.untrack(e.getEntity());
        }
    }
}
