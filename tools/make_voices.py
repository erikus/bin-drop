# /// script
# requires-python = ">=3.10,<3.13"
# dependencies = ["piper-tts>=1.3", "numpy"]
# ///
"""Synthesizes the bins' voice clips: each bin has its own voice and says every colour word it can be.

Writes app/src/main/res/raw/voice_<bin>_<word>.ogg for every voice in VOICES and every word in WORDS.
Voice models are cached in tools/.piper-voices (gitignored) and downloaded on first run.

Usage: uv run tools/make_voices.py   (needs ffmpeg on PATH for the pitch shift and OGG encode)
"""

import subprocess
import sys
import tempfile
import wave
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from piper import PiperVoice, SynthesisConfig

ROOT = Path(__file__).resolve().parent.parent
VOICE_DIR = ROOT / "tools/.piper-voices"
OUT_DIR = ROOT / "app/src/main/res/raw"

# The colour words a toddler learns first; each palette entry maps onto one of these (Palette.kt).
WORDS = ["blue", "red", "pink", "brown", "grey", "orange", "purple", "green", "yellow"]

OUT_RATE = 44100
PEAK = 0.71                 # about -3 dBFS, matching the other clips
TRIM_THRESHOLD = 0.02       # of peak: leading/trailing samples quieter than this are cut
TAIL_PAD_S = 0.04


@dataclass(frozen=True)
class Voice:
    model: str              # Piper voice name, see https://huggingface.co/rhasspy/piper-voices
    speaker: int            # speaker id inside a multi-speaker model (0 for single-speaker models)
    pitch: float            # playback-rate multiplier: >1 raises the pitch and quickens the clip
    length_scale: float     # Piper phoneme duration before the pitch shift; the two roughly cancel in tempo
    noise_w_scale: float    # more = livelier, less even prosody


# One voice per bin index, all under permissive dataset licences (see ASSETS.md). Pitched up so they read as
# bright and cheerful rather than newsreader-flat, and spaced apart in pitch (about 205 / 135 / 245 Hz after
# the shift) so the three bins sound like three characters.
VOICES = [
    Voice("en_GB-alba-medium", speaker=0, pitch=1.08, length_scale=1.05, noise_w_scale=0.9),
    Voice("en_US-joe-medium", speaker=0, pitch=1.2, length_scale=1.1, noise_w_scale=0.9),
    Voice("en_GB-aru-medium", speaker=5, pitch=1.15, length_scale=1.05, noise_w_scale=0.9),
]


def ensure_models() -> None:
    missing = [v.model for v in VOICES if not (VOICE_DIR / f"{v.model}.onnx").exists()]
    if not missing:
        return
    VOICE_DIR.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [sys.executable, "-m", "piper.download_voices", "--data-dir", str(VOICE_DIR), *missing],
        check=True,
    )


def synthesize(voice: PiperVoice, cfg: SynthesisConfig, text: str) -> tuple[np.ndarray, int]:
    with tempfile.NamedTemporaryFile(suffix=".wav") as tmp:
        with wave.open(tmp.name, "wb") as f:
            voice.synthesize_wav(text, f, syn_config=cfg)
        with wave.open(tmp.name, "rb") as f:
            rate = f.getframerate()
            pcm = np.frombuffer(f.readframes(f.getnframes()), dtype=np.int16)
    return pcm.astype(np.float64) / 32768.0, rate


def trim_and_normalize(samples: np.ndarray, rate: int) -> np.ndarray:
    peak = np.max(np.abs(samples))
    loud = np.flatnonzero(np.abs(samples) > peak * TRIM_THRESHOLD)
    start, end = loud[0], loud[-1] + 1
    cut = samples[start:end]
    pad = np.zeros(int(rate * TAIL_PAD_S))
    cut = np.concatenate([pad, cut, pad])
    return cut / peak * PEAK


def write_ogg(samples: np.ndarray, rate: int, pitch: float, out: Path) -> None:
    with tempfile.NamedTemporaryFile(suffix=".wav") as tmp:
        with wave.open(tmp.name, "wb") as f:
            f.setnchannels(1)
            f.setsampwidth(2)
            f.setframerate(rate)
            f.writeframes((samples * 32767).astype(np.int16).tobytes())
        # asetrate shifts pitch (and tempo) by resampling; aresample brings it back to the project's rate.
        subprocess.run(
            [
                "ffmpeg", "-loglevel", "error", "-y", "-i", tmp.name,
                "-af", f"asetrate={rate * pitch:.0f},aresample={OUT_RATE}",
                "-ac", "1", "-c:a", "libvorbis", "-q:a", "4", str(out),
            ],
            check=True,
        )


def main() -> None:
    ensure_models()
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for bin_index, spec in enumerate(VOICES):
        voice = PiperVoice.load(VOICE_DIR / f"{spec.model}.onnx")
        cfg = SynthesisConfig(speaker_id=spec.speaker, length_scale=spec.length_scale, noise_w_scale=spec.noise_w_scale)
        for word in WORDS:
            samples, rate = synthesize(voice, cfg, f"{word.capitalize()}!")
            samples = trim_and_normalize(samples, rate)
            out = OUT_DIR / f"voice_{bin_index}_{word}.ogg"
            write_ogg(samples, rate, spec.pitch, out)
            print(f"{out.name}: {len(samples) / rate / spec.pitch:.2f}s")


if __name__ == "__main__":
    main()
