package me.senkuro.unofficial.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

class ApiException(message: String) : Exception(message)

/** Добавляет заголовки клиента ко всем запросам (API и картинки). */
private class HeadersInterceptor(private val token: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
        if (original.header("User-Agent") == null) builder.header("User-Agent", SenkuroConfig.USER_AGENT)
        builder.header("Referer", SenkuroConfig.BASE_URL + "/")
        if (SenkuroConfig.isApiHost(original.url.host)) {
            builder.header("Origin", SenkuroConfig.BASE_URL)
            builder.header("Accept", "application/json")
            builder.header("App-Id", SenkuroConfig.appId)
            builder.header("App-Version", SenkuroConfig.appVersion)
            // Токен уходит только на хосты API, не на картинки.
            token()?.let { builder.header("Authorization", "Bearer $it") }
        }
        return chain.proceed(builder.build())
    }
}

fun createHttpClient(token: () -> String?): OkHttpClient = OkHttpClient.Builder()
    .addInterceptor(HeadersInterceptor(token))
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

/** Тонкий GraphQL-клиент: запросы лежат в assets/graphql/<имя>.graphql. */
class SenkuroApi(private val context: Context, private val client: OkHttpClient) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private val queryCache = HashMap<String, String>()

    private fun query(file: String): String = synchronized(queryCache) {
        queryCache.getOrPut(file) {
            context.assets.open("graphql/$file.graphql").bufferedReader().use { it.readText() }
        }
    }

    /** Результат сырого запроса для debug-консоли. */
    class RawResult(val code: Int, val millis: Long, val body: String, val error: String?)

    /** Отправляет произвольный GraphQL-запрос на выбранный API и возвращает ответ как есть (токен подставляется как обычно). */
    suspend fun raw(apiUrl: String, operation: String?, queryText: String, variablesText: String): RawResult = withContext(Dispatchers.IO) {
        val vars = try {
            if (variablesText.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(variablesText).jsonObject
        } catch (e: Exception) {
            return@withContext RawResult(0, 0, "", "Переменные: некорректный JSON (${e.message?.take(120)})")
        }
        val payload = buildJsonObject {
            if (!operation.isNullOrBlank()) put("operationName", operation)
            put("query", queryText)
            put("variables", vars)
        }.toString()
        val request = Request.Builder().url(apiUrl).post(payload.toRequestBody("application/json".toMediaType())).build()
        val started = System.currentTimeMillis()
        var code = 0
        var body = ""
        var failure: String? = null
        try {
            client.newCall(request).execute().use { r ->
                code = r.code
                body = r.body?.string().orEmpty()
            }
        } catch (e: Exception) {
            failure = e.message ?: e.javaClass.simpleName
        }
        val ms = System.currentTimeMillis() - started
        ApiTrace.sink?.let { sink ->
            runCatching { sink(ApiCall(started, "Консоль · " + (operation?.takeIf { it.isNotBlank() } ?: "запрос"), queryText, ApiTrace.safeVariables(operation.orEmpty(), vars.toString()), code, ms, body.take(30_000), failure)) }
        }
        RawResult(code, ms, body, failure)
    }

    suspend fun <T> execute(
        operation: String,
        file: String,
        variables: JsonObject,
        deserializer: DeserializationStrategy<T>,
    ): T = executeText(operation, query(file), variables, deserializer)

    /** То же, что [execute], но текст запроса передаётся строкой (для запросов, собираемых на лету). */
    suspend fun <T> executeText(
        operation: String,
        queryText: String,
        variables: JsonObject,
        deserializer: DeserializationStrategy<T>,
    ): T = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("operationName", operation)
            put("query", queryText)
            put("variables", variables)
        }.toString()

        val request = Request.Builder()
            .url(SenkuroConfig.API_URL)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        val sink = ApiTrace.sink
        val started = System.currentTimeMillis()
        var code = 0
        var body = ""
        var failure: String? = null
        try {
            client.newCall(request).execute().use { response ->
                code = response.code
                val text = response.body?.string().orEmpty()
                body = text
                val root = try {
                    json.parseToJsonElement(text).jsonObject
                } catch (e: Exception) {
                    if (!response.isSuccessful) throw ApiException("HTTP ${response.code}")
                    throw ApiException("Некорректный ответ сервера")
                }
                val data = root["data"]
                if (data == null || data is JsonNull) {
                    val message = root["errors"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("message")?.jsonPrimitive?.contentOrNull
                    throw ApiException(message ?: "HTTP ${response.code}")
                }
                if (root["errors"] != null && root["errors"] !is JsonNull) failure = "Частичные ошибки в ответе (errors)"
                json.decodeFromJsonElement(deserializer, data)
            }
        } catch (e: Throwable) {
            failure = (e.message ?: e.javaClass.simpleName)
            throw e
        } finally {
            if (sink != null) runCatching {
                sink(ApiCall(started, operation, queryText, ApiTrace.safeVariables(operation, variables.toString()), code,
                    System.currentTimeMillis() - started, body.take(30_000), failure))
            }
        }
    }
}
