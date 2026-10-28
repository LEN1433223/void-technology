#!/usr/bin/env python3
"""渲染 docs/block-art.html 得到虚空样板供应器的三个面贴图，并生成方块状态/模型/掉落表。

要点
----
* 贴图由 HTML 绘制（与 GUI 同一套配色），这里只负责把浏览器渲染结果切成 16×16 面贴图；
* 切图用最近邻，保证像素级不被抹平；
* 方块状态必须与 AE2 的 PatternProviderBlock 对齐：它只有 `push_direction` 一个属性，
  取值是方向名（all/up/down/north/south/east/west），**没有** facing 属性。

用法：python tools/render_block_art.py
"""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
HTML = ROOT / "docs" / "block-art.html"
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "void_technology"
DATA = ROOT / "src" / "main" / "resources" / "data" / "void_technology"

RENDER = 384          # 3 面 × 每面 128 渲染像素
FACE = 16             # 每面最终 16×16

BROWSERS = [
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
]

# AE2 PatternProviderBlock 的唯一状态属性（值即方向名）
PUSH_DIRECTIONS = ["all", "up", "down", "north", "south", "east", "west"]


def find_browser() -> str:
    for candidate in BROWSERS:
        if Path(candidate).exists():
            return candidate
    sys.exit("[!] 找不到 Chrome / Edge")


def render_strip() -> Image.Image:
    browser = find_browser()
    with tempfile.TemporaryDirectory(prefix="vtblock-") as tmp:
        profile = Path(tmp) / "profile"
        out = Path(tmp) / "faces.png"
        cmd = [
            browser, "--headless=new", "--disable-gpu", "--hide-scrollbars",
            "--no-first-run", "--no-default-browser-check", "--disable-extensions",
            "--force-device-scale-factor=1", f"--user-data-dir={profile}",
            "--virtual-time-budget=1200",
            f"--window-size={RENDER},{RENDER // 3}",
            "--default-background-color=00000000",
            f"--screenshot={out}", HTML.as_uri(),
        ]
        subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=120)
        if not out.exists():
            sys.exit("[!] 方块贴图渲染失败")
        return Image.open(out).convert("RGBA")


def split_faces(strip: Image.Image) -> dict[str, Image.Image]:
    cell = strip.width // 3
    faces = {}
    for i, name in enumerate(("front", "side", "top")):
        crop = strip.crop((i * cell, 0, (i + 1) * cell, cell))
        faces[name] = crop.resize((FACE, FACE), Image.NEAREST)
    return faces


def write_assets(faces: dict[str, Image.Image]) -> None:
    tex = ASSETS / "textures" / "block"
    tex.mkdir(parents=True, exist_ok=True)
    for name, img in faces.items():
        img.save(tex / f"void_pattern_provider_{name}.png", format="PNG", optimize=True)
        print(f"[+] textures/block/void_pattern_provider_{name}.png (16x16)")

    models = ASSETS / "models" / "block"
    models.mkdir(parents=True, exist_ok=True)
    model = {
        "parent": "minecraft:block/cube",
        "textures": {
            "down": "void_technology:block/void_pattern_provider_top",
            "up": "void_technology:block/void_pattern_provider_top",
            "north": "void_technology:block/void_pattern_provider_front",
            "south": "void_technology:block/void_pattern_provider_side",
            "east": "void_technology:block/void_pattern_provider_side",
            "west": "void_technology:block/void_pattern_provider_side",
            "particle": "void_technology:block/void_pattern_provider_side",
        },
    }
    (models / "void_pattern_provider.json").write_text(json.dumps(model, indent=2), encoding="utf-8")
    (ASSETS / "models" / "item").mkdir(parents=True, exist_ok=True)
    (ASSETS / "models" / "item" / "void_pattern_provider.json").write_text(
        json.dumps({"parent": "void_technology:block/void_pattern_provider"}, indent=2), encoding="utf-8")

    states = ASSETS / "blockstates"
    states.mkdir(parents=True, exist_ok=True)
    variants = {
        f"push_direction={push}": {"model": "void_technology:block/void_pattern_provider"}
        for push in PUSH_DIRECTIONS
    }
    (states / "void_pattern_provider.json").write_text(
        json.dumps({"variants": variants}, indent=2), encoding="utf-8")
    print(f"[+] blockstates/void_pattern_provider.json ({len(variants)} variants)")

    loot = DATA / "loot_table" / "blocks"
    loot.mkdir(parents=True, exist_ok=True)
    (loot / "void_pattern_provider.json").write_text(json.dumps({
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{"type": "minecraft:item", "name": "void_technology:void_pattern_provider"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    }, indent=2), encoding="utf-8")
    print("[+] data/void_technology/loot_table/blocks/void_pattern_provider.json")


def main() -> None:
    write_assets(split_faces(render_strip()))


if __name__ == "__main__":
    main()
