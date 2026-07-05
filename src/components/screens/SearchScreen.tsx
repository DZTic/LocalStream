import React from 'react';
import { Play, Check } from 'lucide-react';
import { VideoFile } from '../../lib/types';
import { getCleanTitle, getResolution } from '../../lib/utils';

interface SearchScreenProps {
  searchQuery: string;
  searchResults: VideoFile[];
  posters: Record<string, string>;
  watchedVideos: Record<string, boolean>;
  watchProgress: Record<string, number>;
  onOpenInfo: (video: VideoFile) => void;
}

/** Écran des résultats de recherche (grille d'affiches). */
export const SearchScreen: React.FC<SearchScreenProps> = ({
  searchQuery, searchResults, posters, watchedVideos, watchProgress, onOpenInfo,
}) => (
  <div className="px-4 md:px-12 min-h-screen pt-4">
    <h2 className="text-2xl md:text-3xl font-bold text-white mb-8">
      Résultats pour "{searchQuery}"
    </h2>
    {searchResults.length > 0 ? (
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
        {searchResults.map((video, index) => {
          const isWatched = video.isSeriesGroup
            ? (video.episodes && video.episodes.length > 0 && video.episodes.every(ep => !!watchedVideos[ep.name]))
            : !!watchedVideos[video.name];

          return (
            <div
              key={index}
              className="group flex flex-col"
              onClick={() => onOpenInfo(video)}
            >
              <div className={`relative aspect-[2/3] bg-zinc-800 rounded-md overflow-hidden cursor-pointer transition-transform duration-300 group-hover:scale-105 group-hover:z-30 shadow-lg`}>
                {isWatched && (
                  <div className="absolute top-2 right-2 z-20 bg-green-600 rounded-full p-1 shadow-md">
                    <Check className="w-2.5 h-2.5 md:w-3 md:h-3 text-white" />
                  </div>
                )}
                {video.isSeriesGroup && (
                  <div className="absolute top-2 left-2 z-10 bg-red-600 text-white text-[10px] font-bold px-1.5 py-0.5 rounded shadow-lg uppercase">
                    {video.isTvSeries ? 'Série' : 'Saga'}
                  </div>
                )}
                {getResolution(video.name) && (
                  <div className="absolute bottom-2 left-2 z-10 bg-black/60 backdrop-blur-sm text-white text-[10px] font-black px-1.5 py-0.5 rounded border border-white/20 uppercase tracking-tighter">
                    {getResolution(video.name)}
                  </div>
                )}
                {posters[video.isSeriesGroup ? video.seriesName! : video.name] ? (
                  <img
                    src={posters[video.isSeriesGroup ? video.seriesName! : video.name]}
                    alt={getCleanTitle(video.name)}
                    className="w-full h-full object-cover"
                    loading="lazy"
                  />
                ) : (
                  <div className="w-full h-full flex items-center justify-center p-4 text-center">
                    <span className="text-zinc-500 font-medium text-sm">{video.isSeriesGroup ? video.seriesName : getCleanTitle(video.name)}</span>
                  </div>
                )}
                <div className="absolute inset-0 bg-black/60 opacity-0 group-hover:opacity-100 transition-opacity duration-300 flex items-center justify-center p-4">
                  <Play className="w-10 h-10 text-white drop-shadow-lg" />
                </div>
                {watchProgress[video.name] > 0 && watchProgress[video.name] < 100 && (
                  <div className="absolute bottom-0 left-0 right-0 h-1 bg-zinc-600">
                    <div className="h-full bg-red-600" style={{ width: `${watchProgress[video.name]}%` }} />
                  </div>
                )}
              </div>
              <div className="mt-2 px-1">
                <p className="text-[10px] md:text-xs font-medium text-zinc-400 break-words line-clamp-none">
                  {video.isSeriesGroup ? video.seriesName : getCleanTitle(video.name)}
                </p>
              </div>
            </div>
          );
        })}
      </div>
    ) : (
      <div className="text-center text-zinc-500 mt-12">
        Aucun résultat trouvé pour "{searchQuery}"
      </div>
    )}
  </div>
);
