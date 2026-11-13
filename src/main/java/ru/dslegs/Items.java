package ru.dslegs;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;

public final class Items {
    private Items() {}

    private static NamespacedKey KEY;

    private static DSLegsPlugin P;

    public static void init(DSLegsPlugin plugin) { P = plugin; KEY = new NamespacedKey(plugin, "legend"); }

    public static LegendaryType of(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        String k = it.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        return k == null ? null : LegendaryType.byKey(k);
    }

    public static ItemStack create(LegendaryType t) {
        ItemStack it = new ItemStack(t.material);
        ItemMeta m = it.getItemMeta();
        String c = "<" + t.color + ">";

        m.displayName(Util.item("<gradient:" + t.color + ":#ffffff:" + t.color + "><bold>✦ " + t.title + " ✦</bold></gradient>"));

        List<Component> lore = new ArrayList<>();
        lore.add(Util.item("<dark_gray>▪ " + c + "<bold>ЛЕГЕНДАРНОЕ ОРУЖИЕ</bold> <dark_gray>▪"));
        lore.add(Util.item("<gray>" + t.ru));
        lore.add(Util.item(""));
        lore.add(Util.item(c + "⚡ " + t.trigger + ": <white><bold>" + t.ability));
        for (String d : t.desc) lore.add(Util.item("<gray>" + d));
        lore.add(Util.item(""));
        if (t == LegendaryType.MONARCH_STAFF) {
            lore.add(Util.item("<dark_gray>Перезарядки: <white>ЛКМ " + P.cooldownSec("monarch_launch", 20)
                    + "с <dark_gray>· <white>ПКМ " + P.cooldownSec("monarch_zombies", 45) + "с"));
        } else if (t == LegendaryType.SAKURA_KATANA) {
            lore.add(Util.item("<dark_gray>Перезарядки: <white>ПКМ " + P.cooldownSec("sakura_dash", 12)
                    + "с <dark_gray>· <white>Shift+ЛКМ " + P.cooldownSec("sakura_bloom", 40) + "с"));
        } else if (P.cooldownSec(t) > 0) lore.add(Util.item("<dark_gray>Перезарядка: <white>" + P.cooldownSec(t) + " сек"));
        else lore.add(Util.item("<dark_gray>Перезарядка: <white>нет"));
        lore.add(Util.item(""));
        lore.add(Util.item("<gradient:" + t.color + ":#ffffff:" + t.color + "><strikethrough>          </strikethrough> ✦ ✦ ✦ <strikethrough>          </strikethrough>"));
        m.lore(lore);

        // "Сложная" custom model data: float (id) + string (ключ) + flag
        CustomModelDataComponent cmd = m.getCustomModelDataComponent();
        int id = P.getConfig().getInt("cmd-ids." + t.key, t.cmd);
        cmd.setFloats(List.of((float) id));
        // float32 не хранит нечётные числа > 16 777 216 - точный ID лежит ещё и строкой (strings[0])
        cmd.setStrings(List.of(String.valueOf(id), "dslegs:" + t.key));
        cmd.setFlags(List.of(true));
        m.setCustomModelDataComponent(cmd);

        String model = P.getConfig().getString("item-models." + t.key, t.model());
        if (model != null && !model.isBlank()) {
            NamespacedKey mk = NamespacedKey.fromString(model);
            if (mk != null) m.setItemModel(mk);
        }

        m.setUnbreakable(true);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        m.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, t.key);

        switch (t) {
            case ARTEMIS_BOW -> {
                e(m, Enchantment.POWER, 5); e(m, Enchantment.PUNCH, 2); e(m, Enchantment.FLAME, 1);
                e(m, Enchantment.INFINITY, 1); e(m, Enchantment.UNBREAKING, 3);
            }
            case SONIC_CROSSBOW -> {
                e(m, Enchantment.QUICK_CHARGE, 3); e(m, Enchantment.UNBREAKING, 3); e(m, Enchantment.MENDING, 1);
            }
            case LIGHT_MACE -> {
                e(m, Enchantment.BREACH, 3); e(m, Enchantment.WIND_BURST, 1);
                e(m, Enchantment.UNBREAKING, 3); e(m, Enchantment.MENDING, 1);
            }
            case EXPLOSIVE_BOW -> {
                e(m, Enchantment.POWER, 5); e(m, Enchantment.INFINITY, 1); e(m, Enchantment.UNBREAKING, 3);
            }
            case MONARCH_STAFF -> {
                e(m, Enchantment.SHARPNESS, 5); e(m, Enchantment.KNOCKBACK, 2);
                e(m, Enchantment.UNBREAKING, 3); e(m, Enchantment.MENDING, 1);
            }
            case SOUL_FORTRESS -> {
                e(m, Enchantment.SHARPNESS, 5); e(m, Enchantment.EFFICIENCY, 5);
                e(m, Enchantment.UNBREAKING, 3); e(m, Enchantment.MENDING, 1);
            }
            default -> {
                e(m, Enchantment.SHARPNESS, 5); e(m, Enchantment.SWEEPING_EDGE, 3);
                e(m, Enchantment.UNBREAKING, 3); e(m, Enchantment.LOOTING, 3); e(m, Enchantment.MENDING, 1);
            }
        }
        it.setItemMeta(m);
        return it;
    }

    private static void e(ItemMeta m, Enchantment en, int lvl) { m.addEnchant(en, lvl, true); }
}
