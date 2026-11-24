package com.example.hy_mt2translator

/** Quanti token al massimo può occupare il prompt della chat (impostazioni + scambi + testo nuovo). */
const val PROMPT_TOKEN_BUDGET = 800

/** Lunghezza massima di una singola traduzione generata in chat. */
const val CHAT_MAX_OUTPUT_TOKENS = 384

/** Stima dei token che il modello aggiunge da solo con il suo modello di chat. */
private const val TEMPLATE_OVERHEAD_TOKENS = 40

/** Uno scambio della chat: il messaggio dell'utente e la sua traduzione. */
data class ChatTurn(val source: String, val translation: String)

/** Un termine del glossario: come si scrive nella tua lingua e come va tradotto. */
data class GlossaryEntry(val source: String, val target: String)

/** Le impostazioni fisse della chat. */
data class ChatSettings(
    val target: Language,
    val tone: Tone,
    val context: String,
    val glossary: List<GlossaryEntry>,
    val instructions: List<String>
)

/** Il prompt pronto, con la posizione da cui parte la finestra e i token usati. */
data class BuiltPrompt(val prompt: String, val startIndex: Int, val tokens: Int)

/**
 * Costruisce il prompt completo per un messaggio della chat.
 * L'ordine è pensato per riusare la cache del modello: prima le parti che cambiano
 * di rado (contesto, glossario, scambi precedenti), per ultime l'istruzione e il testo nuovo.
 */
fun buildChatPrompt(settings: ChatSettings, history: List<ChatTurn>, newText: String): String {
    val sb = StringBuilder()
    val hasBackground = settings.context.isNotBlank() || history.isNotEmpty()

    if (settings.context.isNotBlank()) {
        sb.append("[Background Information]\n")
        sb.append(settings.context.trim()).append("\n\n")
    }

    if (settings.glossary.isNotEmpty()) {
        sb.append("Reference the following translations:\n")
        for (entry in settings.glossary) {
            sb.append(entry.source.trim()).append(" translates to ").append(entry.target.trim()).append('\n')
        }
        sb.append('\n')
    }

    if (history.isNotEmpty()) {
        sb.append("Earlier messages of this conversation (original => translation):\n")
        for (turn in history) {
            sb.append(turn.source.trim()).append(" => ").append(turn.translation.trim()).append('\n')
        }
        sb.append('\n')
    }

    sb.append("Please translate the following text into ").append(settings.target.nameEn)
    if (hasBackground) {
        sb.append(", taking the provided background information and earlier messages into consideration")
    }
    sb.append('.')
    settings.tone.style?.let { style ->
        sb.append(" Note that the translation style must strictly conform to [").append(style).append("].")
    }
    if (settings.instructions.isNotEmpty()) {
        sb.append(" Also follow these instructions: ")
        sb.append(settings.instructions.joinToString("; ") { it.trim().trimEnd('.') })
        sb.append('.')
    }
    sb.append(" Only output the translated result without any additional explanation.\n\n")
    sb.append("[Source Text]\n").append(newText.trim())

    return sb.toString()
}

/**
 * Finestra scorrevole: se il prompt è troppo lungo, scarta gli scambi più vecchi.
 * Ne scarta un blocco alla volta (un quarto di quelli rimasti): così la cache del modello
 * si invalida di rado, invece che a ogni messaggio.
 *
 * @param startIndex da quale scambio parte la finestra (cresce soltanto)
 * @param countTokens funzione che conta i token di un testo (viene dal motore)
 */
suspend fun buildPromptWithinBudget(
    settings: ChatSettings,
    turns: List<ChatTurn>,
    startIndex: Int,
    newText: String,
    countTokens: suspend (String) -> Int
): BuiltPrompt {
    var start = startIndex.coerceIn(0, turns.size)
    while (true) {
        val prompt = buildChatPrompt(settings, turns.subList(start, turns.size), newText)
        val tokens = countTokens(prompt) + TEMPLATE_OVERHEAD_TOKENS
        if (tokens <= PROMPT_TOKEN_BUDGET || start >= turns.size) {
            return BuiltPrompt(prompt, start, tokens)
        }
        val remaining = turns.size - start
        start += maxOf(1, remaining / 4)
    }
}