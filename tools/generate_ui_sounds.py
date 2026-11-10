"""Generate original, offline game cues as mono PCM WAV; no external packages required."""
import math
from pathlib import Path
import struct
import wave

RATE = 22050
DEST = Path(__file__).resolve().parents[1] / "app/src/main/res/raw"


def cue(name, notes, duration, volume=0.48):
    samples = [0.0] * int(RATE * duration)
    for start, length, frequency, gain in notes:
        for i in range(int(RATE * length)):
            index = int(start * RATE) + i
            if index >= len(samples):
                break
            t = i / RATE
            # Smooth attack/release, with a gentle bell-like decay and quiet overtone.
            envelope = min(1.0, t / 0.008) * min(1.0, (length - t) / 0.055)
            envelope *= math.exp(-3.0 * t / length)
            tone = math.sin(2 * math.pi * frequency * t)
            tone += 0.18 * math.sin(2 * math.pi * frequency * 2 * t)
            samples[index] += tone * envelope * gain * volume
    peak = max(1.0, max(abs(v) for v in samples) / 0.88)
    pcm = b"".join(struct.pack("<h", round(v / peak * 32767)) for v in samples)
    DEST.mkdir(parents=True, exist_ok=True)
    with wave.open(str(DEST / f"{name}.wav"), "wb") as output:
        output.setparams((1, 2, RATE, 0, "NONE", "not compressed"))
        output.writeframes(pcm)


if __name__ == "__main__":
    cue("sound_tap", [(0, 0.045, 880, 0.40)], 0.055, volume=0.25)
    cue("sound_correct", [(0, .23, 659.25, .75), (.10, .24, 830.61, .7),
                          (.20, .28, 987.77, .7), (.29, .30, 1318.51, .65)], .62)
    cue("sound_wrong", [(0, .24, 329.63, .8), (.13, .30, 261.63, .8)], .47)
    cue("sound_match_start", [(0, .25, 523.25, .7), (.12, .26, 659.25, .7),
                              (.24, .28, 783.99, .7), (.37, .42, 1046.50, .75),
                              (.40, .39, 659.25, .25), (.40, .39, 783.99, .25)], .84)
    # A longer ascending fanfare with a final major chord for completing any mini-game.
    cue("sound_celebration", [(0, .20, 523.25, .65), (.12, .20, 659.25, .65),
                              (.24, .20, 783.99, .65), (.36, .24, 1046.50, .7),
                              (.53, .22, 1318.51, .65), (.70, .60, 1567.98, .7),
                              (.70, .60, 1046.50, .40), (.70, .60, 1318.51, .35)], 1.35)
    print("Generated 5 original game sound effects (22050 Hz, 16-bit mono).")
