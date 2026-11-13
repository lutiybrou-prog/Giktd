package ru.dslegs;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/** Экшенбары: постоянная панель в руке + временные сообщения. */
public final class Hud {
    private final DSLegsPlugin plugin;
    private final Cooldowns cd;
    private Vampire vampire;
    private Abilities abilities;
    private final Map<UUID, Long> flashUntil = new HashMap<>();

    public Hud(DSLegsPlugin plugin, Cooldowns cd) { this.plugin = plugin; this.cd = cd; }

    public void setVampire(Vampire v) { this.vampire = v; }
    public void setAbilities(Abilities a) { this.abilities = a; }

    public void flash(Player p, String mm, long ms) {
        p.sendActionBar(Util.mm(mm));
        flashUntil.put(p.getUniqueId(), System.currentTimeMillis() + ms);
    }

    public void scheduleReady(Player p, LegendaryType t) {
        scheduleReady(p, t, t.key, plugin.cooldownSec(t), t.title);
    }

    /** Уведомление, когда перезарядка закончилась. */
    public void scheduleReady(Player p, LegendaryType t, String key, int sec, String label) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || cd.remaining(p, key) > 100) return;
            flash(p, "<gradient:" + t.color + ":#ffffff>✦ <bold>" + label + "</bold> ✦</gradient> <green>готово!", 2000);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.6f);
            if (Items.of(p.getInventory().getItemInMainHand()) == t) Fx.ready(p, t);
        }, sec * 20L + 2);
    }

    public void start() {
        new BukkitRunnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    Long fu = flashUntil.get(p.getUniqueId());
                    if (fu != null && fu > now) continue;
                    LegendaryType t = Items.of(p.getInventory().getItemInMainHand());
                    if (t == null) continue;
                    p.sendActionBar(Util.mm(line(p, t)));
                }
            }
        }.runTaskTimer(plugin, 10L, 4L);
    }

    private String bar(long rem, double totalSec, String color) {
        double total = totalSec * 1000.0;
        int filled = (int) Math.round((1 - rem / total) * 20);
        filled = Math.max(0, Math.min(20, filled));
        String done = filled == 0 ? "" : "<gradient:" + color + ":#ffffff>" + "▌".repeat(filled) + "</gradient>";
        return "<dark_gray>[" + done + "<#3a3a3a>" + "▌".repeat(20 - filled) + "<dark_gray>] <white>" + Util.sec(rem) + "с";
    }

    private String part(Player p, String label, String key, int def) {
        long rem = cd.remaining(p, key);
        if (rem <= 0) return "<green>" + label + " ✔";
        return "<gray>" + label + " <red>" + Util.sec(rem) + "с";
    }

    private String line(Player p, LegendaryType t) {
        StringBuilder sb = new StringBuilder();
        sb.append("<").append(t.color).append(">✦ <bold>").append(t.title).append("</bold> <dark_gray>» ");
        if (t == LegendaryType.MONARCH_STAFF) {
            sb.append(part(p, "ЛКМ·подброс", "monarch_launch", 20)).append(" <dark_gray>| ")
              .append(part(p, "ПКМ·зомби", "monarch_zombies", 45));
            return sb.toString();
        }
        if (t == LegendaryType.SAKURA_KATANA) {
            sb.append(part(p, "ПКМ·рывок", "sakura_dash", 12)).append(" <dark_gray>| ")
              .append(part(p, "Shift+ЛКМ·цветение", "sakura_bloom", 40));
            return sb.toString();
        }
        sb.append("<gray>").append(t.trigger).append(": <white>").append(t.ability).append(" <dark_gray>» ");
        if (t == LegendaryType.ARTEMIS_BOW) {
            sb.append("<green>◎ АКТИВНО");
        } else {
            long rem = cd.remaining(p, t);
            if (rem <= 0) sb.append("<green><bold>✔ ГОТОВО");
            else sb.append(bar(rem, plugin.cooldownSec(t), t.color));
        }
        if (t == LegendaryType.VAMPIRE_BLADE && vampire != null) {
            double h = vampire.bonusHearts(p);
            if (h > 0) sb.append(" <dark_gray>» <red>❤ +").append(String.format(Locale.US, "%.1f", h));
        }
        if (t == LegendaryType.SANGUINE_ART && abilities != null) {
            long left = abilities.sanguineLeftMs(p);
            if (left > 0) sb.append(" <dark_gray>» <red>КРОВЬ <white>").append(Util.sec(left)).append("с");
        }
        return sb.toString();
    }
}
