package com.localstream.app.domain

import com.localstream.app.domain.model.SubtitleEntry

/**
 * Priorité de sélection des sous-titres (Phase 6, étape 5) :
 * sous-titre choisi manuellement > sous-titre auto-détecté (matching MediaStore, Phase 3).
 */
object SubtitleSelector {

    /** Renvoie l'uri du sous-titre à utiliser : [manualUri] si présent, sinon l'auto-détecté. */
    fun select(manualUri: String?, autoDetected: SubtitleEntry?): String? =
        manualUri ?: autoDetected?.uri
}
