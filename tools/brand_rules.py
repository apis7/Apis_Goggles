#!/usr/bin/env python3
"""Rewrite whole-site discards as brand-wide rules, so discarding abc.com also
discards abc.co.uk, abc.net.au, news.abc.com and so on.

A brand rule is the pair
    ://abc.$discard   host starts with "abc." (abc.com, abc.co.uk)
    .abc.$discard     "abc" is an inner host label (www.abc.co.uk, news.abc.com)
which matches the host label exactly, unlike the old *abc*$discard,inurl that
hit any URL containing "abc" anywhere (abcam.com, /abc-of-gardening/).

Usage: brand_rules.py <file.goggle> [...]   (rewrites in place, idempotent)
"""
import re
import sys
from pathlib import Path

# Old substring patterns -> the brand labels they were meant to catch.
LEGACY_PATTERNS = {
    "reuters": ["reuters"],
    "yahoo": ["yahoo"],
    "abc": ["abc", "abcnews", "abc7", "abc7news", "abc7ny", "abc7chicago", "abc13", "abc11"],
    "google": ["google"],
    "cbs": ["cbs", "cbsnews", "cbssports"],
    "foxnews": ["foxnews", "foxbusiness"],
    "bbc": ["bbc"],
    "pbs": ["pbs"],
    "npr": ["npr"],
    "cnn": ["cnn"],
}

# Mirrors Coverage.GENERIC_BRANDS in the app: names too common to mean one publisher.
GENERIC = set("""
news health time times people the get web online info blog home app mail shop store
world daily live media radio net site post press today life food diet med medical doctor
mind boston who acs lung kidney arthritis alz
""".split())
SLDS = {"co", "com", "org", "net", "gov", "ac", "edu", "or", "ne", "go"}

SITE_DISCARD = re.compile(r"^\$discard,site=([a-z0-9.-]+)$")
LEGACY = re.compile(r"^\*([a-z0-9]+)\*\$discard,inurl$")
BRAND_RULE = re.compile(r"^(?:://|\.)([a-z0-9-]+)\.\$discard$")


def brand_of(domain):
    """The brand label when the domain is a bare registrable name, else None."""
    labels = domain.split(".")
    plain = len(labels) == 2 or (len(labels) == 3 and labels[1] in SLDS and len(labels[2]) == 2)
    brand = labels[0]
    if not plain or len(brand) < 3 or brand in GENERIC:
        return None
    return brand


def rewrite(path):
    lines = path.read_text(encoding="utf-8").split("\n")
    out, brands, converted = [], set(), 0
    boosted = [m.group(1) for l in lines if (m := re.match(r"^\$boost=?\d*,site=([a-z0-9.-]+)$", l.strip()))]

    def emit(brand):
        if brand not in brands:
            brands.add(brand)
            out.extend([f"://{brand}.$discard", f".{brand}.$discard"])

    for raw in lines:
        line = raw.strip()
        if m := LEGACY.match(line):
            if m.group(1) in LEGACY_PATTERNS:
                for b in LEGACY_PATTERNS[m.group(1)]:
                    emit(b)
                converted += 1
                continue
        elif m := BRAND_RULE.match(line):
            emit(m.group(1))
            continue
        elif (m := SITE_DISCARD.match(line)) and (b := brand_of(m.group(1))):
            emit(b)
            converted += 1
            continue
        out.append(raw)

    path.write_text("\n".join(out), encoding="utf-8", newline="\n")
    print(f"{path.name}: {converted} rules -> {len(brands)} brands")
    # A brand discard beats any boost, so flag boosted sites it would swallow.
    for site in boosted:
        hit = [b for b in brands if b in site.split(".")[:-1]]
        if hit:
            print(f"  WARNING: boosted {site} is discarded by brand {hit}")


if __name__ == "__main__":
    for p in sys.argv[1:]:
        rewrite(Path(p))
