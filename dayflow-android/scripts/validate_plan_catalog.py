#!/usr/bin/env python3
"""Validate structured roadmap references, PDF archive integrity, and truthful source flags."""
import argparse
import gzip
import json
import re
import zipfile
from pathlib import Path, PurePosixPath

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
CATALOG = ASSETS / "plans_catalog.json"
PLANS = ASSETS / "plans"
SOURCE_ARCHIVE = ROOT / "source_docs" / "original_source_pdfs.zip"
ARCHIVE = ASSETS / "plan_pdfs.zip"
MONTH_RE = re.compile(r"^\d{4}-(0[1-9]|1[0-2])$")


def fail(message):
    raise SystemExit("Plan catalog validation failed: " + message)


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


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--require-originals", action="store_true",
                        help="Fail unless every document resolves to an original source PDF.")
    args = parser.parse_args()
    assemble_source_archive()
    if not CATALOG.is_file():
        fail("catalog not found: " + str(CATALOG))
    try:
        catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail("catalog is not valid JSON: " + str(exc))
    if catalog.get("schemaVersion") != 1:
        fail("schemaVersion must be 1")
    if not isinstance(catalog.get("sourcePlanVersion"), int) or catalog["sourcePlanVersion"] < 1:
        fail("sourcePlanVersion must be a positive integer")

    docs = catalog.get("documents")
    if not isinstance(docs, list) or not docs:
        fail("documents must be a non-empty list")
    by_id, filenames = {}, set()
    original_count, missing_originals = 0, []
    for doc in docs:
        doc_id, filename = doc.get("id"), doc.get("filename")
        if not isinstance(doc_id, str) or not doc_id.strip() or doc_id in by_id:
            fail("missing or duplicate document id: " + repr(doc_id))
        if not isinstance(filename, str) or not filename.endswith(".pdf") or PurePosixPath(filename).name != filename:
            fail(f"unsafe or invalid filename for {doc_id}: {filename!r}")
        if filename in filenames:
            fail("duplicate PDF filename: " + filename)
        by_id[doc_id], _ = doc, filenames.add(filename)
        original = bool(doc.get("originalPdfBundled", False))
        copy = bool(doc.get("offlinePdfCopyBundled", False))
        if original == copy:
            fail(f"{doc_id} must be exactly one of original PDF or condensed offline copy")
        expected_kind = "original_source_pdf" if original else "condensed_reading_copy"
        if doc.get("sourceKind") != expected_kind:
            fail(f"{doc_id} has inconsistent sourceKind/original flags")

        rel = doc.get("originalAssetPath")
        exists = False
        if rel:
            relative = PurePosixPath(rel)
            if relative.is_absolute() or ".." in relative.parts or not relative.parts or relative.parts[0] != "plans":
                fail(f"unsafe originalAssetPath for {doc_id}: {rel}")
            candidate = (ASSETS / Path(*relative.parts)).resolve()
            if not candidate.is_relative_to(PLANS.resolve()):
                fail(f"original asset path escapes plans/ for {doc_id}")
            compressed = candidate.with_name(candidate.name + ".gz")
            try:
                if candidate.is_file():
                    raw_pdf = candidate.read_bytes()
                elif compressed.is_file():
                    raw_pdf = gzip.decompress(compressed.read_bytes())
                elif SOURCE_ARCHIVE.is_file():
                    with zipfile.ZipFile(SOURCE_ARCHIVE) as source_zip:
                        raw_pdf = source_zip.read(filename) if filename in source_zip.namelist() else b""
                else:
                    raw_pdf = b""
            except (OSError, EOFError, zipfile.BadZipFile, KeyError) as exc:
                fail(f"cannot read/decompress original asset for {doc_id}: {exc}")
            exists = raw_pdf.startswith(b"%PDF-")
        if original:
            original_count += 1
            if not exists:
                fail(f"{doc_id} claims original PDF but its original asset is missing or invalid")
        elif rel and not exists:
            missing_originals.append(f"{doc_id} -> {rel}")

    source_ids = {doc.get("id") for doc in catalog.get("sourceDocuments", [])}
    if source_ids and source_ids != set(by_id):
        fail("sourceDocuments IDs do not match documents IDs")
    goal_ids = set()
    for month in catalog.get("months", []):
        key = month.get("key")
        if not isinstance(key, str) or not MONTH_RE.fullmatch(key):
            fail("invalid month key: " + repr(key))
        for goal in month.get("goals", []):
            goal_id = goal.get("id")
            if not isinstance(goal_id, str) or not goal_id.strip() or goal_id in goal_ids:
                fail("missing or duplicate goal ID: " + repr(goal_id))
            goal_ids.add(goal_id)
            for source_id in goal.get("sourceIds", []):
                if source_id not in by_id:
                    fail(f"goal {goal_id} references unknown source document {source_id}")

    if SOURCE_ARCHIVE.is_file():
        try:
            with zipfile.ZipFile(SOURCE_ARCHIVE) as source_zip:
                if source_zip.testzip() is not None:
                    fail("original source PDF archive failed ZIP integrity")
                allowed = set(filenames)
                for name in source_zip.namelist():
                    if name not in allowed:
                        fail("unexpected original source PDF archive entry: " + name)
                    if not source_zip.read(name).startswith(b"%PDF-"):
                        fail("original source archive entry is not a PDF: " + name)
        except zipfile.BadZipFile as exc:
            fail("invalid original source PDF ZIP: " + str(exc))

    if not ARCHIVE.is_file():
        fail("missing plan_pdfs.zip; run generate_offline_plan_pdfs.py first")
    try:
        with zipfile.ZipFile(ARCHIVE) as archive:
            if archive.testzip() is not None:
                fail("PDF archive failed ZIP integrity")
            if set(archive.namelist()) != filenames:
                fail("archive entries do not exactly match catalog filenames")
            for name in filenames:
                if not archive.read(name).startswith(b"%PDF-"):
                    fail("bad PDF header for " + name)
    except zipfile.BadZipFile as exc:
        fail("invalid PDF ZIP: " + str(exc))

    if args.require_originals and original_count != len(docs):
        fail(f"only {original_count}/{len(docs)} original PDFs are bundled")
    print(f"Plan catalog OK: {len(catalog.get('months', []))} months, "
          f"{len(goal_ids)} goals, {len(docs)} PDFs ({original_count} originals, "
          f"{len(docs) - original_count} condensed copies).")
    if missing_originals:
        print("Original PDF follow-up required:")
        for item in missing_originals:
            print("  - " + item)


if __name__ == "__main__":
    main()
