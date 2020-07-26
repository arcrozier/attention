package com.aracroproducts.attention;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.RemoteMessage;

public class FriendAdapter extends RecyclerView.Adapter<FriendAdapter.FriendItem> {
    private String[][] dataset;
    private static final String TAG = FriendAdapter.class.getName();

    public static class FriendItem extends RecyclerView.ViewHolder implements View.OnClickListener {

        public TextView textView;
        private Button confirmButton;
        private FrameLayout cancelButton;
        private String id;
        private ProgressBar progressBar;

        private CountDownTimer delay;

        private enum State {
            NORMAL, CONFIRM, CANCEL
        }

        private State alertState = State.NORMAL;

        public FriendItem(View v) {
            super(v);

            textView = v.findViewById(R.id.friend_name);
            confirmButton = v.findViewById(R.id.confirm_button);
            cancelButton = v.findViewById(R.id.cancel_button);
            progressBar = v.findViewById(R.id.progress_bar);

            textView.setOnClickListener(this);
            confirmButton.setOnClickListener(this);
            cancelButton.setOnClickListener(this);
        }

        public void setId(String id) {
            this.id = id;
        }

        @Override
        public void onClick(View v) {
            if (v.getId() == textView.getId()) {
                switch (alertState) {
                    case NORMAL:
                        prompt();
                        break;
                    case CONFIRM:
                        cancel();
                        break;
                }
            } else if (v.getId() == confirmButton.getId()) {
                alert();
            } else if (v.getId() == cancelButton.getId()) {
                cancel();

            }
        }

        public void prompt() {
            confirmButton.setVisibility(View.VISIBLE);
            cancelButton.setVisibility(View.GONE);
            alertState = State.CONFIRM;
        }

        public void alert() {
            cancelButton.setVisibility(View.VISIBLE);
            confirmButton.setVisibility(View.GONE);
            alertState = State.CANCEL;
            /*
            Intent intent = new Intent(textView.getContext(), AlertHandler.class);
            intent.putExtra("to", id);
            textView.getContext().startService(intent);
            */

            delay = new CountDownTimer(3500, 3500) {
                @Override
                public void onTick(long l) {

                }

                @Override
                public void onFinish() {
                    sendAlertToServer(id);
                    FriendItem.this.cancel();
                }
            };
            delay.start();

            //final ProgressBar progressBar = textView.findViewById(R.id.progress_bar);
            final ObjectAnimator objectAnimator = ObjectAnimator.ofInt(progressBar, "progress", progressBar.getProgress(), 100).setDuration(3000);
            objectAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    int progress = (int) valueAnimator.getAnimatedValue();
                    progressBar.setProgress(progress);
                }
            });

            objectAnimator.start();
        }

        public void cancel() {
            Log.d(TAG, "Cancelled alert");
            cancelButton.setVisibility(View.GONE);
            confirmButton.setVisibility(View.GONE);
            alertState = State.NORMAL;
            if (delay != null) delay.cancel();
        }

        private boolean sendAlertToServer(final String recipientId) {

            String SENDER_ID = textView.getContext().getSharedPreferences(MainActivity.USER_INFO, Context.MODE_PRIVATE).getString("id", null);

            Task<InstanceIdResult> idResultTask = FirebaseInstanceId.getInstance().getInstanceId();
            idResultTask.addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
                @Override
                public void onComplete(@NonNull Task<InstanceIdResult> task) {
                    String messageId = Long.toString(System.currentTimeMillis());
                    FirebaseMessaging fm = FirebaseMessaging.getInstance();
                    fm.send(new RemoteMessage.Builder(SENDER_ID + "@fcm.googleapis.com")
                            .setMessageId(messageId)
                            .addData("action", "send_alert")
                            .addData("to", recipientId)
                            .addData("from", SENDER_ID)
                            .build());
                    Log.d(TAG, textView.getContext().getString(R.string.log_sending_msg, messageId));
                    //todo send message
                }
            });
            return false;
        }
    }

    public FriendAdapter(String[][] myDataset) {
        dataset = myDataset;
    }

    @Override
    public FriendAdapter.FriendItem onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_layout, parent, false);

       return new FriendItem(v);
    }

    @Override
    public void onBindViewHolder(FriendItem holder, int position) {
        holder.textView.setText(dataset[position][0]);
        holder.setId(dataset[position][1]);
    }

    @Override
    public int getItemCount() {
        return dataset.length;
    }
}
