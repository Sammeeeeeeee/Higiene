"""Tests for osm_extract.py. Run: python3 -I -m unittest discover -s tools -p 'test_*.py'"""
import io
import itertools
import os
import subprocess
import sys
import tempfile
import unittest

import osm_extract as ox

HERE = os.path.dirname(os.path.abspath(__file__))
SCRIPT = os.path.join(HERE, "osm_extract.py")
SAMPLE = os.path.join(HERE, "testdata", "sample.opl")


def fields(**values):
    """A full FIELDS tuple with the given values and the rest empty."""
    return tuple(values.get(f, "") for f in ox.FIELDS)


def line(*values):
    return "\t".join(values) + "\n"


class ParseIdsTest(unittest.TestCase):
    def test_semicolon_list(self):
        self.assertEqual(ox.parse_ids("123;456"), [123, 456])

    def test_spaces_commas_junk_and_zero(self):
        self.assertEqual(ox.parse_ids(" 7 , x, 0"), [7])

    def test_not_a_number(self):
        self.assertEqual(ox.parse_ids("abc"), [])

    def test_edge_cases(self):
        self.assertEqual(ox.parse_ids(None), [])
        self.assertEqual(ox.parse_ids(""), [])
        self.assertEqual(ox.parse_ids(";;"), [])
        self.assertEqual(ox.parse_ids("0012;12"), [12])
        self.assertEqual(ox.parse_ids("-5;+6;7.0;1 2"), [])
        self.assertEqual(ox.parse_ids("١٢;²"), [])  # non-ASCII digits
        self.assertEqual(ox.parse_ids("2147483647;2147483648;99999999999999999999"), [2147483647])


class SanitiseTest(unittest.TestCase):
    def test_tabs_and_newlines(self):
        self.assertEqual(ox.sanitise("Mo-Fr\t09:00-17:00\r\nSa\n10:00"), "Mo-Fr 09:00-17:00 Sa 10:00")

    def test_spaces_collapsed_and_stripped(self):
        self.assertEqual(ox.sanitise("  a   b \t c  "), "a b c")

    def test_other_control_characters(self):
        self.assertEqual(ox.sanitise("a\x00b\x1fc d"), "a b c d")

    def test_empty(self):
        self.assertEqual(ox.sanitise(None), "")
        self.assertEqual(ox.sanitise(""), "")
        self.assertEqual(ox.sanitise(" \t\n "), "")

    def test_truncated(self):
        self.assertEqual(ox.sanitise("x" * 400), "x" * 300)
        self.assertEqual(ox.sanitise("x" * 299 + " y"), "x" * 299)
        self.assertEqual(ox.sanitise("é" * 301), "é" * 300)


class ExtractFieldsTest(unittest.TestCase):
    def get(self, tags, field):
        return ox.extract_fields(tags)[ox.FIELDS.index(field)]

    def test_column_order(self):
        self.assertEqual(ox.COLUMNS[:2], ("fhrs_id", "osm"))
        self.assertEqual(ox.FIELDS, (
            "opening_hours", "cuisine", "website", "phone", "email", "menu", "brand_wikidata",
            "wheelchair", "takeaway", "delivery", "outdoor_seating", "diets"))

    def test_direct_tags(self):
        tags = {
            "opening_hours": "24/7", "cuisine": "pizza", "website": "https://w", "phone": "1", "email": "e@x",
            "website:menu": "https://m", "brand:wikidata": "Q1", "wheelchair": "yes", "takeaway": "only",
            "delivery": "no", "outdoor_seating": "yes", "name": "ignored", "fhrs:id": "1",
        }
        self.assertEqual(ox.extract_fields(tags), fields(
            opening_hours="24/7", cuisine="pizza", website="https://w", phone="1", email="e@x", menu="https://m",
            brand_wikidata="Q1", wheelchair="yes", takeaway="only", delivery="no", outdoor_seating="yes"))

    def test_website_fallbacks(self):
        self.assertEqual(self.get({"website": "w", "contact:website": "c", "url": "u"}, "website"), "w")
        self.assertEqual(self.get({"contact:website": "c", "url": "u"}, "website"), "c")
        self.assertEqual(self.get({"url": "u"}, "website"), "u")
        self.assertEqual(self.get({"website": " \t", "url": "u"}, "website"), "u")

    def test_phone_and_email_fallbacks(self):
        self.assertEqual(self.get({"phone": "p", "contact:phone": "c"}, "phone"), "p")
        self.assertEqual(self.get({"contact:phone": "c"}, "phone"), "c")
        self.assertEqual(self.get({"email": "e", "contact:email": "c"}, "email"), "e")
        self.assertEqual(self.get({"contact:email": "c"}, "email"), "c")

    def test_values_sanitised(self):
        self.assertEqual(self.get({"opening_hours": "Mo\t10:00-12:00\n"}, "opening_hours"), "Mo 10:00-12:00")

    def test_diets(self):
        tags = {"diet:gluten_free": "yes", "diet:halal": "no", "diet:vegan": "only", "diet:vegetarian": "yes",
                "diet:kosher": "limited", "diet:pescetarian": "yes"}
        self.assertEqual(self.get(tags, "diets"), "vegetarian,vegan,gluten_free")
        self.assertEqual(self.get({"diet:halal": " Yes "}, "diets"), "halal")
        self.assertEqual(self.get({"diet:vegan": "no"}, "diets"), "")

    def test_nothing_useful(self):
        self.assertFalse(any(ox.extract_fields({"fhrs:id": "1", "name": "X", "amenity": "cafe"})))


class ExtractorTest(unittest.TestCase):
    def test_skips_object_without_useful_tags(self):
        ex = ox.Extractor()
        ex.add("n", 1, {"fhrs:id": "5", "name": "X", "amenity": "cafe"})
        self.assertEqual(ex.rows(), [])
        self.assertEqual(ex.stats["empty"], 1)

    def test_skips_object_without_valid_id(self):
        ex = ox.Extractor()
        ex.add("n", 1, {"fhrs:id": "none", "cuisine": "thai"})
        self.assertEqual(ex.rows(), [])
        self.assertEqual(ex.stats["no_valid_id"], 1)

    def test_multi_value_makes_a_row_per_id(self):
        ex = ox.Extractor()
        ex.add("w", 9, {"fhrs:id": "20;10", "cuisine": "thai"})
        self.assertEqual(ex.rows(), [(10, "w9", *fields(cuisine="thai")), (20, "w9", *fields(cuisine="thai"))])
        self.assertEqual(ex.stats["ids_from_multi_values"], 2)
        self.assertEqual(ex.stats["multi_value_objects"], 1)

    def assertWinner(self, objects, winner):
        """The same winner whatever order the objects arrive in."""
        for order in itertools.permutations(objects):
            ex = ox.Extractor()
            for osm_type, osm_id, tags in order:
                ex.add(osm_type, osm_id, {"fhrs:id": "1", **tags})
            rows = ex.rows()
            self.assertEqual(len(rows), 1)
            self.assertEqual(rows[0][1], winner, order)
            self.assertEqual(ex.stats["duplicates_dropped"], len(objects) - 1)

    def test_dedupe_most_fields_wins(self):
        self.assertWinner([("n", 1, {"cuisine": "a"}), ("r", 9, {"cuisine": "b", "phone": "1"}),
                           ("w", 2, {"website": "w"})], "r9")

    def test_dedupe_tie_node_before_way_before_relation(self):
        self.assertWinner([("r", 1, {"cuisine": "a"}), ("w", 2, {"phone": "1"}), ("n", 3, {"email": "e"})], "n3")
        self.assertWinner([("r", 1, {"cuisine": "a"}), ("w", 2, {"phone": "1"})], "w2")

    def test_dedupe_tie_lowest_id(self):
        self.assertWinner([("n", 30, {"cuisine": "a"}), ("n", 4, {"cuisine": "b"}), ("n", 100, {"cuisine": "c"})],
                          "n4")

    def test_dedupe_is_per_id(self):
        ex = ox.Extractor()
        ex.add("n", 1, {"fhrs:id": "1;2", "cuisine": "a"})
        ex.add("r", 5, {"fhrs:id": "2", "cuisine": "b", "phone": "1"})
        self.assertEqual([r[:2] for r in ex.rows()], [(1, "n1"), (2, "r5")])
        self.assertEqual(ex.stats["duplicates_dropped"], 1)

    def test_rows_sorted_numerically(self):
        ex = ox.Extractor()
        for i, fhrs_id in enumerate(["100", "9", "25"], start=1):
            ex.add("n", i, {"fhrs:id": fhrs_id, "cuisine": "x"})
        self.assertEqual([r[0] for r in ex.rows()], [9, 25, 100])


class OutputTest(unittest.TestCase):
    def test_write_tsv(self):
        out = io.StringIO()
        ox.write_tsv(out, [(3, "n7", *fields(cuisine="thai", diets="vegan"))], "2026-10-11T01:34:13Z")
        self.assertEqual(out.getvalue(),
                         "# higiene-osm 1 2026-10-11T01:34:13Z\n" + line(*ox.COLUMNS)
                         + line("3", "n7", "", "thai", "", "", "", "", "", "", "", "", "", "vegan"))

    def test_normalise_timestamp(self):
        self.assertEqual(ox.normalise_timestamp("2026-10-11T01:34:13Z"), "2026-10-11T01:34:13Z")
        self.assertEqual(ox.normalise_timestamp("2026-10-11T02:34:13+01:00"), "2026-10-11T01:34:13Z")
        self.assertEqual(ox.normalise_timestamp("2026-10-11T01:34:13"), "2026-10-11T01:34:13Z")
        self.assertEqual(ox.normalise_timestamp(""), "unknown")
        self.assertEqual(ox.normalise_timestamp(None), "unknown")
        self.assertEqual(ox.normalise_timestamp("yesterday"), "unknown")


class EndToEndTest(unittest.TestCase):
    def test_sample_opl(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = os.path.join(tmp, "out.tsv")
            result = subprocess.run([sys.executable, "-I", SCRIPT, SAMPLE, out],
                                    capture_output=True, text=True, timeout=60)
            self.assertEqual(result.returncode, 0, result.stderr)
            with open(out, encoding="utf-8", newline="") as f:
                text = f.read()
            self.assertFalse(os.path.exists(out + ".tmp"))

        e = ""
        expected = (
            "# higiene-osm 1 unknown\n"  # OPL has no header, so no data timestamp
            + line(*ox.COLUMNS)
            # w10: " 7 , x, 0" -> 7
            + line("7", "w10", e, "pizza;kebab", e, e, e, e, e, e, "yes", "no", "yes", e)
            # n1 beats w9 (more fields); spaces in phone collapsed; diet:halal=no ignored
            + line("100", "n1", "Mo-Su 12:00-22:00", "burger", "https://grill.example", "+44 1234 567890",
                   e, e, e, e, e, e, e, "vegetarian,vegan")
            # n2 "200;0201": blank website falls back to contact:website, contact:phone used
            + line("200", "n2", e, e, "https://cafe.example", "01234 567890", e, e, e, "limited", e, e, e, e)
            # r12 has more fields than n2 for 201
            + line("201", "r12", e, "coffee_shop", "https://bigger.example", "111", "x@y.example",
                   e, e, e, e, e, e, e)
            # n5 and r11 tie on 2 fields; the node wins
            + line("400", "n5", e, e, e, e, "hello@four.example", e, "Q123", e, e, e, e, e)
            # tab and newline in opening_hours become spaces
            + line("500", "n6", "Mo-Fr 09:00-17:00 Sa 10:00-14:00", e, e, e, e, e, e, e, e, e, e,
                   "kosher,gluten_free")
            # n7 and n8 tie; lowest id wins
            + line("600", "n7", e, "indian", e, e, e, e, e, e, e, e, e, e)
        )
        self.assertEqual(text, expected)
        self.assertNotIn("secret_user", text)

        for stat in ["objects scanned: 12 (nodes 8, ways 2, relations 2)", "no valid id: 1", "no useful tags: 1",
                     "ids split from multi-values: 2 (from 1 objects)", "duplicates dropped: 4", "rows written: 7",
                     "  cuisine: 4", "  diets: 2"]:
            self.assertIn(stat + "\n", result.stderr)

    def test_usage(self):
        result = subprocess.run([sys.executable, "-I", SCRIPT], capture_output=True, text=True, timeout=60)
        self.assertEqual(result.returncode, 2)
        self.assertIn("Usage:", result.stderr)


if __name__ == "__main__":
    unittest.main()
