package ru.dslegs;

import java.lang.reflect.Method;
import java.util.List;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.inventory.meta.ItemMeta;

/** Различия API между Paper 1.21.1 и 1.21.4+: имена атрибутов, компоненты custom_model_data и item_model. */
public final class Compat {
    private Compat() {}

    public static final Attribute MAX_HEALTH = attr("max_health", "generic.max_health");
    public static final Attribute ATTACK_DAMAGE = attr("attack_damage", "generic.attack_damage");
    public static final Attribute MOVEMENT_SPEED = attr("movement_speed", "generic.movement_speed");
    public static final Attribute FOLLOW_RANGE = attr("follow_range", "generic.follow_range");
    public static final Attribute SPAWN_REINFORCEMENTS = attr("spawn_reinforcements", "zombie.spawn_reinforcements");

    private static Method getCmd, setCmd, setFloats, setStrings, setFlags, setItemModel;

    static {
        try {
            Class<?> c = Class.forName("org.bukkit.inventory.meta.components.CustomModelDataComponent");
            getCmd = ItemMeta.class.getMethod("getCustomModelDataComponent");
            setCmd = ItemMeta.class.getMethod("setCustomModelDataComponent", c);
            setFloats = c.getMethod("setFloats", List.class);
            setStrings = c.getMethod("setStrings", List.class);
            setFlags = c.getMethod("setFlags", List.class);
        } catch (ReflectiveOperationException | LinkageError ex) {
            getCmd = null;   // до 1.21.4 - только числовой custom_model_data
        }
        try {
            setItemModel = ItemMeta.class.getMethod("setItemModel", NamespacedKey.class);
        } catch (ReflectiveOperationException | LinkageError ex) {
            setItemModel = null;   // компонент item_model появился в 1.21.2
        }
    }

    private static Attribute attr(String modern, String legacy) {
        Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(modern));
        return a != null ? a : Registry.ATTRIBUTE.get(NamespacedKey.minecraft(legacy));
    }

    public static boolean hasItemModel() { return setItemModel != null; }

    /** 1.21.4+: float + строки (точный ID и "dslegs:ключ") + флаг; старее - обычное число. */
    public static void customModelData(ItemMeta m, int id, String key) {
        if (getCmd != null) {
            try {
                Object cmd = getCmd.invoke(m);
                setFloats.invoke(cmd, List.of((float) id));
                setStrings.invoke(cmd, List.of(String.valueOf(id), "dslegs:" + key));
                setFlags.invoke(cmd, List.of(true));
                setCmd.invoke(m, cmd);
                return;
            } catch (ReflectiveOperationException ignored) { }
        }
        m.setCustomModelData(id);
    }

    public static void itemModel(ItemMeta m, NamespacedKey model) {
        if (setItemModel == null) return;
        try { setItemModel.invoke(m, model); } catch (ReflectiveOperationException ignored) { }
    }
}
