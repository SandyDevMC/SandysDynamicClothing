# Sandy's Dynamic Clothing

A NeoForge 1.21.1 mod that adds dynamic clothing directly to the player's skin instead of rendering clothes as armor-style models.

Clothing is loaded from simple ZIP archives, equipped through Curios, and composited into the player's existing skin. This means a clothing creator can make a shirt, jacket, coat, uniform, etc. without creating a 3D armor model.

## Features

- Dynamic clothing textures composited onto the player's current skin.
- Capes (`type: "cape"`) rendered as a plain cape-texture swap - no skin compositing involved.
- Curios slots for clothing.
- Seven simple visual layers: `under`, `shirt`, `base`, `vest`, `jacket`, `coat`, `outer`.
- Optional legacy numeric `priority` for precise ordering.
- Multiple clothing items can be supplied by a single ZIP archive
- Optional per-item translations: a `lang/` folder (`en_us.json`, `ru_ru.json`, ...) next to each `item.json`; the base `name`/`description` alone is enough for an item to work

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

`texture.png` must be a **64×64 PNG** matching the normal Minecraft player skin layout - unless the item is a cape (`type: "cape"`), which uses a **64×32** vanilla cape/elytra texture instead. See [CLOTHING_PACK_FORMAT.md](CLOTHING_PACK_FORMAT.md) for the full breakdown.

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
  "layer": "outer"
}
```

Translations are optional and go into a `lang/` folder next to the `item.json`
(`lang/en_us.json`, `lang/ru_ru.json`, ...) - see [Localization](CLOTHING_PACK_FORMAT.md#localization).
Without one, every player sees the `name`/`description` above.

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

`layer`/`priority` only apply to `type: "skin"` items (the default). Capes
(`type: "cape"`) aren't composited onto the skin at all, so this field is
ignored for them - see the next section.

## Capes

A cape is a different item **format**, not just another slot:

```json
{
  "id": "travellers_cape",
  "name": "Traveller's Cape",
  "slot": "back",
  "type": "cape"
}
```

Instead of the usual 64×64 skin-layout texture, a cape's `texture.png` is a
**64×32 PNG in the vanilla cape/elytra layout**. There's no per-pixel
compositing step - the whole texture simply replaces the player's cape
texture, and vanilla's own cape rendering draws it. That also means a cape
doesn't participate in skin layering at all: `layer`/`priority` don't apply
to it.

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
back
```

`back` is the standard Curios slot, normally used for capes (`type: "cape"`,
see above) - but as with any slot, that's just convention, not an enforced
rule.

`slot` controls **where** the item is equipped. `layer` controls **how it is composited** (for `type: "skin"` items only). They are independent.

