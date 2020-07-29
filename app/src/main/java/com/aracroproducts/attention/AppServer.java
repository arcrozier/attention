package com.aracroproducts.attention;

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.Context;
import android.util.Log;

import androidx.core.app.JobIntentService;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class AppServer extends JobIntentService {
    private final String TAG = getClass().getName();

    protected static final String ACTION_POST_TOKEN = "com.aracroproducts.attention.action.token";
    protected static final String ACTION_SEND_ALERT = "com.aracroproducts.attention.action.send";

    protected static final String EXTRA_TOKEN = "com.aracroproducts.attention.extra.token";
    protected static final String EXTRA_ID = "com.aracroproducts.attention.extra.id";
    protected static final String EXTRA_TO = "com.aracroproducts.attention.extra.to";
    protected static final String EXTRA_FROM = "com.aracroproducts.attention.extra.from";
    protected static final String EXTRA_MESSAGE = "com.aracroproducts.attention.extra.message";

    protected static final String EXTRA_PENDING_RESULT = "com.aracroproducts.attention.extra.pending_result";
    protected static final String EXTRA_DATA = "com.aracroproducts.attention.extra.data";

    protected static final int CODE_SUCCESS = 0;
    protected static final int CODE_ERROR = 1;

    protected static final int CALLBACK_POST_TOKEN = 10;
    protected static final int CALLBACK_SEND_ALERT = 11;

    private static final int JOB_ID = 0;

    private static final String PARAM_FUNCTION_ID = "post_id";
    private static final String PARAM_FUNCTION_ALERT = "send_alert";

    private static final String BASE_URL = "https://aracroproducts.com/attention/api/api.php?function=";

    public AppServer() {
        super();
    }

    public static void enqueueWork(Context context, Intent intent) {
        enqueueWork(context, AppServer.class, JOB_ID, intent);
    }

    @Override
    protected void onHandleWork(Intent intent) {
        Log.d(TAG, "Handling work. Is intent null? " + (intent == null));
        if (intent != null) {
            Log.d(TAG, intent.toString() + " Action: " + intent.getAction());
            final String action = intent.getAction();
            //PendingIntent reply = intent.getParcelableExtra(EXTRA_PENDING_RESULT);
            Intent result = new Intent();
            switch (action) {
                case ACTION_POST_TOKEN:
                    String token = intent.getStringExtra(EXTRA_TOKEN);
                    String id = intent.getStringExtra(EXTRA_ID);
                    boolean success = connect(true, new String[][]{{"token", token}, {"id", id}}, PARAM_FUNCTION_ID);
                    if (success) {
                        Log.d(TAG, "Message sent");
                        result.putExtra(EXTRA_DATA, "Sent id successfully");
                        //reply.send(this, CODE_SUCCESS, result);
                    } else {
                        Log.e(TAG, "An error occurred");
                        result.putExtra(EXTRA_DATA, "Error sending ID");
                        //reply.send(this, CODE_ERROR, result);
                    }
                    break;
                case ACTION_SEND_ALERT:
                    String to = intent.getStringExtra(EXTRA_TO);
                    String from = intent.getStringExtra(EXTRA_FROM);
                    String message = intent.getStringExtra(EXTRA_MESSAGE);
                    success = connect(true, new String[][]{{"to", to}, {"from", from}, {"message", message}}, PARAM_FUNCTION_ALERT);
                    if (success) {
                        Log.d(TAG, "Alert sent");
                        result.putExtra(EXTRA_DATA, "Sent alert successfully");
                        //reply.send(this, CODE_SUCCESS, result);
                    } else {
                        Log.e(TAG, "An error occurred");
                        result.putExtra(EXTRA_DATA, "Error sending alert");
                        //reply.send(this, CODE_ERROR, result);
                    }
            }

        }
    }

    /**
     * Sends data via http
     * @param post      - Whether to use POST or GET
     * @param params    - Additional parameters for the server
     * @param function  - Which function it is calling (one of 'post_id' or 'send_alert')
     * @return          - A boolean representing whether the request was successful
     */
    private boolean connect(boolean post, String[][] params, String function) {
        HttpURLConnection c;
        String getParams = parseGetParams(params, !post);
        try {
            URL url;
            if (post) {
                url = new URL(BASE_URL + function);
            } else {
                url = new URL(BASE_URL + function + getParams);
            }
            c = (HttpURLConnection) url.openConnection();
            if (post) {
                c.setRequestMethod("POST");
            } else {
                c.setRequestMethod("GET");
            }
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            c.setRequestProperty("charset", "utf-8");
            c.setRequestProperty("User-Agent", "Attention! client app for Android");
            c.setRequestProperty("Cache-control", "no-cache"); // tell server not to send cached response
            c.setUseCaches(false); // do it twice just to be sure

            if (post) {
                c.setDoOutput(true);
                DataOutputStream os = new DataOutputStream(c.getOutputStream()); // "out" is considered what the app is sending to the server
                os.write(getParams.getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.close();
            }
            int code = c.getResponseCode();
            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8)); // likewise, "in" is what is coming from the server
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();
            Log.d(TAG, url + " " + code + " " + sb.toString());

            return code == HttpURLConnection.HTTP_CREATED || code == HttpURLConnection.HTTP_OK;

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private String parseGetParams(String[][] params, boolean leadingAmp) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < params.length; i++) {
            if (i != 0 || leadingAmp) {
                builder.append('&');
            }
            builder.append(params[i][0]);
            builder.append('=');
            builder.append(params[i][1]);
        }
        return builder.toString();
    }
}
