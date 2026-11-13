# DSLegs - легендарное оружие (Paper 1.21.4+, Java 21)

Сборка: `mvn clean package` -> `target/DSLegs.jar` в папку `plugins/`.
Команды: `/dslegs` - меню (ЛКМ - взять), `/dslegs give <оружие> [игрок]` (dslegs.admin).

## Custom Model Data
Точный ID лежит строкой в strings[0] компонента (float32 не хранит нечётные числа выше 16 777 216),
поэтому в ресурспаке используется select по custom_model_data. Готовый пак: DSLegs-ResourcePack.zip.
ID: 20000000 Excalibur, 20000001 Light Mace, 20000002 Vampire Blade, 20000003 Artemis Bow,
20000004 Sonic Crossbow, 20000005 Dark Katana, 20000006 Ice Sword, 20000007 Soul Fortress,
20000008 Frostmorn, 20000009 Monarch's Staff, 20000010 Shadow Reaper, 20000011 Sanguine Art, 20000012 Explosive Bow,
20000013 Sakura Katana, 20000014 Warrior Greatsword.
Меняются в config.yml (секция cmd-ids).

## 3D-модели: Sakura Katana и Warrior Greatsword
Эти два оружия используют компонент `item_model` (`sakura:sakura_katana`, `warrior:warrior_greatsword`,
меняется в config.yml, секция `item-models`). Ресурспак лежит в `resourcepack/` - заархивируйте его
содержимое (`pack.mcmeta` + `assets/`) или объедините с основным паком. Исходники Blockbench - в `models/`.
Модели собраны под Minecraft 1.21.11 (pack format 75, свободные углы поворота): на более старых клиентах
их нужно пересобрать генератором с `--legacy-rotation`.

- **Sakura Katana** - ПКМ: рывок на 8 блоков с уроном и следом из лепестков (12 с);
  Shift+ЛКМ: взрыв лепестков - урон, оглушение 2 с, Слепота II на 3 с (40 с).
- **Warrior Greatsword** - ПКМ: «Раскол земли» - трещина с каменными шипами на 12 блоков, урон, подброс,
  Замедление II; владелец получает Сопротивление I на 5 с (30 с).

## Визуальные эффекты
Аура вокруг владельца, спираль при взятии оружия, кольца при активации и готовности способности,
искры при ударе. Каждый эффект отключается в config.yml, секция `visuals`.
