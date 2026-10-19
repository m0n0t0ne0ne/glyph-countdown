"""Rewrite an APK so stored entries start on 4-byte boundaries (resources.arsc must be
stored and aligned for apps targeting Android 11+); .so files would need 4 KiB."""
import sys, zipfile

src, dst = sys.argv[1], sys.argv[2]
NO_COMPRESS = ("resources.arsc", ".png", ".ttf")

with zipfile.ZipFile(src) as zin, open(dst, "wb") as raw:
    zout = zipfile.ZipFile(raw, "w")
    for info in zin.infolist():
        data = zin.read(info.filename)
        stored = info.filename == "resources.arsc" or info.filename.endswith(NO_COMPRESS) \
            or info.filename == "AndroidManifest.xml"
        zi = zipfile.ZipInfo(info.filename, date_time=(2026, 10, 19, 0, 0, 0))
        zi.compress_type = zipfile.ZIP_STORED if stored else zipfile.ZIP_DEFLATED
        zi.external_attr = 0o644 << 16
        if stored:
            # local header = 30 bytes + name + extra; pad extra so data lands on a 4-byte boundary
            offset = raw.tell() + 30 + len(info.filename.encode())
            pad = (-offset) % 4
            zi.extra = b"\x00" * pad
        zout.writestr(zi, data)
    zout.close()

with zipfile.ZipFile(dst) as z:
    for i in z.infolist():
        if i.compress_type == zipfile.ZIP_STORED:
            start = i.header_offset + 30 + len(i.filename.encode()) + len(i.extra)
            assert start % 4 == 0, i.filename
print("aligned", dst)
