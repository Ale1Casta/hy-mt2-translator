package com.example.hy_mt2translator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hy_mt2translator.ui.theme.GlassCard
import com.example.hy_mt2translator.ui.theme.PrimaryButton
import com.example.hy_mt2translator.ui.theme.LocalGlass

/**
 * Schermata mostrata al primo avvio (o su richiesta) mentre il benchmark è in corso.
 * Compare davanti a tutto il resto: il modello deve essere già caricato quando viene mostrata.
 */
@Composable
fun BenchmarkScreen(
    pending: Boolean,
    current: Int,
    total: Int,
    description: String,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val g = LocalGlass.current
    val fraction = if (total > 0) current.toFloat() / total else 0f

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            padding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (pending) "Ottimizzazione consigliata" else "Ottimizzazione in corso",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Il telefono misurerà la velocità dei due modelli con diverse configurazioni di thread e sceglierà le migliori. Dura fino a circa 5 minuti e va fatto una sola volta: lascia l'app aperta e il telefono a riposo.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = g.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            if (pending) {
                PrimaryButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text("Avvia l'ottimizzazione", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRoundRect(color = g.trackFill, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                        drawRoundRect(color = g.barFill, size = androidx.compose.ui.geometry.Size(size.width * fraction, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                    }
                }
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (description.isNotEmpty()) {
                        Text(text = "Test $current di $total", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = g.accent)
                        Text(text = description, fontSize = 13.sp, color = g.textMuted)
                    } else {
                        CircularProgressIndicator(color = g.accent, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.height(4.dp))
                        Text(text = "Carico il modello…", fontSize = 13.sp, color = g.textMuted)
                    }
                }
            }

            Text(
                text = "Puoi usare l'app normalmente dalla prossima volta.",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = g.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}