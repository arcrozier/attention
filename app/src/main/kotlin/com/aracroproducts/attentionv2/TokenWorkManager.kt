package com.aracroproducts.attentionv2

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aracroproducts.attentionv2.MainViewModel.Companion.FCM_TOKEN
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import retrofit2.HttpException

class TokenWorkManager(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {
    /**
     * A suspending method to do your work.
     * <p>
     * To specify which [CoroutineDispatcher] your work should run on, use `withContext()`
     * within `doWork()`.
     * If there is no other dispatcher declared, [Dispatchers.Default] will be used.
     * <p>
     * A CoroutineWorker is given a maximum of ten minutes to finish its execution and return a
     * [androidx.work.ListenableWorker.Result].  After this time has expired, the worker will be signalled to
     * stop.
     *
     * @return The [androidx.work.ListenableWorker.Result] of the result of the background work; note that
     * dependent work will not execute if you return [androidx.work.ListenableWorker.Result.failure]
     */
    override suspend fun doWork(): Result {
        val settingsRepository = PreferencesRepository(getDataStore(applicationContext))
        val attentionRepository = AttentionRepository(AttentionDB.getDB(applicationContext))

        val fcmToken = Firebase.messaging.token.await()
        settingsRepository.setValue(stringPreferencesKey(FCM_TOKEN), fcmToken)
        val authToken = settingsRepository.getToken() ?: return Result.failure()
        try {
            attentionRepository.registerDevice(authToken, fcmToken)
            return Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: HttpException) {
            return Result.failure()
        } catch (_: Exception) {
            return Result.retry()
        }
    }
}