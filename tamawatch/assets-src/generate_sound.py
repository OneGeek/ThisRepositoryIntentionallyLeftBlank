"""TamaWatch sound generator. Emits mono 16-bit 22.05 kHz chiptune WAVs for
every sound ID in the Authoritative Generation Set (docs/04). All original."""

import os
import wave
import struct
import numpy as np

SR = 22050
MANIFEST = {}

# note name -> frequency (Hz), a small chiptune scale
_A4 = 440.0
_NOTES = {}
_NAMES = ["C", "Cs", "D", "Ds", "E", "F", "Fs", "G", "Gs", "A", "As", "B"]
for octave in range(2, 8):
    for i, n in enumerate(_NAMES):
        midi = 12 * (octave + 1) + i
        _NOTES[f"{n}{octave}"] = _A4 * (2 ** ((midi - 69) / 12.0))


def _osc(freq, dur, wave_type="square", duty=0.5, vol=0.5):
    n = max(1, int(SR * dur))
    t = np.arange(n) / SR
    if freq <= 0:  # rest
        return np.zeros(n)
    phase = (t * freq) % 1.0
    if wave_type == "square":
        y = np.where(phase < duty, 1.0, -1.0)
    elif wave_type == "triangle":
        y = 2 * np.abs(2 * (phase - 0.5)) - 1
    elif wave_type == "saw":
        y = 2 * phase - 1
    elif wave_type == "sine":
        y = np.sin(2 * np.pi * phase)
    elif wave_type == "noise":
        y = np.random.uniform(-1, 1, n)
    else:
        y = np.sin(2 * np.pi * phase)
    return y * vol


def _env(y, a=0.01, d=0.02, s=0.7, r=0.05):
    n = len(y)
    env = np.ones(n)
    ai = int(a * SR); di = int(d * SR); ri = int(r * SR)
    ai = min(ai, n);
    if ai > 0:
        env[:ai] = np.linspace(0, 1, ai)
    if di > 0 and ai + di <= n:
        env[ai:ai + di] = np.linspace(1, s, di)
        env[ai + di:] = s
    if ri > 0 and ri < n:
        env[-ri:] *= np.linspace(1, 0, ri)
    return y * env


def seq(notes, wave_type="square", vol=0.5, gap=0.0, duty=0.5):
    """notes = list of (name_or_freq, dur). Concatenate with tiny env each."""
    out = []
    for name, dur in notes:
        f = _NOTES.get(name, 0) if isinstance(name, str) else name
        seg = _env(_osc(f, dur, wave_type, duty, vol), a=0.005, d=0.01, s=0.85, r=min(0.03, dur * 0.4))
        out.append(seg)
        if gap:
            out.append(np.zeros(int(SR * gap)))
    return np.concatenate(out) if out else np.zeros(1)


def mix(*layers):
    m = max(len(x) for x in layers)
    acc = np.zeros(m)
    for x in layers:
        acc[:len(x)] += x
    return acc


def sweep(f0, f1, dur, wave_type="square", vol=0.5):
    n = int(SR * dur)
    t = np.arange(n) / SR
    freq = np.linspace(f0, f1, n)
    phase = np.cumsum(freq) / SR
    ph = phase % 1.0
    if wave_type == "square":
        y = np.where(ph < 0.5, 1.0, -1.0)
    elif wave_type == "noise":
        y = np.random.uniform(-1, 1, n)
    else:
        y = np.sin(2 * np.pi * ph)
    return _env(y * vol, a=0.005, d=0.01, s=0.9, r=dur * 0.3)


def save(name, y, out_dir):
    y = np.clip(y, -1.0, 1.0)
    pcm = (y * 32767 * 0.85).astype(np.int16)
    path = os.path.join(out_dir, name + ".wav")
    with wave.open(path, "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    MANIFEST[name] = {"file": name + ".wav", "sr": SR, "len": round(len(y) / SR, 3)}


def generate(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    np.random.seed(7)  # deterministic noise

    # --- UI
    save("sfx_select", seq([("E6", 0.05)], "square", 0.35, duty=0.25), out_dir)
    save("sfx_confirm", seq([("C6", 0.06), ("G6", 0.08)], "square", 0.4), out_dir)
    save("sfx_cancel", seq([("G5", 0.06), ("C5", 0.09)], "square", 0.4), out_dir)
    save("sfx_error", seq([("Ds5", 0.12), ("Cs5", 0.14)], "square", 0.45, duty=0.5), out_dir)

    # --- Care
    save("sfx_eat", mix(seq([("C5", 0.08), (0, 0.04), ("C5", 0.08), (0, 0.04), ("E5", 0.1)], "triangle", 0.5)), out_dir)
    save("sfx_drink", sweep(500, 900, 0.35, "sine", 0.4), out_dir)
    save("sfx_refuse", seq([("A4", 0.12), ("F4", 0.16)], "square", 0.4, duty=0.35), out_dir)
    save("sfx_flush", mix(sweep(900, 200, 0.5, "noise", 0.35), sweep(600, 150, 0.5, "sine", 0.2)), out_dir)
    save("sfx_heal", seq([("C6", 0.1), ("E6", 0.1), ("G6", 0.15)], "sine", 0.4), out_dir)
    save("sfx_pet", seq([("E6", 0.06), ("A6", 0.1)], "triangle", 0.4), out_dir)

    # --- Emotion
    save("sfx_happy", seq([("C6", 0.08), ("E6", 0.08), ("G6", 0.08), ("C7", 0.14)], "square", 0.45, duty=0.25), out_dir)
    save("sfx_sad", seq([("E5", 0.12), ("C5", 0.12), ("A4", 0.16)], "triangle", 0.4), out_dir)
    save("sfx_sick", mix(sweep(400, 300, 0.5, "square", 0.3), sweep(410, 305, 0.5, "sine", 0.15)), out_dir)
    save("sfx_sleep", seq([("C5", 0.2), ("C4", 0.3), (0, 0.05), ("C5", 0.15), ("C4", 0.1)], "triangle", 0.35), out_dir)
    save("sfx_call", seq([("G6", 0.1), (0, 0.05), ("G6", 0.1), (0, 0.05), ("G6", 0.12), ("C7", 0.2)], "square", 0.5, duty=0.25), out_dir)
    save("sfx_talk_react", seq([("A5", 0.08), ("D6", 0.08), ("A5", 0.08), ("D6", 0.14)], "square", 0.4), out_dir)

    # --- Events
    save("sfx_hatch", seq([("C6", 0.1), ("E6", 0.1), ("G6", 0.1), ("C7", 0.15), ("E7", 0.2), ("C7", 0.25)], "square", 0.45, duty=0.3), out_dir)
    save("sfx_evolve", mix(
        seq([("C6", 0.12), ("E6", 0.12), ("G6", 0.12), ("C7", 0.12), ("E7", 0.18), ("G7", 0.24), ("C7", 0.3)], "square", 0.4, duty=0.25),
        seq([(0, 0.24), ("C5", 0.12), ("E5", 0.12), ("G5", 0.6)], "triangle", 0.25)), out_dir)
    save("sfx_goal", seq([("G6", 0.1), ("C7", 0.1), ("E7", 0.2)], "square", 0.45, duty=0.3), out_dir)
    save("sfx_coin", seq([("B6", 0.05), ("E7", 0.12)], "square", 0.4, duty=0.25), out_dir)
    save("sfx_buy", seq([("E6", 0.07), ("G6", 0.07), ("C7", 0.14)], "square", 0.4), out_dir)
    save("sfx_farewell", seq([("G5", 0.25), ("E5", 0.25), ("C5", 0.3), (0, 0.05), ("E5", 0.2), ("C5", 0.45)], "triangle", 0.4), out_dir)
    save("sfx_levelup", seq([("C6", 0.08), ("G6", 0.08), ("C7", 0.2)], "square", 0.45), out_dir)

    # --- Mini-games
    save("sfx_game_start", seq([("C6", 0.08), ("E6", 0.08), ("G6", 0.12)], "square", 0.45), out_dir)
    save("sfx_jump", sweep(400, 800, 0.15, "square", 0.4), out_dir)
    save("sfx_score", seq([("E6", 0.05), ("A6", 0.1)], "square", 0.4, duty=0.25), out_dir)
    save("sfx_miss", seq([("F4", 0.1), ("C4", 0.15)], "square", 0.4, duty=0.5), out_dir)
    save("sfx_game_win", seq([("C6", 0.1), ("E6", 0.1), ("G6", 0.1), ("C7", 0.1), ("G6", 0.1), ("C7", 0.25)], "square", 0.45, duty=0.3), out_dir)
    save("sfx_game_lose", seq([("E5", 0.12), ("Ds5", 0.12), ("D5", 0.12), ("Cs5", 0.25)], "square", 0.4), out_dir)

    # --- optional loop bed (4s) : simple arpeggio, meant to loop
    bass = seq([("C4", 0.5), ("G4", 0.5)] * 4, "triangle", 0.25)
    lead = seq([("C6", 0.25), ("E6", 0.25), ("G6", 0.25), ("E6", 0.25)] * 4, "square", 0.18, duty=0.25)
    save("mus_game_loop", mix(bass, lead), out_dir)

    return MANIFEST
