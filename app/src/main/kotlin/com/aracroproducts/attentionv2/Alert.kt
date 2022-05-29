package com.aracroproducts.attentionv2

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aracroproducts.attentionv2.ui.theme.AppTheme
import com.aracroproducts.attentionv2.ui.theme.HarmonizedTheme

/**
 * An Activity that displays the pop up dialog for an alert
 */
class Alert : AppCompatActivity() {
    private val sTAG = javaClass.name

    val alertModel: AlertViewModel by viewModels(factoryProducer = {
        AlertViewModelFactory(intent, AttentionRepository(AttentionDB.getDB(this)), application)
    })

    inner class AlertViewModelFactory(
        private val intent: Intent,
        private val attentionRepository: AttentionRepository,
        private val application: Application
    ) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AlertViewModel::class.java)) {
                return AlertViewModel(intent, attentionRepository, application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
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
                HarmonizedTheme {
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
    fun Dialog(message: AnnotatedString) {
        AlertDialog(
            onDismissRequest = { },
            dismissButton = {
                Row {
                    AnimatedVisibility(visible = !alertModel.silenced, enter = fadeIn(), exit = fadeOut()) {
                        TextButton(onClick = { alertModel.silence() }) {
                            Text(text = getString(R.string.silence))
                        }
                    }

                    AnimatedVisibility(visible = alertModel.showDNDButton &&
                        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted,
                        enter = fadeIn(), exit = fadeOut
                        ()) {
                        TextButton(onClick = {
                            alertModel.silence()
                            val intent = Intent(
                                Settings
                                    .ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
                            )
                            startActivity(intent)
                        }) {
                            Text(text = getString(R.string.open_settings))
                        }
                    }
                }
            },
            confirmButton = {
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