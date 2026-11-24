package com.example.hy_mt2translator

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Dopo quanti giorni senza essere usata una chat viene eliminata (valore predefinito). */
const val CHAT_EXPIRY_DAYS = 7

/** Le durate tra cui si può scegliere nelle impostazioni di ogni chat. */
val EXPIRY_OPTIONS = listOf(1, 3, 7, 14, 30)

/** Testo di una durata, per esempio "7 giorni senza usarla". */
fun expiryLabel(days: Int): String =
    if (days == 1) "1 giorno senza usarla" else "$days giorni senza usarla"

private const val DAY_MS = 24L * 60 * 60 * 1000

/** Una chat così come viene salvata su disco. */
data class ChatRecord(
    val id: String,
    val title: String,
    val createdAt: Long,
    val lastUsedAt: Long,
    val targetName: String,
    val toneId: String,
    val context: String,
    val glossary: List<GlossaryEntry>,
    val instructions: List<String>,
    val entries: List<ChatEntry>,
    val windowStart: Int,
    val promptTokens: Int,
    val expiryDays: Int = CHAT_EXPIRY_DAYS
) {
    /** Lingua di destinazione, con il nome in italiano. */
    val languageNameIt: String get() = languageFromEnglishName(targetName).nameIt

    /** Bandiera della lingua di destinazione. */
    val languageFlag: String get() = languageFromEnglishName(targetName).flag

    /** Anteprima per l'elenco: l'ultimo messaggio scritto. */
    val previewText: String
        get() {
            val last = entries.lastOrNull() ?: return "Chat vuota"
            return when (last) {
                is ChatEntry.Turn -> last.source
                is ChatEntry.Instruction -> "Istruzione: ${last.text}"
            }
        }

    /** Giorni che mancano alla scadenza, arrotondati per eccesso (0 o meno: sta per scadere). */
    fun daysLeft(now: Long = System.currentTimeMillis()): Int {
        val remaining = lastUsedAt + expiryDays * DAY_MS - now
        return Math.ceil(remaining.toDouble() / DAY_MS).toInt()
    }
}

/**
 * Archivio delle chat: un file JSON per chat, nella memoria privata dell'app
 * (nessun'altra app può leggerlo e sparisce se disinstalli l'app).
 * Le funzioni toccano il disco: vanno chiamate fuori dal thread principale.
 */
class ChatStore(context: Context) {
    private val dir = File(context.filesDir, "chats").apply { mkdirs() }

    /** Tutte le chat salvate, dalla più recente. I file illeggibili vengono ignorati. */
    fun loadAll(): List<ChatRecord> =
        (dir.listFiles { file -> file.extension == "json" } ?: emptyArray())
            .mapNotNull { file -> runCatching { parse(file.readText()) }.getOrNull() }
            .sortedByDescending { it.lastUsedAt }

    /** Salva (o aggiorna) una chat. Scrive prima un file temporaneo, per non lasciare file a metà. */
    fun save(record: ChatRecord) {
        val target = File(dir, "${record.id}.json")
        val temp = File(dir, "${record.id}.json.tmp")
        temp.writeText(toJson(record).toString())
        if (!temp.renameTo(target)) {
            target.delete()
            temp.renameTo(target)
        }
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
    }

    /** Elimina le chat non usate da più dei giorni impostati in ciascuna. Restituisce quante ne ha eliminate. */
    fun purgeExpired(now: Long = System.currentTimeMillis()): Int {
        var removed = 0
        for (record in loadAll()) {
            if (now - record.lastUsedAt > record.expiryDays * DAY_MS) {
                delete(record.id)
                removed++
            }
        }
        return removed
    }

    private fun toJson(r: ChatRecord): JSONObject = JSONObject().apply {
        put("id", r.id)
        put("title", r.title)
        put("createdAt", r.createdAt)
        put("lastUsedAt", r.lastUsedAt)
        put("target", r.targetName)
        put("tone", r.toneId)
        put("context", r.context)
        put("windowStart", r.windowStart)
        put("promptTokens", r.promptTokens)
        put("expiryDays", r.expiryDays)
        put("glossary", JSONArray().apply {
            r.glossary.forEach { put(JSONObject().put("s", it.source).put("t", it.target)) }
        })
        put("instructions", JSONArray().apply { r.instructions.forEach { put(it) } })
        put("entries", JSONArray().apply {
            r.entries.forEach { entry ->
                put(
                    when (entry) {
                        is ChatEntry.Turn -> JSONObject()
                            .put("type", "turn")
                            .put("source", entry.source)
                            .put("translation", entry.translation)

                        is ChatEntry.Instruction -> JSONObject()
                            .put("type", "instruction")
                            .put("text", entry.text)
                    }
                )
            }
        })
    }

    private fun parse(text: String): ChatRecord {
        val o = JSONObject(text)

        val glossaryJson = o.getJSONArray("glossary")
        val glossary = (0 until glossaryJson.length()).map { i ->
            val item = glossaryJson.getJSONObject(i)
            GlossaryEntry(item.getString("s"), item.getString("t"))
        }

        val instructionsJson = o.getJSONArray("instructions")
        val instructions = (0 until instructionsJson.length()).map { instructionsJson.getString(it) }

        val entriesJson = o.getJSONArray("entries")
        val entries = (0 until entriesJson.length()).map { i ->
            val item = entriesJson.getJSONObject(i)
            if (item.getString("type") == "turn") {
                ChatEntry.Turn(item.getString("source"), item.getString("translation"))
            } else {
                ChatEntry.Instruction(item.getString("text"))
            }
        }

        return ChatRecord(
            id = o.getString("id"),
            title = o.getString("title"),
            createdAt = o.getLong("createdAt"),
            lastUsedAt = o.getLong("lastUsedAt"),
            targetName = o.getString("target"),
            toneId = o.getString("tone"),
            context = o.getString("context"),
            glossary = glossary,
            instructions = instructions,
            entries = entries,
            windowStart = o.getInt("windowStart"),
            promptTokens = o.getInt("promptTokens"),
            // Le chat salvate prima di questa versione non hanno il campo: usano la durata predefinita
            expiryDays = o.optInt("expiryDays", CHAT_EXPIRY_DAYS)
        )
    }
}