// Tri et filtrage des vidéos regroupées. Fonctions pures prenant l'état en paramètre.

import type { VideoFile } from './types';

export type SortBy = 'alpha' | 'date' | 'size' | 'duration';

/** Un groupe est considéré "vu" si tous ses épisodes le sont ; un film s'il est marqué vu. */
export const isGroupWatched = (
  video: VideoFile,
  watchedVideos: Record<string, boolean>,
): boolean => {
  if (video.isSeriesGroup) {
    return !!video.episodes?.every(ep => !!watchedVideos[ep.name]);
  }
  return !!watchedVideos[video.name];
};

/** Tri alphabétique, les vidéos vues reléguées à la fin. */
export const sortAlphabetical = (
  videos: VideoFile[],
  watchedVideos: Record<string, boolean>,
): VideoFile[] =>
  [...videos].sort((a, b) => {
    const aWatched = isGroupWatched(a, watchedVideos);
    const bWatched = isGroupWatched(b, watchedVideos);
    if (aWatched !== bWatched) return aWatched ? 1 : -1;
    return a.name.localeCompare(b.name);
  });

/** Recommandations pseudo-aléatoires stables (basées sur la longueur du nom), non-vues en tête. */
export const sortRecommendations = (
  videos: VideoFile[],
  watchedVideos: Record<string, boolean>,
): VideoFile[] =>
  [...videos].sort((a, b) => {
    const aWatched = isGroupWatched(a, watchedVideos);
    const bWatched = isGroupWatched(b, watchedVideos);
    if (aWatched !== bWatched) return aWatched ? 1 : -1;
    return (a.name.length % 7) - (b.name.length % 7);
  });

export interface FilterSortOptions {
  sortBy: SortBy;
  filterGenre: number | 'all';
  filterResolution: string | 'all';
  watchedVideos: Record<string, boolean>;
  releaseDates: Record<string, string>;
  videoGenres: Record<string, number[]>;
  videoDurations: Record<string, number>;
}

/** Applique filtres (genre, résolution) puis tri (alpha/date/taille/durée), non-vues en tête. */
export const filterAndSortVideos = (
  videos: VideoFile[],
  opts: FilterSortOptions,
): VideoFile[] => {
  const { sortBy, filterGenre, filterResolution, watchedVideos, releaseDates, videoGenres, videoDurations } = opts;
  let result = [...videos];

  if (filterGenre !== 'all') {
    result = result.filter(v => {
      const lookupKey = v.isSeriesGroup ? v.seriesName! : v.name;
      const genres = videoGenres[lookupKey];
      return genres && genres.includes(filterGenre as number);
    });
  }

  if (filterResolution !== 'all') {
    result = result.filter(v => {
      const nameLower = (v.isSeriesGroup ? (v.episodes![0]?.name || v.name) : v.name).toLowerCase();
      if (filterResolution === '4k') return nameLower.includes('2160p') || nameLower.includes('4k');
      if (filterResolution === '1080p') return nameLower.includes('1080p');
      if (filterResolution === '720p') return nameLower.includes('720p');
      if (filterResolution === 'sd') return !nameLower.match(/1080p|720p|2160p|4k/);
      return true;
    });
  }

  result.sort((a, b) => {
    // Priorité aux vidéos non-vues : les vues sont triées à la fin.
    const aWatched = isGroupWatched(a, watchedVideos);
    const bWatched = isGroupWatched(b, watchedVideos);
    if (aWatched !== bWatched) return aWatched ? 1 : -1;

    if (sortBy === 'alpha') {
      const nameA = a.isSeriesGroup ? a.seriesName! : a.name;
      const nameB = b.isSeriesGroup ? b.seriesName! : b.name;
      return nameA.localeCompare(nameB);
    } else if (sortBy === 'date') {
      const lookupA = a.isSeriesGroup ? a.seriesName! : a.name;
      const lookupB = b.isSeriesGroup ? b.seriesName! : b.name;
      const dateA = releaseDates[lookupA] || (a.file?.lastModified || a.lastModified || 0).toString();
      const dateB = releaseDates[lookupB] || (b.file?.lastModified || b.lastModified || 0).toString();
      return dateB.localeCompare(dateA);
    } else if (sortBy === 'size') {
      const sizeA = a.isSeriesGroup ? a.episodes!.reduce((s, e) => s + (e.file?.size || e.size || 0), 0) : (a.file?.size || a.size || 0);
      const sizeB = b.isSeriesGroup ? b.episodes!.reduce((s, e) => s + (e.file?.size || e.size || 0), 0) : (b.file?.size || b.size || 0);
      return sizeB - sizeA;
    } else if (sortBy === 'duration') {
      const durA = a.isSeriesGroup ? a.episodes!.reduce((s, e) => s + (videoDurations[e.name] || 0), 0) : (videoDurations[a.name] || 0);
      const durB = b.isSeriesGroup ? b.episodes!.reduce((s, e) => s + (videoDurations[e.name] || 0), 0) : (videoDurations[b.name] || 0);
      return durB - durA;
    }
    return 0;
  });

  return result;
};
