package pt.contacomigo.app.api

import android.os.Build

/**
 * Configuração central da API.
 *
 * Regras:
 * - Emulador Android -> 10.0.2.2 (aponta para o PC host)
 * - Telemóvel físico -> 127.0.0.1 (com adb reverse tcp:3000 tcp:3000)
 */
object ApiConfig {

    private const val DEFAULT_PORT = 3000

    fun baseUrl(port: Int = DEFAULT_PORT): String {
        val host = if (isEmulator()) "10.0.2.2" else "127.0.0.1"
        return "http://$host:$port"
    }

    private fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT ?: ""
        val model = Build.MODEL ?: ""
        val manufacturer = Build.MANUFACTURER ?: ""
        val brand = Build.BRAND ?: ""
        val device = Build.DEVICE ?: ""
        val product = Build.PRODUCT ?: ""

        return fingerprint.contains("generic", ignoreCase = true) ||
                fingerprint.contains("unknown", ignoreCase = true) ||
                model.contains("google_sdk", ignoreCase = true) ||
                model.contains("Emulator", ignoreCase = true) ||
                model.contains("Android SDK built for x86", ignoreCase = true) ||
                manufacturer.contains("Genymotion", ignoreCase = true) ||
                (brand.startsWith("generic", ignoreCase = true) && device.startsWith("generic", ignoreCase = true)) ||
                product.contains("sdk", ignoreCase = true) ||
                product.contains("emulator", ignoreCase = true) ||
                product.contains("simulator", ignoreCase = true)
    }
}