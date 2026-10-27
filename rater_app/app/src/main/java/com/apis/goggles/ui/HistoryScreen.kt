package com.apis.goggles.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apis.goggles.data.Goggle
import com.apis.goggles.data.Rating
import com.apis.goggles.data.SyncState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(goggle: Goggle, ratings: List<Rating>, onUndo: (Rating) -> Unit, onBack: () -> Unit) {
    val colors = LocalActionColors.current
    val mine = ratings.filter { it.goggle == goggle }.sortedByDescending { it.at }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Apis ${goggle.label} ratings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        if (mine.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No ratings yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            items(mine, key = { "${it.domain}@${it.at}" }) { r ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = colors.of(r.action).copy(alpha = 0.15f), contentColor = colors.of(r.action), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            r.describe(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(92.dp).padding(vertical = 6.dp, horizontal = 8.dp),
                        )
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(r.scope, style = MaterialTheme.typography.bodyLarge)
                        val status = when (r.state) {
                            SyncState.PENDING_ADD -> "Waiting to sync"
                            SyncState.SYNCED -> "On GitHub"
                            SyncState.PENDING_REMOVE -> "Will be removed on next sync"
                        }
                        Text(
                            status + (r.siblingOf?.let { " · copied from $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onUndo(r) }) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, if (r.state == SyncState.PENDING_REMOVE) "Keep" else "Undo")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
