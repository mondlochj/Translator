#!/usr/bin/env python3
"""Download a Piper TTS voice model and its JSON config to the app assets directory.

Usage:
    python3 scripts/download_piper_voice.py

The default voice is en_US-lessac-medium — a neutral US English male voice
that sounds natural at the cadence of meeting translations (~150 wpm).

Voice files are placed in:
    app/src/main/assets/piper_en_us_lessac_medium.onnx
    app/src/main/assets/piper_en_us_lessac_medium.onnx.json

These paths are checked by PiperTTSProvider.  If they are absent at runtime
the service falls back to AndroidNativeTTSProvider.

Voice catalogue: https://rhasspy.github.io/piper-samples/
Piper releases:  https://github.com/rhasspy/piper/releases

Run once after cloning the repo and before building the app on-device.
"""

import argparse
import hashlib
import os
import sys
import urllib.request
from pathlib import Path

# ---------------------------------------------------------------------------
# Available voices
# ---------------------------------------------------------------------------

VOICES = {
    "en_US-lessac-medium": {
        "model_url": (
            "https://huggingface.co/rhasspy/piper-voices/resolve/main/"
            "en/en_US/lessac/medium/en_US-lessac-medium.onnx"
        ),
        "config_url": (
            "https://huggingface.co/rhasspy/piper-voices/resolve/main/"
            "en/en_US/lessac/medium/en_US-lessac-medium.onnx.json"
        ),
        "model_size_mb": 63,
        "asset_base": "piper_en_us_lessac_medium",
    },
    "en_US-ryan-medium": {
        "model_url": (
            "https://huggingface.co/rhasspy/piper-voices/resolve/main/"
            "en/en_US/ryan/medium/en_US-ryan-medium.onnx"
        ),
        "config_url": (
            "https://huggingface.co/rhasspy/piper-voices/resolve/main/"
            "en/en_US/ryan/medium/en_US-ryan-medium.onnx.json"
        ),
        "model_size_mb": 63,
        "asset_base": "piper_en_us_ryan_medium",
    },
}

DEFAULT_VOICE = "en_US-lessac-medium"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

ASSETS_DIR = Path(__file__).parent.parent / "app" / "src" / "main" / "assets"


def progress_hook(block_num: int, block_size: int, total_size: int) -> None:
    downloaded = block_num * block_size
    if total_size > 0:
        pct = min(100, downloaded * 100 // total_size)
        mb = downloaded / (1024 * 1024)
        total_mb = total_size / (1024 * 1024)
        print(f"\r  {pct:3d}%  {mb:.1f} / {total_mb:.1f} MB", end="", flush=True)


def download_file(url: str, dest: Path) -> None:
    print(f"  Downloading: {url}")
    print(f"  Destination: {dest}")
    urllib.request.urlretrieve(url, dest, reporthook=progress_hook)
    print()  # newline after progress bar


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument(
        "--voice",
        default=DEFAULT_VOICE,
        choices=list(VOICES.keys()),
        help=f"Piper voice to download (default: {DEFAULT_VOICE})",
    )
    parser.add_argument(
        "--assets-dir",
        type=Path,
        default=ASSETS_DIR,
        help="Path to app assets directory (default: app/src/main/assets)",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Re-download even if files already exist",
    )
    args = parser.parse_args()

    voice = VOICES[args.voice]
    assets_dir: Path = args.assets_dir
    assets_dir.mkdir(parents=True, exist_ok=True)

    model_dest  = assets_dir / f"{voice['asset_base']}.onnx"
    config_dest = assets_dir / f"{voice['asset_base']}.onnx.json"

    print(f"\nPiper voice: {args.voice}")
    print(f"Estimated size: ~{voice['model_size_mb']} MB\n")

    # Model
    if model_dest.exists() and not args.force:
        print(f"[skip] Model already present: {model_dest.name}")
    else:
        download_file(voice["model_url"], model_dest)
        size_mb = model_dest.stat().st_size / (1024 * 1024)
        print(f"  Saved {size_mb:.1f} MB  sha256: {sha256(model_dest)[:12]}…")

    # Config JSON
    if config_dest.exists() and not args.force:
        print(f"[skip] Config already present: {config_dest.name}")
    else:
        download_file(voice["config_url"], config_dest)
        print(f"  Saved config  sha256: {sha256(config_dest)[:12]}…")

    print(f"\nDone. Files placed in:\n  {assets_dir.resolve()}")
    print("\nNext steps:")
    print("  1. Rebuild the app: ./gradlew assembleDebug")
    print("  2. PiperTTSProvider will use these files once the JNI bridge is wired (Phase 3+).")
    print("  3. Until the JNI bridge is complete, the app falls back to AndroidNativeTTSProvider.")


if __name__ == "__main__":
    main()
