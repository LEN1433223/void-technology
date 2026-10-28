#!/usr/bin/env python3
"""渲染 docs/stele-gui.html → 虚空石碑交互面板贴图（3× 超采样）。

产物：assets/void_technology/textures/gui/void_stele_panel.png  (528 × 264)
用法：python tools/render_stele_gui.py
"""

from __future__ import annotations

import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
HTML = ROOT / "docs" / "stele-gui.html"
OUT = (ROOT / "src" / "main" / "resources" / "assets" / "void_technology"
       / "textures" / "gui" / "void_stele_panel.png")

PANEL_W, PANEL_H = 176, 100
SCALE = 3

BROWSERS = [
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
]


def main() -> None:
    browser = next((b for b in BROWSERS if Path(b).exists()), None)
    if not browser:
        sys.exit("[!] 找不到 Chrome / Edge")

    w, h = PANEL_W * SCALE, PANEL_H * SCALE
    with tempfile.TemporaryDirectory(prefix="vtstelegui-") as tmp:
        shot = Path(tmp) / "panel.png"
        subprocess.run([
            browser, "--headless=new", "--disable-gpu", "--hide-scrollbars",
            "--no-first-run", "--no-default-browser-check", "--disable-extensions",
            "--force-device-scale-factor=1", f"--user-data-dir={Path(tmp) / 'profile'}",
            "--virtual-time-budget=1200",
            f"--window-size={w},{h}",
            "--default-background-color=00000000",
            f"--screenshot={shot}", HTML.as_uri(),
        ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=120)
        if not shot.exists():
            sys.exit("[!] 面板渲染失败")
        img = Image.open(shot).convert("RGBA")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT, format="PNG", optimize=True)
    print(f"[+] {OUT.relative_to(ROOT)}  ({img.width}x{img.height})")


if __name__ == "__main__":
    main()
