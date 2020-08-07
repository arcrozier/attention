package com.aracroproducts.attention;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

public class AlertHandler extends FirebaseMessagingService {

    private static final String TAG = AlertHandler.class.getName();

    protected static final String CHANNEL_ID = "Missed Alert Channel";
    protected static final String ALERT_CHANNEL_ID = "Alert Channel";

    protected static final String REMOTE_FROM = "alert_from";
    protected static final String REMOTE_TO = "alert_to";
    protected static final String REMOTE_MESSAGE = "alert_message";
    protected static final String ASSOCIATED_NOTIFICATION = "notification_id";
    protected static final String SHOULD_VIBRATE = "vibrate";

    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "New token: " + token);
        SharedPreferences preferences = getSharedPreferences(MainActivity.USER_INFO, Context.MODE_PRIVATE);
        if (!preferences.getString(MainActivity.MY_TOKEN, "").equals(token)) {
            Log.d(TAG, "Token is new: updating shared preferences");
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(MainActivity.UPLOADED, false);
            editor.putString(MainActivity.MY_TOKEN, token);
            editor.apply();
        }
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "Message received! " + remoteMessage.toString());
        Map<String, String> messageData = remoteMessage.getData();
        if (!senderIsFriend(messageData.get(REMOTE_FROM))) return; //checks if the sender is a friend of the user, ends if not

        SharedPreferences userInfo = getSharedPreferences(MainActivity.USER_INFO, Context.MODE_PRIVATE);

        if (!Objects.equals(messageData.get(REMOTE_TO), userInfo.getString(MainActivity.MY_ID, ""))) return; //if message is not addressed to the user, ends


        String senderName = getFriendNameForID(messageData.get(REMOTE_FROM));
        String message = messageData.get(REMOTE_MESSAGE);

        assert message != null;
        message = message.equals("null") ? getString(R.string.default_message, senderName) : getString(R.string.message_prefix, senderName, message);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (!manager.areNotificationsEnabled() || (!preferences.getBoolean(getString(R.string.override_dnd_key), false) && (manager.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL && manager.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_UNKNOWN))) {
            Log.d(TAG, "App is disabled from showing notifications or interruption filter is set to block notifications");
            showNotification(message, senderName, true);
            return;
        }

        try {
            if (Settings.Global.getInt(getContentResolver(), "zen_mode") > 1) {
                Log.d(TAG, "Device's zen mode is enabled");
                return;
            }
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        /*if (!pm.isInteractive()) { This functionality has been deprecated
            PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, TAG);
            wakeLock.acquire(500);
        }*/

        int id = showNotification(message, senderName, false);

        if (!pm.isInteractive() || Settings.canDrawOverlays(this)) {

            Intent intent = new Intent(this, Alert.class);
            intent.putExtra(REMOTE_FROM, senderName);
            intent.putExtra(REMOTE_MESSAGE, message);
            intent.putExtra(ASSOCIATED_NOTIFICATION, id);
            intent.putExtra(SHOULD_VIBRATE, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            Log.d(TAG, "Sender: " + senderName + ", " + messageData.get(REMOTE_FROM) + " Message: " + message);

            startActivity(intent);
        }
    }

    private boolean senderIsFriend(String sender) {
        SharedPreferences friends = getSharedPreferences(MainActivity.FRIENDS, Context.MODE_PRIVATE);
        String friendJson = friends.getString("friends", null);
        ArrayList<String[]> friendList = new ArrayList<>();

        Gson gson = new Gson();

        if (friendJson != null) {
            Type arrayListType = new TypeToken<ArrayList<String[]>>() {
            }.getType();
            friendList = gson.fromJson(friendJson, arrayListType);

            Log.d(TAG, friendJson);
        }

        return isStringInFriendList(sender, friendList);
    }

    private boolean isStringInFriendList(String id, ArrayList<String[]> friendList) {
        boolean found = false;
        for (int i = 0; i < friendList.size(); i++) {
            found = id.equals(friendList.get(i)[1]);
            if (found) break;
        }
        return found;
    }

    private String getFriendNameForID(String id) {
        SharedPreferences friends = getSharedPreferences(MainActivity.FRIENDS, Context.MODE_PRIVATE);
        String friendJson = friends.getString("friends", null);
        ArrayList<String[]> friendList = new ArrayList<>();

        Gson gson = new Gson();

        if (friendJson != null) {
            Type arrayListType = new TypeToken<ArrayList<String[]>>() {
            }.getType();
            friendList = gson.fromJson(friendJson, arrayListType);

            Log.d(TAG, friendJson);
        }

        for (int i = 0; i < friendList.size(); i++) {
            if (friendList.get(i)[1].equals(id)) {
                return friendList.get(i)[0];
            }
        }

        return null;
    }

    private int showNotification(String message, String senderName, boolean missed) {

        Intent intent = new Intent(this, Alert.class);
        intent.putExtra(REMOTE_MESSAGE, message);
        intent.putExtra(REMOTE_FROM, senderName);
        intent.putExtra(SHOULD_VIBRATE, false);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        NotificationCompat.Builder builder;

        if (missed) {
            createMissedNotificationChannel();

            builder = new NotificationCompat.Builder(this, AlertHandler.CHANNEL_ID);
            builder
                    .setSmallIcon(R.mipmap.add_foreground)
                    .setContentTitle(getString(R.string.notification_title, senderName))
                    .setContentText(message)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setContentIntent(pendingIntent).setAutoCancel(true);
        } else {
            createNotificationChannel();

             builder = new NotificationCompat.Builder(this, AlertHandler.ALERT_CHANNEL_ID);
            builder
                    .setSmallIcon(R.mipmap.add_foreground)
                    .setContentTitle(getString(R.string.alert_notification_title, senderName))
                    .setContentText(message)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setContentIntent(pendingIntent).setAutoCancel(true);
        }

        int notificationID = (int) (System.currentTimeMillis() % 1000000000L) + 1;
        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(this);
        notificationManagerCompat.notify(notificationID, builder.build());

        return notificationID;
    }

   /* private void sendRegistrationToServer(String token) {
        User user = new User(getSharedPreferences(MainActivity.USER_INFO, Context.MODE_PRIVATE).getString("id", null), token);
        if (user.getUid() == null) return;
        PendingIntent pendingIntent = MainActivity.createPendingResult(AppServer.ACTION_POST_TOKEN);

        *//*FirebaseMessaging fm = FirebaseMessaging.getInstance();
        fm.send(new RemoteMessage.Builder(SENDER_ID + "@fcm.googleapis.com")
                .setMessageId(messageId)
                .addData("action", "update_id")
                .addData("id", SENDER_ID)
                .addData("token", token)
                .build());*//*
        Log.d(TAG, getString(R.string.log_register_user));
    }*/



    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.alert_channel_name);
            String description = getString(R.string.alert_channel_description);
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(ALERT_CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void createMissedNotificationChannel() {
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
