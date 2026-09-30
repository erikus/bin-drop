# Bin Drop — design

A toddler (18 months) game for the AYN Thor's top screen (landscape). Side view:
three Recology-style 32-gallon wheeled carts stand on the ground (default
Blue / Grey / Green). Trash items land on the left; the child drags an item and
lets go above a bin — it falls in and the game celebrates. There are no wrong
answers: every bin accepts every item.

## Decisions (with reasons)

| Topic | Decision | Why |
|---|---|---|
| Stack | Kotlin, Jetpack Compose, single `app/` module, same toolchain as `salchang` (AGP 9.3.1, Kotlin 2.4.10, Gradle 9.6.1, JDK 17 via `~/.gradle/gradle.properties`) | Known-good build on this machine. |
| Rendering | One Compose `Canvas`, redrawn every frame from `withFrameNanos`; game state is plain Kotlin (`GameWorld`), no engine | A handful of sprites + particles; an engine (libGDX) would add more setup than game. |
| Bins | Drawn procedurally (body, open lid, wheels, handle, a face) | Palette swaps are just colours; no per-colour art. |
| Items | Noto Emoji PNGs (Apache-2.0, `googlefonts/noto-emoji` `2D/png/512/`) bundled in `res/drawable-nodpi/emoji_<name>.png` | Free, consistent, don't depend on the device's emoji font. |
| Sounds | CC0 clips (Kenney) in `res/raw/sfx_*.ogg`, played with `SoundPool`; synthesized fallback if a clip cannot be sourced | Small, free, no licensing text needed beyond attribution. |
| Buttons | Every `KeyEvent` is consumed. D-pad / ABXY / shoulders / start / select cycle the bin palette; BACK, volume, and everything else are ignored. Joystick motion events are consumed. | Requested: buttons swap colours or do nothing. Exit via Home; pin the app (screen pinning) for the flight. |
| Parent settings | Gear in the top-right corner needs a ~1.5 s long-press to open | Toddlers rarely hold still on one spot. Holds Easy mode, sound, palette. Persisted in `SharedPreferences`. |
| Easy mode | Tapping an item flings it into a bin automatically (arc tween); dragging still works; strong horizontal "magnet" toward the nearest bin mouth while an item falls | Quick wins when dragging is too hard. Normal mode keeps a mild magnet. |
| Screen | `sensorLandscape`, immersive (sticky hide of bars), keep screen on | Handheld, no chrome for small fingers to hit. |

## Game rules

- World is the canvas in px. Ground line at `H * 0.86`. Bins occupy the right ~52% of the width, items land in the left part.
- Keep `TARGET_ITEMS` (5) loose items around; a new one drops from above the top edge at a random x in the item zone ~0.6 s after one is binned.
- Item radius ≈ `H * 0.075` (bigger in easy mode). Touch hit radius = 1.4 × radius; the nearest item wins.
- Held item rides `LIFT` px above the finger so the finger does not cover it; scales to 1.15×.
- Release: velocity = finger velocity clamped (`MAX_THROW`); gravity pulls it down; bounces on the ground with damping; bounces off screen sides.
- Capture: item is falling (vy > 0), not held, its centre x within the bin mouth (± magnet margin), and its centre y crosses the mouth y this frame → item becomes *sinking*: keeps falling behind the bin's front wall for `SINK_TIME`, then is removed.
- Celebration on capture: confetti burst (40) + star ring from the mouth, bin squash-and-stretch and lid flap, happy eyes for 1 s, ⭐ pop, `sfx_drop` + `sfx_chime`. Every 5th capture: full-screen confetti rain + 🎉 + `sfx_bigwin`.
- Tapping a bin: wiggle + `sfx_boing`. Bin eyes follow the nearest held item.
- Multi-touch: each pointer id owns at most one item.

## Palettes (body, lid, accent)

1. Recology: Blue `#1E5EBF`, Grey `#7A7D80`, Green `#2F8F3E`
2. Sunset: Red, Orange, Yellow
3. Candy: Pink, Purple, Teal
4. Ocean: Navy, Aqua, Sand
5. Forest: Brown, Lime, Olive

## Assets

Items (Noto Emoji `2D/png/512/emoji_u<hex>.png` → `res/drawable-nodpi/emoji_<name>.png`):

| name | codepoint | | name | codepoint |
|---|---|---|---|---|
| banana | 1f34c | | newspaper | 1f4f0 |
| apple | 1f34e | | can | 1f96b |
| carrot | 1f955 | | box | 1f4e6 |
| broccoli | 1f966 | | milk | 1f95b |
| pizza | 1f355 | | cup | 1f964 |
| leaf | 1f343 | | bottle | 1f9f4 |
| bread | 1f35e | | envelope | 2709 |
| cookie | 1f36a | | sock | 1f9e6 |
| shoe | 1f45f | | balloon | 1f388 |
| sponge | 1f9fd | | bulb | 1f4a1 |
| toothbrush | 1faa5 | | candy | 1f36c |

Effects / decoration: star `2b50`, party `1f389`, sparkles `2728`, heart `2764`, recycle `267b`, seedling `1f331`.

Sounds (`res/raw/`): `sfx_pop.ogg` (pick up), `sfx_drop.ogg` (lands in bin), `sfx_chime.ogg` (success), `sfx_boing.ogg` (bin tapped), `sfx_bigwin.ogg` (every 5th). Attribution in `ASSETS.md`.

## Layout

```
app/src/main/kotlin/dev/estaab/bindrop/
  MainActivity.kt        fullscreen, keys, hosts GameScreen
  Settings.kt            SharedPreferences-backed easy/sound/palette
  audio/Sounds.kt        SoundPool wrapper
  game/Palette.kt        palettes
  game/Items.kt          item catalog (name → drawable, kind)
  game/GameWorld.kt      state + physics + input (no Compose)
  game/Effects.kt        particles
  game/GameScreen.kt     Compose canvas, pointer input, frame loop, parent overlay
```
