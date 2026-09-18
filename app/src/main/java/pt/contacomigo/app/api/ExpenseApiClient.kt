package pt.contacomigo.app.api

import org.json.JSONArray
import org.json.JSONObject
import pt.contacomigo.app.data.Expense
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object ExpenseApiClient {

    fun getAll(
        baseUrl: String,
        token: String
    ): Pair<List<Expense>?, ApiSimpleResult> {

        val url = URL(
            "${baseUrl.trimEnd('/')}/expenses"
        )

        val connection =
            url.openConnection() as HttpURLConnection

        return try {

            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS

            applyAuthentication(
                connection,
                token
            )

            val code = connection.responseCode
            val body = readBody(
                connection,
                code
            )

            if (code in 200..299) {

                val expenses =
                    parseExpenses(
                        body ?: "[]"
                    )

                Pair(
                    expenses,
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
                        errorMessage =
                            extractErrorMessage(body),
                        rawBody = body
                    )
                )
            }

        } catch (e: Exception) {

            Pair(
                null,
                ApiSimpleResult(
                    success = false,
                    errorMessage =
                        e.message
                            ?: "Erro de comunicação com a API."
                )
            )

        } finally {
            connection.disconnect()
        }
    }

    fun create(
        baseUrl: String,
        token: String,
        title: String,
        amountCents: Long,
        category: String
    ): Pair<Expense?, ApiSimpleResult> {

        val url = URL(
            "${baseUrl.trimEnd('/')}/expenses"
        )

        val connection =
            url.openConnection() as HttpURLConnection

        return try {

            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.doOutput = true

            configureJsonRequest(
                connection,
                token
            )

            val json = JSONObject().apply {
                put("title", title)
                put("amountCents", amountCents)
                put("category", category)
            }

            writeJson(
                connection,
                json
            )

            val code = connection.responseCode
            val body = readBody(
                connection,
                code
            )

            if (code in 200..299) {

                val expense =
                    body?.let {
                        parseExpense(
                            JSONObject(it)
                        )
                    }

                Pair(
                    expense,
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
                        errorMessage =
                            extractErrorMessage(body),
                        rawBody = body
                    )
                )
            }

        } catch (e: Exception) {

            Pair(
                null,
                ApiSimpleResult(
                    success = false,
                    errorMessage =
                        e.message
                            ?: "Erro de comunicação com a API."
                )
            )

        } finally {
            connection.disconnect()
        }
    }

    fun update(
        baseUrl: String,
        token: String,
        id: String,
        title: String,
        amountCents: Long,
        category: String
    ): Pair<Expense?, ApiSimpleResult> {

        val url = URL(
            "${baseUrl.trimEnd('/')}/expenses/$id"
        )

        val connection =
            url.openConnection() as HttpURLConnection

        return try {

            connection.requestMethod = "PUT"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.doOutput = true

            configureJsonRequest(
                connection,
                token
            )

            val json = JSONObject().apply {
                put("title", title)
                put("amountCents", amountCents)
                put("category", category)
            }

            writeJson(
                connection,
                json
            )

            val code = connection.responseCode
            val body = readBody(
                connection,
                code
            )

            if (code in 200..299) {

                val expense =
                    body?.let {
                        parseExpense(
                            JSONObject(it)
                        )
                    }

                Pair(
                    expense,
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
                        errorMessage =
                            extractErrorMessage(body),
                        rawBody = body
                    )
                )
            }

        } catch (e: Exception) {

            Pair(
                null,
                ApiSimpleResult(
                    success = false,
                    errorMessage =
                        e.message
                            ?: "Erro de comunicação com a API."
                )
            )

        } finally {
            connection.disconnect()
        }
    }

    fun delete(
        baseUrl: String,
        token: String,
        id: String
    ): ApiSimpleResult {

        val url = URL(
            "${baseUrl.trimEnd('/')}/expenses/$id"
        )

        val connection =
            url.openConnection() as HttpURLConnection

        return try {

            connection.requestMethod = "DELETE"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS

            applyAuthentication(
                connection,
                token
            )

            val code = connection.responseCode
            val body = readBody(
                connection,
                code
            )

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
                    errorMessage =
                        extractErrorMessage(body),
                    rawBody = body
                )
            }

        } catch (e: Exception) {

            ApiSimpleResult(
                success = false,
                errorMessage =
                    e.message
                        ?: "Erro de comunicação com a API."
            )

        } finally {
            connection.disconnect()
        }
    }

    private fun configureJsonRequest(
        connection: HttpURLConnection,
        token: String
    ) {

        connection.setRequestProperty(
            "Content-Type",
            "application/json; charset=utf-8"
        )

        connection.setRequestProperty(
            "Accept",
            "application/json"
        )

        applyAuthentication(
            connection,
            token
        )
    }

    private fun applyAuthentication(
        connection: HttpURLConnection,
        token: String
    ) {

        connection.setRequestProperty(
            "Authorization",
            "Bearer ${token.trim()}"
        )
    }

    private fun writeJson(
        connection: HttpURLConnection,
        json: JSONObject
    ) {

        connection.outputStream.use {
            it.write(
                json.toString()
                    .toByteArray(
                        Charsets.UTF_8
                    )
            )
        }
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

    private fun parseExpenses(
        rawJson: String
    ): List<Expense> {

        val array =
            JSONArray(rawJson)

        val expenses =
            mutableListOf<Expense>()

        for (i in 0 until array.length()) {
            expenses.add(
                parseExpense(
                    array.getJSONObject(i)
                )
            )
        }

        return expenses
    }

    private fun parseExpense(
        json: JSONObject
    ): Expense {

        return Expense(
            id = json.getString("id"),
            title = json.getString("title"),
            amountCents =
                json.getLong("amountCents"),
            category =
                json.getString("category"),
            createdAtEpochMillis =
                json.getLong(
                    "createdAtEpochMillis"
                )
        )
    }

    private fun extractErrorMessage(
        body: String?
    ): String {

        if (body.isNullOrBlank()) {
            return "Erro devolvido pela API."
        }

        return try {

            JSONObject(body)
                .optString("error")
                .takeIf {
                    it.isNotBlank()
                }
                ?: "Erro devolvido pela API."

        } catch (_: Exception) {

            "Resposta inválida devolvida pela API."
        }
    }

    private const val TIMEOUT_MILLIS =
        5000
}