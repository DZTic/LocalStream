import React from 'react';
import { Play, Check } from 'lucide-react';
import { VideoFile } from '../lib/types';
import { getCleanTitle, getResolution } from '../lib/utils';

interface VideoCardProps {
  video: VideoFile;
  posterUrl?: string;
  isWatched: boolean;
  progress?: number;
  onOpenInfo: (video: VideoFile) => void;
  onPlay: (video: VideoFile) => void;
  children?: React.ReactNode;
  className?: string;
  imgClassName?: string;
}

/** Carte d'affiche vidéo réutilisable (grilles et carrousels). */
export const VideoCard: React.FC<VideoCardProps> = ({
  video,
  posterUrl,
  isWatched,
  progress = 0,
  onOpenInfo,
  onPlay,
  children,
  className = '',
  imgClassName = '',
}) => {
  const resolution = getResolution(video.name);
  const title = video.isSeriesGroup ? video.seriesName : (video.seriesName || getCleanTitle(video.name));

  return (
    <div
      className={`group flex flex-col ${className}`}
      onClick={() => onOpenInfo(video)}
    >
      <div className="relative aspect-[2/3] bg-zinc-800 rounded-md overflow-hidden cursor-pointer transition-transform duration-300 group-hover:scale-105 group-hover:z-30 shadow-lg">
        {/* Badge Watched */}
        {isWatched && (
          <div className="absolute top-2 right-2 z-20 bg-green-600 rounded-full p-1 shadow-md">
            <Check className="w-2.5 h-2.5 md:w-3 md:h-3 text-white" />
          </div>
        )}

        {/* Badge Type (Série/Saga) */}
        {video.isSeriesGroup && (
          <div className="absolute top-2 left-2 z-10 bg-red-600 text-white text-[10px] font-bold px-1.5 py-0.5 rounded shadow-lg uppercase">
            {video.isTvSeries ? 'Série' : 'Saga'}
          </div>
        )}

        {/* Badge Résolution */}
        {resolution && (
          <div className="absolute bottom-2 left-2 z-10 bg-black/70 text-white text-[10px] font-black px-1.5 py-0.5 rounded border border-white/20 uppercase tracking-tighter">
            {resolution}
          </div>
        )}

        {/* Poster Image */}
        {posterUrl ? (
          <img
            src={posterUrl}
            alt={title}
            className={`w-full h-full object-cover ${imgClassName}`}
            loading="lazy"
            decoding="async"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center p-4 text-center bg-zinc-800">
            <span className="text-zinc-500 font-medium text-sm line-clamp-3">{title}</span>
          </div>
        )}

        {/* Overlay d'actions */}
        <div className="absolute inset-0 bg-black/60 opacity-0 group-hover:opacity-100 transition-opacity duration-300 flex items-center justify-center p-4">
          <button
            aria-label="Lire"
            onClick={(e) => {
              e.stopPropagation();
              onPlay(video);
            }}
            className="bg-white text-black p-3 rounded-full hover:bg-white/80 transition-colors"
          >
            <Play className="w-6 h-6 fill-black text-black" />
          </button>
          {children}
        </div>

        {/* Progress Bar */}
        {progress > 0 && progress < 100 && (
          <div className="absolute bottom-0 left-0 right-0 h-1 bg-zinc-600">
            <div className="h-full bg-red-600" style={{ width: `${progress}%` }} />
          </div>
        )}
      </div>

      {/* Titre en dessous */}
      <div className="mt-2 px-1 text-left">
        <p className="text-[10px] md:text-xs font-medium text-zinc-400 break-words line-clamp-none">
          {title}
        </p>
      </div>
    </div>
  );
};
