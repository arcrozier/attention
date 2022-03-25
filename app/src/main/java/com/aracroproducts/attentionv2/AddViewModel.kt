package com.aracroproducts.attentionv2

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class AddViewModel : ViewModel() {

    private val onPauseListeners = ArrayList<() -> Unit>()
    private val onResumeListeners = ArrayList<() -> Unit>()
    private val onKeyListeners = ArrayList<(Int, KeyEvent) -> Unit>()
    private val onSnackBarListeners = ArrayList<(String) -> Unit>()
    var scanning: Boolean by mutableStateOf(false)
    var otherName: String by mutableStateOf("")
    var otherID: String by mutableStateOf("")
    var barcodeStatus: String by mutableStateOf("")

    fun addOnPauseListener(listener: () -> Unit) {
        onPauseListeners.add(listener)
    }

    fun addOnResumeListener(listener: () -> Unit) {
        onResumeListeners.add(listener)
    }

    fun addOnKeyListener(listener: (Int, KeyEvent) -> Unit) {
        onKeyListeners.add(listener)
    }

    fun addOnSnackBarListener(listener: (String) -> Unit) {
        onSnackBarListeners.add(listener)
    }

    /**
     * Call to pause the barcode scanning
     */
    fun onPause() {
        for (listener in onPauseListeners) {
            listener()
        }
    }

    /**
     * Call to resume the barcode scanning
     */
    fun onResume() {
        for (listener in onResumeListeners) {
            listener()
        }
    }

    fun onKey(keyCode: Int, event: KeyEvent) {
        for (listener in onKeyListeners) {
            listener(keyCode, event)
        }
    }

    fun startScan() {
        scanning = true
    }

    fun showSnackBar(text: String) {
        for (listener in onSnackBarListeners) {
            listener(text)
        }
    }
}