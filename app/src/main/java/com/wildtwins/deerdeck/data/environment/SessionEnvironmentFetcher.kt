package com.wildtwins.deerdeck.data.environment

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.wildtwins.deerdeck.data.model.ObservationSessionEntity
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

object SessionEnvironmentFetcher {
    private const val FreshLocationMillis = 10 * 60 * 1_000L
    private const val LocationTimeoutMillis = 12_000L
    private const val ObservationWindowMillis = 90 * 60 * 1_000L
    private const val ElevationPreferences = "environment_elevation_cache"
    private const val UsgsElevationSource = "USGS 3DEP"

    suspend fun capture(context: Context, now: Long = System.currentTimeMillis()): SessionEnvironment {
        return capture(context = context, eventAt = now, locationFreshnessAt = now)
    }

    suspend fun captureLocationContext(
        context: Context,
        now: Long = System.currentTimeMillis(),
    ): SessionEnvironment {
        return withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val moon = MoonPhaseCalculator.phase(now)
            val location = appContext.bestAvailableLocation(now)
                ?: return@withContext SessionEnvironment(
                    moonPhase = moon.phase,
                    moonIlluminationPercent = moon.illuminationPercent,
                    attemptCount = 0,
                    capturedAt = now,
                    status = "Location unavailable",
                )
            val zoneId = ZoneId.systemDefault()
            val eventDate = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
            val cachedElevation = appContext.cachedElevation(location.latitude, location.longitude)
            SessionEnvironment(
                latitude = location.latitude,
                longitude = location.longitude,
                locationAccuracyMeters = location.takeIf { it.hasAccuracy() }?.accuracy?.toDouble(),
                locationProvider = location.provider,
                elevationFeet = cachedElevation
                    ?: location.takeIf { it.hasAltitude() }?.altitude?.metersToFeet(),
                sunsetAt = SunCalculator.sunsetMillis(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    date = eventDate,
                    zoneId = zoneId,
                ),
                moonPhase = moon.phase,
                moonIlluminationPercent = moon.illuminationPercent,
                attemptCount = 0,
                locationCapturedAt = location.time.takeIf { it > 0 },
                elevationSource = if (cachedElevation != null) {
                    UsgsElevationSource
                } else {
                    location.takeIf { it.hasAltitude() }?.let { "Device GPS" }
                },
                capturedAt = now,
                status = "Collecting",
            )
        }
    }

    fun captureWithoutLocation(now: Long = System.currentTimeMillis()): SessionEnvironment {
        val moon = MoonPhaseCalculator.phase(now)
        return SessionEnvironment(
            moonPhase = moon.phase,
            moonIlluminationPercent = moon.illuminationPercent,
            attemptCount = 0,
            capturedAt = now,
            status = "Disabled",
        )
    }

    suspend fun backfill(
        context: Context,
        session: ObservationSessionEntity,
        now: Long = System.currentTimeMillis(),
    ): SessionEnvironment {
        val latitude = session.latitude
        val longitude = session.longitude
        if (latitude == null || longitude == null) {
            return capture(
                context = context,
                eventAt = session.startedAt,
                locationFreshnessAt = now,
                attemptCount = (session.environmentAttemptCount ?: 0) + 1,
            )
        }

        return withContext(Dispatchers.IO) {
            buildEnvironment(
                context = context.applicationContext,
                latitude = latitude,
                longitude = longitude,
                locationAccuracyMeters = session.locationAccuracyMeters,
                locationProvider = session.locationProvider,
                deviceElevationFeet = session.elevationFeet,
                existingElevationSource = session.elevationSource,
                locationCapturedAt = session.locationCapturedAt,
                eventAt = session.startedAt,
                capturedAt = now,
                attemptCount = (session.environmentAttemptCount ?: 0) + 1,
            )
        }
    }

    fun needsWeatherBackfill(session: ObservationSessionEntity): Boolean {
        return EnvironmentCaptureContract.needsBackfill(session)
    }

    private suspend fun capture(
        context: Context,
        eventAt: Long,
        locationFreshnessAt: Long,
        attemptCount: Int = 1,
    ): SessionEnvironment {
        return withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val moon = MoonPhaseCalculator.phase(eventAt)
            val location = appContext.bestAvailableLocation(locationFreshnessAt)
                ?: return@withContext SessionEnvironment(
                    moonPhase = moon.phase,
                    moonIlluminationPercent = moon.illuminationPercent,
                    attemptCount = attemptCount,
                    capturedAt = locationFreshnessAt,
                    status = "Location unavailable",
                )

            buildEnvironment(
                context = appContext,
                latitude = location.latitude,
                longitude = location.longitude,
                locationAccuracyMeters = location.takeIf { it.hasAccuracy() }?.accuracy?.toDouble(),
                locationProvider = location.provider,
                deviceElevationFeet = location.takeIf { it.hasAltitude() }?.altitude?.metersToFeet(),
                existingElevationSource = null,
                locationCapturedAt = location.time.takeIf { it > 0 },
                eventAt = eventAt,
                capturedAt = locationFreshnessAt,
                attemptCount = attemptCount,
            )
        }
    }

    private fun buildEnvironment(
        context: Context,
        latitude: Double,
        longitude: Double,
        locationAccuracyMeters: Double?,
        locationProvider: String?,
        deviceElevationFeet: Double?,
        existingElevationSource: String?,
        locationCapturedAt: Long?,
        eventAt: Long,
        capturedAt: Long,
        attemptCount: Int,
    ): SessionEnvironment {
        val moon = MoonPhaseCalculator.phase(eventAt)
        val zoneId = ZoneId.systemDefault()
        val eventDate = Instant.ofEpochMilli(eventAt).atZone(zoneId).toLocalDate()
        val sunsetAt = SunCalculator.sunsetMillis(
            latitude = latitude,
            longitude = longitude,
            date = eventDate,
            zoneId = zoneId,
        )
        val elevation = resolveElevation(
            context = context,
            latitude = latitude,
            longitude = longitude,
            existingElevationFeet = deviceElevationFeet,
            existingElevationSource = existingElevationSource,
        )
        val weather = runCatching {
            fetchWeather(latitude, longitude, eventAt)
        }.getOrNull()

        val environment = SessionEnvironment(
            latitude = latitude,
            longitude = longitude,
            locationAccuracyMeters = locationAccuracyMeters,
            locationProvider = locationProvider,
            elevationFeet = elevation?.feet,
            sunsetAt = sunsetAt,
            weatherSummary = weather?.summary,
            temperatureF = weather?.temperatureF,
            windSummary = weather?.windSummary,
            pressureInHg = weather?.pressureInHg,
            humidityPercent = weather?.humidityPercent,
            dewPointF = weather?.dewPointF,
            visibilityMiles = weather?.visibilityMiles,
            precipitationProbabilityPercent = weather?.precipitationProbabilityPercent,
            forecastSummary = weather?.forecastSummary,
            moonPhase = moon.phase,
            moonIlluminationPercent = moon.illuminationPercent,
            attemptCount = attemptCount,
            locationCapturedAt = locationCapturedAt,
            weatherObservedAt = weather?.observedAt,
            elevationSource = elevation?.source,
            weatherSource = weather?.source,
            capturedAt = capturedAt,
            status = "Partial",
        )
        return environment.copy(status = EnvironmentCaptureContract.statusFor(environment))
    }

    private fun resolveElevation(
        context: Context,
        latitude: Double,
        longitude: Double,
        existingElevationFeet: Double?,
        existingElevationSource: String?,
    ): ElevationSnapshot? {
        if (existingElevationFeet != null && existingElevationSource == UsgsElevationSource) {
            return ElevationSnapshot(existingElevationFeet, UsgsElevationSource)
        }

        context.cachedElevation(latitude, longitude)?.let {
            return ElevationSnapshot(it, UsgsElevationSource)
        }

        val usgsElevation = runCatching {
            val url = "https://epqs.nationalmap.gov/v1/json" +
                "?x=${longitude.formatPoint()}&y=${latitude.formatPoint()}" +
                "&units=Feet&wkid=4326&includeDate=false"
            JSONObject(readUrl(url))
                .optNullableDouble("value")
                ?.takeIf { it.isFinite() && it in -1_500.0..30_000.0 }
        }.getOrNull()
        if (usgsElevation != null) {
            context.cacheElevation(latitude, longitude, usgsElevation)
            return ElevationSnapshot(usgsElevation, UsgsElevationSource)
        }

        return existingElevationFeet?.let {
            ElevationSnapshot(it, existingElevationSource ?: "Device GPS")
        }
    }

    private suspend fun Context.bestAvailableLocation(now: Long): Location? {
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) return null

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        val lastKnown = providers
            .mapNotNull { provider ->
                runCatching {
                    if (locationManager.isProviderEnabled(provider)) {
                        locationManager.getLastKnownLocation(provider)
                    } else {
                        null
                    }
                }.getOrNull()
            }
            .maxWithOrNull(
                compareBy<Location> { it.time }
                    .thenByDescending { if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE },
            )

        if (lastKnown != null && now - lastKnown.time <= FreshLocationMillis) {
            return lastKnown
        }

        val enabledProviders = providers.filter { provider ->
            runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
        }
        val fresh = withTimeoutOrNull(LocationTimeoutMillis) {
            requestSingleLocation(locationManager, enabledProviders)
        }
        return fresh ?: lastKnown
    }

    @Suppress("MissingPermission")
    private suspend fun requestSingleLocation(
        locationManager: LocationManager,
        providers: List<String>,
    ): Location? = suspendCancellableCoroutine { continuation ->
        if (providers.isEmpty()) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (continuation.isActive) continuation.resume(location)
                locationManager.removeUpdates(this)
            }
        }
        continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }
        val registered = providers.count { provider ->
            runCatching {
                locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }.isSuccess
        }
        if (registered == 0) {
            locationManager.removeUpdates(listener)
            if (continuation.isActive) continuation.resume(null)
        }
    }

    private fun fetchWeather(latitude: Double, longitude: Double, eventAt: Long): WeatherSnapshot {
        val pointUrl = "https://api.weather.gov/points/${latitude.formatPoint()},${longitude.formatPoint()}"
        val pointProperties = JSONObject(readUrl(pointUrl)).getJSONObject("properties")
        val hourly = pointProperties.optString("forecastHourly")
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { fetchHourlyForecast(it, eventAt) }.getOrNull() }
        val current = pointProperties.optString("observationStations")
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { fetchStationObservations(it, eventAt) }.getOrNull() }

        val snapshot = WeatherSnapshot(
            summary = current?.summary ?: hourly?.summary,
            temperatureF = current?.temperatureF ?: hourly?.temperatureF,
            windSummary = current?.windSummary ?: hourly?.windSummary,
            pressureInHg = current?.pressureInHg,
            humidityPercent = current?.humidityPercent,
            dewPointF = current?.dewPointF,
            visibilityMiles = current?.visibilityMiles,
            precipitationProbabilityPercent = hourly?.precipitationProbabilityPercent,
            forecastSummary = hourly?.summary,
            observedAt = current?.observedAt,
            source = listOfNotNull(current?.source, hourly?.source)
                .distinct()
                .joinToString(" + ")
                .takeIf { it.isNotBlank() },
        )
        if (!snapshot.hasAnyWeatherValue()) {
            throw IOException("NWS returned no usable conditions for the session time")
        }
        return snapshot
    }

    private fun fetchHourlyForecast(hourlyUrl: String, eventAt: Long): HourlyForecast? {
        val periods = JSONObject(readUrl(hourlyUrl))
            .getJSONObject("properties")
            .getJSONArray("periods")
        val period = (0 until periods.length())
            .map { periods.getJSONObject(it) }
            .firstOrNull { item ->
                val start = item.optInstant("startTime")
                val end = item.optInstant("endTime")
                start != null && end != null && eventAt >= start && eventAt < end
            }
            ?: return null

        val temperature = period.optDouble("temperature", Double.NaN).takeIf { !it.isNaN() }
        val temperatureF = temperature?.let { value ->
            if (period.optString("temperatureUnit", "F").equals("C", ignoreCase = true)) {
                value.celsiusToFahrenheit()
            } else {
                value
            }
        }
        val wind = listOf(period.optString("windSpeed"), period.optString("windDirection"))
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .takeIf { it.isNotBlank() }
        return HourlyForecast(
            summary = period.optString("shortForecast").takeIf { it.isNotBlank() },
            temperatureF = temperatureF,
            windSummary = wind,
            precipitationProbabilityPercent = period
                .optJSONObject("probabilityOfPrecipitation")
                ?.optNullableDouble("value"),
            source = "NWS hourly forecast",
        )
    }

    private fun fetchStationObservations(stationsUrl: String, eventAt: Long): CurrentObservation? {
        val features = JSONObject(readUrl(stationsUrl)).getJSONArray("features")
        val stationUrls = (0 until minOf(features.length(), 3))
            .mapNotNull { index ->
                features.getJSONObject(index).optString("id").trimEnd('/').takeIf { it.isNotBlank() }
            }
        val observations = stationUrls.mapNotNull { stationUrl ->
            runCatching { fetchObservationNearEvent(stationUrl, eventAt) }.getOrNull()
        }.sortedBy { abs(it.observedAt - eventAt) }
        if (observations.isEmpty()) return null

        fun <T> firstValue(selector: (CurrentObservation) -> T?): T? {
            return observations.firstNotNullOfOrNull(selector)
        }
        return CurrentObservation(
            summary = firstValue { it.summary },
            temperatureF = firstValue { it.temperatureF },
            windSummary = firstValue { it.windSummary },
            pressureInHg = firstValue { it.pressureInHg },
            humidityPercent = firstValue { it.humidityPercent },
            dewPointF = firstValue { it.dewPointF },
            visibilityMiles = firstValue { it.visibilityMiles },
            observedAt = observations.first().observedAt,
            source = "NWS " + observations.map { it.source.removePrefix("NWS ") }
                .distinct()
                .joinToString("+"),
        )
    }

    private fun fetchObservationNearEvent(stationUrl: String, eventAt: Long): CurrentObservation? {
        val start = Instant.ofEpochMilli(eventAt - ObservationWindowMillis)
        val end = Instant.ofEpochMilli(
            minOf(eventAt + ObservationWindowMillis, System.currentTimeMillis() + 5 * 60 * 1_000L),
        )
        val url = "$stationUrl/observations?start=$start&end=$end&limit=25"
        val features = JSONObject(readUrl(url)).getJSONArray("features")
        val stationId = stationUrl.substringAfterLast('/')
        return (0 until features.length())
            .mapNotNull { index ->
                parseCurrentObservation(
                    properties = features.getJSONObject(index).optJSONObject("properties")
                        ?: return@mapNotNull null,
                    stationId = stationId,
                )
            }
            .filter { abs(it.observedAt - eventAt) <= ObservationWindowMillis }
            .minByOrNull { abs(it.observedAt - eventAt) }
    }

    private fun parseCurrentObservation(
        properties: JSONObject,
        stationId: String,
    ): CurrentObservation? {
        val observedAt = properties.optInstant("timestamp") ?: return null
        val temperatureC = properties.optMeasurementValue("temperature")
        val dewPointC = properties.optMeasurementValue("dewpoint")
        val measuredHumidity = properties.optMeasurementValue("relativeHumidity")
        val humidity = measuredHumidity
            ?: calculateRelativeHumidity(temperatureC, dewPointC)
        val resolvedDewPointC = dewPointC
            ?: calculateDewPoint(temperatureC, humidity)
        val windSpeedMph = properties.optMeasurementValue("windSpeed")?.metersPerSecondToMph()
        val windDirection = properties.optMeasurementValue("windDirection")?.degreesToCompass()
        val windSummary = listOfNotNull(
            windSpeedMph?.let { "${String.format(Locale.US, "%.0f", it)} mph" },
            windDirection,
        ).joinToString(" ").takeIf { it.isNotBlank() }

        return CurrentObservation(
            summary = properties.optString("textDescription").takeIf { it.isNotBlank() },
            temperatureF = temperatureC?.celsiusToFahrenheit(),
            windSummary = windSummary,
            pressureInHg = (
                properties.optMeasurementValue("barometricPressure")
                    ?: properties.optMeasurementValue("seaLevelPressure")
                )?.pascalsToInHg(),
            humidityPercent = humidity,
            dewPointF = resolvedDewPointC?.celsiusToFahrenheit(),
            visibilityMiles = properties.optMeasurementValue("visibility")?.metersToMiles(),
            observedAt = observedAt,
            source = "NWS $stationId",
        )
    }

    private fun calculateRelativeHumidity(temperatureC: Double?, dewPointC: Double?): Double? {
        if (temperatureC == null || dewPointC == null) return null
        val numerator = exp((17.625 * dewPointC) / (243.04 + dewPointC))
        val denominator = exp((17.625 * temperatureC) / (243.04 + temperatureC))
        return (100.0 * numerator / denominator).coerceIn(0.0, 100.0)
    }

    private fun calculateDewPoint(temperatureC: Double?, humidityPercent: Double?): Double? {
        if (temperatureC == null || humidityPercent == null || humidityPercent <= 0.0) return null
        val gamma = ln(humidityPercent / 100.0) + (17.625 * temperatureC) / (243.04 + temperatureC)
        return (243.04 * gamma) / (17.625 - gamma)
    }

    private fun readUrl(url: String): String {
        var lastError: Throwable? = null
        repeat(3) { attempt ->
            try {
                return readUrlOnce(url)
            } catch (error: Throwable) {
                lastError = error
                if (attempt < 2) Thread.sleep(400L * (attempt + 1))
            }
        }
        throw IOException("Failed to read $url", lastError)
    }

    private fun readUrlOnce(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/geo+json, application/json")
            setRequestProperty("User-Agent", "WithrowDeerDeck (https://github.com/wildtwins)")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val message = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("HTTP $code from $url $message")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun Context.cachedElevation(latitude: Double, longitude: Double): Double? {
        return getSharedPreferences(ElevationPreferences, Context.MODE_PRIVATE)
            .getString(elevationCacheKey(latitude, longitude), null)
            ?.toDoubleOrNull()
    }

    private fun Context.cacheElevation(latitude: Double, longitude: Double, elevationFeet: Double) {
        getSharedPreferences(ElevationPreferences, Context.MODE_PRIVATE)
            .edit()
            .putString(elevationCacheKey(latitude, longitude), elevationFeet.toString())
            .apply()
    }

    private fun elevationCacheKey(latitude: Double, longitude: Double): String {
        return "${latitude.formatPoint()},${longitude.formatPoint()}"
    }

    private fun Double.formatPoint(): String = String.format(Locale.US, "%.4f", this)

    private fun JSONObject.optMeasurementValue(name: String): Double? {
        return optJSONObject(name)?.optNullableDouble("value")
    }

    private fun JSONObject.optNullableDouble(name: String): Double? {
        val value = optDouble(name, Double.NaN)
        return value.takeIf { !it.isNaN() }
    }

    private fun JSONObject.optInstant(name: String): Long? {
        val value = optString(name).takeIf { it.isNotBlank() } ?: return null
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
    }

    private fun Double.celsiusToFahrenheit(): Double = (this * 9.0 / 5.0) + 32.0

    private fun Double.metersPerSecondToMph(): Double = this * 2.2369362921

    private fun Double.pascalsToInHg(): Double = this * 0.00029529983071445

    private fun Double.metersToMiles(): Double = this / 1609.344

    private fun Double.metersToFeet(): Double = this * 3.280839895

    private fun Double.degreesToCompass(): String {
        val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val index = (((this % 360) + 22.5) / 45.0).toInt() % directions.size
        return directions[index]
    }

    private fun WeatherSnapshot.hasAnyWeatherValue(): Boolean {
        return listOf(
            summary,
            temperatureF,
            windSummary,
            pressureInHg,
            humidityPercent,
            dewPointF,
            visibilityMiles,
            precipitationProbabilityPercent,
            forecastSummary,
        ).any { it != null }
    }

    private data class ElevationSnapshot(
        val feet: Double,
        val source: String,
    )

    private data class HourlyForecast(
        val summary: String?,
        val temperatureF: Double?,
        val windSummary: String?,
        val precipitationProbabilityPercent: Double?,
        val source: String,
    )

    private data class CurrentObservation(
        val summary: String?,
        val temperatureF: Double?,
        val windSummary: String?,
        val pressureInHg: Double?,
        val humidityPercent: Double?,
        val dewPointF: Double?,
        val visibilityMiles: Double?,
        val observedAt: Long,
        val source: String,
    )

    private data class WeatherSnapshot(
        val summary: String?,
        val temperatureF: Double?,
        val windSummary: String?,
        val pressureInHg: Double?,
        val humidityPercent: Double?,
        val dewPointF: Double?,
        val visibilityMiles: Double?,
        val precipitationProbabilityPercent: Double?,
        val forecastSummary: String?,
        val observedAt: Long?,
        val source: String?,
    )
}
