@file:Suppress("UNCHECKED_CAST", "MemberVisibilityCanPrivate", "CascadeIf", "LiftReturnOrAssignment", "UnnecessaryVariable", "SetTextI18n")

package com.dotnative.flocal

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Point
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.app.Fragment
import android.util.Log
import android.view.*
import android.widget.*
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.squareup.picasso.Picasso

class LoginFragment : Fragment() {

    // MARK: - Layout

    lateinit var titleImageView: ImageView
    lateinit var emailEditText: EditText
    lateinit var passwordEditText: EditText
    lateinit var loginButton: Button
    lateinit var forgotPasswordButton: Button
    lateinit var progressBar: ProgressBar

    // MARK: - Vars

    private var loginInteractionListener: LoginInteractionListener? = null

    var popupContainer: ViewGroup? = null
    var popupView: View? = null

    var ref: DatabaseReference = FirebaseDatabase.getInstance().reference
    val storageRef: StorageReference = FirebaseStorage.getInstance().reference
    val analytics: FirebaseAnalytics = FirebaseAnalytics.getInstance(context)
    val auth: FirebaseAuth = FirebaseAuth.getInstance()

    val misc = Misc()

    // MARK: - Lifecycle

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context is LoginInteractionListener) {
            loginInteractionListener = context
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
        val view =  inflater!!.inflate(R.layout.fragment_login, container, false)
        popupContainer = container
        popupView = view
        setLayout(view)
        return view
    }

    override fun onStart() {
        super.onStart()
        logViewLogin()
        loginButton.isEnabled = true
        progressBar.visibility = ProgressBar.INVISIBLE
    }

    override fun onDetach() {
        super.onDetach()
        loginInteractionListener = null
    }

   // MARK - Navigation

    interface LoginInteractionListener {
        fun turnToMeFromLogin()
        fun dismissKeyboardLogin()
    }

    fun setLayout(view: View) {
        titleImageView = view.findViewById(R.id.titleImageView)
        emailEditText = view.findViewById(R.id.emailTextView)
        passwordEditText = view.findViewById(R.id.passwordEditText)

        loginButton = view.findViewById(R.id.loginButton)
        loginButton.setOnClickListener { login() }

        forgotPasswordButton = view.findViewById(R.id.forgotPasswordButton)
        forgotPasswordButton.setOnClickListener { showForgotPassword() }
    }

    // MARK: - Popup

    fun showForgotPassword() {
        val layoutInflater: LayoutInflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val inflatedView: View = layoutInflater.inflate(R.layout.popup_forgot_password, popupContainer, false)

        val display = activity.windowManager.defaultDisplay
        val size = Point()
        display.getSize(size)
        val width = size.x

        val popupWindow = PopupWindow(inflatedView, width - 50, WindowManager.LayoutParams.WRAP_CONTENT, true)
        popupWindow.isFocusable = true
        popupWindow.isOutsideTouchable = true

        val emailEditText: EditText = inflatedView.findViewById(R.id.emailTextView)
        val confirmButton: Button = inflatedView.findViewById(R.id.confirmButton)
        confirmButton.setOnClickListener {
            val email = emailEditText.text.toString()
            auth.sendPasswordResetEmail(email).addOnCompleteListener{ result ->
                if (result.isSuccessful) {
                    val alert = AlertDialog.Builder(context)
                    alert.setTitle("Sign Up Complete")
                    alert.setMessage("Welcome to flocal :) Tell yout friends to check us out!")
                    alert.setPositiveButton("Ok", { _, _ ->
                        confirmButton.isEnabled = true
                        progressBar.visibility = ProgressBar.INVISIBLE
                        popupWindow.dismiss()
                    })
                    alert.show()
                } else {
                    alert("error", "We encountered an error. Please email us at flocalApp@gmail.com if this persists.")
                }
            }
        }

        logViewForgotPassword()
        popupWindow.showAtLocation(popupView, Gravity.CENTER, 0,0)
    }

    // MARK: - Misc

    fun alert(title: String, message: String) {
        misc.displayAlert(context, title, message)
        loginButton.isEnabled = true
        progressBar.visibility = ProgressBar.INVISIBLE
    }

    // MARK: Analytics

    fun logViewLogin() {
        analytics.logEvent("viewLogin_Android", null)
    }

    fun logLoggedIn(userID: String) {
        val bundle = Bundle()
        bundle.putString("userID", userID)
        analytics.logEvent("loggedIn_Android", bundle)
    }

    fun logViewForgotPassword() {
        analytics.logEvent("viewForgotPassword_Android", null)
    }

    // MARK: - Storage

    fun cacheProfilePic(userID: String, size: String) {
        val child = userID + "_" +  size
        val profilePicRef = storageRef.child("profilePic/$child.jpg")
        profilePicRef.downloadUrl.addOnSuccessListener { uri ->
            val imageView = ImageView(context)
            Picasso.with(context).load(uri).into(imageView)
            val urlString = uri.toString()
            ref.child("users").child(userID).child("profilePicURLString").setValue(urlString)
        }.addOnFailureListener { error -> Log.d("urlDownloadError", error.toString()) }
    }

    // MARK: - Firebase

    @SuppressLint("ApplySharedPref")
    fun login() {
        misc.playSound(context, R.raw.button_click, 0)
        loginInteractionListener?.dismissKeyboardLogin()
        loginButton.isEnabled = false
        progressBar.visibility = ProgressBar.VISIBLE

        val emailString = emailEditText.text.toString()
        val passwordString = passwordEditText.text.toString()

        if (emailString == "" || passwordString == "") {
            alert("Incomplete Info", "Please fill the empty fields.")
            return
        }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val deviceToken = sharedPreferences.getString("deviceToken.flocal", "n/a")

        auth.signInWithEmailAndPassword(emailString, passwordString).addOnCompleteListener { result ->
            if (result.isSuccessful) {
                val user = auth.currentUser
                val userID = user!!.uid
                val userRef = ref.child("users").child(userID)

                val editor = sharedPreferences.edit()
                editor.putString("myID.flocal", userID)
                editor.commit()

                val loginFirstTime = sharedPreferences.getBoolean("loginFirstTime.flocal", false)
                if (loginFirstTime) {
                    editor.putBoolean("loginFirstTime.flocal", false)
                    editor.apply()
                    val handle = sharedPreferences.getString("handle.flocal", "Please tap to reset your handle")
                    userRef.child("email").setValue(emailString)
                    userRef.child("handle").setValue(handle)
                    userRef.child("handleLower").setValue(handle.toLowerCase())
                    userRef.child("name").setValue("no name set")
                    userRef.child("description").setValue("no description set")
                    userRef.child("birthday").setValue("no bday set")
                    userRef.child("phoneNumber").setValue("no phone set")
                    userRef.child("longitude").setValue(420)
                    userRef.child("latitude").setValue(420)
                    userRef.child("followersCount").setValue(0)
                    userRef.child("points").setValue(0)
                    userRef.child("postPoints").setValue(0)
                    userRef.child("replyPoints").setValue(0)
                    userRef.child("lastNotificationType").setValue("clear")
                    userRef.child("notificationBadge").setValue(0)
                }

                userRef.child("deviceToken").setValue(deviceToken)
                userRef.child("OS").setValue("Android")

                cacheProfilePic(userID, "large")
                cacheProfilePic(userID, "small")

                logLoggedIn(userID)
                progressBar.visibility = ProgressBar.INVISIBLE
                loginInteractionListener?.turnToMeFromLogin()

            } else {
                alert("Invalid Login", "Your email/pass may not be correct.")
            }
        }
    }

}
