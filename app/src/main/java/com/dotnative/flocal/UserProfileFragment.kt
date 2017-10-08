@file:Suppress("UNCHECKED_CAST", "MemberVisibilityCanPrivate", "CascadeIf", "LiftReturnOrAssignment", "UnnecessaryVariable", "SetTextI18n")

package com.dotnative.flocal

import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.app.Fragment
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.util.*

class UserProfileFragment : Fragment() {

    // MARK: - Layout

    lateinit var editText: EditText
    lateinit var sendImageView: ImageView
    lateinit var layoutManager: LinearLayoutManager
    lateinit var recyclerView: RecyclerView
    lateinit var adapter: UserProfileAdapter

    // MARK: - Vars

    private var userProfileInteractionListener: UserProfileInteractionListener? = null

    var myID: String = "0"
    var userID: String = "0"
    var handle: String = "0"
    var chatID: String = "0"

    var profilePicURL: Uri? = null
    var myProfilePicURL: Uri? = null
    var backgroundPicURL: Uri? = null
    var didIAdd: Boolean = false

    var parentSource: String = "default"

    var userInfo: User = User()
    var posts: MutableList<Post> = mutableListOf()
    var userInfoValueListener: ValueEventListener? = null
    var postHistoryValueListener: ValueEventListener? = null

    var amIBlocked: Boolean = false
    var blockedValueListener: ValueEventListener? = null

    var scrollPosition: String = "top"
    var isRemoved: Boolean = false

    var ref: DatabaseReference = FirebaseDatabase.getInstance().reference
    val analytics: FirebaseAnalytics = FirebaseAnalytics.getInstance(context)
    val storageRef: StorageReference = FirebaseStorage.getInstance().reference

    val misc = Misc()
    var displayProgress: Boolean = false

    // MARK: - Lifecycle

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context is UserProfileInteractionListener) {
            userProfileInteractionListener = context
        } else {
            throw RuntimeException(context!!.toString() + " must implement OnFragmentInteractionListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setArguments()
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater!!.inflate(R.layout.fragment_user_profile, container, false)
        setLayout(view)
        return view
    }

    override fun onStart() {
        super.onStart()

        myID = misc.setMyID(context)
        if (myID == "0") {
            userProfileInteractionListener?.turnToFragmentFromUserProfile("Login")
        } else {
            if (parentSource != "added" && parentSource != "chatList") {
                misc.setSideMenuIndex(context, 42)
            }
            if (userID != "0") {
                downloadURLs()
                chatID = misc.setChatID(myID, userID)
            } else {
                userProfileInteractionListener?.popBackStackFromUserProfile()
            }
            logViewUserProfile()
            setInfo()
            observeBlocked()
        }
    }

    override fun onStop() {
        super.onStop()
        misc.writeAmITyping(false, chatID, myID)
        removeObserverForUserProfile()
        removeObserverForBlocked()
        dimBackground(false)
    }

    override fun onDetach() {
        super.onDetach()
        removeObserverForUserProfile()
        removeObserverForBlocked()
    }

    // MARK: - Navigation

    interface UserProfileInteractionListener {
        fun turnToFragmentFromUserProfile(name: String)
        fun setUserFromUserProfile(userID: String, didIAdd: Boolean)
        fun popBackStackFromUserProfile()
        fun dismissKeyboardFromUserProfile()
    }

    fun setArguments() {
        val arguments = arguments
        userID = arguments.getString("userIDToPass", "0")
        handle = arguments.getString("handleToPass", "0")
        chatID = arguments.getString("chatIDToPass", "0")
        parentSource = arguments.getString("parentSource", "default")
    }

    fun setLayout(view: View) {
        editText = view.findViewById(R.id.editText)
        editText.setOnFocusChangeListener {_, b ->
            if (b) {
                dimBackground(true)
            } else {
                dimBackground(false)
            }
        }

        sendImageView = view.findViewById(R.id.sendImageView)
        sendImageView.setOnClickListener { writeMessage() }

        layoutManager = LinearLayoutManager(context)
        recyclerView = view.findViewById(R.id.userProfileRecyclerView)
        adapter = UserProfileAdapter(context, userInfo, posts, displayProgress, profilePicURL, backgroundPicURL, amIBlocked, didIAdd,this)
        recyclerView.adapter = adapter
        val divider = DividerItemDecoration(recyclerView.context, 1)
        recyclerView.addItemDecoration(divider)
        recyclerView.addOnScrollListener(scrollListener)
    }

    fun resetUser(taggedHandle: String) {
        handle = taggedHandle
        userID = "0"
        setInfo()
    }

    // MARK: - Scroll

    val scrollListener = object: RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)

            if (!isRemoved) {
                removeObserverForUserProfile()
            }

            val firstVisibleItem = layoutManager.findFirstCompletelyVisibleItemPosition()
            val lastVisibleItem = layoutManager.findLastCompletelyVisibleItemPosition()
            if (firstVisibleItem <= 0) {
                scrollPosition = "top"
                observeUserProfile()
            } else if (lastVisibleItem == posts.size) {
                scrollPosition = "bottom"
                if (posts.size >= 8) {
                    displayProgress = true
                    refreshRecycler()
                    recyclerView?.scrollToPosition(posts.size + 1)
                    observeUserProfile()
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

    fun dimBackground(bool: Boolean) {
        recyclerView.alpha = 1f
        if (bool) {
            recyclerView.alpha = 0.25f
        }
    }

    fun refreshRecycler() {
        adapter = UserProfileAdapter(context, userInfo, posts, displayProgress, profilePicURL, backgroundPicURL, amIBlocked, didIAdd, this)
        recyclerView.adapter = adapter
        recyclerView.adapter.notifyDataSetChanged()
    }

    fun alert(title: String, message: String) {
        misc.displayAlert(context, title, message)
    }

    // MARK: - Analytics

    fun logViewUserProfile() {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        bundle.putString("userID", userID)
        analytics.logEvent("viewUserProfile_Android", bundle)
    }

    fun logChatSent() {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        bundle.putString("userID", userID)
        bundle.putString("chatID", chatID)
        analytics.logEvent("sentChatText_Android", bundle)
    }

    fun logAddedUser(userID: String) {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        bundle.putString("userID", userID)
        analytics.logEvent("addedUser_Android", bundle)
    }

    fun logUpvoted(userID: String, postID: String) {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        bundle.putString("userID", userID)
        bundle.putString("postID", postID)
        analytics.logEvent("upvotedPost_Android", bundle)
    }

    fun logDownvoted(userID: String, postID: String) {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        bundle.putString("userID", userID)
        bundle.putString("postID", postID)
        analytics.logEvent("downvotedPost_Android", bundle)
    }

    // MARK: - Storage

    fun downloadURLs() {
        val backgroundPicRef = storageRef.child("backgroundPic/$userID.jpg")
        backgroundPicRef.downloadUrl.addOnSuccessListener { uri ->
            backgroundPicURL = uri
        }.addOnFailureListener { error ->  Log.d("downloadError", error.toString()) }

        val child = userID + "_large"
        val userPicRef = storageRef.child("profilePic/$child.jpg")
        userPicRef.downloadUrl.addOnSuccessListener { uri ->
            profilePicURL = uri
        }.addOnFailureListener { error ->  Log.d("downloadError", error.toString()) }

        val me = myID + "_large"
        val mePicRef = storageRef.child("profilePic/$me.jpg")
        mePicRef.downloadUrl.addOnSuccessListener { uri ->
            myProfilePicURL = uri
        }.addOnFailureListener { error ->  Log.d("downloadError", error.toString()) }
    }

    // MARK: - Firebase

    fun setInfo() {
        setIDFromHandle()
        misc.didIAdd(userID, myID) { add ->
            didIAdd = add
        }
        userProfileInteractionListener?.setUserFromUserProfile(userID, didIAdd)
        observeUserProfile()
    }

    fun setIDFromHandle() {
        if (userID == "0" && handle != "0") {
            val handleLower = handle.toLowerCase()
            val userRef = ref.child("users")

            userRef.orderByChild("handleLower").equalTo(handleLower).addListenerForSingleValueEvent( object: ValueEventListener {
                override fun onDataChange(snap: DataSnapshot?) {
                    val dict  = snap?.value as? MutableMap<String,Any> ?: mutableMapOf()
                    if (!dict.isEmpty()) {
                        userID = dict.keys.first()
                        downloadURLs()
                    } else {
                        alert("Wrong Handle", "This handle does not exist.")
                    }
                }
                override fun onCancelled(error: DatabaseError?) { Log.d("databseError", error.toString()) }
            })
        }
    }

    fun observeUserProfile() {
        removeObserverForUserProfile()
        isRemoved = false

        userInfoValueListener = object: ValueEventListener {
            override fun onDataChange(snap: DataSnapshot?) {
                val dict  = snap?.value as? MutableMap<String,Any> ?: mutableMapOf()
                userInfo = formatUser(dict)
                refreshRecycler()
            }
            override fun onCancelled(error: DatabaseError?) { Log.d("databaseError", error.toString()) }
        }
        val userRef = ref.child("users").child(userID)
        userRef.addListenerForSingleValueEvent(userInfoValueListener)

        if (scrollPosition == "middle" && !posts.isEmpty()) {
            val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
            val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
            for (index in firstVisiblePosition..lastVisiblePosition) {
                val postID = posts[index - 1].postID
                val idRef = ref.child("posts").child(postID)
                idRef.addListenerForSingleValueEvent( object: ValueEventListener {
                    override fun onDataChange(snap: DataSnapshot?) {
                        val post = snap?.value as? MutableMap<String,Any> ?: mutableMapOf()
                        if (!post.isEmpty()) {
                            val isDeleted = post["isDeleted"] as? Boolean ?: false

                            misc.getVoteStatus(postID, null, myID) { voteStatus ->
                                if (!isDeleted) {
                                    val formattedPost = misc.formatPost(postID, voteStatus, post)
                                    posts[index] = formattedPost
                                }
                            }
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
            val lastReverseTimestamp = posts.lastOrNull()?.originalReverseTimestamp
            val lastPostID = posts.lastOrNull()?.postID
            if (scrollPosition == "bottom") {
                reverseTimestamp = lastReverseTimestamp ?: currentReverseTimestamp
            } else {
                reverseTimestamp = currentReverseTimestamp
            }

            postHistoryValueListener = object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot?) {
                    val dict = snap?.value as? MutableMap<String, Double> ?: mutableMapOf()
                    val postIDs = dict.keys.toMutableList()

                    val userPosts: MutableList<Post> = mutableListOf()
                    for (postID in postIDs) {
                        val postRef = ref.child("posts").child(postID)
                        postRef.addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snap: DataSnapshot?) {
                                val post = snap?.value as? MutableMap<String, Any> ?: mutableMapOf()
                                if (!post.isEmpty()) {
                                    val isDeleted = post["isDeleted"] as? Boolean ?: false

                                    misc.getVoteStatus(postID, null, myID) { voteStatus ->
                                        if (!isDeleted) {
                                            val formattedPost = misc.formatPost(postID, voteStatus, post)
                                            userPosts.add(formattedPost)
                                        }
                                    }
                                }
                            }

                            override fun onCancelled(error: DatabaseError?) {
                                Log.d("databaseError", error.toString())
                            }
                        })
                    }

                    if (scrollPosition == "bottom") {
                        if (lastPostID != userPosts.lastOrNull()?.postID) {
                            posts.addAll(userPosts)
                        }
                    } else {
                        posts = userPosts
                    }
                    displayProgress = false
                    refreshRecycler()
                }

                override fun onCancelled(error: DatabaseError?) {
                    Log.d("databaseError", error.toString())
                }
            }
            val userPostHistoryRef = ref.child("userPostHistory").child(userID)
            userPostHistoryRef.orderByChild("originalReverseTimestamp").startAt(reverseTimestamp).limitToFirst(88).addValueEventListener(postHistoryValueListener)
        }
    }

    fun removeObserverForUserProfile() {
        isRemoved = true

        val userRef = ref.child("users").child(userID)
        if (userInfoValueListener != null) {
            userRef.removeEventListener(userInfoValueListener)
            userInfoValueListener = null
        }

        val userPostHistoryRef = ref.child("userPostHistory").child(userID)
        if (postHistoryValueListener != null) {
            userPostHistoryRef.removeEventListener(postHistoryValueListener)
            postHistoryValueListener = null
        }
    }

    fun writeMessage() {
        userProfileInteractionListener?.dismissKeyboardFromUserProfile()
        dimBackground(false)

        if (amIBlocked) {
            alert("Blocked", "You cannot sent messages to this person.")
            return
        }

        if (userID != "0") {
            val text = editText.text.toString()
            if (text == "") {
                alert("Empty Message", "Please type in text to send")
                return
            }

            val timestamp = misc.getTimestamp("UTC", Date())
            val originalReverseTimestamp = misc.getCurrentReverseTimestamp()
            val originalTimestamp = -1*originalReverseTimestamp

            var handle: String
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            handle = sharedPreferences.getString("handle.flocal", "error")
            if (handle == "error") {
                misc.getHandle(myID) { myHandle ->
                    handle = myHandle
                    val editor = sharedPreferences.edit()
                    editor.putString("handle.flocal", handle)
                    editor.apply()
                }
            }

            val chat: MutableMap<String,Any> = mutableMapOf("userID" to myID, "handle" to handle, "timestamp" to timestamp,
                    "originalReverseTimestamp" to originalReverseTimestamp, "originalTimestamp" to originalTimestamp, "message" to text, "type" to "text")

            val chatID = misc.setChatID(myID, userID)
            val chatRef = ref.child("chats").child(chatID)
            val messageRef = chatRef.child("messages").push()
            val messageID = messageRef.key

            messageRef.setValue(chat)
            misc.playSound(context, R.raw.sent_chat, 0)
            misc.writeAmITyping(false, chatID, myID)

            chat["messageID"] = messageID

            val userChatListRef = ref.child("userChatList").child(userID).child(chatID)
            var myProfilePicURLString = "error"
            if (myProfilePicURL != null) {
                myProfilePicURLString = myProfilePicURL.toString()
            }
            chat["profilePicURLString"] = myProfilePicURLString
            userChatListRef.setValue(chat)

            val isUserInChatRef = chatRef.child("info").child(userID)
            isUserInChatRef.addListenerForSingleValueEvent( object: ValueEventListener {
                override fun onDataChange(snap: DataSnapshot?) {
                    val isUserInChat = snap?.value as? Boolean ?: false
                    if (!isUserInChat) {
                        misc.writeChatNotification(context, userID, myID, text, "text")
                    }
                }
                override fun onCancelled(error: DatabaseError?) { Log.d("databaseError", error.toString()) }
            })

            val myChatListRef = ref.child("userChatList").child(myID).child(chatID)
            var profilePicURLString = "error"
            if (profilePicURL != null) {
                profilePicURLString = profilePicURL.toString()
            }
            chat["profilePicURLString"] = profilePicURLString
            chat["userID"] = userID
            chat["handle"] = userInfo.handle
            myChatListRef.setValue(chat)

            editText.setText("")
            editText.clearFocus()
            logChatSent()

        } else {
            alert("Message Error", "We encountered an error trying to send your message. Please report this bug.")
            return
        }
    }

    fun addUser() {
        if (!amIBlocked) {
            didIAdd = true
            misc.playSound(context, R.raw.added_sound, 0)
            userProfileInteractionListener?.setUserFromUserProfile(userID, didIAdd)
            refreshRecycler()

            misc.addUser(userID, myID)
            misc.writeAddedNotification(context, userID, myID)
            logAddedUser(userID)
        } else {
            alert("Blocked", "This person has blocked you. You cannot add them.")
            return
        }
    }

    fun upvote(index: Int) {
        val individualPost = posts[index]
        val postID = individualPost.postID
        val userID = individualPost.userID
        val content = individualPost.content
        val voteStatus = individualPost.voteStatus

        misc.playSound(context, R.raw.pop_drip, 0)
        when (voteStatus) {
            "up" -> { individualPost.points -= 1 }
            "down" -> { individualPost.points += 2 }
            else -> { individualPost.points += 1 }
        }
        individualPost.voteStatus = "up"
        posts[index] = individualPost
        refreshRecycler()

        if (!amIBlocked) {
             misc.upvote(context, postID, myID, userID, voteStatus, content)
            logUpvoted(userID, postID)
        } else {
            individualPost.voteStatus = "none"
            posts[index] = individualPost
            refreshRecycler()
            alert("Blocked", "This person has blocked you. You cannot vote on their posts.")
            return
        }
    }

    fun downvote(index: Int) {
        val individualPost = posts[index]
        val postID = individualPost.postID
        val userID = individualPost.userID
        val voteStatus = individualPost.voteStatus

        when (voteStatus) {
            "up" -> { individualPost.points -= 2 }
            "down" -> { individualPost.points += 1 }
            else -> { individualPost. points -= 1 }
        }
        individualPost.voteStatus = "down"
        posts[index] = individualPost
        refreshRecycler()

        if (!amIBlocked) {
            misc.downvote(postID, myID, userID, voteStatus)
            logDownvoted(userID, postID)
        } else {
            individualPost.voteStatus = "none"
            posts[index] = individualPost
            refreshRecycler()
            alert("Blocked", "This person has blocked you. You cannot vote on their posts.")
            return
        }
    }

    fun formatUser(dict: MutableMap<String,Any>): User {
        val user = User()

        user.handle = dict["handle"] as? String ?: "error"
        user.points = dict["points"] as? Int ?: 0
        user.followersCount = dict["followersCount"] as? Int ?: 0
        user.description = dict["description"] as? String ?: "error"

        return user
    }

    fun observeBlocked() {
        blockedValueListener = object: ValueEventListener {
            override fun onDataChange(snap: DataSnapshot?) {
                val dict = snap?.value as? MutableMap<String,Any> ?: mutableMapOf()
                val blockedByDict = dict["blockedBy"] as? MutableMap<String,Boolean> ?: mutableMapOf()
                val blockedByList = blockedByDict.keys.toMutableList()
                amIBlocked = false
                if (blockedByList.contains(userID)) {
                    amIBlocked = true
                }
            }
            override fun onCancelled(error: DatabaseError?) { Log.d("databaseError", error.toString()) }
        }
        val userBlockedRef = ref.child("userBlocked").child(myID)
        userBlockedRef.addValueEventListener(blockedValueListener)
    }

    fun removeObserverForBlocked() {
        val userBlockedRef = ref.child("userBlocked").child(myID)
        if (blockedValueListener != null) {
            userBlockedRef.removeEventListener(blockedValueListener)
        }
    }

}
