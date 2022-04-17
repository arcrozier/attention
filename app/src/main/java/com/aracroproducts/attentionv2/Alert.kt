package com.aracroproducts.attentionv2

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aracroproducts.attentionv2.ui.theme.AppTheme
import com.aracroproducts.attentionv2.ui.theme.HarmonizedTheme

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                HarmonizedTheme() {
                    Dialog(message = alertModel.message)
                }
            } else {
                AppTheme {
                    Dialog(message = alertModel.message)
                }
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