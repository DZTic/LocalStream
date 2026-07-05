import React from 'react';
import { Play, Check } from 'lucide-react';
import { VideoFile } from '../../lib/types';
import { getCleanTitle, getResolution } from '../../lib/utils';

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
            <div
              key={video.nativeUri || video.path || video.name}
              className="group relative aspect-[2/3] bg-zinc-800 rounded-md overflow-hidden cursor-pointer transition-transform duration-300 hover:scale-105 hover:z-30 shadow-lg"
              onClick={() => onOpenInfo(video)}
            >
              {isWatched && (
                <div className="absolute top-2 right-2 z-20 bg-green-600 rounded-full p-1 shadow-md">
                  <Check className="w-3 h-3 text-white" />
                </div>
              )}
              {getResolution(video.name) && (
                <div className="absolute bottom-2 left-2 z-10 bg-black/70 text-white text-[10px] font-black px-1.5 py-0.5 rounded border border-white/20 uppercase tracking-tighter">
                  {getResolution(video.name)}
                </div>
              )}
              {posters[video.name] ? (
                <img
                  src={posters[video.name]}
                  alt={video.name}
                  className="w-full h-full object-cover"
                  loading="lazy"
                />
              ) : (
                <div className="w-full h-full flex items-center justify-center p-4 text-center">
                  <span className="text-zinc-500 font-medium text-sm line-clamp-3">{getCleanTitle(video.name)}</span>
                </div>
              )}
              <div className="absolute inset-0 bg-black/60 opacity-0 group-hover:opacity-100 transition-opacity duration-300 flex flex-col items-center justify-center p-4">
                <button onClick={(e) => { e.stopPropagation(); onPlay(video); }} className="bg-white text-black p-3 rounded-full hover:bg-white/80 mb-2">
                  <Play className="w-6 h-6 fill-black" />
                </button>
                <p className="text-white text-xs font-medium text-center line-clamp-2 drop-shadow-md">
                  {getCleanTitle(video.name)}
                </p>
              </div>
              {watchProgress[video.name] > 0 && watchProgress[video.name] < 100 && (
                <div className="absolute bottom-0 left-0 right-0 h-1 bg-zinc-600">
                  <div className="h-full bg-red-600" style={{ width: `${watchProgress[video.name]}%` }} />
                </div>
              )}
            </div>
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
