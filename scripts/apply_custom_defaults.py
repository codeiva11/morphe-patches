#!/usr/bin/env python3
import re
import sys
from pathlib import Path

settings_file = Path("extensions/youtube/src/main/java/app/morphe/extension/youtube/settings/Settings.java")
if not settings_file.exists():
    print(f"Error: {settings_file} not found!")
    sys.exit(1)

content = settings_file.read_text(encoding="utf-8")

# 1. Header logo -> PREMIUM
content, c1 = re.subn(
    r'(HEADER_LOGO\s*=\s*new\s*EnumSetting<[^>]*>\s*\(\s*"morphe_header_logo"\s*,\s*HeaderLogo\.)[A-Za-z0-9_]+',
    r'\g<1>PREMIUM',
    content
)

# 2. Swipe left zone -> VOLUME
content, c2 = re.subn(
    r'(SWIPE_LEFT_ZONE\s*=\s*new\s*EnumSetting<[^>]*>\s*\(\s*"morphe_swipe_left_zone"\s*,\s*SwipeZoneAction\.)[A-Za-z0-9_]+',
    r'\g<1>VOLUME',
    content
)

# 3. Swipe right zone -> BRIGHTNESS
content, c3 = re.subn(
    r'(SWIPE_RIGHT_ZONE\s*=\s*new\s*EnumSetting<[^>]*>\s*\(\s*"morphe_swipe_right_zone"\s*,\s*SwipeZoneAction\.)[A-Za-z0-9_]+',
    r'\g<1>BRIGHTNESS',
    content
)

settings_file.write_text(content, encoding="utf-8")
print(f"Patched: HeaderLogo={c1}, SwipeLeft={c2}, SwipeRight={c3}")
if c1 == 0 or c2 == 0 or c3 == 0:
    print("Warning: One or more settings could not be matched!")
    sys.exit(1)
print("Settings.java successfully updated with Premium defaults.")
