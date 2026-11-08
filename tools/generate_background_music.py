"""Compose original Duelmu music locally; Python standard library plus FFmpeg/Vorbis."""
from array import array
import argparse
import math
from pathlib import Path
import random
import shutil
import struct
import subprocess
import wave

RATE = 22050
ROOT = Path(__file__).resolve().parents[1]
TAU = 2 * math.pi


def render(name, bpm, chords, competitive, ffmpeg):
    beat = 60 / bpm
    length = round(len(chords) * 4 * beat * RATE)
    left = array('f', [0.0]) * length
    right = array('f', [0.0]) * length
    rng = random.Random(3204 if competitive else 7804)

    def mix(start, duration, midi, gain, voice='pluck', pan=0.0):
        first = round(start * RATE)
        count = round(duration * RATE)
        frequency = 440 * 2 ** ((midi - 69) / 12)
        lg, rg = math.sqrt((1 - pan) / 2), math.sqrt((1 + pan) / 2)
        for i in range(count):
            t = i / RATE
            position = (first + i) % length  # carry note tails over the loop boundary
            if voice == 'pad':
                env = min(1, t / .22) * min(1, (duration - t) / .45)
                value = (math.sin(TAU * frequency * t) + .22 * math.sin(TAU * frequency * 1.003 * t)) * env
            elif voice == 'bass':
                env = min(1, t / .012) * min(1, (duration - t) / .07) * math.exp(-1.3 * t / duration)
                value = (math.sin(TAU * frequency * t) + .22 * math.sin(TAU * frequency * 2 * t)) * env
            else:
                env = min(1, t / .008) * min(1, (duration - t) / .045) * math.exp(-4 * t / duration)
                value = (math.sin(TAU * frequency * t) + .22 * math.sin(TAU * frequency * 2 * t)
                         + .06 * math.sin(TAU * frequency * 3 * t)) * env
            left[position] += value * gain * lg
            right[position] += value * gain * rg

    def drum(start, kind, gain):
        duration = {'kick': .24, 'snare': .16, 'hat': .07}[kind]
        first = round(start * RATE)
        previous = 0.0
        for i in range(round(duration * RATE)):
            t = i / RATE
            noise = rng.uniform(-1, 1)
            if kind == 'kick':
                phase = TAU * (44 * t + 95 * (1 - math.exp(-38 * t)) / 38)
                value = math.sin(phase) * math.exp(-18 * t) * min(1, t / .003)
            elif kind == 'snare':
                value = (.7 * noise + .3 * math.sin(TAU * 185 * t)) * math.exp(-35 * t) * min(1, t / .003)
            else:
                value = (noise - previous) * math.exp(-65 * t) * min(1, t / .002)
            previous = noise
            value *= gain * min(1, (duration - t) / .015)
            left[(first + i) % length] += value * .707
            right[(first + i) % length] += value * .707

    for bar, chord in enumerate(chords):
        start = bar * 4 * beat
        root = chord[0] - 24
        for index, note in enumerate(chord):
            mix(start, beat * 4.15, note, .034 if competitive else .045, 'pad', (index - 1.5) * .3)
        if competitive:
            for step, note in enumerate([root, root, root + 12, root + 7, root, root + 12, root + 7, root + 12]):
                mix(start + step * beat / 2, beat * .40, note, .15, 'bass')
            pattern = [0, 2, 1, 2, 3, 2, 1, 2] if bar % 4 < 2 else [2, 1, 3, 2, 1, 0, 2, 3]
            for step, index in enumerate(pattern):
                mix(start + step * beat / 2, beat * .75, chord[index] + 12, .11, pan=(-.22 if step % 2 else .22))
            for step in (0, 2, 3.5):
                drum(start + step * beat, 'kick', .22)
            for step in (1, 3):
                drum(start + step * beat, 'snare', .085)
            for step in range(8):
                drum(start + step * beat / 2, 'hat', .030 if step % 2 else .018)
        else:
            mix(start, beat * 3.7, root, .105, 'bass')
            melody = [(0, chord[0] + 12), (1.5, chord[2] + 12), (2.5, chord[1] + 12)]
            if bar % 2:
                melody = [(0.5, chord[3] + 12), (2, chord[2] + 12), (3, chord[1] + 12)]
            for step, note in melody:
                mix(start + step * beat, beat * 1.4, note, .11, pan=.28 if step < 2 else -.28)
            for step in (1.5, 3.5):
                drum(start + step * beat, 'hat', .012)

    peak = max(max(abs(v) for v in left), max(abs(v) for v in right))
    gain = .78 / peak
    pcm = bytearray(length * 4)
    fade = round(RATE * .018)
    for i, (l, r) in enumerate(zip(left, right)):
        edge = min(1, i / fade, (length - 1 - i) / fade)
        struct.pack_into('<hh', pcm, i * 4, round(l * gain * edge * 32767), round(r * gain * edge * 32767))
    temp = ROOT / '.tmp/music-render'
    temp.mkdir(parents=True, exist_ok=True)
    wav = temp / f'{name}.wav'
    with wave.open(str(wav), 'wb') as out:
        out.setparams((2, 2, RATE, 0, 'NONE', 'not compressed'))
        out.writeframes(pcm)
    destination = ROOT / 'app/src/main/res/raw' / f'{name}.ogg'
    subprocess.run([ffmpeg, '-hide_banner', '-loglevel', 'error', '-y', '-i', str(wav),
                    '-c:a', 'libvorbis', '-q:a', '4', str(destination)], check=True)
    print(f'{name}: {bpm} BPM, {length / RATE:.2f}s, {destination.stat().st_size} bytes', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ffmpeg', default=shutil.which('ffmpeg'))
    args = parser.parse_args()
    if not args.ffmpeg:
        parser.error('FFmpeg is required to encode the original tracks as Ogg Vorbis.')
    render('music_menu', 78, [[60, 64, 67, 71], [57, 60, 64, 67], [53, 57, 60, 64], [55, 59, 62, 69],
                             [52, 55, 59, 62], [57, 60, 64, 67], [50, 53, 57, 60], [55, 59, 62, 65]], False, args.ffmpeg)
    render('music_competitive', 128, [[62, 65, 69, 72], [58, 62, 65, 69], [60, 65, 69, 72], [60, 64, 67, 74]] * 4, True, args.ffmpeg)
