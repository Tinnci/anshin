package com.driezy.medlog.interaction

import com.driezy.medlog.data.model.DrugInteraction
import com.driezy.medlog.data.model.InteractionRule
import com.driezy.medlog.data.model.InteractionSeverity
import com.driezy.medlog.data.model.Medication
import javax.inject.Inject
import javax.inject.Singleton

// ─── 规则库（基于 ATC 分类路径关键词 + 药名关键词）────────────────────────────
//
// 匹配逻辑：药品的 name.lowercase() + fullPath.lowercase() 包含组中任一关键词
// 即认为属于该组。规则库覆盖国内临床最常见的高风险配伍。

private val RULES: List<InteractionRule> = listOf(

    // ── HIGH ──────────────────────────────────────────────────────────────────

    InteractionRule(
        groupA = listOf("华法林", "warfarin"),
        groupB = listOf("阿司匹林", "aspirin", "布洛芬", "ibuprofen",
            "萘普生", "naproxen", "双氯芬酸", "diclofenac",
            "抗炎和抗风湿", "非甾体"),
        severity = InteractionSeverity.HIGH,
        labelA = "华法林（抗凝药）",
        labelB = "NSAIDs / 阿司匹林",
        description = "NSAIDs 可增强华法林抗凝效果，并损伤胃黏膜，显著增加严重出血风险。",
        advice = "应尽量避免合用；如必须合用，需密切监测 INR 并观察出血体征。",
    ),

    InteractionRule(
        groupA = listOf("华法林", "warfarin"),
        groupB = listOf("阿奇霉素", "azithromycin", "克拉霉素", "clarithromycin",
            "甲硝唑", "metronidazole", "氟康唑", "fluconazole",
            "环丙沙星", "ciprofloxacin"),
        severity = InteractionSeverity.HIGH,
        labelA = "华法林（抗凝药）",
        labelB = "某些抗感染药（大环内酯/唑类/喹诺酮）",
        description = "上述抗感染药可抑制华法林代谢（CYP2C9），使 INR 升高，出血风险大幅增加。",
        advice = "合用时须频繁监测 INR（每2～3天），必要时减少华法林剂量。",
    ),

    InteractionRule(
        groupA = listOf("单胺氧化酶抑制", "司来吉兰", "selegiline",
            "苯乙肼", "phenelzine", "tranylcypromine"),
        groupB = listOf("曲马多", "tramadol", "哌替啶", "pethidine",
            "芬太尼", "fentanyl", "右美沙芬", "dextromethorphan",
            "镇痛药"),
        severity = InteractionSeverity.HIGH,
        labelA = "MAO 抑制剂",
        labelB = "阿片类 / 中枢镇痛药",
        description = "合用可诱发 5-HT 综合征（高热、震颤、意识障碍），危及生命。",
        advice = "禁止合用；停用 MAOI 至少 14 天后方可使用阿片类药物。",
    ),

    InteractionRule(
        groupA = listOf("单胺氧化酶抑制", "司来吉兰", "selegiline",
            "苯乙肼", "phenelzine"),
        groupB = listOf("氟西汀", "fluoxetine", "帕罗西汀", "paroxetine",
            "舍曲林", "sertraline", "文拉法辛", "venlafaxine",
            "度洛西汀", "duloxetine", "西酞普兰", "citalopram",
            "抗抑郁"),
        severity = InteractionSeverity.HIGH,
        labelA = "MAO 抑制剂",
        labelB = "抗抑郁药（SSRI/SNRI）",
        description = "合用可导致严重 5-HT 综合征，表现为高热、肌阵挛、自主神经紊乱，可致命。",
        advice = "严禁合用；换药时需留出足够洗脱期（氟西汀需 5 周）。",
    ),

    InteractionRule(
        groupA = listOf("氯吡格雷", "clopidogrel", "替格瑞洛", "ticagrelor",
            "普拉格雷", "prasugrel"),
        groupB = listOf("奥美拉唑", "omeprazole", "埃索美拉唑", "esomeprazole",
            "质子泵", "ppi", "治疗与胃酸分泌相关"),
        severity = InteractionSeverity.MODERATE,
        labelA = "P2Y12 抗血小板药",
        labelB = "质子泵抑制剂（PPI）",
        description = "奥美拉唑/埃索美拉唑抑制 CYP2C19，降低氯吡格雷活化，减弱抗血小板效果。",
        advice = "若需 PPI，优选泮托拉唑或兰索拉唑（CYP2C19 抑制作用弱）。",
    ),

    InteractionRule(
        groupA = listOf("地高辛", "digoxin"),
        groupB = listOf("阿奇霉素", "azithromycin", "克拉霉素", "clarithromycin",
            "红霉素", "erythromycin", "胺碘酮", "amiodarone",
            "维拉帕米", "verapamil", "地尔硫", "diltiazem"),
        severity = InteractionSeverity.HIGH,
        labelA = "地高辛",
        labelB = "大环内酯类 / 抗心律失常药",
        description = "上述药物升高地高辛血药浓度（P-gp 抑制），可引起地高辛中毒（心律失常、恶心）。",
        advice = "合用时监测地高辛血药浓度，必要时减量；定期行心电图检查。",
    ),

    InteractionRule(
        groupA = listOf("锂盐", "碳酸锂", "lithium"),
        groupB = listOf("布洛芬", "ibuprofen", "萘普生", "naproxen",
            "双氯芬酸", "diclofenac", "吲哚美辛", "indomethacin",
            "抗炎和抗风湿", "非甾体"),
        severity = InteractionSeverity.HIGH,
        labelA = "锂盐",
        labelB = "NSAIDs",
        description = "NSAIDs 减少肾脏锂排泄，可升高锂血浓度至中毒水平（震颤、意识障碍、心律失常）。",
        advice = "避免合用；如必须合用，需监测锂浓度并适当减量。",
    ),

    InteractionRule(
        groupA = listOf("甲氨蝶呤", "methotrexate"),
        groupB = listOf("布洛芬", "ibuprofen", "萘普生", "naproxen",
            "双氯芬酸", "diclofenac", "阿司匹林", "aspirin",
            "抗炎和抗风湿", "非甾体"),
        severity = InteractionSeverity.HIGH,
        labelA = "甲氨蝶呤",
        labelB = "NSAIDs",
        description = "NSAIDs 减少甲氨蝶呤肾脏排泄，导致毒性蓄积（骨髓抑制、黏膜炎、肝毒性）。",
        advice = "高剂量甲氨蝶呤期间禁用 NSAIDs；低剂量时也需密切监测血象和肝功能。",
    ),

    InteractionRule(
        groupA = listOf("硝酸甘油", "单硝酸异山梨酯", "硝酸异山梨酯", "异山梨酯",
            "nitroglycerin", "isosorbide", "硝酸酯", "抗心绞痛"),
        groupB = listOf("西地那非", "sildenafil", "他达拉非", "tadalafil",
            "伐地那非", "vardenafil", "阿伐那非", "avanafil", "pde5"),
        severity = InteractionSeverity.HIGH,
        labelA = "硝酸酯类",
        labelB = "PDE5 抑制剂",
        description = "两者均扩张血管，合用可造成严重低血压、晕厥甚至心肌缺血。",
        advice = "禁止合用；近期使用 PDE5 抑制剂时不要自行服用硝酸甘油，请按医生给出的间隔处理。",
    ),

    InteractionRule(
        groupA = listOf("利奈唑胺", "linezolid"),
        groupB = listOf("氟西汀", "fluoxetine", "帕罗西汀", "paroxetine",
            "舍曲林", "sertraline", "文拉法辛", "venlafaxine",
            "度洛西汀", "duloxetine", "西酞普兰", "citalopram",
            "抗抑郁"),
        severity = InteractionSeverity.HIGH,
        labelA = "利奈唑胺",
        labelB = "SSRI / SNRI 抗抑郁药",
        description = "利奈唑胺具有 MAOI 样作用，与 SSRI/SNRI 合用可诱发 5-HT 综合征。",
        advice = "通常应避免合用；如感染治疗必须使用，请由医生评估停药、监测和替代方案。",
    ),

    InteractionRule(
        groupA = listOf("别嘌醇", "allopurinol", "非布司他", "febuxostat"),
        groupB = listOf("硫唑嘌呤", "azathioprine", "巯嘌呤", "mercaptopurine"),
        severity = InteractionSeverity.HIGH,
        labelA = "黄嘌呤氧化酶抑制剂",
        labelB = "硫唑嘌呤 / 巯嘌呤",
        description = "别嘌醇/非布司他抑制嘌呤类免疫抑制剂代谢，可导致严重骨髓抑制和感染风险。",
        advice = "避免自行合用；如必须合用，需医生大幅调整剂量并密切监测血常规。",
    ),

    InteractionRule(
        groupA = listOf("阿片", "吗啡", "morphine", "羟考酮", "oxycodone",
            "芬太尼", "fentanyl", "曲马多", "tramadol", "可待因", "codeine",
            "镇痛药"),
        groupB = listOf("苯二氮", "地西泮", "diazepam", "劳拉西泮", "lorazepam",
            "阿普唑仑", "alprazolam", "艾司唑仑", "estazolam",
            "唑吡坦", "zolpidem", "佐匹克隆", "zopiclone", "镇静催眠"),
        severity = InteractionSeverity.HIGH,
        labelA = "阿片类镇痛药",
        labelB = "苯二氮䓬 / Z-drug 镇静催眠药",
        description = "中枢抑制作用叠加，可导致过度镇静、跌倒、呼吸抑制甚至死亡。",
        advice = "避免合用或饮酒；如医生必须联用，应使用最低有效剂量并观察嗜睡、呼吸变慢等警示症状。",
    ),

    // ── MODERATE ──────────────────────────────────────────────────────────────

    InteractionRule(
        groupA = listOf("acei", "血管紧张素转换酶抑制", "卡托普利", "captopril",
            "依那普利", "enalapril", "赖诺普利", "lisinopril",
            "培哚普利", "perindopril"),
        groupB = listOf("arb", "血管紧张素受体拮抗", "缬沙坦", "valsartan",
            "厄贝沙坦", "irbesartan", "氯沙坦", "losartan", "坎地沙坦", "candesartan"),
        severity = InteractionSeverity.MODERATE,
        labelA = "ACEI",
        labelB = "ARB",
        description = "ACEI 与 ARB 双重阻断肾素-血管紧张素系统，可能增加低血压、高血钾和肾功能损害风险。",
        advice = "通常不建议常规合用；如专科医生要求合用，应监测血压、肌酐和血钾。",
    ),

    InteractionRule(
        groupA = listOf("acei", "arb", "血管紧张素转换酶抑制", "血管紧张素受体拮抗",
            "卡托普利", "依那普利", "赖诺普利", "缬沙坦", "厄贝沙坦", "氯沙坦"),
        groupB = listOf("氯化钾", "potassium chloride", "补钾", "钾片", "枸橼酸钾",
            "保钾利尿", "螺内酯", "spironolactone", "阿米洛利", "amiloride"),
        severity = InteractionSeverity.MODERATE,
        labelA = "ACEI/ARB",
        labelB = "补钾或保钾药",
        description = "合用会增加高血钾风险，肾功能下降、老年人或脱水时更明显。",
        advice = "不要自行补钾；合用时应按医嘱监测血钾和肾功能。",
    ),

    InteractionRule(
        groupA = listOf("他汀", "statin", "辛伐他汀", "simvastatin",
            "阿托伐他汀", "atorvastatin", "洛伐他汀", "lovastatin",
            "血脂修正药"),
        groupB = listOf("克拉霉素", "clarithromycin", "红霉素", "erythromycin",
            "伊曲康唑", "itraconazole", "酮康唑", "ketoconazole",
            "氟康唑", "fluconazole", "胺碘酮", "amiodarone",
            "环孢素", "cyclosporine"),
        severity = InteractionSeverity.MODERATE,
        labelA = "他汀类（调脂药）",
        labelB = "CYP3A4 强抑制剂",
        description = "CYP3A4 抑制剂升高他汀血药浓度，增加横纹肌溶解风险，可导致急性肾衰。",
        advice = "换用受 CYP3A4 影响较小的他汀（如瑞舒伐他汀、普伐他汀），或临时停用他汀。",
    ),

    InteractionRule(
        groupA = listOf("acei", "血管紧张素转换酶抑制", "卡托普利", "captopril",
            "依那普利", "enalapril", "赖诺普利", "lisinopril",
            "培哚普利", "perindopril"),
        groupB = listOf("保钾利尿", "螺内酯", "spironolactone",
            "阿米洛利", "amiloride", "氨苯蝶啶", "triamterene"),
        severity = InteractionSeverity.MODERATE,
        labelA = "ACEI（降压药）",
        labelB = "保钾利尿剂",
        description = "两者合用可导致严重高血钾（心律失常，甚至心脏停搏），肾功能不全者风险更高。",
        advice = "合用时密切监测血钾；肾功能不全患者通常应避免同时使用。",
    ),

    InteractionRule(
        groupA = listOf("acei", "arb", "血管紧张素转换酶抑制", "血管紧张素受体拮抗",
            "缬沙坦", "valsartan", "厄贝沙坦", "irbesartan",
            "氯沙坦", "losartan", "坎地沙坦", "candesartan"),
        groupB = listOf("布洛芬", "ibuprofen", "萘普生", "naproxen",
            "双氯芬酸", "diclofenac", "吲哚美辛", "indomethacin",
            "非甾体", "抗炎和抗风湿"),
        severity = InteractionSeverity.MODERATE,
        labelA = "ACEI/ARB（降压药）",
        labelB = "NSAIDs",
        description = "NSAIDs 拮抗降压效果，并可降低肾脏滤过率，诱发急性肾损伤（三联陷阱：合用利尿剂时风险最高）。",
        advice = "避免长期合用；如需止痛优选对乙酰氨基酚；监测血压和肾功能。",
    ),

    InteractionRule(
        groupA = listOf("氟喹诺酮", "左氧氟沙星", "levofloxacin",
            "环丙沙星", "ciprofloxacin", "莫西沙星", "moxifloxacin",
            "诺氟沙星", "norfloxacin"),
        groupB = listOf("钙", "钙剂", "铁", "铁剂", "镁", "铝",
            "铝碳酸镁", "碳酸钙", "矿物质补充", "antacid", "碳酸镁"),
        severity = InteractionSeverity.MODERATE,
        labelA = "氟喹诺酮类抗生素",
        labelB = "钙/铁/镁等矿物质补充剂",
        description = "二价/三价金属离子与喹诺酮螯合，使口服吸收率下降 50～90%，抗菌效果大减。",
        advice = "服用喹诺酮前 2 小时或后 6 小时再服矿物质补充剂/抗酸药。",
    ),

    InteractionRule(
        groupA = listOf("四环素", "tetracycline", "多西环素", "doxycycline",
            "米诺环素", "minocycline"),
        groupB = listOf("钙", "钙剂", "铁", "铁剂", "镁", "铝",
            "碳酸钙", "矿物质补充", "antacid"),
        severity = InteractionSeverity.MODERATE,
        labelA = "四环素类抗生素",
        labelB = "钙/铁/镁等矿物质补充剂",
        description = "金属离子与四环素形成不溶性螯合物，口服吸收率显著下降。",
        advice = "错开服用：两者间隔至少 2 小时。",
    ),

    InteractionRule(
        groupA = listOf("胺碘酮", "amiodarone"),
        groupB = listOf("β受体阻滞", "美托洛尔", "metoprolol", "阿替洛尔", "atenolol",
            "比索洛尔", "bisoprolol", "普萘洛尔", "propranolol",
            "维拉帕米", "verapamil", "地尔硫", "diltiazem",
            "心脏病治疗"),
        severity = InteractionSeverity.MODERATE,
        labelA = "胺碘酮",
        labelB = "β 阻滞剂 / 非二氢吡啶 CCB",
        description = "合用可加重窦性心动过缓、房室传导阻滞，心脏抑制作用叠加。",
        advice = "合用时需心电监护；避免在急性期快速静注；监测心率及 PR 间期。",
    ),

    InteractionRule(
        groupA = listOf("甲氨蝶呤", "methotrexate"),
        groupB = listOf("叶酸", "folic acid", "维生素b9", "甲酰四氢叶酸"),
        severity = InteractionSeverity.LOW,
        labelA = "甲氨蝶呤",
        labelB = "叶酸补充剂",
        description = "叶酸可降低甲氨蝶呤毒性（口腔溃疡、胃肠反应），但大剂量叶酸也可能降低疗效。",
        advice = "遵医嘱使用低剂量叶酸（如5mg/周）以减轻副作用；不要随意增加叶酸剂量。",
    ),

    InteractionRule(
        groupA = listOf("左甲状腺素", "levothyroxine", "优甲乐",
            "甲状腺", "thyroid"),
        groupB = listOf("钙", "钙剂", "铁", "铁剂", "铝",
            "碳酸钙", "矿物质补充"),
        severity = InteractionSeverity.MODERATE,
        labelA = "左甲状腺素",
        labelB = "钙/铁等矿物质补充剂",
        description = "钙/铁与左甲状腺素结合，使其吸收率下降约 20～40%，影响甲状腺功能调控。",
        advice = "左甲状腺素需空腹服用，矿物质补充剂应间隔至少 4 小时。",
    ),

    InteractionRule(
        groupA = listOf("二甲双胍", "metformin", "糖尿病用药", "降糖"),
        groupB = listOf("碘造影剂", "碘克沙醇", "碘普罗胺", "iodinated contrast",
            "造影"),
        severity = InteractionSeverity.MODERATE,
        labelA = "二甲双胍",
        labelB = "含碘造影剂",
        description = "造影剂可导致一过性肾功能下降，延缓二甲双胍清除，增加乳酸酸中毒风险。",
        advice = "造影前48小时停用二甲双胍，检查后确认肾功能正常方可恢复。",
    ),

    InteractionRule(
        groupA = listOf("磺脲类", "格列本脲", "glibenclamide",
            "格列美脲", "glimepiride", "格列吡嗪", "glipizide",
            "格列齐特", "gliclazide"),
        groupB = listOf("氟康唑", "fluconazole", "伏立康唑", "voriconazole",
            "克拉霉素", "clarithromycin", "环丙沙星", "ciprofloxacin"),
        severity = InteractionSeverity.MODERATE,
        labelA = "磺脲类降糖药",
        labelB = "某些抗感染药（唑类/大环内酯/喹诺酮）",
        description = "上述药物抑制磺脲代谢酶，升高磺脲血药浓度，可导致严重低血糖。",
        advice = "合用时加强血糖监测，必要时减少磺脲剂量。",
    ),

    InteractionRule(
        groupA = listOf("茶碱", "theophylline", "氨茶碱", "aminophylline"),
        groupB = listOf("环丙沙星", "ciprofloxacin", "依诺沙星", "enoxacin",
            "氟伏沙明", "fluvoxamine"),
        severity = InteractionSeverity.MODERATE,
        labelA = "茶碱类",
        labelB = "CYP1A2 抑制剂",
        description = "上述药物可升高茶碱血药浓度，引起恶心、心悸、震颤、惊厥或心律失常。",
        advice = "合用时需监测茶碱浓度和不良反应；出现心悸、手抖应及时联系医生。",
    ),

    InteractionRule(
        groupA = listOf("卡马西平", "carbamazepine", "苯妥英", "phenytoin",
            "利福平", "rifampicin", "rifampin"),
        groupB = listOf("利伐沙班", "rivaroxaban", "阿哌沙班", "apixaban",
            "达比加群", "dabigatran", "依度沙班", "edoxaban",
            "口服避孕", "炔雌醇", "ethinylestradiol"),
        severity = InteractionSeverity.MODERATE,
        labelA = "强酶诱导剂",
        labelB = "DOAC / 口服避孕药",
        description = "强酶诱导剂可降低部分抗凝药或口服避孕药血药浓度，导致疗效下降。",
        advice = "合用前请让医生或药师评估替代药物、额外避孕方式或抗凝方案。",
    ),

    InteractionRule(
        groupA = listOf("左旋多巴", "levodopa", "多巴丝肼", "卡左双多巴"),
        groupB = listOf("氟哌啶醇", "haloperidol", "利培酮", "risperidone",
            "奥氮平", "olanzapine", "喹硫平", "quetiapine", "抗精神病"),
        severity = InteractionSeverity.MODERATE,
        labelA = "左旋多巴类",
        labelB = "抗精神病药",
        description = "多巴胺拮抗作用可能削弱左旋多巴疗效，导致帕金森症状加重。",
        advice = "出现震颤、僵硬或行动变差时及时反馈；不要自行停用精神科药物。",
    ),

    // ── LOW ───────────────────────────────────────────────────────────────────

    InteractionRule(
        groupA = listOf("质子泵", "奥美拉唑", "omeprazole",
            "埃索美拉唑", "esomeprazole", "兰索拉唑", "lansoprazole",
            "泮托拉唑", "pantoprazole", "治疗与胃酸分泌相关"),
        groupB = listOf("铁", "铁剂", "硫酸亚铁", "ferrous sulfate",
            "琥珀酸亚铁", "富马酸亚铁"),
        severity = InteractionSeverity.LOW,
        labelA = "质子泵抑制剂（PPI）",
        labelB = "铁剂",
        description = "胃酸减少影响非血红素铁的溶解和吸收（铁需酸性环境吸收）。",
        advice = "铁剂最好在 PPI 服用前 30 分钟或两者间隔 2 小时服用，同时补充维生素 C 可促进吸收。",
    ),

    InteractionRule(
        groupA = listOf("阿司匹林", "aspirin"),
        groupB = listOf("布洛芬", "ibuprofen"),
        severity = InteractionSeverity.LOW,
        labelA = "阿司匹林（抗血小板）",
        labelB = "布洛芬（NSAIDs）",
        description = "布洛芬与阿司匹林竞争 COX-1 结合位点，可削弱阿司匹林的心脏保护性抗血小板效果。",
        advice = "服用阿司匹林后至少 2 小时再服布洛芬；长期止痛优选对乙酰氨基酚。",
    ),

    InteractionRule(
        groupA = listOf("辛伐他汀", "simvastatin", "阿托伐他汀", "atorvastatin",
            "洛伐他汀", "lovastatin"),
        groupB = listOf("葡萄柚", "西柚", "grapefruit"),
        severity = InteractionSeverity.LOW,
        labelA = "部分他汀类",
        labelB = "葡萄柚 / 西柚",
        description = "葡萄柚可抑制肠道 CYP3A4，升高部分他汀暴露量，增加肌痛和横纹肌溶解风险。",
        advice = "服用辛伐他汀、洛伐他汀或阿托伐他汀期间，避免大量食用葡萄柚或西柚汁。",
    ),

    InteractionRule(
        groupA = listOf("甲硝唑", "metronidazole", "替硝唑", "tinidazole"),
        groupB = listOf("酒精", "alcohol", "乙醇"),
        severity = InteractionSeverity.HIGH,
        labelA = "甲硝唑 / 替硝唑",
        labelB = "酒精",
        description = "合用可引起双硫仑样反应（面红、心悸、头痛、呕吐），严重时低血压休克。",
        advice = "服药期间及停药后 48 小时内严禁饮酒。",
    ),
)

// ─── 引擎 ─────────────────────────────────────────────────────────────────────

@Singleton
class InteractionRuleEngine @Inject constructor(
    private val aliasNormalizer: DrugAliasNormalizer,
) {

    /**
     * 检测活跃药品列表中存在的相互作用。
     * 返回去重后的 [DrugInteraction] 列表，按严重程度降序排列。
     */
    fun check(medications: List<Medication>): List<DrugInteraction> {
        if (medications.size < 2) return emptyList()

        val results = mutableListOf<DrugInteraction>()

        for (i in medications.indices) {
            for (j in i + 1 until medications.size) {
                val a = medications[i]
                val b = medications[j]
                val keyA = aliasNormalizer.searchText(a.name, a.fullPath, a.category)
                val keyB = aliasNormalizer.searchText(b.name, b.fullPath, b.category)

                for (rule in RULES) {
                    val aMatchesGroupA = rule.groupA.any { keyA.contains(it.lowercase()) }
                    val bMatchesGroupB = rule.groupB.any { keyB.contains(it.lowercase()) }
                    val aMatchesGroupB = rule.groupB.any { keyA.contains(it.lowercase()) }
                    val bMatchesGroupA = rule.groupA.any { keyB.contains(it.lowercase()) }

                    if ((aMatchesGroupA && bMatchesGroupB) ||
                        (aMatchesGroupB && bMatchesGroupA)
                    ) {
                        val drugA = if (aMatchesGroupA) a.name else b.name
                        val drugB = if (aMatchesGroupA) b.name else a.name
                        results += DrugInteraction(
                            drugA = drugA,
                            drugB = drugB,
                            severity = rule.severity,
                            description = rule.description,
                            advice = rule.advice,
                        )
                        break // 每对药品同一条规则只记一次
                    }
                }
            }
        }

        return results
            .distinctBy { it.drugA + it.drugB + it.severity.name }
            .sortedByDescending { it.severity.ordinal.unaryMinus() } // HIGH first
    }
}
