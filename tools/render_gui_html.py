#!/usr/bin/env python3
"""用无头 Chrome 渲染 docs/gui-art.html 的 16 帧，拼成游戏用图集。

为什么要浏览器渲染
------------------
原型（docs/gui-mockup.html）的质感来自真实的 radial/conic 渐变、圆角、辉光与遮罩。
用 Python 逐像素仿写只能得到近似值，而且 1× 分辨率下线条一定是硬边。
这里直接让 Chrome 以 3× 超采样渲染 HTML，再交给游戏用线性过滤缩回 176 GUI 像素，
细线与曲线才会真正平滑。

流程
----
1. 对 f = 0..15 逐个用 `chrome --headless=new --screenshot` 截图（528×528，透明底）；
2. 按 4×4 网格拼成 2112×2112 图集（LDLib2 的 AnimationTexture 只支持方形格）；
3. 输出到 assets/.../textures/gui/void_controller_sheet.png；
4. 另存 1× 预览条与预览页方便人眼比对。

用法：python tools/render_gui_html.py [--browser 路径]
"""

from __future__ import annotations

import argparse
import base64
import shutil
import subprocess
import sys
import tempfile
from io import BytesIO
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
HTML = ROOT / "docs" / "gui-art.html"
TEX = ROOT / "src" / "main" / "resources" / "assets" / "void_technology" / "textures" / "gui"
DOCS = ROOT / "docs"

SIZE = 528          # 3× of 176 GUI px
GRID = 5
FRAMES = 24

BROWSERS = [
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
]


def find_browser(explicit: str | None) -> str:
    if explicit:
        return explicit
    for candidate in BROWSERS:
        if Path(candidate).exists():
            return candidate
    sys.exit("[!] 找不到 Chrome / Edge，无法渲染 HTML")


def render_frame(browser: str, frame: int, out_png: Path) -> None:
    """渲染单帧。

    注意两个坑：
    - 必须给每次调用独立的 --user-data-dir，否则 Chrome 会复用已在运行的实例，
      直接返回而不截图（表现为空白帧）；
    - 必须给 --virtual-time-budget，让页面把 canvas 画完再截，否则截到空白画布。
    """
    url = HTML.as_uri() + f"?f={frame}"
    with tempfile.TemporaryDirectory(prefix="vtgui-profile-") as profile:
        cmd = [
            browser,
            "--headless=new",
            "--disable-gpu",
            "--hide-scrollbars",
            "--no-first-run",
            "--no-default-browser-check",
            "--disable-extensions",
            "--force-device-scale-factor=1",
            f"--user-data-dir={profile}",
            "--virtual-time-budget=1500",
            f"--window-size={SIZE},{SIZE}",
            "--default-background-color=00000000",
            f"--screenshot={out_png}",
            url,
        ]
        result = subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                timeout=120)
    if result.returncode != 0 or not out_png.exists():
        sys.exit(f"[!] 第 {frame} 帧渲染失败（exit {result.returncode}）")


def build_sheet(browser: str) -> Image.Image:
    sheet = Image.new("RGBA", (SIZE * GRID, SIZE * GRID), (0, 0, 0, 0))
    with tempfile.TemporaryDirectory(prefix="vtgui-") as tmp:
        for f in range(FRAMES):
            png = Path(tmp) / f"frame_{f:02d}.png"
            render_frame(browser, f, png)
            with Image.open(png) as im:
                frame = im.convert("RGBA")
            if frame.size != (SIZE, SIZE):
                frame = frame.resize((SIZE, SIZE), Image.LANCZOS)
            sheet.paste(frame, ((f % GRID) * SIZE, (f // GRID) * SIZE))
            print(f"    frame {f + 1}/{FRAMES}")
    return sheet


def build_strip(sheet: Image.Image) -> Image.Image:
    cell = SIZE // 3                      # 1× 预览
    strip = Image.new("RGBA", (cell * FRAMES, cell), (0, 0, 0, 0))
    for f in range(FRAMES):
        x, y = (f % GRID) * SIZE, (f // GRID) * SIZE
        frame = sheet.crop((x, y, x + SIZE, y + SIZE)).resize((cell, cell), Image.LANCZOS)
        strip.paste(frame, (f * cell, 0))
    return strip


def b64(im: Image.Image) -> str:
    buf = BytesIO()
    im.save(buf, format="PNG")
    return base64.b64encode(buf.getvalue()).decode()


def write_preview(strip: Image.Image) -> None:
    cell = strip.height
    html = f"""<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8"><title>虚空控制器 · HTML 渲染预览</title>
<style>
 body{{margin:0;padding:24px;background:#0B0E12;color:#D8DEE6;font-family:"Segoe UI","Microsoft YaHei",sans-serif}}
 h1{{font-size:16px;margin:0 0 6px}} p{{color:#7C8794;font-size:12.5px;line-height:1.75;max-width:920px}}
 .row{{display:flex;gap:26px;flex-wrap:wrap;margin-top:18px;align-items:flex-start}}
 .card{{background:#0F1318;border:1px solid #232B36;border-radius:10px;padding:14px}}
 .card h2{{font-size:12px;letter-spacing:.6px;color:#AEB8C4;margin:0 0 10px;font-weight:600}}
 .panel{{position:relative;width:176px;height:176px;image-rendering:pixelated;transform:scale(2.2);
        transform-origin:top left;background-image:url(data:image/png;base64,{b64(strip)});
        background-repeat:no-repeat;animation:play 2.4s steps(16) infinite}}
 @keyframes play{{from{{background-position:0 0}}to{{background-position:-{cell * FRAMES}px 0}}}}
 .panel span{{position:absolute;font-family:"Courier New",monospace;white-space:nowrap}}
 code{{background:#171C24;padding:1px 5px;border-radius:3px;color:#C8A24A}}
 .hint{{font-size:11.5px;color:#5C6673;margin-top:10px}}
</style></head><body>
<h1>虚空控制器 · HTML 渲染预览 <small style="color:#7C8794;font-weight:400">176×176 GUI，3× 超采样后由游戏线性缩回</small></h1>
<p>贴图由 <code>docs/gui-art.html</code> 经无头 Chrome 渲染产出（真实 radial/conic 渐变、圆角、辉光、遮罩），
不是逐像素仿写。游戏内开启线性过滤后，细线与曲线会平滑缩放。文字由游戏绘制，这里按坐标模拟。</p>
<div class="row">
  <div class="card"><h2>游戏内效果</h2>
    <div style="width:calc(176px * 2.2);height:calc(176px * 2.2)">
      <div class="panel">
        <span style="left:8px;top:6px;color:#e6b9bb;font-size:8px;letter-spacing:1px">虚空控制器</span>
        <span style="left:22px;top:120px;color:#a08688;font-size:8px">结构状态</span>
        <span style="left:82px;top:120px;color:#8ee69c;font-size:8px">结构已成型</span>
        <span style="left:22px;top:138px;color:#a08688;font-size:8px">目前任务</span>
        <span style="left:82px;top:138px;color:#ecdcdc;font-size:8px">销毁中 · 石头 ×1</span>
        <span style="left:22px;top:156px;color:#a08688;font-size:8px">网络状态</span>
        <span style="left:82px;top:156px;color:#8ee69c;font-size:8px">在线</span>
      </div>
    </div>
  </div>
  <div class="card"><h2>16 帧（1× 预览条）</h2>
    <img src="data:image/png;base64,{b64(strip)}" style="width:{cell * FRAMES}px;image-rendering:pixelated;border:1px solid #1B222B">
    <div class="hint">游戏内图集 <code>void_controller_sheet.png</code>（{SIZE * GRID}×{SIZE * GRID}，4×4，cellSize=4）</div>
  </div>
</div>
</body></html>
"""
    (DOCS / "gui-art-preview.html").write_text(html, encoding="utf-8")
    print("[+] docs/gui-art-preview.html")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--browser", default=None, help="chrome/edge 可执行文件路径")
    args = parser.parse_args()

    browser = find_browser(args.browser)
    print(f"[*] browser: {browser}")
    TEX.mkdir(parents=True, exist_ok=True)

    sheet = build_sheet(browser)
    sheet_path = TEX / "void_controller_sheet.png"
    sheet.save(sheet_path, format="PNG", optimize=True)
    print(f"[+] {sheet_path.relative_to(ROOT)}  ({sheet.width}x{sheet.height})")

    strip = build_strip(sheet)
    strip.save(DOCS / "preview_strip.png", format="PNG", optimize=True)
    print("[+] docs/preview_strip.png")
    write_preview(strip)

    # 清理单帧临时文件
    for stale in DOCS.glob("_frame*.png"):
        stale.unlink()


if __name__ == "__main__":
    main()
