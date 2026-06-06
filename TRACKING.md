# Arosys Meeting Assistant — Project Tracking

**Last Updated:** 2026-06-06
**Target Device:** Samsung Galaxy Fold series
**Primary Use Case:** Bilingual (Spanish/English) business meetings in Guatemala

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Technology Stack](#technology-stack)
3. [Architecture Overview](#architecture-overview)
4. [Phase Status Dashboard](#phase-status-dashboard)
5. [Phase 1 — Live Spanish Transcription](#phase-1--live-spanish-transcription)
6. [Phase 2 — Live English Translation](#phase-2--live-english-translation)
7. [Phase 3 — Earbud Assistance Mode](#phase-3--earbud-assistance-mode)
8. [Phase 4 — AI Meeting Intelligence](#phase-4--ai-meeting-intelligence)
9. [Phase 5 — Smart Real-Time Filtering](#phase-5--smart-real-time-filtering)
10. [Phase 6 — Conversation Copilot](#phase-6--conversation-copilot)
11. [Phase 7 — Knowledge Base](#phase-7--knowledge-base)
12. [Decisions Log](#decisions-log)
13. [Performance Benchmarks](#performance-benchmarks)
14. [Known Issues & Risks](#known-issues--risks)
15. [Stretch Goal — Interpreter Mode](#stretch-goal--interpreter-mode)

---

## Project Overview

An Android application that provides live bilingual assistance during Spanish-language business meetings. The user speaks English and intermediate Spanish — they can speak Spanish but struggle to follow rapid spoken Spanish. The app transcribes, translates, and intelligently summarizes speech, delivering output both visually and via a Bluetooth earbud.

**Design Principles:**
- Speed over perfect translation
- Meaning over literal translation
- Keep user engaged in conversation, not staring at screen
- Local-first / offline-capable
- Modular — models can be swapped without business logic changes
- Battery-conscious
- Separate Android Services for speech and translation engines (enables future offload to home AI server)

**Latency Targets:**
| Milestone | Target |
|-----------|--------|
| Phase 1–2 | < 2 seconds |
| Phase 3–4 | < 1 second |
| Ultimate goal | 500ms–1000ms perceived |

---

## Technology Stack

| Layer | Technology | Notes |
|-------|-----------|-------|
| Language | Kotlin | |
| UI | Jetpack Compose | |
| Speech Recognition | Whisper.cpp | Streaming; local inference |
| Translation | NLLB distilled | Abstraction layer wraps it |
| TTS | Piper TTS | Android native TTS as fallback |
| LLM | Qwen3 4B | Pluggable; future: Gemma, Llama |
| Database | SQLite / Room | |
| Audio | Bluetooth headset API | Single-earbud operation |

All models must be quantized. No network dependencies for core features.

---

## Architecture Overview

### Core Interfaces

These interfaces must remain stable. Models and providers are swapped behind them without touching business logic.

```
SpeechRecognizer
  └── WhisperSpeechRecognizer (Phase 1)

TranslationEngine
  └── NLLBTranslationEngine (Phase 2)

TTSProvider
  └── PiperTTSProvider (Phase 3)
  └── AndroidNativeTTSProvider (Phase 3 fallback)

LLMProvider
  └── Qwen3LLMProvider (Phase 4)

StorageProvider
  └── RoomStorageProvider (Phase 1+)
```

### Android Services Architecture

Per the design note: speech and translation run as separate bound Android Services. This decouples them from the UI lifecycle and enables future offload to a local AI server (home network) while keeping the same UI and workflow.

```
MainActivity (Compose UI)
  ├── TranscriptionService (foreground service)
  │     └── SpeechRecognizer impl
  └── TranslationService (foreground service)
        └── TranslationEngine impl
```

### Folder Structure (target)

```
app/
  src/
    main/
      kotlin/com/arosys/meetingassistant/
        core/
          interfaces/          # SpeechRecognizer, TranslationEngine, TTSProvider, LLMProvider, StorageProvider
        services/
          TranscriptionService.kt
          TranslationService.kt
          TTSService.kt
          LLMService.kt
        models/                # Data classes: TranscriptEntry, MeetingRecord, ActionItem, etc.
        storage/
          RoomStorageProvider.kt
          MeetingDao.kt
          AppDatabase.kt
        ui/
          screens/
            LiveMeetingScreen.kt
            PostMeetingScreen.kt
            KnowledgeBaseScreen.kt
            SettingsScreen.kt
          components/          # Reusable Compose components
          theme/
        impl/
          whisper/             # WhisperSpeechRecognizer
          nllb/                # NLLBTranslationEngine
          piper/               # PiperTTSProvider
          qwen/                # Qwen3LLMProvider
        utils/
      res/
    test/
    androidTest/
  build.gradle.kts
build.gradle.kts
settings.gradle.kts
models/                        # Downloaded quantized model files (gitignored)
```

---

## Phase Status Dashboard

| Phase | Name | Status | Started | Completed | Notes |
|-------|------|--------|---------|-----------|-------|
| 1 | Live Spanish Transcription | **Not Started** | — | — | |
| 2 | Live English Translation | **Not Started** | — | — | Depends on Phase 1 |
| 3 | Earbud Assistance Mode | **Not Started** | — | — | Depends on Phase 2 |
| 4 | AI Meeting Intelligence | **Not Started** | — | — | Depends on Phase 3 |
| 5 | Smart Real-Time Filtering | **Not Started** | — | — | Depends on Phase 4 |
| 6 | Conversation Copilot | **Not Started** | — | — | Depends on Phase 5 |
| 7 | Knowledge Base | **Not Started** | — | — | Can start after Phase 1 |

---

## Phase 1 — Live Spanish Transcription

**Status:** Not Started
**Objective:** Working app that continuously transcribes Spanish speech to screen.

### Features
- [ ] Start/stop listening button
- [ ] Streaming microphone input
- [ ] Real-time transcription display (rolling)
- [ ] Timestamps on transcript entries
- [ ] Automatic transcript saving to Room database

### UI Layout
```
┌─────────────────────────┐
│ Connection Status        │
│ Model Status (Whisper)   │
├─────────────────────────┤
│                         │
│   Live Transcript        │
│   (scrolling)            │
│                         │
├─────────────────────────┤
│  [Start]  [Stop]  [Save] │
└─────────────────────────┘
```

### Architecture Diagram
```
Microphone → AudioRecord buffer
  → TranscriptionService (foreground)
    → WhisperSpeechRecognizer.streamAudio(chunk)
      → whisper.cpp JNI bridge
        → partial transcript callback
          → LiveMeetingScreen (Compose StateFlow)
            → Rolling transcript UI
              → RoomStorageProvider.saveEntry()
```

### Deliverables Checklist
- [ ] `SpeechRecognizer` interface defined
- [ ] `StorageProvider` interface defined
- [ ] `TranscriptEntry` data model
- [ ] `TranscriptionService` (foreground, survives screen off)
- [ ] `WhisperSpeechRecognizer` with JNI bridge to whisper.cpp
- [ ] `RoomStorageProvider` + `MeetingDao` + `AppDatabase`
- [ ] `LiveMeetingScreen` Compose UI
- [ ] JNI/CMake build config for whisper.cpp
- [ ] `models/` download script or README
- [ ] Unit tests: `WhisperSpeechRecognizerTest`, `RoomStorageProviderTest`
- [ ] Integration test: full audio → transcript → DB round trip
- [ ] Build instructions documented

### Success Criteria
User places phone on table → live Spanish transcription appears on screen with < 2s latency.

### Performance Benchmarks
| Metric | Target | Actual |
|--------|--------|--------|
| First token latency | < 2s | — |
| Sustained throughput | Real-time (≥ 1× audio speed) | — |
| RAM usage (idle) | < 500 MB | — |
| RAM usage (active) | < 1.5 GB | — |
| Battery drain per hour | < 15% | — |

### Migration to Phase 2
- `TranslationEngine` interface added alongside `SpeechRecognizer`
- `TranslationService` started; wired to `TranscriptionService` output
- UI updated to show parallel Spanish/English columns

---

## Phase 2 — Live English Translation

**Status:** Not Started
**Depends on:** Phase 1 complete and benchmarked
**Objective:** Streaming Spanish → English translation with < 2s latency.

### Features
- [ ] Streaming translation (does not wait for complete sentences)
- [ ] Parallel display: Spanish (top) / English (bottom)
- [ ] Translation updates continuously as new words arrive

### UI Layout
```
┌─────────────────────────┐
│  [ES] Necesitamos        │
│       revisar el         │
│       cronograma.        │
├─────────────────────────┤
│  [EN] We need to         │
│       review the         │
│       schedule.          │
├─────────────────────────┤
│  [Start]  [Stop]  [Save] │
└─────────────────────────┘
```

### Architecture Diagram
```
TranscriptionService → partial Spanish text
  → TranslationService (foreground)
    → NLLBTranslationEngine.translate(partialText, "spa", "eng")
      → NLLB distilled model (local)
        → partial English text callback
          → LiveMeetingScreen (dual StateFlow)
```

### Deliverables Checklist
- [ ] `TranslationEngine` interface defined
- [ ] `TranslationService` implementation
- [ ] `NLLBTranslationEngine` with NLLB distilled model
- [ ] Updated `LiveMeetingScreen` with dual-pane layout
- [ ] Translation result stored alongside transcript in Room
- [ ] Unit tests: `NLLBTranslationEngineTest`
- [ ] End-to-end latency test (audio in → English text on screen)
- [ ] Build instructions updated

### Success Criteria
Live translated English captions appear with < 2 seconds delay after Spanish is spoken.

### Performance Benchmarks
| Metric | Target | Actual |
|--------|--------|--------|
| Translation latency (per chunk) | < 1.5s | — |
| End-to-end (speech → English) | < 2s | — |
| RAM delta over Phase 1 | < 500 MB | — |
| Battery drain per hour | < 20% | — |

### Migration to Phase 3
- Bluetooth audio routing implemented
- `TTSProvider` interface added
- `TTSService` started
- Settings screen with Mode A/B/C selector added

---

## Phase 3 — Earbud Assistance Mode

**Status:** Not Started
**Depends on:** Phase 2 complete and benchmarked
**Objective:** Discreet audio assistance via single Bluetooth earbud.

### Audio Modes

| Mode | Name | Behavior |
|------|------|----------|
| A | Text Only | No audio output; display only |
| B | Sentence Playback | Speak each translated sentence after completion |
| C | Priority Playback | Speak only: decisions, deadlines, action items, questions directed at user, financial figures, dates |

### Architecture Diagram
```
TranslationService → translated sentence
  → TTSService (foreground)
    → ModeSelector (A/B/C)
      Mode B → PiperTTSProvider.speak(sentence)
      Mode C → PriorityClassifier.classify(sentence)
              → if priority → PiperTTSProvider.speak(summary)
    → BluetoothAudioRouter → BT earpiece
    → fallback → AndroidNativeTTSProvider
```

### Deliverables Checklist
- [ ] `TTSProvider` interface defined
- [ ] `PiperTTSProvider` implementation
- [ ] `AndroidNativeTTSProvider` fallback
- [ ] `TTSService` (foreground, handles BT routing)
- [ ] Bluetooth audio routing (SCO profile)
- [ ] Mode A/B/C selector (persisted in preferences)
- [ ] `PriorityClassifier` (lightweight keyword + pattern matching for Mode C; no LLM in Phase 3)
- [ ] Settings screen
- [ ] Unit tests: `PriorityClassifierTest`, `TTSServiceTest`
- [ ] BT integration test on real device
- [ ] Build instructions updated

### Success Criteria
User can follow a meeting using a single Bluetooth earbud with no need to look at the phone.

### Performance Benchmarks
| Metric | Target | Actual |
|--------|--------|--------|
| TTS synthesis latency | < 500ms | — |
| BT audio routing overhead | < 200ms | — |
| Mode C false positive rate | < 10% | — |
| Battery drain per hour | < 25% | — |

### Migration to Phase 4
- `LLMProvider` interface added
- `LLMService` started
- Post-meeting analysis screen scaffolded
- Qwen3 4B model integrated

---

## Phase 4 — AI Meeting Intelligence

**Status:** Not Started
**Depends on:** Phase 3 complete and benchmarked
**Objective:** Post-meeting LLM-powered analysis of full transcript.

### Output Format
```
Summary
  • Supplier delayed shipment.
  • Q3 budget increase approved.

Action Items
  • John to review proposal by Friday.
  • Maria to send updated timeline EOD Monday.

Decisions
  • Approved $15,000 contingency budget.

Risks
  • Possible two-week schedule slip if supplier misses revised date.

Open Questions
  • Who owns the vendor communication going forward?
```

### Architecture Diagram
```
Meeting ends → user taps "Analyze"
  → LLMService
    → Qwen3LLMProvider.analyze(fullTranscript)
      → structured prompt → Qwen3 4B (local, quantized)
        → JSON response (summary, actions, decisions, risks, questions)
          → PostMeetingScreen (Compose)
            → RoomStorageProvider.saveMeetingAnalysis()
```

### Deliverables Checklist
- [ ] `LLMProvider` interface defined
- [ ] `LLMService` implementation
- [ ] `Qwen3LLMProvider` (GGUF quantized via llama.cpp JNI)
- [ ] Structured prompt templates for meeting analysis
- [ ] `MeetingAnalysis` data model (Room entity)
- [ ] `PostMeetingScreen` Compose UI
- [ ] Export: plain text and JSON
- [ ] Unit tests: prompt template tests, JSON parsing tests
- [ ] Integration test: transcript in → structured analysis out
- [ ] Build instructions updated

### Success Criteria
After meeting ends, user receives accurate summary, action items, decisions, risks, and open questions within 60 seconds.

### Performance Benchmarks
| Metric | Target | Actual |
|--------|--------|--------|
| Analysis generation time | < 60s for 1-hr meeting | — |
| RAM during LLM inference | < 3 GB | — |
| Output accuracy (manual review) | > 80% relevant items captured | — |

### Migration to Phase 5
- Real-time LLM pipeline added (async, non-blocking)
- Alert overlay component added to `LiveMeetingScreen`
- Alert data model added

---

## Phase 5 — Smart Real-Time Filtering

**Status:** Not Started
**Depends on:** Phase 4 complete and benchmarked
**Objective:** Asynchronous real-time alerts for high-value information. Must not increase live translation latency.

### Detected Categories
- Action items
- Commitments
- Risks
- Dates / deadlines
- Costs / budget figures
- Questions directed at user

### Alert Examples
```
⚡ Action item assigned to you.
📅 Deadline mentioned: Friday.
💰 Budget discussed: $15,000.
❓ Question directed at you.
```

### Architecture Diagram
```
TranslationService → translated chunk
  → RealTimeFilterService (async, does not block translation)
    → Qwen3LLMProvider.classify(recentWindow)
      → AlertEvent (category, snippet, timestamp)
        → AlertOverlay on LiveMeetingScreen
          → (Mode C) → TTSService priority queue
```

### Deliverables Checklist
- [ ] `RealTimeFilterService` (async coroutine pipeline, non-blocking)
- [ ] Sliding window buffer (configurable; default: last 60 seconds)
- [ ] Alert classification prompt templates
- [ ] `AlertEvent` data model
- [ ] Alert overlay component in `LiveMeetingScreen`
- [ ] Latency regression test: Phase 2 translation latency must not increase
- [ ] Unit tests: classification accuracy tests
- [ ] Build instructions updated

### Success Criteria
Alerts appear within 3 seconds of the relevant content being spoken. Live translation latency unchanged from Phase 2 baseline.

### Performance Benchmarks
| Metric | Target | Actual |
|--------|--------|--------|
| Alert latency (speech → alert) | < 3s | — |
| Translation latency regression | 0ms | — |
| Alert false positive rate | < 15% | — |

### Migration to Phase 6
- Copilot query interface added
- Query handler wired to `LLMService`
- Quick-action button bar added to UI

---

## Phase 6 — Conversation Copilot

**Status:** Not Started
**Depends on:** Phase 5 complete and benchmarked
**Objective:** On-demand AI answers from the live transcript.

### Quick Actions
| Button | LLM Prompt |
|--------|-----------|
| Summarize last 2 min | Rolling 2-minute window summary |
| What did I miss? | Since last user interaction, what happened? |
| What are they asking for? | Extract the request/ask from recent speech |
| What decisions were made? | List decisions in last N minutes |
| Suggested response | Draft a Spanish response to the current context |

### Example Interaction
```
User taps: "What are they asking for?"

AI returns:
"They want confirmation that the project will be
 delivered before August 15."
```

### Architecture Diagram
```
User taps quick-action button
  → CopilotQueryHandler
    → LLMService.query(action, transcriptWindow)
      → Qwen3LLMProvider
        → CopilotResponseSheet (Compose bottom sheet)
```

### Deliverables Checklist
- [ ] `CopilotQueryHandler`
- [ ] Quick-action prompt templates for each button
- [ ] Copilot response bottom sheet UI
- [ ] Response latency < 5s on Galaxy Fold
- [ ] Unit tests: prompt rendering, response parsing
- [ ] Build instructions updated

### Success Criteria
Each quick-action returns a useful, accurate answer within 5 seconds.

### Performance Benchmarks
| Metric | Target | Actual |
|--------|--------|--------|
| Copilot response latency | < 5s | — |
| Response relevance (manual review) | > 85% rated useful | — |

### Migration to Phase 7
- `MeetingRecord` fully populated (audio path, transcript, translation, analysis, alerts)
- Search index built over Room database
- `KnowledgeBaseScreen` added

---

## Phase 7 — Knowledge Base

**Status:** Not Started
**Depends on:** Phase 6 complete and benchmarked
**Objective:** Searchable organizational memory of all meetings.

### Stored Per Meeting
- Audio recording (compressed)
- Full transcript (Spanish)
- Full translation (English)
- AI summary
- Action items
- Decisions
- Alerts log

### Search Examples
```
"supplier delays"         → all meetings mentioning supplier delays
"project Colibri"         → discussions involving project Colibri
"action items for John"   → all action items assigned to John
"budget > $10,000"        → financial discussions over $10k
```

### Architecture Diagram
```
KnowledgeBaseScreen
  → SearchQuery
    → StorageProvider.search(query, filters)
      → Room FTS (full-text search) on TranscriptEntry + MeetingAnalysis
        → SearchResultScreen
          → tap result → MeetingDetailScreen
```

### Deliverables Checklist
- [ ] Room FTS4/FTS5 configuration
- [ ] `SearchQuery` / `SearchFilter` data models
- [ ] `KnowledgeBaseScreen` Compose UI
- [ ] `MeetingDetailScreen` (audio playback, full transcript, analysis)
- [ ] Export: PDF / share sheet
- [ ] Unit tests: search accuracy tests
- [ ] Build instructions updated

### Success Criteria
User can search any meeting by topic, person, keyword, or date and find relevant results in < 1 second.

### Performance Benchmarks
| Metric | Target | Actual |
|--------|--------|--------|
| Search latency | < 1s | — |
| Search relevance (manual review) | > 90% relevant results | — |
| Storage per hour of meeting | < 200 MB | — |

---

## Decisions Log

Decisions that affect architecture, technology choices, or approach. Record here when a significant decision is made.

| Date | Decision | Rationale | Alternatives Considered |
|------|----------|-----------|------------------------|
| 2026-06-06 | Speech + translation as separate Android Services | Decouples from UI lifecycle; enables future offload to home AI server | Single in-process pipeline (rejected: too tightly coupled) |
| 2026-06-06 | Whisper.cpp for speech recognition | Proven streaming performance, JNI-friendly, offline | Android Speech Recognition API (rejected: requires internet), Vosk (future fallback candidate) |
| 2026-06-06 | NLLB distilled for translation | Strong Spanish↔English quality, distilled fits in device RAM | MarianMT (smaller but lower quality), cloud APIs (rejected: offline requirement) |
| 2026-06-06 | Piper TTS for earbud output | Natural voice, fast inference, fully offline | eSpeak (rejected: robotic), cloud TTS (rejected: latency + offline) |
| 2026-06-06 | Qwen3 4B for LLM layer | Strong multilingual reasoning, 4B fits in RAM with quantization | Gemma 2B (less capable), Llama 3.1 8B (too large for base config) |
| 2026-06-06 | Phase 3 Mode C uses keyword/pattern matching, not LLM | LLM not yet integrated in Phase 3; avoids latency risk | LLM classification (deferred to Phase 5) |

---

## Performance Benchmarks

Running record of actual measurements per phase. Update after each phase is completed.

### Phase 1

| Metric | Target | Measured | Device | Date |
|--------|--------|----------|--------|------|
| First token latency | < 2s | — | — | — |
| Sustained throughput | Real-time | — | — | — |
| RAM (active) | < 1.5 GB | — | — | — |
| Battery / hour | < 15% | — | — | — |

### Phase 2

| Metric | Target | Measured | Device | Date |
|--------|--------|----------|--------|------|
| End-to-end latency | < 2s | — | — | — |
| RAM delta | < 500 MB | — | — | — |
| Battery / hour | < 20% | — | — | — |

### Phase 3

| Metric | Target | Measured | Device | Date |
|--------|--------|----------|--------|------|
| TTS synthesis | < 500ms | — | — | — |
| BT routing overhead | < 200ms | — | — | — |
| Battery / hour | < 25% | — | — | — |

*(Phases 4–7 benchmarks will be added as phases are completed.)*

---

## Known Issues & Risks

| # | Issue / Risk | Severity | Phase | Status | Notes |
|---|-------------|----------|-------|--------|-------|
| 1 | Whisper.cpp NLLB models may exceed Fold RAM during simultaneous inference | High | 2 | Open | Mitigation: quantize both; profile before combining |
| 2 | Bluetooth SCO audio routing latency on Android can be 200–400ms | Medium | 3 | Open | Test on actual BT earpiece early |
| 3 | NLLB may produce lower quality on partial/incomplete sentences | Medium | 2 | Open | May need sentence buffering heuristic |
| 4 | Qwen3 4B cold-start time on first inference | Medium | 4 | Open | Keep model warm in background service |
| 5 | whisper.cpp JNI build complexity with CMake | Low | 1 | Open | Plan extra build config time |
| 6 | Mode C priority classifier precision on business Spanish | Medium | 3 | Open | Build test corpus of Guatemala business speech |

---

## Stretch Goal — Interpreter Mode

**Status:** Not Started (post-Phase 7)

Speaker talks for ~30 seconds. Earbud automatically delivers:

```
"Supplier delay. Delivery expected next Tuesday. They need your approval."
```

Requirements:
- Automatic segmentation (detect natural speaker pauses)
- Compression via LLM: full translation → concise 1–2 sentence summary
- Delivered via Piper TTS to earbud without manual trigger
- Must not disrupt real-time translation pipeline

Architecture sketch:
```
TranscriptionService → SpeakerTurnDetector (pause threshold ~1.5s)
  → on turn end: LLMService.compress(turnTranscript)
    → compressed English summary
      → TTSService priority queue (jumps ahead of Mode B queue)
```

---

*This document is the canonical project state. Update it as each phase is started, completed, or when decisions change. Commit changes alongside the code they describe.*
