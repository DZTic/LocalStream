import React, { useState, useEffect, useCallback } from 'react';
import { Capacitor } from '@capacitor/core';
import { Subtitle, VideoFile } from '../lib/types';
import { srt2vtt, safeSetItem, getCleanTitle } from '../lib/utils';
import { osLogin, osSearch, osDownloadVtt } from '../lib/opensubtitles';
import { VideoLauncher } from '../plugins/videoLauncher';

interface UseOpenSubtitlesProps {
  currentVideo: VideoFile | null;
  showToast: (message: string, type?: 'success' | 'error' | 'info') => void;
}

export function useOpenSubtitles({ currentVideo, showToast }: UseOpenSubtitlesProps) {
  const [osApiKey, setOsApiKey] = useState(localStorage.getItem('osApiKey') || '');
  const [osUsername, setOsUsername] = useState(localStorage.getItem('osUsername') || '');
  const [osPassword, setOsPassword] = useState('');
  const [osToken, setOsToken] = useState(localStorage.getItem('osToken') || '');

  const [subtitles, setSubtitles] = useState<Subtitle[]>([]);
  const [localSubtitles, setLocalSubtitles] = useState<Subtitle[]>([]);
  const [isSearchingSubs, setIsSearchingSubs] = useState(false);
  const [isLoggingIn, setIsLoggingIn] = useState(false);
  const [activeSubtitleUrl, setActiveSubtitleUrl] = useState<string | null>(null);
  const [activeSubtitleNativePath, setActiveSubtitleNativePath] = useState<string | null>(null);
  const [showSubtitlesModal, setShowSubtitlesModal] = useState(false);

  // Enregistrer les modifications dans localStorage
  useEffect(() => {
    safeSetItem('osApiKey', osApiKey);
    safeSetItem('osUsername', osUsername);
    safeSetItem('osToken', osToken);
    localStorage.removeItem('osPassword'); // Nettoyage
  }, [osApiKey, osUsername, osToken]);

  const loginOpenSubtitles = useCallback(async () => {
    if (!osApiKey || !osUsername || !osPassword) {
      showToast("Veuillez remplir tous les champs OpenSubtitles.", 'error');
      return;
    }

    setIsLoggingIn(true);
    try {
      const token = await osLogin(osApiKey, osUsername, osPassword);
      if (token) {
        setOsToken(token);
        showToast('Connexion réussie !', 'success');
      } else {
        showToast('Échec de la connexion OpenSubtitles.', 'error');
      }
    } catch (err: any) {
      showToast('Échec de la connexion OpenSubtitles.', 'error');
    } finally {
      setIsLoggingIn(false);
    }
  }, [osApiKey, osUsername, osPassword, showToast]);

  const searchSubtitles = useCallback(async () => {
    if (!currentVideo) return;
    setIsSearchingSubs(true);
    try {
      const cleanTitle = getCleanTitle(currentVideo.name);
      const results = await osSearch(osApiKey, cleanTitle);
      const formatted = results.map((r: any) => ({
        id: r.id,
        language: r.language,
        filename: r.filename,
        url: '' // Téléchargé à la demande
      }));
      setSubtitles(formatted);
    } catch (err) {
      showToast("Erreur lors de la recherche de sous-titres.", 'error');
    } finally {
      setIsSearchingSubs(false);
    }
  }, [osApiKey, currentVideo, showToast]);

  const downloadSubtitle = useCallback(async (fileId: string) => {
    if (!osToken) {
      showToast("Vous devez être connecté à OpenSubtitles (voir Paramètres).", 'error');
      return;
    }
    try {
      const srtText = await osDownloadVtt(osApiKey, fileId, osToken);
      if (!srtText) {
        showToast("Erreur de téléchargement du sous-titre (session expirée ?). Reconnectez-vous dans les Paramètres.", 'error');
        return;
      }
      const vttText = srt2vtt(srtText);
      const blob = new Blob([vttText], { type: 'text/vtt' });
      const url = URL.createObjectURL(blob);

      setSubtitles(prev => prev.map(sub => sub.id === fileId ? { ...sub, url } : sub));
      setActiveSubtitleUrl(url);
      setShowSubtitlesModal(false);
      showToast("Sous-titre activé !", 'success');
    } catch (err) {
      showToast("Erreur lors du téléchargement du sous-titre.", 'error');
    }
  }, [osApiKey, osToken, showToast]);

  const openNativeSubtitlePicker = useCallback(async () => {
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
      console.error("Sélection annulée ou erreur", err);
    }
  }, []);

  const handleLocalSubtitleSelection = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
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
  }, []);

  return {
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
  };
}
