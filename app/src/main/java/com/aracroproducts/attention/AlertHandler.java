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

import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.InstanceIdResult;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Map;

public class AlertHandler extends FirebaseMessagingService {

    private static final String TAG = AlertHandler.class.getName();

    private Task<InstanceIdResult> idResultTask;

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

        SharedPreferences.Editor editor = getSharedPreferences(MainActivity.USER_INFO, Context.MODE_PRIVATE).edit();
        editor.putBoolean(MainActivity.UPLOADED, false);
        editor.apply();
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "Message received! " + remoteMessage.toString());
        Map<String, String> messageData = remoteMessage.getData();
        if (!senderIsFriend(messageData.get(REMOTE_FROM))) return; //checks if the sender is a friend of the user, ends if not

        SharedPreferences userInfo = getSharedPreferences(MainActivity.USER_INFO, Context.MODE_PRIVATE);

        if (!messageData.get(REMOTE_TO).equals(userInfo.getString(MainActivity.MY_ID, ""))) return; //if message is not addressed to the user, ends

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (!manager.areNotificationsEnabled()) return;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        /*if (!pm.isInteractive()) {
            PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, TAG);
            wakeLock.acquire(500);
        }*/
        String senderName = getFriendNameForID(messageData.get(REMOTE_FROM));
        String message = messageData.get(REMOTE_MESSAGE);

        message = message.equals("null") ? getString(R.string.default_message, senderName) : getString(R.string.message_prefix, senderName, message);

        int id = showNotification(message, senderName);

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

    private int showNotification(String message, String senderName) {
        createNotificationChannel();

        Intent intent = new Intent(this, Alert.class);
        intent.putExtra(REMOTE_MESSAGE, message);
        intent.putExtra(REMOTE_FROM, senderName);
        intent.putExtra(SHOULD_VIBRATE, false);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, AlertHandler.CHANNEL_ID);
        builder
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(getString(R.string.alert_notification_title, senderName))
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setContentIntent(pendingIntent).setAutoCancel(true);

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

}
