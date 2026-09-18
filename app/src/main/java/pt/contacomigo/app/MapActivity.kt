package pt.contacomigo.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import pt.contacomigo.app.api.ApiConfig
import pt.contacomigo.app.api.ExpenseApiClient
import pt.contacomigo.app.api.OccurrenceApiClient
import pt.contacomigo.app.auth.SessionManager
import pt.contacomigo.app.data.Expense
import pt.contacomigo.app.data.Occurrence
import pt.contacomigo.app.data.OccurrenceRepository
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

class MapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var repository: OccurrenceRepository
    private lateinit var occurrences: MutableList<Occurrence>

    private lateinit var sessionManager: SessionManager

    private val ioExecutor =
        Executors.newSingleThreadExecutor()

    private var hasCenteredOnExpense = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().userAgentValue =
            packageName

        setContentView(R.layout.activity_map)

        sessionManager =
            SessionManager(this)

        mapView =
            findViewById(R.id.mapView)

        fusedLocationClient =
            LocationServices
                .getFusedLocationProviderClient(this)

        repository =
            OccurrenceRepository(
                applicationContext
            )

        occurrences =
            repository.loadAll()

        mapView.setTileSource(
            TileSourceFactory.MAPNIK
        )

        mapView.setMultiTouchControls(true)

        /*
         * Local inicial de fallback.
         */
        val startPoint =
            GeoPoint(
                38.7223,
                -9.1393
            )

        mapView.controller.setZoom(15.0)
        mapView.controller.setCenter(startPoint)

        /*
         * Mostrar primeiro as ocorrências locais.
         */
        loadSavedOccurrencesOnMap()

        findViewById<Button>(
            R.id.btnMyLocation
        ).setOnClickListener {
            centerOnMyLocation()
        }

        /*
         * Long press continua a criar uma ocorrência.
         */
        enableLongPressToAddOccurrence()
    }

    /*
     * =====================================================
     * AUTENTICAÇÃO
     * =====================================================
     */

    private fun getAuthToken(): String? {
        val token =
            sessionManager.getToken()

        if (token == null) {
            Toast.makeText(
                this,
                "Sessão inválida. Inicia sessão novamente.",
                Toast.LENGTH_LONG
            ).show()
        }

        return token
    }

    /*
     * =====================================================
     * DESPESAS NO MAPA
     * =====================================================
     */

    private fun loadExpensesOnMap() {
        val token =
            getAuthToken() ?: return

        ioExecutor.execute {

            val (remoteExpenses, result) =
                ExpenseApiClient.getAll(
                    baseUrl =
                        ApiConfig.baseUrl(),
                    token =
                        token
                )

            runOnUiThread {

                if (
                    result.success &&
                    remoteExpenses != null
                ) {

                    clearExpenseMarkers()

                    val locatedExpenses =
                        remoteExpenses.filter {
                            it.hasLocation()
                        }

                    addExpenseMarkers(
                        locatedExpenses
                    )

                    /*
                     * Na primeira carga podemos centrar
                     * numa despesa georreferenciada.
                     */
                    if (
                        !hasCenteredOnExpense &&
                        locatedExpenses.isNotEmpty()
                    ) {

                        val first =
                            locatedExpenses.first()

                        val latitude =
                            first.latitude

                        val longitude =
                            first.longitude

                        if (
                            latitude != null &&
                            longitude != null
                        ) {

                            mapView.controller
                                .setZoom(15.0)

                            mapView.controller
                                .setCenter(
                                    GeoPoint(
                                        latitude,
                                        longitude
                                    )
                                )

                            hasCenteredOnExpense =
                                true
                        }
                    }

                } else {

                    val message =
                        if (
                            result.httpCode == 401
                        ) {
                            "Sessão expirada ou inválida."
                        } else {
                            "Não foi possível carregar as despesas no mapa."
                        }

                    Toast.makeText(
                        this,
                        message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun addExpenseMarkers(
        expenses: List<Expense>
    ) {

        expenses.forEach { expense ->

            val latitude =
                expense.latitude

            val longitude =
                expense.longitude

            if (
                latitude == null ||
                longitude == null
            ) {
                return@forEach
            }

            val marker =
                Marker(mapView).apply {

                    position =
                        GeoPoint(
                            latitude,
                            longitude
                        )

                    title =
                        "Despesa: ${expense.title}"

                    snippet =
                        "${formatMoney(expense.amountCents)} • ${expense.category}"

                    setAnchor(
                        Marker.ANCHOR_CENTER,
                        Marker.ANCHOR_BOTTOM
                    )

                    relatedObject =
                        "$MARKER_EXPENSE_PREFIX${expense.id}"

                    setOnMarkerClickListener { _, _ ->

                        showExpenseDetails(
                            expense
                        )

                        true
                    }
                }

            mapView.overlays.add(marker)
        }

        mapView.invalidate()
    }

    private fun clearExpenseMarkers() {

        val markers =
            mapView.overlays
                .filterIsInstance<Marker>()
                .filter {
                    (it.relatedObject as? String)
                        ?.startsWith(
                            MARKER_EXPENSE_PREFIX
                        ) == true
                }
                .toList()

        markers.forEach {
            mapView.overlays.remove(it)
        }

        mapView.invalidate()
    }

    private fun showExpenseDetails(
        expense: Expense
    ) {

        val locationText =
            if (
                expense.latitude != null &&
                expense.longitude != null
            ) {

                String.format(
                    Locale.US,
                    "%.5f, %.5f",
                    expense.latitude,
                    expense.longitude
                )

            } else {
                "Sem localização"
            }

        val message =
            buildString {

                append(
                    formatMoney(
                        expense.amountCents
                    )
                )

                append(
                    "\nCategoria: "
                )

                append(
                    expense.category
                )

                append(
                    "\n\nLocalização:\n"
                )

                append(
                    locationText
                )
            }

        AlertDialog.Builder(this)
            .setTitle(
                expense.title
            )
            .setMessage(
                message
            )
            .setPositiveButton(
                "Fechar",
                null
            )
            .setNeutralButton(
                "Abrir despesas"
            ) { _, _ ->

                startActivity(
                    Intent(
                        this,
                        ExpensesActivity::class.java
                    )
                )
            }
            .show()
    }

    /*
     * =====================================================
     * OCORRÊNCIAS
     * =====================================================
     */

    private fun loadSavedOccurrencesOnMap() {

        occurrences.forEach {
            addOccurrenceMarker(
                occurrence = it,
                showToast = false
            )
        }
    }

    private fun clearOccurrenceMarkers() {

        val markers =
            mapView.overlays
                .filterIsInstance<Marker>()
                .filter {
                    (it.relatedObject as? String)
                        ?.startsWith(
                            MARKER_OCCURRENCE_PREFIX
                        ) == true
                }
                .toList()

        markers.forEach {
            mapView.overlays.remove(it)
        }

        mapView.invalidate()
    }

    private fun syncOccurrencesFromApi() {

        val token =
            getAuthToken() ?: return

        ioExecutor.execute {

            val (remoteList, result) =
                OccurrenceApiClient.getAll(
                    baseUrl =
                        ApiConfig.baseUrl(),
                    token =
                        token
                )

            runOnUiThread {

                if (
                    result.success &&
                    remoteList != null
                ) {

                    occurrences.clear()

                    occurrences.addAll(
                        remoteList
                    )

                    repository.saveAll(
                        occurrences
                    )

                    clearOccurrenceMarkers()

                    loadSavedOccurrencesOnMap()

                } else if (
                    result.httpCode == 401
                ) {

                    Toast.makeText(
                        this,
                        "Sessão expirada ou inválida.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun enableLongPressToAddOccurrence() {

        val receiver =
            object : MapEventsReceiver {

                override fun singleTapConfirmedHelper(
                    p: GeoPoint
                ): Boolean {
                    return false
                }

                override fun longPressHelper(
                    p: GeoPoint
                ): Boolean {

                    showAddOccurrenceDialog(
                        p
                    )

                    return true
                }
            }

        mapView.overlays.add(
            MapEventsOverlay(receiver)
        )
    }

    private fun showAddOccurrenceDialog(
        point: GeoPoint
    ) {

        val input =
            EditText(this).apply {

                hint =
                    getString(
                        R.string
                            .map_add_occurrence_hint
                    )
            }

        AlertDialog.Builder(this)
            .setTitle(
                R.string
                    .map_add_occurrence_title
            )
            .setView(
                input
            )
            .setPositiveButton(
                R.string
                    .map_add_occurrence_add
            ) { _, _ ->

                val title =
                    input.text
                        .toString()
                        .trim()

                val finalTitle =
                    if (
                        title.isBlank()
                    ) {

                        getString(
                            R.string
                                .map_default_occurrence_title
                        )

                    } else {
                        title
                    }

                val occurrence =
                    Occurrence(
                        id =
                            UUID.randomUUID()
                                .toString(),

                        title =
                            finalTitle,

                        latitude =
                            point.latitude,

                        longitude =
                            point.longitude,

                        createdAtEpochMillis =
                            System.currentTimeMillis()
                    )

                occurrences.add(
                    occurrence
                )

                repository.saveAll(
                    occurrences
                )

                addOccurrenceMarker(
                    occurrence,
                    showToast = true
                )

                createOccurrenceInApi(
                    occurrence
                )
            }
            .setNegativeButton(
                R.string
                    .map_add_occurrence_cancel,
                null
            )
            .show()
    }

    private fun createOccurrenceInApi(
        occurrence: Occurrence
    ) {

        val token =
            getAuthToken() ?: return

        ioExecutor.execute {

            val result =
                OccurrenceApiClient.create(
                    baseUrl =
                        ApiConfig.baseUrl(),

                    token =
                        token,

                    occurrence =
                        occurrence
                )

            runOnUiThread {

                if (!result.success) {

                    val message =
                        if (
                            result.httpCode == 401
                        ) {
                            "Sessão expirada. A ocorrência ficou apenas local."
                        } else {
                            "Não foi possível sincronizar a ocorrência com a API."
                        }

                    Toast.makeText(
                        this,
                        message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun addOccurrenceMarker(
        occurrence: Occurrence,
        showToast: Boolean
    ) {

        val marker =
            Marker(mapView).apply {

                position =
                    GeoPoint(
                        occurrence.latitude,
                        occurrence.longitude
                    )

                title =
                    "Ocorrência: ${occurrence.title}"

                setAnchor(
                    Marker.ANCHOR_CENTER,
                    Marker.ANCHOR_BOTTOM
                )

                relatedObject =
                    "$MARKER_OCCURRENCE_PREFIX${occurrence.id}"

                /*
                 * Uma ocorrência passa agora a oferecer
                 * várias ações, incluindo criar despesa.
                 */
                setOnMarkerClickListener { selectedMarker, _ ->

                    showOccurrenceActions(
                        occurrence =
                            occurrence,

                        marker =
                            selectedMarker
                    )

                    true
                }
            }

        mapView.overlays.add(
            marker
        )

        mapView.invalidate()

        if (showToast) {

            Toast.makeText(
                this,
                getString(
                    R.string
                        .map_occurrence_added
                ),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /*
     * Aqui está a nova funcionalidade principal:
     *
     * ocorrência -> criar despesa neste local.
     */
    private fun showOccurrenceActions(
        occurrence: Occurrence,
        marker: Marker
    ) {

        val coordinates =
            String.format(
                Locale.US,
                "%.5f, %.5f",
                occurrence.latitude,
                occurrence.longitude
            )

        AlertDialog.Builder(this)
            .setTitle(
                occurrence.title
            )
            .setMessage(
                "Ocorrência guardada em:\n$coordinates"
            )
            .setPositiveButton(
                "Criar despesa aqui"
            ) { _, _ ->

                openExpenseFromOccurrence(
                    occurrence
                )
            }
            .setNeutralButton(
                "Remover ocorrência"
            ) { _, _ ->

                showRemoveOccurrenceConfirmation(
                    occurrence,
                    marker
                )
            }
            .setNegativeButton(
                "Fechar",
                null
            )
            .show()
    }

    private fun openExpenseFromOccurrence(
        occurrence: Occurrence
    ) {

        val intent =
            Intent(
                this,
                ExpensesActivity::class.java
            ).apply {

                putExtra(
                    ExpensesActivity
                        .EXTRA_CREATE_EXPENSE,
                    true
                )

                putExtra(
                    ExpensesActivity
                        .EXTRA_LATITUDE,
                    occurrence.latitude
                )

                putExtra(
                    ExpensesActivity
                        .EXTRA_LONGITUDE,
                    occurrence.longitude
                )

                putExtra(
                    ExpensesActivity
                        .EXTRA_INITIAL_TITLE,
                    occurrence.title
                )
            }

        startActivity(intent)
    }

    private fun showRemoveOccurrenceConfirmation(
        occurrence: Occurrence,
        marker: Marker
    ) {

        AlertDialog.Builder(this)
            .setTitle(
                "Remover ocorrência"
            )
            .setMessage(
                "Tem a certeza que pretende remover \"${occurrence.title}\"?"
            )
            .setPositiveButton(
                "Remover"
            ) { _, _ ->

                occurrences.removeAll {
                    it.id ==
                            occurrence.id
                }

                repository.saveAll(
                    occurrences
                )

                mapView.overlays.remove(
                    marker
                )

                mapView.invalidate()

                deleteOccurrenceInApi(
                    occurrence.id
                )

                Toast.makeText(
                    this,
                    "Ocorrência removida.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(
                "Cancelar",
                null
            )
            .show()
    }

    private fun deleteOccurrenceInApi(
        id: String
    ) {

        val token =
            getAuthToken() ?: return

        ioExecutor.execute {

            val result =
                OccurrenceApiClient.delete(
                    baseUrl =
                        ApiConfig.baseUrl(),

                    token =
                        token,

                    id =
                        id
                )

            runOnUiThread {

                if (!result.success) {

                    val message =
                        if (
                            result.httpCode == 401
                        ) {
                            "Sessão expirada. A remoção ficou apenas local."
                        } else {
                            "Não foi possível sincronizar a remoção com a API."
                        }

                    Toast.makeText(
                        this,
                        message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /*
     * =====================================================
     * LOCALIZAÇÃO
     * =====================================================
     */

    private fun centerOnMyLocation() {

        val fineGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission
                    .ACCESS_FINE_LOCATION
            ) ==
                    PackageManager
                        .PERMISSION_GRANTED

        val coarseGranted =
            ContextCompat.checkSelfPermission(
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

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission
                        .ACCESS_FINE_LOCATION,

                    Manifest.permission
                        .ACCESS_COARSE_LOCATION
                ),
                REQ_LOCATION
            )

            return
        }

        fusedLocationClient
            .lastLocation
            .addOnSuccessListener { location ->

                if (location == null) {

                    Toast.makeText(
                        this,
                        "Não foi possível obter a localização.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@addOnSuccessListener
                }

                val point =
                    GeoPoint(
                        location.latitude,
                        location.longitude
                    )

                mapView.controller
                    .setZoom(17.0)

                mapView.controller
                    .setCenter(point)
            }
            .addOnFailureListener {

                Toast.makeText(
                    this,
                    "Não foi possível obter a localização.",
                    Toast.LENGTH_SHORT
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
            REQ_LOCATION
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

        if (!granted) {

            Toast.makeText(
                this,
                "Permissão de localização negada.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        centerOnMyLocation()
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

        return formatter.format(value)
    }

    override fun onResume() {
        super.onResume()

        mapView.onResume()

        /*
         * Ao voltar de Despesas:
         *
         * - atualizamos ocorrências;
         * - atualizamos marcadores de despesas.
         */
        syncOccurrencesFromApi()
        loadExpensesOnMap()
    }

    override fun onPause() {
        super.onPause()

        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()

        ioExecutor.shutdown()
    }

    companion object {

        private const val REQ_LOCATION =
            1001

        private const val MARKER_OCCURRENCE_PREFIX =
            "occurrence:"

        private const val MARKER_EXPENSE_PREFIX =
            "expense:"
    }
}