package com.localstream.app.data.repository

import com.localstream.app.data.db.dao.PlaylistDao
import com.localstream.app.data.db.entity.PlaylistEntity
import com.localstream.app.data.db.entity.PlaylistItemEntity
import com.localstream.app.domain.model.PlaylistInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID

/**
 * Repository de gestion des playlists utilisateur.
 *
 * Toutes les mutations sont suspendues.
 * [observePlaylists] expose un Flow de [PlaylistInfo] reconstitu\u00e9 depuis les deux tables Room.
 */
class PlaylistRepository(private val playlistDao: PlaylistDao) {

    /** Flow reactif : liste compl\u00e8te des playlists avec leurs vid\u00e9os ordonn\u00e9es. */
    val observePlaylists: Flow<List<PlaylistInfo>> =
        combine(
            playlistDao.observePlaylists(),
            // On observe les items globaux via getAllItems dans un flow d\u00e9di\u00e9 (simple polling n'est
            // pas id\u00e9al ; remplacer par une requ\u00eate GROUP BY en Phase 8 si besoin de perf).
            playlistDao.observePlaylists(), // second slot — force re-collect quand les playlists changent
        ) { playlists, _ ->
            buildPlaylistInfoList(playlists)
        }

    private suspend fun buildPlaylistInfoList(playlists: List<PlaylistEntity>): List<PlaylistInfo> =
        playlists.map { entity ->
            val items = playlistDao.getItems(entity.id)
            PlaylistInfo(
                id = entity.id,
                name = entity.name,
                videoNames = items.sortedBy { it.position }.map { it.videoName },
            )
        }

    suspend fun getAllPlaylists(): List<PlaylistInfo> {
        val entities = playlistDao.getAllPlaylists()
        return buildPlaylistInfoList(entities)
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
