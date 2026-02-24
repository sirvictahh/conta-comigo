package pt.contacomigo.app.auth

import android.content.Context

/**
 * Guarda o token JWT localmente (SharedPreferences).
 * Simples, estável e suficiente para este projeto.
 */
class SessionManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token.trim())
            .apply()
    }

    fun getToken(): String? {
        val t = prefs.getString(KEY_TOKEN, null)?.trim()
        return if (t.isNullOrBlank()) null else t
    }

    fun isLoggedIn(): Boolean = getToken() != null

    fun clear() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "conta_comigo_session"
        private const val KEY_TOKEN = "jwt_token"
    }
}