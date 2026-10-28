#!/usr/bin/env python3
"""渲染 docs/stele-art.html 得到「虚空石碑」的方块贴图与配套资源。

与方块供应器同一套管线：HTML 绘制三面（front / side / top），无头 Chrome 渲染，
再用最近邻缩回 16×16 像素图，随后生成模型 / 方块状态 / 掉落表。

用法：python tools/render_stele_art.py
"""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
HTML = ROOT / "docs" / "stele-art.html"
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "void_technology"
DATA = ROOT / "src" / "main" / "resources" / "data" / "void_technology"

RENDER = 384      # 3 面 × 每面 128 渲染像素
FACE = 16
NAME = "void_stele"

BROWSERS = [
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
]


def find_browser() -> str:
    for candidate in BROWSERS:
        if Path(candidate).exists():
            return candidate
    sys.exit("[!] 找不到 Chrome / Edge")


def render_strip() -> Image.Image:
    browser = find_browser()
    with tempfile.TemporaryDirectory(prefix="vtstele-") as tmp:
        out = Path(tmp) / "faces.png"
        cmd = [
            browser, "--headless=new", "--disable-gpu", "--hide-scrollbars",
            "--no-first-run", "--no-default-browser-check", "--disable-extensions",
            "--force-device-scale-factor=1", f"--user-data-dir={Path(tmp) / 'profile'}",
            "--virtual-time-budget=1200",
            f"--window-size={RENDER},{RENDER // 3}",
            "--default-background-color=00000000",
            f"--screenshot={out}", HTML.as_uri(),
        ]
        subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=120)
        if not out.exists():
            sys.exit("[!] 石碑贴图渲染失败")
        return Image.open(out).convert("RGBA")


def main() -> None:
    strip = render_strip()
    cell = strip.width // 3

    tex = ASSETS / "textures" / "block"
    tex.mkdir(parents=True, exist_ok=True)
    for i, face in enumerate(("front", "side", "top")):
        img = strip.crop((i * cell, 0, (i + 1) * cell, cell)).resize((FACE, FACE), Image.NEAREST)
        img.save(tex / f"{NAME}_{face}.png", format="PNG", optimize=True)
        print(f"[+] textures/block/{NAME}_{face}.png (16x16)")

    models = ASSETS / "models" / "block"
    models.mkdir(parents=True, exist_ok=True)
    (models / f"{NAME}.json").write_text(json.dumps({
        "parent": "minecraft:block/cube",
        "textures": {
            "down": f"void_technology:block/{NAME}_top",
            "up": f"void_technology:block/{NAME}_top",
            "north": f"void_technology:block/{NAME}_front",
            "south": f"void_technology:block/{NAME}_side",
            "east": f"void_technology:block/{NAME}_side",
            "west": f"void_technology:block/{NAME}_side",
            "particle": f"void_technology:block/{NAME}_side",
        },
    }, indent=2), encoding="utf-8")

    item_models = ASSETS / "models" / "item"
    item_models.mkdir(parents=True, exist_ok=True)
    (item_models / f"{NAME}.json").write_text(
        json.dumps({"parent": f"void_technology:block/{NAME}"}, indent=2), encoding="utf-8")

    states = ASSETS / "blockstates"
    states.mkdir(parents=True, exist_ok=True)
    (states / f"{NAME}.json").write_text(json.dumps({
        "variants": {"": {"model": f"void_technology:block/{NAME}"}},
    }, indent=2), encoding="utf-8")
    print(f"[+] blockstates/{NAME}.json (single variant)")

    loot = DATA / "loot_table" / "blocks"
    loot.mkdir(parents=True, exist_ok=True)
    (loot / f"{NAME}.json").write_text(json.dumps({
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{"type": "minecraft:item", "name": f"void_technology:{NAME}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    }, indent=2), encoding="utf-8")
    print(f"[+] data/.../loot_table/blocks/{NAME}.json")


if __name__ == "__main__":
    main()
