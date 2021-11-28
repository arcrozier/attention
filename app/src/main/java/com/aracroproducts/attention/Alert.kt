package com.aracroproducts.attention;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.util.HashSet;
import java.util.Set;

public class Alert extends AppCompatActivity {

    private final String TAG = getClass().getName();

    private String from;
    private String message;
    private int id;
    private CountDownTimer timer;
    private Ringtone r;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alert);
        this.setFinishOnTouchOutside(false);

        Log.d(TAG, "Dialog opened");

        TextView messageView = findViewById(R.id.alert_message_view);

        Intent intent = getIntent();
        from = intent.getStringExtra(AlertHandler.REMOTE_FROM);
        message = intent.getStringExtra(AlertHandler.REMOTE_MESSAGE);
        if (intent.hasExtra(AlertHandler.ASSOCIATED_NOTIFICATION)) id = intent.getIntExtra(AlertHandler.ASSOCIATED_NOTIFICATION, 0);

        messageView.setText(message);

        if (intent.getBooleanExtra(AlertHandler.SHOULD_VIBRATE, true)) {

            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
            Set<String> vibrate = settings.getStringSet(getString(R.string.vibrate_preference_key), new HashSet<>());
            Set<String> ring = settings.getStringSet(getString(R.string.ring_preference_key), new HashSet<>());

            ring(ring);
            vibrate(vibrate);
        }
    }

    private boolean shouldRing(Set<String> ringAllowed) {
        AudioManager manager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        switch (manager.getRingerMode()) {
            case AudioManager.RINGER_MODE_SILENT:
                if (ringAllowed.contains("silent")) return true;
                break;
            case AudioManager.RINGER_MODE_VIBRATE:
                if (ringAllowed.contains("vibrate")) return true;
                break;
            case AudioManager.RINGER_MODE_NORMAL:
                if (ringAllowed.contains("ring")) return true;
                break;
        }
        return false;

    }

    private boolean shouldVibrate(Set<String> vibrateAllowed) {
        AudioManager manager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        switch (manager.getRingerMode()) {
            case AudioManager.RINGER_MODE_SILENT:
                if (vibrateAllowed.contains("silent")) return true;
                break;
            case AudioManager.RINGER_MODE_VIBRATE:
                if (vibrateAllowed.contains("vibrate")) return true;
                break;
            case AudioManager.RINGER_MODE_NORMAL:
                if (vibrateAllowed.contains("ring")) return true;
                break;
        }
        return false;
    }

    private void ring(Set<String> ringAllowed) {
        if (shouldRing(ringAllowed)) {
            Uri notification = RingtoneManager.getActualDefaultRingtoneUri(Alert.this, RingtoneManager.TYPE_RINGTONE);
            r = RingtoneManager.getRingtone(Alert.this, notification);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) r.setVolume(1.0f);
            r.play();

        }
    }

    private void vibrate(Set<String> vibrateAllowed) {
        if (!shouldRing(vibrateAllowed)) return;

        timer = new CountDownTimer(5000, 500) {
            @Override
            public void onTick(long l) {
                if (shouldVibrate(vibrateAllowed)) {
                    Log.d(TAG, "Vibrating device");
                    Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(400);
                    }
                }

            }

            @Override
            public void onFinish() {

            }
        };
        timer.start();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (id != 0) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(id);
        }

        if (isFinishing()) return; // prevent this notification from being shown when the user clicks "ok"

        Intent intent = new Intent(this, Alert.class);
        intent.putExtra("alert_message", message);
        intent.putExtra("alert_from", from);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        createNotificationChannel();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, AlertHandler.CHANNEL_ID);
        builder
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(getString(R.string.notification_title, from))
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setContentIntent(pendingIntent).setAutoCancel(true);

        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(this);
        notificationManagerCompat.notify((int) System.currentTimeMillis(), builder.build());
    }

    public void onOK(View view) {
        if (timer != null) {
            timer.cancel();
        }
        if (r != null && r.isPlaying()) {
            r.stop();
        }
        finish();
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(AlertHandler.CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}