"""Shared category normalization rules for MedLog drug assets."""

from __future__ import annotations

MISC_REHOME_RULES: dict[str, str] = {
    "系统用药的抗病毒药": "系统用抗感染药",
    "抗分支杆菌药": "系统用抗感染药",
    "性激素和生殖系统调节药": "生殖泌尿系统和性激素",
    "精神兴奋药": "神经系统",
    "抗抑郁药": "神经系统",
    "抗震颤麻痹药": "神经系统",
    "免疫抑制剂": "抗肿瘤药和免疫机能调节药",
    "内分泌疗法": "抗肿瘤药和免疫机能调节药",
    "垂体和下丘脑激素及其类似药物": "非性激素和胰岛素类的激素类系统用药",
    "血脂调节剂": "心血管系统",
    "抗高血压药": "心血管系统",
    "β-受体阻断药": "心血管系统",
    "钙通道阻断药": "心血管系统",
    "外周血管扩张剂": "心血管系统",
    "抗痤疮药": "皮肤病用药",
}


def normalize_western_path(path: str) -> str:
    """Move clear ATC V/misc records under their user-facing system category."""
    parts = [part.strip() for part in path.split(" > ") if part.strip()]
    if len(parts) < 2 or parts[0] != "杂类":
        return path
    rehomed_top = MISC_REHOME_RULES.get(parts[1])
    if rehomed_top is None:
        return path
    return " > ".join([rehomed_top, *parts[1:]])
