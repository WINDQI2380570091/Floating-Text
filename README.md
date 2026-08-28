# Floating Text

Place editable floating text in the world. Right-click a block face or air to place text; right-click the text to edit its content, color, scale, position and rotation. Text is rendered with the game's native font, with full save support and multiplayer sync.

- Minecraft 1.20.1 · Forge 47.x (tested on 47.4.10) · Java 17
- No dependencies, no Mixins, no core-mod API usage
- 10 languages supported

---

## Gameplay Features

- **Place text** — Right-click a block face to place text right on the surface, centered on the face; right-click air to float text 3 blocks in front of you. On ceilings and floors the text lays flat automatically.
- **Edit anytime** — Right-click the text to open the edit screen: content (up to 100 characters), color (8 built-in colors), scale (0.15 ~ 10), X/Y/Z offset (-1 ~ 1), rotation (0 ~ 360°). The edit screen pops up automatically right after placing.
- **Native font rendering** — Text is drawn with Minecraft's own font renderer, visible from any angle (double-sided), fully bright, no shadow, with depth offset so it sits flush against blocks without z-fighting.
- **Persistent** — Text is saved into the world save. Close and reopen the game and it is still there.
- **Multiplayer ready** — Changes sync to all players instantly; only the creator can edit a text.
- **No dependencies** — No core mods, no Mixins, no third-party libraries. Works alongside Create and any other Forge 1.20.1 mod. Install on the server too for multiplayer.

## Technical Details

- **Entity-based implementation** — Each text is a custom `Entity` (no block, no BlockEntity), so text can float in air or sit on any block face. The entity has zero per-tick logic; the game automatically culls off-screen entities, so performance cost is close to a static entity.
- **Data sync** — All editable fields live in `SynchedEntityData` (auto-synced on change). On top of that, a custom S2C full-sync packet (`SyncFloatingTextPacket`) broadcasts the complete state after every save, bypassing the "dirty only if different from default" behavior of the vanilla sync system — so values like offset 0.00 or white color always reach clients.
- **Network** — Forge `SimpleChannel` (`floatingtext:main`, protocol version 2). Packets:
  - `UpdateFloatingTextPacket` (C2S, edit/delete): payload is a `CompoundTag` read/written by key name (immune to field-order mismatch).
  - `SyncFloatingTextPacket` (S2C, full state broadcast after save).
  - `T72AVBoomPacket` (C2S, easter egg).
- **Server-side validation** — Owner permission check, same-dimension check, 8-block distance check, text truncated to 100 Unicode code points (emoji-safe), all numbers clamped (scale 0.15~10, offset ±1, rotation 0~360), NaN/Infinity rejected.
- **Persistence** — NBT via `saveAdditional` / `readAdditionalSaveData`; all values are clamped again on load so corrupted saves cannot produce giant text.
- **Rendering** — `FontRenderer.drawInBatch` with negative Y scale (font is GUI-oriented, Y-down; world is Y-up), double-sided (back face rotated 180°), `POLYGON_OFFSET` display mode (no z-fighting when flush against blocks), full-bright lighting, no background, no shadow.
- **Placement** — Position fixed at the clicked block face center + 0.01 block outward; yaw snapped to nearest 90°; xRot 90°/-90° makes text lay flat on floors/ceilings.
- **Client auto-open screen** — After placing, the edit screen opens automatically once the entity's synced data arrives (600 ms delay, checks owner, and does not hijack an already-open screen). A `WeakHashMap` keyed by the entity object prevents both duplicate popups and memory leaks.
- **Compatibility** — No Mixins, no vanilla class edits, no third-party dependencies, no core-mod API (Fabric API / NeoForge API). All registrations use the `floatingtext` namespace. Works with Create, optimization mods (Embeddium, Rubidium, etc.) and any other Forge 1.20.1 mod.

## Build

- JDK 17 required
- `gradlew build` (Windows: `gradlew.bat build`)
- Output: `build/libs/floatingtext-1.0.0.jar` (reobfuscated, installable). Do not use the `-sources` jar.
- Install: copy the jar into the `mods` folder (both server and clients for multiplayer).

## Languages

Simplified Chinese, Traditional Chinese (HK/TW), English, Japanese, Korean, French, Russian, Spanish, Arabic.

## Easter Egg

There is an item called **T-72AV** in the creative tab. Nothing happens when you use it... unless your game language is Arabic.

## Contact

This mod was entirely written by AI, containing not a single line of hand-written code. Please be informed.

If you encounter any issues, please contact me via Douyin (the Chinese version of TikTok) rather than GitHub or X. My Douyin ID: 87808215036
