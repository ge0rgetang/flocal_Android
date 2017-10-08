@file:Suppress("UNCHECKED_CAST", "MemberVisibilityCanPrivate", "CascadeIf", "LiftReturnOrAssignment", "UnnecessaryVariable", "SetTextI18n")

package com.dotnative.flocal

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Point
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.PopupWindow
import android.widget.TextView
import com.google.firebase.analytics.FirebaseAnalytics

class AboutFragment : Fragment() {

    // MARK: - Layout

    lateinit var aboutTextView: TextView
    lateinit var ccButton: Button
    lateinit var privacyButton: Button
    lateinit var termsButton: Button
    lateinit var licenseTextView: TextView

    // MARK: - Vars

    private var aboutInteractionListener: AboutInteractionListener? = null

    var myID: String = "0"
    var analytics: FirebaseAnalytics? = null
    val misc = Misc()

    var popupContainer: ViewGroup? = null
    var popupView: View? = null

    // MARK: - Lifecycle

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context is AboutInteractionListener) {
            aboutInteractionListener = context
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
        val view = inflater!!.inflate(R.layout.fragment_about, container, false)
        popupContainer = container
        popupView = view
        setLayout(view)
        return view
    }

    override fun onStart() {
        super.onStart()
        myID = misc.setMyID(context)
        if (myID == "0") {
            aboutInteractionListener?.turnToFragmentFromAbout("Login")
        } else {
            misc.setSideMenuIndex(context, 7)
            logViewAbout()
        }
    }

    override fun onDetach() {
        super.onDetach()
        aboutInteractionListener = null
    }

    // MARK: - Navigation

    interface AboutInteractionListener {
        fun turnToFragmentFromAbout(name: String)
    }

    fun setLayout(view: View) {
        aboutTextView = view.findViewById(R.id.aboutTextView)
        licenseTextView = view.findViewById(R.id.licenseTextView)

        ccButton = view.findViewById(R.id.ccButton)
        ccButton.setOnClickListener { openCC() }

        privacyButton = view.findViewById(R.id.privacyPolicyButton)
        privacyButton.setOnClickListener { openPrivacyPolicy() }

        termsButton = view.findViewById(R.id.termsButton)
        termsButton.setOnClickListener { openTerms() }
    }

    fun openPrivacyPolicy() {
        logViewPrivacyPolicy()
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.iubenda.com/privacy-policy/7955712"))
        startActivity(browserIntent)
    }

    fun openCC() {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.creativecommons.org/licenses/by/4.0/legalcode"))
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

    // MARK: - Analytics

    fun logViewAbout() {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        analytics?.logEvent("viewAbout_Android", bundle)
    }

    fun logViewPrivacyPolicy() {
        analytics?.logEvent("viewPrivacyPolicy_Android", null)
    }

    fun logViewTerms() {
        analytics?.logEvent("viewTerms_Android", null)
    }

}