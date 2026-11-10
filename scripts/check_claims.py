#!/usr/bin/env python3
"""Reject misleading shipped claims, including Kotlin literals compiled into dex."""
import argparse
import pathlib
import re
import subprocess
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
FORBIDDEN = re.compile(
    r"(?:close[sd]?|closing|dismiss(?:es|ed|ing)?)\b[^\n\r]{0,50}\brecents\b"
    r"|\brecents\b[^\n\r]{0,50}\b(?:close[sd]?|closing|dismiss(?:es|ed|ing)?)\b"
    r"|\b(?:\d+(?:\.\d+)?\s*)?(?:gb|mb)\s+(?:freed|saved)\b"
    r"|\b(?:\d+(?:\.\d+)?\s*%\s*)?boost(?:ed|ing)?\b"
    r"|\bfree(?:d)?\s+up\s+\d+(?:\.\d+)?\s*(?:gb|mb)\b",
    re.IGNORECASE,
)


def check(text: str, origin: str) -> None:
    match = FORBIDDEN.search(text)
    if match:
        raise ValueError(f"{origin}: forbidden claim: {match.group()}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=pathlib.Path)
    parser.add_argument("--aapt")
    args = parser.parse_args()
    for path in (ROOT / "app/src/main/java/dev/killer/ui").rglob("*.kt"):
        # Check string literals, not documentation/comments about forbidden wording.
        for literal in re.findall(r'"(?:\\.|[^"\\])*"', path.read_text()):
            check(literal, str(path.relative_to(ROOT)))
    for path in (ROOT / "app/src/main/res/values").glob("*.xml"):
        check(path.read_text(), str(path.relative_to(ROOT)))
    engine = (ROOT / "app/src/main/java/dev/killer/engine").glob("*.kt")
    for path in engine:
        text = path.read_text()
        if re.search(r"GLOBAL_ACTION_RECENTS|dismissFromRecents|dispatchGesture|swipeAway", text):
            raise ValueError(f"{path.name}: Recents automation must not ship")
    if args.apk:
        if not args.aapt:
            parser.error("--aapt is required with --apk")
        check(subprocess.check_output([args.aapt, "dump", "strings", str(args.apk)], text=True), "APK resources")
        with zipfile.ZipFile(args.apk) as apk:
            for name in apk.namelist():
                if re.fullmatch(r"classes\d*\.dex", name):
                    # Android dex strings are UTF-8 encoded; inspect printable runs.
                    for literal in re.findall(rb"[\x20-\x7e]{5,}", apk.read(name)):
                        check(literal.decode("ascii"), name)
    print("Claim and Recents-automation checks passed")


if __name__ == "__main__":
    main()
