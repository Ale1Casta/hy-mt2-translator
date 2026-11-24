package com.example.hy_mt2translator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.example.hy_mt2translator.ui.theme.GlassIcons
import com.example.hy_mt2translator.ui.theme.GlassKind
import com.example.hy_mt2translator.ui.theme.GlassPillButton
import com.example.hy_mt2translator.ui.theme.LocalGlass
import com.example.hy_mt2translator.ui.theme.PrimaryButton
import com.example.hy_mt2translator.ui.theme.glass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Costruisce il prompt di traduzione, nei due formati del README di Hy-MT2:
 * - senza stile (tono Neutro): prompt di base
 * - con stile: istruzione di stile, con lo stile tra parentesi quadre
 */
private fun buildPrompt(text: String, targetLanguage: String, style: String?): String =
    if (style == null) {
        "Translate the following text into $targetLanguage. " +
                "Note that you should only output the translated result without any additional explanation:\n\n" +
                text
    } else {
        "Please translate the following text into $targetLanguage. " +
                "Note that the translation style must strictly conform to [$style]:\n\n" +
                text
    }

/** Testo di stato mostrato in cima alla schermata. */
private fun statusText(state: InferenceEngine.State): String = when (state) {
    is InferenceEngine.State.Uninitialized,
    is InferenceEngine.State.Initializing -> "Avvio del motore…"

    is InferenceEngine.State.Initialized,
    is InferenceEngine.State.LoadingModel -> "Carico il modello…"

    is InferenceEngine.State.ModelReady -> "Modello pronto · tutto sul telefono"
    is InferenceEngine.State.ProcessingUserPrompt -> "Leggo il testo…"
    is InferenceEngine.State.Generating -> "Traduco…"
    is InferenceEngine.State.Error -> "Errore"
    else -> "Attendi…"
}

/** Copia il testo negli appunti. */
private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("traduzione", text))
    // Da Android 13 il sistema mostra già una conferma
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Copiato", Toast.LENGTH_SHORT).show()
    }
}

/** Apre il menu di condivisione di Android con il testo. */
private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

/** Pallino di stato: verde (con alone) quando il modello è pronto, rosso se c'è un errore, giallo lampeggiante mentre lavora. */
@Composable
private fun StatusDot(state: InferenceEngine.State) {
    val g = LocalGlass.current
    when (state) {
        is InferenceEngine.State.ModelReady ->
            Box(
                Modifier
                    .size(8.dp)
                    .shadow(6.dp, CircleShape, ambientColor = g.ok, spotColor = g.ok)
                    .background(g.ok, CircleShape)
            )

        is InferenceEngine.State.Error ->
            Box(Modifier.size(8.dp).background(g.danger, CircleShape))

        else -> {
            val transition = rememberInfiniteTransition(label = "pallino")
            val alpha by transition.animateFloat(
                initialValue = 1f,
                targetValue = 0.2f,
                animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                label = "lampeggio"
            )
            Box(Modifier.size(8.dp).alpha(alpha).background(g.warn, CircleShape))
        }
    }
}

/** Riga di stato: pallino e testo. */
@Composable
private fun StatusLine(state: InferenceEngine.State) {
    val g = LocalGlass.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(state)
        Spacer(Modifier.width(8.dp))
        Text(
            text = statusText(state),
            fontSize = 12.sp,
            color = g.textMuted
        )
    }
}

/** Statistiche dell'ultima traduzione: tempi e velocità. */
private data class TranslationStats(
    /** Dal via al primo pezzo di testo: il modello sta leggendo il prompt. */
    val readSeconds: Double,
    /** Dal primo pezzo alla fine: il modello scrive la traduzione. */
    val writeSeconds: Double,
    /** Pezzi di testo generati (circa un token ciascuno). */
    val tokens: Int
) {
    /** Velocità di scrittura. Il primo token non si conta: il suo tempo è già nella lettura. */
    val tokensPerSecond: Double?
        get() = if (tokens > 1 && writeSeconds > 0.0) (tokens - 1) / writeSeconds else null
}

/** Come salvare le statistiche quando Android ricrea la schermata (per esempio ruotando il telefono). */
private val StatsSaver = listSaver<TranslationStats?, Any>(
    save = { stats ->
        if (stats == null) emptyList() else listOf(stats.readSeconds, stats.writeSeconds, stats.tokens)
    },
    restore = { saved ->
        if (saved.isEmpty()) {
            null
        } else {
            TranslationStats(saved[0] as Double, saved[1] as Double, saved[2] as Int)
        }
    }
)

/** Le statistiche in piccolo, su due righe discrete. */
@Composable
private fun StatsText(stats: TranslationStats) {
    val g = LocalGlass.current
    val speed = stats.tokensPerSecond?.let { " · ${"%.1f".format(it)} token/s" } ?: ""
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "lettura ${"%.1f".format(stats.readSeconds)} s · scrittura ${"%.1f".format(stats.writeSeconds)} s",
            fontSize = 11.sp,
            color = g.textMuted,
            textAlign = TextAlign.Center
        )
        Text(
            text = "${stats.tokens} token$speed",
            fontSize = 11.sp,
            color = g.textMuted,
            textAlign = TextAlign.Center
        )
    }
}

/** Il testo della traduzione, con l'etichetta sopra; prima della traduzione, un segnaposto. */
@Composable
private fun TranslationBody(output: String, target: Language, tone: Tone, compact: Boolean) {
    val g = LocalGlass.current
    if (output.isEmpty()) {
        Text(
            text = "La traduzione apparirà qui",
            fontSize = 16.sp,
            color = g.textMuted
        )
    } else {
        Text(
            text = "${target.nameIt} · ${tone.labelIt}",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp,
            color = g.textMuted
        )
        Spacer(Modifier.height(if (compact) 8.dp else 18.dp))
        Text(
            text = output,
            fontSize = if (compact) 22.sp else 26.sp,
            lineHeight = if (compact) 30.sp else 35.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.2).sp,
            textAlign = TextAlign.Center
        )
    }
}

/** Un pulsante tondo in vetro con icona e testo (Copia, Condividi). */
@Composable
private fun IconPill(icon: ImageVector, label: String, onClick: () -> Unit) {
    GlassPillButton(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** Copia, Condividi e statistiche. In verticale (stacked) quando lo schermo è in orizzontale. */
@Composable
private fun ResultActions(
    context: Context,
    output: String,
    stats: TranslationStats?,
    stacked: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (stacked) {
            IconPill(GlassIcons.Copy, "Copia") { copyToClipboard(context, output) }
            IconPill(GlassIcons.Share, "Condividi") { shareText(context, output) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconPill(GlassIcons.Copy, "Copia") { copyToClipboard(context, output) }
                IconPill(GlassIcons.Share, "Condividi") { shareText(context, output) }
            }
        }
        if (stats != null) StatsText(stats)
    }
}

/** Campo di testo senza bordi, da mettere dentro il riquadro in vetro. */
@Composable
private fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    maxLines: Int,
    modifier: Modifier = Modifier
) {
    val g = LocalGlass.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(color = g.text, fontSize = 17.sp, lineHeight = 25.sp),
        cursorBrush = SolidColor(g.accent),
        minLines = 2,
        maxLines = maxLines,
        modifier = modifier
            .padding(horizontal = 2.dp, vertical = 6.dp)
            .semantics { contentDescription = "Testo da tradurre" },
        decorationBox = { innerTextField ->
            Box {
                // Il suggerimento si vede solo a campo vuoto e sparisce appena si scrive
                if (value.isEmpty()) {
                    Text(
                        text = "Testo da tradurre",
                        fontSize = 17.sp,
                        lineHeight = 25.sp,
                        color = g.textMuted
                    )
                }
                innerTextField()
            }
        }
    )
}

/**
 * Piccola x per cancellare il testo da tradurre. L'area da toccare è di [touchSize] (almeno 40 dp),
 * il disegno è un cerchietto di vetro più piccolo.
 */
@Composable
private fun ClearTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    touchSize: Dp = 44.dp
) {
    Box(
        modifier = modifier
            .size(touchSize)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = "Cancella il testo", onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .glass(CircleShape, GlassKind.Field),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = GlassIcons.Close,
                contentDescription = "Cancella il testo",
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun TranslatorScreen(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onAutoTheme: () -> Unit,
    onOpenSettings: () -> Unit,
    sharedAudioUri: android.net.Uri? = null,
    onSharedAudioConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val engine = remember { AiChat.getInferenceEngine(context.applicationContext) }
    val engineState by engine.state.collectAsState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val mic = remember { MicViewModel(scope) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) mic.start(context) }
    fun startMic() {
        focusManager.clearFocus()
        if (mic.hasPermission(context)) mic.start(context)
        else permLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    }
    val prefs = remember { AppPrefs(context) }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val g = LocalGlass.current

    // rememberSaveable: il testo sopravvive alla rotazione dello schermo
    var input by rememberSaveable { mutableStateOf("") }
    var output by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var stats by rememberSaveable(stateSaver = StatsSaver) { mutableStateOf<TranslationStats?>(null) }

    // Lingua e tono partono dall'ultima scelta salvata nelle preferenze
    var targetName by remember { mutableStateOf(prefs.targetLanguageName) }
    var toneId by remember { mutableStateOf(prefs.toneId) }
    val target = languageFromEnglishName(targetName)
    val tone = toneFromId(toneId)

    LaunchedEffect(mic.transcribed) {
        if (mic.transcribed.isNotEmpty()) input = mic.transcribed
    }
    LaunchedEffect(sharedAudioUri) {
        val uri = sharedAudioUri ?: return@LaunchedEffect
        onSharedAudioConsumed()
        mic.transcribeUri(context, uri)
    }
    // La traduzione in corso (serve per poterla fermare)
    var translateJob by remember { mutableStateOf<Job?>(null) }
    val isBusy = engineState is InferenceEngine.State.ProcessingUserPrompt ||
            engineState is InferenceEngine.State.Generating
    val canPress = isBusy || (engineState is InferenceEngine.State.ModelReady && input.isNotBlank())

    // Azione del pulsante Traduci / Ferma
    val onTranslate: () -> Unit = {
        if (isBusy) {
            translateJob?.cancel()
        } else {
            // Toglie il focus dal campo: la tastiera si chiude
            focusManager.clearFocus()
            translateJob = scope.launch {
                output = ""
                message = ""
                stats = null
                try {
                    val started = System.nanoTime()
                    var firstTokenAt = 0L
                    var pieces = 0
                    // Il motore restituisce i pezzi di testo man mano che li genera
                    engine.sendUserPrompt(
                        buildPrompt(input.trim(), target.nameEn, tone.style)
                    ).collect { token ->
                        if (firstTokenAt == 0L) firstTokenAt = System.nanoTime()
                        pieces++
                        output += token
                    }
                    val ended = System.nanoTime()
                    if (pieces > 0) {
                        stats = TranslationStats(
                            readSeconds = (firstTokenAt - started) / 1e9,
                            writeSeconds = (ended - firstTokenAt) / 1e9,
                            tokens = pieces
                        )
                    }
                } catch (e: CancellationException) {
                    // Fermata voluta dall'utente: non è un errore
                    throw e
                } catch (e: Exception) {
                    message = "Errore: ${e.message}"
                }
            }
        }
    }

    val languagePicker: @Composable (Modifier) -> Unit = { pickerModifier ->
        LanguagePicker(
            selected = target,
            onSelected = {
                targetName = it.nameEn
                prefs.targetLanguageName = it.nameEn
            },
            modifier = pickerModifier
        )
    }
    val tonePicker: @Composable (Modifier) -> Unit = { pickerModifier ->
        TonePicker(
            selected = tone,
            onSelected = {
                toneId = it.id
                prefs.toneId = it.id
            },
            modifier = pickerModifier
        )
    }
    val translateLabel: @Composable () -> Unit = {
        Text(
            text = if (isBusy) "Ferma" else "Traduci",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // imePadding: il riquadro di testo resta sopra la tastiera
                .imePadding()
                .padding(horizontal = 16.dp)
        ) {
            StatusLine(engineState)
            ModelProblem(engine, engineState)
            if (message.isNotEmpty()) {
                Text(
                    text = message,
                    fontSize = 12.sp,
                    color = g.danger,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (isLandscape) {
                // ORIZZONTALE: la traduzione a sinistra, i pulsanti Copia e Condividi a destra
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TranslationBody(output, target, tone, compact = true)
                    }
                    if (output.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .width(150.dp)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ResultActions(context, output, stats, stacked = true)
                        }
                    }
                }

                AnimatedVisibility(visible = mic.recording || mic.transcribing, enter = fadeIn(), exit = fadeOut()) {
                    RecordingBar(
                        seconds = mic.seconds,
                        onStop = { mic.stop() },
                        fromUri = mic.transcribingFromUri,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                if (mic.error.isNotEmpty()) {
                    Text(text = mic.error, fontSize = 12.sp, color = g.danger, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp))
                }
                // Riquadro in vetro: testo a sinistra; lingua e tono uno sopra l'altro; poi Traduci
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .glass(RoundedCornerShape(24.dp), GlassKind.Focus)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        // Campo di testo e microfono: il mic sta sotto il campo,
                        // fuori dal Box, così il testo usa tutta la larghezza
                        Column {
                            Box {
                                InputField(
                                    value = input,
                                    onValueChange = { input = it },
                                    maxLines = 3,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (input.isNotEmpty()) {
                                    ClearTextButton(
                                        onClick = { input = "" },
                                        touchSize = 40.dp,
                                        modifier = Modifier.align(Alignment.TopEnd)
                                    )
                                }
                            }
                            MicButton(
                                recording = mic.recording,
                                seconds = mic.seconds,
                                onStart = ::startMic,
                                onStop = { mic.stop() },
                                modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.width(190.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        languagePicker(
                            Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        )
                        tonePicker(Modifier.fillMaxWidth())
                    }
                    PrimaryButton(
                        onClick = onTranslate,
                        enabled = canPress && !mic.recording && !mic.transcribing,
                        stop = isBusy,
                        modifier = Modifier
                            .width(110.dp)
                            .fillMaxHeight(),
                        height = Dp.Unspecified
                    ) {
                        translateLabel()
                    }
                }
                Spacer(Modifier.height(10.dp))
            } else {
                // VERTICALE: la traduzione al centro, il riquadro e la riga lingua + Traduci in basso
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TranslationBody(output, target, tone, compact = false)
                    if (output.isNotEmpty()) {
                        Spacer(Modifier.height(18.dp))
                        ResultActions(context, output, stats, stacked = false)
                    }
                }

                AnimatedVisibility(visible = mic.recording || mic.transcribing, enter = fadeIn(), exit = fadeOut()) {
                    RecordingBar(
                        seconds = mic.seconds,
                        onStop = { mic.stop() },
                        fromUri = mic.transcribingFromUri,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                if (mic.error.isNotEmpty()) {
                    Text(text = mic.error, fontSize = 12.sp, color = g.danger, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp))
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(24.dp), GlassKind.Focus)
                        .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        tonePicker(Modifier)
                        if (input.isNotEmpty()) {
                            ClearTextButton(
                                onClick = { input = "" },
                                modifier = Modifier.offset(x = 8.dp)
                            )
                        }
                    }
                    // Campo di testo e microfono in una riga: il testo usa lo spazio
                    // disponibile senza essere costretto da padding fissi
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        InputField(
                            value = input,
                            onValueChange = { input = it },
                            maxLines = 6,
                            modifier = Modifier.weight(1f)
                        )
                        MicButton(
                            recording = mic.recording,
                            seconds = mic.seconds,
                            onStart = ::startMic,
                            onStop = { mic.stop() },
                            modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    languagePicker(
                        Modifier
                            .weight(1f)
                            .height(54.dp)
                    )
                    PrimaryButton(
                        onClick = onTranslate,
                        enabled = canPress && !mic.recording && !mic.transcribing,
                        stop = isBusy,
                        modifier = Modifier.weight(1f),
                        height = 54.dp
                    ) {
                        translateLabel()
                    }
                }
            }
        }

        // Linguetta sul bordo sinistro, sotto «Modello pronto»: è l'unica aggiunta a questa schermata
        QuickPanel(
            isDark = isDark,
            onToggleTheme = onToggleTheme,
            onAutoTheme = onAutoTheme,
            onOpenSettings = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 48.dp)
        )
    }
}