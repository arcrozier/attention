package com.aracroproducts.attention;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.CountDownTimer;
import android.os.Vibrator;
import android.telecom.Call;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.constraintlayout.widget.ConstraintLayout;
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
    private Callback callback;

    public static class FriendItem extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

        public TextView textView;
        private Button confirmButton;
        private FrameLayout cancelButton;
        private String id;
        private ProgressBar progressBar;
        private ConstraintLayout confirmButtonLayout;
        private Button addMessage;
        private AppCompatImageButton cancelSend;
        private ConstraintLayout editLayout;
        private Button rename;
        private Button delete;

        private int position;

        protected Callback callback;

        private CountDownTimer delay;

        private enum State {
            NORMAL, CONFIRM, CANCEL, EDIT
        }

        private State alertState = State.NORMAL;

        public FriendItem(View v) {
            super(v);

            textView = v.findViewById(R.id.friend_name);
            confirmButton = v.findViewById(R.id.confirm_button);
            confirmButtonLayout = v.findViewById(R.id.confirmLayout);
            cancelButton = v.findViewById(R.id.cancel_button);
            progressBar = v.findViewById(R.id.progress_bar);
            addMessage = v.findViewById(R.id.add_message);
            cancelSend = v.findViewById(R.id.cancel_send);
            editLayout = v.findViewById(R.id.edit_friend_layout);
            rename = v.findViewById(R.id.rename_button);
            delete = v.findViewById(R.id.delete_friend_button);



            textView.setOnClickListener(this);
            textView.setOnLongClickListener(this);
            confirmButton.setOnClickListener(this);
            cancelButton.setOnClickListener(this);
            cancelSend.setOnClickListener(this);
            addMessage.setOnClickListener(this);
            rename.setOnClickListener(this);
            delete.setOnClickListener(this);

        }

        public void setId(String id) {
            this.id = id;
        }

        public void setPosition(int position) {
            this.position = position;
        }
        @Override
        public void onClick(View v) {
            if (v.getId() == textView.getId()) {
                switch (alertState) {
                    case NORMAL:
                        prompt();
                        break;
                    case EDIT:
                    case CONFIRM:
                        cancel();
                        break;
                }
            } else if (v.getId() == confirmButton.getId()) {
                alert(3500, null);
            } else if (v.getId() == cancelButton.getId() || v.getId() == cancelSend.getId()) {
                cancel();

            } else if (v.getId() == addMessage.getId()) {
                AlertDialog.Builder builder = new AlertDialog.Builder(textView.getContext());
                builder.setTitle(textView.getContext().getString(R.string.add_message));

                final EditText input = new EditText(textView.getContext());
                input.setHint(R.string.message_hint);

                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                builder.setView(input);

                builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        alert(0, input.getText().toString());
                    }
                });
                builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        FriendItem.this.cancel();
                    }
                });
                builder.show();
            } else if (v.getId() == rename.getId()) {
                callback.onEditName(id);
            } else if (v.getId() == delete.getId()) {
                callback.onDeletePrompt(position, textView.getText().toString());
            }
        }

        @Override
        public boolean onLongClick(View v) {
            callback.onLongPress();
            edit();
            alertState = State.EDIT;
            return false;
        }

        public void edit() {
            reset();
            textView.setAlpha(0.25f);
            editLayout.setVisibility(View.VISIBLE);
        }


        public void prompt() {
            reset();
            confirmButtonLayout.setVisibility(View.VISIBLE);
            textView.setAlpha(0.25f);
            alertState = State.CONFIRM;
        }

        public void alert(final int undoTime, String message) {
            reset();
            progressBar.setProgress(0);
            cancelButton.setVisibility(View.VISIBLE);
            alertState = State.CANCEL;
            /*
            Intent intent = new Intent(textView.getContext(), AlertHandler.class);
            intent.putExtra("to", id);
            textView.getContext().startService(intent);
            */

            delay = new CountDownTimer(undoTime, 3500) {
                @Override
                public void onTick(long l) {

                }

                @Override
                public void onFinish() {
                    sendAlert(id, message);
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

        public void reset() {
            confirmButtonLayout.setVisibility(View.GONE);
            cancelButton.setVisibility(View.GONE);
            editLayout.setVisibility(View.GONE);
            textView.setAlpha(1.0f);

        }

        public void cancel() {
            Log.d(TAG, "Cancelled alert");
            cancelButton.setVisibility(View.GONE);
            confirmButtonLayout.setVisibility(View.GONE);
            alertState = State.NORMAL;
            if (delay != null) delay.cancel();
        }

        private void sendAlert(String id, String message) {
            if (callback != null) {
                callback.onSendAlert(id, message);
            } else {
                Log.e(TAG, "Callback was null!");
            }
        }
/*
        private boolean sendAlertToServer(final String recipientId, String message) {

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
                    Log.d(TAG, textView.getContext().getString(R.string.log_sending_msg));
                }
            });
            return false;
        }*/

    }

    public FriendAdapter(String[][] myDataset, Callback callback) {
        super();
        dataset = myDataset;
        this.callback = callback;
    }

    public interface Callback {
        void onSendAlert(String id, String message);
        void onDeletePrompt(int position, String name);
        void onEditName(String id);
        void onLongPress();
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
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
        holder.setPosition(position);
        holder.callback = callback;
    }

    @Override
    public int getItemCount() {
        return dataset.length;
    }
}
