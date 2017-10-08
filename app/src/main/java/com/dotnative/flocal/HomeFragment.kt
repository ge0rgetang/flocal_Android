@file:Suppress("UNCHECKED_CAST", "MemberVisibilityCanPrivate", "CascadeIf", "LiftReturnOrAssignment", "UnnecessaryVariable", "SetTextI18n")

package com.dotnative.flocal

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Point
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.support.constraint.ConstraintLayout
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v7.widget.CardView
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.widget.*
import com.baoyz.actionsheet.ActionSheet
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQuery
import com.firebase.geofire.GeoQueryEventListener
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import info.hoang8f.android.segmented.SegmentedGroup
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class HomeFragment : Fragment() {

    // MARK: - Layout

    lateinit var cardView: CardView
    lateinit var constraintLayout: ConstraintLayout
    lateinit var cameraImageView: ImageView
    lateinit var editText: EditText
    lateinit var sendImageView: ImageView

    lateinit var layoutManager: LinearLayoutManager
    lateinit var recyclerView: RecyclerView
    lateinit var adapter: HomeAdapter
    lateinit var segmentedGroup: SegmentedGroup
    lateinit var progressBar: ProgressBar

    // MARK: - Vars

    private var homeInteractionListener: HomeInteractionListener? = null

    var myID: String = "0"
    var myProfilePicURL: Uri? = null
    var newPosts: MutableList<Post> = mutableListOf()
    var newPostIDs: ArrayList<String> = arrayListOf()
    var hotPosts: MutableList<Post> = mutableListOf()
    var hotPostIDs: ArrayList<String> = arrayListOf()
    var addedPosts: MutableList<Post> = mutableListOf()
    var selectedSegment: String = "new"
    var addedValueListener: ValueEventListener? = null

    var blockedBy: MutableList<String> = mutableListOf()
    var blockedValueListener: ValueEventListener? = null

    var radiusMeters: Double = 2404.02
    var locationManager: LocationManager? = null
    var locationListener: LocationListener? = null
    var longitude: Double = -122.258542
    var latitude: Double = 37.871906

    var scrollPosition: String = "top"
    var displayProgress: Boolean = false
    var isRemoved: Boolean = false

    var ref: DatabaseReference = FirebaseDatabase.getInstance().reference
    val storageRef: StorageReference = FirebaseStorage.getInstance().reference
    val analytics: FirebaseAnalytics = FirebaseAnalytics.getInstance(context)
    var geoFireUsers: GeoFire = GeoFire(ref.child("users_location"))
    var geoFirePosts: GeoFire = GeoFire(ref.child("posts_location"))
    var geoQuery: GeoQuery? = null

    val misc = Misc()
    var popupContainer: ViewGroup? = null
    var popupView: View? = null
    var popWindow: PopupWindow? = null

    // MARK: - Lifecycle

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context is HomeInteractionListener) {
            homeInteractionListener = context
        } else {
            throw RuntimeException(context!!.toString() + " must implement OnFragmentInteractionListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setLocationManager()
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater!!.inflate(R.layout.fragment_home, container, false)
        popupContainer = container
        popupView = view
        setLayout(view)
        return view
    }

    override fun onStart() {
        super.onStart()
        resetSegment()

        myID = misc.setMyID(context)
        if (myID == "0") {
            homeInteractionListener?.turnToFragmentFromHome("Login")
        } else {
            logViewHome()
            misc.setSideMenuIndex(context, 0)
            downloadMyProfilePicURL()
            setLongLat()
            startListeningForLocation()
            observeBlocked()
        }
    }

    override fun onStop() {
        super.onStop()
        saveSegment()
        removeObserverForPosts()
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
        homeInteractionListener = null
        removeObserverForPosts()
    }

    // MARK: - Navigation

    interface HomeInteractionListener {
        fun turnToFragmentFromHome(name: String)
        fun dismissKeyboardFromHome()
        fun showImageFromHomeWrite(bitmap: Bitmap)
        fun showVideoFromHomeWrite(uri: Uri, bitmap: Bitmap)
    }

    fun setLayout(view: View) {
        cardView = view.findViewById(R.id.writePostCardView)
        cardView.setOnClickListener {
            misc.playSound(context, R.raw.button_click, 0)
            showWritePost("text", null, null)
        }
        constraintLayout = view.findViewById(R.id.writePostConstraintLayout)
        cameraImageView = view.findViewById(R.id.cameraImageView)
        cameraImageView.setOnClickListener { selectPicSource() }
        editText = view.findViewById(R.id.editText)
        editText.isEnabled = false
        sendImageView = view.findViewById(R.id.sendImageView)
        sendImageView.isEnabled = false

        layoutManager = LinearLayoutManager(context)
        recyclerView = view.findViewById(R.id.homeRecyclerView)
        adapter = HomeAdapter(context, this, newPosts, hotPosts, addedPosts, selectedSegment, displayProgress)
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)
        val divider = DividerItemDecoration(recyclerView.context, 1)
        recyclerView.addItemDecoration(divider)
        recyclerView.addOnScrollListener(scrollListener)

        segmentedGroup = view.findViewById(R.id.segmentedGroup)
        segmentedGroup.setOnCheckedChangeListener(segmentDidChange)

        progressBar = view.findViewById(R.id.progressBar)
        progressBar.visibility = ProgressBar.INVISIBLE
    }

    @SuppressLint("SetTextI18n")
    fun showWritePost(type: String, bitmap: Bitmap?, uri: Uri?) {
        val layoutInflater: LayoutInflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val inflatedView: View
        if (type != "text") {
            inflatedView = layoutInflater.inflate(R.layout.popup_write_post, popupContainer, false)
        } else {
            inflatedView = layoutInflater.inflate(R.layout.popup_write_post_image, popupContainer, false)
        }

        val display = activity.windowManager.defaultDisplay
        val size = Point()
        display.getSize(size)
        val width = size.x

        val popupWindow = PopupWindow(inflatedView, width - 50, WindowManager.LayoutParams.WRAP_CONTENT, true)
        popWindow = popWindow
        popupWindow.isFocusable = true
        popupWindow.isOutsideTouchable = true

        val characterCountTextView: TextView = inflatedView.findViewById(R.id.characterCountTextView)
        val currentLength = editText.text.toString().length
        characterCountTextView.text = "$currentLength/255"

        val editText: EditText = inflatedView.findViewById(R.id.editText)
        editText.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(text: Editable?) {
                val length = text.toString().length
                characterCountTextView.text = "$length/255"
            }
        })

        val cancelButton: Button = inflatedView.findViewById(R.id.cancelButton)
        cancelButton.setOnClickListener {
            homeInteractionListener?.dismissKeyboardFromHome()
            popupWindow.dismiss()
        }

        val cameraImageView: ImageView = inflatedView.findViewById(R.id.cameraImageView)
        cameraImageView.setOnClickListener { selectPicSource() }

        val sendImageView: ImageView = inflatedView.findViewById(R.id.sendImageView)
        sendImageView.setOnClickListener {
            val text = editText.text.toString()
            if (text == "") {
                alert("Empty Post Content", "Please write some text for your post.")
            } else {
                writePost(type, text, bitmap, uri)
            }
        }

        if (type != "text" && bitmap != null) {
            val postPicImageView: ImageView = inflatedView.findViewById(R.id.postPicImageView)
            postPicImageView.setImageBitmap(bitmap)
            val playImageView: ImageView = inflatedView.findViewById(R.id.playImageView)
            if (type == "video" && uri != null) {
                playImageView.visibility = ImageView.VISIBLE
                postPicImageView.setOnClickListener {
                    homeInteractionListener?.showVideoFromHomeWrite(uri, bitmap) }
            } else {
                playImageView.visibility = ImageView.INVISIBLE
                postPicImageView.setOnClickListener { homeInteractionListener?.showImageFromHomeWrite(bitmap) }
            }
        }

        logViewWritePost()
        popupWindow.showAtLocation(popupView, Gravity.CENTER, 0,0)
    }

    // MARK: Camera

    fun selectPicSource() {
        val sheet = ActionSheet.createBuilder(context, fragmentManager)
        sheet.setCancelButtonTitle("Cancel")
        sheet.setCancelableOnTouchOutside(true)
        sheet.setOtherButtonTitles("Take a Picture", "Choose from Photo Library", "Record Video")
        sheet.setListener( object: ActionSheet.ActionSheetListener {
            override fun onOtherButtonClick(actionSheet: ActionSheet?, index: Int) {
                when (index) {
                    0 -> { chooseCamera() }
                    1 -> { choosePhotoLibrary() }
                    2 -> { chooseVideo() }
                    else -> { actionSheet?.dismiss() }
                }
            }
            override fun onDismiss(actionSheet: ActionSheet?, isCancel: Boolean) {}
        })
        misc.playSound(context, R.raw.button_click, 0)
        sheet.show()
    }

    fun chooseCamera() {
        homeInteractionListener?.dismissKeyboardFromHome()
        popWindow?.dismiss()
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, 0)
    }

    fun choosePhotoLibrary() {
        homeInteractionListener?.dismissKeyboardFromHome()
        popWindow?.dismiss()
        val intent = Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, 1)
    }

    fun chooseVideo() {
        homeInteractionListener?.dismissKeyboardFromHome()
        popWindow?.dismiss()
        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
        intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 10)
        val file = File(Environment.getExternalStorageDirectory().absolutePath + ".mp4")
        val uri = Uri.fromFile(file)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
        startActivityForResult(intent, 2)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
        if (data != null) {
            when (resultCode) {
                0 -> {
                    val bitmap: Bitmap = data.extras.get("data") as Bitmap
                    showWritePost("image", bitmap, null)
                }
                1 -> {
                    val uri = data.data
                    try {
                        val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                        showWritePost("image", bitmap, null)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                2 -> {
                    val uri = data.data
                    try {
                        val file = File(uri.path)
                        val path = file.absolutePath
                        val mediaRetriever = MediaMetadataRetriever()
                        mediaRetriever.setDataSource(path)
                        val thumbnail = mediaRetriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST)
//                        val thumbnail = ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND)
                        showWritePost("video", thumbnail, uri)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                else -> {
                    alert("Error", "We encountered an error with the camera. Please report this bug.")
                    return
                }
            }
        }
    }

    // MARK: - Location

    fun setLocationManager() {
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    fun startListeningForLocation() {
        locationManager?.removeUpdates(locationListener)

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val myLocation = sharedPreferences.getBoolean("myLocation.flocal", true)

        locationListener = object: LocationListener {
            override fun onLocationChanged(location: Location?) {
                if (location != null && myLocation) {
                    longitude = location.longitude
                    latitude = location.latitude
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

    // MARK: - Segment

    val segmentDidChange = RadioGroup.OnCheckedChangeListener { _, id ->
        scrollToTop()

        when (id) {
            R.id.newSegment -> {
                selectedSegment = "new"
            }
            R.id.hotSegment -> {
                selectedSegment = "hot"
            }
            R.id.addedSegment -> {
                selectedSegment = "added"
            }
            else -> {}
        }

        observePosts()
    }

    fun setHomeSegment() {
        if (selectedSegment == "hot") {
            segmentedGroup.check(R.id.newSegment)
            selectedSegment = "new"
        }

        scrollToTop()
    }

    fun saveSegment() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = sharedPreferences.edit()
        editor.putString("lastHomeSegment.flocal", selectedSegment)
        editor.apply()
    }

    fun resetSegment() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        selectedSegment = sharedPreferences.getString("lastHomeSegment.flocal", "new")
        when (selectedSegment) {
            "hot" -> { segmentedGroup.check(R.id.hotSegment) }
            "added" -> { segmentedGroup.check(R.id.addedSegment) }
            else -> { segmentedGroup.check(R.id.newSegment) }
        }
    }


    // MARK: - Scroll

    val scrollListener = object: RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)

            if (!isRemoved) {
                removeObserverForPosts()
            }

            val posts = determinePosts()
            val firstVisibleItem = layoutManager.findFirstCompletelyVisibleItemPosition()
            val lastVisibleItem = layoutManager.findLastCompletelyVisibleItemPosition()
            if (firstVisibleItem <= 0) {
                scrollPosition = "top"
            } else if (lastVisibleItem == posts.size - 1) {
                scrollPosition = "bottom"
                if (posts.size > 8) {
                    displayProgress = true
                    refreshRecycler()
                    recyclerView?.scrollToPosition(posts.size)
                    observePosts()
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
        progressBar.visibility = ProgressBar.INVISIBLE
        misc.displayAlert(context, title, message)
    }

    fun determinePosts(): MutableList<Post> {
        when (selectedSegment) {
            "hot" -> { return hotPosts }
            "added" -> { return addedPosts }
            else -> { return newPosts }
        }
    }

    fun refreshRecycler() {
        adapter = HomeAdapter(context, this, newPosts, hotPosts, addedPosts, selectedSegment, displayProgress)
        recyclerView.adapter = adapter
        recyclerView.adapter.notifyDataSetChanged()
    }

    // MARK: - Analytics

    fun logViewHome() {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        when (selectedSegment) {
            "hot" -> { analytics.logEvent("viewPostsHot_Android", bundle) }
            "added" -> { analytics.logEvent("viewPostsAdded_Android", bundle) }
            else -> { analytics.logEvent("viewPostsHome_Android", bundle) }
        }
    }

    fun logUpvoted(userID: String, postID: String) {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        bundle.putString("userID", userID)
        bundle.putString("postID", postID)
        analytics.logEvent("upvotedPost_Android", bundle)
    }

    fun logDownvoted(userID: String, postID: String) {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        bundle.putString("userID", userID)
        bundle.putString("postID", postID)
        analytics.logEvent("downvotedPost_Android", bundle)
    }

    fun logViewWritePost() {
        val bundle = Bundle()
        bundle.putString("myID", myID)
        analytics.logEvent("viewWritePost_Android", bundle)
    }

    fun logPostSent(postID: String, type: String) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val myLocation = sharedPreferences.getBoolean("myLocation.flocal", true)

        val bundle = Bundle()
        bundle.putString("myID", myID)
        bundle.putString("postID", postID)
        bundle.putString("type", type)
        bundle.putBoolean("atMyLocation", myLocation)
        bundle.putDouble("longitude", longitude)
        bundle.putDouble("latitude", latitude)
        analytics.logEvent("sentPost_Android", bundle)
    }

    fun logUserTagged(postID: String, userID: String, handle: String) {
        val bundle = Bundle()
        bundle.putString("postID", postID)
        bundle.putString("myID", myID)
        bundle.putString("userID", userID)
        bundle.putString("userHandle", handle)
        analytics.logEvent("taggedUserInPost_Android", bundle)
    }

    // MARK: - Storage

    fun downloadMyProfilePicURL() {
        val child = "profilePic/$myID" + "_small.jpg"
        val myProfilePicRef = storageRef.child(child)
        myProfilePicRef.downloadUrl.addOnSuccessListener { uri ->
            myProfilePicURL = uri
        }.addOnFailureListener { error ->
            Log.d("downloadError", error.toString())
            myProfilePicURL = null
        }
    }

    fun uploadPostPic(bitmap: Bitmap, postID: String, post: MutableMap<String, Any>) {
        val postRef = ref.child("posts").child(postID)
        val originalReverseTimestamp = post["originalReverseTimestamp"] as Double
        val originalTimestamp = post["originalTimestamp"] as Double
        val content = post["content"] as String

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
        val postPicRef = storageRef.child("postPic/$postID.jpg")

        val uploadTask = postPicRef.putBytes(data, metadata)
        uploadTask.addOnFailureListener { error ->
            alert("Upload Error", "Your post pic may not have been uploaded. " +
                    "Please try again or report the bug if it persists.")
            Log.d("uploadError", error.toString())
        }
        uploadTask.addOnSuccessListener { taskSnap ->
            val postPicURLString = taskSnap.downloadUrl.toString()
            post["postPicURLString"] = postPicURLString
            post["postVidURLString"] = "n/a"
            post["postVidPreviewURLString"] = "n/a"

            postRef.setValue(post)
            val timestamp = post["timestamp"] as String
            postPostID(postID, timestamp)
            setGeoFirePost(postID)
            logPostSent(postID, "image")
            progressBar.visibility = ProgressBar.INVISIBLE
            misc.playSound(context, R.raw.send_post, 0)
            setHomeSegment()
            observePosts()
            writeToAddedAndHistory(postID, originalReverseTimestamp, originalTimestamp)
            writeTagged(postID, content)
        }
    }

    fun uploadPostVid(uri: Uri, preview: Bitmap, postID: String, post: MutableMap<String, Any>) {
        val postRef = ref.child("posts").child(postID)
        val originalReverseTimestamp = post["originalReverseTimestamp"] as Double
        val originalTimestamp = post["originalTimestamp"] as Double
        val content = post["content"] as String

        val sourceWidth = preview.width
        val sourceHeight = preview.height

        val scaleFactor: Int
        if (sourceWidth > sourceHeight) {
            scaleFactor = 1280/sourceWidth
        } else {
            scaleFactor = 1280/sourceHeight
        }

        val newWidth = scaleFactor*sourceWidth
        val newHeight = scaleFactor*sourceHeight
        val bitmapSized = Bitmap.createScaledBitmap(preview, newWidth, newHeight, true)

        val baos = ByteArrayOutputStream()
        bitmapSized.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val data = baos.toByteArray()

        val metadataPreview = StorageMetadata.Builder().setContentType("image/jpeg").build()
        val postVidPreviewRef = storageRef.child("postVidPreview/$postID.jpg")

        var postVidPreviewURLString = "n/a"
        val uploadPreviewTask = postVidPreviewRef.putBytes(data, metadataPreview)
        uploadPreviewTask.addOnFailureListener { error -> Log.d("uploadError", error.toString()) }
        uploadPreviewTask.addOnSuccessListener { taskSnap ->
            postVidPreviewURLString = taskSnap.downloadUrl.toString()
            Log.d("uploadSuccess", "chatVidPreview")
        }

        val metadata = StorageMetadata.Builder().setContentType("video/mp4").build()
        val postVidRef= storageRef.child("postVid/$postID.mp4")

        val uploadTask = postVidRef.putFile(uri, metadata)
        uploadTask.addOnFailureListener { error ->
            alert("Upload Error", "Your post vid may not have been uploaded. " +
                    "Please try again or report the bug if it persists.")
            Log.d("uploadError", error.toString())
        }
        uploadTask.addOnSuccessListener { taskSnap ->
            val postVidURLString = taskSnap.downloadUrl.toString()
            post["postPicURLString"] = "n/a"
            post["postVidURLString"] = postVidURLString
            post["postVidPreviewURLString"] = postVidPreviewURLString

            postRef.setValue(post)
            val timestamp = post["timestamp"] as String
            postPostID(postID, timestamp)
            setGeoFirePost(postID)
            logPostSent(postID, "video")
            progressBar.visibility = ProgressBar.INVISIBLE
            misc.playSound(context, R.raw.send_post, 0)
            setHomeSegment()
            observePosts()
            writeToAddedAndHistory(postID, originalReverseTimestamp, originalTimestamp)
            writeTagged(postID, content)
        }
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
        isRemoved = false

        val posts = determinePosts()
        val lastPostID = posts.lastOrNull()?.postID
        val postRef = ref.child("posts")

        if (scrollPosition == "middle" && !posts.isEmpty()) {
            val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
            val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
            for (index in firstVisiblePosition..lastVisiblePosition) {
                val postID = posts[index].postID
                val idRef = postRef.child(postID)
                idRef.addListenerForSingleValueEvent( object: ValueEventListener {
                    override fun onDataChange(snap: DataSnapshot?) {
                        val post = snap?.value as? MutableMap<String,Any> ?: mutableMapOf()
                        if (!post.isEmpty()) {
                            val isDeleted = post["isDeleted"] as? Boolean ?: false
                            val reports = post["reports"] as? Int ?: 0

                            misc.getVoteStatus(postID, null, myID) { voteStatus ->
                                if (reports < 3 && !isDeleted) {
                                    val formattedPost = misc.formatPost(postID, voteStatus, post)
                                    when (selectedSegment) {
                                        "hot" -> { hotPosts[index] = formattedPost }
                                        "added" -> { addedPosts[index] = formattedPost }
                                        else -> { newPosts[index] = formattedPost }
                                    }
                                }
                            }
                        }
                    }
                    override fun onCancelled(error: DatabaseError?) { Log.d("databaseError", error.toString()) }
                })
            }
            displayProgress = false
            refreshRecycler()

        } else {
            val postsList: MutableList<Post> = mutableListOf()

            when (selectedSegment) {
                "hot" -> {
                    val score: Double
                    val firstScore = -1.0
                    val lastScore = posts.lastOrNull()?.score
                    if (scrollPosition == "bottom") {
                        score = lastScore ?: firstScore
                    } else {
                        score = firstScore
                    }
                    getPostIDs("hot", misc.getTimestamp("UTC", Date()), score)

                    for (id in hotPostIDs) {
                        val hotRef = postRef.child(id)
                        hotRef.addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snap: DataSnapshot?) {
                                val post = snap?.value as? MutableMap<String, Any> ?: mutableMapOf()
                                if (!post.isEmpty()) {
                                    val isDeleted = post["isDeleted"] as? Boolean ?: false
                                    val reports = post["reports"] as? Int ?: 0

                                    misc.getVoteStatus(id, null, myID) { voteStatus ->
                                        if (reports < 3 && !isDeleted) {
                                            val formattedPost = misc.formatPost(id, voteStatus, post)
                                            postsList.add(formattedPost)
                                        }
                                    }
                                }
                            }
                            override fun onCancelled(error: DatabaseError?) { Log.d("databaseError", error.toString()) }
                        })
                    }

                    if (scrollPosition == "bottom") {
                        if (lastPostID != postsList.lastOrNull()?.postID) {
                            hotPosts.addAll(postsList)
                        }
                    } else {
                        hotPosts = postsList
                    }
                    displayProgress = false
                    refreshRecycler()
                }

                "added" -> {
                    val reverseTimestamp: Double
                    val currentReverseTimestamp = misc.getCurrentReverseTimestamp()
                    val lastReverseTimestamp = posts.lastOrNull()?.originalReverseTimestamp
                    if (scrollPosition == "bottom") {
                        reverseTimestamp = lastReverseTimestamp ?: currentReverseTimestamp
                    } else {
                        reverseTimestamp = currentReverseTimestamp
                    }

                    addedValueListener = object : ValueEventListener {
                        override fun onDataChange(snap: DataSnapshot?) {
                            val dict = snap?.value as? MutableMap<String, Any> ?: mutableMapOf()
                            val postIDs = dict.keys.toMutableList()

                            for (id in postIDs) {
                                val addedRef = postRef.child(id)
                                addedRef.addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onDataChange(snap: DataSnapshot?) {
                                        val post = snap?.value as? MutableMap<String, Any> ?: mutableMapOf()
                                        if (!post.isEmpty()) {
                                            val isDeleted = post["isDeleted"] as? Boolean ?: false

                                            misc.getVoteStatus(id, null, myID) { voteStatus ->
                                                if (!isDeleted) {
                                                    val formattedPost = misc.formatPost(id, voteStatus, post)
                                                    postsList.add(formattedPost)
                                                }
                                            }
                                        }
                                    }
                                    override fun onCancelled(error: DatabaseError?) { Log.d("databaseError", error.toString()) }
                                })
                            }

                            if (scrollPosition == "bottom") {
                                if (lastPostID != postsList.lastOrNull()?.postID) {
                                    addedPosts.addAll(postsList)
                                }
                            } else {
                                addedPosts = postsList
                            }
                            displayProgress = false
                            refreshRecycler()
                        }

                        override fun onCancelled(error: DatabaseError?) {
                            Log.d("databaseError", error.toString())
                        }
                    }
                    val userAddedRef = ref.child("userAddedPosts").child(myID)
                    userAddedRef.orderByChild("originalReverseTimestamp").startAt(reverseTimestamp).limitToFirst(88).addValueEventListener(addedValueListener)
                }

                else -> {
                    val center = GeoLocation(latitude, longitude)
                    geoQuery = geoFirePosts.queryAtLocation(center, radiusMeters / 1000)

                    (geoQuery as GeoQuery).addGeoQueryEventListener(object : GeoQueryEventListener {
                        override fun onKeyEntered(key: String?, location: GeoLocation?) {}
                        override fun onGeoQueryReady() {
                            val timestamp: String
                            val firstTime = misc.getTimestamp("UTC", Date())
                            val lastTime = posts.lastOrNull()?.timestampUTC
                            if (scrollPosition == "bottom") {
                                timestamp = lastTime ?: firstTime
                            } else {
                                timestamp = firstTime
                            }
                            getPostIDs("new", timestamp, -1.0)

                            for (id in newPostIDs) {
                                val newRef = postRef.child(id)
                                newRef.addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onDataChange(snap: DataSnapshot?) {
                                        val post = snap?.value as? MutableMap<String, Any> ?: mutableMapOf()
                                        if (!post.isEmpty()) {

                                            val isDeleted = post["isDeleted"] as? Boolean ?: false
                                            val reports = post["reports"] as? Int ?: 0

                                            if (reports < 3 && !isDeleted) {
                                                val formattedPost = misc.formatPost(id, myID, post)
                                                postsList.add(formattedPost)
                                            }
                                        }
                                    }
                                    override fun onCancelled(error: DatabaseError?) { Log.d("databaseError", error.toString()) }
                                })
                            }

                            if (scrollPosition == "bottom") {
                                if (lastPostID != postsList.lastOrNull()?.postID) {
                                    newPosts.addAll(postsList)
                                }
                            } else {
                                newPosts = postsList
                            }
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

    fun removeObserverForPosts() {
        isRemoved = true

        if (geoQuery != null) {
            (geoQuery as GeoQuery).removeAllListeners()
        }

        val userAddedRef = ref.child("userAddedPosts").child(myID)
        if (addedValueListener != null) {
            userAddedRef.removeEventListener(addedValueListener)
            addedValueListener = null
        }
    }

    fun upvote(index: Int) {
        val individualPost = determinePosts()[index]
        val postID = individualPost.postID
        val userID = individualPost.userID
        val content = individualPost.content
        val voteStatus = individualPost.voteStatus

        misc.playSound(context, R.raw.pop_drip, 0)
        when (voteStatus) {
            "up" -> { individualPost.points -= 1 }
            "down" -> { individualPost.points += 2 }
            else -> { individualPost.points += 1 }
        }
        individualPost.voteStatus = "up"
        when (selectedSegment) {
            "hot" -> { hotPosts[index] = individualPost }
            "added" -> { addedPosts[index] = individualPost }
            else -> { newPosts[index] = individualPost }
        }
        refreshRecycler()

        val amIBlocked = misc.amIBlocked(userID, blockedBy)
        if (!amIBlocked) {
            misc.upvote(context, postID, myID, userID, voteStatus, content)
            logUpvoted(userID, postID)
        } else {
            individualPost.voteStatus = "none"
            when (selectedSegment) {
                "hot" -> { hotPosts[index] = individualPost }
                "added" -> { addedPosts[index] = individualPost }
                else -> { newPosts[index] = individualPost }
            }
            refreshRecycler()
            alert("Blocked", "This person has blocked you. You cannot vote on their posts.")
            return
        }
    }

    fun downvote(index: Int) {
        val individualPost = determinePosts()[index]
        val postID = individualPost.postID
        val userID = individualPost.userID
        val voteStatus = individualPost.voteStatus

        when (voteStatus) {
            "up" -> { individualPost.points -= 2 }
            "down" -> { individualPost.points += 1 }
            else -> { individualPost.points -= 1 }
        }
        individualPost.voteStatus = "down"
        when (selectedSegment) {
            "hot" -> { hotPosts[index] = individualPost }
            "added" -> { addedPosts[index] = individualPost }
            else -> { newPosts[index] = individualPost }
        }
        refreshRecycler()

        val amIBlocked = misc.amIBlocked(userID, blockedBy)
        if (!amIBlocked) {
            misc.downvote(postID, myID, userID, voteStatus)
            logDownvoted(userID, postID)
        } else {
            individualPost.voteStatus = "none"
            when (selectedSegment) {
                "hot" -> { hotPosts[index] = individualPost }
                "added" -> { addedPosts[index] = individualPost }
                else -> { newPosts[index] = individualPost }
            }
            refreshRecycler()
            alert("Blocked", "This person has blocked you. You cannot vote on their posts.")
            return
        }
    }

    fun writePost(type: String, text: String, bitmap: Bitmap?, uri: Uri?) {
        homeInteractionListener?.dismissKeyboardFromHome()
        popWindow?.dismiss()
        progressBar.visibility = ProgressBar.VISIBLE

        val isDeleted = false
        val isEdited = false
        val reports = 0

        val userID = myID
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        var handle = sharedPreferences.getString("handle.flocal", "error")
        if (handle == "error") {
            misc.getHandle(myID) { myHandle ->
                handle = myHandle
                val editor = sharedPreferences.edit()
                editor.putString("handle.flocal", handle)
                editor.apply()
            }
        }

        val points = 0
        val upvotes = 0
        val downvotes = 0
        val score = 0
        val content = text
        val timestamp = misc.getTimestamp("UTC", Date())
        val replyCount = 0
        val originalReverseTimestamp = misc.getCurrentReverseTimestamp()
        val originalTimestamp = -1*originalReverseTimestamp

        var profilePicURLString = "error"
        if (myProfilePicURL != null) {
            profilePicURLString = myProfilePicURL.toString()
        }

        val postRef = ref.child("posts").push()
        val postID = postRef.key

        val post: MutableMap<String,Any> = mutableMapOf("longitude" to longitude, "latitude" to latitude, "isDeleted" to isDeleted,
                "isEdited" to isEdited, "reports" to reports, "userID" to userID, "profilePicURLString" to profilePicURLString, "type" to type,
                "handle" to handle, "points" to points, "upvotes" to upvotes, "downvotes" to downvotes, "score" to score, "originalContent" to content,
                "content" to content, "timestamp" to timestamp, "replyCount" to replyCount, "originalReverseTimestamp" to originalReverseTimestamp, "originalTimestamp" to originalTimestamp)

        when (type) {
            "image" -> {
                uploadPostPic(bitmap!!, postID, post)
            }
            "video" -> {
                uploadPostVid(uri!!, bitmap!!, postID, post)
            }
            else -> {
                postRef.setValue(post)
                postPostID(postID, timestamp)
                setGeoFirePost(postID)
                logPostSent(postID, type)
                progressBar.visibility = ProgressBar.INVISIBLE
                misc.playSound(context, R.raw.send_post, 0)
                setHomeSegment()
                observePosts()
                writeToAddedAndHistory(postID, originalReverseTimestamp, originalTimestamp)
                writeTagged(postID, content)
            }
        }
    }

    fun setGeoFirePost(postID: String) {
        val location = GeoLocation(latitude, longitude)
        geoFirePosts.setLocation(postID, location)
        Timer().schedule( object: TimerTask() {
            override fun run() {
                geoFirePosts.removeLocation(postID)
            }
        }, 5000)
    }

    fun writeToAddedAndHistory(postID: String, originalReverseTimestamp: Double, originalTimestamp: Double) {
        val userPostHistoryRef = ref.child("userPostHistory").child(myID).child(postID)
        userPostHistoryRef.child("originalReverseTimestamp").setValue(originalReverseTimestamp)
        userPostHistoryRef.child("originalTimestamp").setValue(originalTimestamp)
        userPostHistoryRef.child("points").setValue(0)

        val userAddedPostsRef = ref.child("userAddedPosts")
        userAddedPostsRef.child(myID).child(postID).child("originalReverseTimestamp").setValue(originalReverseTimestamp)
        userAddedPostsRef.child(myID).child(postID).child("originalTimestamp").setValue(originalTimestamp)

        misc.getFollowers(myID) { userFollowers ->
            if (!userFollowers.isEmpty()) {
                val fanoutObject = mutableMapOf<String,Any>()
                for (followerID in userFollowers) {
                    fanoutObject.put("/$followerID/$postID/originalReverseTimestamp", originalReverseTimestamp)
                    fanoutObject.put("/$followerID/$postID/originalTimestamp", originalTimestamp)
                }
                userAddedPostsRef.updateChildren(fanoutObject)
            }
        }
    }

    fun writeTagged(postID: String, content: String) {
        val tagged = misc.handlesWithoutAt(content)
        for (tag in tagged) {
            val tagLower = tag.toLowerCase()
            val userRef = ref.child("users")
            userRef.orderByChild("handleLower").equalTo(tagLower).addListenerForSingleValueEvent( object: ValueEventListener {
                override fun onDataChange(snap: DataSnapshot?) {
                    if (snap?.value != null) {
                        val users = snap.value as? MutableMap<String,Any> ?: mutableMapOf()
                        val userID = users.keys.first()
                        val amIBlocked = misc.amIBlocked(userID, blockedBy)
                        if (userID != myID && !amIBlocked) {
                            misc.writeTaggedNotification(context, userID, myID, postID, content, "post")
                            logUserTagged(postID, userID, tag)
                        }
                    }
                }
                override  fun onCancelled(error: DatabaseError?) { Log.d("databaseError", error.toString()) }
            })
        }
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

    // MARK: - OkHttp

    fun getPostIDs(type: String, lastTimestamp: String, lastScore: Double) {
        val JSON = MediaType.parse("application/json; charset=utf-8")
        val params: HashMap<String,Any> = hashMapOf("longitude" to longitude, "latitude" to latitude,
                "lastTimestamp" to lastTimestamp, "lastScore" to lastScore, "sort" to type, "action" to "search")
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
        if (type == "new") {
            newPostIDs = list
        } else {
            hotPostIDs = list
        }
    }

    fun postPostID(postID: String, timestamp: String) {
        val JSON = MediaType.parse("application/json; charset=utf-8")
        val params: HashMap<String,Any> = hashMapOf("longitude" to longitude, "latitude" to latitude, "postID" to postID,
                "timestamp" to timestamp, "userID" to myID, "action" to "post")
        val parameter = JSONObject(params)

        val client = OkHttpClient()
        val body = RequestBody.create(JSON, parameter.toString())
        val request = Request.Builder().url("https://flocalApp.us-west-1.elasticbeanstalk.com").post(body).build()

        val response = client.newCall(request).execute()
        val json = response.body()?.string()
        Log.d("server", json)
    }


}
