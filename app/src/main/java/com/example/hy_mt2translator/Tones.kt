package com.example.hy_mt2translator

/**
 * Un tono di traduzione.
 * - labelIt e description: quello che vede l'utente
 * - style: la parola o frase (in inglese) inserita nell'istruzione di stile del prompt;
 *   per "Neutro" è null e si usa il prompt di base, senza istruzione di stile
 */
data class Tone(
    val id: String,
    val labelIt: String,
    val description: String,
    val style: String?
)

/**
 * Toni in ordine di formalità crescente, con Neutro in mezzo.
 * Le stringhe di stile sono un primo tentativo: il modello le accetta come testo libero,
 * quindi si possono cambiare dopo le prove sul telefono.
 */
val TONES = listOf(
    Tone("informal", "Informale", "Colloquiale, adatto a messaggi tra amici, slang", "informal and casual, slang"),
    Tone("neutral", "Neutro", "Predefinito: traduzione standard, senza uno stile particolare", null),
    Tone("formal", "Formale", "Cortese e composto, adatto a lettere ed email di lavoro", "formal"),
    Tone("technical", "Tecnico", "Preciso e specialistico, con la terminologia del settore", "technical, using precise domain terminology"),
)

/** Tono scelto all'avvio: Neutro (quello senza stile). */
val DEFAULT_TONE = TONES.first { it.style == null }

/** Ritrova un tono dal suo id (serve per salvare e ripristinare la scelta). */
fun toneFromId(id: String): Tone = TONES.firstOrNull { it.id == id } ?: DEFAULT_TONE