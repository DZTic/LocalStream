import React from 'react';
import { X, Subtitles, Search, FolderOpen, Download, Check } from 'lucide-react';
import { Subtitle } from '../lib/types';

interface SubtitlesModalProps {
  subtitles: Subtitle[];
  localSubtitles: Subtitle[];
  isSearchingSubs: boolean;
  osApiKey: string;
  activeSubtitleUrl: string | null;
  onClose: () => void;
  onSearch: () => void;
  onPickLocal: () => void;
  onSelectLocal: (sub: Subtitle) => void;
  onDownload: (fileId: string) => void;
}

/** Modale de sélection des sous-titres : recherche OpenSubtitles + fichiers locaux. */
export const SubtitlesModal: React.FC<SubtitlesModalProps> = ({
  subtitles, localSubtitles, isSearchingSubs, osApiKey, activeSubtitleUrl,
  onClose, onSearch, onPickLocal, onSelectLocal, onDownload,
}) => (
  <div className="fixed inset-0 bg-black/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
    <div className="bg-zinc-900 border border-zinc-800 rounded-lg w-full max-w-md max-h-[80vh] flex flex-col shadow-2xl">
      <div className="flex items-center justify-between p-4 border-b border-zinc-800">
        <h3 className="text-lg font-semibold flex items-center gap-2">
          <Subtitles className="w-5 h-5 text-red-600" />
          Sous-titres
        </h3>
        <button onClick={onClose} className="p-2 rounded-full hover:bg-zinc-800 transition-colors">
          <X className="w-5 h-5" />
        </button>
      </div>

      <div className="p-4 border-b border-zinc-800 flex gap-2">
        <button onClick={onSearch} disabled={isSearchingSubs || !osApiKey} className="flex-1 bg-zinc-800 hover:bg-zinc-700 disabled:opacity-50 text-white px-4 py-2 rounded font-bold flex items-center justify-center gap-2 transition-colors">
          {isSearchingSubs ? <div className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin" /> : <Search className="w-4 h-4" />}
          Rechercher
        </button>
        <button
          onClick={onPickLocal}
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
                onClick={() => onSelectLocal(sub)}
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
                <button onClick={() => onDownload(sub.id)} className="p-2 rounded-full bg-zinc-800 text-zinc-300 hover:bg-red-600 hover:text-white transition-colors shrink-0">
                  <Download className="w-4 h-4" />
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  </div>
);
