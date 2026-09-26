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
import java.util.Locale

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

    private var prefs: SharedPreferences? = null

    private val _currentUser = MutableLiveData<UserProfile?>()
    val currentUser: LiveData<UserProfile?> get() = _currentUser

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadUser()
        }
    }

    private fun loadUser() {
        val p = prefs ?: return
        val isLoggedIn = p.getBoolean(KEY_IS_LOGGED_IN, false)
        if (isLoggedIn) {
            val id = p.getString(KEY_USER_ID, "") ?: ""
            val name = p.getString(KEY_DISPLAY_NAME, "Gmail User") ?: "Gmail User"
            val email = p.getString(KEY_EMAIL, "") ?: ""
            val photo = p.getString(KEY_PHOTO_URL, null)
            _currentUser.postValue(UserProfile(id, name, email, photo))
        } else {
            _currentUser.postValue(null)
        }
    }

    fun getGoogleSignInClient(activity: Activity): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .build()
        return GoogleSignIn.getClient(activity, gso)
    }

    fun handleSignInSuccess(account: GoogleSignInAccount) {
        val email = account.email ?: "user@gmail.com"
        val displayName = account.displayName ?: email.substringBefore("@")
        val photoUrl = account.photoUrl?.toString()

        loginWithEmail(email, displayName, photoUrl)
    }

    fun loginWithEmail(email: String, displayName: String? = null, photoUrl: String? = null) {
        val formattedName = displayName?.takeIf { it.isNotBlank() }
            ?: email.substringBefore("@").replace(Regex("[._-]"), " ")
                .split(" ")
                .filter { it.isNotBlank() }
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase(Locale.getDefault()) } }
                .ifBlank { "Gmail User" }

        val user = UserProfile(
            id = email,
            displayName = formattedName,
            email = email,
            photoUrl = photoUrl
        )

        prefs?.edit()
            ?.putBoolean(KEY_IS_LOGGED_IN, true)
            ?.putString(KEY_USER_ID, user.id)
            ?.putString(KEY_DISPLAY_NAME, user.displayName)
            ?.putString(KEY_EMAIL, user.email)
            ?.putString(KEY_PHOTO_URL, user.photoUrl)
            ?.apply()

        _currentUser.postValue(user)
    }

    fun signOut(context: Context, onComplete: () -> Unit = {}) {
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
            GoogleSignIn.getClient(context, gso).signOut()
        } catch (e: Exception) {
            // Ignore sign-out exceptions
        }
        prefs?.edit()?.clear()?.apply()
        _currentUser.postValue(null)
        onComplete()
    }

    fun isLoggedIn(): Boolean {
        return prefs?.getBoolean(KEY_IS_LOGGED_IN, false) ?: false
    }
}
