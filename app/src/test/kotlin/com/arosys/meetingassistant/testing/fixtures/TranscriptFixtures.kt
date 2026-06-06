package com.arosys.meetingassistant.testing.fixtures

import com.arosys.meetingassistant.core.interfaces.TranscriptEntry

/**
 * Pre-built transcript segments covering common meeting scenarios.
 * Use these instead of ad-hoc strings so test intent is obvious.
 */
object TranscriptFixtures {

    // -------------------------------------------------------------------------
    // Raw Spanish sentences
    // -------------------------------------------------------------------------

    const val SCHEDULE_REVIEW_ES = "Necesitamos revisar el cronograma."
    const val SCHEDULE_REVIEW_EN = "We need to review the schedule."

    const val SUPPLIER_DELAY_ES = "El proveedor retrasó el envío hasta la próxima semana."
    const val SUPPLIER_DELAY_EN = "The supplier delayed the shipment until next week."

    const val ACTION_ITEM_ES = "Juan va a revisar la propuesta antes del viernes."
    const val ACTION_ITEM_EN = "Juan will review the proposal before Friday."

    const val BUDGET_ES = "El presupuesto de contingencia es de quince mil dólares."
    const val BUDGET_EN = "The contingency budget is fifteen thousand dollars."

    const val DEADLINE_ES = "La fecha límite es el quince de agosto."
    const val DEADLINE_EN = "The deadline is August 15th."

    const val QUESTION_ES = "¿Cuándo puede confirmar la entrega?"
    const val QUESTION_EN = "When can you confirm the delivery?"

    // -------------------------------------------------------------------------
    // Pre-built TranscriptEntry objects
    // -------------------------------------------------------------------------

    fun scheduleReview(meetingId: Long, timestampMs: Long = 1_000L) = TranscriptEntry(
        meetingId = meetingId,
        timestampMs = timestampMs,
        spanishText = SCHEDULE_REVIEW_ES,
        englishText = SCHEDULE_REVIEW_EN,
    )

    fun supplierDelay(meetingId: Long, timestampMs: Long = 2_000L) = TranscriptEntry(
        meetingId = meetingId,
        timestampMs = timestampMs,
        spanishText = SUPPLIER_DELAY_ES,
        englishText = SUPPLIER_DELAY_EN,
    )

    fun actionItem(meetingId: Long, timestampMs: Long = 3_000L) = TranscriptEntry(
        meetingId = meetingId,
        timestampMs = timestampMs,
        spanishText = ACTION_ITEM_ES,
        englishText = ACTION_ITEM_EN,
    )

    fun budget(meetingId: Long, timestampMs: Long = 4_000L) = TranscriptEntry(
        meetingId = meetingId,
        timestampMs = timestampMs,
        spanishText = BUDGET_ES,
        englishText = BUDGET_EN,
    )

    /** A realistic 5-sentence meeting excerpt for use in post-meeting analysis tests. */
    fun fullMeetingExcerpt(meetingId: Long): List<TranscriptEntry> = listOf(
        scheduleReview(meetingId, 1_000L),
        supplierDelay(meetingId, 15_000L),
        actionItem(meetingId, 32_000L),
        budget(meetingId, 48_000L),
        TranscriptEntry(
            meetingId = meetingId,
            timestampMs = 61_000L,
            spanishText = DEADLINE_ES,
            englishText = DEADLINE_EN,
        ),
    )

    /** Full Spanish transcript as a single string (for LLM prompt tests). */
    fun fullTranscriptText(meetingId: Long): String =
        fullMeetingExcerpt(meetingId).joinToString("\n") { it.spanishText }
}
