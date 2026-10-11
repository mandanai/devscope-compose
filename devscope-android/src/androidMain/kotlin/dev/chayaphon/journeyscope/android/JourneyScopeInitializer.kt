package dev.chayaphon.journeyscope.android

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.startup.Initializer

/** Runs at process start through androidx.startup, so hosts write no setup code. */
internal class JourneyScopeInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        val app = context.applicationContext as Application
        InspectorNotifier.start(app)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) app.registerActivityLifecycleCallbacks(PermissionPrompt)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()

    private object PermissionPrompt : Application.ActivityLifecycleCallbacks {
        private var asked = false

        override fun onActivityResumed(activity: Activity) {
            if (asked || activity is InspectorActivity) return
            asked = true
            val app = activity.application
            app.unregisterActivityLifecycleCallbacks(this)
            if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE)
            }
        }

        override fun onActivityCreated(
            activity: Activity,
            savedInstanceState: Bundle?,
        ) = Unit

        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(
            activity: Activity,
            outState: Bundle,
        ) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit

        private const val REQUEST_CODE = 0x4A53
    }
}
