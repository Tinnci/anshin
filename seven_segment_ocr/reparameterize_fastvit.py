"""Reparameterize and export the finetuned FastViT-T8 + CTC OCR model."""

import json
import sys
from pathlib import Path
import torch

# Add current directory to path if needed to import local modules
current_dir = Path(__file__).resolve().parent
if str(current_dir) not in sys.path:
    sys.path.insert(0, str(current_dir))

from fastvit_ctc import build_fastvit_t8_ctc, export_fastvit_onnx


def main() -> int:
    # 1. Setup paths
    root_dir = Path(__file__).resolve().parent
    weights_path = Path("/tmp/medlog_kaggle_candidate_results/candidate_finetune/fastvit_t8_ctc/fastvit_t8_ctc_best.pth")
    output_onnx_path = root_dir / "exported_candidates" / "fastvit_t8_ctc_reparam.onnx"
    output_metadata_path = root_dir / "exported_candidates" / "fastvit_t8_ctc_reparam_metadata.json"

    # 2. Check if weights exist
    if not weights_path.exists():
        print(f"Error: Finetuned weights not found at {weights_path}")
        return 1

    print(f"Loading finetuned weights from {weights_path}...")

    # 3. Build model (pretrained=False as we load local state dict)
    # The finetuned checkpoint's ctc_head weight has shape [16, 128], so num_classes = 16.
    model = build_fastvit_t8_ctc(num_classes=16, pretrained=False)

    # Load state dict
    state_dict = torch.load(weights_path, map_location="cpu", weights_only=True)
    model.load_state_dict(state_dict)
    model.eval()

    # 4. Apply structural reparameterization recursively
    reparam_count = 0
    for module in model.modules():
        if hasattr(module, "reparameterize"):
            module.reparameterize()
            reparam_count += 1

    print(f"Successfully reparameterized {reparam_count} submodules.")

    # 5. Export to ONNX
    res = export_fastvit_onnx(
        model,
        onnx_path=output_onnx_path,
        metadata_path=output_metadata_path,
        img_h=128,
        img_w=256,
    )

    # 6. Force-update metadata to mark reparameterized=True
    metadata = json.loads(output_metadata_path.read_text(encoding="utf-8"))
    metadata["reparameterized"] = True
    output_metadata_path.write_text(json.dumps(metadata, indent=2, ensure_ascii=False), encoding="utf-8")

    print(f"ONNX exported successfully to: {res['onnx_path']}")
    print(f"Metadata exported successfully to: {output_metadata_path}")
    print(f"Model file size: {Path(res['onnx_path']).stat().st_size / 1024 / 1024:.2f} MB")

    return 0


if __name__ == "__main__":
    sys.exit(main())
