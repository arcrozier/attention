package com.aracroproducts.attentionv2

import android.Manifest
import android.app.Activity
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.*
import android.util.Base64
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.CompoundBarcodeView
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import kotlinx.coroutines.launch

/**
 * An activity that displays a QR code and has a camera feed to add a friend and allow people to add
 * the user
 */
class Add : AppCompatActivity() {
    // TODO open add user links and populate layout
    // TODO make this JetPack compose too - see https://stackoverflow.com/questions/68139363/using-zxing-library-with-jetpack-compose
    // or https://stackoverflow.com/questions/69618411/firebase-barcode-scanner-using-jetpack-compose-not-working
    private var barcodeView: DecoratedBarcodeView? = null
    private var v: Vibrator? = null
    private val friendModel: MainViewModel by viewModels()
    private val addModel: AddViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colors = if (isSystemInDarkTheme()) darkColors else lightColors) {
                AddWrapper()
            }
        }
        v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = this.getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        if (hasCameraPermission()) {
            addModel.startScan()
        }
    }

    @Composable
    fun AddWrapper() {
        AddScreen(name = friendModel.getMyName(),
                id = Base64.encodeToString(friendModel.getMyID()?.encoded, Base64.URL_SAFE))
    }

    @Composable
    fun AddScreen(name: String, id: String) {
        val scaffoldState = rememberScaffoldState()
        val scope = rememberCoroutineScope()
        val scannedName = rememberSaveable {
            mutableStateOf("")
        }
        val scannedID = rememberSaveable {
            mutableStateOf("")
        }
        Scaffold(scaffoldState = scaffoldState) {
            Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement
                    .spacedBy(Dp(32F))) {
                BarcodeScannerPreview(addModel.scanning.value,
                        onScan = { result ->
                            val separatorIndex = result.lastIndexOf(' ')
                            val idPart = result.substring(separatorIndex + 1).trim { it <= ' ' }
                            scannedID.value = idPart
                            if (separatorIndex != -1) {
                                val namePart =
                                        result.substring(0, separatorIndex).trim { it <= ' ' }
                                scannedName.value = namePart
                            }
                            addModel.onPause()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                v!!.vibrate(VibrationEffect.createOneShot(250,
                                        VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                v!!.vibrate(250)
                            }
                        },
                        onScanError = {
                            scope.launch {
                                scaffoldState.snackbarHostState.showSnackbar(message = it)
                            }
                        })
                ManualEntry(name = scannedName.value, id = scannedID.value) {
                    scope.launch { scaffoldState.snackbarHostState.showSnackbar(message = it) }
                }
                QRCode(nameAndID = "$name $id")
                UserID("$name $id")
            }
        }
    }

    @Composable
    fun BarcodeScannerPreview(scanning: Boolean,
                              onScan: (String) -> Unit,
                              onScanError: (String) -> Unit) {
        var lastText by remember { mutableStateOf("") }
        var lastSnackBar by remember { mutableStateOf<Long>(0) }
        val compoundBarcodeView = remember {
            CompoundBarcodeView(this).apply {
                val capture = CaptureManager(context as Activity, this)
                capture.initializeFromIntent((context as Activity).intent, null)
                this.setStatusText("")
                capture.decode()
                this.decodeContinuous { result ->
                    if (!scanning) {
                        return@decodeContinuous
                    }
                    result.text?.let {
                        //Do something and when you finish this something
                        //put scanFlag = false to scan another item
                        if (it == lastText) { // this could be an issue if someone edits the
                            // recorded ID and then wants to scan the same barcode again
                            if (System.currentTimeMillis() - lastSnackBar > 5000) {
                                onScanError(getString(R.string.scan_new))
                                lastSnackBar = System.currentTimeMillis()
                            }
                            return@decodeContinuous
                        }
                        lastText = it
                        onScan(it)
                    }
                }
                addModel.addOnKeyListener { keyCode, event -> this.onKeyDown(keyCode, event) }
                addModel.addOnPauseListener {
                    if (scanning) {
                        pause()
                        addModel.scanning.value = false
                    }
                }
                addModel.addOnResumeListener {
                    if (hasCameraPermission() && !scanning) {
                        resume()
                        addModel.scanning.value = true
                    }
                }
            }
        }

        AndroidView(
                modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .clickable {
                            addModel.onResume()
                        },
                factory = { compoundBarcodeView },
        )
    }

    @Composable
    fun ManualEntry(name: String, id: String, displaySnackbar: (String) -> Unit) {
        var displayName by rememberSaveable {
            mutableStateOf(name)
        }
        var displayID by rememberSaveable {
            mutableStateOf(id)
        }
        var nameIsError by remember { mutableStateOf(false) }
        var idIsError by remember {
            mutableStateOf(false)
        }
        Row {
            Column {
                TextField(value = displayName,
                        onValueChange = { displayName = it },
                        isError = nameIsError)
                TextField(value = displayID,
                        onValueChange = { displayID = it },
                        isError = idIsError)
            }
            Button(onClick = {
                if (displayName.isBlank()) {
                    displaySnackbar(getString(R.string.no_name))
                    nameIsError = true
                }
                if (displayID.isBlank()) {
                    displaySnackbar(getString(R.string.no_id))
                    idIsError = true
                }
                if (displayName.isBlank() || displayID.isBlank()) {
                    return@Button
                }
                try {
                    Base64.decode(displayID, Base64.URL_SAFE)
                } catch (e: IllegalArgumentException) {
                    idIsError = true
                    displaySnackbar(getString(R.string.invalid_id))
                    return@Button
                }
                friendModel.onAddFriend(Friend(displayID, displayName))
                finish()
            }) {
                Text(text = getString(android.R.string.ok))
            }
        }
    }

    @Composable
    fun QRCode(nameAndID: String) {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(nameAndID, BarcodeFormat.QR_CODE, 512, 512)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        Image(bitmap = bmp.asImageBitmap(), contentDescription = getString(R.string
                .describe_QR))
    }

    @Composable
    fun UserID(nameAndID: String) {
        Row {
            Text(text = nameAndID, modifier = Modifier.fillMaxWidth())
            IconButton(onClick = { /* TODO generate URL for User and open share dialog*/ }) {
                Icon(Icons.Filled.Share, contentDescription = getString(R.string.share_user_id))
            }
        }
    }

    @Preview
    @Composable
    fun PreviewAdd() {
        AddScreen("Example name", "Example id")
    }

    /**
     * Receives the result of asking for camera permission and starts scanning if yes
     * @param requestCode   - The request code attached to the permission request
     * @param permissions   - The permissions requested
     * @param grantResults  - The results for each permission
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        if (requestCode == CAMERA_CALLBACK_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                addModel.startScan()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onResume() {
        super.onResume()
        addModel.onResume()
    }

    override fun onPause() {
        super.onPause()
        addModel.onPause()
    }

    /**
     * Passes key presses to the barcode view
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        addModel.onKey(keyCode, event)
        return true
    }

    /**
     * Helper method to handle checking and getting the camera permission to start scanning
     */
    private fun hasCameraPermission(): Boolean {
        return if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            if (ActivityCompat.checkSelfPermission(this,
                            Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            // app already has camera permission. Great!
                true
            else if (ActivityCompat.shouldShowRequestPermissionRationale(
                            this,
                            Manifest.permission.CAMERA)) {
                // display dialog explaining why the app is requesting camera permission
                val alert = AlertDialog.Builder(this).create()
                alert.setTitle(getString(R.string.permissions_needed))
                alert.setMessage(getString(R.string.permission_details))
                alert.setButton(DialogInterface.BUTTON_POSITIVE,
                        getString(R.string.allow)) { dialogInterface: DialogInterface, _: Int ->
                    dialogInterface.cancel()
                    ActivityCompat.requestPermissions(this@Add, arrayOf(Manifest.permission.CAMERA),
                            CAMERA_CALLBACK_CODE)
                }
                alert.setButton(DialogInterface.BUTTON_NEGATIVE, this.getString(
                        R.string.deny)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
                alert.show()
                false
            } else { // request the permission
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA),
                        CAMERA_CALLBACK_CODE)
                false
            }
        } else { // device does not have a camera for some reason
            barcodeView = findViewById(R.id.zxing_barcode_scanner)
            barcodeView?.setStatusText(getString(R.string.no_camera))
            false
        }
    }

    companion object {
        private const val CAMERA_CALLBACK_CODE = 10
    }
}