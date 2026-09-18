package pt.contacomigo.app.api

import org.json.JSONArray
import org.json.JSONObject
import pt.contacomigo.app.data.Occurrence
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object ApiHealthClient {

    fun checkHealth(
        baseUrl: String,
        timeoutMillis: Int = 5000
    ): HealthResult {

        val url = URL("${baseUrl.trimEnd('/')}/health")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis

            val code = connection.responseCode

            val stream =
                if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val body = stream
                ?.bufferedReader()
                ?.use(BufferedReader::readText)
                ?.trim()

            val ok =
                body?.contains("\"ok\"") == true &&
                        body.contains("true")

            if (ok) {
                HealthResult(
                    success = true,
                    httpCode = code,
                    rawBody = body,
                    errorMessage = null
                )
            } else {
                HealthResult(
                    success = false,
                    httpCode = code,
                    rawBody = body,
                    errorMessage = "Resposta inesperada no /health"
                )
            }

        } catch (e: Exception) {

            HealthResult(
                success = false,
                httpCode = null,
                rawBody = null,
                errorMessage = e.message ?: "Erro desconhecido"
            )

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

    /**
     * GET /occurrences
     */
    fun getAll(
        baseUrl: String,
        token: String
    ): Pair<List<Occurrence>?, ApiSimpleResult> {

        val url = URL(
            "${baseUrl.trimEnd('/')}/occurrences"
        )

        val connection =
            url.openConnection() as HttpURLConnection

        return try {

            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS

            applyAuthentication(
                connection = connection,
                token = token
            )

            val code = connection.responseCode
            val body = readBody(connection, code)

            if (code in 200..299) {

                val list = parseOccurrences(
                    body ?: "[]"
                )

                Pair(
                    list,
                    ApiSimpleResult(
                        success = true,
                        httpCode = code,
                        rawBody = body
                    )
                )

            } else {

                Pair(
                    null,
                    ApiSimpleResult(
                        success = false,
                        httpCode = code,
                        errorMessage = extractErrorMessage(body),
                        rawBody = body
                    )
                )
            }

        } catch (e: Exception) {

            Pair(
                null,
                ApiSimpleResult(
                    success = false,
                    httpCode = null,
                    errorMessage =
                        e.message ?: "Erro de comunicação com a API."
                )
            )

        } finally {
            connection.disconnect()
        }
    }

    /**
     * POST /occurrences
     */
    fun create(
        baseUrl: String,
        token: String,
        occurrence: Occurrence
    ): ApiSimpleResult {

        val url = URL(
            "${baseUrl.trimEnd('/')}/occurrences"
        )

        val connection =
            url.openConnection() as HttpURLConnection

        return try {

            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.doOutput = true

            connection.setRequestProperty(
                "Content-Type",
                "application/json; charset=utf-8"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            applyAuthentication(
                connection = connection,
                token = token
            )

            val json = JSONObject().apply {
                put("id", occurrence.id)
                put("title", occurrence.title)
                put("latitude", occurrence.latitude)
                put("longitude", occurrence.longitude)
                put(
                    "createdAtEpochMillis",
                    occurrence.createdAtEpochMillis
                )
            }

            connection.outputStream.use { output ->
                output.write(
                    json.toString()
                        .toByteArray(Charsets.UTF_8)
                )
            }

            val code = connection.responseCode
            val body = readBody(connection, code)

            if (code in 200..299) {

                ApiSimpleResult(
                    success = true,
                    httpCode = code,
                    rawBody = body
                )

            } else {

                ApiSimpleResult(
                    success = false,
                    httpCode = code,
                    errorMessage = extractErrorMessage(body),
                    rawBody = body
                )
            }

        } catch (e: Exception) {

            ApiSimpleResult(
                success = false,
                httpCode = null,
                errorMessage =
                    e.message ?: "Erro de comunicação com a API."
            )

        } finally {
            connection.disconnect()
        }
    }

    /**
     * DELETE /occurrences/:id
     */
    fun delete(
        baseUrl: String,
        token: String,
        id: String
    ): ApiSimpleResult {

        val url = URL(
            "${baseUrl.trimEnd('/')}/occurrences/$id"
        )

        val connection =
            url.openConnection() as HttpURLConnection

        return try {

            connection.requestMethod = "DELETE"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS

            applyAuthentication(
                connection = connection,
                token = token
            )

            val code = connection.responseCode
            val body = readBody(connection, code)

            if (code in 200..299) {

                ApiSimpleResult(
                    success = true,
                    httpCode = code,
                    rawBody = body
                )

            } else {

                ApiSimpleResult(
                    success = false,
                    httpCode = code,
                    errorMessage = extractErrorMessage(body),
                    rawBody = body
                )
            }

        } catch (e: Exception) {

            ApiSimpleResult(
                success = false,
                httpCode = null,
                errorMessage =
                    e.message ?: "Erro de comunicação com a API."
            )

        } finally {
            connection.disconnect()
        }
    }

    /**
     * Acrescenta o JWT a todas as rotas protegidas.
     */
    private fun applyAuthentication(
        connection: HttpURLConnection,
        token: String
    ) {

        connection.setRequestProperty(
            "Authorization",
            "Bearer ${token.trim()}"
        )
    }

    private fun readBody(
        connection: HttpURLConnection,
        code: Int
    ): String? {

        val stream =
            if (code in 200..399) {
                connection.inputStream
            } else {
                connection.errorStream
            }

        if (stream == null) {
            return null
        }

        return BufferedReader(
            InputStreamReader(stream)
        ).use {
            it.readText()
        }
    }

    private fun extractErrorMessage(
        rawBody: String?
    ): String {

        if (rawBody.isNullOrBlank()) {
            return "Erro devolvido pela API."
        }

        return try {

            val json = JSONObject(rawBody)

            json.optString("error")
                .trim()
                .takeIf { it.isNotEmpty() }
                ?: "Erro devolvido pela API."

        } catch (_: Exception) {
            "Resposta inválida devolvida pela API."
        }
    }

    private fun parseOccurrences(
        rawJson: String
    ): List<Occurrence> {

        val array = JSONArray(rawJson)
        val list = mutableListOf<Occurrence>()

        for (i in 0 until array.length()) {

            val item = array.getJSONObject(i)

            list.add(
                Occurrence(
                    id = item.getString("id"),
                    title = item.getString("title"),
                    latitude = item.getDouble("latitude"),
                    longitude = item.getDouble("longitude"),
                    createdAtEpochMillis =
                        item.getLong("createdAtEpochMillis")
                )
            )
        }

        return list
    }

    private const val TIMEOUT_MILLIS = 5000
}