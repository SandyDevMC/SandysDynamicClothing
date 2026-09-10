# Sandy's Dynamic Clothing

A NeoForge 1.21.1 mod that adds **dynamic clothing directly to the player's skin** instead of rendering clothes as armor-style models.

Clothing is loaded from simple ZIP archives, equipped through Curios, and composited into the player's existing skin. This means a clothing creator can make a shirt, jacket, coat, uniform, etc. without creating a 3D armor model.

## Features

- Dynamic clothing textures composited onto the player's current skin.
- Curios slots for clothing.
- Seven simple visual layers: `under`, `shirt`, `base`, `vest`, `jacket`, `coat`, `outer`.
- Optional legacy numeric `priority` for precise ordering.
- Right-click a clothing item to equip it automatically.
- Automatic replacement of an occupied clothing slot.
- Dynamic item icons and localization.
- Multiple clothing items can be supplied by a single ZIP archive.
- Detailed optional debug logging for troubleshooting.
- Clothing contributes vanilla ARMOR and ARMOR_TOUGHNESS attributes while equipped; there is no custom damage-reduction formula.

## Requirements

- Minecraft **1.21.1**
- NeoForge **21.1.x**
- Curios **9.5.x for NeoForge 1.21.1**
- Java **21**

## Installing clothing packs

Create a `.zip` file and put it in:

```text
.minecraft/clothes/
```

The smallest supported pack looks like this:

```text
sandy_frak.zip
├── item.json
├── texture.png
└── icon.png
```

`texture.png` must be a **64×64 PNG** matching the normal Minecraft player skin layout.

### `item.json`

```json
{
  "id": "sandy_frak",
  "name": "Sandy's Frak",
  "description": [
    "A stylish test coat"
  ],
  "slot": "torso",
  "armor": 2,
  "toughness": 0
  "layer": "outer",
  "translations": {
    "en_us": "Sandy's Frak",
    "ru_ru": "Фрак Сэнди"
  }
}
```

### Fields

| Field | Required | Description |
|---|---|---|
| `id` | yes | Unique lowercase item ID. Use letters, numbers, `_`, `-`, `.`. |
| `name` | yes | Fallback display name. |
| `description` | no | Array of tooltip lines. |
| `slot` | yes | Curios slot, e.g. `head`, `torso`, `legs`, `feet`. |
| `armor` | no | Vanilla armor points added while equipped. Defaults to `0`. |
| `toughness` | no | Vanilla armor toughness added while equipped. Defaults to `0`. |
| `armor` | legacy | Alias for `armor`, kept for older clothing packs. |
| `layer` | no | Visual layer. Defaults to `base`. |
| `priority` | no | Legacy/advanced numeric ordering. Overrides `layer` when present. |
| `texture` | no | Texture filename, defaults to `texture.png`. |
| `icon` | no | Icon filename, defaults to `icon.png`. |
| `translations` | no | Locale-to-name map such as `ru_ru`, `en_us`, `de_de`. |

## Clothing layers

The layer is **global**: it describes visual order, not the body part. Pants can use `base`, boots can use `outer`, and so on.

| Layer | Priority | Typical use |
|---|---:|---|
| `under` | 10 | Underwear, undershirt, thin base layer |
| `shirt` | 20 | T-shirt, shirt, blouse |
| `base` | 30 | Normal everyday clothing |
| `vest` | 40 | Vest, waistcoat |
| `jacket` | 50 | Jacket, blazer |
| `coat` | 60 | Coat, long outerwear |
| `outer` | 70 | Frak, heavy coat, topmost clothing |

Higher priority is composited later and therefore appears above lower layers.

For most clothing creators, **never use `priority`**. Just choose the appropriate `layer`.

## Curios slots

The default clothing slots are:

```text
head
face
neck
torso
jacket
legs
feet
hands
accessory
```

`slot` controls **where** the item is equipped. `layer` controls **how it is composited**. They are independent.

## Localization

The mod contains its own Minecraft language files:

```text
src/main/resources/assets/dynamic_clothing_system/lang/en_us.json
src/main/resources/assets/dynamic_clothing_system/lang/ru_ru.json
```

Clothing packs can provide their own item-name translations through the `translations` object in `item.json`.

## Debugging

Debug logging is disabled by default for normal players.

Enable it in the generated NeoForge config:

```text
config/dynamic_clothing_system-common.toml
```

Set:

```toml
debugLogging = true
```

Useful log markers include:

```text
[DCS][EQUIP]
[DCS][SKIN]
[DCS][CACHE]
[DCS][RENDER]
[DCS][NETWORK]
```

When reporting a bug, include the relevant section of `latest.log`.

## Building from source

```powershell
./gradlew.bat build
```

The resulting mod JAR is produced in:

```text
build/libs/
```

Java 21 is required.

## Project structure

```text
src/main/java/com/sandydev/dcs/
├── clothing/              # clothing data, loading and Curios integration
├── clothing/client/       # skin composition, cache and dynamic icons
└── mixin/                 # player rendering hook
```

## License

The source code is currently distributed under **All Rights Reserved**. See `LICENSE`.

Minecraft, NeoForge and Curios are third-party projects and trademarks of their respective owners.
