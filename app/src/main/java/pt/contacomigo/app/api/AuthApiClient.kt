package pt.contacomigo.app.api

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente responsável pela autenticação através da API.
 */
object AuthApiClient {

    data class AuthResult(
        val success: Boolean,
        val httpCode: Int? = null,
        val token: String? = null,
        val errorMessage: String? = null
    )

    /**
     * POST /auth/login
     */
    fun login(
        baseUrl: String,
        email: String,
        password: String
    ): AuthResult {

        val body = JSONObject().apply {
            put("email", email.trim())
            put("password", password)
        }

        val result = postJson(
            baseUrl = baseUrl,
            path = "/auth/login",
            body = body
        )

        if (!result.success) {
            return AuthResult(
                success = false,
                httpCode = result.httpCode,
                token = null,
                errorMessage = result.errorMessage
            )
        }

        val token = try {
            JSONObject(result.rawBody ?: "{}")
                .optString("token")
                .trim()
                .takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }

        if (token == null) {
            return AuthResult(
                success = false,
                httpCode = result.httpCode,
                token = null,
                errorMessage = "A API não devolveu um token de autenticação."
            )
        }

        return AuthResult(
            success = true,
            httpCode = result.httpCode,
            token = token,
            errorMessage = null
        )
    }

    /**
     * POST /auth/register
     */
    fun register(
        baseUrl: String,
        name: String,
        email: String,
        password: String
    ): AuthResult {

        val body = JSONObject().apply {
            put("name", name.trim())
            put("email", email.trim())
            put("password", password)
        }

        val result = postJson(
            baseUrl = baseUrl,
            path = "/auth/register",
            body = body
        )

        return AuthResult(
            success = result.success,
            httpCode = result.httpCode,
            token = null,
            errorMessage = result.errorMessage
        )
    }

    /**
     * Executa um pedido POST com JSON.
     */
    private fun postJson(
        baseUrl: String,
        path: String,
        body: JSONObject
    ): HttpResult {

        val url = URL("${baseUrl.trimEnd('/')}$path")
        val connection = url.openConnection() as HttpURLConnection

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

            connection.outputStream.use { output ->
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                output.write(bytes)
            }

            val code = connection.responseCode
            val responseBody = readBody(connection, code)

            if (code in 200..299) {
                HttpResult(
                    success = true,
                    httpCode = code,
                    rawBody = responseBody,
                    errorMessage = null
                )
            } else {
                HttpResult(
                    success = false,
                    httpCode = code,
                    rawBody = responseBody,
                    errorMessage = extractErrorMessage(responseBody)
                )
            }
        } catch (e: Exception) {
            HttpResult(
                success = false,
                httpCode = null,
                rawBody = null,
                errorMessage = e.message ?: "Erro de comunicação com a API."
            )
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Lê o corpo da resposta HTTP.
     */
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
        ).use { reader ->
            reader.readText()
        }
    }

    /**
     * Extrai a mensagem "error" devolvida pela API.
     */
    private fun extractErrorMessage(rawBody: String?): String {

        if (rawBody.isNullOrBlank()) {
            return "A API devolveu um erro sem mensagem."
        }

        return try {
            val json = JSONObject(rawBody)

            json.optString("error")
                .trim()
                .takeIf { it.isNotEmpty() }
                ?: "Erro desconhecido devolvido pela API."
        } catch (_: Exception) {
            "Resposta inválida devolvida pela API."
        }
    }

    private data class HttpResult(
        val success: Boolean,
        val httpCode: Int? = null,
        val rawBody: String? = null,
        val errorMessage: String? = null
    )

    private const val TIMEOUT_MILLIS = 5000
}