# Bin Drop

A toddler game for the AYN Thor's top screen: drag trash into three Recology-style
wheeled bins (Blue / Grey / Green) and get confetti. One bin sits at the left edge and
two at the right, so there is a bin near either hand. Every bin accepts every item;
there are no wrong answers. See `docs/DESIGN.md` for the design, `ASSETS.md` for
asset sources and licenses.

## Playing

- **Drag** an item and let go above a bin: it falls in, the bin bounces and grins,
  confetti and stars fly. A drag that starts on empty ground picks up the first item
  the finger crosses; each finger carries one item at a time. Every 5th drop gets a bigger celebration; every 3rd drop
  wipes in a new background (day → sunset → night → snow → spring → ocean → dusk),
  each with its own set of things to throw away.
- **Tap** a bin to make it wiggle and say its colour. Each bin has its own voice, and
  says the nearest colour a toddler would learn (navy and aqua are both "blue"). Bins
  also say their colour when they catch an item. Three quick taps on the same bin swap
  the bin colours (5 palettes) and the bin announces its new one.
- **Any gamepad button / d-pad** also swaps the bin colours. Back and
  volume keys are swallowed; leave the game with Home.
- **Parent settings**: hold the gear in the top-right corner for 1.5 s. Toggles
  **Easy mode** (tapping an item flings it into a bin by itself, items are bigger
  and the "magnet" toward the bins is stronger), sound, colours, and **Pin app**.
- **Pin app** uses Android's app pinning (lock-task mode) so edge swipes no longer
  open the notification shade, Home or Recents. Android shows a confirmation the
  first time after each launch; tap "Got it". Turn the toggle off in parent
  settings to unpin (Android's own unpin gesture works too).
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
scene-change sound. `tools/make_voices.py` (also `uv run`; needs ffmpeg) regenerates
the bins' colour-word clips with Piper TTS, caching the voice models in
`tools/.piper-voices/`.

On the Thor, display 0 is the top 6" screen (1920×1080 landscape) and display 4 is
the bottom touchscreen; launcher launches land on display 0. `screencap -d 0` returns
nothing on this device — plain `adb shell screencap -p /sdcard/x.png` captures the
top screen.
