package org.onevault.app.cloud

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import org.onevault.app.BuildConfig
import org.onevault.app.data.VaultDb
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class GoogleAuth(private val activity: Activity, private val db: VaultDb) {
    companion object { const val OAUTH_REQUEST = 7321 }
    private val service by lazy { AuthorizationService(activity) }
    private val config = AuthorizationServiceConfiguration(
        Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
        Uri.parse("https://oauth2.googleapis.com/token"))

    fun start(accountId: String) {
        require(BuildConfig.GOOGLE_CLIENT_ID.isNotBlank()) {
            "Google OAuth client ID missing. Configure local.properties before building."
        }
        activity.getPreferences(Activity.MODE_PRIVATE).edit().putString("pending_google_account",accountId).apply()
        val request = AuthorizationRequest.Builder(config,BuildConfig.GOOGLE_CLIENT_ID,
            ResponseTypeValues.CODE,Uri.parse(BuildConfig.OAUTH_REDIRECT_URI))
            // Read existing files, write only files created/opened with this app.
            .setScope("openid email profile https://www.googleapis.com/auth/drive.readonly https://www.googleapis.com/auth/drive.file")
            .setAdditionalParameters(mapOf("access_type" to "offline", "prompt" to "consent"))
            .build()
        activity.startActivityForResult(service.getAuthorizationRequestIntent(request),OAUTH_REQUEST)
    }
    fun receive(requestCode:Int,resultCode:Int,data:Intent?,onComplete:(String)->Unit) {
        if(requestCode!=OAUTH_REQUEST)return
        val id=activity.getPreferences(Activity.MODE_PRIVATE).getString("pending_google_account",null)
        activity.getPreferences(Activity.MODE_PRIVATE).edit().remove("pending_google_account").apply()
        if(id==null) {onComplete("No pending account");return}
        val response=data?.let{AuthorizationResponse.fromIntent(it)}
        val exception=data?.let{AuthorizationException.fromIntent(it)}
        if(response==null) {onComplete("Login cancelled or failed: ${exception?.errorDescription?:resultCode}");return}
        val state=AuthState(response,exception)
        service.performTokenRequest(response.createTokenExchangeRequest()) { token, error ->
            // AppAuth dispatches callbacks on the main thread. The userinfo HTTPS
            // verification MUST run off-main or Android throws NetworkOnMainThreadException.
            thread(name = "OneVault-google-identity") {
                val message = if(token!=null) {
                    state.update(token,error)
                    try {
                        // Crucial for multi-account use: never bind the wrong Google identity.
                        val c=(URL("https://openidconnect.googleapis.com/v1/userinfo").openConnection() as HttpURLConnection).apply {
                            setRequestProperty("Authorization","Bearer ${token.accessToken}")
                            connectTimeout=20_000;readTimeout=20_000
                        }
                        val email=try {
                            require(c.responseCode==200){"Could not verify signed-in Google identity"}
                            JSONObject(c.inputStream.bufferedReader().use{it.readText()}).getString("email")
                        } finally {c.disconnect()}
                        require(email.equals(db.account(id)?.email,ignoreCase=true)) {
                            "Wrong Google account: signed in as $email instead of ${db.account(id)?.email}"
                        }
                        db.auth(id,state.jsonSerializeString())
                        "Connected and verified: $email"
                    } catch(e:Exception) { "Connection not saved: ${e.message}" }
                } else "OAuth token exchange failed: ${error?.errorDescription?:"unknown"}"
                Handler(Looper.getMainLooper()).post { onComplete(message) }
            }
        }
    }
    fun close() { service.dispose() }
}

/** Worker-safe token refresh. OAuth state is encrypted with Android Keystore. */
class GoogleTokens(private val context: android.content.Context, private val db:VaultDb) {
    companion object {
        // Serialize refresh+persist per identity: parallel workers must not overwrite
        // each other's rotated refresh tokens with a stale AuthState snapshot.
        private val accountLocks = ConcurrentHashMap<String, Any>()
    }
    fun validToken(accountId:String):String = synchronized(accountLocks.getOrPut(accountId) { Any() }) {
        val raw=db.authState(accountId) ?: error("Google account not connected")
        val state=AuthState.jsonDeserialize(raw)
        val service=AuthorizationService(context)
        val latch=CountDownLatch(1)
        var token:String?=null
        var failure:String?=null
        state.performActionWithFreshTokens(service) { accessToken,_,error ->
            if(error!=null) failure=error.errorDescription?:"Authorization expired"
            else token=accessToken
            latch.countDown()
        }
        val completed=latch.await(40,TimeUnit.SECONDS)
        if(completed) db.auth(accountId,state.jsonSerializeString())
        service.dispose()
        if(!completed) error("OAuth refresh timeout")
        token ?: error(failure?:"No access token")
    }
}
