#!/usr/bin/env python3
# /// script
# requires-python = ">=3.11"
# dependencies = [
#   "pypinyin>=0.51.0",
# ]
# ///
"""分析和清理药品 JSON 数据库，输出整理后的标准格式"""
import json
import collections
import hashlib
import os
import re
from datetime import datetime, timezone

from drug_category_rules import normalize_western_path
from drug_aliases import load_alias_source, write_clean_aliases
from drug_data_cleaning import build_drug_initials, merge_format_duplicate_records, should_keep_source_record

PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
RAW_BASE = os.path.join(PROJECT_ROOT, "scripts", "data")
ASSET_BASE = os.path.join(PROJECT_ROOT, "app", "src", "main", "assets", "json")
DATASET_MANIFEST = os.path.join(ASSET_BASE, "drug_dataset_manifest.json")
DATA_VERSION = "1"
PROTECTED_CANONICAL_NAMES = set(load_alias_source())

def analyze(path, label):
    with open(path, encoding="utf-8") as f:
        data = json.load(f)

    total = len(data)
    multi_cat = sum(1 for v in data.values() if isinstance(v, list))
    single_cat = total - multi_cat

    all_paths = []
    for v in data.values():
        if isinstance(v, list):
            all_paths.extend(v)
        else:
            all_paths.append(v)

    # 顶级分类统计
    top_cats = collections.Counter()
    for p in all_paths:
        top = p.split(" > ")[0].strip()
        top_cats[top] += 1

    # 路径深度统计
    depth_dist = collections.Counter(len(p.split(" > ")) for p in all_paths)

    # 检测问题数据
    empty_names = [k for k in data.keys() if not k.strip()]
    empty_paths = [k for k, v in data.items() if (isinstance(v, list) and not v) or (isinstance(v, str) and not v.strip())]
    dup_paths = {k: v for k, v in data.items() if isinstance(v, list) and len(set(v)) != len(v)}

    print(f"\n{'='*50}")
    print(f"  {label}")
    print(f"{'='*50}")
    print(f"药品总数: {total}")
    print(f"单分类: {single_cat} | 多分类: {multi_cat}")
    print(f"分类路径总计: {len(all_paths)}")
    print(f"\n路径深度分布:")
    for depth in sorted(depth_dist):
        print(f"  {depth}级: {depth_dist[depth]} 条")
    print(f"\n顶级分类 (前20):")
    for cat, cnt in top_cats.most_common(20):
        print(f"  {cnt:4d}  {cat}")
    print(f"\n数据质量问题:")
    print(f"  空药名: {len(empty_names)}")
    print(f"  空路径: {len(empty_paths)}")
    print(f"  路径内重复: {len(dup_paths)}")
    if dup_paths:
        for k, v in list(dup_paths.items())[:3]:
            print(f"    示例: {k} -> {v}")
    return data, top_cats

def clean_and_normalize(data, source, normalize_path=None):
    """
    清理规则：
    1. 去除空药名、空路径条目
    2. 路径内去重（同一药名下重复路径）
    3. 统一格式：value 始终为 list（即使单分类也用 list）
    4. 按药名拼音/笔画排序（这里按 Unicode 排序）
    """
    cleaned = {}
    for name, paths in data.items():
        name = name.strip()
        if not name:
            continue
        if not should_keep_source_record(name, source):
            continue
        if isinstance(paths, str):
            paths = [paths.strip()]
        else:
            paths = [p.strip() for p in paths if p.strip()]
        if normalize_path is not None:
            paths = [normalize_path(p) for p in paths]
        # 路径内去重，保持顺序
        seen = set()
        deduped = []
        for p in paths:
            if p not in seen:
                seen.add(p)
                deduped.append(p)
        if not deduped:
            continue
        cleaned[name] = deduped
    cleaned = merge_format_duplicate_records(cleaned, protected_names=PROTECTED_CANONICAL_NAMES)
    # 按药名 Unicode 排序
    return dict(sorted(cleaned.items()))

def build_category_tree(data):
    """从扁平路径构建分类树，叶子节点为药名列表"""
    tree = {}
    for name, paths in data.items():
        for path in paths:
            parts = [p.strip() for p in path.split(" > ")]
            node = tree
            for part in parts[:-1]:
                child = node.get(part)
                # 若叶子已被提前占用为列表，转为 dict
                if isinstance(child, list):
                    node[part] = {"__drugs__": child}
                elif child is None:
                    node[part] = {}
                node = node[part]
            leaf = parts[-1]
            if leaf not in node:
                node[leaf] = []
            if isinstance(node[leaf], list):
                if name not in node[leaf]:
                    node[leaf].append(name)
            elif isinstance(node[leaf], dict):
                node[leaf].setdefault("__drugs__", [])
                if name not in node[leaf]["__drugs__"]:
                    node[leaf]["__drugs__"].append(name)
    return tree

def count_tree(tree, depth=0):
    if isinstance(tree, list):
        return len(tree)
    return sum(count_tree(v, depth+1) for v in tree.values())

def sha256_file(path):
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()

def preserved_generated_at(manifest_path, comparable_payload):
    if not os.path.exists(manifest_path):
        return None
    try:
        with open(manifest_path, encoding="utf-8") as f:
            existing = json.load(f)
    except (OSError, json.JSONDecodeError):
        return None
    for key, value in comparable_payload.items():
        if existing.get(key) != value:
            return None
    return existing.get("generatedAt")

def write_dataset_manifest(western_count, tcm_count, reviewed_alias_count, initial_count):
    source_files = {
        "drugs.json": os.path.join(RAW_BASE, "drugs.json"),
        "tcm_drugs_flat.json": os.path.join(RAW_BASE, "tcm_drugs_flat.json"),
        "drug_aliases_reviewed.json": os.path.join(RAW_BASE, "drug_aliases_reviewed.json"),
    }
    asset_files = {
        "drugs_clean.json": os.path.join(ASSET_BASE, "drugs_clean.json"),
        "tcm_drugs_clean.json": os.path.join(ASSET_BASE, "tcm_drugs_clean.json"),
        "drug_aliases_clean.json": os.path.join(ASSET_BASE, "drug_aliases_clean.json"),
        "drug_initials_clean.json": os.path.join(ASSET_BASE, "drug_initials_clean.json"),
    }
    comparable_payload = {
        "dataVersion": DATA_VERSION,
        "generator": "scripts/analyze_drugs.py",
        "westernDrugCount": western_count,
        "tcmDrugCount": tcm_count,
        "reviewedAliasCount": reviewed_alias_count,
        "initialCount": initial_count,
        "sourceHashes": {name: sha256_file(path) for name, path in source_files.items()},
        "assetHashes": {name: sha256_file(path) for name, path in asset_files.items()},
    }
    manifest = {
        **comparable_payload,
        "generatedAt": preserved_generated_at(DATASET_MANIFEST, comparable_payload)
        or datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
    }
    with open(DATASET_MANIFEST, "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
        f.write("\n")
    return manifest

# ---- 分析 ----
drugs_data, _ = analyze(os.path.join(RAW_BASE, "drugs.json"), "drugs.json — 西药（ATC 分类）")
tcm_data, _ = analyze(os.path.join(RAW_BASE, "tcm_drugs_flat.json"), "tcm_drugs_flat.json — 中成药")

# ---- 清理 ----
print("\n\n" + "="*50)
print("  清理处理")
print("="*50)

drugs_clean = clean_and_normalize(drugs_data, source="western", normalize_path=normalize_western_path)
tcm_clean = clean_and_normalize(tcm_data, source="tcm")

print(f"西药: {len(drugs_data)} -> {len(drugs_clean)} (清理了 {len(drugs_data)-len(drugs_clean)} 条)")
print(f"中药: {len(tcm_data)} -> {len(tcm_clean)} (清理了 {len(tcm_data)-len(tcm_clean)} 条)")

# ---- 输出清理后的文件 ----
out_drugs = os.path.join(ASSET_BASE, "drugs_clean.json")
out_tcm = os.path.join(ASSET_BASE, "tcm_drugs_clean.json")
out_initials = os.path.join(ASSET_BASE, "drug_initials_clean.json")

with open(out_drugs, "w", encoding="utf-8") as f:
    json.dump(drugs_clean, f, ensure_ascii=False, indent=2)

with open(out_tcm, "w", encoding="utf-8") as f:
    json.dump(tcm_clean, f, ensure_ascii=False, indent=2)

drug_initials = build_drug_initials(drugs_clean, tcm_clean)
with open(out_initials, "w", encoding="utf-8") as f:
    json.dump(drug_initials, f, ensure_ascii=False, indent=2)
    f.write("\n")

print(f"\n已输出:")
print(f"  {out_drugs}")
print(f"  {out_tcm}")
print(f"  {out_initials} ({len(drug_initials)} 条拼音首字母)")

aliases_clean = write_clean_aliases(set(drugs_clean))
print(f"  {os.path.join(ASSET_BASE, 'drug_aliases_clean.json')} ({len(aliases_clean)} 条人工审核别名)")
dataset_manifest = write_dataset_manifest(len(drugs_clean), len(tcm_clean), len(aliases_clean), len(drug_initials))
print(f"  {DATASET_MANIFEST} (dataVersion {dataset_manifest['dataVersion']})")

# ---- 分类树统计 ----
print("\n\n" + "="*50)
print("  西药分类树（前3层）")
print("="*50)
tree = build_category_tree(drugs_clean)
for top, sub in sorted(tree.items()):
    drug_count = count_tree(sub)
    print(f"\n[{top}] ({drug_count} 种药)")
    if isinstance(sub, dict):
        for sec, sub2 in list(sorted(sub.items()))[:5]:
            c = count_tree(sub2)
            print(f"  ├─ {sec} ({c}种)")

print("\n\n" + "="*50)
print("  中药分类树（前3层）")
print("="*50)
tree_tcm = build_category_tree(tcm_clean)
for top, sub in sorted(tree_tcm.items()):
    drug_count = count_tree(sub)
    print(f"\n[{top}] ({drug_count} 种药)")
    if isinstance(sub, dict):
        for sec, sub2 in list(sorted(sub.items()))[:5]:
            c = count_tree(sub2)
            print(f"  ├─ {sec} ({c}种)")

print("\n完成！")
