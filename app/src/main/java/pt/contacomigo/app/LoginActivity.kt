package pt.contacomigo.app

import android.content.Intent
import android.text.InputType
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import pt.contacomigo.app.api.ApiConfig
import pt.contacomigo.app.api.AuthApiClient
import pt.contacomigo.app.auth.SessionManager
import java.util.concurrent.Executors

class LoginActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnGoRegister: Button

    private val ioExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)

        /*
         * Se já existir um token guardado, não mostramos novamente
         * o ecrã de login.
         */
        if (sessionManager.isLoggedIn()) {
            openMainActivity()
            return
        }

        setContentView(R.layout.activity_login)

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnGoRegister = findViewById(R.id.btnGoRegister)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()

            if (!validateLoginFields(email, password)) {
                return@setOnClickListener
            }

            performLogin(email, password)
        }

        btnGoRegister.setOnClickListener {
            showRegisterDialog()
        }
    }

    /**
     * Validação local antes de contactar a API.
     */
    private fun validateLoginFields(
        email: String,
        password: String
    ): Boolean {

        if (email.isBlank()) {
            etEmail.error = "Introduz o email."
            etEmail.requestFocus()
            return false
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Introduz um email válido."
            etEmail.requestFocus()
            return false
        }

        if (password.isBlank()) {
            etPassword.error = "Introduz a palavra-passe."
            etPassword.requestFocus()
            return false
        }

        if (password.length < 8) {
            etPassword.error = "A palavra-passe deve ter pelo menos 8 caracteres."
            etPassword.requestFocus()
            return false
        }

        return true
    }

    /**
     * Executa o login fora da thread principal.
     */
    private fun performLogin(
        email: String,
        password: String,
        accountJustCreated: Boolean = false
    ) {

        setButtonsEnabled(false)

        val baseUrl = ApiConfig.baseUrl()

        ioExecutor.execute {

            val result = AuthApiClient.login(
                baseUrl = baseUrl,
                email = email,
                password = password
            )

            runOnUiThread {

                setButtonsEnabled(true)

                if (result.success && !result.token.isNullOrBlank()) {

                    sessionManager.saveToken(result.token)

                    val message =
                        if (accountJustCreated) {
                            "Conta criada e sessão iniciada."
                        } else {
                            "Sessão iniciada."
                        }

                    Toast.makeText(
                        this,
                        message,
                        Toast.LENGTH_SHORT
                    ).show()

                    openMainActivity()

                } else {

                    Toast.makeText(
                        this,
                        getFriendlyError(
                            result.httpCode,
                            result.errorMessage
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Mostra o formulário de registo sem criar outro ecrã.
     */
    private fun showRegisterDialog() {

        val padding = (20 * resources.displayMetrics.density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 0, padding, 0)
        }

        val inputName = EditText(this).apply {
            hint = "Nome"
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }

        val inputEmail = EditText(this).apply {
            hint = "Email"
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }

        val inputPassword = EditText(this).apply {
            hint = "Palavra-passe"
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        container.addView(inputName)
        container.addView(inputEmail)
        container.addView(inputPassword)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Criar conta")
            .setView(container)
            .setPositiveButton("Criar", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {

                    val name = inputName.text.toString().trim()
                    val email = inputEmail.text.toString().trim()
                    val password = inputPassword.text.toString()

                    if (name.length < 2) {
                        inputName.error = "O nome deve ter pelo menos 2 caracteres."
                        inputName.requestFocus()
                        return@setOnClickListener
                    }

                    if (
                        email.isBlank() ||
                        !android.util.Patterns.EMAIL_ADDRESS
                            .matcher(email)
                            .matches()
                    ) {
                        inputEmail.error = "Introduz um email válido."
                        inputEmail.requestFocus()
                        return@setOnClickListener
                    }

                    if (password.length < 8) {
                        inputPassword.error =
                            "A palavra-passe deve ter pelo menos 8 caracteres."
                        inputPassword.requestFocus()
                        return@setOnClickListener
                    }

                    dialog.dismiss()

                    performRegister(
                        name = name,
                        email = email,
                        password = password
                    )
                }
        }

        dialog.show()
    }

    /**
     * Cria a conta na API.
     *
     * Quando o registo termina com sucesso,
     * fazemos login automaticamente.
     */
    private fun performRegister(
        name: String,
        email: String,
        password: String
    ) {

        setButtonsEnabled(false)

        val baseUrl = ApiConfig.baseUrl()

        ioExecutor.execute {

            val result = AuthApiClient.register(
                baseUrl = baseUrl,
                name = name,
                email = email,
                password = password
            )

            runOnUiThread {

                if (!result.success) {

                    setButtonsEnabled(true)

                    Toast.makeText(
                        this,
                        getFriendlyError(
                            result.httpCode,
                            result.errorMessage
                        ),
                        Toast.LENGTH_LONG
                    ).show()

                    return@runOnUiThread
                }

                /*
                 * Coloca os dados criados também no formulário principal.
                 */
                etEmail.setText(email)
                etPassword.setText(password)

                /*
                 * Depois de criar a conta, iniciamos sessão automaticamente.
                 */
                performLogin(
                    email = email,
                    password = password,
                    accountJustCreated = true
                )
            }
        }
    }

    /**
     * Traduz os erros mais comuns da API para mensagens
     * mais adequadas para o utilizador.
     */
    private fun getFriendlyError(
        httpCode: Int?,
        apiMessage: String?
    ): String {

        return when (httpCode) {

            400 ->
                "Verifica os dados introduzidos."

            401 ->
                "Email ou palavra-passe incorretos."

            409 ->
                "Já existe uma conta com esse email."

            500 ->
                "Ocorreu um erro no servidor."

            null ->
                "Não foi possível comunicar com a API."

            else ->
                apiMessage ?: "Ocorreu um erro inesperado."
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        btnLogin.isEnabled = enabled
        btnGoRegister.isEnabled = enabled
    }

    /**
     * Abre o menu principal e remove o login
     * do histórico de navegação.
     */
    private fun openMainActivity() {

        val intent = Intent(
            this,
            MainActivity::class.java
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