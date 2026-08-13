package com.example.swtichandsavepda.data.remote.session

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.swtichandsavepda.data.remote.PdaJson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The persisted session, stored as Keystore-encrypted JSON in private prefs.
 * Only the bearer [token] is truly sensitive; the operator/tenant fields ride
 * along so an already-signed-in user is restored without a round trip.
 */
@Serializable
data class StoredSession(
    val token: String,
    val userId: Long,
    val userName: String,
    val userEmail: String,
    val shopId: Long? = null,
    val tenantId: String? = null,
    val tenantName: String? = null,
)

/**
 * Persists the session across launches. Prefers Keystore-encrypted storage, but
 * if the Keystore is unavailable (some emulators/devices), it falls back to
 * app-private storage rather than silently losing the login — a session that
 * survives until logout is the requirement, and `MODE_PRIVATE` prefs are already
 * sandboxed from other apps. `commit()` is used (not `apply()`) so the write is
 * durable before the process can be killed.
 */
@Singleton
class SecureSessionStore @Inject constructor(
    @ApplicationContext context: Context,
    private val crypto: KeystoreCrypto,
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(session: StoredSession) {
        val plain = PdaJson.instance.encodeToString(session)
        val encrypted = crypto.encrypt(plain)
        val value = if (encrypted != null) {
            ENCRYPTED_PREFIX + encrypted
        } else {
            Log.w(TAG, "Keystore unavailable — persisting session in app-private storage")
            PLAIN_PREFIX + Base64.encodeToString(plain.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
        prefs.edit().putString(KEY_SESSION, value).commit()
    }

    fun load(): StoredSession? {
        val raw = prefs.getString(KEY_SESSION, null) ?: return null
        val plain = when {
            raw.startsWith(ENCRYPTED_PREFIX) -> crypto.decrypt(raw.removePrefix(ENCRYPTED_PREFIX))
            raw.startsWith(PLAIN_PREFIX) -> runCatching {
                String(Base64.decode(raw.removePrefix(PLAIN_PREFIX), Base64.NO_WRAP), Charsets.UTF_8)
            }.getOrNull()
            // Legacy value with no prefix — try to decrypt it.
            else -> crypto.decrypt(raw)
        }
        if (plain == null) {
            // Leave the stored value in place; the user simply signs in again and
            // it is overwritten. We do not wipe on a transient decrypt failure.
            Log.w(TAG, "Could not read stored session")
            return null
        }
        return runCatching { PdaJson.instance.decodeFromString<StoredSession>(plain) }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove(KEY_SESSION).commit()
    }

    private companion object {
        const val PREFS_NAME = "pda_secure_session"
        const val KEY_SESSION = "session"
        const val ENCRYPTED_PREFIX = "enc:"
        const val PLAIN_PREFIX = "plain:"
        const val TAG = "PdaSession"
    }
}
