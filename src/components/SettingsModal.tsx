import React from 'react';
import { X, Subtitles, Play, LogIn, RefreshCw, Image as ImageIcon } from 'lucide-react';
import { Capacitor } from '@capacitor/core';

interface SettingsModalProps {
  osApiKey: string;
  osUsername: string;
  osPassword: string;
  osToken: string;
  isLoggingIn: boolean;
  videoPlayer: 'internal' | 'external';
  externalPlayers: { name: string, packageId: string }[];
  selectedExternalPlayer: string;
  tmdbApiKey: string;
  isFetchingMetadata: boolean;
  onClose: () => void;
  onOsApiKeyChange: (value: string) => void;
  onOsUsernameChange: (value: string) => void;
  onOsPasswordChange: (value: string) => void;
  onLogin: () => void;
  onVideoPlayerChange: (value: 'internal' | 'external') => void;
  onSelectedExternalPlayerChange: (value: string) => void;
  onRefreshPlayers: () => void;
  onOpenSystemSettings: () => void;
  onTmdbApiKeyChange: (value: string) => void;
  onTestTmdbKey: () => void;
}

/** Modale des paramètres : OpenSubtitles, lecteur vidéo, TMDB. */
export const SettingsModal: React.FC<SettingsModalProps> = ({
  osApiKey, osUsername, osPassword, osToken, isLoggingIn,
  videoPlayer, externalPlayers, selectedExternalPlayer,
  tmdbApiKey, isFetchingMetadata,
  onClose, onOsApiKeyChange, onOsUsernameChange, onOsPasswordChange, onLogin,
  onVideoPlayerChange, onSelectedExternalPlayerChange, onRefreshPlayers, onOpenSystemSettings,
  onTmdbApiKeyChange, onTestTmdbKey,
}) => (
  <div className="fixed inset-0 bg-black/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
    <div className="bg-zinc-900 border border-zinc-800 rounded-lg w-full max-w-md max-h-[90vh] flex flex-col overflow-hidden shadow-2xl">
      <div className="flex items-center justify-between p-4 border-b border-zinc-800 shrink-0">
        <h3 className="text-lg font-semibold">Paramètres</h3>
        <button onClick={onClose} className="p-2 rounded-full hover:bg-zinc-800 transition-colors">
          <X className="w-5 h-5" />
        </button>
      </div>
      <div className="p-6 space-y-4 flex-1 overflow-y-auto">
        <div>
          <h4 className="text-sm font-bold text-zinc-400 uppercase tracking-wider mb-3 flex items-center gap-2">
            <Subtitles className="w-4 h-4" /> OpenSubtitles
          </h4>
          <div className="space-y-3">
            <input type="text" value={osApiKey} onChange={(e) => onOsApiKeyChange(e.target.value)} placeholder="Clé API" className="w-full bg-zinc-950 border border-zinc-800 rounded px-4 py-2 text-white focus:border-red-600 focus:outline-none" />
            <input type="text" value={osUsername} onChange={(e) => onOsUsernameChange(e.target.value)} placeholder="Nom d'utilisateur" className="w-full bg-zinc-950 border border-zinc-800 rounded px-4 py-2 text-white focus:border-red-600 focus:outline-none" />
            <input type="password" value={osPassword} onChange={(e) => onOsPasswordChange(e.target.value)} placeholder="Mot de passe" className="w-full bg-zinc-950 border border-zinc-800 rounded px-4 py-2 text-white focus:border-red-600 focus:outline-none" />
            <button onClick={onLogin} disabled={isLoggingIn} className="w-full bg-red-600 hover:bg-red-700 disabled:opacity-50 text-white font-bold py-2 rounded transition-colors flex items-center justify-center gap-2">
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
                onClick={() => onVideoPlayerChange('internal')}
                className={`py-2 px-4 rounded font-bold text-sm transition ${videoPlayer === 'internal' ? 'bg-red-600 text-white' : 'bg-zinc-800 text-zinc-400 hover:bg-zinc-700'}`}
              >
                Interne
              </button>
              <button
                onClick={() => onVideoPlayerChange('external')}
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
                    onClick={onRefreshPlayers}
                    className="text-[10px] text-red-500 hover:text-red-400 font-bold uppercase tracking-wider flex items-center gap-1"
                  >
                    <RefreshCw className="w-3 h-3" />
                    Rafraîchir
                  </button>
                </div>
                <select
                  value={selectedExternalPlayer}
                  onChange={(e) => onSelectedExternalPlayerChange(e.target.value)}
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
                    onClick={onOpenSystemSettings}
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
                onChange={(e) => onTmdbApiKeyChange(e.target.value)}
                placeholder="Clé API TMDB"
                className="w-full bg-zinc-950 border border-zinc-800 rounded px-4 py-2 text-white focus:border-red-600 focus:outline-none"
              />
              <div className="mt-2 text-[10px] text-zinc-500 flex justify-between items-center pr-2">
                <span>Nécessaire pour les affiches et synopsis.</span>
                <button
                  onClick={onTestTmdbKey}
                  className="text-red-500 font-bold hover:underline"
                >
                  Tester la clé
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
);
