package com.dotnative.flocal

import android.preference.PreferenceManager
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.iid.FirebaseInstanceIdService

class FirebaseInstanceIDService: FirebaseInstanceIdService() {

    // MARK: - Vars

    val misc = Misc()

    // MARK: - Implementation

    override fun onTokenRefresh() {
        super.onTokenRefresh()

         val myID = misc.setMyID(this)
         val token = FirebaseInstanceId.getInstance().token
         if (token != null) {
             val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
             val editor = sharedPreferences.edit()
             editor.putString("deviceToken.flocal", token)
             editor.apply()

             if (myID != "0") {
                 val ref: DatabaseReference = FirebaseDatabase.getInstance().reference
                 val userRef = ref.child("users").child(myID)
                 userRef.child("deviceToken").setValue(token)
                 userRef.child("OS").setValue("Android")
             }
         }
    }

}