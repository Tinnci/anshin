"""Build offline catalog labels from reviewed translations and exact ingredient references.

Run `python3 scripts/drug_localization.py --help` for source refresh and review commands.
"""
from __future__ import annotations

import argparse
import csv
import json
import os
import subprocess
import unicodedata
from collections import defaultdict
from pathlib import Path
from urllib.request import Request, urlopen

from drug_aliases import PROJECT_ROOT, load_alias_source
from drug_catalog import load_catalog

DATA = PROJECT_ROOT / "scripts/data"
LOCALES = ("en", "ja", "ko")
RXNORM_URL = "https://rxnav.nlm.nih.gov/REST/allconcepts.json?tty=IN%20PIN%20MIN"


def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, value: dict) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def name_key(name: str) -> str:
    # Keep salt, formulation, strength and punctuation intact during matching.
    return " ".join(unicodedata.normalize("NFKC", name).casefold().split())


def category_nodes(catalogs: dict) -> dict:
    nodes = {}
    for catalog, drugs in catalogs.items():
        paths = {path for paths in drugs.values() for path in paths}
        for path in sorted(paths):
            parts = path.split(" > ")[:-1]
            for length in range(1, len(parts) + 1):
                nodes[f"{catalog}:{' > '.join(parts[:length])}"] = parts[length - 1]
    return nodes


def load_category_glossary() -> dict:
    with (DATA / "drug_category_labels.tsv").open(encoding="utf-8", newline="") as file:
        return {
            row["zh"]: {locale: row[locale] for locale in LOCALES if row[locale]}
            for row in csv.DictReader(file, delimiter="\t")
        }


def exact_rxnorm_matches(western: dict, reviewed_aliases: dict, concepts: list) -> tuple[dict, dict]:
    by_name = defaultdict(set)
    by_id = {}
    for concept in concepts:
        if concept["tty"] not in {"IN", "PIN", "MIN"}:
            continue
        by_name[name_key(concept["name"])].add(concept["rxcui"])
        by_id[concept["rxcui"]] = concept

    matches = {}
    ambiguous = {}
    for name in western.keys() & reviewed_aliases.keys():
        record = reviewed_aliases[name]
        aliases = record.get("aliases", []) + record.get("genericNames", [])
        candidates = set().union(*(by_name[name_key(alias)] for alias in aliases))
        if len(candidates) == 1:
            concept = by_id[next(iter(candidates))]
            # An exact ingredient alias does not prove the composition of a combination product.
            if concept["tty"] != "MIN" and (name.startswith("复方") or "/" in name):
                ambiguous[name] = sorted(candidates)
                continue
            matches[name] = {"rxcui": concept["rxcui"], "name": concept["name"]}
        elif candidates:
            ambiguous[name] = sorted(candidates)

    # A single ingredient name can be an alias for multiple catalog records. Preserve the
    # original rows and avoid auto-assigning a display name that makes them indistinguishable.
    owners = defaultdict(list)
    for name, match in matches.items():
        owners[match["rxcui"]].append(name)
    for rxcui, names in owners.items():
        if len(names) > 1:
            for name in names:
                ambiguous[name] = [rxcui]
                del matches[name]
    return matches, ambiguous


def refresh_rxnorm(western: dict) -> None:
    with urlopen(RXNORM_URL, timeout=60) as response:
        concepts = json.load(response)["minConceptGroup"]["minConcept"]
    matches, ambiguous = exact_rxnorm_matches(western, load_alias_source(), concepts)
    if not matches:
        raise ValueError("RxNorm refresh returned no matching ingredient names")
    write_json(DATA / "drug_rxnorm_matches.json", {
        "source": RXNORM_URL,
        "matches": matches,
        "ambiguous": ambiguous,
    })


def match_reference_exports(exports: list[Path], western: dict) -> dict:
    """Join official bilingual export rows to the verified English ingredient crosswalk."""
    rxnorm = read_json(DATA / "drug_rxnorm_matches.json")
    english_to_canonical = defaultdict(set)
    for name, match in rxnorm["matches"].items():
        if name in western:
            english_to_canonical[name_key(match["name"])].add(name)

    candidates = defaultdict(dict)
    unresolved = []
    for export in exports:
        with export.open(encoding="utf-8", newline="") as file:
            for row in csv.DictReader(file, delimiter="\t"):
                english, locale, name, source = (row[field].strip() for field in ("english", "language", "name", "source"))
                if locale not in {"ja", "ko"} or not all((english, name, source)):
                    raise ValueError(f"Invalid national reference row: {row}")
                matched = english_to_canonical[name_key(english)]
                if len(matched) != 1:
                    unresolved.append(row)
                    continue
                canonical = next(iter(matched))
                candidates[canonical].setdefault(locale, {}).setdefault(name, set()).add(source)

    matches = []
    ambiguous = []
    for canonical, by_locale in sorted(candidates.items()):
        for locale, names in sorted(by_locale.items()):
            if len(names) == 1:
                name, sources = next(iter(names.items()))
                matches.append({"canonical": canonical, "language": locale, "name": name, "source": " | ".join(sorted(sources))})
            else:
                ambiguous.append({"canonical": canonical, "language": locale, "names": sorted(names)})
    return {"matched": matches, "ambiguous": ambiguous, "unresolved": unresolved}


def reference_names(western: dict) -> dict:
    rxnorm = read_json(DATA / "drug_rxnorm_matches.json")
    names = {
        name: {"en": match["name"]}
        for name, match in rxnorm["matches"].items()
        if name in western
    }
    # Optional reviewed national source crosswalk. Each row ties an official name to an
    # existing catalog key and retains its source record for audit.
    with (DATA / "drug_reference_names.tsv").open(encoding="utf-8", newline="") as file:
        for row in csv.DictReader(file, delimiter="\t"):
            canonical, language, name = row["canonical"], row["language"], row["name"]
            if canonical not in western or language not in LOCALES or not name.strip() or not row["source"].strip():
                raise ValueError(f"Invalid reference name row: {row}")
            labels = names.setdefault(canonical, {})
            if language in labels and labels[language] != name:
                raise ValueError(f"Conflicting reference names for {canonical!r} ({language})")
            labels[language] = name
    return names


def build_localization(
    western: dict,
    tcm: dict,
    report_path: Path | None = None,
) -> tuple[dict, dict, dict, dict]:
    catalogs = {"western": western, "tcm": tcm}
    nodes = category_nodes(catalogs)
    glossary = load_category_glossary()
    categories = {key: {"zh": label, **glossary.get(label, {})} for key, label in nodes.items()}
    names = reference_names(western)
    starters = read_json(DATA / "drug_name_starters.json")
    merged = {name: {**names.get(name, {}), **starters.get(name, {})} for name in western}
    ambiguous = read_json(DATA / "drug_rxnorm_matches.json")["ambiguous"]
    missing = {
        locale: [name for name, localized in merged.items() if locale not in localized]
        for locale in LOCALES
    }
    untranslated = {
        locale: [key for key, localized in categories.items() if locale not in localized]
        for locale in LOCALES
    }
    summary = {
        "categoryNodes": len(categories),
        "topLevelCategories": sum(" > " not in key for key in categories),
        "translatedCategoryNodes": {
            "zh": len(categories), **{locale: len(categories) - len(keys) for locale, keys in untranslated.items()},
        },
        "conventionalNames": {
            "zh": len(western), **{locale: len(western) - len(keys) for locale, keys in missing.items()},
        },
        "ambiguousRxNormMatches": len(ambiguous),
        "tcmOriginalNames": len(tcm),
    }
    if report_path is not None:
        write_json(report_path, {
            "summary": summary,
            "ambiguousRxNormMatches": ambiguous,
            "missingConventionalNames": missing,
            "missingCategoryLabels": untranslated,
        })
    return categories, names, starters, summary


def category_drafts(nodes: dict, translate) -> dict:
    reviewed = load_category_glossary()
    result = defaultdict(dict)
    for language in LOCALES:
        missing = sorted({label for label in nodes.values() if language not in reviewed.get(label, {})})
        for start in range(0, len(missing), 50):
            batch = missing[start:start + 50]
            for source, translated in zip(batch, translate(batch, language), strict=True):
                if not translated.strip():
                    raise ValueError(f"Empty translation for {source!r} ({language})")
                result[source][language] = translated
    return dict(result)


def post_json(url: str, payload: dict, headers: dict | None = None) -> dict:
    request = Request(
        url, data=json.dumps(payload).encode(),
        headers={"Content-Type": "application/json", **(headers or {})},
    )
    with urlopen(request, timeout=90) as response:
        return json.load(response)


def google_drafts(project: str, location: str, glossary: str | None, nodes: dict) -> dict:
    token = os.environ.get("GOOGLE_OAUTH_ACCESS_TOKEN") or subprocess.check_output(
        ["gcloud", "auth", "application-default", "print-access-token"], text=True,
    ).strip()
    parent = f"projects/{project}/locations/{location}"
    def translate(batch: list[str], language: str) -> list[str]:
        payload = {
            "sourceLanguageCode": "zh-CN",
            "targetLanguageCode": language,
            "mimeType": "text/plain",
            "contents": batch,
        }
        if glossary:
            payload["glossaryConfig"] = {"glossary": f"{parent}/glossaries/{glossary}"}
        result = post_json(
            f"https://translation.googleapis.com/v3/{parent}:translateText",
            payload, {"Authorization": f"Bearer {token}"},
        )
        return [item["translatedText"] for item in result["glossaryTranslations" if glossary else "translations"]]

    return category_drafts(nodes, translate)


def libre_drafts(base_url: str, nodes: dict) -> dict:
    base_url = base_url.rstrip("/")
    with urlopen(f"{base_url}/languages", timeout=15) as response:
        languages = {entry["code"]: entry for entry in json.load(response)}
    source = next((code for code in ("zh-Hans", "zh-CN", "zh") if code in languages), None)
    if source is None or not set(LOCALES).issubset(languages[source]["targets"]):
        raise ValueError("The translation endpoint must support Simplified Chinese to English, Japanese and Korean")
    key = os.environ.get("LIBRETRANSLATE_API_KEY")

    def translate(batch: list[str], language: str) -> list[str]:
        payload = {"q": batch, "source": source, "target": language, "format": "text"}
        if key:
            payload["api_key"] = key
        return post_json(f"{base_url}/translate", payload)["translatedText"]

    return category_drafts(nodes, translate)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--refresh-rxnorm", action="store_true", help="Refresh exact matches from the public RxNorm API")
    parser.add_argument("--report", type=Path, help="Write full coverage and unmatched records to JSON")
    parser.add_argument("--reference-export", type=Path, action="append", help="Match a bilingual national reference TSV")
    parser.add_argument("--reference-candidates", type=Path, help="Write matched, ambiguous and unresolved reference rows")
    provider = parser.add_mutually_exclusive_group()
    provider.add_argument("--google-project", help="Google Cloud project for translation drafts")
    provider.add_argument("--libretranslate-url", help="Base URL of a LibreTranslate-compatible endpoint")
    parser.add_argument("--google-location", default="us-central1")
    parser.add_argument("--google-glossary", help="Name of an optional multilingual glossary in that location")
    parser.add_argument("--draft-output", type=Path, help="Write machine-translated category drafts separately for review")
    args = parser.parse_args()
    if bool(args.google_project or args.libretranslate_url) != bool(args.draft_output):
        parser.error("A translation provider and --draft-output must be used together")
    if bool(args.reference_export) != bool(args.reference_candidates):
        parser.error("--reference-export and --reference-candidates must be used together")
    catalogs, _ = load_catalog()
    western, tcm = catalogs["western"], catalogs["tcm"]
    if args.refresh_rxnorm:
        refresh_rxnorm(western)
    if args.reference_export:
        write_json(args.reference_candidates, match_reference_exports(args.reference_export, western))
    if args.draft_output:
        nodes = category_nodes({"western": western, "tcm": tcm})
        drafts = (
            google_drafts(args.google_project, args.google_location, args.google_glossary, nodes)
            if args.google_project else libre_drafts(args.libretranslate_url, nodes)
        )
        write_json(args.draft_output, drafts)
    _, _, _, summary = build_localization(western, tcm, args.report)
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
