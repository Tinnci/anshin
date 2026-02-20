#!/usr/bin/env python3
"""分析和清理药品 JSON 数据库，输出整理后的标准格式"""
import json
import collections
import os
import re

BASE = "/Users/driezy/Downloads/MedLogAndroid/app/src/main/assets/json"

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

def clean_and_normalize(data):
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
        if isinstance(paths, str):
            paths = [paths.strip()]
        else:
            paths = [p.strip() for p in paths if p.strip()]
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

# ---- 分析 ----
drugs_data, _ = analyze(f"{BASE}/drugs.json", "drugs.json — 西药（ATC 分类）")
tcm_data, _ = analyze(f"{BASE}/tcm_drugs_flat.json", "tcm_drugs_flat.json — 中成药")

# ---- 清理 ----
print("\n\n" + "="*50)
print("  清理处理")
print("="*50)

drugs_clean = clean_and_normalize(drugs_data)
tcm_clean = clean_and_normalize(tcm_data)

print(f"西药: {len(drugs_data)} -> {len(drugs_clean)} (清理了 {len(drugs_data)-len(drugs_clean)} 条)")
print(f"中药: {len(tcm_data)} -> {len(tcm_clean)} (清理了 {len(tcm_data)-len(tcm_clean)} 条)")

# ---- 输出清理后的文件 ----
out_drugs = f"{BASE}/drugs_clean.json"
out_tcm = f"{BASE}/tcm_drugs_clean.json"

with open(out_drugs, "w", encoding="utf-8") as f:
    json.dump(drugs_clean, f, ensure_ascii=False, indent=2)

with open(out_tcm, "w", encoding="utf-8") as f:
    json.dump(tcm_clean, f, ensure_ascii=False, indent=2)

print(f"\n已输出:")
print(f"  {out_drugs}")
print(f"  {out_tcm}")

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
