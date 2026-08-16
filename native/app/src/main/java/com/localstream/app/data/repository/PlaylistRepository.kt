package com.localstream.app.data.repository

import com.localstream.app.data.db.dao.PlaylistDao
import com.localstream.app.data.db.entity.PlaylistEntity
import com.localstream.app.data.db.entity.PlaylistItemEntity
import com.localstream.app.data.db.entity.PlaylistWithItems
import com.localstream.app.domain.model.PlaylistInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Repository de gestion des playlists utilisateur.
 *
 * Toutes les mutations sont suspendues.
 * [observePlaylists] expose un Flow de [PlaylistInfo] reconstitué depuis les deux tables Room.
 */
class PlaylistRepository(private val playlistDao: PlaylistDao) {

    /** Flow réactif : liste complète des playlists avec leurs vidéos ordonnées en une seule requête. */
    val observePlaylists: Flow<List<PlaylistInfo>> =
        playlistDao.observePlaylistsWithItems().map { list ->
            list.map { it.toPlaylistInfo() }
        }

    suspend fun getAllPlaylists(): List<PlaylistInfo> {
        val list = playlistDao.getPlaylistsWithItems()
        return list.map { it.toPlaylistInfo() }
    }

    suspend fun createPlaylist(name: String): PlaylistInfo {
        val entity = PlaylistEntity(
            id = UUID.randomUUID().toString(),
            name = name,
        )
        playlistDao.upsertPlaylist(entity)
        return PlaylistInfo(id = entity.id, name = entity.name)
    }

    suspend fun deletePlaylist(playlistId: String) {
        playlistDao.deletePlaylist(playlistId)
    }

    suspend fun addToPlaylist(playlistId: String, videoName: String) {
        val currentItems = playlistDao.getItems(playlistId)
        if (currentItems.any { it.videoName == videoName }) return
        val newPosition = currentItems.size
        playlistDao.upsertItem(
            PlaylistItemEntity(
                playlistId = playlistId,
                videoName = videoName,
                position = newPosition,
            )
        )
    }

    suspend fun removeFromPlaylist(playlistId: String, videoName: String) {
        playlistDao.deleteItem(playlistId, videoName)
        // R\u00e9num\u00e9rotation des positions
        val remaining = playlistDao.getItems(playlistId)
        val reordered = remaining.sortedBy { it.position }.mapIndexed { idx, item ->
            item.copy(position = idx)
        }
        playlistDao.replaceItems(playlistId, reordered)
    }

    suspend fun renamePlaylist(playlistId: String, newName: String) {
        val existing = playlistDao.getAllPlaylists().firstOrNull { it.id == playlistId } ?: return
        playlistDao.upsertPlaylist(existing.copy(name = newName))
    }
}

private fun PlaylistWithItems.toPlaylistInfo(): PlaylistInfo =
    PlaylistInfo(
        id = playlist.id,
        name = playlist.name,
        videoNames = items.sortedBy { it.position }.map { it.videoName },
    )
