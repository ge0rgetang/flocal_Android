@file:Suppress("UNCHECKED_CAST", "MemberVisibilityCanPrivate", "CascadeIf", "LiftReturnOrAssignment", "UnnecessaryVariable", "SetTextI18n")

package com.dotnative.flocal

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQuery
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.database.*
import com.google.maps.android.heatmaps.HeatmapTileProvider
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.*

class LocationFragment : Fragment(), OnMapReadyCallback {

    // MARK: - Layout

    lateinit var mapView: MapView
    lateinit var editText: EditText
    lateinit var myLocationImageView: ImageView
    lateinit var confirmButton: Button

    // MARK: - Vars

    private var locationInteractionListener: LocationInteractionListener? = null

    var myID: String = "0"
    var newPostIDs: MutableList<String> = mutableListOf()
    var coordinates: MutableList<MutableMap<String,Double>> = mutableListOf()
    var map: GoogleMap? = null
    var provider: HeatmapTileProvider? = null
    var overlay: TileOverlay? = null

    var radiusMeters: Double = 2404.02
    var locationManager: LocationManager? = null
    var locationListener: LocationListener? = null
    var longitude: Double = -122.258542
    var latitude: Double = 37.871906
    var city: String = "Berkeley"
    var zip: String = "94720"

    var ref: DatabaseReference = FirebaseDatabase.getInstance().reference
    val analytics: FirebaseAnalytics = FirebaseAnalytics.getInstance(context)
    var geoFireUsers: GeoFire = GeoFire(ref.child("users_location"))
    var geoFirePosts: GeoFire = GeoFire(ref.child("posts_location"))
    var geoQuery: GeoQuery? = null

    val misc = Misc()


    // MARK: - Lifecycle

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context is LocationInteractionListener) {
            locationInteractionListener = context
        } else {
            throw RuntimeException(context!!.toString() + " must implement OnFragmentInteractionListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        setLocationManager()
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater!!.inflate(R.layout.fragment_location, container, false)
        setLayout(view)
        return view
    }

    override fun onStart() {
        super.onStart()
        myID = misc.setMyID(context)
        if (myID == "0") {
            locationInteractionListener?.turnToFragmentFromLocation("Login")
        } else {
            logViewLocation()
            setLongLat()
            startListeningForLocation()
        }
    }

    override fun onStop() {
        super.onStop()
        if (locationListener != null) {
            locationManager?.removeUpdates(locationListener)
        }
        removeObserverForPosts()
    }

    override fun onDetach() {
        super.onDetach()
        if (locationListener != null) {
            locationManager?.removeUpdates(locationListener)
        }
        removeObserverForPosts()
        locationInteractionListener = null
    }

    // MARK: - Navigation

    interface LocationInteractionListener {
        fun turnToFragmentFromLocation(name: String)
        fun dismissKeyboardFromLocation()
        fun popBackStackLocation()
    }

    fun setLayout(view: View) {
        mapView = view.findViewById(R.id.mapView)
        mapView.isEnabled = false

        editText = view.findViewById(R.id.locationEditText)
        editText.setOnFocusChangeListener { _: View?, p1: Boolean ->
            if (!p1) {
                startListeningForLocation()
            }
        }

        myLocationImageView = view.findViewById(R.id.myLocationImageView)
        myLocationImageView.setOnClickListener {
            editText.setText(R.string.myLocation)
            startListeningForLocation()
        }

        confirmButton = view.findViewById(R.id.confirmButton)
        confirmButton.setOnClickListener { saveLocation() }
    }

    // MARK: - Map

    override fun onMapReady(p0: GoogleMap?) {
        map = p0
        map?.mapType = GoogleMap.MAP_TYPE_NORMAL
        map?.isTrafficEnabled = false
        map?.isIndoorEnabled = true
        map?.isBuildingsEnabled = true
        map?.uiSettings?.isMyLocationButtonEnabled = false
        map?.uiSettings?.isCompassEnabled = false
        map?.uiSettings?.isIndoorLevelPickerEnabled = false
        map?.uiSettings?.isMapToolbarEnabled = false
        map?.uiSettings?.isRotateGesturesEnabled = false
        map?.uiSettings?.isScrollGesturesEnabled = false
        map?.uiSettings?.isTiltGesturesEnabled = false
        map?.uiSettings?.isZoomControlsEnabled = false
        map?.uiSettings?.isZoomGesturesEnabled = false
    }

    fun addHeatmap() {
        if (map != null) {
            removeHeatmap()
            centerMap()

            val arrayList = arrayListOf<LatLng>()
            for (coordinate in coordinates) {
                val long = coordinate["longitude"] as Double
                val lat = coordinate["latitude"] as Double
                arrayList.add(LatLng(lat, long))
            }

            provider = HeatmapTileProvider.Builder().data(arrayList).build()
            overlay = (map as GoogleMap).addTileOverlay(TileOverlayOptions().tileProvider(provider))
        }
    }

    fun removeHeatmap() {
        if (map != null) {
            overlay?.remove()
        }
    }

    fun centerMap() {
        if (map != null) {
            val position = LatLng(latitude, longitude)
            val scale = radiusMeters/500
            val zoomLevel = (16 - Math.log(scale))/Math.log(2.0)
            val zoom = zoomLevel.toInt()
            (map as GoogleMap).moveCamera(CameraUpdateFactory.newLatLngZoom(position, zoom.toFloat()))
        }
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
                    var text = editText.text.toString().toLowerCase()
                    if (text == "") {
                        text = "my location"
                    }
                    val geocoder = Geocoder(context, Locale.getDefault())

                    if (text.contains("my location")) {
                        longitude = location.longitude
                        latitude = location.latitude
                        writeMyLocation()
                        editText.setText(R.string.myLocation)

                        try {
                            val address = geocoder.getFromLocation(longitude, latitude, 1)
                            if (address.size > 0) {
                                val c = address[0].locality
                                if (c != null) {
                                    city = c
                                }
                                val z = address[0].postalCode
                                if (z != null) {
                                    zip = z
                                }
                            }
                        } catch (e: IOException) {
                            Log.d("locationException", e.toString())
                        }
                    } else {
                        try {
                            val address = geocoder.getFromLocationName(text, 1)
                            if (address.size > 0) {
                                var locationText = ""

                                val c = address[0].locality
                                if (c != null) {
                                    city = c
                                    locationText = "city"
                                }
                                val z = address[0].postalCode
                                if (z != null) {
                                    zip = z
                                    locationText += " $zip"
                                }
                                editText.setText(locationText)

                                longitude = address[0].longitude
                                latitude = address[0].latitude
                            }
                        } catch (e: IOException) {
                            Log.d("locationException", e.toString())
                        }
                    }
                }

                observePosts()
            }
            override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) { observePosts() }
            override fun onProviderEnabled(p0: String?) { observePosts() }
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

        val myLocation = sharedPreferences.getBoolean("myLocation.flocal", false)
        if (myLocation) {
            editText.setText(R.string.myLocation)
        } else {
            var locationText = ""
            val c = sharedPreferences.getString("city.flocal", "0")
            if (c != null) {
                city = c
                locationText = city
            }
            val z = sharedPreferences.getString("city.flocal", "0")
            if (z != null) {
                zip = z
                locationText += " $zip"
            }
            editText.setText(locationText)
        }

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
        startListeningForLocation()
    }

    @SuppressLint("ApplySharedPref")
    fun saveLocation() {
        editText.clearFocus()
        misc.playSound(context, R.raw.button_click, 0)
        locationInteractionListener?.dismissKeyboardFromLocation()

        val text = editText.text.toString()
        if (text == "") {
            alert("Empty Field", "Please enter in a location or type in My Location")
            return
        }

        val myLocation: Boolean
        if (text.toLowerCase().contains("my location")) {
            myLocation = true
            logSetMyLocation()
        } else {
            myLocation = false
            logSetOtherLocation()
        }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = sharedPreferences.edit()
        editor.putString("logitude.flocal", longitude.toString())
        editor.putString("latitude.flocal", latitude.toString())
        editor.putBoolean("myLocation.flocal", myLocation)
        editor.putString("city.flocal", city)
        editor.putString("zip.flocal", zip)
        editor.commit()

        locationInteractionListener?.popBackStackLocation()
    }

    // MARK: - Misc

    fun alert(title: String, message: String) {
        misc.displayAlert(context, title, message)
    }

    // MARK: - Analytics

    fun logViewLocation() {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        analytics.logEvent("viewLocation_Android", bundle)
    }

    fun logSetMyLocation() {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        bundle.putDouble("longitude", longitude)
        bundle.putDouble("latitude", latitude)
        bundle.putString("city", city)
        bundle.putString("zip", zip)
        analytics.logEvent("setMyLocation_Android", bundle)
    }

    fun logSetOtherLocation() {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        bundle.putString("city", city)
        bundle.putString("zip", zip)
        analytics.logEvent("setOtherLocation_Android", bundle)
    }

    // MARK: - Firebase

    fun writeMyLocation() {
        val meRef = ref.child("users").child(myID)
        meRef.child("longitude").setValue(longitude)
        meRef.child("latitude").setValue(latitude)

        val location = GeoLocation(latitude, longitude)
        geoFireUsers.setLocation(myID, location)
    }

    fun observePosts() {
        removeObserverForPosts()

        val center = GeoLocation(latitude, longitude)
        geoQuery = geoFirePosts.queryAtLocation(center, radiusMeters / 1000)
        (geoQuery as GeoQuery).addGeoQueryEventListener(object : GeoQueryEventListener {
            override fun onKeyEntered(key: String?, location: GeoLocation?) {}
            override fun onGeoQueryReady() {
                getPostIDs()

                val postRef = ref.child("posts")

                val coor: MutableList<MutableMap<String,Double>> = mutableListOf()
                for (id in newPostIDs) {
                    val newRef = postRef.child(id)
                    newRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snap: DataSnapshot?) {
                            val post = snap?.value as? MutableMap<String, Any> ?: mutableMapOf()
                            if (!post.isEmpty()) {
                                val longitude = post["longitude"] as? Double ?: 0.0
                                val latitude = post["latitude"] as? Double ?: 0.0
                                val map: MutableMap<String,Double> = mutableMapOf("longitude" to longitude, "latitude" to latitude)
                                coor.add(map)
                            }
                        }
                        override fun onCancelled(error: DatabaseError?) { Log.d("databaseError", error.toString()) }
                    })
                }

                coordinates = coor
                addHeatmap()
            }
            override fun onGeoQueryError(error: DatabaseError?) { Log.d("geoFireError", error.toString()) }
            override fun onKeyExited(key: String?) {}
            override fun onKeyMoved(key: String?, location: GeoLocation?) {}
        })
    }

    fun removeObserverForPosts() {
        if (geoQuery != null) {
            (geoQuery as GeoQuery).removeAllListeners()
        }
    }

    // MARK: - OkHttp

    fun getPostIDs() {
        val JSON = MediaType.parse("application/json; charset=utf-8")
        val params: HashMap<String,Any> = hashMapOf("longitude" to longitude, "latitude" to latitude, "sort" to "new", "action" to "search")
        val parameter = JSONObject(params)

        val client = OkHttpClient()
        val body = RequestBody.create(JSON, parameter.toString())
        val request = Request.Builder().url("https://flocalApp.us-west-1.elasticbeanstalk.com").post(body).build()

        val response = client.newCall(request).execute()
        val jsonArray = JSONArray(response.body()?.string())

        val list: ArrayList<String> = arrayListOf()
        for (i in 0 until jsonArray.length() - 1) {
            list.add(jsonArray.getString(i))
        }
        newPostIDs = list
    }

}
