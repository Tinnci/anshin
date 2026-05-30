#!/usr/bin/env python3
"""Tests for drug data normalization helpers."""

import unittest

from drug_data_cleaning import (
    build_drug_initials,
    merge_format_duplicate_records,
    should_keep_source_record,
)


class DrugDataCleaningTest(unittest.TestCase):
    def test_resolves_known_cross_database_duplicate_ownership(self):
        self.assertFalse(should_keep_source_record("复方樟脑乳膏", "western"))
        self.assertTrue(should_keep_source_record("复方樟脑乳膏", "tcm"))
        self.assertTrue(should_keep_source_record("复方甘草片", "western"))
        self.assertFalse(should_keep_source_record("复方甘草片", "tcm"))

    def test_merges_names_that_only_differ_by_formatting(self):
        merged = merge_format_duplicate_records(
            {
                "碘[125I] 糖类抗原19-9免疫放射分析药盒": [
                    "杂类 > 诊断用放射性药物 > 碘[125I] 糖类抗原19-9免疫放射分析药盒",
                ],
                "碘[125I]糖类抗原19-9免疫放射分析药盒": [
                    "杂类 > 诊断用放射性药物 > 碘[125I]糖类抗原19-9免疫放射分析药盒",
                ],
            },
        )

        self.assertEqual(["碘[125I]糖类抗原19-9免疫放射分析药盒"], list(merged))
        self.assertEqual(
            [
                "杂类 > 诊断用放射性药物 > 碘[125I]糖类抗原19-9免疫放射分析药盒",
            ],
            merged["碘[125I]糖类抗原19-9免疫放射分析药盒"],
        )

    def test_keeps_reviewed_alias_canonical_name_when_merging_format_variants(self):
        merged = merge_format_duplicate_records(
            {
                "重组人凝血因子VIIa": ["血液和造血器官 > 重组人凝血因子VIIa"],
                "重组人凝血因子Ⅶa": ["血液和造血器官 > 重组人凝血因子Ⅶa"],
            },
            protected_names={"重组人凝血因子VIIa"},
        )

        self.assertEqual(["重组人凝血因子VIIa"], list(merged))

    def test_builds_pinyin_initials_at_generation_time(self):
        initials = build_drug_initials({"阿司匹林": [], "重组人促卵泡激素": []})

        self.assertEqual("A", initials["阿司匹林"])
        self.assertEqual("Z", initials["重组人促卵泡激素"])


if __name__ == "__main__":
    unittest.main()
