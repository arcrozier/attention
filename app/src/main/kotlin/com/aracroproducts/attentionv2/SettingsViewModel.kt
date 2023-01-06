package com.aracroproducts.attentionv2

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aracroproducts.attentionv2.MainViewModel.Companion.MY_TOKEN
import com.aracroproducts.attentionv2.MainViewModel.Companion.PFP_FILENAME
import kotlinx.coroutines.*
import retrofit2.Response
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.min

class SettingsViewModel(
    private val repository: AttentionRepository,
    val preferencesRepository: PreferencesRepository,
    private val applicationScope: CoroutineScope,
    application: Application
) : AndroidViewModel(application) {

    data class SnackBarData(
        val message: String,
        val withDismissAction: Boolean = true,
        val actionLabel: String? = null,
        val duration: SnackbarDuration = SnackbarDuration.Short
    )

    private val sharedViewModel = SharedViewModel(
        repository, preferencesRepository, applicationScope, application
    )

    var outstandingRequests by mutableStateOf(0)

    var currentPreferenceGroup by mutableStateOf<@Composable () -> Unit>(@Composable {})
    var selectedPreferenceGroupIndex by mutableStateOf(0)

    var photo: ImageBitmap? by mutableStateOf(null)

    var currentSnackBar: SnackBarData? by mutableStateOf(null)

    var uploadDialog by mutableStateOf(false)
    var uploadStatus by mutableStateOf("")
    var uploadSuccess: Boolean? by mutableStateOf(null)
    var shouldRetryUpload by mutableStateOf(false)
    var onCancel: (() -> Unit)? by mutableStateOf(null)
    var uri: Uri? by mutableStateOf(null)
    private val uploadLock = ReentrantLock()
    var uploading by mutableStateOf(false)
    var uploadProgress by mutableStateOf(0f)

    init {
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) {
                File(
                    getApplication<Application>().filesDir, PFP_FILENAME
                ).readBytes()
            }
            photo = withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap()
            }
        }
    }


    suspend fun getImageBitmap(
        uri: Uri, context: Context, size: IntSize, minSize: Boolean
    ): Bitmap? {
        val job = viewModelScope.async(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(context.contentResolver, uri)
                ) { decoder, info, _ ->
                    var s = min(
                        size.width.toFloat() / info.size.width,
                        size.height.toFloat() / info.size.height
                    )
                    if (minSize) s = min(s, 1f)
                    decoder.setTargetSize(
                        (info.size.width * s).toInt(), (info.size.height * s).toInt()
                    )
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

    fun uploadImage(uri: Uri, context: Activity) {
        if (!uploadLock.tryLock()) {
            assert(uploading)
            return
        }
        uploading = true
        shouldRetryUpload = false
        this.uri = uri
        uploadDialog = true

        viewModelScope.launch(context = Dispatchers.IO) {

            val token = preferencesRepository.getValue(stringPreferencesKey(MY_TOKEN))
            if (token != null) {
                uploadStatus = context.getString(R.string.processing)
                val image: InputStream? = try {
                    context.contentResolver.openInputStream(uri)
                } catch (e: FileNotFoundException) {
                    null
                }
                if (image == null) {
                    uploadSuccess = false
                    uploading = false
                    uploadStatus = context.getString(
                        R.string.upload_failed, context.getString(
                            R.string.no_file
                        )
                    )
                    return@launch
                } else {
                    uploadStatus = context.getString(R.string.uploading)
                }
                val call = repository.editPhoto(photo = image,
                                                token = token,
                                                responseListener = { _, response, _ ->
                                                    onCancel = null
                                                    uploading = false
                                                    if (response.isSuccessful) {
                                                        uploadSuccess = true
                                                        uploadStatus =
                                                            context.getString(R.string.uploaded)
                                                        viewModelScope.launch(Dispatchers.IO) {
                                                            val bitmap = getImageBitmap(
                                                                uri, context, ICON_SIZE, false
                                                            )
                                                            val file = File(
                                                                context.filesDir, PFP_FILENAME
                                                            ).apply {
                                                                createNewFile()
                                                            }
                                                            val output = FileOutputStream(file)
                                                            bitmap?.compress(
                                                                Bitmap.CompressFormat.PNG,
                                                                100,
                                                                output
                                                            )
                                                            photo = bitmap?.asImageBitmap()
                                                        }
                                                    } else {
                                                        uploadSuccess = false
                                                        when (response.code()) {
                                                            400 -> {
                                                                shouldRetryUpload = false
                                                                uploadStatus = context.getString(
                                                                    R.string.upload_failed,
                                                                    context.getString(R.string.invalid_photo)
                                                                )
                                                            }
                                                            403 -> {
                                                                uploadDialog = false
                                                                sharedViewModel.logout(
                                                                    context, context
                                                                )
                                                            }
                                                            413 -> {
                                                                shouldRetryUpload = false
                                                                uploadStatus = context.getString(
                                                                    R.string.upload_failed,
                                                                    context.getString(R.string.photo_too_large)
                                                                )
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
                                                },
                                                errorListener = { _, _ ->
                                                    uploadSuccess = false
                                                    uploading = false
                                                    shouldRetryUpload = true
                                                    uploadStatus = context.getString(
                                                        R.string.upload_failed, context.getString(
                                                            R.string.connection_error
                                                        )
                                                    )
                                                    onCancel = null
                                                    uploadLock.unlock()
                                                },
                                                uploadCallbacks = {
                                                    uploadProgress = it
                                                })
                onCancel = {
                    call.cancel()
                    uploadLock.unlock()
                }
            } else {
                sharedViewModel.logout(context, context)
                uploadLock.unlock()
                uploadDialog = false
                uploading = false
            }
        }
    }

    fun <T> writeToDatastore(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch {
            preferencesRepository.setValue(key, value)
        }
    }

    fun logout(context: Activity) = sharedViewModel.logout(context, context)


    fun changeUsername(
        username: String? = null,
        email: String? = null,
        setUsername: ((String?) -> Unit)? = null,
        setEmail: ((String?) -> Unit)? = null,
        setCaption: (String) -> Unit,
        setStatus: (error: Boolean, loading: Boolean) -> Unit,
        dismissDialog: () -> Unit,
        context: Activity
    ) {
        viewModelScope.launch {
            val token = preferencesRepository.getValue(stringPreferencesKey(MY_TOKEN))
            if (token == null) {
                val loginIntent = Intent(context, LoginActivity::class.java)
                context.startActivity(loginIntent)
                return@launch
            }
            repository.editUser(token = token,
                                username = username,
                                email = email,
                                responseListener = { _, response, _ ->
                                    setStatus(!response.isSuccessful, false)
                                    when (response.code()) {
                                        200 -> {
                                            setCaption("")
                                            setUsername?.invoke(username)
                                            setEmail?.invoke(email)
                                            dismissDialog()
                                        }
                                        400 -> {
                                            setCaption(
                                                if (username != null) context.getString(
                                                    R.string.username_in_use
                                                ) else if (email != null) context.getString(
                                                    R.string.email_in_use
                                                ) else ""
                                            )
                                        }
                                        403 -> {
                                            setCaption("")
                                            dismissDialog()
                                            sharedViewModel.logout(context, context)
                                        }
                                        else -> {
                                            setCaption(
                                                context.getString(
                                                    R.string.unknown_error
                                                )
                                            )
                                        }
                                    }
                                },
                                errorListener = { _, _ ->
                                    setStatus(true, false)
                                    currentSnackBar = SnackBarData(
                                        context.getString(
                                            R.string.disconnected
                                        ), duration = SnackbarDuration.Long
                                    )
                                })
        }

    }

    fun launchShareSheet(context: Context) {
        viewModelScope.launch {
            val username = preferencesRepository.getValue(
                stringPreferencesKey(
                    MainViewModel.MY_ID
                )
            )
            if (username != null) {
                val sharingIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    val shareBody = context.getString(R.string.share_text, username)
                    putExtra(Intent.EXTRA_TEXT, shareBody)
                }
                context.startActivity(Intent.createChooser(sharingIntent, null))
            }
        }
    }

    companion object {
        val ICON_SIZE = IntSize(128, 128)
    }

    inner class UserInfoChangeListener(
        private val context: Activity, private val model: SettingsViewModel
    ) {

        private fun <T> onResponse(response: Response<*>, newValue: T, key: Preferences.Key<T>) {
            synchronized(this) {
                outstandingRequests--
            }
            val message = when (response.code()) {
                200 -> {
                    applicationScope.launch {
                        preferencesRepository.setValue(key, newValue)
                    }
                    if (model.outstandingRequests == 0) {
                        R.string.saved
                    } else null
                }
                400 -> {
                    if (response.errorBody()?.string()?.contains("in use") == true) {
                        R.string.email_in_use
                    } else {
                        R.string.invalid_email
                    }
                }
                403 -> {
                    sharedViewModel.logout(context, context)
                    R.string.confirm_logout_title
                }
                429 -> {
                    R.string.rate_limited
                }
                else -> {
                    R.string.unknown_error
                }
            }
            if (message != null) {
                currentSnackBar = SnackBarData(
                    context.getString(message),
                    withDismissAction = false,
                    actionLabel = context.getString(android.R.string.ok),
                    duration = SnackbarDuration.Long
                )
            }
        }

        private fun onError() {
            synchronized(this) {
                outstandingRequests--
            }
            currentSnackBar = SnackBarData(
                context.getString(R.string.disconnected), duration = SnackbarDuration.Long
            )
        }

        fun onPreferenceChange(
            preference: Preferences.Key<String>, newValue: String
        ): Boolean {
            viewModelScope.launch {
                val token = preferencesRepository.getValue(stringPreferencesKey(MY_TOKEN))
                if (token != null) {
                    currentSnackBar = SnackBarData(
                        context.getString(R.string.saving),
                        withDismissAction = true,
                        duration = SnackbarDuration.Indefinite
                    )
                    synchronized(this) {
                        outstandingRequests++
                    }
                    when (preference.name) {
                        context.getString(R.string.first_name_key) -> {
                            repository.editUser(token = token,
                                                firstName = newValue,
                                                responseListener = { _, response, _ ->
                                                    onResponse(
                                                        response, newValue, preference
                                                    )
                                                },
                                                errorListener = { _, _ ->
                                                    onError()
                                                })
                        }
                        context.getString(R.string.last_name_key) -> {
                            repository.editUser(token = token,
                                                lastName = newValue,
                                                responseListener = { _, response, _ ->
                                                    onResponse(
                                                        response, newValue, preference
                                                    )
                                                },
                                                errorListener = { _, _ ->
                                                    onError()
                                                })
                        }
                        context.getString(R.string.email_key) -> {
                            repository.editUser(token = token,
                                                email = newValue,
                                                responseListener = { _, response, _ ->
                                                    onResponse(
                                                        response, newValue, preference
                                                    )
                                                },
                                                errorListener = { _, _ ->
                                                    onError()
                                                })
                        }
                    }
                } else {
                    sharedViewModel.logout(context, context)
                }
            }
            return false
        }

    }
}