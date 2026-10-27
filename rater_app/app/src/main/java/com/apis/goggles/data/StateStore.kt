package com.apis.goggles.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/** All app state lives in one small JSON file in private storage. */
class StateStore(private val context: Context) {
    private val file = File(context.filesDir, "state.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun load(): AppState = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString(AppState.serializer(), file.readText()) }.getOrDefault(AppState())
    }

    suspend fun save(state: AppState) = withContext(Dispatchers.IO) {
        val tmp = File(file.parentFile, "state.json.tmp")
        tmp.writeText(json.encodeToString(AppState.serializer(), state))
        tmp.renameTo(file)
    }

    suspend fun asset(name: String): String = withContext(Dispatchers.IO) {
        context.assets.open(name).bufferedReader().use { it.readText() }
    }
}

/** Unrated rows of the bundled news seed CSV: (domain, topic). */
fun parseSeedCsv(text: String): List<Pair<String, String>> =
    text.lineSequence().drop(1).mapNotNull { line ->
        val cols = splitCsv(line)
        if (cols.size < 3 || cols.getOrNull(3).orEmpty().isNotBlank()) null
        else cols[1].trim().lowercase() to cols[2].trim()
    }.filter { it.first.isNotEmpty() }.toList()

private fun splitCsv(line: String): List<String> {
    val out = ArrayList<String>()
    val sb = StringBuilder()
    var quoted = false
    for (c in line) {
        when {
            c == '"' -> quoted = !quoted
            c == ',' && !quoted -> { out += sb.toString(); sb.clear() }
            else -> sb.append(c)
        }
    }
    out += sb.toString()
    return out
}

/** search_terms.txt: one term per line, blank lines and # comments ignored. */
fun parseTerms(text: String): List<String> =
    text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.distinct().toList()
