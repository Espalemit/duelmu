"""
Dekoder PNG minimal (tanpa dependensi) untuk validator Duelmu.

Mendukung PNG 8-bit non-interlaced (grayscale, RGB, RGBA, gray+alpha) yang dipakai paket
soal bergambar. Dipakai untuk:
  - memastikan setiap PNG benar-benar dapat didekode (CRC chunk, zlib, filter baris);
  - membandingkan piksel PNG ZIP dengan gambar yang tertanam di PDF (pasangan soal-gambar).
"""
from __future__ import annotations

import hashlib
import struct
import zlib
from dataclasses import dataclass

PNG_SIG = b"\x89PNG\r\n\x1a\n"
CHANNELS = {0: 1, 2: 3, 4: 2, 6: 4}


class PngError(Exception):
    pass


@dataclass
class Png:
    width: int
    height: int
    channels: int
    pixels: bytes  # baris demi baris, tanpa byte filter

    def rgb(self) -> bytes:
        """Kanal warna saja (RGB) agar bisa dibandingkan dengan gambar PDF (alpha disimpan terpisah)."""
        if self.channels == 3:
            return self.pixels
        if self.channels == 4:
            p = self.pixels
            out = bytearray(len(p) // 4 * 3)
            out[0::3] = p[0::4]
            out[1::3] = p[1::4]
            out[2::3] = p[2::4]
            return bytes(out)
        raise PngError("bukan gambar RGB/RGBA")

    def alpha(self) -> bytes | None:
        if self.channels == 4:
            return self.pixels[3::4]
        if self.channels == 2:
            return self.pixels[1::2]
        return None

    def pixel_sha256(self) -> str:
        return hashlib.sha256(self.pixels).hexdigest()

    def distinct_sample_colors(self, step: int = 97) -> int:
        c = self.channels
        return len({self.pixels[i:i + c] for i in range(0, len(self.pixels) - c, step * c)})


def read_header(data: bytes) -> tuple[int, int, int, int]:
    if data[:8] != PNG_SIG:
        raise PngError("signature PNG tidak valid")
    if data[12:16] != b"IHDR":
        raise PngError("chunk pertama bukan IHDR")
    w, h, depth, ctype = struct.unpack(">IIBB", data[16:26])
    return w, h, depth, ctype


def decode(data: bytes) -> Png:
    w, h, depth, ctype = read_header(data)
    if depth != 8 or ctype not in CHANNELS:
        raise PngError(f"format tidak didukung: depth={depth} colorType={ctype}")
    pos = 8
    idat = bytearray()
    interlace = None
    seen_end = False
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        ctype_b = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        (crc,) = struct.unpack(">I", data[pos + 8 + length:pos + 12 + length])
        if zlib.crc32(ctype_b + body) & 0xFFFFFFFF != crc:
            raise PngError(f"CRC chunk {ctype_b!r} salah")
        if ctype_b == b"IHDR":
            interlace = body[12]
        elif ctype_b == b"IDAT":
            idat += body
        elif ctype_b == b"IEND":
            seen_end = True
            break
        pos += 12 + length
    if not seen_end:
        raise PngError("IEND tidak ditemukan (file terpotong)")
    if interlace:
        raise PngError("PNG interlaced tidak didukung")
    try:
        raw = zlib.decompress(bytes(idat))
    except zlib.error as e:
        raise PngError(f"zlib rusak: {e}") from e
    bpp = CHANNELS[ctype]
    stride = w * bpp
    if len(raw) != h * (stride + 1):
        raise PngError("ukuran data piksel tidak sesuai IHDR")
    out = bytearray(h * stride)
    prev = bytearray(stride)
    for y in range(h):
        f = raw[y * (stride + 1)]
        line = bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        if f == 0:
            pass
        elif f == 1:
            for i in range(bpp, stride):
                line[i] = (line[i] + line[i - bpp]) & 0xFF
        elif f == 2:
            line = bytearray((a + b) & 0xFF for a, b in zip(line, prev))
        elif f == 3:
            for i in range(stride):
                left = line[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + ((left + prev[i]) >> 1)) & 0xFF
        elif f == 4:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                b = prev[i]
                c = prev[i - bpp] if i >= bpp else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        else:
            raise PngError(f"filter baris tidak dikenal: {f}")
        out[y * stride:(y + 1) * stride] = line
        prev = line
    return Png(w, h, bpp, bytes(out))
