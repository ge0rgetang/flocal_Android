package com.dotnative.flocal

import android.net.Uri

class User {
    var userID: String = "0"
    var handle: String = "error"
    var profilePicURL: Uri? = null
    var points: Int = 0
    var followersCount: Int = 0
    var description: String = "error"
    var originalReverseTimestamp: Double = 0.0
    var didIAdd: Boolean = false
}