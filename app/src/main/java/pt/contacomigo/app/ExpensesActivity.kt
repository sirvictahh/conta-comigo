package pt.contacomigo.app

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import pt.contacomigo.app.api.ApiConfig
import pt.contacomigo.app.api.ExpenseApiClient
import pt.contacomigo.app.auth.SessionManager
import pt.contacomigo.app.data.Expense
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.Executors

class ExpensesActivity : AppCompatActivity() {

    private lateinit var sessionManager:
            SessionManager

    private lateinit var tvTotal:
            TextView

    private lateinit var tvEmpty:
            TextView

    private lateinit var progress:
            ProgressBar

    private lateinit var expensesContainer:
            LinearLayout

    private lateinit var btnAddExpense:
            Button

    private val expenses =
        mutableListOf<Expense>()

    private val ioExecutor =
        Executors.newSingleThreadExecutor()

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContentView(
            R.layout.activity_expenses
        )

        sessionManager =
            SessionManager(this)

        tvTotal =
            findViewById(
                R.id.tvExpensesTotal
            )

        tvEmpty =
            findViewById(
                R.id.tvExpensesEmpty
            )

        progress =
            findViewById(
                R.id.progressExpenses
            )

        expensesContainer =
            findViewById(
                R.id.expensesContainer
            )

        btnAddExpense =
            findViewById(
                R.id.btnAddExpense
            )

        btnAddExpense.setOnClickListener {
            showExpenseDialog(
                expense = null
            )
        }

        loadExpenses()
    }

    /**
     * Obtém todas as despesas do utilizador.
     */
    private fun loadExpenses() {

        val token =
            sessionManager.getToken()

        if (token == null) {

            Toast.makeText(
                this,
                "Sessão inválida.",
                Toast.LENGTH_LONG
            ).show()

            finish()
            return
        }

        setLoading(true)

        ioExecutor.execute {

            val (remoteExpenses, result) =
                ExpenseApiClient.getAll(
                    baseUrl =
                        ApiConfig.baseUrl(),
                    token = token
                )

            runOnUiThread {

                setLoading(false)

                if (
                    result.success &&
                    remoteExpenses != null
                ) {

                    expenses.clear()
                    expenses.addAll(
                        remoteExpenses
                    )

                    renderExpenses()

                } else {

                    showApiError(
                        result.httpCode,
                        result.errorMessage
                    )
                }
            }
        }
    }

    /**
     * Desenha a lista com base no conteúdo atual.
     */
    private fun renderExpenses() {

        expensesContainer.removeAllViews()

        tvEmpty.visibility =
            if (expenses.isEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }

        updateTotal()

        expenses.forEach { expense ->

            expensesContainer.addView(
                createExpenseView(
                    expense
                )
            )
        }
    }

    /**
     * Cria visualmente uma entrada da lista.
     */
    private fun createExpenseView(
        expense: Expense
    ): View {

        val density =
            resources.displayMetrics.density

        fun dp(value: Int): Int =
            (value * density)
                .toInt()

        val container =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(16),
                    dp(14),
                    dp(16),
                    dp(14)
                )

                setBackgroundColor(
                    getColor(
                        android.R.color
                            .transparent
                    )
                )
            }

        val title =
            TextView(this).apply {

                text =
                    expense.title

                textSize =
                    18f

                setTypeface(
                    typeface,
                    android.graphics
                        .Typeface.BOLD
                )
            }

        val details =
            TextView(this).apply {

                text =
                    "${formatMoney(expense.amountCents)} • ${expense.category}"

                textSize =
                    15f

                setPadding(
                    0,
                    dp(4),
                    0,
                    dp(8)
                )
            }

        val actions =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL
            }

        val editButton =
            Button(this).apply {

                text =
                    "Editar"

                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams
                            .WRAP_CONTENT,
                        1f
                    )

                setOnClickListener {
                    showExpenseDialog(
                        expense
                    )
                }
            }

        val deleteButton =
            Button(this).apply {

                text =
                    "Remover"

                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams
                            .WRAP_CONTENT,
                        1f
                    ).apply {
                        marginStart =
                            dp(8)
                    }

                setOnClickListener {
                    showDeleteConfirmation(
                        expense
                    )
                }
            }

        actions.addView(
            editButton
        )

        actions.addView(
            deleteButton
        )

        container.addView(
            title
        )

        container.addView(
            details
        )

        container.addView(
            actions
        )

        val params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams
                    .MATCH_PARENT,
                LinearLayout.LayoutParams
                    .WRAP_CONTENT
            )

        params.bottomMargin =
            dp(12)

        container.layoutParams =
            params

        return container
    }

    /**
     * Diálogo utilizado tanto para criação
     * como para edição.
     */
    private fun showExpenseDialog(
        expense: Expense?
    ) {

        val density =
            resources.displayMetrics.density

        val padding =
            (20 * density).toInt()

        val container =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    padding,
                    0,
                    padding,
                    0
                )
            }

        val inputTitle =
            EditText(this).apply {

                hint =
                    "Descrição"

                inputType =
                    InputType.TYPE_CLASS_TEXT

                setText(
                    expense?.title ?: ""
                )
            }

        val inputAmount =
            EditText(this).apply {

                hint =
                    "Valor (€)"

                inputType =
                    InputType.TYPE_CLASS_NUMBER or
                            InputType
                                .TYPE_NUMBER_FLAG_DECIMAL

                if (expense != null) {

                    setText(
                        BigDecimal(
                            expense.amountCents
                        )
                            .movePointLeft(2)
                            .setScale(
                                2,
                                RoundingMode.UNNECESSARY
                            )
                            .toPlainString()
                    )
                }
            }

        val inputCategory =
            EditText(this).apply {

                hint =
                    "Categoria (ex.: Alimentação)"

                inputType =
                    InputType.TYPE_CLASS_TEXT

                setText(
                    expense?.category ?: ""
                )
            }

        container.addView(
            inputTitle
        )

        container.addView(
            inputAmount
        )

        container.addView(
            inputCategory
        )

        val dialog =
            AlertDialog.Builder(this)
                .setTitle(
                    if (expense == null) {
                        "Nova despesa"
                    } else {
                        "Editar despesa"
                    }
                )
                .setView(container)
                .setPositiveButton(
                    if (expense == null) {
                        "Adicionar"
                    } else {
                        "Guardar"
                    },
                    null
                )
                .setNegativeButton(
                    "Cancelar",
                    null
                )
                .create()

        dialog.setOnShowListener {

            dialog
                .getButton(
                    AlertDialog
                        .BUTTON_POSITIVE
                )
                .setOnClickListener {

                    val title =
                        inputTitle.text
                            .toString()
                            .trim()

                    val category =
                        inputCategory.text
                            .toString()
                            .trim()

                    val amountCents =
                        parseAmountToCents(
                            inputAmount.text
                                .toString()
                        )

                    if (title.isBlank()) {

                        inputTitle.error =
                            "Introduz uma descrição."

                        inputTitle
                            .requestFocus()

                        return@setOnClickListener
                    }

                    if (amountCents == null) {

                        inputAmount.error =
                            "Introduz um valor válido superior a 0."

                        inputAmount
                            .requestFocus()

                        return@setOnClickListener
                    }

                    if (category.isBlank()) {

                        inputCategory.error =
                            "Introduz uma categoria."

                        inputCategory
                            .requestFocus()

                        return@setOnClickListener
                    }

                    dialog.dismiss()

                    if (expense == null) {

                        createExpense(
                            title,
                            amountCents,
                            category
                        )

                    } else {

                        updateExpense(
                            expense,
                            title,
                            amountCents,
                            category
                        )
                    }
                }
        }

        dialog.show()
    }

    private fun createExpense(
        title: String,
        amountCents: Long,
        category: String
    ) {

        val token =
            sessionManager.getToken()
                ?: return

        setLoading(true)

        ioExecutor.execute {

            val (
                createdExpense,
                result
            ) =
                ExpenseApiClient.create(
                    baseUrl =
                        ApiConfig.baseUrl(),
                    token = token,
                    title = title,
                    amountCents =
                        amountCents,
                    category =
                        category
                )

            runOnUiThread {

                setLoading(false)

                if (
                    result.success &&
                    createdExpense != null
                ) {

                    expenses.add(
                        0,
                        createdExpense
                    )

                    renderExpenses()

                    Toast.makeText(
                        this,
                        "Despesa adicionada.",
                        Toast.LENGTH_SHORT
                    ).show()

                } else {

                    showApiError(
                        result.httpCode,
                        result.errorMessage
                    )
                }
            }
        }
    }

    private fun updateExpense(
        expense: Expense,
        title: String,
        amountCents: Long,
        category: String
    ) {

        val token =
            sessionManager.getToken()
                ?: return

        setLoading(true)

        ioExecutor.execute {

            val (
                updatedExpense,
                result
            ) =
                ExpenseApiClient.update(
                    baseUrl =
                        ApiConfig.baseUrl(),
                    token = token,
                    id = expense.id,
                    title = title,
                    amountCents =
                        amountCents,
                    category =
                        category
                )

            runOnUiThread {

                setLoading(false)

                if (
                    result.success &&
                    updatedExpense != null
                ) {

                    val index =
                        expenses.indexOfFirst {
                            it.id ==
                                    updatedExpense.id
                        }

                    if (index >= 0) {
                        expenses[index] =
                            updatedExpense
                    }

                    renderExpenses()

                    Toast.makeText(
                        this,
                        "Despesa atualizada.",
                        Toast.LENGTH_SHORT
                    ).show()

                } else {

                    showApiError(
                        result.httpCode,
                        result.errorMessage
                    )
                }
            }
        }
    }

    private fun showDeleteConfirmation(
        expense: Expense
    ) {

        AlertDialog.Builder(this)
            .setTitle(
                "Remover despesa"
            )
            .setMessage(
                "Pretende remover \"${expense.title}\"?"
            )
            .setPositiveButton(
                "Remover"
            ) { _, _ ->

                deleteExpense(
                    expense
                )
            }
            .setNegativeButton(
                "Cancelar",
                null
            )
            .show()
    }

    private fun deleteExpense(
        expense: Expense
    ) {

        val token =
            sessionManager.getToken()
                ?: return

        setLoading(true)

        ioExecutor.execute {

            val result =
                ExpenseApiClient.delete(
                    baseUrl =
                        ApiConfig.baseUrl(),
                    token = token,
                    id = expense.id
                )

            runOnUiThread {

                setLoading(false)

                if (result.success) {

                    expenses.removeAll {
                        it.id ==
                                expense.id
                    }

                    renderExpenses()

                    Toast.makeText(
                        this,
                        "Despesa removida.",
                        Toast.LENGTH_SHORT
                    ).show()

                } else {

                    showApiError(
                        result.httpCode,
                        result.errorMessage
                    )
                }
            }
        }
    }

    /**
     * Atualiza o total apresentado no topo.
     */
    private fun updateTotal() {

        val totalCents =
            expenses.sumOf {
                it.amountCents
            }

        tvTotal.text =
            "Total: ${formatMoney(totalCents)}"
    }

    private fun formatMoney(
        cents: Long
    ): String {

        val value =
            BigDecimal(cents)
                .movePointLeft(2)

        val formatter =
            NumberFormat
                .getCurrencyInstance(
                    Locale(
                        "pt",
                        "PT"
                    )
                )

        return formatter.format(
            value
        )
    }

    /**
     * Converte, por exemplo:
     *
     * 12,50 -> 1250
     * 12.50 -> 1250
     */
    private fun parseAmountToCents(
        raw: String
    ): Long? {

        val normalized =
            raw.trim()
                .replace(
                    ",",
                    "."
                )

        val value =
            normalized
                .toBigDecimalOrNull()
                ?: return null

        if (
            value <=
            BigDecimal.ZERO
        ) {
            return null
        }

        return try {

            value
                .movePointRight(2)
                .setScale(
                    0,
                    RoundingMode.HALF_UP
                )
                .longValueExact()

        } catch (_: Exception) {
            null
        }
    }

    private fun setLoading(
        loading: Boolean
    ) {

        progress.visibility =
            if (loading) {
                View.VISIBLE
            } else {
                View.GONE
            }

        btnAddExpense.isEnabled =
            !loading
    }

    private fun showApiError(
        httpCode: Int?,
        errorMessage: String?
    ) {

        val message =
            when (httpCode) {

                401 ->
                    "Sessão expirada ou inválida."

                404 ->
                    "A despesa já não existe."

                null ->
                    "Não foi possível comunicar com a API."

                else ->
                    errorMessage
                        ?: "Ocorreu um erro."
            }

        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        ioExecutor.shutdown()
    }
}