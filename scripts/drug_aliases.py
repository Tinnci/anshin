"""Build reviewed drug alias assets for runtime search."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

PROJECT_ROOT = Path(__file__).resolve().parents[1]
RAW_ALIAS_PATH = PROJECT_ROOT / "scripts" / "data" / "drug_aliases_reviewed.json"
DEFAULT_OUTPUT_PATH = PROJECT_ROOT / "app" / "src" / "main" / "assets" / "json" / "drug_aliases_clean.json"


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
        for key in ("aliases", "brandNames", "genericNames", "chemicalNames"):
            values.extend(entry.get(key, []))

        aliases: list[str] = []
        seen = {canonical_name.casefold()}
        for value in values:
            alias = str(value).strip()
            folded = alias.casefold()
            if not alias or folded in seen:
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
