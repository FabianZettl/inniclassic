#!/usr/bin/env python3
"""Builds a single-frame MTK "LOGO" boot-splash partition (logo.bin) for the
Innioasis Y1, showing white text centered on a black background.

Format was reverse-engineered from two real logo.bin samples (Innioasis
stock firmware and the Rockbox-derived one this project's rom.zip carried
until now) - see tools/rom_assets/README.md for the full writeup. Summary:

  offset 0-3    constant tag/CRC, always 88 16 88 58 in both real samples
  offset 4-7    uint32 LE, must equal the table's total_size field below
  offset 8-11   ASCII "LOGO"
  offset 12-511 reserved, byte-identical (mostly 0x00 then 0xFF padding)
                between both real samples - copied verbatim from a real
                sample rather than reconstructed
  offset 512    uint32 LE frame_count
  offset 516    uint32 LE total_size (== offset 4-7 above)
  offset 520.. frame_count x uint32 LE cumulative offsets (only fully
                understood for frame_count == 1, where offsets[0] is just
                the single compressed frame's length - the real multi-frame
                animation's table semantics were NOT fully solved, hence
                this script only ever emits a single static frame)
  offset ...    zlib-compressed RGB565 raw frame data, 480x360 (the Y1's
                real panel resolution, confirmed via successfully decoding
                both real samples at this exact size)

Usage: python3 build_boot_logo.py "InniClassic" output/logo.bin
"""
import struct
import sys
import zlib

from PIL import Image, ImageDraw, ImageFont

W, H = 480, 360
FONT_PATH = "/usr/share/fonts/liberation/LiberationSans-Bold.ttf"
FONT_SIZE = 44

# A known-good real logo.bin's first 512 bytes, used as a template for the
# constant/reserved header region (bytes 0-3, 8-511) - see module docstring.
REFERENCE_LOGO_BIN = "tools/rom_assets/logo_stock_innioasis.bin"


def render_text_image(text: str) -> Image.Image:
    img = Image.new("RGB", (W, H), (0, 0, 0))
    draw = ImageDraw.Draw(img)
    font = ImageFont.truetype(FONT_PATH, FONT_SIZE)
    bbox = draw.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    x = (W - tw) / 2 - bbox[0]
    y = (H - th) / 2 - bbox[1]
    draw.text((x, y), text, font=font, fill=(255, 255, 255))
    return img


def to_rgb565_bytes(img: Image.Image) -> bytes:
    import numpy as np

    arr = np.asarray(img.convert("RGB")).astype(np.uint16)
    r, g, b = arr[:, :, 0], arr[:, :, 1], arr[:, :, 2]
    px565 = ((r >> 3) << 11) | ((g >> 2) << 5) | (b >> 3)
    return px565.astype("<u2").tobytes()


def build_logo_bin(img: Image.Image, reference_path: str) -> bytes:
    raw = to_rgb565_bytes(img)
    assert len(raw) == W * H * 2, "unexpected raw frame size"
    compressed = zlib.compress(raw, 9)

    header = bytearray(open(reference_path, "rb").read(512))
    header[4:8] = struct.pack("<I", len(compressed))

    table = struct.pack("<II", 1, len(compressed)) + struct.pack("<I", len(compressed))
    return bytes(header) + table + compressed


def verify(data: bytes) -> None:
    """Round-trip check: re-parse our own output the same way we parsed the
    two real samples, and confirm it decompresses cleanly with no leftover
    bytes."""
    count, total = struct.unpack_from("<II", data, 512)
    assert count == 1
    (frame_len,) = struct.unpack_from("<I", data, 520)
    assert frame_len == total
    block_start = 520 + 4 * count
    d = zlib.decompressobj()
    raw = d.decompress(data[block_start:])
    assert len(raw) == W * H * 2, f"decompressed to {len(raw)}, expected {W*H*2}"
    assert not d.unused_data, "trailing bytes after the single frame"


if __name__ == "__main__":
    text = sys.argv[1] if len(sys.argv) > 1 else "InniClassic"
    out_path = sys.argv[2] if len(sys.argv) > 2 else "logo_out.bin"

    img = render_text_image(text)
    data = build_logo_bin(img, REFERENCE_LOGO_BIN)
    verify(data)

    with open(out_path, "wb") as f:
        f.write(data)
    print(f"Wrote {out_path} ({len(data)} bytes, partition limit is 3145728)")
