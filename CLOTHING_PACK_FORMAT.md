# Clothing Pack Format

A clothing pack is a ZIP archive placed in `.minecraft/clothes/`.

## Minimal pack

```text
my_pack.zip
├── item.json
├── texture.png
└── icon.png
```

## Example

```json
{
  "id": "winter_coat",
  "name": "Winter Coat",
  "description": ["Warm outerwear"],
  "slot": "body",
  "armor": 3,
  "toughness": 0,
  "layer": "coat",
  "translations": {
    "en_us": "Winter Coat",
    "ru_ru": "Зимнее пальто"
  }
}
```

## Slots

`slot` must be one of the identifiers the mod currently registers for players
(`data/dynamic_clothing_system/curios/entities/players.json`):

| Slot | Display name | Typical use |
|---|---|---|
| `head` | Head | Hats, helmets, hoods |
| `face` | Face | Glasses, masks, face paint |
| `neck` | Neck | Scarves, ties, necklaces |
| `body` | Torso | Shirts, coats, frocks |
| `jacket` | Jacket | Jackets, blazers worn over `body` |
| `legs` | Legs | Trousers, skirts |
| `feet` | Feet | Boots, shoes |
| `hands` | Hands | Gloves |
| `accessory` | Accessory | Anything that doesn't fit the slots above |

`slot` only controls **where** the item is equipped in the Curios inventory. It
does **not** control visual stacking order - that's what `layer` is for (see
below). An item's `slot` and `layer` are independent, e.g. a `jacket` can use
the `outer` layer and a `body` item can also use `outer` if it should render
above a jacket.

Any other string is **not** rejected at load time - the item is still parsed
and registered, but there's no matching Curios slot for a player to put it
into, so it can never actually be equipped. Stick to the identifiers above.

## Multiple items in one ZIP

An archive may contain multiple `item.json` files in different directories. Files are resolved relative to each `item.json`:

```text
uniform_pack.zip
├── officer/
│   ├── item.json
│   ├── texture.png
│   └── icon.png
└── soldier/
    ├── item.json
    ├── texture.png
    └── icon.png
```

A single `item.json` may also contain an array of clothing definitions.

## Custom filenames

```json
{
  "id": "officer",
  "name": "Officer Uniform",
  "slot": "body",
  "layer": "jacket",
  "texture": "officer_texture.png",
  "icon": "officer_icon.png"
}
```

## Texture requirements

- PNG format.
- Exactly 64×64 pixels.
- Normal Minecraft player-skin layout.
- Alpha transparency is supported.

