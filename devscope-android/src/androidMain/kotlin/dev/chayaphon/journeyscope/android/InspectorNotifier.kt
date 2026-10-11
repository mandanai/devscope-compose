package dev.chayaphon.journeyscope.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.chayaphon.journeyscope.core.JourneyScope
import dev.chayaphon.journeyscope.core.model.TraceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

internal object InspectorNotifier {
    private const val CHANNEL_ID = "journeyscope_inspector"
    private const val NOTIFICATION_ID = 0x4A53
    private const val THROTTLE_MILLIS = 1_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val toggle = MutableStateFlow(0)

    private var started = false

    @Synchronized
    fun start(app: Application) {
        if (started) return
        started = true
        createChannel(app)
        scope.launch {
            summaries().collect { render(app, it) }
        }
    }

    fun refresh() {
        toggle.value++
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun summaries(): Flow<Summary?> =
        combine(JourneyScope.tracerFlow, toggle) { tracer, _ -> tracer }
            .flatMapLatest { tracer ->
                if (!tracer.config.enabled || !JourneyScopeAndroid.notificationsEnabled) {
                    flowOf(null)
                } else {
                    val recorder = tracer.recorder
                    recorder.events
                        .onStart { emit(dev.chayaphon.journeyscope.core.recorder.RecorderEvent.Cleared) }
                        .map {
                            val traces = recorder.snapshot()
                            Summary(
                                total = traces.size,
                                failed = traces.count { it.status == TraceStatus.FAILED },
                                running = traces.count { !it.status.isTerminal },
                            )
                        }.conflate()
                        .onEach { delay(THROTTLE_MILLIS) }
                }
            }

    @SuppressLint("MissingPermission")
    private fun render(
        app: Application,
        summary: Summary?,
    ) {
        val manager = NotificationManagerCompat.from(app)
        if (summary == null) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (!canPost(app)) return
        val open =
            PendingIntent.getActivity(
                app,
                0,
                JourneyScopeAndroid.intent(app),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val text =
            if (summary.total == 0) {
                app.getString(R.string.journeyscope_notification_idle)
            } else {
                "${summary.total} journeys, ${summary.failed} failed, ${summary.running} running"
            }
        val notification =
            NotificationCompat
                .Builder(app, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setContentTitle(app.getString(R.string.journeyscope_inspector_title))
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun canPost(app: Application): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun createChannel(app: Application) {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                app.getString(R.string.journeyscope_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        app.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private data class Summary(
        val total: Int,
        val failed: Int,
        val running: Int,
    )
}
