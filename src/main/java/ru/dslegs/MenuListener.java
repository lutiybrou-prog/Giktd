package ru.dslegs;

import java.util.Map;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

public final class MenuListener implements Listener {
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getInventory() || !e.getClick().isLeftClick()) return;
        LegendaryType t = menu.slots.get(e.getSlot());
        if (t == null) return;
        Map<Integer, ItemStack> left = p.getInventory().addItem(Items.create(t));
        left.values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
        p.sendActionBar(Util.mm("<green>✔ Вы взяли <" + t.color + "><bold>" + t.title));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }
}
