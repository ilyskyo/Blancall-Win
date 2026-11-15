"""由 blancall.png 生成 Windows 多尺寸图标。

Windows 的 .ico 装载器只在 256x256 上支持 PNG 压缩条目；16~192 若写成 PNG，
快捷方式/开始菜单取图标会失败并退回空白页图标。因此这里小尺寸一律写
32bpp BMP（BITMAPINFOHEADER + BGRA + AND 掩码），仅 256 写 PNG。

用法：python tools/make-windows-ico.py
"""

import struct
from io import BytesIO
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "desktop" / "src" / "main" / "resources" / "icon" / "blancall.png"
DST = SRC.with_suffix(".ico")
SIZES = (16, 24, 32, 48, 64, 128, 192, 256)


def to_dib(image: Image.Image) -> bytes:
    w, h = image.size
    px = image.load()
    xor = bytearray()
    for y in range(h - 1, -1, -1):
        for x in range(w):
            r, g, b, a = px[x, y]
            xor += bytes((b, g, r, a))
    mask_stride = (w + 31) // 32 * 4
    mask = bytes(mask_stride * h)
    header = struct.pack(
        "<IiiHHIIiiII", 40, w, h * 2, 1, 32, 0, len(xor) + len(mask), 0, 0, 0, 0
    )
    return header + bytes(xor) + mask


def main() -> None:
    master = Image.open(SRC).convert("RGBA")
    frames = []
    for size in SIZES:
        frame = master.resize((size, size), Image.Resampling.LANCZOS)
        if size >= 256:
            buf = BytesIO()
            frame.save(buf, format="PNG", optimize=True)
            frames.append((size, buf.getvalue()))
        else:
            frames.append((size, to_dib(frame)))

    offset = 6 + 16 * len(frames)
    directory = bytearray(struct.pack("<HHH", 0, 1, len(frames)))
    blobs = bytearray()
    for size, payload in frames:
        directory += struct.pack(
            "<BBBBHHII", size % 256, size % 256, 0, 0, 1, 32, len(payload), offset
        )
        blobs += payload
        offset += len(payload)
    DST.write_bytes(bytes(directory) + bytes(blobs))
    print(f"wrote {DST} ({len(directory) + len(blobs)} bytes)")


if __name__ == "__main__":
    main()
