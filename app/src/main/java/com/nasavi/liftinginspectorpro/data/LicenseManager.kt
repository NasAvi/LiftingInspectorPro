package com.nasavi.liftinginspectorpro.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

sealed class LicenseResult {
    object NotLoggedIn : LicenseResult()
    object Active : LicenseResult()
    object Expired : LicenseResult()
    object RequiresInternet : LicenseResult()
}

object LicenseManager {
    private const val PREF_NAME = "license_cache"
    private const val KEY_STATUS = "status"
    private const val KEY_EXPIRY = "expiry_ms"
    private const val KEY_LAST_SYNC = "last_sync_ms"
    private const val GRACE_PERIOD_MS = 30L * 24 * 60 * 60 * 1000
    private const val TRIAL_DAYS = 120L // ~4 חודשים

    private val auth: FirebaseAuth get() = Firebase.auth
    val currentUser get() = auth.currentUser

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> = try {
        auth.sendPasswordResetEmail(email).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = try {
        auth.signInWithEmailAndPassword(email, password).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun signOut() = auth.signOut()

    suspend fun checkLicense(context: Context): LicenseResult {
        val user = auth.currentUser ?: return LicenseResult.NotLoggedIn

        return try {
            val doc = Firebase.firestore
                .collection("licenses")
                .document(user.uid)
                .get()
                .await()

            if (!doc.exists()) return LicenseResult.Expired

            val isActive = doc.getBoolean("isActive") ?: true
            if (!isActive) {
                saveCache(context, "expired", 0L)
                return LicenseResult.Expired
            }

            val now = System.currentTimeMillis()
            val trialStartMs = doc.getTimestamp("trialStartDate")?.toDate()?.time
            val paidUntilMs = doc.getTimestamp("paidUntil")?.toDate()?.time

            val (status, expiryMs) = when {
                paidUntilMs != null && paidUntilMs > now ->
                    "active" to paidUntilMs
                trialStartMs != null -> {
                    val trialEnd = trialStartMs + TimeUnit.DAYS.toMillis(TRIAL_DAYS)
                    if (trialEnd > now) "active" to trialEnd else "expired" to trialEnd
                }
                else -> "expired" to 0L
            }

            saveCache(context, status, expiryMs)
            if (status == "active") LicenseResult.Active else LicenseResult.Expired

        } catch (_: Exception) {
            checkCachedLicense(context)
        }
    }

    private fun checkCachedLicense(context: Context): LicenseResult {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lastSync = prefs.getLong(KEY_LAST_SYNC, 0L)
        val now = System.currentTimeMillis()

        if (lastSync == 0L || now - lastSync > GRACE_PERIOD_MS) {
            return LicenseResult.RequiresInternet
        }

        val expiry = prefs.getLong(KEY_EXPIRY, 0L)
        return when {
            prefs.getString(KEY_STATUS, "expired") == "active" && (expiry == 0L || expiry > now) ->
                LicenseResult.Active
            else -> LicenseResult.Expired
        }
    }

    fun getExpiryMs(context: Context): Long =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getLong(KEY_EXPIRY, 0L)

    fun isConsentGiven(context: Context): Boolean =
        context.getSharedPreferences("user_consent", Context.MODE_PRIVATE)
            .getBoolean("consent_done", false)

    suspend fun saveConsent(context: Context) {
        val user = auth.currentUser ?: return
        context.getSharedPreferences("user_consent", Context.MODE_PRIVATE).edit()
            .putBoolean("consent_done", true).apply()
        try {
            Firebase.firestore.collection("licenses").document(user.uid)
                .update("termsAcceptedAt", com.google.firebase.Timestamp.now())
                .await()
        } catch (_: Exception) { }
    }

    private fun saveCache(context: Context, status: String, expiryMs: Long) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_STATUS, status)
            .putLong(KEY_EXPIRY, expiryMs)
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .apply()
    }
}

