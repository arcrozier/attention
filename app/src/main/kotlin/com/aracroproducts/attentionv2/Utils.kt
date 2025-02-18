package com.aracroproducts.attentionv2

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aracroproducts.attentionv2.MainViewModel.Companion.EXTRA_BODY
import com.aracroproducts.attentionv2.MainViewModel.Companion.EXTRA_RECIPIENT
import com.aracroproducts.attentionv2.MainViewModel.Companion.FAILED_ALERT_CHANNEL_ID
import com.aracroproducts.attentionv2.MainViewModel.Companion.createFailedAlertNotificationChannel
import retrofit2.HttpException
import kotlin.math.min
import kotlin.math.roundToInt


fun filterUsername(username: String): String {
    return username.substring(0, min(username.length, 150)).filter {
        it.isLetterOrDigit() or (it == '@') or (it == '_') or (it == '-') or (it == '+') or (it == '.')
    }
}

fun filterSpecialChars(string: String): String {
    return string.filter { letter ->
        letter != '\n' && letter != '\t' && letter != '\r'
    }
}

const val DISABLED_ALPHA = 0.38f
const val HIGH_ALPHA = 1f

/**
 * Notifies the user that an alert was not successfully sent
 *
 * @param text  - The message to display in the body of the notification
 * @requires    - Code is one of ErrorType.SERVER_ERROR or ErrorType.BAD_REQUEST
 */
fun notifyUser(context: Context, text: String, message: Message? = null) {
        val intent = getSendIntent(context, message)

        val pendingIntent =
            PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        createFailedAlertNotificationChannel(context)
        val builder: NotificationCompat.Builder =
            NotificationCompat.Builder(context, FAILED_ALERT_CHANNEL_ID)
    builder.setSmallIcon(R.drawable.icon)
            .setContentTitle(context.getString(R.string.alert_failed)).setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX).setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationID = System.currentTimeMillis().toInt()
        val notificationManagerCompat = NotificationManagerCompat.from(context)
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationManagerCompat.notify(notificationID, builder.build())
}

fun getSendIntent(context: Context, message: Message?): Intent {
    val intent = Intent(context, MainActivity::class.java)
    if (message != null) {
        intent.action = context.getString(R.string.reopen_failed_alert_action)
        intent.putExtra(EXTRA_RECIPIENT, message.otherId)
        intent.putExtra(EXTRA_BODY, message.message)
    }
    return intent
}

fun Modifier.grayScale(saturation: Float = 0f): Modifier {
    // by cesonha and Saket: https://stackoverflow.com/a/76244926
    // CC BY-SA 4.0
    val saturationMatrix = ColorMatrix().apply { setToSaturation(saturation) }
    val saturationFilter = ColorFilter.colorMatrix(saturationMatrix)
    val paint = Paint().apply { colorFilter = saturationFilter }

    return drawWithCache {
        val canvasBounds = Rect(Offset.Zero, size)
        onDrawWithContent {
            drawIntoCanvas {
                it.saveLayer(canvasBounds, paint)
                drawContent()
                it.restore()
            }
        }
    }
}

@Composable
fun MeasureView(
    viewToMeasure: @Composable () -> Unit,
    content: @Composable (measuredWidth: Dp, measuredHeight: Dp) -> Unit,
) {
    SubcomposeLayout { constraints ->
        val measuredSize = subcompose("viewToMeasure", viewToMeasure)[0]
            .measure(constraints)

        val contentPlaceable = subcompose("content") {
            content(measuredSize.width.toDp(), measuredSize.height.toDp())
        }[0].measure(constraints)
        layout(contentPlaceable.width, contentPlaceable.height) {
            contentPlaceable.place(0, 0)
        }
    }
}

fun min(a: Dp, b: Dp): Dp = if (a < b) a else b

val centerWithBottomElement = object : Arrangement.HorizontalOrVertical {
    override fun Density.arrange(
        totalSize: Int,
        sizes: IntArray,
        layoutDirection: LayoutDirection,
        outPositions: IntArray
    ) {
        val consumedSize = sizes.fold(0) { a, b -> a + b }
        var current = (totalSize - consumedSize).toFloat() / 2
        sizes.forEachIndexed { index, size ->
            if (index == sizes.lastIndex) {
                outPositions[index] =
                    if (layoutDirection == LayoutDirection.Ltr) totalSize - size
                    else size
            } else {
                outPositions[index] =
                    if (layoutDirection == LayoutDirection.Ltr) current.roundToInt()
                    else totalSize - current.roundToInt()
                current += size.toFloat()
            }
        }
    }

    override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
        arrange(totalSize, sizes, LayoutDirection.Ltr, outPositions)
    }
}


fun Exception?.toMessage(): String {
    return "${(this as? HttpException)?.run { "${this::class.qualifiedName} ${this.message}\n${code()}\n${response()?.errorBody()}" } ?: this?.stackTraceToString()}"
}

const val EXTRA_ALERT_ID = "com.aracroproducts.attention.extra.ALERT_ID"

