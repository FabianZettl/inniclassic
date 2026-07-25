#!/usr/bin/env python3
"""
Pre-builds InniClassic's on-device library cache (.y1_library_cache.json) on your PC.

Why: InniClassic already caches the whole scanned library to a JSON file at the SD
card's root. On the next scan, any file whose path is already in that cache gets
skipped entirely (no tag extraction) - only genuinely new files get the expensive
per-file work. This script does that tag extraction on your PC (mutagen is far
faster than the device's CPU) and writes the cache file directly, so a 14k-track
library scan on-device becomes a fast existence check instead of a full re-tag.

Usage:
    python3 build_library_cache.py /path/to/sdcard/mountpoint

Run this any time after copying new music onto the card, before putting it back
into the device. Safe to re-run - it always rebuilds the cache from what's
currently on the card.

Requires: pip install mutagen
"""
import argparse
import json
import os
import sys
from pathlib import Path

try:
    from mutagen import File as MutagenFile
except ImportError:
    print("This script needs the 'mutagen' package: pip install mutagen", file=sys.stderr)
    sys.exit(1)

# Keep this in sync with isAudioFile() in MainActivity.java
AUDIO_EXTENSIONS = {".mp3", ".flac", ".wav", ".ogg", ".m4a", ".aac", ".ape", ".wma", ".opus", ".m4b"}


def first_tag(tags, *keys):
    if not tags:
        return ""
    for key in keys:
        val = tags.get(key)
        if val:
            v = val[0] if isinstance(val, list) else val
            return str(v).strip()
    return ""


def extract_track_number(raw):
    if not raw:
        return 0
    digits = raw.split("/")[0].strip()  # "3/12" -> "3"
    try:
        return int(digits)
    except ValueError:
        return 0


def scan_folder(local_root: Path, device_root: str):
    entries = []
    for dirpath, _dirnames, filenames in os.walk(local_root):
        for name in filenames:
            if Path(name).suffix.lower() not in AUDIO_EXTENSIONS:
                continue
            local_path = Path(dirpath) / name
            rel = local_path.relative_to(local_root)
            device_path = device_root.rstrip("/") + "/" + str(rel).replace(os.sep, "/")

            title = artist = album = year = genre = album_artist = composer = ""
            track_num = 0
            try:
                audio = MutagenFile(local_path, easy=True)
                if audio is not None and audio.tags:
                    tags = audio.tags
                    title = first_tag(tags, "title")
                    artist = first_tag(tags, "artist")
                    album = first_tag(tags, "album")
                    year = first_tag(tags, "date", "year", "originaldate")[:4]
                    genre = first_tag(tags, "genre")
                    album_artist = first_tag(tags, "albumartist", "album artist")
                    composer = first_tag(tags, "composer")
                    track_num = extract_track_number(first_tag(tags, "tracknumber", "track"))
            except Exception as e:
                print(f"  ! couldn't read tags from {local_path}: {e}", file=sys.stderr)

            entries.append({
                "path": device_path,
                "title": title,
                "artist": artist,
                "album": album,
                "year": year,
                "genre": genre,
                "albumArtist": album_artist,
                "composer": composer,
                "trackNum": track_num,
            })
    return entries


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("sdcard", help="Path to the mounted SD card root (contains Music/ and/or Audiobooks/)")
    parser.add_argument("--device-root", default="/storage/sdcard0",
                         help="Root path as seen ON the device (default: /storage/sdcard0)")
    args = parser.parse_args()

    sdcard = Path(args.sdcard).expanduser().resolve()
    if not sdcard.is_dir():
        print(f"'{sdcard}' is not a folder.", file=sys.stderr)
        sys.exit(1)

    music_entries = []
    music_dir = sdcard / "Music"
    if music_dir.is_dir():
        print(f"Scanning {music_dir} ...")
        music_entries = scan_folder(music_dir, args.device_root + "/Music")
        print(f"  {len(music_entries)} tracks")
    else:
        print(f"No Music/ folder found under {sdcard}, skipping.")

    book_entries = []
    books_dir = sdcard / "Audiobooks"
    if books_dir.is_dir():
        print(f"Scanning {books_dir} ...")
        book_entries = scan_folder(books_dir, args.device_root + "/Audiobooks")
        print(f"  {len(book_entries)} audiobook files")

    out_path = sdcard / ".y1_library_cache.json"
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump({"music": music_entries, "books": book_entries}, f, ensure_ascii=False)

    print(f"\nWrote {out_path}")
    print("Eject the card and put it back in the Y1 - the next library scan should skip tag extraction for everything in here.")


if __name__ == "__main__":
    main()
