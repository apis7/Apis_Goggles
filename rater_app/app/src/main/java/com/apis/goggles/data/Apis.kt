package com.apis.goggles.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

class HttpError(val code: Int, message: String) : IOException(message)

private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
private val json = Json { ignoreUnknownKeys = true }

private suspend fun call(request: Request): String = withContext(Dispatchers.IO) {
    http.newCall(request).execute().use { resp ->
        val body = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) throw HttpError(resp.code, "HTTP ${resp.code}: ${body.take(200)}")
        body
    }
}

private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content

object BraveApi {
    /** One page (≤20 results) of Brave web search. Brave allows offsets 0–9. */
    suspend fun search(key: String, query: String, offset: Int = 0, count: Int = 20): List<Hit> {
        val url = "https://api.search.brave.com/res/v1/web/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("count", count.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("result_filter", "web")
            .addQueryParameter("safesearch", "off")
            .build()
        val body = call(
            Request.Builder().url(url)
                .header("Accept", "application/json")
                .header("X-Subscription-Token", key)
                .build(),
        )
        val results = json.parseToJsonElement(body).jsonObject["web"]?.jsonObject?.get("results")?.jsonArray
            ?: return emptyList()
        return results.mapNotNull { el ->
            val o = el.jsonObject
            val link = o.str("url") ?: return@mapNotNull null
            Hit(
                title = cleanHtml(o.str("title").orEmpty()),
                url = link,
                snippet = cleanHtml(o.str("description").orEmpty()),
                favicon = o["meta_url"]?.jsonObject?.str("favicon"),
            )
        }
    }

    fun cleanHtml(s: String): String = s.replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#x27;", "'").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
}

object GitHubApi {
    data class File(val text: String, val sha: String)

    private fun request(url: String, token: String) = Request.Builder().url(url)
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .apply { if (token.isNotBlank()) header("Authorization", "Bearer $token") }

    private fun contentsUrl(repo: String, path: String) = "https://api.github.com/repos/$repo/contents/$path"

    /** Works without a token for a public repo (60 requests/hour). */
    suspend fun get(repo: String, branch: String, path: String, token: String): File {
        val body = call(request("${contentsUrl(repo, path)}?ref=$branch", token).build())
        val o = json.parseToJsonElement(body).jsonObject
        val text = String(Base64.getMimeDecoder().decode(o.str("content").orEmpty()), Charsets.UTF_8)
        return File(text, o.str("sha").orEmpty())
    }

    /** Read-modify-write with one retry if the file changed underneath us. Returns the committed text. */
    suspend fun update(
        repo: String, branch: String, path: String, token: String, message: String,
        edit: (String) -> String,
    ): String {
        repeat(2) { attempt ->
            val current = get(repo, branch, path, token)
            val updated = edit(current.text)
            if (updated == current.text) return updated
            val payload = buildJsonObject {
                put("message", message)
                put("branch", branch)
                put("sha", current.sha)
                put("content", Base64.getEncoder().encodeToString(updated.toByteArray(Charsets.UTF_8)))
            }.toString()
            try {
                call(request(contentsUrl(repo, path), token).put(payload.toRequestBody("application/json".toMediaType())).build())
                return updated
            } catch (e: HttpError) {
                if (attempt == 1 || e.code != 409) throw e
            }
        }
        error("unreachable")
    }
}
