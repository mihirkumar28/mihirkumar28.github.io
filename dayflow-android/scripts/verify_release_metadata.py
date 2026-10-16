#!/usr/bin/env python3
"""Generate update.json only after verifying the release APK's manifest version."""

import argparse
import json
import os
import pathlib
import re
import subprocess
import sys


def parse_apk_badging(output):
    package_line = next(
        (line for line in output.splitlines() if line.startswith("package:")),
        "",
    )
    package_name = re.search(r"name='([^']+)'", package_line)
    version_code = re.search(r"versionCode='(\\d+)'", package_line)
    version_name = re.search(r"versionName='([^']+)'", package_line)
    if not package_name or not version_code or not version_name:
        raise ValueError("Could not read package/version metadata from the release APK.")
    return package_name.group(1), int(version_code.group(1)), version_name.group(1)


def expected_release_version(run_number):
    run_number = int(run_number)
    if run_number <= 0:
        raise ValueError("GitHub run number must be positive.")
    return run_number, f"1.0.{run_number}"


def validate_release_version(package_name, apk_code, apk_name, run_number):
    expected_code, expected_name = expected_release_version(run_number)
    if package_name != "com.mihirkumar.dayflow":
        raise ValueError("Release APK has an unexpected package name.")
    if apk_code != expected_code or apk_name != expected_name:
        raise ValueError(
            f"APK version mismatch: expected {expected_code}/{expected_name}, "
            f"got {apk_code}/{apk_name}."
        )
    return expected_code, expected_name


def find_aapt():
    candidates = [
        os.environ.get("ANDROID_HOME"),
        os.environ.get("ANDROID_SDK_ROOT"),
        "/usr/local/lib/android/sdk",
    ]
    sdk_roots = [pathlib.Path(candidate) for candidate in candidates if candidate]
    sdk = next((root for root in sdk_roots if (root / "build-tools").is_dir()), None)
    if sdk is None:
        raise FileNotFoundError("Android SDK build-tools directory was not found.")

    tools = [path for path in (sdk / "build-tools").glob("*/aapt") if path.is_file()]
    if not tools:
        raise FileNotFoundError("Android aapt tool was not found.")

    def version_key(path):
        return tuple(int(piece) for piece in re.findall(r"\\d+", path.parent.name))

    return max(tools, key=version_key)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True, type=pathlib.Path)
    parser.add_argument("--metadata", required=True, type=pathlib.Path)
    parser.add_argument("--run-number", required=True, type=int)
    args = parser.parse_args()

    if not args.apk.is_file():
        raise FileNotFoundError(f"Release APK is missing: {args.apk}")

    result = subprocess.run(
        [str(find_aapt()), "dump", "badging", str(args.apk)],
        check=True,
        capture_output=True,
        text=True,
    )
    package_name, apk_code, apk_name = parse_apk_badging(result.stdout)
    expected_code, expected_name = validate_release_version(
        package_name, apk_code, apk_name, args.run_number
    )

    payload = {
        "versionCode": expected_code,
        "versionName": expected_name,
        "releaseNotes": "Latest DayFlow Android build with bug fixes and improvements.",
    }
    args.metadata.parent.mkdir(parents=True, exist_ok=True)
    args.metadata.write_text(json.dumps(payload, indent=2) + "\\n", encoding="utf-8")

    written = json.loads(args.metadata.read_text(encoding="utf-8"))
    if written.get("versionCode") != apk_code or written.get("versionName") != apk_name:
        raise ValueError("Generated update metadata does not match the release APK.")

    print(f"Verified release APK and update metadata: {apk_code}/{apk_name}")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, FileNotFoundError, subprocess.CalledProcessError) as exc:
        print(f"::error::{exc}", file=sys.stderr)
        sys.exit(1)
