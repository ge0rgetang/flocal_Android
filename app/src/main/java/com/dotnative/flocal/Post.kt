package com.dotnative.flocal

import android.net.Uri

class Post {
    var postID: String = "0"
    var userID: String = "0"
    var type: String = "0"
    var handle: String = "0"
    var points: Int = 0
    var score: Double = 0.0
    var voteStatus: String = "none"
    var content: String = "error"
    var timestamp: String = "error"
    var timestampUTC: String = "error"
    var replyString: String = "- replies"
    var originalReverseTimestamp: Double = 0.0
    var profilePicURL: Uri? = null
    var postPicURL: Uri? = null
    var postVidURL: Uri? = null
    var postVidPreviewURL: Uri? = null
}