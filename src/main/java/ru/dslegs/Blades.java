package ru.dslegs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
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

/** Способности Sakura Katana и Warrior Greatsword. */
public final class Blades {
    private final DSLegsPlugin plugin;
    private final Stun stun;
    private final Hud hud;
    private final Random rnd = new Random();

    private static final Particle.DustOptions PINK = new Particle.DustOptions(Color.fromRGB(255, 140, 195), 1.4f);
    private static final Particle.DustOptions PALE = new Particle.DustOptions(Color.fromRGB(255, 225, 238), 0.9f);
    private static final Particle.DustTransition PETAL =
            new Particle.DustTransition(Color.fromRGB(255, 95, 170), Color.fromRGB(255, 240, 246), 1.7f);
    private static final Particle.DustTransition STEEL =
            new Particle.DustTransition(Color.fromRGB(205, 210, 220), Color.fromRGB(110, 70, 50), 1.6f);

    public Blades(DSLegsPlugin plugin, Stun stun, Hud hud) {
        this.plugin = plugin; this.stun = stun; this.hud = hud;
    }

    private double cfg(String path, double def) { return plugin.getConfig().getDouble(path, def); }

    // =====================================================================
    // SAKURA KATANA - ПКМ: рывок лепестков
    // =====================================================================
    public boolean sakuraDash(Player p) {
        final World w = p.getWorld();
        final Location eye = p.getEyeLocation();
        final Vector dir = eye.getDirection().normalize();
        final double eyeH = p.getEyeHeight();
        final double range = cfg("sakura.dash-range", 8.0), damage = cfg("sakura.dash-damage", 6.0);
        RayTraceResult rr = w.rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
        double dist = rr == null ? range : Math.max(0, rr.getHitPosition().distance(eye.toVector()) - 0.3);
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
        Util.sound(from, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.4f, 1.5f);
        Util.sound(from, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.9f, 1.7f);
        Util.sound(from, Sound.BLOCK_CHERRY_LEAVES_BREAK, 1.6f, 0.9f);
        w.spawnParticle(Particle.CHERRY_LEAVES, from.clone().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0);

        final List<Vector> path = new ArrayList<>();
        final Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            double cur = 0;
            @Override public void run() {
                if (!p.isOnline() || p.isDead()) { cancel(); return; }
                cur = Math.min(total, cur + 2.0);
                Location now = p.getLocation();
                Location pos = eye.clone().add(dir.clone().multiply(cur)).subtract(0, eyeH, 0);
                pos.setYaw(now.getYaw());
                pos.setPitch(now.getPitch());
                BoundingBox bb = p.getBoundingBox().shift(pos.toVector().subtract(now.toVector())).expand(-0.05);
                if (w.hasCollisionsIn(bb)) { finish(now); return; }

                Vector a = now.toVector().add(new Vector(0, 1, 0)), b = pos.toVector().add(new Vector(0, 1, 0));
                Vector step = b.clone().subtract(a);
                double len = step.length();
                if (len > 0) step.normalize();
                for (double s = 0; s < len; s += 0.3) {
                    Vector pt = a.clone().add(step.clone().multiply(s));
                    path.add(pt);
                    w.spawnParticle(Particle.DUST_COLOR_TRANSITION, pt.getX(), pt.getY(), pt.getZ(), 3, 0.2, 0.45, 0.2, 0, PETAL);
                    w.spawnParticle(Particle.CHERRY_LEAVES, pt.getX(), pt.getY() + 0.3, pt.getZ(), 3, 0.3, 0.5, 0.3, 0);
                    w.spawnParticle(Particle.DUST, pt.getX(), pt.getY(), pt.getZ(), 1, 0.1, 0.1, 0.1, 0, PALE);
                    // урон всем, кого задел клинок
                    Location c = pt.toLocation(w);
                    for (LivingEntity le : Util.targets(c, 1.8, p)) {
                        if (!hit.add(le.getUniqueId())) continue;
                        Util.hurt(le, p, damage);
                        Location lc = le.getBoundingBox().getCenter().toLocation(w);
                        w.spawnParticle(Particle.SWEEP_ATTACK, lc, 2, 0.2, 0.2, 0.2, 0);
                        w.spawnParticle(Particle.CHERRY_LEAVES, lc, 30, 0.35, 0.5, 0.35, 0);
                        w.spawnParticle(Particle.DUST_COLOR_TRANSITION, lc, 18, 0.3, 0.5, 0.3, 0, PETAL);
                        Util.sound(lc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.5f);
                    }
                }
                if (((int) cur) % 4 == 0) w.spawnParticle(Particle.SWEEP_ATTACK, b.getX(), b.getY(), b.getZ(), 1);
                p.teleport(pos);
                p.setFallDistance(0);
                p.setVelocity(new Vector(0, 0, 0));
                if (cur >= total) finish(pos);
            }

            private void finish(Location end) {
                cancel();
                p.setVelocity(dir.clone().multiply(0.25));
                // финальный взмах: дуга перед игроком
                Vector f = dir.clone().setY(0);
                if (f.lengthSquared() < 1e-4) f = new Vector(0, 0, 1);
                f.normalize();
                Vector r = new Vector(-f.getZ(), 0, f.getX());
                for (double ang = -75; ang <= 75; ang += 5) {
                    double rad = Math.toRadians(ang);
                    for (double R = 1.6; R <= 2.6; R += 0.5) {
                        double x = Math.sin(rad) * R, z = Math.cos(rad) * R;
                        w.spawnParticle(Particle.DUST_COLOR_TRANSITION, end.getX() + f.getX() * z + r.getX() * x,
                                end.getY() + 1.1 + x * 0.25, end.getZ() + f.getZ() * z + r.getZ() * x, 1, 0, 0, 0, 0, PETAL);
                    }
                }
                w.spawnParticle(Particle.CHERRY_LEAVES, end.clone().add(0, 1.2, 0), 50, 0.8, 0.6, 0.8, 0);
                Util.sound(end, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 1.6f);
                Util.sound(end, Sound.BLOCK_CHERRY_LEAVES_PLACE, 1.4f, 1.2f);
                petalTrail(w, path);
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    /** След рывка: лепестки ещё несколько секунд осыпаются вдоль пути. */
    private void petalTrail(World w, List<Vector> path) {
        if (path.isEmpty()) return;
        final int ticks = (int) (cfg("sakura.trail-seconds", 3.0) * 20);
        new BukkitRunnable() {
            int t;
            @Override public void run() {
                for (int i = 0; i < path.size(); i += 3) {
                    Vector v = path.get(Math.min(path.size() - 1, i + rnd.nextInt(3)));
                    w.spawnParticle(Particle.CHERRY_LEAVES, v.getX(), v.getY() + 0.6, v.getZ(), 1, 0.4, 0.5, 0.4, 0);
                    if (rnd.nextInt(3) == 0)
                        w.spawnParticle(Particle.DUST, v.getX(), v.getY() - 0.7, v.getZ(), 1, 0.3, 0.1, 0.3, 0, PALE);
                }
                if ((t += 3) >= ticks) cancel();
            }
        }.runTaskTimer(plugin, 2L, 3L);
    }

    // =====================================================================
    // SAKURA KATANA - Shift+ЛКМ: цветение (взрыв лепестков)
    // =====================================================================
    public void sakuraBloom(Player p) {
        final Location c = p.getLocation();
        final World w = c.getWorld();
        final double radius = cfg("sakura.bloom-radius", 6.0), damage = cfg("sakura.bloom-damage", 8.0);
        final int stunTicks = (int) (cfg("sakura.stun-seconds", 2.0) * 20);
        final int blindTicks = (int) (cfg("sakura.blindness-seconds", 3.0) * 20);
        final Location mid = c.clone().add(0, 1, 0);

        Util.sound(c, Sound.ENTITY_GENERIC_EXPLODE, 1.3f, 1.5f);
        Util.sound(c, Sound.BLOCK_CHERRY_LEAVES_BREAK, 2f, 0.6f);
        Util.sound(c, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.5f, 0.7f);
        Util.sound(c, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.5f);
        w.spawnParticle(Particle.EXPLOSION, mid, 3, 0.6, 0.4, 0.6, 0);
        w.spawnParticle(Particle.CHERRY_LEAVES, mid, 350, radius * 0.45, 1.2, radius * 0.45, 0);
        w.spawnParticle(Particle.END_ROD, mid, 40, 0.2, 0.2, 0.2, 0.25);

        for (LivingEntity le : Util.targets(mid, radius, p)) {
            Util.hurt(le, p, damage);
            stun.apply(le, stunTicks, Particle.CHERRY_LEAVES);
            le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindTicks, 1, false, true, true));
            Location lc = le.getBoundingBox().getCenter().toLocation(w);
            w.spawnParticle(Particle.CHERRY_LEAVES, lc, 40, 0.4, 0.6, 0.4, 0);
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, lc, 25, 0.35, 0.6, 0.35, 0, PETAL);
        }

        new BukkitRunnable() {   // расширяющаяся сфера лепестков + цветок на земле
            int t;
            @Override public void run() {
                double r = radius * (t + 1) / 7.0;
                int n = (int) (40 + r * 22);
                for (int i = 0; i < n; i++) {   // точки по сфере (спираль Фибоначчи), только верхняя половина
                    double y = 1 - (i + 0.5) / n, rad = Math.sqrt(1 - y * y), th = i * 2.399963 + t;
                    double px = mid.getX() + Math.cos(th) * rad * r, py = c.getY() + 0.2 + y * r * 0.8, pz = mid.getZ() + Math.sin(th) * rad * r;
                    w.spawnParticle(Particle.DUST_COLOR_TRANSITION, px, py, pz, 1, 0, 0, 0, 0, PETAL);
                    if (i % 4 == 0) w.spawnParticle(Particle.CHERRY_LEAVES, px, py, pz, 1, 0.1, 0.1, 0.1, 0);
                }
                flower(c.clone().add(0, 0.15, 0), r, t * 0.12);
                if (++t >= 7) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);

        new BukkitRunnable() {   // после взрыва лепестки опадают по всей площади
            int t;
            @Override public void run() {
                for (int i = 0; i < 14; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * radius;
                    w.spawnParticle(Particle.CHERRY_LEAVES, c.getX() + Math.cos(a) * d, c.getY() + 2.5 + rnd.nextDouble() * 1.5,
                            c.getZ() + Math.sin(a) * d, 1, 0, 0, 0, 0);
                }
                if (t % 10 == 0) flower(c.clone().add(0, 0.15, 0), radius, t * 0.02);
                if ((t += 2) >= 60) cancel();
            }
        }.runTaskTimer(plugin, 8L, 2L);
    }

    /** Пятилепестковый цветок сакуры на земле. */
    private void flower(Location c, double r, double rot) {
        World w = c.getWorld();
        int n = (int) Math.max(60, r * 36);
        for (int i = 0; i < n; i++) {
            double th = 2 * Math.PI * i / n;
            double rr = r * (0.35 + 0.65 * Math.abs(Math.cos(2.5 * th)));
            w.spawnParticle(Particle.DUST, c.getX() + Math.cos(th + rot) * rr, c.getY(), c.getZ() + Math.sin(th + rot) * rr,
                    1, 0, 0, 0, 0, PINK);
        }
        Util.ring(c, r * 0.18, 14, q -> w.spawnParticle(Particle.DUST, q, 1, 0, 0, 0, 0, PALE));
    }

    // =====================================================================
    // WARRIOR GREATSWORD - ПКМ: раскол земли
    // =====================================================================
    public void earthshatter(Player p) {
        final Location o = p.getLocation();
        final World w = o.getWorld();
        Vector f0 = o.getDirection().setY(0);
        if (f0.lengthSquared() < 1e-4) f0 = new Vector(0, 0, 1);
        final Vector f = f0.normalize();
        final Vector side = new Vector(-f.getZ(), 0, f.getX());
        final double range = cfg("warrior.range", 12.0), damage = cfg("warrior.damage", 8.0);
        final int resist = (int) (cfg("warrior.resistance-seconds", 5.0) * 20);

        // боевой клич: воин на время становится крепче
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, resist, 0, false, true, true));
        Util.sound(o, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.6f, 0.7f);
        Util.sound(o, Sound.ENTITY_RAVAGER_ROAR, 0.8f, 1.3f);
        Util.sound(o, Sound.BLOCK_ANVIL_LAND, 0.7f, 0.5f);
        final Location slam = o.clone().add(f.clone().multiply(1.2));
        Block slamBlock = ground(slam);
        BlockData slamData = slamBlock.isPassable() ? Material.STONE.createBlockData() : slamBlock.getBlockData();
        w.spawnParticle(Particle.BLOCK, slam.clone().add(0, 0.2, 0), 80, 0.9, 0.2, 0.9, 0, slamData);
        w.spawnParticle(Particle.CRIT, slam.clone().add(0, 0.6, 0), 40, 0.5, 0.4, 0.5, 0.5);
        for (int k = 0; k < 3; k++) {
            final double r = 1.2 + k * 1.1;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> Util.ring(slam.clone().add(0, 0.15, 0), r, (int) (r * 14), q -> {
                w.spawnParticle(Particle.DUST_COLOR_TRANSITION, q, 1, 0, 0, 0, 0, STEEL);
                w.spawnParticle(Particle.DUST_PLUME, q, 1, 0, 0, 0, 0.01);
            }), k * 2L);
        }

        final Set<UUID> hit = new HashSet<>();
        final List<BlockDisplay> rocks = new ArrayList<>();
        final List<Vector> vel = new ArrayList<>();
        new BukkitRunnable() {   // трещина бежит вперёд
            double d = 1.5;
            double y = o.getY();
            @Override public void run() {
                Location pos = new Location(w, o.getX() + f.getX() * d, y + 1.5, o.getZ() + f.getZ() * d);
                Block g = ground(pos);
                boolean wall = g.getY() + 1 > y + 1.6 || g.isPassable();
                if (!wall) {
                    y = g.getY() + 1;
                    Location top = new Location(w, pos.getX(), y, pos.getZ());
                    crack(top, g);
                    for (LivingEntity le : Util.targets(top.clone().add(0, 1, 0), 2.4, p)) {
                        if (!hit.add(le.getUniqueId())) continue;
                        Util.hurt(le, p, damage);
                        le.setVelocity(new Vector(f.getX() * 0.35, 0.8, f.getZ() * 0.35));
                        le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, false, true, true));
                        Location lc = le.getBoundingBox().getCenter().toLocation(w);
                        w.spawnParticle(Particle.BLOCK, lc, 30, 0.3, 0.5, 0.3, 0, g.getBlockData());
                        w.spawnParticle(Particle.CRIT, lc, 20, 0.3, 0.5, 0.3, 0.4);
                    }
                }
                d += 1.5;
                if (wall || d > range) {
                    if (!wall) eruption(new Location(w, pos.getX(), y, pos.getZ()), g);
                    cancel();
                }
            }

            private void crack(Location top, Block g) {
                BlockData data = g.getBlockData();
                w.spawnParticle(Particle.BLOCK, top.clone().add(0, 0.2, 0), 45, 0.5, 0.25, 0.5, 0, data);
                w.spawnParticle(Particle.DUST_PLUME, top.clone().add(0, 0.2, 0), 8, 0.4, 0.1, 0.4, 0.03);
                w.spawnParticle(Particle.LAVA, top, 2, 0.3, 0.05, 0.3, 0);
                w.spawnParticle(Particle.DUST_COLOR_TRANSITION, top.clone().add(0, 0.3, 0), 12, 0.5, 0.3, 0.5, 0, STEEL);
                Util.sound(top, Sound.BLOCK_DEEPSLATE_BREAK, 1.2f, 0.6f);
                Util.sound(top, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.35f, 0.6f);
                // каменные шипы по краям трещины
                for (int s = -1; s <= 1; s += 2) {
                    double off = 0.7 + rnd.nextDouble() * 0.5;
                    spike(top.clone().add(side.clone().multiply(s * off)).add(f.clone().multiply(rnd.nextDouble() - 0.5)),
                            data, s, 1.0f + rnd.nextFloat() * 1.3f);
                }
                // обломок, вылетающий из трещины
                BlockDisplay rock = block(top.clone().add(0, 0.3, 0), data, 0.4f);
                rocks.add(rock);
                vel.add(new Vector((rnd.nextDouble() - 0.5) * 0.4, 0.55 + rnd.nextDouble() * 0.35, (rnd.nextDouble() - 0.5) * 0.4));
            }
        }.runTaskTimer(plugin, 3L, 1L);

        new BukkitRunnable() {   // полёт обломков
            int t;
            @Override public void run() {
                for (int i = 0; i < rocks.size(); i++) {
                    BlockDisplay b = rocks.get(i);
                    if (!b.isValid()) continue;
                    Vector v = vel.get(i);
                    v.setY(v.getY() - 0.08);
                    b.teleport(b.getLocation().add(v));
                }
                if (++t >= 34) {
                    for (BlockDisplay b : rocks) { plugin.untrack(b); if (b.isValid()) b.remove(); }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 4L, 1L);
    }

    /** Финальный выброс в конце трещины. */
    private void eruption(Location c, Block g) {
        World w = c.getWorld();
        BlockData data = g.getBlockData();
        Util.sound(c, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.6f);
        Util.sound(c, Sound.ITEM_MACE_SMASH_GROUND, 1.5f, 0.6f);
        w.spawnParticle(Particle.EXPLOSION, c.clone().add(0, 0.6, 0), 2, 0.4, 0.2, 0.4, 0);
        w.spawnParticle(Particle.BLOCK, c.clone().add(0, 0.4, 0), 150, 1.2, 0.5, 1.2, 0, data);
        w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, c.clone().add(0, 0.4, 0), 12, 0.6, 0.2, 0.6, 0.03);
        Util.ring(c.clone().add(0, 0.15, 0), 2.6, 40, q -> w.spawnParticle(Particle.DUST_COLOR_TRANSITION, q, 1, 0, 0, 0, 0, STEEL));
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            Location l = c.clone().add(Math.cos(a) * 1.4, 0, Math.sin(a) * 1.4);
            spike(l, data, i % 2 == 0 ? 1 : -1, 1.8f + rnd.nextFloat() * 1.2f);
        }
    }

    /** Верхний твёрдый блок под точкой (ищем до 5 блоков вниз). */
    private Block ground(Location l) {
        Block b = l.getBlock();
        for (int i = 0; i < 6 && b.isPassable(); i++) b = b.getRelative(0, -1, 0);
        return b;
    }

    private BlockDisplay block(Location l, BlockData data, float size) {
        Location at = l.clone();
        at.setYaw(rnd.nextFloat() * 360f);
        at.setPitch(0);
        BlockDisplay bd = l.getWorld().spawn(at, BlockDisplay.class, d -> {
            d.setBlock(data.getMaterial().isBlock() && data.getMaterial() != Material.AIR ? data : Material.STONE.createBlockData());
            d.setTeleportDuration(1);
            d.setViewRange(4f);
            d.setTransformation(spikeTf(size, size, 0f));
        });
        plugin.track(bd);
        return bd;
    }

    private static Transformation spikeTf(float width, float height, float tilt) {
        return new Transformation(new Vector3f(-width / 2f, 0f, -width / 2f), new Quaternionf().rotateZ(tilt),
                new Vector3f(width, height, width), new Quaternionf());
    }

    /** Шип вырастает из земли, стоит полторы секунды и уходит обратно. */
    private void spike(Location l, BlockData data, int sideSign, float height) {
        final float tilt = sideSign * (0.2f + rnd.nextFloat() * 0.3f);
        final BlockDisplay bd = block(l.clone().add(0, -0.05, 0), data, 0.45f);
        bd.setTransformation(spikeTf(0.45f, 0.05f, tilt));
        var sch = plugin.getServer().getScheduler();
        sch.runTaskLater(plugin, () -> {
            if (!bd.isValid()) return;
            bd.setInterpolationDelay(0);
            bd.setInterpolationDuration(4);
            bd.setTransformation(spikeTf(0.45f, height, tilt));
        }, 2L);
        sch.runTaskLater(plugin, () -> {
            if (!bd.isValid()) return;
            bd.setInterpolationDelay(0);
            bd.setInterpolationDuration(8);
            bd.setTransformation(spikeTf(0.45f, 0.02f, tilt));
        }, 30L);
        sch.runTaskLater(plugin, () -> { plugin.untrack(bd); if (bd.isValid()) bd.remove(); }, 40L);
    }
}
