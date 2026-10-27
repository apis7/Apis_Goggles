package com.apis.goggles.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.apis.goggles.data.AppState
import com.apis.goggles.data.Goggle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: AppState,
    onSave: (braveKey: String, githubToken: String, repo: String, branch: String) -> Unit,
    onResetPaging: (Goggle) -> Unit,
    onBack: () -> Unit,
) {
    val uri = LocalUriHandler.current
    var brave by remember { mutableStateOf(state.braveKey) }
    var token by remember { mutableStateOf(state.githubToken) }
    var repo by remember { mutableStateOf(state.repo) }
    var branch by remember { mutableStateOf(state.branch) }

    fun save() {
        onSave(brave, token, repo, branch)
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { TextButton(onClick = ::save) { Text("Save") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("Brave Search")
            OutlinedTextField(
                value = brave, onValueChange = { brave = it }, label = { Text("API key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { uri.openUri("https://api-dashboard.search.brave.com/") }) { Text("Get a key at api-dashboard.search.brave.com") }

            Section("GitHub sync")
            OutlinedTextField(
                value = token, onValueChange = { token = it }, label = { Text("Personal access token") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Fine-grained token, this repo only, Contents: read & write") },
            )
            TextButton(onClick = { uri.openUri("https://github.com/settings/personal-access-tokens/new") }) { Text("Create a token on GitHub") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = repo, onValueChange = { repo = it }, label = { Text("Repo") }, singleLine = true, modifier = Modifier.weight(2f))
                OutlinedTextField(value = branch, onValueChange = { branch = it }, label = { Text("Branch") }, singleLine = true, modifier = Modifier.weight(1f))
            }

            Goggle.entries.forEach { g ->
                val gs = state.of(g)
                val used = gs.queries.count { (gs.offsets[it] ?: 0) > 0 }
                Section("Apis ${g.label} search terms")
                Text(
                    "${gs.queries.size} terms from ${g.termsPath}, picked at random; $used searched so far. " +
                        "Edit search_terms.xlsx, export and push; the app picks them up on its next start.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { onResetPaging(g) }) { Text("Start all terms over from page 1") }
            }
            Button(onClick = ::save, modifier = Modifier.fillMaxWidth()) { Text("Save") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}
