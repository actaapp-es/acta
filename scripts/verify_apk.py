"""Verificación independiente: alineación, firma APK v2/v3 (criptográfica) y digest de contenido."""
import sys, struct, hashlib, zipfile
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, ec

d = open(sys.argv[1], 'rb').read()

# Alineación de entradas sin comprimir
with zipfile.ZipFile(sys.argv[1]) as z:
    for i in z.infolist():
        if i.compress_type == 0:
            off = i.header_offset
            n, e = struct.unpack('<HH', d[off + 26:off + 30])
            data_off = off + 30 + n + e
            print(f"  stored {i.filename:28s} data@{data_off} aligned4={data_off % 4 == 0}")

eocd = d.rfind(b'PK\x05\x06')
cd_size, cd_off = struct.unpack('<II', d[eocd + 12:eocd + 20])
assert d[cd_off - 16:cd_off] == b'APK Sig Block 42', 'no signing block'
blk_size = struct.unpack('<Q', d[cd_off - 24:cd_off - 16])[0]
blk_start = cd_off - blk_size - 8
pairs = d[blk_start + 8:cd_off - 24]
blocks = {}
p = 0
while p < len(pairs):
    ln, = struct.unpack('<Q', pairs[p:p + 8]); bid, = struct.unpack('<I', pairs[p + 8:p + 12])
    blocks[bid] = pairs[p + 12:p + 8 + ln]; p += 8 + ln
print('  blocks:', [hex(k) for k in blocks])

def lp(b, o):
    n, = struct.unpack('<I', b[o:o + 4]); return b[o + 4:o + 4 + n], o + 4 + n

def seq(b):
    out, o = [], 0
    while o < len(b):
        x, o = lp(b, o); out.append(x)
    return out

# Digest de contenido
eocd_mod = bytearray(d[eocd:]); eocd_mod[16:20] = struct.pack('<I', blk_start)
def content_digest(H):
    cs = []
    for sec in (d[:blk_start], d[cd_off:eocd], bytes(eocd_mod)):
        for i in range(0, len(sec), 1 << 20):
            c = sec[i:i + (1 << 20)]
            cs.append(H(b'\xa5' + struct.pack('<I', len(c)) + c).digest())
    return H(b'\x5a' + struct.pack('<I', len(cs)) + b''.join(cs)).digest()

for bid, name in ((0x7109871a, 'v2'), (0xf05368c0, 'v3')):
    if bid not in blocks:
        continue
    signers, _ = lp(blocks[bid], 0)
    for s in seq(signers):
        o = 0
        signed, o = lp(s, o)
        if name == 'v3':
            o += 8
        sigs, o = lp(s, o)
        pub, o = lp(s, o)
        key = serialization.load_der_public_key(pub)
        ok = False
        for sg in seq(sigs):
            alg, = struct.unpack('<I', sg[:4]); sig, _ = lp(sg, 4)
            h = hashes.SHA256() if alg in (0x103, 0x201) else hashes.SHA512()
            if isinstance(key, ec.EllipticCurvePublicKey):
                key.verify(sig, signed, ec.ECDSA(h))
            else:
                key.verify(sig, signed, padding.PKCS1v15(), h)
            ok = True
        digests, _ = lp(signed, 0)
        dg = seq(digests)[0]
        alg, = struct.unpack('<I', dg[:4]); val, _ = lp(dg, 4)
        content = content_digest(hashlib.sha256 if alg in (0x101, 0x103, 0x201) else hashlib.sha512)
        print(f'  {name}: firma válida={ok}  digest coincide={val == content}')
