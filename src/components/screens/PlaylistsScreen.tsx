import React from 'react';
import { ChevronLeft, Play, Trash2, ListVideo, X } from 'lucide-react';
import { VideoFile, Playlist } from '../../lib/types';
import { VideoCard } from '../VideoCard';

interface PlaylistsScreenProps {
  playlists: Playlist[];
  selectedPlaylist: Playlist | null;
  groupedVideos: VideoFile[];
  posters: Record<string, string>;
  watchProgress: Record<string, number>;
  watchedVideos: Record<string, boolean>;
  onSelectPlaylist: (playlist: Playlist | null) => void;
  onOpenInfo: (video: VideoFile) => void;
  onPlay: (video: VideoFile, playlist?: Playlist, index?: number) => void;
  onDeletePlaylist: (id: string) => void;
  onRemoveVideo: (playlistId: string, videoName: string) => void;
}

/** Écran "Listes de lecture" : grille des playlists puis détail d'une playlist. */
export const PlaylistsScreen: React.FC<PlaylistsScreenProps> = ({
  playlists, selectedPlaylist, groupedVideos, posters, watchProgress, watchedVideos,
  onSelectPlaylist, onOpenInfo, onPlay, onDeletePlaylist, onRemoveVideo,
}) => (
  <div className="px-4 md:px-12 min-h-screen pt-20">
    {selectedPlaylist ? (
      <div>
        <div className="flex items-center gap-4 mb-8">
          <button onClick={() => onSelectPlaylist(null)} className="p-2 bg-zinc-800 rounded-full hover:bg-zinc-700 transition">
            <ChevronLeft className="w-6 h-6" />
          </button>
          <h2 className="text-2xl md:text-3xl font-bold text-white">{selectedPlaylist.name}</h2>
          <div className="ml-auto flex items-center gap-2 md:gap-4">
            {selectedPlaylist.videoNames.length > 0 && (
              <button
                onClick={() => {
                  const firstVideo = groupedVideos.find(v => v.name === selectedPlaylist.videoNames[0]);
                  if (firstVideo) onPlay(firstVideo, selectedPlaylist, 0);
                }}
                className="p-2 md:px-4 md:py-2 bg-white text-black rounded hover:bg-white/80 transition flex items-center gap-2 font-bold"
              >
                <Play className="w-5 h-5 fill-black" />
                <span className="hidden md:inline">Tout lire</span>
              </button>
            )}
            <button onClick={() => onDeletePlaylist(selectedPlaylist.id)} aria-label="Supprimer la liste" className="p-2 bg-red-600/20 text-red-500 rounded hover:bg-red-600/40 transition flex items-center gap-2">
              <Trash2 className="w-5 h-5" />
              <span className="hidden md:inline">Supprimer la liste</span>
            </button>
          </div>
        </div>
        {selectedPlaylist.videoNames.length > 0 ? (
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
            {selectedPlaylist.videoNames.map((videoName, index) => {
              const video = groupedVideos.find(v => v.name === videoName);
              if (!video) return null;

              return (
                <VideoCard
                  key={videoName}
                  video={video}
                  posterUrl={posters[video.name]}
                  isWatched={!!watchedVideos[video.name]}
                  progress={watchProgress[video.name]}
                  onOpenInfo={onOpenInfo}
                  onPlay={() => onPlay(video, selectedPlaylist, index)}
                >
                  <button
                    onClick={(e) => { e.stopPropagation(); onRemoveVideo(selectedPlaylist.id, video.name); }}
                    aria-label="Retirer de la liste"
                    className="absolute top-2 right-2 p-2 bg-red-600/80 rounded-full hover:bg-red-600 transition z-40"
                  >
                    <X className="w-3.5 h-3.5 text-white" />
                  </button>
                </VideoCard>
              );
            })}
          </div>
        ) : (
          <div className="text-center text-zinc-500 mt-12">
            Cette liste de lecture est vide.
          </div>
        )}
      </div>
    ) : (
      <div>
        <h2 className="text-2xl md:text-3xl font-bold text-white mb-8">Vos listes de lecture</h2>
        {playlists.length > 0 ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
            {playlists.map(playlist => (
              <div
                key={playlist.id}
                onClick={() => onSelectPlaylist(playlist)}
                className="bg-zinc-900 border border-zinc-800 p-6 rounded-lg cursor-pointer hover:border-zinc-600 transition group"
              >
                <div className="flex items-center justify-between mb-4">
                  <div className="p-3 bg-zinc-800 rounded-full group-hover:bg-zinc-700 transition">
                    <ListVideo className="w-8 h-8 text-white" />
                  </div>
                  <span className="text-xs font-medium bg-zinc-800 px-2 py-1 rounded text-zinc-300">
                    {playlist.videoNames.length} vidéo{playlist.videoNames.length !== 1 ? 's' : ''}
                  </span>
                </div>
                <h3 className="text-lg font-bold text-white truncate">{playlist.name}</h3>
              </div>
            ))}
          </div>
        ) : (
          <div className="text-center text-zinc-500 mt-12">
            Vous n'avez pas encore créé de liste de lecture.
          </div>
        )}
      </div>
    )}
  </div>
);
