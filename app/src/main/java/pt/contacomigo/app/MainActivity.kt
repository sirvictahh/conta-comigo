package pt.contacomigo.app

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Mantém o comportamento "edge-to-edge" (padding automático por causa da status bar/navigation bar)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val btnExpenses = findViewById<Button>(R.id.btnExpenses)
        val btnMap = findViewById<Button>(R.id.btnMap)
        val btnInfo = findViewById<Button>(R.id.btnInfo)

        // Exemplo completo (faz o botão responder já)
        btnExpenses.setOnClickListener {
            showNotImplemented("Ecrã de Despesas")
        }

        // TODO: Faz o botão "Mapa" chamar showNotImplemented("Ecrã do Mapa")
        // btnMap.setOnClickListener { ... }

        // TODO: Faz o botão "Informações" chamar showNotImplemented("Ecrã de Informações")
        // btnInfo.setOnClickListener { ... }
    }

    private fun showNotImplemented(featureName: String) {
        Toast.makeText(this, "$featureName (a implementar)", Toast.LENGTH_SHORT).show()
    }
}