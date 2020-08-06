package com.aracroproducts.attention;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.Layout;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Objects;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class MainActivity extends AppCompatActivity {

    public static final String USER_INFO = "user";
    public static final String FRIENDS = "listen";

    public static final String MY_ID = "id";
    public static final String MY_NAME = "name";
    public static final String MY_TOKEN = "token";
    public static final String UPLOADED = "uploaded";
    public static final String FRIEND_LIST = "friends";
    public static final String OVERLAY_NO_PROMPT = "OverlayDoNotAsk";

    public static final int NAME_CALLBACK = 0;
    public static final int EDIT_NAME_CALLBACK = 1;

    private final String TAG = getClass().getName();

    private String token;
    private User user;

    private BroadcastReceiver networkCallback = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Received callback from network class");
            SharedPreferences.Editor editor = getSharedPreferences(USER_INFO, Context.MODE_PRIVATE).edit();
            int resultCode = intent.getIntExtra(AppServer.EXTRA_RESULT_CODE, AppServer.CODE_NA);

            switch (Objects.requireNonNull(intent.getAction())) {
                case AppServer.ACTION_POST_TOKEN:
                    if (resultCode == AppServer.CODE_SUCCESS) {
                        editor.putBoolean(UPLOADED, true);
                        editor.putString(MY_TOKEN, token);
                        Toast.makeText(MainActivity.this, getString(R.string.user_registered), Toast.LENGTH_SHORT).show();
                    } else {
                        editor.putBoolean(UPLOADED, false);
                    }
                    break;
                case AppServer.ACTION_SEND_ALERT:
                    if (resultCode == AppServer.CODE_SUCCESS) {
                        View layout = findViewById(R.id.coordinatorLayout);
                        Snackbar snackbar = Snackbar.make(layout, R.string.alert_sent, Snackbar.LENGTH_SHORT);
                        snackbar.show();
                    } else {
                        Toast.makeText(MainActivity.this, getString(R.string.general_error), Toast.LENGTH_LONG).show();
                    }
            }
            editor.apply();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) != ConnectionResult.SUCCESS) {
            Toast.makeText(this, getString(R.string.no_play_services), Toast.LENGTH_LONG).show();
            GoogleApiAvailability.getInstance().makeGooglePlayServicesAvailable(this);
            return;
        }

        SharedPreferences prefs = getSharedPreferences(USER_INFO, Context.MODE_PRIVATE);

        if (!prefs.contains(UPLOADED)) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(UPLOADED, false);
            editor.apply();
        }

        token = prefs.getString(MY_TOKEN, null);

        if (!prefs.contains(MY_NAME) || !prefs.contains(MY_ID)) {
            user = new User();
            Intent intent = new Intent(this, DialogActivity.class);
            startActivityForResult(intent, NAME_CALLBACK);
        } else {
            user = new User(prefs.getString(MY_ID, null), token);
        }

        if (user.getUid() != null && user.getToken() != null && !prefs.getBoolean(UPLOADED, false)) {
            updateToken(user);
        } else {
            getToken();
        }

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(this.getClass().getName(), "Attempting to add");
                Intent intent = new Intent(view.getContext(), Add.class);
                startActivity(intent);
            }
        });

        if (!Settings.canDrawOverlays(this) && !prefs.contains(OVERLAY_NO_PROMPT)) {
            androidx.appcompat.app.AlertDialog alertDialog = new androidx.appcompat.app.AlertDialog.Builder(this).create();
            alertDialog.setTitle(getString(R.string.draw_title));
            alertDialog.setMessage(getString(R.string.draw_message));
            alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.open_settings), (dialogInterface, i) -> {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getApplicationContext().getPackageName()));
                startActivity(intent);
            });
            alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.do_not_ask_again), (dialogInterface, i) -> {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean(OVERLAY_NO_PROMPT, true);
                editor.apply();
            });
            alertDialog.show();
        }


    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case NAME_CALLBACK:
                SharedPreferences prefs = getSharedPreferences(USER_INFO, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(MY_NAME, data.getStringExtra(MY_NAME));
                editor.putString(MY_ID, makeId(data.getStringExtra(MY_NAME)));
                editor.apply();
                user.setUid(prefs.getString(MY_ID, null));
                addUserToDB(user);
                break;
            case EDIT_NAME_CALLBACK:
                Log.d(TAG, "Received edit name callback");
                SharedPreferences friends = getSharedPreferences(FRIENDS, Context.MODE_PRIVATE);
                ArrayList<String[]> friendList = parseFriends(friends.getString(FRIEND_LIST, null));

                if (friendList == null) {
                    Log.w(TAG, "FriendList was null, unable to edit");
                    break;
                }

                String friendId = data.getStringExtra(DialogActivity.EXTRA_USER_ID);

                String[] friend = null;
                int friendIndex = -1;

                for (int i = 0; i < friendList.size(); i++) {
                    if (friendList.get(i)[1].equals(friendId)) {
                        friend = friendList.get(i);
                        friendIndex = i;
                        break;
                    }
                }

                if (friend == null) {
                    Log.e(TAG, "Could not find requested ID to rename");
                    break;
                }

                friend[0] = data.getStringExtra(MY_NAME);

                friendList.set(friendIndex, friend);

                Gson gson = new Gson();
                String friendJson = gson.toJson(friendList);

                SharedPreferences.Editor friendEditor = friends.edit();
                friendEditor.putString(FRIEND_LIST, friendJson);
                friendEditor.apply();
                populateFriendList();

        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private String makeId(String name) {
        String fullString = name + Build.FINGERPRINT;
        byte[] salt = {69, 42, 0, 37, 10, 127, 34, 85, 83, 24, 98, 75, 49, 8, 67}; // very secure salt but this isn't a cryptographic application so it doesn't really matter

        try {
            SecretKeyFactory secretKeyFactory;
            secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512"); //not available to Android 7.1 and lower
            PBEKeySpec spec = new PBEKeySpec(fullString.toCharArray(), salt, 32, 64);
            SecretKey key = secretKeyFactory.generateSecret(spec);
            byte[] hashed = key.getEncoded();
            StringBuilder builder = new StringBuilder();
            for (byte letter : hashed) {
                builder.append(letter);
            }
            return builder.toString();
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
    }

    private void getToken() {
        FirebaseInstanceId.getInstance().getInstanceId().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.w(TAG, "getInstanceId failed", task.getException());
                return;
            }

            // Get new Instance ID token
            token = task.getResult().getToken();
            Log.d(TAG, "Got token! " + token);
            user.setToken(token);

            SharedPreferences preferences = getSharedPreferences(USER_INFO, Context.MODE_PRIVATE);
            if (!token.equals(preferences.getString(MY_TOKEN, ""))) {
                if (user.getUid() == null && preferences.getBoolean(UPLOADED, false)) {
                    preferences.edit().putBoolean(UPLOADED, false).apply();
                } else {
                    updateToken(user);
                }
            }

            // Log and toast
            String msg = getString(R.string.msg_token_fmt, token);
            Log.d(TAG, msg);
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) != ConnectionResult.SUCCESS) {
            Toast.makeText(this, getString(R.string.no_play_services), Toast.LENGTH_LONG).show();
            GoogleApiAvailability.getInstance().makeGooglePlayServicesAvailable(this);
            return;
        }

        populateFriendList();

        IntentFilter filter = new IntentFilter(AppServer.ACTION_SEND_ALERT);
        filter.addAction(AppServer.ACTION_POST_TOKEN);
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        manager.registerReceiver(networkCallback, filter);

    }

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        manager.unregisterReceiver(networkCallback);
    }

    private void populateFriendList() {
        SharedPreferences friends = getSharedPreferences(FRIENDS, Context.MODE_PRIVATE);
        String friendJson = friends.getString(FRIEND_LIST, null);

        ArrayList<String[]> friendList = parseFriends(friendJson);

        RecyclerView friendListView = findViewById(R.id.friends_list);
        friendListView.setLayoutManager(new LinearLayoutManager(this));

        String[][] dataset = new String[friendList.size()][2];
        for (int x = 0; x < friendList.size(); x++) {
            dataset[x][0] = friendList.get(x)[0];
            dataset[x][1] = friendList.get(x)[1];
        }

        FriendAdapter adapter = new FriendAdapter(dataset, null);

        FriendAdapter.Callback adapterListener = new FriendAdapter.Callback() {
            @Override
            public void onSendAlert(String id, String message) {
                sendAlertToServer(id, message);
            }

            @Override
            public void onDeletePrompt(int position, String name) {



                AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                alertDialog.setTitle(getString(R.string.confirm_delete_title));
                alertDialog.setMessage(getString(R.string.confirm_delete_message, name));
                alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.yes), (dialogInterface, i) -> {
                    deleteFriend(position);
                    dialogInterface.cancel();
                });
                alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(android.R.string.cancel), (dialogInterface, i) -> dialogInterface.cancel());
                alertDialog.show();
            }

            @Override
            public void onEditName(String id) {
                Intent intent = new Intent(MainActivity.this, DialogActivity.class);
                intent.putExtra(DialogActivity.EXTRA_EDIT_NAME, true);
                intent.putExtra(DialogActivity.EXTRA_USER_ID, id);
                startActivityForResult(intent, EDIT_NAME_CALLBACK);
            }

            @Override
            public void onLongPress() {
                Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.EFFECT_HEAVY_CLICK));
            }

        };

        adapter.setCallback(adapterListener);

        friendListView.setAdapter(adapter);
    }

    public static ArrayList<String[]> parseFriends(String json) {
        if (json == null) return null;
        Gson gson = new Gson();

            Type arrayListType = new TypeToken<ArrayList<String[]>>() {
            }.getType();
            return gson.fromJson(json, arrayListType);


    }

    private void deleteFriend(int index) {
        SharedPreferences friends = getSharedPreferences(FRIENDS, Context.MODE_PRIVATE);
        String friendJson = friends.getString(FRIEND_LIST, null);
        ArrayList<String[]> friendList;


        if (friendJson == null) {
            Log.w(TAG, "Friend list was null, unable to delete friend");
            return;
        }

        Gson gson = new Gson();
        friendList = parseFriends(friendJson);

        friendList.remove(index);
        Log.d(TAG, "Removed friend");
        SharedPreferences.Editor editor = friends.edit();
        editor.putString(FRIEND_LIST, gson.toJson(friendList));
        editor.apply();
        populateFriendList();

    }

    private void sendAlertToServer(String id, String message) {
        Log.d(TAG, "Sending alert to server via AppServer service");
        //PendingIntent pendingIntent = createPendingResult(AppServer.CALLBACK_POST_TOKEN, new Intent(), 0);
        Intent intent = new Intent(MainActivity.this, AppServer.class);


        intent.putExtra(AppServer.EXTRA_TO, id);
        intent.putExtra(AppServer.EXTRA_FROM, user.getUid());
        if (message != null) {
            intent.putExtra(AppServer.EXTRA_MESSAGE, message);
        }
        intent.setAction(AppServer.ACTION_SEND_ALERT);
       // intent.putExtra(AppServer.EXTRA_PENDING_RESULT, pendingIntent);
        AppServer.enqueueWork(this, intent);


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void addUserToDB(User user) {

        Task<InstanceIdResult> idResultTask = FirebaseInstanceId.getInstance().getInstanceId();
        idResultTask.addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
            @Override
            public void onComplete(@NonNull Task<InstanceIdResult> task) {
                user.setToken(task.getResult().getToken());

                //PendingIntent pendingIntent = createPendingResult(AppServer.CALLBACK_POST_TOKEN, new Intent(), 0);
                Intent intent = new Intent(MainActivity.this, AppServer.class);

                intent.putExtra(AppServer.EXTRA_TOKEN, user.getToken());
                intent.putExtra(AppServer.EXTRA_ID, user.getUid());
                intent.setAction(AppServer.ACTION_POST_TOKEN);
                //intent.putExtra(AppServer.EXTRA_PENDING_RESULT, pendingIntent);
                AppServer.enqueueWork(MainActivity.this, intent);
                Log.d(TAG, getString(R.string.log_sending_msg));
            }
        });
    }

    private void updateToken(User user) {
        addUserToDB(user);
        /*DatabaseReference database = FirebaseDatabase.getInstance().getReference();
        if (user.getUid() != null) {
            database.child("users").child(user.getUid()).child("token").setValue(user.getToken());
        }*/
    }

}