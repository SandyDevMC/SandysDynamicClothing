# Addon API

This mod exposes a small set of NeoForge events for other mods to hook into - adding clothing
programmatically, patching what was found on disk, and overriding cape rendering. This is
infrastructure for addon mods, not for clothing packs themselves (see
[CLOTHING_PACK_FORMAT.md](CLOTHING_PACK_FORMAT.md) for those).

All events live in `com.sandydev.dcs.api.event` and are posted on `NeoForge.EVENT_BUS` (the
shared game bus, not this mod's own mod bus) - any mod can listen for them the normal way:

```java
NeoForge.EVENT_BUS.addListener(YourAddon::onDefinitionsCollected);
```

Add this mod as a compile-time dependency to use its classes.

## Timing: register your listener in your mod's constructor

`ClothingPackDiscoveryEvent` and `ClothingDefinitionsCollectedEvent` (below) fire from this
mod's `FMLConstructModEvent` handler - the point in NeoForge's startup where every mod's
constructor has already run, but `RegisterEvent` hasn't fired yet. This mod needs that timing
itself (clothing items have to be queued before `RegisterEvent`), and it also means your
addon's listener is guaranteed to already be registered **as long as you register it in your
own mod's constructor**, regardless of which mod happens to construct first:

```java
@Mod("your_addon")
public class YourAddon {
    public YourAddon(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(YourAddon::onDefinitionsCollected);
    }
}
```

This is the standard place `NeoForge.EVENT_BUS` listeners are registered anyway - if you're
already doing this, there's nothing special to do.

`ClothingCapeTextureEvent` (below) is different: it fires continuously during rendering, long
after startup, so this timing concern doesn't apply to it - register that listener whenever you
like.

## `ClothingPackDiscoveryEvent`

Fired once, before any archive is read. Lets you add extra archive files (`.cloth` or `.zip`)
to the pipeline - for example, one bundled as a resource inside your own mod jar and extracted
to a temp file - without touching the user's `.minecraft/clothes/` folder.

```java
public static void onPackDiscovery(ClothingPackDiscoveryEvent event) {
    Path bundled = extractBundledPack(); // your own logic
    event.addArchive(bundled);
}
```

Every archive you add goes through the exact same parsing, validation, and archive-size limits
as anything found on disk - see [CLOTHING_PACK_FORMAT.md](CLOTHING_PACK_FORMAT.md).

## `ClothingDefinitionsCollectedEvent`

Fired once, after every archive (including ones added via the event above) has been parsed into
`ClothingDefinition`s, but before they become real items. `event.getDefinitions()` is a mutable
`List<ClothingDefinition>` - add to it, remove from it, or replace an entry:

```java
public static void onDefinitionsCollected(ClothingDefinitionsCollectedEvent event) {
    // Add clothing without any archive at all - textureBytes/iconBytes just need to be
    // valid PNGs of the right size for the item's ClothingKind, exactly as with a pack.
    event.getDefinitions().add(myHandBuiltDefinition);

    // Remove something you don't want registered.
    event.getDefinitions().removeIf(def -> def.id().equals("some_pack:banned_item"));
}
```

If two definitions end up with the same `id`, the **last** one in the list wins - so to override
an existing item, remove it and re-add your own version (or just append yours after it).

## `ClothingCapeTextureEvent`

Fired on the client, every time a player's cape texture is resolved for rendering - after this
mod has already resolved it from Curios. `getCapeTexture()` is what the mod resolved (`null` if
no cape is equipped); call `setCapeTexture(...)` to override it, including forcing a cape where
none was equipped, or hiding one by passing `null`.

```java
public static void onCapeTexture(ClothingCapeTextureEvent event) {
    if (shouldShowSeasonalCape(event.getPlayer())) {
        event.setCapeTexture(SEASONAL_CAPE_TEXTURE);
    }
}
```

This fires potentially every frame per visible player - keep the handler cheap (no I/O, no
network, no heavy computation).

The event is player-only: capes on [Straw Statues](README.md#straw-statues-support) statues are
resolved from the statue's equipped clothing alone and the event is not fired for them.
