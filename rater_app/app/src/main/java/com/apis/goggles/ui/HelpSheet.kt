package com.apis.goggles.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apis.goggles.data.Action

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSheet(onDismiss: () -> Unit) {
    val colors = LocalActionColors.current
    val uri = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("How Brave Goggles ratings work", style = MaterialTheme.typography.titleLarge)
            Text(
                "A Goggle re-ranks Brave Search results. Each rating you give becomes one rule in the goggle file.",
                style = MaterialTheme.typography.bodyMedium,
            )
            ScaleRow(Action.BOOST, colors.boost, "1 – 10", "Pushes the site up. Higher number, stronger push. Brave's default strength is 1.")
            ScaleRow(Action.DOWNRANK, colors.downrank, "1 – 10", "Pushes the site down but keeps it in results.")
            ScaleRow(Action.DISCARD, colors.discard, "", "Removes the site from results entirely.")
            ScaleRow(Action.NEUTRAL, colors.neutral, "", "No rule. The app just remembers you looked, so it never asks again.")

            Text("When rules overlap", style = MaterialTheme.typography.titleMedium)
            Text(
                "Goggles favour surfacing content. If a page matches several rules: Discard wins, then the strongest Boost, " +
                    "then the mildest Downrank. So boost=3 beats boost=2, and downrank=1 beats downrank=2.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("What a rating covers", style = MaterialTheme.typography.titleMedium)
            Text(
                "Rules are written as site=example.com, which covers every subdomain (news.example.com, m.example.com). " +
                    "When a same-name site on another domain turns up (example.co.uk), the app copies your rating to it automatically " +
                    "and shows it in History, where you can undo it.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("When it takes effect", style = MaterialTheme.typography.titleMedium)
            Text(
                "Ratings are saved on the phone, then committed to the goggle file on GitHub when you tap sync or leave the app. " +
                    "Brave re-reads the file from GitHub on its own schedule.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { uri.openUri("https://github.com/brave/goggles-quickstart/blob/main/getting-started.md") }) {
                Text("Brave's Goggles guide")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ScaleRow(action: Action, color: androidx.compose.ui.graphics.Color, range: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(color = color.copy(alpha = 0.15f), contentColor = color, shape = RoundedCornerShape(10.dp)) {
            Box(Modifier.width(96.dp).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(action.label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                    if (range.isNotEmpty()) Text(range, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(top = 4.dp))
    }
}
