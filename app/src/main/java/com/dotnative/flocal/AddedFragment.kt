@file:Suppress("UNCHECKED_CAST", "SetTextI18n", "MemberVisibilityCanPrivate", "CascadeIf", "LiftReturnOrAssignment", "UnnecessaryVariable", "SetTextI18n")

package com.dotnative.flocal

import android.app.AlertDialog
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.TextView
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQuery
import com.firebase.geofire.GeoQueryEventListener
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.database.*
import info.hoang8f.android.segmented.SegmentedGroup
import java.io.IOException
import java.util.*

class AddedFragment : Fragment() {

    // MARK - Layout

    lateinit var followersTextView: TextView
    lateinit var layoutManager: LinearLayoutManager
    lateinit var recyclerView: RecyclerView
    lateinit var adapter: AddedAdapter
    lateinit var segmentedGroup: SegmentedGroup

    // MARK: - Vars

    private var addedInteractionListener: AddedInteractionListener? = null

    var myID: String = "0"
    var locals: MutableList<User> = mutableListOf()
    var added: MutableList<User> = mutableListOf()
    var followers: MutableList<User> = mutableListOf()
    var selectedSegment: String = "locals"
    var addedValueListener: ValueEventListener? = null
    var followersValueListener: ValueEventListener? = null
    var followersCountValueListener: ValueEventListener? = null

    var blockedBy: MutableList<String> = mutableListOf()
    var blockedValueListener: ValueEventListener? = null

    var addedIDs: MutableList<String> = mutableListOf()

    var radiusMeters: Double = 2404.02
    var locationManager: LocationManager? = null
    var locationListener: LocationListener? = null
    var longitude: Double = -122.258542
    var latitude: Double = 37.871906

    var scrollPosition: String = "top"
    var displayProgress: Boolean = false
    var isRemoved: Boolean = false

    var ref: DatabaseReference = FirebaseDatabase.getInstance().reference
    var analytics: FirebaseAnalytics? = null
    var geoFireUsers: GeoFire = GeoFire(ref.child("users_location"))
    var geoQuery: GeoQuery? = null

    val misc = Misc()

    // MARK: - Lifecycle

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context is AddedInteractionListener) {
            addedInteractionListener = context
        } else {
            throw RuntimeException(context!!.toString() + " must implement OnFragmentInteractionListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setLocationManager()
        analytics = FirebaseAnalytics.getInstance(context)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater!!.inflate(R.layout.fragment_added, container, false)
        setLayout(view)
        return view
    }

    override fun onStart() {
        super.onStart()
        resetSegment()

        myID = misc.setMyID(context)
        if (myID == "0") {
            addedInteractionListener?.turnToFragmentFromAdded("Login")
        } else {
            logViewPeeps()
            misc.setSideMenuIndex(context, 1)
            getMyAdded()

            setLongLat()
            if (selectedSegment == "locals") {
                startListeningForLocation()
            } else {
                if (selectedSegment == "followers") {
                    showFollowers(true)
                } else {
                    showFollowers(false)
                }
                observePeeps()
            }
            observeBlocked()
        }
    }

    override fun onStop() {
        super.onStop()
        saveSegment()
        removeObserverForPeeps()
        removeObserverForBlocked()
        if (locationListener != null) {
            locationManager?.removeUpdates(locationListener)
        }
    }

    override fun onDetach() {
        super.onDetach()
        if (locationListener != null) {
            locationManager?.removeUpdates(locationListener)
        }
        addedInteractionListener = null
        removeObserverForPeeps()
    }

    // MARK: - Navigation

    interface AddedInteractionListener {
        fun turnToFragmentFromAdded(name: String)
    }

    fun setLayout(view: View) {
        layoutManager = LinearLayoutManager(context)
        recyclerView = view.findViewById(R.id.addedRecyclerView)
        adapter = AddedAdapter(context, this, locals, added, followers, selectedSegment, displayProgress)
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)
        val divider = DividerItemDecoration(recyclerView.context, 1)
        recyclerView.addItemDecoration(divider)
        recyclerView.addOnScrollListener(scrollListener)

        segmentedGroup = view.findViewById(R.id.addedSegmentedGroup)
        segmentedGroup.setOnCheckedChangeListener(segmentDidChange)
    }

    // MARK: - Location

    fun setLocationManager() {
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    fun startListeningForLocation() {
        locationManager?.removeUpdates(locationListener)

        locationListener = object: LocationListener {
            override fun onLocationChanged(location: Location?) {
                if (location != null) {
                    longitude = location.longitude
                    latitude = location.latitude
                    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
                    val editor = sharedPreferences.edit()
                    editor.putString("logitude.flocal", longitude.toString())
                    editor.putString("latitude.flocal", latitude.toString())
                    editor.apply()
                    writeMyLocation()

                    val geocoder = Geocoder(context, Locale.getDefault())
                    try {
                        val address = geocoder.getFromLocation(longitude, latitude, 1)
                        if (address.size > 0) {
                            val city = address[0].locality
                            if (city != null) {
                                editor.putString("city.flocal", city)
                                editor.apply()
                            }
                            val zip = address[0].postalCode
                            if (zip != null ) {
                                editor.putString("zip.flocal", zip)
                                editor.apply()
                            }
                        }
                    } catch (e: IOException) {
                        Log.d("locationException", e.toString())
                    }
                }
                observePeeps()
            }
            override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) { observePeeps() }
            override fun onProviderEnabled(p0: String?) { observePeeps() }
            override fun onProviderDisabled(p0: String?) {}
        }

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            val alert = AlertDialog.Builder(context)
            alert.setTitle("Location needed for Locals")
            alert.setMessage("Please grant permission for location so we can bring you nearby posts and locals.")
            alert.setPositiveButton("Ok", { _, _ ->
                ActivityCompat.requestPermissions(activity, arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION,
                        android.Manifest.permission.ACCESS_FINE_LOCATION), 0)
            })
            alert.create()
            alert.show()
        } else {
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 402.336f, locationListener)
        }
    }

    fun setLongLat() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val long = sharedPreferences.getString("longitude.flocal", "0")
        val lat = sharedPreferences.getString("latitude.flocal", "0")

        if (long != "0") {
            try {
                longitude = long.toDouble()
            } catch(ex: NumberFormatException) {
                Log.d("numberException", "Cannot format to double")
            }
        }
        if (lat != "0") {
            try {
                latitude = lat.toDouble()
            } catch(ex: NumberFormatException) {
                Log.d("numberException", "Cannot format to double")
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 0) {
            startListeningForLocation()
        }
    }

    // MARK: - Segment

    val segmentDidChange = RadioGroup.OnCheckedChangeListener { _, id ->
        scrollToTop()

        when (id) {
            R.id.localsSegment -> {
                selectedSegment = "locals"
                showFollowers(false)
                startListeningForLocation()
            }
            R.id.addedSegment -> {
                selectedSegment = "added"
                showFollowers(false)
                observePeeps()
            }
            R.id.followersSegment -> {
                selectedSegment = "followers"
                showFollowers(true)
                observePeeps()
            }
            else -> {}
        }
    }

    fun saveSegment() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = sharedPreferences.edit()
        editor.putString("lastAddedSegment.flocal", selectedSegment)
        editor.apply()
    }

    fun resetSegment() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        selectedSegment = sharedPreferences.getString("lastAddedSegment.flocal", "locals")
        when (selectedSegment) {
            "added" -> {
                segmentedGroup.check(R.id.addedSegment)
                showFollowers(false)
            }
            "followers" -> {
                segmentedGroup.check(R.id.followersSegment)
                showFollowers(false)
            }
            else -> {
                segmentedGroup.check(R.id.localsSegment)
                showFollowers(true)
            }
        }
    }

    // MARK: - Scroll

    val scrollListener = object: RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)

            if (!isRemoved) {
                removeObserverForPeeps()
            }

            val peeps = determinePeeps()
            val firstVisibleItem = layoutManager.findFirstCompletelyVisibleItemPosition()
            val lastVisibleItem = layoutManager.findLastCompletelyVisibleItemPosition()
            if (firstVisibleItem <= 0) {
                scrollPosition = "top"
            } else if (lastVisibleItem == peeps.size - 1) {
                scrollPosition = "bottom"
                if (peeps.size > 8) {
                    if (selectedSegment != "locals") {
                        displayProgress = true
                        refreshRecycler()
                        recyclerView?.scrollToPosition(peeps.size)
                        observePeeps()
                    }
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

    fun showFollowers(bool: Boolean) {
        if (bool) {
            followersTextView.layoutParams.height = resources.getDimension(R.dimen.followersHeight).toInt()
        } else {
            followersTextView.layoutParams.height = 0
        }
    }

    fun determinePeeps(): MutableList<User> {
        when (selectedSegment) {
            "added" -> { return added }
            "followers" -> { return followers }
            else -> { return locals }
        }
    }

    fun didIAdd(userID: String): Boolean {
        var added = false
        if (addedIDs.contains(userID)) {
            added = true
        }
        return added
    }

    fun refreshRecycler() {
        adapter = AddedAdapter(context, this@AddedFragment, locals, added, followers, selectedSegment, displayProgress)
        recyclerView.adapter = adapter
        recyclerView.adapter.notifyDataSetChanged()
    }

    // MARK: - Analytics

    fun logViewPeeps() {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        when (selectedSegment) {
            "added" -> { analytics?.logEvent("viewAdded_Android", bundle) }
            "followers" -> { analytics?.logEvent("viewFollowers_Android", bundle) }
            else -> { analytics?.logEvent("viewLocals_Android", bundle) }
        }
    }

    fun logAddedUser(addedID: String) {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        bundle.putString("userID", addedID)
        analytics?.logEvent("addedUser_Android", bundle)
    }

    // MARK: - Firebase

    fun writeMyLocation() {
        val meRef = ref.child("users").child(myID)
        meRef.child("longitude").setValue(longitude)
        meRef.child("latitude").setValue(latitude)

        val location = GeoLocation(latitude, longitude)
        geoFireUsers.setLocation(myID, location)
    }

    fun observePeeps() {
        removeObserverForPeeps()
        isRemoved = false

        val peeps = determinePeeps()
        val userRef = ref.child("users")

        if (scrollPosition == "middle" && !peeps.isEmpty()) {
            val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
            val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
            for (index in firstVisiblePosition..lastVisiblePosition) {
                val userID = peeps[index].userID
                val idRef = userRef.child(userID)
                idRef.addListenerForSingleValueEvent( object: ValueEventListener {
                    override fun onDataChange(snap: DataSnapshot?) {
                        val user = snap?.value as? MutableMap<String,Any> ?: mutableMapOf()
                        if (!user.isEmpty()) {
                            val didIAdd = didIAdd(userID)
                            val formattedUser = formatUser(userID, didIAdd, user)
                            when (selectedSegment) {
                                "added" -> { added[index] = formattedUser }
                                "followers" -> { followers[index] = formattedUser }
                                else -> { locals[index] = formattedUser }
                            }
                        }
                    }
                    override fun onCancelled(error: DatabaseError?) { Log.d("databaseError", error.toString()) }
                })
            }
            displayProgress = false
            refreshRecycler()

        } else {
            val alphabeticalStartRange = "_"
            val lastHandle = peeps.lastOrNull()?.handle
            val handle: String

            val currentReverseTimestamp = misc.getCurrentReverseTimestamp()
            val lastReverseTimestamp = peeps.lastOrNull()?.originalReverseTimestamp
            val reverseTimestamp: Double

            if (scrollPosition == "bottom") {
                handle = lastHandle ?: alphabeticalStartRange
                reverseTimestamp = lastReverseTimestamp ?: currentReverseTimestamp
            } else {
                handle = alphabeticalStartRange
                reverseTimestamp = currentReverseTimestamp
            }

            var users: MutableList<User> = mutableListOf()

            when (selectedSegment) {
                "added" -> {
                    addedValueListener = object : ValueEventListener {
                        override fun onDataChange(snap: DataSnapshot?) {
                            val dict = snap?.value as? MutableMap<String, Any> ?: mutableMapOf()
                            for ((userID, value) in dict) {
                                val user = value as? MutableMap<String, Any> ?: mutableMapOf()
                                if (!user.isEmpty()) {
                                    val formattedUser = formatUser(userID, true, user)
                                    users.add(formattedUser)
                                }
                            }

                            if (scrollPosition == "bottom") {
                                if (lastHandle != users.lastOrNull()?.handle) {
                                    added.addAll(users)
                                }
                            } else {
                                added = users
                            }
                            displayProgress = false
                            refreshRecycler()
                        }
                        override fun onCancelled(error: DatabaseError?) { Log.d("databaseError", error.toString()) }
                    }
                    val addedRef = ref.child("userAdded").child(myID)
                    addedRef.orderByChild("handle").startAt(handle).limitToFirst(88).addValueEventListener(addedValueListener)
                }

                "followers" -> {
                    val lastUserID = peeps.lastOrNull()?.userID

                    followersCountValueListener = object : ValueEventListener {
                        override fun onDataChange(snap: DataSnapshot?) {
                            val count = snap?.value as? Int ?: 0
                            followersTextView.text = count.toString()
                        }
                        override fun onCancelled(error: DatabaseError?) { Log.d("databaseError", error.toString()) }
                    }
                    val myFollowersCountRef = ref.child("users").child(myID).child("followersCount")
                    myFollowersCountRef.addValueEventListener(followersCountValueListener)

                    followersValueListener = object : ValueEventListener {
                        override fun onDataChange(snap: DataSnapshot?) {
                            val dict = snap?.value as? MutableMap<String, Any> ?: mutableMapOf()
                            for ((userID, value) in dict) {
                                val user = value as? MutableMap<String, Any> ?: mutableMapOf()
                                val didIAdd = didIAdd(userID)
                                val formattedUser = formatUser(userID, didIAdd, user)
                                users.add(formattedUser)
                            }

                            if (scrollPosition == "bottom") {
                                if (lastUserID != users.lastOrNull()?.userID) {
                                    followers.addAll(users)
                                }
                            } else {
                                followers = users
                            }
                            displayProgress = false
                            refreshRecycler()
                        }
                        override fun onCancelled(error: DatabaseError?) { Log.d("databaseError", error.toString()) }
                    }
                    val followersRef = ref.child("userFollowers").child(myID)
                    followersRef.orderByChild("originalReverseTimestamp").startAt(reverseTimestamp).limitToFirst(88)
                }

                else -> {
                    val userIDs: MutableList<String> = mutableListOf()

                    val center = GeoLocation(latitude, longitude)
                    geoQuery = geoFireUsers.queryAtLocation(center, radiusMeters / 1000)
                    (geoQuery as GeoQuery).addGeoQueryEventListener(object : GeoQueryEventListener {
                        override fun onKeyEntered(key: String?, location: GeoLocation?) {
                            if (key != null && !userIDs.contains(key)) {
                                userIDs.add(key)
                            }
                        }

                        override fun onGeoQueryReady() {
                            for (userID in userIDs) {
                                userRef.child(userID).addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onDataChange(snap: DataSnapshot?) {
                                        val dict = snap?.value as? MutableMap<String, Any> ?: mutableMapOf()
                                        val didIAdd = didIAdd(userID)
                                        val formattedUser = formatUser(userID, didIAdd, dict)
                                        users.add(formattedUser)
                                    }
                                    override fun onCancelled(error: DatabaseError?) { Log.d("databaseError", error.toString()) }
                                })
                            }

                            val comparator = Comparator<User> { m0, m1 ->
                                val f0 = m0.followersCount
                                val f1 = m1.followersCount
                                f1.compareTo(f0)
                            }
                            Collections.sort(users, comparator)
                            if (users.size > 100) {
                                users = users.subList(0, 99)
                            }
                            locals = users

                            displayProgress = false
                            refreshRecycler()
                        }
                        override fun onGeoQueryError(error: DatabaseError?) { Log.d("geoFireError", error.toString()) }
                        override fun onKeyExited(key: String?) {}
                        override fun onKeyMoved(key: String?, location: GeoLocation?) {}
                    })
                }
            }
        }
    }

    fun removeObserverForPeeps() {
        isRemoved = true

        if (geoQuery != null) {
            (geoQuery as GeoQuery).removeAllListeners()
        }

        val userAddedRef = ref.child("userAdded").child(myID)
        if (addedValueListener != null) {
            userAddedRef.removeEventListener(addedValueListener)
            addedValueListener = null
        }

        val myFollowersCountRef = ref.child("users").child(myID).child("followersCount")
        if (followersCountValueListener != null) {
            myFollowersCountRef.removeEventListener(followersCountValueListener)
            followersCountValueListener = null
        }

        val followersRef = ref.child("userFollowers").child(myID)
        if (followersValueListener != null) {
            followersRef.removeEventListener(followersValueListener)
            followersValueListener = null
        }
    }

    fun getMyAdded() {
        val userAddedRef = ref.child("userAdded").child(myID)
        userAddedRef.addListenerForSingleValueEvent( object: ValueEventListener {
            override fun onDataChange(snap: DataSnapshot?) {
                val dict = snap?.value as? MutableMap<String,Any> ?: mutableMapOf()
                addedIDs = dict.keys.toMutableList()
            }
            override fun onCancelled(error: DatabaseError?) { Log.d("databaseError", error.toString()) }
        })
    }

    fun addUser(index: Int, userID: String) {
        val amIBlocked = misc.amIBlocked(userID, blockedBy)
        if (!amIBlocked) {
            misc.playSound(context, R.raw.added_sound, 0)
            when (selectedSegment) {
                "locals" -> { locals[index].didIAdd }
                "followers" -> { followers[index].didIAdd }
                else -> { return }
            }
            refreshRecycler()

            misc.addUser(userID, myID)
            if (!addedIDs.contains(userID)) {
                addedIDs.add(userID)
            }
            misc.writeAddedNotification(context, userID, myID)
            logAddedUser(userID)
        } else {
            alert("Blocked", "This person has blocked you. You cannot add them.")
            return
        }
    }

    fun formatUser(userID: String, didIAdd: Boolean, dict: MutableMap<String,Any>): User {
        val user = User()

        user.userID = userID
        user.didIAdd = didIAdd
        user.handle = dict["handle"] as? String ?: "error"
        user.points = dict["points"] as? Int ?: 0
        user.description = dict["description"] as? String ?: "error"
        user.followersCount = dict["followersCount"] as? Int ?: 0
        user.originalReverseTimestamp = dict["originalReverseTimestamp"] as? Double ?: 0.0

        val profilePicURLString = dict["profilePicURLString"] as? String ?: "error"
        if (profilePicURLString != "error") {
            user.profilePicURL = Uri.parse(profilePicURLString)
        }

        return user
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
