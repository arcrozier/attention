package com.aracroproducts.attentionv2

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aracroproducts.attentionv2.MainViewModel.Companion.FCM_TOKEN
import com.aracroproducts.attentionv2.MainViewModel.Companion.TOKEN_UPLOADED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.locks.ReentrantLock

class SettingsViewModel(private val repository: AttentionRepository, application: Application) :
        AndroidViewModel(application) {

    var outstandingRequests by mutableStateOf(0)

    var currentPreferenceGroup by mutableStateOf<@Composable () -> Unit>(@Composable {})
    var selectedPreferenceGroupIndex by mutableStateOf(0)

    var photo: ImageBitmap? by mutableStateOf(null)

    var uploadDialog by mutableStateOf(false)
    var uploadStatus by mutableStateOf("")
    var shouldRetryUpload by mutableStateOf(false)
    var onCancel: (() -> Unit)? by mutableStateOf(null)
    var uri: Uri? by mutableStateOf(null)
    private val uploadLock = ReentrantLock()
    var uploading by mutableStateOf(false)

    init {
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) {
                File(getApplication<Application>().filesDir, MainViewModel
                    .PFP_FILENAME).readBytes()
            }
            withContext(Dispatchers.Default) {
                photo = BitmapFactory.decodeByteArray(data, 0, data.size)
                    .asImageBitmap()
            }
        }
    }

    fun clearAllDatabaseTables() = repository.clearTables()

    fun unregisterDevice(token: String, fcmToken: String) {
        repository.unregisterDevice(token = token, fcmToken = fcmToken)

        val context = getApplication<Application>()
        val fcmTokenPrefs = context.getSharedPreferences(FCM_TOKEN, Context.MODE_PRIVATE)
        fcmTokenPrefs.edit().apply {
            putBoolean(TOKEN_UPLOADED, false)
            apply()
        }
    }

    fun uploadImage(uri: Uri, context: Context, launchLogin: () -> Unit) {
        if (!uploadLock.tryLock()) {
            assert(uploading)
            return
        }
        uploading = true
        shouldRetryUpload = false
        this.uri = uri
        uploadDialog = true

        viewModelScope.launch(context = Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.buffered()?.use {
                it.readBytes()
            }

            val token = context.getSharedPreferences(
                    MainViewModel.USER_INFO, Context.MODE_PRIVATE
            ).getString(MainViewModel.MY_TOKEN, null)
            if (token != null) {
                if (bytes == null) {
                    uploadStatus = context.getString(R.string.upload_failed, context.getString(R
                            .string.no_file))
                    return@launch
                } else {
                    uploadStatus = context.getString(R.string.uploading)
                }
                val call = repository.editUser(photo = Base64.encodeToString(bytes, Base64
                        .DEFAULT), token = token, responseListener = { _, response, _ ->
                    onCancel = null
                    uploading = false
                    if (response.isSuccessful) {
                        uploadStatus = context.getString(R.string.uploaded)
                        // todo move file
                    } else {
                        when (response.code()) {
                            403 -> {
                                uploadDialog = false
                                launchLogin()
                            }
                            429 -> {
                                shouldRetryUpload = true
                                uploadStatus = context.getString(R.string.upload_failed, context
                                        .getString(R.string.rate_limited))
                            }
                            else -> {
                                shouldRetryUpload = true
                                uploadStatus = context.getString(R.string.upload_failed, context
                                        .getString(R.string.server_error))
                            }
                        }
                    }
                    uploadLock.unlock()
                }, errorListener = { _, _ ->
                    shouldRetryUpload = true
                    uploadStatus = context.getString(R.string.upload_failed, context.getString(R
                            .string.connection_error))
                    onCancel = null
                    uploadLock.unlock()
                })
                onCancel = {
                    call.cancel()
                    uploadLock.unlock()
                }
            }
        }
    }
}