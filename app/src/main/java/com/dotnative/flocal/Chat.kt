package com.dotnative.flocal

import android.net.Uri

class Chat {
    var chatID: String = "0"
    var messageID: String = "0"
    var userID: String = "0"
    var profilePicURL: Uri? = null
    var handle: String = "error"
    var type: String = "error"
    var timestamp: String = "error"
    var originalReverseTimestamp: Double = 0.0
    var originalTimestamp: Double = 0.0
    var message: String = "error"
    var chatPicURL: Uri? = null
    var chatVidURL: Uri? = null
    var chatVidPreviewURL: Uri? = null
}