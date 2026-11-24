package com.example.hy_mt2translator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import android.Manifest
import com.example.hy_mt2translator.ui.theme.GlassIcons
import com.example.hy_mt2translator.ui.theme.GlassKind
import com.example.hy_mt2translator.ui.theme.LocalGlass
import com.example.hy_mt2translator.ui.theme.glass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Quanto bisogna scorrere in su sul pulsante di invio per cambiare modalità. */
private const val MODE_SWIPE_THRESHOLD_DP = 64

/** Una vibrazione breve ("scatto"), indipendente dalle impostazioni di feedback tattile del sistema. */
private fun tickVibration(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    if (vibrator.hasVibrator()) {
        vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

/** Copia un testo negli appunti. */
private fun copyChatText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("traduzione", text))
    // Da Android 13 il sistema mostra già una conferma
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Copiato", Toast.LENGTH_SHORT).show()
    }
}

/** Una bolla di testo: a destra quella dell'utente (arancione), a sinistra la traduzione (vetro). */
@Composable
private fun Bubble(
    text: String,
    isSource: Boolean,
    modifier: Modifier = Modifier
) {
    val g = LocalGlass.current
    // L'angolo vicino a chi scrive è meno arrotondato
    val shape = if (isSource) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 6.dp, bottomStart = 20.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp)
    }
    Box(
        modifier = modifier
            .widthIn(max = 280.dp)
            .background(if (isSource) g.meFill else g.themFill, shape)
            .border(1.dp, if (isSource) g.meBorder else g.themBorder, shape)
            .padding(horizontal = 15.dp, vertical = 12.dp)
    ) {
        Text(
            text = text,
            color = if (isSource) g.meText else g.text,
            fontSize = if (isSource) 16.sp else 17.sp,
            lineHeight = if (isSource) 22.sp else 25.sp
        )
    }
}

/** Un messaggio dell'utente (a destra) e la sua traduzione (a sinistra, con Copia). */
@Composable
private fun TurnItem(source: String, translation: String, onCopy: () -> Unit) {
    val g = LocalGlass.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Bubble(source, isSource = true)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Bubble(translation, isSource = false, modifier = Modifier.weight(1f, fill = false))
            Row(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(role = Role.Button, onClick = onCopy)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = GlassIcons.Copy,
                    contentDescription = null,
                    tint = g.textMuted,
                    modifier = Modifier.size(18.dp)
                )
                Text("Copia", fontSize = 13.sp, color = g.textMuted)
            }
        }
    }
}

/** Un'istruzione data al modello: bolla con il bordo tratteggiato e una nota sotto. */
@Composable
private fun InstructionItem(text: String) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 6.dp, bottomStart = 20.dp)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(g.dashedFill, shape)
                .drawBehind {
                    // Bordo tratteggiato lungo la forma della bolla (tre angoli grandi, uno piccolo)
                    val big = 20.dp.toPx()
                    val small = 6.dp.toPx()
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = 0f,
                                top = 0f,
                                right = size.width,
                                bottom = size.height,
                                topLeftCornerRadius = CornerRadius(big),
                                topRightCornerRadius = CornerRadius(big),
                                bottomRightCornerRadius = CornerRadius(small),
                                bottomLeftCornerRadius = CornerRadius(big)
                            )
                        )
                    }
                    drawPath(
                        path = path,
                        color = g.dashedBorder,
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                        )
                    )
                }
                .padding(horizontal = 15.dp, vertical = 10.dp)
        ) {
            Column {
                Text(
                    text = "Istruzione",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = g.accentText
                )
                Spacer(Modifier.height(4.dp))
                Text(text, fontSize = 16.sp, lineHeight = 22.sp)
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .background(g.noteFill, RoundedCornerShape(14.dp))
                .border(1.dp, g.noteBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = GlassIcons.Check,
                contentDescription = null,
                tint = g.ok,
                modifier = Modifier.size(14.dp)
            )
            Text("Aggiunta alle istruzioni fisse", fontSize = 12.sp, color = g.textMuted)
        }
    }
}

/**
 * Pulsante di invio. Un tocco invia il messaggio da tradurre.
 * Scorrendo verso l'alto cambia modalità: diventa il pulsante "istruzione" (il messaggio non
 * viene tradotto ma salvato tra le istruzioni fisse). Continuando a scorrere verso l'alto
 * le modalità ruotano, quindi non serve mai scorrere verso il basso.
 * Per chi non può usare il gesto c'è un'azione di accessibilità equivalente.
 */
@Composable
private fun SendButton(
    instructionMode: Boolean,
    enabled: Boolean,
    onModeChange: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val g = LocalGlass.current
    // Quanto bisogna scorrere per ogni cambio di modalità
    val threshold = with(LocalDensity.current) { MODE_SWIPE_THRESHOLD_DP.dp.toPx() }
    val context = LocalContext.current
    // Il gesto resta attivo anche quando la modalità cambia a metà scorrimento,
    // perciò legge sempre i valori più recenti invece di ripartire a ogni cambio
    val currentMode by rememberUpdatedState(instructionMode)
    val currentOnModeChange by rememberUpdatedState(onModeChange)

    // In modalità istruzione il pulsante diventa giallo; altrimenti è corallo come gli altri principali
    val container = if (instructionMode) g.warn else g.primaryFill
    val content = when {
        !instructionMode -> g.primaryText
        g.isDark -> Color(0xFF3A2A06)
        else -> Color.White
    }
    val label = if (instructionMode) "Salva come istruzione" else "Invia e traduci"

    Box(
        modifier = Modifier
            .size(50.dp)
            .then(
                if (enabled) {
                    Modifier.shadow(8.dp, CircleShape, ambientColor = container, spotColor = container)
                } else {
                    Modifier
                }
            )
            .clip(CircleShape)
            .background(if (enabled) container else g.pillFill)
            .then(
                if (enabled) {
                    Modifier.border(
                        1.dp,
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.18f))
                        ),
                        CircleShape
                    )
                } else {
                    Modifier.border(1.dp, g.pillBorder, CircleShape)
                }
            )
            .pointerInput(Unit) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = { dragged = 0f },
                    onDragCancel = { dragged = 0f }
                ) { change, amount ->
                    change.consume()
                    // Conta solo lo scorrimento verso l'alto
                    if (amount < 0f) dragged += amount
                    if (dragged < -threshold) {
                        // Ogni soglia superata fa passare alla modalità successiva
                        currentOnModeChange(!currentMode)
                        tickVibration(context)
                        dragged = 0f
                    }
                }
            }
            .semantics {
                contentDescription = label
                customActions = listOf(
                    CustomAccessibilityAction(
                        if (instructionMode) "Torna a invia e traduci" else "Passa a salva come istruzione"
                    ) {
                        onModeChange(!instructionMode)
                        true
                    }
                )
            }
            .clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (instructionMode) GlassIcons.Edit else GlassIcons.Send,
            contentDescription = null,
            tint = if (enabled) content else g.textMuted,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** Dove si trova l'utente dentro la sezione Chat. */
private sealed interface ChatDestination {
    data object Overview : ChatDestination
    data class Conversation(val session: ChatSession) : ChatDestination
    data class Settings(val session: ChatSession) : ChatDestination
    data class Export(val session: ChatSession) : ChatDestination
}

/** Sezione Chat: elenco delle chat, conversazione e impostazioni. */
@Composable
fun ChatScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { ChatStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val saveLock = remember { Mutex() }

    var destination by remember { mutableStateOf<ChatDestination>(ChatDestination.Overview) }
    var chats by remember { mutableStateOf<List<ChatRecord>>(emptyList()) }

    // All'apertura: elimina le chat scadute e carica l'elenco
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { store.purgeExpired() }
        chats = withContext(Dispatchers.IO) { store.loadAll() }
    }

    // Salva la chat su disco (solo se ha qualcosa dentro); poi, se serve, ricarica l'elenco
    fun persist(session: ChatSession, reloadList: Boolean = false) {
        scope.launch {
            if (session.hasContent) {
                session.lastUsedAt = System.currentTimeMillis()
                val record = session.toRecord()
                withContext(Dispatchers.IO) { saveLock.withLock { store.save(record) } }
            }
            if (reloadList) {
                chats = withContext(Dispatchers.IO) { store.loadAll() }
            }
        }
    }

    // Elimina una chat; per qualche secondo resta il pulsante "Annulla"
    fun delete(record: ChatRecord) {
        scope.launch {
            withContext(Dispatchers.IO) { store.delete(record.id) }
            chats = chats.filter { it.id != record.id }
            val result = snackbarHostState.showSnackbar(
                message = "Chat eliminata",
                actionLabel = "Annulla",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                withContext(Dispatchers.IO) { saveLock.withLock { store.save(record) } }
                chats = withContext(Dispatchers.IO) { store.loadAll() }
            }
        }
    }

    val current = destination

    // Il tasto Indietro di Android risale di un livello: impostazioni, conversazione, elenco
    BackHandler(enabled = current !is ChatDestination.Overview) {
        when (current) {
            is ChatDestination.Export -> {
                destination = ChatDestination.Settings(current.session)
            }

            is ChatDestination.Settings -> {
                persist(current.session)
                destination = ChatDestination.Conversation(current.session)
            }

            is ChatDestination.Conversation -> {
                persist(current.session, reloadList = true)
                destination = ChatDestination.Overview
            }

            ChatDestination.Overview -> Unit
        }
    }

    when (current) {
        ChatDestination.Overview -> ChatListScreen(
            chats = chats,
            snackbarHostState = snackbarHostState,
            onOpen = { record ->
                destination = ChatDestination.Conversation(ChatSession.fromRecord(record))
            },
            onNew = { destination = ChatDestination.Conversation(ChatSession()) },
            onDelete = { delete(it) },
            modifier = modifier
        )

        is ChatDestination.Conversation -> ChatConversation(
            session = current.session,
            onBack = {
                persist(current.session, reloadList = true)
                destination = ChatDestination.Overview
            },
            onOpenSettings = { destination = ChatDestination.Settings(current.session) },
            onChanged = { persist(current.session) },
            modifier = modifier
        )

        is ChatDestination.Settings -> ChatSettingsScreen(
            session = current.session,
            onBack = {
                persist(current.session)
                destination = ChatDestination.Conversation(current.session)
            },
            onExport = { destination = ChatDestination.Export(current.session) },
            onDeleteChat = {
                // Stessa eliminazione della card nell'elenco: con "Annulla" per qualche secondo
                val record = current.session.toRecord()
                destination = ChatDestination.Overview
                delete(record)
            },
            modifier = modifier
        )

        is ChatDestination.Export -> ChatExportScreen(
            session = current.session,
            onBack = { destination = ChatDestination.Settings(current.session) },
            modifier = modifier
        )
    }
}

@Composable
private fun ChatConversation(
    session: ChatSession,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val engine = remember { AiChat.getInferenceEngine(context.applicationContext) }
    val engineState by engine.state.collectAsState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    val chatScope = rememberCoroutineScope()
    val mic = remember { MicViewModel(chatScope) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) mic.start(context) }
    fun startMic() {
        if (mic.hasPermission(context)) mic.start(context)
        else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    var input by rememberSaveable { mutableStateOf("") }
    var instructionMode by remember { mutableStateOf(false) }

    LaunchedEffect(mic.transcribed) {
        if (mic.transcribed.isNotEmpty()) input = mic.transcribed
    }
    var pendingSource by remember { mutableStateOf<String?>(null) }
    var pendingTranslation by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    // Porta la conversazione in fondo, fino all'ultima riga dell'ultimo messaggio
    suspend fun scrollToBottom(animated: Boolean) {
        val count = session.entries.size + if (pendingSource != null) 1 else 0
        if (count == 0) return
        if (animated) listState.animateScrollToItem(count - 1) else listState.scrollToItem(count - 1)
        // Se l'ultimo messaggio è lungo si scende fino alla sua fine (lo scorrimento si ferma da solo al fondo)
        listState.scrollBy(100_000f)
    }

    // Scorre in fondo quando arriva un nuovo messaggio
    LaunchedEffect(session.entries.size, pendingSource) {
        scrollToBottom(animated = true)
    }

    // Mentre arriva la traduzione il messaggio cresce: resta visibile la parte appena scritta
    LaunchedEffect(pendingTranslation) {
        if (pendingSource != null) scrollToBottom(animated = false)
    }

    // Quando la tastiera si apre lo spazio per la conversazione si accorcia: si resta in fondo, così gli
    // ultimi messaggi non finiscono sotto la tastiera. Segue tutta l'animazione della tastiera; dopo,
    // si può scorrere liberamente verso l'alto.
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0) scrollToBottom(animated = false)
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty()) return
        input = ""
        message = ""
        focusManager.clearFocus()

        // Modalità istruzione: il testo non si traduce, va tra le istruzioni fisse
        if (instructionMode) {
            session.instructions.add(text)
            session.entries.add(ChatEntry.Instruction(text))
            instructionMode = false
            onChanged()
            return
        }

        scope.launch {
            try {
                val built = buildPromptWithinBudget(
                    session.settings(), session.turns(), session.windowStart, text
                ) { engine.countTokens(it) }
                session.windowStart = built.startIndex
                session.promptTokens = built.tokens

                pendingSource = text
                pendingTranslation = ""
                val started = System.nanoTime()
                engine.sendUserPrompt(built.prompt, CHAT_MAX_OUTPUT_TOKENS)
                    .collect { token -> pendingTranslation += token }
                session.lastSeconds = (System.nanoTime() - started) / 1e9
                session.entries.add(ChatEntry.Turn(text, pendingTranslation.trim()))
                onChanged()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Errore: ${e.message}"
                input = text
            } finally {
                pendingSource = null
                pendingTranslation = ""
            }
        }
    }

    val g = LocalGlass.current
    val fraction = (session.promptTokens.toFloat() / PROMPT_TOKEN_BUDGET).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Intestazione: freccia indietro (all'elenco), titolo e lingua
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .glass(CircleShape, GlassKind.Pill)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = GlassIcons.Back,
                    contentDescription = "Torna all'elenco delle chat",
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.displayTitle,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Traduce in ${session.target.nameIt}",
                    fontSize = 12.sp,
                    color = g.textMuted
                )
            }
        }

        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Pulsante lungo in vetro con il riassunto delle impostazioni fisse
            val stripShape = RoundedCornerShape(24.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .glass(stripShape, GlassKind.Panel)
                    .clip(stripShape)
                    .clickable(role = Role.Button, onClick = onOpenSettings)
                    .padding(start = 16.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = GlassIcons.Pin,
                    contentDescription = null,
                    tint = g.accent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = session.summary(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = GlassIcons.ChevronRight,
                    contentDescription = null,
                    tint = g.textMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Avviso se il modello manca o il motore è andato in errore
            ModelProblem(engine, engineState)

            // Barra della memoria
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Memoria della chat", fontSize = 12.sp, color = g.textMuted)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(g.trackFill)
                        .progressSemantics(fraction)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .background(g.barFill)
                    )
                }
                Text("${(fraction * 100).toInt()}%", fontSize = 12.sp, color = g.textMuted)
            }
            session.lastSeconds?.let { seconds ->
                Text(
                    text = "Ultimo messaggio: ${"%.1f".format(seconds)} s",
                    fontSize = 12.sp,
                    color = g.textMuted,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
            if (message.isNotEmpty()) {
                Text(
                    text = message,
                    fontSize = 12.sp,
                    color = g.danger,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
        }

        // Conversazione
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(session.entries) { entry ->
                when (entry) {
                    is ChatEntry.Turn -> TurnItem(
                        source = entry.source,
                        translation = entry.translation,
                        onCopy = { copyChatText(context, entry.translation) }
                    )

                    is ChatEntry.Instruction -> InstructionItem(entry.text)
                }
            }
            val pending = pendingSource
            if (pending != null) {
                item {
                    TurnItem(
                        source = pending,
                        translation = pendingTranslation.ifEmpty { "…" },
                        onCopy = {}
                    )
                }
            }
        }

        // Suggerimento per il gesto, finché la chat è vuota
        if (session.entries.isEmpty()) {
            Text(
                text = "Suggerimento: scorri in su sul pulsante di invio per scrivere un'istruzione; scorri ancora per tornare a tradurre.",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = g.textMuted,
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 8.dp)
            )
        }

        AnimatedVisibility(visible = mic.recording || mic.transcribing, enter = fadeIn(), exit = fadeOut()) {
            RecordingBar(
                seconds = mic.seconds,
                onStop = { mic.stop() },
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
            )
        }
        if (mic.error.isNotEmpty()) {
            Text(text = mic.error, fontSize = 12.sp, lineHeight = 17.sp,
                color = LocalGlass.current.danger,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp))
        }

        // Barra in vetro con il campo di testo e il pulsante di invio
        val barShape = RoundedCornerShape(32.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
                .glass(barShape, GlassKind.Panel)
                // In modalità istruzione il bordo della barra diventa giallo
                .then(
                    if (instructionMode) {
                        Modifier.border(1.5.dp, g.warn.copy(alpha = 0.85f), barShape)
                    } else {
                        Modifier
                    }
                )
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                textStyle = TextStyle(color = g.text, fontSize = 16.sp, lineHeight = 22.sp),
                cursorBrush = SolidColor(g.accent),
                maxLines = 4,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
                    .semantics {
                        contentDescription = if (instructionMode) "Istruzione" else "Messaggio da tradurre"
                    },
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.heightIn(min = 50.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (input.isEmpty()) {
                            Text(
                                text = if (instructionMode) "Scrivi un'istruzione" else "Scrivi un messaggio",
                                fontSize = 16.sp,
                                color = g.textMuted
                            )
                        }
                        innerTextField()
                    }
                }
            )
            MicButton(
                recording = mic.recording,
                seconds = mic.seconds,
                onStart = ::startMic,
                onStop = { mic.stop() }
            )
            SendButton(
                instructionMode = instructionMode,
                enabled = input.isNotBlank() &&
                        (instructionMode || engineState is InferenceEngine.State.ModelReady) &&
                        !mic.recording && !mic.transcribing,
                onModeChange = { instructionMode = it },
                onClick = { send() }
            )
        }
    }
}