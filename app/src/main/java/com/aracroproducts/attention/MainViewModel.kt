package com.aracroproducts.attention

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import javax.inject.Inject

class MainViewModel @Inject constructor(
        private val attentionRepository: AttentionRepository,
        vararg init: Pair<String, Friend> = arrayOf()
) : ViewModel() {
    // private val _friends: MutableLiveData<MutableMap<String, Friend>> = MutableLiveData
    // (HashMap())
    // val friends: LiveData<MutableMap<String, Friend>> = _friends
    val friends = attentionRepository.getFriends().asLiveData()

    fun onAddFriend(friend: Friend) {
        attentionRepository.insert(friend)
    }

    fun onDeleteFriend(friend: Friend) {
        attentionRepository.delete(friend)
    }

    fun onEditName(id: String, name: String) {
        attentionRepository.edit(Friend(id = id, name = name))
    }

    fun isFromFriend(message: Message): Boolean {
        return attentionRepository.isFromFriend(message = message)
    }

}