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
  "layer": "coat"
}
```

`name` and `description` are the item's base text and are all it needs to work. Translations
are optional and live in a separate `lang/` folder - see [Localization](#localization).

## Item type: `skin` vs `cape`

`type` is optional and defaults to `skin`, so every pack without this field
keeps working unchanged.

| `type` | Texture | How it's rendered |
|---|---|---|
| `skin` (default) | 64×64, player-skin layout | Composited pixel-by-pixel onto the player's skin, stacked with other `skin` items by `layer`/`priority` - see [ClothingTextureComposer](src/main/java/com/sandydev/dcs/clothing/client/ClothingTextureComposer.java). |
| `cape` | 64×32, vanilla cape/elytra layout | Not composited at all - the whole texture replaces the player's cape texture and is drawn by vanilla's own cape layer. `layer`/`priority` is ignored for `cape` items. |

A `cape` item is normally equipped in the standard Curios `back` slot (see
below), but nothing enforces that - as with any other slot, it's just what
`slot` you put in `item.json`.

```json
{
  "id": "travellers_cape",
  "name": "Traveller's Cape",
  "description": ["A weathered cape"],
  "slot": "back",
  "type": "cape"
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
| `back` | Back | Capes (`type: "cape"`, see above) |

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
│   ├── icon.png
│   └── lang/            (optional)
│       ├── en_us.json
│       └── ru_ru.json
└── soldier/
    ├── item.json
    ├── texture.png
    └── icon.png
```

A single `item.json` may also contain an array of clothing definitions.

## Localization

Localization is **optional**. `name` and `description` in `item.json` are the item's *base*
text: they are all an item needs to work, and they are what every player sees in a language
that has no translation.

To translate an item, add a `lang/` folder **next to that item's `item.json`** with one file per
language, named after the lowercase Minecraft language code:

```text
winter_coat/
├── item.json
├── texture.png
├── icon.png
└── lang/
    ├── en_us.json
    ├── ru_ru.json
    └── de_de.json
```

Every item folder has its own `lang/`; there is no pack-wide one. Create only the languages you
need, or none at all.

Each language file looks like this:

```json
{
  "name": "Winter Coat",
  "description": [
    "Warm outerwear",
    "Smells faintly of pine"
  ]
}
```

- Both keys are optional. A key that is missing (or an empty `description`) simply falls back to
  the next step of the lookup order below - so a file may translate only the `name`.
- `description` may also be a single string, exactly like in `item.json`.
- Translations replace the description as a whole, so a translation may have more or fewer lines
  than the base.

**Which text a player sees.** Minecraft always loads `en_us` first and puts the player's own
language on top of it, so the lookup is:

1. the player's language file (`ru_ru.json` for a Russian client), then
2. the item's `en_us.json`, if it has one, then
3. the base `name`/`description` from `item.json`.

In practice: without any `lang/` folder everybody sees the base text; an `en_us.json` makes
English the default for every language you did not translate; a `ru_ru.json` overrides it for
Russian clients only.

A broken language file (invalid JSON, a wrong field type, a name that is not a language code)
never stops the item from loading - that file is skipped and a warning is written to the log.

**Several items in one `item.json`.** If `item.json` is an array, the folder's `lang/` files are
shared by all of its items, so key each file by item `id` instead:

```json
{
  "officer": { "name": "Officer Uniform" },
  "soldier": { "name": "Soldier Uniform", "description": ["Standard issue"] }
}
```

**Legacy `translations`.** The old `"translations": {"ru_ru": "..."}` object inside `item.json`
still works (names only). If a `lang/` file sets the same field for the same language, the
`lang/` file wins. New packs should use `lang/` files.

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

For `type: "skin"` (default):
- PNG format.
- Exactly 64×64 pixels.
- Normal Minecraft player-skin layout.
- Alpha transparency is supported.

For `type: "cape"`:
- PNG format.
- Exactly 64×32 pixels.
- Normal Minecraft cape/elytra layout (the front face of the cape sits at
  pixels 1,1 to 11,17 - the same template used for vanilla Mojang capes).
- Alpha transparency is supported.
- `layer` is not applicable and is ignored if present.

