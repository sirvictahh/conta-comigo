package pt.contacomigo.app

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
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

    private lateinit var fusedLocationClient:
            FusedLocationProviderClient

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

    private var pendingLocationCallback:
            ((Double, Double) -> Unit)? =
        null

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

        fusedLocationClient =
            LocationServices
                .getFusedLocationProviderClient(
                    this
                )

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

        /*
         * Se chegámos aqui através de uma ocorrência,
         * abre automaticamente o formulário com:
         *
         * - título da ocorrência;
         * - latitude;
         * - longitude.
         */
        handleMapExpenseRequest(
            savedInstanceState
        )
    }

    /*
     * =====================================================
     * PEDIDO VINDO DO MAPA
     * =====================================================
     */

    private fun handleMapExpenseRequest(
        savedInstanceState: Bundle?
    ) {

        /*
         * Evita abrir novamente o formulário
         * depois de uma recriação da Activity.
         */
        if (
            savedInstanceState != null
        ) {
            return
        }

        val shouldCreate =
            intent.getBooleanExtra(
                EXTRA_CREATE_EXPENSE,
                false
            )

        if (!shouldCreate) {
            return
        }

        if (
            !intent.hasExtra(
                EXTRA_LATITUDE
            ) ||
            !intent.hasExtra(
                EXTRA_LONGITUDE
            )
        ) {
            return
        }

        val latitude =
            intent.getDoubleExtra(
                EXTRA_LATITUDE,
                0.0
            )

        val longitude =
            intent.getDoubleExtra(
                EXTRA_LONGITUDE,
                0.0
            )

        val initialTitle =
            intent.getStringExtra(
                EXTRA_INITIAL_TITLE
            )

        /*
         * Consumimos os extras.
         */
        intent.removeExtra(
            EXTRA_CREATE_EXPENSE
        )

        intent.removeExtra(
            EXTRA_LATITUDE
        )

        intent.removeExtra(
            EXTRA_LONGITUDE
        )

        intent.removeExtra(
            EXTRA_INITIAL_TITLE
        )

        showExpenseDialog(
            expense = null,
            initialLatitude =
                latitude,
            initialLongitude =
                longitude,
            initialTitle =
                initialTitle
        )
    }

    /*
     * =====================================================
     * CARREGAMENTO / LISTAGEM
     * =====================================================
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

            val (
                remoteExpenses,
                result
            ) =
                ExpenseApiClient.getAll(
                    baseUrl =
                        ApiConfig.baseUrl(),

                    token =
                        token
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

    private fun renderExpenses() {

        expensesContainer
            .removeAllViews()

        tvEmpty.visibility =
            if (
                expenses.isEmpty()
            ) {
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

    private fun createExpenseView(
        expense: Expense
    ): View {

        val density =
            resources
                .displayMetrics
                .density

        fun dp(
            value: Int
        ): Int =
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

        val detailsText =
            buildString {

                append(
                    formatMoney(
                        expense.amountCents
                    )
                )

                append(
                    " • "
                )

                append(
                    expense.category
                )

                if (
                    expense.latitude != null &&
                    expense.longitude != null
                ) {

                    append(
                        "\nLocalização: "
                    )

                    append(
                        String.format(
                            Locale.US,
                            "%.5f, %.5f",
                            expense.latitude,
                            expense.longitude
                        )
                    )
                }
            }

        val details =
            TextView(this).apply {

                text =
                    detailsText

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

    /*
     * =====================================================
     * FORMULÁRIO
     * =====================================================
     */

    private fun showExpenseDialog(
        expense: Expense?,
        initialLatitude: Double? = null,
        initialLongitude: Double? = null,
        initialTitle: String? = null
    ) {

        val density =
            resources
                .displayMetrics
                .density

        val padding =
            (20 * density)
                .toInt()

        var selectedLatitude:
                Double? =
            expense?.latitude
                ?: initialLatitude

        var selectedLongitude:
                Double? =
            expense?.longitude
                ?: initialLongitude

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
                    InputType
                        .TYPE_CLASS_TEXT

                setText(
                    expense?.title
                        ?: initialTitle
                        ?: ""
                )
            }

        val inputAmount =
            EditText(this).apply {

                hint =
                    "Valor (€)"

                inputType =
                    InputType
                        .TYPE_CLASS_NUMBER or
                            InputType
                                .TYPE_NUMBER_FLAG_DECIMAL

                if (
                    expense != null
                ) {

                    setText(
                        BigDecimal(
                            expense.amountCents
                        )
                            .movePointLeft(2)
                            .setScale(
                                2,
                                RoundingMode
                                    .UNNECESSARY
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
                    InputType
                        .TYPE_CLASS_TEXT

                setText(
                    expense?.category
                        ?: ""
                )
            }

        val locationStatus =
            TextView(this).apply {

                textSize =
                    14f

                setPadding(
                    0,
                    padding / 2,
                    0,
                    padding / 4
                )
            }

        val buttonUseLocation =
            Button(this).apply {

                text =
                    "Usar a minha localização"
            }

        val buttonRemoveLocation =
            Button(this).apply {

                text =
                    "Remover localização"
            }

        fun refreshLocationUi() {

            if (
                selectedLatitude != null &&
                selectedLongitude != null
            ) {

                locationStatus.text =
                    "Localização associada:\n" +
                            String.format(
                                Locale.US,
                                "%.5f, %.5f",
                                selectedLatitude,
                                selectedLongitude
                            )

                buttonRemoveLocation
                    .visibility =
                    View.VISIBLE

            } else {

                locationStatus.text =
                    "Sem localização associada."

                buttonRemoveLocation
                    .visibility =
                    View.GONE
            }
        }

        buttonUseLocation
            .setOnClickListener {

                locationStatus.text =
                    "A obter localização..."

                requestCurrentLocation {
                        latitude,
                        longitude ->

                    selectedLatitude =
                        latitude

                    selectedLongitude =
                        longitude

                    refreshLocationUi()

                    Toast.makeText(
                        this,
                        "Localização associada à despesa.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

        buttonRemoveLocation
            .setOnClickListener {

                selectedLatitude =
                    null

                selectedLongitude =
                    null

                refreshLocationUi()
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

        container.addView(
            locationStatus
        )

        container.addView(
            buttonUseLocation
        )

        container.addView(
            buttonRemoveLocation
        )

        refreshLocationUi()

        val dialog =
            AlertDialog.Builder(this)
                .setTitle(
                    if (
                        expense == null
                    ) {
                        "Nova despesa"
                    } else {
                        "Editar despesa"
                    }
                )
                .setView(
                    container
                )
                .setPositiveButton(
                    if (
                        expense == null
                    ) {
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

            dialog.getButton(
                AlertDialog
                    .BUTTON_POSITIVE
            ).setOnClickListener {

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

                if (
                    title.isBlank()
                ) {

                    inputTitle.error =
                        "Introduz uma descrição."

                    inputTitle
                        .requestFocus()

                    return@setOnClickListener
                }

                if (
                    amountCents == null
                ) {

                    inputAmount.error =
                        "Introduz um valor válido superior a 0."

                    inputAmount
                        .requestFocus()

                    return@setOnClickListener
                }

                if (
                    category.isBlank()
                ) {

                    inputCategory.error =
                        "Introduz uma categoria."

                    inputCategory
                        .requestFocus()

                    return@setOnClickListener
                }

                dialog.dismiss()

                if (
                    expense == null
                ) {

                    createExpense(
                        title =
                            title,

                        amountCents =
                            amountCents,

                        category =
                            category,

                        latitude =
                            selectedLatitude,

                        longitude =
                            selectedLongitude
                    )

                } else {

                    updateExpense(
                        expense =
                            expense,

                        title =
                            title,

                        amountCents =
                            amountCents,

                        category =
                            category,

                        latitude =
                            selectedLatitude,

                        longitude =
                            selectedLongitude
                    )
                }
            }
        }

        dialog.show()
    }

    /*
     * =====================================================
     * GPS
     * =====================================================
     */

    private fun requestCurrentLocation(
        callback:
            (Double, Double) -> Unit
    ) {

        val fineGranted =
            ContextCompat
                .checkSelfPermission(
                    this,
                    Manifest.permission
                        .ACCESS_FINE_LOCATION
                ) ==
                    PackageManager
                        .PERMISSION_GRANTED

        val coarseGranted =
            ContextCompat
                .checkSelfPermission(
                    this,
                    Manifest.permission
                        .ACCESS_COARSE_LOCATION
                ) ==
                    PackageManager
                        .PERMISSION_GRANTED

        if (
            !fineGranted &&
            !coarseGranted
        ) {

            pendingLocationCallback =
                callback

            ActivityCompat
                .requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission
                            .ACCESS_FINE_LOCATION,

                        Manifest.permission
                            .ACCESS_COARSE_LOCATION
                    ),
                    REQ_EXPENSE_LOCATION
                )

            return
        }

        fetchCurrentLocation(
            callback
        )
    }

    private fun fetchCurrentLocation(
        callback:
            (Double, Double) -> Unit
    ) {

        val permissionGranted =
            ContextCompat
                .checkSelfPermission(
                    this,
                    Manifest.permission
                        .ACCESS_FINE_LOCATION
                ) ==
                    PackageManager
                        .PERMISSION_GRANTED ||
                    ContextCompat
                        .checkSelfPermission(
                            this,
                            Manifest.permission
                                .ACCESS_COARSE_LOCATION
                        ) ==
                    PackageManager
                        .PERMISSION_GRANTED

        if (!permissionGranted) {
            return
        }

        try {

            val cancellationTokenSource =
                CancellationTokenSource()

            fusedLocationClient
                .getCurrentLocation(
                    Priority
                        .PRIORITY_HIGH_ACCURACY,

                    cancellationTokenSource
                        .token
                )
                .addOnSuccessListener { location ->

                    if (
                        location == null
                    ) {

                        Toast.makeText(
                            this,
                            "Não foi possível obter a localização atual.",
                            Toast.LENGTH_LONG
                        ).show()

                        return@addOnSuccessListener
                    }

                    callback(
                        location.latitude,
                        location.longitude
                    )
                }
                .addOnFailureListener {

                    Toast.makeText(
                        this,
                        "Erro ao obter a localização.",
                        Toast.LENGTH_LONG
                    ).show()
                }

        } catch (
            _: SecurityException
        ) {

            Toast.makeText(
                this,
                "Não existe permissão para utilizar a localização.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            requestCode !=
            REQ_EXPENSE_LOCATION
        ) {
            return
        }

        val granted =
            grantResults.isNotEmpty() &&
                    grantResults.any {
                        it ==
                                PackageManager
                                    .PERMISSION_GRANTED
                    }

        val callback =
            pendingLocationCallback

        pendingLocationCallback =
            null

        if (
            granted &&
            callback != null
        ) {

            fetchCurrentLocation(
                callback
            )

        } else {

            Toast.makeText(
                this,
                "A localização não foi autorizada.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /*
     * =====================================================
     * CRUD
     * =====================================================
     */

    private fun createExpense(
        title: String,
        amountCents: Long,
        category: String,
        latitude: Double?,
        longitude: Double?
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

                    token =
                        token,

                    title =
                        title,

                    amountCents =
                        amountCents,

                    category =
                        category,

                    latitude =
                        latitude,

                    longitude =
                        longitude
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
        category: String,
        latitude: Double?,
        longitude: Double?
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

                    token =
                        token,

                    id =
                        expense.id,

                    title =
                        title,

                    amountCents =
                        amountCents,

                    category =
                        category,

                    latitude =
                        latitude,

                    longitude =
                        longitude
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

                    if (
                        index >= 0
                    ) {

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

                    token =
                        token,

                    id =
                        expense.id
                )

            runOnUiThread {

                setLoading(false)

                if (
                    result.success
                ) {

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

    /*
     * =====================================================
     * UTILITÁRIOS
     * =====================================================
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
                    Locale.forLanguageTag(
                        "pt-PT"
                    )
                )

        return formatter.format(
            value
        )
    }

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

        } catch (
            _: Exception
        ) {
            null
        }
    }

    private fun setLoading(
        loading: Boolean
    ) {

        progress.visibility =
            if (
                loading
            ) {
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
            when (
                httpCode
            ) {

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

        pendingLocationCallback =
            null

        ioExecutor.shutdown()
    }

    companion object {

        private const val
                REQ_EXPENSE_LOCATION =
            2001

        const val EXTRA_CREATE_EXPENSE =
            "create_expense"

        const val EXTRA_LATITUDE =
            "expense_latitude"

        const val EXTRA_LONGITUDE =
            "expense_longitude"

        const val EXTRA_INITIAL_TITLE =
            "expense_initial_title"
    }
}