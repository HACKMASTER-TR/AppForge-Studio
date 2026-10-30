package com.appforge.studio.security

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** V9-only, NOT wired into the current GoogleAdminIdentityClient or app navigation.
 * Client ID comes from trusted app configuration; nonce comes ONLY from V9 session source.
 */
internal class V9AdminCredentialProvider(
    private val activity: Activity,
    private val webClientId: String
) : V9GoogleTokenSource {
    private val webId = Regex("^[0-9]+-[a-z0-9-]+\\.apps\\.googleusercontent\\.com$")
    init { require(webId.matches(webClientId)) { "V9 invalid configured Web Client ID" } }

    override suspend fun requestIdToken(serverNonce: String): String {
        require(Regex("^[A-Za-z0-9_-]{22,128}$").matches(serverNonce)) {
            "V9 invalid server nonce"
        }
        val option = GetSignInWithGoogleOption.Builder(webClientId)
            .setNonce(serverNonce)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activity)
            .getCredential(context = activity, request = request).credential
        check(credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "V9 unexpected Google credential"
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
}
