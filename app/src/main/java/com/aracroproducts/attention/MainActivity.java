package com.aracroproducts.attention;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.View;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.lang.reflect.Type;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.HashSet;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class MainActivity extends AppCompatActivity {

    public static final String USER_INFO = "user";
    public static final String FRIENDS = "listen";

    public static final int NAME_CALLBACK = 0;
    public final int CUSTOMIZED_REQUEST_CODE = 0x0000ffff;

    private final String TAG = getClass().getName();

    private String token;
    private User user;

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

        if (!prefs.contains("uploaded")) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("uploaded", false);
            editor.apply();
        }


        if (!prefs.contains("name") || !prefs.contains("id")) {
            user = new User();
            Intent intent = new Intent(this, DialogActivity.class);
            startActivityForResult(intent, NAME_CALLBACK);
        } else {
            user = new User(prefs.getString("id", null), token);
        }

        if (user.getUid() != null && user.getToken() != null && !prefs.getBoolean("uploaded", false)) {
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


    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case NAME_CALLBACK:
                SharedPreferences prefs = getSharedPreferences(USER_INFO, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("name", data.getStringExtra("name"));
                editor.putString("id", makeId(data.getStringExtra("name")));
                editor.apply();
                user.setUid(prefs.getString("id", null));
                addUserToDB(user);
                break;
            case AppServer.CALLBACK_POST_TOKEN:

                SharedPreferences.Editor sharedPreferences = getSharedPreferences(USER_INFO, Context.MODE_PRIVATE).edit();
                switch (resultCode) {
                    case AppServer.CODE_SUCCESS:
                        sharedPreferences.putBoolean("uploaded", true);
                        Toast.makeText(this, getString(R.string.user_registered), Toast.LENGTH_SHORT).show();
                        break;
                    case AppServer.CODE_ERROR:
                        sharedPreferences.putBoolean("uploaded", false);

                }
                sharedPreferences.apply();
                break;
            case AppServer.CALLBACK_SEND_ALERT:
                switch (resultCode) {
                    case AppServer.CODE_SUCCESS:
                        Toast.makeText(this, getString(R.string.alert_sent), Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Alert sent");
                        break;
                    case AppServer.CODE_ERROR:
                        Log.e(TAG, "Error sending alert");
                        Toast.makeText(this, getString(R.string.general_error), Toast.LENGTH_LONG).show();
                }

            /*case CUSTOMIZED_REQUEST_CODE:
                IntentResult result = IntentIntegrator.parseActivityResult(resultCode, data);
                if(result.getContents() == null) {
                    Intent originalIntent = result.getOriginalIntent();
                    if (originalIntent == null) {
                        Log.d("MainActivity", "Cancelled scan");
                        Toast.makeText(this, "Cancelled", Toast.LENGTH_LONG).show();
                    } else if(originalIntent.hasExtra(Intents.Scan.MISSING_CAMERA_PERMISSION)) {
                        Log.d("MainActivity", "Cancelled scan due to missing camera permission");
                        Toast.makeText(this, "Cancelled due to missing camera permission", Toast.LENGTH_LONG).show();
                    }
                } else {
                    Log.d("MainActivity", "Scanned");
                    String friendTag = result.getContents();
                    String friendName = friendTag.substring(friendTag.indexOf(' ') + 1);
                    String  friendId = friendTag.substring(0, friendTag.indexOf(' ') + 1);
                    SharedPreferences friends = getSharedPreferences(FRIENDS, Context.MODE_PRIVATE);
                    HashSet<String> friendIdSet = new HashSet<>(friends.getStringSet("ids", new HashSet<String>()));
                    HashSet<String> friendNameSet = new HashSet<>(friends.getStringSet("names", new HashSet<String>()));
                    friendIdSet.add(friendId);
                    friendNameSet.add(friendName);
                    SharedPreferences.Editor editor1 = friends.edit();
                    editor1.putStringSet("ids", friendIdSet);
                    editor1.putStringSet("names", friendNameSet);
                    editor1.apply();
                }

             */
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private String makeId(String name) {
        String fullString = name + Build.FINGERPRINT;
        byte[] salt = {69, 42, 0, 37, 10, 127, 34, 85, 83, 24, 98, 75, 49, 8, 67};

        try {
            SecretKeyFactory secretKeyFactory;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512"); //not available to Android 7.1 and lower
            } else {
                secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            }
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
        FirebaseInstanceId.getInstance().getInstanceId().addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
            @Override
            public void onComplete(@NonNull Task<InstanceIdResult> task) {
                if (!task.isSuccessful()) {
                    Log.w(TAG, "getInstanceId failed", task.getException());
                    return;
                }

                // Get new Instance ID token
                token = task.getResult().getToken();
                Log.d(TAG, "Got token! " + token);
                user.setToken(token);

                SharedPreferences preferences = getSharedPreferences(USER_INFO, Context.MODE_PRIVATE);
                if (!token.equals(preferences.getString("token", ""))) {
                    if (user.getUid() == null && preferences.getBoolean("uploaded", false)) {
                        preferences.edit().putBoolean("uploaded", false).apply();
                    } else {
                        updateToken(user);
                    }
                }

                // Log and toast
                String msg = getString(R.string.msg_token_fmt, token);
                Log.d(TAG, msg);
            }
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

        SharedPreferences friends = getSharedPreferences(FRIENDS, Context.MODE_PRIVATE);
        String friendJson = friends.getString("friends", null);
        ArrayList<String[]> friendList = new ArrayList<>();

        Gson gson = new Gson();

        if (friendJson != null) {
            Type arrayListType = new TypeToken<ArrayList<String[]>>() {
            }.getType();
            friendList = gson.fromJson(friendJson, arrayListType);

            Log.d(TAG, friendJson);
        }

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
        };

        adapter.setCallback(adapterListener);

        friendListView.setAdapter(adapter);
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
                //todo send message
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