package ru.dslegs;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class WeaponListener implements Listener {
    private final DSLegsPlugin plugin;
    private final Cooldowns cd;
    private final Hud hud;
    private final Abilities ab;
    private final Stun stun;
    private final Blades blades;

    public WeaponListener(DSLegsPlugin plugin, Cooldowns cd, Hud hud, Abilities ab, Stun stun, Blades blades) {
        this.plugin = plugin; this.cd = cd; this.hud = hud; this.ab = ab; this.stun = stun; this.blades = blades;
    }

    private void begin(Player p, LegendaryType t) {
        cd.start(p, t);
        hud.scheduleReady(p, t);
        announce(p, t, t.ability);
    }

    private void announce(Player p, LegendaryType t, String ability) {
        hud.flash(p, "<gradient:" + t.color + ":#ffffff>✦ <bold>" + t.title + "</bold></gradient> <dark_gray>» <white>" + ability + "!", 1500);
        Fx.cast(p, t);
    }

    /** Способность с отдельным кулдауном (посох, катана сакуры). */
    private void beginKeyed(Player p, LegendaryType t, String key, int def, String label) {
        int sec = plugin.cooldownSec(key, def);
        cd.start(p, key, sec);
        hud.scheduleReady(p, t, key, sec, label);
        announce(p, t, label);
    }

    private void deny(Player p, long rem) {
        hud.flash(p, "<red>✘ <gray>Перезарядка: <white>" + Util.sec(rem) + "с", 1200);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        LegendaryType t = Items.of(p.getInventory().getItemInMainHand());
        if (t == null) return;
        Action a = e.getAction();

        if (t == LegendaryType.MONARCH_STAFF) { staff(e, p, a, t); return; }
        if (t == LegendaryType.SAKURA_KATANA) { sakura(e, p, a, t); return; }

        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        if (stun.isStunned(p)) { e.setCancelled(true); return; }

        // арбалет: нельзя ни заряжать, ни стрелять на перезарядке
        if (t == LegendaryType.SONIC_CROSSBOW) {
            long rem = cd.remaining(p, t);
            if (rem > 0) {
                e.setUseItemInHand(Event.Result.DENY);
                deny(p, rem);
            }
            return;
        }
        if (!t.hasRmb()) return;

        if (a == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && e.getClickedBlock().getType().isInteractable() && !p.isSneaking()) return;

        e.setUseInteractedBlock(Event.Result.DENY);   // топор не будет стругать брёвна
        e.setUseItemInHand(Event.Result.DENY);

        long rem = cd.remaining(p, t);
        if (rem > 0) { deny(p, rem); return; }
        boolean ok = switch (t) {
            case EXCALIBUR -> { ab.excalibur(p); yield true; }
            case LIGHT_MACE -> { ab.mace(p); yield true; }
            case VAMPIRE_BLADE -> { ab.vampire(p); yield true; }
            case DARK_KATANA -> { ab.katana(p); yield true; }
            case ICE_SWORD -> { ab.ice(p); yield true; }
            case SOUL_FORTRESS -> { ab.fortress(p); yield true; }
            case FROSTMORN -> { ab.frostmorn(p); yield true; }
            case SHADOW_REAPER -> ab.shadowStep(p);
            case SANGUINE_ART -> ab.sanguine(p);
            case WARRIOR_GREATSWORD -> { blades.earthshatter(p); yield true; }
            default -> false;
        };
        if (ok) begin(p, t);
    }

    /** Посох монарха: ЛКМ подброс, ПКМ зомби - у каждого свой кулдаун. */
    private void staff(PlayerInteractEvent e, Player p, Action a, LegendaryType t) {
        boolean left = a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK;
        boolean right = a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK;
        if (!left && !right) return;
        if (stun.isStunned(p)) { e.setCancelled(true); return; }
        if (a == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && e.getClickedBlock().getType().isInteractable() && !p.isSneaking()) return;

        String key, label;
        int def;
        if (left) { key = "monarch_launch"; def = 20; label = "Подброс"; }
        else { key = "monarch_zombies"; def = 45; label = "Зов теней"; }

        e.setCancelled(true);
        long rem = cd.remaining(p, key);
        if (rem > 0) { deny(p, rem); return; }
        beginKeyed(p, t, key, def, label);
        if (left) ab.launch(p); else ab.zombies(p);
    }

    /** Катана сакуры: ПКМ - рывок, Shift+ЛКМ - цветение; у каждого свой кулдаун. */
    private void sakura(PlayerInteractEvent e, Player p, Action a, LegendaryType t) {
        boolean left = a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK;
        boolean right = a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK;
        if (left) {
            if (!p.isSneaking()) return;
            if (stun.isStunned(p)) { e.setCancelled(true); return; }
            bloom(p, t, true);
            return;
        }
        if (!right) return;
        if (stun.isStunned(p)) { e.setCancelled(true); return; }
        if (a == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && e.getClickedBlock().getType().isInteractable() && !p.isSneaking()) return;
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setUseItemInHand(Event.Result.DENY);
        long rem = cd.remaining(p, "sakura_dash");
        if (rem > 0) { deny(p, rem); return; }
        if (blades.sakuraDash(p)) beginKeyed(p, t, "sakura_dash", 12, "Рывок лепестков");
    }

    private void bloom(Player p, LegendaryType t, boolean notify) {
        long rem = cd.remaining(p, "sakura_bloom");
        if (rem > 0) {
            // удар по существу даёт и урон, и клик: не ругаемся на только что запущенную способность
            long total = plugin.cooldownSec("sakura_bloom", 40) * 1000L;
            if (notify && total - rem > 400) deny(p, rem);
            return;
        }
        beginKeyed(p, t, "sakura_bloom", 40, "Цветение");
        blades.sakuraBloom(p);
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        LegendaryType t = Items.of(e.getBow());
        if (t == null) return;
        if (t == LegendaryType.ARTEMIS_BOW && e.getProjectile() instanceof AbstractArrow arrow) {
            ab.homing(p, arrow);
        } else if (t == LegendaryType.EXPLOSIVE_BOW && e.getProjectile() instanceof AbstractArrow arrow) {
            if (cd.remaining(p, t) <= 0) {   // динамит раз в 15 с, остальные выстрелы обычные
                begin(p, t);
                ab.dynamiteShot(p, arrow);
            }
        } else if (t == LegendaryType.SONIC_CROSSBOW) {
            e.setCancelled(true);   // обычная стрела не вылетает - вместо неё лучи
            long rem = cd.remaining(p, t);
            if (rem > 0) { deny(p, rem); return; }
            begin(p, t);
            ab.sonic(p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent e) {
        if (e.getEntity() instanceof AbstractArrow arrow && ab.isDynamite(arrow)) ab.dynamiteHit(arrow);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        // динамит: урон самой стрелы в 3 раза меньше (взрыв считается отдельно)
        if (e.getDamager() instanceof AbstractArrow arrow && ab.isDynamite(arrow)) {
            e.setDamage(e.getDamage() / 3.0);
            return;
        }
        if (!Util.abilityDamage && e.getDamager() instanceof Player hp && e.getEntity() instanceof LivingEntity hv
                && e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            LegendaryType ht = Items.of(hp.getInventory().getItemInMainHand());
            if (ht != null) Fx.hit(hv, ht, e.isCritical());
            // Shift+ЛКМ по существу тоже запускает цветение
            if (ht == LegendaryType.SAKURA_KATANA && hp.isSneaking() && !stun.isStunned(hp))
                plugin.getServer().getScheduler().runTask(plugin, () -> {   // после расчёта самого удара
                    if (hp.isOnline() && !hp.isDead()) bloom(hp, ht, false);
                });
        }
        // Shadow Reaper: крит -> слепота I на 1.5 с
        if (e.getDamager() instanceof Player p && e.getEntity() instanceof LivingEntity victim
                && e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && e.isCritical()
                && Items.of(p.getInventory().getItemInMainHand()) == LegendaryType.SHADOW_REAPER) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, true, true));
            var c = victim.getBoundingBox().getCenter().toLocation(victim.getWorld());
            victim.getWorld().spawnParticle(Particle.SQUID_INK, c, 15, 0.3, 0.5, 0.3, 0.05);
            victim.getWorld().spawnParticle(Particle.SMOKE, c, 10, 0.3, 0.5, 0.3, 0.02);
            Util.sound(c, Sound.ENTITY_PHANTOM_BITE, 1f, 1.4f);
        }
    }

    /** Свой динамит с радиусом x3 не убивает стрелка (config: explosive.self-damage). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBoom(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !ab.boomImmune.contains(p.getUniqueId())) return;
        if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || e.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) e.setCancelled(true);
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        LegendaryType t = Items.of(p.getInventory().getItem(e.getNewSlot()));
        if (t == null) return;
        hud.flash(p, "<gradient:" + t.color + ":#ffffff:" + t.color + ">✦ <bold>" + t.title + "</bold> ✦</gradient> <dark_gray>- <gray>" + t.ru, 1600);
        Fx.equip(p, t);
    }

    /** Light Mace: первое падение после рывка без урона */
    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof Player p)) return;
        Long until = ab.noFall.get(p.getUniqueId());
        if (until == null) return;
        if (until > System.currentTimeMillis()) e.setCancelled(true);
        ab.noFall.remove(p.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        ab.noFall.remove(e.getPlayer().getUniqueId());
        ab.endSanguine(e.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) { ab.endSanguine(e.getEntity()); }
}
