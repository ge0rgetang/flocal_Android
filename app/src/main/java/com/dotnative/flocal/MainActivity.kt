@file:Suppress("UNCHECKED_CAST", "MemberVisibilityCanPrivate", "CascadeIf", "LiftReturnOrAssignment", "UnnecessaryVariable", "SetTextI18n")

package com.dotnative.flocal

import android.annotation.SuppressLint
import android.app.Activity
import android.app.FragmentManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.baoyz.actionsheet.ActionSheet
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.squareup.picasso.LruCache
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.fragment_chat.*
import kotlinx.android.synthetic.main.popup_edit.*

class MainActivity : AppCompatActivity(), SideMenuAdapter.SideMenuAdapterListener, LocationFragment.LocationInteractionListener,
        NotificationsAdapter.NotificationsAdapterListener,
        SignUpFragment.SignUpInteractionListener, LoginFragment.LoginInteractionListener,
        HomeFragment.HomeInteractionListener, HomeAdapter.HomeAdapterListener,
        ReplyFragment.ReplyInteractionListener, ReplyAdapter.ReplyAdapterListener,
        ReportPostFragment.ReportPostInteractionListener,
        UserProfileFragment.UserProfileInteractionListener, UserProfileAdapter.UserProfileAdapterListener,
        ReportUserFragment.ReportUserInteractionListener,
        AddedFragment.AddedInteractionListener, AddedAdapter.AddedAdapterListener,
        ChatListFragment.ChatListInteractionListener, ChatListAdapter.ChatListAdapterListener,
        ChatFragment.ChatInteractionListener, ChatAdapter.ChatAdapterListener,
        MeFragment.MeInteractionListener,
        ReportBugFragment.ReportBugInteractionListener,
        FeedbackFragment.FeedbackInteractionListener,
        AboutFragment.AboutInteractionListener {

    // MARK - Layouts

    lateinit var toolbar: Toolbar
    lateinit var toolbarTitleTextView: TextView
    lateinit var lastNotificationTextView: TextView
    lateinit var drawerLayout: DrawerLayout
    lateinit var drawerToggle: ActionBarDrawerToggle

    lateinit var searchEditText: EditText
    lateinit var searchCancelButton: Button
    lateinit var locationTextView: TextView
    lateinit var leftDrawerLayout: LinearLayout
    lateinit var sideMenuLayoutManager: LinearLayoutManager
    lateinit var sideMenuRecyclerView: RecyclerView
    lateinit var sideMenuAdapter: SideMenuAdapter

    lateinit var rightDrawerLayout: LinearLayout
    lateinit var notificationsToolbar: Toolbar
    lateinit var notificationsLayoutManager: LinearLayoutManager
    lateinit var notificationsRecyclerView: RecyclerView
    lateinit var notificationsAdapter: NotificationsAdapter

    var menu: Menu? = null

    // MARK: - Vars

    var myID: String = "0"
    var currentFragmentTag: String = "default"

    var postIDToPass: String = "0"
    var replyIDToPass: String = "0"
    var userIDToPass: String = "0"
    var didIAddToPass: Boolean = false

    var searchResults: MutableList<User> = mutableListOf()
    var isSideMenuSearchActive: Boolean = false

    var notifications: MutableList<NotificationClass> = mutableListOf()
    var notificationsLastTypeValueListener: ValueEventListener? = null
    var notificationsValueListener: ValueEventListener? = null
    var notificationsScrollPosition: String = "top"
    var notificationsDisplayProgress: Boolean = false
    var isNotificationsObserverRemoved: Boolean = false

    var blockedBy: MutableList<String> = mutableListOf()
    var blocked: MutableList<String> = mutableListOf()
    var blockedValueListener: ValueEventListener? = null

    val ref: DatabaseReference = FirebaseDatabase.getInstance().reference
    val storageRef: StorageReference = FirebaseStorage.getInstance().reference
    var analytics: FirebaseAnalytics? = null

    val misc = Misc()
    var searchHandler = Handler()

    // MARK: - Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        analytics = FirebaseAnalytics.getInstance(this)
        setNavigation()

        myID = misc.setMyID(this)
        if (savedInstanceState == null) {
            if (myID == "0") {
                turnToFragment("Sign Up")
            } else {
                turnToFragment("Home")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (myID != "0") {
            observeLastNotificationType()
            observeBlocked()
        }
    }

    override fun onStop() {
        super.onStop()
        searchHandler.removeCallbacksAndMessages(null)
        removeObserverForNotifications()
        removeObserverForLastNotificationType()
        removeObserverForBlocked()
    }

    // MARK: - Navigation Drawers

    fun setNavigation() {
        toolbar = findViewById(R.id.toolbar)
        toolbar.setOnClickListener{ scrollFragmentToTop(currentFragmentTag) }
        toolbarTitleTextView = findViewById(R.id.toolbar_title)
        lastNotificationTextView = findViewById(R.id.lastNotificationTextView)
        setSupportActionBar(toolbar)
        supportActionBar?.title = null

        drawerLayout = findViewById(R.id.drawer_layout)

        leftDrawerLayout = findViewById(R.id.leftDrawerLayout)
        sideMenuLayoutManager = LinearLayoutManager(this)
        searchEditText = findViewById(R.id.searchEditText)

        val searchEditTextWatcher = object: TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(p0: Editable?) {
//                searchHandler.removeCallbacksAndMessages(null)
//                searchHandler.postDelayed({ ->
//                    val text = editText.text.toString()
//                    if (text != "") {
//                        searchUser()
//                    }
//                }, 1000)
            }
        }
        val searchDoneListener = TextView.OnEditorActionListener { _, p1, _ ->
            if (p1 == EditorInfo.IME_ACTION_SEARCH) {
                searchHandler.removeCallbacksAndMessages(null)
                hideKeyboard()
                searchEditText.clearFocus()
                searchUser()
                true
            } else {
                false
            }
        }

        searchEditText.addTextChangedListener( searchEditTextWatcher )
        searchEditText.setOnEditorActionListener( searchDoneListener )

        searchCancelButton = findViewById(R.id.searchCancelButton)
        searchCancelButton.setOnClickListener { searchCancelButtonClicked() }
        locationTextView = findViewById(R.id.locationTextView)
        locationTextView.setOnClickListener { turnToFragment("Location") }
        sideMenuRecyclerView = findViewById(R.id.sideMenuRecyclerView)
        sideMenuRecyclerView.layoutManager = sideMenuLayoutManager
        sideMenuAdapter = SideMenuAdapter(this, isSideMenuSearchActive, searchResults)
        sideMenuRecyclerView.adapter = sideMenuAdapter
        sideMenuRecyclerView.setHasFixedSize(true)
        val sideMenuDivider = DividerItemDecoration(sideMenuRecyclerView.context, 1)
        sideMenuRecyclerView.addItemDecoration(sideMenuDivider)

        searchEditText.setOnFocusChangeListener {_, b ->
            if (b) {
                dimSideMenuBackground(true)
                if (searchCancelButton.visibility == Button.INVISIBLE) {
                    searchCancelButton.visibility = Button.VISIBLE
                }
            }
        }

        rightDrawerLayout = findViewById(R.id.rightDrawerLayout)
        notificationsLayoutManager = LinearLayoutManager(this)
        notificationsRecyclerView = findViewById(R.id.notificationsRecyclerView)
        notificationsRecyclerView.layoutManager = notificationsLayoutManager
        notificationsAdapter = NotificationsAdapter(this, notifications, notificationsDisplayProgress)
        notificationsRecyclerView.adapter = notificationsAdapter
        notificationsRecyclerView.setHasFixedSize(true)
        val notificationsDivider = DividerItemDecoration(notificationsRecyclerView.context, 1)
        notificationsRecyclerView.addItemDecoration(notificationsDivider)
        notificationsToolbar = findViewById(R.id.notificationsToolbar)
        notificationsToolbar.setOnClickListener { notificationsRecyclerView.scrollToPosition(0) }

        notificationsRecyclerView.addOnScrollListener( notificationsScrollListener )

        drawerToggle = object: ActionBarDrawerToggle(this, drawerLayout, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close) {
            override fun onDrawerOpened(drawerView: View?) {
                super.onDrawerOpened(drawerView)

                hideKeyboard()

                if (drawerView == leftDrawerLayout) {
                    logViewSideMenu()
                    refreshSideMenuRecycler()
                }

                if (drawerView == rightDrawerLayout) {
                    logViewNotifications()
                    notificationsRecyclerView.scrollToPosition(0)
                    clearLastNotificationType()
                    observeNotifications()
                }
            }

            override fun onDrawerClosed(drawerView: View?) {
                super.onDrawerClosed(drawerView)
                if (drawerView == rightDrawerLayout) {
                    clearLastNotificationType()
                }
            }
        }

        drawerToggle.isDrawerIndicatorEnabled = false
        setSideMenuIcon(R.drawable.menu_s)
        drawerToggle.setToolbarNavigationClickListener { openSideMenu() }
        drawerLayout.addDrawerListener(drawerToggle)

        if (myID == "0") {
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, rightDrawerLayout)
            hideLocationTextView(true)
        }
    }

    override fun onBackPressed() {
        val fragmentManager = supportFragmentManager

        if (drawerLayout.isDrawerOpen(leftDrawerLayout)) {
            drawerLayout.closeDrawer(leftDrawerLayout)
        } else if (drawerLayout.isDrawerOpen(rightDrawerLayout)) {
            drawerLayout.closeDrawer(rightDrawerLayout)
        } else if (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStack()
        } else {
            super.onBackPressed()
            System.exit(0)
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        drawerToggle.syncState()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        this.menu = menu!!
        menuInflater.inflate(R.menu.menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        val id = item?.itemId

        val notificationIdArray = arrayOf(R.id.notification_blue_grey_s, R.id.notification_blue_s,
                R.id.notification_green_s, R.id.notification_orange_s, R.id.notification_purple_s,
                R.id.notification_red_s, R.id.notification_s, R.id.notification_teal_s,
                R.id.notification_yellow_s, R.id.upvote_s, R.id.reply_s, R.id.tagged_s,
                R.id.add_s, R.id.chat_s)
        if (notificationIdArray.contains(id)) {
            openNotificationsMenu()
            return true
        }

        val settingsIdArray = arrayOf(R.id.settings_s, R.id.settings_teal_s, R.id.settings_yellow_s)
        if (settingsIdArray.contains(id) && id != null) {
            openSettings(id)
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    fun openSideMenu() {
        misc.playSound(this, R.raw.menu_swish, 322)
        drawerLayout.closeDrawer(rightDrawerLayout)
        drawerLayout.openDrawer(leftDrawerLayout)
    }

    fun openNotificationsMenu() {
        misc.playSound(this, R.raw.menu_swish, 322)
        drawerLayout.closeDrawer(leftDrawerLayout)
        drawerLayout.openDrawer(rightDrawerLayout)
    }

    fun openSettings(id: Int) {
        when (id) {
            R.drawable.settings_teal_s -> {
                showChatSettings()
            }
            R.drawable.settings_yellow_s -> {
                showPostSettings()
            }
            else -> {
                showUserProfileSettings()
            }
        }
    }

    fun closeDrawers() {
        drawerLayout.closeDrawer(leftDrawerLayout)
        drawerLayout.closeDrawer(rightDrawerLayout)
    }

    fun setSideMenuIcon(id: Int) {
        drawerToggle.setHomeAsUpIndicator(id)
    }

    fun setNotificationIcon(ids: Array<Int>) {
        if (menu != null) {
            for (i in 0 until ((menu as Menu).size() - 1)) {
                (menu as Menu).getItem(i).isVisible = false

                val itemId = (menu as Menu).getItem(i).itemId
                if (ids.contains(itemId)) {
                    (menu as Menu).getItem(i).isVisible = true
                }
            }
        }
    }

    fun hideNotificationIcons() {
        if (menu != null) {
            for (i in 0 until ((menu as Menu).size() - 1)) {
                (menu as Menu).getItem(i).isVisible = false
            }
        }
    }

    // MARK: - Navigation

    @SuppressLint("SetTextI18n")
    fun turnToFragment(name: String) {
        closeDrawers()

        if (name == "Sign Up" || name == "Login") {
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, rightDrawerLayout)
            hideLocationTextView(true)
        } else {
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_OPEN, rightDrawerLayout)
            if (isSideMenuSearchActive) {
                hideLocationTextView(true)
            } else {
                hideLocationTextView(false)
            }
        }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val fragmentManager = supportFragmentManager
        if (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
        val transaction = fragmentManager.beginTransaction()
        when (name) {
            "Sign Up" -> {
                setSideMenuIcon(R.drawable.menu_s)
                toolbarTitleTextView.setText(R.string.signUp)
                toolbarTitleTextView.setTextColor(misc.flocalColor)
                hideNotificationIcons()
                transaction.replace(R.id.mainLayout, SignUpFragment(), name)
            }
            "Home" -> {
                setSideMenuIcon(R.drawable.menu_s)
                toolbarTitleTextView.setText(R.string.home)
                toolbarTitleTextView.setTextColor(misc.flocalColor)
                setNotificationIcon(arrayOf(R.drawable.notification_s))
                transaction.replace(R.id.mainLayout, HomeFragment(), name)
            }
            "Reply" -> {
                setSideMenuIcon(R.drawable.menu_yellow_s)
                toolbarTitleTextView.setText(R.string.comments)
                toolbarTitleTextView.setTextColor(misc.flocalYellow)
                setNotificationIcon(arrayOf(R.drawable.settings_yellow_s, R.drawable.notification_yellow_s))
                val bundle = Bundle()
                bundle.putString("postIDToPass", postIDToPass)
                bundle.putString("parentSource", "notification")
                val replyFragment = ReplyFragment()
                replyFragment.arguments = bundle
                transaction.replace(R.id.mainLayout, replyFragment, name)
            }
            "Report Post" -> {
                if (replyIDToPass == "0") {
                    toolbarTitleTextView.setText(R.string.reportComment)
                } else {
                    toolbarTitleTextView.setText(R.string.reportPost)
                }
                toolbarTitleTextView.setTextColor(misc.flocalBlueGrey)
                setNotificationIcon(arrayOf(R.drawable.notification_blue_grey_s))

                val bundle = Bundle()
                bundle.putString("postIDToPass", postIDToPass)
                bundle.putString("replyIDToPass", replyIDToPass)
                val reportPostFragment = ReportPostFragment()
                reportPostFragment.arguments = bundle
                transaction.add(R.id.mainLayout, reportPostFragment, name)
                transaction.addToBackStack("Report Post")
            }
            "Added" -> {
                setSideMenuIcon(R.drawable.menu_green_s)
                toolbarTitleTextView.setText(R.string.peeps)
                toolbarTitleTextView.setTextColor(misc.flocalGreen)
                setNotificationIcon(arrayOf(R.drawable.notification_green_s))
                transaction.replace(R.id.mainLayout, AddedFragment(), name)
            }
            "Me" -> {
                setSideMenuIcon(R.drawable.menu_blue_s)
                toolbarTitleTextView.setText(R.string.me)
                toolbarTitleTextView.setTextColor(misc.flocalBlue)
                setNotificationIcon(arrayOf(R.drawable.notification_blue_s))
                transaction.replace(R.id.mainLayout, MeFragment(), name)
            }
            "Chat List" -> {
                setSideMenuIcon(R.drawable.menu_teal_s)
                toolbarTitleTextView.setText(R.string.chats)
                toolbarTitleTextView.setTextColor(misc.flocalTeal)
                setNotificationIcon(arrayOf(R.drawable.notification_teal_s))
                transaction.replace(R.id.mainLayout, ChatListFragment(), name)
            }
            "Chat" -> {
                val chatID = sharedPreferences.getString("chatIDToPass.flocal", "error")
                val userID = sharedPreferences.getString("userIDToPass.flocal", "error")
                val handle = sharedPreferences.getString("handleToPass.flocal", "error")

                setSideMenuIcon(R.drawable.menu_teal_s)
                toolbarTitleTextView.setText(R.string.chatUpper)
                if (handle != "error") {
                    toolbarTitleTextView.text = "@$handle"
                }
                toolbarTitleTextView.setTextColor(misc.flocalTeal)
                setNotificationIcon(arrayOf(R.drawable.settings_teal_s, R.drawable.notification_teal_s))

                val bundle = Bundle()
                bundle.putString("chatIDToPass", chatID)
                bundle.putString("userIDToPass", userID)
                bundle.putString("chatParentSource", "notification")
                val chatFragment = ChatFragment()
                chatFragment.arguments = bundle
                transaction.replace(R.id.mainLayout, ChatListFragment(), name)
            }
            "Report User" -> {
                toolbarTitleTextView.setText(R.string.reportUser)
                toolbarTitleTextView.setTextColor(misc.flocalBlueGrey)
                setNotificationIcon(arrayOf(R.drawable.notification_blue_grey_s))

                val bundle = Bundle()
                bundle.putString("userIDToPass", userIDToPass)
                val reportUserFragment = ReportUserFragment()
                reportUserFragment.arguments = bundle
                transaction.add(R.id.mainLayout, reportUserFragment, name)
                transaction.addToBackStack("Report User")
            }
            "Report Bug" -> {
                setSideMenuIcon(R.drawable.menu_blue_grey_s)
                toolbarTitleTextView.setText(R.string.reportUser)
                toolbarTitleTextView.setTextColor(misc.flocalBlueGrey)
                setNotificationIcon(arrayOf(R.drawable.notification_blue_grey_s))
                transaction.replace(R.id.mainLayout, ReportBugFragment(), name)
            }
            "Feedback" -> {
                setSideMenuIcon(R.drawable.menu_red_s)
                toolbarTitleTextView.setText(R.string.feedback)
                toolbarTitleTextView.setTextColor(misc.flocalRed)
                setNotificationIcon(arrayOf(R.drawable.notification_red_s))
                transaction.replace(R.id.mainLayout, FeedbackFragment(), name)
            }
            "About" -> {
                setSideMenuIcon(R.drawable.menu_purple_s)
                toolbarTitleTextView.setText(R.string.about)
                toolbarTitleTextView.setTextColor(misc.flocalPurple)
                setNotificationIcon(arrayOf(R.drawable.notification_purple_s))
                transaction.replace(R.id.mainLayout, AboutFragment(), name)
            }
            "Location" -> {
                toolbarTitleTextView.setText(R.string.location)
                toolbarTitleTextView.setTextColor(misc.flocalColor)
                setNotificationIcon(arrayOf(R.drawable.notification_s))
                transaction.add(R.id.mainLayout, LocationFragment(), name)
                transaction.addToBackStack("Location")
            }
            else -> {
                setSideMenuIcon(R.drawable.menu_s)
                hideNotificationIcons()
                transaction.replace(R.id.mainLayout, LoginFragment(), name)
            }
        }
        transaction.commit()

        scrollFragmentToTop(name)
        currentFragmentTag = name
        if (myID != "0") {
            observeLastNotificationType()
        }
    }

    override fun turnToFragmentFromSideMenu(name: String) {
        misc.playSound(this, R.raw.menu_click, 2)
        turnToFragment(name)
    }

    override fun addUserFromSideMenu(index: Int, userID: String) {
        addUser(index, userID)
    }

    override fun logoutFromSideMenu() {
        logOut()
    }

    @SuppressLint("ApplySharedPref")
    override fun turnToFragmentFromNotifications(type: String, postID: String, userID: String, handle: String) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val editor = sharedPreferences.edit()

        when (type) {
            "upvote", "reply", "tagged" -> {
                postIDToPass = postID
                editor.putString("postIDToPass.flocal", postID)
                editor.commit()
                turnToFragment("Reply")
            }
            "chat" -> {
                userIDToPass = userID
                val chatID = misc.setChatID(myID, userID)
                editor.putString("chatIDToPass.flocal", chatID)
                editor.putString("userIDToPass.flocal", userID)
                editor.putString("chatParentSource.flocal", "notification")
                editor.commit()
                turnToFragment("Chat")
            }
            "added" -> {
                editor.putString("addedSegment.flocal", "followers")
                editor.commit()
                turnToFragment("Added")
            }
            else -> {
                Log.d("waht", "notification turn fragment type not recognized")
            }
        }

        observeLastNotificationType()
    }

    fun turnToUserProfile(userID: String, handle: String, chatID: String, parentSource: String) {
        prefetchUserPics(userID)
        userIDToPass = userID
        val fragmentManager = supportFragmentManager
        val transaction = fragmentManager.beginTransaction()
        val bundle = Bundle()
        bundle.putString("chatIDToPass", chatID)
        bundle.putString("userIDToPass", userID)
        bundle.putString("handleToPass", handle)
        bundle.putString("parentSource", parentSource)
        val userProfileFragment = UserProfileFragment()
        userProfileFragment.arguments = bundle
        transaction.add(R.id.mainLayout, userProfileFragment, "User Profile")
        transaction.addToBackStack("User Profile")
        transaction.commit()

        toolbarTitleTextView.setText(R.string.profile)
        toolbarTitleTextView.setTextColor(misc.flocalColor)
        setNotificationIcon(arrayOf(R.drawable.settings_s, R.drawable.notification_s))
        currentFragmentTag = "User Profile"
        observeLastNotificationType()
    }

    fun turnToReply(post: Post, parentSource: String) {
        postIDToPass = post.postID
        val fragmentManager = supportFragmentManager
        val transaction = fragmentManager.beginTransaction()
        val bundle = Bundle()

        val hash: HashMap<String,Any?> = hashMapOf()
        hash.put("postID", post.postID)
        hash.put("userID", post.userID)
        hash.put("type", post.type)
        hash.put("handle", post.handle)
        hash.put("points", post.points)
        hash.put("score", post.score)
        hash.put("voteStatus", post.voteStatus)
        hash.put("content", post.content)
        hash.put("timestamp", post.timestamp)
        hash.put("replyString", post.replyString)
        hash.put("originalReverseTimestamp", post.originalReverseTimestamp)
        hash.put("profilePicURL", post.profilePicURL)
        hash.put("postPicURL", post.postPicURL)
        hash.put("postVidURL", post.postVidURL)
        hash.put("postVidPreviewURL", post.postVidPreviewURL)

        bundle.putSerializable("postToPass", hash)
        bundle.putString("postIDToPass", post.postID)
        bundle.putString("parentSource", parentSource)
        val replyFragment = ReplyFragment()
        replyFragment.arguments = bundle
        transaction.add(R.id.mainLayout, replyFragment, "Reply")
        transaction.addToBackStack("Reply")
        transaction.commit()

        toolbarTitleTextView.setText(R.string.comments)
        toolbarTitleTextView.setTextColor(misc.flocalYellow)
        setNotificationIcon(arrayOf(R.drawable.settings_yellow_s, R.drawable.notification_yellow_s))
        currentFragmentTag = "Reply"
        observeLastNotificationType()
    }

    // MARK: - Fragment Listeners

        // Sign Up

    override fun turnToMeFromSignUp() {
        myID = misc.setMyID(this)
        turnToFragment("Me")
    }

    override fun dismissKeyboardSignUp() {
        hideKeyboard()
    }

        // Login

    override fun turnToMeFromLogin() {
        myID = misc.setMyID(this)
        turnToFragment("Me")
    }

    override fun dismissKeyboardLogin() {
        hideKeyboard()
    }

        // Location

    override fun turnToFragmentFromLocation(name: String) {
        turnToFragment(name)
    }

    override fun dismissKeyboardFromLocation() {
        hideKeyboard()
    }

    override fun popBackStackLocation() {
        val fragmentManager = supportFragmentManager
        fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

        // Home

    override fun turnToFragmentFromHome(name: String) {
        turnToFragment(name)
    }

    override fun dismissKeyboardFromHome() {
        hideKeyboard()
    }

    override fun turnToReplyFromHome(post: Post) {
        turnToReply(post, "home")
    }

    override fun turnToUserProfileFromHome(userID: String?, handle: String) {
        if (userID != null) {
            val chatID = misc.setChatID(myID, userID)
            turnToUserProfile(userID, handle, chatID, "home")
        } else {
            turnToUserFromHandle(handle)
        }
    }

    override fun showImageFromHome(uri: Uri, postID: String) {
        showImage(uri, postID, null, null, null)
    }

    override fun showVideoFromHome(uri: Uri, preview: Uri?, postID: String) {
        showVideo(uri, preview, "post", postID, null, null)
    }

    override fun showImageFromHomeWrite(bitmap: Bitmap) {
        showImageFromWrite(bitmap)
    }

    override fun showVideoFromHomeWrite(uri: Uri, bitmap: Bitmap) {
        showVideoFromWrite(uri, bitmap)
    }

        // Reply

    override fun turnToFragmentFromReply(name: String) {
        turnToFragment(name)
    }

    override fun dismissKeyboardFromReply() {
        hideKeyboard()
    }

    override fun turnToReportPostFromReply(postID: String, replyID: String?) {
        postIDToPass = postID
        replyIDToPass = replyID ?: "0"
        turnToFragment("Report Post")
    }

    override fun turnToUserProfileFromReply(userID: String?, handle: String) {
        if (userID != null) {
            val chatID = misc.setChatID(myID, userID)
            turnToUserProfile(userID, handle, chatID, "reply")
        } else {
            turnToUserFromHandle(handle)
        }
    }

    override fun showImageFromReply(uri: Uri, postID: String) {
        showImage(uri, postID, null, null, null)
    }

    override fun showVideoFromReply(uri: Uri, preview: Uri?, postID: String) {
        showVideo(uri, preview, "post", postID, null, null)
    }

        // Report Post

    override fun popBackStackReportPost() {
        val fragmentManager = supportFragmentManager
        fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    override fun dismissKeyboardFromReportPost() {
        hideKeyboard()
    }

        // User Profile

    override fun setUserFromUserProfile(userID: String, didIAdd: Boolean) {
        userIDToPass = userID
        didIAddToPass = didIAdd
    }

    override fun turnToReplyFromUserProfile(post: Post) {
        turnToReply(post, "userProfile")
    }

    override fun showImageFromUserProfile(uri: Uri, postID: String) {
        hideKeyboard()
        showImage(uri, "post", postID, null, null)
    }

    override fun showVideoFromUserProfile(uri: Uri, preview: Uri?, postID: String) {
        hideKeyboard()
        showVideo(uri, preview, "post", postID, null, null)
    }

    override fun turnToFragmentFromUserProfile(name: String) {
        turnToFragment(name)
    }

    override fun popBackStackFromUserProfile() {
        val fragmentManager = supportFragmentManager
        fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    override fun dismissKeyboardFromUserProfile() {
        hideKeyboard()
    }

        // Added

    override fun turnToFragmentFromAdded(name: String) {
        turnToFragment(name)
    }

    override fun turnToUserProfileFromAdded(userID: String, handle: String, chatID: String) {
        turnToUserProfile(userID, handle, chatID, "added")
    }

        // Chat List

    @SuppressLint("SetTextI18n")
    override fun turnToChatFromChatList(chatID: String, userID: String, handle: String, type: String, messageID: String) {
        prefetchChatPic(chatID, type, messageID)

        val fragmentManager = supportFragmentManager
        val transaction = fragmentManager.beginTransaction()
        val bundle = Bundle()
        bundle.putString("chatIDToPass", chatID)
        bundle.putString("userIDToPass", userID)
        bundle.putString("chatParentSource", "chatList")
        val chatFragment = ChatFragment()
        chatFragment.arguments = bundle
        transaction.add(R.id.mainLayout, chatFragment, "Chat")
        transaction.addToBackStack("Chat")
        transaction.commit()

        toolbarTitleTextView.text = "@$handle"
        toolbarTitleTextView.setTextColor(misc.flocalTeal)
        setNotificationIcon(arrayOf(R.drawable.settings_teal_s, R.drawable.notification_teal_s))
        currentFragmentTag = "Chat"
        observeLastNotificationType()
    }

    override fun turnToUserProfileFromChatList(userID: String, handle: String, chatID: String) {
        turnToUserProfile(userID, handle, chatID, "chatList")
    }

    override fun turnToFragmentFromChatList(name: String) {
        turnToFragment(name)
    }

        // Chat

    override fun showImageFromChat(uri: Uri, chatID: String, messageID: String) {
        hideKeyboard()
        showImage(uri, "chat", null, chatID, messageID)
    }

    override fun showVideoFromChat(uri: Uri, preview: Uri?, chatID: String, messageID: String) {
        hideKeyboard()
        showVideo(uri, preview, "chat", null, chatID, messageID)
    }

    override fun setUserIDFromChat(userID: String) {
        userIDToPass = userID
    }

    override fun turnToFragmentFromChat(name: String) {
        hideKeyboard()
        turnToFragment(name)
    }

    override fun dismissKeyboardFromChat() {
        hideKeyboard()
    }

        // Me

    override fun turnToFragmentFromMe(name: String) {
        turnToFragment(name)
    }

    override fun dismissKeyboardMe() {
        hideKeyboard()
    }

        // Report User

    override fun dismissReportUser() {
        supportFragmentManager.popBackStack("Report User", FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    override fun dismissKeyboardFromReportUser() {
        hideKeyboard()
    }

        // Report Bug

    override fun turnToFragmentFromReportBug(name: String) {
        turnToFragment(name)
    }

    override fun dismissKeyboardFromReportBug() {
        hideKeyboard()
    }

        // Feedback

    override fun turnToFragmentFromFeedback(name: String) {
        turnToFragment(name)
    }

    override fun dismissKeyboardFromFeedback() {
        hideKeyboard()
    }

        // About

    override fun turnToFragmentFromAbout(name: String) {
        turnToFragment(name)
    }

    // MARK: - Search

    fun searchCancelButtonClicked() {
        searchEditText.setText("")
        searchEditText.clearFocus()
        searchCancelButton.visibility = Button.INVISIBLE
        hideLocationTextView(false)
        isSideMenuSearchActive = false
        dimSideMenuBackground(false)

        searchResults = mutableListOf()
        refreshSideMenuRecycler()
    }

    // MARK: - Scroll

    val notificationsScrollListener = object: RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)

            if (!isNotificationsObserverRemoved) {
                removeObserverForNotifications()
            }

            val firstVisibleItem = notificationsLayoutManager.findFirstCompletelyVisibleItemPosition()
            val lastVisibleItem = notificationsLayoutManager.findLastCompletelyVisibleItemPosition()
            if (firstVisibleItem <= 0) {
                notificationsScrollPosition = "top"
                observeNotifications()
            } else if (lastVisibleItem == notifications.size - 1) {
                notificationsScrollPosition = "bottom"
                if (notifications.size >= 8) {
                    notificationsDisplayProgress = true
                    refreshNotificationsRecycler()
                    recyclerView?.scrollToPosition(notifications.size)
                    observeNotifications()
                }
            } else {
                notificationsScrollPosition = "middle"
            }
        }
    }

    fun scrollFragmentToTop(name: String) {
        val fragmentManager = supportFragmentManager
        when(name) {
            "Home" -> {
                val homeFragment = fragmentManager.findFragmentByTag(name) as? HomeFragment
                homeFragment?.scrollToTop()
            }
            "Reply" -> {
                val replyFragment = fragmentManager.findFragmentByTag(name) as? ReplyFragment
                replyFragment?.scrollToTop()
            }
            "User Profile" -> {
                val userProfileFragment = fragmentManager.findFragmentByTag(name) as? UserProfileFragment
                userProfileFragment?.scrollToTop()
            }
            "Added" -> {
                val addedFragment = fragmentManager.findFragmentByTag(name) as? AddedFragment
                addedFragment?.scrollToTop()
            }
            "Chat List" -> {
                val chatListFragment = fragmentManager.findFragmentByTag(name) as? ChatListFragment
                chatListFragment?.scrollToTop()
            }
            "Chat" -> {
                val chatFragment = fragmentManager.findFragmentByTag(name) as? ChatFragment
                chatFragment?.scrollToTop()
            }
            else -> { return }
        }
    }

    // MARK: - Popup

    fun showImageFromWrite(bitmap: Bitmap) {
        hideKeyboard()
        val layoutInflater: LayoutInflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val viewGroup: ViewGroup = findViewById(android.R.id.content)
        val inflatedView: View = layoutInflater.inflate(R.layout.popup_view_image, viewGroup, false)

        val popupWindow = PopupWindow(inflatedView, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, true)

        val xButton: Button = inflatedView.findViewById(R.id.xButton)
        xButton.setOnClickListener {
            popupWindow.dismiss()
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        val imageView: ImageView = inflatedView.findViewById(R.id.imageView)
        imageView.setImageBitmap(bitmap)

        val height = bitmap.height
        val width = bitmap.width
        if (width > height) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        popupWindow.showAtLocation(drawerLayout, Gravity.CENTER, 0,0)
    }

    fun showVideoFromWrite(uri: Uri, preview: Bitmap) {
        hideKeyboard()
        val layoutInflater: LayoutInflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val viewGroup: ViewGroup = findViewById(android.R.id.content)
        val inflatedView: View = layoutInflater.inflate(R.layout.popup_view_video, viewGroup, false)

        val popupWindow = PopupWindow(inflatedView, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, true)

        val xButton: Button = inflatedView.findViewById(R.id.xButton)
        xButton.setOnClickListener {
            popupWindow.dismiss()
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        val videoView: VideoView = inflatedView.findViewById(R.id.videoView)
        videoView.setVideoURI(uri)
        videoView.setOnClickListener { _ ->
            if (sendImageView.visibility == ImageView.INVISIBLE) {
                videoView.pause()
                sendImageView.visibility = ImageView.VISIBLE
            } else {
                videoView.resume()
                sendImageView.visibility = ImageView.INVISIBLE
            }
        }
        videoView.setOnPreparedListener {mediaPlayer ->
            mediaPlayer.isLooping = true
        }

        val sendVideoImageView: ImageView = inflatedView.findViewById(R.id.sendImageView)
        sendVideoImageView.visibility = ImageView.INVISIBLE
        sendVideoImageView.setOnClickListener { _ ->
            videoView.resume()
            sendImageView.visibility = ImageView.INVISIBLE
        }

        val height = preview.height
        val width = preview.width
        if (width > height) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        popupWindow.showAtLocation(drawerLayout, Gravity.CENTER, 0,0)
        videoView.start()
    }


    fun showImage(uri: Uri, type: String, postID: String?, chatID: String?, messageID: String?) {
        hideKeyboard()
        val layoutInflater: LayoutInflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val viewGroup: ViewGroup = findViewById(android.R.id.content)
        val inflatedView: View = layoutInflater.inflate(R.layout.popup_view_image, viewGroup, false)

        val popupWindow = PopupWindow(inflatedView, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, true)

        val xButton: Button = inflatedView.findViewById(R.id.xButton)
        xButton.setOnClickListener {
            popupWindow.dismiss()
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        val imageView: ImageView = inflatedView.findViewById(R.id.imageView)
        Picasso.with(this).load(uri).into(imageView)

        logViewImage(type, postID, chatID, messageID)

        val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
        val height = bitmap.height
        val width = bitmap.width
        if (width > height) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        popupWindow.showAtLocation(drawerLayout, Gravity.CENTER, 0,0)
    }

    fun showVideo(uri: Uri, preview: Uri?, type: String, postID: String?, chatID: String?, messageID: String?) {
        hideKeyboard()
        val layoutInflater: LayoutInflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val viewGroup: ViewGroup = findViewById(android.R.id.content)
        val inflatedView: View = layoutInflater.inflate(R.layout.popup_view_video, viewGroup, false)

        val popupWindow = PopupWindow(inflatedView, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, true)

        val xButton: Button = inflatedView.findViewById(R.id.xButton)
        xButton.setOnClickListener {
            popupWindow.dismiss()
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        val videoView: VideoView = inflatedView.findViewById(R.id.videoView)
        videoView.setVideoURI(uri)
        videoView.setOnClickListener { _ ->
            if (sendImageView.visibility == ImageView.INVISIBLE) {
                videoView.pause()
                sendImageView.visibility = ImageView.VISIBLE
            } else {
                videoView.resume()
                sendImageView.visibility = ImageView.INVISIBLE
            }
        }
        videoView.setOnPreparedListener {mediaPlayer ->
            mediaPlayer.isLooping = true
        }

        val sendVideoImageView: ImageView = inflatedView.findViewById(R.id.sendImageView)
        sendVideoImageView.visibility = ImageView.INVISIBLE
        sendVideoImageView.setOnClickListener { _ ->
                videoView.resume()
                sendImageView.visibility = ImageView.INVISIBLE
        }

        logViewVideo(type, postID, chatID, messageID)

        if (preview != null) {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, preview)
            val height = bitmap.height
            val width = bitmap.width
            if (width > height) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
        popupWindow.showAtLocation(drawerLayout, Gravity.CENTER, 0,0)
        videoView.start()
    }

    // MARK: - Misc

    fun Activity.hideKeyboard() {
        val imm = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(this.currentFocus.windowToken, 0)
    }

    fun hideLocationTextView(bool: Boolean) {
        if (bool) {
            locationTextView.layoutParams.height = 0
        } else {
            locationTextView.layoutParams.height = resources.getDimension(R.dimen.locationHeight).toInt()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            val view = currentFocus
            if (view is EditText) {
                val outRect = Rect()
                view.getGlobalVisibleRect(outRect)
                if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    view.clearFocus()
                    hideKeyboard()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    fun alert(title: String, message: String) {
        misc.displayAlert(this, title, message)
    }

    fun dimSideMenuBackground(bool: Boolean) {
        sideMenuRecyclerView.alpha = 1f
        searchEditText.alpha = 1f
        if (bool) {
            sideMenuRecyclerView.alpha = 0.25f
            searchEditText.alpha = 0.25f
        }
    }

    fun refreshSideMenuRecycler() {
        sideMenuAdapter = SideMenuAdapter(this@MainActivity, isSideMenuSearchActive, searchResults)
        sideMenuRecyclerView.adapter = sideMenuAdapter
        sideMenuRecyclerView.adapter.notifyDataSetChanged()
    }

    fun refreshNotificationsRecycler() {
        notificationsAdapter = NotificationsAdapter(this@MainActivity, notifications, notificationsDisplayProgress)
        notificationsRecyclerView.adapter = notificationsAdapter
        notificationsRecyclerView.adapter.notifyDataSetChanged()
    }

    fun showPostSettings() {
        val sheet = ActionSheet.createBuilder(this, supportFragmentManager)
        sheet.setCancelButtonTitle("Cancel")
        sheet.setCancelableOnTouchOutside(true)
        if (myID == userIDToPass) {
            sheet.setOtherButtonTitles("Edit", "Delete")
            sheet.setListener( object: ActionSheet.ActionSheetListener {
                override fun onOtherButtonClick(actionSheet: ActionSheet?, index: Int) {
                    when (index) {
                        0 -> { turnToFragment("Edit Post") }
                        1 -> { deletePost() }
                        else -> { actionSheet?.dismiss() }
                    }
                }
                override fun onDismiss(actionSheet: ActionSheet?, isCancel: Boolean) {}
            })
        } else {
            sheet.setOtherButtonTitles("Report Post")
            sheet.setListener( object: ActionSheet.ActionSheetListener {
                override fun onOtherButtonClick(actionSheet: ActionSheet?, index: Int) {
                    when (index) {
                        0 -> { turnToFragment("Report Post") }
                        else -> { actionSheet?.dismiss() }
                    }
                }
                override fun onDismiss(actionSheet: ActionSheet?, isCancel: Boolean) {}
            })
        }
        sheet.show()
    }

    fun showChatSettings() {
        val sheet = ActionSheet.createBuilder(this, supportFragmentManager)
        sheet.setCancelButtonTitle("Cancel")
        sheet.setCancelableOnTouchOutside(true)
        val didIBlock = misc.didIBlock(userIDToPass, blocked)
        if (didIBlock) {
            sheet.setOtherButtonTitles("Report User", "Unblock User")
        } else {
            sheet.setOtherButtonTitles("Report User", "Block User")
        }
        sheet.setListener( object: ActionSheet.ActionSheetListener {
            override fun onOtherButtonClick(actionSheet: ActionSheet?, index: Int) {
                when (index) {
                    0 -> {
                        turnToFragment("Report User")
                    }
                    1 -> {
                        if (didIBlock) {
                            unblockUser()
                        } else {
                            blockUser()
                        }
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

    fun showUserProfileSettings() {
        val sheet = ActionSheet.createBuilder(this, supportFragmentManager)
        sheet.setCancelButtonTitle("Cancel")
        sheet.setCancelableOnTouchOutside(true)
        val didIBlock = misc.didIBlock(userIDToPass, blocked)
        if (didIBlock) {
            if (didIAddToPass) {
                sheet.setOtherButtonTitles("Report User", "Unblock User", "Remove from Added")
            } else {
                sheet.setOtherButtonTitles("Report User", "Unblock User")
            }
        } else {
            if (didIAddToPass) {
                sheet.setOtherButtonTitles("Report User", "Block User", "Remove from Added")
            } else {
                sheet.setOtherButtonTitles("Report User", "Block User")
            }
        }
        sheet.setListener(object : ActionSheet.ActionSheetListener {
            override fun onOtherButtonClick(actionSheet: ActionSheet?, index: Int) {
                when (index) {
                    0 -> {
                        turnToFragment("Report User")
                    }
                    1 -> {
                        if (didIBlock) {
                            unblockUser()
                        } else {
                            blockUser()
                        }
                    }
                    2 -> {
                        if (didIAddToPass) {
                            removeAddedUser()
                        }
                    }
                    else -> { actionSheet?.dismiss() }
                }
            }
            override fun onDismiss(actionSheet: ActionSheet?, isCancel: Boolean) {}
        })
        sheet.show()
    }

    // MARK: - Analytics

    fun logViewSideMenu() {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        analytics?.logEvent("viewSideMenu_Android", bundle)
    }

    fun logSearch(term: String) {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        bundle.putString("searchTerm", term)
        bundle.putString("searchTermLower", term.toLowerCase())
        analytics?.logEvent("viewSearchResults_Android", bundle)
    }

    fun logAddedUser(addedID: String) {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        bundle.putString("userID", addedID)
        analytics?.logEvent("addedUser_Android", bundle)
    }

    fun logRemoveAddedUser(addedID: String) {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        bundle.putString("userID", addedID)
        analytics?.logEvent("removeAddedUser_Android", bundle)
    }

    fun logViewNotifications() {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        analytics?.logEvent("viewNotifications_Android", bundle)
    }


    fun logViewImage(type: String, postID: String?, chatID: String?, messageID: String?) {
        val t = type.capitalize()
        val child = "view$t" + "Image_Android"
        val bundle = Bundle()
        bundle.putString("myID", myID)
        when (type) {
            "post", "reply" -> {
                val pid = postID ?: "error"
                bundle.putString("postID", pid)
            }
            "chat" -> {
                val cid = chatID ?: "error"
                val mid = messageID ?: "error"
                bundle.putString("chatID", cid)
                bundle.putString("messageID", mid)
            }
            else -> {}
        }
        analytics?.logEvent(child, bundle)
    }

    fun logViewVideo(type: String, postID: String?, chatID: String?, messageID: String?) {
        val t = type.capitalize()
        val child = "view$t" + "Video_Android"
        val bundle = Bundle()
        bundle.putString("myID", myID)
        when (type) {
            "post", "reply" -> {
                val pid = postID ?: "error"
                bundle.putString("postID", pid)
            }
            "chat" -> {
                val cid = chatID ?: "error"
                val mid = messageID ?: "error"
                bundle.putString("chatID", cid)
                bundle.putString("messageID", mid)
            }
            else -> {}
        }
        analytics?.logEvent(child, bundle)
    }

    fun logBlockedUser(userID: String) {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        bundle.putString("userID", userID)
        analytics?.logEvent("blockedUser_Android", bundle)
    }

    fun logUnblockedUser(userID: String) {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        bundle.putString("userID", userID)
        analytics?.logEvent("unblockedUser_Android", bundle)
    }

    // MARK: - Storage

    fun prefetchUserPics(userID: String) {
        val backgroundPicRef = storageRef.child("backgroundPic/$userID.jpg")
        backgroundPicRef.downloadUrl.addOnSuccessListener { uri ->
            Picasso.with(this).load(uri).fetch()
        }.addOnFailureListener { error ->  Log.d("downloadError", error.toString()) }

        val child = userID + "_large"
        val userPicRef = storageRef.child("profilePic/$child.jpg")
        userPicRef.downloadUrl.addOnSuccessListener { uri ->
            Picasso.with(this).load(uri).fetch()
        }.addOnFailureListener { error ->  Log.d("downloadError", error.toString()) }
    }

    fun prefetchChatPic(chatID: String, type: String, messageID: String) {
        if (type == "image" || type == "video") {
            val picRef: StorageReference
            when (type) {
                "video" -> {
                    picRef = storageRef.child("chatVidPreview/$chatID/$messageID.jpg")
                }
                else -> {
                    picRef = storageRef.child("chatPic/$chatID/$messageID.jpg")
                }
            }

            picRef.downloadUrl.addOnSuccessListener { uri ->
                Picasso.with(this).load(uri).fetch()
            }.addOnFailureListener { error -> Log.d("downloadError", error.toString()) }
        }
    }

    // MARK: - Firebase

    fun searchUser() {
        isSideMenuSearchActive = true
        hideLocationTextView(true)

        val term = searchEditText.text.toString()
        logSearch(term)
        val termLower = term.toLowerCase()
        val handleLower = misc.handlesWithoutAt(termLower).first()

        val searchListener = object: ValueEventListener {
            override fun onDataChange(snap: DataSnapshot?) {
                val results: MutableList<User> = mutableListOf()

                val dict = snap?.value as? MutableMap<String,Any> ?: mutableMapOf()
                for ((userID,value) in dict) {
                    val info = value as? MutableMap<String, Any> ?: mutableMapOf()

                    val amIBlocked = misc.amIBlocked(userID, blockedBy)
                    if (!amIBlocked && (myID != userID)) {
                        val user = User()

                        user.userID = userID
                        user.handle = info["handle"] as? String ?: "error"

                        val profilePicURLString = info["profilePicURLString"] as? String ?: "error"
                        if (profilePicURLString != "error") {
                            user.profilePicURL = Uri.parse(profilePicURLString)
                        }

                       misc.didIAdd(userID, myID) { didIAdd ->
                           user.didIAdd = didIAdd
                       }

                        results.add(user)
                    }
                }

                searchResults = results
                dimSideMenuBackground(false)
                refreshSideMenuRecycler()
            }
            override fun onCancelled(error: DatabaseError?) {
                Log.d("DatabaseError", error.toString())
                alert("Error", error.toString())
            }
        }
        val userRef = ref.child("users")
        userRef.orderByChild("handleLower").startAt(handleLower).endAt(handleLower + "\\u{f8ff}")
                .limitToFirst(21).addListenerForSingleValueEvent(searchListener)
    }

    fun turnToUserFromHandle(handle: String) {
        val handleLower = handle.toLowerCase().trim()
        val userRef = ref.child("users")
        userRef.orderByChild("handleLower").equalTo(handleLower).addListenerForSingleValueEvent( object: ValueEventListener {
            override fun onDataChange(snap: DataSnapshot?) {
                if (snap?.value != null) {
                    val users = snap.value as? MutableMap<String,Any> ?: mutableMapOf()
                    if (!users.isEmpty()) {
                        val uid = users.keys.toMutableList()[0]
                        val chatID = misc.setChatID(myID, uid)
                        turnToUserProfile(uid, handle, chatID, "home")
                    }
                } else {
                    alert("Handle Not Found", "We could not find this handle. Either the tag is incorrect or we messed up.")
                    return
                }
            }
            override fun onCancelled(error: DatabaseError?) { Log.d("DatabaseError", error.toString()) }
        })
    }

    fun addUser(index: Int, userID: String) {
        val amIBlocked = misc.amIBlocked(userID, blockedBy)
        if (!amIBlocked) {
            misc.playSound(this, R.raw.added_sound, 0)
            searchResults[index].didIAdd = true
            refreshSideMenuRecycler()

            misc.addUser(userID, myID)
            misc.writeAddedNotification(this@MainActivity, userID, myID)
            logAddedUser(userID)
        } else {
            alert("Blocked", "This person has blocked you. You cannot add them.")
            return
        }
    }

    fun removeAddedUser() {
        didIAddToPass = false

        val userRef = ref.child("users")
        val userAddedRef = ref.child("userAdded")
        val userFollowersRef = ref.child("userFollowers")

        userAddedRef.child(myID).child(userIDToPass).removeValue()
        userFollowersRef.child(userIDToPass).child(myID).removeValue()

        val userProfileFragment = supportFragmentManager.findFragmentByTag("User Profile") as? UserProfileFragment
        userProfileFragment?.observeUserProfile()

        var updatedFollowersCount = 0
        userRef.runTransaction( object: Transaction.Handler {
            override fun doTransaction(data: MutableData?): Transaction.Result {
                val user = data?.value as? MutableMap<String,Any> ?: return Transaction.success(data)
                var count = user["followersCount"] as? Int ?: 0
                count -= 1
                updatedFollowersCount = count
                user["followersCount"] = count
                data.value = user
                return Transaction.success(data)
            }
            override fun onComplete(p0: DatabaseError?, p1: Boolean, p2: DataSnapshot?) {}
        })

        misc.getFollowers(userIDToPass) { userFollowers ->
            if (!userFollowers.isEmpty()) {
                val fanoutObject = mutableMapOf<String,Any>()
                for (followerID in userFollowers) {
                    fanoutObject.put("/$followerID/$userIDToPass/followersCount", updatedFollowersCount)
                }
                userAddedRef.updateChildren(fanoutObject)
            }
        }

        logRemoveAddedUser(userIDToPass)
    }

    fun clearLastNotificationType() {
        val userRef = ref.child("users").child(myID)
        userRef.child("notificationBadge").setValue(0)
        userRef.child("lastNotificationType").setValue("clear")
        val notManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notManager.cancelAll()

        when (currentFragmentTag) {
            "Reply" -> { setNotificationIcon(arrayOf(R.drawable.settings_yellow_s, R.drawable.notification_yellow_s)) }
            "About" -> { setNotificationIcon(arrayOf(R.drawable.notification_purple_s)) }
            "User Profile" -> { setNotificationIcon(arrayOf(R.drawable.settings_s, R.drawable.notification_s)) }
            "Added" -> { setNotificationIcon(arrayOf(R.drawable.notification_green_s)) }
            "Chat List" -> { setNotificationIcon(arrayOf(R.drawable.notification_teal_s)) }
            "Chat" -> { setNotificationIcon(arrayOf(R.drawable.settings_teal_s, R.drawable.notification_teal_s)) }
            "Me" -> { setNotificationIcon(arrayOf(R.drawable.notification_blue_s)) }
            "Report User", "Report Bug", "Report Post" -> { setNotificationIcon(arrayOf(R.drawable.notification_blue_grey_s)) }
            "Feedback" -> { setNotificationIcon(arrayOf(R.drawable.notification_red_s)) }
            "Sign Up", "Login" -> { hideNotificationIcons() }
            else -> { setNotificationIcon(arrayOf(R.drawable.notification_s)) }
        }
    }

    fun observeNotifications() {
        removeObserverForNotifications()
        isNotificationsObserverRemoved = false

        val reverseTimestamp: Double
        val currentReverseTimestamp = misc.getCurrentReverseTimestamp()
        val lastReverseTimestamp = notifications.lastOrNull()?.originalReverseTimestamp
        val lastNotificationID = notifications.lastOrNull()?.notificationID

        if (notificationsScrollPosition == "bottom") {
            reverseTimestamp = lastReverseTimestamp ?: currentReverseTimestamp
        } else {
            reverseTimestamp = currentReverseTimestamp
        }

        notificationsValueListener = object: ValueEventListener {
            override fun onDataChange(snap: DataSnapshot?) {
                val notificationsList: MutableList<NotificationClass> = mutableListOf()

                val dict = snap?.value as? MutableMap<String,Any> ?: mutableMapOf()
                for ((notificationID,value) in dict) {
                    val info = value as? MutableMap<String,Any> ?: mutableMapOf()

                    val notification = NotificationClass()

                    notification.notificationID = notificationID
                    notification.type = info["type"] as? String ?: "error"
                    notification.postID = info["postID"] as? String ?: "error"
                    notification.userID = info["userID"] as? String ?: "error"
                    notification.handle = info["handle"] as? String ?: "error"
                    notification.message = info["notification"] as? String ?: "error"

                    val timestamp = info["timestamp"] as? String ?: "error"
                    notification.timestamp = misc.formatTimestamp(timestamp)
                    notification.originalReverseTimestamp = info["originalReverseTimestamp"] as? Double ?: 0.0

                    notificationsList.add(notification)
                }

                if (notificationsScrollPosition == "bottom") {
                    if (lastNotificationID != notificationsList.lastOrNull()?.notificationID) {
                        notifications.addAll(notificationsList)
                    }
                } else {
                    notifications = notificationsList
                }
                notificationsDisplayProgress = false
                refreshNotificationsRecycler()
            }
            override fun onCancelled(error: DatabaseError?) {
                Log.d("DatabaseError", error.toString())
                alert("Error", error.toString())
            }
        }
        val notificationsRef = ref.child("userNotifications").child(myID)
        notificationsRef.orderByChild("originalReverseTimestamp").startAt(reverseTimestamp)
                .limitToFirst(88).addValueEventListener(notificationsValueListener)
    }

    fun removeObserverForNotifications() {
        isNotificationsObserverRemoved = true
        val notificationsRef = ref.child(myID).child("notifications")
        if (notificationsValueListener != null) {
            notificationsRef.removeEventListener(notificationsValueListener)
            notificationsValueListener = null
        }
    }

    fun observeLastNotificationType() {
        removeObserverForLastNotificationType()

        notificationsLastTypeValueListener = object: ValueEventListener {
            override fun onDataChange(snap: DataSnapshot?) {
                val type = snap?.value as? String ?: "error"

                val fade = AlphaAnimation(1f, 0.1f)
                fade.duration = 2500
                fade.repeatMode = Animation.REVERSE
                fade.repeatCount = Animation.INFINITE

                when (type) {
                    "upvote" -> {
                        lastNotificationTextView.setBackgroundColor(misc.flocalOrange)
                        lastNotificationTextView.animation = fade
                        lastNotificationTextView.animation.start()
                        setNotificationIcon(arrayOf(R.drawable.upvote_s))
                    }
                    "reply" -> {
                        lastNotificationTextView.setBackgroundColor(misc.flocalYellow)
                        lastNotificationTextView.animation = fade
                        lastNotificationTextView.animation.start()
                        setNotificationIcon(arrayOf(R.drawable.reply_s))
                    }
                    "tagged" -> {
                        lastNotificationTextView.setBackgroundColor(misc.flocalYellow)
                        lastNotificationTextView.animation = fade
                        lastNotificationTextView.animation.start()
                        setNotificationIcon(arrayOf(R.drawable.tagged_s))
                    }
                    "chat" -> {
                        lastNotificationTextView.setBackgroundColor(misc.flocalTeal)
                        lastNotificationTextView.animation = fade
                        lastNotificationTextView.animation.start()
                        setNotificationIcon(arrayOf(R.drawable.chat_s))
                    }
                    "added" -> {
                        lastNotificationTextView.setBackgroundColor(misc.flocalGreen)
                        lastNotificationTextView.animation = fade
                        lastNotificationTextView.animation.start()
                        setNotificationIcon(arrayOf(R.drawable.add_s))
                    }
                    else -> {
                        lastNotificationTextView.alpha = 0f
                        lastNotificationTextView.animation.cancel()
                        clearLastNotificationType()
                    }
                }
            }
            override fun onCancelled(error: DatabaseError?) { Log.d("DatabaseError", error.toString()) }
        }
        val lastTypeRef = ref.child(myID).child("lastNotificationType")
        lastTypeRef.addValueEventListener(notificationsLastTypeValueListener)
    }

    fun removeObserverForLastNotificationType() {
        val lastTypeRef = ref.child(myID).child("lastNotificationType")
        if (notificationsLastTypeValueListener != null) {
            lastTypeRef.removeEventListener(notificationsLastTypeValueListener)
            notificationsLastTypeValueListener = null
        }
    }

    fun blockUser() {
        val blockedRef = ref.child("userBlocked")

        val userBlockedRef = blockedRef.child(userIDToPass)
        userBlockedRef.child("blockedBy").child(myID).setValue(true)

        val myBlockedRef = blockedRef.child(myID)
        myBlockedRef.child("blocked").child(userIDToPass).setValue(true)

        val userAddedRef = ref.child("userAdded")
        userAddedRef.child(myID).child(userIDToPass).removeValue()
        userAddedRef.child(userIDToPass).child(myID).removeValue()

        val userFollowersRef = ref.child("userFollowers")
        userFollowersRef.child(myID).child(userIDToPass).removeValue()
        userFollowersRef.child(userIDToPass).child(myID).removeValue()

        val chatID = misc.setChatID(myID, userIDToPass)
        val userChatListRef = ref.child("userChatList")
        userChatListRef.child(myID).child(chatID).removeValue()
        userChatListRef.child(userIDToPass).child(chatID).removeValue()

        logBlockedUser(userIDToPass)
        alert("User Blocked", "You have blocked this person.")
    }

    fun unblockUser() {
        val blockedRef = ref.child("userBlocked")

        val userBlockedRef = blockedRef.child(userIDToPass)
        userBlockedRef.child("blockedBy").child(myID).removeValue()

        val myBlockedRef = blockedRef.child(myID)
        myBlockedRef.child("blocked").child(userIDToPass).removeValue()

        logUnblockedUser(userIDToPass)
        alert("Unblocked", "You have unblocked this person.")
    }

    fun deletePost() {
        val postRef = ref.child("posts").child(postIDToPass)
        postRef.child("isDeleted").setValue(true)

        val replyFragment = supportFragmentManager.findFragmentByTag("Reply") as ReplyFragment
        replyFragment.deletedPostFromSettings()
    }

    @SuppressLint("ApplySharedPref")
    fun logOut() {
        myID = "0"
        turnToFragment("Login")

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val editor = sharedPreferences.edit()
        editor.putString("myID.flocal", "0")
        editor.commit()
        isSideMenuSearchActive = false
        refreshSideMenuRecycler()

        val lruCache = LruCache(this)
        Picasso.Builder(this).memoryCache(lruCache).build()
        lruCache.clear()

        FirebaseAuth.getInstance().signOut()
    }

    fun observeBlocked() {
        blockedValueListener = object: ValueEventListener {
            override fun onDataChange(snap: DataSnapshot?) {
                val dict = snap?.value as? MutableMap<String,Any> ?: mutableMapOf()

                val blockedByDict = dict["blockedBy"] as? MutableMap<String,Boolean> ?: mutableMapOf()
                blockedBy = blockedByDict.keys.toMutableList()

                val blockedDict = dict["blocked"] as? MutableMap<String,Boolean> ?: mutableMapOf()
                blocked = blockedDict.keys.toMutableList()
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


