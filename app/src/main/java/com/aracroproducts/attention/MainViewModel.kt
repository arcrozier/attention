package com.aracroproducts.attention

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson

class MainViewModel(vararg init: Pair<String, Friend> = arrayOf()) : ViewModel() {
    // private val _friends: MutableLiveData<MutableMap<String, Friend>> = MutableLiveData
    // (HashMap())
    // val friends: LiveData<MutableMap<String, Friend>> = _friends
    var friends = mutableStateMapOf(*init)
        private set

    fun onAddFriend(friend: Friend, context: Context? = null) {
        friends[friend.id] = friend
        if (context != null) saveFriendMap(context)
    }

    fun onDeleteFriend(id: String, context: Context? = null) {
        friends.remove(id)
        if (context != null) saveFriendMap(context)
    }

    fun onEditName(id: String, name: String, context: Context? = null) {
        friends[id]?.name = name
        if (context != null) saveFriendMap(context)
    }

    fun onLaunch(context: Context) {
        friends = MainActivity.getFriendMap(context) as SnapshotStateMap<String, Friend>
    }

    /**
     * Writes the friendMap to shared preferences in map encoding
     */
    private fun saveFriendMap(context: Context) {
        val editor = context.getSharedPreferences(MainActivity.FRIENDS, AppCompatActivity
                .MODE_PRIVATE).edit()
        val gson = Gson()
        editor.putString(MainActivity.FRIEND_LIST, gson.toJson(friends))
        editor.apply()
    }

}