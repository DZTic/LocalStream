import React, { useState, useRef, useEffect, useCallback } from 'react';
import { Capacitor } from '@capacitor/core';
import { Filesystem, Directory } from '@capacitor/filesystem';
import { VideoFile } from '../lib/types';
import { SubtitleEntry, buildSubtitleIndex, matchSubtitle, parentFolder, VIDEO_EXT_RE, SUBTITLE_EXT_RE } from '../lib/scan';
import { VideoLauncher } from '../plugins/videoLauncher';

interface UseScanProps {
  showToast: (message: string, type?: 'success' | 'error' | 'info') => void;
}

export function useScan({ showToast }: UseScanProps) {
  const [videos, setVideos] = useState<VideoFile[]>([]);
  const [isScanning, setIsScanning] = useState(false);
  const [diagLogs, setDiagLogs] = useState<string[]>([]);

  const addLog = useCallback((msg: string) => {
    setDiagLogs(prev => [new Date().toLocaleTimeString() + ": " + msg, ...prev].slice(0, 50));
  }, []);

  const scanDirectoryRecursively = useCallback(async (
    directory: typeof Directory[keyof typeof Directory],
    basePath: string,
    debugLogs: string[]
  ): Promise<VideoFile[]> => {
    let result: VideoFile[] = [];
    try {
      const list = await Filesystem.readdir({ directory, path: basePath });
      for (const item of list.files) {
        const itemPath = basePath ? `${basePath}/${item.name}` : item.name;
        if (item.type === 'directory') {
          if (item.name.startsWith('.') || item.name === 'Android' || item.name === 'LOST.DIR') continue;
          const subResult = await scanDirectoryRecursively(directory, itemPath, debugLogs);
          result = [...result, ...subResult];
        } else {
          if (item.name.match(VIDEO_EXT_RE)) {
            result.push({
              url: item.uri,
              name: item.name,
              type: 'video/mp4',
              path: itemPath,
              nativeUri: item.uri
            });
          }
        }
      }
    } catch (e: any) {
      debugLogs.push(`Erreur scan dossier ${basePath}: ${e.message}`);
    }
    return result;
  }, []);

  const scanViaMediaStore = useCallback(async (): Promise<VideoFile[]> => {
    const { videos: scanned, subtitles: scannedSubs } = await VideoLauncher.scanVideos();
    
    const subEntries: SubtitleEntry[] = scannedSubs.map(s => ({
      name: s.name,
      folder: parentFolder(s.relativePath || s.path || ''),
      uri: s.path
    }));
    
    const subIndex = buildSubtitleIndex(subEntries);

    const videoFiles: VideoFile[] = scanned.map((v: any) => {
      const parent = parentFolder(v.relativePath || v.path || '');
      const matchedSub = matchSubtitle(subIndex, v.name, parent);
      return {
        url: Capacitor.convertFileSrc(v.path),
        name: v.name,
        type: 'video/mp4',
        path: v.path,
        nativeUri: `file://${v.path}`,
        subtitleNativePath: matchedSub?.uri,
        subtitleUrl: matchedSub ? Capacitor.convertFileSrc(matchedSub.uri) : undefined
      };
    });
    return videoFiles;
  }, []);

  const startNativeScan = useCallback(async (quiet = false) => {
    if (isScanning) return;
    setIsScanning(true);
    if (!quiet) addLog("Début du scan...");
    try {
      let scannedVideos: VideoFile[] = [];
      if (Capacitor.isNativePlatform()) {
        scannedVideos = await scanViaMediaStore();
      } else {
        const debugLogs: string[] = [];
        scannedVideos = await scanDirectoryRecursively(Directory.Documents, '', debugLogs);
        debugLogs.forEach(l => addLog(l));
      }

      setVideos(scannedVideos);
      if (!quiet) {
        if (scannedVideos.length > 0) {
          showToast(`${scannedVideos.length} vidéos trouvées.`, 'success');
        } else {
          showToast("Aucune vidéo trouvée dans les dossiers scannés.", 'error');
        }
      }
    } catch (e: any) {
      addLog(`Erreur scan: ${e.message}`);
      if (!quiet) showToast("Erreur pendant le scan : " + e.message, 'error');
    } finally {
      setIsScanning(false);
    }
  }, [isScanning, addLog, scanViaMediaStore, scanDirectoryRecursively, showToast]);

  const handleFolderSelect = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;

    const videoFiles: VideoFile[] = [];
    const subtitleFiles: SubtitleEntry[] = [];

    for (let i = 0; i < files.length; i++) {
      const file = files[i];
      const relativePath = (file as any).webkitRelativePath || file.name;
      const folder = parentFolder(relativePath);

      if (file.name.match(VIDEO_EXT_RE)) {
        const url = URL.createObjectURL(file);
        videoFiles.push({
          url: url,
          name: file.name,
          type: file.type || 'video/mp4',
          path: relativePath,
          file: file
        });
      } else if (file.name.match(SUBTITLE_EXT_RE)) {
        const url = URL.createObjectURL(file);
        subtitleFiles.push({
          name: file.name,
          uri: url,
          folder: folder
        });
      }
    }

    const subIndex = buildSubtitleIndex(subtitleFiles);

    const mappedVideos = videoFiles.map(video => {
      const parent = parentFolder(video.path);
      const matched = matchSubtitle(subIndex, video.name, parent);
      if (matched) {
        return {
          ...video,
          subtitleUrl: matched.uri
        };
      }
      return video;
    });

    setVideos(mappedVideos);
    showToast(`${mappedVideos.length} vidéos chargées.`, 'success');
    if (e.target) e.target.value = '';
  }, [showToast]);

  return {
    videos,
    setVideos,
    isScanning,
    diagLogs,
    addLog,
    startNativeScan,
    handleFolderSelect,
  };
}
