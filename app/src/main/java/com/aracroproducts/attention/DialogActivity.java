package com.aracroproducts.attention;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class DialogActivity extends AppCompatActivity {

    public static final int SUCCESS_CODE = 1;
    public static final String EXTRA_EDIT_NAME = "com.aracroproducts.attention.extra.edit_name";
    public static final String EXTRA_USER_ID = "com.aracroproducts.attention.extra.user_id";

    private boolean friend_name = false;
    private String friend_id;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.name_dialog);
        this.setFinishOnTouchOutside(false);

        Intent intent = getIntent();
        if (intent.hasExtra(EXTRA_EDIT_NAME)) {
            friend_name = true;
            TextView namePrompt = findViewById(R.id.name_prompt);
            namePrompt.setText(getString(R.string.new_name));
            friend_id = intent.getStringExtra(EXTRA_USER_ID);
        }

    }

    public void setName(View view) {
        TextView nameField = findViewById(R.id.person_name_field);
        CharSequence name = nameField.getText();
        if (name.length() <= 0) {
            Toast.makeText(this, R.string.no_name, Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent();
        intent.putExtra("name", name.toString());
        if (friend_name) {
            intent.putExtra(EXTRA_USER_ID, friend_id);
        }

        setResult(SUCCESS_CODE, intent);
        finish();
    }

    @Override
    public void onBackPressed() {

    }
}
