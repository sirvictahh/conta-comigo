package pt.contacomigo.app.api

import org.json.JSONArray
import org.json.JSONObject
import pt.contacomigo.app.data.Occurrence
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object ApiHealthClient {

    /**
     * Faz GET a {baseUrl}/health e devolve true se receber JSON com {"ok":true}.
     * Implementação minimalista: HttpURLConnection + parsing simples.
     */
    fun checkHealth(baseUrl: String, timeoutMillis: Int = 5000): HealthResult {
        val url = URL("${baseUrl.trimEnd('/')}/health")
        val connection = (url.openConnection() as HttpURLConnection)

        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use(BufferedReader::readText).trim()

            val ok = body.contains("\"ok\"") && body.contains("true")
            if (ok) {
                HealthResult(success = true, httpCode = code, rawBody = body, errorMessage = null)
            } else {
                HealthResult(success = false, httpCode = code, rawBody = body, errorMessage = "Resposta inesperada no /health")
            }
        } catch (e: Exception) {
            HealthResult(success = false, httpCode = null, rawBody = null, errorMessage = e.message ?: "Erro desconhecido")
        } finally {
            connection.disconnect()
        }
    }

    data class HealthResult(
        val success: Boolean,
        val httpCode: Int?,
        val rawBody: String?,
        val errorMessage: String?
    )
}

data class ApiSimpleResult(
    val success: Boolean,
    val httpCode: Int? = null,
    val errorMessage: String? = null,
    val rawBody: String? = null
)

object OccurrenceApiClient {

    fun getAll(baseUrl: String): Pair<List<Occurrence>?, ApiSimpleResult> {
        val url = URL("$baseUrl/occurrences")
        val conn = (url.openConnection() as HttpURLConnection)

        return try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            val code = conn.responseCode
            val body = readBody(conn, code)

            if (code in 200..299) {
                val list = parseOccurrences(body ?: "[]")
                Pair(list, ApiSimpleResult(success = true, httpCode = code, rawBody = body))
            } else {
                Pair(null, ApiSimpleResult(false, code, "HTTP error", body))
            }
        } catch (e: Exception) {
            Pair(null, ApiSimpleResult(false, null, e.message, null))
        } finally {
            conn.disconnect()
        }
    }

    fun create(baseUrl: String, occurrence: Occurrence): ApiSimpleResult {
        val url = URL("$baseUrl/occurrences")
        val conn = (url.openConnection() as HttpURLConnection)

        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")

            val json = JSONObject().apply {
                put("id", occurrence.id)
                put("title", occurrence.title)
                put("latitude", occurrence.latitude)
                put("longitude", occurrence.longitude)
                put("createdAtEpochMillis", occurrence.createdAtEpochMillis)
            }

            conn.outputStream.use { os ->
                val bytes = json.toString().toByteArray(Charsets.UTF_8)
                os.write(bytes)
            }

            val code = conn.responseCode
            val body = readBody(conn, code)

            if (code in 200..299) {
                ApiSimpleResult(true, code, null, body)
            } else {
                ApiSimpleResult(false, code, "HTTP error", body)
            }
        } catch (e: Exception) {
            ApiSimpleResult(false, null, e.message, null)
        } finally {
            conn.disconnect()
        }
    }

    fun delete(baseUrl: String, id: String): ApiSimpleResult {
        val url = URL("$baseUrl/occurrences/$id")
        val conn = (url.openConnection() as HttpURLConnection)

        return try {
            conn.requestMethod = "DELETE"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            val code = conn.responseCode
            val body = readBody(conn, code)

            if (code in 200..299) {
                ApiSimpleResult(true, code, null, body)
            } else {
                ApiSimpleResult(false, code, "HTTP error", body)
            }
        } catch (e: Exception) {
            ApiSimpleResult(false, null, e.message, null)
        } finally {
            conn.disconnect()
        }
    }

    private fun readBody(conn: HttpURLConnection, code: Int): String? {
        val stream = if (code in 200..399) conn.inputStream else conn.errorStream
        if (stream == null) return null

        return BufferedReader(InputStreamReader(stream)).use { it.readText() }
    }

    private fun parseOccurrences(rawJson: String): List<Occurrence> {
        val arr = JSONArray(rawJson)
        val list = mutableListOf<Occurrence>()

        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                Occurrence(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    latitude = o.getDouble("latitude"),
                    longitude = o.getDouble("longitude"),
                    createdAtEpochMillis = o.getLong("createdAtEpochMillis")
                )
            )
        }

        return list
    }
}