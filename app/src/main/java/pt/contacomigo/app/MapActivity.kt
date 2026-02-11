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

class MapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_map)

        mapView = findViewById(R.id.mapView)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        // Fallback: Lisboa
        val startPoint = GeoPoint(38.7223, -9.1393)
        mapView.controller.setZoom(15.0)
        mapView.controller.setCenter(startPoint)

        // Botão: centrar na minha localização
        findViewById<Button>(R.id.btnMyLocation).setOnClickListener {
            centerOnMyLocation()
        }

        // Toque prolongado no mapa: criar uma ocorrência (marcador)
        enableLongPressToAddOccurrence()
    }

    private fun enableLongPressToAddOccurrence() {
        val receiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                // Não usamos toque simples por agora
                return false
            }

            override fun longPressHelper(p: GeoPoint): Boolean {
                showAddOccurrenceDialog(p)
                return true
            }
        }

        // Overlay que escuta eventos do mapa (tap/long press)
        mapView.overlays.add(MapEventsOverlay(receiver))
    }

    private fun showAddOccurrenceDialog(point: GeoPoint) {
        val input = EditText(this).apply {
            hint = getString(R.string.map_add_occurrence_hint)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.map_add_occurrence_title)
            .setView(input)
            .setPositiveButton(R.string.map_add_occurrence_add) { _, _ ->
                val title = input.text.toString().trim()
                val finalTitle = if (title.isBlank()) getString(R.string.map_add_occurrence_title) else title
                addOccurrenceMarker(point, finalTitle)
            }
            .setNegativeButton(R.string.map_add_occurrence_cancel, null)
            .show()
    }

    private fun addOccurrenceMarker(point: GeoPoint, title: String) {
        val marker = Marker(mapView).apply {
            position = point
            this.title = title
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }

        mapView.overlays.add(marker)
        mapView.invalidate()

        Toast.makeText(this, getString(R.string.map_occurrence_added), Toast.LENGTH_SHORT).show()
    }

    private fun centerOnMyLocation() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
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

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location == null) {
                    Toast.makeText(
                        this,
                        getString(R.string.map_error_location_unavailable),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@addOnSuccessListener
                }

                val point = GeoPoint(location.latitude, location.longitude)
                mapView.controller.setZoom(17.0)
                mapView.controller.setCenter(point)
            }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    getString(R.string.map_error_location_unavailable),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != REQ_LOCATION) return

        val granted = grantResults.isNotEmpty() && grantResults.any { it == PackageManager.PERMISSION_GRANTED }
        if (!granted) {
            Toast.makeText(
                this,
                getString(R.string.map_error_location_permission),
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

    companion object {
        private const val REQ_LOCATION = 1001
    }
}