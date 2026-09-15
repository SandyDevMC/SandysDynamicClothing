# Sandy's Dynamic Clothing

A NeoForge 1.21.1 mod that adds dynamic clothing directly to the player's skin instead of rendering clothes as armor-style models.

Clothing is loaded from simple ZIP archives, equipped through Curios, and composited into the player's existing skin. This means a clothing creator can make a shirt, jacket, coat, uniform, etc. without creating a 3D armor model.

## Features

- Dynamic clothing textures composited onto the player's current skin.
- Curios slots for clothing.
- Seven simple visual layers: `under`, `shirt`, `base`, `vest`, `jacket`, `coat`, `outer`.
- Optional legacy numeric `priority` for precise ordering.
- Multiple clothing items can be supplied by a single ZIP archive

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
  "slot": "body",
  "armor": 2,
  "toughness": 0,
  "layer": "outer",
  "translations": {
    "ru_ru": "Фрак Санди"
  }
}
```

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
body
jacket
legs
feet
hands
accessory
```

`slot` controls **where** the item is equipped. `layer` controls **how it is composited**. They are independent.

