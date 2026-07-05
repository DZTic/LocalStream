// Logique pure de scan : détection de séries et association des sous-titres.
// Partagée entre le scan natif (MediaStore/Filesystem) et la sélection de dossier web.

export const VIDEO_EXT_RE = /\.(mp4|mkv|webm|avi|mov)$/i;
export const SUBTITLE_EXT_RE = /\.(srt|vtt|ass|ssa)$/i;

// Suffixe de langue optionnel avant l'extension : "film.fr.srt" → base "film"
const LANG_SUFFIX_RE = /\.(fr|en|es|de|it|pt|nl|pl|ru|ja|zh|ko|ar|he|tr|sv|da|fi|nb|uk|cs|sk|hu|ro|hr|sr|bg|el|vi|th|hi|id|ms|fa)$/i;

// "S01E02", "s1 e2" ou "1x02"
const SEASON_EPISODE_RE = /[sS](\d+)(\s*)[eE](\d+)|(\d+)(\s*)x(\d+)/i;

// Dossier de saison : "Saison 1", "Season 2", "S3"
const SEASON_FOLDER_RE = /Saison\s*(\d+)|Season\s*(\d+)|S(\d+)/i;

/** Nom de base d'un fichier vidéo, sans extension, en minuscules. */
export const videoBaseName = (fileName: string): string =>
  fileName.replace(/\.\w+$/, '').toLowerCase();

/** Nom de base d'un sous-titre : sans extension ni suffixe de langue, en minuscules. */
export const subtitleBaseName = (fileName: string): string =>
  fileName.replace(/\.\w+$/, '').replace(LANG_SUFFIX_RE, '').toLowerCase();

export interface SeriesInfo {
  seriesName?: string;
  season?: number;
  episode?: number;
}

/**
 * Détecte série/saison/épisode depuis le nom de fichier (SxxExx ou 1x02),
 * avec repli sur la structure de dossiers ("Show/Saison 1/01.mp4") si un
 * chemin complet est fourni.
 */
export const parseSeriesInfo = (fileName: string, fullPath?: string): SeriesInfo => {
  const match = fileName.match(SEASON_EPISODE_RE);
  if (match) {
    const season = parseInt(match[1] || match[4], 10);
    const episode = parseInt(match[3] || match[6], 10);
    let seriesName = fileName
      .substring(0, match.index)
      .replace(/[\.\-_/\\\[\]\(\)]/g, ' ')
      .trim()
      .replace(/[\s\-]+$/, '');
    if (!seriesName) seriesName = 'Série Inconnue';
    return { seriesName, season, episode };
  }

  // Détection par structure de dossiers : ex Series/ShowName/Season 1/01.mp4
  const pathParts = (fullPath || '').split('/').filter(p => !!p);
  if (pathParts.length >= 2) {
    const lastFolder = pathParts[pathParts.length - 2]; // ex: "Season 1"
    const sMatch = lastFolder.match(SEASON_FOLDER_RE);
    if (sMatch) {
      const season = parseInt(sMatch[1] || sMatch[2] || sMatch[3], 10);
      const seriesName = pathParts[pathParts.length - 3] || 'Série Inconnue'; // ex: "ShowName"
      // Essayer d'extraire l'épisode du début du nom de fichier
      const epMatch = fileName.match(/^(\d+)/);
      const episode = epMatch ? parseInt(epMatch[1], 10) : undefined;
      return { seriesName, season, episode };
    }
  }

  return {};
};

export interface SubtitleEntry {
  /** Nom de fichier du sous-titre (ex: "film.fr.srt"). */
  name: string;
  /** Dossier contenant le sous-titre (chemin relatif, sans le nom de fichier). */
  folder: string;
  /** URI ou chemin permettant de charger le sous-titre. */
  uri: string;
}

/** Dossier parent d'un chemin relatif ("Movies/film.mp4" → "Movies"). */
export const parentFolder = (relativePath: string): string => {
  const normalized = relativePath.replace(/\\/g, '/').replace(/\/+$/, '');
  const idx = normalized.lastIndexOf('/');
  return idx === -1 ? '' : normalized.slice(0, idx);
};

/**
 * Indexe des sous-titres par "dossier/nom-de-base" pour un matching rapide
 * avec les vidéos du même dossier portant le même nom de base.
 */
export const buildSubtitleIndex = (subtitles: SubtitleEntry[]): Record<string, SubtitleEntry> => {
  const index: Record<string, SubtitleEntry> = {};
  for (const sub of subtitles) {
    if (!SUBTITLE_EXT_RE.test(sub.name)) continue;
    index[`${sub.folder}/${subtitleBaseName(sub.name)}`] = sub;
  }
  return index;
};

/** Retrouve le sous-titre voisin d'une vidéo (même dossier, même nom de base). */
export const matchSubtitle = (
  index: Record<string, SubtitleEntry>,
  videoName: string,
  videoFolder: string,
): SubtitleEntry | undefined =>
  index[`${videoFolder}/${videoBaseName(videoName)}`];
