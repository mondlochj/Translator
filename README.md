# Arosys Meeting Assistant

An Android app that provides real-time bilingual assistance during Spanish-language business meetings. It transcribes Spanish speech, translates it to English, and delivers audio output to a Bluetooth earbud — all on-device, offline, with the phone's screen off and pocketed.

**Target device:** Samsung Galaxy Fold (ARM64, Android 8.1+)  
**Primary use case:** English speaker attending Spanish-language business meetings in Guatemala

---

## What It Does

```
Spanish speech → Whisper (transcription) → NLLB (translation) → English text on screen
                                                               → Piper TTS → Bluetooth earbud
```

The pipeline runs entirely in the background. Once started, the phone can be folded, pocketed, and the screen turned off — transcription, translation, and audio output continue via foreground services and wake locks.

### Audio Modes

| Mode | Behavior |
|------|----------|
| A — Text Only | Display only; no audio |
| B — All Speech | Speaks every translated sentence to the earbud |
| C — Priority Only | Speaks only decisions, deadlines, action items, questions directed at you, and financial figures |

---

## Setup

### 1. Prerequisites

- Android Studio Ladybug or newer
- Python 3.8+ (for model download scripts)
- A connected device or emulator (ARM64 or x86_64)

### 2. Download Models

Model files are not included in the repository. Run these scripts once before building:

```bash
python scripts/generate_benchmark_model.py   # required for hardware detection
python scripts/download_whisper_onnx.py      # Phase 1: speech recognition
python scripts/download_nllb_onnx.py         # Phase 2: translation
python scripts/download_piper_voice.py       # Phase 3: text-to-speech
```

Models are saved to `app/src/main/assets/` and `models/` (both gitignored).

### 3. Samsung Galaxy Fold — Battery Setting

On the device, go to **Settings → Battery → Background usage limits → Arosys Meeting Assistant** and set to **Unrestricted**. Without this, the Samsung OEM battery manager may kill the background services.

### 4. Build & Install

```bash
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Development

```bash
# Build
./gradlew :app:assembleDebug             # debug APK
./gradlew :app:assembleRelease           # release APK (minified, ARM64 only)

# Tests
./gradlew testDebugUnitTest                          # all JVM unit tests
./gradlew testDebugUnitTest --tests "*.ClassName"    # single test class
./gradlew :app:jacocoTestDebugUnitTestReport         # coverage (build/reports/)
./gradlew :app:connectedAndroidTest                  # instrumented tests (requires device)

# Lint
./gradlew :app:lintDebug
```

CI runs unit tests + lint on every push to `main` or `claude/**` branches and all PRs.

---

## Architecture

### Services & Data Flow

Three independent foreground services communicate through `MeetingSession` (a SharedFlow event bus):

```
Microphone
  └─► TranscriptionService  (Whisper ONNX)
          └─► MeetingSession.pendingTranslations
                  └─► TranslationService  (NLLB ONNX)
                          └─► MeetingSession.translationCompleted
                                  ├─► TTSService  (Piper → Bluetooth SCO)
                                  ├─► LiveMeetingViewModel  (Compose UI)
                                  └─► Room DB  (persistent history + FTS5 search)
```

Each service holds a `PARTIAL_WAKE_LOCK`. The `mediaPlayback` foreground service type on `TTSService` is required for audio routing to a Bluetooth headset when the screen is off.

### Hardware Acceleration

`HardwareAcceleratorManager` runs at app startup to probe the device and benchmark each model across three execution providers:

```
NNAPI (Hexagon NPU on Snapdragon)  →  XNNPACK (CPU, SIMD)  →  CPU (baseline)
```

Results are cached in SharedPreferences (7-day TTL). `OrtSessionFactory.createWithFallback()` applies the cached backend and falls back to CPU on any error. All models must be INT8/FP16 quantized ONNX (or Q4 GGUF for the LLM).

### Core Interfaces

All ML backends are swappable behind stable interfaces in `core/interfaces/`:

| Interface | Current Implementation |
|-----------|----------------------|
| `SpeechRecognizer` | `WhisperOnnxSpeechRecognizer` |
| `TranslationEngine` | `NLLBTranslationEngine` |
| `TTSProvider` | `PiperTTSProvider` (+ `AndroidNativeTTSProvider` fallback) |
| `LLMProvider` | `Qwen3LLMProvider` (Phase 4 stub) |
| `StorageProvider` | `RoomStorageProvider` |

### Key Permissions

| Permission | Purpose |
|------------|---------|
| `RECORD_AUDIO` | Microphone input |
| `BLUETOOTH_CONNECT` | Bluetooth headset (runtime on API 31+) |
| `FOREGROUND_SERVICE_*` | Keep services alive when screen is off |
| `WAKE_LOCK` | Prevent CPU sleep during inference |

---

## Project Phases

| Phase | Feature | Status |
|-------|---------|--------|
| 1 | Live Spanish Transcription (Whisper ONNX) | ✅ Implemented |
| 2 | Live English Translation (NLLB-200-distilled-600M) | ✅ Implemented |
| 3 | Earbud Assistance (Piper TTS + Bluetooth SCO) | ✅ Implemented |
| 4 | AI Meeting Intelligence (Qwen3 4B — post-meeting analysis) | Stub |
| 5 | Smart Real-Time Filtering (async LLM alerts) | Planned |
| 6 | Conversation Copilot (on-demand quick answers) | Planned |
| 7 | Knowledge Base (searchable meeting history) | Planned |

Phases 1–3 are fully implemented and pending first device benchmark. See [TRACKING.md](TRACKING.md) for detailed specs, decisions log, latency targets, and known issues.

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.0, JVM 17 |
| UI | Jetpack Compose (Material3, BOM 2024.09) |
| Inference | ONNX Runtime Mobile 1.19 (CPU / XNNPACK / NNAPI) |
| Speech recognition | Whisper (ONNX quantized) |
| Translation | NLLB-200-distilled-600M (ONNX INT8) |
| Text-to-speech | Piper TTS (ONNX) + Android TTS fallback |
| LLM | Qwen3 4B (GGUF via llama.cpp) — Phase 4 |
| Database | Room 2.6 + FTS5 |
| Min / Target SDK | 27 (Android 8.1) / 35 |
| ABI | `arm64-v8a` + `x86_64` (debug); `arm64-v8a` only (release) |

---

## Testing

- **Fakes** over mocks — ready-made fake implementations for all core interfaces are in `src/test/.../testing/fakes/`
- **Robolectric** for Android-dependent unit tests (Room, SharedPreferences, Context)
- **Turbine** for Flow assertions
- **`MainDispatcherRule`** required for any test that touches coroutines

```bash
# Run a specific test class
./gradlew testDebugUnitTest --tests "*.HardwareAcceleratorManagerTest"
```
