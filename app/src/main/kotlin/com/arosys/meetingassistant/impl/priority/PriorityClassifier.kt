package com.arosys.meetingassistant.impl.priority

/**
 * Lightweight keyword and pattern classifier for Mode C (Priority Only).
 *
 * Operates on the original Spanish text (before translation) to detect
 * five high-value business categories without requiring an LLM.  The LLM
 * path (Phase 5) will supersede this once it's integrated.
 *
 * Precedence: QUESTION > ACTION_ITEM > DEADLINE > FINANCIAL > DECISION.
 * Returns the first matching category so the utterance is spoken once.
 */
class PriorityClassifier {

    fun classify(spanishText: String): PriorityEvent? {
        val lower = spanishText.lowercase()
        return checkQuestion(lower, spanishText)
            ?: checkActionItem(lower, spanishText)
            ?: checkDeadline(lower, spanishText)
            ?: checkFinancial(lower, spanishText)
            ?: checkDecision(lower, spanishText)
    }

    // -------------------------------------------------------------------------
    // Category checks
    // -------------------------------------------------------------------------

    private fun checkQuestion(lower: String, original: String): PriorityEvent? {
        if (!lower.contains('?') && !lower.contains('¿')) return null
        // Only surface questions directed at the listener
        val directed = listOf(
            "usted", "ustedes", "puede ", "podría", "cree ", "piensa",
            "sabe ", "quiere", "necesita", " tú ", " tu ",
        )
        return if (directed.any { lower.contains(it) }) {
            PriorityEvent(PriorityCategory.QUESTION, original.take(80))
        } else null
    }

    private fun checkActionItem(lower: String, original: String): PriorityEvent? {
        val patterns = listOf(
            "necesita ", "necesitan ", "tiene que", "tienen que",
            "va a ", "van a ", "debe ", "deben ", "hay que",
            "vamos a ", "por favor ", "se encarga", "queda pendiente",
            "quedamos en", "tiene pendiente",
        )
        return if (patterns.any { lower.contains(it) }) {
            PriorityEvent(PriorityCategory.ACTION_ITEM, original.take(80))
        } else null
    }

    private fun checkDeadline(lower: String, original: String): PriorityEvent? {
        val keywords = listOf(
            "mañana", "pasado mañana", "esta semana", "próxima semana",
            "próximo lunes", "próximo viernes", "el lunes", "el martes",
            "el miércoles", "el jueves", "el viernes",
            "lunes", "martes", "miércoles", "jueves", "viernes",
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
            "antes de", "para el ", "fecha límite", "plazo", "entrega",
            "vence", "vencimiento",
        )
        return if (keywords.any { lower.contains(it) }) {
            PriorityEvent(PriorityCategory.DEADLINE, original.take(80))
        } else null
    }

    private fun checkFinancial(lower: String, original: String): PriorityEvent? {
        val keywords = listOf(
            "presupuesto", "costo", "precio", "pagar", "factura",
            "cotización", "inversión", "dólares", "quetzales", "pesos",
            "mil dólares", "millones",
        )
        val numericPatterns = listOf(
            Regex("""\$[\d,]+"""),
            Regex("""\d[\d,]*\s*(dólares|quetzales|pesos|mil|millones)"""),
        )
        return if (keywords.any { lower.contains(it) } ||
            numericPatterns.any { it.containsMatchIn(lower) }
        ) {
            PriorityEvent(PriorityCategory.FINANCIAL, original.take(80))
        } else null
    }

    private fun checkDecision(lower: String, original: String): PriorityEvent? {
        val patterns = listOf(
            "decidimos", "decidió", "acordamos", "acordó",
            "aprobamos", "aprobó", "aprobado", "confirmamos",
            "confirmó", "autorizado", "está decidido", "queda decidido",
            "lo hacemos", "vamos a proceder", "procedemos",
        )
        return if (patterns.any { lower.contains(it) }) {
            PriorityEvent(PriorityCategory.DECISION, original.take(80))
        } else null
    }
}

// -------------------------------------------------------------------------
// Result types
// -------------------------------------------------------------------------

enum class PriorityCategory(val label: String) {
    ACTION_ITEM("Action item"),
    DEADLINE("Deadline"),
    FINANCIAL("Budget/financial"),
    QUESTION("Question for you"),
    DECISION("Decision made"),
}

data class PriorityEvent(
    val category: PriorityCategory,
    val snippet: String,
)
