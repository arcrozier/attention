package com.aracroproducts.attention

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.DialogInterface
import android.os.CountDownTimer
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.AppCompatImageButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView

/*
class FriendAdapter(private val dataset: List<Pair<String, Friend>>, private var callback:
Callback) {


    /*
     * A single element of the list view

    inner class FriendItem(v: View) : RecyclerView.ViewHolder(v), View.OnClickListener,
                                      OnLongClickListener {
        val textView: TextView = v.findViewById(R.id.friend_name)
        private val confirmButton: Button = v.findViewById(R.id.confirm_button)
        private val cancelButton: FrameLayout = v.findViewById(R.id.cancel_button)

        // ID of the friend in this adapter
        private var id: String? = null
        private val progressBar: ProgressBar = v.findViewById(R.id.progress_bar)
        private val confirmButtonLayout: ConstraintLayout = v.findViewById(R.id.confirmLayout)
        private val addMessage: Button = v.findViewById(R.id.add_message)
        private val cancelSend: AppCompatImageButton = v.findViewById(R.id.cancel_send)
        private val editLayout: ConstraintLayout = v.findViewById(R.id.edit_friend_layout)
        private val rename: Button = v.findViewById(R.id.rename_button)
        private val delete: Button = v.findViewById(R.id.delete_friend_button)
        private val cancelEdit: ImageButton = v.findViewById(R.id.cancel_edit)
        private var delay: CountDownTimer? = null


        private var alertState = State.NORMAL
        fun setId(id: String?) {
            this.id = id
        }

        /**
         * Handles all click events
         */
        override fun onClick(v: View) {
            // when the normal element (background) gets tapped
            if (v.id == textView.id) {
                when (alertState) {
                    State.NORMAL -> prompt()
                    State.EDIT, State.CONFIRM -> cancel()
                    else -> {
                    }
                }
            } else if (v.id == confirmButton.id) {  // when the user taps confirm
                alert(3500, null)
            } else if (v.id == cancelButton.id || v.id == cancelSend.id || v.id == cancelEdit.id) {
                // when the user taps any cancel button
                cancel()
            } else if (v.id == addMessage.id) {  // the user taps "add message"
                val builder = AlertDialog.Builder(textView.context)
                builder.setTitle(textView.context.getString(R.string.add_message))
                val input = EditText(textView.context)
                input.setHint(R.string.message_hint)
                input.inputType =
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE or
                                InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or
                                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                                InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE
                builder.setView(input)
                builder.setPositiveButton(
                        android.R.string.ok) { _: DialogInterface?, _: Int ->
                    alert(0, input.text.toString())
                }
                builder.setNegativeButton(
                        android.R.string.cancel) { _: DialogInterface?, _: Int -> cancel() }
                builder.show()
            } else if (v.id == rename.id) {
                id?.let { callback.onEditName(it) }
            } else if (v.id == delete.id) {
                callback.onDeletePrompt(id!!, textView.text.toString())
            }
        }



        init {
            textView.setOnClickListener(this)
            textView.setOnLongClickListener(this)
            confirmButton.setOnClickListener(this)
            cancelButton.setOnClickListener(this)
            cancelSend.setOnClickListener(this)
            addMessage.setOnClickListener(this)
            rename.setOnClickListener(this)
            delete.setOnClickListener(this)
            cancelEdit.setOnClickListener(this)
        }
    }

    /**
     * An interface for callbacks from actions the user does
     */
    interface Callback {
        /**
         * Sends an alert to the provided ID, with an optional message
         *
         * @param id    - The recipient ID
         * @param message   - The message to include (optional)
         */
        fun onSendAlert(id: String, message: String?)

        /**
         * Called when the user presses "delete" on this friend. Should display a confirmation dialog
         *
         * @param id    - The ID of the friend to delete
         * @param name  - The name of the friend to delete
         */
        fun onDeletePrompt(id: String, name: String)

        /**
         * Called when a user wants to edit the name of one of their friends
         *
         * @param id    - The ID of the friend to edit
         */
        fun onEditName(id: String)

        /**
         * Called when the user long presses on the adapter
         */
        fun onLongPress()
    }

    /**
     * Creates FriendItems
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendItem {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_layout, parent, false)
        return FriendItem(v)
    }

    /**
     * Initializes the FriendItem to the specified position
     *
     * @param holder    - The FriendItem to initialize
     * @param position  - The position of the FriendItem in the list - dictates the data stored in it
     */
    override fun onBindViewHolder(holder: FriendItem, position: Int) {
        holder.textView.text = dataset[position].second.name
        holder.setId(dataset[position].first)
    }

    /**
     * @return  The number of friends that need to be displayed
     */
    override fun getItemCount(): Int {
        return dataset.size
    }

    companion object {
        private val TAG = FriendAdapter::class.java.name
    }

    /**
     * Transitions the item into the edit state - sets alertState to EDIT
     *
     * Calls edit()
     */
    fun onLongClick(v: View): Boolean {
        callback.onLongPress()
        return true
    }

    /**
     * Sends an alert after a time delay, during which the user can cancel the alert
     *
     * @param undoTime  - The amount of time it takes for the progress bar to complete
     * @param message   - The message to send with the alert
     */
    private fun alert(undoTime: Int, message: String?) {
        reset()
        progressBar.progress = 0
        cancelButton.visibility = View.VISIBLE
        alertState = State.CANCEL

        delay = object : CountDownTimer(undoTime.toLong(), 3500) {
            override fun onTick(l: Long) {}
            override fun onFinish() {
                id?.let { sendAlert(it, message) }
                this@FriendItem.cancel()
            }
        }
        (delay as CountDownTimer).start()

        val objectAnimator =
                ObjectAnimator.ofInt(progressBar, "progress", progressBar.progress, 100)
                        .setDuration(3000)
        objectAnimator.addUpdateListener { valueAnimator: ValueAnimator ->
            val progress = valueAnimator.animatedValue as Int
            progressBar.progress = progress
        }
        objectAnimator.start()
    }

    /**
     * Resets display of the view adapter by setting all the other layouts' visibilities to gone
     *
     * Does not modify the state
     */
    private fun reset() {
        confirmButtonLayout.visibility = View.GONE
        cancelButton.visibility = View.GONE
        editLayout.visibility = View.GONE
        textView.alpha = 1.0f
    }

    /**
     * Cancels the alert
     */
    fun cancel() {
        Log.d(TAG, "Cancelled alert")
        reset()
        alertState = State.NORMAL
        if (delay != null) delay!!.cancel()
    }

    /**
     * Sends an alert
     *
     * @param id    - The ID to send to
     * @param message   - The message to include
     */
    private fun sendAlert(id: String, message: String?) {
        callback.onSendAlert(id, message)
    }
     */
} */
