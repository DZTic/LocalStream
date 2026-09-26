@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.localstream.app.ui.player

import android.media.MediaFormat
import android.os.Handler
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.video.VideoFrameMetadataListener

/**
 * Décide, à la première image affichée après un seek ou une reprise, s'il faut recaler la
 * lecture sur cette image.
 *
 * Sans index d'images clés (flux MPEG-TS, souvent des HLS concaténés nommés « .mp4 »), le seek
 * d'ExoPlayer tombe au milieu d'un GOP : l'audio repart à la position demandée, mais la vidéo
 * ne peut repartir qu'à l'image clé suivante. ExoPlayer l'affiche aussitôt puis la fige jusqu'à
 * ce que l'horloge audio la rattrape : image figée, son qui continue. Se recaler sur cette image
 * clé fait repartir les deux ensemble.
 */
internal class KeyframeAlignmentPolicy {

    /** Recalage décidé et pas encore abouti : sa propre première image ne doit pas en relancer un. */
    private var pendingAlignmentMs: Long? = null

    fun reset() {
        pendingAlignmentMs = null
    }

    fun isPending(targetMs: Long): Boolean = pendingAlignmentMs == targetMs

    /** Un seek vient d'être demandé : s'il ne s'agit pas du recalage, celui-ci est abandonné. */
    fun onSeek(targetMs: Long) {
        if (targetMs != pendingAlignmentMs) {
            pendingAlignmentMs = null
        }
    }

    /** Retourne la position sur laquelle se recaler, ou null si la vidéo est déjà en phase. */
    fun onFirstFrameRendered(positionMs: Long, framePositionMs: Long, durationMs: Long): Long? {
        val isOwnAlignment = pendingAlignmentMs != null
        val targetMs = framePositionMs.takeIf { !isOwnAlignment && isAhead(positionMs, it, durationMs) }
        pendingAlignmentMs = targetMs
        return targetMs
    }

    private fun isAhead(positionMs: Long, framePositionMs: Long, durationMs: Long): Boolean {
        val leadMs = framePositionMs - positionMs
        val isBeforeEnd = durationMs == C.TIME_UNSET || framePositionMs < durationMs
        // Position 0 : début naturel du fichier, on ne saute pas une éventuelle intro sans image.
        return positionMs > 0L && leadMs in MIN_LEAD_MS..MAX_LEAD_MS && isBeforeEnd
    }

    companion object {
        /** En dessous, l'écart tient dans une durée d'image (seek exact dans un MP4/MKV). */
        const val MIN_LEAD_MS = 100L

        /** Au-delà d'un GOP plausible, l'écart vient d'autre chose : on ne saute pas. */
        const val MAX_LEAD_MS = 15_000L
    }
}

/**
 * Position (ms) à viser pour se recaler sur une image horodatée en µs.
 *
 * Arrondie au-dessus : après un seek hors tampon, la file vidéo commence exactement à l'image
 * clé, et ExoPlayer refuse un seek dans le tampon qui vise avant son premier échantillon
 * (il relirait alors le fichier et tomberait sur l'image clé suivante). L'image clé reste
 * décodée, seul son ré-affichage est sauté : elle est déjà à l'écran.
 */
internal fun frameSeekPositionMs(frameUs: Long): Long = (frameUs.coerceAtLeast(0L) + 999L) / 1000L

/** Branche [KeyframeAlignmentPolicy] sur un [Player] et sur le rendu vidéo d'ExoPlayer. */
internal class KeyframeSeekAligner(
    private val player: Player,
) : Player.Listener, VideoFrameMetadataListener {

    private val policy = KeyframeAlignmentPolicy()
    private val handler = Handler(player.applicationLooper)

    // Écrit par le thread de lecture d'ExoPlayer, lu sur le thread principal.
    @Volatile
    private var lastRenderedFrameUs: Long = C.TIME_UNSET

    private var alignmentTargetMs: Long = C.TIME_UNSET
    private var alignmentDeadlineMs: Long = 0L

    // Le recalage n'évite le rechargement (et un nouveau saut d'image clé) que s'il reste dans
    // le tampon : on attend que l'audio comme la vidéo soient chargés au-delà de l'image clé.
    private val seekWhenBuffered = object : Runnable {
        override fun run() {
            val targetMs = alignmentTargetMs
            if (!policy.isPending(targetMs)) return
            when {
                player.bufferedPosition > targetMs -> player.seekTo(targetMs)
                SystemClock.elapsedRealtime() < alignmentDeadlineMs ->
                    handler.postDelayed(this, BUFFER_POLL_INTERVAL_MS)
                else -> policy.reset()
            }
        }
    }

    fun release() {
        handler.removeCallbacks(seekWhenBuffered)
    }

    override fun onVideoFrameAboutToBeRendered(
        presentationTimeUs: Long,
        releaseTimeNs: Long,
        format: Format,
        mediaFormat: MediaFormat?,
    ) {
        lastRenderedFrameUs = presentationTimeUs
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        policy.reset()
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            policy.onSeek(newPosition.positionMs)
        }
    }

    override fun onRenderedFirstFrame() {
        val frameUs = lastRenderedFrameUs
        if (frameUs == C.TIME_UNSET) return
        // Les images suivantes ne sont libérées qu'à leur échéance : si la première est en
        // avance, lastRenderedFrameUs la désigne encore ici.
        val targetMs = policy.onFirstFrameRendered(
            positionMs = player.currentPosition,
            framePositionMs = frameSeekPositionMs(frameUs),
            durationMs = player.duration,
        ) ?: return
        alignmentTargetMs = targetMs
        alignmentDeadlineMs = SystemClock.elapsedRealtime() + MAX_BUFFER_WAIT_MS
        handler.removeCallbacks(seekWhenBuffered)
        seekWhenBuffered.run()
    }

    private companion object {
        const val BUFFER_POLL_INTERVAL_MS = 10L

        /** Fichier local : le tampon dépasse l'image clé en quelques ms. Au-delà, on renonce. */
        const val MAX_BUFFER_WAIT_MS = 1_000L
    }
}
