"""Export the official PARSeq checkpoint to a static ONNX graph."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Callable

import torch


DEFAULT_CHECKPOINT = Path("exported_candidates/parseq/parseq-bb5792a6.pt")
DEFAULT_OUTPUT = Path("exported_candidates/parseq.onnx")
DEFAULT_METADATA = Path("exported_candidates/parseq_metadata.json")


class ParseqOnnxWrapper(torch.nn.Module):
    def __init__(self, model: torch.nn.Module):
        super().__init__()
        self.model = model

    def forward(self, images: torch.Tensor) -> torch.Tensor:
        return self.model(images)


def load_parseq_for_export(
    checkpoint_path: str | Path,
    *,
    hub_loader: Callable[..., torch.nn.Module] | None = None,
    checkpoint_loader: Callable[..., object] | None = None,
) -> torch.nn.Module:
    hub_loader = hub_loader or torch.hub.load
    checkpoint_loader = checkpoint_loader or torch.load
    model = hub_loader(
        "baudm/parseq",
        "parseq",
        pretrained=False,
        decode_ar=False,
        refine_iters=0,
        trust_repo=True,
    ).eval()
    checkpoint = checkpoint_loader(checkpoint_path, map_location="cpu")
    inner_model = getattr(model, "model", model)
    if isinstance(checkpoint, dict) and "state_dict" in checkpoint:
        checkpoint = checkpoint["state_dict"]
    inner_model.load_state_dict(checkpoint)
    if hasattr(inner_model, "decode_ar"):
        inner_model.decode_ar = False
    if hasattr(inner_model, "refine_iters"):
        inner_model.refine_iters = 0
    return model.eval()


def _token_list(model: torch.nn.Module) -> list[str]:
    tokenizer = getattr(model, "tokenizer", None)
    tokens = getattr(tokenizer, "_itos", None)
    if tokens is None:
        raise ValueError("PARSeq tokenizer does not expose _itos tokens")
    return [str(token) for token in tokens]


def export_parseq_onnx(
    *,
    checkpoint_path: str | Path = DEFAULT_CHECKPOINT,
    output_path: str | Path = DEFAULT_OUTPUT,
    metadata_path: str | Path = DEFAULT_METADATA,
    opset: int = 17,
) -> dict[str, object]:
    model = load_parseq_for_export(checkpoint_path)
    wrapper = ParseqOnnxWrapper(model).eval()
    dummy_input = torch.randn(1, 3, 32, 128, dtype=torch.float32)
    output = Path(output_path)
    output.parent.mkdir(parents=True, exist_ok=True)
    torch.onnx.export(
        wrapper,
        dummy_input,
        str(output),
        opset_version=opset,
        do_constant_folding=True,
        input_names=["input"],
        output_names=["output"],
        dynamic_axes={
            "input": {0: "batch_size"},
            "output": {0: "batch_size"},
        },
    )
    metadata = {
        "tokens": _token_list(model),
        "image_shape": [3, 32, 128],
        "decode_ar": False,
        "refine_iters": 0,
    }
    metadata_output = Path(metadata_path)
    metadata_output.parent.mkdir(parents=True, exist_ok=True)
    metadata_output.write_text(json.dumps(metadata, indent=2, ensure_ascii=False), encoding="utf-8")
    return {
        "onnx_path": str(output),
        "metadata_path": str(metadata_output),
        "model_bytes": output.stat().st_size,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--checkpoint", default=str(DEFAULT_CHECKPOINT))
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT))
    parser.add_argument("--metadata-output", default=str(DEFAULT_METADATA))
    parser.add_argument("--opset", type=int, default=17)
    args = parser.parse_args(argv)
    report = export_parseq_onnx(
        checkpoint_path=args.checkpoint,
        output_path=args.output,
        metadata_path=args.metadata_output,
        opset=args.opset,
    )
    print(json.dumps(report, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
