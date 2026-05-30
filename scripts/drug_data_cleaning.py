"""Shared cleaning helpers for MedLog drug seed assets."""

from __future__ import annotations

import unicodedata
from collections import defaultdict
from typing import Iterable

from pypinyin import lazy_pinyin


PRIMARY_SOURCE_OVERRIDES = {
    "复方樟脑乳膏": "tcm",
    "复方甘草片": "western",
}


def should_keep_source_record(name: str, source: str) -> bool:
    primary_source = PRIMARY_SOURCE_OVERRIDES.get(name)
    return primary_source is None or primary_source == source


def format_duplicate_key(name: str) -> str:
    normalized = unicodedata.normalize("NFKC", name).casefold()
    normalized = normalized.replace("×", "x").replace("*", "x")
    return "".join(
        char
        for char in normalized
        if unicodedata.category(char)[0] not in {"P", "Z"}
    )


def merge_format_duplicate_records(
    data: dict[str, list[str]],
    protected_names: set[str] | None = None,
) -> dict[str, list[str]]:
    protected_names = protected_names or set()
    groups: dict[str, list[str]] = defaultdict(list)
    for name in data:
        groups[format_duplicate_key(name)].append(name)

    merged: dict[str, list[str]] = {}
    for names in groups.values():
        canonical_name = choose_canonical_display_name(names, protected_names)
        paths: list[str] = []
        seen_paths: set[str] = set()
        for name in names:
            for path in data[name]:
                normalized_path = replace_path_leaf(path, canonical_name)
                if normalized_path not in seen_paths:
                    seen_paths.add(normalized_path)
                    paths.append(normalized_path)
        merged[canonical_name] = paths
    return dict(sorted(merged.items()))


def choose_canonical_display_name(names: Iterable[str], protected_names: set[str] | None = None) -> str:
    protected_names = protected_names or set()
    return min(
        names,
        key=lambda name: (
            0 if name in protected_names else 1,
            sum(1 for char in name if unicodedata.category(char)[0] in {"P", "Z"}),
            len(name),
            name,
        ),
    )


def replace_path_leaf(path: str, leaf: str) -> str:
    parts = [part.strip() for part in path.split(" > ") if part.strip()]
    if not parts:
        return leaf
    parts[-1] = leaf
    return " > ".join(parts)


def build_drug_initials(*datasets: dict[str, list[str]]) -> dict[str, str]:
    names = sorted(name for dataset in datasets for name in dataset)
    return {name: pinyin_initial(name) for name in names}


def pinyin_initial(name: str) -> str:
    if not name:
        return "#"
    if "a" <= name[0] <= "z":
        return name[0].upper()
    if "A" <= name[0] <= "Z":
        return name[0]
    pinyin = lazy_pinyin(name[0])
    if not pinyin:
        return "#"
    initial = pinyin[0][:1].upper()
    return initial if "A" <= initial <= "Z" else "#"
