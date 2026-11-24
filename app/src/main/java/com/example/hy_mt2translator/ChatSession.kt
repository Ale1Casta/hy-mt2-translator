package com.example.hy_mt2translator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

/** Una voce nella conversazione: uno scambio tradotto oppure un'istruzione data al modello. */
sealed interface ChatEntry {
    data class Turn(val source: String, val translation: String) : ChatEntry
    data class Instruction(val text: String) : ChatEntry
}

/**
 * Lo stato di una chat. È una classe a parte, così la schermata della conversazione
 * e quella delle impostazioni leggono e modificano gli stessi dati,
 * e si può salvare e ricaricare (toRecord / fromRecord).
 */
class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis()
) {
    val entries = mutableStateListOf<ChatEntry>()

    var targetName by mutableStateOf(DEFAULT_LANGUAGE.nameEn)
    var toneId by mutableStateOf(DEFAULT_TONE.id)
    var context by mutableStateOf("")
    val glossary = mutableStateListOf<GlossaryEntry>()
    val instructions = mutableStateListOf<String>()

    /** Da quale scambio parte la finestra del prompt (vedi buildPromptWithinBudget). */
    var windowStart by mutableIntStateOf(0)

    /** Token del prompt dell'ultimo messaggio, per la barra della memoria. */
    var promptTokens by mutableIntStateOf(0)

    /** Durata dell'ultimo messaggio, in secondi (non viene salvata). */
    var lastSeconds by mutableStateOf<Double?>(null)

    var lastUsedAt by mutableStateOf(createdAt)

    /** Dopo quanti giorni senza usarla la chat viene eliminata. */
    var expiryDays by mutableIntStateOf(CHAT_EXPIRY_DAYS)

    val target: Language get() = languageFromEnglishName(targetName)
    val tone: Tone get() = toneFromId(toneId)

    /** Titolo ricavato dal primo messaggio. */
    val displayTitle: String
        get() {
            val first = entries.firstNotNullOfOrNull { entry ->
                when (entry) {
                    is ChatEntry.Turn -> entry.source
                    is ChatEntry.Instruction -> null
                }
            } ?: entries.firstNotNullOfOrNull { (it as? ChatEntry.Instruction)?.text }
            ?: return "Nuova chat"
            val clean = first.trim().replace('\n', ' ')
            return if (clean.length > 32) clean.take(32).trimEnd() + "…" else clean
        }

    /** Una chat ancora vuota e senza impostazioni non si salva: non deve comparire nell'elenco. */
    val hasContent: Boolean
        get() = entries.isNotEmpty() || context.isNotBlank() ||
                glossary.isNotEmpty() || instructions.isNotEmpty()

    fun settings() = ChatSettings(
        target = target,
        tone = tone,
        context = context,
        glossary = glossary.toList(),
        instructions = instructions.toList()
    )

    /** Gli scambi tradotti, senza le istruzioni: sono quelli che finiscono nel prompt. */
    fun turns(): List<ChatTurn> =
        entries.filterIsInstance<ChatEntry.Turn>().map { ChatTurn(it.source, it.translation) }

    /** Riassunto delle impostazioni fisse, per il pulsante nella schermata della chat. */
    fun summary(): String {
        val parts = mutableListOf<String>()
        if (tone.style != null) parts.add(tone.labelIt.lowercase())
        if (glossary.isNotEmpty()) {
            parts.add(if (glossary.size == 1) "1 termine" else "${glossary.size} termini")
        }
        if (context.isNotBlank()) parts.add("contesto")
        if (instructions.isNotEmpty()) {
            parts.add(if (instructions.size == 1) "1 istruzione" else "${instructions.size} istruzioni")
        }
        return if (parts.isEmpty()) "Impostazioni fisse: nessuna" else "Fisse: " + parts.joinToString(" · ")
    }

    /** Fotografia della chat da scrivere su disco. */
    fun toRecord() = ChatRecord(
        id = id,
        title = displayTitle,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt,
        targetName = targetName,
        toneId = toneId,
        context = context,
        glossary = glossary.toList(),
        instructions = instructions.toList(),
        entries = entries.toList(),
        windowStart = windowStart,
        promptTokens = promptTokens,
        expiryDays = expiryDays
    )

    companion object {
        /** Ricostruisce una chat da un file salvato. */
        fun fromRecord(record: ChatRecord): ChatSession {
            val session = ChatSession(id = record.id, createdAt = record.createdAt)
            session.lastUsedAt = record.lastUsedAt
            session.targetName = record.targetName
            session.toneId = record.toneId
            session.context = record.context
            session.glossary.addAll(record.glossary)
            session.instructions.addAll(record.instructions)
            session.entries.addAll(record.entries)
            session.windowStart = record.windowStart
            session.promptTokens = record.promptTokens
            session.expiryDays = record.expiryDays
            return session
        }
    }
}