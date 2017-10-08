@file:Suppress("UNCHECKED_CAST", "MemberVisibilityCanPrivate", "CascadeIf", "LiftReturnOrAssignment", "UnnecessaryVariable", "SetTextI18n")

package com.dotnative.flocal

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class ReportBugFragment : Fragment() {

// MARK: - Layout

    lateinit var pickerTextView: AutoCompleteTextView
    lateinit var editText: EditText
    lateinit var confirmButton: Button

    // MARK: - Vars

    private var reportBugInteractionListener: ReportBugInteractionListener? = null

    var myID: String = "0"
    val options = arrayOf("Post issue", "Profile issue", "Peeps issue", "Chat issue", "Other")

    val ref: DatabaseReference = FirebaseDatabase.getInstance().reference
    val analytics: FirebaseAnalytics = FirebaseAnalytics.getInstance(context)

    val misc = Misc()

    // MARK: - Lifecycle

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context is ReportBugInteractionListener) {
            reportBugInteractionListener = context
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
        val view = inflater!!.inflate(R.layout.fragment_report_bug, container, false)
        setLayout(view)
        return view
    }

    override fun onStart() {
        super.onStart()
        myID = misc.setMyID(context)
        if (myID == "0") {
            reportBugInteractionListener?.turnToFragmentFromReportBug("Login")
        } else {
            misc.setSideMenuIndex(context, 5)
            logViewReportBug()
        }
    }

    override fun onDetach() {
        super.onDetach()
        reportBugInteractionListener = null
    }

    // MARK: - Navigation

    interface ReportBugInteractionListener {
        fun turnToFragmentFromReportBug(name: String)
        fun dismissKeyboardFromReportBug()
    }

    fun setLayout(view: View) {
        pickerTextView = view.findViewById(R.id.pickerTextView)
        val adapter = ArrayAdapter<String>(context, R.layout.select_item, options)
        pickerTextView.setAdapter(adapter)
        pickerTextView.threshold = 0
        pickerTextView.setOnFocusChangeListener { _, b ->
            if (b) {
                pickerTextView.showDropDown()
            }
        }
        pickerTextView.setOnItemClickListener { _, _, i, _ ->
            val text = options[i]
            pickerTextView.setText(text)
            pickerTextView.dismissDropDown()
        }

        editText = view.findViewById(R.id.editText)

        confirmButton = view.findViewById(R.id.confirmButton)
        confirmButton.setOnClickListener { writeReportBug() }
    }

    // MARK: - Misc

    fun alert(title: String, message: String) {
        misc.displayAlert(context, title, message)
    }

    // MARK: - Analytics

    fun logViewReportBug() {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        analytics.logEvent("viewReportBug_Android", bundle)
    }

    fun logWroteReportBug() {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        analytics.logEvent("wroteReportBug_Android", bundle)
    }

    // MARK: - Firebase

    fun writeReportBug() {
        misc.playSound(context, R.raw.button_click, 0)
        reportBugInteractionListener?.dismissKeyboardFromReportBug()

        val reason = pickerTextView.text.toString()
        if (reason == "") {
            alert("Empty Reason", "Please pick the best suited reason")
            return
        }

        val details = editText.text.toString()
        if (details == "") {
            alert("Empty Details", "Please give us some specific details for the report.")
            return
        }

        val reportRef = ref.child("bugs").push()
        reportRef.child("myID").setValue(myID)
        reportRef.child("reason").setValue(reason)
        reportRef.child("description").setValue(details)

        logWroteReportBug()

        alert("Thank you :)", "Your bug report has been received. We'll try ti resolve the issue in the next update!")
        editText.setText("")
    }

}
