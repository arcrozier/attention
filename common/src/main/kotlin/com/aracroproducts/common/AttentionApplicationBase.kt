package com.aracroproducts.common

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class AttentionContainer(application: AttentionApplicationBase) {
    private val database by lazy { AttentionDB.getDB(application.applicationContext) }
    val repository by lazy { AttentionRepository(database, application) }
    val settingsRepository by lazy { PreferencesRepository(getDataStore(application.applicationContext)) }
    val applicationScope = CoroutineScope(SupervisorJob())
}

abstract class AttentionApplicationBase : Application(), LifecycleEventObserver,
    Application.ActivityLifecycleCallbacks {

    val container = AttentionContainer(this)

//    var activity: Activity? = null

    override fun onCreate() {
        DynamicColors.applyToActivitiesIfAvailable(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        super.onCreate()
    }

    companion object {
        //        var shownAlertID: String? = null
        fun isActivityVisible(): Boolean {
            return ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(
                Lifecycle.State.STARTED
            )
        }
    }

    /**
     * Called when a state transition event happens.
     *
     * @param source The source of the event
     * @param event The event
     */
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) { // do nothing
    }

    override fun onActivityCreated(p0: Activity, p1: Bundle?) { // do nothing
    }

    override fun onActivityStarted(p0: Activity) { // do nothing
    }

    override fun onActivityResumed(activity: Activity) {

    }

    override fun onActivityPaused(activity: Activity) {

    }
//    override fun onActivityResumed(p0: Activity) {
//        if (p0 is Alert) {
//            shownAlertID = p0.alertModel.alertId
//            activity = p0
//        }
//    }

//    override fun onActivityPaused(p0: Activity) {
//        shownAlertID = null
//        activity = null
//    }

    override fun onActivityStopped(p0: Activity) { // do nothing
    }

    override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) { // do nothing
    }

    override fun onActivityDestroyed(p0: Activity) { // do nothing
    }

    abstract val mainActivity: Class<*>
    abstract val alertActivity: Class<*>?

    abstract val baseUrl: String
}