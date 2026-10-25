#!/usr/bin/env python3
"""Build the offline PDF archive, preferring original assets when present."""
import gzip
import json
import textwrap
import zipfile
import zlib
from pathlib import Path, PurePosixPath

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
CATALOG = ASSETS / "plans_catalog.json"
PLANS = ASSETS / "plans"
SOURCE_ARCHIVE = ROOT / "source_docs" / "original_source_pdfs.zip"
OUT = ASSETS / "plan_pdfs.zip"
NOTICE = "Condensed offline reading copy generated from DayFlow's catalog; not the original source PDF."
ORIGINAL_IDS = set()


def pdf_text(value):
    return value.encode("cp1252", "replace").hex().upper()


def wrap_body(value, width=94):
    lines = []
    for paragraph in value.splitlines():
        if not paragraph.strip():
            lines.append("")
        else:
            lines.extend(textwrap.wrap(paragraph, width=width, replace_whitespace=False,
                drop_whitespace=True, break_long_words=True, break_on_hyphens=True) or [""])
    return lines


def make_pdf(title, subtitle, body):
    body_lines = wrap_body(body)
    per_page = 46
    chunks = [body_lines[i:i + per_page] for i in range(0, len(body_lines), per_page)] or [[]]
    objects = {
        1: b"<< /Type /Catalog /Pages 2 0 R >>",
        3: b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
        4: b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>",
    }
    page_ids = []
    for idx, lines in enumerate(chunks):
        page_id, content_id = 5 + idx * 2, 6 + idx * 2
        page_ids.append(page_id)
        objects[page_id] = (
            f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
            f"/Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents {content_id} 0 R >>"
        ).encode("ascii")
        ops = ["BT"]
        if idx == 0:
            ops += ["/F2 18 Tf", "54 746 Td", f"<{pdf_text(title)}> Tj",
                    "/F1 10 Tf", "0 -23 Td", f"<{pdf_text(subtitle)}> Tj",
                    "/F1 8 Tf", "0 -17 Td", f"<{pdf_text(NOTICE)}> Tj", "/F1 9 Tf", "0 -22 Td"]
        else:
            ops += ["/F2 12 Tf", "54 748 Td", f"<{pdf_text(title + ' (continued)')}> Tj",
                    "/F1 9 Tf", "0 -27 Td"]
        for line_num, line in enumerate(lines):
            if line_num:
                ops.append("0 -12 Td")
            ops.append(f"<{pdf_text(line)}> Tj")
        ops += ["ET", "BT", "/F1 8 Tf", "520 24 Td",
                f"<{pdf_text('Page ' + str(idx + 1) + ' of ' + str(len(chunks)))}> Tj", "ET"]
        stream = zlib.compress(("\n".join(ops) + "\n").encode("ascii"), level=9)
        objects[content_id] = (f"<< /Length {len(stream)} /Filter /FlateDecode >>\nstream\n".encode("ascii")
                               + stream + b"\nendstream")
    objects[2] = ("<< /Type /Pages /Kids [" + " ".join(f"{n} 0 R" for n in page_ids)
                  + f"] /Count {len(page_ids)} >>").encode("ascii")
    max_id = max(objects)
    out = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets = [0] * (max_id + 1)
    for object_id in range(1, max_id + 1):
        offsets[object_id] = len(out)
        out.extend(f"{object_id} 0 obj\n".encode("ascii"))
        out.extend(objects[object_id] + b"\nendobj\n")
    xref_offset = len(out)
    out.extend(f"xref\n0 {max_id + 1}\n".encode("ascii"))
    out.extend(b"0000000000 65535 f \n")
    for object_id in range(1, max_id + 1):
        out.extend(f"{offsets[object_id]:010d} 00000 n \n".encode("ascii"))
    out.extend(f"trailer\n<< /Size {max_id + 1} /Root 1 0 R >>\nstartxref\n{xref_offset}\n%%EOF\n".encode("ascii"))
    return bytes(out)


def assemble_source_archive():
    """Reassemble the checked-in binary parts into a transient build-time ZIP."""
    if SOURCE_ARCHIVE.is_file():
        return
    parts = sorted(SOURCE_ARCHIVE.parent.glob("original_source_pdfs.zip.part[0-9][0-9]"))
    if not parts:
        return
    SOURCE_ARCHIVE.parent.mkdir(parents=True, exist_ok=True)
    with SOURCE_ARCHIVE.open("wb") as output:
        for part in parts:
            output.write(part.read_bytes())


def original_pdf_bytes_for(doc):
    relative = doc.get("originalAssetPath")
    if not relative:
        return None
    rel = PurePosixPath(relative)
    if rel.is_absolute() or ".." in rel.parts or not rel.parts or rel.parts[0] != "plans":
        raise SystemExit(f"Unsafe originalAssetPath for {doc.get('id')}: {relative}")
    candidate = (ASSETS / Path(*rel.parts)).resolve()
    if not candidate.is_relative_to(PLANS.resolve()):
        raise SystemExit(f"Original PDF path must be inside {PLANS}: {relative}")
    if candidate.name != doc["filename"]:
        raise SystemExit(f"Original asset name must match catalog filename for {doc['id']}")
    if candidate.is_file():
        data = candidate.read_bytes()
    else:
        # Prefer a directly-stored gzipped file if present.
        compressed = candidate.with_name(candidate.name + ".gz")
        if compressed.is_file():
            try:
                data = gzip.decompress(compressed.read_bytes())
            except (OSError, EOFError) as exc:
                raise SystemExit(f"Cannot decompress original PDF asset {compressed}: {exc}")
        elif SOURCE_ARCHIVE.is_file():
            # The git repository keeps the source PDFs in a compressed ZIP to
            # reduce repository size; this reads bytes from the original entries.
            try:
                with zipfile.ZipFile(SOURCE_ARCHIVE) as archive:
                    if doc["filename"] not in archive.namelist():
                        return None
                    data = archive.read(doc["filename"])
            except (OSError, zipfile.BadZipFile, KeyError) as exc:
                raise SystemExit(f"Cannot read original source PDF archive {SOURCE_ARCHIVE}: {exc}")
        else:
            return None
    if not data.startswith(b"%PDF-"):
        raise SystemExit(f"Original asset is not a PDF: {candidate}")
    return data


def sync_flags(catalog):
    docs = {doc["id"]: doc for doc in catalog["documents"]}
    for doc in catalog["documents"]:
        original = doc["id"] in ORIGINAL_IDS
        doc["originalPdfBundled"] = original
        doc["offlinePdfCopyBundled"] = not original
        doc["sourceKind"] = "original_source_pdf" if original else "condensed_reading_copy"
    for doc in catalog.get("sourceDocuments", []):
        actual = docs.get(doc.get("id"))
        if actual:
            for key in ("filename", "sourceKind", "originalPdfBundled", "offlinePdfCopyBundled", "originalAssetPath"):
                if key in actual:
                    doc[key] = actual[key]


def main():
    global ORIGINAL_IDS
    assemble_source_archive()
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    docs = catalog.get("documents", [])
    if not docs:
        raise SystemExit("Plan catalog contains no documents.")
    ids = [doc.get("id") for doc in docs]
    filenames = [doc.get("filename") for doc in docs]
    if len(set(ids)) != len(ids) or len(set(filenames)) != len(filenames):
        raise SystemExit("Plan catalog has duplicate document IDs or filenames.")

    ORIGINAL_IDS = set()
    missing = []
    expected = set()
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(OUT, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for doc in docs:
            filename = doc.get("filename", "")
            if Path(filename).name != filename or not filename.lower().endswith(".pdf"):
                raise SystemExit(f"Unsafe PDF filename for {doc.get('id')}: {filename}")
            expected.add(filename)
            original_bytes = original_pdf_bytes_for(doc)
            if original_bytes:
                ORIGINAL_IDS.add(doc["id"])
                archive.writestr(filename, original_bytes)
            else:
                if doc.get("originalAssetPath"):
                    missing.append(f"{doc['id']} -> {doc['originalAssetPath']}")
                archive.writestr(filename, make_pdf(doc["title"], doc["subtitle"], doc["content"]))

    sync_flags(catalog)
    CATALOG.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    with zipfile.ZipFile(OUT, "r") as archive:
        if archive.testzip() is not None or set(archive.namelist()) != expected:
            raise SystemExit("Offline PDF archive failed ZIP integrity or filename validation")
        for name in expected:
            if not archive.read(name).startswith(b"%PDF-"):
                raise SystemExit(f"Invalid PDF header: {name}")

    copies = len(docs) - len(ORIGINAL_IDS)
    print(f"Validated {len(expected)} offline PDFs: {len(ORIGINAL_IDS)} original(s), {copies} condensed copy/copies.")
    if missing:
        print("WARNING: Original source PDF assets still need to be committed:")
        for item in missing:
            print("  - " + item)
    if copies:
        print("NOTE: This build is not original-PDF-complete; condensed files remain labelled as copies.")


if __name__ == "__main__":
    main()
