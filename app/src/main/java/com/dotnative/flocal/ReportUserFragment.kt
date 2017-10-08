@file:Suppress("UNCHECKED_CAST", "MemberVisibilityCanPrivate", "CascadeIf", "LiftReturnOrAssignment", "UnnecessaryVariable", "SetTextI18n")

package com.dotnative.flocal

import android.app.AlertDialog
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

class ReportUserFragment : Fragment() {

    // MARK: - Layout

    lateinit var pickerTextView: AutoCompleteTextView
    lateinit var editText: EditText
    lateinit var confirmButton: Button

    // MARK: - Vars

    private var reportUserInteractionListener: ReportUserInteractionListener? = null

    var myID: String = "0"
    var userID: String = "0"
    val options = arrayOf("Offensive/Inappropriate Chat", "Inappropriate Posts", "Bullying", "Other")

    val ref: DatabaseReference = FirebaseDatabase.getInstance().reference
    val analytics: FirebaseAnalytics = FirebaseAnalytics.getInstance(context)

    val misc = Misc()

    // MARK: - Lifecycle

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context is ReportUserInteractionListener) {
            reportUserInteractionListener = context
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
        val view = inflater!!.inflate(R.layout.fragment_report_user, container, false)
        setLayout(view)
        return view
    }

    override fun onStart() {
        super.onStart()
        myID = misc.setMyID(context)
        if (myID == "0") {
            reportUserInteractionListener?.dismissReportUser()
        } else {
            logViewReportUser()
        }
    }

    override fun onDetach() {
        super.onDetach()
        reportUserInteractionListener = null
    }

    // MARK: - Navigation

    interface ReportUserInteractionListener {
        fun dismissReportUser()
        fun dismissKeyboardFromReportUser()
    }

    fun setArguments() {
        val arguments = arguments
        userID = arguments.getString("userIDToPass", "0")
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
        confirmButton.setOnClickListener { writeReportUser() }
    }

    // MARK: - Misc

    fun alert(title: String, message: String) {
        misc.displayAlert(context, title, message)
    }

    // MARK: - Analytics

    fun logViewReportUser() {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        bundle.putString("userID", userID)
        analytics.logEvent("viewReportUser_Android", bundle)
    }

    fun logWroteReportUser() {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        bundle.putString("userID", userID)
        analytics.logEvent("wroteReportUser_Android", bundle)
    }

    // MARK: - Firebase

    fun writeReportUser() {
        misc.playSound(context, R.raw.button_click, 0)
        reportUserInteractionListener?.dismissKeyboardFromReportUser()

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

        val reportRef = ref.child("userReports").child(userID).push()
        reportRef.child("myID").setValue(myID)
        reportRef.child("reason").setValue(reason)
        reportRef.child("description").setValue(details)

        logWroteReportUser()

        val alert = AlertDialog.Builder(context)
        alert.setTitle("Thank you!")
        alert.setMessage("Your report has been received. Some people just suck, but please don't let it ruin your day!")
        alert.setPositiveButton("Ok", { _, _ ->
            reportUserInteractionListener?.dismissReportUser()
        })
        alert.create()
        alert.show()
    }

}