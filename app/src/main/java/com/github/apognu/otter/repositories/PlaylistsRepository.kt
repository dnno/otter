package com.github.apognu.otter.repositories

import android.content.Context
import com.github.apognu.otter.utils.OtterResponse
import com.github.apognu.otter.utils.Playlist
import com.github.apognu.otter.utils.PlaylistsCache
import com.github.apognu.otter.utils.PlaylistsResponse
import com.github.apognu.otter.utils.Settings
import com.github.apognu.otter.utils.Track
import com.github.apognu.otter.utils.mustNormalizeUrl
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.coroutines.awaitByteArrayResponseResult
import com.github.kittinunf.fuel.coroutines.awaitObjectResponseResult
import com.github.kittinunf.fuel.gson.gsonDeserializerOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader

data class PlaylistAdd(val tracks: List<Int>, val allow_duplicates: Boolean)

class PlaylistsRepository(override val context: Context?) : Repository<Playlist, PlaylistsCache>() {
  override val cacheId = "tracks-playlists"
  override val upstream = HttpUpstream<Playlist, OtterResponse<Playlist>>(
    context,
    HttpUpstream.Behavior.Progressive,
    "/api/v1/playlists/?playable=true&ordering=name",
    object : TypeToken<PlaylistsResponse>() {}.type
  )

  override fun cache(data: List<Playlist>) = PlaylistsCache(data)
  override fun uncache(reader: BufferedReader) =
    gsonDeserializerOf(PlaylistsCache::class.java).deserialize(reader)

  suspend fun new(name: String): Int? {
    val body = mapOf("name" to name, "privacy_level" to "me")

    val request = Fuel.post(mustNormalizeUrl("/api/v1/playlists/")).apply {
      if (!Settings.isAnonymous()) {
        header("Authorization", "Bearer ${Settings.getAccessToken()}")
      }
    }

    val (_, response, result) = request
      .header("Content-Type", "application/json")
      .body(Gson().toJson(body))
      .awaitObjectResponseResult(gsonDeserializerOf(Playlist::class.java))

    if (response.statusCode != 201) return null

    return result.get().id
  }

  fun add(id: Int, tracks: List<Track>) {
    val body = PlaylistAdd(tracks.map { it.id }, false)

    val request = Fuel.post(mustNormalizeUrl("/api/v1/playlists/$id/add/")).apply {
      if (!Settings.isAnonymous()) {
        header("Authorization", "Bearer ${Settings.getAccessToken()}")
      }
    }

    scope.launch(Dispatchers.IO) {
      request
        .header("Content-Type", "application/json")
        .body(Gson().toJson(body))
        .awaitByteArrayResponseResult()
    }
  }

  suspend fun remove(id: Int, track: Track, index: Int) {
    val body = mapOf("index" to index)

    val request = Fuel.post(mustNormalizeUrl("/api/v1/playlists/$id/remove/")).apply {
      if (!Settings.isAnonymous()) {
        header("Authorization", "Bearer ${Settings.getAccessToken()}")
      }
    }

    request
      .header("Content-Type", "application/json")
      .body(Gson().toJson(body))
      .awaitByteArrayResponseResult()
  }

  fun move(id: Int, from: Int, to: Int) {
    val body = mapOf("from" to from, "to" to to)

    val request = Fuel.post(mustNormalizeUrl("/api/v1/playlists/$id/move/")).apply {
      if (!Settings.isAnonymous()) {
        header("Authorization", "Bearer ${Settings.getAccessToken()}")
      }
    }

    scope.launch(Dispatchers.IO) {
      request
        .header("Content-Type", "application/json")
        .body(Gson().toJson(body))
        .awaitByteArrayResponseResult()
    }
  }
}
