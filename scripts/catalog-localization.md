# Catalog localization

Anshin preloads the catalog assets produced by `uv run scripts/analyze_drugs.py`. The generator works offline from checked-in inputs and records locale coverage and asset hashes in `drug_dataset_manifest.json`.

## Bundled data

| Asset | Contents |
| --- | --- |
| `drug_catalog.json` | Conventional and TCM records keyed by their canonical names. Each stores a pinyin initial and an ordered list of category references. |
| `drug_categories.json` | Separate category trees for conventional and TCM records. Nodes store the original label, a parent reference, and approved translations. |
| `drug_names.json` | Sparse index of aliases, preferred localized names, and starter-list membership. The catalog and the interaction checker share it. |

Category references are offsets within one generated tree. A shared taxonomy fingerprint prevents an app build from pairing drug records with a different tree. The app reconstructs the original category paths at load time so saved medications and backups retain their existing identifiers. The raw catalog, glossary, and source crosswalks live under `scripts/data`.

## Update names and measure coverage

```sh
python3 scripts/drug_localization.py --refresh-rxnorm --report /tmp/anshin-catalog-coverage.json
uv run scripts/analyze_drugs.py
```

The refresh uses the public RxNorm ingredient vocabulary and exact matches against Anshin's reviewed aliases. Conflicting matches and unresolved entries appear in the report. Japanese and Korean names from national references can be added to `scripts/data/drug_reference_names.tsv` with the existing Chinese catalog key, language, official name, and a link or record identifier for the source. Confirm the reuse terms and that the reference identifies the same ingredient or combination. The original catalog name is the fallback for gaps.

For bulk matching, export a TSV with `english`, `language`, `name`, and `source` columns. The `english` column should contain the same ingredient designation in English, including any applicable salt or combination.

```sh
python3 scripts/drug_localization.py --reference-export /tmp/national-reference.tsv \
  --reference-candidates /tmp/anshin-reference-candidates.json
```

The matcher produces an exact-match queue and separates conflicting and unresolved records. Move reviewed matches into the reference TSV before regenerating the app assets.

This product uses publicly available data courtesy of the U.S. National Library of Medicine (NLM), National Institutes of Health and Department of Health and Human Services. NLM is not responsible for the product and does not endorse or recommend this or any other product. [RxNorm terms](https://www.nlm.nih.gov/research/umls/rxnorm/docs/termsofservice.html)

## Draft category labels

Point the generator at a LibreTranslate-compatible server. A self-hosted instance needs Chinese, English, Japanese, and Korean enabled. The Apple-backed `translate` server can use the same option once the corresponding macOS translation languages are installed. [LibreTranslate](https://github.com/LibreTranslate/LibreTranslate), [Apple-backed server](https://github.com/Arthur-Ficial/translate)

```sh
python3 scripts/drug_localization.py --libretranslate-url http://127.0.0.1:8989 \
  --draft-output /tmp/anshin-category-drafts.json
```

For a server that requires a key, set `LIBRETRANSLATE_API_KEY`. The Google Cloud alternative takes `--google-project PROJECT` and optionally `--google-glossary GLOSSARY`. It uses application-default credentials via `gcloud` or a short-lived `GOOGLE_OAUTH_ACCESS_TOKEN`.

The draft command translates missing labels only. Add approved labels to `scripts/data/drug_category_labels.tsv`, then rerun the offline generator to publish them. The generator derives category membership from the original catalog paths, independently of the display labels.
