#!/usr/bin/env python3
"""Builds osm-fhrs.tsv: OpenStreetMap details for UK food businesses, keyed by FSA FHRSID.

Usage: python3 -I tools/osm_extract.py INPUT.osm.pbf OUTPUT.tsv

Reads every node, way and relation tagged fhrs:id (the FSA FHRSID) and writes one row per FHRSID
with the tags the app shows (opening hours, website, phone, cuisine...). Only tags are read; no
geometry and no user/uid/version/timestamp metadata. Run weekly by .github/workflows/osm-data.yml
on the Geofabrik united-kingdom extract; the app downloads the result instead of querying Overpass.

Output (UTF-8, "\\n" line endings):
  line 1: "# higiene-osm 1 <data timestamp, ISO-8601 UTC, or 'unknown'>"
  line 2: the column names, tab-separated
  then one tab-separated row per FHRSID, sorted by FHRSID ascending.
Values never contain tabs or line breaks. Empty string means "not tagged".

Map data (c) OpenStreetMap contributors, ODbL 1.0.
"""
import datetime
import os
import re
import sys
from collections import Counter

import osmium

FORMAT_VERSION = 1
COLUMNS = (
    "fhrs_id", "osm", "opening_hours", "cuisine", "website", "phone", "email", "menu", "brand_wikidata",
    "wheelchair", "takeaway", "delivery", "outdoor_seating", "diets",
)
FIELDS = COLUMNS[2:]

# Field -> OSM keys, first non-empty wins. "diets" is built separately.
SOURCES = {
    "opening_hours": ("opening_hours",),
    "cuisine": ("cuisine",),
    "website": ("website", "contact:website", "url"),
    "phone": ("phone", "contact:phone"),
    "email": ("email", "contact:email"),
    "menu": ("website:menu",),
    "brand_wikidata": ("brand:wikidata",),
    "wheelchair": ("wheelchair",),
    "takeaway": ("takeaway",),
    "delivery": ("delivery",),
    "outdoor_seating": ("outdoor_seating",),
}
DIETS = ("vegetarian", "vegan", "halal", "kosher", "gluten_free")
DIET_YES = ("yes", "only")

MAX_LEN = 300
MAX_ID = 2**31 - 1  # FHRSIDs are small; keeps the app's Int parsing safe.
TYPE_ORDER = {"n": 0, "w": 1, "r": 2}

_ID_SPLIT = re.compile(r"[;,]")
# Tab, CR, LF and every other control or line-separator character become a space.
_CONTROL = re.compile("[\x00-\x1f\x7f\x85  ]")
_SPACES = re.compile(" {2,}")


def parse_ids(value):
    """FHRSIDs from an fhrs:id value: split on ';' and ',', keep positive all-ASCII-digit tokens, in order, unique."""
    ids = []
    for token in _ID_SPLIT.split(value or ""):
        token = token.strip()
        if token and len(token) <= 10 and token.isascii() and token.isdigit():
            n = int(token)
            if 0 < n <= MAX_ID and n not in ids:
                ids.append(n)
    return ids


def sanitise(value):
    """One-line value: control characters to spaces, runs of spaces collapsed, stripped, at most MAX_LEN chars."""
    if not value:
        return ""
    value = _SPACES.sub(" ", _CONTROL.sub(" ", value)).strip()
    return value[:MAX_LEN].rstrip()


def extract_fields(tags):
    """Values for FIELDS (in order) from a {key: value} tag mapping. All empty means nothing useful."""
    out = []
    for field in FIELDS:
        if field == "diets":
            out.append(",".join(d for d in DIETS if sanitise(tags.get("diet:" + d)).lower() in DIET_YES))
        else:
            out.append(next((v for v in (sanitise(tags.get(k)) for k in SOURCES[field]) if v), ""))
    return tuple(out)


def normalise_timestamp(value):
    """ISO-8601 UTC "YYYY-MM-DDTHH:MM:SSZ" from a header value, or "unknown"."""
    try:
        t = datetime.datetime.fromisoformat((value or "").strip())
        if t.tzinfo is None:
            t = t.replace(tzinfo=datetime.timezone.utc)
        return t.astimezone(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    except (ValueError, OverflowError):
        return "unknown"


class Extractor:
    """Collects objects and keeps the best one per FHRSID."""

    def __init__(self):
        self.best = {}  # fhrs_id -> (rank, osm, fields)
        self.stats = Counter()

    def add(self, osm_type, osm_id, tags):
        """Offers one OSM object ("n"/"w"/"r", id, {key: value})."""
        self.stats["scanned"] += 1
        self.stats["scanned_" + osm_type] += 1
        ids = parse_ids(tags.get("fhrs:id"))
        if not ids:
            self.stats["no_valid_id"] += 1
            return
        if len(ids) > 1:
            self.stats["multi_value_objects"] += 1
            self.stats["ids_from_multi_values"] += len(ids)
        fields = extract_fields(tags)
        filled = sum(1 for v in fields if v)
        if not filled:
            self.stats["empty"] += 1
            return
        # Most filled fields first, then node < way < relation, then lowest id.
        candidate = ((-filled, TYPE_ORDER[osm_type], osm_id), f"{osm_type}{osm_id}", fields)
        for fhrs_id in ids:
            current = self.best.get(fhrs_id)
            if current is not None:
                self.stats["duplicates_dropped"] += 1
                if current[0] <= candidate[0]:
                    continue
            self.best[fhrs_id] = candidate

    def rows(self):
        """(fhrs_id, osm, *fields) tuples sorted by fhrs_id."""
        return [(fhrs_id, osm, *fields) for fhrs_id, (_, osm, fields) in sorted(self.best.items())]


def write_tsv(out, rows, timestamp):
    """Writes the header lines and rows to a text stream."""
    out.write(f"# higiene-osm {FORMAT_VERSION} {timestamp}\n")
    out.write("\t".join(COLUMNS) + "\n")
    for row in rows:
        out.write("\t".join(str(v) for v in row) + "\n")


def read_timestamp(path):
    """The extract's data timestamp from the file header, or "unknown"."""
    reader = osmium.io.Reader(path, osmium.osm.osm_entity_bits.NOTHING)
    try:
        header = reader.header()
        value = header.get("osmosis_replication_timestamp") or header.get("timestamp")
    finally:
        reader.close()
    return normalise_timestamp(value)


def scan(path, extractor):
    """Feeds every node/way/relation with an fhrs:id tag into the extractor (tags only)."""
    entities = osmium.osm.NODE | osmium.osm.WAY | osmium.osm.RELATION
    processor = osmium.FileProcessor(path, entities).with_filter(osmium.filter.KeyFilter("fhrs:id"))
    for obj in processor:
        extractor.add(obj.type_str(), obj.id, {t.k: t.v for t in obj.tags})


def format_stats(stats, rows):
    lines = [
        f"objects scanned: {stats['scanned']} "
        f"(nodes {stats['scanned_n']}, ways {stats['scanned_w']}, relations {stats['scanned_r']})",
        f"no valid id: {stats['no_valid_id']}",
        f"no useful tags: {stats['empty']}",
        f"ids split from multi-values: {stats['ids_from_multi_values']} (from {stats['multi_value_objects']} objects)",
        f"duplicates dropped: {stats['duplicates_dropped']}",
        f"rows written: {len(rows)}",
        "non-empty per field:",
    ]
    lines += [f"  {field}: {sum(1 for r in rows if r[i])}" for i, field in enumerate(FIELDS, start=2)]
    return "\n".join(lines)


def main(argv):
    if len(argv) != 3:
        print(__doc__.split("\n\n")[1], file=sys.stderr)
        return 2
    src, dst = argv[1], argv[2]
    timestamp = read_timestamp(src)
    extractor = Extractor()
    scan(src, extractor)
    rows = extractor.rows()
    tmp = dst + ".tmp"
    with open(tmp, "w", encoding="utf-8", newline="\n") as out:
        write_tsv(out, rows, timestamp)
    os.replace(tmp, dst)
    print(f"data timestamp: {timestamp}", file=sys.stderr)
    print(format_stats(extractor.stats, rows), file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
