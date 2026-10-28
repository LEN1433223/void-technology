#!/usr/bin/env python3
"""渲染 docs/mod-icon.html 生成虚空科技的模组图标。

产物：
    src/main/resources/logo.png   256×256  → 模组列表图标（neoforge.mods.toml 的 logoFile）
    dist/curseforge-icon.png      400×400  → CurseForge 项目图标

用法：python tools/render_mod_icon.py
"""

from __future__ import annotations

import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
HTML = ROOT / "docs" / "mod-icon.html"
LOGO = ROOT / "src" / "main" / "resources" / "logo.png"
CF_ICON = ROOT / "dist" / "curseforge-icon.png"

RENDER = 512
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

    with tempfile.TemporaryDirectory(prefix="vticon-") as tmp:
        shot = Path(tmp) / "icon.png"
        subprocess.run([
            browser, "--headless=new", "--disable-gpu", "--hide-scrollbars",
            "--no-first-run", "--no-default-browser-check", "--disable-extensions",
            "--force-device-scale-factor=1", f"--user-data-dir={Path(tmp) / 'profile'}",
            "--virtual-time-budget=1500",
            f"--window-size={RENDER},{RENDER}",
            "--default-background-color=ff0a0405",
            f"--screenshot={shot}", HTML.as_uri(),
        ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=120)
        if not shot.exists():
            sys.exit("[!] 图标渲染失败")
        img = Image.open(shot).convert("RGB")

    LOGO.parent.mkdir(parents=True, exist_ok=True)
    img.resize((256, 256), Image.LANCZOS).save(LOGO, format="PNG", optimize=True)
    print(f"[+] {LOGO.relative_to(ROOT)}  (256x256)")

    CF_ICON.parent.mkdir(parents=True, exist_ok=True)
    img.resize((400, 400), Image.LANCZOS).save(CF_ICON, format="PNG", optimize=True)
    print(f"[+] {CF_ICON.relative_to(ROOT)}  (400x400)")


if __name__ == "__main__":
    main()
