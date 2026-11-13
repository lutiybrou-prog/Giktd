package ru.dslegs;

import java.util.List;
import org.bukkit.Material;

public enum LegendaryType {
    EXCALIBUR("excalibur", "Excalibur", "Меч Паладина", "#FFD54F", Material.NETHERITE_SWORD, 20000000, 75,
            "ПКМ", "Свет паладина",
            List.of("Жёлтый свет падает на всех в радиусе <white>10</white> блоков:",
                    "каждый теряет <white>1 сердце</white> на <white>20</white> с,",
                    "а вы получаете эти сердца на то же время.")),
    LIGHT_MACE("light_mace", "Light Mace", "Лёгкая булава", "#8FE3FF", Material.MACE, 20000001, 20,
            "ПКМ", "Порыв бури",
            List.of("Подбрасывает вас на <white>15</white> блоков и делает",
                    "рывок на <white>3</white> блока вперёд.",
                    "Падение после рывка не наносит урона.")),
    VAMPIRE_BLADE("vampire_blade", "Vampire Blade", "Клинок вампира", "#E0192B", Material.NETHERITE_SWORD, 20000002, 60,
            "ПКМ", "Кровавая жатва",
            List.of("<white>Полный удар</white> крадёт <white>½ сердца</white> макс. здоровья врага",
                    "и добавляет его вам. Ещё <white>½ сердца</white> урона лечит вас.",
                    "ПКМ: кровь вытягивается из всех в радиусе <white>3</white> блоков,",
                    "<white>3 сердца</white> урона (сквозь броню) и оглушение <white>1.5</white> с.")),
    ARTEMIS_BOW("artemis_bow", "Artemis Bow", "Лук смерти", "#B65CFF", Material.BOW, 20000003, 0,
            "Пассивно", "Самонаведение",
            List.of("Стрелы сами летят к ближайшей цели.",
                    "Наблюдателей (спектаторов) игнорируют.",
                    "Сила <white>V</white> и все чары.")),
    SONIC_CROSSBOW("sonic_crossbow", "Sonic Crossbow", "Арбалет ветра", "#E8F7FF", Material.CROSSBOW, 20000004, 45,
            "Выстрел", "Звуковой удар",
            List.of("Выстрел - это способность: <white>3</white> широких луча.",
                    "Лучи затягивают цели, наносят <white>3 сердца</white>",
                    "и подбрасывают на <white>5</white> блоков.")),
    DARK_KATANA("dark_katana", "Dark Katana", "Катана Тьмы", "#7A1FD1", Material.NETHERITE_SWORD, 20000005, 60,
            "ПКМ", "Три разреза тьмы",
            List.of("Три огромных горизонтальных разреза.",
                    "Каждый наносит <white>1 сердце</white> урона.")),
    ICE_SWORD("ice_sword", "Ice Sword", "Ледянка", "#6EC6FF", Material.NETHERITE_SWORD, 20000006, 60,
            "ПКМ", "Ледяной взрыв",
            List.of("Ледяной взрыв в точке прицела с синим грибом,",
                    "как от ядовитой бомбы. Туман держится <white>6</white> с:",
                    "все внутри (радиус <white>5</white>) получают <white>Замедление II</white>.")),
    SOUL_FORTRESS("soul_fortress", "Soul Fortress", "Крепость душ", "#00E5FF", Material.NETHERITE_AXE, 20000007, 60,
            "ПКМ", "Купол душ",
            List.of("Сфера на <white>10</white> секунд: никто не выйдет наружу,",
                    "и никто снаружи не войдёт. Блоки не ломает.")),
    FROSTMORN("frostmorn", "Frostmorn", "Фростморн", "#8EA9FF", Material.NETHERITE_SWORD, 20000008, 60,
            "ПКМ", "Ледяная буря",
            List.of("Буря в точке прицела (до <white>20</white> блоков) на <white>10</white> с.",
                    "Радиус <white>7</white>: <white>Замедление II</white>.",
                    "Притягивает игроков к центру в радиусе <white>10</white> блоков.")),
    MONARCH_STAFF("monarch_staff", "Monarch's Staff", "Посох монарха", "#FF6A2B", Material.STICK, 20000009, 0,
            "ЛКМ / ПКМ", "Подброс · Тени",
            List.of("<white>ЛКМ</white> - подбрасывает всех в радиусе <white>7</white> на <white>3</white> блока.",
                    "<white>ПКМ</white> - <white>5</white> зомби, атакуют всех кроме вас.")),
    SHADOW_REAPER("shadow_reaper", "Shadow Reaper", "Теневой Серп", "#8C7AE6", Material.NETHERITE_SWORD, 20000010, 35,
            "ПКМ", "Теневой шаг",
            List.of("Рывок на <white>8</white> блоков вперёд (не сквозь блоки).",
                    "Даёт <white>Скорость II</white> на <white>3</white> с.",
                    "Пассивно: крит накладывает <white>Слепоту I</white> на <white>1.5</white> с.")),
    SANGUINE_ART("sanguine_art", "Sanguine Art", "Кровавое искусство", "#B3001B", Material.NETHERITE_SWORD, 20000011, 55,
            "ПКМ", "Кровавый ритуал",
            List.of("На <white>15</white> с у вас всего <white>7 сердец</white> (-3), но вы получаете",
                    "<white>Силу I</white> и <white>Скорость I</white>. С вас стекает кровь.")),
    EXPLOSIVE_BOW("explosive_bow", "Explosive Bow", "Взрывной лук", "#FF8A00", Material.BOW, 20000012, 15,
            "Выстрел", "Динамит",
            List.of("Раз в <white>15</white> с выстрел становится динамитом:",
                    "урон самой стрелы в <white>3</white> раза меньше,",
                    "а взрыв - полноценный, радиус взрыва <white>x3</white>.")),
    SAKURA_KATANA("sakura_katana", "Sakura Katana", "Сакура Катана", "#FF8FC7", Material.NETHERITE_SWORD, 20000013, 0,
            "ПКМ / Shift+ЛКМ", "Рывок лепестков · Цветение",
            List.of("<white>ПКМ</white> - рывок на <white>8</white> блоков: <white>3 сердца</white> урона всем на пути,",
                    "за вами остаётся след из лепестков сакуры.",
                    "<white>Shift+ЛКМ</white> - взрыв лепестков в радиусе <white>6</white> блоков:",
                    "<white>4 сердца</white> урона, оглушение <white>2</white> с и <white>Слепота II</white> на <white>3</white> с.")),
    WARRIOR_GREATSWORD("warrior_greatsword", "Warrior Greatsword", "Меч Воина", "#C9CED6", Material.NETHERITE_SWORD, 20000014, 30,
            "ПКМ", "Раскол земли",
            List.of("Удар мечом в землю: трещина с каменными шипами",
                    "бежит на <white>12</white> блоков вперёд. <white>4 сердца</white> урона,",
                    "подброс и <white>Замедление II</white> на <white>3</white> с.",
                    "Боевой клич: вы получаете <white>Сопротивление I</white> на <white>5</white> с."));

    public final String key, title, ru, color, trigger, ability;
    public final Material material;
    public final int cmd, cooldown;
    public final List<String> desc;

    LegendaryType(String key, String title, String ru, String color, Material material, int cmd, int cooldown,
                  String trigger, String ability, List<String> desc) {
        this.key = key; this.title = title; this.ru = ru; this.color = color; this.material = material;
        this.cmd = cmd; this.cooldown = cooldown; this.trigger = trigger; this.ability = ability; this.desc = desc;
    }

    /** true - способность срабатывает по ПКМ */
    public boolean hasRmb() {
        return this != ARTEMIS_BOW && this != SONIC_CROSSBOW && this != MONARCH_STAFF && this != EXPLOSIVE_BOW;
    }

    /** Модель предмета (компонент item_model) для оружия с 3D-моделью, иначе null. */
    public String model() {
        return switch (this) {
            case SAKURA_KATANA -> "sakura:sakura_katana";
            case WARRIOR_GREATSWORD -> "warrior:warrior_greatsword";
            default -> null;
        };
    }

    public static LegendaryType byKey(String k) {
        for (LegendaryType t : values()) if (t.key.equalsIgnoreCase(k)) return t;
        return null;
    }
}
