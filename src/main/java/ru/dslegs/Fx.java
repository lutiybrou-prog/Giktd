package ru.dslegs;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/** Общие визуальные эффекты: аура оружия в руке, экипировка, активация, готовность, попадания. */
public final class Fx {
    private Fx() {}

    private static DSLegsPlugin P;
    private static final Map<LegendaryType, Particle.DustOptions> DUST = new EnumMap<>(LegendaryType.class);
    private static final Map<LegendaryType, Particle.DustTransition> FADE = new EnumMap<>(LegendaryType.class);

    public static void init(DSLegsPlugin plugin) {
        P = plugin;
        for (LegendaryType t : LegendaryType.values()) {
            Color c = color(t.color);
            DUST.put(t, new Particle.DustOptions(c, 1.1f));
            FADE.put(t, new Particle.DustTransition(c, Color.WHITE, 1.4f));
        }
    }

    public static Color color(String hex) {
        try { return Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16)); }
        catch (NumberFormatException ex) { return Color.WHITE; }
    }

    private static boolean on(String key) { return P.getConfig().getBoolean("visuals." + key, true); }

    /** Характерная частица оружия (все без дополнительных данных). */
    public static Particle accent(LegendaryType t) {
        return switch (t) {
            case EXCALIBUR -> Particle.END_ROD;
            case LIGHT_MACE, SONIC_CROSSBOW -> Particle.CLOUD;
            case VAMPIRE_BLADE, SANGUINE_ART -> Particle.CRIMSON_SPORE;
            case ARTEMIS_BOW -> Particle.WITCH;
            case DARK_KATANA -> Particle.REVERSE_PORTAL;
            case ICE_SWORD, FROSTMORN -> Particle.SNOWFLAKE;
            case SOUL_FORTRESS -> Particle.SOUL_FIRE_FLAME;
            case MONARCH_STAFF -> Particle.FLAME;
            case SHADOW_REAPER -> Particle.SMOKE;
            case EXPLOSIVE_BOW -> Particle.SMALL_FLAME;
            case SAKURA_KATANA -> Particle.CHERRY_LEAVES;
            case WARRIOR_GREATSWORD -> Particle.CRIT;
        };
    }

    private static boolean hidden(Player p) {
        return p.getGameMode() == GameMode.SPECTATOR || p.isDead() || p.hasPotionEffect(PotionEffectType.INVISIBILITY);
    }

    /** Аура: редкие частицы вокруг владельца, пока легендарка в руке. */
    public static void start() {
        new BukkitRunnable() {
            int tick;
            @Override public void run() {
                tick++;
                if (!on("aura")) return;
                ThreadLocalRandom r = ThreadLocalRandom.current();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    LegendaryType t = Items.of(p.getInventory().getItemInMainHand());
                    if (t == null || hidden(p)) continue;
                    World w = p.getWorld();
                    Location l = p.getLocation();
                    // две точки медленно кружат у ног
                    for (int k = 0; k < 2; k++) {
                        double a = tick * 0.35 + k * Math.PI;
                        w.spawnParticle(Particle.DUST, l.getX() + Math.cos(a) * 0.75, l.getY() + 0.12,
                                l.getZ() + Math.sin(a) * 0.75, 1, 0, 0, 0, 0, DUST.get(t));
                    }
                    if (tick % 2 == 0) {
                        double a = r.nextDouble(Math.PI * 2), d = 0.4 + r.nextDouble(0.5);
                        w.spawnParticle(accent(t), l.getX() + Math.cos(a) * d, l.getY() + 0.3 + r.nextDouble(1.5),
                                l.getZ() + Math.sin(a) * d, 1, 0, 0, 0, 0);
                    }
                }
            }
        }.runTaskTimer(P, 20L, 4L);
    }

    /** Двойная спираль вверх по игроку - оружие взято в руку. */
    public static void equip(Player p, LegendaryType t) {
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.4f);
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.6f, 1.3f);
        if (!on("equip") || hidden(p)) return;
        new BukkitRunnable() {
            int i;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                Location l = p.getLocation();
                World w = l.getWorld();
                for (int s = 0; s < 3; s++, i++) {
                    double y = i * 0.075, a = i * 0.55;
                    for (int k = 0; k < 2; k++) {
                        double b = a + k * Math.PI;
                        w.spawnParticle(Particle.DUST_COLOR_TRANSITION, l.getX() + Math.cos(b) * 0.7, l.getY() + y,
                                l.getZ() + Math.sin(b) * 0.7, 1, 0, 0, 0, 0, FADE.get(t));
                    }
                }
                if (i >= 27) {
                    w.spawnParticle(accent(t), l.clone().add(0, 2.1, 0), 12, 0.25, 0.1, 0.25, 0.02);
                    cancel();
                }
            }
        }.runTaskTimer(P, 0L, 1L);
    }

    /** Расходящееся кольцо под ногами + всплеск - активация способности. */
    public static void cast(Player p, LegendaryType t) {
        if (!on("cast") || hidden(p)) return;
        final Location c = p.getLocation().add(0, 0.15, 0);
        final World w = c.getWorld();
        w.spawnParticle(accent(t), c.clone().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0.06);
        new BukkitRunnable() {
            int i;
            @Override public void run() {
                double r = 0.8 + i * 0.6;
                Util.ring(c, r, (int) (14 + r * 9), q ->
                        w.spawnParticle(Particle.DUST_COLOR_TRANSITION, q, 1, 0, 0, 0, 0, FADE.get(t)));
                if (++i >= 5) cancel();
            }
        }.runTaskTimer(P, 0L, 1L);
    }

    /** Сходящееся кольцо - способность снова готова. */
    public static void ready(Player p, LegendaryType t) {
        if (!on("ready") || hidden(p)) return;
        final World w = p.getWorld();
        new BukkitRunnable() {
            int i;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                Location c = p.getLocation().add(0, 0.2 + i * 0.3, 0);
                Util.ring(c, 1.6 - i * 0.3, 22, q ->
                        w.spawnParticle(Particle.DUST_COLOR_TRANSITION, q, 1, 0, 0, 0, 0, FADE.get(t)));
                if (++i >= 5) {
                    w.spawnParticle(accent(t), c.add(0, 0.4, 0), 10, 0.15, 0.15, 0.15, 0.03);
                    cancel();
                }
            }
        }.runTaskTimer(P, 0L, 2L);
    }

    /** Искры цвета оружия на цели при ударе в ближнем бою. */
    public static void hit(LivingEntity victim, LegendaryType t, boolean crit) {
        if (!on("hit-effects")) return;
        Location c = victim.getBoundingBox().getCenter().toLocation(victim.getWorld());
        World w = c.getWorld();
        w.spawnParticle(Particle.DUST_COLOR_TRANSITION, c, crit ? 22 : 10, 0.3, 0.45, 0.3, 0, FADE.get(t));
        w.spawnParticle(accent(t), c, crit ? 12 : 5, 0.25, 0.4, 0.25, 0.04);
    }

    public static Particle.DustOptions dust(LegendaryType t) { return DUST.get(t); }
    public static Particle.DustTransition fade(LegendaryType t) { return FADE.get(t); }
}
