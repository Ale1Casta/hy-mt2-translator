package com.example.hy_mt2translator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hy_mt2translator.ui.theme.GlassIcons
import com.example.hy_mt2translator.ui.theme.GlassKind
import com.example.hy_mt2translator.ui.theme.LocalGlass
import com.example.hy_mt2translator.ui.theme.PrimaryButton
import com.example.hy_mt2translator.ui.theme.glass

/** Testo sulla scadenza di una chat. */
private fun expiryText(daysLeft: Int): String = when {
    daysLeft <= 1 -> "Scade entro 24 ore · esportala se ti serve"
    else -> "Scade tra $daysLeft giorni"
}

/** Una card dell'elenco: toccandola si apre la chat, il cestino la elimina. */
@Composable
private fun ChatCard(
    record: ChatRecord,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val g = LocalGlass.current
    val daysLeft = record.daysLeft()
    val expiring = daysLeft <= 1
    val shape = RoundedCornerShape(22.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .glass(shape, GlassKind.Panel)
            // Bordo giallo quando manca poco, così non si perde una chat per distrazione
            .then(if (expiring) Modifier.border(1.dp, g.warnBorder, shape) else Modifier)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(shape)
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(start = 18.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = record.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // Etichetta della lingua
                Box(
                    modifier = Modifier
                        .background(g.chipFill, RoundedCornerShape(12.dp))
                        .border(1.dp, g.chipBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${record.languageFlag} ${record.languageNameIt}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = g.chipText
                    )
                }
            }
            Text(
                text = record.previewText,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = g.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = GlassIcons.Clock,
                    contentDescription = null,
                    tint = if (expiring) g.warn else g.textMuted,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = expiryText(daysLeft),
                    fontSize = 12.sp,
                    fontWeight = if (expiring) FontWeight.Medium else FontWeight.Normal,
                    color = if (expiring) g.warn else g.textMuted
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(end = 6.dp, bottom = 6.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = GlassIcons.Trash,
                    contentDescription = "Elimina la chat ${record.title}",
                    tint = g.textMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Elenco delle chat salvate. */
@Composable
fun ChatListScreen(
    chats: List<ChatRecord>,
    snackbarHostState: SnackbarHostState,
    onOpen: (ChatRecord) -> Unit,
    onNew: () -> Unit,
    onDelete: (ChatRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val g = LocalGlass.current

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Le chat si eliminano da sole dopo alcuni giorni senza usarle " +
                        "(si imposta in ogni chat, di solito $CHAT_EXPIRY_DAYS). " +
                        "Il testo resta solo su questo telefono.",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = g.textMuted,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp)
            )
            if (chats.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nessuna chat. Tocca «Nuova chat» per iniziare.",
                        fontSize = 16.sp,
                        color = g.textMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    // Spazio in fondo, così l'ultima card non finisce sotto il pulsante
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(chats, key = { it.id }) { record ->
                        ChatCard(
                            record = record,
                            onOpen = { onOpen(record) },
                            onDelete = { onDelete(record) }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 92.dp)
        ) { data ->
            // Avviso in stile: sfondo opaco (sotto passano le card) e azione in corallo
            val snackShape = RoundedCornerShape(20.dp)
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .background(g.sheetBackground, snackShape)
                    .border(1.dp, g.glassBorder, snackShape)
                    .padding(start = 18.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = data.visuals.message,
                    fontSize = 14.sp,
                    color = g.text,
                    modifier = Modifier.weight(1f, fill = false)
                )
                data.visuals.actionLabel?.let { label ->
                    Box(
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .clickable(role = Role.Button) { data.performAction() }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = g.accent
                        )
                    }
                }
            }
        }

        PrimaryButton(
            onClick = onNew,
            height = 56.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 26.dp)
        ) {
            Icon(
                imageVector = GlassIcons.Plus,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Nuova chat",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}