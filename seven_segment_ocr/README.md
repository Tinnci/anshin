# 七段数码管 OCR 模型 & LCD 检测器

轻量级 LightSVTR (CNN + SVTR Attention + CTC) 模型,专门用于识别血压计、体温计等医疗设备的七段数码管（7-segment LCD）显示数字。配合 YOLOv11-nano 检测器在复杂照片中定位 LCD 区域。

## 模型信息

### LightSVTR 七段管识别模型

| 项目 | 值 |
|---|---|
| 架构 | LightSVTR: CNN backbone + 3× TransformerEncoder + CTC |
| 参数量 | ~638K |
| 输入 | 灰度图 `[1, 1, 128, 256]` |
| 输出 | CTC logits `[T, 1, 16]` |
| 字符集 | `0-9 / . 空格 - \n` (15字符 + blank) |
| 格式 | ONNX (opset 17) |
| Android 部署 | ONNX Runtime Android |

### YOLOv11-nano LCD 检测模型

| 项目 | 值 |
|---|---|
| 架构 | YOLOv11-nano (Detect) |
| 参数量 | ~2.6M |
| 输入 | RGB `[1, 3, 640, 640]` |
| 输出 | `[1, 5, 8400]` (cx, cy, w, h, conf) |
| 类别 | lcd_display (1 class) |
| 格式 | ONNX |
| 大小 | 10.1 MB (FP32) / **2.87 MB (INT8 量化)** |
| mAP50 | 0.9950 |
| mAP50-95 | 0.9947 |

## 准确率

在含纹理背景的困难测试集（25% easy / 35% normal / 40% hard）上:

| 模型 | 检测率 | 识别准确率 | 推理速度 | 大小 |
|---|---|---|---|---|
| **Our CRNN v3** | 100% | **90.5%** | 1.2 ms | **316 KB** |
| EAST | 32.5% | 0% | 180 ms | 92 MB |
| DB50 | 15.0% | 0% | 1283 ms | 97 MB |
| DB18 | 14.5% | 0% | 513 ms | 47 MB |

> EAST/DB 是通用场景文本检测模型,未针对七段管数字优化,且不带识别能力。

## 训练

### 环境准备

```bash
# 使用 pixi 管理环境 (需要先安装 pixi)
pixi install
```

### 本地训练

```bash
# 1. 生成合成训练数据 (8000 digit + 8000 sequence)
pixi run generate

# 2. 训练模型
pixi run train

# 3. 导出 ONNX 模型
pixi run export
```

### Kaggle GPU 训练（推荐）

#### LightSVTR 七段管识别

`kaggle_kernel/kaggle_train.py` 是自包含脚本,包含数据生成+模型定义+训练+ONNX导出:

```bash
cd kaggle_kernel
pixi run kaggle kernels push -p .
pixi run kaggle kernels output tiiann/seven-segment-ocr-training -p ../kaggle_output/
```

GPU 训练适合快速迭代合成数据分布；TPU 对本流程收益不明显，且 XLA 调试成本更高。

#### 真实域后训练 / Domain Adaptation

`kaggle_domain_adaptation_kernel/kaggle_domain_adaptation.py` 用于真实世界后训练:

```bash
cd kaggle_domain_adaptation_kernel
pixi run kaggle kernels push -p . --accelerator NvidiaTeslaT4
pixi run kaggle kernels output tiiann/seven-segment-ocr-domain-adaptation -p ../kaggle_domain_output/
```

也可以在 `seven_segment_ocr/` 目录直接使用:

```bash
pixi run kaggle-push-domain
pixi run kaggle-status-domain
```

推荐在 Kaggle 绑定一个真实 LCD 数据集:

```
labels.csv
images/
  sample_001.jpg
```

`labels.csv`:

```csv
filename,label,split
sample_001.jpg,138/88,train
sample_002.jpg,97.2,val
```

该脚本会:

1. 读取真实标注数据,没有 `split` 时用稳定 hash 分 train/val/test。
2. 生成带 `real_world` 增强的合成样本,覆盖扫描纹、背光不均、玻璃眩光、局部段缺失、压缩和运动模糊。
3. 输出 PaddleOCR SimpleDataSet 文件,用于可选的 `PP-OCRv5_mobile_rec` teacher fine-tune。
4. 输出 `runtime_report.json`,记录 Kaggle 实际 Python/PyTorch/CUDA、GPU 名称/显存、`nvidia-smi` 和 TPU 环境变量。
5. 训练 Android 端可部署的 LightSVTR student,导出 `svtr_seven_seg_domain.onnx`。
6. 输出 `evaluation_report.json`,包含 validation/test exact match、按 `real`/`synthetic` source 拆分的准确率和错误样例。

当前推荐显式使用 `NvidiaTeslaT4`。Kaggle CLI 还支持传入其它 accelerator ID,但部分硬件可能只对特定比赛或管理员开放。

#### YOLOv11-nano LCD 检测

`kaggle_detection_kernel/kaggle_detection_train.py` 是自包含脚本,包含合成场景数据生成+YOLO训练+ONNX导出:

```bash
cd kaggle_detection_kernel
pixi run kaggle kernels push -p .
pixi run kaggle kernels output tiiann/lcd-display-detector-yolov11-nano -p ../kaggle_detection_output/
```

v3 模型（修复 gradient 背景 bug）在 P100 上训练 100 epochs 约 100 分钟。

## 数据增强

### CRNN 识别数据

合成数据包含以下增强以模拟真实场景:

- **6 种纹理背景**: 塑料、金属拉丝、木纹、织物、医疗设备面板、大理石
- **几何变换**: 旋转、透视、倾斜
- **噪声**: 高斯噪声、椒盐噪声、JPEG 压缩伪影
- **光照**: 亮度/对比度变化、色偏、反射高光
- **遮挡**: 部分遮挡、边框
- **真实 LCD 失真**: 扫描纹、背光不均、玻璃眩光、段缺失/污渍、压缩与运动模糊

### 外部数据集候选

Kaggle 上存在一些七段数码管相关数据集,可作为真实域补充:

- `thearshiya/7-segment-industrial-digits-dataset`: 工业现场采集,含 train/val/test,标签在文件名中。
- `loclaurote/seven-segment-display-dataset-yolov5`: 偏 YOLO 检测格式,适合 LCD/数字区域检测预训练。
- `testtor/sevensegment-numbers`: 七段数字数据,可作为识别预训练补充。
- `edventy/nto-lcd-2`: LCD 相关小数据集,需要先检查标注结构。

这些数据集不能完全替代医疗设备域数据。仍然欠缺:

- 血压计/血糖仪/体温计/体重秤的真实整机照片和真实 LCD crop。
- 多行读数 (SYS/DIA/PUL)、单位标签、图标、低电量/记忆符号对数字行的干扰。
- 弯曲玻璃、强反光、手持倾斜、低光、低分辨率压缩和局部段老化。
- 设备型号级切分的 test set,用于验证跨设备泛化,避免同一设备照片同时进入 train/test。

### YOLO 检测数据

场景合成生成器 (`generate_detection_data.py` / `kaggle_detection_train.py` 内置):

- **8 种场景背景**: 纯色、渐变、布纹、木纹、大理石、金属、磨砂、医疗
- **4 种设备外壳**: 圆角矩形、圆形、梯形、带按钮面板
- **LCD 区域**: 七段管数码管渲染、亮度/对比度变化
- **几何变换**: 随机缩放、旋转、位置偏移
- **YOLO 格式**: 自动生成 `labels/` 归一化标注

## 文件结构

```
seven_segment_ocr/
├── generate_data.py              # LightSVTR/CTC 合成数据生成器 (含纹理背景、增强)
├── generate_detection_data.py    # YOLO 检测数据生成器 (场景合成)
├── train.py                      # 本地 CRNN 训练脚本
├── export_tflite.py              # ONNX 模型导出
├── benchmark.py                  # 模型对比基准测试
├── pixi.toml                     # Python 环境配置
├── exported/
│   ├── svtr_seven_seg.onnx       # LightSVTR ONNX 模型
│   ├── crnn_seven_seg.onnx       # 旧版 CRNN ONNX 模型
│   ├── lcd_detector.onnx         # YOLOv11-nano FP32 (10.1 MB)
│   └── lcd_detector_int8.onnx    # YOLOv11-nano INT8 量化 (2.87 MB)
├── kaggle_kernel/
│   ├── kaggle_train.py           # Kaggle LightSVTR/CRNN 训练脚本 (自包含)
│   └── kernel-metadata.json
├── kaggle_domain_adaptation_kernel/
│   ├── kaggle_domain_adaptation.py # Kaggle 真实域后训练 + PP-OCR 数据导出
│   └── kernel-metadata.json
└── kaggle_detection_kernel/
    ├── kaggle_detection_train.py # Kaggle 检测训练脚本 (自包含)
    └── kernel-metadata.json
```

## 预处理要求

推理时的图像预处理**必须**与训练一致:

1. 转灰度
2. 保持宽高比缩放到高度 64px
3. 左对齐填充到 256px 宽（右侧黑色填充）
4. 归一化到 `[0, 1]`

> ⚠️ 不要直接 resize 到 `(256, 64)`,这会拉伸图像导致准确率大幅下降。

## 在 Android 中使用

模型文件放在 `app/src/main/assets/`:
- `crnn_seven_seg.onnx` — 七段管识别 (参见 `SevenSegmentRecognizer.kt`)
- `lcd_detector.onnx` — LCD 区域检测 INT8 量化版 (参见 `LcdDisplayDetector.kt`)

通过 ONNX Runtime Android 1.26.0 加载推理,模型均异步加载以避免阻塞 UI 线程。
