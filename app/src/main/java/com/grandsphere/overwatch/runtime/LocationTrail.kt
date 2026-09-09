package com.grandsphere.overwatch.runtime

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToInt

data class TrailPoint(
    val atEpochMs: Long,
    val latitude: Double,
    val longitude: Double,
    val provider: String = "",
)

/**
 * Keeps high-accuracy fused location updates registered while location Alarm is active.
 */
object LocationTrail {
    private const val INTERVAL_MS = 3_000L
    private const val MIN_INTERVAL_MS = 2_000L
    private const val FETCH_TIMEOUT_MS = 20_000L
    private const val POKE_GRACE_MS = 5_000L

    private val points = CopyOnWriteArrayList<TrailPoint>()
    private val lock = Any()
    private val currentCancels = CopyOnWriteArrayList<CancellationTokenSource>()
    private var thread: HandlerThread? = null
    private var fused: FusedLocationProviderClient? = null
    private var callback: LocationCallback? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastVerboseMs = 0L
    private var appContext: Context? = null

    @Volatile
    private var lastFixAt = 0L

    @Volatile
    private var latest: TrailPoint? = null

    @Volatile
    private var lastSentLatitude: Double? = null

    @Volatile
    private var lastSentLongitude: Double? = null

    fun snapshot(): List<TrailPoint> = points.toList()

    fun latest(): TrailPoint? = latest

    fun isFresh(maxAgeMs: Long): Boolean {
        val point = latest ?: return false
        return System.currentTimeMillis() - point.atEpochMs <= maxAgeMs
    }

    fun isFreshSince(maxAgeMs: Long, minEpochMs: Long): Boolean {
        val point = latest ?: return false
        val now = System.currentTimeMillis()
        return (now - point.atEpochMs <= maxAgeMs) && (point.atEpochMs >= minEpochMs)
    }

    fun staleAfterMs(context: Context): Long {
        val minutes = com.grandsphere.overwatch.OverwatchApp.from(context)
            .latestSettings.continuousLocationMinutes.coerceAtLeast(1)
        return (minutes * 60_000L) / 2
    }

    fun matchesLastSent(latitude: Double, longitude: Double): Boolean {
        val lastLat = lastSentLatitude ?: return false
        val lastLng = lastSentLongitude ?: return false
        return lastLat == latitude && lastLng == longitude
    }

    fun rememberSent(latitude: Double, longitude: Double) {
        lastSentLatitude = latitude
        lastSentLongitude = longitude
    }

    fun clearLastSent() {
        lastSentLatitude = null
        lastSentLongitude = null
    }

    fun clear() {
        points.clear()
        latest = null
        lastFixAt = 0L
        clearLastSent()
    }

    fun start(context: Context) {
        stop()
        clear()
        VerboseLog.ok("Trail", "start hot flp")
        startListener(context)
    }

    fun ensureHot(context: Context) {
        synchronized(lock) {
            if (callback != null) {
                VerboseLog.d("Trail", "hot already")
                return
            }
        }
        VerboseLog.ok("Trail", "ensure hot flp")
        startListener(context)
    }

    fun stop() {
        synchronized(lock) {
            cancelCurrentRequestsLocked()
            val client = fused
            val cb = callback
            if (cb != null) VerboseLog.d("Trail", "stop")
            if (client != null && cb != null) {
                runCatching { client.removeLocationUpdates(cb) }
            }
            callback = null
            fused = null
            thread?.quitSafely()
            thread = null
            val wake = wakeLock
            if (wake?.isHeld == true) runCatching { wake.release() }
            wakeLock = null
            appContext = null
        }
    }

    fun add(point: TrailPoint) {
        latest = point
        points += point
        if (points.size > 120) points.removeAt(0)
    }

    fun packForSms(points: List<TrailPoint>, count: Int): List<TrailPoint> {
        val n = count.coerceIn(1, 20)
        if (points.isEmpty()) return emptyList()
        if (points.size <= n) return points
        if (n == 1) return listOf(points.last())
        val span = (points.size - 1).toDouble()
        val indices = (0 until n).map { i ->
            (i * span / (n - 1)).roundToInt()
        }.distinct()
        return indices.map { points[it] }
    }

    fun formatTrail(points: List<TrailPoint>): String {
        if (points.isEmpty()) return ""
        return points.joinToString("\n") { p -> formatPointLine(p) }
    }

    fun formatPointLine(point: TrailPoint): String =
        "${formatFixTime(point.atEpochMs)} ${mapsLink(point.latitude, point.longitude)}"

    fun mapsLink(latitude: Double, longitude: Double): String =
        "https://maps.google.com/?q=$latitude,$longitude"

    fun formatFixTime(epochMs: Long): String {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        return fmt.format(Date(epochMs))
    }

    fun awaitFresh(context: Context, maxAgeMs: Long, timeoutMs: Long = FETCH_TIMEOUT_MS): Boolean {
        heartbeat(context, "tick")
        val pokeStartMs = System.currentTimeMillis()
        val minFixMs = pokeStartMs - POKE_GRACE_MS
        poke(context)
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (isFreshSince(maxAgeMs, minFixMs)) {
                VerboseLog.ok(
                    "Trail",
                    "fresh during wait age=${System.currentTimeMillis() - (latest?.atEpochMs ?: 0L)}ms",
                )
                break
            }
            try {
                Thread.sleep(250L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        cancelCurrentRequests()
        restoreStanding()
        heartbeat(context, "afterWait")
        return isFreshSince(maxAgeMs, minFixMs)
    }

    fun poke(context: Context) {
        ensureHot(context)
        val app = context.applicationContext
        val client: FusedLocationProviderClient?
        val looper: Looper?
        synchronized(lock) {
            client = fused
            looper = thread?.looper
        }
        if (client == null || looper == null) {
            VerboseLog.fail("Trail", "poke no fused")
            return
        }
        requestUpdates(client, looper, burst = true)
        requestCurrentLocation(app, client)
    }

    fun heartbeat(context: Context, label: String) {
        val app = context.applicationContext
        val kg = app.getSystemService(KeyguardManager::class.java)
        val pm = app.getSystemService(PowerManager::class.java)
        val locked = kg?.isKeyguardLocked == true
        val screenOn = pm?.isInteractive == true
        val listening: Boolean
        val wakeHeld: Boolean
        synchronized(lock) {
            listening = callback != null
            wakeHeld = wakeLock?.isHeld == true
        }
        val lm = app.getSystemService(LocationManager::class.java)
        val gpsOn = lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        val netOn = lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        val now = System.currentTimeMillis()
        val fixAge = if (lastFixAt > 0L) now - lastFixAt else null
        val latestAge = latest?.let { now - it.atEpochMs }
        VerboseLog.d(
            "Trail",
            "$label locked=$locked screenOn=$screenOn gpsOn=$gpsOn netOn=$netOn " +
                "wake=$wakeHeld listener=$listening fixAge=$fixAge " +
                "latestAge=$latestAge latest=${latest?.provider}",
        )
    }

    private fun restoreStanding() {
        val client: FusedLocationProviderClient?
        val looper: Looper?
        synchronized(lock) {
            client = fused
            looper = thread?.looper
        }
        if (client == null || looper == null) return
        requestUpdates(client, looper, burst = false)
    }

    private fun requestCurrentLocation(app: Context, client: FusedLocationProviderClient) {
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val cancel = CancellationTokenSource()
        currentCancels += cancel
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(15_000L)
            .build()
        val ok = runCatching {
            client.getCurrentLocation(request, cancel.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) onFix(app, loc)
                    else VerboseLog.d("Trail", "getCurrentLocation null")
                }
                .addOnFailureListener {
                    VerboseLog.fail("Trail", "getCurrentLocation", it)
                }
        }.onFailure {
            VerboseLog.fail("Trail", "getCurrentLocation start", it)
        }.isSuccess
        if (ok) VerboseLog.d("Trail", "getCurrentLocation flp")
    }

    private fun cancelCurrentRequests() {
        synchronized(lock) { cancelCurrentRequestsLocked() }
    }

    private fun cancelCurrentRequestsLocked() {
        for (signal in currentCancels) {
            runCatching { signal.cancel() }
        }
        currentCancels.clear()
    }

    private fun startListener(context: Context) {
        val app = context.applicationContext
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            VerboseLog.fail("Trail", "no ACCESS_FINE_LOCATION")
            return
        }
        val client = LocationServices.getFusedLocationProviderClient(app)
        val ht = HandlerThread("ow-loc-hot").apply { start() }
        val locCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                onFix(app, location)
            }
        }
        val pm = app.getSystemService(PowerManager::class.java)
        val wake = runCatching {
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "overwatch:loc-hot").apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L)
            }
        }.getOrNull()
        if (wake?.isHeld == true) VerboseLog.ok("Trail", "wake lock")
        else VerboseLog.fail("Trail", "wake lock")
        synchronized(lock) {
            thread = ht
            fused = client
            callback = locCallback
            wakeLock = wake
            appContext = app
        }
        requestUpdates(client, ht.looper, burst = false)
        runCatching {
            client.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) onFix(app, loc)
            }
        }
    }

    private fun requestUpdates(
        client: FusedLocationProviderClient,
        looper: Looper,
        burst: Boolean,
    ) {
        val cb = synchronized(lock) { callback } ?: return
        val app = appContext ?: return
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val interval = if (burst) 0L else INTERVAL_MS
        val minInterval = if (burst) 0L else MIN_INTERVAL_MS
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
            .setMinUpdateIntervalMillis(minInterval)
            .setWaitForAccurateLocation(true)
            .build()
        val ok = runCatching {
            client.requestLocationUpdates(request, cb, looper)
        }.onFailure {
            VerboseLog.fail("Trail", "flp requestLocationUpdates", it)
        }.isSuccess
        val kind = if (burst) "burst" else "listen"
        if (ok) VerboseLog.ok("Trail", "$kind flp interval=${interval}ms")
    }

    private fun onFix(context: Context, location: Location) {
        val provider = location.provider.orEmpty().ifEmpty { "fused" }
        val stamp = stampOf(location)
        lastFixAt = stamp
        val point = TrailPoint(
            atEpochMs = stamp,
            latitude = location.latitude,
            longitude = location.longitude,
            provider = provider,
        )
        add(point)
        val now = System.currentTimeMillis()
        if (now - lastVerboseMs >= 15_000L) {
            lastVerboseMs = now
            val kg = context.getSystemService(KeyguardManager::class.java)
            val locked = kg?.isKeyguardLocked == true
            VerboseLog.d(
                "Trail",
                "store ${point.latitude},${point.longitude} provider=$provider " +
                    "age=${now - stamp}ms locked=$locked n=${points.size}",
            )
        }
    }

    private fun stampOf(location: Location): Long {
        val now = System.currentTimeMillis()
        val t = location.time
        if (t <= 0L) return now
        if (t > now + 5_000L) return now
        return t
    }
}
