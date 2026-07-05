import React from 'react';
import { VideoFile } from '../../lib/types';
import { VideoCard } from '../VideoCard';

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
  <div className="px-4 md:px-12 min-h-screen pt-24">
    <h2 className="text-2xl md:text-3xl font-bold text-white mb-8">
      Résultats pour "{searchQuery}"
    </h2>
    {searchResults.length > 0 ? (
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
        {searchResults.map((video) => {
          const isWatched = video.isSeriesGroup
            ? (video.episodes && video.episodes.length > 0 && video.episodes.every(ep => !!watchedVideos[ep.name]))
            : !!watchedVideos[video.name];

          const posterKey = video.isSeriesGroup ? video.seriesName! : video.name;

          return (
            <VideoCard
              key={video.nativeUri || video.path || video.name}
              video={video}
              posterUrl={posters[posterKey]}
              isWatched={isWatched}
              progress={watchProgress[video.name]}
              onOpenInfo={onOpenInfo}
              onPlay={() => {}} // Play par défaut ou non utilisé directement dans SearchScreen
            />
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
