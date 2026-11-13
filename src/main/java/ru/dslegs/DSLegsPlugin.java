package ru.dslegs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class DSLegsPlugin extends JavaPlugin {
    private final Set<Entity> tracked = new HashSet<>();
    private Fortress fortress;
    private Vampire vampire;
    private Stun stun;
    private Minions minions;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Items.init(this);
        Fx.init(this);

        Cooldowns cd = new Cooldowns(this);
        Hud hud = new Hud(this, cd);
        stun = new Stun(this);
        vampire = new Vampire(this);
        hud.setVampire(vampire);
        fortress = new Fortress(this);
        minions = new Minions(this);
        Abilities ab = new Abilities(this, stun, fortress, hud, minions);
        hud.setAbilities(ab);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(stun, this);
        pm.registerEvents(vampire, this);
        pm.registerEvents(fortress, this);
        pm.registerEvents(minions, this);
        pm.registerEvents(new WeaponListener(this, cd, hud, ab, stun, new Blades(this, stun, hud)), this);
        pm.registerEvents(new MenuListener(), this);

        LegsCommand cmd = new LegsCommand();
        getCommand("dslegs").setExecutor(cmd);
        getCommand("dslegs").setTabCompleter(cmd);

        if (!Compat.hasItemModel())
            getLogger().warning("Сервер старше 1.21.2: компонент item_model недоступен, 3D-модели Sakura Katana "
                    + "и Warrior Greatsword выбираются только по custom_model_data (см. README).");
        for (LegendaryType t : LegendaryType.values()) {   // ранняя проверка: предметы создаются на этой версии сервера
            try { Items.create(t); }
            catch (Throwable ex) { getLogger().severe("Не удалось создать " + t.key + ": " + ex); }
        }

        hud.start();
        stun.start();
        Fx.start();
        getLogger().info("DSLegs включён: " + LegendaryType.values().length + " легендарных оружий.");
    }

    @Override
    public void onDisable() {
        if (fortress != null) fortress.endAll();
        if (vampire != null) vampire.clearAll();
        if (stun != null) stun.releaseAll();
        if (minions != null) minions.removeAll();
        for (Entity e : new ArrayList<>(tracked)) if (e.isValid()) e.remove();
        tracked.clear();
    }

    public int cooldownSec(LegendaryType t) { return getConfig().getInt("cooldowns." + t.key, t.cooldown); }

    public int cooldownSec(String key, int def) { return getConfig().getInt("cooldowns." + key, def); }

    public void track(Entity e) { tracked.add(e); }
    public void untrack(Entity e) { tracked.remove(e); }
}
