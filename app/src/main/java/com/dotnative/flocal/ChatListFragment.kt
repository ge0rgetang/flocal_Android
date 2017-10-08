@file:Suppress("UNCHECKED_CAST", "MemberVisibilityCanPrivate", "CascadeIf", "LiftReturnOrAssignment", "UnnecessaryVariable", "SetTextI18n")

package com.dotnative.flocal

import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.database.*

class ChatListFragment : Fragment() {

    // MARK: - Layout

    lateinit var layoutManager: LinearLayoutManager
    lateinit var recyclerView: RecyclerView
    lateinit var adapter: ChatListAdapter

    // MARK: - Vars

    private var chatListInteractionListener: ChatListInteractionListener? = null

    var myID: String = "0"
    var chatList: MutableList<Chat> = mutableListOf()
    var chatListValueListener: ValueEventListener? = null

    var scrollPosition: String = "top"
    var isRemoved: Boolean = false

    var ref: DatabaseReference = FirebaseDatabase.getInstance().reference
    val analytics: FirebaseAnalytics = FirebaseAnalytics.getInstance(context)

    val misc = Misc()
    var displayProgress: Boolean = false

    // MARK: - Lifecycle

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context is ChatListInteractionListener) {
            chatListInteractionListener = context
        } else {
            throw RuntimeException(context!!.toString() + " must implement OnFragmentInteractionListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view =  inflater!!.inflate(R.layout.fragment_chat_list, container, false)
        setLayout(view)
        return view
    }

    override fun onStart() {
        super.onStart()

        myID = misc.setMyID(context)
        if (myID == "0") {
            chatListInteractionListener?.turnToFragmentFromChatList("Login")
        } else {
            logViewChatList()
            misc.setSideMenuIndex(context, 2)
            observeChatList()
        }
    }

    override fun onStop() {
        super.onStop()
        removeObserverForChatList()
    }

    override fun onDetach() {
        super.onDetach()
        chatListInteractionListener = null
        removeObserverForChatList()
    }

    // MARK: - Navigation

    interface ChatListInteractionListener {
        fun turnToFragmentFromChatList(name: String)
    }

    fun setLayout(view: View) {
        layoutManager = LinearLayoutManager(context)
        recyclerView = view.findViewById(R.id.chatListRecyclerView)
        adapter = ChatListAdapter(context, chatList, displayProgress)
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)
        val divider = DividerItemDecoration(recyclerView.context, 1)
        recyclerView.addItemDecoration(divider)
        recyclerView.addOnScrollListener(scrollListener)
    }

    // MARK: - Scroll

    val scrollListener = object: RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)

            if (!isRemoved) {
                removeObserverForChatList()
            }

            val firstVisibleItem = layoutManager.findFirstCompletelyVisibleItemPosition()
            val lastVisibleItem = layoutManager.findLastCompletelyVisibleItemPosition()
            if (firstVisibleItem <= 0) {
                scrollPosition = "top"
                observeChatList()
            } else if (lastVisibleItem == chatList.size - 1) {
                scrollPosition = "bottom"
                if (chatList.size >= 8) {
                    displayProgress = true
                    refreshRecycler()
                    recyclerView?.scrollToPosition(chatList.size)
                    observeChatList()
                }
            } else {
                scrollPosition = "middle"
            }
        }
    }

    fun scrollToTop() {
        recyclerView.scrollToPosition(0)
        scrollPosition = "top"
    }

    // MARK: - Misc

    fun refreshRecycler() {
        adapter = ChatListAdapter(context, chatList, displayProgress)
        recyclerView.adapter = adapter
        recyclerView.adapter.notifyDataSetChanged()
    }

    // MARK: - Analytics

    fun logViewChatList() {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        analytics.logEvent("viewChatList_Android", bundle)
    }

    // MARK: - Firebase

    fun observeChatList() {
        removeObserverForChatList()
        isRemoved = false

        val chatListRef = ref.child("userChatList").child(myID)

        if (scrollPosition == "middle" && !chatList.isEmpty()) {
            val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
            val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
            for (index in firstVisiblePosition..lastVisiblePosition) {
                val chatID = chatList[index].chatID
                val idRef = chatListRef.child(chatID)
                idRef.addListenerForSingleValueEvent( object: ValueEventListener {
                    override fun onDataChange(snap: DataSnapshot?) {
                        val chat = snap?.value as? MutableMap<String,Any> ?: mutableMapOf()
                        if (!chat.isEmpty()) {
                            val formattedChat= formatChatList(chatID, chat)
                            chatList[index] = formattedChat
                        }
                    }
                    override fun onCancelled(error: DatabaseError?) { Log.d("databaseError", error.toString()) }
                })
            }
            displayProgress = false
            refreshRecycler()

        } else {
            val reverseTimestamp: Double
            val currentReverseTimestamp = misc.getCurrentReverseTimestamp()
            val lastReverseTimestamp = chatList.lastOrNull()?.originalReverseTimestamp
            val lastChatID = chatList.lastOrNull()?.chatID

            if (scrollPosition == "bottom") {
                reverseTimestamp = lastReverseTimestamp ?: currentReverseTimestamp
            } else {
                reverseTimestamp = currentReverseTimestamp
            }

            chatListValueListener = object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot?) {
                    val chats: MutableList<Chat> = mutableListOf()

                    val dict = snap?.value as? MutableMap<String, Any> ?: mutableMapOf()
                    for ((chatID, value) in dict) {
                        val chat = value as? MutableMap<String, Any> ?: mutableMapOf()
                        val formattedChat = formatChatList(chatID, chat)
                        chats.add(formattedChat)
                    }

                    if (scrollPosition == "bottom") {
                        if (lastChatID != chatList.lastOrNull()?.chatID) {
                            chatList.addAll(chats)
                        }
                    } else {
                        chatList = chats
                    }
                    refreshRecycler()
                }

                override fun onCancelled(error: DatabaseError?) {
                    Log.d("DatabaseError", error.toString())
                }
            }
            chatListRef.orderByChild("originalReverseTimestamp").startAt(reverseTimestamp).limitToFirst(88)
                    .addValueEventListener(chatListValueListener)
        }
    }

    fun removeObserverForChatList() {
        val chatListRef = ref.child("userChatList").child(myID)
        if (chatListValueListener != null) {
            chatListRef.removeEventListener(chatListValueListener)
            chatListValueListener = null
            isRemoved = true
        }
    }

    fun formatChatList(chatID: String, chat: MutableMap<String,Any>): Chat {
        val formattedChat = Chat()

        formattedChat.chatID = chatID
        formattedChat.userID = chat["userID"] as? String ?: "error"
        formattedChat.messageID = chat["messageID"] as? String ?: "error"

        val profilePicURLString = chat["profilePicURLString"] as? String ?: "error"
        if (profilePicURLString != "error") {
            formattedChat.profilePicURL = Uri.parse(profilePicURLString)
        }

        formattedChat.handle = chat["handle"] as? String ?: "error"

        val type = chat["type"] as? String ?: "error"
        formattedChat.type = type
        when (type) {
            "image" -> { formattedChat.message = "image sent" }
            "video" -> { formattedChat.message = "video sent" }
            else -> { formattedChat.message = chat["message"] as? String ?: "error" }
        }

        val timestamp = chat["timestamp"] as? String ?: "error"
        formattedChat.timestamp = misc.formatTimestamp(timestamp)
        formattedChat.originalReverseTimestamp = chat["originalReverseTimestamp"] as? Double ?: 0.0

        return formattedChat
    }

}
