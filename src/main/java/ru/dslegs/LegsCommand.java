package ru.dslegs;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class LegsCommand implements TabExecutor {
    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 0) {
            if (!(s instanceof Player p)) { s.sendMessage("Только для игроков."); return true; }
            p.openInventory(new Menu().getInventory());
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
            return true;
        }
        if (a[0].equalsIgnoreCase("give") && s.hasPermission("dslegs.admin")) {
            if (a.length < 2) { s.sendMessage(Util.mm("<red>/dslegs give <оружие> [игрок]")); return true; }
            LegendaryType t = LegendaryType.byKey(a[1]);
            if (t == null) { s.sendMessage(Util.mm("<red>Нет такого оружия.")); return true; }
            Player target = a.length >= 3 ? Bukkit.getPlayer(a[2]) : (s instanceof Player pl ? pl : null);
            if (target == null) { s.sendMessage(Util.mm("<red>Игрок не найден.")); return true; }
            target.getInventory().addItem(Items.create(t)).values()
                    .forEach(i -> target.getWorld().dropItemNaturally(target.getLocation(), i));
            s.sendMessage(Util.mm("<green>Выдано: <" + t.color + ">" + t.title));
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (!s.hasPermission("dslegs.admin")) return out;
        if (a.length == 1) out.add("give");
        else if (a.length == 2 && a[0].equalsIgnoreCase("give"))
            for (LegendaryType t : LegendaryType.values()) out.add(t.key);
        else if (a.length == 3 && a[0].equalsIgnoreCase("give"))
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        return out;
    }
}
