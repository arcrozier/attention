package com.aracroproducts.attentionv2

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aracroproducts.attentionv2.MainViewModel.Companion.FCM_TOKEN
import com.aracroproducts.attentionv2.MainViewModel.Companion.PFP_FILENAME
import com.aracroproducts.attentionv2.MainViewModel.Companion.TOKEN_UPLOADED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.min

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
                File(
                    getApplication<Application>().filesDir, PFP_FILENAME
                ).readBytes()
            }
            withContext(Dispatchers.Default) {
                photo = BitmapFactory.decodeByteArray(data, 0, data.size).asImageBitmap()
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

    suspend fun getImageBitmap(
        uri: Uri,
        context: Context,
        size: IntSize,
        minSize: Boolean
    ): Bitmap? {
        val job = viewModelScope.async(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(context.contentResolver, uri)
                ) { decoder, info, _ ->
                    if (minSize) decoder.setTargetSize(
                        min(info.size.width, size.width), min(info.size.height, size.height)
                    )
                    else decoder.setTargetSize(size.width, size.height)
                }
            } else {
                BitmapFactory.Options().run {
                    var input = context.contentResolver.openInputStream(uri)
                    inJustDecodeBounds = true
                    BitmapFactory.decodeStream(input, null, this)
                    input?.close() // Calculate inSampleSize
                    val (height: Int, width: Int) = run { outHeight to outWidth }
                    var inSampleSize = 1

                    if (height > size.height || width > size.width) {

                        val halfHeight: Int = height / 2
                        val halfWidth: Int = width / 2

                        // Calculate the largest inSampleSize value that is a power of 2 and keeps both
                        // height and width larger than the requested height and width.
                        while (halfHeight / inSampleSize >= size.height && halfWidth / inSampleSize >= size.width) {
                            inSampleSize *= 2
                        }
                    }

                    // Decode bitmap with inSampleSize set
                    inJustDecodeBounds = false
                    input = context.contentResolver.openInputStream(uri)

                    BitmapFactory.decodeStream(input, null, this)
                }
            }
        }
        return job.await()
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

            val token = context.getSharedPreferences(
                MainViewModel.USER_INFO, Context.MODE_PRIVATE
            ).getString(MainViewModel.MY_TOKEN, null)
            if (token != null) {
                uploadStatus = context.getString(R.string.processing)
                val bytes = ByteArrayOutputStream().run {
                    getImageBitmap(uri, context, UPLOAD_SIZE, true)?.compress(
                        Bitmap.CompressFormat.PNG, 100, this
                    )
                    if (this.size() != 0) this.toByteArray()
                    else null
                }
                if (bytes == null) {
                    uploadStatus = context.getString(
                        R.string.upload_failed, context.getString(
                            R.string.no_file
                        )
                    )
                    return@launch
                } else {
                    uploadStatus = context.getString(R.string.uploading)
                }
                val call = repository.editUser(photo = Base64.encodeToString(
                    bytes, Base64.DEFAULT
                ), token = token, responseListener = { _, response, _ ->
                    onCancel = null
                    uploading = false
                    if (response.isSuccessful) {
                        uploadStatus = context.getString(R.string.uploaded)
                        viewModelScope.launch(Dispatchers.IO) {
                            val bitmap = getImageBitmap(uri, context, ICON_SIZE, false)
                            val file = File(context.filesDir, PFP_FILENAME).apply {
                                createNewFile()
                            }
                            val output = FileOutputStream(file)
                            bitmap?.compress(Bitmap.CompressFormat.PNG, 100, output)
                            photo = bitmap?.asImageBitmap()
                        }
                    } else {
                        when (response.code()) {
                            403 -> {
                                uploadDialog = false
                                launchLogin()
                            }
                            429 -> {
                                shouldRetryUpload = true
                                uploadStatus = context.getString(
                                    R.string.upload_failed,
                                    context.getString(R.string.rate_limited)
                                )
                            }
                            else -> {
                                shouldRetryUpload = true
                                uploadStatus = context.getString(
                                    R.string.upload_failed,
                                    context.getString(R.string.server_error)
                                )
                            }
                        }
                    }
                    uploadLock.unlock()
                }, errorListener = { _, _ ->
                    shouldRetryUpload = true
                    uploadStatus = context.getString(
                        R.string.upload_failed, context.getString(
                            R.string.connection_error
                        )
                    )
                    onCancel = null
                    uploadLock.unlock()
                })
                onCancel = {
                    call.cancel()
                    uploadLock.unlock()
                }
            } else {
                launchLogin()
                uploadLock.unlock()
                uploadDialog = false
                uploading = false
            }
        }
    }

    companion object {
        val UPLOAD_SIZE = IntSize(1000, 1000)
        val ICON_SIZE = IntSize(128, 128)
    }
}