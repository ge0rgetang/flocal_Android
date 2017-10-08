@file:Suppress("UNCHECKED_CAST", "MemberVisibilityCanPrivate", "CascadeIf", "LiftReturnOrAssignment", "UnnecessaryVariable", "SetTextI18n")

package com.dotnative.flocal

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.support.v4.app.Fragment
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.*
import android.widget.*
import com.baoyz.actionsheet.ActionSheet
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import kotlinx.android.synthetic.main.activity_main.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.*

class ChatFragment : Fragment() {

    // MARK: - Layout

    lateinit var layoutManager: LinearLayoutManager
    lateinit var recyclerView: RecyclerView
    lateinit var adapter: ChatAdapter
    lateinit var typingImageView: ImageView
    lateinit var cameraImageView: ImageView
    lateinit var sendImageView: ImageView
    lateinit var editText: EditText

    // MARK: - Vars

    private var chatInteractionListener: ChatInteractionListener? = null

    var myID: String = "0"
    var userID: String = "0"
    var chatID: String = "0"
    var parentSource: String = "default"

    var profilePicURL: Uri? = null
    var myProfilePicURL: Uri? = null
    var chatPicURL: Uri? = null
    var chatVidURL: Uri? = null
    var chatVidPreviewURL: Uri? = null

    var amIBlocked: Boolean = false
    var blockedValueListener: ValueEventListener? = null

    var messages: MutableList<Chat> = mutableListOf()
    var typingValueListener: ValueEventListener? = null
    var messageValueListener: ValueEventListener? = null

    var firstLoad: Boolean = true
    var scrollPosition: String = "top"
    var displayProgress: Boolean = false

    var ref: DatabaseReference = FirebaseDatabase.getInstance().reference
    val storageRef: StorageReference = FirebaseStorage.getInstance().reference
    var analytics: FirebaseAnalytics? = null

    val misc = Misc()
    var popupContainer: ViewGroup? = null
    var popupView: View? = null

    // MARK: - Lifecycle

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context is ChatInteractionListener) {
            chatInteractionListener = context
        } else {
            throw RuntimeException(context!!.toString() + " must implement OnFragmentInteractionListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        setArguments()
        analytics = FirebaseAnalytics.getInstance(context)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view =  inflater!!.inflate(R.layout.fragment_chat, container, false)
        popupContainer = container
        popupView = view
        setLayout(view)
        return view
    }

    override fun onStart() {
        super.onStart()
        firstLoad = true
        showTyping(false)
        chatInteractionListener?.setUserIDFromChat(userID)

        if (chatID == "0") {
            chatInteractionListener?.turnToFragmentFromChat("Chat List")
        }

        myID = misc.setMyID(context)
        if (myID == "0") {
            chatInteractionListener?.turnToFragmentFromChat("Login")
        } else {
            logViewChat()
            if (parentSource != "chatList") {
                misc.setSideMenuIndex(context, 42)
            }
            misc.writeAmIInChat(true, chatID, myID)
            downloadProfilePicURL()
            downloadMyProfilePicURL()
            observeChat()
            observeBlocked()
        }
    }

    override fun onStop() {
        super.onStop()
        misc.writeAmIInChat(false, chatID, myID)
        misc.writeAmITyping(false, chatID, myID)
        removeObserverForChat()
        removeObserverForBlocked()
    }

    override fun onDetach() {
        super.onDetach()
        chatInteractionListener = null
        removeObserverForChat()
        removeObserverForBlocked()
    }

    // MARK: - Navigation

    interface ChatInteractionListener {
        fun setUserIDFromChat(userID: String)
        fun turnToFragmentFromChat(name: String)
        fun dismissKeyboardFromChat()
    }

    fun setArguments() {
        val arguments = arguments
        chatID = arguments.getString("chatIDToPass", "0")
        userID = arguments.getString("userIDToPass", "0")
        parentSource = arguments.getString("chatParentSource", "default")

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (chatID == "0") {
            chatID = sharedPreferences.getString("chatIDToPass.flocal", "0")
        }
        if (userID == "0") {
            chatID = sharedPreferences.getString("userIDToPass.flocal", "0")
        }
        if (parentSource == "default") {
            parentSource = sharedPreferences.getString("chatParentSource.flocal", "default")
        }
    }

    fun setLayout(view: View) {
        layoutManager = LinearLayoutManager(context)
        layoutManager.stackFromEnd = true
        recyclerView = view.findViewById(R.id.chatListRecyclerView)
        adapter = ChatAdapter(context, messages, profilePicURL, chatID, displayProgress)
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)
        val divider = DividerItemDecoration(recyclerView.context, 1)
        recyclerView.addItemDecoration(divider)
        recyclerView.addOnScrollListener(scrollListener)

        typingImageView = view.findViewById(R.id.typingImageView)

        cameraImageView = view.findViewById(R.id.cameraImageView)
        cameraImageView.setOnClickListener { selectPicSource() }
        editText = view.findViewById(R.id.chatEditText)
        sendImageView = view.findViewById(R.id.sendImageView)
        sendImageView.setOnClickListener { writeMessage("text", null, null) }
    }

    // MARK: - Popup

    fun openSendImage(bitmap: Bitmap) {
        val layoutInflater: LayoutInflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val viewGroup: ViewGroup = activity.findViewById(android.R.id.content)
        val inflatedView: View = layoutInflater.inflate(R.layout.popup_send_image, viewGroup, false)

        val popupWindow = PopupWindow(inflatedView, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, true)

        val xButton: Button = inflatedView.findViewById(R.id.xButton)
        xButton.setOnClickListener {
            popupWindow.dismiss()
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        val imageView: ImageView = inflatedView.findViewById(R.id.imageView)
        imageView.setImageBitmap(bitmap)
        imageView.setOnClickListener {
            val sheet = ActionSheet.createBuilder(context, fragmentManager)
            sheet.setCancelButtonTitle("Cancel")
            sheet.setCancelableOnTouchOutside(true)
            sheet.setOtherButtonTitles("Re-take Pic with Camera", "Choose other from Photo Library")
            sheet.setListener( object: ActionSheet.ActionSheetListener {
                override fun onOtherButtonClick(actionSheet: ActionSheet?, index: Int) {
                    when (index) {
                        0 -> {
                            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            popupWindow.dismiss()
                            chooseCamera()
                        }
                        1 -> {
                            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            popupWindow.dismiss()
                            choosePhotoLibrary()
                        }
                        else -> {
                            actionSheet?.dismiss()
                        }
                    }
                }
                override fun onDismiss(actionSheet: ActionSheet?, isCancel: Boolean) {}
            })
            sheet.show()
        }

        val progressBar: ProgressBar = inflatedView.findViewById(R.id.progressBar)
        progressBar.visibility = ProgressBar.INVISIBLE

        val sendImageView: ImageView = inflatedView.findViewById(R.id.sendImageView)
        sendImageView.visibility = ImageView.VISIBLE
        sendImageView.setOnClickListener {
            progressBar.visibility = ImageView.VISIBLE
            sendImageView.visibility = ImageView.INVISIBLE
            writeMessage("image", bitmap, null)
            popupWindow.dismiss()
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        logViewSendChatImageVideo("image")

        val height = bitmap.height
        val width = bitmap.width
        if (width > height) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        popupWindow.showAtLocation(activity.drawer_layout, Gravity.CENTER, 0,0)
    }

    fun openSendVideo(uri: Uri, previewBitmap: Bitmap) {
        val layoutInflater: LayoutInflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val viewGroup: ViewGroup = activity.findViewById(android.R.id.content)
        val inflatedView: View = layoutInflater.inflate(R.layout.popup_send_video, viewGroup, false)

        val popupWindow = PopupWindow(inflatedView, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, true)

        val xButton: Button = inflatedView.findViewById(R.id.xButton)
        xButton.setOnClickListener {
            popupWindow.dismiss()
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        val videoView: VideoView = inflatedView.findViewById(R.id.videoView)
        videoView.setVideoURI(uri)
        videoView.setOnClickListener {
            val sheet = ActionSheet.createBuilder(context, fragmentManager)
            sheet.setCancelButtonTitle("Cancel")
            sheet.setCancelableOnTouchOutside(true)
            sheet.setOtherButtonTitles("Re-record with Camera")
            sheet.setListener( object: ActionSheet.ActionSheetListener {
                override fun onOtherButtonClick(actionSheet: ActionSheet?, index: Int) {
                    when (index) {
                        0 -> {
                            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            popupWindow.dismiss()
                            chooseVideo()
                        }
                        else -> {
                            actionSheet?.dismiss()
                        }
                    }
                }
                override fun onDismiss(actionSheet: ActionSheet?, isCancel: Boolean) {}
            })
            sheet.show()
        }
        videoView.setOnPreparedListener {mediaPlayer ->
            mediaPlayer.isLooping = true
        }

        val progressBar: ProgressBar = inflatedView.findViewById(R.id.progressBar)
        progressBar.visibility = ProgressBar.INVISIBLE

        val sendImageView: ImageView = inflatedView.findViewById(R.id.sendImageView)
        sendImageView.visibility = ImageView.VISIBLE
        sendImageView.setOnClickListener {
            progressBar.visibility = ImageView.VISIBLE
            sendImageView.visibility = ImageView.INVISIBLE
            writeMessage("video", previewBitmap, uri)
            popupWindow.dismiss()
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        logViewSendChatImageVideo("video")

        val height = previewBitmap.height
        val width = previewBitmap.width
        if (width > height) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        popupWindow.showAtLocation(activity.drawer_layout, Gravity.CENTER, 0,0)
        videoView.start()
    }

    // MARK: - Camera

    fun selectPicSource() {
        val sheet = ActionSheet.createBuilder(context, fragmentManager)
        sheet.setCancelButtonTitle("Cancel")
        sheet.setCancelableOnTouchOutside(true)
        sheet.setOtherButtonTitles("Take a Picture", "Choose from Photo Library", "Record Video")
        sheet.setListener( object: ActionSheet.ActionSheetListener {
            override fun onOtherButtonClick(actionSheet: ActionSheet?, index: Int) {
                when (index) {
                    0 -> { chooseCamera() }
                    1 -> { choosePhotoLibrary() }
                    2 -> { chooseVideo() }
                    else -> { actionSheet?.dismiss() }
                }
            }
            override fun onDismiss(actionSheet: ActionSheet?, isCancel: Boolean) {}
        })
        misc.playSound(context, R.raw.button_click, 0)
        sheet.show()
    }

    fun chooseCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, 0)
    }

    fun choosePhotoLibrary() {
        val intent = Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, 1)
    }

    fun chooseVideo() {
        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
        intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 10)
        val file = File(Environment.getExternalStorageDirectory().absolutePath + ".mp4")
        val uri = Uri.fromFile(file)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
        startActivityForResult(intent, 2)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
        if (data != null) {
            when (resultCode) {
                0 -> {
                    val bitmap: Bitmap = data.extras.get("data") as Bitmap
                    openSendImage(bitmap)
                }
                1 -> {
                    val uri = data.data
                    try {
                        val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                        openSendImage(bitmap)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                2 -> {
                    val uri = data.data
                    try {
                        val file = File(uri.path)
                        val path = file.absolutePath
                        val mediaRetriever = MediaMetadataRetriever()
                        mediaRetriever.setDataSource(path)
                        val thumbnail = mediaRetriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST)
//                        val thumbnail = ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND)
                        openSendVideo(uri, thumbnail)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                else -> {
                    alert("Error", "We encountered an error with the camera. Please report this bug.")
                    return
                }
            }
        }
    }

    // MARK: - Scroll

    val scrollListener = object: RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)

            val firstVisibleItem = layoutManager.findFirstCompletelyVisibleItemPosition()
            val lastVisibleItem = layoutManager.findLastCompletelyVisibleItemPosition()
            if (firstVisibleItem <= 0) {
                scrollPosition = "top"
            } else if (lastVisibleItem == messages.size - 1) {
                scrollPosition = "bottom"
                if (messages.size > 8) {
                    displayProgress = true
                    refreshRecycler()
                    recyclerView?.scrollToPosition(messages.size)
                    observeChat()
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

    fun showTyping(bool: Boolean) {
        val anim = AnimatorSet()
        if (bool) {
            val h = resources.getDimension(R.dimen.typingHeight)
            val w = resources.getDimension(R.dimen.typingWidth)
            val scaleUpY = ObjectAnimator.ofFloat(typingImageView, "scaleY", h)
            scaleUpY.duration = 1000
            anim.play(scaleUpY)
            scaleUpY.start()
            typingImageView.layoutParams.height = h.toInt()
            typingImageView.layoutParams.width = w.toInt()
            typingImageView.requestLayout()
        } else {
            val scaleDownY = ObjectAnimator.ofFloat(typingImageView, "scaleY", 0f)
            scaleDownY.duration = 1000
            anim.play(scaleDownY)
            scaleDownY.start()
            typingImageView.layoutParams.height = 0
            typingImageView.layoutParams.width = 0
            typingImageView.requestLayout()
        }
    }

    fun alert(title: String, message: String) {
        misc.displayAlert(context, title, message)
    }

    fun refreshRecycler() {
        adapter = ChatAdapter(context, messages, profilePicURL, chatID, displayProgress)
        recyclerView.adapter = adapter
        recyclerView.adapter.notifyDataSetChanged()
    }

    // MARK: - Analytics

    fun logViewChat() {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        bundle.putString("userID", userID)
        bundle.putString("chatID", chatID)
        analytics?.logEvent("viewChat_Android", bundle)
    }

    fun logChatTextSent() {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        bundle.putString("userID", userID)
        bundle.putString("chatID", chatID)
        analytics?.logEvent("sentChatText_Android", bundle)
    }

    fun logViewSendChatImageVideo(type: String) {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        bundle.putString("userID", userID)
        bundle.putString("chatID", chatID)
        val t = type.capitalize()
        val child = "viewSend$t" + "FromChat" + "_Android"
        analytics?.logEvent(child, bundle)
    }

    fun logChatImageVideoSent(type: String) {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        bundle.putString("userID", userID)
        bundle.putString("chatID", chatID)
        val t = type.capitalize()
        val child = "sentChat$t" + "_Android"
        analytics?.logEvent(child, bundle)
    }

    // MARK: - Storage

    fun downloadProfilePicURL() {
        val child = "profilePic/$userID" + "_large.jpg"
        val profilePicRef = storageRef.child(child)
        profilePicRef.downloadUrl.addOnSuccessListener { uri ->
            profilePicURL = uri
        }.addOnFailureListener { error ->
            Log.d("downloadError", error.toString())
            profilePicURL = null
        }
    }

    fun downloadMyProfilePicURL() {
        val child = "profilePic/$myID" + "_large.jpg"
        val myProfilePicRef = storageRef.child(child)
        myProfilePicRef.downloadUrl.addOnSuccessListener { uri ->
            myProfilePicURL = uri
        }.addOnFailureListener { error ->
            Log.d("downloadError", error.toString())
            myProfilePicURL = null
        }
    }

    fun uploadPic(bitmap: Bitmap, messageID: String) {
        val sourceWidth = bitmap.width
        val sourceHeight = bitmap.height

        val scaleFactor: Int
        if (sourceWidth > sourceHeight) {
            scaleFactor = 1280/sourceWidth
        } else {
            scaleFactor = 1280/sourceHeight
        }

        val newWidth = scaleFactor*sourceWidth
        val newHeight = scaleFactor*sourceHeight
        val bitmapSized = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)

        val baos = ByteArrayOutputStream()
        bitmapSized.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val data = baos.toByteArray()

        val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
        val chatPicRef = storageRef.child("chatPic/$chatID/$messageID.jpg")

        val uploadTask = chatPicRef.putBytes(data, metadata)
        uploadTask.addOnFailureListener { error ->
            alert("Upload Error", "Your chat pic may not have been uploaded. " +
                    "Please try again or report the bug if it persists.")
            Log.d("uploadError", error.toString())
        }
        uploadTask.addOnSuccessListener { taskSnap ->
            chatPicURL = taskSnap.downloadUrl
            setChat(messageID, "image")
        }
    }

    fun uploadVidPreview(bitmap: Bitmap, messageID: String) {
        val sourceWidth = bitmap.width
        val sourceHeight = bitmap.height

        val scaleFactor: Int
        if (sourceWidth > sourceHeight) {
            scaleFactor = 1280/sourceWidth
        } else {
            scaleFactor = 1280/sourceHeight
        }

        val newWidth = scaleFactor*sourceWidth
        val newHeight = scaleFactor*sourceHeight
        val bitmapSized = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)

        val baos = ByteArrayOutputStream()
        bitmapSized.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val data = baos.toByteArray()

        val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
        val chatVidRef = storageRef.child("chatVidPreview/$chatID/$messageID.jpg")

        val uploadTask = chatVidRef.putBytes(data, metadata)
        uploadTask.addOnFailureListener { error -> Log.d("uploadError", error.toString()) }
        uploadTask.addOnSuccessListener { taskSnap ->
            chatVidPreviewURL = taskSnap.downloadUrl
            Log.d("uploadSuccess", "chatVidPreview")
        }
    }

    fun uploadVid(uri: Uri, messageID: String) {
        val metadata = StorageMetadata.Builder().setContentType("video/mp4").build()
        val chatVidRef = storageRef.child("chatVid/$chatID/$messageID.mp4")

        val uploadTask = chatVidRef.putFile(uri, metadata)
        uploadTask.addOnFailureListener { error ->
            alert("Upload Error", "Your chat vid may not have been uploaded. " +
                    "Please try again or report the bug if it persists.")
            Log.d("uploadError", error.toString())
        }
        uploadTask.addOnSuccessListener { taskSnap ->
            chatVidURL = taskSnap.downloadUrl
            setChat(messageID, "video")
        }
    }

    // MARK: - Firebase

    fun writeMessage(type: String, bitmap: Bitmap?, uri: Uri?) {
        if (amIBlocked) {
            alert("Blocked", "You cannot send messages to this person.")
            return
        }

        val messageRef = ref.child("chats").child(chatID).child("messages").push()
        val messageID = messageRef.key
        when (type) {
            "image" -> {
                if (bitmap != null) {
                    uploadPic(bitmap, messageID)
                } else {
                    alert("Bitmap Error", "We encountered an error converting your pic. Please report this bug if it persists.")
                    return
                }
            }
            "video" -> {
                if (uri != null) {
                    if (bitmap != null) {
                        uploadVidPreview(bitmap, messageID)
                    }
                    uploadVid(uri, messageID)
                } else {
                    alert("File Error", "We encountered an error finding your vid. Please report this bug if it persists.")
                    return
                }
            }
            "else" -> {
                setChat(messageID, type)
            }
        }
    }

    fun setChat(messageID: String, type: String) {
        chatInteractionListener?.dismissKeyboardFromChat()

        val chatRef = ref.child("chats").child(chatID)
        val messageRef = chatRef.child("messages").child(messageID)

        var message = type
        var chatPicURLString = "n/a"
        var chatVidURLString = "n/a"
        var chatVidPreviewURLString = "n/a"
        when (type) {
            "image" -> {
                if (chatPicURL != null) {
                    chatPicURLString = chatPicURL.toString()
                }
            }
            "video" -> {
                if (chatVidURL != null) {
                    chatVidURLString = chatVidURL.toString()
                }

                if (chatVidPreviewURL != null) {
                    chatVidPreviewURLString = chatVidPreviewURL.toString()
                }
            }
            else -> {
                val text = editText.text.toString()
                if (text == "") {
                    alert("Empty Text", "Please type in text to send")
                    return
                } else {
                    message = text
                }
            }
        }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        var handle = sharedPreferences.getString("handle.flocal", "error")
        if (handle == "error") {
            misc.getHandle(myID) {myHandle ->
                handle = myHandle
                val editor = sharedPreferences.edit()
                editor.putString("handle.flocal", handle)
                editor.apply()
            }
        }

        val timestamp = misc.getTimestamp("UTC", Date())
        val originalReverseTimestamp = misc.getCurrentReverseTimestamp()
        val originalTimestamp = -1*originalReverseTimestamp

        misc.playSound(context, R.raw.sent_chat, 0)
        val formattedMessage = Chat()
        formattedMessage.chatID = chatID
        formattedMessage.messageID = messageID
        formattedMessage.userID = myID
        formattedMessage.handle = handle
        formattedMessage.timestamp = timestamp
        formattedMessage.originalReverseTimestamp = originalReverseTimestamp
        formattedMessage.originalTimestamp = originalTimestamp
        formattedMessage.message = message
        formattedMessage.type = type
        formattedMessage.chatPicURL = chatPicURL
        formattedMessage.chatVidURL = chatVidURL
        formattedMessage.chatVidPreviewURL = chatVidPreviewURL
        messages.add(0, formattedMessage)
        refreshRecycler()

        val chat: MutableMap<String,Any> = mutableMapOf("userID" to myID, "handle" to handle, "timestamp" to timestamp,
                "originalReverseTimestamp" to originalReverseTimestamp, "message" to message, "type" to type,
                "chatPicURLString" to chatPicURLString, "chatVidURLString" to chatVidURLString, "chatVidPreviewURLString" to chatVidPreviewURLString)

        if (userID != "0") {
            messageRef.setValue(chat)
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
                        misc.writeChatNotification(context, userID, myID, message, type)
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
            chat["handle"] = handle
            myChatListRef.setValue(chat)
            chatPicURL = null
            chatVidURL = null
            chatVidPreviewURL = null

            when (type) {
                "image", "video" -> {
                    logChatImageVideoSent(type)
                }
                else -> {
                    editText.setText("")
                    editText.clearFocus()
                    logChatTextSent()
                }
            }

        } else {
            alert("Message Error", "We encountered an error trying to send your message. Please report the bug.")
            return
        }
    }

    fun observeChat() {
        removeObserverForChat()

        val reverseTimestamp: Double
        val currentReverseTimestamp = misc.getCurrentReverseTimestamp()
        val lastReverseTimestamp = messages.lastOrNull()?.originalReverseTimestamp
        val lastMessageID = messages.lastOrNull()?.messageID

        if (scrollPosition == "bottom") {
            reverseTimestamp = lastReverseTimestamp ?: currentReverseTimestamp
        } else {
            reverseTimestamp = currentReverseTimestamp
        }

        val chatRef = ref.child("chats").child(chatID)

        messageValueListener = object: ValueEventListener {
            override fun onDataChange(snap: DataSnapshot?) {
                val messagesList: MutableList<Chat> = mutableListOf()

                val dict = snap?.value as? MutableMap<String,Any> ?: mutableMapOf()
                for ((key,value) in dict) {
                    val messageID = key
                    val message = value as? MutableMap<String,Any> ?: mutableMapOf()
                    val formattedMessage = formatChat(messageID, message)
                    messagesList.add(formattedMessage)
                }

                if (scrollPosition == "bottom") {
                    if (lastMessageID != messagesList.lastOrNull()?.messageID) {
                        messages.addAll(messagesList)
                    }
                } else {
                    messages = messagesList
                    if (firstLoad) {
                        firstLoad = false
                    } else {
                        if (myID != messagesList.lastOrNull()?.userID) {
                            misc.playSound(context, R.raw.received_chat, 0)
                        }
                    }
                }
                displayProgress = false
                refreshRecycler()
            }
            override fun onCancelled(error: DatabaseError?) { Log.d("databaseError", error.toString()) }
        }
        val messageRef = chatRef.child("messages")
        messageRef.orderByChild("originalReverseTimestamp").startAt(reverseTimestamp).limitToFirst(88)
                .addValueEventListener(messageValueListener)

        typingValueListener = object: ValueEventListener {
            override fun onDataChange(snap: DataSnapshot?) {
                val isUserTyping = snap?.value as? Boolean ?: false
                showTyping(isUserTyping)
            }
            override fun onCancelled(error: DatabaseError?) { Log.d("databaseError", error.toString()) }
        }
        val typingRef = chatRef.child("info").child(userID + "_typing")
        typingRef.addValueEventListener(typingValueListener)
    }

    fun removeObserverForChat() {
        val chatRef = ref.child("chats").child(chatID)

        val messageRef = chatRef.child("messages")
        if (messageValueListener != null) {
            messageRef.removeEventListener(messageValueListener)
            messageValueListener = null
        }

        val typingRef = chatRef.child("info").child(userID + "_typing")
        if (typingValueListener != null) {
            typingRef.removeEventListener(typingValueListener)
            typingValueListener = null
        }
    }

    fun formatChat(messageID: String, chat: MutableMap<String,Any>): Chat {
        val formattedMessage = Chat()

        formattedMessage.chatID = chatID
        formattedMessage.messageID = messageID
        formattedMessage.userID = chat["userID"] as? String ?: "error"
        formattedMessage.handle = chat["handle"] as? String ?: "error"

        val type = chat["type"] as? String ?: "error"
        formattedMessage.type = type
        if (type != "image" && type != "video") {
            formattedMessage.message = chat["message"] as? String ?: "error"
        }

        val chatPicURLString = chat["chatPicURLString"] as? String ?: "error"
        if (chatPicURLString != "error") {
            formattedMessage.chatPicURL = Uri.parse(chatPicURLString)
        }

        val chatVidURLString = chat["chatVidURLString"] as? String ?: "error"
        if (chatVidURLString != "error") {
            formattedMessage.chatVidURL = Uri.parse(chatVidURLString)
        }

        val chatVidPreviewURLString = chat["chatVidPreviewURLString"] as? String ?: "error"
        if (chatVidPreviewURLString == "error") {
            formattedMessage.chatVidPreviewURL = Uri.parse(chatPicURLString)
        }

        val timestamp = chat["timestamp"] as? String ?: "error"
        formattedMessage.timestamp = misc.formatTimestamp(timestamp)
        val originalReverseTimestamp = chat["originalReverseTimestamp"] as? Double ?: 0.0
        formattedMessage.originalReverseTimestamp = originalReverseTimestamp
        formattedMessage.originalTimestamp = -1*originalReverseTimestamp

        return formattedMessage
    }

    fun observeBlocked() {
        blockedValueListener = object: ValueEventListener {
            override fun onDataChange(snap: DataSnapshot?) {
                val dict = snap?.value as? MutableMap<String,Any> ?: mutableMapOf()
                val blockedByDict = dict["blockedBy"] as? MutableMap<String,Boolean> ?: mutableMapOf()
                val blockedBy = blockedByDict.keys.toMutableList()
                amIBlocked = false
                if (blockedBy.contains(userID)) {
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
