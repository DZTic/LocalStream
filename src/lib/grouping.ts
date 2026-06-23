// Regroupement des vidéos en séries (saisons/épisodes) et sagas de films (collections TMDB).

import type { VideoFile } from './types';
import { getCleanTitle, isPersonalVideo } from './utils';

/**
 * Regroupe une liste plate de vidéos :
 *  - les épisodes (SxxExx / NxM) sont rassemblés par série,
 *  - les films appartenant à une même collection TMDB (>= 2 films locaux) forment une saga,
 *  - le reste demeure en standalone.
 *
 * Fonction pure : les vidéos personnelles non whitelistées sont filtrées.
 */
export const groupVideos = (
  videos: VideoFile[],
  movieCollections: Record<string, { id: number; name: string }>,
  releaseDates: Record<string, string>,
  whitelistedVideos: Set<string>,
): VideoFile[] => {
  const groups: Record<string, VideoFile> = {};
  const standaloneVideos: VideoFile[] = [];

  // Filtrer les vidéos personnelles (sauf celles whitelistées)
  const filteredVideos = videos.filter(video =>
    whitelistedVideos.has(video.name) || !isPersonalVideo(video.name, video.path || '')
  );

  filteredVideos.forEach(video => {
    const match = video.name.match(/[sS](\d+)(\s*)[eE](\d+)|(\d+)(\s*)x(\d+)/i);
    const isSeriesPattern = !!match;

    if (isSeriesPattern || video.seriesName) {
      if (match) {
        if (match[1]) {
          video.season = parseInt(match[1]);
          video.episode = parseInt(match[3]);
        } else {
          video.season = parseInt(match[4]);
          video.episode = parseInt(match[6]);
        }
      }

      const sName = video.seriesName || (match ? video.name.substring(0, match.index).replace(/[\.\-_/\\\[\]\(\)]/g, " ").trim().replace(/[\s\-]+$/, "") : getCleanTitle(video.name));
      const finalSeriesName = sName || "Série Inconnue";

      if (!groups[finalSeriesName]) {
        groups[finalSeriesName] = {
          file: video.file,
          url: video.url,
          name: finalSeriesName,
          type: 'series',
          path: video.path,
          isSeriesGroup: true,
          isTvSeries: true,
          episodes: [],
          seriesName: finalSeriesName
        };
      }
      groups[finalSeriesName].episodes!.push(video);
    } else {
      const cleanTitle = getCleanTitle(video.name);
      standaloneVideos.push({ ...video, cleanTitle });
    }
  });

  const seriesResult = Object.values(groups).map(group => {
    group.episodes!.sort((a, b) => {
      if (a.season !== b.season) return (a.season || 0) - (b.season || 0);
      return (a.episode || 0) - (b.episode || 0);
    });
    const firstEp = group.episodes![0];
    group.file = firstEp.file;
    group.url = firstEp.url;
    group.path = firstEp.path;
    return group;
  });

  // Groupement des films en sagas via les collections TMDB
  const collectionGroups: Record<string, { colName: string; films: VideoFile[] }> = {};
  const usedVideoNames = new Set<string>();
  const finalStandalone: VideoFile[] = [];

  standaloneVideos.forEach(v => {
    const col = movieCollections[v.name];
    if (col) {
      const key = `col_${col.id}`;
      if (!collectionGroups[key]) collectionGroups[key] = { colName: col.name, films: [] };
      collectionGroups[key].films.push(v);
      usedVideoNames.add(v.name);
    }
  });

  // Ne créer un groupe saga que s'il y a au moins 2 films locaux dans la collection
  const groupedMovies: VideoFile[] = [];
  Object.values(collectionGroups).forEach(({ colName, films }) => {
    if (films.length > 1) {
      films.sort((a, b) => {
        const dA = releaseDates[a.name] || '';
        const dB = releaseDates[b.name] || '';
        return dA.localeCompare(dB) || a.name.localeCompare(b.name, undefined, { numeric: true });
      });
      const first = films[0];
      groupedMovies.push({
        ...first,
        name: colName,
        type: 'series',
        isSeriesGroup: true,
        isTvSeries: false,
        episodes: films,
        seriesName: colName
      } as VideoFile);
    } else {
      // 1 seul film local dans la collection → laisser en standalone
      films.forEach(f => usedVideoNames.delete(f.name));
    }
  });

  standaloneVideos.forEach(v => {
    if (!usedVideoNames.has(v.name)) {
      finalStandalone.push(v);
    }
  });

  return [...seriesResult, ...groupedMovies, ...finalStandalone];
};
