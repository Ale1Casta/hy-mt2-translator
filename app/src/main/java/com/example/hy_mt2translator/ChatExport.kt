package com.example.hy_mt2translator

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** I formati in cui si può esportare una chat. */
enum class ExportFormat(
    val label: String,
    val shortName: String,
    val description: String,
    val extension: String,
    val mime: String
) {
    Pdf("PDF", "PDF", "Da leggere o stampare", "pdf", "application/pdf"),
    Txt("Testo semplice (.txt)", "TXT", "Si apre ovunque", "txt", "text/plain"),
    Markdown("Markdown (.md)", "Markdown", "Con titoli e formattazione", "md", "text/markdown"),
    Csv("CSV", "CSV", "Due colonne: originale e traduzione", "csv", "text/csv")
}

/** Le scelte fatte nella schermata di esportazione. */
data class ExportOptions(
    val format: ExportFormat,
    /** Con false si esportano solo le traduzioni (non vale per il CSV, che ha sempre due colonne). */
    val includeOriginal: Boolean,
    /** Aggiunge stile, contesto, glossario e istruzioni (non vale per il CSV). */
    val includeSettings: Boolean
)

/** Il contenuto da esportare, già in forma neutra: i vari formati lo scrivono ciascuno a modo suo. */
private class ExportContent(
    val title: String,
    val infoLines: List<String>,
    val settingsLines: List<String>,
    val entries: List<ChatEntry>
)

private fun buildContent(session: ChatSession, includeSettings: Boolean): ExportContent {
    val date = SimpleDateFormat("d MMMM yyyy", Locale.ITALIAN).format(Date())
    val info = listOf(
        "Lingua di destinazione: ${session.target.nameIt}",
        "Esportata il $date"
    )
    val settings = if (!includeSettings) {
        emptyList()
    } else {
        buildList {
            add("Stile: ${session.tone.labelIt}")
            if (session.context.isNotBlank()) add("Contesto: ${session.context.trim()}")
            if (session.glossary.isNotEmpty()) {
                add("Glossario:")
                session.glossary.forEach { add("  ${it.source} → ${it.target}") }
            }
            if (session.instructions.isNotEmpty()) {
                add("Istruzioni:")
                session.instructions.forEach { add("  ${it.trim()}") }
            }
        }
    }
    return ExportContent(session.displayTitle, info, settings, session.entries.toList())
}

private fun buildText(content: ExportContent, includeOriginal: Boolean): String {
    val sb = StringBuilder()
    sb.append(content.title).append('\n')
    content.infoLines.forEach { sb.append(it).append('\n') }
    if (content.settingsLines.isNotEmpty()) {
        sb.append("\nImpostazioni\n")
        content.settingsLines.forEach { sb.append(it).append('\n') }
    }
    sb.append('\n')
    for (entry in content.entries) {
        when (entry) {
            is ChatEntry.Turn -> {
                if (includeOriginal) {
                    sb.append("Originale: ").append(entry.source).append('\n')
                    sb.append("Traduzione: ").append(entry.translation).append("\n\n")
                } else {
                    sb.append(entry.translation).append("\n\n")
                }
            }

            is ChatEntry.Instruction -> {
                if (includeOriginal) sb.append("[Istruzione] ").append(entry.text).append("\n\n")
            }
        }
    }
    return sb.toString().trimEnd() + "\n"
}

private fun buildMarkdown(content: ExportContent, includeOriginal: Boolean): String {
    val sb = StringBuilder()
    sb.append("# ").append(content.title).append("\n\n")
    content.infoLines.forEach { sb.append("- ").append(it).append('\n') }
    if (content.settingsLines.isNotEmpty()) {
        sb.append("\n## Impostazioni\n\n")
        content.settingsLines.forEach { line ->
            // Le righe rientrate (voci di glossario e istruzioni) diventano elenchi annidati
            if (line.startsWith("  ")) {
                sb.append("  - ").append(line.trim()).append('\n')
            } else {
                sb.append("- ").append(line).append('\n')
            }
        }
    }
    sb.append("\n## Conversazione\n\n")
    for (entry in content.entries) {
        when (entry) {
            is ChatEntry.Turn -> {
                if (includeOriginal) {
                    sb.append("**Originale:** ").append(entry.source).append("\n\n")
                }
                sb.append("> ").append(entry.translation.replace("\n", "\n> ")).append("\n\n")
            }

            is ChatEntry.Instruction -> {
                if (includeOriginal) sb.append("*Istruzione:* ").append(entry.text).append("\n\n")
            }
        }
    }
    return sb.toString().trimEnd() + "\n"
}

private fun csvField(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

private fun buildCsv(content: ExportContent): String {
    // Il BOM iniziale fa riconoscere la codifica UTF-8 a Excel
    val sb = StringBuilder("\uFEFF")
    sb.append("originale,traduzione\n")
    for (entry in content.entries) {
        if (entry is ChatEntry.Turn) {
            sb.append(csvField(entry.source)).append(',').append(csvField(entry.translation)).append('\n')
        }
    }
    return sb.toString()
}

/** Scrive il PDF andando a capo e cambiando pagina da solo; funziona con qualsiasi scrittura. */
private fun writePdf(file: File, content: ExportContent, includeOriginal: Boolean) {
    val pageWidth = 595
    val pageHeight = 842
    val margin = 40f
    val textWidth = pageWidth - (2 * margin).toInt()
    val bottomLimit = pageHeight - margin

    fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    val titlePaint = paint(18f, bold = true)
    val bodyPaint = paint(11f)
    val labelPaint = paint(9f, bold = true, color = Color.GRAY)
    val mutedPaint = paint(10f, color = Color.DKGRAY)

    val doc = PdfDocument()
    var pageNumber = 1
    var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
    var canvas = page.canvas
    var y = margin

    fun newPage() {
        doc.finishPage(page)
        pageNumber++
        page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        canvas = page.canvas
        y = margin
    }

    // Disegna un blocco di testo riga per riga, passando alla pagina dopo quando serve
    fun drawBlock(text: String, textPaint: TextPaint, indent: Float = 0f, spaceAfter: Float = 6f) {
        if (text.isEmpty()) return
        val width = textWidth - indent.toInt()
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
            .setLineSpacing(0f, 1.15f)
            .build()
        for (i in 0 until layout.lineCount) {
            val top = layout.getLineTop(i)
            val lineHeight = layout.getLineBottom(i) - top
            if (y + lineHeight > bottomLimit) newPage()
            canvas.save()
            canvas.clipRect(margin + indent, y, margin + indent + width, y + lineHeight)
            canvas.translate(margin + indent, y - top)
            layout.draw(canvas)
            canvas.restore()
            y += lineHeight
        }
        y += spaceAfter
    }

    drawBlock(content.title, titlePaint, spaceAfter = 8f)
    content.infoLines.forEach { drawBlock(it, mutedPaint, spaceAfter = 2f) }
    if (content.settingsLines.isNotEmpty()) {
        y += 8f
        drawBlock("IMPOSTAZIONI", labelPaint, spaceAfter = 3f)
        content.settingsLines.forEach { drawBlock(it, mutedPaint, spaceAfter = 1f) }
    }
    y += 14f

    for (entry in content.entries) {
        when (entry) {
            is ChatEntry.Turn -> {
                if (includeOriginal) {
                    drawBlock("ORIGINALE", labelPaint, spaceAfter = 1f)
                    drawBlock(entry.source, bodyPaint, spaceAfter = 5f)
                    drawBlock("TRADUZIONE", labelPaint, indent = 12f, spaceAfter = 1f)
                    drawBlock(entry.translation, bodyPaint, indent = 12f, spaceAfter = 12f)
                } else {
                    drawBlock(entry.translation, bodyPaint, spaceAfter = 12f)
                }
            }

            is ChatEntry.Instruction -> {
                if (includeOriginal) {
                    drawBlock("ISTRUZIONE", labelPaint, spaceAfter = 1f)
                    drawBlock(entry.text, mutedPaint, spaceAfter = 12f)
                }
            }
        }
    }

    doc.finishPage(page)
    FileOutputStream(file).use { doc.writeTo(it) }
    doc.close()
}

/** Nome del file, sicuro per qualsiasi app: solo lettere minuscole, numeri e trattini. */
private fun safeFileName(title: String): String =
    title.lowercase(Locale.ITALIAN)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifEmpty { "chat" }
        .take(40)

/**
 * Crea il file di esportazione nella cartella temporanea dell'app e restituisce l'indirizzo
 * con cui altre app possono leggerlo. Tocca il disco: da chiamare fuori dal thread principale.
 */
fun exportChat(context: Context, session: ChatSession, options: ExportOptions): Uri {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }

    // Pulizia: i file creati più di un giorno fa non servono più
    val dayAgo = System.currentTimeMillis() - 24L * 60 * 60 * 1000
    dir.listFiles()?.filter { it.lastModified() < dayAgo }?.forEach { it.delete() }

    val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.ITALIAN).format(Date())
    val file = File(dir, "${safeFileName(session.displayTitle)}-$stamp.${options.format.extension}")

    val isCsv = options.format == ExportFormat.Csv
    val content = buildContent(session, includeSettings = options.includeSettings && !isCsv)
    val includeOriginal = options.includeOriginal || isCsv

    when (options.format) {
        ExportFormat.Pdf -> writePdf(file, content, includeOriginal)
        ExportFormat.Txt -> file.writeText(buildText(content, includeOriginal), Charsets.UTF_8)
        ExportFormat.Markdown -> file.writeText(buildMarkdown(content, includeOriginal), Charsets.UTF_8)
        ExportFormat.Csv -> file.writeText(buildCsv(content), Charsets.UTF_8)
    }

    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/** Apre il menu di condivisione di Android con il file. */
fun shareFile(context: Context, uri: Uri, mime: String, subject: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}