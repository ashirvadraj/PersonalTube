package com.personal.tube.util

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.personal.tube.PersonalTubeApp

data class UserProfile(
    val id: String,
    val displayName: String,
    val email: String,
    val photoUrl: String? = null
)

object UserManager {

    private const val PREFS_NAME = "personal_tube_user_prefs"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_EMAIL = "email"
    private const val KEY_PHOTO_URL = "photo_url"

    private val prefs: SharedPreferences by lazy {
        PersonalTubeApp.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _currentUser = MutableLiveData<UserProfile?>()
    val currentUser: LiveData<UserProfile?> get() = _currentUser

    init {
        loadUser()
    }

    private fun loadUser() {
        val isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        if (isLoggedIn) {
            val id = prefs.getString(KEY_USER_ID, "") ?: ""
            val name = prefs.getString(KEY_DISPLAY_NAME, "User") ?: "User"
            val email = prefs.getString(KEY_EMAIL, "") ?: ""
            val photo = prefs.getString(KEY_PHOTO_URL, null)
            _currentUser.postValue(UserProfile(id, name, email, photo))
        } else {
            _currentUser.postValue(null)
        }
    }

    fun getGoogleSignInClient(activity: Activity): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestId()
            .build()
        return GoogleSignIn.getClient(activity, gso)
    }

    fun handleSignInSuccess(account: GoogleSignInAccount) {
        val user = UserProfile(
            id = account.id ?: "",
            displayName = account.displayName ?: "Gmail User",
            email = account.email ?: "",
            photoUrl = account.photoUrl?.toString()
        )

        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_USER_ID, user.id)
            .putString(KEY_DISPLAY_NAME, user.displayName)
            .putString(KEY_EMAIL, user.email)
            .putString(KEY_PHOTO_URL, user.photoUrl)
            .apply()

        _currentUser.postValue(user)
    }

    fun signOut(activity: Activity, onComplete: () -> Unit = {}) {
        val client = getGoogleSignInClient(activity)
        client.signOut().addOnCompleteListener {
            prefs.edit().clear().apply()
            _currentUser.postValue(null)
            onComplete()
        }
    }

    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }
}
