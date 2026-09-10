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
  "slot": "torso",
  "armor": 3,
  "toughness": 0
  "layer": "coat",
  "translations": {
    "en_us": "Winter Coat",
    "ru_ru": "Зимнее пальто"
  }
}
```

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
  "slot": "torso",
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


### Legacy compatibility

Older packs may continue to use `armor`; DCS treats it as an alias for `armor`. New packs should use `armor` and optionally `toughness`.
