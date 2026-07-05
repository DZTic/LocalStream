import React, { useState, useEffect } from 'react';
import { Play, X, Check, RefreshCw, ListPlus, Clock, RotateCcw } from 'lucide-react';
import { VideoFile, Playlist, TMDB_GENRES } from '../lib/types';
import { getCleanTitle, getResolution, formatSize, formatDuration } from '../lib/utils';

interface InfoModalProps {
  video: VideoFile;
  posters: Record<string, string>;
  backdrops: Record<string, string>;
  overviews: Record<string, string>;
  videoGenres: Record<string, number[]>;
  releaseDates: Record<string, string>;
  episodeNames: Record<string, string>;
  episodeOverviews: Record<string, string>;
  episodePosters: Record<string, string>;
  watchedVideos: Record<string, boolean>;
  watchPositions: Record<string, number>;
  watchProgress: Record<string, number>;
  videoDurations: Record<string, number>;
  tmdbApiKey: string;
  isRefreshingMetadata: boolean;
  playlists: Playlist[];
  selectedSeason: number | null;
  expandedEpisode: string | null;
  onClose: () => void;
  onPlay: (video: VideoFile) => void;
  onToggleWatched: (name: string) => void;
  onResetProgress: (name: string) => void;
  onRefreshMetadata: (video: VideoFile) => void;
  onToggleVideoInPlaylist: (playlistId: string, videoName: string) => void;
  onCreatePlaylist: (name: string, initialVideos: string[]) => void;
  onSelectSeason: (season: number | null) => void;
  onExpandEpisode: (name: string | null) => void;
}

/** Fiche détail d'un film / d'une série : hero, actions, synopsis, saisons et épisodes. */
export const InfoModal: React.FC<InfoModalProps> = ({
  video: infoVideo,
  posters, backdrops, overviews, videoGenres, releaseDates,
  episodeNames, episodeOverviews, episodePosters,
  watchedVideos, watchPositions, watchProgress, videoDurations,
  tmdbApiKey, isRefreshingMetadata, playlists,
  selectedSeason, expandedEpisode,
  onClose, onPlay, onToggleWatched, onResetProgress, onRefreshMetadata,
  onToggleVideoInPlaylist, onCreatePlaylist, onSelectSeason, onExpandEpisode,
}) => {
  const [showPlaylistSelector, setShowPlaylistSelector] = useState(false);
  const [newPlaylistName, setNewPlaylistName] = useState('');
  const [isSynopsisExpanded, setIsSynopsisExpanded] = useState(false);
  const [isEpisodeSynopsisExpanded, setIsEpisodeSynopsisExpanded] = useState(false);

  useEffect(() => {
    setShowPlaylistSelector(false);
  }, [infoVideo]);

  useEffect(() => {
    // Reset expansion when changing video or episode
    setIsSynopsisExpanded(false);
    setIsEpisodeSynopsisExpanded(false);
  }, [infoVideo, expandedEpisode]);

  // Crée la playlist depuis le champ de saisie, pré-remplie avec la vidéo affichée.
  const handleCreatePlaylist = () => {
    onCreatePlaylist(newPlaylistName, infoVideo ? [infoVideo.name] : []);
    setNewPlaylistName('');
  };

  const groupKey = infoVideo.isSeriesGroup ? infoVideo.seriesName! : infoVideo.name;
  const isGroupWatched = infoVideo.isSeriesGroup
    ? (infoVideo.episodes && infoVideo.episodes.length > 0 && infoVideo.episodes.every(ep => !!watchedVideos[ep.name]))
    : !!watchedVideos[infoVideo.name];

  return (
    <div className="fixed inset-0 bg-black/95 md:bg-black/80 backdrop-blur-md z-50 flex items-center justify-center p-0 md:p-12 overflow-y-auto">
      <div className="bg-zinc-950 md:rounded-3xl w-full h-full md:h-auto md:max-h-[90vh] md:max-w-6xl overflow-hidden shadow-[0_0_100px_rgba(0,0,0,0.8)] relative animate-in fade-in zoom-in-95 duration-500 flex flex-col border border-white/5">

        {/* Header / Hero Section */}
        <div className="relative w-full shrink-0" style={{ height: 'max(48vh, 260px)' }}>
          <div className="absolute inset-0">
            {backdrops[groupKey] || posters[groupKey] ? (
              <img
                src={backdrops[groupKey] || posters[groupKey]}
                alt={infoVideo.name}
                className="w-full h-full object-cover"
              />
            ) : (
              <div className="w-full h-full bg-gradient-to-br from-zinc-900 to-black border-b border-white/5" />
            )}
            <div className="absolute inset-0 bg-gradient-to-t from-zinc-950 via-zinc-950/30 to-black/50" />
            <div className="absolute inset-0 bg-gradient-to-r from-zinc-950/90 via-transparent to-transparent hidden md:block" />
          </div>

          {/* Bouton fermer — safe-area pour eviter le status bar Android */}
          <button
            onClick={onClose}
            className="absolute right-4 z-50 p-2.5 bg-black/60 hover:bg-zinc-700 rounded-full text-white backdrop-blur-xl transition-all shadow-2xl border border-white/20 active:scale-90"
            style={{ top: 'calc(env(safe-area-inset-top, 0px) + 16px)' }}
          >
            <X className="w-6 h-6" />
          </button>

          {/* Titre + boutons d'action */}
          <div className="absolute bottom-5 md:bottom-12 left-4 md:left-12 right-4 md:right-12 z-10">
            <div className="flex flex-col gap-3 md:gap-7">
              <div>
                {infoVideo.isSeriesGroup && (
                  <div className="flex items-center gap-3 mb-2">
                    <span className="text-red-500 font-black text-xs md:text-sm uppercase tracking-[0.3em] drop-shadow-lg">
                      {infoVideo.isTvSeries ? 'SÉRIE ORIGINALE' : 'SAGA / COLLECTION'}
                    </span>
                    <div className="h-4 w-px bg-white/20" />
                    <span className="text-white/60 text-xs md:text-sm font-bold uppercase tracking-widest">{infoVideo.episodes?.length || 0} {infoVideo.isTvSeries ? 'Épisodes' : 'Films'}</span>
                  </div>
                )}
                <h2 className="text-3xl md:text-8xl font-black text-white drop-shadow-[0_4px_20px_rgba(0,0,0,0.9)] leading-tight max-w-5xl tracking-tighter">
                  {infoVideo.isSeriesGroup ? infoVideo.seriesName : getCleanTitle(infoVideo.name)}
                </h2>
              </div>

              <div className="flex flex-wrap items-center gap-2 md:gap-4">
                <button
                  onClick={() => onPlay(infoVideo)}
                  className="bg-white text-black px-6 md:px-10 py-2.5 md:py-4 rounded-xl flex items-center justify-center gap-2 font-black text-sm md:text-xl hover:bg-zinc-200 active:scale-95 transition-all shadow-xl group flex-1 md:flex-none"
                >
                  <Play className="w-5 h-5 md:w-7 md:h-7 fill-black group-hover:scale-110 transition-transform" />
                  {(() => {
                    const target = (infoVideo.isSeriesGroup && infoVideo.episodes)
                      ? (infoVideo.episodes.find(ep => !watchedVideos[ep.name]) || infoVideo.episodes[0])
                      : infoVideo;
                    const pos = watchPositions[target.name] || 0;
                    const isTargetWatched = !!watchedVideos[target.name];
                    return (pos > 0 && !isTargetWatched) ? `REPRENDRE À ${formatDuration(pos / 1000)}` : 'LECTURE';
                  })()}
                </button>

                <button
                  onClick={() => onToggleWatched(groupKey)}
                  className={`px-4 md:px-8 py-2.5 md:py-4 rounded-xl flex items-center justify-center gap-2 font-black text-sm md:text-xl transition-all border shadow-lg flex-1 md:flex-none active:scale-95 ${
                    isGroupWatched ? 'bg-green-600 border-green-400 text-white shadow-green-900/20' : 'bg-white/5 border-white/10 text-white hover:bg-white/20 hover:border-white/30'
                  }`}
                >
                  <Check className={`w-5 h-5 md:w-7 md:h-7 ${isGroupWatched ? 'stroke-[4px]' : 'opacity-40'}`} />
                  {isGroupWatched ? 'VU' : 'MARQUER VU'}
                </button>

                {/* Bouton recharger TMDB — visible avec label */}
                <button
                  onClick={() => onRefreshMetadata(infoVideo)}
                  disabled={isRefreshingMetadata}
                  className="px-4 py-2.5 md:py-4 bg-white/5 border border-white/10 rounded-xl text-white hover:bg-white/20 active:scale-95 transition-all shadow-xl flex items-center gap-2 font-bold text-sm disabled:opacity-50"
                  title="Recharger les données TMDB"
                >
                  <RefreshCw className={`w-4 h-4 ${isRefreshingMetadata ? 'animate-spin text-red-400' : 'text-zinc-300'}`} />
                  <span className="hidden sm:inline">{isRefreshingMetadata ? 'Chargement…' : 'TMDB'}</span>
                </button>

                <div className="relative flex-1 md:flex-none">
                  <button
                    onClick={() => setShowPlaylistSelector(!showPlaylistSelector)}
                    className="px-4 py-2.5 md:py-4 bg-white/5 border border-white/10 rounded-xl text-white hover:bg-white/20 active:scale-95 transition-all shadow-xl flex items-center justify-center gap-2 font-bold text-sm"
                    title="Ajouter à une liste de lecture"
                  >
                    <ListPlus className="w-4 h-4 text-zinc-300" />
                    <span className="hidden sm:inline">MA LISTE</span>
                  </button>
                  {showPlaylistSelector && (
                    <div className="absolute bottom-full mb-4 right-0 w-[min(20rem,calc(100vw_-_2rem))] md:w-80 bg-zinc-900/90 border border-white/10 rounded-2xl shadow-[0_20px_50px_rgba(0,0,0,0.5)] z-50 overflow-hidden backdrop-blur-2xl animate-in fade-in slide-in-from-bottom-6 duration-500">
                      <div className="p-5 bg-gradient-to-b from-zinc-800/50 to-transparent border-b border-white/5">
                        <h4 className="text-xs font-black text-white/40 uppercase tracking-[0.32em] mb-4">Mes Listes</h4>
                        <div className="max-h-64 overflow-y-auto space-y-2 pr-1 custom-scrollbar">
                          {playlists.map(playlist => {
                            const isInPlaylist = playlist.videoNames.includes(infoVideo.name);
                            return (
                              <button
                                key={playlist.id}
                                onClick={() => onToggleVideoInPlaylist(playlist.id, infoVideo.name)}
                                className="w-full text-left px-4 py-3 text-sm font-bold rounded-xl hover:bg-white/10 flex items-center justify-between group transition-all"
                              >
                                <span className={isInPlaylist ? "text-red-500" : "text-zinc-400 group-hover:text-white"}>{playlist.name}</span>
                                {isInPlaylist && <Check className="w-4 h-4 text-red-600 animate-in zoom-in" />}
                              </button>
                            );
                          })}
                        </div>
                      </div>
                      <div className="p-5 bg-black/40">
                        <div className="flex gap-2">
                          <input
                            type="text"
                            value={newPlaylistName}
                            onChange={(e) => setNewPlaylistName(e.target.value)}
                            placeholder="Nouvelle liste..."
                            className="flex-1 bg-zinc-900 border border-white/5 rounded-xl px-4 py-3 text-sm text-white focus:border-red-600 focus:outline-none transition-all placeholder:text-zinc-600"
                            onKeyDown={(e) => e.key === 'Enter' && handleCreatePlaylist()}
                          />
                          <button
                            onClick={handleCreatePlaylist}
                            disabled={!newPlaylistName.trim()}
                            className="bg-red-600 hover:bg-red-700 disabled:opacity-30 text-white px-4 py-3 rounded-xl text-xs font-black transition-all active:scale-90 shadow-lg shadow-red-900/20"
                          >
                            CRÉER
                          </button>
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="p-6 md:p-12 overflow-y-auto custom-scrollbar flex-1 bg-gradient-to-b from-transparent to-black/40">
          <div className="flex flex-col md:flex-row gap-8 md:gap-16">
            <div className="flex-1 space-y-6">
              <div className="space-y-4">
                <p className={`text-zinc-300 text-base md:text-xl leading-relaxed font-medium transition-all duration-500 origin-top overflow-hidden ${!isSynopsisExpanded ? 'line-clamp-3' : ''}`}>
                  {overviews[groupKey] ||
                    (!tmdbApiKey ? "Veuillez configurer votre clé API TMDB dans les paramètres pour voir les résumés." : "Synopsis non disponible sur TMDB.")
                  }
                </p>
                {overviews[groupKey] && overviews[groupKey].length > 150 && (
                  <button
                    onClick={() => setIsSynopsisExpanded(!isSynopsisExpanded)}
                    className="text-red-500 font-black text-[10px] md:text-xs uppercase tracking-[0.2em] border-b border-red-600/30 pb-0.5 hover:text-white hover:border-white transition-all"
                  >
                    {isSynopsisExpanded ? '↑ RÉDUIRE' : '↓ LIRE LA SUITE'}
                  </button>
                )}
              </div>

              {videoGenres[groupKey] && (
                <div className="flex flex-wrap gap-2">
                  {videoGenres[groupKey].map(id => (
                    <span key={id} className="px-3 py-1 bg-white/5 border border-white/10 rounded-full text-[10px] md:text-xs font-black text-zinc-400 uppercase tracking-widest">{TMDB_GENRES[id]}</span>
                  ))}
                </div>
              )}
            </div>

            <div className="w-full md:w-80 space-y-5">
              <div className="grid grid-cols-1 gap-4">
                <div className="bg-white/5 p-4 rounded-2xl border border-white/5">
                  <p className="text-[10px] text-zinc-500 uppercase font-black tracking-widest mb-1.5 opacity-50">Qualité Maximale</p>
                  <p className="text-sm text-white font-black">{getResolution(infoVideo.isSeriesGroup && infoVideo.episodes ? infoVideo.episodes[0].name : infoVideo.name) || 'HD'}</p>
                </div>
                <div className="bg-white/5 p-4 rounded-2xl border border-white/5">
                  <p className="text-[10px] text-zinc-500 uppercase font-black tracking-widest mb-1.5 opacity-50">Format et Type</p>
                  <p className="text-sm text-white font-black uppercase">{infoVideo.isSeriesGroup ? 'Série TV' : 'Film'} • {infoVideo.type || 'Fichier'}</p>
                </div>
                <div className="bg-white/5 p-4 rounded-2xl border border-white/5">
                  <p className="text-[10px] text-zinc-500 uppercase font-black tracking-widest mb-1.5 opacity-50">Taille Totale</p>
                  <p className="text-sm text-white font-black">
                    {infoVideo.isSeriesGroup && infoVideo.episodes
                      ? formatSize(infoVideo.episodes.reduce((sum, ep) => sum + (ep.file?.size || ep.size || 0), 0))
                      : formatSize(infoVideo.file?.size || infoVideo.size || 0)}
                  </p>
                </div>
                {releaseDates[groupKey] && (
                  <div className="bg-white/5 p-4 rounded-2xl border border-white/5">
                    <p className="text-[10px] text-zinc-500 uppercase font-black tracking-widest mb-1.5 opacity-50">Sortie</p>
                    <p className="text-sm text-white font-black">{new Date(releaseDates[groupKey]).getFullYear()}</p>
                  </div>
                )}
              </div>
            </div>
          </div>

          {/* Episodes List Section */}
          {infoVideo.isSeriesGroup && infoVideo.episodes && (
            <div className="mt-16 border-t border-white/10 pt-12 last:pb-12">
              <div className="flex flex-col md:flex-row md:items-center justify-between mb-8 gap-4">
                <div className="space-y-1">
                  <h3 className="text-2xl md:text-4xl font-black text-white uppercase tracking-tighter italic">Épisodes</h3>
                  <p className="text-zinc-500 text-[10px] md:text-xs font-black uppercase tracking-[0.3em]">{infoVideo.episodes.length} TOTAL</p>
                </div>

                {(() => {
                  const seasons = Array.from(new Set(infoVideo.episodes.map(ep => ep.season || 1))).sort((a: number, b: number) => a - b);
                  if (seasons.length <= 1) return null;
                  return (
                    <div className="flex flex-wrap gap-2 bg-white/5 p-1.5 rounded-[1.5rem] border border-white/5 shadow-inner">
                      {seasons.map(s => (
                        <button
                          key={s}
                          onClick={() => {
                            onSelectSeason(s);
                            onExpandEpisode(null);
                          }}
                          className={`px-6 py-2.5 rounded-xl text-[10px] md:text-xs font-black transition-all uppercase tracking-[0.2em] ${selectedSeason === s ? 'bg-red-600 text-white shadow-lg shadow-red-900/20' : 'text-zinc-500 hover:text-white hover:bg-white/5'}`}
                        >
                          Saison {s}
                        </button>
                      ))}
                    </div>
                  );
                })()}
              </div>

              <div className="grid grid-cols-1 gap-3 md:gap-4">
                {infoVideo.episodes
                  .filter(ep => !selectedSeason || (ep.season || 1) === selectedSeason)
                  .map((ep, idx) => {
                  const isWatched = !!watchedVideos[ep.name];
                  const currentPos = watchPositions[ep.name] || 0;
                  const res = getResolution(ep.name);
                  const isExpanded = expandedEpisode === ep.name;
                  return (
                    <div
                      key={idx}
                      className={`flex flex-col p-2 md:p-4 bg-zinc-900/40 hover:bg-zinc-900/80 rounded-[1.5rem] transition-all duration-500 group border border-white/5 hover:border-white/10 shadow-xl relative overflow-hidden ${isExpanded ? 'bg-zinc-900 border-red-600/20' : ''}`}
                    >
                      <div className="flex items-center gap-4 md:gap-6">
                        <div className="absolute left-0 top-0 bottom-0 w-1.5 bg-red-600 scale-y-0 group-hover:scale-y-100 transition-transform origin-top duration-500" />

                        <div
                          className="w-20 h-20 md:w-44 md:h-24 rounded-2xl bg-zinc-950 flex items-center justify-center shrink-0 relative overflow-hidden border border-white/5 shadow-inner group/thumb"
                        >
                          {backdrops[infoVideo.seriesName!] ? (
                            <img src={backdrops[infoVideo.seriesName!]} className="w-full h-full object-cover opacity-30 group-hover:opacity-60 transition-all duration-700 group-hover:scale-110" loading="lazy" />
                          ) : (
                            <div className="w-full h-full bg-gradient-to-br from-zinc-800 to-zinc-950" />
                          )}

                          <div className="absolute inset-0 flex items-center justify-center">
                            <button
                              onClick={(e) => { e.stopPropagation(); onPlay(ep); }}
                              className="p-3 md:p-5 bg-white text-black rounded-full shadow-2xl scale-75 md:scale-90 group-hover:scale-110 opacity-0 group-hover:opacity-100 transition-all duration-500 transform active:scale-95"
                            >
                              <Play className="w-5 h-5 md:w-7 md:h-7 fill-black translate-x-0.5" />
                            </button>
                          </div>

                          <div className="absolute top-1.5 left-1.5 px-2 py-0.5 bg-black/60 backdrop-blur-md rounded-full text-[9px] font-black text-white/40 ring-1 ring-white/5">
                            {idx + 1}
                          </div>

                          {isWatched && (
                            <div className="absolute top-1.5 right-1.5 bg-green-500 text-white rounded-full p-1 shadow-2xl ring-2 ring-zinc-950">
                              <Check className="w-3 h-3 stroke-[4]" />
                            </div>
                          )}

                          <div className="absolute bottom-0 left-0 right-0 h-1.5 bg-black/40">
                            {currentPos > 0 && !isWatched && (
                              <div className="h-full bg-red-600 shadow-[0_0_10px_rgba(220,38,38,1)]" style={{ width: `${Math.floor((currentPos / (videoDurations[ep.name] || 1)) * 100)}%` }} />
                            )}
                          </div>
                        </div>

                        <div className="min-w-0 flex-1 cursor-pointer py-1" onClick={() => onExpandEpisode(isExpanded ? null : ep.name)}>
                          <div className="flex flex-col gap-1">
                            <div className="flex items-center gap-2">
                              <h4 className="font-black text-white text-sm md:text-xl truncate tracking-tight group-hover:text-red-500 transition-colors">
                                {ep.season && ep.episode ? (
                                  <>
                                    <span className="text-red-500 mr-2">S{ep.season.toString().padStart(2, '0')} E{ep.episode.toString().padStart(2, '0')}</span>
                                    {episodeNames[`${infoVideo.seriesName}_s${ep.season}_e${ep.episode}`] || getCleanTitle(ep.name)}
                                  </>
                                ) : getCleanTitle(ep.name)}
                              </h4>
                              {isWatched && <span className="text-[9px] bg-green-500 text-white px-2 py-0.5 rounded-full font-black tracking-widest shadow-lg animate-in zoom-in ring-1 ring-white/10 uppercase">VU</span>}
                            </div>
                            <p className="text-[9px] md:text-[10px] text-white/20 font-black truncate max-w-[180px] md:max-w-xl uppercase tracking-[0.15em]">
                              {ep.name}
                            </p>
                          </div>

                          <div className="flex flex-wrap items-center gap-x-4 gap-y-2 mt-4">
                            {res && (
                              <span className="text-[9px] bg-white/5 text-zinc-300 px-2.5 py-0.5 rounded-md font-black border border-white/10 tracking-widest">{res}</span>
                            )}
                            <div className="flex items-center gap-1.5">
                              <div className="w-1 h-1 rounded-full bg-red-600 shadow-[0_0_3px_rgba(220,38,38,1)]" />
                              <span className="text-[10px] text-zinc-400 font-black tracking-widest uppercase">{formatSize(ep.file?.size || ep.size || 0)}</span>
                            </div>
                            {videoDurations[ep.name] && (
                              <div className="flex items-center gap-3">
                                {currentPos > 0 && (
                                  <button
                                    onClick={(e) => { e.stopPropagation(); onResetProgress(ep.name); }}
                                    className="p-1 px-2 bg-zinc-800/50 hover:bg-zinc-700 text-[9px] text-zinc-400 font-black rounded-lg transition-all flex items-center gap-1.5"
                                    title="Remettre à zéro"
                                  >
                                    <RotateCcw className="w-2.5 h-2.5" /> REINITIALISER
                                  </button>
                                )}
                                <span className="text-[10px] text-zinc-400 font-black tracking-tight">{formatDuration(videoDurations[ep.name])}</span>
                              </div>
                            )}
                            {currentPos > 0 && !isWatched && (
                              <span className="text-[10px] text-red-500 font-black tracking-widest uppercase">
                                Reprendre à {formatDuration(currentPos / 1000)}
                              </span>
                            )}
                          </div>
                        </div>

                        <div className="flex flex-col gap-2 justify-center shrink-0 pr-1">
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              onToggleWatched(ep.name);
                            }}
                            className={`group/btn p-3 md:p-5 rounded-[1.2rem] border transition-all flex flex-col items-center gap-1 min-w-[65px] md:min-w-[90px] shadow-lg active:scale-90 ${isWatched ? 'bg-green-600 border-green-400 text-white shadow-green-900/20' : 'bg-white/5 border-white/5 text-zinc-600 hover:bg-white/10 hover:border-white/20 hover:text-white'}`}
                          >
                            <Check className={`w-5 h-5 md:w-8 md:h-8 transition-all duration-300 ${isWatched ? 'stroke-[4px] scale-110 drop-shadow-md' : 'opacity-20 group-hover/btn:opacity-100 group-hover/btn:scale-110'}`} />
                            <span className="text-[8px] md:text-[9px] font-black uppercase tracking-[0.15em] leading-none whitespace-nowrap">{isWatched ? 'VU' : 'À VOIR'}</span>
                          </button>
                        </div>
                      </div>

                      {isExpanded && (
                        <div className="mt-8 transition-all animate-in fade-in slide-in-from-top-4 duration-700">
                          <div className="flex flex-col lg:flex-row gap-8 lg:items-start">
                            {/* Extended info for episode */}
                            <div className="flex-1 space-y-6">
                              <div className="flex flex-wrap items-center gap-4">
                                <div className="px-3 py-1.5 bg-red-600/10 border border-red-600/20 rounded-lg">
                                  <span className="text-red-500 text-[10px] font-black uppercase tracking-[0.2em]">{infoVideo.isTvSeries ? 'ÉPISODE' : 'FILM'} {idx + 1}</span>
                                </div>
                                <div className="flex items-center gap-2 text-zinc-500">
                                  <Clock className="w-4 h-4" />
                                  <span className="text-xs font-bold">{videoDurations[ep.name] ? formatDuration(videoDurations[ep.name]) : 'Durée inconnue'}</span>
                                </div>
                                {res && (
                                  <div className="px-2 py-1 bg-white/5 border border-white/10 rounded text-zinc-400 text-[10px] font-black">{res}</div>
                                )}
                              </div>

                              <div className="space-y-4">
                                <h5 className="text-white/60 text-[10px] font-black uppercase tracking-[0.3em]">Synopsis {infoVideo.isTvSeries ? "de l'épisode" : "du film"}</h5>
                                <p className={`text-zinc-300 text-sm md:text-base leading-relaxed font-medium transition-all duration-500 overflow-hidden ${!isEpisodeSynopsisExpanded ? 'line-clamp-3' : ''}`}>
                                  {episodeOverviews[`${infoVideo.seriesName}_s${ep.season ?? 1}_e${ep.episode}`] || "(Synopsis non disponible ou en cours de chargement...)"}
                                </p>
                                {episodeOverviews[`${infoVideo.seriesName}_s${ep.season ?? 1}_e${ep.episode}`] && episodeOverviews[`${infoVideo.seriesName}_s${ep.season ?? 1}_e${ep.episode}`].length > 100 && (
                                  <button
                                    onClick={() => setIsEpisodeSynopsisExpanded(!isEpisodeSynopsisExpanded)}
                                    className="text-red-500 font-black text-[9px] uppercase tracking-[0.2em] transition-all hover:text-white"
                                  >
                                    {isEpisodeSynopsisExpanded ? '↑ RÉDUIRE' : '↓ LIRE LA SUITE'}
                                  </button>
                                )}
                              </div>

                              <div className="pt-4 flex flex-wrap gap-4">
                                <button
                                  onClick={() => onPlay(ep)}
                                  className="bg-white text-black px-8 py-3 rounded-xl font-black text-xs uppercase tracking-widest hover:bg-zinc-200 transition-all flex items-center gap-2 shadow-xl"
                                >
                                  <Play className="w-4 h-4 fill-black" /> Regarder
                                </button>
                                <button
                                  onClick={() => onToggleWatched(ep.name)}
                                  className={`px-8 py-3 rounded-xl font-black text-xs uppercase tracking-widest transition-all border ${isWatched ? 'bg-green-600 border-green-400 text-white shadow-green-900/20' : 'bg-white/5 border-white/10 text-zinc-400 hover:bg-white/10 hover:text-white'}`}
                                >
                                  {isWatched ? 'VU' : 'Marquer vu'}
                                </button>
                              </div>
                            </div>

                            {/* Poster side for large screens */}
                            {episodePosters[`${infoVideo.seriesName}_s${ep.season || 1}_e${ep.episode}`] && (
                              <div className="hidden lg:block w-[300px] h-[170px] rounded-2xl overflow-hidden border border-white/10 shadow-2xl shrink-0 group-hover:scale-[1.02] transition-transform duration-700 relative">
                                <img
                                  src={episodePosters[`${infoVideo.seriesName}_s${ep.season || 1}_e${ep.episode}`]}
                                  className="w-full h-full object-cover"
                                  loading="lazy"
                                />
                                <div className="absolute inset-0 bg-gradient-to-t from-black/60 to-transparent" />
                              </div>
                            )}
                          </div>

                          <div className="mt-8 pt-6 border-t border-white/5">
                            <div className="bg-black/30 p-4 rounded-[1.5rem] border border-white/5 shadow-inner">
                              <h5 className="text-[9px] text-white/30 uppercase font-black mb-2 tracking-[0.2em]">MÉTADONNÉES TECHNIQUES</h5>
                              <div className="flex flex-col gap-0.5">
                                <p className="text-[8px] text-zinc-500 font-bold uppercase">Nom du fichier</p>
                                <p className="text-xs text-zinc-300 font-mono break-all leading-relaxed select-all">
                                  {ep.name}
                                </p>
                              </div>
                            </div>
                          </div>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
