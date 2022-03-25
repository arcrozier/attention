package com.aracroproducts.attentionv2

import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent
import android.view.View
import android.widget.TextView
import android.widget.Toast

/**
 * An activity that prompts the user to input a name
 */
class DialogActivity : AppCompatActivity() {
    private var friendName = false
    private var friendId: String? = null

    /**
     * Called when activity is created
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.name_dialog)
        setFinishOnTouchOutside(false)
        val intent = intent
        if (intent.hasExtra(EXTRA_EDIT_NAME)) {
            friendName = true
            val namePrompt = findViewById<TextView>(R.id.name_prompt)
            namePrompt.text = getString(R.string.new_name)
            friendId = intent.getStringExtra(EXTRA_USER_ID)
        }
    }

    /**
     * Sets the name (either the user's or the friend's)
     */
    fun setName(view: View) {
        val nameField = findViewById<TextView>(R.id.person_name_field)
        val name = nameField.text
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.no_name, Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent()
        intent.putExtra("name", name.toString())
        if (friendName) {
            intent.putExtra(EXTRA_USER_ID, friendId)
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    /**
     * Prevent back button from doing anything
     */
    override fun onBackPressed() {}

    companion object {
        const val EXTRA_EDIT_NAME = "com.aracroproducts.attention.extra.edit_name"
        const val EXTRA_USER_ID = "com.aracroproducts.attention.extra.user_id"
    }
}