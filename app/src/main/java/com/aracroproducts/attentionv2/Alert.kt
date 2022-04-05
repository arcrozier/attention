package com.aracroproducts.attentionv2

import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * An Activity that displays the pop up dialog for an alert
 */
class Alert : AppCompatActivity() {
    private val sTAG = javaClass.name

    private val alertModel: AlertViewModel by viewModels(factoryProducer = {
        AlertViewModelFactory(intent)
    })

    inner class AlertViewModelFactory(private val intent: Intent) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return modelClass.getConstructor(Intent::class.java).newInstance(intent)
        }
    }

    /**
     * Called when the activity is created
     *
     * @param savedInstanceState - Data saved from before a configuration changed. Not used
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // don't let users dismiss by tapping outside the dialog - prevent accidental dismissals
        setFinishOnTouchOutside(false)

        setContent {
            MaterialTheme(colors = if (isSystemInDarkTheme()) darkColors else lightColors) {
                Dialog(message = alertModel.message)
            }
        }

        Log.d(sTAG, "Dialog opened")
        alertModel.startPrompting()
    }

    @Composable
    fun Dialog(message: String) {
        AlertDialog(
                onDismissRequest = { },
                confirmButton = {
                    TextButton(onClick = { alertModel.silence() }) {
                        Text(text = getString(R.string.silence))
                    }
                },
                dismissButton = {
                    Button(onClick = {
                        alertModel.ok()
                        finish()
                    }) {
                        Text(text = getString(android.R.string.ok))
                    }
                },
                title = { Text(getString(R.string.alert_title)) },
                text = { Text(message) }
        )
    }
}