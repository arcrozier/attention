package com.aracroproducts.common

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
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
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.aracroproducts.common.AlertHandler.Companion.ALERT_CHANNEL_ID
import com.aracroproducts.common.AlertSendService.Companion.EXTRA_RECIPIENT
import com.aracroproducts.common.AlertSendService.Companion.FRIEND_SERVICE_CHANNEL_ID
import com.aracroproducts.common.AlertSendService.Companion.SERVICE_CHANNEL_ID
import retrofit2.HttpException
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Rect as ComposeRect


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
fun notifyUser(context: Context, text: String, mainActivity: Class<*>, message: Message? = null) {
    val intent = getSendIntent(context, message, mainActivity)
    val notificationID = System.currentTimeMillis().toInt()

        val pendingIntent =
            PendingIntent.getActivity(context, notificationID, intent, PendingIntent.FLAG_IMMUTABLE)

        createFailedAlertNotificationChannel(context)
        val builder: NotificationCompat.Builder =
            NotificationCompat.Builder(context, FAILED_ALERT_CHANNEL_ID)
    builder.setSmallIcon(R.drawable.icon)
            .setContentTitle(context.getString(R.string.alert_failed)).setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX).setContentIntent(pendingIntent)
            .setAutoCancel(true)

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

fun getSendIntent(context: Context, message: Message?, mainActivity: Class<*>): Intent {
    val intent = Intent(context, mainActivity)
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
        val canvasBounds = ComposeRect(Offset.Zero, size)
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

/**
 * Helper function to create the notification channel for the failed alert
 *
 * @param context   A context for getting strings
 */
fun createFailedAlertNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name: CharSequence = context.getString(R.string.alert_failed_channel_name)
        val description = context.getString(R.string.alert_failed_channel_description)
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(FAILED_ALERT_CHANNEL_ID, name, importance)
        channel.description =
            description // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
}

fun createFriendRequestNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name: CharSequence = context.getString(R.string.friend_request_channel_name)
        val description = context.getString(R.string.friend_service_channel_description)
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(FRIEND_REQUEST_CHANNEL_ID, name, importance)
        channel.description =
            description // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
}

fun createForegroundServiceNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        run {
            val name: CharSequence = context.getString(R.string.service_channel_name)
            val description = context.getString(R.string.service_channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(SERVICE_CHANNEL_ID, name, importance)
            channel.description =
                description // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager =
                context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        run {
            val name: CharSequence = context.getString(R.string.friend_service_channel_name)
            val description = context.getString(R.string.friend_service_channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(FRIEND_SERVICE_CHANNEL_ID, name, importance)
            channel.description =
                description // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager =
                context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}

/**
 * Creates the notification channel for notifications that are displayed alongside dialogs
 */
fun createNotificationChannel(context: Context) { // Create the NotificationChannel, but only on API 26+ because
    // the NotificationChannel class is new and not in the support library
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name: CharSequence = context.getString(R.string.alert_channel_name)
        val description = context.getString(R.string.alert_channel_description)
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(ALERT_CHANNEL_ID, name, importance)
        channel.description =
            description // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
}

fun pushFriendShortcut(context: Context, friend: Friend, mainActivity: Class<*>) {
    val contactCategories = setOf(SHARE_CATEGORY)
    val icon = if (friend.photo != null) IconCompat.createWithBitmap(run {
        val imageDecoded = Base64.decode(friend.photo, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(imageDecoded, 0, imageDecoded.size)

        // From diesel - https://stackoverflow.com/a/15537470/7484693
        // Licensed under CC BY-SA 3.0
        val output: Bitmap = if (bitmap.width > bitmap.height) {
            Bitmap.createBitmap(bitmap.height, bitmap.height, Bitmap.Config.ARGB_8888)
        } else {
            Bitmap.createBitmap(bitmap.width, bitmap.width, Bitmap.Config.ARGB_8888)
        }

        Log.d("Utils", "${bitmap.width} x ${bitmap.height}")
        val canvas = Canvas(output)

        val color = 0xff424242u
        val paint = android.graphics.Paint()
        val rect = Rect(0, 0, bitmap.width, bitmap.height)

        val r = if (bitmap.width > bitmap.height) {
            (bitmap.height / 2).toFloat()
        } else {
            (bitmap.width / 2).toFloat()
        }

        paint.isAntiAlias = true
        canvas.drawARGB(0, 0, 0, 0)
        paint.color = color.toInt()
        canvas.drawCircle(r, r, r, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        output
    }) else null

    ShortcutManagerCompat.pushDynamicShortcut(
        context,
        ShortcutInfoCompat.Builder(context, friend.username).setShortLabel(friend.name)
            .setPerson(
                Person.Builder().setName(friend.name).setKey(friend.username)
                    .setImportant(true)
                    .setIcon(icon).build()
            ).setIcon(icon).setIntent(Intent(
                context, mainActivity
            ).apply {
                action = Intent.ACTION_SENDTO
                putExtra(EXTRA_RECIPIENT, friend.username)
            }).setLongLived(true).setCategories(contactCategories).setPerson(
                Person.Builder().setName(friend.name).build()
            ).build()
    )
}

const val EXTRA_ALERT_ID = "com.aracroproducts.attention.extra.ALERT_ID"
const val EXTRA_BODY = "com.aracroproducts.attention.extra.BODY"

const val REQUEST_MAIN_ACTIVITY = -1
const val NO_ID = -1
const val USER_INFO = "user"
const val FCM_TOKEN = "fcm_token"
const val FRIEND_REQUEST_CHANNEL_ID = "friend_request_channel"
const val FAILED_ALERT_CHANNEL_ID = "Failed alert channel"

private const val SHARE_CATEGORY =
    "com.aracroproducts.attentionv2.sharingshortcuts.category.TEXT_SHARE_TARGET"