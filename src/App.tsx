import React, { useState, useRef, useEffect } from 'react';
import { FolderOpen, Search, RefreshCw } from 'lucide-react';
import { Capacitor } from '@capacitor/core';
import { Filesystem, Directory } from '@capacitor/filesystem';
import { VideoFile, Subtitle, Playlist } from './lib/types';
import { srt2vtt, safeSetItem } from './lib/utils';
import { osLogin, osSearch, osDownloadVtt } from './lib/opensubtitles';
import { getPopular } from './lib/tmdb';
import { filterAndSortVideos } from './lib/sorting';
import { getHeroCandidates } from './lib/hero';
import { parseSeriesInfo, buildSubtitleIndex, matchSubtitle, parentFolder, subtitleBaseName, videoBaseName, VIDEO_EXT_RE, SUBTITLE_EXT_RE } from './lib/scan';
import { useTmdbMetadata } from './hooks/useTmdbMetadata';
import { usePlaylists } from './hooks/usePlaylists';
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

export default function App() {
  const [videos, setVideos] = useState<VideoFile[]>([]);
  const [currentVideo, setCurrentVideo] = useState<VideoFile | null>(null);
  const [showSettings, setShowSettings] = useState(false);
  const [isScrolled, setIsScrolled] = useState(false);

  // OpenSubtitles Credentials
  const [osApiKey, setOsApiKey] = useState(localStorage.getItem('osApiKey') || '');
  const [osUsername, setOsUsername] = useState(localStorage.getItem('osUsername') || '');
  const [osPassword, setOsPassword] = useState(localStorage.getItem('osPassword') || '');
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

  // TMDB Credentials & Posters
  const [watchedVideos, setWatchedVideos] = useState<Record<string, boolean>>(() => {
    try {
      const saved = localStorage.getItem('watchedVideos');
      return saved ? JSON.parse(saved) : {};
    } catch {
      return {};
    }
  });

  const [tmdbApiKey, setTmdbApiKey] = useState(localStorage.getItem('tmdbApiKey') || '');
  const [videoDurations, setVideoDurations] = useState<Record<string, number>>({});

  const [sortBy, setSortBy] = useState<SortBy>('alpha');
  const [filterGenre, setFilterGenre] = useState<number | 'all'>('all');
  const [filterResolution, setFilterResolution] = useState<string | 'all'>('all');

  const [recentlyWatched, setRecentlyWatched] = useState<string[]>(() => {
    try {
      const saved = localStorage.getItem('recentlyWatched');
      return saved ? JSON.parse(saved) : [];
    } catch {
      return [];
    }
  });

  const [subtitles, setSubtitles] = useState<Subtitle[]>([]);
  const [localSubtitles, setLocalSubtitles] = useState<Subtitle[]>([]);
  const [isSearchingSubs, setIsSearchingSubs] = useState(false);
  const [isLoggingIn, setIsLoggingIn] = useState(false);
  const [activeSubtitleUrl, setActiveSubtitleUrl] = useState<string | null>(null);
  const [activeSubtitleNativePath, setActiveSubtitleNativePath] = useState<string | null>(null);
  const subtitleInputRef = useRef<HTMLInputElement>(null);
  const [showSubtitlesModal, setShowSubtitlesModal] = useState(false);

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
  const [forceAvailableVideos, setForceAvailableVideos] = useState<Record<string, boolean>>(() => {
    try {
      const saved = localStorage.getItem('forceAvailableVideos');
      return saved ? JSON.parse(saved) : {};
    } catch {
      return {};
    }
  });

  const [isScanning, setIsScanning] = useState(false);
  const [, setDiagLogs] = useState<string[]>([]);
  const addLog = (msg: string) => {
    setDiagLogs(prev => [new Date().toLocaleTimeString() + ": " + msg, ...prev].slice(0, 50));
  };

  // Couche métadonnées TMDB (état + regroupement + récupération auto/manuelle)
  const {
    groupedVideos,
    posters, backdrops, overviews, releaseDates, videoGenres,
    episodeOverviews, episodePosters, episodeNames,
    isFetchingMetadata, isRefreshingMetadata,
    fetchSingleMetadata,
  } = useTmdbMetadata({ videos, whitelistedVideos, tmdbApiKey, addLog });
  const [permsNeeded, setPermsNeeded] = useState(false);
  const [externalPlayers, setExternalPlayers] = useState<{name: string, packageId: string}[]>([]);
  const [selectedExternalPlayer, setSelectedExternalPlayer] = useState<string>(localStorage.getItem('selectedExternalPlayer') || '');

  const [watchProgress, setWatchProgress] = useState<Record<string, number>>(() => {
    try {
      const saved = localStorage.getItem('watchProgress');
      return saved ? JSON.parse(saved) : {};
    } catch {
      return {};
    }
  });

  const [watchPositions, setWatchPositions] = useState<Record<string, number>>(() => {
    try {
      const saved = localStorage.getItem('watchPositions');
      return saved ? JSON.parse(saved) : {};
    } catch {
      return {};
    }
  });

  const [activePlaylist, setActivePlaylist] = useState<Playlist | null>(null);
  const [activePlaylistIndex, setActivePlaylistIndex] = useState<number>(0);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);

  const toggleWatched = (videoName: string) => {
    setWatchedVideos(prev => {
      // Trouver si c'est un groupe de vidéos (série ou collection)
      const group = groupedVideos.find(v => (v.seriesName === videoName || v.name === videoName) && v.isSeriesGroup);

      let isCurrentlyWatched;
      if (group && group.episodes) {
        // Pour un groupe, on considère qu'il est vu si TOUS les épisodes sont vus
        isCurrentlyWatched = group.episodes.every(ep => !!prev[ep.name]);
      } else {
        isCurrentlyWatched = !!prev[videoName];
      }

      const newState = { ...prev };
      const targetValue = !isCurrentlyWatched;

      if (group && group.episodes) {
        // On marque le nom du groupe ET tous ses épisodes
        newState[videoName] = targetValue;
        group.episodes.forEach(ep => {
          newState[ep.name] = targetValue;
        });
      } else {
        // C'est un épisode seul
        newState[videoName] = targetValue;

        // Propagation automatique vers le groupe de série s'il existe
        const parentSeries = groupedVideos.find(g => g.isSeriesGroup && g.episodes?.some(ep => ep.name === videoName));
        if (parentSeries) {
          const seriesKey = parentSeries.seriesName || parentSeries.name;
          const allEpsWatched = parentSeries.episodes!.every(ep => !!newState[ep.name]);
          newState[seriesKey] = allEpsWatched;
        }
      }

      safeSetItem('watchedVideos', JSON.stringify(newState));
      return newState;
    });
  };

  const resetProgress = (videoName: string) => {
    setWatchProgress(prev => {
      const newState = { ...prev };
      delete newState[videoName];
      safeSetItem('watchProgress', JSON.stringify(newState));
      return newState;
    });
    setWatchPositions(prev => {
      const newState = { ...prev };
      delete newState[videoName];
      safeSetItem('watchPositions', JSON.stringify(newState));
      return newState;
    });
  };

  const toggleForceAvailable = (videoName: string) => {
    setForceAvailableVideos(prev => {
      const newState = { ...prev, [videoName]: !prev[videoName] };
      safeSetItem('forceAvailableVideos', JSON.stringify(newState));
      return newState;
    });
  };

  const addManualHistoryItem = () => {
    if (!newHistoryItemName.trim()) return;
    const name = newHistoryItemName.trim();

    setWatchedVideos(prev => {
      const newState = { ...prev, [name]: true };
      safeSetItem('watchedVideos', JSON.stringify(newState));
      return newState;
    });

    setForceAvailableVideos(prev => {
      const newState = { ...prev, [name]: true };
      safeSetItem('forceAvailableVideos', JSON.stringify(newState));
      return newState;
    });

    setNewHistoryItemName('');
  };

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

  useEffect(() => {
    safeSetItem('watchPositions', JSON.stringify(watchPositions));
  }, [watchPositions]);

  useEffect(() => {
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 50);
    };
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  // Save credentials
  useEffect(() => {
    safeSetItem('osApiKey', osApiKey);
    safeSetItem('osUsername', osUsername);
    safeSetItem('osPassword', osPassword);
    safeSetItem('osToken', osToken);
    safeSetItem('videoPlayer', videoPlayer);
  }, [osApiKey, osUsername, osPassword, osToken, videoPlayer]);

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

  const refreshExternalPlayers = () => {
    VideoLauncher.getList()
      .then(res => setExternalPlayers(res.players))
      .catch(err => console.error("Error fetching external players", err));
  };

  // Initial setup
  useEffect(() => {
    checkGlobalPermissions();
    if (Capacitor.isNativePlatform()) {
      refreshExternalPlayers();

      // Lancement du scan silencieux au démarrage de l'application
      startNativeScan(true);
    }
    // Listen for window focus to re-check permissions (when coming back from settings)
    window.addEventListener('focus', checkGlobalPermissions);

    return () => {
      window.removeEventListener('focus', checkGlobalPermissions);
    };
  }, []);

  const extractedDurationsRef = useRef<Set<string>>(new Set());

  useEffect(() => {
    const extractDurations = async () => {
      for (const video of videos) {
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

          if (duration > 0) {
            setVideoDurations(prev => ({ ...prev, [video.name]: duration }));
          }
          extractedDurationsRef.current.add(video.name);
        } catch (e) {
          // Ignore errors
        }
      }
    };

    // Run in background to not block main thread
    if (videos.length > 0) {
      setTimeout(extractDurations, 2000);
    }
  }, [videos]);

  /**
   * Scan rapide via MediaStore (plugin natif) : l'index du système connaît
   * déjà toutes les vidéos, leurs durées et leurs dates.
   */
  const scanViaMediaStore = async (): Promise<VideoFile[]> => {
    const { videos: scanned, subtitles: scannedSubs } = await VideoLauncher.scanVideos();

    const subIndex = buildSubtitleIndex(scannedSubs.map(s => ({
      name: s.name,
      folder: parentFolder(s.relativePath || s.name),
      uri: s.path ? 'file://' + s.path : s.uri,
    })));

    const durations: Record<string, number> = {};
    const result = scanned.map(v => {
      const nativeUri = v.path ? 'file://' + v.path : v.uri;
      const relPath = v.relativePath || v.name;
      const info = parseSeriesInfo(v.name, relPath);
      const matched = matchSubtitle(subIndex, v.name, parentFolder(relPath));
      if (v.durationMs > 0) durations[v.name] = v.durationMs / 1000;

      return {
        url: Capacitor.convertFileSrc(nativeUri),
        nativeUri,
        name: v.name,
        type: 'video/mp4',
        path: relPath,
        size: v.size,
        lastModified: v.lastModified,
        seriesName: info.seriesName,
        season: info.season,
        episode: info.episode,
        subtitleNativePath: matched?.uri,
      } as VideoFile;
    });

    // Les durées viennent gratuitement de MediaStore : on évite l'extraction
    // coûteuse via des balises <video> cachées.
    if (Object.keys(durations).length > 0) {
      Object.keys(durations).forEach(name => extractedDurationsRef.current.add(name));
      setVideoDurations(prev => ({ ...durations, ...prev }));
    }
    return result;
  };

  /** Ancien scan récursif via Filesystem — conservé en secours si MediaStore échoue. */
  const scanDirectoryRecursively = async (directory: typeof Directory[keyof typeof Directory], basePath: string, debugLogs: string[]): Promise<VideoFile[]> => {
    let result: VideoFile[] = [];
    try {
      const response = await Filesystem.readdir({ path: basePath, directory });

      // Indexer les sous-titres du dossier par nom de base (sans extension)
      const subtitleMap: Record<string, { uri: string; name: string }> = {};
      for (const file of response.files) {
        if (file.type === 'file' && SUBTITLE_EXT_RE.test(file.name)) {
          subtitleMap[subtitleBaseName(file.name)] = { uri: file.uri, name: file.name };
        }
      }

      for (const file of response.files) {
        const fullPath = basePath ? `${basePath}/${file.name}` : file.name;
        if (file.type === 'directory') {
          const subFiles = await scanDirectoryRecursively(directory, fullPath, debugLogs);
          result = result.concat(subFiles);
        } else if (file.type === 'file' && VIDEO_EXT_RE.test(file.name)) {
          const { seriesName, season, episode } = parseSeriesInfo(file.name, fullPath);

          // Chercher un sous-titre avec le même nom de base
          const matchedSub = subtitleMap[videoBaseName(file.name)];

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
    if (!quiet) setIsScanning(true);
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

      let allVideos: VideoFile[] = [];
      try {
        allVideos = await scanViaMediaStore();
      } catch (e: any) {
        debugLogs.push(`MediaStore scan failed: ${e.message}`);
        console.warn("MediaStore scan failed, falling back to Filesystem", e);
        const foldersToScan = [
          { dir: Directory.ExternalStorage, path: 'Movies' },
          { dir: Directory.ExternalStorage, path: 'Download' },
          { dir: Directory.ExternalStorage, path: 'Downloads' },
          { dir: Directory.ExternalStorage, path: 'Documents' }
        ];

        for (const folder of foldersToScan) {
          const vids = await scanDirectoryRecursively(folder.dir, folder.path, debugLogs);
          allVideos = allVideos.concat(vids);
        }
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
        alert("Aucune vidéo trouvée.\nErreurs rencontrées :\n" + debugLogs.slice(0, 5).join("\n"));
      }
    } catch (e: any) {
      console.error(e);
      if (!quiet) alert("Erreur générale : " + e.message);
    } finally {
      if (!quiet) setIsScanning(false);
    }
  };

  const handleFolderSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files) return;

    // Indexer les sous-titres par chemin relatif (sans extension)
    const subtitleFileMap: Record<string, File> = {};
    for (let i = 0; i < files.length; i++) {
      const f = files[i];
      if (SUBTITLE_EXT_RE.test(f.name)) {
        const folder = (f.webkitRelativePath || f.name).replace(/\/[^\/]+$/, '');
        subtitleFileMap[`${folder}/${subtitleBaseName(f.name)}`] = f;
      }
    }

    const videoFiles: VideoFile[] = [];
    for (let i = 0; i < files.length; i++) {
      const file = files[i];
      if (file.type.startsWith('video/') || VIDEO_EXT_RE.test(file.name)) {
        const { seriesName, season, episode } = parseSeriesInfo(file.name);

        // Chercher un sous-titre avec le même nom de base dans le même dossier
        const folder = (file.webkitRelativePath || file.name).replace(/\/[^\/]+$/, '');
        const subFile = subtitleFileMap[`${folder}/${videoBaseName(file.name)}`];
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
    // Diff update for folder select (web)
    setVideos(prev => {
      const currentPaths = new Set(videoFiles.map(v => v.path));
      const kept = prev.filter(v => currentPaths.has(v.path));

      const existingPaths = new Set(kept.map(v => v.path));
      const extra = videoFiles.filter(v => !existingPaths.has(v.path));

      return [...kept, ...extra];
    });
  };

  const playVideo = (video: VideoFile, playlist?: Playlist, index?: number) => {
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
          alert("Erreur : " + err);
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
  };

  const loginOpenSubtitles = async () => {
    if (!osApiKey || !osUsername || !osPassword) {
      alert("Veuillez remplir tous les champs.");
      return;
    }

    setIsLoggingIn(true);
    try {
      const token = await osLogin(osApiKey, osUsername, osPassword);
      if (token) {
        setOsToken(token);
        alert('Connexion réussie !');
      } else {
        alert('Erreur de connexion.');
      }
    } catch (error) {
      alert('Erreur de connexion.');
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
      alert("Erreur lors de la recherche de sous-titres.");
    } finally {
      setIsSearchingSubs(false);
    }
  };

  const downloadSubtitle = async (fileId: string) => {
    if (!osApiKey || !osToken) {
      alert("Vous devez être connecté à OpenSubtitles.");
      setShowSubtitlesModal(false);
      setShowSettings(true);
      return;
    }
    try {
      const vttContent = await osDownloadVtt(osApiKey, osToken, fileId);
      if (vttContent) {
        const blob = new Blob([vttContent], { type: 'text/vtt' });
        setActiveSubtitleUrl(URL.createObjectURL(blob));
        setShowSubtitlesModal(false);
      } else {
        alert("Erreur de téléchargement.");
      }
    } catch (error) {
      alert("Erreur lors du téléchargement.");
    }
  };

  const tvShows = groupedVideos.filter(v => v.isSeriesGroup);
  const movies = groupedVideos.filter(v => !v.isSeriesGroup);

  const isGroupWatched = (v: VideoFile) =>
    v.isSeriesGroup ? !!(v.episodes && v.episodes.every(ep => !!watchedVideos[ep.name])) : !!watchedVideos[v.name];

  // Nouveautés (Recently Added) - Unwatched first
  const recentAdditions = [...groupedVideos].sort((a, b) => {
    const aWatched = isGroupWatched(a);
    const bWatched = isGroupWatched(b);
    if (aWatched !== bWatched) return aWatched ? 1 : -1;
    return (b.file?.lastModified || b.lastModified || 0) - (a.file?.lastModified || a.lastModified || 0);
  });

  // En cours (In Progress)
  const inProgressVideos = recentlyWatched
    .map(name => videos.find(v => v.name === name))
    .filter((v): v is VideoFile => v !== undefined && (watchProgress[v.name] || 0) > 0 && (watchProgress[v.name] || 0) < 95 && !watchedVideos[v.name]);

  // De A à Z (Alphabetical) - Unwatched first
  const alphabetical = [...groupedVideos].sort((a, b) => {
    const aWatched = isGroupWatched(a);
    const bWatched = isGroupWatched(b);
    if (aWatched !== bWatched) return aWatched ? 1 : -1;
    return a.name.localeCompare(b.name);
  });

  // Recommandations (Pseudo-random based on name length for stability) - Unwatched first
  const recommendations = [...groupedVideos].sort((a, b) => {
    const aWatched = isGroupWatched(a);
    const bWatched = isGroupWatched(b);
    if (aWatched !== bWatched) return aWatched ? 1 : -1;
    return (a.name.length % 7) - (b.name.length % 7);
  });

  // Group by folder
  const folders = groupedVideos.reduce((acc, video) => {
    const parts = video.path.split('/');
    const folderName = parts.length > 1 ? parts[0] : 'Racine';

    if (!acc[folderName]) {
      acc[folderName] = [];
    }
    acc[folderName].push(video);
    return acc;
  }, {} as Record<string, VideoFile[]>);

  const folderNames = Object.keys(folders).sort();

  const filteredAndSortedVideos = React.useMemo(
    () => filterAndSortVideos(groupedVideos, {
      sortBy, filterGenre, filterResolution, releaseDates, videoGenres, videoDurations, watchedVideos,
    }),
    [groupedVideos, sortBy, filterGenre, filterResolution, releaseDates, videoGenres, videoDurations, watchedVideos]
  );

  const searchResults = searchQuery.trim()
    ? filteredAndSortedVideos.filter(v => v.name.toLowerCase().includes(searchQuery.toLowerCase()))
    : [];

  const historyItems: HistoryItem[] = React.useMemo(() => {
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

  useEffect(() => {
    const handleFocusScan = () => {
      if (Capacitor.isNativePlatform()) {
        startNativeScan(true);
      }
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

  const handleOpenInfoModal = (video: VideoFile) => {
    // Si c'est un épisode individuel (pas un groupe), on remonte au groupe de série parent
    if (!video.isSeriesGroup && (video.season !== undefined || video.seriesName)) {
      const parentGroup = groupedVideos.find(g =>
        g.isSeriesGroup && g.episodes?.some(ep => ep.name === video.name)
      );
      if (parentGroup) {
        setInfoVideo(parentGroup);
        // Ouvrir directement l'épisode cliqué dans le panneau d'info
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

  const handleTestTmdbKey = async () => {
    addLog("Test de la clé API...");
    try {
      const r = await getPopular(tmdbApiKey);
      if (r.ok) addLog("Test API réussi !");
      else addLog("Test API échoué - Code : " + r.status);
    } catch(e: any) { addLog("Erreur test : " + e.message); }
  };

  const resetHomeView = () => {
    setActiveTab('home');
    setSearchQuery('');
    setSortBy('alpha');
    setFilterGenre('all');
    setFilterResolution('all');
    setSelectedPlaylist(null);
  };

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
                {/* Filter and Sort Bar - Only show when searching or in library view */}
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
                    onSelectPlaylist={setSelectedPlaylist}
                    onOpenInfo={handleOpenInfoModal}
                    onPlay={playVideo}
                    onDeletePlaylist={deletePlaylist}
                    onRemoveVideo={removeVideoFromPlaylist}
                  />
                ) : activeTab === 'history' ? (
                  <HistoryScreen
                    historyItems={historyItems}
                    searchQuery={searchQuery}
                    newItemName={newHistoryItemName}
                    posters={posters}
                    onNewItemNameChange={setNewHistoryItemName}
                    onAddManualItem={addManualHistoryItem}
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
                    inProgressVideos={inProgressVideos}
                    recentAdditions={recentAdditions}
                    recommendations={recommendations}
                    tvShows={tvShows}
                    movies={movies}
                    folders={folders}
                    folderNames={folderNames}
                    alphabetical={alphabetical}
                    watchProgress={watchProgress}
                    watchedVideos={watchedVideos}
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
      <input
        type="file"
        ref={subtitleInputRef}
        onChange={handleLocalSubtitleSelection}
        accept=".srt,.vtt"
        style={{ display: 'none' }}
        multiple
      />
    </div>
  );
}
