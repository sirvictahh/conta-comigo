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
import java.util.concurrent.Executors
import pt.contacomigo.app.api.ApiConfig
import pt.contacomigo.app.api.ApiHealthClient

class MainActivity : AppCompatActivity() {

    private val ioExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val btnExpenses = findViewById<Button>(R.id.btnExpenses)
        val btnMap = findViewById<Button>(R.id.btnMap)
        val btnInfo = findViewById<Button>(R.id.btnInfo)

        btnExpenses.setOnClickListener {
            startActivity(Intent(this, ExpensesActivity::class.java))
        }

        btnMap.setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }

        btnInfo.setOnClickListener {
            startActivity(Intent(this, InfoActivity::class.java))
        }

        val baseUrl = ApiConfig.baseUrl()

        ioExecutor.execute {
            val result = ApiHealthClient.checkHealth(baseUrl)

            runOnUiThread {
                if (result.success) {
                    Toast.makeText(this, "API OK em $baseUrl", Toast.LENGTH_SHORT).show()
                } else {
                    val msg =
                        "API FALHOU em $baseUrl (${result.httpCode ?: "sem HTTP"}): ${result.errorMessage ?: "erro"}"
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    Log.e("ContaComigo", "$msg | body=${result.rawBody ?: "null"}")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ioExecutor.shutdown()
    }
}