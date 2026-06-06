package com.arosys.meetingassistant.services

import com.arosys.meetingassistant.core.interfaces.AudioMode
import com.arosys.meetingassistant.impl.priority.PriorityClassifier
import com.arosys.meetingassistant.testing.MainDispatcherRule
import com.arosys.meetingassistant.testing.fakes.FakeBluetoothAudioRouter
import com.arosys.meetingassistant.testing.fakes.FakeTTSProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests the TTS mode-routing logic from [TTSService.startTts] without
 * requiring the Android Service lifecycle.
 *
 * The pipeline under test:
 *   [MeetingSession.translationCompleted] → mode filter → [FakeTTSProvider.speak]
 *
 * We simulate what TTSService's collect loop does by calling [routeToTts]
 * directly.  Bluetooth pre-connect behaviour is verified separately.
 */
class TTSServiceTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private lateinit var session: MeetingSession
    private lateinit var tts: FakeTTSProvider
    private lateinit var btRouter: FakeBluetoothAudioRouter
    private val classifier = PriorityClassifier()

    @Before
    fun setup() {
        session   = MeetingSession()
        tts       = FakeTTSProvider()
        btRouter  = FakeBluetoothAudioRouter()
    }

    // -------------------------------------------------------------------------
    // Helpers — mirrors TTSService.startTts() routing logic
    // -------------------------------------------------------------------------

    private suspend fun routeToTts(completed: CompletedTranslation, mode: AudioMode) {
        when (mode) {
            AudioMode.TEXT_ONLY -> { /* no audio */ }
            AudioMode.ALL_SPEECH -> tts.speak(completed.englishText)
            AudioMode.PRIORITY_ONLY -> {
                if (classifier.classify(completed.spanishText) != null) {
                    tts.speak(completed.englishText)
                }
            }
        }
    }

    private fun completedTranslation(
        spanish: String,
        english: String,
        meetingId: Long = 1L,
    ) = CompletedTranslation(
        entryId     = System.nanoTime(),
        spanishText = spanish,
        englishText = english,
        meetingId   = meetingId,
    )

    // -------------------------------------------------------------------------
    // Mode A — TEXT_ONLY
    // -------------------------------------------------------------------------

    @Test
    fun `mode A does not speak any translation`() = runTest {
        val ct = completedTranslation(
            spanish = "Necesitamos revisar el cronograma.",
            english = "We need to review the schedule.",
        )
        routeToTts(ct, AudioMode.TEXT_ONLY)

        assertTrue(tts.spokenUtterances.isEmpty())
    }

    @Test
    fun `mode A discards priority item without speaking`() = runTest {
        val ct = completedTranslation(
            spanish = "¿Puede usted confirmar la entrega?",
            english = "Can you confirm the delivery?",
        )
        routeToTts(ct, AudioMode.TEXT_ONLY)

        assertTrue(tts.spokenUtterances.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Mode B — ALL_SPEECH
    // -------------------------------------------------------------------------

    @Test
    fun `mode B speaks every translation`() = runTest {
        val items = listOf(
            completedTranslation("Hola a todos.", "Hello everyone."),
            completedTranslation("Vamos a revisar el plan.", "Let us review the plan."),
            completedTranslation("Gracias por su tiempo.", "Thank you for your time."),
        )
        items.forEach { routeToTts(it, AudioMode.ALL_SPEECH) }

        assertEquals(3, tts.spokenUtterances.size)
        assertEquals("Hello everyone.", tts.spokenUtterances[0].text)
        assertEquals("Let us review the plan.", tts.spokenUtterances[1].text)
        assertEquals("Thank you for your time.", tts.spokenUtterances[2].text)
    }

    @Test
    fun `mode B speaks non-priority items that mode C would skip`() = runTest {
        val ct = completedTranslation(
            spanish = "La reunión fue productiva.",
            english = "The meeting was productive.",
        )
        routeToTts(ct, AudioMode.ALL_SPEECH)

        assertEquals(1, tts.spokenUtterances.size)
    }

    // -------------------------------------------------------------------------
    // Mode C — PRIORITY_ONLY
    // -------------------------------------------------------------------------

    @Test
    fun `mode C speaks classified action item`() = runTest {
        val ct = completedTranslation(
            spanish = "Necesita revisar el contrato antes del viernes.",
            english = "You need to review the contract before Friday.",
        )
        routeToTts(ct, AudioMode.PRIORITY_ONLY)

        assertEquals(1, tts.spokenUtterances.size)
        assertEquals("You need to review the contract before Friday.", tts.spokenUtterances[0].text)
    }

    @Test
    fun `mode C speaks classified question`() = runTest {
        val ct = completedTranslation(
            spanish = "¿Puede usted confirmar la entrega para mañana?",
            english = "Can you confirm the delivery for tomorrow?",
        )
        routeToTts(ct, AudioMode.PRIORITY_ONLY)

        assertEquals(1, tts.spokenUtterances.size)
    }

    @Test
    fun `mode C speaks classified financial item`() = runTest {
        val ct = completedTranslation(
            spanish = "El presupuesto total es de $50,000.",
            english = "The total budget is $50,000.",
        )
        routeToTts(ct, AudioMode.PRIORITY_ONLY)

        assertEquals(1, tts.spokenUtterances.size)
    }

    @Test
    fun `mode C skips ordinary non-priority statement`() = runTest {
        val ct = completedTranslation(
            spanish = "El equipo está trabajando bien.",
            english = "The team is working well.",
        )
        routeToTts(ct, AudioMode.PRIORITY_ONLY)

        assertTrue(tts.spokenUtterances.isEmpty())
    }

    @Test
    fun `mode C skips multiple non-priority statements`() = runTest {
        val items = listOf(
            completedTranslation("Bienvenidos a la reunión.", "Welcome to the meeting."),
            completedTranslation("Hoy revisamos el avance.", "Today we review the progress."),
            completedTranslation("El clima está bien.", "The weather is fine."),
        )
        items.forEach { routeToTts(it, AudioMode.PRIORITY_ONLY) }

        assertTrue(tts.spokenUtterances.isEmpty())
    }

    @Test
    fun `mode C speaks priority items and skips non-priority in sequence`() = runTest {
        val items = listOf(
            completedTranslation("Hola a todos.", "Hello everyone."),                        // skip
            completedTranslation("Necesita enviar el informe.", "You need to send the report."),  // speak
            completedTranslation("El tiempo es bueno.", "The weather is good."),             // skip
            completedTranslation("Decidimos proceder con el plan.", "We decided to proceed."),    // speak
        )
        items.forEach { routeToTts(it, AudioMode.PRIORITY_ONLY) }

        assertEquals(2, tts.spokenUtterances.size)
        assertEquals("You need to send the report.", tts.spokenUtterances[0].text)
        assertEquals("We decided to proceed.", tts.spokenUtterances[1].text)
    }

    // -------------------------------------------------------------------------
    // Bluetooth routing
    // -------------------------------------------------------------------------

    @Test
    fun `btRouter connect is called when connecting`() = runTest {
        btRouter.connect()
        assertEquals(1, btRouter.connectCount)
        assertTrue(btRouter.isConnected.value)
    }

    @Test
    fun `btRouter disconnect resets connected state`() = runTest {
        btRouter.connect()
        btRouter.disconnect()
        assertEquals(false, btRouter.isConnected.value)
        assertEquals(1, btRouter.disconnectCount)
    }

    @Test
    fun `btRouter returns false when unavailable`() = runTest {
        btRouter.shouldConnect = false
        val connected = btRouter.connect()
        assertEquals(false, connected)
        assertEquals(false, btRouter.isConnected.value)
    }

    // -------------------------------------------------------------------------
    // MeetingSession pipeline
    // -------------------------------------------------------------------------

    @Test
    fun `translationCompleted SharedFlow delivers to mode B collector`() = runTest {
        val spoken = mutableListOf<String>()

        val job = launch {
            session.translationCompleted.collect { completed ->
                routeToTts(completed, AudioMode.ALL_SPEECH)
                spoken += completed.englishText
            }
        }

        session.notifyTranslationCompleted(
            CompletedTranslation(1L, "Tiene que enviar.", "You must send.", 1L)
        )
        session.notifyTranslationCompleted(
            CompletedTranslation(2L, "Gracias.", "Thank you.", 1L)
        )

        // Let the coroutines process
        kotlinx.coroutines.test.advanceUntilIdle()
        job.cancel()

        assertEquals(2, spoken.size)
        assertEquals(2, tts.spokenUtterances.size)
    }

    @Test
    fun `session meetingId null stops service simulation`() = runTest {
        session.setMeetingId(1L)
        assertEquals(1L, session.currentMeetingId.value)
        session.setMeetingId(null)
        assertEquals(null, session.currentMeetingId.value)
    }
}
