#!/usr/bin/env python3
"""Genera resource_windows_amd64.syso con icono, manifiesto e información de versión.
Go lo enlaza solo al compilar para Windows. Uso: mkres.py <logo.svg> <salida.syso> <versión x.y.z>"""
import io, struct, subprocess, sys
from PIL import Image

svg, out, ver = sys.argv[1], sys.argv[2], sys.argv[3]
vparts = [int(x) for x in ver.split(".")] + [0] * 4
vparts = vparts[:4]

COMPANY = "Acta"
PRODUCT = "Acta Conexión"
DESC = "Acta Conexión: activa y reconecta la grabación de las dos voces de Acta"
COPY = "© 2026 Acta"
ORIG = "ActaConexion.exe"

# ---------------------------------------------------------------- iconos
sizes = [16, 24, 32, 48, 64, 128, 256]
icons = []  # (size, bytes)
for s in sizes:
    png = subprocess.run(["rsvg-convert", "-w", str(s), "-h", str(s), svg], capture_output=True, check=True).stdout
    if s >= 128:
        icons.append((s, png))
        continue
    im = Image.open(io.BytesIO(png)).convert("RGBA")
    w, h = im.size
    px = im.load()
    xor = bytearray()
    for y in range(h - 1, -1, -1):
        for x in range(w):
            r, g, b, a = px[x, y]
            xor += bytes((b, g, r, a))
    rowmask = ((w + 31) // 32) * 4
    andm = bytes(rowmask * h)
    hdr = struct.pack("<IiiHHIIiiII", 40, w, h * 2, 1, 32, 0, len(xor) + len(andm), 0, 0, 0, 0)
    icons.append((s, hdr + bytes(xor) + andm))

grp = struct.pack("<HHH", 0, 1, len(icons))
for i, (s, data) in enumerate(icons):
    grp += struct.pack("<BBBBHHIH", 0 if s >= 256 else s, 0 if s >= 256 else s, 0, 0, 1, 32, len(data), i + 1)

# ---------------------------------------------------------------- manifiesto
manifest = f"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<assembly xmlns="urn:schemas-microsoft-com:asm.v1" manifestVersion="1.0">
  <assemblyIdentity type="win32" name="Acta.Conexion" version="{'.'.join(str(x) for x in vparts)}" processorArchitecture="amd64"/>
  <description>{PRODUCT}</description>
  <trustInfo xmlns="urn:schemas-microsoft-com:asm.v3">
    <security><requestedPrivileges><requestedExecutionLevel level="asInvoker" uiAccess="false"/></requestedPrivileges></security>
  </trustInfo>
  <compatibility xmlns="urn:schemas-microsoft-com:compatibility.v1">
    <application>
      <supportedOS Id="{{8e0f7a12-bfb3-4fe8-b9a5-48fd50a15a9a}}"/>
      <supportedOS Id="{{1f676c76-80e1-4239-95bb-83d0f6d0da78}}"/>
    </application>
  </compatibility>
  <application xmlns="urn:schemas-microsoft-com:asm.v3">
    <windowsSettings>
      <longPathAware xmlns="http://schemas.microsoft.com/SMI/2016/WindowsSettings">true</longPathAware>
      <activeCodePage xmlns="http://schemas.microsoft.com/SMI/2019/WindowsSettings">UTF-8</activeCodePage>
    </windowsSettings>
  </application>
</assembly>
""".encode("utf-8")

# ---------------------------------------------------------------- versión (VS_VERSIONINFO)
def u16(s):
    return s.encode("utf-16-le") + b"\0\0"

def pad4(b):
    return b + b"\0" * ((-len(b)) % 4)

def node(key, value=b"", is_text=False, children=b""):
    k = u16(key)
    head = struct.pack("<HHH", 0, 0, 0)  # se rellena al final
    body = head + k
    body = pad4(body)
    vlen = (len(value) // 2) if is_text else len(value)
    body += value
    body = pad4(body)
    body += children
    total = len(body)
    return struct.pack("<HHH", total, vlen, 1 if is_text else 0) + body[6:]

def strentry(k, v):
    return pad4(node(k, u16(v), True))

fv = struct.pack("<IIIIIIIIIIIII", 0xFEEF04BD, 0x10000,
                 (vparts[0] << 16) | vparts[1], (vparts[2] << 16) | vparts[3],
                 (vparts[0] << 16) | vparts[1], (vparts[2] << 16) | vparts[3],
                 0x3F, 0, 0x40004, 0x1, 0, 0, 0)
vs = ".".join(str(x) for x in vparts)
strings = b"".join([
    strentry("CompanyName", COMPANY), strentry("FileDescription", DESC), strentry("FileVersion", vs),
    strentry("InternalName", "ActaConexion"), strentry("LegalCopyright", COPY),
    strentry("OriginalFilename", ORIG), strentry("ProductName", PRODUCT), strentry("ProductVersion", vs)])
st = pad4(node("0C0A04B0", b"", False, strings))
sfi = pad4(node("StringFileInfo", b"", False, st))
tr = pad4(node("Translation", struct.pack("<HH", 0x0C0A, 0x04B0), False))
vfi = pad4(node("VarFileInfo", b"", False, tr))
version = node("VS_VERSION_INFO", fv, False, sfi + vfi)

# ---------------------------------------------------------------- árbol de recursos
LANG_NEUTRAL, LANG_ES = 0, 0x0C0A
# tipo -> [(nombre, idioma, datos)]
res = {
    3: [(i + 1, LANG_NEUTRAL, d) for i, (s, d) in enumerate(icons)],
    14: [(1, LANG_NEUTRAL, grp)],
    16: [(1, LANG_ES, version)],
    24: [(1, LANG_NEUTRAL, manifest)],
}

def dir_bytes(entries):
    # entries: [(id, offset_or_subdir_flag)]
    out = struct.pack("<IIHHHH", 0, 0, 0, 0, 0, len(entries))
    for i, off in sorted(entries):
        out += struct.pack("<II", i, off)
    return out

# Calcula tamaños para fijar desplazamientos.
types = sorted(res)
root_size = 16 + 8 * len(types)
name_dir_sizes = {t: 16 + 8 * len(res[t]) for t in types}
lang_dir_size = 16 + 8  # un idioma por nombre
pos = root_size
name_dir_off = {}
for t in types:
    name_dir_off[t] = pos
    pos += name_dir_sizes[t]
lang_dir_off = {}
for t in types:
    for (n, l, d) in res[t]:
        lang_dir_off[(t, n)] = pos
        pos += lang_dir_size
entry_off = {}
for t in types:
    for (n, l, d) in res[t]:
        entry_off[(t, n)] = pos
        pos += 16
data_off = {}
for t in types:
    for (n, l, d) in res[t]:
        data_off[(t, n)] = pos
        pos += (len(d) + 3) & ~3

sec = bytearray()
sec += dir_bytes([(t, 0x80000000 | name_dir_off[t]) for t in types])
for t in types:
    sec += dir_bytes([(n, 0x80000000 | lang_dir_off[(t, n)]) for (n, l, d) in res[t]])
for t in types:
    for (n, l, d) in res[t]:
        sec += dir_bytes([(l, entry_off[(t, n)])])
relocs = []
for t in types:
    for (n, l, d) in res[t]:
        relocs.append(len(sec))
        sec += struct.pack("<IIII", data_off[(t, n)], len(d), 1252, 0)
for t in types:
    for (n, l, d) in res[t]:
        assert len(sec) == data_off[(t, n)]
        sec += d + b"\0" * ((-len(d)) % 4)

# ---------------------------------------------------------------- COFF
raw_ptr = 20 + 40
reloc_ptr = raw_ptr + len(sec)
sym_ptr = reloc_ptr + 10 * len(relocs)
coff = struct.pack("<HHIIIHH", 0x8664, 1, 0, sym_ptr, 1, 0, 0)
coff += struct.pack("<8sIIIIIIHHI", b".rsrc", 0, 0, len(sec), raw_ptr, reloc_ptr, 0, len(relocs), 0, 0x40000040)
coff += bytes(sec)
for r in relocs:
    coff += struct.pack("<IIH", r, 0, 3)  # IMAGE_REL_AMD64_ADDR32NB hacia el símbolo .rsrc
coff += struct.pack("<8sIhHBB", b".rsrc", 0, 1, 0, 3, 0)
coff += struct.pack("<I", 4)
open(out, "wb").write(coff)
print("escrito", out, len(coff), "bytes;", len(icons), "iconos")
