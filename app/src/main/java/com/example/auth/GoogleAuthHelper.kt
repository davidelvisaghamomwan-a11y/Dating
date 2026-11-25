package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

object GoogleAuthHelper {
    private const val TAG = "KindredAuth"
    private val signOutScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isSigningOut = AtomicBoolean(false)
    private val _isSigningOutFlow = MutableStateFlow(false)
    val isSigningOutFlow: StateFlow<Boolean> = _isSigningOutFlow.asStateFlow()

    @Volatile
    private var suppressAutoSignInAfterLogout: Boolean = false

    fun isAutoSignInSuppressed(): Boolean = suppressAutoSignInAfterLogout

    fun resetAutoSignInSuppression() {
        suppressAutoSignInAfterLogout = false
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    private fun resolveServerClientId(context: Context): String? {
        return try {
            context.getString(R.string.default_web_client_id).takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun attemptAutoSignIn(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onUnauthenticated: () -> Unit,
        scope: CoroutineScope
    ) {
        if (Firebase.auth.currentUser != null) {
            onAuthSuccess()
            return
        }
        val clientId = resolveServerClientId(context) ?: run {
            onUnauthenticated()
            return
        }
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                val activity = context.findActivity()
                val result = if (activity != null) {
                    credentialManager.getCredential(activity, request)
                } else {
                    credentialManager.getCredential(context, request)
                }
                val cred = result.credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val idToken = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    if (idToken.isNotBlank()) {
                        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                        Firebase.auth.signInWithCredential(firebaseCredential).await()
                        onAuthSuccess()
                    } else {
                        onUnauthenticated()
                    }
                } else {
                    onUnauthenticated()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onUnauthenticated()
            }
        }
    }

    fun onGoogleSignInClicked(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit,
        onAuthCancelled: () -> Unit,
        scope: CoroutineScope
    ) {
        resetAutoSignInSuppression()
        val clientId = resolveServerClientId(context) ?: run {
            onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
            return
        }
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch {
            try {
                val activity = context.findActivity()
                val result = if (activity != null) {
                    credentialManager.getCredential(activity, request)
                } else {
                    credentialManager.getCredential(context, request)
                }
                val cred = result.credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val idToken = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    if (idToken.isBlank()) {
                        onAuthError("Google Sign-In returned an empty ID token")
                        return@launch
                    }
                    val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                    Firebase.auth.signInWithCredential(firebaseCredential).await()
                    onAuthSuccess()
                } else {
                    onAuthError("Unexpected credential type returned by Google Sign-In")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                onAuthCancelled()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed", e)
                onAuthError(e.localizedMessage ?: "Sign in failed. Please try again.")
            }
        }
    }

    fun signInWithEmail(
        email: String,
        password: String,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit,
        scope: CoroutineScope
    ) {
        resetAutoSignInSuppression()
        scope.launch {
            try {
                Firebase.auth.signInWithEmailAndPassword(email.trim(), password).await()
                onAuthSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Email sign-in failed", e)
                onAuthError(e.localizedMessage ?: "Unable to log in with email and password.")
            }
        }
    }

    fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit,
        scope: CoroutineScope
    ) {
        resetAutoSignInSuppression()
        scope.launch {
            try {
                val result = Firebase.auth.createUserWithEmailAndPassword(email.trim(), password).await()
                val user = result.user
                if (user != null && displayName.isNotBlank()) {
                    runCatching {
                        user.updateProfile(
                            userProfileChangeRequest {
                                this.displayName = displayName.trim()
                            }
                        ).await()
                    }
                }
                onAuthSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Email sign-up failed", e)
                onAuthError(e.localizedMessage ?: "Unable to create account with email and password.")
            }
        }
    }

    fun sendPasswordReset(
        email: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
        scope: CoroutineScope
    ) {
        val cleanEmail = email.trim()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            onError("Enter your account email above to receive a password reset link.")
            return
        }
        scope.launch {
            try {
                Firebase.auth.sendPasswordResetEmail(cleanEmail).await()
                onSuccess("Password reset email sent to $cleanEmail.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Password reset failed", e)
                onError(e.localizedMessage ?: "Unable to send password reset email.")
            }
        }
    }

    fun signOut(
        credentialManager: CredentialManager,
        @Suppress("UNUSED_PARAMETER") scope: CoroutineScope,
        onBeforeSignOut: suspend () -> Unit = {},
        onSignOutComplete: () -> Unit = {}
    ) {
        if (!isSigningOut.compareAndSet(false, true)) return
        _isSigningOutFlow.value = true
        suppressAutoSignInAfterLogout = true
        signOutScope.launch {
            withContext(NonCancellable) {
                try {
                    try {
                        withTimeoutOrNull(350L) {
                            onBeforeSignOut()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Pre-sign-out hook failed: ${e.message}")
                    }
                    withContext(Dispatchers.Main.immediate) {
                        Firebase.auth.signOut()
                        onSignOutComplete()
                    }
                    try {
                        withTimeoutOrNull(800L) {
                            credentialManager.clearCredentialState(ClearCredentialStateRequest())
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to clear credential state: ${e.message}")
                    }
                } finally {
                    _isSigningOutFlow.value = false
                    isSigningOut.set(false)
                }
            }
        }
    }
}
