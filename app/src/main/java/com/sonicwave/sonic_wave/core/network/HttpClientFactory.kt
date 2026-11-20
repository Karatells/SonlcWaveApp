package com.sonicwave.sonic_wave.core.network

import android.annotation.SuppressLint
import com.sonicwave.sonic_wave.core.security.SecureCredentialsStore
import com.sonicwave.sonic_wave.domain.models.ServerProfile
import com.sonicwave.sonic_wave.domain.models.ServerProtocol
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object SubsonicAuthHelper {
    private const val SALT_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private val secureRandom = SecureRandom()

    fun generateSalt(deterministicSeed: String? = null): String {
        if (!deterministicSeed.isNullOrEmpty()) {
            val digest = md5("sonicwave_salt_$deterministicSeed")
            return digest.take(12)
        }
        return buildString(12) {
            repeat(12) {
                append(SALT_CHARS[secureRandom.nextInt(SALT_CHARS.length)])
            }
        }
    }

    fun buildAuthParams(
        profile: ServerProfile,
        deterministicSeed: String? = null
    ): Map<String, String> {
        val salt = generateSalt(deterministicSeed)
        val token = md5("${profile.secretOrPassword}$salt")
        return mapOf(
            "u" to profile.username,
            "t" to token,
            "s" to salt,
            "v" to "1.16.1",
            "c" to "SonicWaveAndroid",
            "f" to "json"
        )
    }

    fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}

class AuthAndFailoverInterceptor(
    private val profileProvider: () -> ServerProfile
) : Interceptor {

    @Volatile
    private var lanDisabledUntilMs: Long = 0L

    fun resetLanFailover() {
        lanDisabledUntilMs = 0L
    }

    fun activeBaseUrl(profile: ServerProfile): String {
        val lan = profile.lanUrl?.trim()?.takeIf { it.isNotEmpty() }
        val now = System.currentTimeMillis()
        return if (lan != null && now >= lanDisabledUntilMs) {
            lan.trimEnd('/')
        } else {
            profile.primaryUrl.trim().trimEnd('/')
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val profile = profileProvider()
        val original = chain.request()
        val usingLan = !profile.lanUrl.isNullOrBlank() && System.currentTimeMillis() >= lanDisabledUntilMs

        val authedRequest = applyProtocolAuth(original, profile, activeBaseUrl(profile))

        return try {
            chain.proceed(authedRequest)
        } catch (e: IOException) {
            if (usingLan) {
                lanDisabledUntilMs = System.currentTimeMillis() + 3 * 60 * 1000L
                val fallbackRequest = applyProtocolAuth(
                    original,
                    profile,
                    profile.primaryUrl.trim().trimEnd('/')
                )
                chain.proceed(fallbackRequest)
            } else {
                throw e
            }
        }
    }

    private fun applyProtocolAuth(
        request: okhttp3.Request,
        profile: ServerProfile,
        baseUrl: String
    ): okhttp3.Request {
        val baseHttp = baseUrl.toHttpUrlOrNull()
        val urlBuilder = request.url.newBuilder()
        if (baseHttp != null) {
            urlBuilder.scheme(baseHttp.scheme)
            urlBuilder.host(baseHttp.host)
            urlBuilder.port(baseHttp.port)
        }

        val reqBuilder = request.newBuilder()
        when (profile.protocol) {
            ServerProtocol.OPEN_SUBSONIC, ServerProtocol.NAVIDROME -> {
                val params = SubsonicAuthHelper.buildAuthParams(profile)
                params.forEach { (k, v) ->
                    if (request.url.queryParameter(k) == null) {
                        urlBuilder.addQueryParameter(k, v)
                    }
                }
            }
            ServerProtocol.JELLYFIN -> {
                if (profile.secretOrPassword.isNotBlank()) {
                    reqBuilder.header("X-Emby-Token", profile.secretOrPassword)
                }
                reqBuilder.header(
                    "X-Emby-Authorization",
                    "MediaBrowser Client=\"SonicWave\", Device=\"Android\", DeviceId=\"sonicwave-android\", Version=\"1.0.0\", Token=\"${profile.secretOrPassword}\""
                )
            }
            ServerProtocol.CUSTOM_REST -> {
                if (profile.secretOrPassword.isNotBlank()) {
                    reqBuilder.header("Authorization", "Bearer ${profile.secretOrPassword}")
                }
                if (profile.username.isNotBlank()) {
                    reqBuilder.header("X-SonicWave-User", profile.username)
                }
            }
        }

        return reqBuilder.url(urlBuilder.build()).build()
    }
}

class ExponentialRetryInterceptor(
    private val maxRetries: Int = 3
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var attempt = 0
        var lastException: IOException? = null
        while (attempt <= maxRetries) {
            try {
                val response = chain.proceed(request)
                if (response.code < 500 || attempt == maxRetries) {
                    return response
                }
                response.close()
            } catch (e: IOException) {
                lastException = e
                if (attempt == maxRetries) throw e
            }
            val delayMs = 400L * (1 shl attempt)
            try {
                Thread.sleep(delayMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw lastException ?: IOException("Interrupted during retry")
            }
            attempt++
        }
        throw lastException ?: IOException("Max retries exceeded")
    }
}

object HttpClientFactory {
    fun createApiClient(
        profileProvider: () -> ServerProfile,
        credentialsStore: SecureCredentialsStore,
        authInterceptor: AuthAndFailoverInterceptor
    ): OkHttpClient {
        return buildBaseBuilder(profileProvider, credentialsStore)
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(ExponentialRetryInterceptor(maxRetries = 3))
            .build()
    }

    fun createStreamingClient(
        profileProvider: () -> ServerProfile,
        credentialsStore: SecureCredentialsStore
    ): OkHttpClient {
        return buildBaseBuilder(profileProvider, credentialsStore)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    private fun buildBaseBuilder(
        profileProvider: () -> ServerProfile,
        credentialsStore: SecureCredentialsStore
    ): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                val profile = profileProvider()
                val cert = chain?.firstOrNull() ?: throw java.security.cert.CertificateException("Empty cert chain")
                if (!profile.allowSelfSignedSsl) {
                    val defaultFactory = javax.net.ssl.TrustManagerFactory.getInstance(
                        javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm()
                    )
                    defaultFactory.init(null as java.security.KeyStore?)
                    val defaultTm = defaultFactory.trustManagers.filterIsInstance<X509TrustManager>().first()
                    defaultTm.checkServerTrusted(chain, authType)
                    return
                }
                val fingerprint = SubsonicAuthHelper.sha256Hex(cert.encoded)
                val pinned = credentialsStore.getPinnedCertFingerprintSync(profile.id)
                if (pinned == null) {
                    credentialsStore.pinCertFingerprintSync(profile.id, fingerprint)
                } else if (!pinned.equals(fingerprint, ignoreCase = true)) {
                    credentialsStore.recordRejectedCertFingerprint(profile.id, fingerprint)
                    throw java.security.cert.CertificateException(
                        "Certificate fingerprint changed for ${profile.name}"
                    )
                }
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        try {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustManager)
            builder.hostnameVerifier(HostnameVerifier { hostname, session ->
                val profile = profileProvider()
                if (profile.allowSelfSignedSsl) {
                    val primaryHost = profile.primaryUrl.toHttpUrlOrNull()?.host
                    val lanHost = profile.lanUrl?.toHttpUrlOrNull()?.host
                    if (hostname.equals(primaryHost, ignoreCase = true) ||
                        (lanHost != null && hostname.equals(lanHost, ignoreCase = true))
                    ) {
                        return@HostnameVerifier true
                    }
                }
                javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
            })
        } catch (_: Exception) {
        }

        return builder
    }
}
