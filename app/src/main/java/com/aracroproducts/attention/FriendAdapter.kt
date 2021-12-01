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
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.aracroproducts.attention.FriendAdapter
import com.aracroproducts.attention.FriendAdapter.FriendItem

class FriendAdapter(private val dataset: Array<Array<String>>, private var callback: Callback) :
        RecyclerView.Adapter<FriendItem>() {

    private enum class State {
        NORMAL, CONFIRM, CANCEL, EDIT
    }

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

        override fun onClick(v: View) {
            if (v.id == textView.id) {
                when (alertState) {
                    State.NORMAL -> prompt()
                    State.EDIT, State.CONFIRM -> cancel()
                    else -> {}
                }
            } else if (v.id == confirmButton.id) {
                alert(3500, null)
            } else if (v.id == cancelButton.id || v.id == cancelSend.id || v.id == cancelEdit.id) {
                cancel()
            } else if (v.id == addMessage.id) {
                val builder = AlertDialog.Builder(textView.context)
                builder.setTitle(textView.context.getString(R.string.add_message))
                val input = EditText(textView.context)
                input.setHint(R.string.message_hint)
                input.inputType =
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
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

        override fun onLongClick(v: View): Boolean {
            callback.onLongPress()
            edit()
            alertState = State.EDIT
            return true
        }

        private fun edit() {
            reset()
            textView.alpha = 0.25f
            editLayout.visibility = View.VISIBLE
        }

        private fun prompt() {
            reset()
            confirmButtonLayout.visibility = View.VISIBLE
            textView.alpha = 0.25f
            alertState = State.CONFIRM
        }

        private fun alert(undoTime: Int, message: String?) {
            reset()
            progressBar.progress = 0
            cancelButton.visibility = View.VISIBLE
            alertState = State.CANCEL
            /*
            Intent intent = new Intent(textView.getContext(), AlertHandler.class);
            intent.putExtra("to", id);
            textView.getContext().startService(intent);
            */delay = object : CountDownTimer(undoTime.toLong(), 3500) {
                override fun onTick(l: Long) {}
                override fun onFinish() {
                    id?.let { sendAlert(it, message) }
                    this@FriendItem.cancel()
                }
            }
            (delay as CountDownTimer).start()

            //final ProgressBar progressBar = textView.findViewById(R.id.progress_bar);
            val objectAnimator =
                    ObjectAnimator.ofInt(progressBar, "progress", progressBar.progress, 100)
                            .setDuration(3000)
            objectAnimator.addUpdateListener { valueAnimator: ValueAnimator ->
                val progress = valueAnimator.animatedValue as Int
                progressBar.progress = progress
            }
            objectAnimator.start()
        }

        private fun reset() {
            confirmButtonLayout.visibility = View.GONE
            cancelButton.visibility = View.GONE
            editLayout.visibility = View.GONE
            textView.alpha = 1.0f
        }

        fun cancel() {
            Log.d(TAG, "Cancelled alert")
            reset()
            alertState = State.NORMAL
            if (delay != null) delay!!.cancel()
        }

        private fun sendAlert(id: String, message: String?) {
            callback.onSendAlert(id, message)
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

    interface Callback {
        fun onSendAlert(id: String, message: String?)
        fun onDeletePrompt(id: String, name: String)
        fun onEditName(id: String)
        fun onLongPress()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendItem {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_layout, parent, false)
        return FriendItem(v)
    }

    override fun onBindViewHolder(holder: FriendItem, position: Int) {
        holder.textView.text = dataset[position][0]
        holder.setId(dataset[position][1])
    }

    override fun getItemCount(): Int {
        return dataset.size
    }

    companion object {
        private val TAG = FriendAdapter::class.java.name
    }
}