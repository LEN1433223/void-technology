#!/usr/bin/env python3
"""按显式范围从世界存档提取建筑，导出为原版结构 .nbt（含方块实体）。

与 extract_structure.py 的区别：不猜包围盒，直接给范围；并且会读取区块里的
`block_entities`（箱子/信标/我们的方块实体等），避免"内容丢失"。

用法：
    python tools/extract_building.py --name void_palace \
        --x -80 63 --z -72 72 --min-y -60 --max-y 0
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import nbt  # noqa: E402
import importlib.util

ROOT = Path(__file__).resolve().parents[1]
RUN = ROOT / "run"
DEST = ROOT / "src" / "main" / "resources" / "data" / "void_technology" / "structure"

_spec = importlib.util.spec_from_file_location("extract_structure", ROOT / "tools" / "extract_structure.py")
ex = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(ex)


def _to_nbt(v):
    if isinstance(v, nbt.NbtList):
        return (nbt.TAG_LIST, nbt.NbtList(v.tag_type, [_to_nbt(x)[1] for x in v.items]))
    if isinstance(v, bool):
        return (nbt.TAG_BYTE, 1 if v else 0)
    if isinstance(v, int):
        return (nbt.TAG_INT, v)
    if isinstance(v, float):
        return (nbt.TAG_DOUBLE, v)
    if isinstance(v, str):
        return (nbt.TAG_STRING, v)
    if isinstance(v, dict):
        return (nbt.TAG_COMPOUND, {k: _to_nbt(x) for k, x in v.items()})
    if isinstance(v, list):
        if all(isinstance(x, int) for x in v):
            return (nbt.TAG_LIST, nbt.NbtList(nbt.TAG_INT, v))
        if all(isinstance(x, float) for x in v):
            return (nbt.TAG_LIST, nbt.NbtList(nbt.TAG_DOUBLE, v))
        return (nbt.TAG_LIST, nbt.NbtList(nbt.TAG_COMPOUND,
                                          [{k: _to_nbt(x) for k, x in d.items()} if isinstance(d, dict) else {}
                                           for d in v]))
    return (nbt.TAG_STRING, str(v))


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--world", default="MCBuild-1667")
    ap.add_argument("--name", required=True)
    ap.add_argument("--x", nargs=2, type=int, required=True, metavar=("X0", "X1"))
    ap.add_argument("--z", nargs=2, type=int, required=True, metavar=("Z0", "Z1"))
    ap.add_argument("--min-y", type=int, required=True)
    ap.add_argument("--max-y", type=int, default=320)
    ap.add_argument("--keep-structure-blocks", action="store_true")
    args = ap.parse_args()

    save = RUN / "saves" / args.world
    regions = sorted((save / "region").glob("*.mca"))
    if not regions:
        print("[!] 没有区域文件")
        return

    x0, x1 = min(args.x), max(args.x)
    z0, z1 = min(args.z), max(args.z)
    y0, y1 = args.min_y, args.max_y
    print(f"[i] 范围 x {x0}..{x1}  y {y0}..{y1}  z {z0}..{z1}")

    blocks = ex.scan_bbox(regions, (x0, y0, z0, x1, y1, z1))
    if not blocks:
        print("[!] 范围内没有方块")
        return
    xs = [p[0] for p in blocks]
    ys = [p[1] for p in blocks]
    zs = [p[2] for p in blocks]
    print(f"[i] 方块 {len(blocks)}  实际范围 x {min(xs)}..{max(xs)} y {min(ys)}..{max(ys)} z {min(zs)}..{max(zs)}")

    # ---- 调色板
    palette: list[tuple[str, tuple]] = []
    index: dict[tuple, int] = {}
    blocks_out = []
    ox, oy, oz = min(xs), min(ys), min(zs)
    for (x, y, z), (name, props) in sorted(blocks.items()):
        key = (name, tuple(sorted(props.items())))
        if key not in index:
            index[key] = len(palette)
            palette.append(key)
        blocks_out.append(([x - ox, y - oy, z - oz], index[key]))

    pal_list = []
    for name, props in palette:
        entry: dict = {"Name": (nbt.TAG_STRING, name)}
        if props:
            entry["Properties"] = (nbt.TAG_COMPOUND, {k: (nbt.TAG_STRING, str(v)) for k, v in dict(props).items()})
        pal_list.append(entry)

    # ---- 方块实体（区块里的 block_entities）
    be_list = []
    for rf in regions:
        try:
            blobs = list(ex.chunk_payloads(rf))
        except OSError:
            continue
        for raw in blobs:
            try:
                root = nbt.parse(raw)
            except Exception:  # noqa: BLE001
                continue
            for be in ex.as_py(root.get("block_entities", [])) or []:
                if not isinstance(be, dict):
                    continue
                ident = be.get("id")
                if not ident:
                    continue
                if not args.keep_structure_blocks and ident == "minecraft:structure_block":
                    continue
                try:
                    bx, by, bz = int(be["x"]), int(be["y"]), int(be["z"])
                except (KeyError, TypeError, ValueError):
                    continue
                if not (x0 <= bx <= x1 and y0 <= by <= y1 and z0 <= bz <= z1):
                    continue
                payload = {k: v for k, v in be.items() if k not in ("x", "y", "z", "id", "keepPacked")}
                entry = {
                    "pos": (nbt.TAG_LIST, nbt.NbtList(nbt.TAG_INT, [bx - ox, by - oy, bz - oz])),
                    "id": (nbt.TAG_STRING, ident),
                }
                if payload:
                    entry["nbt"] = (nbt.TAG_COMPOUND, {k: _to_nbt(v) for k, v in payload.items()})
                be_list.append(entry)
                print(f"     + 方块实体 {ident} @ {[bx-ox, by-oy, bz-oz]}")

    size = [max(xs) - ox + 1, max(ys) - oy + 1, max(zs) - oz + 1]
    root_out: dict = {
        "size": (nbt.TAG_LIST, nbt.NbtList(nbt.TAG_INT, size)),
        "palette": (nbt.TAG_LIST, nbt.NbtList(nbt.TAG_COMPOUND, pal_list)),
        "blocks": (nbt.TAG_LIST, nbt.NbtList(nbt.TAG_COMPOUND, [
            {"pos": (nbt.TAG_LIST, nbt.NbtList(nbt.TAG_INT, rel)), "state": (nbt.TAG_INT, st)}
            for rel, st in blocks_out
        ])),
        "entities": (nbt.TAG_LIST, nbt.NbtList(nbt.TAG_COMPOUND, [])),
        "DataVersion": (nbt.TAG_INT, 3955),
    }
    if be_list:
        root_out["block_entities"] = (nbt.TAG_LIST, nbt.NbtList(nbt.TAG_COMPOUND, be_list))

    DEST.mkdir(parents=True, exist_ok=True)
    out = DEST / f"{args.name}.nbt"
    nbt.write_compound(root_out, str(out))
    print(f"\n[+] {out.relative_to(ROOT)}")
    print(f"    尺寸 {size[0]}x{size[1]}x{size[2]}  方块 {len(blocks_out)}  调色板 {len(palette)}  方块实体 {len(be_list)}")
    print(f"    原点偏移 x {ox} y {oy} z {oz}（相对世界坐标）")
    print(f"\n游戏内: /place template void_technology:{args.name}")


if __name__ == "__main__":
    main()
