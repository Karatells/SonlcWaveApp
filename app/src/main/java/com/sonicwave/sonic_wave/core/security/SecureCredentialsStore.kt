package com.sonicwave.sonic_wave.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class SecureCredentialsStore private constructor(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "sonicwave_secure_prefs",
            masterKeyAlias,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Throwable) {
        context.applicationContext.getSharedPreferences("sonicwave_fallback_prefs", Context.MODE_PRIVATE)
    }

    private val memoryCache = ConcurrentHashMap<String, String>()
    private val certFingerprints = ConcurrentHashMap<String, String>()
    private val rejectedCertFingerprints = ConcurrentHashMap<String, String>()

    private fun secretKey(serverId: String) = "sonicwave_secret_$serverId"
    private fun certKey(serverId: String) = "sonicwave_cert_sha256_$serverId"

    suspend fun saveSecret(serverId: String, secret: String): Boolean = withContext(Dispatchers.IO) {
        memoryCache[serverId] = secret
        try {
            prefs.edit().putString(secretKey(serverId), secret).apply()
            true
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun readSecret(serverId: String): String? = withContext(Dispatchers.IO) {
        memoryCache[serverId]?.let { return@withContext it }
        try {
            val stored = prefs.getString(secretKey(serverId), null)
            if (stored != null) {
                memoryCache[serverId] = stored
            }
            stored
        } catch (_: Throwable) {
            memoryCache[serverId]
        }
    }

    fun getPinnedCertFingerprintSync(serverId: String): String? {
        certFingerprints[serverId]?.let { return it }
        return try {
            val stored = prefs.getString(certKey(serverId), null)
            if (!stored.isNullOrBlank()) {
                certFingerprints[serverId] = stored
            }
            stored
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun loadPinnedCertFingerprint(serverId: String): String? = withContext(Dispatchers.IO) {
        getPinnedCertFingerprintSync(serverId)
    }

    fun pinCertFingerprintSync(serverId: String, sha256Hex: String) {
        certFingerprints[serverId] = sha256Hex
        rejectedCertFingerprints.remove(serverId)
        try {
            prefs.edit().putString(certKey(serverId), sha256Hex).apply()
        } catch (_: Throwable) {
        }
    }

    suspend fun pinCertFingerprint(serverId: String, sha256Hex: String) = withContext(Dispatchers.IO) {
        pinCertFingerprintSync(serverId, sha256Hex)
    }

    fun recordRejectedCertFingerprint(serverId: String, sha256Hex: String) {
        rejectedCertFingerprints[serverId] = sha256Hex
    }

    fun getLastRejectedCertFingerprint(serverId: String): String? = rejectedCertFingerprints[serverId]

    fun clearRejectedCertFingerprint(serverId: String) {
        rejectedCertFingerprints.remove(serverId)
    }

    suspend fun trustRejectedCertFingerprint(serverId: String): Boolean = withContext(Dispatchers.IO) {
        val rejected = rejectedCertFingerprints.remove(serverId) ?: return@withContext false
        pinCertFingerprintSync(serverId, rejected)
        true
    }

    suspend fun clearPinnedCertFingerprint(serverId: String) = withContext(Dispatchers.IO) {
        certFingerprints.remove(serverId)
        rejectedCertFingerprints.remove(serverId)
        try {
            prefs.edit().remove(certKey(serverId)).apply()
        } catch (_: Throwable) {
        }
    }

    suspend fun deleteSecret(serverId: String) = withContext(Dispatchers.IO) {
        memoryCache.remove(serverId)
        certFingerprints.remove(serverId)
        rejectedCertFingerprints.remove(serverId)
        try {
            prefs.edit()
                .remove(secretKey(serverId))
                .remove(certKey(serverId))
                .apply()
        } catch (_: Throwable) {
        }
    }

    fun clearMemoryCache() {
        memoryCache.clear()
    }

    companion object {
        const val SECURE_MARKER = "__SECURE__"

        @Volatile
        private var instance: SecureCredentialsStore? = null

        fun getInstance(context: Context): SecureCredentialsStore =
            instance ?: synchronized(this) {
                instance ?: SecureCredentialsStore(context).also { instance = it }
            }
    }
}
