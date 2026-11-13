package ru.dslegs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Реализация всех способностей. */
public final class Abilities {
    private final DSLegsPlugin plugin;
    private final Stun stun;
    private final Fortress fortress;
    private final Hud hud;
    private final Minions minions;
    private final Random rnd = new Random();

    /** игрок -> до какого времени (мс) первое падение не наносит урона (Light Mace) */
    public final Map<UUID, Long> noFall = new HashMap<>();
    public final Map<UUID, Long> sanguineUntil = new HashMap<>();
    public final Set<UUID> dynamite = new HashSet<>();
    public final Set<UUID> boomImmune = new HashSet<>();
    private final NamespacedKey sanguineKey;
    private final NamespacedKey dynKey;

    private static final Particle.DustOptions BLOOD = new Particle.DustOptions(Color.fromRGB(170, 0, 10), 1.3f);
    private static final Particle.DustOptions BLOOD_BIG = new Particle.DustOptions(Color.fromRGB(110, 0, 5), 1.9f);
    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 215, 64), 1.7f);
    private static final Particle.DustOptions WHITE = new Particle.DustOptions(Color.WHITE, 2.4f);
    private static final Particle.DustOptions ICE_DUST = new Particle.DustOptions(Color.fromRGB(150, 225, 255), 1.6f);
    private static final Particle.DustTransition DARK =
            new Particle.DustTransition(Color.fromRGB(8, 0, 18), Color.fromRGB(150, 0, 255), 1.9f);

    public Abilities(DSLegsPlugin plugin, Stun stun, Fortress fortress, Hud hud, Minions minions) {
        this.plugin = plugin; this.stun = stun; this.fortress = fortress; this.hud = hud; this.minions = minions;
        this.sanguineKey = new NamespacedKey(plugin, "sanguine_loss");
        this.dynKey = new NamespacedKey(plugin, "dynamite");
    }

    // =====================================================================
    // 1. EXCALIBUR - Небесный суд
    // =====================================================================
    public void excalibur(Player p) {
        final World w = p.getWorld();
        final Location c = p.getLocation().clone();
        final List<LivingEntity> list = Util.targets(c.clone().add(0, 1, 0), 10, p);
        final double maxBonus = plugin.getConfig().getDouble("paladin.max-bonus-health", 20.0);
        final long durTicks = plugin.getConfig().getInt("paladin.duration-seconds", 20) * 20L;

        Util.sound(c, Sound.BLOCK_BEACON_ACTIVATE, 2f, 1.6f);
        Util.sound(c, Sound.ITEM_TRIDENT_THUNDER, 1.5f, 1.3f);
        Util.sound(c, Sound.BLOCK_BEACON_POWER_SELECT, 2f, 1.2f);

        // отнимаем по 1 сердцу макс. здоровья (временные модификаторы - не сохраняются)
        final Map<UUID, AttributeModifier> lost = new HashMap<>();
        double gained = 0;
        for (LivingEntity le : list) {
            AttributeInstance ai = le.getAttribute(Attribute.MAX_HEALTH);
            if (ai == null) continue;
            double loss = Math.min(2.0, Math.max(0, ai.getValue() - 2.0));   // минимум 1 сердце остаётся
            if (loss <= 0) continue;
            AttributeModifier m = new AttributeModifier(new NamespacedKey(plugin, "paladin_" + UUID.randomUUID()),
                    -loss, AttributeModifier.Operation.ADD_NUMBER);
            ai.addTransientModifier(m);
            if (le.getHealth() > ai.getValue()) le.setHealth(ai.getValue());
            lost.put(le.getUniqueId(), m);
            gained += loss;
        }
        final double gain = Math.min(gained, maxBonus);
        AttributeModifier ownerMod = null;
        AttributeInstance oa = p.getAttribute(Attribute.MAX_HEALTH);
        if (oa != null && gain > 0) {
            ownerMod = new AttributeModifier(new NamespacedKey(plugin, "paladin_" + UUID.randomUUID()),
                    gain, AttributeModifier.Operation.ADD_NUMBER);
            oa.addTransientModifier(ownerMod);
            p.setHealth(Math.min(oa.getValue(), p.getHealth() + gain));
        }
        hud.flash(p, "<#FFD54F>✦ <white>Свет паладина: <bold>" + lost.size() + "</bold> целей, <red>❤ +"
                + String.format(java.util.Locale.US, "%.1f", gain / 2.0), 2500);

        final AttributeModifier om = ownerMod;
        final UUID ownerId = p.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {   // через 20 секунд всё возвращается
            for (Map.Entry<UUID, AttributeModifier> en : lost.entrySet()) {
                Entity e = Bukkit.getEntity(en.getKey());
                if (e instanceof LivingEntity le) {
                    AttributeInstance ai = le.getAttribute(Attribute.MAX_HEALTH);
                    if (ai != null) ai.removeModifier(en.getValue());
                }
            }
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner != null && om != null) {
                AttributeInstance ai = owner.getAttribute(Attribute.MAX_HEALTH);
                if (ai != null) {
                    ai.removeModifier(om);
                    if (owner.getHealth() > ai.getValue()) owner.setHealth(ai.getValue());
                }
            }
        }, durTicks);

        // жёлтый свет падает на всех в радиусе 10
        new BukkitRunnable() {
            int t;
            @Override public void run() {
                try {
                    if (t < 24) {
                        double r = 10.0 * Math.min(1.0, (t + 1) / 10.0);
                        if (t % 2 == 0) Util.sigil(c.clone().add(0, 0.2, 0), r, t * 0.06, l -> w.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, GOLD));
                        for (double y = 0; y < 16; y += 0.5) w.spawnParticle(Particle.END_ROD, c.getX(), c.getY() + y, c.getZ(), 1, 0.2, 0, 0.2, 0.0);
                        final Location sun = new Location(w, c.getX(), c.getY() + 14, c.getZ());
                        double sr = 3.5 * Math.min(1.0, (t + 1) / 8.0);
                        Util.ring(sun, sr, 36, l -> w.spawnParticle(Particle.END_ROD, l, 1, 0.1, 0.1, 0.1, 0.0));
                        Util.ring(sun, sr * 0.55, 24, l -> w.spawnParticle(Particle.DUST, l, 1, 0.1, 0.1, 0.1, 0, GOLD));
                        w.spawnParticle(Particle.TOTEM_OF_UNDYING, sun, 3, 1.2, 0.3, 1.2, 0.05);
                        Util.ring(c.clone().add(0, 0.15, 0), r, 70, l -> {
                            w.spawnParticle(Particle.DUST, l, 1, 0.05, 0.05, 0.05, 0, GOLD);
                            w.spawnParticle(Particle.END_ROD, l, 1, 0.05, 0.1, 0.05, 0.02);
                        });
                        for (int i = 0; i < 80; i++) {   // светящийся "ливень" по всему кругу
                            double a = rnd.nextDouble() * Math.PI * 2, rr = Math.sqrt(rnd.nextDouble()) * 10.0;
                            double y = c.getY() + 14 - t * 0.6 - rnd.nextDouble() * 4;
                            w.spawnParticle(Particle.DUST, c.getX() + Math.cos(a) * rr, y, c.getZ() + Math.sin(a) * rr, 1, 0.1, 0.8, 0.1, 0, GOLD);
                        }
                        for (LivingEntity le : list) {   // луч света на каждую цель
                            if (!le.isValid()) continue;
                            Location b = le.getLocation();
                            double top = 14 - t * 0.6;
                            for (double y = Math.max(0, top - 3); y < top + 1; y += 0.5) {
                                w.spawnParticle(Particle.END_ROD, b.getX(), b.getY() + y, b.getZ(), 1, 0.15, 0, 0.15, 0.0);
                                w.spawnParticle(Particle.DUST, b.getX(), b.getY() + y, b.getZ(), 2, 0.3, 0.1, 0.3, 0, GOLD);
                            }
                            if (t == 14) {   // вспышка там, где свет упал
                                w.spawnParticle(Particle.TOTEM_OF_UNDYING, b.clone().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.2);
                                Util.sound(b, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.7f);
                            }
                        }
                    }
                    // следующие 20 секунд: нимб у владельца, тёмное золото над ослабленными
                    if (t % 4 == 0 && t < durTicks) {
                        Player owner = Bukkit.getPlayer(ownerId);
                        if (owner != null && owner.isOnline()) {
                            Location h = owner.getLocation().add(0, 2.3, 0);
                            for (int k = 0; k < 6; k++) {
                                double a = t * 0.3 + k * Math.PI / 3;
                                w.spawnParticle(Particle.END_ROD, h.getX() + Math.cos(a) * 0.5, h.getY(), h.getZ() + Math.sin(a) * 0.5, 1, 0, 0, 0, 0);
                            }
                            w.spawnParticle(Particle.DUST, h, 2, 0.3, 0.1, 0.3, 0, GOLD);
                        }
                        for (UUID id : lost.keySet()) {
                            Entity e = Bukkit.getEntity(id);
                            if (e instanceof LivingEntity le && le.isValid())
                                w.spawnParticle(Particle.DUST, le.getLocation().add(0, le.getHeight() + 0.3, 0), 1, 0.2, 0.05, 0.2, 0, BLOOD_BIG);
                        }
                    }
                    if (++t >= durTicks) cancel();
                } catch (Throwable ex) {
                    plugin.getLogger().warning("Excalibur: " + ex);
                    ex.printStackTrace();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    

    // --- модель золотого жителя из блоков (размеры в "пикселях" ванильного жителя, 1 px = 0.3 блока) ---


    

    /** Точка на земле, куда смотрит игрок (блок или существо), либо fallbackAhead блоков вперёд. */
    private Location aimGround(Player p, double range, double fallbackAhead) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult rr = w.rayTrace(eye, dir, range, FluidCollisionMode.NEVER, true, 0.6,
                en -> en != p && en instanceof LivingEntity le && !(en instanceof ArmorStand)
                        && !(le instanceof Player sp && sp.getGameMode() == GameMode.SPECTATOR));
        Location pt;
        if (rr != null) {
            pt = rr.getHitEntity() != null ? rr.getHitEntity().getLocation() : rr.getHitPosition().toLocation(w);
        } else {
            Vector h = dir.clone().setY(0);
            if (h.lengthSquared() < 1e-4) h = new Vector(0, 0, 1);
            h.normalize();
            pt = p.getLocation().add(h.multiply(fallbackAhead));
        }
        RayTraceResult dn = w.rayTraceBlocks(pt.clone().add(0, 2.0, 0), new Vector(0, -1, 0), 80, FluidCollisionMode.NEVER, true);
        if (dn != null) pt = dn.getHitPosition().toLocation(w);
        return pt;
    }

    // =====================================================================
    // 2. LIGHT MACE - подброс на 15 + рывок на 3
    // =====================================================================
    public void mace(Player p) {
        Location l = p.getLocation();
        World w = l.getWorld();
        Vector dir = l.getDirection().setY(0);
        if (dir.lengthSquared() < 1e-4) dir = new Vector(0, 0, 1);
        dir.normalize();
        double h = p.isOnGround() ? 0.42 : 0.28;   // ~3 блока по горизонтали
        p.setVelocity(dir.multiply(h).setY(Util.velocityForHeight(15)));
        p.setFallDistance(0);
        noFall.put(p.getUniqueId(), System.currentTimeMillis() + 20000L);

        Util.sound(l, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.5f, 0.9f);
        Util.sound(l, Sound.ITEM_TRIDENT_RIPTIDE_3, 1f, 1.5f);
        w.spawnParticle(Particle.GUST_EMITTER_LARGE, l, 1);
        Util.ring(l.clone().add(0, 0.1, 0), 1.8, 30, x -> w.spawnParticle(Particle.CLOUD, x, 1, 0.1, 0.02, 0.1, 0.12));

        new BukkitRunnable() {
            int t;
            @Override public void run() {
                if (!p.isOnline() || ++t > 40) { cancel(); return; }
                if (t > 6 && p.isOnGround()) {   // удар о землю: ударная волна
                    Location gl = p.getLocation();
                    Util.sound(gl, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.5f);
                    Util.sound(gl, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.5f, 0.7f);
                    w.spawnParticle(Particle.GUST_EMITTER_LARGE, gl, 1);
                    for (int k = 0; k < 4; k++) {
                        final double rad = 1.5 + k * 1.2;
                        Util.ring(gl.clone().add(0, 0.2 + k * 0.1, 0), rad, 30, x -> w.spawnParticle(Particle.CLOUD, x, 1, 0.1, 0.05, 0.1, 0.1));
                    }
                    cancel();
                    return;
                }
                Location pl = p.getLocation().add(0, 0.8, 0);
                w.spawnParticle(Particle.GUST, pl, 1, 0.2, 0.3, 0.2, 0);
                for (int k = 0; k < 2; k++) {   // вихрь вокруг
                    double ang = t * 0.8 + k * Math.PI;
                    double vx = pl.getX() + Math.cos(ang) * 1.3, vz = pl.getZ() + Math.sin(ang) * 1.3;
                    w.spawnParticle(Particle.CLOUD, vx, pl.getY() - 0.3 + Math.sin(t * 0.3) * 0.5, vz, 2, 0.05, 0.05, 0.05, 0.02);
                    w.spawnParticle(Particle.END_ROD, vx, pl.getY() + Math.cos(t * 0.3) * 0.5, vz, 1, 0, 0, 0, 0);
                }
                if (t % 3 == 0) Util.ring(pl.clone().add(0, -0.8, 0), 1.4 + (t % 9) * 0.15, 20, x -> w.spawnParticle(Particle.CLOUD, x, 1, 0.05, 0.02, 0.05, 0.01));
                w.spawnParticle(Particle.CLOUD, pl, 6, 0.3, 0.4, 0.3, 0.02);
                w.spawnParticle(Particle.END_ROD, pl, 3, 0.25, 0.4, 0.25, 0.03);
                double a = t * 0.9;
                w.spawnParticle(Particle.SNOWFLAKE, pl.getX() + Math.cos(a), pl.getY(), pl.getZ() + Math.sin(a), 1, 0, 0, 0, 0);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // =====================================================================
    // 3. VAMPIRE BLADE - ПКМ: кровавая жатва
    // =====================================================================
    public void vampire(Player p) {
        Location c = p.getLocation().add(0, 1, 0);
        World w = c.getWorld();
        List<LivingEntity> list = Util.targets(c, 3, p);

        Util.sound(c, Sound.ENTITY_WARDEN_HEARTBEAT, 1.6f, 0.7f);
        Util.sound(c, Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.2f, 0.6f);
        Util.sound(c, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1f, 0.7f);
        for (LivingEntity le : list) stun.apply(le, 30, Particle.DAMAGE_INDICATOR);

        new BukkitRunnable() {
            int t;
            @Override public void run() {
                Location o = p.isOnline() ? p.getLocation().add(0, 1.1, 0) : c;
                double r = 3.0 * Math.min(1.0, (t + 1) / 8.0);
                Util.ring(o.clone().add(0, -1.0, 0), r, 36, l -> w.spawnParticle(Particle.DUST, l, 1, 0.05, 0.02, 0.05, 0, BLOOD));
                Util.ring(o.clone().add(0, -0.95, 0), r * 0.55, 20, l -> w.spawnParticle(Particle.DUST, l, 1, 0.05, 0.02, 0.05, 0, BLOOD_BIG));
                if (t % 2 == 0) Util.sigil(o.clone().add(0, -1.0, 0), r, t * 0.08, l2 -> w.spawnParticle(Particle.DUST, l2, 1, 0, 0, 0, 0, BLOOD_BIG));
                for (int i = 0; i < 40; i++) {   // кровавый купол
                    double yy = (i + 0.5) / 40.0, rad = Math.sqrt(1 - yy * yy), th = i * 2.399963 + t * 0.15;
                    w.spawnParticle(Particle.DUST, o.getX() + Math.cos(th) * rad * r, o.getY() - 1.0 + yy * r, o.getZ() + Math.sin(th) * rad * r, 1, 0, 0, 0, 0, BLOOD);
                }
                for (LivingEntity le : list) {
                    if (!le.isValid()) continue;
                    Vector from = le.getBoundingBox().getCenter();
                    Vector to = o.toVector();
                    for (int k = 0; k < 5; k++) {
                        double f = ((t * 0.12) + k * 0.2) % 1.0;
                        Vector pt = from.clone().add(to.clone().subtract(from).multiply(f));
                        pt.setY(pt.getY() + Math.sin(f * Math.PI) * 0.8);
                        w.spawnParticle(Particle.DUST, pt.getX(), pt.getY(), pt.getZ(), 1, 0.04, 0.04, 0.04, 0, BLOOD_BIG);
                    }
                    if (t % 3 == 0) w.spawnParticle(Particle.BLOCK, from.getX(), from.getY(), from.getZ(), 6, 0.25, 0.4, 0.25, 0,
                            Material.REDSTONE_BLOCK.createBlockData());
                }
                if (t == 12) {
                    for (LivingEntity le : list) {
                        if (!le.isValid()) continue;
                        Util.hurt(le, p, 6.0);   // 3 сердца, броня не важна
                        Location lc = le.getBoundingBox().getCenter().toLocation(w);
                        w.spawnParticle(Particle.DAMAGE_INDICATOR, lc, 10, 0.3, 0.5, 0.3, 0.1);
                        w.spawnParticle(Particle.DUST, lc, 30, 0.4, 0.6, 0.4, 0, BLOOD_BIG);
                    }
                    Util.sound(c, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.5f, 0.6f);
                }
                if (++t > 24) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // =====================================================================
    // 4. ARTEMIS BOW - самонаведение
    // =====================================================================
    public void homing(Player shooter, AbstractArrow arrow) {
        arrow.setCritical(true);
        final Vector dd = arrow.getVelocity().clone();
        if (dd.lengthSquared() > 1e-6) {
            dd.normalize();
            final Particle.DustTransition rune = new Particle.DustTransition(Color.fromRGB(150, 0, 255), Color.fromRGB(255, 0, 80), 1.4f);
            for (int k = 0; k < 3; k++)
                Util.ringAround(arrow.getLocation().toVector().add(dd.clone().multiply(0.6 + k * 0.5)), dd, 0.5 + k * 0.25, 20,
                        q -> arrow.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, q.getX(), q.getY(), q.getZ(), 1, 0, 0, 0, 0, rune));
        }
        new BukkitRunnable() {
            int age;
            Player target;
            @Override public void run() {
                if (!arrow.isValid() || arrow.isInBlock() || ++age > 140) { cancel(); return; }
                Location loc = arrow.getLocation();
                World w = loc.getWorld();
                w.spawnParticle(Particle.DUST_COLOR_TRANSITION, loc, 2, 0.05, 0.05, 0.05, 0,
                        new Particle.DustTransition(Color.fromRGB(150, 0, 255), Color.fromRGB(255, 0, 80), 1.2f));
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 1, 0, 0, 0, 0.01);

                if (target == null || !Util.valid(target, shooter) || !Util.clear(loc, target)) {
                    Player old = target;
                    target = (age % 2 == 0) ? find(shooter, arrow) : null;
                    if (target != null && target != old && shooter.isOnline()) {
                        hud.flash(shooter, "<#B65CFF>◎ <white>Цель захвачена: <bold>" + target.getName(), 1200);
                    }
                }
                if (target != null) {
                    Vector vel = arrow.getVelocity();
                    double sp = Math.max(vel.length(), 1.6);
                    Vector to = target.getBoundingBox().getCenter().subtract(loc.toVector());
                    if (to.lengthSquared() > 1e-4 && vel.lengthSquared() > 1e-6) {
                        Vector nd = vel.clone().normalize().multiply(0.65).add(to.normalize().multiply(0.35)).normalize();
                        arrow.setVelocity(nd.multiply(sp));
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private Player find(Player shooter, AbstractArrow arrow) {
        Location loc = arrow.getLocation();
        Vector dir = arrow.getVelocity().clone();
        if (dir.lengthSquared() < 1e-6) return null;
        dir.normalize();
        Player best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity e : loc.getWorld().getNearbyEntities(loc, 40, 40, 40)) {
            if (!(e instanceof Player le) || !Util.valid(le, shooter)) continue;
            Vector to = le.getBoundingBox().getCenter().subtract(loc.toVector());
            double dist = to.length();
            if (dist < 0.5) continue;
            double ang = dir.angle(to.clone().normalize());
            if (ang > Math.toRadians(75)) continue;
            if (!Util.clear(loc, le)) continue;
            double score = dist * (1 + ang * 2);
            if (score < bestScore) { bestScore = score; best = le; }
        }
        return best;
    }

    // =====================================================================
    // 5. SONIC CROSSBOW - три луча
    // =====================================================================
    public void sonic(Player p) {
        Location eye = p.getEyeLocation();
        World w = eye.getWorld();
        Vector base = eye.getDirection().normalize();
        final double LEN = 26;
        double[] angles = {-10, 0, 10};
        Set<UUID> hit = new HashSet<>();
        List<LivingEntity> victims = new ArrayList<>();
        List<Vector> starts = new ArrayList<>(), dirs = new ArrayList<>();
        List<Double> lens = new ArrayList<>();

        Util.sound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.6f, 1.3f);
        Util.sound(eye, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.2f, 1.4f);
        Util.sound(eye, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.5f, 0.8f);
        w.spawnParticle(Particle.GUST_EMITTER_SMALL, eye.clone().add(base.clone().multiply(1.2)), 1);

        for (double a : angles) {
            Vector d = base.clone().rotateAroundY(Math.toRadians(a)).normalize();
            Vector start = eye.toVector().add(d.clone().multiply(0.8)).add(new Vector(0, -0.2, 0));
            RayTraceResult br = w.rayTraceBlocks(eye, d, LEN, FluidCollisionMode.NEVER, true);
            double len = br == null ? LEN : Math.max(2, eye.toVector().distance(br.getHitPosition()));
            starts.add(start); dirs.add(d); lens.add(len);

            BoundingBox box = BoundingBox.of(start, start.clone().add(d.clone().multiply(len))).expand(3);
            for (Entity e : w.getNearbyEntities(box)) {
                if (!(e instanceof LivingEntity le) || !Util.valid(le, p) || hit.contains(le.getUniqueId())) continue;
                Vector rel = le.getBoundingBox().getCenter().subtract(start);
                double proj = rel.dot(d);
                if (proj < 0 || proj > len) continue;
                double dist = rel.clone().subtract(d.clone().multiply(proj)).length();
                if (dist <= 1.7 + le.getWidth() / 2) { hit.add(le.getUniqueId()); victims.add(le); }
            }
        }

        // анимация лучей (толстые, со спиралью)
        new BukkitRunnable() {
            int t;
            @Override public void run() {
                for (int i = 0; i < starts.size(); i++) {
                    Vector start = starts.get(i), d = dirs.get(i);
                    double len = lens.get(i);
                    Vector u = d.clone().crossProduct(new Vector(0, 1, 0));
                    if (u.lengthSquared() < 1e-4) u = new Vector(1, 0, 0);
                    u.normalize();
                    Vector v = d.clone().crossProduct(u).normalize();
                    for (double s = len * t / 6.0; s < len * (t + 1) / 6.0; s += 0.35) {
                        Vector pt = start.clone().add(d.clone().multiply(s));
                        w.spawnParticle(Particle.END_ROD, pt.getX(), pt.getY(), pt.getZ(), 2, 0.12, 0.12, 0.12, 0.0);
                        w.spawnParticle(Particle.DUST, pt.getX(), pt.getY(), pt.getZ(), 2, 0.3, 0.3, 0.3, 0, WHITE);
                        w.spawnParticle(Particle.CLOUD, pt.getX(), pt.getY(), pt.getZ(), 1, 0.2, 0.2, 0.2, 0.01);
                        double ang = s * 1.4;
                        Vector hp = pt.clone().add(u.clone().multiply(Math.cos(ang) * 0.9)).add(v.clone().multiply(Math.sin(ang) * 0.9));
                        w.spawnParticle(Particle.END_ROD, hp.getX(), hp.getY(), hp.getZ(), 1, 0, 0, 0, 0);
                        w.spawnParticle(Particle.ELECTRIC_SPARK, hp.getX(), hp.getY(), hp.getZ(), 1, 0, 0, 0, 0);
                    }
                    // звуковые кольца, летящие по лучу
                    double sr = len * (t + 0.5) / 6.0;
                    Vector rc = start.clone().add(d.clone().multiply(sr));
                    Util.ringAround(rc, d, 1.4 + sr * 0.05, 24, q -> w.spawnParticle(Particle.END_ROD, q.getX(), q.getY(), q.getZ(), 1, 0, 0, 0, 0));
                    Util.ringAround(rc, d, 1.0 + sr * 0.04, 20, q -> w.spawnParticle(Particle.DUST, q.getX(), q.getY(), q.getZ(), 1, 0, 0, 0, 0, WHITE));
                }
                if (++t >= 6) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);

        Location dest = eye.clone().add(base.clone().multiply(2.5));
        for (LivingEntity le : victims) suck(le, p, dest, base);
        if (!victims.isEmpty()) hud.flash(p, "<#E8F7FF>✦ <white>Звуковой удар! Поражено целей: <bold>" + victims.size(), 1800);
    }

    private void suck(LivingEntity le, Player p, Location dest, Vector dir) {
        Util.hurt(le, p, 6.0);   // 3 сердца
        World w = le.getWorld();
        new BukkitRunnable() {
            int t;
            @Override public void run() {
                if (!le.isValid()) { cancel(); return; }
                Vector c = le.getBoundingBox().getCenter();
                if (t < 7) {
                    Vector to = dest.toVector().subtract(c);
                    double d = to.length();
                    if (d > 0.5) le.setVelocity(to.normalize().multiply(Math.min(0.9, d * 0.35)));
                    for (int k = 0; k < 3; k++) {
                        double a = t * 0.9 + k * 2.094, rr = 1.1 - t * 0.1;
                        w.spawnParticle(Particle.END_ROD, c.getX() + Math.cos(a) * rr, c.getY() + (k - 1) * 0.4,
                                c.getZ() + Math.sin(a) * rr, 1, 0, 0, 0, 0);
                    }
                    t++;
                } else {
                    le.setVelocity(new Vector(dir.getX() * 0.15, Util.velocityForHeight(5), dir.getZ() * 0.15));
                    Location l = le.getLocation();
                    Util.sound(l, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.3f, 1.2f);
                    w.spawnParticle(Particle.GUST, l.clone().add(0, 0.8, 0), 2, 0.2, 0.2, 0.2, 0);
                    w.spawnParticle(Particle.CLOUD, l.clone().add(0, 0.4, 0), 20, 0.3, 0.2, 0.3, 0.15);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // =====================================================================
    // 6. DARK KATANA - три разреза
    // =====================================================================
    public void katana(Player p) {
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && !p.isDead()) slash(p, idx);
            }, i * 8L);
        }
    }

    private void slash(Player p, int idx) {
        Location o = p.getLocation();
        World w = o.getWorld();
        Vector f0 = o.getDirection().setY(0);
        if (f0.lengthSquared() < 1e-4) f0 = new Vector(0, 0, 1);
        final Vector f = f0.normalize();
        final Vector r = new Vector(-f.getZ(), 0, f.getX());
        final double[] heights = {0.8, 1.25, 1.7};
        final double[] tilt = {-14, 0, 14};
        final double tiltR = Math.toRadians(tilt[idx]);
        final double baseY = o.getY() + heights[idx];
        final boolean ltr = idx % 2 == 0;
        final double[] radii = {3.2, 4.2, 5.2, 6.2, 7.0};

        Util.sound(o, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.6f, 0.6f + idx * 0.15f);
        Util.sound(o, Sound.ENTITY_ENDERMAN_TELEPORT, 0.9f, 0.5f + idx * 0.2f);
        Util.sound(o, Sound.ENTITY_WITHER_SHOOT, 0.8f, 1.4f);

        // урон: 1 сердце каждому, кого задел разрез
        for (LivingEntity le : Util.targets(o.clone().add(0, 1, 0), 8, p)) {
            Vector rel = le.getBoundingBox().getCenter().subtract(o.toVector());
            double h = Math.hypot(rel.getX(), rel.getZ());
            if (h < 1.0 || h > 8.0) continue;
            Vector hn = new Vector(rel.getX(), 0, rel.getZ()).normalize();
            if (Math.acos(Math.max(-1, Math.min(1, hn.dot(f)))) > Math.toRadians(95)) continue;
            if (Math.abs(le.getBoundingBox().getCenter().getY() - baseY) > 3.2) continue;
            Util.hurt(le, p, 2.0);
            le.setVelocity(le.getVelocity().add(hn.multiply(0.5)).setY(0.25));
            Location lc = le.getBoundingBox().getCenter().toLocation(w);
            w.spawnParticle(Particle.SWEEP_ATTACK, lc, 1);
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, lc, 25, 0.3, 0.5, 0.3, 0, DARK);
        }

        new BukkitRunnable() {   // тёмная волна летит дальше разреза
            int t;
            @Override public void run() {
                double dist = 4 + t * 1.8;
                for (double a = -70; a <= 70; a += 5) {
                    double rad = Math.toRadians(a);
                    double x = Math.sin(rad) * dist * 0.9, z = Math.cos(rad) * dist - Math.abs(a) * 0.01 * dist;
                    double px = o.getX() + f.getX() * z + r.getX() * x, pz = o.getZ() + f.getZ() * z + r.getZ() * x;
                    w.spawnParticle(Particle.DUST_COLOR_TRANSITION, px, baseY, pz, 1, 0.1, 0.15, 0.1, 0, DARK);
                    if (((int) a) % 15 == 0) w.spawnParticle(Particle.SOUL_FIRE_FLAME, px, baseY, pz, 1, 0.1, 0.1, 0.1, 0.02);
                    if (Math.abs(a) > 60) w.spawnParticle(Particle.SWEEP_ATTACK, px, baseY, pz, 1);
                }
                if (idx == 2) {   // финальный разрез - вертикальное кольцо-"крест"
                    Vector ctr = new Vector(o.getX() + f.getX() * dist * 0.6, baseY + 0.4, o.getZ() + f.getZ() * dist * 0.6);
                    Util.ringAround(ctr, f, dist * 0.35, 30, q -> w.spawnParticle(Particle.DUST_COLOR_TRANSITION, q.getX(), q.getY(), q.getZ(), 1, 0.05, 0.05, 0.05, 0, DARK));
                }
                if (++t >= 6) cancel();
            }
        }.runTaskTimer(plugin, 2L, 1L);

        new BukkitRunnable() {
            int t;
            @Override public void run() {
                double a0 = -90 + 180.0 * t / 6.0, a1 = -90 + 180.0 * (t + 1) / 6.0;
                if (!ltr) { double tmp = 90 - (a0 + 90); a0 = 90 - (a1 + 90); a1 = tmp; }
                for (double a = Math.min(a0, a1); a <= Math.max(a0, a1); a += 4) {
                    double rad = Math.toRadians(a);
                    for (double R : radii) {
                        double x = Math.sin(rad) * R, z = Math.cos(rad) * R;
                        double px = o.getX() + f.getX() * z + r.getX() * x;
                        double pz = o.getZ() + f.getZ() * z + r.getZ() * x;
                        double py = baseY + x * Math.sin(tiltR) * 0.5;
                        w.spawnParticle(Particle.DUST_COLOR_TRANSITION, px, py, pz, 1, 0.05, 0.05, 0.05, 0, DARK);
                        if (R >= 6.2) {
                            w.spawnParticle(Particle.SOUL_FIRE_FLAME, px, py, pz, 1, 0.05, 0.05, 0.05, 0.01);
                            w.spawnParticle(Particle.SMOKE, px, py, pz, 1, 0.1, 0.1, 0.1, 0.01);
                        }
                        if (R == 5.2 && ((int) Math.round(a)) % 12 == 0)
                            w.spawnParticle(Particle.SWEEP_ATTACK, px, py, pz, 1);
                        if (R == 7.0) w.spawnParticle(Particle.REVERSE_PORTAL, px, py, pz, 1, 0.05, 0.05, 0.05, 0.05);
                    }
                }
                if (++t >= 6) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // =====================================================================
    // 7. ICE SWORD - ледяной метеор
    // =====================================================================
    private static Transformation tf(float s, float yaw, float tilt) {
        Quaternionf q = new Quaternionf().rotationY(yaw).rotateX(tilt);
        Vector3f t = new Vector3f(s / 2f, s / 2f, s / 2f).rotate(q).negate();
        return new Transformation(t, q, new Vector3f(s, s, s), new Quaternionf());
    }

    public void ice(Player p) {
        final World w = p.getWorld();
        final Location impact = aimGround(p, 30, 12);
        final Location c = impact.clone().add(0, 1, 0);
        final Particle.DustOptions blue = new Particle.DustOptions(Color.fromRGB(90, 175, 255), 2.6f);
        final Particle.DustTransition mist = new Particle.DustTransition(Color.fromRGB(120, 205, 255), Color.fromRGB(235, 250, 255), 2.2f);

        Util.sound(c, Sound.BLOCK_GLASS_BREAK, 2f, 0.5f);
        Util.sound(c, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.7f);
        Util.sound(c, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 2f, 0.6f);
        Util.sound(c, Sound.ENTITY_PLAYER_HURT_FREEZE, 2f, 0.6f);
        Util.sound(p.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 1.1f);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, c, 1);
        w.spawnParticle(Particle.BLOCK, c, 200, 2.5, 1.2, 2.5, 0.2, Material.BLUE_ICE.createBlockData());
        w.spawnParticle(Particle.SNOWFLAKE, c, 300, 4, 1.5, 4, 0.3);
        debris(c, impact.getY(), new Material[]{Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE}, 18, Particle.SNOWFLAKE);

        new BukkitRunnable() {
            int t;
            @Override public void run() {
                try {
                    if (t % 5 == 0 && t <= 120) {   // замедление II всем в облаке (кроме владельца)
                        for (Entity e : w.getNearbyEntities(impact, 5.2, 6, 5.2)) {
                            if (!(e instanceof LivingEntity le) || !Util.valid(le, p)) continue;
                            double hd = Math.hypot(le.getLocation().getX() - impact.getX(), le.getLocation().getZ() - impact.getZ());
                            if (hd > 5.2) continue;
                            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, false, true, true));
                            w.spawnParticle(Particle.SNOWFLAKE, le.getBoundingBox().getCenter().toLocation(w), 10, 0.3, 0.5, 0.3, 0.02);
                        }
                    }
                    if (t < 8) {   // ударная волна
                        double r = 7.0 * (t + 1) / 8.0;
                        Util.ring(impact.clone().add(0, 0.2, 0), r, Math.max(24, (int) (r * 10)), l -> {
                            w.spawnParticle(Particle.SNOWFLAKE, l, 1, 0.1, 0.15, 0.1, 0.02);
                            w.spawnParticle(Particle.DUST, l, 1, 0.1, 0.1, 0.1, 0, blue);
                        });
                    }
                    if (t < 70) {
                        // ножка гриба
                        double stemH = Math.min(9.0, t * 0.5);
                        for (double hgt = 0; hgt <= stemH; hgt += 0.6) {
                            double rr = 1.0 + 0.5 * Math.sin(hgt * 0.8 + t * 0.2);
                            for (int k = 0; k < 4; k++) {
                                double a = t * 0.25 + hgt * 0.6 + k * Math.PI / 2;
                                double x = impact.getX() + Math.cos(a) * rr, z = impact.getZ() + Math.sin(a) * rr, y = impact.getY() + hgt;
                                w.spawnParticle(Particle.DUST_COLOR_TRANSITION, x, y, z, 1, 0.15, 0.15, 0.15, 0, mist);
                                if (k % 2 == 0) w.spawnParticle(Particle.CLOUD, x, y, z, 1, 0.2, 0.2, 0.2, 0.01);
                            }
                        }
                        // шляпка гриба
                        if (t >= 6) {
                            double grow = Math.min(1.0, (t - 6) / 20.0);
                            double rc = 1.5 + 3.5 * grow;
                            double capY = impact.getY() + stemH + 0.4 * rc;
                            int n = 90;
                            double golden = Math.PI * (3 - Math.sqrt(5));
                            for (int i = 0; i < n; i++) {
                                double yy = 1 - 2.0 * (i + 0.5) / n;
                                if (yy < -0.3) continue;   // плоское "брюшко"
                                double rad = Math.sqrt(1 - yy * yy), th = i * golden + t * 0.08;
                                double x = impact.getX() + Math.cos(th) * rad * rc, z = impact.getZ() + Math.sin(th) * rad * rc;
                                double y = capY + yy * rc * 0.55;
                                w.spawnParticle(Particle.DUST, x, y, z, 1, 0.1, 0.1, 0.1, 0, blue);
                                if (i % 3 == 0) w.spawnParticle(Particle.CLOUD, x, y, z, 1, 0.15, 0.15, 0.15, 0.01);
                                if (i % 4 == 0) w.spawnParticle(Particle.SNOWFLAKE, x, y, z, 1, 0.1, 0.1, 0.1, 0.01);
                            }
                            final double ringY = capY - 0.3 * rc;
                            Util.ring(new Location(w, impact.getX(), ringY, impact.getZ()), rc * 0.95, 36,
                                    l -> w.spawnParticle(Particle.DUST_COLOR_TRANSITION, l, 1, 0.15, 0.15, 0.15, 0, mist));
                        }
                        if (t < 30) {   // волна у земли
                            double r = 1 + t * 0.2;
                            Util.ring(impact.clone().add(0, 0.3, 0), r, 40, l -> {
                                w.spawnParticle(Particle.CLOUD, l, 1, 0.2, 0.1, 0.2, 0.02);
                                w.spawnParticle(Particle.SNOWFLAKE, l, 1, 0.2, 0.1, 0.2, 0.02);
                            });
                        }
                    }
                    if (t >= 15) {   // синий туман на земле - держится 6 секунд
                        for (int i = 0; i < 45; i++) {
                            double a = rnd.nextDouble() * Math.PI * 2, rr = Math.sqrt(rnd.nextDouble()) * 5.0;
                            double x = impact.getX() + Math.cos(a) * rr, z = impact.getZ() + Math.sin(a) * rr, y = impact.getY() + 0.1 + rnd.nextDouble() * 2.2;
                            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, x, y, z, 1, 0.3, 0.2, 0.3, 0, mist);
                            w.spawnParticle(Particle.CLOUD, x, y, z, 1, 0.3, 0.2, 0.3, 0.01);
                            if (i % 2 == 0) w.spawnParticle(Particle.SNOWFLAKE, x, y, z, 1, 0.3, 0.2, 0.3, 0.01);
                        }
                    }
                    if (t % 20 == 0 && t > 0) Util.sound(c, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.6f, 0.5f);
                    if (++t > 120) {
                        w.spawnParticle(Particle.SNOWFLAKE, c, 120, 3, 1.5, 3, 0.2);
                        Util.sound(c, Sound.BLOCK_GLASS_BREAK, 1f, 0.8f);
                        cancel();
                    }
                } catch (Throwable ex) {
                    plugin.getLogger().warning("Ice Sword: " + ex);
                    ex.printStackTrace();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    

    // =====================================================================
    // Общие помощники
    // =====================================================================
    private BlockDisplay spawnBlock(Location l, Material m, float size) {
        BlockDisplay bd = l.getWorld().spawn(l, BlockDisplay.class, d -> {
            d.setBlock(m.createBlockData());
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(1);
            d.setInterpolationDuration(1);
            d.setViewRange(4f);
            d.setTransformation(tf(size, 0f, 0f));
        });
        plugin.track(bd);
        return bd;
    }

    private void debris(Location c, double floorY, Material[] mats, int n, Particle trail) {
        World w = c.getWorld();
        List<BlockDisplay> shards = new ArrayList<>();
        List<Vector> vel = new ArrayList<>();
        List<Float> sizes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, sp = 0.35 + rnd.nextDouble() * 0.6;
            float sz = 0.5f + rnd.nextFloat();
            shards.add(spawnBlock(c.clone(), mats[rnd.nextInt(mats.length)], sz));
            sizes.add(sz);
            vel.add(new Vector(Math.cos(a) * sp, 0.6 + rnd.nextDouble() * 0.7, Math.sin(a) * sp));
        }
        new BukkitRunnable() {
            int t;
            @Override public void run() {
                for (int i = 0; i < shards.size(); i++) {
                    BlockDisplay s = shards.get(i);
                    if (!s.isValid()) continue;
                    Vector v = vel.get(i);
                    v.setY(v.getY() - 0.07);
                    Location l = s.getLocation().add(v);
                    if (l.getY() < floorY + 0.2) { l.setY(floorY + 0.2); v.multiply(0.0); }
                    s.teleport(l);
                    s.setTransformation(tf(sizes.get(i), t * 0.4f + i, t * 0.25f));
                    s.setInterpolationDelay(0);
                    if (t % 2 == 0) w.spawnParticle(trail, l, 1, 0.1, 0.1, 0.1, 0.01);
                }
                if (++t >= 30) {
                    for (BlockDisplay s : shards) { plugin.untrack(s); if (s.isValid()) s.remove(); }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // =====================================================================
    // 9. FROSTMORN - ледяная буря
    // =====================================================================
    public void frostmorn(Player p) {
        final Location c = aimGround(p, 20, 20);
        final World w = c.getWorld();
        final int dur = plugin.getConfig().getInt("frostmorn.duration-seconds", 10) * 20;
        final double R = 7.0;
        final Particle.DustOptions storm = new Particle.DustOptions(Color.fromRGB(190, 225, 255), 2.6f);
        final Particle.DustOptions gloom = new Particle.DustOptions(Color.fromRGB(110, 140, 190), 3.2f);
        Util.sound(c, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 2f, 0.6f);
        Util.sound(c, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.8f, 0.5f);
        Util.sound(c, Sound.BLOCK_POWDER_SNOW_BREAK, 2f, 0.6f);
        Util.sound(c, Sound.ITEM_TRIDENT_THUNDER, 1.2f, 0.5f);
        w.spawnParticle(Particle.GUST_EMITTER_LARGE, c.clone().add(0, 1, 0), 1);
        hud.flash(p, "<#8EA9FF>❄ <white>Ледяная буря! <gray>Притяжение <white>10 <gray>блоков", 1800);
        Util.sound(p.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 1.2f);

        new BukkitRunnable() {
            int t;
            @Override public void run() {
                try {
                    if (t >= dur) {
                        Util.sound(c, Sound.BLOCK_GLASS_BREAK, 1.5f, 0.6f);
                        w.spawnParticle(Particle.SNOWFLAKE, c.clone().add(0, 3, 0), 200, R / 2, 3, R / 2, 0.3);
                        cancel();
                        return;
                    }
                    double grow = Math.min(1.0, t / 10.0);

                    if (t % 5 == 0) {   // замедление II всем в радиусе (кроме владельца)
                        for (Entity e : w.getNearbyEntities(c, R, 7, R)) {
                            if (!(e instanceof LivingEntity le) || !Util.valid(le, p)) continue;
                            double hd = Math.hypot(le.getLocation().getX() - c.getX(), le.getLocation().getZ() - c.getZ());
                            if (hd > R) continue;
                            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, true, true));
                            w.spawnParticle(Particle.SNOWFLAKE, le.getBoundingBox().getCenter().toLocation(w), 12, 0.3, 0.5, 0.3, 0.02);
                        }
                    }
                    if (t % 10 == 0) Util.sound(c, Sound.ITEM_ELYTRA_FLYING, 1.5f, 0.5f);
                    if (t % 40 == 20) {   // молния-эффект (без урона)
                        double a = rnd.nextDouble() * Math.PI * 2, rr = rnd.nextDouble() * R;
                        w.strikeLightningEffect(new Location(w, c.getX() + Math.cos(a) * rr, c.getY(), c.getZ() + Math.sin(a) * rr));
                    }

                    // буря притягивает игроков в центр (радиус 10)
                    for (Entity e : w.getNearbyEntities(c, 10, 10, 10)) {
                        if (!(e instanceof Player pl) || !Util.valid(pl, p)) continue;
                        Vector to = c.toVector().add(new Vector(0, 1, 0)).subtract(pl.getBoundingBox().getCenter());
                        double dh = Math.hypot(to.getX(), to.getZ());
                        if (dh > 10 || dh < 1.2) continue;
                        Vector pull = new Vector(to.getX(), 0, to.getZ()).normalize().multiply(0.22 + 0.1 * (dh / 10.0));
                        Vector cur = pl.getVelocity();
                        pl.setVelocity(new Vector(cur.getX() * 0.6 + pull.getX(), cur.getY(), cur.getZ() * 0.6 + pull.getZ()));
                    }
                    if (t % 4 == 0) Util.ring(c.clone().add(0, 0.15, 0), 10.0 * grow, 70, l2 -> w.spawnParticle(Particle.SNOWFLAKE, l2, 1, 0.1, 0.05, 0.1, 0.02));
                    // воронка из 8 рукавов
                    for (int arm = 0; arm < 8; arm++) {
                        for (double hgt = 0; hgt <= 10; hgt += 0.6) {
                            double r = (2.0 + (R - 2.0) * (hgt / 10.0)) * grow;
                            double a = t * 0.3 + arm * (Math.PI * 2 / 8) + hgt * 0.5;
                            double x = c.getX() + Math.cos(a) * r, y = c.getY() + hgt * grow, z = c.getZ() + Math.sin(a) * r;
                            w.spawnParticle(Particle.SNOWFLAKE, x, y, z, 2, 0.3, 0.3, 0.3, 0.01);
                            w.spawnParticle(Particle.CLOUD, x, y, z, 1, 0.3, 0.3, 0.3, 0.01);
                            if (((int) (hgt / 0.6) + arm) % 2 == 0) w.spawnParticle(Particle.DUST, x, y, z, 1, 0.25, 0.25, 0.25, 0, storm);
                        }
                    }
                    // ледяной сигил-снежинка на земле и столб холода в центре
                    if (t % 2 == 0) {
                        double ph = t * 0.03;
                        for (int k = 0; k < 6; k++) {
                            double sa = ph + k * Math.PI / 3;
                            for (double rr2 = 0.6; rr2 <= R * grow; rr2 += 0.5) {
                                double bx = c.getX() + Math.cos(sa) * rr2, bz = c.getZ() + Math.sin(sa) * rr2;
                                w.spawnParticle(Particle.SNOWFLAKE, bx, c.getY() + 0.15, bz, 1, 0, 0, 0, 0);
                                if (((int) (rr2 * 2)) % 4 == 0) {
                                    for (int sg = -1; sg <= 1; sg += 2) {
                                        double sb = sa + sg * 0.6;
                                        w.spawnParticle(Particle.DUST, bx + Math.cos(sb) * 0.9, c.getY() + 0.15, bz + Math.sin(sb) * 0.9, 1, 0, 0, 0, 0, storm);
                                    }
                                }
                            }
                        }
                        for (double cy = 0; cy < 12; cy += 0.5)
                            w.spawnParticle(Particle.END_ROD, c.getX(), c.getY() + cy * grow, c.getZ(), 1, 0.15, 0, 0.15, 0.0);
                    }
                    // грозовая "крыша" бури
                    for (int i = 0; i < 24; i++) {
                        double a = rnd.nextDouble() * Math.PI * 2, rr = Math.sqrt(rnd.nextDouble()) * R * grow;
                        double y = c.getY() + 9.5 + rnd.nextDouble() * 1.5;
                        w.spawnParticle(Particle.DUST, c.getX() + Math.cos(a) * rr, y, c.getZ() + Math.sin(a) * rr, 1, 0.5, 0.3, 0.5, 0, gloom);
                        w.spawnParticle(Particle.CLOUD, c.getX() + Math.cos(a) * rr, y, c.getZ() + Math.sin(a) * rr, 1, 0.4, 0.2, 0.4, 0.01);
                    }
                    // метель по объёму
                    for (int i = 0; i < 50; i++) {
                        double a = rnd.nextDouble() * Math.PI * 2, rr = Math.sqrt(rnd.nextDouble()) * R * grow;
                        w.spawnParticle(Particle.SNOWFLAKE, c.getX() + Math.cos(a) * rr, c.getY() + rnd.nextDouble() * 9,
                                c.getZ() + Math.sin(a) * rr, 1, 0, 0, 0, 0.1);
                    }
                    // позёмка и ледяная крошка у земли
                    for (int i = 0; i < 14; i++) {
                        double a = rnd.nextDouble() * Math.PI * 2, rr = Math.sqrt(rnd.nextDouble()) * R * grow;
                        Location g = new Location(w, c.getX() + Math.cos(a) * rr, c.getY() + 0.2, c.getZ() + Math.sin(a) * rr);
                        w.spawnParticle(Particle.CLOUD, g, 1, 0.3, 0.05, 0.3, 0.02);
                        w.spawnParticle(Particle.WHITE_ASH, g, 3, 0.3, 0.2, 0.3, 0);
                    }
                    final double rimR = R * grow;
                    Util.ring(c.clone().add(0, 0.15, 0), rimR, 48, l -> w.spawnParticle(Particle.DUST, l, 1, 0.1, 0.05, 0.1, 0, storm));
                    t++;
                } catch (Throwable ex) {
                    plugin.getLogger().warning("Frostmorn: " + ex);
                    ex.printStackTrace();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // =====================================================================
    // 10. MONARCH STAFF
    // =====================================================================
    /** ЛКМ: метеорит */
    

    

    /** Общий "метеорит": огромный блок плавно падает строго сверху, бьёт при касании и при приземлении. */
    

    /** ПКМ: 5 зомби */
    public void zombies(Player p) { minions.summon(p); }

    /** Shift+ЛКМ: подброс всех в радиусе 7 на 3 блока */
    public void launch(Player p) {
        Location c = p.getLocation();
        World w = c.getWorld();
        Util.sound(c, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.5f, 1.4f);
        Util.sound(c, Sound.ENTITY_EVOKER_CAST_SPELL, 1.5f, 0.8f);
        Util.sound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f);
        double v = Util.velocityForHeight(3);
        for (LivingEntity le : Util.targets(c.clone().add(0, 1, 0), 7, p)) {
            le.setVelocity(new Vector(0, v, 0));
            w.spawnParticle(Particle.REVERSE_PORTAL, le.getLocation().add(0, 0.5, 0), 25, 0.3, 0.5, 0.3, 0.2);
        }
        final Particle.DustTransition violet = new Particle.DustTransition(Color.fromRGB(176, 77, 255), Color.fromRGB(30, 0, 60), 1.8f);
        new BukkitRunnable() {
            int t;
            @Override public void run() {
                double r = 7.0 * (t + 1) / 10.0;
                Util.ring(c.clone().add(0, 0.15, 0), r, Math.max(24, (int) (r * 9)), l -> {
                    w.spawnParticle(Particle.DUST_COLOR_TRANSITION, l, 1, 0.1, 0.1, 0.1, 0, violet);
                    w.spawnParticle(Particle.SOUL_FIRE_FLAME, l, 1, 0.1, 0.2, 0.1, 0.02);
                });
                for (double y = 0; y < 6; y += 0.5) {
                    double a = t * 0.8 + y;
                    w.spawnParticle(Particle.REVERSE_PORTAL, c.getX() + Math.cos(a), c.getY() + y, c.getZ() + Math.sin(a), 2, 0.1, 0.1, 0.1, 0.05);
                }
                if (t == 0) w.spawnParticle(Particle.EXPLOSION, c.clone().add(0, 1, 0), 1);
                if (t % 2 == 0) Util.sigil(c.clone().add(0, 0.2, 0), Math.min(7.0, 2 + t * 0.6), t * 0.15, l2 -> w.spawnParticle(Particle.DUST_COLOR_TRANSITION, l2, 1, 0, 0, 0, 0, violet));
                for (int k = 0; k < 3; k++)   // кольца гравитации поднимаются вверх
                    Util.ring(c.clone().add(0, (t * 0.35 + k * 1.0) % 3.5, 0), 7.0 * (1.0 - ((t * 0.35 + k * 1.0) % 3.5) / 5.0), 36,
                            l2 -> w.spawnParticle(Particle.SOUL_FIRE_FLAME, l2, 1, 0.05, 0.05, 0.05, 0.01));
                if (++t >= 14) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // =====================================================================
    // 11. SHADOW REAPER - теневой шаг
    // =====================================================================
    public boolean shadowStep(Player p) {
        final World w = p.getWorld();
        final Location eye = p.getEyeLocation();
        final Vector dir = eye.getDirection().normalize();
        final double eyeH = p.getEyeHeight();
        RayTraceResult rr = w.rayTraceBlocks(eye, dir, 8, FluidCollisionMode.NEVER, true);
        double dist = rr == null ? 8 : Math.max(0, rr.getHitPosition().distance(eye.toVector()) - 0.3);
        final Location from = p.getLocation();
        double safe = -1;
        for (double d = dist; d >= 0.5; d -= 0.25) {
            Location cand = eye.clone().add(dir.clone().multiply(d)).subtract(0, eyeH, 0);
            BoundingBox bb = p.getBoundingBox().shift(cand.toVector().subtract(from.toVector())).expand(-0.05);
            if (!w.hasCollisionsIn(bb)) { safe = d; break; }
        }
        if (safe < 0) {
            hud.flash(p, "<red>✘ <gray>Нет места для рывка", 1200);
            return false;
        }
        final double total = safe;
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1, false, true, true));
        Util.sound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 1.6f);
        Util.sound(from, Sound.ENTITY_PHANTOM_SWOOP, 1.5f, 1.5f);
        w.spawnParticle(Particle.REVERSE_PORTAL, from.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.3);

        new BukkitRunnable() {
            double cur = 0;
            @Override public void run() {
                if (!p.isOnline() || p.isDead()) { cancel(); return; }
                cur = Math.min(total, cur + 2.0);   // 2 блока за тик - рывок ~4 тика
                Location now = p.getLocation();
                Location pos = eye.clone().add(dir.clone().multiply(cur)).subtract(0, eyeH, 0);
                pos.setYaw(now.getYaw());
                pos.setPitch(now.getPitch());
                BoundingBox bb = p.getBoundingBox().shift(pos.toVector().subtract(now.toVector())).expand(-0.05);
                if (w.hasCollisionsIn(bb)) { cancel(); return; }

                Vector a = now.toVector().add(new Vector(0, 1, 0)), b = pos.toVector().add(new Vector(0, 1, 0));
                Vector step = b.clone().subtract(a);
                double len = step.length();
                if (len > 0) step.normalize();
                for (double s2 = 0; s2 < len; s2 += 0.3) {
                    Vector pt = a.clone().add(step.clone().multiply(s2));
                    w.spawnParticle(Particle.SQUID_INK, pt.getX(), pt.getY(), pt.getZ(), 2, 0.2, 0.4, 0.2, 0.01);
                    w.spawnParticle(Particle.DUST_COLOR_TRANSITION, pt.getX(), pt.getY(), pt.getZ(), 2, 0.15, 0.3, 0.15, 0, DARK);
                    w.spawnParticle(Particle.SMOKE, pt.getX(), pt.getY(), pt.getZ(), 1, 0.1, 0.3, 0.1, 0.01);
                }
                for (double yy = 0; yy <= 1.8; yy += 0.3) {   // тень-послеобраз остаётся на месте
                    w.spawnParticle(Particle.SQUID_INK, now.getX(), now.getY() + yy, now.getZ(), 1, 0.2, 0.05, 0.2, 0.0);
                    w.spawnParticle(Particle.DUST_COLOR_TRANSITION, now.getX(), now.getY() + yy, now.getZ(), 1, 0.25, 0.05, 0.25, 0, DARK);
                }
                p.teleport(pos);
                p.setFallDistance(0);
                p.setVelocity(new Vector(0, 0, 0));
                if (cur >= total) {
                    p.setVelocity(dir.clone().multiply(0.25));
                    Vector ctr = pos.toVector().add(new Vector(0, 1, 0));
                    for (int k = 0; k < 3; k++)
                        Util.ringAround(ctr.clone().add(dir.clone().multiply(0.4 * k)), dir, 1.0 + k * 0.7, 30,
                                q -> w.spawnParticle(Particle.DUST_COLOR_TRANSITION, q.getX(), q.getY(), q.getZ(), 1, 0.05, 0.05, 0.05, 0, DARK));
                    w.spawnParticle(Particle.REVERSE_PORTAL, pos.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.3);
                    Util.sound(pos, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.8f);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    // =====================================================================
    // 12. SANGUINE ART - кровавый ритуал
    // =====================================================================
    public long sanguineLeftMs(Player p) {
        Long u = sanguineUntil.get(p.getUniqueId());
        return u == null ? 0 : Math.max(0, u - System.currentTimeMillis());
    }

    public boolean sanguine(Player p) {
        if (sanguineLeftMs(p) > 0) {
            hud.flash(p, "<red>✘ <gray>Ритуал уже активен", 1000);
            return false;
        }
        final int dur = plugin.getConfig().getInt("sanguine.duration-seconds", 15) * 20;
        final double target = plugin.getConfig().getDouble("sanguine.max-health", 14.0);   // 7 сердец
        AttributeInstance ai = p.getAttribute(Attribute.MAX_HEALTH);
        if (ai != null) {
            if (ai.getModifier(sanguineKey) != null) ai.removeModifier(ai.getModifier(sanguineKey));
            double loss = Math.max(0, ai.getValue() - target);
            if (loss > 0) ai.addModifier(new AttributeModifier(sanguineKey, -loss, AttributeModifier.Operation.ADD_NUMBER));
            if (p.getHealth() > ai.getValue()) p.setHealth(ai.getValue());
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, dur, 0, false, true, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, dur, 0, false, true, true));
        sanguineUntil.put(p.getUniqueId(), System.currentTimeMillis() + dur * 50L);

        World w = p.getWorld();
        Location c = p.getLocation();
        Util.sound(c, Sound.ENTITY_WARDEN_HEARTBEAT, 2f, 0.6f);
        Util.sound(c, Sound.ENTITY_PLAYER_HURT, 1f, 0.6f);
        Util.sound(c, Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1f, 0.5f);
        w.spawnParticle(Particle.DAMAGE_INDICATOR, c.clone().add(0, 1, 0), 15, 0.4, 0.6, 0.4, 0.1);
        w.spawnParticle(Particle.DUST, c.clone().add(0, 1, 0), 60, 0.5, 0.8, 0.5, 0, BLOOD_BIG);
        Util.ring(c.clone().add(0, 0.1, 0), 1.6, 28, l -> w.spawnParticle(Particle.DUST, l, 1, 0.05, 0.02, 0.05, 0, BLOOD_BIG));
        new BukkitRunnable() {   // кровавая ударная волна
            int t;
            @Override public void run() {
                double r = 6.0 * (t + 1) / 10.0;
                Util.ring(c.clone().add(0, 0.2, 0), r, Math.max(24, (int) (r * 8)), l2 -> {
                    w.spawnParticle(Particle.DUST, l2, 1, 0.1, 0.05, 0.1, 0, BLOOD_BIG);
                    w.spawnParticle(Particle.FALLING_DUST, l2.clone().add(0, 1.2, 0), 1, 0.1, 0.1, 0.1, 0, Material.REDSTONE_BLOCK.createBlockData());
                });
                if (++t >= 10) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);

        new BukkitRunnable() {
            int t;
            @Override public void run() {
                if (!p.isOnline() || p.isDead() || t >= dur) { endSanguine(p); cancel(); return; }
                t++;
                if (t % 2 == 0) {   // кровь стекает
                    Location l = p.getLocation();
                    for (int k = 0; k < 3; k++) {
                        Location b = l.clone().add((rnd.nextDouble() - 0.5) * 0.7, rnd.nextDouble() * 1.8, (rnd.nextDouble() - 0.5) * 0.7);
                        w.spawnParticle(Particle.FALLING_DUST, b, 1, 0, 0, 0, 0, Material.REDSTONE_BLOCK.createBlockData());
                        w.spawnParticle(Particle.DUST, b, 1, 0.05, 0.05, 0.05, 0, BLOOD);
                    }
                    Location hand = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(0.7)).add(0, -0.5, 0);
                    w.spawnParticle(Particle.FALLING_DUST, hand, 2, 0.1, 0.1, 0.1, 0, Material.REDSTONE_BLOCK.createBlockData());
                }
                if (t % 10 == 0) Util.ring(p.getLocation().add(0, 0.1, 0), 0.9, 14,
                        l2 -> w.spawnParticle(Particle.DUST, l2, 1, 0.03, 0.01, 0.03, 0, BLOOD_BIG));
                if (t % 30 == 0) Util.sound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 0.8f);
                if (t % 2 == 0) Util.sigil(p.getLocation().add(0, 0.1, 0), 1.8, t * 0.07, l3 -> w.spawnParticle(Particle.DUST, l3, 1, 0, 0, 0, 0, BLOOD_BIG));
                double ha = t * 0.5;
                w.spawnParticle(Particle.DUST, p.getLocation().add(Math.cos(ha) * 0.9, (t % 20) * 0.1, Math.sin(ha) * 0.9), 1, 0, 0, 0, 0, BLOOD);
                w.spawnParticle(Particle.DUST, p.getLocation().add(Math.cos(ha + Math.PI) * 0.9, (t % 20) * 0.1, Math.sin(ha + Math.PI) * 0.9), 1, 0, 0, 0, 0, BLOOD);
                if (t % 30 == 0) Util.ring(p.getLocation().add(0, 0.2, 0), 2.6, 30, l3 -> w.spawnParticle(Particle.DUST, l3, 1, 0.05, 0.02, 0.05, 0, BLOOD_BIG));
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    public void endSanguine(Player p) {
        sanguineUntil.remove(p.getUniqueId());
        AttributeInstance ai = p.getAttribute(Attribute.MAX_HEALTH);
        if (ai != null && ai.getModifier(sanguineKey) != null) ai.removeModifier(ai.getModifier(sanguineKey));
    }

    // =====================================================================
    // 13. EXPLOSIVE BOW - динамит
    // =====================================================================
    public boolean isDynamite(Entity e) {
        return e.getPersistentDataContainer().has(dynKey, org.bukkit.persistence.PersistentDataType.BYTE);
    }

    public void dynamiteShot(Player p, AbstractArrow arrow) {
        arrow.getPersistentDataContainer().set(dynKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        dynamite.add(arrow.getUniqueId());
        final BlockDisplay tnt = spawnBlock(arrow.getLocation(), Material.TNT, 0.6f);
        arrow.addPassenger(tnt);
        Util.sound(p.getLocation(), Sound.ENTITY_TNT_PRIMED, 1.5f, 1.3f);
        Util.sound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.8f);

        new BukkitRunnable() {
            int t;
            @Override public void run() {
                if (!arrow.isValid() || !dynamite.contains(arrow.getUniqueId())) {
                    plugin.untrack(tnt);
                    if (tnt.isValid()) tnt.remove();
                    cancel();
                    return;
                }
                t++;
                Location l = arrow.getLocation();
                l.getWorld().spawnParticle(Particle.SMOKE, l, 3, 0.1, 0.1, 0.1, 0.01);
                l.getWorld().spawnParticle(Particle.FLAME, l, 2, 0.1, 0.1, 0.1, 0.02);
                if (t % 3 == 0) l.getWorld().spawnParticle(Particle.LAVA, l, 1, 0.1, 0.1, 0.1, 0);
                if (arrow.isInBlock() || t > 160) dynamiteHit(arrow);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Попадание динамита: урон стрелы уже прошёл (÷3), взрыв - на следующем тике. */
    public void dynamiteHit(AbstractArrow arrow) {
        if (!dynamite.remove(arrow.getUniqueId())) return;
        final Location l = arrow.getLocation();
        final Entity shooter = arrow.getShooter() instanceof Entity en ? en : null;
        Bukkit.getScheduler().runTask(plugin, () -> {
            arrow.remove();
            float power = (float) (plugin.getConfig().getDouble("explosive.power", 4.0)
                    * plugin.getConfig().getDouble("explosive.radius-multiplier", 3.0));   // радиус x3
            boolean breakBlocks = plugin.getConfig().getBoolean("explosive.break-blocks", false);
            if (shooter instanceof Player sp && !plugin.getConfig().getBoolean("explosive.self-damage", false)) {
                UUID sid = sp.getUniqueId();
                boomImmune.add(sid);
                Bukkit.getScheduler().runTaskLater(plugin, () -> boomImmune.remove(sid), 2L);
            }
            l.getWorld().createExplosion(l, power, false, breakBlocks, shooter);
            fireBoom(l);
        });
    }

    /** Огненный гриб и ударные волны для взрывного лука. */
    private void fireBoom(Location l) {
        final World w = l.getWorld();
        final Particle.DustTransition fire = new Particle.DustTransition(Color.fromRGB(255, 170, 40), Color.fromRGB(120, 30, 10), 2.4f);
        new BukkitRunnable() {
            int t;
            @Override public void run() {
                try {
                    if (t < 10) {   // ударные волны
                        double r = 12.0 * (t + 1) / 10.0;
                        Util.ring(l.clone().add(0, 0.3, 0), r, Math.max(30, (int) (r * 8)), x -> {
                            w.spawnParticle(Particle.FLAME, x, 1, 0.1, 0.15, 0.1, 0.03);
                            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, x, 1, 0.1, 0.1, 0.1, 0, fire);
                        });
                    }
                    double stemH = Math.min(10.0, t * 0.6);
                    for (double hgt = 0; hgt <= stemH; hgt += 0.7) {   // ножка
                        double rr = 1.4 + 0.6 * Math.sin(hgt * 0.7 + t * 0.3);
                        for (int k = 0; k < 4; k++) {
                            double a = t * 0.3 + hgt * 0.5 + k * Math.PI / 2;
                            double x = l.getX() + Math.cos(a) * rr, z = l.getZ() + Math.sin(a) * rr, y = l.getY() + hgt;
                            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, x, y, z, 1, 0.2, 0.2, 0.2, 0, fire);
                            w.spawnParticle(k % 2 == 0 ? Particle.FLAME : Particle.LARGE_SMOKE, x, y, z, 1, 0.2, 0.2, 0.2, 0.01);
                        }
                    }
                    if (t >= 5) {   // шляпка
                        double grow = Math.min(1.0, (t - 5) / 18.0);
                        double rc = 2.0 + 4.5 * grow;
                        double capY = l.getY() + stemH + 0.4 * rc;
                        int n = 100;
                        double golden = Math.PI * (3 - Math.sqrt(5));
                        for (int i = 0; i < n; i++) {
                            double yy = 1 - 2.0 * (i + 0.5) / n;
                            if (yy < -0.3) continue;
                            double rad = Math.sqrt(1 - yy * yy), th = i * golden + t * 0.08;
                            double x = l.getX() + Math.cos(th) * rad * rc, z = l.getZ() + Math.sin(th) * rad * rc, y = capY + yy * rc * 0.55;
                            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, x, y, z, 1, 0.1, 0.1, 0.1, 0, fire);
                            if (i % 3 == 0) w.spawnParticle(Particle.FLAME, x, y, z, 1, 0.15, 0.15, 0.15, 0.01);
                            if (i % 4 == 0) w.spawnParticle(Particle.LARGE_SMOKE, x, y, z, 1, 0.15, 0.15, 0.15, 0.01);
                        }
                    }
                    if (++t > 45) cancel();
                } catch (Throwable ex) {
                    plugin.getLogger().warning("Explosive Bow: " + ex);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // =====================================================================
    // 8. SOUL FORTRESS
    // =====================================================================
    public void fortress(Player p) { fortress.start(p); }
}
