package ru.dslegs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** Крепость душ: сфера, из которой нельзя выйти и в которую нельзя войти. Блоки не ломает. */
public final class Fortress implements Listener {
    private static final class Dome {
        final UUID owner; final Location center; final double r; final int dur;
        final Set<UUID> members = new HashSet<>();
        int age; long lastSound; BukkitRunnable task;
        Dome(UUID owner, Location center, double r, int dur) { this.owner = owner; this.center = center; this.r = r; this.dur = dur; }
    }

    private final DSLegsPlugin plugin;
    private final List<Dome> domes = new ArrayList<>();

    public Fortress(DSLegsPlugin plugin) { this.plugin = plugin; }

    public void start(Player p) {
        double r = plugin.getConfig().getDouble("fortress.radius", 7.0);
        int dur = plugin.getConfig().getInt("fortress.duration-seconds", 10) * 20;
        Location c = p.getLocation().add(0, 1, 0);
        Dome d = new Dome(p.getUniqueId(), c, r, dur);
        for (LivingEntity le : Util.targets(c, r, null)) d.members.add(le.getUniqueId());
        d.members.add(p.getUniqueId());
        domes.add(d);

        Util.sound(c, Sound.BLOCK_BEACON_ACTIVATE, 2f, 0.6f);
        Util.sound(c, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.5f, 0.7f);
        Util.sound(c, Sound.BLOCK_END_PORTAL_FRAME_FILL, 2f, 0.5f);

        d.task = new BukkitRunnable() {
            @Override public void run() { tick(d); }
        };
        d.task.runTaskTimer(plugin, 0L, 1L);
    }

    private void tick(Dome d) {
        d.age++;
        Player owner = Bukkit.getPlayer(d.owner);
        if (d.age >= d.dur || owner == null || !owner.isOnline() || owner.isDead()) { end(d); return; }
        if (d.age % 2 == 0) {
            double grow = Math.min(1.0, d.age / 8.0);
            double vr = d.r * (1 - Math.pow(1 - grow, 3));
            draw(d, vr);
        }
        enforce(d);
    }

    private void draw(Dome d, double vr) {
        World w = d.center.getWorld();
        boolean ending = d.dur - d.age < 40;
        Particle.DustTransition tr = ending
                ? new Particle.DustTransition(Color.fromRGB(255, 120, 40), Color.fromRGB(255, 40, 40), 1.4f)
                : new Particle.DustTransition(Color.fromRGB(0, 229, 255), Color.fromRGB(40, 80, 255), 1.4f);
        int n = 120;
        double phase = d.age * 0.04, golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < n; i++) {
            double y = 1 - 2.0 * (i + 0.5) / n, rad = Math.sqrt(1 - y * y), th = i * golden + phase;
            double x = Math.cos(th) * rad, z = Math.sin(th) * rad;
            Location l = d.center.clone().add(x * vr, y * vr, z * vr);
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, l, 1, 0, 0, 0, 0, tr);
            if (i % 5 == 0) w.spawnParticle(Particle.SOUL_FIRE_FLAME, l, 1, 0, 0, 0, 0.005);
        }
        Util.ring(d.center.clone().add(0, -0.9, 0), vr * 0.98, 48, l -> w.spawnParticle(Particle.SOUL, l, 1, 0.1, 0.05, 0.1, 0.01));
        for (int k = 0; k < 3; k++) {   // три вращающихся кольца-руны
            double tilt = k * Math.PI / 3, ph = d.age * (0.05 + 0.02 * k);
            for (int i = 0; i < 48; i++) {
                double a = ph + 2 * Math.PI * i / 48;
                double x = Math.cos(a) * vr, z0 = Math.sin(a) * vr;
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, d.center.getX() + x, d.center.getY() + z0 * Math.sin(tilt),
                        d.center.getZ() + z0 * Math.cos(tilt), 1, 0, 0, 0, 0);
            }
        }
        for (int i = 0; i < 6; i++) {   // души поднимаются внутри
            double a = Math.random() * Math.PI * 2, rr = Math.sqrt(Math.random()) * vr * 0.9;
            w.spawnParticle(Particle.SOUL, d.center.getX() + Math.cos(a) * rr, d.center.getY() - 1 + Math.random() * 2,
                    d.center.getZ() + Math.sin(a) * rr, 1, 0, 0.1, 0, 0.04);
        }
        if (d.age % 6 == 0) {   // молнии пробегают по куполу
            for (int arc = 0; arc < 2; arc++) {
                double a1 = Math.random() * Math.PI * 2, b1 = Math.random() * Math.PI - Math.PI / 2;
                double a2 = a1 + (Math.random() - 0.5) * 1.2, b2 = b1 + (Math.random() - 0.5) * 1.2;
                for (int i = 0; i <= 10; i++) {
                    double tt = i / 10.0, aa = a1 + (a2 - a1) * tt, bb = b1 + (b2 - b1) * tt;
                    w.spawnParticle(Particle.ELECTRIC_SPARK, d.center.getX() + Math.cos(aa) * Math.cos(bb) * vr,
                            d.center.getY() + Math.sin(bb) * vr, d.center.getZ() + Math.sin(aa) * Math.cos(bb) * vr, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    private void enforce(Dome d) {
        World w = d.center.getWorld();
        Vector cv = d.center.toVector();
        double R = d.r;
        for (Entity e : w.getNearbyEntities(d.center, R + 5, R + 5, R + 5)) {
            if (e instanceof LivingEntity le && !(e instanceof ArmorStand)) {
                Vector bc = le.getBoundingBox().getCenter();
                Vector rel = bc.clone().subtract(cv);
                double dist = rel.length();
                boolean mem = d.members.contains(e.getUniqueId());
                if (mem && dist > R - 0.5) push(d, le, rel, bc, cv, R - 0.9, true);
                else if (!mem && dist < R + 0.5) push(d, le, rel, bc, cv, R + 0.9, false);
            } else if (e instanceof Projectile pr) {
                double dist = pr.getLocation().toVector().distance(cv);
                boolean shooterIn = pr.getShooter() instanceof LivingEntity ls && d.members.contains(ls.getUniqueId());
                boolean inside = dist < R;
                if (shooterIn != inside) {
                    w.spawnParticle(Particle.SOUL_FIRE_FLAME, pr.getLocation(), 8, 0.1, 0.1, 0.1, 0.02);
                    pr.remove();
                }
            }
        }
    }

    private void push(Dome d, LivingEntity le, Vector rel, Vector bc, Vector cv, double target, boolean inward) {
        if (rel.lengthSquared() < 1e-6) rel = new Vector(1, 0, 0);
        Vector dir = rel.clone().normalize();
        Vector np = cv.clone().add(dir.clone().multiply(target));
        Location loc = le.getLocation().add(np.clone().subtract(bc));
        World w = le.getWorld();
        boolean safe = loc.getBlock().isPassable() && loc.clone().add(0, 1, 0).getBlock().isPassable();
        if (safe) le.teleport(loc);
        le.setVelocity(dir.clone().multiply(inward ? -0.35 : 0.35));
        w.spawnParticle(Particle.DUST_COLOR_TRANSITION, np.toLocation(w), 6, 0.3, 0.3, 0.3, 0,
                new Particle.DustTransition(Color.fromRGB(0, 229, 255), Color.WHITE, 1.6f));
        long now = System.currentTimeMillis();
        if (now - d.lastSound > 400) {
            d.lastSound = now;
            Util.sound(np.toLocation(w), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 0.5f);
        }
    }

    private void end(Dome d) {
        if (d.task != null) d.task.cancel();
        domes.remove(d);
        World w = d.center.getWorld();
        Util.sound(d.center, Sound.BLOCK_BEACON_DEACTIVATE, 2f, 0.8f);
        Util.sound(d.center, Sound.BLOCK_GLASS_BREAK, 2f, 0.5f);
        int n = 160;
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < n; i++) {
            double y = 1 - 2.0 * (i + 0.5) / n, rad = Math.sqrt(1 - y * y), th = i * golden;
            Location l = d.center.clone().add(Math.cos(th) * rad * d.r, y * d.r, Math.sin(th) * rad * d.r);
            w.spawnParticle(Particle.SOUL, l, 1, 0.1, 0.1, 0.1, 0.05);
            if (i % 3 == 0) w.spawnParticle(Particle.END_ROD, l, 1, 0.1, 0.1, 0.1, 0.1);
        }
    }

    public void endAll() { for (Dome d : new ArrayList<>(domes)) end(d); }

    private double dist(Dome d, Location l) {
        double dx = l.getX() - d.center.getX(), dy = l.getY() + 0.9 - d.center.getY(), dz = l.getZ() - d.center.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (domes.isEmpty()) return;
        Location from = e.getFrom(), to = e.getTo();
        if (to == null) return;
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;
        UUID id = e.getPlayer().getUniqueId();
        for (Dome d : domes) {
            if (!d.center.getWorld().equals(to.getWorld())) continue;
            boolean mem = d.members.contains(id);
            double df = dist(d, from), dt = dist(d, to);
            if (mem && dt > d.r - 0.4 && dt > df) { e.setCancelled(true); return; }
            if (!mem && dt < d.r + 0.4 && dt < df) { e.setCancelled(true); return; }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (domes.isEmpty()) return;
        Location from = e.getFrom(), to = e.getTo();
        if (to == null) return;
        UUID id = e.getPlayer().getUniqueId();
        for (Dome d : domes) {
            World dw = d.center.getWorld();
            boolean mem = d.members.contains(id);
            boolean inFrom = dw.equals(from.getWorld()) && dist(d, from) < d.r;
            boolean inTo = dw.equals(to.getWorld()) && dist(d, to) < d.r;
            if (mem && inFrom && !inTo) { e.setCancelled(true); return; }
            if (!mem && !inFrom && inTo) { e.setCancelled(true); return; }
        }
    }
}
