package pt.contacomigo.app

import android.Manifest
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
import pt.contacomigo.app.api.OccurrenceApiClient
import pt.contacomigo.app.auth.SessionManager
import pt.contacomigo.app.data.Occurrence
import pt.contacomigo.app.data.OccurrenceRepository
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().userAgentValue =
            packageName

        setContentView(R.layout.activity_map)

        sessionManager = SessionManager(this)

        mapView = findViewById(R.id.mapView)

        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(this)

        repository =
            OccurrenceRepository(applicationContext)

        occurrences =
            repository.loadAll()

        mapView.setTileSource(
            TileSourceFactory.MAPNIK
        )

        mapView.setMultiTouchControls(true)

        // Localização inicial de fallback: Lisboa
        val startPoint =
            GeoPoint(38.7223, -9.1393)

        mapView.controller.setZoom(15.0)
        mapView.controller.setCenter(startPoint)

        /*
         * Mostramos primeiro o conteúdo local.
         * Assim a aplicação continua utilizável offline.
         */
        loadSavedOccurrencesOnMap()

        findViewById<Button>(
            R.id.btnMyLocation
        ).setOnClickListener {
            centerOnMyLocation()
        }

        enableLongPressToAddOccurrence()

        /*
         * Depois tentamos sincronizar com o servidor.
         */
        syncFromApiOnStart()
    }

    private fun loadSavedOccurrencesOnMap() {

        occurrences.forEach { occurrence ->
            addOccurrenceMarker(
                occurrence,
                showToast = false
            )
        }
    }

    private fun clearAllMarkers() {

        val markers =
            mapView.overlays
                .filterIsInstance<Marker>()
                .toList()

        markers.forEach {
            mapView.overlays.remove(it)
        }

        mapView.invalidate()
    }

    /**
     * Obtém o JWT atualmente guardado.
     */
    private fun getAuthToken(): String? {

        val token = sessionManager.getToken()

        if (token == null) {

            Toast.makeText(
                this,
                "Sessão inválida. Inicia sessão novamente.",
                Toast.LENGTH_LONG
            ).show()
        }

        return token
    }

    /**
     * Sincroniza as ocorrências deste utilizador
     * com a API autenticada.
     */
    private fun syncFromApiOnStart() {

        val token =
            getAuthToken() ?: return

        val baseUrl =
            ApiConfig.baseUrl()

        ioExecutor.execute {

            val (remoteList, result) =
                OccurrenceApiClient.getAll(
                    baseUrl = baseUrl,
                    token = token
                )

            runOnUiThread {

                if (
                    result.success &&
                    remoteList != null
                ) {

                    /*
                     * Nesta fase, o servidor é a fonte
                     * principal quando está disponível.
                     */
                    occurrences.clear()
                    occurrences.addAll(remoteList)

                    repository.saveAll(
                        occurrences
                    )

                    clearAllMarkers()
                    loadSavedOccurrencesOnMap()

                    Toast.makeText(
                        this,
                        "Sincronização concluída " +
                                "(${remoteList.size} ocorrências)",
                        Toast.LENGTH_SHORT
                    ).show()

                } else {

                    val message =
                        when (result.httpCode) {

                            401 ->
                                "Sessão expirada ou inválida."

                            else ->
                                "Sem sincronização. " +
                                        "A usar dados locais."
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

    private fun enableLongPressToAddOccurrence() {

        val receiver =
            object : MapEventsReceiver {

                override fun singleTapConfirmedHelper(
                    p: GeoPoint
                ): Boolean = false

                override fun longPressHelper(
                    p: GeoPoint
                ): Boolean {

                    showAddOccurrenceDialog(p)
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

        val input = EditText(this).apply {
            hint =
                getString(
                    R.string.map_add_occurrence_hint
                )
        }

        AlertDialog.Builder(this)
            .setTitle(
                R.string.map_add_occurrence_title
            )
            .setView(input)
            .setPositiveButton(
                R.string.map_add_occurrence_add
            ) { _, _ ->

                val title =
                    input.text
                        .toString()
                        .trim()

                val finalTitle =
                    if (title.isBlank()) {
                        getString(
                            R.string.map_default_occurrence_title
                        )
                    } else {
                        title
                    }

                val occurrence =
                    Occurrence(
                        id =
                            UUID.randomUUID()
                                .toString(),
                        title = finalTitle,
                        latitude = point.latitude,
                        longitude = point.longitude,
                        createdAtEpochMillis =
                            System.currentTimeMillis()
                    )

                /*
                 * Primeiro guardamos localmente.
                 * Se a API estiver offline, a app continua
                 * a funcionar.
                 */
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
                R.string.map_add_occurrence_cancel,
                null
            )
            .show()
    }

    private fun createOccurrenceInApi(
        occurrence: Occurrence
    ) {

        val token =
            getAuthToken() ?: return

        val baseUrl =
            ApiConfig.baseUrl()

        ioExecutor.execute {

            val result =
                OccurrenceApiClient.create(
                    baseUrl = baseUrl,
                    token = token,
                    occurrence = occurrence
                )

            runOnUiThread {

                if (!result.success) {

                    val message =
                        if (result.httpCode == 401) {
                            "Sessão expirada. " +
                                    "A ocorrência ficou apenas local."
                        } else {
                            "Não foi possível sincronizar " +
                                    "a ocorrência com a API."
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
                    occurrence.title

                setAnchor(
                    Marker.ANCHOR_CENTER,
                    Marker.ANCHOR_BOTTOM
                )

                relatedObject =
                    occurrence.id

                setOnMarkerClickListener { selectedMarker, _ ->

                    showRemoveOccurrenceDialog(
                        selectedMarker
                    )

                    true
                }
            }

        mapView.overlays.add(marker)
        mapView.invalidate()

        if (showToast) {

            Toast.makeText(
                this,
                getString(
                    R.string.map_occurrence_added
                ),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showRemoveOccurrenceDialog(
        marker: Marker
    ) {

        AlertDialog.Builder(this)
            .setTitle(
                getString(
                    R.string.map_occurrence_remove_title
                )
            )
            .setMessage(
                getString(
                    R.string.map_occurrence_remove_message
                )
            )
            .setPositiveButton(
                getString(
                    R.string.map_occurrence_remove_confirm
                )
            ) { _, _ ->

                val occurrenceId =
                    marker.relatedObject as? String

                if (occurrenceId != null) {

                    occurrences.removeAll {
                        it.id == occurrenceId
                    }

                    repository.saveAll(
                        occurrences
                    )

                    deleteOccurrenceInApi(
                        occurrenceId
                    )
                }

                mapView.overlays.remove(marker)
                mapView.invalidate()

                Toast.makeText(
                    this,
                    getString(
                        R.string.map_occurrence_removed
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(
                getString(
                    R.string.action_cancel
                ),
                null
            )
            .show()
    }

    private fun deleteOccurrenceInApi(
        id: String
    ) {

        val token =
            getAuthToken() ?: return

        val baseUrl =
            ApiConfig.baseUrl()

        ioExecutor.execute {

            val result =
                OccurrenceApiClient.delete(
                    baseUrl = baseUrl,
                    token = token,
                    id = id
                )

            runOnUiThread {

                if (!result.success) {

                    val message =
                        if (result.httpCode == 401) {
                            "Sessão expirada. " +
                                    "A remoção ficou apenas local."
                        } else {
                            "Não foi possível sincronizar " +
                                    "a remoção com a API."
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

    private fun centerOnMyLocation() {

        val fineGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) ==
                    PackageManager.PERMISSION_GRANTED

        val coarseGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) ==
                    PackageManager.PERMISSION_GRANTED

        if (
            !fineGranted &&
            !coarseGranted
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
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
                        getString(
                            R.string.map_error_location_unavailable
                        ),
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
                    getString(
                        R.string.map_error_location_unavailable
                    ),
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
            requestCode != REQ_LOCATION
        ) {
            return
        }

        val granted =
            grantResults.isNotEmpty() &&
                    grantResults.any {
                        it ==
                                PackageManager.PERMISSION_GRANTED
                    }

        if (!granted) {

            Toast.makeText(
                this,
                getString(
                    R.string.map_error_location_permission
                ),
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        centerOnMyLocation()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
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
        private const val REQ_LOCATION = 1001
    }
}