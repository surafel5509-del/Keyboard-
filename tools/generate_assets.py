#!/usr/bin/env python3
"""Generates the bundled offline assets for NOVA KEYBOARD.

Inputs (downloaded once by tools/fetch_assets.sh, never at runtime):

  * FrequencyWords English 50k list (MIT, (c) 2016 Hermit Dave)
        content/2018/en/en_50k.txt      "word count" (descending frequency)
  * NLTK Brown corpus (public domain text, (c) 1961 Brown University;
    distributed by the NLTK project in nltk_data/packages/corpora/brown.zip)
  * iamcal/emoji-data emoji.json (MIT, (c) 2013 Cal Henderson)

Outputs (committed to the repository, bundled inside the APK):

  app/src/main/assets/dictionaries/en_words.txt     "word<tab>count"
  app/src/main/assets/dictionaries/en_bigrams.txt   "word1<tab>word2<tab>count"
  app/src/main/assets/emoji/emoji.tsv               "emoji<tab>short<tab>name<tab>category<tab>subcategory"
  app/src/main/assets/sounds/key_click.wav          short synthesized click

Usage:
  python3 tools/generate_assets.py --frequency <en_50k.txt> \
      --brown <brown_corpus_dir> --emoji <emoji.json> --out app/src/main/assets
"""
import argparse
import collections
import json
import math
import os
import re
import struct
import sys
import wave

WORD_RE = re.compile(r"^[a-z][a-z']*[a-z]$|^[ai]$")


def generate_words(path, out_path, max_words=40000, min_count=3):
    entries = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split()
            if len(parts) != 2:
                continue
            word, count = parts[0].lower(), parts[1]
            try:
                count = int(count)
            except ValueError:
                continue
            if count < min_count:
                continue
            if not WORD_RE.match(word):
                continue
            entries.append((word, count))
    entries.sort(key=lambda e: (-e[1], e[0]))
    entries = entries[:max_words]
    entries.sort(key=lambda e: e[0])  # binary-search friendly on device
    with open(out_path, "w", encoding="utf-8") as f:
        for word, count in entries:
            f.write("%s\t%d\n" % (word, count))
    return len(entries)


def tokenize_brown(text):
    """Brown corpus is word/tag per whitespace token; strip tags, keep sentence ends."""
    words = []
    for raw in text.split():
        token = raw.split("/")[0]
        token = token.strip().lower()
        if token in {".", "?", "!"}:
            words.append(None)  # sentence boundary
            continue
        if WORD_RE.match(token):
            words.append(token)
    return words


def generate_bigrams(brown_dir, out_path, max_bigrams=20000, min_count=3):
    counts = collections.Counter()
    for root, _, files in os.walk(brown_dir):
        for name in sorted(files):
            if not name or name.startswith("."):
                continue
            try:
                with open(os.path.join(root, name), "r", encoding="utf-8", errors="ignore") as f:
                    tokens = tokenize_brown(f.read())
            except (IsADirectoryError, PermissionError):
                continue
            prev = None
            for token in tokens:
                if token is None:
                    prev = None
                    continue
                if prev is not None:
                    counts[(prev, token)] += 1
                prev = token
    common = [w for w, _ in collections.Counter(
        t.lower() for root, _, files in os.walk(brown_dir)
        for name in files for t in tokenize_brown(
            open(os.path.join(root, name), errors="ignore").read()) if t is not None).most_common(6000)]
    allowed = set(common)
    filtered = [(pair, count) for pair, count in counts.items()
                if count >= min_count and pair[0] in allowed and pair[1] in allowed]
    filtered.sort(key=lambda e: (-e[1], e[0]))
    filtered = filtered[:max_bigrams]
    filtered.sort(key=lambda e: (e[0][0], -e[1], e[0][1]))
    with open(out_path, "w", encoding="utf-8") as f:
        for (first, second), count in filtered:
            f.write("%s\t%s\t%d\n" % (first, second, count))
    return len(filtered)


def decode_emoji(unified):
    # unified is like "0023-FE0F-20E3"; strip the FE0F variation selector only when
    # the sequence is a keycap (keeps ZWJ sequences and skin tones intact).
    parts = [int(p, 16) for p in unified.split("-")]
    if len(parts) > 1 and 0xFE0F in parts and not any(p == 0x200D for p in parts):
        parts = [p for p in parts if p != 0xFE0F]
    return "".join(chr(p) for p in parts)


def generate_emoji(path, out_path):
    data = json.load(open(path, encoding="utf-8"))
    rows = []
    for entry in data:
        category = entry.get("category") or ""
        if category == "Component":
            continue
        emoji = decode_emoji(entry["unified"])
        short = (entry.get("short_name") or "").replace("_", " ")
        name = (entry.get("name") or "").replace(":", "").lower()
        subcategory = (entry.get("subcategory") or "").replace("-", " ")
        rows.append((entry.get("sort_order", 0), emoji, short, name, category, subcategory))
    rows.sort()
    with open(out_path, "w", encoding="utf-8") as f:
        for _, emoji, short, name, category, subcategory in rows:
            f.write("%s\t%s\t%s\t%s\t%s\n" % (emoji, short.replace("\t", " "), name.replace("\t", " "), category, subcategory))
    return len(rows)


def generate_click(path, sample_rate=44100):
    """Synthesizes a short, soft mechanical click (no external audio assets needed)."""
    duration = 0.030
    n = int(sample_rate * duration)
    frames = bytearray()
    for i in range(n):
        t = i / sample_rate
        env = math.exp(-t * 150.0)
        # two quick noise-ish partials give a "tick" character without sounding harsh
        s = (math.sin(2 * math.pi * 2100 * t) * 0.55 + math.sin(2 * math.pi * 3400 * t) * 0.28)
        s *= env
        s = max(-1.0, min(1.0, s * 0.45))
        frames += struct.pack("<h", int(s * 32767))
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sample_rate)
        w.writeframes(bytes(frames))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--frequency")
    ap.add_argument("--brown")
    ap.add_argument("--emoji")
    ap.add_argument("--out", default="app/src/main/assets")
    args = ap.parse_args()

    dict_dir = os.path.join(args.out, "dictionaries")
    emoji_dir = os.path.join(args.out, "emoji")
    sound_dir = os.path.join(args.out, "sounds")
    for d in (dict_dir, emoji_dir, sound_dir):
        os.makedirs(d, exist_ok=True)

    if args.frequency:
        n = generate_words(args.frequency, os.path.join(dict_dir, "en_words.txt"))
        print("en_words.txt: %d words" % n)
    if args.brown:
        n = generate_bigrams(args.brown, os.path.join(dict_dir, "en_bigrams.txt"))
        print("en_bigrams.txt: %d bigrams" % n)
    if args.emoji:
        n = generate_emoji(args.emoji, os.path.join(emoji_dir, "emoji.tsv"))
        print("emoji.tsv: %d emoji" % n)
    generate_click(os.path.join(sound_dir, "key_click.wav"))
    print("key_click.wav written")


if __name__ == "__main__":
    sys.exit(main())
