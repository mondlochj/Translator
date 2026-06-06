package com.arosys.meetingassistant.impl.priority

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PriorityClassifierTest {

    private val classifier = PriorityClassifier()

    // -------------------------------------------------------------------------
    // QUESTION
    // -------------------------------------------------------------------------

    @Test
    fun `direct question with usted returns QUESTION`() {
        val result = classifier.classify("¿Puede usted confirmar la entrega?")
        assertNotNull(result)
        assertEquals(PriorityCategory.QUESTION, result!!.category)
    }

    @Test
    fun `question mark without directed keyword returns null`() {
        val result = classifier.classify("¿Qué hora es?")
        assertNull(result)
    }

    @Test
    fun `question with sabe directed keyword returns QUESTION`() {
        val result = classifier.classify("¿Sabe cómo proceder con el contrato?")
        assertNotNull(result)
        assertEquals(PriorityCategory.QUESTION, result!!.category)
    }

    @Test
    fun `question with podría directed keyword returns QUESTION`() {
        val result = classifier.classify("¿Podría revisar los números?")
        assertNotNull(result)
        assertEquals(PriorityCategory.QUESTION, result!!.category)
    }

    @Test
    fun `inverted question mark alone with directed keyword returns QUESTION`() {
        val result = classifier.classify("¿Puede confirmar?")
        assertNotNull(result)
        assertEquals(PriorityCategory.QUESTION, result!!.category)
    }

    // -------------------------------------------------------------------------
    // ACTION_ITEM
    // -------------------------------------------------------------------------

    @Test
    fun `necesita triggers ACTION_ITEM`() {
        val result = classifier.classify("Necesita revisar el contrato antes del jueves.")
        assertNotNull(result)
        assertEquals(PriorityCategory.ACTION_ITEM, result!!.category)
    }

    @Test
    fun `hay que triggers ACTION_ITEM`() {
        val result = classifier.classify("Hay que actualizar el informe de ventas.")
        assertNotNull(result)
        assertEquals(PriorityCategory.ACTION_ITEM, result!!.category)
    }

    @Test
    fun `tiene que triggers ACTION_ITEM`() {
        val result = classifier.classify("Tiene que enviar el documento hoy.")
        assertNotNull(result)
        assertEquals(PriorityCategory.ACTION_ITEM, result!!.category)
    }

    @Test
    fun `por favor triggers ACTION_ITEM`() {
        val result = classifier.classify("Por favor envía la cotización al cliente.")
        assertNotNull(result)
        assertEquals(PriorityCategory.ACTION_ITEM, result!!.category)
    }

    @Test
    fun `vamos a triggers ACTION_ITEM`() {
        val result = classifier.classify("Vamos a coordinar la logística del evento.")
        assertNotNull(result)
        assertEquals(PriorityCategory.ACTION_ITEM, result!!.category)
    }

    @Test
    fun `quedamos en triggers ACTION_ITEM`() {
        val result = classifier.classify("Quedamos en enviar el reporte el lunes.")
        assertNotNull(result)
        assertEquals(PriorityCategory.ACTION_ITEM, result!!.category)
    }

    // -------------------------------------------------------------------------
    // DEADLINE
    // -------------------------------------------------------------------------

    @Test
    fun `mañana triggers DEADLINE`() {
        val result = classifier.classify("Necesitamos entregar esto mañana.")
        // ACTION_ITEM "necesitamos" — note: keyword is "necesita " (with space), not "necesitamos"
        // So this may return DEADLINE. Let me verify: "necesita " with trailing space.
        // "necesitamos" does NOT contain "necesita " (it's "necesita" + "mos")...
        // Actually "necesitamos".contains("necesita ") = false (no trailing space after 'a')
        // Therefore: ACTION_ITEM check fails, DEADLINE check passes for "mañana".
        assertNotNull(result)
        assertEquals(PriorityCategory.DEADLINE, result!!.category)
    }

    @Test
    fun `viernes triggers DEADLINE`() {
        val result = classifier.classify("La reunión de seguimiento es el viernes.")
        assertNotNull(result)
        assertEquals(PriorityCategory.DEADLINE, result!!.category)
    }

    @Test
    fun `fecha límite triggers DEADLINE`() {
        val result = classifier.classify("La fecha límite es el treinta de junio.")
        assertNotNull(result)
        assertEquals(PriorityCategory.DEADLINE, result!!.category)
    }

    @Test
    fun `próxima semana triggers DEADLINE`() {
        val result = classifier.classify("Lo presentamos la próxima semana.")
        assertNotNull(result)
        assertEquals(PriorityCategory.DEADLINE, result!!.category)
    }

    @Test
    fun `month name triggers DEADLINE`() {
        val result = classifier.classify("El contrato vence en octubre.")
        assertNotNull(result)
        assertEquals(PriorityCategory.DEADLINE, result!!.category)
    }

    // -------------------------------------------------------------------------
    // FINANCIAL
    // -------------------------------------------------------------------------

    @Test
    fun `presupuesto triggers FINANCIAL`() {
        val result = classifier.classify("El presupuesto asignado es suficiente.")
        assertNotNull(result)
        assertEquals(PriorityCategory.FINANCIAL, result!!.category)
    }

    @Test
    fun `dollar sign regex triggers FINANCIAL`() {
        val result = classifier.classify("Aprobamos un gasto de $15,000.")
        assertNotNull(result)
        assertEquals(PriorityCategory.FINANCIAL, result!!.category)
    }

    @Test
    fun `dólares keyword triggers FINANCIAL`() {
        val result = classifier.classify("Son dos mil dólares adicionales al plan.")
        assertNotNull(result)
        assertEquals(PriorityCategory.FINANCIAL, result!!.category)
    }

    @Test
    fun `quetzales keyword triggers FINANCIAL`() {
        val result = classifier.classify("El pago es de cincuenta quetzales por unidad.")
        assertNotNull(result)
        assertEquals(PriorityCategory.FINANCIAL, result!!.category)
    }

    @Test
    fun `numeric dólares pattern triggers FINANCIAL`() {
        val result = classifier.classify("El costo es de 5000 dólares por mes.")
        assertNotNull(result)
        assertEquals(PriorityCategory.FINANCIAL, result!!.category)
    }

    // -------------------------------------------------------------------------
    // DECISION
    // -------------------------------------------------------------------------

    @Test
    fun `decidimos triggers DECISION`() {
        val result = classifier.classify("Analizamos todo y decidimos cambiar al proveedor.")
        assertNotNull(result)
        assertEquals(PriorityCategory.DECISION, result!!.category)
    }

    @Test
    fun `aprobado triggers DECISION`() {
        val result = classifier.classify("El plan está aprobado por la directiva.")
        assertNotNull(result)
        assertEquals(PriorityCategory.DECISION, result!!.category)
    }

    @Test
    fun `está decidido triggers DECISION`() {
        val result = classifier.classify("Está decidido, vamos a cambiar el proveedor.")
        assertNotNull(result)
        assertEquals(PriorityCategory.DECISION, result!!.category)
    }

    @Test
    fun `confirmamos triggers DECISION`() {
        val result = classifier.classify("Confirmamos la estrategia para el próximo trimestre.")
        // "próximo" is not in DEADLINE keywords (only "próxima semana", "próximo lunes", etc.)
        // Let's verify: "próximo trimestre" — "próxima semana"? No. "próximo lunes"? No.
        // "trimestre" is not in deadline keywords. So DEADLINE won't fire.
        assertNotNull(result)
        assertEquals(PriorityCategory.DECISION, result!!.category)
    }

    // -------------------------------------------------------------------------
    // PRECEDENCE
    // -------------------------------------------------------------------------

    @Test
    fun `question beats action item when both present`() {
        val result = classifier.classify("¿Puede usted revisar? Tiene que enviar el informe.")
        assertNotNull(result)
        assertEquals(PriorityCategory.QUESTION, result!!.category)
    }

    @Test
    fun `action item beats deadline when both present`() {
        val result = classifier.classify("Tiene que entregar esto el viernes.")
        assertNotNull(result)
        assertEquals(PriorityCategory.ACTION_ITEM, result!!.category)
    }

    @Test
    fun `deadline beats financial when both present`() {
        // "plazo" is a DEADLINE keyword; "factura" is a FINANCIAL keyword
        val result = classifier.classify("El plazo para pagar la factura ya pasó.")
        assertNotNull(result)
        assertEquals(PriorityCategory.DEADLINE, result!!.category)
    }

    @Test
    fun `financial beats decision when both present`() {
        val result = classifier.classify("Decidimos aprobar el presupuesto extra.")
        assertNotNull(result)
        assertEquals(PriorityCategory.FINANCIAL, result!!.category)
    }

    // -------------------------------------------------------------------------
    // NO-MATCH
    // -------------------------------------------------------------------------

    @Test
    fun `ordinary statement returns null`() {
        val result = classifier.classify("El equipo está trabajando bien en el proyecto.")
        assertNull(result)
    }

    @Test
    fun `empty string returns null`() {
        val result = classifier.classify("")
        assertNull(result)
    }

    // -------------------------------------------------------------------------
    // SNIPPET
    // -------------------------------------------------------------------------

    @Test
    fun `snippet is truncated to 80 chars for long input`() {
        val long = "Necesita revisar el contrato. " + "A".repeat(100)
        val result = classifier.classify(long)
        assertNotNull(result)
        assertEquals(80, result!!.snippet.length)
    }

    @Test
    fun `snippet preserves full text when shorter than 80 chars`() {
        val short = "Necesita enviar el reporte."
        val result = classifier.classify(short)
        assertNotNull(result)
        assertEquals(short, result!!.snippet)
    }
}
