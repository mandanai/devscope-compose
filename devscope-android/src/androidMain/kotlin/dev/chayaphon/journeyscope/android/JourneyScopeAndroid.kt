package dev.chayaphon.journeyscope.android

import android.content.Context
import android.content.Intent
import dev.chayaphon.journeyscope.export.JourneyExportController
import kotlin.concurrent.Volatile

/** Hooks for the auto-installed inspector notification; the defaults need no host code. */
public object JourneyScopeAndroid {
    /** Replaces the default controller (history, import, share) the inspector uses; set before the inspector opens. */
    @Volatile
    public var exportController: JourneyExportController? = null

    /** Hides the notification while false; the inspector stays reachable through [launch]. */
    @Volatile
    public var notificationsEnabled: Boolean = true
        set(value) {
            field = value
            InspectorNotifier.refresh()
        }

    public fun intent(context: Context): Intent = Intent(context, InspectorActivity::class.java)

    public fun launch(context: Context) {
        context.startActivity(intent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
