import React, { useState, useRef, useEffect, useMemo, useCallback } from 'react';
import { Settings, FolderOpen, Play, Search, X, Download, ChevronLeft, Subtitles, LogIn, Image as ImageIcon, Info, ListPlus, Check, Trash2, ListVideo, RefreshCw, Cloud, CloudOff, RotateCcw, RotateCw, Pause, Clock, Plus, History, Film, AlertTriangle, EyeOff } from 'lucide-react';
import { Capacitor, registerPlugin } from '@capacitor/core';
import { App as CapacitorApp } from '@capacitor/app';
import { Filesystem, Directory } from '@capacitor/filesystem';
import { VideoFile, Subtitle, Playlist, TMDB_GENRES } from './lib/types';
import { srt2vtt, safeSetItem, getCleanTitle, getResolution, formatSize, formatDuration, isPersonalVideo } from './lib/utils';
import { osLogin, osSearch, osDownloadVtt } from './lib/opensubtitles';
import { getPopular } from './lib/tmdb';
import { filterAndSortVideos } from './lib/sorting';
import { getHeroCandidates } from './lib/hero';
import { useTmdbMetadata } from './hooks/useTmdbMetadata';
import { usePlaylists } from './hooks/usePlaylists';
import { useWatchedState } from './hooks/useWatchedState';
import { VideoRow } from './components/VideoRow';
import { BottomNav } from './components/BottomNav';

interface VideoLauncherPlugin {
  openVideo(options: { 
    path: string, 
    title?: string, 
    startPosition?: number, 
    playerType?: string, 
    packageId?: string,
    subtitlePath?: string
  }): Promise<void>;
  getList(): Promise<{ players: { name: string, packageId: string }[] }>;
  openSettings(): Promise<void>;
  pickSubtitle(): Promise<{ path: string, name: string }>;
  checkStoragePermission(): Promise<{ granted: boolean }>;
  requestStoragePermission(): Promise<{ granted: boolean }>;
}

const VideoLauncher = registerPlugin<VideoLauncherPlugin>('VideoLauncher');

export default function App() {
  const [videos, setVideos] = useState<VideoFile[]>([]);
  const [currentVideo, setCurrentVideo] = useState<VideoFile | null>(null);
  const [showSettings, setShowSettings] = useState(false);
  const [isScrolled, setIsScrolled] = useState(false);
  // Recherche mobile : ouverte en plein header via une icône loupe (évite que
  // le champ toujours affiché ne serre le logo et ne pousse les boutons hors écran).
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const searchInputRef = useRef<HTMLInputElement>(null);

  // Toasts non bloquants (remplacent les alert() natifs de la WebView)
  const [toasts, setToasts] = useState<{ id: number; message: string; type: 'success' | 'error' | 'info' }[]>([]);
  const showToast = useCallback((message: string, type: 'success' | 'error' | 'info' = 'info') => {
    const id = Date.now() + Math.random();
    setToasts(prev => [...prev.slice(-2), { id, message, type }]);
    setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), 4000);
  }, []);

  // Confirmation avant action destructive (suppression de playlist…)
  const [confirmDialog, setConfirmDialog] = useState<{ message: string; onConfirm: () => void } | null>(null);

  // OpenSubtitles Credentials
  const [osApiKey, setOsApiKey] = useState(localStorage.getItem('osApiKey') || '');
  const [osUsername, setOsUsername] = useState(localStorage.getItem('osUsername') || '');
  // Le mot de passe n'est JAMAIS persisté (il était auparavant stocké en clair
  // dans localStorage) : il ne vit qu'en mémoire, le temps d'obtenir un token.
  const [osPassword, setOsPassword] = useState('');
  const [osToken, setOsToken] = useState(localStorage.getItem('osToken') || '');
  
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
  // Bannière d'aide à la configuration TMDB (affichée tant que pas de clé, dismissible)
  const [tmdbBannerDismissed, setTmdbBannerDismissed] = useState(localStorage.getItem('tmdbBannerDismissed') === '1');
  const [videoDurations, setVideoDurations] = useState<Record<string, number>>({});

  const [sortBy, setSortBy] = useState<'alpha' | 'date' | 'size' | 'duration'>('alpha');
  const [filterGenre, setFilterGenre] = useState<number | 'all'>('all');
  const [filterResolution, setFilterResolution] = useState<string | 'all'>('all');

  const [subtitles, setSubtitles] = useState<Subtitle[]>([]);
  const [localSubtitles, setLocalSubtitles] = useState<Subtitle[]>([]);
  const [isSearchingSubs, setIsSearchingSubs] = useState(false);
  const [isLoggingIn, setIsLoggingIn] = useState(false);
  const [activeSubtitleUrl, setActiveSubtitleUrl] = useState<string | null>(null);
  const [activeSubtitleNativePath, setActiveSubtitleNativePath] = useState<string | null>(null);
  const subtitleInputRef = useRef<HTMLInputElement>(null);
  const [showSubtitlesModal, setShowSubtitlesModal] = useState(false);

  const openNativeSubtitlePicker = async () => {
    try {
      const res = await VideoLauncher.pickSubtitle();
      const convertUri = Capacitor.convertFileSrc(res.path);
      const newSub: Subtitle = {
        id: `local-${Date.now()}`,
        language: 'Local',
        filename: res.name,
        url: convertUri
      };
      setLocalSubtitles(prev => [...prev, newSub]);
      setActiveSubtitleUrl(convertUri);
      setActiveSubtitleNativePath(res.path);
      setShowSubtitlesModal(false);
    } catch (err) {
      console.error("Selection annulée ou erreur", err);
    }
  };

  const handleLocalSubtitleSelection = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;

    const newLocalSubs: Subtitle[] = [];
    for (let i = 0; i < files.length; i++) {
      const file = files[i];
      if (file.name.endsWith('.srt') || file.name.endsWith('.vtt')) {
        const url = URL.createObjectURL(file);
        newLocalSubs.push({
          id: `local-${Date.now()}-${i}`,
          language: 'Local',
          filename: file.name,
          url: url
        });
      }
    }
    setLocalSubtitles(prev => [...prev, ...newLocalSubs]);
    if (e.target) e.target.value = '';
  };
  const [infoVideo, setInfoVideo] = useState<VideoFile | null>(null);
  const [expandedEpisode, setExpandedEpisode] = useState<string | null>(null);
  const [playerFeedback, setPlayerFeedback] = useState<{type: 'rewind' | 'forward' | 'pause' | 'play', visible: boolean}>({type: 'pause', visible: false});
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
  const [showPlaylistSelector, setShowPlaylistSelector] = useState(false);
  const [newPlaylistName, setNewPlaylistName] = useState('');
  
  const [newHistoryItemName, setNewHistoryItemName] = useState('');

  // Ajoute l'entrée saisie puis vide le champ.
  const handleAddHistory = () => {
    addManualHistoryItem(newHistoryItemName);
    setNewHistoryItemName('');
  };

  const [isScanning, setIsScanning] = useState(false);
  const [diagLogs, setDiagLogs] = useState<string[]>([]);
  const addLog = (msg: string) => {
    setDiagLogs(prev => [new Date().toLocaleTimeString() + ": " + msg, ...prev].slice(0, 50));
  };

  // Couche métadonnées TMDB (état + regroupement + récupération auto/manuelle)
  const {
    groupedVideos,
    posters, backdrops, overviews, releaseDates, videoGenres, tmdbIds,
    movieCollections, episodeOverviews, episodePosters, episodeNames,
    isFetchingMetadata, isRefreshingMetadata,
    fetchSingleMetadata,
    clearMetadataCache,
  } = useTmdbMetadata({ videos, whitelistedVideos, tmdbApiKey, addLog });

  const [permsNeeded, setPermsNeeded] = useState(false);
  const [externalPlayers, setExternalPlayers] = useState<{name: string, packageId: string}[]>([]);
  const [selectedExternalPlayer, setSelectedExternalPlayer] = useState<string>(localStorage.getItem('selectedExternalPlayer') || '');

  // État de visionnage : vu/non vu, progression, reprise, historique récent.
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

  const fileInputRef = useRef<HTMLInputElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);

  useEffect(() => {
    // passive: ne bloque pas le compositing du scroll ; le setState fonctionnel
    // évite un re-render quand la valeur booléenne n'a pas changé.
    const handleScroll = () => {
      const scrolled = window.scrollY > 50;
      setIsScrolled(prev => (prev === scrolled ? prev : scrolled));
    };
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  // Save credentials (jamais le mot de passe — sécurité)
  useEffect(() => {
    safeSetItem('osApiKey', osApiKey);
    safeSetItem('osUsername', osUsername);
    safeSetItem('osToken', osToken);
    safeSetItem('videoPlayer', videoPlayer);
    // Purge un éventuel mot de passe stocké en clair par les anciennes versions.
    localStorage.removeItem('osPassword');
  }, [osApiKey, osUsername, osToken, videoPlayer]);

  // Signale à l'utilisateur quand le stockage local est saturé (au plus 1×/min).
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
      // Re-vérifier après
      const { granted } = await VideoLauncher.checkStoragePermission();
      const stats = await Filesystem.checkPermissions();
      if (granted && stats.publicStorage === 'granted') {
        setPermsNeeded(false);
      }
    } catch (err) {
      console.error(err);
    }
  };

  // Initial setup
  useEffect(() => {
    checkGlobalPermissions();
    if (Capacitor.isNativePlatform()) {
      VideoLauncher.getList()
        .then(res => setExternalPlayers(res.players))
        .catch(err => console.error("Error fetching external players", err));
      
      // Lancement du scan silencieux au démarrage de l'application
      startNativeScan(true);
    }
    // Listen for window focus to re-check permissions (when coming back from settings)
    window.addEventListener('focus', checkGlobalPermissions);

    return () => {
      window.removeEventListener('focus', checkGlobalPermissions);
    };
  }, []);

  const [isSynopsisExpanded, setIsSynopsisExpanded] = useState(false);
  const [isEpisodeSynopsisExpanded, setIsEpisodeSynopsisExpanded] = useState(false);

  useEffect(() => {
    // Reset expansion when changing video or episode
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
          // Ignore errors
        }
      }
    };

    // Run in background to not block main thread
    let timeoutId: ReturnType<typeof setTimeout> | undefined;
    if (videos.length > 0) {
      timeoutId = setTimeout(extractDurations, 2000);
    }
    return () => {
      cancelled = true;
      if (timeoutId) clearTimeout(timeoutId);
    };
  }, [videos]);

  const scanDirectoryRecursively = async (directory: typeof Directory[keyof typeof Directory], basePath: string, debugLogs: string[]): Promise<VideoFile[]> => {
    let result: VideoFile[] = [];
    try {
      const response = await Filesystem.readdir({ path: basePath, directory });

      // Indexer les sous-titres du dossier par nom de base (sans extension)
      const subtitleMap: Record<string, { uri: string; name: string }> = {};
      for (const file of response.files) {
        if (file.type === 'file' && file.name.match(/\.(srt|vtt|ass|ssa)$/i)) {
          // Nom de base sans extension
          const base = file.name.replace(/\.\w+$/, '').replace(/\.(fr|en|es|de|it|pt|nl|pl|ru|ja|zh|ko|ar|he|tr|sv|da|fi|nb|uk|cs|sk|hu|ro|hr|sr|bg|el|vi|th|hi|id|ms|fa)$/i, '').toLowerCase();
          subtitleMap[base] = { uri: file.uri, name: file.name };
        }
      }

      for (const file of response.files) {
        const fullPath = basePath ? `${basePath}/${file.name}` : file.name;
        if (file.type === 'directory') {
          const subFiles = await scanDirectoryRecursively(directory, fullPath, debugLogs);
          result = result.concat(subFiles);
        } else if (file.type === 'file' && (file.name.match(/\.(mp4|mkv|webm|avi|mov)$/i))) {
          let seriesName, season, episode;
          const match = file.name.match(/[sS](\d+)(\s*)[eE](\d+)|(\d+)(\s*)x(\d+)/i);
          if (match) {
            season = parseInt(match[1] || match[4], 10);
            episode = parseInt(match[3] || match[6], 10);
            seriesName = file.name.substring(0, match.index).replace(/[\.\-_/\\\[\]\(\)]/g, " ").trim().replace(/[\s\-]+$/, "");
            if (!seriesName) seriesName = "Série Inconnue";
          } else {
            // Détection par structure de dossiers : ex Series/ShowName/Season 1/01.mp4
            const pathParts = (fullPath || "").split('/').filter(p => !!p);
            if (pathParts.length >= 2) {
              const lastFolder = pathParts[pathParts.length - 2]; // ex: "Season 1"
              const sMatch = lastFolder.match(/Saison\s*(\d+)|Season\s*(\d+)|S(\d+)/i);
              if (sMatch) {
                season = parseInt(sMatch[1] || sMatch[2] || sMatch[3], 10);
                seriesName = pathParts[pathParts.length - 3] || "Série Inconnue"; // ex: "ShowName"
                
                // Essayer d'extraire l'épisode du début du nom de fichier
                const epMatch = file.name.match(/^(\d+)/);
                if (epMatch) episode = parseInt(epMatch[1], 10);
              }
            }
          }

          // Chercher un sous-titre avec le même nom de base
          const videoBase = file.name.replace(/\.\w+$/, '').toLowerCase();
          const matchedSub = subtitleMap[videoBase];

          result.push({
            url: Capacitor.convertFileSrc(file.uri),
            nativeUri: file.uri,
            name: file.name,
            type: 'video/mp4',
            path: fullPath,
            size: file.size,
            lastModified: file.mtime,
            seriesName,
            season,
            episode,
            subtitleNativePath: matchedSub ? matchedSub.uri : undefined,
          } as any);
        }
      }
    } catch (e: any) {
      debugLogs.push(`Could not scan ${basePath}: ${e.message}`);
      console.warn("Could not scan", basePath, e);
    }
    return result;
  };

  const startNativeScan = async (quiet = false) => {
    if (!Capacitor.isNativePlatform()) return;
    // Toujours signaler le scan en cours (l'écran vide affiche un indicateur),
    // "quiet" ne concerne que les popups d'erreur.
    setIsScanning(true);
    const debugLogs: string[] = [];
    try {
      const stats = await Filesystem.checkPermissions();
      if (stats.publicStorage !== 'granted') {
        if (!quiet) {
          await Filesystem.requestPermissions();
        } else {
          return;
        }
      }
      const foldersToScan = [
        { dir: Directory.ExternalStorage, path: 'Movies' },
        { dir: Directory.ExternalStorage, path: 'Download' },
        { dir: Directory.ExternalStorage, path: 'Downloads' },
        { dir: Directory.ExternalStorage, path: 'Documents' }
      ];
      
      let allVideos: VideoFile[] = [];
      for (const folder of foldersToScan) {
        const vids = await scanDirectoryRecursively(folder.dir, folder.path, debugLogs);
        allVideos = allVideos.concat(vids);
      }
      
      // Mise à jour de la liste avec détection de changements
      setVideos(prev => {
        // Supprimer ce qui n'existe plus
        const currentPaths = new Set(allVideos.map(v => v.nativeUri || v.path));
        const kept = prev.filter(v => currentPaths.has(v.nativeUri || v.path));
        
        // Ajouter les nouveaux
        const existingPaths = new Set(kept.map(v => v.nativeUri || v.path));
        const extra = allVideos.filter(v => !existingPaths.has(v.nativeUri || v.path));
        
        return [...kept, ...extra];
      });

      if (!quiet && allVideos.length === 0 && debugLogs.length > 0) {
        showToast("Aucune vidéo trouvée dans les dossiers scannés.", 'error');
      }
    } catch (e: any) {
      console.error(e);
      if (!quiet) showToast("Erreur pendant le scan : " + e.message, 'error');
    } finally {
      setIsScanning(false);
    }
  };

  const handleFolderSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files) return;

    // Indexer les sous-titres par chemin relatif (sans extension)
    const subtitleFileMap: Record<string, File> = {};
    for (let i = 0; i < files.length; i++) {
      const f = files[i];
      if (f.name.match(/\.(srt|vtt|ass|ssa)$/i)) {
        const folder = (f.webkitRelativePath || f.name).replace(/\/[^\/]+$/, '');
        const base = f.name.replace(/\.\w+$/, '').replace(/\.(fr|en|es|de|it|pt|nl|pl|ru|ja|zh|ko|ar|he|tr|sv|da|fi|nb|uk|cs|sk|hu|ro|hr|sr|bg|el|vi|th|hi|id|ms|fa)$/i, '').toLowerCase();
        subtitleFileMap[`${folder}/${base}`] = f;
      }
    }

    const videoFiles: VideoFile[] = [];
    for (let i = 0; i < files.length; i++) {
      const file = files[i];
      if (file.type.startsWith('video/') || file.name.match(/\.(mp4|mkv|webm|avi|mov)$/i)) {
        let seriesName, season, episode;
        const match = file.name.match(/[sS](\d+)(\s*)[eE](\d+)|(\d+)(\s*)x(\d+)/i);
        if (match) {
          season = parseInt(match[1] || match[4], 10);
          episode = parseInt(match[3] || match[6], 10);
          seriesName = file.name.substring(0, match.index).replace(/[\.\-_]/g, " ").trim().replace(/\s+$/, "");
          if (!seriesName) seriesName = "Série Inconnue";
        }

        // Chercher un sous-titre avec le même nom de base dans le même dossier
        const folder = (file.webkitRelativePath || file.name).replace(/\/[^\/]+$/, '');
        const videoBase = file.name.replace(/\.\w+$/, '').toLowerCase();
        const subFile = subtitleFileMap[`${folder}/${videoBase}`];
        let subtitleUrl: string | undefined;
        if (subFile) {
          // Convertir .srt en .vtt si nécessaire
          if (subFile.name.endsWith('.srt')) {
            const reader = new FileReader();
            reader.onload = (ev) => {
              const vttContent = srt2vtt(ev.target?.result as string || '');
              const blob = new Blob([vttContent], { type: 'text/vtt' });
              subtitleUrl = URL.createObjectURL(blob);
              // Mettre à jour la vidéo avec l'URL du sous-titre
              setVideos(prev => prev.map(v => v.name === file.name ? { ...v, subtitleUrl } : v));
            };
            reader.readAsText(subFile);
          } else {
            subtitleUrl = URL.createObjectURL(subFile);
          }
        }

        videoFiles.push({
          file,
          url: URL.createObjectURL(file),
          name: file.name,
          type: file.type || 'video/mp4',
          path: file.webkitRelativePath || file.name,
          seriesName,
          season,
          episode,
          subtitleUrl,
        });
      }
    }
    // Diff update for folder select (web) + révocation des object URLs
    // qui ne seront plus référencés (sinon les blobs restent en mémoire).
    setVideos(prev => {
      const currentPaths = new Set(videoFiles.map(v => v.path));
      const kept = prev.filter(v => currentPaths.has(v.path));

      const existingPaths = new Set(kept.map(v => v.path));
      const extra = videoFiles.filter(v => !existingPaths.has(v.path));

      const removed = prev.filter(v => !currentPaths.has(v.path));
      const unusedNew = videoFiles.filter(v => existingPaths.has(v.path));
      [...removed, ...unusedNew].forEach(v => {
        if (v.file && v.url?.startsWith('blob:')) URL.revokeObjectURL(v.url);
        if (v.subtitleUrl?.startsWith('blob:')) URL.revokeObjectURL(v.subtitleUrl);
      });

      return [...kept, ...extra];
    });
  };

  const [activePlaylist, setActivePlaylist] = useState<Playlist | null>(null);
  const [activePlaylistIndex, setActivePlaylistIndex] = useState<number>(0);

  const playVideo = useCallback((video: VideoFile, playlist?: Playlist, index?: number) => {
    let videoToPlay = video;
    if (video.isSeriesGroup && video.episodes && video.episodes.length > 0) {
      // Trouver le premier épisode non vu
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
    
    // Ajout à l'historique (Recently Watched) - déplacé ici pour fonctionner sur toutes les plateformes
    setRecentlyWatched(prev => {
      const newWatched = [videoToPlay.name, ...prev.filter(name => name !== videoToPlay.name)].slice(0, 30);
      safeSetItem('recentlyWatched', JSON.stringify(newWatched));
      return newWatched;
    });

    if (Capacitor.isNativePlatform()) {
      // Utilise l'URI native absolue (file:///storage/...) pour ExoPlayer
      const videoPath = videoToPlay.nativeUri ?? videoToPlay.url;
      const startPosMs = watchPositions[videoToPlay.name] || 0;
      // Priorité : sous-titre manuel > sous-titre auto-détecté
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
            // Mise à jour de la position et progression
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
            
            // Si le lecteur renvoie qu'on a atteint la fin (ou proche de la fin)
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
    // Charger le sous-titre auto-détecté si aucun n'est sélectionné manuellement
    if (!activeSubtitleUrl && videoToPlay.subtitleUrl) {
      setActiveSubtitleUrl(videoToPlay.subtitleUrl);
    } else if (!activeSubtitleUrl) {
      setActiveSubtitleUrl(null);
    }
    setSubtitles([]);
  }, [watchedVideos, watchPositions, videoPlayer, selectedExternalPlayer, activeSubtitleNativePath, activeSubtitleUrl, toggleWatched, showToast]);

  const loginOpenSubtitles = async () => {
    if (!osApiKey || !osUsername || !osPassword) {
      showToast("Veuillez remplir tous les champs OpenSubtitles.", 'error');
      return;
    }

    setIsLoggingIn(true);
    try {
      const token = await osLogin(osApiKey, osUsername, osPassword);
      if (token) {
        setOsToken(token);
        // Seul le token est conservé — on efface le mot de passe de la mémoire.
        setOsPassword('');
        showToast('Connexion réussie !', 'success');
      } else {
        showToast('Échec de la connexion OpenSubtitles.', 'error');
      }
    } catch (error) {
      showToast('Échec de la connexion OpenSubtitles.', 'error');
    } finally {
      setIsLoggingIn(false);
    }
  };

  const searchSubtitles = async () => {
    if (!currentVideo || !osApiKey) return;
    setIsSearchingSubs(true);
    try {
      const cleanName = currentVideo.name.replace(/\.(mp4|mkv|webm|avi|mov)$/i, '').replace(/[\.\-_]/g, ' ');
      setSubtitles(await osSearch(osApiKey, cleanName));
    } catch (error) {
      showToast("Erreur lors de la recherche de sous-titres.", 'error');
    } finally {
      setIsSearchingSubs(false);
    }
  };

  // URL du dernier sous-titre téléchargé (à révoquer quand on le remplace).
  const downloadedSubUrlRef = useRef<string | null>(null);

  const downloadSubtitle = async (fileId: string) => {
    if (!osApiKey || !osToken) {
      showToast("Vous devez être connecté à OpenSubtitles (voir Paramètres).", 'error');
      setShowSubtitlesModal(false);
      setShowSettings(true);
      return;
    }
    try {
      const vttContent = await osDownloadVtt(osApiKey, osToken, fileId);
      if (vttContent) {
        const blob = new Blob([vttContent], { type: 'text/vtt' });
        if (downloadedSubUrlRef.current) URL.revokeObjectURL(downloadedSubUrlRef.current);
        const url = URL.createObjectURL(blob);
        downloadedSubUrlRef.current = url;
        setActiveSubtitleUrl(url);
        setShowSubtitlesModal(false);
      } else {
        showToast("Erreur de téléchargement du sous-titre (session expirée ?). Reconnectez-vous dans les Paramètres.", 'error');
      }
    } catch (error) {
      showToast("Erreur lors du téléchargement du sous-titre.", 'error');
    }
  };

  // Toutes les listes dérivées sont mémoïsées : App re-rend souvent (scroll,
  // rotation du hero, progression) et ces tris/filtres sont coûteux sur mobile.
  const { tvShows, movies } = useMemo(() => ({
    tvShows: groupedVideos.filter(v => v.isSeriesGroup),
    movies: groupedVideos.filter(v => !v.isSeriesGroup),
  }), [groupedVideos]);

  // Nouveautés (Recently Added) - Unwatched first
  const recentAdditions = useMemo(() => [...groupedVideos].sort((a, b) => {
    const aWatched = a.isSeriesGroup ? a.episodes?.every(ep => !!watchedVideos[ep.name]) : !!watchedVideos[a.name];
    const bWatched = b.isSeriesGroup ? b.episodes?.every(ep => !!watchedVideos[ep.name]) : !!watchedVideos[b.name];
    if (aWatched !== bWatched) return aWatched ? 1 : -1;
    return (b.file?.lastModified || b.lastModified || 0) - (a.file?.lastModified || a.lastModified || 0);
  }), [groupedVideos, watchedVideos]);

  // Vus récemment (Recently Watched)
  const recentlyWatchedVideos = useMemo(() => recentlyWatched
    .map(name => groupedVideos.find(v => v.name === name || (v.episodes && v.episodes.some(ep => ep.name === name))))
    .filter((v): v is VideoFile => v !== undefined)
    .filter((v, i, a) => a.indexOf(v) === i), // remove duplicates if multiple episodes of same series are watched
    [recentlyWatched, groupedVideos]);

  // En cours (In Progress)
  const inProgressVideos = useMemo(() => recentlyWatched
    .map(name => videos.find(v => v.name === name))
    .filter((v): v is VideoFile => v !== undefined && (watchProgress[v.name] || 0) > 0 && (watchProgress[v.name] || 0) < 95 && !watchedVideos[v.name]),
    [recentlyWatched, videos, watchProgress, watchedVideos]);

  // De A à Z (Alphabetical) - Unwatched first
  const alphabetical = useMemo(() => [...groupedVideos].sort((a, b) => {
    const aWatched = a.isSeriesGroup ? a.episodes?.every(ep => !!watchedVideos[ep.name]) : !!watchedVideos[a.name];
    const bWatched = b.isSeriesGroup ? b.episodes?.every(ep => !!watchedVideos[ep.name]) : !!watchedVideos[b.name];
    if (aWatched !== bWatched) return aWatched ? 1 : -1;
    return a.name.localeCompare(b.name);
  }), [groupedVideos, watchedVideos]);

  // Recommandations (Pseudo-random based on name length for stability) - Unwatched first
  const recommendations = useMemo(() => [...groupedVideos].sort((a, b) => {
    const aWatched = a.isSeriesGroup ? a.episodes?.every(ep => !!watchedVideos[ep.name]) : !!watchedVideos[a.name];
    const bWatched = b.isSeriesGroup ? b.episodes?.every(ep => !!watchedVideos[ep.name]) : !!watchedVideos[b.name];
    if (aWatched !== bWatched) return aWatched ? 1 : -1;
    return (a.name.length % 7) - (b.name.length % 7);
  }), [groupedVideos, watchedVideos]);

  // Group by folder
  const folders = useMemo(() => groupedVideos.reduce((acc, video) => {
    const parts = video.path.split('/');
    const folderName = parts.length > 1 ? parts[0] : 'Racine';

    if (!acc[folderName]) {
      acc[folderName] = [];
    }
    acc[folderName].push(video);
    return acc;
  }, {} as Record<string, VideoFile[]>), [groupedVideos]);

  const folderNames = useMemo(() => Object.keys(folders).sort(), [folders]);

  // Vidéos personnelles détectées (exclues de la bibliothèque, réintégrables
  // via les Paramètres → whitelist).
  const personalVideos = useMemo(
    () => videos.filter(v => isPersonalVideo(v.name, v.path || '')),
    [videos]
  );

  const filteredAndSortedVideos = React.useMemo(
    () => filterAndSortVideos(groupedVideos, {
      sortBy, filterGenre, filterResolution, releaseDates, videoGenres, videoDurations, watchedVideos,
    }),
    [groupedVideos, sortBy, filterGenre, filterResolution, releaseDates, videoGenres, videoDurations, watchedVideos]
  );

  // Recherche débouncée : évite de re-filtrer toute la bibliothèque à chaque frappe.
  const [debouncedQuery, setDebouncedQuery] = useState('');
  useEffect(() => {
    const id = setTimeout(() => setDebouncedQuery(searchQuery), 250);
    return () => clearTimeout(id);
  }, [searchQuery]);

  const searchResults = useMemo(() => debouncedQuery.trim()
    ? filteredAndSortedVideos.filter(v => v.name.toLowerCase().includes(debouncedQuery.toLowerCase()))
    : [], [debouncedQuery, filteredAndSortedVideos]);

  const historyItems = React.useMemo(() => {
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
  }, [isLibraryViewActive]);

  // Re-scan au retour sur l'app, au plus une fois toutes les 30 s
  // (le focus peut se déclencher en rafale : permissions, changement d'app…).
  const lastFocusScanRef = useRef(0);
  useEffect(() => {
    const handleFocusScan = () => {
      if (!Capacitor.isNativePlatform()) return;
      const now = Date.now();
      if (now - lastFocusScanRef.current < 30000) return;
      lastFocusScanRef.current = now;
      startNativeScan(true);
    };
    window.addEventListener('focus', handleFocusScan);
    return () => window.removeEventListener('focus', handleFocusScan);
  }, []);

  // Hero rotatif (#4) : on fait défiler les films/séries non vus ou non terminés
  // plutôt que d'afficher toujours le dernier ajouté.
  const heroCandidates = React.useMemo(
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
    : (recentAdditions[0] || groupedVideos[0]);

  // Crée la playlist depuis le champ de saisie, pré-remplie avec la vidéo affichée.
  const handleCreatePlaylist = () => {
    createPlaylist(newPlaylistName, infoVideo ? [infoVideo.name] : []);
    setNewPlaylistName('');
  };

  const handleOpenInfoModal = useCallback((video: VideoFile) => {
    // Si c'est un épisode individuel (pas un groupe), on remonte au groupe de série parent
    if (!video.isSeriesGroup && (video.season !== undefined || video.seriesName)) {
      const parentGroup = groupedVideos.find(g =>
        g.isSeriesGroup && g.episodes?.some(ep => ep.name === video.name)
      );
      if (parentGroup) {
        setInfoVideo(parentGroup);
        setShowPlaylistSelector(false);
        // Ouvrir directement l'épisode cliqué dans le panneau d'info
        setExpandedEpisode(video.name);
        const season = video.season || 1;
        setSelectedSeason(season);
        return;
      }
    }

    setInfoVideo(video);
    setShowPlaylistSelector(false);
    setExpandedEpisode(null);
    if (video.isSeriesGroup && video.episodes && video.episodes.length > 0) {
      const seasons = Array.from(new Set(video.episodes.map(ep => ep.season || 1))).sort((a, b) => a - b);
      setSelectedSeason(seasons[0]);
    } else {
      setSelectedSeason(null);
    }
  }, [groupedVideos]);

  // Props partagées par toutes les lignes de carrousel (cf. components/VideoRow).
  // Mémoïsées, sinon un nouvel objet à chaque render neutralise le React.memo
  // de VideoRow et toutes les rangées re-rendent en permanence.
  const rowProps = useMemo(() => ({
    posters,
    watchProgress,
    watchedVideos,
    onOpenInfo: handleOpenInfoModal,
    onPlay: playVideo,
    onResetProgress: resetProgress,
  }), [posters, watchProgress, watchedVideos, handleOpenInfoModal, playVideo, resetProgress]);

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
        return;
      }
    }

    // Hors playlist : si l'épisode appartient à une série, enchaîner sur le suivant.
    if (currentVideo) {
      const parentSeries = groupedVideos.find(g => g.isSeriesGroup && g.episodes?.some(ep => ep.name === currentVideo.name));
      if (parentSeries?.episodes) {
        const idx = parentSeries.episodes.findIndex(ep => ep.name === currentVideo.name);
        const nextEpisode = parentSeries.episodes[idx + 1];
        if (nextEpisode) {
          showToast(`Épisode suivant : ${getCleanTitle(nextEpisode.name)}`, 'info');
          playVideo(nextEpisode);
        }
      }
    }
  };

  // Position de lecture : throttle des écritures. Sans cela, timeupdate (~4×/s)
  // déclenche une sérialisation JSON + écriture localStorage à chaque tick.
  const lastPositionSaveRef = useRef(0);

  const savePlaybackPosition = () => {
    if (!videoRef.current || !currentVideo) return;
    const currentTime = videoRef.current.currentTime;
    if (currentTime > 0) {
      setWatchPositions(prev => ({ ...prev, [currentVideo.name]: currentTime * 1000 }));
    }
  };

  const handleTimeUpdate = () => {
    if (!videoRef.current || !currentVideo) return;
    const currentTime = videoRef.current.currentTime;
    const duration = videoRef.current.duration;
    if (!duration) return;

    const percentage = (currentTime / duration) * 100;

    const now = Date.now();
    if (now - lastPositionSaveRef.current >= 5000) {
      lastPositionSaveRef.current = now;
      setWatchPositions(prev => ({ ...prev, [currentVideo.name]: currentTime * 1000 }));
    }

    setWatchProgress(prev => {
      const prevPercentage = prev[currentVideo.name] || 0;
      // Update state if difference is > 1% or if it's finished
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
      // On reprend là où on en était si position existe (> 0) et n'est pas presque à la fin
      if (savedPosMs && savedPosMs > 0 && (savedPosMs / (videoRef.current.duration * 1000)) < 0.95) {
        videoRef.current.currentTime = savedPosMs / 1000;
      }
    }
  };

  // Bouton retour matériel Android : ferme la vue la plus "haute" (modal, lecteur,
  // sous-vue) au lieu de quitter l'application. Ref réassignée à chaque render
  // pour que le listener (enregistré une seule fois) voie l'état courant.
  const backHandlerRef = useRef<() => boolean>(() => false);
  backHandlerRef.current = () => {
    if (confirmDialog) { setConfirmDialog(null); return true; }
    if (showSubtitlesModal) { setShowSubtitlesModal(false); return true; }
    if (showPlaylistSelector) { setShowPlaylistSelector(false); return true; }
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

  // Focus automatique du champ quand la recherche mobile s'ouvre.
  useEffect(() => {
    if (isSearchOpen) searchInputRef.current?.focus();
  }, [isSearchOpen]);

  return (
    <div className="min-h-screen bg-black text-zinc-100 font-sans selection:bg-red-600/30">
      {/* Permission Gate */}
      {permsNeeded && (
        <div className="fixed inset-0 bg-black z-[100] flex items-center justify-center p-6 text-center">
          <div className="max-w-sm">
            <FolderOpen className="w-16 h-16 text-red-600 mx-auto mb-6" />
            <h2 className="text-2xl font-bold mb-4">Accès au stockage</h2>
            <p className="text-zinc-400 mb-8 text-sm leading-relaxed">
              LocalStream a besoin d'accéder à vos dossiers pour scanner et afficher vos vidéos. 
              Veuillez accorder les permissions nécessaires pour continuer.
            </p>
            <button
              onClick={handleManualRequest}
              className="w-full bg-red-600 hover:bg-red-700 text-white font-black py-4 rounded-2xl transition-all shadow-xl shadow-red-900/20 active:scale-95 uppercase tracking-widest text-xs"
            >
              Accorder l'accès
            </button>
            <button
              onClick={() => VideoLauncher.openSettings()}
              className="w-full mt-3 bg-zinc-800 hover:bg-zinc-700 text-white/80 font-bold py-3 rounded-2xl transition-all text-xs"
            >
              Ouvrir les réglages système
            </button>
            <p className="mt-4 text-[10px] text-zinc-600 uppercase tracking-widest font-black">
              Si la demande n'apparaît plus, activez la permission dans les réglages système.
            </p>
          </div>
        </div>
      )}
      {/* Header */}
      {!currentVideo && (
        <header 
          className={`fixed top-0 z-50 w-full px-4 md:px-12 pb-3 md:pb-4 flex items-center justify-between transition-colors duration-300 ${(isScrolled || searchQuery.trim() || activeTab !== 'home') ? 'bg-black shadow-lg' : 'bg-gradient-to-b from-black/80 to-transparent'}`}
          style={{ paddingTop: 'max(env(safe-area-inset-top), 16px)' }}
        >
          {/* Mode recherche plein header (mobile) : le champ prend toute la
              largeur avec un bouton fermer, sans écraser les autres commandes. */}
          {isSearchOpen && (
            <div className="flex md:hidden items-center gap-2 w-full">
              <div className="relative flex items-center flex-1 min-w-0">
                <Search className="w-4 h-4 text-white absolute left-3 pointer-events-none" />
                <input
                  ref={searchInputRef}
                  type="text"
                  placeholder="Rechercher un titre..."
                  aria-label="Rechercher un titre"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="w-full bg-zinc-900/80 border border-zinc-700 text-white text-sm rounded-full pl-9 pr-4 py-2 focus:outline-none focus:border-zinc-500 focus:bg-black placeholder:text-zinc-500"
                />
              </div>
              <button
                onClick={() => { setIsSearchOpen(false); setSearchQuery(''); }}
                aria-label="Fermer la recherche"
                className="p-2 rounded-full hover:bg-zinc-800/50 transition-colors shrink-0"
              >
                <X className="w-6 h-6 text-white" />
              </button>
            </div>
          )}

          {/* En-tête normal — masqué sur mobile pendant la recherche */}
          <div className={`${isSearchOpen ? 'hidden md:flex' : 'flex'} items-center gap-6 md:gap-10 min-w-0`}>
            <h1
              className="text-lg md:text-3xl font-black text-red-600 tracking-tighter cursor-pointer active:scale-95 transition-transform truncate"
              onClick={() => {
                setActiveTab('home');
                setSearchQuery('');
                setIsSearchOpen(false);
                setSortBy('alpha');
                setFilterGenre('all');
                setFilterResolution('all');
                setSelectedPlaylist(null);
              }}
            >
              LOCALSTREAM
            </h1>
          </div>
          <div className={`${isSearchOpen ? 'hidden md:flex' : 'flex'} items-center gap-1 md:gap-4 shrink-0`}>
            {isFetchingMetadata && (
              <RefreshCw className="w-4 h-4 animate-spin text-red-500 shrink-0" aria-label="Récupération des métadonnées TMDB en cours" />
            )}
            {/* Desktop : champ toujours affiché (place suffisante) */}
            {videos.length > 0 && (
              <div className="relative hidden md:flex items-center">
                <Search className="w-5 h-5 text-white absolute left-3" />
                <input
                  type="text"
                  placeholder="Titre..."
                  aria-label="Rechercher un titre"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="bg-zinc-900/80 border border-zinc-700 text-white text-sm rounded-full pl-10 pr-4 py-1.5 focus:outline-none focus:border-zinc-500 focus:bg-black transition-all w-64 placeholder:text-zinc-500"
                />
              </div>
            )}
            {/* Mobile : icône loupe qui ouvre la recherche plein header */}
            {videos.length > 0 && (
              <button
                onClick={() => setIsSearchOpen(true)}
                aria-label="Rechercher"
                className="md:hidden p-2 rounded-full hover:bg-zinc-800/50 transition-colors"
              >
                <Search className="w-5 h-5 text-white" />
              </button>
            )}
            <button
              onClick={() => fileInputRef.current?.click()}
              aria-label="Changer de dossier"
              className="text-sm font-medium text-white hover:text-zinc-300 transition flex items-center gap-2 md:bg-transparent p-2 md:p-0 rounded-full md:rounded-none"
            >
              <FolderOpen className="w-5 h-5 md:hidden" />
              <span className="hidden md:inline">Changer de dossier</span>
            </button>
            <button
              onClick={() => {
                setShowSettings(true);
                if (Capacitor.isNativePlatform()) {
                  VideoLauncher.getList()
                    .then(res => setExternalPlayers(res.players))
                    .catch(err => console.error("Error fetching players", err));
                }
              }}
              aria-label="Paramètres"
              className="p-2 rounded-full hover:bg-zinc-800/50 transition-colors"
            >
              <Settings className="w-5 h-5 text-white" />
            </button>
          </div>
        </header>
      )}

      {/* Main Content */}
      <main className="pb-24 md:pb-12">
        {currentVideo ? (
          <div className="fixed inset-0 bg-black z-50 flex flex-col">
            <div 
              className="absolute top-0 left-0 right-0 px-4 pb-4 bg-gradient-to-b from-black/80 to-transparent z-10 flex items-center justify-between"
              style={{ paddingTop: 'max(env(safe-area-inset-top), 16px)' }}
            >
              <button
                onClick={() => { savePlaybackPosition(); setCurrentVideo(null); }}
                aria-label="Retour"
                className="p-2 rounded-full hover:bg-zinc-800 transition-colors bg-black/40 md:bg-transparent"
              >
                <ChevronLeft className="w-6 h-6 md:w-8 md:h-8" />
              </button>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => {
                    setShowSubtitlesModal(true);
                    if (subtitles.length === 0) searchSubtitles();
                  }}
                  aria-label="Sous-titres"
                  className="p-2 rounded-full hover:bg-zinc-800 transition-colors flex items-center gap-2 bg-black/40 md:bg-transparent"
                >
                  <Subtitles className="w-5 h-5 md:w-6 md:h-6" />
                </button>
              </div>
            </div>
            
            <div className="flex-1 flex items-center justify-center bg-black relative group">
              <video 
                ref={videoRef}
                src={currentVideo.url} 
                controls
                autoPlay
                onEnded={handleVideoEnded}
                onTimeUpdate={handleTimeUpdate}
                onPause={savePlaybackPosition}
                onLoadedMetadata={handleLoadedMetadata}
                crossOrigin="anonymous"
                className="w-full h-full max-h-screen object-contain"
              >
                {activeSubtitleUrl && (
                  <track 
                    kind="subtitles" 
                    src={activeSubtitleUrl} 
                    srcLang="fr" 
                    label="Français" 
                    default 
                  />
                )}
              </video>

              {/* Touch Controls Overlay - utilise onTouchEnd pour Android */}
              <div 
                className="absolute inset-0 flex z-30"
                style={{ bottom: '60px' }}
              >
                {/* Zone gauche - Reculer 10s */}
                <div 
                  className="flex-1 h-full flex items-center justify-center"
                  onTouchEnd={(e) => {
                    e.stopPropagation();
                    e.preventDefault();
                    if (videoRef.current) {
                      videoRef.current.currentTime = Math.max(0, videoRef.current.currentTime - 10);
                      setPlayerFeedback({type: 'rewind', visible: true});
                      setTimeout(() => setPlayerFeedback(prev => ({...prev, visible: false})), 700);
                    }
                  }}
                />
                {/* Zone centre - Pause/Play */}
                <div 
                  className="w-[34%] h-full flex items-center justify-center"
                  onTouchEnd={(e) => {
                    e.stopPropagation();
                    e.preventDefault();
                    if (videoRef.current) {
                      if (videoRef.current.paused) {
                        videoRef.current.play();
                        setPlayerFeedback({type: 'play', visible: true});
                      } else {
                        videoRef.current.pause();
                        setPlayerFeedback({type: 'pause', visible: true});
                      }
                      setTimeout(() => setPlayerFeedback(prev => ({...prev, visible: false})), 700);
                    }
                  }}
                />
                {/* Zone droite - Avancer 10s */}
                <div 
                  className="flex-1 h-full flex items-center justify-center"
                  onTouchEnd={(e) => {
                    e.stopPropagation();
                    e.preventDefault();
                    if (videoRef.current) {
                      videoRef.current.currentTime = Math.min(videoRef.current.duration || 0, videoRef.current.currentTime + 10);
                      setPlayerFeedback({type: 'forward', visible: true});
                      setTimeout(() => setPlayerFeedback(prev => ({...prev, visible: false})), 700);
                    }
                  }}
                />
              </div>

              {/* Feedback UI */}
              {playerFeedback.visible && (
                <div className="absolute inset-0 flex items-center justify-center pointer-events-none z-50">
                  <div className="bg-black/60 backdrop-blur-md rounded-full p-8 animate-out fade-out zoom-out duration-500">
                    {playerFeedback.type === 'rewind' && <RotateCcw className="w-12 h-12 text-white animate-in slide-in-from-right-4" />}
                    {playerFeedback.type === 'forward' && <RotateCw className="w-12 h-12 text-white animate-in slide-in-from-left-4" />}
                    {playerFeedback.type === 'pause' && <Pause className="w-12 h-12 text-white scale-110" />}
                    {playerFeedback.type === 'play' && <Play className="w-12 h-12 text-white scale-110 fill-white" />}
                  </div>
                </div>
              )}
            </div>
          </div>
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
                    onClick={startNativeScan}
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
                {/* Filter and Sort Bar - Only show when searching or in library view */}
                {(isLibraryViewActive || searchQuery.trim()) && activeTab === 'home' && (
                  <div 
                    className="px-4 md:px-12 pt-28 pb-6 flex flex-col md:flex-row items-center gap-3 z-30 w-full animate-in fade-in slide-in-from-top-4 duration-500"
                    style={{ marginTop: 'max(env(safe-area-inset-top), 0px)' }}
                  >
                    <div className="flex flex-row flex-wrap pb-2 md:pb-0 w-full gap-2 md:gap-4">
                      
                      <div className="flex items-center gap-2 bg-zinc-900/80 backdrop-blur-md border border-zinc-700/50 rounded-full px-4 py-2 flex-shrink-0 shadow-lg">
                        <span className="text-zinc-400 font-medium text-xs uppercase tracking-wider hidden sm:inline">Trier par:</span>
                        <select 
                          value={sortBy} 
                          onChange={(e) => setSortBy(e.target.value as any)}
                          className="bg-transparent text-white font-bold focus:outline-none cursor-pointer text-sm"
                        >
                          <option value="alpha" className="bg-zinc-900 text-white">A-Z</option>
                          <option value="date" className="bg-zinc-900 text-white">Date d'ajout</option>
                          <option value="size" className="bg-zinc-900 text-white">Taille</option>
                          <option value="duration" className="bg-zinc-900 text-white">Durée</option>
                        </select>
                      </div>
                      
                      <div className="flex items-center gap-2 bg-zinc-900/80 backdrop-blur-md border border-zinc-700/50 rounded-full px-4 py-2 flex-shrink-0 shadow-lg">
                        <span className="text-zinc-400 font-medium text-xs uppercase tracking-wider hidden sm:inline">Genre:</span>
                        <select 
                          value={filterGenre} 
                          onChange={(e) => setFilterGenre(e.target.value === 'all' ? 'all' : Number(e.target.value))}
                          className="bg-transparent text-white font-bold focus:outline-none cursor-pointer max-w-[120px] md:max-w-[200px] truncate text-sm"
                        >
                          <option value="all" className="bg-zinc-900 text-white">Tous les genres</option>
                          {Object.entries(TMDB_GENRES).map(([id, name]) => (
                            <option key={id} value={id} className="bg-zinc-900 text-white">{name}</option>
                          ))}
                        </select>
                      </div>
  
                      <div className="flex items-center gap-2 bg-zinc-900/80 backdrop-blur-md border border-zinc-700/50 rounded-full px-4 py-2 flex-shrink-0 shadow-lg">
                        <span className="text-zinc-400 font-medium text-xs uppercase tracking-wider hidden sm:inline">Qualité:</span>
                        <select 
                          value={filterResolution} 
                          onChange={(e) => setFilterResolution(e.target.value)}
                          className="bg-transparent text-white font-bold focus:outline-none cursor-pointer text-sm"
                        >
                          <option value="all" className="bg-zinc-900 text-white">Toutes</option>
                          <option value="4k" className="bg-zinc-900 text-white">4K / 2160p</option>
                          <option value="1080p" className="bg-zinc-900 text-white">1080p</option>
                          <option value="720p" className="bg-zinc-900 text-white">720p</option>
                          <option value="sd" className="bg-zinc-900 text-white">SD</option>
                        </select>
                      </div>
  
                    </div>
                  </div>
                )}

                {activeTab === 'playlists' ? (
                  <div className="px-4 md:px-12 min-h-screen pt-20">
                    {selectedPlaylist ? (
                      <div>
                        <div className="flex items-center gap-4 mb-8">
                          <button onClick={() => setSelectedPlaylist(null)} className="p-2 bg-zinc-800 rounded-full hover:bg-zinc-700 transition">
                            <ChevronLeft className="w-6 h-6" />
                          </button>
                          <h2 className="text-2xl md:text-3xl font-bold text-white">{selectedPlaylist.name}</h2>
                          <div className="ml-auto flex items-center gap-2 md:gap-4">
                            {selectedPlaylist.videoNames.length > 0 && (
                              <button 
                                onClick={() => {
                                  const firstVideo = groupedVideos.find(v => v.name === selectedPlaylist.videoNames[0]);
                                  if (firstVideo) playVideo(firstVideo, selectedPlaylist, 0);
                                }} 
                                className="p-2 md:px-4 md:py-2 bg-white text-black rounded hover:bg-white/80 transition flex items-center gap-2 font-bold"
                              >
                                <Play className="w-5 h-5 fill-black" />
                                <span className="hidden md:inline">Tout lire</span>
                              </button>
                            )}
                            <button
                              onClick={() => setConfirmDialog({
                                message: `Supprimer la liste "${selectedPlaylist.name}" ? Cette action est irréversible.`,
                                onConfirm: () => deletePlaylist(selectedPlaylist.id),
                              })}
                              aria-label="Supprimer la liste"
                              className="p-2 bg-red-600/20 text-red-500 rounded hover:bg-red-600/40 transition flex items-center gap-2"
                            >
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
                              
                              const isWatched = video.isSeriesGroup 
                                ? (video.episodes && video.episodes.length > 0 && video.episodes.every(ep => !!watchedVideos[ep.name]))
                                : !!watchedVideos[video.name];

                              return (
                                <div
                                  key={videoName}
                                  className="group flex flex-col"
                                  onClick={() => handleOpenInfoModal(video)}
                                >
                                  <div className={`relative aspect-[2/3] bg-zinc-800 rounded-md overflow-hidden cursor-pointer transition-transform duration-300 group-hover:scale-105 group-hover:z-30 shadow-lg`}>
                                    {posters[video.name] ? (
                                      <img src={posters[video.name]} alt={getCleanTitle(video.name)} className="w-full h-full object-cover" loading="lazy" decoding="async" />
                                    ) : (
                                      <div className="w-full h-full flex items-center justify-center p-4 text-center">
                                        <span className="text-zinc-500 font-medium text-sm">{getCleanTitle(video.name)}</span>
                                      </div>
                                    )}
                                    <div className="absolute inset-0 bg-black/60 opacity-0 group-hover:opacity-100 transition-opacity duration-300 flex items-center justify-center p-4">
                                      <button onClick={(e) => { e.stopPropagation(); playVideo(video, selectedPlaylist, index); }} className="bg-white text-black p-3 rounded-full hover:bg-white/80">
                                        <Play className="w-6 h-6 fill-black" />
                                      </button>
                                      <button
                                        onClick={(e) => { e.stopPropagation(); removeVideoFromPlaylist(selectedPlaylist.id, video.name); }}
                                        aria-label="Retirer de la liste"
                                        className="absolute top-2 right-2 p-2 bg-red-600/80 rounded-full hover:bg-red-600 transition"
                                      >
                                        <X className="w-3.5 h-3.5 text-white" />
                                      </button>
                                    </div>
                                    {watchProgress[video.name] > 0 && (
                                      <div className="absolute bottom-0 left-0 right-0 h-1 bg-zinc-600">
                                        <div className="h-full bg-red-600" style={{ width: `${watchProgress[video.name]}%` }} />
                                      </div>
                                    )}
                                  </div>
                                  <div className="mt-2 px-1">
                                    <p className="text-[10px] md:text-xs font-medium text-zinc-400 break-words line-clamp-none">
                                      {getCleanTitle(video.name)}
                                    </p>
                                  </div>
                                </div>
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
                                onClick={() => setSelectedPlaylist(playlist)}
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
                ) : activeTab === 'history' ? (
                  <div className="px-4 md:px-12 min-h-screen pt-20">
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-8">
                      <h2 className="text-2xl md:text-3xl font-bold text-white">Contenus vus</h2>
                      
                      <div className="flex gap-2 w-full sm:w-auto max-w-md">
                        <input
                          type="text"
                          placeholder="Ajouter un film ou une série..."
                          value={newHistoryItemName}
                          onChange={(e) => setNewHistoryItemName(e.target.value)}
                          className="flex-1 bg-zinc-900 border border-white/10 rounded-xl px-4 py-2 text-sm text-white focus:border-red-600 focus:outline-none transition-all placeholder:text-zinc-500"
                          onKeyDown={(e) => e.key === 'Enter' && handleAddHistory()}
                        />
                        <button
                          onClick={handleAddHistory}
                          disabled={!newHistoryItemName.trim()}
                          className="bg-red-600 hover:bg-red-700 disabled:opacity-30 text-white px-4 py-2 rounded-xl text-xs font-black transition-all active:scale-95 shadow-lg shadow-red-900/20 whitespace-nowrap"
                        >
                          AJOUTER
                        </button>
                      </div>
                    </div>
                    {(() => {
                      const displayedHistoryItems = searchQuery.trim()
                        ? historyItems.filter(item => item.name.toLowerCase().includes(searchQuery.toLowerCase()))
                        : historyItems;

                      if (historyItems.length === 0) {
                        return (
                          <div className="text-center text-zinc-500 mt-12 flex flex-col items-center gap-4">
                            <History className="w-16 h-16 text-zinc-700" />
                            <p>Votre historique est vide.</p>
                          </div>
                        );
                      }

                      if (displayedHistoryItems.length === 0) {
                        return (
                          <div className="text-center text-zinc-500 mt-12">
                            Aucun contenu correspondant dans votre historique.
                          </div>
                        );
                      }

                      return (
                        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
                          {displayedHistoryItems.map((item) => (
                            <div
                              key={item.id}
                              className="group flex flex-col"
                              onClick={() => item.isLocal && item.video && handleOpenInfoModal(item.video)}
                            >
                              <div className={`relative aspect-[2/3] bg-zinc-800 rounded-md overflow-hidden transition-transform duration-300 shadow-lg ${(item.isLocal || item.isForcedAvailable) ? 'cursor-pointer group-hover:scale-105 group-hover:z-30' : 'opacity-60'}`}>
                                {posters[item.id] ? (
                                  <img src={posters[item.id]} alt={getCleanTitle(item.name)} className={`w-full h-full object-cover ${(!item.isLocal && !item.isForcedAvailable) ? 'grayscale opacity-50' : ''}`} loading="lazy" decoding="async" />
                                ) : (
                                  <div className="w-full h-full flex items-center justify-center p-4 text-center">
                                    <span className="text-zinc-500 font-medium text-sm">{getCleanTitle(item.name)}</span>
                                  </div>
                                )}

                                {!item.isLocal && !item.isForcedAvailable && (
                                  <div className="absolute inset-0 flex flex-col items-center justify-center bg-black/60 z-20">
                                    <CloudOff className="w-8 h-8 text-zinc-400 mb-2" />
                                    <span className="text-xs font-bold text-zinc-400 uppercase tracking-widest text-center px-2">Non disponible</span>
                                  </div>
                                )}

                                {!item.isLocal && (
                                  <button
                                    onClick={(e) => { e.stopPropagation(); toggleForceAvailable(item.id); }}
                                    className={`absolute top-2 left-2 z-40 p-2 rounded-full transition ${item.isForcedAvailable ? 'bg-green-600/90 text-white' : 'bg-zinc-800/90 text-zinc-400'}`}
                                    title={item.isForcedAvailable ? "Rendre indisponible" : "Rendre disponible"}
                                    aria-label={item.isForcedAvailable ? "Rendre indisponible" : "Rendre disponible"}
                                  >
                                    {item.isForcedAvailable ? <Cloud className="w-3.5 h-3.5" /> : <CloudOff className="w-3.5 h-3.5" />}
                                  </button>
                                )}

                                <div className={`absolute inset-0 bg-black/60 opacity-0 group-hover:opacity-100 transition-opacity duration-300 flex items-center justify-center p-4 ${!item.isLocal ? 'z-30' : ''}`}>
                                  {item.isLocal && (
                                    <button onClick={(e) => { e.stopPropagation(); if (item.video) playVideo(item.video); }} className="bg-white text-black p-3 rounded-full hover:bg-white/80">
                                      <Play className="w-6 h-6 fill-black" />
                                    </button>
                                  )}
                                  <button
                                    onClick={(e) => { e.stopPropagation(); toggleWatched(item.id); }}
                                    className={`absolute top-2 right-2 p-2 bg-red-600/80 rounded-full hover:bg-red-600 transition`}
                                    title="Retirer de l'historique"
                                    aria-label="Retirer de l'historique"
                                  >
                                    <Trash2 className="w-3.5 h-3.5 text-white" />
                                  </button>
                                </div>
                              </div>
                              <div className="mt-2 px-1">
                                <p className="text-[10px] md:text-xs font-medium text-zinc-400 break-words line-clamp-none">
                                  {getCleanTitle(item.name)}
                                </p>
                              </div>
                            </div>
                          ))}
                        </div>
                      );
                    })()}
                  </div>
                ) : searchQuery.trim() ? (
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

                          return (
                            <div
                              key={video.nativeUri || video.path || video.name}
                              className="group flex flex-col"
                              onClick={() => handleOpenInfoModal(video)}
                            >
                              <div className={`relative aspect-[2/3] bg-zinc-800 rounded-md overflow-hidden cursor-pointer transition-transform duration-300 group-hover:scale-105 group-hover:z-30 shadow-lg`}>
                                {(video.isSeriesGroup 
                                    ? (video.episodes && video.episodes.length > 0 && video.episodes.every(ep => !!watchedVideos[ep.name]))
                                    : !!watchedVideos[video.name]
                                  ) && (
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
                                  <div className="absolute bottom-2 left-2 z-10 bg-black/70 text-white text-[10px] font-black px-1.5 py-0.5 rounded border border-white/20 uppercase tracking-tighter">
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
                ) : isLibraryViewActive ? (
                  <div className="px-4 md:px-12 min-h-screen">
                    <h2 className="text-2xl md:text-3xl font-bold text-white mb-8">
                      Bibliothèque
                    </h2>
                    {filteredAndSortedVideos.length > 0 ? (
                      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
                        {filteredAndSortedVideos.map((video) => {
                          const isWatched = video.isSeriesGroup
                            ? (video.episodes && video.episodes.length > 0 && video.episodes.every(ep => !!watchedVideos[ep.name]))
                            : !!watchedVideos[video.name];

                          return (
                            <div
                              key={video.nativeUri || video.path || video.name}
                              className="group relative aspect-[2/3] bg-zinc-800 rounded-md overflow-hidden cursor-pointer transition-transform duration-300 hover:scale-105 hover:z-30 shadow-lg"
                              onClick={() => handleOpenInfoModal(video)}
                            >
                            {(video.isSeriesGroup 
                                ? (video.episodes && video.episodes.length > 0 && video.episodes.every(ep => !!watchedVideos[ep.name]))
                                : !!watchedVideos[video.name]
                              ) && (
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
                              <button onClick={(e) => { e.stopPropagation(); playVideo(video); }} className="bg-white text-black p-3 rounded-full hover:bg-white/80 mb-2">
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
                ) : (
                  <>
                    {/* Hero Section */}
                    {heroVideo && (
                      <div className="relative h-[60vh] md:h-[80vh] w-full mb-8">
                        <div className="absolute inset-0">
                          {backdrops[heroVideo.name] || posters[heroVideo.name] ? (
                            <img
                              src={backdrops[heroVideo.name] || posters[heroVideo.name]}
                              alt={heroVideo.name}
                              decoding="async"
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
                              onClick={() => playVideo(heroVideo)} 
                              className="bg-white text-black px-4 md:px-8 py-2 md:py-3 rounded flex items-center justify-center gap-2 font-bold text-sm md:text-lg hover:bg-white/80 transition flex-1 md:flex-none"
                            >
                              <Play className="w-5 h-5 md:w-6 md:h-6 fill-black" /> Lecture
                            </button>
                            <button 
                              onClick={() => handleOpenInfoModal(heroVideo)}
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
                      {/* Bannière onboarding TMDB : sans clé, aucune affiche/synopsis
                          et rien ne l'explique — on guide vers les Paramètres. */}
                      {!tmdbApiKey && !tmdbBannerDismissed && groupedVideos.length > 0 && (
                        <div className="mx-4 md:mx-12 mb-6 bg-zinc-900/95 border border-zinc-700 rounded-2xl p-4 flex items-start gap-3 shadow-xl">
                          <ImageIcon className="w-5 h-5 text-red-500 shrink-0 mt-0.5" />
                          <div className="flex-1 min-w-0">
                            <p className="text-sm text-white font-bold mb-1">Ajoutez les affiches et synopsis</p>
                            <p className="text-xs text-zinc-400 leading-relaxed mb-3">
                              Configurez une clé API TMDB (gratuite) pour récupérer automatiquement les affiches, résumés et regroupements en sagas.
                            </p>
                            <button
                              onClick={() => setShowSettings(true)}
                              className="bg-red-600 hover:bg-red-700 text-white text-xs font-black px-4 py-2 rounded-lg transition-colors active:scale-95"
                            >
                              Configurer TMDB
                            </button>
                          </div>
                          <button
                            onClick={() => { setTmdbBannerDismissed(true); safeSetItem('tmdbBannerDismissed', '1'); }}
                            aria-label="Masquer cette bannière"
                            className="p-1.5 rounded-full hover:bg-zinc-800 text-zinc-500 shrink-0"
                          >
                            <X className="w-4 h-4" />
                          </button>
                        </div>
                      )}
                      {inProgressVideos.length > 0 && (
                        <VideoRow title="Continuer la lecture" items={inProgressVideos} {...rowProps} />
                      )}
                      <VideoRow title="Nouveautés" items={recentAdditions.slice(0, 15)} {...rowProps} />
                      <VideoRow title="Recommandations" items={recommendations.slice(0, 15)} {...rowProps} />
                      <VideoRow title="Séries" items={tvShows} {...rowProps} />
                      <VideoRow title="Films" items={movies} {...rowProps} />
                      
                      {/* Dossiers */}
                      {folderNames.map(folderName => {
                        const isSystemFolder = ['Movies', 'Download', 'Downloads', 'Documents', 'Racine'].includes(folderName);
                        if (isSystemFolder) return null;
                        
                        return folders[folderName].length > 0 && (
                          <VideoRow key={folderName} title={`Dossier : ${folderName}`} items={folders[folderName]} {...rowProps} />
                        );
                      })}

                      <VideoRow title="De A à Z" items={alphabetical} {...rowProps} />
                    </div>
                  </>
                )
              }
              </div>
            )
          }
          </div>
        )
      }

      {videos.length > 0 && !currentVideo && (
        <BottomNav
          activeTab={activeTab}
          isLibraryViewActive={isLibraryViewActive}
          onHome={() => {
            setActiveTab('home');
            setSearchQuery('');
            setSortBy('alpha');
            setFilterGenre('all');
            setFilterResolution('all');
            setSelectedPlaylist(null);
          }}
          onLibrary={() => {
            setActiveTab('home');
            setSortBy('date');
          }}
          onPlaylists={() => setActiveTab('playlists')}
          onHistory={() => setActiveTab('history')}
        />
      )}
    </main>

      {/* Hidden File Input */}
      <input 
        type="file" 
        ref={fileInputRef}
        onChange={handleFolderSelect}
        className="hidden"
        multiple
        {...({ webkitdirectory: "true", directory: "true", accept: "video/*" } as any)}
      />

      {/* Info Modal */}
      {infoVideo && (
        <div
          className="fixed inset-0 bg-black/95 md:bg-black/80 backdrop-blur-md z-50 flex items-center justify-center p-0 md:p-12 overflow-y-auto"
          onClick={() => { setInfoVideo(null); setExpandedEpisode(null); }}
        >
          <div
            className="bg-zinc-950 md:rounded-3xl w-full h-full md:h-auto md:max-h-[90vh] md:max-w-6xl overflow-hidden shadow-[0_0_100px_rgba(0,0,0,0.8)] relative animate-in fade-in zoom-in-95 duration-500 flex flex-col border border-white/5"
            onClick={(e) => e.stopPropagation()}
          >
            
            {/* Header / Hero Section */}
            <div className="relative w-full shrink-0" style={{ height: 'max(48vh, 260px)' }}>
              <div className="absolute inset-0">
                {backdrops[infoVideo.isSeriesGroup ? infoVideo.seriesName! : infoVideo.name] || posters[infoVideo.isSeriesGroup ? infoVideo.seriesName! : infoVideo.name] ? (
                  <img 
                    src={backdrops[infoVideo.isSeriesGroup ? infoVideo.seriesName! : infoVideo.name] || posters[infoVideo.isSeriesGroup ? infoVideo.seriesName! : infoVideo.name]} 
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
                onClick={() => { setInfoVideo(null); setExpandedEpisode(null); }}
                aria-label="Fermer"
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
                  
                   {/* Grille 2 colonnes sur mobile : hauteurs homogènes, pas de
                       débordement quand un label passe sur 2 lignes. */}
                   <div className="grid grid-cols-2 md:flex md:flex-wrap md:items-center gap-2 md:gap-4">
                    <button
                      onClick={() => playVideo(infoVideo)}
                      className="bg-white text-black px-4 md:px-10 py-2.5 md:py-4 rounded-xl flex items-center justify-center gap-2 font-black text-sm md:text-xl hover:bg-zinc-200 active:scale-95 transition-all shadow-xl group"
                    >
                      <Play className="w-5 h-5 md:w-7 md:h-7 fill-black group-hover:scale-110 transition-transform shrink-0" />
                      {(() => {
                        const target = (infoVideo.isSeriesGroup && infoVideo.episodes)
                          ? (infoVideo.episodes.find(ep => !watchedVideos[ep.name]) || infoVideo.episodes[0])
                          : infoVideo;
                        const pos = watchPositions[target.name] || 0;
                        const pct = watchProgress[target.name] || 0;
                        const isTargetWatched = !!watchedVideos[target.name];
                        // Même seuil que le lecteur (handleLoadedMetadata) : au-delà
                        // de 95 % on repart du début, donc pas de label "REPRENDRE".
                        return (pos > 0 && !isTargetWatched && pct < 95) ? `REPRENDRE À ${formatDuration(pos / 1000)}` : 'LECTURE';
                      })()}
                    </button>

                    <button
                      onClick={() => toggleWatched(infoVideo.isSeriesGroup ? infoVideo.seriesName! : infoVideo.name)}
                      className={`px-4 md:px-8 py-2.5 md:py-4 rounded-xl flex items-center justify-center gap-2 font-black text-sm md:text-xl transition-all border shadow-lg active:scale-95 ${
                        (infoVideo.isSeriesGroup 
                          ? (infoVideo.episodes && infoVideo.episodes.length > 0 && infoVideo.episodes.every(ep => !!watchedVideos[ep.name]))
                          : !!watchedVideos[infoVideo.name]
                        ) ? 'bg-green-600 border-green-400 text-white shadow-green-900/20' : 'bg-white/5 border-white/10 text-white hover:bg-white/20 hover:border-white/30'
                      }`}
                    >
                      <Check className={`w-5 h-5 md:w-7 md:h-7 ${(infoVideo.isSeriesGroup 
                          ? (infoVideo.episodes && infoVideo.episodes.length > 0 && infoVideo.episodes.every(ep => !!watchedVideos[ep.name]))
                          : !!watchedVideos[infoVideo.name]
                        ) ? 'stroke-[4px]' : 'opacity-40'}`} />
                      {(infoVideo.isSeriesGroup 
                          ? (infoVideo.episodes && infoVideo.episodes.length > 0 && infoVideo.episodes.every(ep => !!watchedVideos[ep.name]))
                          : !!watchedVideos[infoVideo.name]
                        ) ? 'VU' : 'MARQUER VU'}
                    </button>

                    {/* Bouton recharger TMDB — visible avec label */}
                    <button 
                      onClick={() => fetchSingleMetadata(infoVideo)}
                      disabled={isRefreshingMetadata}
                      className="px-4 py-2.5 md:py-4 bg-white/5 border border-white/10 rounded-xl text-white hover:bg-white/20 active:scale-95 transition-all shadow-xl flex items-center justify-center gap-2 font-bold text-sm disabled:opacity-50"
                      title="Recharger les données TMDB"
                    >
                      <RefreshCw className={`w-4 h-4 ${isRefreshingMetadata ? 'animate-spin text-red-400' : 'text-zinc-300'}`} />
                      <span className="hidden sm:inline">{isRefreshingMetadata ? 'Chargement…' : 'TMDB'}</span>
                    </button>
                    
                    <div className="relative md:flex-none">
                      <button
                        onClick={() => setShowPlaylistSelector(!showPlaylistSelector)}
                        className="w-full h-full md:w-auto md:h-auto px-4 py-2.5 md:py-4 bg-white/5 border border-white/10 rounded-xl text-white hover:bg-white/20 active:scale-95 transition-all shadow-xl flex items-center justify-center gap-2 font-bold text-sm"
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
                                    onClick={() => toggleVideoInPlaylist(playlist.id, infoVideo.name)}
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
                      {overviews[infoVideo.isSeriesGroup ? infoVideo.seriesName! : infoVideo.name] || 
                        (!tmdbApiKey ? "Veuillez configurer votre clé API TMDB dans les paramètres pour voir les résumés." : "Synopsis non disponible sur TMDB.")
                      }
                    </p>
                    {overviews[infoVideo.isSeriesGroup ? infoVideo.seriesName! : infoVideo.name] && overviews[infoVideo.isSeriesGroup ? infoVideo.seriesName! : infoVideo.name].length > 150 && (
                      <button 
                        onClick={() => setIsSynopsisExpanded(!isSynopsisExpanded)}
                        className="text-red-500 font-black text-[10px] md:text-xs uppercase tracking-[0.2em] border-b border-red-600/30 pb-0.5 hover:text-white hover:border-white transition-all"
                      >
                        {isSynopsisExpanded ? '↑ RÉDUIRE' : '↓ LIRE LA SUITE'}
                      </button>
                    )}
                  </div>
                  
                  {videoGenres[infoVideo.isSeriesGroup ? infoVideo.seriesName! : infoVideo.name] && (
                    <div className="flex flex-wrap gap-2">
                      {videoGenres[infoVideo.isSeriesGroup ? infoVideo.seriesName! : infoVideo.name].map(id => (
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
                    {releaseDates[infoVideo.isSeriesGroup ? infoVideo.seriesName! : infoVideo.name] && (
                      <div className="bg-white/5 p-4 rounded-2xl border border-white/5">
                        <p className="text-[10px] text-zinc-500 uppercase font-black tracking-widest mb-1.5 opacity-50">Sortie</p>
                        <p className="text-sm text-white font-black">{new Date(releaseDates[infoVideo.isSeriesGroup ? infoVideo.seriesName! : infoVideo.name]).getFullYear()}</p>
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
                                setSelectedSeason(s);
                                setExpandedEpisode(null);
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
                          key={ep.name}
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
                                  onClick={(e) => { e.stopPropagation(); playVideo(ep); }}
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
                            
                            <div className="min-w-0 flex-1 cursor-pointer py-1" onClick={() => setExpandedEpisode(isExpanded ? null : ep.name)}>
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
                                      onClick={(e) => { e.stopPropagation(); resetProgress(ep.name); }}
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
                                  toggleWatched(ep.name);
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
                                      onClick={() => playVideo(ep)}
                                      className="bg-white text-black px-8 py-3 rounded-xl font-black text-xs uppercase tracking-widest hover:bg-zinc-200 transition-all flex items-center gap-2 shadow-xl"
                                    >
                                      <Play className="w-4 h-4 fill-black" /> Regarder
                                    </button>
                                    <button 
                                      onClick={() => toggleWatched(ep.name)}
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
      )}

      {/* Settings Modal */}
      {showSettings && (
        <div
          className="fixed inset-0 bg-black/80 backdrop-blur-sm z-50 flex items-center justify-center p-4"
          onClick={() => setShowSettings(false)}
        >
          <div
            className="bg-zinc-900 border border-zinc-800 rounded-lg w-full max-w-md max-h-[90vh] flex flex-col overflow-hidden shadow-2xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between p-4 border-b border-zinc-800 shrink-0">
              <h3 className="text-lg font-semibold">Paramètres</h3>
              <button onClick={() => setShowSettings(false)} aria-label="Fermer" className="p-2 rounded-full hover:bg-zinc-800 transition-colors">
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="p-6 space-y-4 flex-1 overflow-y-auto">
              <div>
                <h4 className="text-sm font-bold text-zinc-400 uppercase tracking-wider mb-3 flex items-center gap-2">
                  <Subtitles className="w-4 h-4" /> OpenSubtitles
                </h4>
                <div className="space-y-3">
                  <input type="text" value={osApiKey} onChange={(e) => setOsApiKey(e.target.value)} placeholder="Clé API" className="w-full bg-zinc-950 border border-zinc-800 rounded px-4 py-2 text-white focus:border-red-600 focus:outline-none" />
                  <input type="text" value={osUsername} onChange={(e) => setOsUsername(e.target.value)} placeholder="Nom d'utilisateur" className="w-full bg-zinc-950 border border-zinc-800 rounded px-4 py-2 text-white focus:border-red-600 focus:outline-none" />
                  <input type="password" value={osPassword} onChange={(e) => setOsPassword(e.target.value)} placeholder="Mot de passe" className="w-full bg-zinc-950 border border-zinc-800 rounded px-4 py-2 text-white focus:border-red-600 focus:outline-none" />
                  <button onClick={loginOpenSubtitles} disabled={isLoggingIn} className="w-full bg-red-600 hover:bg-red-700 disabled:opacity-50 text-white font-bold py-2 rounded transition-colors flex items-center justify-center gap-2">
                    {isLoggingIn ? <div className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin" /> : <LogIn className="w-4 h-4" />}
                    Se connecter
                  </button>
                  {osToken && <div className="text-xs text-emerald-500 text-center">Connecté avec succès.</div>}
                </div>
              </div>

               <div className="pt-4 border-t border-zinc-800">
                <h4 className="text-sm font-bold text-zinc-400 uppercase tracking-wider mb-3 flex items-center gap-2">
                  <Play className="w-4 h-4" /> Lecteur Vidéo
                </h4>
                <div className="space-y-3">
                  <div className="grid grid-cols-2 gap-2">
                    <button 
                      onClick={() => setVideoPlayer('internal')}
                      className={`py-2 px-4 rounded font-bold text-sm transition ${videoPlayer === 'internal' ? 'bg-red-600 text-white' : 'bg-zinc-800 text-zinc-400 hover:bg-zinc-700'}`}
                    >
                      Interne
                    </button>
                    <button 
                      onClick={() => setVideoPlayer('external')}
                      className={`py-2 px-4 rounded font-bold text-sm transition ${videoPlayer === 'external' ? 'bg-red-600 text-white' : 'bg-zinc-800 text-zinc-400 hover:bg-zinc-700'}`}
                    >
                      Externe
                    </button>
                  </div>

                  {videoPlayer === 'external' && Capacitor.isNativePlatform() && (
                    <div className="space-y-4">
                       <div className="flex items-center justify-between">
                        <label className="text-xs text-zinc-500 block">Choisissez votre lecteur préféré :</label>
                        <button 
                          onClick={() => {
                            VideoLauncher.getList()
                              .then(res => {
                                setExternalPlayers(res.players);
                              })
                              .catch(err => {
                                console.error("Error refreshing players", err);
                              });
                          }}
                          className="text-[10px] text-red-500 hover:text-red-400 font-bold uppercase tracking-wider flex items-center gap-1"
                        >
                          <RefreshCw className="w-3 h-3" />
                          Rafraîchir
                        </button>
                      </div>
                      <select 
                        value={selectedExternalPlayer}
                        onChange={(e) => setSelectedExternalPlayer(e.target.value)}
                        className="w-full bg-zinc-950 border border-zinc-800 rounded px-4 py-2 text-white focus:border-red-600 focus:outline-none text-sm"
                      >
                        <option value="">Sélection automatique (Conseillé)</option>
                        {externalPlayers.map(player => (
                          <option key={player.packageId} value={player.packageId}>
                            {player.name}
                          </option>
                        ))}
                      </select>

                      <div className="flex flex-col gap-3">
                        <button
                          onClick={() => VideoLauncher.openSettings()}
                          className="w-full bg-zinc-800 hover:bg-zinc-700 text-white/70 text-xs py-2 rounded border border-zinc-700 transition-colors"
                        >
                          Ouvrir les réglages système (Permissions)
                        </button>

                        <p className="text-[10px] text-zinc-500 leading-relaxed italic">
                          💡 Si VLC n'apparaît pas, utilisez "Sélection automatique". Android vous demandera de choisir VLC lors du lancement de la vidéo.
                        </p>
                      </div>
                      </div>
                    )}
                  </div>
                <p className="text-[10px] text-zinc-500 mt-2">
                  Le lecteur externe permet d'utiliser votre application favorite (VLC, MX Player, etc.).
                </p>
              </div>

               <div className="pt-4 border-t border-zinc-800">
                <h4 className="text-sm font-bold text-zinc-400 uppercase tracking-wider mb-3 flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <ImageIcon className="w-4 h-4" /> TMDB (Affiches)
                  </div>
                  {isFetchingMetadata && <div className="w-2 h-2 rounded-full bg-red-600 animate-pulse shadow-[0_0_8px_rgba(220,38,38,0.8)]" title="Récupération des métadonnées..." />}
                </h4>
                <div className="space-y-4">
                  <div>
                    <input 
                      type="text" 
                      value={tmdbApiKey} 
                      onChange={(e) => setTmdbApiKey(e.target.value)} 
                      placeholder="Clé API TMDB" 
                      className="w-full bg-zinc-950 border border-zinc-800 rounded px-4 py-2 text-white focus:border-red-600 focus:outline-none" 
                    />
                    <div className="mt-2 text-[10px] text-zinc-500 flex justify-between items-center pr-2">
                      <span>Nécessaire pour les affiches et synopsis.</span>
                      <button
                        onClick={async () => {
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
                        }}
                        className="text-red-500 font-bold hover:underline"
                      >
                        Tester la clé
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              {/* Bibliothèque : re-scan et cache */}
              <div className="pt-4 border-t border-zinc-800">
                <h4 className="text-sm font-bold text-zinc-400 uppercase tracking-wider mb-3 flex items-center gap-2">
                  <Film className="w-4 h-4" /> Bibliothèque
                </h4>
                <div className="space-y-3">
                  {Capacitor.isNativePlatform() && (
                    <button
                      onClick={() => { startNativeScan(false); showToast("Scan de la bibliothèque relancé.", 'info'); }}
                      disabled={isScanning}
                      className="w-full bg-zinc-800 hover:bg-zinc-700 disabled:opacity-50 text-white text-sm font-bold py-2 rounded border border-zinc-700 transition-colors flex items-center justify-center gap-2"
                    >
                      <RefreshCw className={`w-4 h-4 ${isScanning ? 'animate-spin' : ''}`} />
                      {isScanning ? 'Scan en cours…' : 'Relancer le scan'}
                    </button>
                  )}
                  <button
                    onClick={() => setConfirmDialog({
                      message: "Vider le cache des métadonnées TMDB (affiches, synopsis…) ? Elles seront re-téléchargées au prochain scan.",
                      onConfirm: () => { clearMetadataCache(); showToast("Cache des métadonnées vidé.", 'success'); },
                    })}
                    className="w-full bg-zinc-800 hover:bg-zinc-700 text-white text-sm font-bold py-2 rounded border border-zinc-700 transition-colors flex items-center justify-center gap-2"
                  >
                    <Trash2 className="w-4 h-4" />
                    Vider le cache métadonnées
                  </button>
                </div>
              </div>

              {/* Vidéos personnelles exclues du scan (whitelist pour les réintégrer) */}
              {personalVideos.length > 0 && (
                <div className="pt-4 border-t border-zinc-800">
                  <h4 className="text-sm font-bold text-zinc-400 uppercase tracking-wider mb-1 flex items-center gap-2">
                    <EyeOff className="w-4 h-4" /> Vidéos exclues ({personalVideos.length})
                  </h4>
                  <p className="text-[10px] text-zinc-500 mb-3 leading-relaxed">
                    Vidéos détectées comme personnelles (caméra, WhatsApp…) et masquées de la bibliothèque. Activez celles à réintégrer.
                  </p>
                  <div className="space-y-1 max-h-48 overflow-y-auto custom-scrollbar pr-1">
                    {personalVideos.map(v => {
                      const included = whitelistedVideos.has(v.name);
                      return (
                        <button
                          key={v.path || v.name}
                          onClick={() => toggleWhitelist(v.name)}
                          className="w-full flex items-center justify-between gap-3 px-3 py-2 rounded bg-zinc-950 border border-zinc-800 hover:border-zinc-600 transition text-left"
                        >
                          <span className="text-xs text-zinc-300 truncate flex-1">{v.name}</span>
                          <span className={`text-[10px] font-black uppercase tracking-wider shrink-0 px-2 py-0.5 rounded-full ${included ? 'bg-green-600/20 text-green-500' : 'bg-zinc-800 text-zinc-500'}`}>
                            {included ? 'Visible' : 'Masquée'}
                          </span>
                        </button>
                      );
                    })}
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Subtitles Modal */}
      {showSubtitlesModal && (
        <div
          className="fixed inset-0 bg-black/80 backdrop-blur-sm z-50 flex items-center justify-center p-4"
          onClick={() => setShowSubtitlesModal(false)}
        >
          <div
            className="bg-zinc-900 border border-zinc-800 rounded-lg w-full max-w-md max-h-[80vh] flex flex-col shadow-2xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between p-4 border-b border-zinc-800">
              <h3 className="text-lg font-semibold flex items-center gap-2">
                <Subtitles className="w-5 h-5 text-red-600" />
                Sous-titres
              </h3>
              <button onClick={() => setShowSubtitlesModal(false)} aria-label="Fermer" className="p-2 rounded-full hover:bg-zinc-800 transition-colors">
                <X className="w-5 h-5" />
              </button>
            </div>
            
            <div className="p-4 border-b border-zinc-800 flex gap-2">
              <button onClick={searchSubtitles} disabled={isSearchingSubs || !osApiKey} className="flex-1 bg-zinc-800 hover:bg-zinc-700 disabled:opacity-50 text-white px-4 py-2 rounded font-bold flex items-center justify-center gap-2 transition-colors">
                {isSearchingSubs ? <div className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin" /> : <Search className="w-4 h-4" />}
                Rechercher
              </button>
              <button 
                onClick={Capacitor.isNativePlatform() ? openNativeSubtitlePicker : () => subtitleInputRef.current?.click()}
                className="bg-red-600 hover:bg-red-700 text-white px-4 py-2 rounded font-bold flex items-center justify-center gap-2 transition-colors"
                title="Ajouter un fichier local (.srt, .vtt)"
              >
                <FolderOpen className="w-4 h-4" />
                Local
              </button>
            </div>

            <div className="flex-1 overflow-y-auto p-2">
              {!osApiKey ? (
                <div className="p-6 text-center text-zinc-400">
                  <p className="mb-4">Configurez votre clé API OpenSubtitles.</p>
                </div>
              ) : subtitles.length === 0 ? (
                <div className="p-8 text-center text-zinc-500">
                  {isSearchingSubs ? 'Recherche...' : 'Aucun sous-titre trouvé.'}
                </div>
              ) : (
                <div className="space-y-1">
                  {localSubtitles.map((sub) => (
                    <button
                      key={sub.id}
                      onClick={() => {
                        setActiveSubtitleUrl(sub.url || null);
                        setShowSubtitlesModal(false);
                      }}
                      className={`w-full text-left p-3 rounded hover:bg-zinc-800 flex items-center justify-between group ${activeSubtitleUrl === sub.url ? 'bg-red-600/20 border border-red-600/30' : ''}`}
                    >
                      <div className="flex flex-col gap-0.5">
                        <span className="text-xs font-black text-red-500 uppercase tracking-widest">{sub.language}</span>
                        <span className="text-sm text-zinc-300 font-medium truncate max-w-[280px]">{sub.filename}</span>
                      </div>
                      <Check className={`w-4 h-4 text-red-600 ${activeSubtitleUrl === sub.url ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'}`} />
                    </button>
                  ))}
                  {subtitles.map((sub) => (
                    <div key={sub.id} className="flex items-center justify-between p-3 rounded hover:bg-zinc-800 transition-colors group">
                      <div className="flex-1 min-w-0 pr-4">
                        <span className="text-xs font-bold uppercase bg-zinc-800 text-zinc-300 px-2 py-0.5 rounded mr-2">{sub.language}</span>
                        <span className="text-sm text-zinc-300 truncate">{sub.filename}</span>
                      </div>
                      <button onClick={() => downloadSubtitle(sub.id)} className="p-2 rounded-full bg-zinc-800 text-zinc-300 hover:bg-red-600 hover:text-white transition-colors shrink-0">
                        <Download className="w-4 h-4" />
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
      <input
        type="file"
        ref={subtitleInputRef}
        onChange={handleLocalSubtitleSelection}
        accept=".srt,.vtt"
        style={{ display: 'none' }}
        multiple
      />

      {/* Toasts (remplacent les alert() bloquants) */}
      {toasts.length > 0 && (
        <div
          className="fixed left-4 right-4 md:left-auto md:right-8 md:w-96 z-[120] flex flex-col gap-2 pointer-events-none"
          style={{ bottom: 'calc(max(env(safe-area-inset-bottom), 8px) + 76px)' }}
          role="status"
          aria-live="polite"
        >
          {toasts.map(toast => (
            <div
              key={toast.id}
              className={`flex items-start gap-3 px-4 py-3 rounded-xl shadow-2xl border text-sm font-medium animate-in fade-in slide-in-from-bottom-4 duration-300 ${
                toast.type === 'error' ? 'bg-red-950/95 border-red-800 text-red-100'
                : toast.type === 'success' ? 'bg-emerald-950/95 border-emerald-800 text-emerald-100'
                : 'bg-zinc-900/95 border-zinc-700 text-zinc-100'
              }`}
            >
              {toast.type === 'error' ? <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
                : toast.type === 'success' ? <Check className="w-4 h-4 shrink-0 mt-0.5" />
                : <Info className="w-4 h-4 shrink-0 mt-0.5" />}
              <span className="flex-1">{toast.message}</span>
            </div>
          ))}
        </div>
      )}

      {/* Dialogue de confirmation (actions destructives) */}
      {confirmDialog && (
        <div
          className="fixed inset-0 bg-black/80 z-[130] flex items-center justify-center p-6"
          onClick={() => setConfirmDialog(null)}
        >
          <div
            className="bg-zinc-900 border border-zinc-700 rounded-2xl w-full max-w-sm p-6 shadow-2xl animate-in fade-in zoom-in-95 duration-300"
            onClick={(e) => e.stopPropagation()}
          >
            <p className="text-white text-sm leading-relaxed mb-6">{confirmDialog.message}</p>
            <div className="grid grid-cols-2 gap-3">
              <button
                onClick={() => setConfirmDialog(null)}
                className="py-3 rounded-xl bg-zinc-800 hover:bg-zinc-700 text-white text-sm font-bold transition-colors"
              >
                Annuler
              </button>
              <button
                onClick={() => { confirmDialog.onConfirm(); setConfirmDialog(null); }}
                className="py-3 rounded-xl bg-red-600 hover:bg-red-700 text-white text-sm font-black transition-colors"
              >
                Confirmer
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
