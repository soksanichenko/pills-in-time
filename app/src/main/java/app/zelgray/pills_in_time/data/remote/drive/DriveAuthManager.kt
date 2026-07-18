package app.zelgray.pills_in_time.data.remote.drive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * appDataFolder access requires this specific scope (spec 4.8: "Authorization
 * via Google account"). Only Drive's own app-private folder is requested —
 * never full Drive access.
 */
private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

sealed interface DriveAuthResult {
    data class Authorized(val accessToken: String) : DriveAuthResult
    data class NeedsConsent(val pendingIntent: PendingIntent) : DriveAuthResult
    data class Error(val message: String) : DriveAuthResult
}

/**
 * Wraps the Play Services Authorization API (Identity.getAuthorizationClient)
 * to obtain a short-lived Drive appdata access token.
 *
 * REQUIRED ONE-TIME SETUP (in Google Cloud Console, not in this codebase —
 * this on-device access-token flow needs no client-ID string embedded in the
 * app; Play Services matches the request to your OAuth client automatically
 * by the app's package name + signing certificate):
 *   1. Create a Google Cloud project (or reuse one) and enable the
 *      "Google Drive API" under APIs & Services > Library.
 *   2. Configure the OAuth consent screen (APIs & Services > OAuth consent
 *      screen). While testing, add your own Google account as a test user —
 *      the drive.appdata scope is "sensitive" and would otherwise require
 *      Google's verification review before working for other accounts.
 *   3. Create an OAuth client ID of type "Android" (APIs & Services >
 *      Credentials > Create Credentials > OAuth client ID), using this app's
 *      applicationId (app.zelgray.pills_in_time) and the SHA-1 fingerprint of
 *      whichever signing key you build with (debug: run
 *      `./gradlew signingReport` to print it; release: your upload key's
 *      SHA-1). Add one OAuth client per signing key you use (debug/release).
 * Until that Android OAuth client exists for the exact package+SHA-1 you're
 * building with, requestAuthorization() below will surface a DriveAuthResult.Error
 * (Play Services rejects the request as coming from an unrecognized app).
 */
class DriveAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val authorizationClient by lazy { Identity.getAuthorizationClient(context) }

    suspend fun requestAuthorization(): DriveAuthResult = try {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()
        val result = authorizationClient.authorize(request).await()
        when {
            result.hasResolution() -> {
                val pendingIntent = result.pendingIntent
                if (pendingIntent != null) {
                    DriveAuthResult.NeedsConsent(pendingIntent)
                } else {
                    DriveAuthResult.Error("Authorization requires consent but no resolution was provided")
                }
            }
            result.accessToken != null -> DriveAuthResult.Authorized(result.accessToken!!)
            else -> DriveAuthResult.Error("No access token returned")
        }
    } catch (e: Exception) {
        DriveAuthResult.Error(e.message ?: "Authorization failed")
    }

    /** Call from the consent-intent ActivityResult launcher's callback. */
    fun resultFromIntent(data: Intent?): DriveAuthResult = try {
        val result = authorizationClient.getAuthorizationResultFromIntent(data)
        val token = result.accessToken
        if (token != null) DriveAuthResult.Authorized(token) else DriveAuthResult.Error("No access token returned")
    } catch (e: Exception) {
        DriveAuthResult.Error(e.message ?: "Authorization failed")
    }
}
