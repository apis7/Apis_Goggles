package com.apis.goggles.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.apis.goggles.data.Action
import com.apis.goggles.data.Candidate
import com.apis.goggles.data.Goggle
import com.apis.goggles.data.GoggleIndex

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RateTopBar(pending: Int, syncing: Boolean, onSync: () -> Unit, onHelp: () -> Unit, onHistory: () -> Unit, onSettings: () -> Unit) {
    TopAppBar(
        title = { Text("Goggle Rater") },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        actions = {
            IconButton(onClick = onHelp) { Icon(Icons.AutoMirrored.Outlined.HelpOutline, "How ratings work") }
            IconButton(onClick = onSync, enabled = !syncing) {
                if (syncing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    BadgedBox(badge = { if (pending > 0) Badge { Text("$pending") } }) {
                        Icon(Icons.Outlined.CloudUpload, "Sync to GitHub")
                    }
                }
            }
            IconButton(onClick = onHistory) { Icon(Icons.Outlined.History, "History") }
            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings") }
        },
    )
}

@Composable
fun RateScreen(
    goggle: Goggle,
    goggleText: String?,
    queue: List<Candidate>,
    searching: Boolean,
    hasBraveKey: Boolean,
    onSelect: (Goggle) -> Unit,
    onRate: (Candidate, Action, Int) -> Unit,
    onLater: (Candidate) -> Unit,
    onNeedPreview: (Candidate) -> Unit,
    onFetchMore: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ruleCount = remember(goggleText) { goggleText?.let { GoggleIndex(it).ruleCount } ?: 0 }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Goggle.entries.forEachIndexed { i, g ->
                SegmentedButton(
                    selected = g == goggle,
                    onClick = { onSelect(g) },
                    shape = SegmentedButtonDefaults.itemShape(i, Goggle.entries.size),
                ) { Text("Apis ${g.label}") }
            }
        }
        Text(
            "$ruleCount site rules · ${queue.size} to review" + if (searching) " · searching…" else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp, start = 4.dp),
        )

        val current = queue.firstOrNull()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                targetState = current,
                contentKey = { it?.domain },
                transitionSpec = {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
                },
                label = "candidate",
            ) { c ->
                if (c == null) {
                    EmptyState(searching, hasBraveKey, onFetchMore, onSettings)
                } else {
                    LaunchedEffect(c.domain) { onNeedPreview(c) }
                    CandidateCard(c)
                }
            }
        }

        if (current != null) {
            RatingPanel(
                key = current.domain,
                onRate = { a, s -> onRate(current, a, s) },
                onLater = { onLater(current) },
            )
        }
    }
}

@Composable
private fun CandidateCard(c: Candidate) {
    val context = LocalContext.current
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    ElevatedCard(Modifier.fillMaxSize(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Favicon(c, Modifier.size(52.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.domain, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (c.source.startsWith("Seed list")) c.source else "Found via “${c.source}”",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { open("https://${c.domain}") }) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open site", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                "Rating covers ${c.domain}, every *.${c.domain} subdomain, and other ${c.domain.substringBefore('.')}.* domains when they turn up.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
            if (c.hits.isEmpty()) {
                Text(
                    "No snippets yet. Tap ↗ to look at the site.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            c.hits.forEach { h ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open(h.url) }.padding(vertical = 8.dp, horizontal = 4.dp),
                ) {
                    Text(h.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        h.url.removePrefix("https://").removePrefix("http://").removePrefix("www."),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (h.snippet.isNotBlank()) {
                        Text(h.snippet, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Favicon(c: Candidate, modifier: Modifier) {
    val fallback = @Composable {
        Box(
            modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                c.domain.take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
    SubcomposeAsyncImage(
        model = c.hits.firstNotNullOfOrNull { it.favicon } ?: "https://${c.domain}/favicon.ico",
        contentDescription = null,
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        loading = { fallback() },
        error = { fallback() },
    )
}

@Composable
private fun RatingPanel(key: String, onRate: (Action, Int) -> Unit, onLater: () -> Unit) {
    val colors = LocalActionColors.current
    var picking by remember(key) { mutableStateOf<Action?>(null) }
    Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(Action.DISCARD, Action.DOWNRANK, Action.NEUTRAL, Action.BOOST).forEach { a ->
                val color = colors.of(a)
                val chosen = picking == a
                Surface(
                    onClick = {
                        when (a) {
                            Action.DISCARD, Action.NEUTRAL -> onRate(a, 0)
                            else -> picking = if (chosen) null else a
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = if (chosen) color else color.copy(alpha = 0.12f),
                    contentColor = if (chosen) MaterialTheme.colorScheme.surface else color,
                    border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(a.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
        AnimatedVisibility(picking != null) {
            val a = picking ?: Action.BOOST
            val color = colors.of(a)
            Column(Modifier.padding(top = 12.dp)) {
                Text(
                    "${a.label} strength — 1 is gentle, 10 is strongest",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                    (1..10).forEach { n ->
                        Surface(
                            onClick = { onRate(a, n) },
                            shape = CircleShape,
                            color = color.copy(alpha = 0.10f + 0.07f * n),
                            contentColor = if (n >= 6) MaterialTheme.colorScheme.surface else color,
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("$n", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        TextButton(onClick = onLater, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Not sure — ask me later")
        }
    }
}

@Composable
private fun EmptyState(searching: Boolean, hasBraveKey: Boolean, onFetchMore: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            searching -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Searching for sites you haven't rated…", textAlign = TextAlign.Center)
            }
            !hasBraveKey -> {
                Text("Nothing left to review", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Add a Brave Search API key and the app will search your topics for new sites.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onSettings) { Text("Open Settings") }
            }
            else -> {
                Text("All caught up", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onFetchMore) { Text("Find more sites") }
            }
        }
    }
}
