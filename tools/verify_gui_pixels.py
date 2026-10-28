#!/usr/bin/env python3
"""像素级验证：游戏截图 vs HTML 渲染出的 16 帧。

为什么不能用「单帧逐像素相等」判定
----------------------------------
1. 贴图开的是线性过滤：3× 超采样被缩到屏幕像素时必然产生插值，逐像素相等不成立；
2. 截图时刻落在动画中间：眼睛呼吸/虹膜旋转/扫光/流光都在动，
   对应的是「某一帧附近」，而不是精确某一帧；
3. 面板在屏幕上的位置由 LDLib2 计算，可能与推算差 1–2 像素。

因此判定方式为：
  - 用 run/screenshots/uitest.json 给的矩形作为起点，在 ±3 物理像素内搜索最佳对齐；
  - 对 16 帧取**逐像素最小误差**（即「这个像素是否符合任意一帧」）；
  - 统计平均误差与超阈像素占比。

用法：python tools/verify_gui_pixels.py
"""

from __future__ import annotations

import json
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SHOT = ROOT / "run" / "screenshots" / "uitest.png"
RECT = ROOT / "run" / "screenshots" / "uitest.json"
SHEET = ROOT / "src" / "main" / "resources" / "assets" / "void_technology" / "textures" / "gui" / "void_controller_sheet.png"

SIZE = 176
GRID = 5
FRAMES = 24
DIFF_THRESHOLD = 26          # 单通道差值超过它算「不同」
MEAN_TOLERANCE = 6.0         # 线性过滤 + 动画相位下允许的平均误差
SEARCH_RADIUS = 3            # 物理像素搜索半径

# 文字由游戏字体绘制，属于 overlay，比对时排除
TEXT_BOXES = [
    (6, 4, 170, 20),
    (20, 117, 170, 131),
    (20, 135, 170, 149),
    (20, 153, 170, 167),
]


def load_frames() -> np.ndarray:
    sheet = Image.open(SHEET).convert("RGB")
    cell = sheet.width // GRID
    frames = []
    for f in range(FRAMES):
        x, y = (f % GRID) * cell, (f // GRID) * cell
        frames.append(np.array(
            sheet.crop((x, y, x + cell, y + cell)).resize((SIZE, SIZE), Image.LANCZOS),
            dtype=int))
    return np.stack(frames)          # (FRAMES, SIZE, SIZE, 3)


def text_mask() -> np.ndarray:
    m = np.zeros((SIZE, SIZE), dtype=bool)
    for x0, y0, x1, y1 in TEXT_BOXES:
        m[y0:y1, x0:x1] = True
    return m


def main() -> None:
    if not SHOT.exists():
        raise SystemExit(f"[!] 没有截图：{SHOT}（先执行 gradlew runClient -Puitest=true）")
    if not RECT.exists():
        raise SystemExit("[!] 缺少 run/screenshots/uitest.json（截图工具会写入面板矩形）")

    shot = np.array(Image.open(SHOT).convert("RGB"), dtype=int)
    meta = json.loads(RECT.read_text(encoding="utf-8"))

    win_w, win_h = meta["window"]
    gui_w, gui_h = meta["gui"]
    scale = max(1, round(win_w / gui_w))
    base_left = meta["left"] * scale
    base_top = meta["top"] * scale
    panel_px = SIZE * scale

    frames = load_frames()
    mask = ~text_mask()
    mask_count = int(mask.sum())

    best = None
    for dy in range(-SEARCH_RADIUS, SEARCH_RADIUS + 1):
        for dx in range(-SEARCH_RADIUS, SEARCH_RADIUS + 1):
            left, top = base_left + dx, base_top + dy
            if left < 0 or top < 0 or top + panel_px > shot.shape[0] or left + panel_px > shot.shape[1]:
                continue
            block = shot[top:top + panel_px, left:left + panel_px]
            idx = (np.arange(SIZE) * scale + scale // 2).clip(0, block.shape[0] - 1)
            small = block[np.ix_(idx, idx)]

            diff = np.abs(frames - small[None]).max(axis=3)   # (FRAMES, H, W)
            per_pixel_min = diff.min(axis=0)
            mean_err = float(per_pixel_min[mask].mean())
            bad = int((per_pixel_min[mask] > DIFF_THRESHOLD).sum())
            closest = int(diff.mean(axis=(1, 2)).argmin())

            if best is None or mean_err < best[0]:
                best = (mean_err, bad / mask_count, bad, dx, dy, small, closest, per_pixel_min)

    mean_err, ratio, bad, dx, dy, small, closest, per_pixel_min = best
    left, top = base_left + dx, base_top + dy
    print(f"截图       : {SHOT.relative_to(ROOT)}")
    print(f"面板位置   : ({left}, {top})，GUI 缩放 ×{scale}，对齐偏移 ({dx:+d}, {dy:+d})")
    print(f"比对像素   : {mask_count}（已排除文字区域）")
    print(f"最接近相位 : 第 {closest} 帧附近")
    print(f"平均误差   : {mean_err:.2f}/255（对 16 帧取逐像素最小误差）")
    print(f"超阈像素   : {bad}（{ratio * 100:.2f}%，阈值 {DIFF_THRESHOLD}/255）")

    if mean_err < 2.0:
        judgement = "一致（误差极小）"
    elif mean_err < MEAN_TOLERANCE:
        judgement = "一致（线性过滤 + 动画相位内的正常误差）"
    else:
        judgement = "存在明显偏差，需要检查"
    print(f"判定       : {judgement}")

    # 对照图：左＝游戏截图(缩回 1:1)，中＝最接近的帧，右＝误差热力图
    frame = frames[closest]
    err = np.clip(per_pixel_min * 4, 0, 255).astype(np.uint8)
    err_rgb = np.stack([err, err // 3, err // 3], axis=2)
    canvas = Image.new("RGB", (SIZE * 3 + 16, SIZE + 18), (16, 10, 12))
    canvas.paste(Image.fromarray(small.astype(np.uint8)), (0, 18))
    canvas.paste(Image.fromarray(frame.astype(np.uint8)), (SIZE + 8, 18))
    canvas.paste(Image.fromarray(err_rgb), (2 * SIZE + 16, 18))
    canvas = canvas.resize((canvas.width * 2, canvas.height * 2), Image.NEAREST)
    out = ROOT / "docs" / "pixel-verify.png"
    canvas.save(out)
    print(f"对照图     : {out.relative_to(ROOT)}（左=游戏截图，中=最佳帧，右=误差热力图）")


if __name__ == "__main__":
    main()
