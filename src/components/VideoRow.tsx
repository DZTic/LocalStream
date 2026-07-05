import React from 'react';
import { Info, RotateCcw } from 'lucide-react';
import { VideoFile } from '../lib/types';
import { VideoCard } from './VideoCard';

export interface VideoRowProps {
  title: string;
  items: VideoFile[];
  posters: Record<string, string>;
  watchProgress: Record<string, number>;
  watchedVideos: Record<string, boolean>;
  onOpenInfo: (video: VideoFile) => void;
  onPlay: (video: VideoFile) => void;
  onResetProgress: (name: string) => void;
}

/**
 * Carrousel horizontal d'affiches (une "ligne" de la page d'accueil).
 * Composant présentationnel pur, mémoïsé : ne se re-rend que si ses props changent.
 */
const VideoRowComponent: React.FC<VideoRowProps> = ({
  title, items, posters, watchProgress, watchedVideos, onOpenInfo, onPlay, onResetProgress,
}) => {
  if (items.length === 0) return null;
  return (
    <div className="mb-8">
      <h2 className="text-lg md:text-2xl font-bold text-white mb-2 md:mb-4 px-4 md:px-12">{title}</h2>
      <div className="flex gap-2 md:gap-3 overflow-x-auto px-4 md:px-12 pb-8 pt-2 scrollbar-hide snap-x">
        {items.map((video) => {
          const isWatched = video.isSeriesGroup
            ? (video.episodes && video.episodes.length > 0 && video.episodes.every(ep => !!watchedVideos[ep.name]))
            : !!watchedVideos[video.name];
          // Pour un groupe (série/saga), la progression est stockée par épisode :
          // on affiche celle du premier épisode en cours.
          const progress = video.isSeriesGroup && video.episodes
            ? (video.episodes.map(ep => watchProgress[ep.name] || 0).find(p => p > 0 && p < 100) || 0)
            : (watchProgress[video.name] || 0);

          const posterKey = video.isSeriesGroup ? video.seriesName! : (video.seriesName || video.name);

          return (
            <VideoCard
              key={video.nativeUri || video.path || video.name}
              video={video}
              posterUrl={posters[posterKey]}
              isWatched={isWatched}
              progress={progress}
              onOpenInfo={onOpenInfo}
              onPlay={onPlay}
              className="flex-none w-28 md:w-48 snap-start"
            >
              {/* Bouton Info additionnel dans l'overlay */}
              <button
                aria-label="Plus d'infos"
                onClick={(e) => { e.stopPropagation(); onOpenInfo(video); }}
                className="opacity-0 group-hover:opacity-100 transition-opacity bg-zinc-800/80 text-white p-3 rounded-full hover:bg-zinc-700/80 border border-white/20 ml-2 shadow-lg"
              >
                <Info className="w-6 h-6" />
              </button>

              {/* Bouton Reset Progress dans l'overlay */}
              {title === "Continuer la lecture" && watchProgress[video.name] > 0 && (
                <button
                  onClick={(e) => { e.stopPropagation(); onResetProgress(video.name); }}
                  className="absolute bottom-2 right-2 p-2 md:p-1 bg-black/60 rounded-full text-white hover:bg-red-600 transition-colors shadow-lg z-40 opacity-100"
                  title="Reprendre à zéro"
                  aria-label="Reprendre à zéro"
                >
                  <RotateCcw className="w-4 h-4 md:w-3 md:h-3" />
                </button>
              )}
            </VideoCard>
          );
        })}
      </div>
    </div>
  );
};

export const VideoRow = React.memo(VideoRowComponent);
