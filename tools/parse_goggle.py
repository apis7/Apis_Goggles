#!/usr/bin/env python3
"""Parse a Brave Goggle file into a structured CSV of its elements/rules."""
import csv
import re
import sys

# Usage: parse_goggle.py <file.goggle> [out.csv]
SRC = sys.argv[1] if len(sys.argv) > 1 else "apis_search.goggle"
OUT = sys.argv[2] if len(sys.argv) > 2 else SRC.rsplit(".", 1)[0] + "_elements.csv"

ACTIONS = ("discard", "downrank", "boost")


def parse_line(raw, section):
    """Return a dict describing a single goggle rule line, or None to skip."""
    line = raw.strip()
    if not line or line.startswith("!"):
        return None

    # Split pattern (before first '$') from the instruction (after first '$').
    if "$" not in line:
        return None
    pattern, _, instr = line.partition("$")

    # Some lines double up (e.g. site=*reuters*$discard,inurl); keep only the
    # first instruction group, but preserve the full raw line for fidelity.
    instr = instr.split("$")[0]
    parts = [p.strip() for p in instr.split(",") if p.strip()]

    action = ""
    rank = ""
    site = ""
    location = ""  # inurl / intitle etc.

    for p in parts:
        key, _, val = p.partition("=")
        key = key.lower()
        if key in ACTIONS:
            action = key
            rank = val  # e.g. boost=7 -> "7"
        elif key == "site":
            site = val
        elif key in ("inurl", "intitle", "indomain"):
            location = key

    # Determine what the rule matches on: an explicit site, or the URL pattern.
    target = site if site else pattern.strip()
    match_field = "site" if site else (location or "url_pattern")

    return {
        "section": section,
        "action": action,
        "rank": rank,
        "target": target,
        "match_field": match_field,
        "location": location,
        "raw_rule": line,
    }


def main():
    section = ""
    rows = []
    with open(SRC, encoding="utf-8") as fh:
        for raw in fh:
            stripped = raw.strip()
            m = re.match(r"!\s*(DISCARD|DOWNRANK|BOOST|NEUTRAL)\s+LIST", stripped, re.I)
            if m:
                section = m.group(1).upper()
                continue
            row = parse_line(raw, section)
            if row:
                rows.append(row)

    fields = ["section", "action", "rank", "target", "match_field", "location", "raw_rule"]
    with open(OUT, "w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=fields)
        w.writeheader()
        w.writerows(rows)

    counts = {}
    for r in rows:
        counts[r["action"]] = counts.get(r["action"], 0) + 1
    print(f"Wrote {len(rows)} rules to {OUT}")
    for a, c in sorted(counts.items()):
        print(f"  {a or '(none)'}: {c}")


if __name__ == "__main__":
    sys.exit(main())
