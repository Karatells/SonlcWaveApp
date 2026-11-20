package com.sonicwave.sonic_wave.domain.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.sonicwave.sonic_wave.data.local.AppDatabase
import com.sonicwave.sonic_wave.data.remote.SubsonicApiException
import com.sonicwave.sonic_wave.domain.repository.MediaServerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SyncQueueWorker(
    private val context: Context,
    private val database: AppDatabase,
    private val repository: MediaServerRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val gson = Gson()

    @Volatile
    private var activeServerId: String? = null

    @Volatile
    private var registeredCallback: ConnectivityManager.NetworkCallback? = null

    fun startListening(serverId: String) {
        activeServerId = serverId
        if (registeredCallback != null) {
            scope.launch { flushPendingActions(serverId) }
            return
        }

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val caps = cm.getNetworkCapabilities(network)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                    caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
                ) {
                    repository.resetLanFailover()
                }
                activeServerId?.let { id ->
                    scope.launch { flushPendingActions(id) }
                }
            }
        }
        registeredCallback = callback
        runCatching { cm.registerDefaultNetworkCallback(callback) }
        scope.launch { flushPendingActions(serverId) }
    }

    fun updateActiveServer(serverId: String) {
        activeServerId = serverId
        scope.launch { flushPendingActions(serverId) }
    }

    suspend fun flushPendingActions(serverId: String) {
        if (!syncMutex.tryLock()) return
        try {
            val actions = database.getPendingSyncActions(serverId)
            for (action in actions) {
                try {
                    val payload = runCatching {
                        gson.fromJson(action.payloadJson, JsonObject::class.java)
                    }.getOrNull() ?: JsonObject()

                    when (action.actionType) {
                        "scrobble" -> {
                            val ts = payload.get("timestampMs")?.asLong ?: System.currentTimeMillis()
                            val sub = payload.get("submission")?.asBoolean ?: true
                            repository.scrobble(action.targetId, ts, sub)
                        }
                        "star" -> repository.setFavorite(action.targetId, true)
                        "unstar" -> repository.setFavorite(action.targetId, false)
                        "rating" -> {
                            val rating = payload.get("rating")?.asInt ?: 0
                            repository.setRating(action.targetId, rating)
                        }
                        "bookmark" -> {
                            val posMs = payload.get("positionMs")?.asLong ?: 0L
                            val comment = payload.get("comment")?.asString
                            repository.saveBookmark(action.targetId, posMs, comment)
                        }
                    }
                    database.deleteSyncAction(action.id)
                } catch (e: SubsonicApiException) {
                    if (e.code == 70 || action.retryCount >= 4) {
                        database.deleteSyncAction(action.id)
                    } else {
                        database.incrementSyncActionRetry(action.id)
                        break
                    }
                } catch (_: Exception) {
                    if (action.retryCount >= 4) {
                        database.deleteSyncAction(action.id)
                    } else {
                        database.incrementSyncActionRetry(action.id)
                        break
                    }
                }
            }
        } finally {
            syncMutex.unlock()
        }
    }
}
