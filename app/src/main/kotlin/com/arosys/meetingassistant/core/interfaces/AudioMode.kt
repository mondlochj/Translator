package com.arosys.meetingassistant.core.interfaces

enum class AudioMode(val displayName: String, val description: String) {
    TEXT_ONLY(
        displayName = "Mode A — Text Only",
        description = "No audio output; transcript displayed on screen",
    ),
    ALL_SPEECH(
        displayName = "Mode B — All Speech",
        description = "Speak each English translation to Bluetooth earbud",
    ),
    PRIORITY_ONLY(
        displayName = "Mode C — Priority Only",
        description = "Speak only action items, deadlines, decisions, financial figures, and questions",
    ),
}
