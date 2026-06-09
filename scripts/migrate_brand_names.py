#!/usr/bin/env python3
# /// script
# requires-python = ">=3.11"
# ///
"""Promote manufacturer-style brand aliases into `brandNames` in reviewed drug aliases."""

from __future__ import annotations

import argparse
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from drug_aliases import alias_semantic_warning_reason

PROJECT_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE_PATH = PROJECT_ROOT / "scripts" / "data" / "drug_aliases_reviewed.json"

CHINESE_BRAND = re.compile(r"^[\u4e00-\u9fff0-9]{2,8}$")

SALT_PREFIXES = (
    "盐酸",
    "醋酸",
    "硫酸",
    "柠檬酸",
    "枸橼酸",
    "磷酸",
    "氢氟噻",
)

GENERIC_END_TOKENS = (
    "酸",
    "酯",
    "胺",
    "烷",
    "酮",
    "醇",
    "盐",
    "钠",
    "钾",
    "镁",
    "钙",
    "醚",
    "苷",
    "肽",
    "素",
    "磺",
    "酚",
    "核",
    "菌",
)

DOSE_FORM_TOKENS = (
    "片",
    "片剂",
    "丸",
    "口服液",
    "滴眼液",
    "胶囊",
    "注射液",
    "注射剂",
    "软膏",
    "乳膏",
    "吸入",
    "喷雾",
    "粉雾剂",
    "贴",
    "贴片",
    "散",
    "颗粒",
    "糖浆",
    "栓",
)

BRAND_HINT_CHARS = (
    "诺",
    "爱",
    "乐",
    "安",
    "宁",
    "舒",
    "德",
    "达",
    "康",
    "力",
    "可",
    "优",
    "佳",
    "瑞",
    "敏",
    "泰",
    "新",
    "益",
    "善",
    "美",
    "和",
)


def has_dose_form_hint(alias: str) -> bool:
    return any(token in alias for token in DOSE_FORM_TOKENS)


def _fold(value: str) -> str:
    return value.strip().casefold()


def _dedupe(values: list[str]) -> list[str]:
    seen: set[str] = set()
    out: list[str] = []
    for value in values:
        if not isinstance(value, str):
            continue
        token = value.strip()
        if not token:
            continue
        key = _fold(token)
        if key in seen:
            continue
        seen.add(key)
        out.append(token)
    return out


def has_brand_hint_chars(alias: str) -> bool:
    return any(ch in alias for ch in BRAND_HINT_CHARS)


def is_brand_name_candidate(canonical: str, alias: str, known_drug_names: set[str]) -> bool:
    if not alias or alias == canonical:
        return False
    if alias in known_drug_names:
        return False
    if any(alias.startswith(prefix) for prefix in SALT_PREFIXES):
        return False

    reason = alias_semantic_warning_reason(canonical, alias, known_drug_names)
    if reason in {"organization_name", "compound_or_ratio"}:
        return True

    if has_dose_form_hint(alias):
        return False
    if not CHINESE_BRAND.fullmatch(alias):
        return False
    if len(alias) > 6:
        return False
    if any(alias.endswith(token) for token in GENERIC_END_TOKENS):
        return False
    return has_brand_hint_chars(alias)


@dataclass(frozen=True)
class BrandMoveSummary:
    canonical: str
    moved_from_aliases: list[str]
    added_brand_names: list[str]
    demoted_to_aliases: list[str]


def migrate_aliases(
    payload: dict[str, dict[str, Any]],
    extra_brand_names: dict[str, list[str]] | None = None,
) -> tuple[dict[str, dict[str, Any]], list[BrandMoveSummary]]:
    if extra_brand_names is None:
        extra_brand_names = {}

    known_drug_names = {name.strip() for name in payload}
    updated: dict[str, dict[str, Any]] = {}
    summaries: list[BrandMoveSummary] = []

    for canonical in payload:
        entry = payload.get(canonical, {})
        aliases = _dedupe(list(entry.get("aliases", [])))
        raw_brands = _dedupe(list(entry.get("brandNames", [])))
        demoted = [name for name in raw_brands if has_dose_form_hint(name)]
        brands = [name for name in raw_brands if name not in demoted]
        aliases = _dedupe(aliases + demoted)
        brands_set = {_fold(name) for name in brands}

        moved: list[str] = []
        for alias in aliases:
            folded = _fold(alias)
            if folded in brands_set:
                continue
            if is_brand_name_candidate(canonical, alias, known_drug_names):
                brands_set.add(folded)
                brands.append(alias)
                moved.append(alias)

        added: list[str] = []
        for brand in extra_brand_names.get(canonical, []):
            if not isinstance(brand, str):
                continue
            brand = brand.strip()
            if not brand:
                continue
            folded = _fold(brand)
            if folded in {_fold(canonical)}:
                continue
            if folded not in brands_set:
                brands_set.add(folded)
                brands.append(brand)
                added.append(brand)

        brand_keys = {_fold(name) for name in brands}
        aliases = _dedupe([name for name in aliases if _fold(name) not in brand_keys])
        brands = _dedupe(brands)

        if moved or added or demoted:
            updated[canonical] = {
                **{k: v for k, v in entry.items() if k not in {"aliases", "brandNames"}},
                "aliases": aliases,
                "brandNames": brands,
            }
            summaries.append(
                BrandMoveSummary(
                    canonical=canonical,
                    moved_from_aliases=moved,
                    added_brand_names=added,
                    demoted_to_aliases=demoted,
                )
            )
        else:
            updated[canonical] = {**entry}

    return updated, summaries


def load_extra_brands(path: Path | None) -> dict[str, list[str]]:
    if path is None:
        return {}
    raw = json.loads(path.read_text(encoding="utf-8"))
    output: dict[str, list[str]] = {}
    for canonical, brands in raw.items():
        if isinstance(brands, str):
            output[str(canonical).strip()] = [str(brands)]
        else:
            output[str(canonical).strip()] = [str(brand) for brand in (brands or []) if isinstance(brand, str)]
    return output


def write_payload(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE_PATH)
    parser.add_argument("--output", type=Path, default=DEFAULT_SOURCE_PATH)
    parser.add_argument("--extra", type=Path, default=None, help="Optional JSON: {canonical: [brandName, ...]}")
    parser.add_argument("--apply", action="store_true", help="Write output file")
    parser.add_argument("--dry-run", action="store_true", help="Print preview without writing. (default)")
    args = parser.parse_args()

    if args.dry_run:
        args.apply = False

    payload = json.loads(args.source.read_text(encoding="utf-8"))
    extras = load_extra_brands(args.extra)

    migrated, summaries = migrate_aliases(payload, extras)

    total_moved = sum(len(item.moved_from_aliases) for item in summaries)
    total_added = sum(len(item.added_brand_names) for item in summaries)
    total_demoted = sum(len(item.demoted_to_aliases) for item in summaries)
    touched = len(summaries)

    if args.apply:
        write_payload(args.output, migrated)
    else:
        for item in summaries[:40]:
            details = item.moved_from_aliases + item.added_brand_names + item.demoted_to_aliases
            if details:
                print(f"{item.canonical}: {', '.join(details)}")

    print(
        f"Found {touched} entries with brand-name updates ({total_moved} aliases moved,"
        f" {total_added} manually added, {total_demoted} demoted). {'Applied' if args.apply else 'Dry run'}"
    )


if __name__ == "__main__":
    main()
