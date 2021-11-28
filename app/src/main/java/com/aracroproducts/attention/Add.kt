package com.aracroproducts.attention

import android.Manifest
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.BarcodeCallback
import com.google.android.material.snackbar.Snackbar
import android.widget.TextView
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.BarcodeFormat
import android.graphics.Bitmap
import com.google.zxing.WriterException
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.content.DialogInterface
import android.graphics.Color
import android.os.*
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import java.util.*

class Add : AppCompatActivity() {
    private var barcodeView: DecoratedBarcodeView = DecoratedBarcodeView(this)
    private var v: Vibrator? = null
    private var lastText: String = ""
    private var cameraActive = true
    private var lastSnackBar: Long = 0

    // Handles events with the barcode scanner
    private val callback = BarcodeCallback { result ->
        if (result.text == null) {
            return@BarcodeCallback
        }
        if (result.text == lastText) { // this could be an issue if someone edits the recorded ID and then wants to scan the same barcode again
            if (System.currentTimeMillis() - lastSnackBar > 5000) {
                val layout = findViewById<View>(R.id.add_constraint)
                val snackbar = Snackbar.make(layout, R.string.scan_new, Snackbar.LENGTH_SHORT)
                snackbar.show()
                lastSnackBar = System.currentTimeMillis()
            }
            return@BarcodeCallback
        }
        lastText = result.text.toString()
        val separatorIndex = lastText.lastIndexOf(' ')
        val id = lastText.substring(separatorIndex + 1).trim { it <= ' ' }
        val enterId = findViewById<TextView>(R.id.manual_code)
        enterId.text = id
        if (separatorIndex != -1) {
            val name = lastText.substring(0, separatorIndex).trim { it <= ' ' }
            val enterName = findViewById<TextView>(R.id.manual_name)
            enterName.text = name
        }
        pause()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v!!.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            v!!.vibrate(250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add)
        v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =  this.getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        val writer = QRCodeWriter()
        val id = getSharedPreferences(MainActivity.USER_INFO, MODE_PRIVATE).getString(MainActivity.MY_ID, null)
        val name = PreferenceManager.getDefaultSharedPreferences(this).getString(getString(R.string.name_key), null)
        val user = "$name $id"
        try {
            val bitMatrix = writer.encode(user, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            (findViewById<View>(R.id.QR) as ImageView).setImageBitmap(bmp)
        } catch (e: WriterException) {
            e.printStackTrace()
        }
        (findViewById<View>(R.id.user_id_text) as TextView).text = user
        if (hasCameraPermission()) {
            startScan()
        }
    }

    /**
     * Helper method to start scanning for codes
     */
    private fun startScan() {
        barcodeView = findViewById(R.id.zxing_barcode_scanner)
        val formats: Collection<BarcodeFormat> = listOf(BarcodeFormat.QR_CODE, BarcodeFormat.CODE_39)
        barcodeView.barcodeView.decoderFactory = DefaultDecoderFactory(formats)
        barcodeView.initializeFromIntent(intent)
        barcodeView.decodeContinuous(callback)
        barcodeView.setOnClickListener { resume() }
    }

    /**
     * Validates the inputs before adding them to friend list and returning to the start screen
     * @param view  - The view object that calls this method from the activity
     */
    fun finishActivity(view: View?) {
        val idView = findViewById<TextView>(R.id.manual_code)
        val nameView = findViewById<TextView>(R.id.manual_name)
        var id = idView.text.toString()
        var name = nameView.text.toString()
        if (id.isEmpty()) {
            idView.error = getString(R.string.no_id)
            resume()
            return
        }
        if (name.isEmpty()) {
            nameView.error = getString(R.string.no_name)
            return
        }
        id = id.trim { it <= ' ' }
        name = name.trim { it <= ' ' }
        if (id.contains(" ")) {
            idView.error = getString(R.string.invalid_id)
            resume()
            return
        }
        val preferences = getSharedPreferences(MainActivity.FRIENDS, MODE_PRIVATE)
        val friendJson = preferences.getString("friends", null)
        var friendList = ArrayList<Array<String?>?>()
        val gson = Gson()
        if (friendJson != null) {
            val arrayListType = object : TypeToken<ArrayList<Array<String?>?>?>() {}.type
            friendList = gson.fromJson(friendJson, arrayListType)
        }
        val newFriend = arrayOf<String?>(name, id)
        friendList.add(newFriend)
        val editor = preferences.edit()
        editor.putString("friends", gson.toJson(friendList))
        editor.apply()
        finish()
    }

    /**
     * Receives the result of asking for camera permission and starts scanning if yes
     * @param requestCode   - The request code attached to the permission request
     * @param permissions   - The permissions requested
     * @param grantResults  - The results for each permission
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == CAMERA_CALLBACK_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScan()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onResume() {
        super.onResume()
        if (hasCameraPermission()) {
            barcodeView.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        barcodeView.pause()
    }

    /**
     * Helper method to pause the scanning
     */
    private fun pause() {
        if (cameraActive) {
            barcodeView.pause()
            cameraActive = false
        }
    }

    /**
     * Helper method to resume scanning
     */
    private fun resume() {
        if (!cameraActive) {
            barcodeView.resume()
            cameraActive = true
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return barcodeView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }

    /**
     * Helper method to handle checking and getting the camera permission to start scanning
     */
    private fun hasCameraPermission(): Boolean {
        return if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) //app already has camera permission. Great!
                true else if (ActivityCompat.shouldShowRequestPermissionRationale(this,  // display dialog explaining why the app is requesting camera permission
                            Manifest.permission.CAMERA)) {
                val alert = AlertDialog.Builder(this).create()
                alert.setTitle(getString(R.string.permissions_needed))
                alert.setMessage(getString(R.string.permission_details))
                alert.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.allow)) { dialogInterface: DialogInterface, i: Int ->
                    dialogInterface.cancel()
                    ActivityCompat.requestPermissions(this@Add, arrayOf(Manifest.permission.CAMERA), CAMERA_CALLBACK_CODE)
                }
                alert.setButton(DialogInterface.BUTTON_NEGATIVE, this.getString(R.string.deny)) { dialogInterface: DialogInterface, i: Int -> dialogInterface.cancel() }
                alert.show()
                false
            } else { // request the permission
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_CALLBACK_CODE)
                false
            }
        } else { // device does not have a camera for some reason
            barcodeView = findViewById(R.id.zxing_barcode_scanner)
            barcodeView.setStatusText(getString(R.string.no_camera))
            false
        }
    }

    companion object {
        private const val CAMERA_CALLBACK_CODE = 10
    }
}