package com.dotnative.flocal

import android.net.Uri

class Reply {
    var replyID: String = "0"
    var userID: String = "0"
    var profilePicURL: Uri? = null
    var handle: String = "0"
    var points: Int = 0
    var score: Double = 0.0
    var voteStatus: String = "none"
    var content: String = "error"
    var timestamp: String = "error"
    var originalReverseTimestamp: Double = 0.0
}