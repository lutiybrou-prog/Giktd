package ru.dslegs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class Menu implements InventoryHolder {
    /** Сетка 5x3 по центру; при большем числе оружий занимаются боковые колонки. */
    private static final int[] POS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33,
                                      10, 16, 19, 25, 28, 34};
    private static final Material[] FRAME = {Material.ORANGE_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE};
    private final Inventory inv;
    public final Map<Integer, LegendaryType> slots = new HashMap<>();

    public Menu() {
        inv = Bukkit.createInventory(this, 54, Util.mm("<gradient:#ffd54f:#ff7043:#ff8fc7><bold>✦ Легендарное оружие ✦</bold></gradient>"));
        for (int i = 0; i < inv.getSize(); i++) {
            int row = i / 9, col = i % 9;
            boolean edge = row == 0 || row == 5;
            boolean corner = edge && (col == 0 || col == 8);
            Material m = corner ? Material.RED_STAINED_GLASS_PANE
                    : edge ? FRAME[col % 2]
                    : (col == 0 || col == 8) ? Material.GRAY_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE;
            inv.setItem(i, named(m, " "));
        }

        LegendaryType[] all = LegendaryType.values();
        ItemStack head = named(Material.NETHER_STAR, "<gradient:#ffd54f:#ffffff:#ffd54f><bold>✦ Арсенал легенд ✦</bold></gradient>",
                "<gray>Оружий в коллекции: <white>" + all.length,
                "",
                "<gray>Каждое оружие обладает своей способностью,",
                "<gray>аурой и эффектами.");
        inv.setItem(4, head);
        inv.setItem(49, named(Material.BOOK, "<yellow><bold>Как пользоваться",
                "<green>▶ ЛКМ <gray>по оружию - взять в инвентарь",
                "<gray>Перезарядка и подсказки показываются",
                "<gray>над хотбаром, пока оружие в руке.",
                "",
                "<dark_gray>/dslegs give <оружие> [игрок]"));

        for (int i = 0; i < all.length && i < POS.length; i++) {
            ItemStack it = Items.create(all[i]);
            ItemMeta m = it.getItemMeta();
            List<Component> lore = new ArrayList<>(m.lore());
            lore.add(Util.item(""));
            lore.add(Util.item("<green>▶ ЛКМ <gray>- взять"));
            m.lore(lore);
            it.setItemMeta(m);
            inv.setItem(POS[i], it);
            slots.put(POS[i], all[i]);
        }
    }

    private static ItemStack named(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(Util.item(name));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(Util.item(s));
            m.lore(l);
        }
        it.setItemMeta(m);
        return it;
    }

    @Override public Inventory getInventory() { return inv; }
}
