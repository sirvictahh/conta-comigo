package pt.contacomigo.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import pt.contacomigo.app.api.ApiConfig
import pt.contacomigo.app.api.ApiHealthClient
import pt.contacomigo.app.auth.SessionManager
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val ioExecutor = Executors.newSingleThreadExecutor()

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)

        /*
         * Se não existir um token JWT guardado,
         * o utilizador é enviado para o ecrã de login.
         */
        if (!sessionManager.isLoggedIn()) {
            openLoginActivity()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main)
        ) { view, insets ->

            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }

        val btnExpenses = findViewById<Button>(R.id.btnExpenses)
        val btnMap = findViewById<Button>(R.id.btnMap)
        val btnInfo = findViewById<Button>(R.id.btnInfo)

        btnExpenses.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    ExpensesActivity::class.java
                )
            )
        }

        btnMap.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    MapActivity::class.java
                )
            )
        }

        btnInfo.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    InfoActivity::class.java
                )
            )
        }

        /*
         * Mantemos temporariamente o health check da API.
         * É útil nesta fase de desenvolvimento para confirmar
         * que Android e backend continuam comunicáveis.
         */
        checkApiHealth()
    }

    private fun checkApiHealth() {

        val baseUrl = ApiConfig.baseUrl()

        ioExecutor.execute {

            val result = ApiHealthClient.checkHealth(baseUrl)

            runOnUiThread {

                if (result.success) {

                    Toast.makeText(
                        this,
                        "API OK em $baseUrl",
                        Toast.LENGTH_SHORT
                    ).show()

                } else {

                    val message =
                        "API FALHOU em $baseUrl " +
                                "(${result.httpCode ?: "sem HTTP"}): " +
                                "${result.errorMessage ?: "erro"}"

                    Toast.makeText(
                        this,
                        message,
                        Toast.LENGTH_LONG
                    ).show()

                    Log.e(
                        "ContaComigo",
                        "$message | body=${result.rawBody ?: "null"}"
                    )
                }
            }
        }
    }

    /**
     * Abre o login e remove o MainActivity atual
     * do histórico de navegação.
     */
    private fun openLoginActivity() {

        val intent = Intent(
            this,
            LoginActivity::class.java
        ).apply {

            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        ioExecutor.shutdown()
    }
}