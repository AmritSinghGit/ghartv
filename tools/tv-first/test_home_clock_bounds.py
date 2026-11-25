#!/usr/bin/env python3
"""TV-emulator regression check for the fixed/pinned GharTV home clock.

Read-only application inspection; temporarily adjusts ONLY emulator display size,
always restores the prior override, and never reads account data or phone tokens.
Run while the canonical blue GharTV home screen is visible without a dialog.
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

SERIAL = "emulator-5554"
COMPONENT = "in.ghartv.nova/.MainActivity"
DUMP = "/sdcard/ghartv-home-clock-test.xml"


def adb(*args, timeout=35):
    result = subprocess.run(["adb", "-s", SERIAL, *args], capture_output=True,
                            text=True, timeout=timeout, check=True)
    return result.stdout


def bounds(node):
    raw = node.attrib.get("bounds", "")
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", raw)
    return tuple(map(int, match.groups())) if match else None


def sample(width, height):
    adb("shell", "wm", "size", f"{width}x{height}")
    adb("shell", "am", "start", "-n", COMPONENT)
    # A resumed Activity can be visible before first-frame accessibility text
    # exists. Retry observing the *same real widget*, never fabricate a clock.
    clock = []
    nodes = []
    for attempt in range(4):
        time.sleep(0.7)
        adb("shell", "uiautomator", "dump", DUMP, timeout=50)
        xml = ET.fromstring(adb("shell", "cat", DUMP, timeout=10))
        nodes = list(xml.iter("node"))
        clock = [(n, bounds(n)) for n in nodes
                 if re.fullmatch(r"\d{1,2}:\d{2}\s*[ap]m", n.attrib.get("text", ""), re.I)
                 and bounds(n)]
        if clock:
            break
    if len(clock) != 1:
        raise AssertionError(f"Expected exactly one full clock at {width}x{height}, got {len(clock)} after 4 real UI checks")
    node, (left, top, right, bottom) = clock[0]
    assert left >= 0 and right <= width - 16, (
        f"Clock extends past the TV at {width}x{height}: {left}..{right}")
    # 110dp at the emulator's standard 2x density provides the unellipsized
    # 17sp '12:59 PM' text; 160px is a conservative lower safety bound.
    assert right - left >= 160, (
        f"Clock could truncate the AM/PM label: {right-left}px at {width}x{height}")
    assert bottom > top and top >= 0
    if width >= 1920:
        for label in ("Search / mic", "Update guide", "IPTV", "FlixMomo"):
            hits = [bounds(n) for n in nodes if n.attrib.get("text") == label and bounds(n)]
            assert hits and 0 <= hits[0][0] < hits[0][2] <= width, (
                f"Lost or clipped top action {label!r} at {width}x{height}")
    print(f"PASS {width}x{height}: complete clock inside viewport; width {right-left}px")


def main():
    devices = adb("get-state").strip()
    assert devices == "device", "TV emulator is not online"
    original = adb("shell", "wm", "size")
    override = re.search(r"Override size:\s*(\d+x\d+)", original)
    try:
        sample(1920, 1080)
        sample(1280, 720)
    finally:
        adb("shell", "wm", "size", override.group(1) if override else "reset")
        try:
            adb("shell", "rm", "-f", DUMP)
        except (OSError, subprocess.SubprocessError):
            pass
    print("PASS original emulator resolution restored; no app data touched")


if __name__ == "__main__":
    try:
        main()
    except (AssertionError, OSError, subprocess.SubprocessError, ET.ParseError) as exc:
        print(f"FAIL home clock regression: {exc}", file=sys.stderr)
        sys.exit(1)
