# Assets

## Images — Noto Emoji (Apache-2.0)

Source: https://github.com/googlefonts/noto-emoji (license: Apache License 2.0,
https://github.com/googlefonts/noto-emoji/blob/main/LICENSE).
Files are the unmodified 512 x 512 PNGs from
`https://raw.githubusercontent.com/googlefonts/noto-emoji/main/2D/png/512/emoji_u<hex>.png`.
(The repo moved its PNGs from `png/` to `2D/png/`.)

All saved in `app/src/main/res/drawable-nodpi/`.

| File | Codepoint | Source URL |
|---|---|---|
| emoji_banana.png | U+1F34C | .../2D/png/512/emoji_u1f34c.png |
| emoji_apple.png | U+1F34E | .../2D/png/512/emoji_u1f34e.png |
| emoji_carrot.png | U+1F955 | .../2D/png/512/emoji_u1f955.png |
| emoji_broccoli.png | U+1F966 | .../2D/png/512/emoji_u1f966.png |
| emoji_pizza.png | U+1F355 | .../2D/png/512/emoji_u1f355.png |
| emoji_leaf.png | U+1F343 | .../2D/png/512/emoji_u1f343.png |
| emoji_bread.png | U+1F35E | .../2D/png/512/emoji_u1f35e.png |
| emoji_cookie.png | U+1F36A | .../2D/png/512/emoji_u1f36a.png |
| emoji_shoe.png | U+1F45F | .../2D/png/512/emoji_u1f45f.png |
| emoji_sponge.png | U+1F9FD | .../2D/png/512/emoji_u1f9fd.png |
| emoji_toothbrush.png | U+1FAA5 | .../2D/png/512/emoji_u1faa5.png |
| emoji_newspaper.png | U+1F4F0 | .../2D/png/512/emoji_u1f4f0.png |
| emoji_can.png | U+1F96B | .../2D/png/512/emoji_u1f96b.png |
| emoji_box.png | U+1F4E6 | .../2D/png/512/emoji_u1f4e6.png |
| emoji_milk.png | U+1F95B | .../2D/png/512/emoji_u1f95b.png |
| emoji_cup.png | U+1F964 | .../2D/png/512/emoji_u1f964.png |
| emoji_bottle.png | U+1F9F4 | .../2D/png/512/emoji_u1f9f4.png |
| emoji_envelope.png | U+2709 | .../2D/png/512/emoji_u2709.png |
| emoji_sock.png | U+1F9E6 | .../2D/png/512/emoji_u1f9e6.png |
| emoji_balloon.png | U+1F388 | .../2D/png/512/emoji_u1f388.png |
| emoji_bulb.png | U+1F4A1 | .../2D/png/512/emoji_u1f4a1.png |
| emoji_candy.png | U+1F36C | .../2D/png/512/emoji_u1f36c.png |
| emoji_star.png | U+2B50 | .../2D/png/512/emoji_u2b50.png |
| emoji_party.png | U+1F389 | .../2D/png/512/emoji_u1f389.png |
| emoji_sparkles.png | U+2728 | .../2D/png/512/emoji_u2728.png |
| emoji_heart.png | U+2764 | .../2D/png/512/emoji_u2764.png |
| emoji_recycle.png | U+267B | .../2D/png/512/emoji_u267b.png |
| emoji_seedling.png | U+1F331 | .../2D/png/512/emoji_u1f331.png |

## Sounds

All saved in `app/src/main/res/raw/` as mono 44.1 kHz OGG Vorbis, peak-normalized
to about -3 dBFS with ffmpeg. The Kenney sounds are otherwise unmodified.

| File | Source | Original file | License |
|---|---|---|---|
| sfx_pop.ogg | Kenney "Interface Sounds" — https://kenney.nl/assets/interface-sounds | Audio/pluck_001.ogg | CC0 1.0 |
| sfx_drop.ogg | Kenney "Impact Sounds" — https://kenney.nl/assets/impact-sounds | Audio/impactSoft_medium_001.ogg | CC0 1.0 |
| sfx_chime.ogg | Kenney "Interface Sounds" — https://kenney.nl/assets/interface-sounds | Audio/confirmation_001.ogg | CC0 1.0 |
| sfx_magic.ogg | Synthesized for this project (`tools/make_magic_sfx.py`: C5-E5-G5-C6 arpeggio with shimmer, 0.9 s) | — | none (original work) |
| sfx_boing.ogg | Synthesized for this project (numpy: sine sweep 220→400 Hz with decaying 14 Hz pitch wobble, 0.55 s) | — | none (original work) |
| sfx_bigwin.ogg | Kenney "Digital Audio" — https://kenney.nl/assets/digital-audio | Audio/powerUp1.ogg | CC0 1.0 |

Kenney license: Creative Commons Zero, CC0 — http://creativecommons.org/publicdomain/zero/1.0/
