"""Build reviewed drug alias assets for runtime search."""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

PROJECT_ROOT = Path(__file__).resolve().parents[1]
RAW_ALIAS_PATH = PROJECT_ROOT / "scripts" / "data" / "drug_aliases_reviewed.json"
DEFAULT_OUTPUT_PATH = PROJECT_ROOT / "app" / "src" / "main" / "assets" / "json" / "drug_aliases_clean.json"

ALIAS_FIELD_NAMES = ("aliases", "brandNames", "genericNames", "chemicalNames")
ORGANIZATION_TOKENS = (
    "公司",
    "制药",
    "药业",
    "医药",
    "药厂",
    "pharma",
    "pharmaceutical",
    "laboratories",
    "laboratory",
    "labs",
    "inc",
    "ltd",
    "llc",
    "gmbh",
    "corp",
)
ENGLISH_ORGANIZATION_TOKENS = tuple(
    token for token in ORGANIZATION_TOKENS if token.isascii()
)
CHINESE_ORGANIZATION_TOKENS = tuple(
    token for token in ORGANIZATION_TOKENS if not token.isascii()
)
DOSE_FORM_TOKENS = (
    "胶囊",
    "注射液",
    "注射剂",
    "颗粒",
    "口服液",
    "滴眼液",
    "乳膏",
    "软膏",
    "喷雾",
    "吸入",
    "缓释",
    "控释",
)


@dataclass(frozen=True)
class AliasSemanticWarning:
    canonical_name: str
    alias: str
    reason: str
    field_name: str


def load_alias_source(path: Path = RAW_ALIAS_PATH) -> dict[str, dict[str, Any]]:
    with path.open(encoding="utf-8") as handle:
        raw = json.load(handle)
    return raw


def build_clean_aliases(
    known_drug_names: set[str],
    alias_source: dict[str, dict[str, Any]],
) -> dict[str, dict[str, Any]]:
    clean: dict[str, dict[str, Any]] = {}
    alias_owner: dict[str, str] = {}
    conflicts: list[tuple[str, str, str]] = []

    for canonical_name, entry in sorted(alias_source.items()):
        if canonical_name not in known_drug_names:
            raise ValueError(f"Unknown canonical drug in alias source: {canonical_name}")

        values: list[str] = []
        for key in ALIAS_FIELD_NAMES:
            values.extend(entry.get(key, []))

        aliases: list[str] = []
        seen = {canonical_name.casefold()}
        for value in values:
            alias = str(value).strip()
            folded = alias.casefold()
            if not alias or folded in seen:
                continue
            if alias in known_drug_names and alias != canonical_name:
                conflicts.append((alias, alias, canonical_name))
                continue
            seen.add(folded)
            previous = alias_owner.get(folded)
            if previous is not None and previous != canonical_name:
                conflicts.append((alias, previous, canonical_name))
                continue
            alias_owner[folded] = canonical_name
            aliases.append(alias)

        if aliases:
            clean[canonical_name] = {"aliases": aliases}

    if conflicts:
        details = "; ".join(f"{alias}: {left} / {right}" for alias, left, right in conflicts[:10])
        raise ValueError(f"Alias conflicts found: {details}")

    return clean


def find_alias_semantic_warnings(
    known_drug_names: set[str],
    alias_source: dict[str, dict[str, Any]],
) -> list[AliasSemanticWarning]:
    warnings: list[AliasSemanticWarning] = []
    for canonical_name, entry in sorted(alias_source.items()):
        for field_name in ALIAS_FIELD_NAMES:
            for value in entry.get(field_name, []):
                alias = str(value).strip()
                if not alias:
                    continue
                reason = alias_semantic_warning_reason(canonical_name, alias, known_drug_names)
                if reason is not None:
                    warnings.append(AliasSemanticWarning(canonical_name, alias, reason, field_name))
    return warnings


def alias_semantic_warning_reason(
    canonical_name: str,
    alias: str,
    known_drug_names: set[str],
) -> str | None:
    folded = alias.casefold()
    if any(token in alias for token in CHINESE_ORGANIZATION_TOKENS) or any(
        re.search(rf"(^|[^a-z]){re.escape(token)}([^a-z]|$)", folded)
        for token in ENGLISH_ORGANIZATION_TOKENS
    ):
        return "organization_name"
    if ("/" in alias or "＋" in alias or "+" in alias) and not (
        "/" in canonical_name or "＋" in canonical_name or "+" in canonical_name
    ):
        return "compound_or_ratio"
    if alias.startswith("复方") and not canonical_name.startswith("复方"):
        return "compound_or_ratio"
    if alias != canonical_name and looks_like_dose_form(alias):
        return "dose_form_or_package"
    if alias in known_drug_names and alias != canonical_name:
        return "other_canonical_drug"
    return None


def looks_like_dose_form(alias: str) -> bool:
    if any(token in alias for token in DOSE_FORM_TOKENS):
        return True
    return alias.endswith(("片", "栓", "贴"))


def write_clean_aliases(
    known_drug_names: set[str],
    output_path: Path = DEFAULT_OUTPUT_PATH,
    alias_source_path: Path = RAW_ALIAS_PATH,
) -> dict[str, dict[str, Any]]:
    clean = build_clean_aliases(known_drug_names, load_alias_source(alias_source_path))
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps(clean, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return clean


def main() -> None:
    drugs_path = PROJECT_ROOT / "app" / "src" / "main" / "assets" / "json" / "drugs_clean.json"
    drugs = json.loads(drugs_path.read_text(encoding="utf-8"))
    clean = write_clean_aliases(set(drugs))
    print(f"Wrote {len(clean)} reviewed drug alias entries to {DEFAULT_OUTPUT_PATH}")


if __name__ == "__main__":
    main()
