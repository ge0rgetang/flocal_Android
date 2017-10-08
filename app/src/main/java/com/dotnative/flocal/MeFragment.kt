@file:Suppress("UNCHECKED_CAST", "MemberVisibilityCanPrivate", "CascadeIf", "LiftReturnOrAssignment", "UnnecessaryVariable", "SetTextI18n")

package com.dotnative.flocal

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Point
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.app.Fragment
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.widget.*
import com.baoyz.actionsheet.ActionSheet
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import com.squareup.picasso.Picasso
import de.hdodenhof.circleimageview.CircleImageView
import java.io.ByteArrayOutputStream
import java.io.IOException

class MeFragment : Fragment() {

    // MARK: - Layout

    lateinit var addBackgroundTextView: TextView
    lateinit var backgroundPicImageView: ImageView
    lateinit var profilePicImageView: CircleImageView
    lateinit var handleTextView: TextView
    lateinit var pointsTextView: TextView
    lateinit var followersTextView: TextView
    lateinit var descriptionTextView: TextView
    lateinit var privateInfoTextView: TextView
    lateinit var emailTextView: TextView
    lateinit var nameTextView: TextView
    lateinit var phoneTextView: TextView
    lateinit var birthdayTextView: TextView

    // MARK: - Vars

    private var meInteractionListener: MeInteractionListener? = null

    var myID: String = "0"

    var popupContainer: ViewGroup? = null
    var popupView: View? = null

    var ref: DatabaseReference = FirebaseDatabase.getInstance().reference
    val storageRef: StorageReference = FirebaseStorage.getInstance().reference
    var analytics: FirebaseAnalytics? = null
    val auth: FirebaseAuth = FirebaseAuth.getInstance()

    var meValueListener: ValueEventListener? = null

    val misc = Misc()

    // MARK: - Lifecycle

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context is MeInteractionListener) {
            meInteractionListener = context
        } else {
            throw RuntimeException(context!!.toString() + " must implement OnFragmentInteractionListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        analytics = FirebaseAnalytics.getInstance(context)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view =  inflater!!.inflate(R.layout.fragment_me, container, false)
        popupContainer = container
        popupView = view
        setLayout(view)
        return view
    }

    override fun onStart() {
        super.onStart()

        myID = misc.setMyID(context)
        if (myID == "0") {
            meInteractionListener?.turnToFragmentFromMe("Login")
        } else {
            logViewMe()
            misc.setSideMenuIndex(context, 3)
            downloadProfilePic()
            downloadBackgroundPic()
            observeMe()
        }
    }

    override fun onStop() {
        super.onStop()
        removeObserverForMe()
    }

    override fun onDetach() {
        super.onDetach()
        meInteractionListener = null
        removeObserverForMe()
    }

    // MARK: - Navigation

    interface MeInteractionListener {
        fun turnToFragmentFromMe(name: String)
        fun dismissKeyboardMe()
    }

    fun setLayout(view: View) {
        addBackgroundTextView = view.findViewById(R.id.addBackGroundTextView)
        addBackgroundTextView.visibility = TextView.INVISIBLE

        backgroundPicImageView = view.findViewById(R.id.backgroundPicImageView)
        backgroundPicImageView.setOnClickListener { selectBackgroundPicSource() }

        profilePicImageView = view.findViewById(R.id.profilePicImageView)
        profilePicImageView.setOnClickListener { selectPicSource() }

        handleTextView = view.findViewById(R.id.handleTextView)
        handleTextView.setOnClickListener { showEdit("handle", handleTextView.text.toString()) }

        pointsTextView = view.findViewById(R.id.pointsTextView)
        followersTextView = view.findViewById(R.id.followersTextView)

        descriptionTextView = view.findViewById(R.id.descriptionTextView)
        descriptionTextView.setOnClickListener { showEdit("description", descriptionTextView.text.toString()) }

        privateInfoTextView = view.findViewById(R.id.privateInfoTextView)

        emailTextView = view.findViewById(R.id.emailTextView)
        emailTextView.setOnClickListener { showEditLogin(emailTextView.text.toString()) }

        nameTextView = view.findViewById(R.id.nameTextView)
        nameTextView.setOnClickListener { showEdit("name", nameTextView.text.toString()) }

        phoneTextView = view.findViewById(R.id.phoneTextView)
        phoneTextView.setOnClickListener { showEdit("phone", phoneTextView.text.toString()) }

        birthdayTextView = view.findViewById(R.id.birthdayTextView)
        birthdayTextView.setOnClickListener { showEdit("birthday", birthdayTextView.text.toString()) }
    }

    @SuppressLint("SetTextI18n")
    fun showEdit(type: String, currentText: String) {
        val layoutInflater: LayoutInflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val inflatedView: View = layoutInflater.inflate(R.layout.popup_edit, popupContainer, false)

        val display = activity.windowManager.defaultDisplay
        val size = Point()
        display.getSize(size)
        val width = size.x

        val popupWindow = PopupWindow(inflatedView, width - 50, WindowManager.LayoutParams.WRAP_CONTENT, true)
        popupWindow.isFocusable = true
        popupWindow.isOutsideTouchable = true

        val titleTextView: TextView = inflatedView.findViewById(R.id.titleTextView)

        val characterCountTextView: TextView = inflatedView.findViewById(R.id.characterCountTextView)
        val currentLength = currentText.length
        characterCountTextView.text = "$currentLength/255"

        val editText: EditText = inflatedView.findViewById(R.id.editText)
        editText.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                when (type) {
                    "description" -> {
                        if (currentText.toLowerCase() == "no name set") {
                            editText.setText("")
                        }
                    }
                    "name" -> {
                        if (currentText.toLowerCase() == "no description set") {
                            editText.setText("")
                        }
                    }
                    "phone" -> {
                        if (currentText.toLowerCase() == "no phone set") {
                            editText.setText("")
                        }
                    }
                    "birthday" -> {
                        if (currentText.toLowerCase() == "no bday set") {
                            editText.setText("")
                        }
                    }
                }
            }
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(text: Editable?) {
                val length = text.toString().length
                characterCountTextView.text = "$length/255"
            }
        })

        val confirmButton: Button = inflatedView.findViewById(R.id.confirmButton)
        confirmButton.setOnClickListener { editInfo(type, editText.text.toString().trim(), popupWindow) }

        if (type == "handle") {
            editText.filters = arrayOf(InputFilter.LengthFilter(15))
        } else {
            editText.filters = arrayOf(InputFilter.LengthFilter(255))
        }

        if (type == "description") {
            characterCountTextView.visibility = TextView.VISIBLE
        } else {
            characterCountTextView.visibility = TextView.INVISIBLE
        }

        if (type == "phone") {
            editText.inputType = InputType.TYPE_CLASS_PHONE
        } else {
            editText.inputType = InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }

        when (type) {
            "handle" -> {
                titleTextView.setText(R.string.editHandle)
                editText.maxLines = 1
                editText.gravity = Gravity.CENTER
            }
            "description" -> {
                titleTextView.setText(R.string.editDescription)
                editText.maxLines = 1000
                editText.gravity = Gravity.START
            }
            "name" -> {
                titleTextView.setText(R.string.editName)
                editText.maxLines = 1
                editText.gravity = Gravity.CENTER
            }
            "phone" -> {
                titleTextView.setText(R.string.editPhone)
                editText.maxLines = 1
                editText.gravity = Gravity.CENTER
            }
            "birthday" -> {
                titleTextView.setText(R.string.editBirthday)
                editText.maxLines = 1
                editText.gravity = Gravity.CENTER
            }
            else -> {
                alert("Error", "We encountered an error and can't edit right now. Please report this bug.")
                return
            }
        }

        logViewEdit(type)
        popupWindow.showAtLocation(popupView, Gravity.CENTER, 0,0)
    }

    fun showEditLogin(email: String) {
        val layoutInflater: LayoutInflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val inflatedView: View = layoutInflater.inflate(R.layout.popup_edit_login, popupContainer, false)

        val display = activity.windowManager.defaultDisplay
        val size = Point()
        display.getSize(size)
        val width = size.x

        val popupWindow = PopupWindow(inflatedView, width - 50, WindowManager.LayoutParams.WRAP_CONTENT, true)
        popupWindow.isFocusable = true
        popupWindow.isOutsideTouchable = true

        val currentEmailEditText: EditText = inflatedView.findViewById(R.id.currentEmailEditText)
        currentEmailEditText.setText(email)
        val currentPassEditText: EditText = inflatedView.findViewById(R.id.currentPasswordEditText)
        val newEmailEditText: EditText = inflatedView.findViewById(R.id.newEmailEditText)
        val newPassEditText: EditText = inflatedView.findViewById(R.id.newPasswordEditText)

        val confirmButton: Button = inflatedView.findViewById(R.id.confirmButton)
        confirmButton.setOnClickListener {
            val currentEmail = currentEmailEditText.text.toString().trim()
            val currentPass = currentPassEditText.text.toString()
            val newEmail = newEmailEditText.text.toString().trim()
            val newPass = newPassEditText.text.toString()
            authenticate(currentEmail, currentPass, newEmail, newPass, popupWindow)
        }

        logViewEdit("login")
        popupWindow.showAtLocation(popupView, Gravity.CENTER, 0,0)
    }

    // MARK - Camera

    fun selectPicSource() {
        val sheet = ActionSheet.createBuilder(context, fragmentManager)
        sheet.setCancelButtonTitle("Cancel")
        sheet.setCancelableOnTouchOutside(true)
        sheet.setOtherButtonTitles("Take a Selfie!", "Choose from Photo Library")
        sheet.setListener( object: ActionSheet.ActionSheetListener {
            override fun onOtherButtonClick(actionSheet: ActionSheet?, index: Int) {
                when (index) {
                    0 -> {
                        chooseCamera()
                    }
                    1 -> {
                        choosePhotoLibrary("profile")
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

    fun selectBackgroundPicSource() {
        val sheet = ActionSheet.createBuilder(context, fragmentManager)
        sheet.setCancelButtonTitle("Cancel")
        sheet.setCancelableOnTouchOutside(true)
        sheet.setOtherButtonTitles("Choose from Photo Library")
        sheet.setListener( object: ActionSheet.ActionSheetListener {
            override fun onOtherButtonClick(actionSheet: ActionSheet?, index: Int) {
                when (index) {
                    0 -> {
                        choosePhotoLibrary("background")
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

    fun chooseCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra("android.intent.extras.CAMERA_FACING", 1)
        startActivityForResult(intent, 0)
    }

    fun choosePhotoLibrary(type: String) {
        val intent = Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        if (type == "profile") {
            startActivityForResult(intent, 1)
        } else {
            startActivityForResult(intent, 2)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
        if (data != null) {
            when (resultCode) {
                0 -> {
                    val bitmap: Bitmap = data.extras.get("data") as Bitmap
                    profilePicImageView.setImageBitmap(bitmap)
                    logProfPicEdited()
                    profilePicImageView.isDrawingCacheEnabled = true
                    profilePicImageView.buildDrawingCache()
                    val bit = profilePicImageView.drawingCache
                    uploadProfilePic(bit, "large")
                    uploadProfilePic(bit, "small")
                }
                1 -> {
                    val uri = data.data
                    try {
                        val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                        profilePicImageView.setImageBitmap(bitmap)
                        logProfPicEdited()
                        profilePicImageView.isDrawingCacheEnabled = true
                        profilePicImageView.buildDrawingCache()
                        val bit = profilePicImageView.drawingCache
                        uploadProfilePic(bit, "large")
                        uploadProfilePic(bit, "small")
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                2 -> {
                    val uri = data.data
                    try {
                        val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                        backgroundPicImageView.setImageBitmap(bitmap)
                        addBackgroundTextView.visibility = TextView.INVISIBLE
                        logBackgroundPicEdited()
                        backgroundPicImageView.isDrawingCacheEnabled = true
                        backgroundPicImageView.buildDrawingCache()
                        val bit = backgroundPicImageView.drawingCache
                        uploadBackgroundPic(bit)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                else -> {
                    return
                }
            }
        }
    }

    // MARK: Misc

    fun alert(title: String, message: String) {
        misc.displayAlert(context, title, message)
    }

    // MARK: Analytics

    fun logViewMe() {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        analytics?.logEvent("viewMe_Android", bundle)
    }

    fun logProfPicEdited() {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        analytics?.logEvent("editedProfilePic_Android", bundle)
    }

    fun logBackgroundPicEdited() {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        analytics?.logEvent("editedBackgroundPic_Android", bundle)
    }

    fun logViewEdit(type: String) {
        val bundle = Bundle()
        bundle.putString("myID", myID)

        val t = type.capitalize() + "_Android"
        analytics?.logEvent("viewEdit$t", bundle)
    }

    fun logEdited(type: String) {
        val bundle = Bundle()
        bundle.putString("myID", myID)

        val t = type.capitalize() + "_Android"
        analytics?.logEvent("edited$t", bundle)
    }

    // MARK: Storage

    fun downloadProfilePic() {
        val child = myID + "_" +  "large"
        val profilePicRef = storageRef.child("profilePic/$child.jpg")
        profilePicRef.downloadUrl.addOnSuccessListener { uri ->
            Picasso.with(context).load(uri).into(profilePicImageView)
        }.addOnFailureListener { error ->
            Log.d("urlDownloadError", error.toString())
            profilePicImageView.setImageResource(R.drawable.me)
        }
    }

    fun downloadBackgroundPic() {
        val backgroundPicRef = storageRef.child("backgroundPic/$myID.jpg")
        backgroundPicRef.downloadUrl.addOnSuccessListener { uri ->
            addBackgroundTextView.visibility = TextView.INVISIBLE
            Picasso.with(context).load(uri).into(backgroundPicImageView)
        }.addOnFailureListener { error ->
            addBackgroundTextView.visibility = TextView.VISIBLE
            Log.d("urlDownloadError", error.toString())
        }
    }

    fun uploadProfilePic(bitmap: Bitmap, size: String) {
        val sourceWidth = bitmap.width
        val sourceHeight = bitmap.height

        val scaleFactor: Int
        when (size) {
            "small" -> {
                if (sourceWidth > sourceHeight) {
                    scaleFactor = 100/sourceWidth
                } else {
                    scaleFactor = 100/sourceHeight
                }
            }
            else -> {
                if (sourceWidth > sourceHeight) {
                    scaleFactor = 300/sourceWidth
                } else {
                    scaleFactor = 300/sourceHeight
                }
            }
        }

        val newWidth = scaleFactor*sourceWidth
        val newHeight = scaleFactor*sourceHeight
        val bitmapSized = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)

        val baos = ByteArrayOutputStream()
        bitmapSized.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val data = baos.toByteArray()

        val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
        val child = myID + "_" +  size
        val profilePicRef = storageRef.child("profilePic/$child.jpg")

        val uploadTask = profilePicRef.putBytes(data, metadata)
        uploadTask.addOnFailureListener { error ->
            alert("Upload Error", "Your profile pic may not have been uploaded. " +
                    "Please try again or report the bug if it persists.")
            Log.d("uploadError", error.toString())
        }
        uploadTask.addOnSuccessListener { taskSnap ->
            if (size == "large") {
                val downloadURL = taskSnap.downloadUrl
                val urlString = downloadURL.toString()
                ref.child("users").child(myID).child("profilePicURLString").setValue(urlString)
                Picasso.with(context).invalidate(downloadURL)
                Picasso.with(context).load(downloadURL).into(profilePicImageView)
            }
        }
    }

    fun uploadBackgroundPic(bitmap: Bitmap) {
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
        val backgroundPicRef = storageRef.child("backgroundPic/$myID.jpg")

        val uploadTask = backgroundPicRef.putBytes(data, metadata)
        uploadTask.addOnFailureListener { error ->
            alert("Upload Error", "Your background pic may not have been uploaded. " +
                    "Please try again or report the bug if it persists.")
            Log.d("uploadError", error.toString())
        }
        uploadTask.addOnSuccessListener { taskSnap ->
            val downloadURL = taskSnap.downloadUrl
            Picasso.with(context).invalidate(downloadURL)
            Picasso.with(context).load(downloadURL).into(backgroundPicImageView)
        }
    }

    // MARK: Firebase

    fun observeMe() {
        removeObserverForMe()

        meValueListener = object: ValueEventListener {
            @SuppressLint("SetTextI18n")
            override fun onDataChange(snap: DataSnapshot?) {
                val dict = snap?.value as? MutableMap<String,Any> ?: mutableMapOf()

                val postPoints = dict["postPoints"] as? Int ?: 0
                val replyPoints = dict["replyPoints"] as? Int ?: 0
                val points = postPoints + replyPoints
                pointsTextView.text = "$points points"
                pointsTextView.setTextColor(misc.setPointsColor(points, "profile"))

                val followersCount = dict["followersCount"] as? Int ?: 0
                followersTextView.text = "$followersCount followers"
                followersTextView.setTextColor(misc.setFollowersColor(followersCount))

                val handle = dict["handle"] as? String ?: "error"
                handleTextView.text = "@$handle"

                descriptionTextView.text = dict["description"] as? String ?: "error"
                emailTextView.text = dict["email"] as? String ?: "error"
                nameTextView.text = dict["email"] as? String ?: "error"
                phoneTextView.text = dict["phoneNumber"] as? String ?: "error"
                birthdayTextView.text = dict["birthday"] as? String ?: "error"
            }
            override fun onCancelled(error: DatabaseError?) {  Log.d("DatabaseError", error.toString()) }
        }
        val meRef = ref.child("users").child(myID)
        meRef.addValueEventListener(meValueListener)
    }

    fun removeObserverForMe() {
        val meRef = ref.child("users").child(myID)
        if (meValueListener != null) {
            meRef.removeEventListener(meValueListener)
            meValueListener = null
        }
    }

    fun editInfo(type: String, text: String, pop: PopupWindow) {
        meInteractionListener?.dismissKeyboardMe()

        if (text == "") {
            alert("Empty Field", "Please fill in the empty field or cancel editing.")
            return
        }

        val meRef = ref.child("users").child(myID)

        when (type) {
            "handle" -> {
                val handleLower = text.toLowerCase()
                val userRef = ref.child("users")
                userRef.orderByChild("handleLower").equalTo(handleLower).addListenerForSingleValueEvent( object: ValueEventListener {
                    @SuppressLint("SetTextI18n")
                    override fun onDataChange(snap: DataSnapshot?) {
                        if  (snap?.value != null) {
                            alert("Handle Exists", "This handle is taken. Please pick a new one or cancel to keep your old one.")
                            return
                        } else {
                            val spec = misc.getSpecialHandles()
                            if (spec.contains(handleLower)) {
                                alert(":(", "This handle is unavailable.")
                                return
                            } else {
                                meRef.child("handle").setValue(text)
                                meRef.child("handleLower").setValue(handleLower)
                                handleTextView.text = "@$text"
                                logEdited(type)

                                misc.getFollowers(myID) { userFollowers ->
                                    if (!userFollowers.isEmpty()) {
                                        val fanoutObject = mutableMapOf<String,Any>()
                                        for (followerID in userFollowers) {
                                            fanoutObject.put("/$followerID/$myID/handle", text)
                                        }
                                        val userAddedRef = ref.child("userAdded")
                                        userAddedRef.updateChildren(fanoutObject)
                                    }
                                }

                                pop.dismiss()
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError?) {
                        Log.d("DatabaseError", error.toString())
                        return
                    }
                })
            }

            "description" -> {
                meRef.child("description").setValue(text)
                descriptionTextView.text = text
                logEdited(type)

                misc.getFollowers(myID) { userFollowers ->
                    if (!userFollowers.isEmpty()) {
                        val fanoutObject = mutableMapOf<String,Any>()
                        for (followerID in userFollowers) {
                            fanoutObject.put("/$followerID/$myID/description", text)
                        }
                        val userAddedRef = ref.child("userAdded")
                        userAddedRef.updateChildren(fanoutObject)
                    }
                }

                pop.dismiss()
            }

            "name" -> {
                meRef.child("name").setValue(text)
                nameTextView.text = text
                logEdited(type)
                pop.dismiss()
            }

            "phone" -> {
                val phone = misc.formatPhoneNumber(text)
                meRef.child("phoneNumber").setValue(phone)
                phoneTextView.text = misc.formatPhoneNumber(phone)
                logEdited(type)
                pop.dismiss()
            }

            "birthday" -> {
                meRef.child("birthday").setValue(text)
                birthdayTextView.text = text
                logEdited(type)
                pop.dismiss()
            }

            else -> {
                alert("Error", "We encountered an error and cannot edit right now. Please report this bug if it persists.")
                return
            }
        }
    }

    fun authenticate(currentEmail: String, currentPassword: String, newEmail: String, newPassword: String, pop: PopupWindow) {
        meInteractionListener?.dismissKeyboardMe()

        if (currentEmail == "" || currentPassword == "") {
            alert("Empty Fields", "Please enter in your current email and password.")
            return
        }

        if (newPassword != "" && newPassword.length < 6) {
            alert("New Password Too Short", "Your new password needs to be at least 6 characters.")
            return
        }

        val credential = EmailAuthProvider.getCredential(currentEmail, currentPassword)
        val user = auth.currentUser
        user?.reauthenticate(credential)?.addOnCompleteListener { result ->
            if (result.isSuccessful) {
                editLogin(newEmail, newPassword, pop)
            } else {
                alert("Oops", "Please check if your current login info is valid.")
            }
        }
    }

    fun editLogin(newEmail: String, newPassword: String, pop: PopupWindow) {
        val user = auth.currentUser

        if (newEmail != "") {
            user?.updateEmail(newEmail)?.addOnCompleteListener { result ->
                if (result.isSuccessful) {
                    val meRef = ref.child("users").child(myID)
                    meRef.child("email").setValue(newEmail)
                    logEdited("email")
                } else {
                    alert("Oops", "We encountered an email error - please try again. Report the bug if it persists.")
                }
            }
        }

        if (newPassword != "") {
            user?.updatePassword(newPassword)?.addOnCompleteListener { result ->
                if (result.isSuccessful) {
                    logEdited("password")
                } else {
                    alert("Oops", "We encountered a password error - please try again. Report the bug if it persists.")
                }
            }
        }

        pop.dismiss()
    }

}
