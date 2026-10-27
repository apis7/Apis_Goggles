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
    onSave: (braveKey: String, githubToken: String, repo: String, branch: String, queries: Map<Goggle, List<String>>) -> Unit,
    onResetPaging: (Goggle) -> Unit,
    onBack: () -> Unit,
) {
    val uri = LocalUriHandler.current
    var brave by remember { mutableStateOf(state.braveKey) }
    var token by remember { mutableStateOf(state.githubToken) }
    var repo by remember { mutableStateOf(state.repo) }
    var branch by remember { mutableStateOf(state.branch) }
    val queries = remember { Goggle.entries.associateWith { mutableStateOf(state.of(it).queries.joinToString("\n")) } }

    fun save() {
        onSave(brave, token, repo, branch, queries.mapValues { (_, v) -> v.value.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct() })
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
                Section("Apis ${g.label} search topics")
                OutlinedTextField(
                    value = queries.getValue(g).value,
                    onValueChange = { queries.getValue(g).value = it },
                    label = { Text("One search per line") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { onResetPaging(g) }) { Text("Search these topics again from page 1") }
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
