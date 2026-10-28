#!/usr/bin/env python3
"""最小 NBT 读写器（供存档解析 / 结构导出脚本复用）。

只实现 Minecraft 用到的标签类型 1..12；命名空间下不需要的部分故意省略。
写入端用于生成结构文件（.nbt），格式与 /place template 兼容。
"""

from __future__ import annotations

import gzip
import struct
from dataclasses import dataclass, field
from typing import Any

TAG_END = 0
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_FLOAT = 5
TAG_DOUBLE = 6
TAG_BYTE_ARRAY = 7
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_INT_ARRAY = 11
TAG_LONG_ARRAY = 12


@dataclass
class NbtList:
    """NBT 列表：保留元素类型，写入时需要。"""
    tag_type: int
    items: list[Any] = field(default_factory=list)


class _Reader:
    def __init__(self, data: bytes):
        self.d = data
        self.p = 0

    def u1(self) -> int:
        v = self.d[self.p]
        self.p += 1
        return v

    def _unpack(self, fmt: str, size: int):
        v = struct.unpack_from(fmt, self.d, self.p)[0]
        self.p += size
        return v

    def i2(self) -> int:
        return self._unpack(">h", 2)

    def i4(self) -> int:
        return self._unpack(">i", 4)

    def i8(self) -> int:
        return self._unpack(">q", 8)

    def f4(self) -> float:
        return self._unpack(">f", 4)

    def f8(self) -> float:
        return self._unpack(">d", 8)

    def string(self) -> str:
        n = self._unpack(">H", 2)
        v = self.d[self.p:self.p + n].decode("utf-8", "replace")
        self.p += n
        return v

    def payload(self, t: int):
        if t == TAG_BYTE:
            return self._unpack(">b", 1)
        if t == TAG_SHORT:
            return self.i2()
        if t == TAG_INT:
            return self.i4()
        if t == TAG_LONG:
            return self.i8()
        if t == TAG_FLOAT:
            return self.f4()
        if t == TAG_DOUBLE:
            return self.f8()
        if t == TAG_BYTE_ARRAY:
            n = self.i4()
            v = self.d[self.p:self.p + n]
            self.p += n
            return list(v)
        if t == TAG_STRING:
            return self.string()
        if t == TAG_LIST:
            it = self.u1()
            n = self.i4()
            return NbtList(it, [self.payload(it) for _ in range(n)])
        if t == TAG_COMPOUND:
            out: dict[str, Any] = {}
            while True:
                tt = self.u1()
                if tt == TAG_END:
                    break
                name = self.string()
                out[name] = self.payload(tt)
            return out
        if t == TAG_INT_ARRAY:
            n = self.i4()
            return [self.i4() for _ in range(n)]
        if t == TAG_LONG_ARRAY:
            n = self.i4()
            return [self.i8() for _ in range(n)]
        raise ValueError(f"unsupported tag {t}")


def parse(blob: bytes) -> Any:
    """解析未压缩的 NBT 字节串（可含根 tag 头）。"""
    r = _Reader(blob)
    t = r.u1()
    if t != TAG_COMPOUND:
        raise ValueError("root tag must be compound")
    r.string()  # root name
    return r.payload(TAG_COMPOUND)


def load_gzip(path: str) -> dict:
    with gzip.open(path, "rb") as f:
        return parse(f.read())


# --------------------------------------------------------------- 写入端（结构文件用）

def _write_payload(out: bytearray, t: int, v: Any) -> None:
    if t == TAG_BYTE:
        out += struct.pack(">b", v)
    elif t == TAG_SHORT:
        out += struct.pack(">h", v)
    elif t == TAG_INT:
        out += struct.pack(">i", v)
    elif t == TAG_LONG:
        out += struct.pack(">q", v)
    elif t == TAG_FLOAT:
        out += struct.pack(">f", v)
    elif t == TAG_DOUBLE:
        out += struct.pack(">d", v)
    elif t == TAG_BYTE_ARRAY:
        out += struct.pack(">i", len(v))
        out += bytes(v)
    elif t == TAG_STRING:
        b = v.encode("utf-8")
        out += struct.pack(">H", len(b))
        out += b
    elif t == TAG_LIST:
        items = v.items if isinstance(v, NbtList) else v
        it = v.tag_type if isinstance(v, NbtList) else TAG_COMPOUND
        out += struct.pack(">b", it)
        out += struct.pack(">i", len(items))
        for item in items:
            _write_payload(out, it, item)
    elif t == TAG_COMPOUND:
        for name, (tt, val) in v.items():
            out += struct.pack(">b", tt)
            nb = name.encode("utf-8")
            out += struct.pack(">H", len(nb))
            out += nb
            _write_payload(out, tt, val)
        out += struct.pack(">b", TAG_END)
    elif t == TAG_INT_ARRAY:
        out += struct.pack(">i", len(v))
        for x in v:
            out += struct.pack(">i", x)
    elif t == TAG_LONG_ARRAY:
        out += struct.pack(">i", len(v))
        for x in v:
            out += struct.pack(">q", x)
    else:
        raise ValueError(f"unsupported tag {t}")


def write_compound(compound: dict, path: str | None = None, compress: bool = True) -> bytes:
    """compound 形如 {name: (tag_type, value)}；返回写入的字节串。"""
    out = bytearray()
    out += struct.pack(">b", TAG_COMPOUND)
    out += struct.pack(">H", 0)  # root name 为空
    _write_payload(out, TAG_COMPOUND, compound)
    data = bytes(out)
    if path:
        if compress:
            with gzip.open(path, "wb", compresslevel=6) as f:
                f.write(data)
        else:
            with open(path, "wb") as f:
                f.write(data)
    return data
