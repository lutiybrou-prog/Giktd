package ru.dslegs;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Пассивка Клинка вампира: кража макс. здоровья + вампиризм на полных ударах. */
public final class Vampire implements Listener {
    private static final double STEAL = 1.0;     // ½ сердца
    private static final double EXTRA = 1.0;     // ½ сердца доп. урона / лечения
    private static final double MIN_MAX_HP = 4.0; // ниже 2 сердец макс. здоровье не опускаем

    private final DSLegsPlugin plugin;
    private final NamespacedKey gainKey, lossKey;
    private final Set<UUID> fullSwing = new HashSet<>();

    public Vampire(DSLegsPlugin plugin) {
        this.plugin = plugin;
        this.gainKey = new NamespacedKey(plugin, "vamp_gain");
        this.lossKey = new NamespacedKey(plugin, "vamp_loss");
    }

    /** Сколько сердец макс. здоровья сейчас украдено у других (для HUD). */
    public double bonusHearts(Player p) {
        AttributeInstance a = p.getAttribute(Compat.MAX_HEALTH);
        if (a == null) return 0;
        AttributeModifier m = a.getModifier(gainKey);
        return m == null ? 0 : m.getAmount() / 2.0;
    }

    // ---- полный удар (кулдаун атаки >= 90%) ----
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPre(PrePlayerAttackEntityEvent e) {
        Player p = e.getPlayer();
        if (p.getAttackCooldown() >= 0.9f) {
            fullSwing.add(p.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> fullSwing.remove(p.getUniqueId()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity victim)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && e.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;
        if (Items.of(p.getInventory().getItemInMainHand()) != LegendaryType.VAMPIRE_BLADE) return;
        if (!fullSwing.contains(p.getUniqueId())) return;

        // +½ сердца урона -> лечит владельца
        e.setDamage(e.getDamage() + EXTRA);
        AttributeInstance ma = p.getAttribute(Compat.MAX_HEALTH);
        double max = ma == null ? 20 : ma.getValue();
        p.setHealth(Math.min(max, p.getHealth() + EXTRA));

        if (victim instanceof Player vp) steal(p, vp);

        var w = victim.getWorld();
        var c = victim.getBoundingBox().getCenter().toLocation(w);
        w.spawnParticle(Particle.DUST, c, 18, 0.3, 0.5, 0.3, 0, new Particle.DustOptions(Color.fromRGB(170, 0, 10), 1.5f));
        w.spawnParticle(Particle.BLOCK, c, 12, 0.3, 0.5, 0.3, 0, Material.REDSTONE_BLOCK.createBlockData());
        w.spawnParticle(Particle.HEART, p.getLocation().add(0, 2.1, 0), 2, 0.3, 0.1, 0.3, 0);
    }

    private void steal(Player owner, Player victim) {
        AttributeInstance va = victim.getAttribute(Compat.MAX_HEALTH);
        if (va == null) return;
        double min = MIN_MAX_HP - va.getBaseValue();
        double taken = -adjust(victim, lossKey, -STEAL, min, 0);
        if (taken <= 0) return;
        double maxBonus = plugin.getConfig().getDouble("vampire.max-bonus-health", 20.0);
        double gained = adjust(owner, gainKey, taken, 0, maxBonus);
        long ticks = plugin.getConfig().getLong("vampire.steal-duration-seconds", 120) * 20L;
        final double g = gained;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (victim.isOnline()) adjust(victim, lossKey, taken, min, 0);
            if (owner.isOnline()) adjust(owner, gainKey, -g, 0, maxBonus);
        }, ticks);
    }

    private double adjust(LivingEntity le, NamespacedKey key, double delta, double min, double max) {
        AttributeInstance a = le.getAttribute(Compat.MAX_HEALTH);
        if (a == null) return 0;
        AttributeModifier old = a.getModifier(key);
        double cur = old == null ? 0 : old.getAmount();
        double next = Math.max(min, Math.min(max, cur + delta));
        if (Math.abs(next - cur) < 1e-9) return 0;
        if (old != null) a.removeModifier(old);
        if (Math.abs(next) > 1e-9) a.addModifier(new AttributeModifier(key, next, AttributeModifier.Operation.ADD_NUMBER));
        if (le.getHealth() > a.getValue()) le.setHealth(a.getValue());
        return next - cur;
    }

    public void clear(Player p) {
        AttributeInstance a = p.getAttribute(Compat.MAX_HEALTH);
        if (a == null) return;
        if (a.getModifier(gainKey) != null) a.removeModifier(a.getModifier(gainKey));
        if (a.getModifier(lossKey) != null) a.removeModifier(a.getModifier(lossKey));
        if (p.getHealth() > a.getValue()) p.setHealth(a.getValue());
    }

    public void clearAll() { for (Player p : Bukkit.getOnlinePlayers()) clear(p); }

    @EventHandler public void onDeath(PlayerDeathEvent e) { clear(e.getEntity()); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { clear(e.getPlayer()); }
    @EventHandler public void onJoin(PlayerJoinEvent e) { clear(e.getPlayer()); }
}
