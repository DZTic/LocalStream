import React from 'react';
import { VideoFile } from '../../lib/types';
import { VideoCard } from '../VideoCard';

interface LibraryScreenProps {
  videos: VideoFile[];
  posters: Record<string, string>;
  watchedVideos: Record<string, boolean>;
  watchProgress: Record<string, number>;
  onOpenInfo: (video: VideoFile) => void;
  onPlay: (video: VideoFile) => void;
}

/** Vue "Bibliothèque" : grille filtrée/triée de toute la collection. */
export const LibraryScreen: React.FC<LibraryScreenProps> = ({
  videos, posters, watchedVideos, watchProgress, onOpenInfo, onPlay,
}) => (
  <div className="px-4 md:px-12 min-h-screen">
    <h2 className="text-2xl md:text-3xl font-bold text-white mb-8">
      Bibliothèque
    </h2>
    {videos.length > 0 ? (
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
        {videos.map((video) => {
          const isWatched = video.isSeriesGroup
            ? (video.episodes && video.episodes.length > 0 && video.episodes.every(ep => !!watchedVideos[ep.name]))
            : !!watchedVideos[video.name];

          return (
            <VideoCard
              key={video.nativeUri || video.path || video.name}
              video={video}
              posterUrl={posters[video.name]}
              isWatched={isWatched}
              progress={watchProgress[video.name]}
              onOpenInfo={onOpenInfo}
              onPlay={onPlay}
            />
          );
        })}
      </div>
    ) : (
      <div className="text-center text-zinc-500 mt-12">
        Aucun résultat correspondant à ces filtres.
      </div>
    )}
  </div>
);
