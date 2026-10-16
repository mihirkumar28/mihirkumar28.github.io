#!/usr/bin/env python3
"""Fail CI if the DayFlow home-screen widget uses unsupported RemoteViews views or stale IDs."""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LAYOUT_PATH = ROOT / "app/src/main/res/layout/widget_dayflow.xml"
INFO_PATH = ROOT / "app/src/main/res/xml/dayflow_widget_info.xml"
PROVIDER_PATH = ROOT / "app/src/main/java/com/mihirkumar/dayflow/DayFlowWidgetProvider.java"

# RemoteViews supports a restricted set of platform widget classes. Keep this list
# intentionally narrow to what this widget currently uses and what Android supports.
SUPPORTED_TAGS = {
    "LinearLayout",
    "FrameLayout",
    "RelativeLayout",
    "GridLayout",
    "TextView",
    "Chronometer",
    "ImageView",
    "ImageButton",
    "Button",
    "ProgressBar",
    "TextClock",
}

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def main() -> None:
    for path in (LAYOUT_PATH, INFO_PATH, PROVIDER_PATH):
        if not path.is_file():
            fail(f"required widget file is missing: {path.relative_to(ROOT)}")

    try:
        layout_root = ET.parse(LAYOUT_PATH).getroot()
        info_root = ET.parse(INFO_PATH).getroot()
    except ET.ParseError as exc:
        fail(f"invalid widget XML: {exc}")

    tags = {element.tag.rsplit("}", 1)[-1] for element in layout_root.iter()}
    unsupported = sorted(tags - SUPPORTED_TAGS)
    if unsupported:
        fail("unsupported RemoteViews tag(s) in widget_dayflow.xml: " + ", ".join(unsupported))

    ids: list[str] = []
    for element in layout_root.iter():
        view_id = element.attrib.get(ANDROID_NS + "id", "")
        match = re.fullmatch(r"@\+id/([A-Za-z0-9_]+)", view_id)
        if match:
            ids.append(match.group(1))
    duplicates = sorted({view_id for view_id in ids if ids.count(view_id) > 1})
    if duplicates:
        fail("duplicate widget view ID(s): " + ", ".join(duplicates))

    provider_source = PROVIDER_PATH.read_text(encoding="utf-8")
    referenced_ids = set(re.findall(r"R\.id\.(widget_[A-Za-z0-9_]+)", provider_source))
    missing_ids = sorted(referenced_ids - set(ids))
    if missing_ids:
        fail("widget provider references IDs missing from XML: " + ", ".join(missing_ids))

    initial_layout = info_root.attrib.get(ANDROID_NS + "initialLayout", "")
    if initial_layout != "@layout/widget_dayflow":
        fail(f"unexpected initial widget layout: {initial_layout!r}")

    if not 1 <= len(ids) <= 80:
        fail(f"unexpected widget ID count: {len(ids)}")

    print(
        "Widget RemoteViews validation passed: "
        f"{len(tags)} supported view types, {len(ids)} unique IDs, "
        f"{len(referenced_ids)} provider references."
    )


if __name__ == "__main__":
    main()
