#!/usr/bin/env python3
"""把游戏内结构方块导出的 .nbt 收进模组资源。

用途：在存档里用结构方块「保存」建筑 → 本脚本扫描 generated 目录 → 校验 →
复制到 src/main/resources/data/void_technology/structure/<name>.nbt。

用法：
    python tools/import_structure.py            # 列出找到的结构并导入全部
    python tools/import_structure.py --list     # 只列出
    python tools/import_structure.py --name monument_01
"""

from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import nbt  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
RUN = ROOT / "run"
DEST = ROOT / "src" / "main" / "resources" / "data" / "void_technology" / "structure"


def find_structures() -> list[Path]:
    found: list[Path] = []
    for base in (RUN / "saves", RUN / "generated"):
        if base.exists():
            # 1.21 里可能是 structures 也可能是 structure（官方目录改名过）
            found.extend(base.rglob("structures/*.nbt"))
            found.extend(base.rglob("structure/*.nbt"))
    return sorted(set(found))


def summarize(path: Path) -> tuple[list[int], int, int]:
    root = nbt.load_gzip(str(path))
    size = [int(v) for v in root.get("size", [0, 0, 0])]
    palette = root.get("palette", [])
    blocks = root.get("blocks", [])
    n_blocks = len(blocks.items) if hasattr(blocks, "items") else len(blocks)
    return size, len(palette.items) if hasattr(palette, "items") else len(palette), n_blocks


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--list", action="store_true", help="只列出，不复制")
    ap.add_argument("--name", help="只导入这个文件名（不含扩展名）")
    args = ap.parse_args()

    found = find_structures()
    if not found:
        print("[!] 没找到结构文件。请先在游戏里用结构方块保存，并确认它在：")
        print(f"    {RUN}\\saves\\<存档>\\generated\\<命名空间>\\structures\\<名字>.nbt")
        return

    print(f"找到 {len(found)} 个结构文件：")
    targets: list[Path] = []
    for p in found:
        try:
            size, pal, blocks = summarize(p)
            rel = p.relative_to(RUN)
            print(f"  - {rel}")
            print(f"      尺寸 {size[0]}×{size[1]}×{size[2]}   调色板 {pal} 种   方块 {blocks} 个")
        except Exception as e:  # noqa: BLE001
            print(f"  - {p.relative_to(RUN)}  [解析失败: {e}]")
            continue
        if args.name and p.stem != args.name:
            continue
        targets.append(p)

    if args.list or not targets:
        return

    DEST.mkdir(parents=True, exist_ok=True)
    for p in targets:
        out = DEST / f"{p.stem}.nbt"
        shutil.copy2(p, out)
        print(f"[+] {p.name} -> {out.relative_to(ROOT)}")

    print("\n放置方式（游戏内）：")
    print("  /place template void_technology:<文件名>")
    print("方块实体数据会随结构一起写入（箱子/告示牌内容保留）。")


if __name__ == "__main__":
    main()
