#!/usr/bin/env python3
"""Builds app/src/main/assets/brands.json: UK food brands with logos, from the OSM name-suggestion-index.

Usage: python3 tools/build_brands.py [NSI_VERSION]
Downloads the pinned NSI release from jsDelivr (BSD-3-Clause; Wikidata-derived parts CC0).
The app matches FSA business names against these names entirely offline.
"""
import json, re, sys, unicodedata, urllib.request, urllib.parse, os

VERSION = sys.argv[1] if len(sys.argv) > 1 else "8.0.20260918"
BASE = f"https://cdn.jsdelivr.net/npm/name-suggestion-index@{VERSION}/dist"
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "brands.json")

PATHS = {
    "brands/amenity/" + v for v in ["fast_food", "restaurant", "cafe", "pub", "bar", "ice_cream", "biergarten", "food_court"]
} | {
    "brands/shop/" + v for v in ["supermarket", "convenience", "bakery", "butcher", "deli", "confectionery", "greengrocer", "alcohol",
                                 "pastry", "chocolate", "coffee", "tea", "seafood", "cheese", "frozen_food", "beverages", "wine",
                                 "health_food", "dairy", "spices", "nuts", "department_store", "chemist"]
} | {"brands/tourism/hotel", "operators/amenity/pub", "operators/shop/convenience"}

UK = re.compile(r"^(gb|gb-.*|001|150)$")


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Higiene build script (+https://github.com/Sammeeeeeeee/Higiene)"})
    with urllib.request.urlopen(req, timeout=120) as r:
        return json.load(r)


def norm(s):
    """Must match Brands.normalise() in the app."""
    s = unicodedata.normalize("NFKD", s.lower())
    s = "".join(c for c in s if not unicodedata.combining(c))
    s = s.replace("&", " and ").replace("'", "").replace("’", "")
    s = re.sub(r"[^a-z0-9]+", " ", s)
    return " ".join(s.split())


def is_uk(loc):
    inc = [str(x).lower() for x in loc.get("include", []) if isinstance(x, str)]
    exc = [str(x).lower() for x in loc.get("exclude", []) if isinstance(x, str)]
    return any(UK.match(x) for x in inc) and "gb" not in exc


def main():
    nsi = fetch(f"{BASE}/json/nsi.min.json")["nsi"]
    wd = fetch(f"{BASE}/wikidata/wikidata.min.json")["wikidata"]
    brands, names, aliases = [], {}, {}
    by_qid = {}
    for path, group in nsi.items():
        if path not in PATHS:
            continue
        for item in group.get("items", []):
            if not is_uk(item.get("locationSet", {})):
                continue
            tags = item.get("tags", {})
            qid = tags.get("brand:wikidata") or tags.get("operator:wikidata")
            if not qid:
                continue
            logos = (wd.get(qid) or {}).get("logos") or {}
            commons = None
            w = logos.get("wikidata")
            if w:
                m = re.search(r"Special:FilePath/(.+)$", w) or re.search(r"Special%3ARedirect%2Ffile%2F([^&]+)", w)
                if m:
                    commons = urllib.parse.unquote(m.group(1))
            fb = None
            f = logos.get("facebook")
            if f:
                m = re.search(r"graph\.facebook\.com/([^/]+)/picture", f)
                if m:
                    fb = m.group(1)
            if not commons and not fb:
                continue
            if qid not in by_qid:
                by_qid[qid] = len(brands)
                brands.append([tags.get("brand") or item.get("displayName") or tags.get("name"), qid, commons, fb])
            idx = by_qid[qid]
            # Real names win over matchNames (alternate spellings), which can collide across brands.
            for k in {item.get("displayName", ""), tags.get("name", ""), tags.get("brand", ""), tags.get("operator", "")}:
                n = norm(k or "")
                # Very short keys ("bp", "m") would match too many independents.
                if len(n) >= 3 and n not in names:
                    names[n] = idx
            for k in item.get("matchNames", []):
                n = norm(k or "")
                if len(n) >= 3 and n not in aliases:
                    aliases[n] = idx
    for n, idx in aliases.items():
        names.setdefault(n, idx)
    out = {"source": f"name-suggestion-index {VERSION}", "brands": brands, "names": dict(sorted(names.items()))}
    with open(OUT, "w", encoding="utf-8") as fh:
        json.dump(out, fh, ensure_ascii=False, separators=(",", ":"))
    print(f"{len(brands)} brands, {len(names)} names -> {os.path.relpath(OUT)} ({os.path.getsize(OUT)} bytes)")


if __name__ == "__main__":
    main()
