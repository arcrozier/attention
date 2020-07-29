package com.aracroproducts.attention;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.firebase.messaging.SendException;

public class AlertHandler extends FirebaseMessagingService {

    private static final String TAG = AlertHandler.class.getName();

    private Task<InstanceIdResult> idResultTask;

    /*@Override
    public void onNewToken(String token) {
        Log.d(TAG, "New token: " + token);

        sendRegistrationToServer(token);
    }*/

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "Message received! " + remoteMessage.toString());
        //First: check if the sender is on the list in the shared preferences
        //todo here is where the phone should vibrated, woken up, and pop-up dialog displayed (provided message is correct)
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

    @Override
    public void onMessageSent(String messageId) {
        Log.d(TAG, getString(R.string.log_msg_sent, messageId));
        Toast.makeText(this, getString(R.string.alert_sent), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onSendError(String messageId, Exception exception) {
        String exceptionId = exception.getMessage();
        if (exception instanceof SendException) {
            SendException sendException = (SendException) exception;
            switch (sendException.getErrorCode()) {
                case SendException.ERROR_TOO_MANY_MESSAGES:
                    exceptionId = "Message dropped due to too many pending messages";
                    Toast.makeText(this, getString(R.string.too_many_messages), Toast.LENGTH_SHORT).show();
                    break;
                case SendException.ERROR_INVALID_PARAMETERS:
                    exceptionId = "Invalid parameters";
                    break;
                case SendException.ERROR_SIZE:
                    exceptionId = "Message too large";
                    break;
                case SendException.ERROR_TTL_EXCEEDED:
                    exceptionId = "Message timed out";
                    break;
                case SendException.ERROR_UNKNOWN:
                    exceptionId = "Unknown error";
                    break;
            }

            if (sendException.getErrorCode() != SendException.ERROR_TOO_MANY_MESSAGES) {
                Toast.makeText(this, getString(R.string.general_error), Toast.LENGTH_SHORT).show();
            }
        }
        Log.d(TAG, getString(R.string.log_send_error, messageId, exceptionId));
    }

}
