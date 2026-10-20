#!/usr/bin/env python3
"""Build small, offline PDF reading copies from DayFlow's canonical document summaries.

These are deliberately labelled as generated reading copies, not the original source PDFs.
The source-document milestone remains open until the original PDFs are actually packaged.
"""
import json
import textwrap
import zipfile
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "app/src/main/assets/plans_catalog.json"
OUT = ROOT / "app/src/main/assets/plan_pdfs.zip"
NOTICE = "Offline reading copy generated from DayFlow's bundled catalog. It is not the original source PDF."

def pdf_text(value):
    return value.encode("cp1252", "replace").hex().upper()

def wrap_body(value, width=94):
    lines = []
    for paragraph in value.splitlines():
        if not paragraph.strip():
            lines.append("")
        else:
            lines.extend(textwrap.wrap(
                paragraph, width=width, replace_whitespace=False,
                drop_whitespace=True, break_long_words=True, break_on_hyphens=True
            ) or [""])
    return lines

def make_pdf(title, subtitle, body):
    body_lines = wrap_body(body)
    per_page = 46
    chunks = [body_lines[i:i + per_page] for i in range(0, len(body_lines), per_page)] or [[]]
    objects = {}
    objects[1] = b"<< /Type /Catalog /Pages 2 0 R >>"
    objects[3] = b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"
    objects[4] = b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>"
    page_ids = []
    for idx, lines in enumerate(chunks):
        page_id = 5 + idx * 2
        content_id = page_id + 1
        page_ids.append(page_id)
        objects[page_id] = (
            f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
            f"/Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> "
            f"/Contents {content_id} 0 R >>"
        ).encode("ascii")
        ops = ["BT"]
        if idx == 0:
            ops += ["/F2 18 Tf", "54 746 Td", f"<{pdf_text(title)}> Tj",
                    "/F1 10 Tf", "0 -23 Td", f"<{pdf_text(subtitle)}> Tj",
                    "/F1 8 Tf", "0 -17 Td", f"<{pdf_text(NOTICE)}> Tj",
                    "/F1 9 Tf", "0 -22 Td"]
        else:
            ops += ["/F2 12 Tf", "54 748 Td", f"<{pdf_text(title + ' (continued)')}> Tj",
                    "/F1 9 Tf", "0 -27 Td"]
        for line_num, line in enumerate(lines):
            if line_num:
                ops.append("0 -12 Td")
            ops.append(f"<{pdf_text(line)}> Tj")
        ops.append("ET")
        ops += ["BT", "/F1 8 Tf", "520 24 Td", f"<{pdf_text('Page ' + str(idx + 1) + ' of ' + str(len(chunks)))}> Tj", "ET"]
        stream = zlib.compress(("\n".join(ops) + "\n").encode("ascii"), level=9)
        objects[content_id] = (
            f"<< /Length {len(stream)} /Filter /FlateDecode >>\nstream\n".encode("ascii")
            + stream + b"\nendstream"
        )
    objects[2] = ("<< /Type /Pages /Kids [" + " ".join(f"{n} 0 R" for n in page_ids)
                  + f"] /Count {len(page_ids)} >>").encode("ascii")
    max_id = max(objects)
    out = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets = [0] * (max_id + 1)
    for object_id in range(1, max_id + 1):
        offsets[object_id] = len(out)
        out.extend(f"{object_id} 0 obj\n".encode("ascii"))
        out.extend(objects[object_id])
        out.extend(b"\nendobj\n")
    xref_offset = len(out)
    out.extend(f"xref\n0 {max_id + 1}\n".encode("ascii"))
    out.extend(b"0000000000 65535 f \n")
    for object_id in range(1, max_id + 1):
        out.extend(f"{offsets[object_id]:010d} 00000 n \n".encode("ascii"))
    out.extend(f"trailer\n<< /Size {max_id + 1} /Root 1 0 R >>\nstartxref\n{xref_offset}\n%%EOF\n".encode("ascii"))
    return bytes(out)

def main():
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    docs = catalog.get("documents", [])
    if len(docs) != 5:
        raise SystemExit(f"Expected 5 bundled document summaries, got {len(docs)}")
    expected = {doc["filename"] for doc in docs}
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(OUT, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for doc in docs:
            pdf = make_pdf(doc["title"], doc["subtitle"], doc["content"])
            if not pdf.startswith(b"%PDF-"):
                raise SystemExit(f"Generated file is not a PDF: {doc['filename']}")
            archive.writestr(doc["filename"], pdf)
    with zipfile.ZipFile(OUT, "r") as archive:
        if archive.testzip() is not None or set(archive.namelist()) != expected:
            raise SystemExit("Offline PDF archive failed ZIP integrity or filename validation")
        for name in expected:
            if not archive.read(name).startswith(b"%PDF-"):
                raise SystemExit(f"Invalid PDF header: {name}")
    print(f"Created and validated {len(expected)} offline PDF reading copies: {OUT} ({OUT.stat().st_size} bytes)")

if __name__ == "__main__":
    main()
