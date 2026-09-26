package com.localstream.app.ui.player

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyframeAlignmentPolicyTest {

    private val durationMs = 14_008_620L

    @Test
    fun firstFrameAfterKeyframeGap_alignsOnThatFrame() {
        // Mesuré sur un .mp4 MPEG-TS : seek à 3783,344 s, première image vidéo à 3784,083 s.
        val policy = KeyframeAlignmentPolicy()

        assertEquals(3_784_083L, policy.onFirstFrameRendered(3_783_344L, 3_784_083L, durationMs))
        assertTrue(policy.isPending(3_784_083L))
    }

    @Test
    fun firstFrameWithinOneFrameDuration_doesNotAlign() {
        // Seek exact dans un vrai MP4 : la première image suit la position de moins d'une image.
        val policy = KeyframeAlignmentPolicy()

        assertNull(policy.onFirstFrameRendered(15_041L, 15_082L, durationMs))
    }

    @Test
    fun startOfFile_doesNotAlign() {
        assertNull(KeyframeAlignmentPolicy().onFirstFrameRendered(0L, 2_000L, durationMs))
    }

    @Test
    fun implausibleLead_doesNotAlign() {
        val policy = KeyframeAlignmentPolicy()

        assertNull(policy.onFirstFrameRendered(10_000L, 10_000L + KeyframeAlignmentPolicy.MAX_LEAD_MS + 1, durationMs))
    }

    @Test
    fun frameAtOrAfterEnd_doesNotAlign() {
        val policy = KeyframeAlignmentPolicy()

        assertNull(policy.onFirstFrameRendered(durationMs - 1_000L, durationMs, durationMs))
    }

    @Test
    fun unknownDuration_stillAligns() {
        val policy = KeyframeAlignmentPolicy()

        assertEquals(5_000L, policy.onFirstFrameRendered(4_000L, 5_000L, C.TIME_UNSET))
    }

    @Test
    fun firstFrameOfOwnAlignmentSeek_doesNotChain() {
        val policy = KeyframeAlignmentPolicy()
        val target = policy.onFirstFrameRendered(3_796_101L, 3_800_084L, durationMs)!!

        policy.onSeek(target)
        // Si le seek a dû relire le fichier, l'image suivante peut encore être en avance :
        // on ne relance pas un recalage qui repousserait la position d'un GOP à chaque fois.
        assertNull(policy.onFirstFrameRendered(target, target + 4_000L, durationMs))
        assertFalse(policy.isPending(target))
    }

    @Test
    fun userSeekDuringAlignment_cancelsItAndIsAlignedOnItsOwn() {
        val policy = KeyframeAlignmentPolicy()
        val first = policy.onFirstFrameRendered(3_727_642L, 3_728_084L, durationMs)!!

        policy.onSeek(3_726_171L)

        assertFalse(policy.isPending(first))
        assertEquals(3_728_084L, policy.onFirstFrameRendered(3_726_171L, 3_728_084L, durationMs))
    }

    @Test
    fun reset_dropsPendingAlignment() {
        val policy = KeyframeAlignmentPolicy()
        policy.onFirstFrameRendered(1_000L, 2_000L, durationMs)

        policy.reset()

        assertFalse(policy.isPending(2_000L))
        assertEquals(4_000L, policy.onFirstFrameRendered(3_000L, 4_000L, durationMs))
    }

    @Test
    fun frameSeekPositionMs_roundsUpToStayAtOrAfterTheFrame() {
        // Horodatage réel d'une image clé TS (90 kHz) : arrondir en dessous viserait avant elle.
        assertEquals(3_720_084L, frameSeekPositionMs(3_720_083_333L))
        assertEquals(15_082L, frameSeekPositionMs(15_082_000L))
        assertEquals(0L, frameSeekPositionMs(0L))
    }
}
