#!/usr/bin/env python3
"""
Downloads facebook/nllb-200-distilled-600M, exports encoder and decoder to
ONNX using Hugging Face optimum, and places the files in:
  app/src/main/assets/nllb_encoder.onnx
  app/src/main/assets/nllb_decoder.onnx
  app/src/main/assets/nllb_vocab.json

Requirements:
  pip install optimum[onnxruntime] transformers sentencepiece onnx onnxruntime

Disk: ~700 MB for the distilled-600M ONNX models (FP32).
      Use --quantize for INT8 (~175 MB, recommended for mobile).

Usage:
  python3 scripts/download_nllb_onnx.py
  python3 scripts/download_nllb_onnx.py --quantize
  python3 scripts/download_nllb_onnx.py --model facebook/nllb-200-distilled-1.3B

Notes:
  • First run downloads ~2.4 GB from HuggingFace; subsequent runs use cache.
  • INT8 quantization reduces size ~75% with minimal quality loss for Spanish→English.
  • On Galaxy Fold with Snapdragon 8 Gen 2+, NNAPI routes the distilled-600M
    encoder through the Hexagon NPU, achieving < 400ms latency per chunk.
"""

import argparse
import json
import os
import shutil
import sys

MODEL_ID    = "facebook/nllb-200-distilled-600M"
ASSETS_DIR  = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets")
TMP_DIR     = os.path.join(os.path.expanduser("~"), ".cache", "arosys_nllb_export")


def check_deps():
    from packaging.version import Version

    # optimum 2.x moved onnxruntime support to a separate package and changed
    # import paths.  Pin to the stable 1.x API.
    try:
        from importlib.metadata import version as pkg_version, PackageNotFoundError
        try:
            _opt_ver = pkg_version("optimum")
            if Version(_opt_ver) >= Version("2.0.0"):
                print(f"optimum {_opt_ver} is installed but these scripts require optimum 1.x.")
                print("The onnxruntime export API changed in 2.x and is not yet supported here.")
                print("Downgrade with:")
                print('  pip install "optimum[onnxruntime]<2.0"')
                sys.exit(1)
        except PackageNotFoundError:
            pass  # not installed — caught below
    except ImportError:
        pass  # Python < 3.8 fallback; version check skipped

    missing = []
    for pkg in ("transformers", "sentencepiece", "onnx", "onnxruntime"):
        try:
            __import__(pkg)
        except ImportError:
            missing.append(pkg)
    try:
        from optimum.onnxruntime import ORTModelForSeq2SeqLM  # noqa: F401
    except ImportError:
        missing.append('"optimum[onnxruntime]<2.0"')
    if missing:
        print(f"Missing packages: {', '.join(missing)}")
        print("Install with:")
        print('  pip install "optimum[onnxruntime]<2.0" transformers sentencepiece onnx onnxruntime')
        sys.exit(1)

    # torch ≥ 2.6 required — earlier versions have CVE-2025-32434 and are blocked
    # by transformers when loading pytorch_model.bin checkpoints
    try:
        import torch
        if Version(torch.__version__.split("+")[0]) < Version("2.6.0"):
            print(f"PyTorch {torch.__version__} is too old (need ≥ 2.6.0).")
            print("Upgrade with:  pip install --upgrade torch")
            sys.exit(1)
    except ImportError:
        print("torch not found. Install with:  pip install torch")
        sys.exit(1)


def export_nllb(model_id: str, quantize: bool):
    from optimum.onnxruntime import ORTModelForSeq2SeqLM
    from transformers import NllbTokenizer

    print(f"Downloading and exporting {model_id} to ONNX…")
    print("This will take several minutes on first run (~2.4 GB download).")

    os.makedirs(TMP_DIR, exist_ok=True)
    os.makedirs(ASSETS_DIR, exist_ok=True)

    export_kwargs = dict(export=True, cache_dir=TMP_DIR)
    if quantize:
        from optimum.onnxruntime.configuration import AutoQuantizationConfig
        print("INT8 quantization enabled — smaller model, faster on CPU/NNAPI")
        q_config = AutoQuantizationConfig.avx512_vnni(is_static=False, per_channel=False)
        model = ORTModelForSeq2SeqLM.from_pretrained(model_id, **export_kwargs)
        from optimum.onnxruntime import ORTQuantizer
        quantizers = [
            ORTQuantizer.from_pretrained(model, file_name="encoder_model.onnx"),
            ORTQuantizer.from_pretrained(model, file_name="decoder_model.onnx"),
        ]
        for q in quantizers:
            q.quantize(save_dir=TMP_DIR, quantization_config=q_config)
    else:
        model = ORTModelForSeq2SeqLM.from_pretrained(model_id, **export_kwargs)

    save_dir = os.path.join(TMP_DIR, "saved")
    model.save_pretrained(save_dir)

    # Copy encoder and decoder to assets
    for src_name, dst_name in [
        ("encoder_model.onnx", "nllb_encoder.onnx"),
        ("decoder_model.onnx", "nllb_decoder.onnx"),
    ]:
        src = os.path.join(save_dir, src_name)
        dst = os.path.join(ASSETS_DIR, dst_name)
        if os.path.exists(src):
            shutil.copy2(src, dst)
            size_mb = os.path.getsize(dst) // 1024 // 1024
            print(f"  {dst_name}: {size_mb} MB")
        else:
            print(f"  WARNING: {src_name} not found at {src}")

    # Export vocabulary as JSON (token → id)
    print("Exporting vocabulary…")
    tokenizer = NllbTokenizer.from_pretrained(model_id, cache_dir=TMP_DIR)
    vocab = tokenizer.get_vocab()
    vocab_path = os.path.join(ASSETS_DIR, "nllb_vocab.json")
    with open(vocab_path, "w", encoding="utf-8") as f:
        json.dump(vocab, f, ensure_ascii=False)
    size_kb = os.path.getsize(vocab_path) // 1024
    print(f"  nllb_vocab.json: {size_kb} KB, {len(vocab)} tokens")


def check_existing():
    """Report any already-downloaded assets."""
    for name in ("nllb_encoder.onnx", "nllb_decoder.onnx", "nllb_vocab.json"):
        path = os.path.join(ASSETS_DIR, name)
        if os.path.exists(path):
            size = os.path.getsize(path)
            unit = "KB" if size < 1_000_000 else "MB"
            val  = size // 1024 if size < 1_000_000 else size // 1024 // 1024
            print(f"  Already exists: {name} ({val} {unit}) — will overwrite")


def main():
    parser = argparse.ArgumentParser(description="Download and export NLLB-200 to ONNX")
    parser.add_argument("--model", default=MODEL_ID, help="HuggingFace model ID")
    parser.add_argument("--quantize", action="store_true",
                        help="Apply INT8 dynamic quantization (recommended for mobile)")
    args = parser.parse_args()

    print("=== NLLB-200 ONNX export ===")
    print(f"Model:     {args.model}")
    print(f"Quantize:  {args.quantize}")
    print(f"Destination: {os.path.abspath(ASSETS_DIR)}")
    print()

    check_deps()
    check_existing()
    export_nllb(args.model, args.quantize)

    print()
    print("All assets written to", os.path.abspath(ASSETS_DIR))
    print("Rebuild the app to include them in the APK.")
    print()
    print("Recommended model variants:")
    print("  facebook/nllb-200-distilled-600M  (default, ~700 MB FP32 / ~175 MB INT8)")
    print("  facebook/nllb-200-distilled-1.3B  (higher quality, ~1.4 GB FP32)")


if __name__ == "__main__":
    main()
