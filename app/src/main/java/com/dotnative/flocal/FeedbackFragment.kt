@file:Suppress("UNCHECKED_CAST", "MemberVisibilityCanPrivate", "CascadeIf", "LiftReturnOrAssignment", "UnnecessaryVariable", "SetTextI18n")

package com.dotnative.flocal

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase


class FeedbackFragment : Fragment() {

    // MARK: - Layout

    lateinit var titleTextView: TextView
    lateinit var editText: EditText
    lateinit var confirmButton: Button

    // MARK - Vars

    private var feedbackInteractionListener: FeedbackInteractionListener? = null

    var myID: String = "0"
    val ref: DatabaseReference = FirebaseDatabase.getInstance().reference
    val analytics: FirebaseAnalytics = FirebaseAnalytics.getInstance(context)
    val misc = Misc()

    // MARK: - Lifecycle

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context is FeedbackInteractionListener) {
            feedbackInteractionListener = context
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
        val view = inflater!!.inflate(R.layout.fragment_feedback, container, false)
        setLayout(view)
        return view
    }

    override fun onStart() {
        super.onStart()
        myID = misc.setMyID(context)
        if (myID == "0") {
            feedbackInteractionListener?.turnToFragmentFromFeedback("Login")
        } else {
            misc.setSideMenuIndex(context, 6)
            logViewFeedback()
        }
    }

    override fun onDetach() {
        super.onDetach()
        feedbackInteractionListener = null
    }

    // MARK: - Navigation

    interface FeedbackInteractionListener {
        fun turnToFragmentFromFeedback(name: String)
        fun dismissKeyboardFromFeedback()
    }

    fun setLayout(view: View) {
        titleTextView = view.findViewById(R.id.titleTextView)
        editText = view.findViewById(R.id.editText)

        confirmButton = view.findViewById(R.id.confirmButton)
        confirmButton.setOnClickListener { writeFeedback() }
    }

    // MARK: - Misc

    fun alert(title: String, message: String) {
        misc.displayAlert(context, title, message)
    }

    // Analytics

    fun logViewFeedback() {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        analytics.logEvent("viewFeedback_Android", bundle)
    }

    fun logWroteFeedback() {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        analytics.logEvent("wroteFeedback_Android", bundle)
    }

    // MARK: - Firebase

    fun writeFeedback() {
        misc.playSound(context, R.raw.button_click, 0)
        feedbackInteractionListener?.dismissKeyboardFromFeedback()
        val text = editText.text.toString()
        if (text == "") {
            alert("Empty Field", "Please type some text.")
            return
        }

        val feedbackRef = ref.child("feedback").push()
        feedbackRef.child("myID").setValue(myID)
        feedbackRef.child("feedback").setValue(text)

        logWroteFeedback()
        alert("Thank you :)", "Your feedback has been received. This app is made fore people like you," +
                " abd we'll continue to shae it towards what you guys want!")
        editText.setText("")
    }

}
