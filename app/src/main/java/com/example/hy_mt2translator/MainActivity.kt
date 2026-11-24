package com.example.hy_mt2translator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.dp
import com.arm.aichat.AiChat
import com.example.hy_mt2translator.ui.theme.GlassBackground
import com.example.hy_mt2translator.ui.theme.GlassIcons
import com.example.hy_mt2translator.ui.theme.GlassTab
import com.example.hy_mt2translator.ui.theme.GlassTabBar
import com.example.hy_mt2translator.ui.theme.HyMT2TranslatorTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var sharedAudioUri by rememberSaveable { mutableStateOf<Uri?>(null) }
            LaunchedEffect(Unit) { sharedAudioUri = extractSharedAudio(intent) }

            // Benchmark al primo avvio
            var benchPending by remember { mutableStateOf(false) }  // mostra popup di conferma
            var benchRunning by remember { mutableStateOf(false) }   // test in corso
            var benchCurrent by remember { mutableStateOf(0) }
            var benchTotal by remember { mutableStateOf(0) }
            var benchDesc by remember { mutableStateOf("") }
            val benchContext = LocalContext.current
            val benchScope = rememberCoroutineScope()
            // Durante il test lo schermo resta acceso: se si spegne, le misure non sono affidabili
            val benchView = LocalView.current
            DisposableEffect(benchRunning) {
                benchView.keepScreenOn = benchRunning
                onDispose { benchView.keepScreenOn = false }
            }

            fun startBench() {
                benchPending = false
                benchRunning = true
                val appContext = benchContext.applicationContext
                val eng = com.arm.aichat.AiChat.getInferenceEngine(appContext)
                benchScope.launch {
                    try {
                        // Stessa funzione usata dal pulsante «Rilancia il benchmark» nelle
                        // Impostazioni, così il comportamento è identico nei due punti
                        BenchmarkEngine.runAll(appContext, eng) { cur, tot, desc ->
                            benchCurrent = cur; benchTotal = tot; benchDesc = desc
                        }
                    } finally {
                        benchRunning = false
                    }
                }
            }

            LaunchedEffect(Unit) {
                // Aspetta che il modello sia caricato, poi controlla se serve il benchmark
                val appContext = benchContext.applicationContext
                val eng = com.arm.aichat.AiChat.getInferenceEngine(appContext)
                eng.state.first { it is com.arm.aichat.InferenceEngine.State.ModelReady }
                if (!BenchmarkStore.hasBenchmark(appContext)) {
                    benchPending = true  // mostra popup, non avvia ancora
                }
            }

            val context = LocalContext.current
            val prefs = remember { AppPrefs(context) }
            // Tema: segue il telefono finché non si usa il pulsante giorno/notte
            var themeMode by rememberSaveable { mutableStateOf(prefs.themeMode) }
            val dark = when (themeMode) {
                ThemeMode.Auto -> isSystemInDarkTheme()
                ThemeMode.Night -> true
                ThemeMode.Day -> false
            }
            var showSettings by rememberSaveable { mutableStateOf(false) }

            // Le icone delle barre di sistema restano leggibili anche se il tema scelto
            // non coincide con quello del telefono
            val view = LocalView.current
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }

            // Indietro chiude le Impostazioni
            BackHandler(enabled = showSettings) { showSettings = false }

            HyMT2TranslatorTheme(darkTheme = dark) {
                // Lo sfondo occupa tutto lo schermo, anche dietro le barre di sistema;
                // il contenuto resta nello spazio libero
                GlassBackground(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        modifier = Modifier.systemBarsPadding(),
                        isDark = dark,
                        onToggleTheme = {
                            themeMode = if (dark) ThemeMode.Day else ThemeMode.Night
                            prefs.themeMode = themeMode
                        },
                        onAutoTheme = {
                            themeMode = ThemeMode.Auto
                            prefs.themeMode = themeMode
                        },
                        onOpenSettings = { showSettings = true },
                        sharedAudioUri = sharedAudioUri,
                        onSharedAudioConsumed = { sharedAudioUri = null }
                    )

                    // Benchmark: sopra tutto, anche le Impostazioni
                    if (benchPending || benchRunning) {
                        GlassBackground(modifier = Modifier.fillMaxSize()) {
                            BenchmarkScreen(
                                pending = benchPending,
                                current = benchCurrent,
                                total = benchTotal,
                                description = benchDesc,
                                onStart = ::startBench,
                                modifier = Modifier.systemBarsPadding()
                            )
                        }
                    }

                    // Le Impostazioni stanno sopra: la schermata sotto resta in vita,
                    // così il testo scritto non va perso
                    AnimatedVisibility(
                        visible = showSettings,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(160))
                    ) {
                        GlassBackground(modifier = Modifier.fillMaxSize()) {
                            SettingsScreen(
                                onBack = { showSettings = false },
                                engine = com.arm.aichat.AiChat.getInferenceEngine(benchContext.applicationContext),
                                modifier = Modifier.systemBarsPadding()
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

private fun extractSharedAudio(intent: Intent?): Uri? {
    if (intent?.action != Intent.ACTION_SEND) return null
    val mime = intent.type ?: return null
    if (!mime.startsWith("audio/") && !mime.startsWith("video/") && mime != "*/*") return null
    @Suppress("DEPRECATION")
    return intent.getParcelableExtra(Intent.EXTRA_STREAM)
}

// Le due sezioni dell'app, nell'ordine in cui compaiono
private val TABS = listOf(
    // "Traduci" è spostato di 14 dp: la parola è più corta di "Chat" con l'icona e così sembra centrata
    GlassTab("Traduci", GlassIcons.Translate, endPadding = 14.dp),
    GlassTab("Chat", GlassIcons.Chat)
)

/**
 * Struttura principale: barra di tab in vetro in alto e pagine affiancate.
 * Si cambia sezione toccando una tab o scorrendo lateralmente: l'indicatore segue il dito
 * e la pagina che si allontana sfuma e si rimpicciolisce un po'.
 */
@Composable
fun MainScreen(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onAutoTheme: () -> Unit,
    onOpenSettings: () -> Unit,
    sharedAudioUri: Uri?,
    onSharedAudioConsumed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { TABS.size })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Carica il modello una sola volta, all'avvio dell'app (vale per Traduci e per Chat)
    LaunchedEffect(Unit) {
        val appContext = context.applicationContext
        ModelManager.ensureLoaded(appContext, AiChat.getInferenceEngine(appContext))
    }

    Column(modifier = modifier.fillMaxSize()) {
        GlassTabBar(
            position = pagerState.currentPage + pagerState.currentPageOffsetFraction,
            tabs = TABS,
            onSelect = { index ->
                scope.launch {
                    // Scorrimento di 560 ms con la curva del mockup: parte deciso e si ferma morbido
                    pagerState.animateScrollToPage(
                        index,
                        animationSpec = tween(560, easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f))
                    )
                }
            },
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp)
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            // Tiene vive anche la pagina accanto, così il testo scritto non va perso cambiando tab
            beyondViewportPageCount = 1
        ) { page ->
            // Quanto la pagina è lontana da quella al centro (0 = al centro, 1 = fuori)
            val distance = abs(pagerState.currentPage - page + pagerState.currentPageOffsetFraction)
                .coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = 1f - 0.75f * distance
                        val scale = 1f - 0.04f * distance
                        scaleX = scale
                        scaleY = scale
                    }
            ) {
                when (page) {
                    0 -> TranslatorScreen(
                        isDark = isDark,
                        onToggleTheme = onToggleTheme,
                        onAutoTheme = onAutoTheme,
                        onOpenSettings = onOpenSettings,
                        sharedAudioUri = sharedAudioUri,
                        onSharedAudioConsumed = onSharedAudioConsumed
                    )

                    else -> ChatScreen()
                }
            }
        }
    }
}