#!/usr/bin/env python3
"""把 Litematica 投影（.litematic）转成原版结构文件（.nbt），供虚空石碑 / /place template 使用。

要点
----
* BlockStates 是**跨界打包**（第 i 个方块占 [i*bits, (i+1)*bits)，可横跨两个 long），
  与 Minecraft 原版调色板容器（不跨界）不同——按原版解会整体错位。
* 方块实体在 litematic 里是**扁平字段**（x/y/z + id + 其余字段），需要转成原版结构的
  {pos, id, nbt}，否则箱子内容 / 信标效果 / 样板供应器里的样板都会丢。
* 实体的 Pos 是绝对世界坐标，与区域局部坐标系不一致；本脚本只在能映射到范围内时才带上。

用法：
    python tools/litematic_to_structure.py run/schematics/Unnamed.litematic \
        --name void_palace --crop 19,0,10,163,47,158
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import nbt  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "src" / "main" / "resources" / "data" / "void_technology" / "structure"

AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}
SKIP_BLOCK_ENTITY = {"minecraft:structure_block"}


def as_list(v):
    return v.items if isinstance(v, nbt.NbtList) else (v or [])


def vec(d) -> list[int]:
    d = d or {}
    return [abs(int(d.get("x", d.get("X", 0)))), abs(int(d.get("y", d.get("Y", 0)))),
            abs(int(d.get("z", d.get("Z", 0))))]


def decode_region(reg: dict) -> dict[tuple[int, int, int], tuple[str, dict]]:
    size = vec(reg.get("Size"))
    palette = as_list(reg.get("BlockStatePalette"))
    longs = as_list(reg.get("BlockStates"))
    sx, sy, sz = size
    if not sx or not sy or not sz or not palette:
        return {}

    bits = max(2, (len(palette) - 1).bit_length())
    mask = (1 << bits) - 1

    out: dict[tuple[int, int, int], tuple[str, dict]] = {}
    total = sx * sy * sz
    for i in range(total):
        bit = i * bits
        li = bit >> 6
        off = bit & 63
        if li >= len(longs):
            break
        value = longs[li] >> off
        if off + bits > 64 and li + 1 < len(longs):
            value |= longs[li + 1] << (64 - off)
        idx = value & mask
        if idx >= len(palette):
            continue
        entry = palette[idx]
        name = entry.get("Name", "")
        if name in AIR or not name:
            continue
        x = i % sx
        z = (i // sx) % sz
        y = i // (sx * sz)
        out[(x, y, z)] = (name, dict(entry.get("Properties") or {}))
    return out


def _to_nbt(v):
    # 解析出来的 NbtList 里装的仍是「已解析」的值（dict/int/…），必须逐项转换，
    # 否则写文件时会把 dict 当成 (tag, value) 元组解包而报错。
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
    ap.add_argument("litematic")
    ap.add_argument("--name", required=True)
    ap.add_argument("--crop", default=None, help="裁剪范围 x0,y0,z0,x1,y1,z1（结构局部坐标）")
    ap.add_argument("--no-entities", action="store_true")
    ap.add_argument("--keep-structure-blocks", action="store_true")
    args = ap.parse_args()

    src = Path(args.litematic)
    if not src.is_absolute():
        src = ROOT / src
    root = nbt.load_gzip(str(src))
    regions = root.get("Regions") or {}
    print(f"[i] {src.name}: {len(regions)} 个 region")

    # 注意：litematic 的方块坐标是「区域局部坐标」（0 起），区域 Position 只是该区域在
    # 世界里被框选的角落（Size 为负表示向负方向展开）。这里统一用局部坐标，
    # 这样 TileEntities 的 x/y/z 与方块处在同一坐标系，可直接对位。
    merged: dict[tuple[int, int, int], tuple[str, dict]] = {}
    for name, reg in regions.items():
        blocks = decode_region(reg)
        print(f"     region {name}: {len(blocks)} 个非空气方块")
        merged.update(blocks)

    if not merged:
        print("[!] 没有可导出的方块")
        return

    # 先把坐标归一化到 0 起（区域 Position 可能是负方向原点，直接用会带偏移）
    nx0 = min(p[0] for p in merged)
    ny0 = min(p[1] for p in merged)
    nz0 = min(p[2] for p in merged)
    base = {(x - nx0, y - ny0, z - nz0): v for (x, y, z), v in merged.items()}

    if args.crop:
        cx0, cy0, cz0, cx1, cy1, cz1 = [int(v) for v in args.crop.split(",")]
        before = len(base)
        base = {k: v for k, v in base.items()
                if cx0 <= k[0] <= cx1 and cy0 <= k[1] <= cy1 and cz0 <= k[2] <= cz1}
        print(f"[i] 裁剪 x {cx0}..{cx1} y {cy0}..{cy1} z {cz0}..{cz1}: {before} -> {len(base)} 方块")
        if not base:
            print("[!] 裁剪后没有方块，检查 --crop 范围")
            return

    # 裁剪后重新归一化，并记下「原始坐标 -> 结构坐标」的总偏移
    fx0 = min(p[0] for p in base)
    fy0 = min(p[1] for p in base)
    fz0 = min(p[2] for p in base)
    merged = {(x - fx0, y - fy0, z - fz0): v for (x, y, z), v in base.items()}
    shift_x, shift_y, shift_z = nx0 + fx0, ny0 + fy0, nz0 + fz0

    size = [max(p[0] for p in merged) + 1,
            max(p[1] for p in merged) + 1,
            max(p[2] for p in merged) + 1]
    print(f"[i] 尺寸 {size[0]}x{size[1]}x{size[2]}，方块 {len(merged)}")

    palette: list[tuple[str, tuple]] = []
    palette_index: dict[tuple, int] = {}
    blocks_out = []
    for (x, y, z), (name, props) in sorted(merged.items()):
        key = (name, tuple(sorted(props.items())))
        if key not in palette_index:
            palette_index[key] = len(palette)
            palette.append(key)
        blocks_out.append(([x, y, z], palette_index[key]))

    pal_list = []
    for name, props in palette:
        entry: dict = {"Name": (nbt.TAG_STRING, name)}
        if props:
            entry["Properties"] = (nbt.TAG_COMPOUND, {k: (nbt.TAG_STRING, str(v)) for k, v in props})
        pal_list.append(entry)

    be_list = []
    if not args.no_entities:
        for reg in regions.values():
            for be in as_list(reg.get("TileEntities")):
                if not isinstance(be, dict):
                    continue
                ident = be.get("id") or be.get("Id")
                if not ident or (not args.keep_structure_blocks and ident in SKIP_BLOCK_ENTITY):
                    continue
                if all(k in be for k in ("x", "y", "z")):
                    px, py, pz = int(be["x"]), int(be["y"]), int(be["z"])
                else:
                    pos = be.get("Pos") or be.get("pos") or {}
                    if isinstance(pos, nbt.NbtList):
                        px, py, pz = (int(pos.items[0]), int(pos.items[1]), int(pos.items[2]))
                    else:
                        px, py, pz = int(pos.get("x", 0)), int(pos.get("y", 0)), int(pos.get("z", 0))
                rel = [px - shift_x, py - shift_y, pz - shift_z]
                if not (0 <= rel[0] < size[0] and 0 <= rel[1] < size[1] and 0 <= rel[2] < size[2]):
                    print(f"[!] 方块实体 {ident} 在裁剪范围外，跳过 {rel}")
                    continue
                payload = {k: v for k, v in be.items()
                           if k not in ("x", "y", "z", "id", "Id", "Pos", "pos", "keepPacked")}
                entry = {
                    "pos": (nbt.TAG_LIST, nbt.NbtList(nbt.TAG_INT, rel)),
                    "id": (nbt.TAG_STRING, ident),
                }
                if payload:
                    entry["nbt"] = (nbt.TAG_COMPOUND, {k: _to_nbt(v) for k, v in payload.items()})
                be_list.append(entry)
                print(f"     + 方块实体 {ident} @ {rel}")
        print(f"[i] 方块实体合计 {len(be_list)} 个")

    ent_list = []
    if not args.no_entities:
        for reg in regions.values():
            rp = reg.get("Position") or {}
            ox, oy, oz = int(rp.get("x", 0)), int(rp.get("y", 0)), int(rp.get("z", 0))
            for ent in as_list(reg.get("Entities")):
                if not isinstance(ent, dict):
                    continue
                pos = ent.get("Pos")
                if not isinstance(pos, nbt.NbtList) or len(pos.items) < 3:
                    continue
                ex, ey, ez = (float(v) for v in pos.items[:3])
                cands = [
                    [ex - ox, ey - oy, ez - oz],
                    [-(ex - ox) - 1, -(ey - oy) - 1, ez - oz],
                    [ox - ex, oy - ey, ez - oz],
                ]
                local = next((c for c in cands if 0 <= c[0] < size[0] and 0 <= c[1] < size[1]
                              and 0 <= c[2] < size[2]), None)
                if local is None:
                    print(f"[!] 实体 {ent.get('id')} 坐标无法映射，跳过 Pos={[round(v,1) for v in (ex,ey,ez)]}")
                    continue
                ent_list.append({
                    "pos": (nbt.TAG_LIST, nbt.NbtList(nbt.TAG_DOUBLE, local)),
                    "blockPos": (nbt.TAG_LIST, nbt.NbtList(nbt.TAG_INT,
                                                           [int(local[0]), int(local[1]), int(local[2])])),
                    "nbt": (nbt.TAG_COMPOUND, {k: _to_nbt(v) for k, v in ent.items()}),
                })
                print(f"     + 实体 {ent.get('id')} @ {[round(v,1) for v in local]}")
        print(f"[i] 实体合计 {len(ent_list)} 个")

    root_out: dict = {
        "size": (nbt.TAG_LIST, nbt.NbtList(nbt.TAG_INT, size)),
        "palette": (nbt.TAG_LIST, nbt.NbtList(nbt.TAG_COMPOUND, pal_list)),
        "blocks": (nbt.TAG_LIST, nbt.NbtList(nbt.TAG_COMPOUND, [
            {"pos": (nbt.TAG_LIST, nbt.NbtList(nbt.TAG_INT, rel)),
             "state": (nbt.TAG_INT, st)} for rel, st in blocks_out
        ])),
        "entities": (nbt.TAG_LIST, nbt.NbtList(nbt.TAG_COMPOUND, ent_list)),
        "DataVersion": (nbt.TAG_INT, 3955),
    }
    if be_list:
        root_out["block_entities"] = (nbt.TAG_LIST, nbt.NbtList(nbt.TAG_COMPOUND, be_list))

    DEST.mkdir(parents=True, exist_ok=True)
    out = DEST / f"{args.name}.nbt"
    nbt.write_compound(root_out, str(out))
    print(f"\n[+] {out.relative_to(ROOT)}")
    print(f"    方块 {len(blocks_out)}  调色板 {len(palette)}  方块实体 {len(be_list)}  实体 {len(ent_list)}")
    print(f"\n游戏内: /place template void_technology:{args.name}")


if __name__ == "__main__":
    main()
