@file:Suppress("UNCHECKED_CAST", "MemberVisibilityCanPrivate", "CascadeIf", "LiftReturnOrAssignment", "UnnecessaryVariable", "SetTextI18n")

package com.dotnative.flocal

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.support.v4.app.Fragment
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.baoyz.actionsheet.ActionSheet
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import de.hdodenhof.circleimageview.CircleImageView
import java.io.ByteArrayOutputStream
import java.io.IOException

class SignUpFragment : Fragment() {

    // MARK: - Layout

    lateinit var tapProfilePicTextView: TextView
    lateinit var profilePicImageView: CircleImageView
    lateinit var checkHandleTextView: TextView
    lateinit var handleEditText: EditText
    lateinit var emailEditText: EditText
    lateinit var passwordEditText: EditText
    lateinit var signUpButton: Button
    lateinit var privacyPolicyButton: Button
    lateinit var termsButton: Button
    lateinit var progressBar: ProgressBar

    // MARK: - Vars

    private var signUpInteractionListener: SignUpInteractionListener? = null

    var isPicSet: Boolean = false
    var handleExists: String = "error"

    var popupContainer: ViewGroup? = null
    var popupView: View? = null

    var ref: DatabaseReference = FirebaseDatabase.getInstance().reference
    val storageRef: StorageReference = FirebaseStorage.getInstance().reference
    var analytics: FirebaseAnalytics? = null
    val auth: FirebaseAuth = FirebaseAuth.getInstance()

    val misc = Misc()

    // MARK: - Lifecycle

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context is SignUpInteractionListener) {
            signUpInteractionListener = context
        } else {
            throw RuntimeException(context!!.toString() + " must implement SignUpInteractionListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        analytics = FirebaseAnalytics.getInstance(context)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view =  inflater!!.inflate(R.layout.fragment_sign_up, container, false)
        popupContainer = container
        popupView = view
        setLayout(view)
        return view
    }

    override fun onStart() {
        super.onStart()
        signUpButton.isEnabled = true
        profilePicImageView.isClickable = true
        progressBar.visibility = ProgressBar.INVISIBLE
        logViewSignUp()
    }

    override fun onDetach() {
        super.onDetach()
        resetSignUp()
        signUpInteractionListener = null
    }

    // MARK: - Navigation

    interface SignUpInteractionListener {
        fun turnToMeFromSignUp()
        fun dismissKeyboardSignUp()
    }

    fun setLayout(view: View) {
        tapProfilePicTextView = view.findViewById(R.id.tapTextView)
        profilePicImageView = view.findViewById(R.id.profilePicImageView)
        profilePicImageView.setOnClickListener{ selectPicSource() }

        checkHandleTextView = view.findViewById(R.id.checkHandleTextView)
        handleEditText = view.findViewById(R.id.handleTextView)
        handleEditText.setOnFocusChangeListener { _, b ->
            val handle = handleEditText.text.toString().trim()
            if (!b && handle != "") {
                checkHandle(handle)
            }
        }
        emailEditText = view.findViewById(R.id.emailTextView)
        passwordEditText = view.findViewById(R.id.passwordEditText)

        signUpButton = view.findViewById(R.id.signUpButton)
        signUpButton.setOnClickListener { signUp() }

        privacyPolicyButton = view.findViewById(R.id.privacyPolicyButton)
        privacyPolicyButton.setOnClickListener { openPrivacyPolicy() }

        termsButton = view.findViewById(R.id.termsButton)
        termsButton.setOnClickListener { openTerms() }

        progressBar = view.findViewById(R.id.progressBar)
    }

    fun openPrivacyPolicy() {
        logViewPrivacyPolicy()
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.iubenda.com/privacy-policy/7955712"))
        startActivity(browserIntent)
    }

    fun openTerms() {
        val layoutInflater: LayoutInflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val inflatedView: View = layoutInflater.inflate(R.layout.popup_terms, popupContainer, false)

        val display = activity.windowManager.defaultDisplay
        val size = Point()
        display.getSize(size)
        val width = size.x
        val height = size.y

        val popupWindow = PopupWindow(inflatedView, width - 50, height - 400, true)
        popupWindow.isFocusable = true
        popupWindow.isOutsideTouchable = true

        val xButton: Button = inflatedView.findViewById(R.id.xButton)
        xButton.setOnClickListener { popupWindow.dismiss() }

        logViewTerms()
        popupWindow.showAtLocation(popupView, Gravity.BOTTOM, 0,100)
    }

    // MARK - Camera

    fun selectPicSource() {
        profilePicImageView.setBackgroundResource(R.drawable.add_pic_s)

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
                        choosePhotoLibrary()
                    }
                    else -> {
                        profilePicImageView.setBackgroundResource(R.drawable.add_pic)
                        actionSheet?.dismiss()
                    }
                }

            }
            override fun onDismiss(actionSheet: ActionSheet?, isCancel: Boolean) {
                profilePicImageView.setBackgroundResource(R.drawable.add_pic)
            }

        })
        misc.playSound(context, R.raw.button_click, 0)
        sheet.show()
    }

    fun chooseCamera() {
        val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra("android.intent.extras.CAMERA_FACING", 1)
        startActivityForResult(intent, 0)
    }

    fun choosePhotoLibrary() {
        val intent = Intent(Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, 1)
    }

     override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
         if (data != null) {
             when (resultCode) {
                 0 -> {
                     val bitmap: Bitmap = data.extras.get("data") as Bitmap
                     profilePicImageView.setBackgroundResource(android.R.color.transparent)
                     profilePicImageView.setImageBitmap(bitmap)
                     isPicSet = true
                 }
                 1 -> {
                    val uri = data.data
                     try {
                         val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                         profilePicImageView.setBackgroundResource(android.R.color.transparent)
                         profilePicImageView.setImageBitmap(bitmap)
                         isPicSet = true
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

    // MARK: - Misc

    fun setCheckHandleTextView() {
        when (handleExists) {
            "no" -> {
                val spec = misc.getSpecialHandles()
                val handleLower: String = this.handleEditText.text.toString().toLowerCase()
                if (spec.contains(handleLower)) {
                    checkHandleTextView.setText(R.string.handleYes)
                    checkHandleTextView.setTextColor(Color.RED)
                } else {
                    checkHandleTextView.setText(R.string.handleNo)
                    checkHandleTextView.setTextColor(Color.GREEN)
                }
            }
            "yes" -> {
                checkHandleTextView.setText(R.string.handleYes)
                checkHandleTextView.setTextColor(Color.RED)
            }
            "special" -> {
                checkHandleTextView.setText(R.string.handleSpecial)
                checkHandleTextView.setTextColor(Color.RED)
            }
            "internet" -> {
                checkHandleTextView.setText(R.string.handleInternet)
                checkHandleTextView.setTextColor(Color.LTGRAY)
            }
            else -> {
                checkHandleTextView.setText(R.string.error)
                checkHandleTextView.setTextColor(Color.RED)
            }
        }
    }

    fun resetSignUp() {
        profilePicImageView.setImageResource(android.R.color.transparent)
        profilePicImageView.setBackgroundResource(R.drawable.add_pic)
        checkHandleTextView.text = ""
        handleEditText.setText("")
        emailEditText.setText("")
        passwordEditText.setText("")
        isPicSet = false
        signUpButton.isEnabled = true
        profilePicImageView.isClickable = true
        progressBar.visibility = ProgressBar.INVISIBLE
    }

    fun alert(title: String, message: String) {
        misc.displayAlert(context, title, message)
        signUpButton.isEnabled = true
        profilePicImageView.isClickable = true
        progressBar.visibility = ProgressBar.INVISIBLE
    }

    // MARK: Analytics

    fun logViewSignUp() {
        analytics?.logEvent("viewSignUp_Android", null)
    }

    fun logSignedUp(userID: String, email: String) {
        val bundle = Bundle()
        bundle.putString("userID", userID)
        bundle.putString("email", email)
        analytics?.logEvent("signedUp_Android", bundle)
    }

    fun logViewPrivacyPolicy() {
        analytics?.logEvent("viewPrivacyPolicy_Android", null)
    }

    fun logViewTerms() {
        analytics?.logEvent("viewTerms_Android", null)
    }

    // MARK: Storage

    fun uploadPic(bitmap: Bitmap, userID: String, size: String) {
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
        val child = userID + "_" +  size
        val profilePicRef = storageRef.child("profilePic/$child.jpg")

        val uploadTask = profilePicRef.putBytes(data, metadata)
        uploadTask.addOnFailureListener { error ->
            alert("Upload Error", "Your profile pic may not have been uploaded. " +
                    "Please try again or report the bug if it persists.")
            Log.d("uploadError", error.toString())
        }
        uploadTask.addOnSuccessListener { taskSnap ->
            if (size == "large") {
                val urlString = taskSnap.downloadUrl.toString()
                ref.child("users").child(userID).child("profilePicURLString").setValue(urlString)
            }
        }
    }

    // MARK: - Firebase

    fun checkHandle(handle: String) {
        val handleLower = handle.toLowerCase().trim()
        val userRef = ref.child("users")
        userRef.orderByChild("handleLower").equalTo(handleLower).addListenerForSingleValueEvent( object: ValueEventListener {
            override fun onDataChange(snap: DataSnapshot?) {
                if  (snap?.value != null) {
                    handleExists = "yes"
                    setCheckHandleTextView()
                } else {
                    val spec = misc.getSpecialHandles()
                    if (spec.contains(handleLower)) {
                        handleExists = "yes"
                    } else {
                        handleExists = "no"
                    }
                    setCheckHandleTextView()
                }

            }
            override fun onCancelled(error: DatabaseError?) { Log.d("DatabaseError", error.toString()) }
        })
    }

    @SuppressLint("ApplySharedPref")
    fun signUp() {
        progressBar.visibility = ProgressBar.VISIBLE
        misc.playSound(context, R.raw.button_click, 0)
        signUpInteractionListener?.dismissKeyboardSignUp()
        signUpButton.isEnabled = false
        profilePicImageView.isClickable = false

        val emailString = emailEditText.text.toString().trim()
        val passwordString = passwordEditText.text.toString()
        val handleString = handleEditText.text.toString().trim()
        val handleLowerString = handleString.toLowerCase()

        if (emailString == "" || passwordString == "" || handleString == "") {
            alert("Incomplete Info", "Please fill the empty fields.")
            return
        }

        val at = "@"
        if (!emailString.contains(at)) {
            alert("Invalid Email", "Please enter a valid email")
            return
        }

        if (passwordString.length < 6) {
            alert("Password Too Short", "Your pass needs to be at least 6 characters.")
            return
        }

        val hasSpecialChars = misc.checkSpecialCharacters(handleString)
        if (hasSpecialChars) {
            alert("Special Characters", "Please remove any special characters from your handle." +
                    "Only a-z, A-Z, 0-9, periods and underscores are allowed.")
            return
        }

        val spec = misc.getSpecialHandles()
        if (spec.contains(handleLowerString)) {
            alert(":(", "Sorry, this handle is unavailable. Please choose another.")
            return
        }

        if (handleExists != "no") {
            alert("Invalid Handle", "Sorry, this handle is invalid or we can't check or server now." +
                    "Please try again.")
            return
        }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val deviceToken = sharedPreferences.getString("deviceToken.flocal", "n/a")

        auth.createUserWithEmailAndPassword(emailString, passwordString).addOnCompleteListener { result ->
            if (result.isSuccessful) {
                val userID = auth.currentUser!!.uid
                logSignedUp(userID, emailString)
                val editor = sharedPreferences.edit()
                editor.putString("myID.flocal", userID)
                editor.putString("handle.flocal", handleString)
                editor.commit()
                if (isPicSet) {
                    profilePicImageView.isDrawingCacheEnabled = true
                    profilePicImageView.buildDrawingCache()
                    val bitmap = profilePicImageView.drawingCache
                    uploadPic(bitmap, userID, "large")
                    uploadPic(bitmap, userID, "small")
                }
                loginFirstTime(emailString, passwordString, userID, handleString, deviceToken)
            } else {
                val e = result.exception
                alert("Oops", "$e")
            }
        }

        progressBar.visibility = ProgressBar.INVISIBLE
    }

    @SuppressLint("ApplySharedPref")
    fun loginFirstTime(email: String, password: String, userID: String, handle: String, deviceToken: String) {
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { result ->
            if (result.isSuccessful) {
                val userRef = ref.child("users").child(userID)
                userRef.child("email").setValue(email)
                userRef.child("handle").setValue(handle)
                userRef.child("handleLower").setValue(handle.toLowerCase())
                userRef.child("name").setValue("no name set")
                userRef.child("description").setValue("no description set")
                userRef.child("birthday").setValue("no bday set")
                userRef.child("phoneNumber").setValue("no phone set")
                userRef.child("deviceToken").setValue(deviceToken)
                userRef.child("OS").setValue("Android")
                userRef.child("longitude").setValue(420)
                userRef.child("latitude").setValue(420)
                userRef.child("followersCount").setValue(0)
                userRef.child("points").setValue(0)
                userRef.child("postPoints").setValue(0)
                userRef.child("replyPoints").setValue(0)
                userRef.child("lastNotificationType").setValue("clear")
                userRef.child("notificationBadge").setValue(0)

                val alert = AlertDialog.Builder(context)
                alert.setTitle("Sign Up Complete")
                alert.setMessage("Welcome to flocal :) Tell yout friends to check us out!")
                alert.setPositiveButton("Ok", { _, _ ->
                    resetSignUp()
                    signUpInteractionListener?.turnToMeFromSignUp()
                })
                alert.show()

            } else {
                alert("waht. How did that happen?", "We seem to have come across a rare error and can't sign you in automatically." +
                        "Try logging in through the login page. If you still can't login or sign up, please email us at flocalApp@gmail.com." )
                val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
                val editor = sharedPreferences.edit()
                editor.putBoolean("loginFirstTime.flocal", true)
                editor.putString("handle.flocal", handle)
                editor.commit()
            }
        }
    }


}