# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Arosys Meeting Assistant** — an Android app for real-time Spanish→English bilingual assistance during business meetings. Runs entirely on-device (offline), including with the screen off and phone pocketed. Target hardware is Samsung Galaxy Fold (ARM64). All ML inference runs locally via ONNX Runtime Mobile.

## Build & Test Commands

```bash
# Build
./gradlew :app:assembleDebug             # Debug APK
./gradlew :app:assembleRelease           # Release APK (minified + shrunk)

# Tests
./gradlew testDebugUnitTest              # All JVM unit tests
./gradlew :app:jacocoTestDebugUnitTestReport  # Coverage report (HTML in build/reports/)
./gradlew :app:connectedAndroidTest      # Instrumented tests (requires device/emulator)

# Lint
./gradlew :app:lintDebug

# Model setup (run once before building — models are git-ignored)
python scripts/download_whisper_onnx.py
python scripts/download_nllb_onnx.py
python scripts/download_piper_voice.py
python scripts/generate_benchmark_model.py
```

CI runs unit tests + coverage + lint on every push/PR to `main` or `claude/**/feature/**`.

## Architecture

### Services & Data Flow

The app uses three independent foreground services wired together by `MeetingSession` (SharedFlow event bus):

```
Microphone → TranscriptionService (Whisper ONNX) → MeetingSession.pendingTranslations
                                                           ↓
                                         TranslationService (NLLB ONNX) → MeetingSession.translationCompleted
                                                                                  ↓
                                                               TTSService (Piper TTS → Bluetooth SCO)
                                                               LiveMeetingViewModel (UI state)
                                                               Room DB (persistent history)
```

Each service holds a `PARTIAL_WAKE_LOCK` so the pipeline keeps running when the screen is off. The `LLMService` (Qwen3 4B via llama.cpp) is a Phase 4 stub.

### Core Interfaces (stable, backend-swappable)

All ML backends implement interfaces in `core/interfaces/`:
- `SpeechRecognizer` → `WhisperOnnxSpeechRecognizer`
- `TranslationEngine` → `NLLBTranslationEngine`
- `TTSProvider` → `PiperTTSProvider`
- `LLMProvider` → `Qwen3LLMProvider` (stub)
- `StorageProvider` → `RoomStorageProvider`

When swapping models, implement the interface and swap the binding — services never depend on concrete impl classes.

### Hardware Acceleration

`HardwareAcceleratorManager` runs at app startup to probe the device and micro-benchmark each model:
- Tests NNAPI (NPU/DSP/GPU) → XNNPACK → CPU
- Caches results in SharedPreferences (7-day TTL)
- `OrtSessionFactory.createWithFallback()` applies the cached backend and falls back to CPU on any error

All ONNX models must be INT8/FP16 quantized. GGUF models must be Q4 or smaller.

### Audio Pipeline

`MicrophoneAudioSource` → `AudioChunker` → `MelSpectrogramProcessor` (FFT) → Whisper encoder/decoder. Audio chunk sizing is tuned for streaming latency; avoid changing `AudioChunker` without re-validating latency targets (< 2s transcription → translation).

### UI

Single-activity Compose app (`MainActivity`). `LiveMeetingViewModel` is the sole bridge between services and UI — it observes `MeetingSession` flows and exposes `uiState` as `StateFlow`. No XML layouts anywhere.

### Database

Room 2.6 with KSP. `AppDatabase` holds `MeetingEntity` and `TranscriptEntryEntity`. `TranscriptDao` uses FTS5 for full-text transcript search. Schema migrations must be versioned.

## Key Constraints

- **Min SDK 27** (required for NNAPI). **Target SDK 35**.
- **ABI filters:** `arm64-v8a` + `x86_64` only (debug). Release strips to `arm64-v8a`.
- **Kotlin 2.0 / JVM 17.** Compose BOM 2024.09.
- **No cloud calls.** All inference is on-device; no network permissions are declared.
- **Background operation is a first-class requirement** — every pipeline change must be tested with screen off.

## Testing Conventions

- Prefer **fake implementations** over mocks for ML interfaces. Ready-made fakes live in `src/test/.../testing/fakes/`.
- `MainDispatcherRule` (in `testing/`) must be applied to any test that touches `Dispatchers.Main`.
- Use **Turbine** for asserting on Flows (`turbineScope`, `awaitItem()`).
- Test data lives in `testing/fixtures/` (`TranscriptFixtures`, `AudioFixtures`).

## Development Phases

| Phase | Feature | Status |
|-------|---------|--------|
| 1 | Live Spanish Transcription (Whisper) | ✅ Complete |
| 2 | Live English Translation (NLLB) | ✅ Complete |
| 3 | Earbud Assistance (Piper TTS + Bluetooth SCO) | ✅ Complete |
| 4 | AI Meeting Intelligence (Qwen3 LLM) | 🚧 Stub |
| 5–7 | Smart Filtering, Conversation Copilot, Knowledge Base | Planned |

See `TRACKING.md` for detailed phase specs, decisions log, and latency benchmarks.
