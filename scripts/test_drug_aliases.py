#!/usr/bin/env python3
"""Tests for reviewed drug alias asset helpers."""

import unittest

from drug_aliases import find_alias_semantic_warnings


class DrugAliasSemanticWarningTest(unittest.TestCase):
    def test_flags_non_alias_like_review_entries(self):
        warnings = find_alias_semantic_warnings(
            {"阿司匹林"},
            {
                "阿司匹林": {
                    "aliases": [
                        "拜耳公司",
                        "阿司匹林片",
                        "阿司匹林/咖啡因",
                        "aspirin",
                    ],
                },
            },
        )

        rows = {(warning.canonical_name, warning.alias, warning.reason) for warning in warnings}
        self.assertIn(("阿司匹林", "拜耳公司", "organization_name"), rows)
        self.assertIn(("阿司匹林", "阿司匹林片", "dose_form_or_package"), rows)
        self.assertIn(("阿司匹林", "阿司匹林/咖啡因", "compound_or_ratio"), rows)
        self.assertNotIn(("阿司匹林", "aspirin", "organization_name"), rows)

    def test_allows_english_names_that_only_contain_company_abbreviations_inside_words(self):
        warnings = find_alias_semantic_warnings(
            {"吡硫翁锌", "厄贝沙坦/氢氯噻嗪"},
            {
                "吡硫翁锌": {"aliases": ["zinc pyrithione"]},
                "厄贝沙坦/氢氯噻嗪": {"aliases": ["irbesartan/hydrochlorothiazide"]},
            },
        )

        self.assertEqual([], warnings)


if __name__ == "__main__":
    unittest.main()
