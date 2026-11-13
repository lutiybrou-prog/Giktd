package ru.dslegs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class Util {
    private Util() {}

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public static Component mm(String s) { return MM.deserialize(s); }

    /** Для предметов: без курсива */
    public static Component item(String s) { return MM.deserialize(s).decoration(TextDecoration.ITALIC, false); }

    public static String sec(long ms) { return String.format(Locale.US, "%.1f", ms / 1000.0); }

    public static void sound(Location l, Sound s, float vol, float pitch) {
        l.getWorld().playSound(l, s, SoundCategory.PLAYERS, vol, pitch);
    }

    /** Подходящая цель: живая, не стойка, не спектатор. */
    public static boolean valid(LivingEntity le, Entity exclude) {
        if (le == exclude || le instanceof ArmorStand || le.isDead() || !le.isValid()) return false;
        return !(le instanceof Player p && p.getGameMode() == GameMode.SPECTATOR);
    }

    public static List<LivingEntity> targets(Location c, double r, Entity exclude) {
        List<LivingEntity> out = new ArrayList<>();
        Vector cv = c.toVector();
        for (Entity e : c.getWorld().getNearbyEntities(c, r + 1, r + 1, r + 1)) {
            if (!(e instanceof LivingEntity le) || !valid(le, exclude)) continue;
            if (le.getBoundingBox().getCenter().distanceSquared(cv) > r * r) continue;
            out.add(le);
        }
        return out;
    }

    /** true, пока наносится урон способности - чтобы он не считался обычным ударом. */
    public static boolean abilityDamage;

    /** Урон способностей: точное число, броню игнорирует, автор - игрок. */
    public static void hurt(LivingEntity victim, Player src, double amount) {
        victim.setNoDamageTicks(0);
        DamageSource ds = DamageSource.builder(DamageType.MAGIC)
                .withCausingEntity(src).withDirectEntity(src).build();
        boolean prev = abilityDamage;
        abilityDamage = true;
        try { victim.damage(amount, ds); } finally { abilityDamage = prev; }
    }

    /** Начальная вертикальная скорость, чтобы подняться примерно на N блоков (с учётом сопротивления воздуха). */
    public static double velocityForHeight(double blocks) {
        for (double v = 0.3; v < 4.0; v += 0.01) {
            double y = 0, vy = v;
            while (vy > 0) { y += vy; vy = (vy - 0.08) * 0.98; }
            if (y >= blocks) return v;
        }
        return 3.0;
    }

    public static void ring(Location c, double r, int pts, Consumer<Location> f) {
        for (int i = 0; i < pts; i++) {
            double a = 2 * Math.PI * i / pts;
            f.accept(new Location(c.getWorld(), c.getX() + Math.cos(a) * r, c.getY(), c.getZ() + Math.sin(a) * r));
        }
    }

    /** Кольцо, перпендикулярное оси axis. */
    public static void ringAround(Vector center, Vector axis, double radius, int pts, Consumer<Vector> f) {
        Vector ax = axis.clone().normalize();
        Vector u = ax.clone().crossProduct(new Vector(0, 1, 0));
        if (u.lengthSquared() < 1e-4) u = new Vector(1, 0, 0);
        u.normalize();
        Vector v = ax.clone().crossProduct(u).normalize();
        for (int i = 0; i < pts; i++) {
            double a = 2 * Math.PI * i / pts;
            f.accept(center.clone().add(u.clone().multiply(Math.cos(a) * radius)).add(v.clone().multiply(Math.sin(a) * radius)));
        }
    }

    /** Магический круг на земле: два кольца + пентаграмма. */
    public static void sigil(Location c, double r, double rot, Consumer<Location> f) {
        ring(c, r, Math.max(30, (int) (r * 10)), f);
        double rr = r * 0.85;
        ring(c, rr, Math.max(24, (int) (r * 8)), f);
        for (int k = 0; k < 5; k++) {
            double a1 = rot + k * 2 * Math.PI / 5, a2 = rot + ((k + 2) % 5) * 2 * Math.PI / 5;
            double x1 = c.getX() + Math.cos(a1) * rr, z1 = c.getZ() + Math.sin(a1) * rr;
            double x2 = c.getX() + Math.cos(a2) * rr, z2 = c.getZ() + Math.sin(a2) * rr;
            int n = Math.max(6, (int) (Math.hypot(x2 - x1, z2 - z1) * 3));
            for (int i = 0; i <= n; i++) {
                double t = (double) i / n;
                f.accept(new Location(c.getWorld(), x1 + (x2 - x1) * t, c.getY(), z1 + (z2 - z1) * t));
            }
        }
    }

    /** Нет ли блоков между точкой и целью. */
    public static boolean clear(Location from, LivingEntity target) {
        Vector to = target.getBoundingBox().getCenter().subtract(from.toVector());
        double d = to.length();
        if (d < 0.5) return true;
        return from.getWorld().rayTraceBlocks(from, to.normalize(), d, FluidCollisionMode.NEVER, true) == null;
    }
}
