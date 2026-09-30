# /// script
# requires-python = ">=3.10"
# dependencies = ["numpy"]
# ///
"""Synthesizes sfx_magic.ogg: a quick rising four-note sparkle used when the background changes.

Usage: uv run tools/make_magic_sfx.py   (needs ffmpeg on PATH for the OGG encode)
"""

import subprocess
import tempfile
import wave
from pathlib import Path

import numpy as np

SAMPLE_RATE = 44100
NOTES_HZ = [523.25, 659.25, 783.99, 1046.50]  # C5 E5 G5 C6
NOTE_SPACING_S = 0.09
NOTE_DECAY_S = 0.35
SHIMMER_HZ = 6.0
SHIMMER_DEPTH = 0.15
TOTAL_S = 0.9
PEAK = 0.7
OUT = Path(__file__).resolve().parent.parent / "app/src/main/res/raw/sfx_magic.ogg"


def note(freq: float, start: float, t: np.ndarray) -> np.ndarray:
    local = t - start
    env = np.where(local >= 0, np.exp(-local / NOTE_DECAY_S), 0.0)
    shimmer = 1.0 + SHIMMER_DEPTH * np.sin(2 * np.pi * SHIMMER_HZ * local)
    tone = np.sin(2 * np.pi * freq * local) + 0.35 * np.sin(2 * np.pi * freq * 2 * local)
    return env * shimmer * tone


def main() -> None:
    t = np.arange(int(SAMPLE_RATE * TOTAL_S)) / SAMPLE_RATE
    mix = sum(note(f, i * NOTE_SPACING_S, t) for i, f in enumerate(NOTES_HZ))
    mix = mix / np.max(np.abs(mix)) * PEAK
    pcm = (mix * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        with wave.open(tmp.name, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SAMPLE_RATE)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp.name, "-c:a", "libvorbis", "-q:a", "4", str(OUT)], check=True)
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
