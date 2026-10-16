# Clothing Pack Format

A clothing pack is a ZIP archive placed in `.minecraft/clothes/`, with the extension
**`.cloth`**. The older **`.zip`** extension still works and is scanned the same way, for packs
made before the `.cloth` switch - there's no format difference between the two, only the
extension. New packs should use `.cloth`.

## Minimal pack

```text
my_pack.cloth
├── item.json
├── texture.png
└── icon.png
```

## Pack manifest (optional)

An archive may have a `manifest.json` at its **root** (next to `item.json`, not inside an
item's own folder):

```json
{
  "format_version": "1.1"
}
```

`format_version` declares which pack format the archive was written against. It's entirely
optional - no `manifest.json`, or one without `format_version`, defaults to the current format
(currently `1.1`). Right now `1.0` (pre-`.cloth`) and `1.1` (current) parse `item.json`
identically; the version exists so a pack can pin itself to older behavior if a future format
change needs one. An unrecognized value falls back to the current format with a warning in the
log, rather than failing the whole archive.

## Archive limits

A handful of limits protect the game from a malformed or hostile archive - normal packs (a
handful of small PNGs and JSON files) are nowhere near any of them:

| Limit | Value |
|---|---|
| Archive file size on disk | 64 MiB |
| Entries per archive | 2000 |
| Uncompressed size of one entry | 32 MiB |
| Total uncompressed size per archive | 128 MiB |

An archive over these limits is skipped entirely (logged as a warning), the same as any other
unreadable archive. Nothing from an archive is ever executed or written to disk - parsing only
ever produces JSON text and image bytes held in memory.

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

| Slot | Display name | Typical use | Also fits vanilla slot |
|---|---|---|---|
| `head` | Head | Hats, helmets, hoods | Helmet |
| `face` | Face | Glasses, masks, face paint | Helmet |
| `neck` | Neck | Scarves, ties, necklaces | Helmet |
| `body` | Torso | Shirts, coats, frocks | Chestplate |
| `jacket` | Jacket | Jackets, blazers worn over `body` | Chestplate |
| `legs` | Legs | Trousers, skirts | Leggings |
| `feet` | Feet | Boots, shoes | Boots |
| `hands` | Hands | Gloves | - |
| `accessory` | Accessory | Anything that doesn't fit the slots above | - |
| `back` | Back | Capes (`type: "cape"`, see above) | Chestplate |

`slot` controls **where** the item is equipped: its own Curios slot, plus the vanilla armor slot from the last column of the table above (if it has one - `hands` and `accessory` are Curios-only). It
does **not** control visual stacking order - that's what `layer` is for (see
below). An item's `slot` and `layer` are independent, e.g. a `jacket` can use
the `outer` layer and a `body` item can also use `outer` if it should render
above a jacket.

Any other string is **not** rejected at load time - the item is still parsed
and registered, but there's no matching Curios slot for a player to put it
into, so it can never actually be equipped. Stick to the identifiers above.

## Multiple items in one archive

An archive may contain multiple `item.json` files in different directories. Files are resolved relative to each `item.json`:

```text
uniform_pack.cloth
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

## Styles (texture variants)

One item can have several looks - **styles**. The player switches between them by
**right-clicking the item in an inventory** (player inventory, the Curios screen, chests, ...) with an
empty cursor; every click moves to the next style and, after the last one, back to the first. The
current style is shown in the item's tooltip (`Style: 2/4`) and is stored in the item itself, so it
stays with the item and is visible to other players.

The numbering is simple: the texture you already have is **style 0**, and every further style is the
same file name with the style number appended:

```text
winter_coat/
├── item.json
├── texture.png      <- style 0 (what the item always looked like)
├── texture1.png     <- style 1
├── texture2.png     <- style 2
├── icon.png         <- inventory icon of style 0
└── icon2.png        <- optional: own inventory icon for style 2
```

- Styles need no changes in `item.json`. An item with a single `texture.png` behaves exactly as before.
- A style texture must meet the same requirements as the base texture (64×64 for `skin`, 64×32 for
  `cape`). The number is one or two digits, `1`-`99`. Gaps are fine (`texture1.png` and `texture3.png`
  give styles 1 and 3); the number in the file name is the style number, it never shifts.
- `iconN.png` is optional. A style without its own icon keeps the item's normal icon.
- Styles are looked up **only in the folder of the item's base texture**, so every item folder is
  independent from the others.
- A broken style file (not a PNG, wrong size) only skips that one style and writes a warning to the
  log; the item and its other styles still load.

**Custom file names.** If `item.json` names its own files, the same rule applies to *those* names -
the number is appended to the name you chose:

```json
{
  "id": "officer",
  "name": "Officer Uniform",
  "slot": "body",
  "texture": "officer_texture.png",
  "icon": "officer_icon.png"
}
```

```text
officer/
├── item.json
├── officer_texture.png     <- style 0
├── officer_texture1.png    <- style 1
├── officer_texture2.png    <- style 2
├── officer_icon.png
└── officer_icon2.png       <- icon of style 2
```

Files that `item.json` already names for an item (as `texture`, `icon` or in `styles`) are never
mistaken for a style of another item in the same folder - e.g. with `"texture": "a.png"` for one
item and `"texture": "a2.png"` for another, `a2.png` is not "style 2 of `a.png`".

**Explicit `styles`.** When the file names don't follow the numbering at all, list the styles in
`item.json` instead. The key is the style number, the value is a texture file name, or an object with
`texture` and optionally `icon`:

```json
{
  "id": "officer",
  "name": "Officer Uniform",
  "slot": "body",
  "styles": {
    "1": "officer_red.png",
    "2": { "texture": "officer_blue.png", "icon": "officer_blue_icon.png" }
  }
}
```

If `styles` is present, the numbered-file lookup is **not** used for that item. Style 0 stays the
item's `texture`/`texture.png`.

**Good to know**

- The style is part of the item (a data component), so it is kept when the item is moved, equipped by
  right-clicking it in the world, or put on a Straw Statues statue.
- Switching works in survival and creative inventories. In the *creative item picker* tabs the click
  picks up a fresh copy as usual - take the item first, then right-click it in your inventory.
- Every player needs the same pack. If a client doesn't have a style that the item uses, it shows
  style 0 instead.

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

## Extending this with an addon mod

If you're writing a separate mod (rather than a pack), see [ADDON_API.md](ADDON_API.md) for the
hooks this mod exposes - adding clothing programmatically, patching packs found on disk, and
overriding cape rendering.

