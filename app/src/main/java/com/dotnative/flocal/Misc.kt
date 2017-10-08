@file:Suppress("UNCHECKED_CAST", "MemberVisibilityCanPrivate", "CascadeIf", "LiftReturnOrAssignment", "UnnecessaryVariable", "SetTextI18n")

package com.dotnative.flocal

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.preference.PreferenceManager
import android.util.Log
import android.widget.ImageView
import com.google.firebase.database.*
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class Misc {

    // MARK: - Colors

//    val softGreyColor = Color.parseColor("#F0F0F0")
    val flocalColor = Color.parseColor("#006064")
    val flocalFade = Color.parseColor("#26006064")

    val flocalBlue = Color.parseColor("#2196F3")
    val flocalBlueGrey = Color.parseColor("#607D8B")
    val flocalGreen = Color.parseColor("#4CAF50")
    val flocalOrange = Color.parseColor("#FF4605")
    val flocalPurple = Color.parseColor("#673AB7")
    val flocalRed = Color.parseColor("#F44336")
    val flocalTeal = Color.parseColor("#009688")
    val flocalYellow = Color.parseColor("#FFD600")

    val flocalBlueFade = Color.parseColor("#262196F3")
    val flocalBlueGreyFade = Color.parseColor("#26607D8B")
    val flocalGreenFade = Color.parseColor("#264CAF50")
    val flocalOrangeFade = Color.parseColor("#26FF4605")
    val flocalPurpleFade = Color.parseColor("#26673AB7")
    val flocalRedFade = Color.parseColor("#26F44336")
    val flocalTealFade = Color.parseColor("#26009688")
    val flocalYellowFade = Color.parseColor("#26FFD600")

    // MARK: - Navigation

    @SuppressLint("ApplySharedPref")
    fun setMyID(context: Context): String {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val myID = sharedPreferences.getString("myID.flocal", "0")
        return myID
    }

    fun getSideMenuIndex(context: Context): Int {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val index = sharedPreferences.getInt("sideMenuIndex.flocal", 42)
        return index
    }

    fun setSideMenuIndex(context: Context, index: Int): Int {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = sharedPreferences.edit()
        editor.putInt("sideMenuIndex.flocal", index)
        editor.apply()
        return index
    }

    // MARK - Posts

    fun formatTimestamp(timestampString: String): String {
        val utcDateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        utcDateFormatter.timeZone = TimeZone.getTimeZone("UTC")
        val utcDate = utcDateFormatter.parse(timestampString)

        val localDateFormatter = SimpleDateFormat("h:mm a MMM dd, yyyy", Locale.US)
        localDateFormatter.timeZone = TimeZone.getDefault()
        val timestamp = localDateFormatter.format(utcDate)

        return timestamp
    }

    fun getTimestamp(zone: String, date: Date): String {
        if (zone == "UTC") {
            val utcDateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            utcDateFormatter.timeZone = TimeZone.getTimeZone("UTC")
            val timestamp = utcDateFormatter.format(date)
            return timestamp
        } else {
            val localDateFormatter = SimpleDateFormat("h:mm a MMM dd, yyyy", Locale.US)
            localDateFormatter.timeZone = TimeZone.getDefault()
            val timestamp = localDateFormatter.format(date)
            return timestamp
        }
    }


    fun getCurrentReverseTimestamp(): Double {
        return (0 - System.currentTimeMillis()/1000).toDouble()
    }

    fun roundDouble(double: Double, places: Int): Double {
        var bigDecimal = BigDecimal(double)
        bigDecimal = bigDecimal.setScale(places, RoundingMode.HALF_UP)
        return bigDecimal.toDouble()
    }

    fun setCount(count: Int): String {
        val countDouble = count.toDouble()
        val countAbs = Math.abs(countDouble)

        var rounded: String
        if (countAbs >= 10000 && countAbs < 1000000) {
            val countThousand = countAbs/1000
            val countRounded = roundDouble(countThousand, 1)
            rounded = "$countRounded" + "k"
        } else if (countAbs >= 1000000 && countAbs < 1000000000) {
            val countMillion = countAbs/1000000
            val countRounded = roundDouble(countMillion, 1)
            rounded = "$countRounded" + "M"
        } else if (countAbs >= 1000000000) {
            val countBillion = countAbs/1000000000
            val countRounded = roundDouble(countBillion, 1)
            rounded = "$countRounded" + "M"
        } else {
            rounded = "$countAbs"
        }

        if (countDouble < 0) {
            rounded = "-$rounded"
        }

        return rounded
    }

    fun setPointsColor(points: Int, source: String): Int {
        if (points < 0) {
            return flocalBlueGrey
        } else if (points == 0) {
            return Color.DKGRAY
        } else {
            if (source == "profile") {
                return flocalOrange
            } else {
                return flocalColor
            }
        }
    }

    // MARK: - Profile

    fun getSpecialHandles(): Array<String> {
        val spec = arrayOf("georgetang", "george.tang", "george_tang", "gtang", "gtang42",
                "gtang43", "george", "georget", "tang", "native", "nativ", "dotnativ", "dotnative",
                ".nativ", ".native", "flocal", ".flocal", "0", "god", "buddha", "shiva", "satan", "vishnu",
                "hitler", "error")
        return spec
    }

    fun checkSpecialCharacters(handle: String): Boolean {
        val pattern = Pattern.compile("[^a-zA-Z0-9._]")
        val matcher = pattern.matcher(handle)
        return matcher.find()
    }

    fun formatPhoneNumber(phoneNumber: String): String {
        val numbersOnly = phoneNumber.replace("[^0-9]", "")
        val length = numbersOnly.length

        val firstChar = numbersOnly[0]
        var isLeadOne = false
        if (firstChar.toString() == "1") {
            isLeadOne = true
        }

        if ((length == 7) || (length == 10) || (length == 11 && isLeadOne)) {
            Log.d("Phone", "length ok")
        } else {
            return phoneNumber
        }

        val hasAreaCode = length >= 10
        var sourceIndex = 0

        var leadingOne = ""
        if (isLeadOne) {
            leadingOne = "1 "
            sourceIndex += 1
        }

        var areaCode = ""
        if (hasAreaCode) {
            val areaCodeLength = 3
            val areaCodeSubstring = numbersOnly.substring(sourceIndex, sourceIndex+areaCodeLength)
            areaCode = "($areaCodeSubstring)"
            sourceIndex += areaCodeLength
        }

        val prefixLength = 3
        val prefix = numbersOnly.substring(sourceIndex, sourceIndex+prefixLength)
        sourceIndex += prefixLength

        val suffixLength = 4
        val suffix = numbersOnly.substring(sourceIndex, sourceIndex+suffixLength)
        sourceIndex += suffixLength

        return leadingOne + areaCode + prefix + "-" + suffix
    }

    fun stringWithColoredTags(string: String, time: String?): String {
        val stringArray = string.split(" ")

        val handlesToColor: MutableList<String> = mutableListOf()
        for (word in stringArray) {
            if (word.first().toString() == "@") {
                handlesToColor.add(word)
            }
        }

        val coloredString = string
        for (handle in handlesToColor) {
            coloredString.replace(handle,"<font color='#006064'>$handle</font>")
        }

        if (time != null) {
            coloredString.replace("$time", "<font color='#808080'>$time</font>")
        }

        return coloredString
    }

    fun handlesWithoutAt(string: String): MutableList<String> {
        val stringArray = string.split(" ")

        val handles: MutableList<String> = mutableListOf()
        for (word in stringArray) {
            if (word.first().toString() == "@") {
                handles.add(word)
            }
        }

        for (handle in handles) {
            val index = handles.indexOf(handle)
            val handleNoAt = handle.substring(1)
            handles.removeAt(index)
            handles.add(index, handleNoAt)
        }

        return handles
    }

    fun setDefaultPic(handle: String): Int {
        val handleLower = handle.toLowerCase()
        val firstLetter = handleLower.first().toString()
        when (firstLetter) {
            "a", "b", "c" -> { return R.drawable.me_red }
            "d", "e", "f" -> { return R.drawable.me_orange }
            "h", "i", "j" -> { return R.drawable.me_yellow }
            "k", "l", "m" -> { return R.drawable.me_green }
            "n", "o", "p", "q" -> { return R.drawable.me_blue }
            "r", "s", "u", "v" -> { return R.drawable.me_teal }
            "w", "x", "y", "z" -> { return R.drawable.me_purple }
            else -> { return R.drawable.me_s }
        }
    }

    fun setChatID(myID: String, userID: String): String {
        if (myID < userID) {
            return myID + "_" + userID
        } else {
            return userID + "_" + myID
        }
    }

    fun setFollowersColor(followers: Int): Int {
        if (followers <= 0) {
            return Color.DKGRAY
        } else {
            return flocalGreen
        }
    }

    // MARK: Other

    fun displayAlert(context: Context, title: String, message: String) {
        val alert = AlertDialog.Builder(context)
        alert.setTitle(title)
        alert.setMessage(message)
        alert.setPositiveButton("Ok", { _, _ -> })
        alert.create()
        alert.show()
    }

    var mp = MediaPlayer()
    fun playSound(context: Context, rid: Int, start: Int) {
        if (mp.isPlaying) {
            mp.stop()
            mp.release()
        }
        mp = MediaPlayer.create(context, rid)
        mp.seekTo(start)
        mp.start()
    }

    fun setImageAspect(imageView: ImageView, bitmap: Bitmap) {
        val w = bitmap.width
        val h = bitmap.height
        val layoutWidth = imageView.width

        if (w > h) {
            imageView.layoutParams.height = (9*layoutWidth)/16
        } else if (w == h){
            imageView.layoutParams.height = layoutWidth
        } else {
            imageView.layoutParams.height = (4*layoutWidth)/3
        }
    }

    // MARK: Firebase

    val ref: DatabaseReference = FirebaseDatabase.getInstance().reference

    fun addNotificationBadge(userID: String) {
        val badgeNumberRef = ref.child("users").child(userID)
        badgeNumberRef.runTransaction( object: Transaction.Handler {
            override fun doTransaction(data: MutableData?): Transaction.Result {
                val user = data?.value as? MutableMap<String,Any> ?: return Transaction.success(data)
                var badgeNumber = user["notificationBadge"] as? Int ?: 0
                badgeNumber += 1
                user["notificationBadge"] = badgeNumber
                data.value = user
                return Transaction.success(data)
            }
            override fun onComplete(p0: DatabaseError?, p1: Boolean, p2: DataSnapshot?) {}
        })
    }

    fun setNotification(postID: String, myID: String, userID: String, handle: String, notification: String, type: String) {
        val timestamp = getTimestamp("UTC", Date())
        val originalReverseTimestamp = getCurrentReverseTimestamp()

        val not: MutableMap<String,Any> = mutableMapOf("postID" to postID, "userID" to myID, "handle" to handle,
                "type" to type, "timestamp" to timestamp, "originalReverseTimestamp" to originalReverseTimestamp, "notification" to notification)
        ref.child("userNotifications").child(userID).push().setValue(not)
        addNotificationBadge(userID)

        if (type == "tagged" || type == "chat") {
            postNotification(notification, type, userID)
        }
    }

    fun writePointNotification(context: Context, userID: String, myID: String, postID: String, content: String, type: String) {
        val userRef = ref.child("users").child(userID)
        userRef.child("lastNotificationType").setValue("upvote")

        var notification: String
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        var handle = sharedPreferences.getString("handle.flocal", "error")
        if (handle != "error") {
            notification = "@$handle has upvoted your $type: $content"
            setNotification(postID, myID, userID, handle, notification, "upvote")
        } else {
            getHandle(myID) { myHandle ->
                handle = myHandle
                if (handle == "error") {
                    notification = "Your $type has been upvoted: $content"
                } else {
                    notification = "@$handle has upvoted your $type: $content"
                }
                setNotification(postID, myID, userID, handle, notification, "upvote")
            }
        }

    }

    fun writeTaggedNotification(context: Context, userID: String, myID: String, postID: String, content: String, type: String) {
        val userRef = ref.child("users").child(userID)
        userRef.child("lastNotificationType").setValue("tagg")

        var notification: String
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        var handle = sharedPreferences.getString("handle.flocal", "error")
        if (handle != "error") {
            notification = "@$handle tagged you in a $type: $content"
            setNotification(postID, myID, userID, handle, notification, "tagged")
        } else {
            getHandle(myID) { myHandle ->
                handle = myHandle
                if (handle == "error") {
                    notification = "You'be been tagged in a $type: $content"
                } else {
                    notification = "@$handle tagged you in a $type: $content"
                    val editor = sharedPreferences.edit()
                    editor.putString("handle.flocal", handle)
                    editor.apply()
                }
                setNotification(postID, myID, userID, handle, notification, "tagged")
            }
        }
    }

    fun writeAddedNotification(context: Context, userID: String, myID: String) {
        val userRef = ref.child("users").child(userID)
        userRef.child("lastNotificationType").setValue("added")

        var notification: String
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        var handle = sharedPreferences.getString("handle.flocal", "error")
        if (handle != "error") {
            notification = "@$handle has added you! :)"
            setNotification("0", myID, userID, handle, notification, "added")
        } else {
            getHandle(myID) { myHandle ->
                handle = myHandle
                if (handle == "error") {
                    notification = "You have a new follower! :)"
                } else {
                    notification = "@$handle has added you! :)"
                    val editor = sharedPreferences.edit()
                    editor.putString("handle.flocal", handle)
                    editor.apply()
                }
                setNotification("0", myID, userID, handle, notification, "added")
            }
        }
    }

    fun writeAmITyping(bool: Boolean, chatID: String, myID: String) {
        val chatRef = ref.child("chats").child(chatID)
        val child = myID + "_typing"
        chatRef.child("info").child(child).setValue(bool)
    }

    fun writeAmIInChat(bool: Boolean, chatID: String, myID: String) {
        val chatRef = ref.child("chats").child(chatID)
        chatRef.child("info").child(myID).setValue(bool)
    }

    fun writeChatNotification(context: Context, userID: String, myID: String, message: String, type: String) {
        val userRef = ref.child("users").child(userID)
        userRef.child("lastNotificationType").setValue("chat")

        val chatMessage: String
        when (type) {
            "image" -> { chatMessage = "image sent" }
            "video" -> { chatMessage = "video sent" }
            else -> { chatMessage = message }
        }

        var notification: String
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        var handle = sharedPreferences.getString("handle.flocal", "error")
        if (handle != "error") {
            notification = "@$handle: $chatMessage"
            setNotification("0", myID, userID, handle, notification, "chat")
        } else {
            getHandle(myID) { myHandle ->
                handle = myHandle
                if (handle == "error") {
                    notification = "You've received a message: $chatMessage"
                } else {
                    notification = "@$handle: $chatMessage"
                    val editor = sharedPreferences.edit()
                    editor.putString("handle.flocal", handle)
                    editor.apply()
                }
                setNotification("0", myID, userID, handle, notification, "chat")
            }
        }
    }

    fun getVoteStatus(postID: String, replyID: String?, myID: String, callback: (String) -> Unit) {
        val voteHistoryRef: DatabaseReference
        val postVoteHistoryRef = ref.child("postVoteHistory").child(postID)
        if (replyID == null) {
            voteHistoryRef = postVoteHistoryRef.child(myID)
        } else {
            voteHistoryRef = postVoteHistoryRef.child("replies").child(replyID).child(myID)
        }
        voteHistoryRef.addListenerForSingleValueEvent( object: ValueEventListener {
            override fun onDataChange(snap: DataSnapshot?) {
                if (snap?.value != null) {
                    val vote = snap.value as? Boolean ?: true
                    if (vote) {
                        callback("up")
                    } else {
                        callback("down")
                    }
                } else {
                    callback("none")
                }
            }
            override  fun onCancelled(error: DatabaseError?) { Log.d("databaseError", error.toString()) }
        })
    }

    fun getFollowers(userID: String, callback: (MutableList<String>) -> Unit) {
        val followersRef = ref.child("userFollowers").child(userID)
        followersRef.addListenerForSingleValueEvent( object: ValueEventListener {
            override fun onDataChange(snap: DataSnapshot?) {
                val follow = snap?.value as? MutableMap<String,Any> ?: mutableMapOf()
                callback(follow.keys.toMutableList())
            }
            override fun onCancelled(error: DatabaseError?) { Log.d("DatabaseError", error.toString()) }
        })
    }

    fun amIBlocked(userID: String, blockedBy: MutableList<String>): Boolean {
        if (blockedBy.contains(userID)) {
            return true
        }
        return false
    }

    fun didIBlock(userID: String, blocked: MutableList<String>): Boolean {
        if (blocked.contains(userID)) {
            return true
        }
        return false
    }

    fun didIAdd(userID: String, myID: String, callback: (Boolean) -> Unit) {
        val userAddedRef = ref.child("userAdded").child(myID).child(userID)
        userAddedRef.addListenerForSingleValueEvent(object: ValueEventListener {
            override fun onDataChange(snap: DataSnapshot?) {
                if (snap?.value != null) {
                    callback(true)
                } else {
                    callback(false)
                }
            }
            override fun onCancelled(error: DatabaseError?) { Log.d("DatabaseError", error.toString()) }
        })
    }

    fun getHandle(userID: String, callback: (String) -> Unit){
        val userRef = ref.child("users").child(userID)
        userRef.addListenerForSingleValueEvent(object: ValueEventListener {
            override fun onDataChange(snap: DataSnapshot?) {
                val dict = snap?.value as? MutableMap<String,Any> ?: mutableMapOf()
                val handle = dict["handle"] as? String ?: "error"
                callback(handle)
            }
            override fun onCancelled(error: DatabaseError?) { Log.d("DatabaseError", error.toString()) }
        })
    }

    fun doesUserExist(handle: String, callback: (Boolean) -> Unit) {
        val handleLower = handle.toLowerCase().trim()
        val userRef = ref.child("users")
        userRef.orderByChild("handleLower").equalTo(handleLower).addListenerForSingleValueEvent( object: ValueEventListener {
            override fun onDataChange(snap: DataSnapshot?) {
                if  (snap?.value != null) {
                    callback(true)
                } else {
                    callback(false)
                }
            }
            override fun onCancelled(error: DatabaseError?) { Log.d("DatabaseError", error.toString()) }
        })
    }

    fun addUser(userID: String, myID: String) {
        val originalReverseTimestamp = getCurrentReverseTimestamp()

        val userRef = ref.child("users").child(userID)
        val meRef = ref.child("users").child(myID)
        val userAddedRef = ref.child("userAdded")
        val userFollowersRef = ref.child("userFollowers")

        var updatedFollowersCount = 0
        userRef.runTransaction( object: Transaction.Handler {
            override fun doTransaction(data: MutableData?): Transaction.Result {
                val user = data?.value as? MutableMap<String,Any> ?: return Transaction.success(data)
                var count = user["followersCount"] as? Int ?: 0
                count += 1
                updatedFollowersCount = count
                user["followersCount"] = count
                data.value = user
                return Transaction.success(data)
            }
            override fun onComplete(p0: DatabaseError?, p1: Boolean, p2: DataSnapshot?) {}
        })

        userRef.addListenerForSingleValueEvent( object: ValueEventListener {
            override fun onDataChange(snap: DataSnapshot?) {
                val dict = snap?.value as? MutableMap<String,Any> ?: mutableMapOf()
                val handle = dict["handle"] as? String ?: "error"
                val description = dict["description"] as? String ?: "error"
                val points = dict["points"] as? Int ?: 0
                val profilePicURLString = dict["profilePicURLString"] as? String ?: "error"
                val user: MutableMap<String,Any> = mutableMapOf("handle" to handle, "description" to description,
                        "points" to points, "followersCount" to updatedFollowersCount, "profilePicURLString" to profilePicURLString,
                        "originalReverseTimestamp" to originalReverseTimestamp)
                userAddedRef.child(myID).child(userID).setValue(user)
            }
            override fun onCancelled(error: DatabaseError?) { Log.d("DatabaseError", error.toString()) }
        })

        meRef.addListenerForSingleValueEvent( object: ValueEventListener {
            override fun onDataChange(snap: DataSnapshot?) {
                val dict = snap?.value as? MutableMap<String,Any> ?: mutableMapOf()
                val handle = dict["handle"] as? String ?: "error"
                val description = dict["description"] as? String ?: "error"
                val points = dict["points"] as? Int ?: 0
                val followersCount = dict["followersCount"] as? Int ?: 0
                val profilePicURLString = dict["profilePicURLString"] as? String ?: "error"
                val me: MutableMap<String,Any> = mutableMapOf("handle" to handle, "description" to description,
                        "points" to points, "followersCount" to followersCount, "profilePicURLString" to profilePicURLString,
                        "originalReverseTimestamp" to originalReverseTimestamp)
                userFollowersRef.child("followers").child(myID).setValue(me)
            }
            override fun onCancelled(error: DatabaseError?) { Log.d("DatabaseError", error.toString()) }
        })

        getFollowers(userID) { userFollowers ->
            if (!userFollowers.isEmpty()) {
                val fanoutObject = mutableMapOf<String,Any>()
                for (followerID in userFollowers) {
                    fanoutObject.put("/$followerID/$userID/followersCount", updatedFollowersCount)
                }
                userAddedRef.updateChildren(fanoutObject)
            }
        }
    }

    fun hasUpvoteNotified(postID: String, replyID: String?, myID: String, callback: (Boolean) -> Unit) {
        val hasUpvoteNotifiedRef: DatabaseReference
        val postVoteHistoryRef = ref.child("postVoteHistory").child(postID)
        if (replyID != null && replyID != "0") {
            hasUpvoteNotifiedRef = postVoteHistoryRef.child("replies").child(replyID).child("upvoteNotified").child(myID)
        } else {
            hasUpvoteNotifiedRef = postVoteHistoryRef.child("upvoteNotified").child(myID)
        }
        hasUpvoteNotifiedRef.addListenerForSingleValueEvent( object: ValueEventListener {
            override fun onDataChange(snap: DataSnapshot?) {
                if (snap?.value != null) {
                    callback(true)
                } else {
                    callback(false)
                }
            }
            override fun onCancelled(error: DatabaseError?) { Log.d("databaseError", error.toString()) }
        })
    }

    fun upvote(context: Context, postID: String, myID: String, userID: String, voteStatus: String, content: String) {
        val postVoteHistoryRef = ref.child("postVoteHistory").child(postID).child(myID)
        val postRef = ref.child("posts").child(postID)
        postRef.runTransaction( object: Transaction.Handler {
            override fun doTransaction(data: MutableData?): Transaction.Result {
                val post = data?.value as? MutableMap<String,Any> ?: return Transaction.success(data)
                var upvotes = post["upvotes"] as? Int ?: 0
                var downvotes = post["downvotes"] as? Int ?: 0
                when (voteStatus) {
                    "up" -> {
                        upvotes -= 1
                        postVoteHistoryRef.removeValue()
                    }
                    "down" -> {
                        upvotes += 1
                        downvotes -= 1
                        postVoteHistoryRef.setValue(true)
                    }
                    else -> {
                        upvotes += 1
                        postVoteHistoryRef.setValue(true)
                    }
                }
                post["upvotes"] = upvotes
                post["downvotes"] = downvotes
                post["points"] = upvotes - downvotes
                data.value = post
                return Transaction.success(data)
            }
            override fun onComplete(p0: DatabaseError?, p1: Boolean, p2: DataSnapshot?) {}
        })

        hasUpvoteNotified(postID, null, myID) { notified ->
            if ((myID != userID) && !notified) {
                val hasUpvotedRef =  ref.child("postVoteHistory").child(postID).child("upvoteNotified").child(myID)
                hasUpvotedRef.setValue(true)
                writePointNotification(context, userID, myID, postID, content, "post")
            }
        }

        val userRef = ref.child("users").child(userID)
        userRef.runTransaction( object: Transaction.Handler {
            override fun doTransaction(data: MutableData?): Transaction.Result {
                val userInfo = data?.value as? MutableMap<String,Any> ?: return Transaction.success(data)
                var postPoints = userInfo["postPoints"] as? Int ?: 0
                var points = userInfo["points"] as? Int ?: 0
                when (voteStatus) {
                    "up" -> {
                        postPoints -= 1
                        points -= 1
                    }
                    "down" -> {
                        postPoints += 2
                        points += 2
                    }
                    else -> {
                        postPoints += 1
                        points += 1
                    }
                }
                userInfo["postPoints"] = postPoints
                userInfo["points"] = points
                data.value = userInfo

                getFollowers(userID) { userFollowers ->
                    if (!userFollowers.isEmpty() && points != 0) {
                        val fanoutObject = mutableMapOf<String,Any>()
                        for (followerID in userFollowers) {
                            fanoutObject.put("/$followerID/$userID/points", points)
                        }
                        val userAddedRef = ref.child("userAdded")
                        userAddedRef.updateChildren(fanoutObject)
                    }
                }

                return Transaction.success(data)
            }
            override fun onComplete(p0: DatabaseError?, p1: Boolean, p2: DataSnapshot?) {}
        })

        val userPostHistoryRef = userRef.child("userPostHistory").child(userID).child(postID)
        userPostHistoryRef.runTransaction( object: Transaction.Handler {
            override fun doTransaction(data: MutableData?): Transaction.Result {
                val postInfo = data?.value as? MutableMap<String,Any> ?: return Transaction.success(data)
                var points = postInfo["points"] as? Int ?: 0
                when (voteStatus) {
                    "up" -> { points -= 1 }
                    "down" -> { points += 2 }
                    else -> { points += 1 }
                }
                postInfo["points"] = points
                data.value = postInfo
                return Transaction.success(data)
            }
            override fun onComplete(p0: DatabaseError?, p1: Boolean, p2: DataSnapshot?) {}
        })

    }

    fun downvote(postID: String, myID: String, userID: String, voteStatus: String) {
        val postVoteHistoryRef = ref.child("postVoteHistory").child(postID).child(myID)
        val postRef = ref.child("posts").child(postID)
        postRef.runTransaction( object: Transaction.Handler {
            override fun doTransaction(data: MutableData?): Transaction.Result {
                val post = data?.value as? MutableMap<String,Any> ?: return Transaction.success(data)
                var upvotes = post["upvotes"] as? Int ?: 0
                var downvotes = post["downvotes"] as? Int ?: 0
                when (voteStatus) {
                    "up" -> {
                        upvotes -= 1
                        downvotes += 1
                        postVoteHistoryRef.setValue(false)
                    }
                    "down" -> {
                        downvotes -= 1
                        postVoteHistoryRef.removeValue()
                    }
                    else -> {
                        downvotes += 1
                        postVoteHistoryRef.setValue(false)
                    }
                }

                post["upvotes"] = upvotes
                post["downvotes"] = downvotes
                post["points"] = upvotes - downvotes
                data.value = post
                return Transaction.success(data)
            }
            override fun onComplete(p0: DatabaseError?, p1: Boolean, p2: DataSnapshot?) {}
        })

        val userRef = ref.child("users").child(userID)
        userRef.runTransaction( object: Transaction.Handler {
            override fun doTransaction(data: MutableData?): Transaction.Result {
                val userInfo = data?.value as? MutableMap<String,Any> ?: return Transaction.success(data)
                var postPoints = userInfo["postPoints"] as? Int ?: 0
                var points = userInfo["points"] as? Int ?: 0
                when (voteStatus) {
                    "up" -> {
                        postPoints -= 2
                        points -= 2
                    }
                    "down" -> {
                        postPoints += 1
                        points += 1
                    }
                    else -> {
                        postPoints -= 1
                        points -= 1
                    }
                }
                userInfo["postPoints"] = postPoints
                userInfo["points"] = points
                data.value = userInfo

                getFollowers(userID) { userFollowers ->
                    if (!userFollowers.isEmpty() && points != 0) {
                        val fanoutObject = mutableMapOf<String,Any>()
                        for (followerID in userFollowers) {
                            fanoutObject.put("/$followerID/$userID/points", points)
                        }
                        val userAddedRef = ref.child("userAdded")
                        userAddedRef.updateChildren(fanoutObject)
                    }
                }

                return Transaction.success(data)
            }
            override fun onComplete(p0: DatabaseError?, p1: Boolean, p2: DataSnapshot?) {}
        })

        val userPostHistoryRef = userRef.child("userPostHistory").child(userID).child(postID)
        userPostHistoryRef.runTransaction( object: Transaction.Handler {
            override fun doTransaction(data: MutableData?): Transaction.Result {
                val postInfo = data?.value as? MutableMap<String,Any> ?: return Transaction.success(data)
                var points = postInfo["points"] as? Int ?: 0
                when (voteStatus) {
                    "up" -> { points -= 2 }
                    "down" -> { points += 1 }
                    else -> { points -= 1 }
                }
                postInfo["points"] = points
                data.value = postInfo
                return Transaction.success(data)
            }
            override fun onComplete(p0: DatabaseError?, p1: Boolean, p2: DataSnapshot?) {}
        })

    }

    // MARK: - DataTypes

    fun formatPost(postID: String, voteStatus: String, post: MutableMap<String,Any>): Post {
        val formattedPost = Post()

        formattedPost.postID = postID
        formattedPost.userID = post["userID"] as? String ?: "error"
        formattedPost.type = post["type"] as? String ?: "error"

        val profilePicURLString = post["profilePicURLString"] as? String ?: "error"
        if (profilePicURLString != "error") {
            formattedPost.profilePicURL = Uri.parse(profilePicURLString)
        }

        val postPicURLString = post["postPicURLString"] as? String ?: "error"
        if (postPicURLString != "error") {
            formattedPost.postPicURL = Uri.parse(postPicURLString)
        }

        val postVidURLString = post["postVidURLString"] as? String ?: "error"
        if (postVidURLString != "error") {
            formattedPost.postVidURL = Uri.parse(postVidURLString)
        }

        val postVidPreviewURLString = post["postVidPreviewURLString"] as? String ?: "error"
        if (postVidPreviewURLString != "error") {
            formattedPost.postVidPreviewURL = Uri.parse(postVidPreviewURLString)
        }

        formattedPost.handle = post["handle"] as? String ?: "error"
        formattedPost.content = post["content"] as? String ?: "error"

        val timestamp = post["timestamp"] as? String ?: "error"
        formattedPost.timestampUTC = timestamp

        val isEdited = post["isEdited"] as? Boolean ?: false
        val formattedTimestamp = formatTimestamp(timestamp)
        if (isEdited) {
            formattedPost.timestamp = "edited $formattedTimestamp"
        } else {
            formattedPost.timestamp = formattedTimestamp
        }
        formattedPost.originalReverseTimestamp = post["originalReverseTimestamp"] as? Double ?: 0.0

        val upvotes = post["upvotes"] as? Int ?: 0
        val downvotes = post["downvotes"] as? Int ?: 0
        formattedPost.points = upvotes - downvotes
        formattedPost.score = post["score"] as? Double ?: 0.0

        val replyCount = post["replyCount"] as? Int ?: 0
        val replyFormatted = setCount(replyCount)
        formattedPost.replyString = "$replyFormatted replies"

        formattedPost.voteStatus = voteStatus

        return formattedPost
    }

    // MARK: - OkHttp

    fun postNotification(message: String, category: String, userID: String) {
        val userRef = ref.child("users").child(userID)
        userRef.addListenerForSingleValueEvent( object: ValueEventListener {
            override fun onDataChange(snap: DataSnapshot?) {
                val userInfo = snap?.value as? MutableMap<String,Any> ?: mutableMapOf()
                val badge = userInfo["notificationBadge"] as? Int ?: 0
                val endpointToken = userInfo["deviceToken"] as? String ?: "error"

                val JSON = MediaType.parse("application/json; charset=utf-8")
                val params: HashMap<String,Any> = hashMapOf("body" to message, "category" to category, "badge" to badge,
                        "endpointToken" to endpointToken, "action" to "message")
                val parameter = JSONObject(params)

                val client = OkHttpClient()
                val body = RequestBody.create(JSON, parameter.toString())
                val request = Request.Builder().url("https://flocalApp.us-west-1.elasticbeanstalk.com").post(body).build()

                val response = client.newCall(request).execute()
                val json = response.body()?.string()
                Log.d("server", json)
            }
            override fun onCancelled(error: DatabaseError?) { Log.d("databaseError", error.toString()) }
        })
    }

}