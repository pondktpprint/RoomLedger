package com.pondktpprint.roomledger.data

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

fun firebaseAuthOrNull(context: Context): FirebaseAuth? {
    return try {
        val app = FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context) ?: return null
        FirebaseAuth.getInstance(app)
    } catch (_: Exception) { null }
}

fun googleWebClientId(context: Context): String? = runCatching {
    val resourceId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    if (resourceId == 0) null else context.getString(resourceId).takeIf(String::isNotBlank)
}.getOrNull()

fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

suspend fun signInWithGoogle(context: Context, auth: FirebaseAuth, webClientId: String): FirebaseUser {
    val activity = context.findActivity() ?: error("ไม่พบหน้าต่างสำหรับเข้าสู่ระบบ")
    val option = GetGoogleIdOption.Builder()
        .setServerClientId(webClientId)
        .setFilterByAuthorizedAccounts(false)
        .setAutoSelectEnabled(false)
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val credential = CredentialManager.create(context).getCredential(activity, request).credential
    require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        "ไม่ได้รับข้อมูลเข้าสู่ระบบ Google ที่รองรับ"
    }
    val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
    val result = auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
    return result.user ?: error("เข้าสู่ระบบสำเร็จแต่ไม่พบข้อมูลบัญชี")
}
