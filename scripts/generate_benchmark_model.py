#!/usr/bin/env python3
"""
Generates benchmark_model.onnx — a tiny ONNX model used by AcceleratorBenchmarkRunner
to measure relative backend performance (CPU vs XNNPACK vs NNAPI) at app start.

The model performs:
    mm = MatMul(a, b)         # [1, 64] x [64, 64] → [1, 64]
    out = Relu(mm)            # element-wise ReLU

This exercises matrix multiply + activation, which is representative of
the inner loop of both Whisper (attention) and NLLB (encoder/decoder).
The model has no weights (both inputs are runtime tensors) so the file is < 500 bytes.

Requirements:
    pip install onnx>=1.14.0

Usage:
    python3 scripts/generate_benchmark_model.py
    # Output: app/src/main/assets/benchmark_model.onnx
"""

import os
import sys

try:
    import onnx
    from onnx import helper, TensorProto
except ImportError:
    print("Error: onnx package not found. Install with: pip install onnx>=1.14.0")
    sys.exit(1)

OUTPUT_PATH = os.path.join(
    os.path.dirname(__file__),
    "..", "app", "src", "main", "assets", "benchmark_model.onnx"
)

BATCH = 1
INNER = 64
OUTER = 64

def build_model() -> onnx.ModelProto:
    a = helper.make_tensor_value_info("a", TensorProto.FLOAT, [BATCH, INNER])
    b = helper.make_tensor_value_info("b", TensorProto.FLOAT, [INNER, OUTER])
    out = helper.make_tensor_value_info("out", TensorProto.FLOAT, [BATCH, OUTER])

    matmul_node = helper.make_node("MatMul", inputs=["a", "b"], outputs=["mm_out"])
    relu_node = helper.make_node("Relu",   inputs=["mm_out"], outputs=["out"])

    graph = helper.make_graph(
        nodes=[matmul_node, relu_node],
        name="arosys_benchmark",
        inputs=[a, b],
        outputs=[out],
    )

    model = helper.make_model(
        graph,
        opset_imports=[helper.make_opsetid("", 17)],
    )
    model.ir_version = 8
    model.doc_string = "Arosys Meeting Assistant — accelerator micro-benchmark model"

    onnx.checker.check_model(model)
    return model


def main():
    os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)
    model = build_model()
    onnx.save(model, OUTPUT_PATH)
    size = os.path.getsize(OUTPUT_PATH)
    print(f"Written: {OUTPUT_PATH}  ({size} bytes)")
    print()
    print("Input shapes:")
    print(f"  a: float32[{BATCH}, {INNER}]")
    print(f"  b: float32[{INNER}, {OUTER}]")
    print(f"Output shape:")
    print(f"  out: float32[{BATCH}, {OUTER}]")
    print()
    print("Place this file in app/src/main/assets/ before building the APK.")


if __name__ == "__main__":
    main()
