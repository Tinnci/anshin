#!/usr/bin/env python3
# /// script
# requires-python = ">=3.11"
# dependencies = [
#   "chembl-webresource-client>=0.10.9",
#   "pubchempy>=1.0.4",
#   "pypinyin>=0.51.0",
#   "rapidfuzz>=3.13.0",
# ]
# ///
"""Audit MedLog's local drug reference database.

The default mode is offline and deterministic. Optional external enrichment uses
PubChemPy and ChEMBL for a small candidate sample, but never rewrites app data.
Run with:

    uv run scripts/drug_database_audit.py
    uv run scripts/drug_database_audit.py --external-limit 20
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import signal
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

from pypinyin import lazy_pinyin
from rapidfuzz import fuzz

from drug_category_rules import MISC_REHOME_RULES
from drug_aliases import find_alias_semantic_warnings, load_alias_source


PROJECT_ROOT = Path(__file__).resolve().parents[1]
ASSET_BASE = PROJECT_ROOT / "app" / "src" / "main" / "assets" / "json"
RAW_BASE = PROJECT_ROOT / "scripts" / "data"
DEFAULT_OUTPUT = PROJECT_ROOT / "build" / "reports" / "drug_database_audit"

GENERIC_SUFFIXES = (
    "西林", "头孢", "霉素", "沙星", "硝唑", "康唑", "洛尔", "地平", "普利", "沙坦",
    "他汀", "拉唑", "替丁", "双胍", "格列", "磺脲", "泊苷", "铂", "单抗", "替尼",
    "司琼", "曲普坦", "膦酸", "肝素", "华法林", "胰岛素", "盐酸", "硫酸", "磷酸",
    "枸橼酸", "酒石酸", "马来酸", "富马酸", "苯磺酸", "甲磺酸",
)

BRANDISH_TOKENS = (
    "必", "达", "宁", "灵", "康", "欣", "舒", "乐", "泰", "安", "适", "优",
    "力", "可", "敏", "芬", "通", "清", "悦", "迪", "邦", "克",
)

EXTERNAL_NAME_SEEDS: dict[str, str] = {
    "阿司匹林": "aspirin",
    "布洛芬": "ibuprofen",
    "华法林": "warfarin",
    "二甲双胍": "metformin",
    "阿莫西林": "amoxicillin",
    "氨氯地平": "amlodipine",
    "辛伐他汀": "simvastatin",
    "阿托伐他汀": "atorvastatin",
    "奥美拉唑": "omeprazole",
    "氯吡格雷": "clopidogrel",
    "西地那非": "sildenafil",
    "硝酸甘油": "nitroglycerin",
    "利奈唑胺": "linezolid",
    "别嘌醇": "allopurinol",
    "硫唑嘌呤": "azathioprine",
    "茶碱": "theophylline",
}


class ExternalLookupTimeout(RuntimeError):
    pass


@dataclass(frozen=True)
class DrugRecord:
    name: str
    source: str
    paths: tuple[str, ...]

    @property
    def primary_path(self) -> str:
        return self.paths[0] if self.paths else ""

    @property
    def top_category(self) -> str:
        return path_part(self.primary_path, 0)

    @property
    def second_category(self) -> str:
        return path_part(self.primary_path, 1)


def load_json_dict(path: Path) -> dict[str, list[str]]:
    with path.open(encoding="utf-8") as handle:
        raw = json.load(handle)
    normalized: dict[str, list[str]] = {}
    for name, value in raw.items():
        if isinstance(value, str):
            paths = [value]
        else:
            paths = list(value)
        normalized[name.strip()] = [p.strip() for p in paths if p and p.strip()]
    return normalized


def load_records() -> list[DrugRecord]:
    western = load_json_dict(ASSET_BASE / "drugs_clean.json")
    tcm = load_json_dict(ASSET_BASE / "tcm_drugs_clean.json")
    return [
        *(DrugRecord(name, "western", tuple(paths)) for name, paths in western.items()),
        *(DrugRecord(name, "tcm", tuple(paths)) for name, paths in tcm.items()),
    ]


def load_initials() -> dict[str, str]:
    path = ASSET_BASE / "drug_initials_clean.json"
    if not path.exists():
        return {}
    with path.open(encoding="utf-8") as handle:
        return {str(name): str(initial) for name, initial in json.load(handle).items()}


def path_part(path: str, index: int) -> str:
    parts = [p.strip() for p in path.split(" > ") if p.strip()]
    return parts[index] if len(parts) > index else ""


def classify_name(name: str) -> str:
    if len(name) <= 2:
        return "very_short"
    if name.startswith("复方") or "/" in name or "复合" in name:
        return "compound"
    if any(token in name for token in ("疫苗", "免疫球蛋白", "抗体", "血清")):
        return "biologic_or_vaccine"
    if any(token in name for token in GENERIC_SUFFIXES):
        return "generic_like"
    if any(token in name for token in BRANDISH_TOKENS) and len(name) <= 5:
        return "brand_like_candidate"
    return "uncertain"


def pinyin_initial(name: str) -> str:
    if not name:
        return "#"
    first = lazy_pinyin(name[0])
    if not first:
        return "#"
    initial = first[0][:1].upper()
    return initial if "A" <= initial <= "Z" else "#"


def gb2312_bucket_initial(name: str) -> str:
    if not name:
        return "#"
    c = name[0]
    if "a" <= c <= "z":
        return c.upper()
    if "A" <= c <= "Z":
        return c
    code = ord(c)
    if code < 0x4E00 or code > 0x9FA5:
        return "#"
    thresholds = [
        (0x554A, "A"), (0x5C1B, "B"), (0x6015, "C"), (0x61A7, "D"),
        (0x63D3, "E"), (0x6617, "F"), (0x6747, "G"), (0x6B2D, "H"),
        (0x6D84, "J"), (0x7057, "K"), (0x725C, "L"), (0x7528, "M"),
        (0x7838, "N"), (0x7E31, "O"), (0x81D9, "P"), (0x8426, "Q"),
        (0x8704, "R"), (0x8C28, "S"), (0x8EA0, "T"), (0x9128, "W"),
        (0x9294, "X"), (0x96AF, "Y"), (0x9B31, "Z"),
    ]
    for threshold, initial in thresholds:
        if code < threshold:
            return initial
    return "#"


def find_cross_database_duplicates(records: Iterable[DrugRecord]) -> list[tuple[str, str, str]]:
    by_name: dict[str, list[DrugRecord]] = defaultdict(list)
    for record in records:
        by_name[record.name].append(record)
    rows = []
    for name, group in sorted(by_name.items()):
        sources = {record.source for record in group}
        if len(sources) > 1:
            rows.append((name, " | ".join(sorted(sources)), " || ".join(r.primary_path for r in group)))
    return rows


def find_fuzzy_duplicates(records: list[DrugRecord], limit: int = 80) -> list[tuple[str, str, int]]:
    names = sorted({record.name for record in records if len(record.name) >= 4})
    rows: list[tuple[str, str, int]] = []
    for index, name in enumerate(names):
        for other in names[index + 1:index + 80]:
            score = fuzz.ratio(name, other)
            if score >= 92 and name != other:
                rows.append((name, other, score))
    return sorted(rows, key=lambda row: row[2], reverse=True)[:limit]


def audit(records: list[DrugRecord], output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    name_classes = Counter(classify_name(record.name) for record in records)
    top_categories = Counter(record.top_category for record in records)
    path_depths = Counter(len(record.primary_path.split(" > ")) for record in records if record.primary_path)
    multi_path = [record for record in records if len(record.paths) > 1]
    v_candidates = [
        (
            record.name,
            record.second_category,
            MISC_REHOME_RULES[record.second_category],
            record.primary_path,
        )
        for record in records
        if record.top_category == "杂类" and record.second_category in MISC_REHOME_RULES
    ]
    initials = load_initials()
    pinyin_mismatches = [
        (record.name, initials.get(record.name, ""), pinyin_initial(record.name))
        for record in records
        if initials.get(record.name) != pinyin_initial(record.name)
    ]
    western_names = {record.name for record in records if record.source == "western"}
    alias_warnings = find_alias_semantic_warnings(western_names, load_alias_source())

    write_csv(
        output_dir / "v_rehome_candidates.csv",
        ("name", "current_second_category", "suggested_top_category", "primary_path"),
        v_candidates,
    )
    write_csv(
        output_dir / "cross_database_duplicates.csv",
        ("name", "sources", "paths"),
        find_cross_database_duplicates(records),
    )
    write_csv(
        output_dir / "pinyin_initial_mismatches.csv",
        ("name", "asset_initial", "pypinyin_initial"),
        pinyin_mismatches,
    )
    write_csv(
        output_dir / "fuzzy_duplicate_candidates.csv",
        ("name_a", "name_b", "similarity"),
        find_fuzzy_duplicates(records),
    )
    write_csv(
        output_dir / "alias_semantic_warnings.csv",
        ("canonical_name", "alias", "reason", "field_name"),
        (
            (warning.canonical_name, warning.alias, warning.reason, warning.field_name)
            for warning in alias_warnings
        ),
    )

    summary = [
        "# MedLog Drug Database Audit",
        "",
        f"- total records: {len(records)}",
        f"- western records: {sum(1 for r in records if r.source == 'western')}",
        f"- tcm records: {sum(1 for r in records if r.source == 'tcm')}",
        f"- multi-path western/tcm records: {len(multi_path)}",
        f"- V/misc rehome candidates: {len(v_candidates)}",
        f"- cross database duplicate names: {len(find_cross_database_duplicates(records))}",
        f"- pinyin initial asset mismatches: {len(pinyin_mismatches)}",
        f"- alias semantic warning candidates: {len(alias_warnings)}",
        "",
        "## Name classification heuristic",
        *[f"- {key}: {value}" for key, value in name_classes.most_common()],
        "",
        "## Top categories",
        *[f"- {key or '(empty)'}: {value}" for key, value in top_categories.most_common(20)],
        "",
        "## Primary path depth",
        *[f"- {key}: {value}" for key, value in sorted(path_depths.items())],
        "",
        "Generated CSV files are candidate lists only. Review before changing runtime data.",
    ]
    (output_dir / "summary.md").write_text("\n".join(summary) + "\n", encoding="utf-8")


def write_csv(path: Path, header: tuple[str, ...], rows: Iterable[tuple[object, ...]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(header)
        writer.writerows(rows)


def run_with_timeout(seconds: int, callback):
    def on_timeout(_signum, _frame):
        raise ExternalLookupTimeout(f"external lookup exceeded {seconds}s")

    previous_handler = signal.signal(signal.SIGALRM, on_timeout)
    previous_timer = signal.setitimer(signal.ITIMER_REAL, seconds)
    try:
        return callback()
    finally:
        signal.setitimer(signal.ITIMER_REAL, previous_timer[0], previous_timer[1])
        signal.signal(signal.SIGALRM, previous_handler)


def external_enrichment(output_dir: Path, limit: int, timeout_seconds: int) -> None:
    if limit <= 0:
        return
    try:
        import pubchempy as pcp
        pubchem_error = ""
    except Exception as error:
        pcp = None
        pubchem_error = f"pubchem_init:{error.__class__.__name__}"

    def load_chembl_molecule():
        from chembl_webresource_client.new_client import new_client

        return new_client.molecule

    try:
        molecule = run_with_timeout(timeout_seconds, load_chembl_molecule)
        chembl_error = ""
    except Exception as error:
        molecule = None
        chembl_error = f"chembl_init:{error.__class__.__name__}"

    rows = []
    for chinese_name, english_query in list(EXTERNAL_NAME_SEEDS.items())[:limit]:
        pubchem_cids: list[str] = []
        pubchem_synonyms: list[str] = []
        chembl_ids: list[str] = []
        pref_names: list[str] = []
        errors: list[str] = []
        if pcp is None:
            errors.append(pubchem_error)
        else:
            try:
                compounds = run_with_timeout(
                    timeout_seconds,
                    lambda: pcp.get_compounds(english_query, "name")[:3],
                )
                for compound in compounds:
                    pubchem_cids.append(str(compound.cid))
                    pubchem_synonyms.extend((compound.synonyms or [])[:8])
            except Exception as error:
                errors.append(f"pubchem:{error.__class__.__name__}")
        if molecule is None:
            errors.append(chembl_error)
        else:
            try:
                chembl_hits = run_with_timeout(
                    timeout_seconds,
                    lambda: molecule.search(english_query)[:3],
                )
                chembl_ids = [hit.get("molecule_chembl_id", "") for hit in chembl_hits]
                pref_names = [hit.get("pref_name", "") for hit in chembl_hits]
            except Exception as error:
                errors.append(f"chembl:{error.__class__.__name__}")
        rows.append(
            (
                chinese_name,
                english_query,
                " | ".join(pubchem_cids),
                " | ".join(dict.fromkeys(pubchem_synonyms[:12])),
                " | ".join(chembl_ids),
                " | ".join(pref_names),
                " | ".join(errors),
            )
        )
    write_csv(
        output_dir / "external_enrichment_sample.csv",
        (
            "local_name",
            "query",
            "pubchem_cids",
            "pubchem_synonyms",
            "chembl_ids",
            "chembl_pref_names",
            "errors",
        ),
        rows,
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument(
        "--external-limit",
        type=int,
        default=0,
        help="Query PubChemPy and ChEMBL for the first N built-in seed names.",
    )
    parser.add_argument(
        "--external-timeout-seconds",
        type=int,
        default=8,
        help="Per-provider timeout for external enrichment calls.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    records = load_records()
    audit(records, args.output_dir)
    external_enrichment(args.output_dir, args.external_limit, args.external_timeout_seconds)
    print(f"Wrote audit report to {args.output_dir}")


if __name__ == "__main__":
    main()
