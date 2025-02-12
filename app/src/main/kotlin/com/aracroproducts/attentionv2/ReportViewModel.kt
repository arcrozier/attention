package com.aracroproducts.attentionv2

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import retrofit2.HttpException
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.mapOf

class ReportViewModel(
    private val attentionRepository: AttentionRepository,
    private val preferencesRepository: PreferencesRepository,
    application: Application,
    body: String = ""
) : AndroidViewModel(application) {

    enum class Reason(val description: Int, val tags: List<String>) {
        BUG(R.string.report_reason_bug, listOf("bug")),
        CSAM(R.string.report_reason_csam, listOf("legal", "csam")),
        DANGEROUS_CONTENT(R.string.report_reason_dangerous, listOf("legal", "danger")),
        TOS_VIOLATION(R.string.report_reason_tos, listOf("tos")),
        DMCA(R.string.report_reason_dmca, listOf("legal", "dmca")),
        OTHER(R.string.report_reason_other, listOf())
    }

    enum class AttachmentType {
        IMAGE, VIDEO, UNKNOWN
    }

    var body: String by mutableStateOf(body)
    var reason: Reason? by mutableStateOf(null)
    val attachments = mutableStateListOf<Uri>()

    var snackbarMessage: String? by mutableStateOf(null)
    var showSuccessScreen by mutableStateOf(false)

    suspend fun submit(reason: Reason, body: String, attachments: List<Uri>): Int? {
        val context = getApplication<Application>().applicationContext
        // token is auth token
        val token = preferencesRepository.getValue(stringPreferencesKey(MainViewModel.MY_TOKEN))

        if (token == null) {
            Log.e(javaClass.name, "Token is null when sending report")
            return 403
        }

        val config =
            context.resources.configuration
        config.setLocale(Locale.ROOT)

        val augmentedBody = when (reason) {
            Reason.BUG -> {
                val fields = mapOf(
                    "App build" to context.getString(R.string.version_name),
                    "Android API level" to Build.VERSION.SDK_INT,
                    "Manufacturer" to Build.MANUFACTURER,
                    "Model" to Build.MODEL
                ).map { "${it.key}: ${it.value}" }.joinToString("\n")

                "## ======= System Info =======\n${fields}\n\n## ======= User Report =======\n${body}"
            }

            else -> body
        }

        try {
            attentionRepository.report(
                context = context,
                title = context.createConfigurationContext(
                    config
                ).resources.getString(reason.description), message = augmentedBody, token = token,
                photos = attachments, tags = reason.tags
            )
            return 200
        } catch (e: HttpException) {
            return e.code()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Tracks thumbnails for each attachment and jobs that are loading the thumbnails
     *
     * Access must be synchronized
     */
    val attachmentsThumbnails = mutableStateMapOf<Uri, UriState>()

    companion object {
        class UriState(
            val bitmap: Bitmap?,
            val job: Job?,
            val referenceCount: AtomicInteger,
            val type: AttachmentType
        )

    }
}