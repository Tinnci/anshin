"""Encode the offline catalog and expose its records to maintenance tools."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets/json"
CATALOG_FILE = "drug_catalog.json"
CATEGORIES_FILE = "drug_categories.json"
NAMES_FILE = "drug_names.json"
CATALOGS = ("western", "tcm")
SCHEMA_VERSION = 2
PATH_SEPARATOR = " > "


def read_asset(filename: str) -> dict:
    return json.loads((ASSETS / filename).read_text(encoding="utf-8"))


def write_rows(filename: str, groups: dict, taxonomy_id: str | None = None) -> None:
    """Use one line per generated record so individual edits stay visible in diffs."""
    def encode(value):
        return json.dumps(value, ensure_ascii=False, separators=(",", ":"))

    sections = []
    for name, rows in groups.items():
        if isinstance(rows, dict):
            entries = ",\n".join(f"    {encode(key)}: {encode(value)}" for key, value in rows.items())
            sections.append(f'  "{name}": {{\n{entries}\n  }}')
        else:
            entries = ",\n".join(f"    {encode(value)}" for value in rows)
            sections.append(f'  "{name}": [\n{entries}\n  ]')
    header = [f'  "schemaVersion": {SCHEMA_VERSION}']
    if taxonomy_id is not None:
        header.append(f'  "taxonomyId": {encode(taxonomy_id)}')
    (ASSETS / filename).write_text("{\n" + ",\n".join([*header, *sections]) + "\n}\n", encoding="utf-8")


def write_catalog(catalogs, categories, initials, aliases, localized_names, starters):
    trees = {}
    records = {}
    for catalog in CATALOGS:
        nodes = []
        ids = {}
        for key, labels in sorted(categories.items()):
            namespace, _, path = key.partition(":")
            if namespace != catalog:
                continue
            parent_path, separator, _ = path.rpartition(PATH_SEPARATOR)
            node = {"name": labels["zh"]}
            if separator:
                node["parent"] = ids[parent_path]
            localized = {locale: text for locale, text in labels.items() if locale != "zh"}
            if localized:
                node["labels"] = localized
            ids[path] = len(nodes)
            nodes.append(node)
        trees[catalog] = nodes

        drugs = {}
        for name, paths in sorted(catalogs[catalog].items()):
            category_ids = []
            for path in paths:
                category_path, _, leaf = path.rpartition(PATH_SEPARATOR)
                if leaf != name:
                    raise ValueError(f"Catalog path does not end in its drug name: {path!r}")
                category_ids.append(ids[category_path])
            drugs[name] = {"initial": initials[name], "categories": category_ids}
        records[catalog] = drugs

    names = {}
    for name in sorted(aliases.keys() | localized_names.keys() | starters.keys()):
        row = {}
        if name in aliases:
            row["aliases"] = aliases[name]["aliases"]
        localized = localized_names.get(name, {}) | starters.get(name, {})
        if localized:
            row["localized"] = localized
        if name in starters:
            row["starter"] = True
        names[name] = row

    # Numeric category IDs are valid only for the exact same ordered tree.
    structure = {
        catalog: [(node["name"], node.get("parent")) for node in nodes]
        for catalog, nodes in trees.items()
    }
    taxonomy_id = hashlib.sha256(json.dumps(structure, ensure_ascii=False).encode("utf-8")).hexdigest()
    write_rows(CATEGORIES_FILE, trees, taxonomy_id)
    write_rows(CATALOG_FILE, records, taxonomy_id)
    write_rows(NAMES_FILE, {"names": names})


def load_catalog() -> tuple[dict, dict]:
    records = read_asset(CATALOG_FILE)
    trees = read_asset(CATEGORIES_FILE)
    if records["schemaVersion"] != SCHEMA_VERSION or trees["schemaVersion"] != SCHEMA_VERSION:
        raise ValueError("Unsupported drug catalog schema. Run scripts/analyze_drugs.py to regenerate it.")
    if records["taxonomyId"] != trees["taxonomyId"]:
        raise ValueError("Catalog and category assets are from different snapshots.")
    catalogs = {}
    initials = {}
    for catalog in CATALOGS:
        paths = []
        for node in trees[catalog]:
            parent = node.get("parent")
            paths.append(node["name"] if parent is None else paths[parent] + PATH_SEPARATOR + node["name"])
        catalogs[catalog] = {
            name: [paths[index] + PATH_SEPARATOR + name for index in record["categories"]]
            for name, record in records[catalog].items()
        }
        initials.update({name: record["initial"] for name, record in records[catalog].items()})
    return catalogs, initials
