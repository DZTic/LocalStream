import React, { useState, useRef, useEffect, useMemo, useCallback } from 'react';
import { FolderOpen, Search, RefreshCw, Info } from 'lucide-react';
import { Capacitor } from '@capacitor/core';
import { App as CapacitorApp } from '@capacitor/app';
import { VideoFile, Playlist } from './lib/types';
import { Filesystem, Directory } from '@capacitor/filesystem';
import { safeSetItem, getCleanTitle, isPersonalVideo } from './lib/utils';
import { filterAndSortVideos } from './lib/sorting';
import { getHeroCandidates } from './lib/hero';
import { getPopular } from './lib/tmdb';
import { parseSeriesInfo } from './lib/scan';
import { useTmdbMetadata } from './hooks/useTmdbMetadata';
import { usePlaylists } from './hooks/usePlaylists';
import { useWatchedState } from './hooks/useWatchedState';
import { useScan } from './hooks/useScan';
import { useOpenSubtitles } from './hooks/useOpenSubtitles';
import { VideoLauncher } from './plugins/videoLauncher';
import { BottomNav } from './components/BottomNav';
import { PermissionGate } from './components/PermissionGate';
import { AppHeader } from './components/AppHeader';
import { WebPlayer } from './components/WebPlayer';
import { FilterBar, SortBy } from './components/FilterBar';
import { InfoModal } from './components/InfoModal';
import { SettingsModal } from './components/SettingsModal';
import { SubtitlesModal } from './components/SubtitlesModal';
import { HomeScreen } from './components/screens/HomeScreen';
import { SearchScreen } from './components/screens/SearchScreen';
import { LibraryScreen } from './components/screens/LibraryScreen';
import { PlaylistsScreen } from './components/screens/PlaylistsScreen';
import { HistoryScreen, HistoryItem } from './components/screens/HistoryScreen';
import { Toasts, Toast } from './components/Toasts';
import { ConfirmDialog, ConfirmDialogState } from './components/ConfirmDialog';

export default function App() {
  const [currentVideo, setCurrentVideo] = useState<VideoFile | null>(null);
  const [showSettings, setShowSettings] = useState(false);
  const [isScrolled, setIsScrolled] = useState(false);

  // Recherche mobile
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const searchInputRef = useRef<HTMLInputElement>(null);

  // Toasts non bloquants
  const [toasts, setToasts] = useState<Toast[]>([]);
  const showToast = useCallback((message: string, type: 'success' | 'error' | 'info' = 'info') => {
    const id = Date.now() + Math.random();
    setToasts(prev => [...prev.slice(-2), { id, message, type }]);
    setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), 4000);
  }, []);

  // Confirmation avant action destructive
  const [confirmDialog, setConfirmDialog] = useState<ConfirmDialogState | null>(null);

  // Hook scan personnalisé
  const {
    videos,
    setVideos,
    isScanning,
    diagLogs,
    addLog,
    startNativeScan,
    handleFolderSelect,
  } = useScan({ showToast });

  // Settings
  const [videoPlayer, setVideoPlayer] = useState<'internal' | 'external'>(localStorage.getItem('videoPlayer') as any || 'internal');
  
  // Vidéos personnelles manuellement incluses (whitelist)
  const [whitelistedVideos, setWhitelistedVideos] = useState<Set<string>>(() => {
    try {
      const saved = localStorage.getItem('whitelistedVideos');
      return saved ? new Set(JSON.parse(saved)) : new Set();
    } catch { return new Set(); }
  });

  const toggleWhitelist = (name: string) => {
    setWhitelistedVideos(prev => {
      const next = new Set(prev);
      if (next.has(name)) next.delete(name); else next.add(name);
      safeSetItem('whitelistedVideos', JSON.stringify([...next]));
      return next;
    });
  };

  const [tmdbApiKey, setTmdbApiKey] = useState(localStorage.getItem('tmdbApiKey') || '');
  const [videoDurations, setVideoDurations] = useState<Record<string, number>>({});

  const [sortBy, setSortBy] = useState<SortBy>('alpha');
  const [filterGenre, setFilterGenre] = useState<number | 'all'>('all');
  const [filterResolution, setFilterResolution] = useState<string | 'all'>('all');

  const [infoVideo, setInfoVideo] = useState<VideoFile | null>(null);
  const [expandedEpisode, setExpandedEpisode] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');

  const {
    playlists,
    selectedPlaylist,
    setSelectedPlaylist,
    createPlaylist,
    toggleVideoInPlaylist,
    deletePlaylist,
    removeVideoFromPlaylist,
  } = usePlaylists();
  
  const [activeTab, setActiveTab] = useState<'home' | 'playlists' | 'history'>('home');
  const [selectedSeason, setSelectedSeason] = useState<number | null>(null);

  const [newHistoryItemName, setNewHistoryItemName] = useState('');

  // Couche métadonnées TMDB
  const {
    groupedVideos,
    posters, backdrops, overviews, releaseDates, videoGenres,
    episodeOverviews, episodePosters, episodeNames,
    isFetchingMetadata, isRefreshingMetadata,
    fetchSingleMetadata,
    clearMetadataCache,
  } = useTmdbMetadata({ videos, whitelistedVideos, tmdbApiKey, addLog });
  
  const [permsNeeded, setPermsNeeded] = useState(false);
  const [externalPlayers, setExternalPlayers] = useState<{name: string, packageId: string}[]>([]);
  const [selectedExternalPlayer, setSelectedExternalPlayer] = useState<string>(localStorage.getItem('selectedExternalPlayer') || '');

  // État de visionnage
  const {
    watchedVideos,
    watchProgress, setWatchProgress,
    watchPositions, setWatchPositions,
    recentlyWatched, setRecentlyWatched,
    forceAvailableVideos,
    toggleWatched,
    resetProgress,
    toggleForceAvailable,
    addManualHistoryItem,
  } = useWatchedState(groupedVideos);

  // Hook OpenSubtitles personnalisé
  const {
    osApiKey,
    setOsApiKey,
    osUsername,
    setOsUsername,
    osPassword,
    setOsPassword,
    osToken,
    subtitles,
    setSubtitles,
    localSubtitles,
    isSearchingSubs,
    isLoggingIn,
    activeSubtitleUrl,
    setActiveSubtitleUrl,
    activeSubtitleNativePath,
    setActiveSubtitleNativePath,
    showSubtitlesModal,
    setShowSubtitlesModal,
    loginOpenSubtitles,
    searchSubtitles,
    downloadSubtitle,
    openNativeSubtitlePicker,
    handleLocalSubtitleSelection,
  } = useOpenSubtitles({ currentVideo, showToast });

  const [activePlaylist, setActivePlaylist] = useState<Playlist | null>(null);
  const [activePlaylistIndex, setActivePlaylistIndex] = useState<number>(0);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const subtitleInputRef = useRef<HTMLInputElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);

  // Bannière TMDB
  const [showTmdbBanner, setShowTmdbBanner] = useState(() => localStorage.getItem('showTmdbBanner') !== 'false');

  const handleDismissTmdbBanner = () => {
    setShowTmdbBanner(false);
    safeSetItem('showTmdbBanner', 'false');
  };

  const handleConfigureTmdb = () => {
    setShowSettings(true);
  };

  // Enregistrer les modifications de configuration
  useEffect(() => {
    safeSetItem('videoPlayer', videoPlayer);
  }, [videoPlayer]);

  // Toast en cas de stockage local saturé
  const lastStorageToastRef = useRef(0);
  useEffect(() => {
    const onStorageFull = () => {
      const now = Date.now();
      if (now - lastStorageToastRef.current < 60000) return;
      lastStorageToastRef.current = now;
      showToast("Stockage local saturé : certaines données ne sont plus sauvegardées.", 'error');
    };
    window.addEventListener('localstream:storage-full', onStorageFull);
    return () => window.removeEventListener('localstream:storage-full', onStorageFull);
  }, [showToast]);

  useEffect(() => {
    safeSetItem('tmdbApiKey', tmdbApiKey);
  }, [tmdbApiKey]);

  useEffect(() => {
    safeSetItem('selectedExternalPlayer', selectedExternalPlayer);
  }, [selectedExternalPlayer]);

  const checkGlobalPermissions = async () => {
    if (Capacitor.isNativePlatform()) {
      try {
        const stats = await Filesystem.checkPermissions();
        const { granted } = await VideoLauncher.checkStoragePermission();
        if (stats.publicStorage !== 'granted' || !granted) {
          setPermsNeeded(true);
        } else {
          setPermsNeeded(false);
        }
      } catch (e) {
        setPermsNeeded(true);
      }
    }
  };

  const handleManualRequest = async () => {
    try {
      await Filesystem.requestPermissions();
      await VideoLauncher.requestStoragePermission();
      const { granted } = await VideoLauncher.checkStoragePermission();
      const stats = await Filesystem.checkPermissions();
      if (granted && stats.publicStorage === 'granted') {
        setPermsNeeded(false);
      }
    } catch (err) {
      console.error(err);
    }
  };

  const refreshExternalPlayers = () => {
    VideoLauncher.getList()
      .then(res => setExternalPlayers(res.players))
      .catch(err => console.error("Error fetching external players", err));
  };

  // Configuration initiale
  useEffect(() => {
    checkGlobalPermissions();
    if (Capacitor.isNativePlatform()) {
      refreshExternalPlayers();
      startNativeScan(true);
    }
    window.addEventListener('focus', checkGlobalPermissions);
    return () => {
      window.removeEventListener('focus', checkGlobalPermissions);
    };
  }, [startNativeScan]);

  const [isSynopsisExpanded, setIsSynopsisExpanded] = useState(false);
  const [isEpisodeSynopsisExpanded, setIsEpisodeSynopsisExpanded] = useState(false);

  useEffect(() => {
    setIsSynopsisExpanded(false);
    setIsEpisodeSynopsisExpanded(false);
  }, [infoVideo, expandedEpisode]);

  const extractedDurationsRef = useRef<Set<string>>(new Set());

  useEffect(() => {
    let cancelled = false;

    const extractDurations = async () => {
      for (const video of videos) {
        if (cancelled) return;
        if (extractedDurationsRef.current.has(video.name)) continue;

        try {
          const duration = await new Promise<number>((resolve) => {
            const vid = document.createElement('video');
            vid.preload = 'metadata';
            vid.onloadedmetadata = () => {
              if (video.file) URL.revokeObjectURL(vid.src);
              resolve(vid.duration);
            };
            vid.onerror = () => resolve(0);
            vid.src = video.file ? URL.createObjectURL(video.file) : video.url;
          });

          if (duration > 0 && !cancelled) {
            setVideoDurations(prev => ({ ...prev, [video.name]: duration }));
          }
          extractedDurationsRef.current.add(video.name);
        } catch (e) {
          // Ignorer
        }
      }
    };

    if (videos.length > 0) {
      const timeoutId = setTimeout(extractDurations, 2000);
      return () => {
        cancelled = true;
        clearTimeout(timeoutId);
      };
    }
  }, [videos]);

  const playVideo = useCallback((video: VideoFile, playlist?: Playlist, index?: number) => {
    let videoToPlay = video;
    if (video.isSeriesGroup && video.episodes && video.episodes.length > 0) {
      const unwatchedEpisode = video.episodes.find(ep => !watchedVideos[ep.name]);
      videoToPlay = unwatchedEpisode || video.episodes[0];
    }

    if (playlist && index !== undefined) {
      setActivePlaylist(playlist);
      setActivePlaylistIndex(index);
    } else {
      setActivePlaylist(null);
      setActivePlaylistIndex(0);
    }

    setRecentlyWatched(prev => {
      const newWatched = [videoToPlay.name, ...prev.filter(name => name !== videoToPlay.name)].slice(0, 30);
      safeSetItem('recentlyWatched', JSON.stringify(newWatched));
      return newWatched;
    });

    if (Capacitor.isNativePlatform()) {
      const videoPath = videoToPlay.nativeUri ?? videoToPlay.url;
      const startPosMs = watchPositions[videoToPlay.name] || 0;
      const autoSubPath = videoToPlay.subtitleNativePath;
      const subtitleToUse = activeSubtitleNativePath || activeSubtitleUrl || autoSubPath || undefined;

      VideoLauncher.openVideo({
        path: videoPath,
        title: videoToPlay.name,
        startPosition: startPosMs,
        playerType: videoPlayer,
        packageId: selectedExternalPlayer,
        subtitlePath: subtitleToUse
      }).then((result: any) => {
        if (result) {
          if (result.position !== undefined && result.duration) {
            const pos = result.position;
            const dur = result.duration;
            const percentage = (pos / dur) * 100;

            setWatchPositions(prev => ({ ...prev, [videoToPlay.name]: pos }));
            setWatchProgress(prev => {
              const newProgress = { ...prev, [videoToPlay.name]: percentage };
              safeSetItem('watchProgress', JSON.stringify(newProgress));
              return newProgress;
            });
          }

          if (result.watched && !watchedVideos[videoToPlay.name]) {
            toggleWatched(videoToPlay.name);
          }
        }
      }).catch(err => {
        console.error("Erreur lecteur natif", err);
        showToast("Impossible de lancer la lecture : " + err, 'error');
      });
      return;
    }

    setCurrentVideo(videoToPlay);
    setInfoVideo(null);
    if (!activeSubtitleUrl && videoToPlay.subtitleUrl) {
      setActiveSubtitleUrl(videoToPlay.subtitleUrl);
    } else if (!activeSubtitleUrl) {
      setActiveSubtitleUrl(null);
    }
    setSubtitles([]);
  }, [watchedVideos, watchPositions, videoPlayer, selectedExternalPlayer, activeSubtitleNativePath, activeSubtitleUrl, toggleWatched, showToast, setRecentlyWatched, setWatchPositions, setWatchProgress, setActiveSubtitleUrl, setSubtitles]);

  const savePlaybackPosition = () => {
    if (videoRef.current && currentVideo) {
      const currentPosMs = videoRef.current.currentTime * 1000;
      setWatchPositions(prev => ({ ...prev, [currentVideo.name]: currentPosMs }));
    }
  };

  const handleOpenInfoModal = (video: VideoFile) => {
    if (!video.isSeriesGroup && (video.season !== undefined || video.seriesName)) {
      const parentGroup = groupedVideos.find(g =>
        g.isSeriesGroup && g.episodes?.some(ep => ep.name === video.name)
      );
      if (parentGroup) {
        setInfoVideo(parentGroup);
        setExpandedEpisode(video.name);
        const season = video.season || 1;
        setSelectedSeason(season);
        return;
      }
    }

    setInfoVideo(video);
    setExpandedEpisode(null);
    if (video.isSeriesGroup && video.episodes && video.episodes.length > 0) {
      const seasons = Array.from(new Set(video.episodes.map(ep => ep.season || 1))).sort((a, b) => a - b);
      setSelectedSeason(seasons[0]);
    } else {
      setSelectedSeason(null);
    }
  };

  const handleVideoEnded = () => {
    if (currentVideo) {
      setWatchProgress(prev => {
        const newProgress = { ...prev, [currentVideo.name]: 100 };
        safeSetItem('watchProgress', JSON.stringify(newProgress));
        return newProgress;
      });
      if (!watchedVideos[currentVideo.name]) {
        toggleWatched(currentVideo.name);
      }
    }

    if (activePlaylist && activePlaylistIndex < activePlaylist.videoNames.length - 1) {
      const nextIndex = activePlaylistIndex + 1;
      const nextVideoName = activePlaylist.videoNames[nextIndex];
      const nextVideo = groupedVideos.find(v => v.name === nextVideoName);
      if (nextVideo) {
        playVideo(nextVideo, activePlaylist, nextIndex);
      }
    }
  };

  const handleTimeUpdate = () => {
    if (!videoRef.current || !currentVideo) return;
    const currentTime = videoRef.current.currentTime;
    const duration = videoRef.current.duration;
    if (!duration) return;

    const percentage = (currentTime / duration) * 100;

    setWatchPositions(prev => ({ ...prev, [currentVideo.name]: currentTime * 1000 }));

    setWatchProgress(prev => {
      const prevPercentage = prev[currentVideo.name] || 0;
      if (Math.abs(percentage - prevPercentage) > 1 || percentage === 100) {
        const newProgress = { ...prev, [currentVideo.name]: percentage };
        safeSetItem('watchProgress', JSON.stringify(newProgress));
        return newProgress;
      }
      return prev;
    });
  };

  const handleLoadedMetadata = () => {
    if (videoRef.current && currentVideo) {
      const savedPosMs = watchPositions[currentVideo.name];
      if (savedPosMs && savedPosMs > 0 && (savedPosMs / (videoRef.current.duration * 1000)) < 0.95) {
        videoRef.current.currentTime = savedPosMs / 1000;
      }
    }
  };

  const handleTestTmdbKey = async () => {
    addLog("Test de la clé API...");
    try {
      const r = await getPopular(tmdbApiKey);
      if (r.ok) {
        addLog("Test API réussi !");
        showToast("Clé TMDB valide !", 'success');
      } else {
        addLog("Test API échoué - Code : " + r.status);
        showToast(`Clé TMDB invalide (code ${r.status}).`, 'error');
      }
    } catch(e: any) {
      addLog("Erreur test : " + e.message);
      showToast("Test impossible : " + e.message, 'error');
    }
  };

  const resetHomeView = () => {
    setActiveTab('home');
    setSearchQuery('');
    setSortBy('alpha');
    setFilterGenre('all');
    setFilterResolution('all');
    setSelectedPlaylist(null);
    setIsSearchOpen(false);
  };

  // Filtrage des vidéos personnelles
  const personalVideos = useMemo(
    () => videos.filter(v => isPersonalVideo(v.name, v.path || '')),
    [videos]
  );

  const filteredAndSortedVideos = useMemo(
    () => filterAndSortVideos(groupedVideos, {
      sortBy, filterGenre, filterResolution, releaseDates, videoGenres, videoDurations, watchedVideos,
    }),
    [groupedVideos, sortBy, filterGenre, filterResolution, releaseDates, videoGenres, videoDurations, watchedVideos]
  );

  const [debouncedQuery, setDebouncedQuery] = useState('');
  useEffect(() => {
    const id = setTimeout(() => setDebouncedQuery(searchQuery), 250);
    return () => clearTimeout(id);
  }, [searchQuery]);

  const searchResults = useMemo(() => debouncedQuery.trim()
    ? filteredAndSortedVideos.filter(v => v.name.toLowerCase().includes(debouncedQuery.toLowerCase()))
    : [], [debouncedQuery, filteredAndSortedVideos]);

  const historyItems: HistoryItem[] = useMemo(() => {
    const items = Object.keys(watchedVideos)
      .filter(key => watchedVideos[key])
      .filter(key => !key.match(/[sS]\d+(\s*)[eE]\d+|(\d+)(\s*)x(\d+)/i));

    return items.map(key => {
      const videoMatch = groupedVideos.find(v => v.seriesName === key || v.name === key);
      return {
        id: key,
        name: key,
        isLocal: !!videoMatch,
        isForcedAvailable: !!forceAvailableVideos[key],
        video: videoMatch
      };
    });
  }, [watchedVideos, groupedVideos, forceAvailableVideos]);

  const isLibraryViewActive = activeTab === 'home' && (sortBy !== 'alpha' || filterGenre !== 'all' || filterResolution !== 'all');

  useEffect(() => {
    if (isLibraryViewActive) {
      startNativeScan(true);
    }
  }, [isLibraryViewActive, startNativeScan]);

  useEffect(() => {
    const handleFocusScan = () => {
      if (Capacitor.isNativePlatform()) {
        startNativeScan(true);
      }
    };
    window.addEventListener('focus', handleFocusScan);
    return () => window.removeEventListener('focus', handleFocusScan);
  }, [startNativeScan]);

  const heroCandidates = useMemo(
    () => getHeroCandidates(groupedVideos, watchedVideos, watchProgress),
    [groupedVideos, watchedVideos, watchProgress]
  );

  const [heroIndex, setHeroIndex] = useState(0);
  useEffect(() => {
    if (heroCandidates.length <= 1) return;
    const id = setInterval(() => setHeroIndex(i => i + 1), 10000);
    return () => clearInterval(id);
  }, [heroCandidates.length]);

  const heroVideo = heroCandidates.length > 0
    ? heroCandidates[heroIndex % heroCandidates.length]
    : (filteredAndSortedVideos[0] || groupedVideos[0]);

  // Bouton retour matériel Android
  const backHandlerRef = useRef<() => boolean>(() => false);
  backHandlerRef.current = () => {
    if (confirmDialog) { setConfirmDialog(null); return true; }
    if (showSubtitlesModal) { setShowSubtitlesModal(false); return true; }
    if (infoVideo) { setInfoVideo(null); setExpandedEpisode(null); return true; }
    if (showSettings) { setShowSettings(false); return true; }
    if (currentVideo) { savePlaybackPosition(); setCurrentVideo(null); return true; }
    if (isSearchOpen) { setIsSearchOpen(false); setSearchQuery(''); return true; }
    if (searchQuery) { setSearchQuery(''); return true; }
    if (selectedPlaylist) { setSelectedPlaylist(null); return true; }
    if (activeTab !== 'home') { setActiveTab('home'); return true; }
    if (isLibraryViewActive) {
      setSortBy('alpha');
      setFilterGenre('all');
      setFilterResolution('all');
      return true;
    }
    return false;
  };

  useEffect(() => {
    if (!Capacitor.isNativePlatform()) return;
    const listener = CapacitorApp.addListener('backButton', () => {
      if (!backHandlerRef.current()) {
        CapacitorApp.exitApp();
      }
    });
    return () => { listener.then(l => l.remove()); };
  }, []);

  useEffect(() => {
    if (isSearchOpen) searchInputRef.current?.focus();
  }, [isSearchOpen]);

  const recentAdditions = useMemo(() => [...groupedVideos].reverse().slice(0, 15), [groupedVideos]);
  const recommendations = useMemo(() => groupedVideos.slice(0, 15), [groupedVideos]);
  const tvShows = useMemo(() => groupedVideos.filter(v => v.isSeriesGroup), [groupedVideos]);
  const movies = useMemo(() => groupedVideos.filter(v => !v.isSeriesGroup), [groupedVideos]);
  
  const alphabetical = useMemo(() => [...filteredAndSortedVideos].sort((a, b) => a.name.localeCompare(b.name)), [filteredAndSortedVideos]);

  return (
    <div className="min-h-screen bg-black text-zinc-100 font-sans selection:bg-red-600/30">
      {/* Permission Gate */}
      {permsNeeded && <PermissionGate onRequest={handleManualRequest} />}

      {/* Header */}
      {!currentVideo && (
        <AppHeader
          isScrolled={isScrolled}
          activeTab={activeTab}
          searchQuery={searchQuery}
          showSearch={videos.length > 0}
          onSearchChange={setSearchQuery}
          onLogoClick={resetHomeView}
          onOpenFolder={() => fileInputRef.current?.click()}
          onOpenSettings={() => {
            setShowSettings(true);
            if (Capacitor.isNativePlatform()) {
              refreshExternalPlayers();
            }
          }}
        />
      )}

      {/* Main Content */}
      <main className="pb-24 md:pb-12">
        {currentVideo ? (
          <WebPlayer
            video={currentVideo}
            videoRef={videoRef}
            activeSubtitleUrl={activeSubtitleUrl}
            onClose={() => setCurrentVideo(null)}
            onOpenSubtitles={() => {
              setShowSubtitlesModal(true);
              if (subtitles.length === 0) searchSubtitles();
            }}
            onEnded={handleVideoEnded}
            onTimeUpdate={handleTimeUpdate}
            onLoadedMetadata={handleLoadedMetadata}
          />
        ) : (
          <div>
            {videos.length === 0 ? (
              <div className="flex flex-col items-center justify-center min-h-screen px-4 text-center bg-zinc-900">
                <h2 className="text-4xl md:text-5xl font-bold mb-4">Vos films et séries.</h2>
                <h3 className="text-xl md:text-2xl text-zinc-300 mb-8">Où vous voulez. Quand vous voulez.</h3>
                <p className="text-zinc-400 mb-8 max-w-md">
                  Sélectionnez un dossier sur votre appareil contenant des vidéos pour commencer le streaming local.
                </p>
                <button
                  onClick={() => fileInputRef.current?.click()}
                  className="bg-red-600 text-white px-8 py-4 rounded font-bold text-xl flex items-center gap-2 hover:bg-red-700 transition-colors active:scale-95 mb-4 mx-auto w-full md:w-auto justify-center"
                >
                  <FolderOpen className="w-6 h-6" />
                  Choisir un dossier
                </button>
                {Capacitor.isNativePlatform() && (
                  <button
                    onClick={() => startNativeScan()}
                    disabled={isScanning}
                    className="bg-zinc-800 text-white px-8 py-4 rounded font-bold text-lg flex items-center justify-center gap-2 hover:bg-zinc-700 transition-colors active:scale-95 mx-auto w-full md:w-auto"
                  >
                    {isScanning ? <RefreshCw className="w-6 h-6 animate-spin" /> : <Search className="w-6 h-6" />}
                    {isScanning ? "Analyse en cours..." : "Scanner le téléphone automatiquement"}
                  </button>
                )}
              </div>
            ) : (
              <div>
                {/* Bar de tri et de filtre */}
                {(isLibraryViewActive || searchQuery.trim()) && activeTab === 'home' && (
                  <FilterBar
                    sortBy={sortBy}
                    onSortBy={setSortBy}
                    filterGenre={filterGenre}
                    onFilterGenre={setFilterGenre}
                    filterResolution={filterResolution}
                    onFilterResolution={setFilterResolution}
                  />
                )}

                {activeTab === 'playlists' ? (
                  <PlaylistsScreen
                    playlists={playlists}
                    selectedPlaylist={selectedPlaylist}
                    groupedVideos={groupedVideos}
                    posters={posters}
                    watchProgress={watchProgress}
                    watchedVideos={watchedVideos}
                    onSelectPlaylist={setSelectedPlaylist}
                    onOpenInfo={handleOpenInfoModal}
                    onPlay={playVideo}
                    onDeletePlaylist={(id) => setConfirmDialog({
                      message: "Voulez-vous vraiment supprimer cette liste de lecture ?",
                      onConfirm: () => {
                        deletePlaylist(id);
                        showToast("Liste de lecture supprimée.", 'info');
                      }
                    })}
                    onRemoveVideo={removeVideoFromPlaylist}
                  />
                ) : activeTab === 'history' ? (
                  <HistoryScreen
                    historyItems={historyItems}
                    searchQuery={searchQuery}
                    newItemName={newHistoryItemName}
                    posters={posters}
                    onNewItemNameChange={setNewHistoryItemName}
                    onAddManualItem={() => {
                      if (!newHistoryItemName.trim()) return;
                      addManualHistoryItem(newHistoryItemName);
                      setNewHistoryItemName('');
                      showToast("Entrée ajoutée manuellement.", 'success');
                    }}
                    onOpenInfo={handleOpenInfoModal}
                    onPlay={playVideo}
                    onToggleWatched={toggleWatched}
                    onToggleForceAvailable={toggleForceAvailable}
                  />
                ) : searchQuery.trim() ? (
                  <SearchScreen
                    searchQuery={searchQuery}
                    searchResults={searchResults}
                    posters={posters}
                    watchedVideos={watchedVideos}
                    watchProgress={watchProgress}
                    onOpenInfo={handleOpenInfoModal}
                  />
                ) : isLibraryViewActive ? (
                  <LibraryScreen
                    videos={filteredAndSortedVideos}
                    posters={posters}
                    watchedVideos={watchedVideos}
                    watchProgress={watchProgress}
                    onOpenInfo={handleOpenInfoModal}
                    onPlay={playVideo}
                  />
                ) : (
                  <HomeScreen
                    heroVideo={heroVideo}
                    backdrops={backdrops}
                    posters={posters}
                    overviews={overviews}
                    inProgressVideos={filteredAndSortedVideos.filter(v => (watchProgress[v.name] || 0) > 0 && (watchProgress[v.name] || 0) < 95)}
                    recentAdditions={recentAdditions}
                    recommendations={recommendations}
                    tvShows={tvShows}
                    movies={movies}
                    alphabetical={alphabetical}
                    watchProgress={watchProgress}
                    watchedVideos={watchedVideos}
                    groupedVideosCount={groupedVideos.length}
                    showTmdbBanner={showTmdbBanner}
                    onConfigureTmdb={handleConfigureTmdb}
                    onDismissTmdbBanner={handleDismissTmdbBanner}
                    onOpenInfo={handleOpenInfoModal}
                    onPlay={playVideo}
                    onResetProgress={resetProgress}
                  />
                )}
              </div>
            )}
          </div>
        )}

        {videos.length > 0 && !currentVideo && (
          <BottomNav
            activeTab={activeTab}
            isLibraryViewActive={isLibraryViewActive}
            onHome={resetHomeView}
            onLibrary={() => {
              setActiveTab('home');
              setSortBy('date');
            }}
            onPlaylists={() => setActiveTab('playlists')}
            onHistory={() => setActiveTab('history')}
          />
        )}
      </main>

      {/* Inputs cachés */}
      <input
        type="file"
        ref={fileInputRef}
        onChange={handleFolderSelect}
        className="hidden"
        multiple
        {...({ webkitdirectory: "true", directory: "true", accept: "video/*" } as any)}
      />

      <input
        type="file"
        ref={subtitleInputRef}
        onChange={handleLocalSubtitleSelection}
        accept=".srt,.vtt"
        style={{ display: 'none' }}
        multiple
      />

      {/* Info Modal */}
      {infoVideo && (
        <InfoModal
          video={infoVideo}
          posters={posters}
          backdrops={backdrops}
          overviews={overviews}
          videoGenres={videoGenres}
          releaseDates={releaseDates}
          episodeNames={episodeNames}
          episodeOverviews={episodeOverviews}
          episodePosters={episodePosters}
          watchedVideos={watchedVideos}
          watchPositions={watchPositions}
          videoDurations={videoDurations}
          tmdbApiKey={tmdbApiKey}
          isRefreshingMetadata={isRefreshingMetadata}
          playlists={playlists}
          selectedSeason={selectedSeason}
          expandedEpisode={expandedEpisode}
          onClose={() => { setInfoVideo(null); setExpandedEpisode(null); }}
          onPlay={playVideo}
          onToggleWatched={toggleWatched}
          onResetProgress={resetProgress}
          onRefreshMetadata={fetchSingleMetadata}
          onToggleVideoInPlaylist={toggleVideoInPlaylist}
          onCreatePlaylist={createPlaylist}
          onSelectSeason={setSelectedSeason}
          onExpandEpisode={setExpandedEpisode}
        />
      )}

      {/* Settings Modal */}
      {showSettings && (
        <SettingsModal
          osApiKey={osApiKey}
          osUsername={osUsername}
          osPassword={osPassword}
          osToken={osToken}
          isLoggingIn={isLoggingIn}
          videoPlayer={videoPlayer}
          externalPlayers={externalPlayers}
          selectedExternalPlayer={selectedExternalPlayer}
          tmdbApiKey={tmdbApiKey}
          isFetchingMetadata={isFetchingMetadata}
          personalVideos={personalVideos}
          whitelistedVideos={whitelistedVideos}
          onToggleWhitelist={toggleWhitelist}
          isScanning={isScanning}
          onStartScan={() => {
            startNativeScan(false);
            showToast("Scan de la bibliothèque relancé.", 'info');
          }}
          onClearMetadataCache={() => {
            setConfirmDialog({
              message: "Vider le cache des métadonnées TMDB (affiches, synopsis…) ? Elles seront re-téléchargées au prochain scan.",
              onConfirm: () => {
                clearMetadataCache();
                showToast("Cache des métadonnées vidé.", 'success');
              }
            });
          }}
          onClose={() => setShowSettings(false)}
          onOsApiKeyChange={setOsApiKey}
          onOsUsernameChange={setOsUsername}
          onOsPasswordChange={setOsPassword}
          onLogin={loginOpenSubtitles}
          onVideoPlayerChange={setVideoPlayer}
          onSelectedExternalPlayerChange={setSelectedExternalPlayer}
          onRefreshPlayers={refreshExternalPlayers}
          onOpenSystemSettings={() => VideoLauncher.openSettings()}
          onTmdbApiKeyChange={setTmdbApiKey}
          onTestTmdbKey={handleTestTmdbKey}
        />
      )}

      {/* Subtitles Modal */}
      {showSubtitlesModal && (
        <SubtitlesModal
          subtitles={subtitles}
          localSubtitles={localSubtitles}
          isSearchingSubs={isSearchingSubs}
          osApiKey={osApiKey}
          activeSubtitleUrl={activeSubtitleUrl}
          onClose={() => setShowSubtitlesModal(false)}
          onSearch={searchSubtitles}
          onPickLocal={Capacitor.isNativePlatform() ? openNativeSubtitlePicker : () => subtitleInputRef.current?.click()}
          onSelectLocal={(sub) => {
            setActiveSubtitleUrl(sub.url || null);
            setShowSubtitlesModal(false);
          }}
          onDownload={downloadSubtitle}
        />
      )}

      {/* Toasts */}
      <Toasts toasts={toasts} />

      {/* Dialogue de confirmation */}
      {confirmDialog && (
        <ConfirmDialog
          dialog={confirmDialog}
          onCancel={() => setConfirmDialog(null)}
          onConfirm={() => {
            confirmDialog.onConfirm();
            setConfirmDialog(null);
          }}
        />
      )}
    </div>
  );
}
