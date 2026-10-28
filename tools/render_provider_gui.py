#!/usr/bin/env python3
"""渲染 docs/pattern-provider-gui.html 得到虚空样板供应器的 GUI 底图（符文盘）。

设计：中心虚空之眼 + 三环 12/18/24 = 54 个样板槽（弧距 20.9，缝 5px），
      环间蚀刻圆与方位刻度，顶部标题带（无搜索框、无输入框），底部标准物品栏。
面板：208 × 304；3× 超采样渲染，游戏侧线性过滤缩回。

坐标约定（重要）
----------------
`pattern_slots.json` 里**所有槽位都是槽心坐标**（pattern 与 inventory 统一），
槽的实际绘制区域 = (x-8, y-8, 16, 16)。Java 侧按「槽心 - 8」摆 ItemSlot。

产物
----
    assets/void_technology/textures/gui/pattern_provider_panel.png   624 × 912
    docs/provider-gui-preview.html                                   预览页（虚线框可开关）
    docs/pattern_slots.json                                          54 样板槽 + 36 背包槽（槽心）

用法：python tools/render_provider_gui.py
"""

from __future__ import annotations

import base64
import json
import math
import subprocess
import sys
import tempfile
from io import BytesIO
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
HTML = ROOT / "docs" / "pattern-provider-gui.html"
TEX = ROOT / "src" / "main" / "resources" / "assets" / "void_technology" / "textures" / "gui"
DOCS = ROOT / "docs"

PANEL_W, PANEL_H = 208, 304
SCALE = 3
SLOT = 16

# 符文盘：与 HTML 的 RINGS / CX / CY 必须一致
CX, CY = 104, 112
RINGS = [(40.0, 12), (60.0, 18), (80.0, 24)]

# 物品栏：左上角 + 行距；写入 JSON 时转成槽心
INV_X, INV_Y, PITCH = 24, 218, 18
HOTBAR_Y = 278

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


def render_panel() -> Image.Image:
    browser = find_browser()
    w, h = PANEL_W * SCALE, PANEL_H * SCALE
    with tempfile.TemporaryDirectory(prefix="vtprov-") as tmp:
        out = Path(tmp) / "panel.png"
        cmd = [
            browser, "--headless=new", "--disable-gpu", "--hide-scrollbars",
            "--no-first-run", "--no-default-browser-check", "--disable-extensions",
            "--force-device-scale-factor=1", f"--user-data-dir={Path(tmp) / 'profile'}",
            "--virtual-time-budget=1500",
            f"--window-size={w},{h}",
            "--default-background-color=00000000",
            f"--screenshot={out}", HTML.as_uri(),
        ]
        subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=180)
        if not out.exists():
            sys.exit("[!] 面板渲染失败")
        return Image.open(out).convert("RGBA")


def pattern_slots() -> list[dict]:
    slots = []
    for radius, count in RINGS:
        for i in range(count):
            angle = math.radians(-90 + i * 360 / count)
            slots.append({
                "x": round(CX + math.cos(angle) * radius, 2),
                "y": round(CY + math.sin(angle) * radius, 2),
                "ring": radius,
            })
    return slots


def inventory_slots() -> list[dict]:
    """背包槽同样返回**槽心**（左上角 + 8），与样板槽语义统一。"""
    half = SLOT / 2
    slots = []
    for r in range(3):
        for c in range(9):
            slots.append({"x": INV_X + c * PITCH + half, "y": INV_Y + r * PITCH + half})
    for c in range(9):
        slots.append({"x": INV_X + c * PITCH + half, "y": HOTBAR_Y + half})
    return slots


def b64(im: Image.Image) -> str:
    buf = BytesIO()
    im.save(buf, format="PNG")
    return base64.b64encode(buf.getvalue()).decode()


def write_preview(panel: Image.Image, pattern: list[dict], inv: list[dict]) -> None:
    def dots(points):
        return "".join(
            f'<div class="slot" style="left:{p["x"] - SLOT / 2:.1f}px;top:{p["y"] - SLOT / 2:.1f}px;'
            f'width:{SLOT}px;height:{SLOT}px"></div>'
            for p in points
        )

    html = f"""<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8"><title>虚空样板供应器 · 符文盘</title>
<style>
 body{{margin:0;padding:24px;background:#0A0608;color:#E8D6D4;font-family:"Segoe UI","Microsoft YaHei",sans-serif}}
 h1{{font-size:16px;margin:0 0 6px}} p{{color:#A8908E;font-size:12.5px;line-height:1.75;max-width:920px}}
 .row{{display:flex;gap:26px;flex-wrap:wrap;margin-top:18px;align-items:flex-start}}
 .card{{background:#120A0C;border:1px solid #3A1A1F;border-radius:10px;padding:14px}}
 .card h2{{font-size:12px;letter-spacing:.6px;color:#D8B25C;margin:0 0 10px;font-weight:600}}
 .panel{{position:relative;width:{PANEL_W}px;height:{PANEL_H}px;image-rendering:pixelated;
        transform:scale(2.0);transform-origin:top left;
        background-image:url(data:image/png;base64,{b64(panel)});background-size:100% 100%}}
 .slot{{position:absolute;box-sizing:border-box;border:1px dashed rgba(200,162,74,.30);border-radius:2px;
        display:none}}
 body.show-slots .slot{{display:block}}
 .txt{{position:absolute;font-family:"Courier New",monospace;font-size:8px;letter-spacing:1px;color:#e6b9bb}}
 .sub{{position:absolute;font-family:"Courier New",monospace;font-size:7px;color:#a08688}} code{{background:#2A1216;padding:1px 5px;border-radius:3px;color:#D8B25C}}
 .hint{{font-size:11.5px;color:#7C6664;margin-top:10px}}
 label{{font-size:12px;color:#A8908E;cursor:pointer}}
</style></head><body>
<h1>虚空样板供应器 · 符文盘 <small style="color:#A8908E;font-weight:400">{PANEL_W}×{PANEL_H}，3× 超采样</small></h1>
<p>中心虚空之眼 + 三环 12 / 18 / 24 = <b>54 个样板槽</b>；顶部只有标题带，<b>没有搜索框、没有输入框</b>；
标题带保持干净：不放置搜索框、输入框或计数文字。底部为标准物品栏。</p>
<div class="row">
  <div class="card"><h2>游戏内效果（1:1 坐标）</h2>
    <div style="width:calc({PANEL_W}px * 2.0);height:calc({PANEL_H}px * 2.0)">
      <div class="panel">
        <div class="txt" style="left:26px;top:8px">虚空样板供应器</div>
        <div class="sub" style="left:26px;top:208px">物品栏</div>
        {dots(pattern)}
        {dots(inv)}
      </div>
    </div>
    <div class="hint"><label><input type="checkbox" onchange="document.body.classList.toggle('show-slots', this.checked)"> 显示槽位虚线（默认隐藏，虚线不属于底图）</label></div>
    <div class="hint">槽心坐标：<code>docs/pattern_slots.json</code>（样板槽与背包槽统一为槽心）</div>
  </div>
  <div class="card"><h2>底图（{panel.width}×{panel.height}）</h2>
    <img src="data:image/png;base64,{b64(panel)}" style="width:{PANEL_W}px;image-rendering:pixelated;border:1px solid #000">
    <div class="hint"><code>textures/gui/pattern_provider_panel.png</code></div>
  </div>
</div>
</body></html>
"""
    (DOCS / "provider-gui-preview.html").write_text(html, encoding="utf-8")
    print("[+] docs/provider-gui-preview.html")


def main() -> None:
    TEX.mkdir(parents=True, exist_ok=True)
    panel = render_panel()
    path = TEX / "pattern_provider_panel.png"
    panel.save(path, format="PNG", optimize=True)
    print(f"[+] {path.relative_to(ROOT)}  ({panel.width}x{panel.height})")

    pattern = pattern_slots()
    inv = inventory_slots()
    (DOCS / "pattern_slots.json").write_text(json.dumps({
        "panel": [PANEL_W, PANEL_H],
        "origin": "center",
        "slot": SLOT,
        "pattern": pattern,
        "inventory": inv,
    }, indent=1), encoding="utf-8")
    print(f"[+] docs/pattern_slots.json  (pattern {len(pattern)}, inventory {len(inv)}, origin=center)")

    write_preview(panel, pattern, inv)


if __name__ == "__main__":
    main()
