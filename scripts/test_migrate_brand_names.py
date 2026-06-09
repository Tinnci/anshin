import unittest
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.append(str(SCRIPT_DIR))

from migrate_brand_names import migrate_aliases


class BrandNameMigrationTest(unittest.TestCase):
    def test_moves_obvious_brand_alias_to_brand_names(self):
        payload = {
            "氯吡格雷": {
                "aliases": ["clopidogrel", "海乐神"],
            }
        }
        migrated, summaries = migrate_aliases(payload)

        self.assertEqual(["clopidogrel"], migrated["氯吡格雷"]["aliases"])
        self.assertEqual(["海乐神"], migrated["氯吡格雷"]["brandNames"])
        self.assertEqual(["海乐神"], summaries[0].moved_from_aliases)

    def test_allows_manual_brand_supplement(self):
        payload = {
            "阿司匹林": {
                "aliases": ["aspirin", "拜耳"],
            }
        }
        migrated, summaries = migrate_aliases(payload, {"阿司匹林": ["拜耳"]})

        self.assertIn("拜耳", migrated["阿司匹林"]["brandNames"])
        self.assertIn("aspirin", migrated["阿司匹林"]["aliases"])
        self.assertNotIn("拜耳", migrated["阿司匹林"]["aliases"])
        self.assertEqual([], summaries[0].moved_from_aliases)

    def test_refuses_dose_form_alias_as_brand(self):
        payload = {
            "舒必利": {
                "aliases": ["舒必利片"],
            }
        }
        migrated, _ = migrate_aliases(payload)
        self.assertIn("舒必利片", migrated["舒必利"]["aliases"])
        self.assertEqual([], migrated["舒必利"].get("brandNames", []))

    def test_demotes_existing_dose_form_brand_name(self):
        payload = {
            "舒必利": {
                "aliases": ["sulpiride"],
                "brandNames": ["舒必利片", "止呕灵"],
            }
        }
        migrated, summaries = migrate_aliases(payload)

        self.assertIn("舒必利片", migrated["舒必利"]["aliases"])
        self.assertEqual(["止呕灵"], migrated["舒必利"]["brandNames"])
        self.assertEqual(["舒必利片"], summaries[0].demoted_to_aliases)


if __name__ == "__main__":
    unittest.main()
