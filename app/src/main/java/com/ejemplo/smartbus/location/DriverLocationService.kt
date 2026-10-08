package com.ejemplo.smartbus.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ejemplo.smartbus.data.SessionManager
import com.ejemplo.smartbus.data.driver.DriverRepository
import com.ejemplo.smartbus.data.model.UserRole
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Comparte la ubicacion del conductor mientras esta online.
 *
 * Es un servicio en primer plano porque el GPS tiene que seguir
 * funcionando con la pantalla apagada y la app en segundo plano: sin
 * eso Android corta el proceso y el pasajero dejaria de ver el vehiculo.
 *
 * Diferencias con el seguimiento del pasajero:
 *  - Este manda la posicion a la base de datos (el pasajero la ve).
 *  - El del pasajero sirve para detectar abordajes y dar puntos.
 */
class DriverLocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private lateinit var driverRepository: DriverRepository

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    private var accountId: String? = null
    private var tracking = false

    override fun onCreate() {

        super.onCreate()

        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(this)

        driverRepository = DriverRepository(this)

        createNotificationChannel()
        createLocationCallback()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_START -> {
                accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID)
                startTracking()
            }

            ACTION_STOP -> {
                stopTracking()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        // Si el sistema mata el servicio, se reinicia solo con la
        // ultima cuenta conocida para no perder el viaje.
        return START_STICKY
    }

    // =====================================================
    // GPS
    // =====================================================

    private fun startTracking() {

        if (tracking) {
            return
        }

        val session = SessionManager.get(this).session.value

        if (session == null || session.role != UserRole.DRIVER) {
            Log.w(TAG, "No se inicia el GPS: la sesion no es de conductor")
            stopSelf()
            return
        }

        if (accountId == null) {
            accountId = session.storageKey
        }

        if (!hasLocationPermission()) {
            Log.w(TAG, "No se inicia el GPS: falta permiso de ubicacion")
            stopSelf()
            return
        }

        val notification = createPersistentNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        tracking = true

        val request =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
                .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
                .setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_M)
                .setWaitForAccurateLocation(false)
                .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                mainLooper
            )
        } catch (exception: SecurityException) {
            Log.w(TAG, "GPS del conductor rechazado: ${exception.message}")
            tracking = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createLocationCallback() {

        locationCallback =
            object : LocationCallback() {

                override fun onLocationResult(result: LocationResult) {

                    for (location in result.locations) {
                        publish(location)
                    }
                }
            }
    }

    /**
     * Escribe la posicion en la base. Solo si el fix es bueno: una
     * ubicacion de 500 m de error dibujaria el vehiculo donde no esta.
     */
    private fun publish(location: Location) {

        val id = accountId ?: return

        if (location.hasAccuracy() && location.accuracy > MAX_ACCEPTED_ACCURACY_M) {
            return
        }

        // Ubicacion falsa: una app de GPS simulado, un emulador o una
        // extension del navegador pueden mover el vehiculo sin que
        // exista. Si el conductor hace eso, el pasajero esta en
        // peligro, asi que la posicion no se publica.
        if (isMockLocation(location)) {

            Log.w(TAG, "Se descarto una ubicacion simulada")

            return
        }

        val session = SessionManager.get(this).session.value

        if (session == null || session.role != UserRole.DRIVER) {
            stopTracking()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        scope.launch {
            try {
                driverRepository.publishLocation(
                    accountId = id,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    speedKmh = if (location.hasSpeed()) {
                        location.speed * 3.6f
                    } else {
                        0f
                    },
                    headingDegrees = if (location.hasBearing()) {
                        location.bearing
                    } else {
                        null
                    },
                    gpsAccuracyMeters = if (location.hasAccuracy()) {
                        location.accuracy
                    } else {
                        null
                    }
                )
            } catch (exception: Exception) {
                Log.w(
                    TAG,
                    "No se pudo publicar la posicion: ${exception.message}"
                )
            }
        }
    }

    /**
     * True si la posicion viene de un proveedor simulado.
     *
     * FusedLocationProvider avisa con isFromMockProvider, y desde
     * Android 12 el propio Location trae isMock. Se miran los dos
     * porque no todos los dispositivos rellenan el segundo.
     */
    private fun isMockLocation(location: Location): Boolean {

        if (location.isFromMockProvider) {
            return true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return location.isMock
        }

        return false
    }

    private fun stopTracking() {

        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            } catch (exception: SecurityException) {
                Log.w(TAG, "No se pudo quitar el GPS: ${exception.message}")
            }
        }

        tracking = false
    }

    private fun hasLocationPermission(): Boolean {

        val fine = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fine || coarse
    }

    // =====================================================
    // NOTIFICACION
    // =====================================================

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Servicio de conductor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Comparte tu ubicacion mientras estas en servicio"
            setShowBadge(false)
        }

        getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    private fun createPersistentNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Estas en servicio")
            .setContentText("El pasajero puede ver donde vas")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopTracking()
        scope.cancel()
        super.onDestroy()
    }

    companion object {

        private const val TAG = "SmartBusDriver"

        private const val CHANNEL_ID = "smartbus_driver_channel"
        private const val NOTIFICATION_ID = 2001

        private const val UPDATE_INTERVAL_MS = 5_000L
        private const val MIN_UPDATE_INTERVAL_MS = 2_000L
        private const val MIN_UPDATE_DISTANCE_M = 8f

        /** Un fix peor que esto no se muestra: seria mentir. */
        private const val MAX_ACCEPTED_ACCURACY_M = 100f

        const val ACTION_START = "SMARTBUS_DRIVER_START"
        const val ACTION_STOP = "SMARTBUS_DRIVER_STOP"
        const val EXTRA_ACCOUNT_ID = "account_id"

        fun start(context: Context, accountId: String) {

            val intent = Intent(context, DriverLocationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ACCOUNT_ID, accountId)
            }

            try {
                // ContextCompat es obligatorio: startForegroundService
                // como metodo de Context solo existe desde API 26 y
                // el proyecto sirve desde API 24.
                ContextCompat.startForegroundService(context, intent)
            } catch (exception: Exception) {
                Log.w(TAG, "No se pudo arrancar el servicio: ${exception.message}")
            }
        }

        fun stop(context: Context) {

            val intent = Intent(context, DriverLocationService::class.java).apply {
                action = ACTION_STOP
            }

            try {
                context.startService(intent)
            } catch (exception: Exception) {
                Log.w(TAG, "No se pudo parar el servicio: ${exception.message}")
            }
        }
    }
}