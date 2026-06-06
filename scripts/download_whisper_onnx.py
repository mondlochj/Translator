#!/usr/bin/env python3
"""
Downloads openai/whisper-tiny, exports encoder and decoder to ONNX using
Hugging Face optimum, and places the files in:
  app/src/main/assets/whisper_encoder.onnx
  app/src/main/assets/whisper_decoder.onnx
  app/src/main/assets/whisper_vocab.json

Also generates the mel filterbank benchmark model:
  app/src/main/assets/benchmark_model.onnx

Requirements:
  pip install optimum[onnxruntime] transformers onnx onnxruntime

Disk: ~150 MB for whisper-tiny ONNX models.

For a larger/better model, change MODEL_ID to:
  "openai/whisper-base"   (~290 MB, significantly more accurate)
  "openai/whisper-small"  (~970 MB, best quality that fits in ~2 GB RAM)
"""

import os
import sys
import json
import shutil
import tempfile

MODEL_ID = "openai/whisper-tiny"
ASSETS_DIR = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets")
TMP_DIR = os.path.join(os.path.expanduser("~"), ".cache", "arosys_whisper_export")

def check_deps():
    missing = []
    for pkg in ("transformers", "onnx", "onnxruntime"):
        try:
            __import__(pkg)
        except ImportError:
            missing.append(pkg)
    try:
        from optimum.onnxruntime import ORTModelForSpeechSeq2Seq  # noqa: F401
    except ImportError:
        missing.append("optimum[onnxruntime]")
    if missing:
        print(f"Missing packages: {', '.join(missing)}")
        print("Install with:")
        print("  pip install optimum[onnxruntime] transformers onnx onnxruntime")
        sys.exit(1)
    # torch ≥ 2.6 required — earlier versions have CVE-2025-32434 and are blocked
    # by transformers when loading pytorch_model.bin checkpoints
    try:
        import torch
        from packaging.version import Version
        if Version(torch.__version__.split("+")[0]) < Version("2.6.0"):
            print(f"PyTorch {torch.__version__} is too old (need ≥ 2.6.0).")
            print("Upgrade with:  pip install --upgrade torch")
            sys.exit(1)
    except ImportError:
        print("torch not found. Install with:  pip install torch")
        sys.exit(1)

def export_whisper():
    from optimum.onnxruntime import ORTModelForSpeechSeq2Seq
    from transformers import WhisperProcessor

    print(f"Downloading and exporting {MODEL_ID} to ONNX…")
    print("This will take a few minutes on first run.")

    save_dir = os.path.join(TMP_DIR, "saved")
    os.makedirs(save_dir, exist_ok=True)
    os.makedirs(ASSETS_DIR, exist_ok=True)

    # Export to ONNX and save to save_dir in one step
    ort_model = ORTModelForSpeechSeq2Seq.from_pretrained(
        MODEL_ID,
        export=True,
        cache_dir=TMP_DIR,
    )
    ort_model.save_pretrained(save_dir)
    print(f"  Saved ONNX files to {save_dir}")

    # Copy encoder and decoder to assets; try both naming conventions optimum uses
    for candidates, dst_name in [
        (["encoder_model.onnx", "model_encoder.onnx"], "whisper_encoder.onnx"),
        (["decoder_model.onnx", "model_decoder.onnx", "decoder_model_merged.onnx"], "whisper_decoder.onnx"),
    ]:
        copied = False
        for candidate in candidates:
            src = os.path.join(save_dir, candidate)
            if os.path.exists(src):
                dst = os.path.join(ASSETS_DIR, dst_name)
                shutil.copy2(src, dst)
                size_mb = os.path.getsize(dst) // 1024 // 1024
                print(f"  {dst_name}: {size_mb} MB  (from {candidate})")
                copied = True
                break
        if not copied:
            # List what was actually produced so the user can report it
            produced = [f for f in os.listdir(save_dir) if f.endswith(".onnx")]
            print(f"  WARNING: could not find {dst_name}. Files in save_dir: {produced}")

    # Export vocabulary
    processor = WhisperProcessor.from_pretrained(MODEL_ID, cache_dir=TMP_DIR)
    vocab = processor.tokenizer.get_vocab()
    vocab_path = os.path.join(ASSETS_DIR, "whisper_vocab.json")
    with open(vocab_path, "w", encoding="utf-8") as f:
        json.dump(vocab, f)
    print(f"  whisper_vocab.json: {len(vocab)} tokens")

def generate_benchmark_model():
    try:
        import onnx
        from onnx import helper, TensorProto
        print("\nGenerating benchmark_model.onnx…")
        a = helper.make_tensor_value_info("a", TensorProto.FLOAT, [1, 64])
        b = helper.make_tensor_value_info("b", TensorProto.FLOAT, [64, 64])
        out = helper.make_tensor_value_info("out", TensorProto.FLOAT, [1, 64])
        graph = helper.make_graph(
            [helper.make_node("MatMul", ["a", "b"], ["mm"]),
             helper.make_node("Relu", ["mm"], ["out"])],
            "benchmark", [a, b], [out],
        )
        model = helper.make_model(graph, opset_imports=[helper.make_opsetid("", 17)])
        model.ir_version = 8
        onnx.checker.check_model(model)
        path = os.path.join(ASSETS_DIR, "benchmark_model.onnx")
        onnx.save(model, path)
        print(f"  benchmark_model.onnx: {os.path.getsize(path)} bytes")
    except Exception as e:
        print(f"  Warning: could not generate benchmark model: {e}")

def main():
    check_deps()
    os.makedirs(ASSETS_DIR, exist_ok=True)
    export_whisper()
    generate_benchmark_model()
    print("\nAll assets written to", ASSETS_DIR)
    print("Rebuild the app to include them in the APK.")

if __name__ == "__main__":
    main()
