# Bin Drop

A toddler game for the AYN Thor's top screen: drag trash into three Recology-style
wheeled bins (Blue / Grey / Green) and get confetti. Every bin accepts every item;
there are no wrong answers. See `docs/DESIGN.md` for the design, `ASSETS.md` for
asset sources and licenses.

## Playing

- **Drag** an item and let go above a bin: it falls in, the bin bounces and grins,
  confetti and stars fly. Every 5th drop gets a bigger celebration; every 3rd drop
  wipes in a new background (day → sunset → night → snow → spring → ocean → dusk),
  each with its own set of things to throw away.
- **Tap** a bin to make it wiggle.
- **Any gamepad button / d-pad** swaps the bin colours (5 palettes). Back and
  volume keys are swallowed; leave the game with Home.
- **Parent settings**: hold the gear in the top-right corner for 1.5 s. Toggles
  **Easy mode** (tapping an item flings it into a bin by itself, items are bigger
  and the "magnet" toward the bins is stronger), sound, and colours.
- For a flight: pin the app (Recents → app icon → Pin) so Home doesn't leave it.
  The Thor's floating "game assistant" bubble sits on the right edge and can be
  turned off in the Thor's own settings.

## Build & install

Requires JDK 17 (`~/.gradle/gradle.properties` sets `org.gradle.java.home`), the
Android SDK (platform 37, build-tools 36) and `local.properties` with `sdk.dir`.

```sh
./gradlew :app:testDebugUnitTest      # game-logic tests (no device needed)
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.estaab.bindrop/.MainActivity --display 0
```

`tools/make_magic_sfx.py` (run with `uv run`) regenerates the synthesized
scene-change sound.

On the Thor, display 0 is the top 6" screen (1920×1080 landscape) and display 4 is
the bottom touchscreen; launcher launches land on display 0. `screencap -d 0` returns
nothing on this device — plain `adb shell screencap -p /sdcard/x.png` captures the
top screen.
