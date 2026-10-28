#!/usr/bin/env python3
"""从 docs/texture-lab.html 里抽出像素数据，生成游戏用的方块贴图。

唯一数据源就是 texture-lab.html 里 <script id="tex-data"> 中的 JSON：
HTML 负责「画」，本脚本负责「导出成 PNG」，因此游戏内贴图与 HTML 预览逐像素一致。

用法（在项目根目录）：
    python tools/gen_textures.py
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
HTML = ROOT / "docs" / "texture-lab.html"
OUT_DIR = ROOT / "src" / "main" / "resources" / "assets" / "void_technology" / "textures" / "block"

# HTML 里的 <script id="tex-data" type="application/json"> ... </script>
PATTERN = re.compile(
    r'<script\s+id="tex-data"[^>]*>(.*?)</script>', re.DOTALL | re.IGNORECASE
)


def load_spec() -> dict:
    html = HTML.read_text(encoding="utf-8")
    match = PATTERN.search(html)
    if not match:
        sys.exit(f"[!] 没在 {HTML} 里找到 <script id=\"tex-data\"> 数据块")
    return json.loads(match.group(1))


def hex_to_rgba(value: str) -> tuple[int, int, int, int]:
    value = value.strip().lstrip("#")
    if len(value) == 6:
        value += "FF"
    if len(value) != 8:
        sys.exit(f"[!] 颜色格式不认识: {value}")
    return tuple(int(value[i : i + 2], 16) for i in (0, 2, 4, 6))  # type: ignore[return-value]


def render(spec: dict) -> Image.Image:
    size = int(spec["size"])
    palette = {k: hex_to_rgba(v) for k, v in spec["palette"].items()}
    rows = spec["rows"]
    if len(rows) != size:
        sys.exit(f"[!] 行数 {len(rows)} 与 size {size} 不符")

    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    pixels = img.load()
    for y, row in enumerate(rows):
        if len(row) != size:
            sys.exit(f"[!] 第 {y} 行宽度 {len(row)} 与 size {size} 不符")
        for x, ch in enumerate(row):
            if ch not in palette:
                sys.exit(f"[!] 第 {y} 行第 {x} 列出现调色板外的字符: {ch!r}")
            pixels[x, y] = palette[ch]  # type: ignore[index]
    return img


def main() -> None:
    spec = load_spec()
    img = render(spec)
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    targets = {
        "void_controller.png": img,
        # 结构成型时的发光版本：把烬色/高光换成更亮的橙白
        "void_controller_formed.png": _formed_variant(img),
    }
    for name, picture in targets.items():
        path = OUT_DIR / name
        picture.save(path, format="PNG", optimize=True)
        print(f"[+] {path.relative_to(ROOT)}  ({picture.width}x{picture.height})")


def _formed_variant(img: Image.Image) -> Image.Image:
    """结构成型版：只提亮红色系像素，暗部保持不动，用于方块状态切换。"""
    out = img.copy()
    pixels = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = pixels[x, y]  # type: ignore[misc]
            if a == 0:
                continue
            if r > 90:  # 红色系 → 提亮
                pixels[x, y] = (min(255, int(r * 1.18) + 18), min(255, int(g * 1.1) + 6), b, a)  # type: ignore[index]
    return out


if __name__ == "__main__":
    main()
