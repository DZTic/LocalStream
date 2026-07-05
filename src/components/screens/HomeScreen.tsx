import React from 'react';
import { Play, Info } from 'lucide-react';
import { VideoFile, Playlist } from '../../lib/types';
import { getCleanTitle } from '../../lib/utils';
import { VideoRow } from '../VideoRow';

interface HomeScreenProps {
  heroVideo: VideoFile | undefined;
  backdrops: Record<string, string>;
  posters: Record<string, string>;
  overviews: Record<string, string>;
  inProgressVideos: VideoFile[];
  recentAdditions: VideoFile[];
  recommendations: VideoFile[];
  tvShows: VideoFile[];
  movies: VideoFile[];
  folders: Record<string, VideoFile[]>;
  folderNames: string[];
  alphabetical: VideoFile[];
  watchProgress: Record<string, number>;
  watchedVideos: Record<string, boolean>;
  onOpenInfo: (video: VideoFile) => void;
  onPlay: (video: VideoFile, playlist?: Playlist, index?: number) => void;
  onResetProgress: (name: string) => void;
}

const SYSTEM_FOLDERS = ['Movies', 'Download', 'Downloads', 'Documents', 'Racine'];

/** Page d'accueil : section Hero + carrousels thématiques. */
export const HomeScreen: React.FC<HomeScreenProps> = ({
  heroVideo, backdrops, posters, overviews,
  inProgressVideos, recentAdditions, recommendations, tvShows, movies, folders, folderNames, alphabetical,
  watchProgress, watchedVideos, onOpenInfo, onPlay, onResetProgress,
}) => {
  // Props partagées par toutes les lignes de carrousel (cf. components/VideoRow)
  const rowProps = {
    posters,
    watchProgress,
    watchedVideos,
    onOpenInfo,
    onPlay,
    onResetProgress,
  };

  return (
    <>
      {/* Hero Section */}
      {heroVideo && (
        <div className="relative h-[60vh] md:h-[80vh] w-full mb-8">
          <div className="absolute inset-0">
            {backdrops[heroVideo.name] || posters[heroVideo.name] ? (
              <img
                src={backdrops[heroVideo.name] || posters[heroVideo.name]}
                alt={heroVideo.name}
                className="w-full h-full object-cover"
              />
            ) : (
              <div className="w-full h-full bg-zinc-800" />
            )}
            <div className="absolute inset-0 bg-gradient-to-r from-black via-black/60 to-transparent" />
            <div className="absolute inset-0 bg-gradient-to-t from-black via-transparent to-transparent" />
          </div>

          <div className="absolute bottom-[5%] md:bottom-[20%] left-4 md:left-12 right-4 md:right-auto max-w-2xl z-10">
            <h2 className="text-3xl md:text-6xl font-bold text-white mb-2 md:mb-4 drop-shadow-xl">
              {getCleanTitle(heroVideo.name)}
            </h2>
            {overviews[heroVideo.isSeriesGroup ? heroVideo.seriesName! : (heroVideo.seriesName || heroVideo.name)] && (
              <p className="text-zinc-300 text-sm md:text-lg mb-4 md:mb-6 line-clamp-2 md:line-clamp-3 max-w-xl drop-shadow-md">
                {overviews[heroVideo.isSeriesGroup ? heroVideo.seriesName! : (heroVideo.seriesName || heroVideo.name)]}
              </p>
            )}
            <div className="flex flex-wrap gap-2 md:gap-3">
              <button
                onClick={() => onPlay(heroVideo)}
                className="bg-white text-black px-4 md:px-8 py-2 md:py-3 rounded flex items-center justify-center gap-2 font-bold text-sm md:text-lg hover:bg-white/80 transition flex-1 md:flex-none"
              >
                <Play className="w-5 h-5 md:w-6 md:h-6 fill-black" /> Lecture
              </button>
              <button
                onClick={() => onOpenInfo(heroVideo)}
                className="bg-zinc-500/70 text-white px-4 md:px-8 py-2 md:py-3 rounded flex items-center justify-center gap-2 font-bold text-sm md:text-lg hover:bg-zinc-500/90 transition flex-1 md:flex-none"
              >
                <Info className="w-5 h-5 md:w-6 md:h-6" /> Plus d'infos
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Rows */}
      <div className="relative z-20 -mt-12 md:-mt-24">
        {inProgressVideos.length > 0 && (
          <VideoRow title="Continuer la lecture" items={inProgressVideos} {...rowProps} />
        )}
        <VideoRow title="Nouveautés" items={recentAdditions.slice(0, 15)} {...rowProps} />
        <VideoRow title="Recommandations" items={recommendations.slice(0, 15)} {...rowProps} />
        <VideoRow title="Séries" items={tvShows} {...rowProps} />
        <VideoRow title="Films" items={movies} {...rowProps} />

        {/* Dossiers */}
        {folderNames.map(folderName => {
          if (SYSTEM_FOLDERS.includes(folderName)) return null;

          return folders[folderName].length > 0 && (
            <VideoRow key={folderName} title={`Dossier : ${folderName}`} items={folders[folderName]} {...rowProps} />
          );
        })}

        <VideoRow title="De A à Z" items={alphabetical} {...rowProps} />
      </div>
    </>
  );
};
