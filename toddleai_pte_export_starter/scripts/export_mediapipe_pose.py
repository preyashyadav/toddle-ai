#!/usr/bin/env python3
"""
Export Qualcomm AI Hub MediaPipe Pose neural components to ExecuTorch PTE.

This is a starter/debugging script, not a promise that every upstream model
revision will export unchanged. It deliberately exports detector and landmark
components separately and keeps application postprocessing outside the graph.
"""

from __future__ import annotations

import argparse
import sys
import traceback
from pathlib import Path
from typing import Any, Dict, Iterable, Tuple

import torch


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser()
    p.add_argument(
        "--backend",
        choices=("inspect", "xnnpack", "qnn"),
        default="inspect",
    )
    p.add_argument(
        "--component",
        choices=("detector", "landmark", "both"),
        default="both",
    )
    p.add_argument("--soc", default="SM8750")
    p.add_argument("--output-dir", default="./output")
    return p.parse_args(argv)


def dtype_from_spec(dtype: Any) -> torch.dtype:
    if isinstance(dtype, torch.dtype):
        return dtype

    name = str(dtype).lower().replace("torch.", "")
    mapping = {
        "float": torch.float32,
        "float32": torch.float32,
        "fp32": torch.float32,
        "float16": torch.float16,
        "fp16": torch.float16,
        "uint8": torch.uint8,
        "int8": torch.int8,
        "int16": torch.int16,
        "int32": torch.int32,
        "int64": torch.int64,
        "bool": torch.bool,
    }
    if name not in mapping:
        raise ValueError(f"Unsupported dtype in input spec: {dtype!r}")
    return mapping[name]


def make_tensor(shape: Iterable[int], dtype: torch.dtype) -> torch.Tensor:
    shape = tuple(int(x) for x in shape)
    if dtype == torch.bool:
        return torch.zeros(shape, dtype=dtype)
    if dtype in (torch.uint8, torch.int8, torch.int16, torch.int32, torch.int64):
        return torch.zeros(shape, dtype=dtype)
    return torch.rand(shape, dtype=dtype)


def make_example_inputs(module: torch.nn.Module) -> Tuple[torch.Tensor, ...]:
    if not hasattr(module, "get_input_spec"):
        raise AttributeError(
            f"{type(module).__name__} has no get_input_spec(); "
            "provide its example input manually."
        )

    spec: Dict[str, Any] = module.get_input_spec()
    tensors = []

    for name, value in spec.items():
        if not isinstance(value, (tuple, list)) or len(value) != 2:
            raise ValueError(
                f"Unexpected input spec for {name}: {value!r}. "
                "Expected (shape, dtype)."
            )
        shape, dtype = value
        tensor = make_tensor(shape, dtype_from_spec(dtype))
        print(f"  input {name}: shape={tuple(tensor.shape)}, dtype={tensor.dtype}")
        tensors.append(tensor)

    return tuple(tensors)


def flatten_outputs(value: Any) -> list[torch.Tensor]:
    if isinstance(value, torch.Tensor):
        return [value]
    if isinstance(value, (tuple, list)):
        out: list[torch.Tensor] = []
        for item in value:
            out.extend(flatten_outputs(item))
        return out
    if isinstance(value, dict):
        out = []
        for key in value:
            out.extend(flatten_outputs(value[key]))
        return out
    raise TypeError(
        f"Output contains unsupported type {type(value).__name__}; "
        "wrap the module so forward returns tensors/tuples only."
    )


def load_components() -> dict[str, torch.nn.Module]:
    from qai_hub_models.models.mediapipe_pose.model import MediaPipePose

    bundle = MediaPipePose.from_pretrained()

    components = {
        "detector": bundle.pose_detector.eval(),
        "landmark": bundle.pose_landmark_detector.eval(),
    }
    return components


def inspect_component(name: str, module: torch.nn.Module):
    print(f"\n=== {name.upper()} ===")
    print("class:", type(module))
    if hasattr(module, "get_input_spec"):
        print("input spec:", module.get_input_spec())

    inputs = make_example_inputs(module)

    with torch.no_grad():
        eager = module(*inputs)

    outputs = flatten_outputs(eager)
    for idx, tensor in enumerate(outputs):
        print(
            f"  output[{idx}]: shape={tuple(tensor.shape)}, "
            f"dtype={tensor.dtype}"
        )

    print("Trying torch.export(strict=True)...")
    exported = torch.export.export(module, inputs, strict=True)
    print("torch.export succeeded.")
    return inputs, exported, outputs


def export_xnnpack(
    name: str,
    module: torch.nn.Module,
    inputs: Tuple[torch.Tensor, ...],
    output_dir: Path,
) -> Path:
    from executorch.backends.xnnpack.partition.xnnpack_partitioner import (
        XnnpackPartitioner,
    )
    from executorch.exir import to_edge_transform_and_lower

    exported = torch.export.export(module, inputs, strict=True)
    program = to_edge_transform_and_lower(
        exported,
        partitioner=[XnnpackPartitioner()],
    ).to_executorch()

    path = output_dir / f"mediapipe_pose_{name}_xnnpack.pte"
    path.write_bytes(program.buffer)
    return path


def get_qcom_imports():
    from executorch.backends.qualcomm.utils.utils import (
        generate_htp_compiler_spec,
        generate_qnn_executorch_compiler_spec,
        to_edge_transform_and_lower_to_qnn,
    )

    try:
        from executorch.backends.qualcomm.utils.utils import QcomChipset
    except ImportError:
        from executorch.backends.qualcomm.serialization.qc_schema import (
            QcomChipset,
        )

    return (
        QcomChipset,
        generate_htp_compiler_spec,
        generate_qnn_executorch_compiler_spec,
        to_edge_transform_and_lower_to_qnn,
    )


def export_qnn(
    name: str,
    module: torch.nn.Module,
    inputs: Tuple[torch.Tensor, ...],
    soc_name: str,
    output_dir: Path,
) -> Path:
    (
        QcomChipset,
        generate_htp_compiler_spec,
        generate_qnn_executorch_compiler_spec,
        to_edge_transform_and_lower_to_qnn,
    ) = get_qcom_imports()

    if not hasattr(QcomChipset, soc_name):
        supported = [x for x in dir(QcomChipset) if x.startswith("SM")]
        raise ValueError(f"Unsupported SoC {soc_name}. Available: {supported}")

    # Start with FP16 to de-risk graph export. Quantize only after the float
    # pipeline is numerically correct.
    backend_options = generate_htp_compiler_spec(use_fp16=True)
    compile_spec = generate_qnn_executorch_compiler_spec(
        soc_model=getattr(QcomChipset, soc_name),
        backend_options=backend_options,
    )

    delegated = to_edge_transform_and_lower_to_qnn(
        module,
        inputs,
        compile_spec,
    )
    program = delegated.to_executorch()

    path = output_dir / (
        f"mediapipe_pose_{name}_{soc_name.lower()}_qnn_fp16.pte"
    )
    path.write_bytes(program.buffer)
    return path


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    selected = (
        ("detector", "landmark")
        if args.component == "both"
        else (args.component,)
    )

    components = load_components()
    failures = []

    for name in selected:
        module = components[name]
        try:
            inputs, _, _ = inspect_component(name, module)

            if args.backend == "inspect":
                continue

            if args.backend == "xnnpack":
                path = export_xnnpack(name, module, inputs, output_dir)
            else:
                path = export_qnn(
                    name, module, inputs, args.soc, output_dir
                )

            print(f"Exported: {path}")

        except Exception as exc:
            failures.append((name, exc))
            print(f"\nFAILED: {name}: {exc}", file=sys.stderr)
            traceback.print_exc()

    if failures:
        print("\nOne or more components failed:")
        for name, exc in failures:
            print(f"- {name}: {exc}")
        print(
            "\nDo not hide this failure. Save the traceback; it identifies "
            "whether the problem is torch.export, an unsupported QNN op, "
            "or MediaPipe postprocessing."
        )
        return 1

    print("\nAll selected components completed successfully.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
