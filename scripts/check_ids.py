#!/usr/bin/env python3
import json
import re

with open("app/src/main/assets/assets/json/mcm-map-data.json", "r", encoding="utf-8") as f:
    data = json.load(f)

known_ids = set()
for b in data:
    bid = b.get("id", "").strip().upper()
    known_ids.add(bid)
    if bid.startswith("B"):
        known_ids.add(bid[1:])

with open("app/src/main/assets/map_core.svg", "r", encoding="utf-8") as f:
    svg = f.read()

svg_ids = set(re.findall(r'<[a-zA-Z]+\b[^>]*\bid=["\']([A-Za-z0-9_\-]+)["\']', svg))

missing = []
for sid in sorted(svg_ids):
    if sid.startswith(("B", "SCOTT", "TURB", "POWER", "DISCOVERY")):
        clean_sid = sid.upper()
        if clean_sid not in known_ids and clean_sid[1:] not in known_ids:
            missing.append(sid)

print(f"Total known IDs in JSON: {len(known_ids)}")
print(f"Total matching SVG IDs: {len(svg_ids)}")
print(f"Missing from JSON: {len(missing)}")
for m in missing:
    print("  Missing ID in JSON:", m)
