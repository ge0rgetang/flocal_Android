@file:Suppress("UNCHECKED_CAST", "MemberVisibilityCanPrivate", "CascadeIf", "LiftReturnOrAssignment", "UnnecessaryVariable", "SetTextI18n")

package com.dotnative.flocal

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Point
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.app.Fragment
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.widget.*
import com.baoyz.actionsheet.ActionSheet
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import info.hoang8f.android.segmented.SegmentedGroup
import kotlinx.android.synthetic.main.popup_write_post_image.*
import java.util.*
import kotlin.collections.HashMap

class ReplyFragment : Fragment() {

    // MARK: - Layout

    lateinit var segmentedGroup: SegmentedGroup
    lateinit var layoutManager: LinearLayoutManager
    lateinit var recyclerView: RecyclerView
    lateinit var adapter: ReplyAdapter

    // MARK: - Vars

    private var replyInteractionListener: ReplyInteractionListener? = null

    var myID: String = "0"
    var myProfilePicURL: Uri? = null
    var parentSource: String = "default"
    var postID: String = "0"
    var selectedSegment: String = "ordered"

    var parentPost: Post = Post()
    var postValueListener: ValueEventListener? = null

    var orderedReplies: MutableList<Reply> = mutableListOf()
    var orderedValueListener: ValueEventListener? = null
    var topReplies: MutableList<Reply> = mutableListOf()
    var topValueListener: ValueEventListener? = null
    var newestReplies: MutableList<Reply> = mutableListOf()
    var newestValueListener: ValueEventListener? = null

    var blockedBy: MutableList<String> = mutableListOf()
    var blockedValueListener: ValueEventListener? = null

    var scrollPosition: String = "top"
    var displayProgress: Boolean = false
    var isRemoved: Boolean = false

    var ref: DatabaseReference = FirebaseDatabase.getInstance().reference
    val storageRef: StorageReference = FirebaseStorage.getInstance().reference
    var analytics: FirebaseAnalytics? = null

    val misc = Misc()
    var popupContainer: ViewGroup? = null
    var popupView: View? = null
    var popWindow: PopupWindow? = null

    // MARK: - Lifecycle

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context is ReplyInteractionListener) {
            replyInteractionListener = context
        } else {
            throw RuntimeException(context!!.toString() + " must implement OnFragmentInteractionListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setArguments()
        analytics = FirebaseAnalytics.getInstance(context)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater!!.inflate(R.layout.fragment_reply, container, false)
        popupContainer = container
        popupView = view
        setLayout(view)
        return view
    }

    override fun onStart() {
        super.onStart()

        myID = misc.setMyID(context)
        if (myID == "0") {
            replyInteractionListener?.turnToFragmentFromReply("Login")
        } else {
            logViewReplies()
            if (parentSource != "home") {
                misc.setSideMenuIndex(context, 42)
            }
            downloadMyProfilePicURL()
            observeReplies()
            observeBlocked()
        }
    }

    override fun onStop() {
        super.onStop()
        removeObserverForReplies()
        removeObserverForBlocked()
    }

    override fun onDetach() {
        super.onDetach()
        replyInteractionListener = null
        removeObserverForReplies()
    }

    // MARK: - Navigation

    interface ReplyInteractionListener {
        fun turnToFragmentFromReply(name: String)
        fun dismissKeyboardFromReply()
        fun turnToReportPostFromReply(postID: String, replyID: String?)
    }

    fun setArguments() {
        val arguments = arguments
        postID = arguments.getString("postIDToPass", "0")
        parentSource = arguments.getString("parentSource", "default")

        val parentMap = arguments.getSerializable("postToPass") as? HashMap<String, Any?> ?: hashMapOf()
        if (!parentMap.isEmpty()) {
            parentPost.postID = postID
            parentPost.userID = parentMap["userID"] as? String ?: "error"
            parentPost.type = parentMap["type"] as? String ?: "error"
            parentPost.profilePicURL = parentMap["profilePicURL"] as? Uri
            parentPost.postPicURL= parentMap["postPicURL"] as? Uri
            parentPost.postVidURL = parentMap["postVidURL"] as? Uri
            parentPost.postVidPreviewURL = parentMap["postVidPreviewURL"] as? Uri
            parentPost.handle = parentMap["handle"] as? String ?: "error"
            parentPost.content = parentMap["content"] as? String ?: "error"
            parentPost.timestamp = parentMap["timestamp"] as? String ?: "error"
            parentPost.originalReverseTimestamp = parentMap["originalReverseTimestamp"] as? Double ?: 0.0
            parentPost.points = parentMap["points"] as? Int ?: 0
            parentPost.score = parentMap["score"] as? Double ?: 0.0
            parentPost.replyString = parentMap["replyString"] as? String ?: "- replies"
            parentPost.voteStatus = parentMap["voteStatus"] as? String ?: "none"
        }

        if (postID == "0") {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            postID = sharedPreferences.getString("postIDToPass.flocal", "0")
        }
    }

    fun setLayout(view: View) {
        segmentedGroup = view.findViewById(R.id.segmentedGroup)
        segmentedGroup.setOnCheckedChangeListener(segmentDidChange)
        layoutManager = LinearLayoutManager(context)
        recyclerView = view.findViewById(R.id.replyRecyclerView)
        adapter = ReplyAdapter(context, this, parentPost, orderedReplies, topReplies, newestReplies, selectedSegment, displayProgress)
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)
        val divider = DividerItemDecoration(recyclerView.context, 1)
        recyclerView.addItemDecoration(divider)
        recyclerView.addOnScrollListener(scrollListener)
    }

    @SuppressLint("SetTextI18n")
    fun showWriteReply(content: String?, index: Int) {
        val layoutInflater: LayoutInflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val inflatedView: View = layoutInflater.inflate(R.layout.popup_write_reply, popupContainer, false)

        val display = activity.windowManager.defaultDisplay
        val size = Point()
        display.getSize(size)
        val width = size.x

        val popupWindow = PopupWindow(inflatedView, width - 50, WindowManager.LayoutParams.WRAP_CONTENT, true)
        popWindow = popWindow
        popupWindow.isFocusable = true
        popupWindow.isOutsideTouchable = true

        val characterCountTextView: TextView = inflatedView.findViewById(R.id.characterCountTextView)
        val currentLength = editText.text.toString().length
        characterCountTextView.text = "$currentLength/255"

        val editText: EditText = inflatedView.findViewById(R.id.editText)
        if (content != null) {
            editText.setText(content)
            var replyID: String? = null
            if (index != 0) {
                replyID = determineReplies()[index - 2].replyID
            }
            logViewEdit(replyID)
        } else {
            logViewWriteReply()
        }
        editText.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(text: Editable?) {
                val length = text.toString().length
                characterCountTextView.text = "$length/255"
            }
        })

        val cancelButton: Button = inflatedView.findViewById(R.id.cancelButton)
        cancelButton.setOnClickListener {
            replyInteractionListener?.dismissKeyboardFromReply()
            popupWindow.dismiss()
        }

        val sendImageView: ImageView = inflatedView.findViewById(R.id.sendImageView)
        sendImageView.setOnClickListener {
            val text = editText.text.toString()
            if (text == "") {
                alert("Empty Post Content", "Please write some text for your post.")
            } else {
                if (content != null) {
                    var replyID: String? = null
                    if (index != 0) {
                        replyID = determineReplies()[index - 2].replyID
                    }
                    edit(index, replyID, text)
                } else {
                    writeReply(text)
                }
            }
        }

        misc.playSound(context, R.raw.button_click, 0)
        popupWindow.showAtLocation(popupView, Gravity.CENTER, 0,0)
    }

    fun showEditSheet(index: Int) {
        val userID: String
        val content: String
        var replyID: String? = null

        if (index == 0) {
            userID = parentPost.userID
            content = parentPost.content
        } else {
            val reply = determineReplies()[index - 2]
            userID = reply.userID
            content = reply.content
            replyID = reply.replyID
        }

        val sheet = ActionSheet.createBuilder(context, fragmentManager)
        sheet.setCancelButtonTitle("Cancel")
        sheet.setCancelableOnTouchOutside(true)
        if (userID == myID) {
            sheet.setOtherButtonTitles("Edit", "Delete")
        } else {
            sheet.setOtherButtonTitles("Report")
        }
        sheet.setListener( object: ActionSheet.ActionSheetListener {
            override fun onOtherButtonClick(actionSheet: ActionSheet?, index: Int) {
                if (userID == myID) {
                    when (index) {
                        0 -> { showWriteReply(content, index) }
                        1 -> { deletePost(replyID, index) }
                        else -> { actionSheet?.dismiss() }
                    }
                } else {
                    replyInteractionListener?.turnToReportPostFromReply(postID, replyID)
                }
            }
            override fun onDismiss(actionSheet: ActionSheet?, isCancel: Boolean) {}
        })
        sheet.show()
    }

    // MARK: - Segment

    val segmentDidChange = RadioGroup.OnCheckedChangeListener { _, id ->
        scrollToTop()

        when (id) {
            R.id.orderedSegment -> {
                selectedSegment = "ordered"
            }
            R.id.topSegment -> {
                selectedSegment = "top"
            }
            R.id.newestSegment -> {
                selectedSegment = "newest"
            }
            else -> {}
        }

        observeReplies()
    }

    fun setReplySegment() {
        segmentedGroup.check(R.id.newestSegment)
        selectedSegment = "newest"
        scrollToTop()
    }

    // MARK: - Scroll

    val scrollListener = object: RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)

            if (!isRemoved) {
                removeObserverForReplies()
            }

            val replies = determineReplies()

            val firstVisibleItem = layoutManager.findFirstCompletelyVisibleItemPosition()
            val lastVisibleItem = layoutManager.findLastCompletelyVisibleItemPosition()
            if (firstVisibleItem <= 0) {
                scrollPosition = "top"
            } else if (lastVisibleItem == replies.size - 1) {
                scrollPosition = "bottom"
                if (replies.size > 8) {
                    displayProgress = true
                    refreshRecycler()
                    recyclerView?.scrollToPosition(replies.size)
                    observeReplies()
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

    fun alert(title: String, message: String) {
        misc.displayAlert(context, title, message)
    }

    fun determineReplies(): MutableList<Reply> {
        when (selectedSegment) {
            "top" -> { return topReplies }
            "newest" -> { return newestReplies }
            else -> { return orderedReplies }
        }
    }

    fun refreshRecycler() {
        adapter = ReplyAdapter(context, this, parentPost, orderedReplies, topReplies, newestReplies, selectedSegment, displayProgress)
        recyclerView.adapter = adapter
        recyclerView.adapter.notifyDataSetChanged()
    }

    // MARK: - Analytics

    fun logViewReplies() {
        val child: String
        when (selectedSegment) {
            "top" -> {
                child = "viewTopReplies_Android"
            }
            "newest" -> {
                child = "viewNewestReplies_Android"
            }
            else -> {
                child = "viewOrderedReplies_Android"
            }
        }

        val bundle = Bundle()
        bundle.putString("myID", myID)
        bundle.putString("postID", postID)
        analytics?.logEvent(child, bundle)
    }

    fun logUpvoted(userID: String, postID: String) {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        bundle.putString("userID", userID)
        bundle.putString("postID", postID)
        analytics?.logEvent("upvotedPost_Android", bundle)
    }

    fun logDownvoted(userID: String, postID: String) {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        bundle.putString("userID", userID)
        bundle.putString("postID", postID)
        analytics?.logEvent("downvotedPost_Android", bundle)
    }

    fun logUpvotedReply(userID: String, postID: String, replyID: String) {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        bundle.putString("userID", userID)
        bundle.putString("postID", postID)
        bundle.putString("replyID", replyID)
        analytics?.logEvent("upvotedReply_Android", bundle)
    }

    fun logDownvotedReply(userID: String, postID: String, replyID: String) {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        bundle.putString("userID", userID)
        bundle.putString("postID", postID)
        bundle.putString("replyID", replyID)
        analytics?.logEvent("downvotedReply_Android", bundle)
    }

    fun logViewWriteReply() {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        analytics?.logEvent("viewWriteReply_Android", bundle)
    }

    fun logReplySent(replyID: String) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val myLocation = sharedPreferences.getBoolean("myLocation.flocal", true)
        val longitude = sharedPreferences.getString("longitude.flocal", "0").toDouble()
        val latitude = sharedPreferences.getString("latitude.flocal", "0").toDouble()

        val bundle = Bundle()
        bundle.putString("myID", myID)
        bundle.putString("postID", postID)
        bundle.putString("replyID", replyID)
        bundle.putBoolean("atMyLocation", myLocation)
        bundle.putDouble("longitude", longitude)
        bundle.putDouble("latitude", latitude)
        analytics?.logEvent("sentReply_Android", bundle)
    }

    fun logUserTagged(replyID: String, userID: String, handle: String) {
        val bundle = Bundle()
        bundle.putString("postID", postID)
        bundle.putString("replyID", replyID)
        bundle.putString("myID", myID)
        bundle.putString("userID", userID)
        bundle.putString("userHandle", handle)
        analytics?.logEvent("taggedUserInReply_Android", bundle)
    }

    fun logViewEdit(replyID: String?) {
        val bundle = Bundle()

        var type = "Post"
        if (replyID != null) {
            type = "Reply"
            bundle.putString("replyID", replyID)
        }

        bundle.putString("postID", postID)
        bundle.putString("myID", myID)
        analytics?.logEvent("viewEdit" + type + "_Android", bundle)
    }

    fun logEdited(replyID: String?) {
        val bundle = Bundle()

        var type = "Post"
        if (replyID != null) {
            type = "Reply"
            bundle.putString("replyID", replyID)
        }

        bundle.putString("postID", postID)
        bundle.putString("myID", myID)
        analytics?.logEvent("edited" + type + "_Android", bundle)
    }

    // MARK: - Storage

    fun downloadMyProfilePicURL() {
        val child = "profilePic/$myID" + "_small.jpg"
        val myProfilePicRef = storageRef.child(child)
        myProfilePicRef.downloadUrl.addOnSuccessListener { uri ->
            myProfilePicURL = uri
        }.addOnFailureListener { error ->
            Log.d("downloadError", error.toString())
            myProfilePicURL = null
        }
    }

    // MARK: - Firebase

    fun observeReplies() {
        removeObserverForReplies()
        isRemoved = false

        postValueListener = object: ValueEventListener {
            override fun onDataChange(snap: DataSnapshot?) {
                val post = snap?.value as? MutableMap<String,Any> ?: mutableMapOf()
                if (!post.isEmpty()) {
                    parentPost = misc.formatPost(postID, myID, post)
                    displayProgress = false
                    refreshRecycler()
                }
            }
            override fun onCancelled(error: DatabaseError?) { Log.d("databaseError", error.toString()) }
        }
        val postRef = ref.child("posts").child(postID)
        postRef.addValueEventListener(postValueListener)

        val replies = determineReplies()

        if (scrollPosition == "middle" && !replies.isEmpty()) {
            val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
            val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
            for (index in firstVisiblePosition..lastVisiblePosition) {
                val replyID = replies[index - 2].replyID
                val idRef = postRef.child(replyID)
                idRef.addListenerForSingleValueEvent( object: ValueEventListener {
                    override fun onDataChange(snap: DataSnapshot?) {
                        val reply = snap?.value as? MutableMap<String,Any> ?: mutableMapOf()
                        if (!reply.isEmpty()) {
                            val isDeleted = reply["isDeleted"] as? Boolean ?: false
                            val reports = reply["reports"] as? Int ?: 0

                            misc.getVoteStatus(postID, replyID, myID) { voteStatus ->
                                if (reports < 3 && !isDeleted) {
                                    val formattedReply = formatReply(replyID, voteStatus, reply)
                                    when (selectedSegment) {
                                        "top" -> { topReplies[index - 2] = formattedReply }
                                        "newest" -> { newestReplies[index - 2] = formattedReply }
                                        else -> { orderedReplies[index - 2] = formattedReply  }
                                    }
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
            val lastReplyID = replies.lastOrNull()?.replyID
            val repliesList: MutableList<Reply> = mutableListOf()
            val replyRef = ref.child("replies").child(postID)

            when (selectedSegment) {
                "top" -> {
                    topValueListener = object : ValueEventListener {
                        override fun onDataChange(snap: DataSnapshot?) {
                            val dict = snap?.value as? MutableMap<String, Any> ?: mutableMapOf()
                            for ((replyID, value) in dict) {
                                val reply = value as? MutableMap<String, Any> ?: mutableMapOf()
                                if (!reply.isEmpty()) {
                                    val isDeleted = reply["isDeleted"] as? Boolean ?: false
                                    val reports = reply["reports"] as? Int ?: 0

                                    misc.getVoteStatus(postID, replyID, myID) { voteStatus ->
                                        if (reports < 3 && !isDeleted) {
                                            val formattedReply = formatReply(replyID, voteStatus, reply)
                                            repliesList.add(formattedReply)
                                        }
                                    }
                                }
                            }

                            if (scrollPosition == "bottom") {
                                if (lastReplyID != repliesList.lastOrNull()?.replyID) {
                                    topReplies.addAll(repliesList)
                                }
                            } else {
                                topReplies = repliesList
                            }
                            displayProgress = false
                            refreshRecycler()
                        }
                        override fun onCancelled(error: DatabaseError?) {
                            Log.d("databaseError", error.toString())
                        }
                    }

                    if (scrollPosition == "bottom") {
                        val lastReverseScore = -1*(replies.lastOrNull()?.score ?: 0.0)
                        replyRef.orderByChild("reverseScore").startAt(lastReverseScore).limitToFirst(88).addValueEventListener(topValueListener)
                    } else {
                        replyRef.orderByChild("reverseScore").limitToFirst(88).addValueEventListener(topValueListener)
                    }
                }

                "newest" -> {
                    val reverseTimestamp: Double
                    val currentReverseTimestamp = misc.getCurrentReverseTimestamp()
                    val lastReverseTimestamp = replies.lastOrNull()?.originalReverseTimestamp

                    if (scrollPosition == "bottom") {
                        reverseTimestamp = lastReverseTimestamp ?: currentReverseTimestamp
                    } else {
                        reverseTimestamp = currentReverseTimestamp
                    }

                    newestValueListener = object : ValueEventListener {
                        override fun onDataChange(snap: DataSnapshot?) {
                            val dict = snap?.value as? MutableMap<String, Any> ?: mutableMapOf()
                            for ((replyID, value) in dict) {
                                val reply = value as? MutableMap<String, Any> ?: mutableMapOf()
                                if (!reply.isEmpty()) {
                                    val isDeleted = reply["isDeleted"] as? Boolean ?: false
                                    val reports = reply["reports"] as? Int ?: 0

                                    misc.getVoteStatus(postID, replyID, myID) { voteStatus ->
                                        if (reports < 3 && !isDeleted) {
                                            val formattedReply = formatReply(replyID, voteStatus, reply)
                                            repliesList.add(formattedReply)
                                        }
                                    }
                                }
                            }

                            if (scrollPosition == "bottom") {
                                if (lastReplyID != repliesList.lastOrNull()?.replyID) {
                                    topReplies.addAll(repliesList)
                                }
                            } else {
                                topReplies = repliesList
                            }
                            displayProgress = false
                            refreshRecycler()
                        }
                        override fun onCancelled(error: DatabaseError?) {
                            Log.d("databaseError", error.toString())
                        }
                    }
                    replyRef.orderByChild("originalReverseTimestamp").startAt(reverseTimestamp).limitToFirst(88).
                            addValueEventListener(newestValueListener)
                }

                else -> {
                    val timestamp: Double
                    val firstReverseTimestamp = parentPost.originalReverseTimestamp
                    val firstTimestamp = -1 * firstReverseTimestamp
                    val lastReverseTimestamp = replies.lastOrNull()?.originalReverseTimestamp
                    val lastTimestamp = -1 * (lastReverseTimestamp ?: firstReverseTimestamp)

                    if (scrollPosition == "bottom") {
                        timestamp = lastTimestamp
                    } else {
                        timestamp = firstTimestamp
                    }

                    orderedValueListener = object : ValueEventListener {
                        override fun onDataChange(snap: DataSnapshot?) {
                            val dict = snap?.value as? MutableMap<String, Any> ?: mutableMapOf()
                            for ((replyID, value) in dict) {
                                val reply = value as? MutableMap<String, Any> ?: mutableMapOf()
                                if (!reply.isEmpty()) {
                                    val isDeleted = reply["isDeleted"] as? Boolean ?: false
                                    val reports = reply["reports"] as? Int ?: 0

                                    misc.getVoteStatus(postID, replyID, myID) { voteStatus ->
                                        if (reports < 3 && !isDeleted) {
                                            val formattedReply = formatReply(replyID, voteStatus, reply)
                                            repliesList.add(formattedReply)
                                        }
                                    }
                                }
                            }

                            if (scrollPosition == "bottom") {
                                if (lastReplyID != repliesList.lastOrNull()?.replyID) {
                                    orderedReplies.addAll(repliesList)
                                }
                            } else {
                                orderedReplies = repliesList
                            }
                            displayProgress = false
                            refreshRecycler()
                        }
                        override fun onCancelled(error: DatabaseError?) {
                            Log.d("databaseError", error.toString())
                        }
                    }
                    replyRef.orderByChild("originalTimestamp").startAt(timestamp).limitToFirst(88).
                            addValueEventListener(orderedValueListener)
                }

            }
        }
    }

    fun removeObserverForReplies() {
        isRemoved = true

        val postRef = ref.child("posts").child(postID)
        if (postValueListener != null) {
            postRef.removeEventListener(postValueListener)
        }

        val replyRef = ref.child("replies").child(postID)
        if (orderedValueListener != null) {
            replyRef.removeEventListener(orderedValueListener)
        }
        if (topValueListener != null) {
            replyRef.removeEventListener(topValueListener)
        }
        if (newestValueListener != null) {
            replyRef.removeEventListener(newestValueListener)
        }
    }

    fun upvote(index: Int) {
        var replyID: String? = null
        val userID: String
        val content: String
        val voteStatus: String
        var pointsCount: Int

        if (index == 0) {
            userID = parentPost.userID
            content = parentPost.content
            voteStatus = parentPost.voteStatus
            pointsCount = parentPost.points
        } else {
            val reply = determineReplies()[index - 2]
            replyID = reply.replyID
            userID = reply.userID
            content = reply.content
            voteStatus = reply.voteStatus
            pointsCount = reply.points
        }

        when (voteStatus) {
            "up" -> { pointsCount -= 1 }
            "down" -> { pointsCount += 2 }
            else -> { pointsCount += 1 }
        }
        if (index == 0) {
            logUpvoted(userID, postID)
            parentPost.voteStatus = "up"
            parentPost.points = pointsCount
        } else {
            logUpvotedReply(userID, postID, replyID ?: "0")
            misc.playSound(context, R.raw.pop_drip, 0)
            when (selectedSegment) {
                "top" -> {
                    topReplies[index - 2].voteStatus = "up"
                    topReplies[index - 2].points = pointsCount
                }
                "newest" -> {
                    newestReplies[index - 2].voteStatus = "up"
                    newestReplies[index - 2].points = pointsCount
                }
                else -> {
                    orderedReplies[index - 2].voteStatus = "up"
                    orderedReplies[index - 2].points = pointsCount
                }
            }
        }
        refreshRecycler()

        val amIBlocked = misc.amIBlocked(userID, blockedBy)
        if (!amIBlocked) {
            val postVoteHistoryRef = ref.child("postVoteHistory").child(postID)
            var postRef = ref.child("posts").child(postID)
            var voteHistoryRef = postVoteHistoryRef.child(myID)
            var type = "post"
            if (index != 0) {
                postRef = ref.child("replies").child(postID).child(replyID)
                voteHistoryRef = postVoteHistoryRef.child("replies").child(replyID).child(myID)
                type = "comment"
            }

            postRef.runTransaction( object: Transaction.Handler {
                override fun doTransaction(data: MutableData?): Transaction.Result {
                    val post = data?.value as? MutableMap<String,Any> ?: return Transaction.success(data)
                    var upvotes = post["upvotes"] as? Int ?: 0
                    var downvotes = post["downvotes"] as? Int ?: 0
                    when (voteStatus) {
                        "up" -> {
                            upvotes -= 1
                            voteHistoryRef.removeValue()
                        }
                        "down" -> {
                            upvotes += 1
                            downvotes -= 1
                            voteHistoryRef.setValue(true)
                        }
                        else -> {
                            upvotes += 1
                            voteHistoryRef.setValue(true)
                        }
                    }

                    post["upvotes"] = upvotes
                    post["downvotes"] = downvotes
                    post["points"] = upvotes - downvotes
                    data.value = post
                    return Transaction.success(data)
                }
                override fun onComplete(p0: DatabaseError?, p1: Boolean, p2: DataSnapshot?) {}
            })

            misc.hasUpvoteNotified(postID, replyID, myID) { notified ->
                if ((myID != userID) && !notified) {
                    var hasUpvotedRef =  postVoteHistoryRef.child("upvoteNotified").child(myID)
                    if (index != 0) {
                        hasUpvotedRef = postVoteHistoryRef.child("replies").child(replyID).child("upvoteNotified").child(myID)
                    }
                    hasUpvotedRef.setValue(true)
                    misc.writePointNotification(context, userID, myID, postID, content, type)
                }
            }

            val userRef = ref.child("users").child(userID)
            userRef.runTransaction( object: Transaction.Handler {
                override fun doTransaction(data: MutableData?): Transaction.Result {
                    val userInfo = data?.value as? MutableMap<String,Any> ?: return Transaction.success(data)
                    var postPoints = userInfo["postPoints"] as? Int ?: 0
                    var replyPoints = userInfo["replyPoints"] as? Int ?: 0

                    when (voteStatus) {
                        "up" -> {
                            if (index == 0) {
                                postPoints -= 1
                            } else {
                                replyPoints -= 1
                            }
                        }
                        "down" -> {
                            if (index == 0) {
                                postPoints += 2
                            } else {
                                replyPoints += 2
                            }
                        }
                        else -> {
                            if (index == 0) {
                                postPoints += 1
                            } else {
                                replyPoints += 1
                            }
                        }
                    }

                    val updatedPoints = postPoints + replyPoints
                    userInfo["points"] = updatedPoints
                    userInfo["postPoints"] = postPoints
                    userInfo["replyPoints"] = replyPoints
                    data.value = userInfo

                    misc.getFollowers(userID) { userFollowers ->
                        if (!userFollowers.isEmpty() && updatedPoints != 0) {
                            val fanoutObject = mutableMapOf<String,Any>()
                            for (followerID in userFollowers) {
                                fanoutObject.put("/$followerID/$userID/points", updatedPoints)
                            }
                            val userAddedRef = ref.child("userAdded")
                            userAddedRef.updateChildren(fanoutObject)
                        }
                    }

                    return Transaction.success(data)
                }
                override fun onComplete(p0: DatabaseError?, p1: Boolean, p2: DataSnapshot?) {}
            })

            if (index == 0) {
                val userPostHistoryRef = userRef.child("userPostHistory").child(userID).child(postID)
                userPostHistoryRef.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(data: MutableData?): Transaction.Result {
                        val postInfo = data?.value as? MutableMap<String, Any> ?: return Transaction.success(data)
                        var points = postInfo["points"] as? Int ?: 0
                        when (voteStatus) {
                            "up" -> { points -= 1 }
                            "down" -> { points += 2 }
                            else -> { points += 1 }
                        }
                        postInfo["points"] = points
                        data.value = postInfo
                        return Transaction.success(data)
                    }
                    override fun onComplete(p0: DatabaseError?, p1: Boolean, p2: DataSnapshot?) {}
                })
            }

        } else {
            if (index == 0) {
                parentPost.voteStatus = "none"
            } else {
                when (selectedSegment) {
                    "top" -> {
                        topReplies[index - 2].voteStatus = "none"
                    }
                    "newest" -> {
                        newestReplies[index - 2].voteStatus = "none"
                    }
                    else -> {
                        orderedReplies[index - 2].voteStatus = "none"
                    }
                }
            }
            refreshRecycler()
            alert("blocked", "This person has blocked you. You cannot vote on their posts.")
            return
        }
    }

    fun downvote(index: Int) {
        var replyID: String? = null
        val userID: String
        val voteStatus: String
        var pointsCount: Int

        if (index == 0) {
            userID = parentPost.userID
            voteStatus = parentPost.voteStatus
            pointsCount = parentPost.points
        } else {
            val reply = determineReplies()[index - 2]
            replyID = reply.replyID
            userID = reply.userID
            voteStatus = reply.voteStatus
            pointsCount = reply.points
        }

        when (voteStatus) {
            "up" -> { pointsCount -= 2 }
            "down" -> { pointsCount += 1 }
            else -> { pointsCount -= 1 }
        }
        if (index == 0) {
            logDownvoted(userID, postID)
            parentPost.voteStatus = "down"
            parentPost.points = pointsCount
        } else {
            logDownvotedReply(userID, postID, replyID ?: "0")
            when (selectedSegment) {
                "top" -> {
                    topReplies[index - 2].voteStatus = "down"
                    topReplies[index - 2].points = pointsCount
                }
                "newest" -> {
                    newestReplies[index - 2].voteStatus = "down"
                    newestReplies[index - 2].points = pointsCount
                }
                else -> {
                    orderedReplies[index - 2].voteStatus = "down"
                    orderedReplies[index - 2].points = pointsCount
                }
            }
        }
        refreshRecycler()

        val amIBlocked = misc.amIBlocked(userID, blockedBy)
        if (!amIBlocked) {
            val postVoteHistoryRef = ref.child("postVoteHistory").child(postID)
            var postRef = ref.child("posts").child(postID)
            var voteHistoryRef = postVoteHistoryRef.child(myID)
            if (index != 0) {
                postRef = ref.child("replies").child(postID).child(replyID)
                voteHistoryRef = postVoteHistoryRef.child("replies").child(replyID).child(myID)
            }

            postRef.runTransaction( object: Transaction.Handler {
                override fun doTransaction(data: MutableData?): Transaction.Result {
                    val post = data?.value as? MutableMap<String,Any> ?: return Transaction.success(data)
                    var upvotes = post["upvotes"] as? Int ?: 0
                    var downvotes = post["downvotes"] as? Int ?: 0
                    when (voteStatus) {
                        "up" -> {
                            upvotes -= 1
                            downvotes += 1
                            voteHistoryRef.setValue(false)
                        }
                        "down" -> {
                            downvotes -= 1
                            voteHistoryRef.removeValue()
                        }
                        else -> {
                            downvotes += 1
                            voteHistoryRef.setValue(false)
                        }
                    }

                    post["upvotes"] = upvotes
                    post["downvotes"] = downvotes
                    post["points"] = upvotes - downvotes
                    data.value = post
                    return Transaction.success(data)
                }
                override fun onComplete(p0: DatabaseError?, p1: Boolean, p2: DataSnapshot?) {}
            })

            val userRef = ref.child("users").child(userID)
            userRef.runTransaction( object: Transaction.Handler {
                override fun doTransaction(data: MutableData?): Transaction.Result {
                    val userInfo = data?.value as? MutableMap<String,Any> ?: return Transaction.success(data)
                    var postPoints = userInfo["postPoints"] as? Int ?: 0
                    var replyPoints = userInfo["replyPoints"] as? Int ?: 0

                    when (voteStatus) {
                        "up" -> {
                            if (index == 0) {
                                postPoints -= 2
                            } else {
                                replyPoints -= 2
                            }
                        }
                        "down" -> {
                            if (index == 0) {
                                postPoints += 1
                            } else {
                                replyPoints += 1
                            }
                        }
                        else -> {
                            if (index == 0) {
                                postPoints -= 1
                            } else {
                                replyPoints -= 1
                            }
                        }
                    }

                    val updatedPoints = postPoints + replyPoints
                    userInfo["points"] = updatedPoints
                    userInfo["postPoints"] = postPoints
                    userInfo["replyPoints"] = replyPoints
                    data.value = userInfo

                    misc.getFollowers(userID) { userFollowers ->
                        if (!userFollowers.isEmpty() && updatedPoints != 0) {
                            val fanoutObject = mutableMapOf<String,Any>()
                            for (followerID in userFollowers) {
                                fanoutObject.put("/$followerID/$userID/points", updatedPoints)
                            }
                            val userAddedRef = ref.child("userAdded")
                            userAddedRef.updateChildren(fanoutObject)
                        }
                    }

                    return Transaction.success(data)
                }
                override fun onComplete(p0: DatabaseError?, p1: Boolean, p2: DataSnapshot?) {}
            })

            if (index == 0) {
                val userPostHistoryRef = userRef.child("userPostHistory").child(userID).child(postID)
                userPostHistoryRef.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(data: MutableData?): Transaction.Result {
                        val postInfo = data?.value as? MutableMap<String, Any> ?: return Transaction.success(data)
                        var points = postInfo["points"] as? Int ?: 0
                        when (voteStatus) {
                            "up" -> { points -= 2 }
                            "down" -> { points += 1 }
                            else -> { points -= 1 }
                        }
                        postInfo["points"] = points
                        data.value = postInfo
                        return Transaction.success(data)
                    }
                    override fun onComplete(p0: DatabaseError?, p1: Boolean, p2: DataSnapshot?) {}
                })
            }

        } else {
            if (index == 0) {
                parentPost.voteStatus = "none"
            } else {
                when (selectedSegment) {
                    "top" -> {
                        topReplies[index - 2].voteStatus = "none"
                    }
                    "newest" -> {
                        newestReplies[index - 2].voteStatus = "none"
                    }
                    else -> {
                        orderedReplies[index - 2].voteStatus = "none"
                    }
                }
            }
            refreshRecycler()
            alert("blocked", "This person has blocked you. You cannot vote on their posts.")
            return
        }
    }

    fun writeReply(text: String) {
        replyInteractionListener?.dismissKeyboardFromReply()
        popWindow?.dismiss()

        val isDeleted = false
        val isEdited = false
        val reports = 0

        val userID = myID
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        var handle = sharedPreferences.getString("handle.flocal", "error")
        if (handle == "error") {
            misc.getHandle(myID) { myHandle ->
                handle = myHandle
                val editor = sharedPreferences.edit()
                editor.putString("handle.flocal", handle)
                editor.apply()
            }
        }

        val points = 0
        val upvotes = 0
        val downvotes = 0
        val score = 0
        val content = text
        val timestamp = misc.getTimestamp("UTC", Date())
        val originalReverseTimestamp = misc.getCurrentReverseTimestamp()
        val originalTimestamp = -1*originalReverseTimestamp

        var profilePicURLString = "error"
        if (myProfilePicURL != null) {
            profilePicURLString = myProfilePicURL.toString()
        }

        val reply: MutableMap<String,Any> = mutableMapOf("isDeleted" to isDeleted, "isEdited" to isEdited, "reports" to reports,
                "userID" to userID, "profilePicURLString" to profilePicURLString, "handle" to handle, "points" to points, "upvotes" to upvotes, "downvotes" to downvotes,
                "score" to score, "reverseScore" to score, "originalContent" to content, "content" to content, "timestamp" to timestamp,
                "originalReverseTimestamp" to originalReverseTimestamp, "originalTimestamp" to originalTimestamp)

        val replyRef = ref.child("replies").child(postID).push()
        val replyID = replyRef.key
        replyRef.setValue(reply)

        val postRef = ref.child("posts").child(postID)
        postRef.runTransaction( object: Transaction.Handler {
            override fun doTransaction(data: MutableData?): Transaction.Result {
                val post = data?.value as? MutableMap<String,Any> ?: return Transaction.success(data)
                var replyCount = post["replyCount"] as? Int ?: 0
                replyCount += 1
                post["replyCount"] = replyCount
                data.value = post
                return Transaction.success(data)
            }
            override fun onComplete(p0: DatabaseError?, p1: Boolean, p2: DataSnapshot?) {}
        })

        logReplySent(replyID)
        misc.playSound(context, R.raw.send_post, 0)
        setReplySegment()
        observeReplies()
        popWindow?.dismiss()

        if (myID != userID) {
            writeReplyNotification(userID, replyID, content)
        }
        writeTagged(replyID, content)
    }

    fun writeTagged(replyID: String, content: String) {
        val tagged = misc.handlesWithoutAt(content)
        for (tag in tagged) {
            val tagLower = tag.toLowerCase()
            val userRef = ref.child("users")
            userRef.orderByChild("handleLower").equalTo(tagLower).addListenerForSingleValueEvent( object: ValueEventListener {
                override fun onDataChange(snap: DataSnapshot?) {
                    if (snap?.value != null) {
                        val users = snap.value as? MutableMap<String,Any> ?: mutableMapOf()
                        val userID = users.keys.first()
                        val amIBlocked = misc.amIBlocked(userID, blockedBy)
                        if (userID != myID && !amIBlocked) {
                            misc.writeTaggedNotification(context, userID, myID, postID, content, "comment")
                            logUserTagged(replyID, userID, tag)
                        }
                    }
                }
                override  fun onCancelled(error: DatabaseError?) { Log.d("databaseError", error.toString()) }
            })
        }
    }

    fun writeReplyNotification(userID: String, replyID: String, content: String) {
        val userRef = ref.child("users").child(userID)
        userRef.child("lastNotificationType").setValue("reply")

        var notification: String
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        var handle = sharedPreferences.getString("handle.flocal", "error")
        if (handle != "error") {
            notification = "@$handle commented on your post: $content"
            setNotification(postID, replyID, myID, userID, handle, notification, "reply")
        } else {
             misc.getHandle(myID) { myHandle ->
                 handle = myHandle
                 if (handle == "error") {
                     notification = "You have a new follower! :)"
                 } else {
                     notification = "@$handle has added you! :)"
                     val editor = sharedPreferences.edit()
                     editor.putString("handle.flocal", handle)
                     editor.apply()
                 }
                 setNotification(postID, replyID, myID, userID, handle, notification, "reply")
             }
        }
    }

    fun setNotification(postID: String, replyID: String, myID: String, userID: String, handle: String, notification: String, type: String) {
        val timestamp = misc.getTimestamp("UTC", Date())
        val originalReverseTimestamp = misc.getCurrentReverseTimestamp()

        val not: MutableMap<String,Any> = mutableMapOf("postID" to postID, "replyID" to replyID, "userID" to myID, "handle" to handle,
                "type" to type, "timestamp" to timestamp, "originalReverseTimestamp" to originalReverseTimestamp, "notification" to notification)
        ref.child("userNotifications").child(userID).push().setValue(not)
        misc.addNotificationBadge(userID)
    }

    fun edit(index: Int, replyID: String?, newContent: String) {
        val timestamp = misc.getTimestamp("UTC", Date())
        val formattedTimestamp = misc.formatTimestamp(timestamp)

        val postRef: DatabaseReference
        if (index != 0 && replyID != null) {
            postRef = ref.child("replies").child(postID).child(replyID)
            val replies = determineReplies()
            replies[index - 2].content
            replies[index - 2].timestamp = "edited $formattedTimestamp"
        } else {
            postRef = ref.child("posts").child(postID)
            parentPost.content = newContent
            parentPost.timestamp = "edited $formattedTimestamp"
        }
        postRef.child("content").setValue(newContent)
        postRef.child("isEdited").setValue(true)
        postRef.child("timestamp").setValue(timestamp)

        logEdited(replyID)
        refreshRecycler()
    }

    fun deletedPostFromSettings() {
        parentPost.content = "[deleted]"
        refreshRecycler()

        val postRef = ref.child("posts").child(postID)
        postRef.child("isDeleted").setValue(true)
    }

    fun deletePost(replyID: String?, index: Int) {
        if (replyID != null) {
            determineReplies()[index].content = "[deleted]"
            refreshRecycler()

            val replyRef = ref.child("replies").child(postID).child(replyID)
            replyRef.child("isDeleted").setValue(true)
            val postRef = ref.child("posts").child(postID)
            postRef.runTransaction( object: Transaction.Handler {
                override fun doTransaction(data: MutableData?): Transaction.Result {
                    val post = data?.value as? MutableMap<String,Any> ?: return Transaction.success(data)
                    var replyCount = post["replyCount"] as? Int ?: 0
                    replyCount -= 1
                    post["replyCount"] = replyCount
                    data.value = post
                    return Transaction.success(data)
                }
                override fun onComplete(p0: DatabaseError?, p1: Boolean, p2: DataSnapshot?) {}
            })
        } else {
            deletedPostFromSettings()
        }
    }

    fun formatReply(replyID: String, voteStatus: String, reply: MutableMap<String,Any>): Reply {
        val formattedReply = Reply()

        formattedReply.replyID = replyID
        formattedReply.userID = reply["userID"] as? String ?: "error"

        val profilePicURLString = reply["profilePicURLString"] as? String ?: "error"
        if (profilePicURLString != "error") {
            formattedReply.profilePicURL = Uri.parse(profilePicURLString)
        }

        formattedReply.handle = reply["handle"] as? String ?: "error"
        formattedReply.content = reply["content"] as? String ?: "error"

        val timestamp = reply["timestamp"] as? String ?: "error"
        val isEdited = reply["isEdited"] as? Boolean ?: false
        val formattedTimestamp = misc.formatTimestamp(timestamp)
        if (isEdited) {
            formattedReply.timestamp = "edited $formattedTimestamp"
        } else {
            formattedReply.timestamp = formattedTimestamp
        }
        formattedReply.originalReverseTimestamp = reply["originalReverseTimestamp"] as? Double ?: 0.0

        val upvotes = reply["upvotes"] as? Int ?: 0
        val downvotes = reply["downvotes"] as? Int ?: 0
        formattedReply.points = upvotes - downvotes
        formattedReply.score = reply["score"] as? Double ?: 0.0

        formattedReply.voteStatus = voteStatus

        return formattedReply
    }

    fun observeBlocked() {
        blockedValueListener = object: ValueEventListener {
            override fun onDataChange(snap: DataSnapshot?) {
                val dict = snap?.value as? MutableMap<String,Any> ?: mutableMapOf()
                val blockedByDict = dict["blockedBy"] as? MutableMap<String,Boolean> ?: mutableMapOf()
                blockedBy = blockedByDict.keys.toMutableList()
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
