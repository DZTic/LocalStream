// Sélection des candidats pour la bannière "hero" de l'accueil.
// Objectif (#4) : faire tourner les films/séries non vus ou non terminés,
// au lieu d'afficher toujours le dernier ajouté.

import type { VideoFile } from './types';

/** Un contenu est "terminé" si vu (film) ou si tous ses épisodes sont vus (série). */
export const isFullyWatched = (
  v: VideoFile,
  watchedVideos: Record<string, boolean>,
): boolean =>
  v.isSeriesGroup
    ? !!(v.episodes && v.episodes.length > 0 && v.episodes.every(ep => !!watchedVideos[ep.name]))
    : !!watchedVideos[v.name];

/** Un film est "en cours" si sa progression est entamée mais non terminée. */
const isInProgress = (v: VideoFile, watchProgress: Record<string, number>): boolean => {
  const p = watchProgress[v.name] || 0;
  return p > 0 && p < 95;
};

/**
 * Liste ordonnée des candidats pour la rotation du hero :
 *  - uniquement les contenus non terminés (non vus ou en cours),
 *  - les contenus en cours d'abord, puis les plus récemment ajoutés.
 * Fonction pure (sans état React).
 */
export const getHeroCandidates = (
  groupedVideos: VideoFile[],
  watchedVideos: Record<string, boolean>,
  watchProgress: Record<string, number> = {},
): VideoFile[] =>
  groupedVideos
    .filter(v => !isFullyWatched(v, watchedVideos))
    .sort((a, b) => {
      const aProg = isInProgress(a, watchProgress);
      const bProg = isInProgress(b, watchProgress);
      if (aProg !== bProg) return aProg ? -1 : 1;
      return (b.file?.lastModified || b.lastModified || 0) - (a.file?.lastModified || a.lastModified || 0);
    });
