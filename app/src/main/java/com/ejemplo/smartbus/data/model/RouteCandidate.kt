package com.ejemplo.smartbus.data.model

/**
 * Una ruta que sirve a un destino concreto, con los buses que la
 * recorren ahora mismo y el mas cercano a la parada donde te subes.
 */
data class RouteCandidate(
    val route: BusRoute,
    val boardingStop: BusStop,
    val distanceToStopMeters: Float,
    val buses: List<Bus>,
    val closestBus: Bus? = null,
    val closestBusDistanceMeters: Float? = null
) {

    /** Minutos estimados de llegada del bus mas cercano. */
    val etaMinutes: Int?
        get() {
            val bus = closestBus ?: return null
            val meters = closestBusDistanceMeters ?: return null

            if (meters <= 40f) {
                return 0
            }

            val speed = bus.speedKmh.takeIf { it > 1f } ?: DEFAULT_BUS_SPEED_KMH
            val minutes = (meters / 1000f) / speed * 60f

            return kotlin.math.ceil(minutes).toInt()
        }

    companion object {
        const val DEFAULT_BUS_SPEED_KMH = 18f
    }
}