#!/usr/bin/env python3
"""从存档的 Anvil 区域文件里直接抽取建筑，导出为原版结构 .nbt。

思路：不依赖结构方块。扫描玩家附近区域，按“人造材料”白名单筛方块（石砖/楼梯/台阶/墙/
平滑石/深板岩砖/发光块……），取它们的最小包围盒，再写出 /place template 兼容的结构文件。

用法：
    python tools/extract_structure.py --list                  # 只统计候选
    python tools/extract_structure.py --name monument         # 导出到模组资源
    python tools/extract_structure.py --radius 128 --name a   # 调搜索半径
"""

from __future__ import annotations

import argparse
import io
import math
import re
import struct
import sys
import zlib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import nbt  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
RUN = ROOT / "run"
DEST = ROOT / "src" / "main" / "resources" / "data" / "void_technology" / "structure"

# 判定“玩家摆的”材料；自然地形（草/泥土/石头/水/沙…）不在内
MAN_MADE = re.compile(
    r"(bricks?|stairs|slab|_wall|chiseled|polished|smooth_(stone|sandstone|quartz)|"
    r"glowstone|sea_lantern|shroomlight|lantern|torch|campfire|"
    r"deepslate_tiles|deepslate_bricks|cracked_|mossy_|"
    r"obsidian|crying_obsidian|amethyst|blackstone|basalt|"
    r"iron_bars|chain|glass|tinted_glass|"
    r"quartz|copper|waxed_|cut_|"
    r"carpet|wool|concrete|terracotta|"
    r"leaves|log|planks|_fence|door|trapdoor|"
    r"glow_lichen|sculk|redstone_lamp|froglight|"
    r"purpur|prismarine|nether_brick|end_stone_brick|"
    r"bone_block|soul_|magma|"
    r"void_technology)"
)


def chunk_payloads(region_path: Path):
    """读取 .mca：产出 (chunk_x, chunk_z, nbt_bytes)。"""
    data = region_path.read_bytes()
    if len(data) < 8192:
        return
    for i in range(1024):
        entry = struct.unpack_from(">I", data, i * 4)[0]
        offset = entry >> 8
        if offset == 0:
            continue
        pos = offset * 4096
        if pos + 5 > len(data):
            continue
        length = struct.unpack_from(">I", data, pos)[0]
        ctype = data[pos + 4]
        blob = data[pos + 5:pos + 4 + length]
        if ctype == 2:
            try:
                raw = zlib.decompress(blob)
            except zlib.error:
                continue
        elif ctype == 1:
            import gzip
            try:
                raw = gzip.decompress(blob)
            except OSError:
                continue
        elif ctype == 3:
            raw = blob
        else:
            continue
        yield raw


def unpack_palette(palette: list, longs: list | None, min_bits: int = 4) -> list[int]:
    """把调色板容器解成 4096 个索引（1.16+ 不跨界打包）。

    bits 不能只按调色板长度推：有些区块的 data 是用更大的位宽写的（比如调色板缩小后
    旧数据还在），按 (n-1).bit_length() 解会越界。这里优先用 data 长度反推位宽。
    """
    n = len(palette)
    if n <= 1 or not longs:
        return [0] * 4096
    bits = max(min_bits, (n - 1).bit_length())
    if longs:
        from_data = (64 * len(longs)) // 4096
        if from_data >= min_bits and (1 << from_data) >= n:
            bits = max(bits, from_data)
    per_long = 64 // bits
    mask = (1 << bits) - 1
    out = []
    for i in range(4096):
        li = i // per_long
        if li >= len(longs):
            out.append(0)
            continue
        shift = 64 - bits * (i % per_long + 1)
        out.append((longs[li] >> shift) & mask)
    return out


EXCLUDE = {"minecraft:bedrock", "minecraft:dirt", "minecraft:grass_block", "minecraft:coarse_dirt",
           "minecraft:podzol", "minecraft:mycelium", "minecraft:rooted_dirt", "minecraft:farmland", "minecraft:dirt_path"}

AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}


def scan_bbox(region_files: list[Path], bbox: tuple[int, int, int, int, int, int]):
    """按包围盒完整收集所有非空气方块（含自然地形），保证导出的结构不缺料。"""
    x0, y0, z0, x1, y1, z1 = bbox
    found: dict[tuple[int, int, int], tuple[str, dict]] = {}
    for rf in region_files:
        try:
            blobs = list(chunk_payloads(rf))
        except OSError:
            continue
        for raw in blobs:
            try:
                root = nbt.parse(raw)
            except Exception:  # noqa: BLE001
                continue
            base_x = int(root.get("xPos", 0)) * 16
            base_z = int(root.get("zPos", 0)) * 16
            if base_x + 16 <= x0 or base_x > x1 or base_z + 16 <= z0 or base_z > z1:
                continue
            for sec in as_py(root.get("sections", [])) or []:
                if not isinstance(sec, dict):
                    continue
                bs = sec.get("block_states")
                if not bs:
                    continue
                sec_y = int(sec.get("Y", 0)) * 16
                if sec_y + 16 <= y0 or sec_y > y1:
                    continue
                palette = as_py(bs.get("palette", [])) or []
                names = [p.get("Name", "") for p in palette]
                if all(n in AIR for n in names):
                    continue
                longs = as_py(bs.get("data")) if bs.get("data") is not None else None
                idx = unpack_palette(palette, longs)
                for i, pi in enumerate(idx):
                    if pi >= len(names):
                        continue
                    nm = names[pi]
                    if nm in AIR or nm in EXCLUDE:
                        continue
                    lx, lz, ly = i & 15, (i >> 4) & 15, (i >> 8) & 15
                    wx, wz, wy = base_x + lx, base_z + lz, sec_y + ly
                    if not (x0 <= wx <= x1 and y0 <= wy <= y1 and z0 <= wz <= z1):
                        continue
                    p = palette[pi]
                    found[(wx, wy, wz)] = (nm, dict(p.get("Properties") or {}))
    return found

def as_py(v):
    """nbt 模块的列表可能包成 NbtList。"""
    return v.items if isinstance(v, nbt.NbtList) else v


def scan(region_files: list[Path], cx0: float, cz0: float, radius: float):
    """收集半径内的人造方块：{(x,y,z): (name, properties)}"""
    found: dict[tuple[int, int, int], tuple[str, dict]] = {}
    for rf in region_files:
        try:
            blobs = list(chunk_payloads(rf))
        except OSError:
            continue
        for raw in blobs:
            try:
                root = nbt.parse(raw)
            except Exception:  # noqa: BLE001
                continue
            sections = as_py(root.get("sections", [])) or []
            for sec in sections:
                if not isinstance(sec, dict):
                    continue
                bs = sec.get("block_states")
                if not bs:
                    continue
                sec_y = int(sec.get("Y", 0)) * 16
                palette = as_py(bs.get("palette", [])) or []
                names = [p.get("Name", "") for p in palette]
                # 先看这个 section 有没有候选，没有就跳过解码
                hits = [i for i, nm in enumerate(names) if MAN_MADE.search(nm)]
                if not hits:
                    continue
                longs = as_py(bs.get("data")) if bs.get("data") is not None else None
                idx = unpack_palette(palette, longs)
                for i, pi in enumerate(idx):
                    if pi >= len(names) or pi not in hits:
                        continue
                    lx, lz, ly = i & 15, (i >> 4) & 15, (i >> 8) & 15
                    wz = int(root.get("zPos", 0)) * 16 + lz
                    wx = int(root.get("xPos", 0)) * 16 + lx
                    wy = sec_y + ly
                    if abs(wx - cx0) > radius or abs(wz - cz0) > radius:
                        continue
                    p = palette[pi]
                    props = p.get("Properties") or {}
                    found[(wx, wy, wz)] = (p.get("Name", ""), dict(props))
    return found


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--world", default="MCBuild-1667")
    ap.add_argument("--radius", type=float, default=96.0)
    ap.add_argument("--name", default=None, help="导出名；不给则只统计")
    ap.add_argument("--pad", type=int, default=1, help="包围盒外扩")
    args = ap.parse_args()

    save = RUN / "saves" / args.world
    level = nbt.load_gzip(str(save / "level.dat"))
    data = level.get("Data", level)
    pos = as_py(data.get("Player", {}).get("Pos")) or [0, 64, 0]
    px, py, pz = float(pos[0]), float(pos[1]), float(pos[2])
    print(f"[i] 世界 {data.get('LevelName')}  玩家 {px:.1f} {py:.1f} {pz:.1f}")

    regions = sorted((save / "region").glob("*.mca"))
    if not regions:
        print("[!] 没有区域文件")
        return
    print(f"[i] 扫描 {len(regions)} 个区域文件，半径 {args.radius}")

    blocks = scan(regions, px, pz, args.radius)
    if not blocks:
        print("[!] 半径内没找到人造方块，试试加大 --radius")
        return

    xs = [p[0] for p in blocks]
    ys = [p[1] for p in blocks]
    zs = [p[2] for p in blocks]
    x0, x1 = min(xs) - args.pad, max(xs) + args.pad
    y0, y1 = min(ys), max(ys) + args.pad
    z0, z1 = min(zs) - args.pad, max(zs) + args.pad
    size = (x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1)

    from collections import Counter
    kinds = Counter(v[0] for v in blocks.values())
    print(f"[i] 命中 {len(blocks)} 个方块，{len(kinds)} 种材质")
    for nm, c in kinds.most_common(12):
        print(f"      {c:6d}  {nm}")
    print(f"[i] 包围盒 x {x0}..{x1}  y {y0}..{y1}  z {z0}..{z1}   尺寸 {size[0]}×{size[1]}×{size[2]}")

    if not args.name:
        print("\n[提示] 确认尺寸合理后加 --name <名字> 导出。")
        return

    # ---- 导出：用包围盒重新完整扫描（含自然方块），避免缺料留洞
    full = scan_bbox(regions, (x0, y0, z0, x1, y1, z1))
    print(f"[i] 包围盒内非空气方块 {len(full)} 个（含自然地形填充）")

    # ---- 写结构文件
    palette: list[tuple[str, dict]] = []
    palette_index: dict[tuple, int] = {}
    out_blocks = []
    for (wx, wy, wz), (name, props) in sorted(full.items()):
        key = (name, tuple(sorted(props.items())))
        if key not in palette_index:
            palette_index[key] = len(palette)
            palette.append((name, props))
        out_blocks.append({
            "pos": [wx - x0, wy - y0, wz - z0],
            "state": palette_index[key],
        })

    pal_list = []
    for name, props in palette:
        entry: dict = {"Name": (nbt.TAG_STRING, name)}
        if props:
            entry["Properties"] = (nbt.TAG_COMPOUND, {
                k: (nbt.TAG_STRING, v) for k, v in props.items()
            })
        pal_list.append(entry)

    root: dict = {
        "size": (nbt.TAG_LIST, nbt.NbtList(nbt.TAG_INT, list(size))),
        "palette": (nbt.TAG_LIST, nbt.NbtList(nbt.TAG_COMPOUND, pal_list)),
        "blocks": (nbt.TAG_LIST, nbt.NbtList(nbt.TAG_COMPOUND, [
            {
                "pos": (nbt.TAG_LIST, nbt.NbtList(nbt.TAG_INT, b["pos"])),
                "state": (nbt.TAG_INT, b["state"]),
            } for b in out_blocks
        ])),
        "entities": (nbt.TAG_LIST, nbt.NbtList(nbt.TAG_COMPOUND, [])),
        "DataVersion": (nbt.TAG_INT, 3955),
    }

    DEST.mkdir(parents=True, exist_ok=True)
    out_path = DEST / f"{args.name}.nbt"
    nbt.write_compound(root, str(out_path))
    print(f"\n[+] 已导出 {out_path.relative_to(ROOT)}")
    print(f"    方块 {len(out_blocks)} 个，调色板 {len(palette)} 种")
    print("\n游戏内放置：")
    print(f"  /place template void_technology:{args.name}")


if __name__ == "__main__":
    main()
