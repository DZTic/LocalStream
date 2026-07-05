import React from 'react';
import { Play, Trash2, Cloud, CloudOff, History } from 'lucide-react';
import { VideoFile } from '../../lib/types';
import { getCleanTitle } from '../../lib/utils';

export interface HistoryItem {
  id: string;
  name: string;
  isLocal: boolean;
  isForcedAvailable: boolean;
  video?: VideoFile;
}

interface HistoryScreenProps {
  historyItems: HistoryItem[];
  searchQuery: string;
  newItemName: string;
  posters: Record<string, string>;
  onNewItemNameChange: (value: string) => void;
  onAddManualItem: () => void;
  onOpenInfo: (video: VideoFile) => void;
  onPlay: (video: VideoFile) => void;
  onToggleWatched: (name: string) => void;
  onToggleForceAvailable: (name: string) => void;
}

/** Écran "Contenus vus" : historique, ajout manuel, disponibilité forcée. */
export const HistoryScreen: React.FC<HistoryScreenProps> = ({
  historyItems, searchQuery, newItemName, posters,
  onNewItemNameChange, onAddManualItem, onOpenInfo, onPlay, onToggleWatched, onToggleForceAvailable,
}) => {
  const displayedHistoryItems = searchQuery.trim()
    ? historyItems.filter(item => item.name.toLowerCase().includes(searchQuery.toLowerCase()))
    : historyItems;

  return (
    <div className="px-4 md:px-12 min-h-screen pt-20">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-8">
        <h2 className="text-2xl md:text-3xl font-bold text-white">Contenus vus</h2>

        <div className="flex gap-2 w-full sm:w-auto max-w-md">
          <input
            type="text"
            placeholder="Ajouter un film ou une série..."
            value={newItemName}
            onChange={(e) => onNewItemNameChange(e.target.value)}
            className="flex-1 bg-zinc-900 border border-white/10 rounded-xl px-4 py-2 text-sm text-white focus:border-red-600 focus:outline-none transition-all placeholder:text-zinc-500"
            onKeyDown={(e) => e.key === 'Enter' && onAddManualItem()}
          />
          <button
            onClick={onAddManualItem}
            disabled={!newItemName.trim()}
            className="bg-red-600 hover:bg-red-700 disabled:opacity-30 text-white px-4 py-2 rounded-xl text-xs font-black transition-all active:scale-95 shadow-lg shadow-red-900/20 whitespace-nowrap"
          >
            AJOUTER
          </button>
        </div>
      </div>
      {historyItems.length === 0 ? (
        <div className="text-center text-zinc-500 mt-12 flex flex-col items-center gap-4">
          <History className="w-16 h-16 text-zinc-700" />
          <p>Votre historique est vide.</p>
        </div>
      ) : displayedHistoryItems.length === 0 ? (
        <div className="text-center text-zinc-500 mt-12">
          Aucun contenu correspondant dans votre historique.
        </div>
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
          {displayedHistoryItems.map((item) => (
            <div
              key={item.id}
              className="group flex flex-col"
              onClick={() => item.isLocal && item.video && onOpenInfo(item.video)}
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
                    onClick={(e) => { e.stopPropagation(); onToggleForceAvailable(item.id); }}
                    className={`absolute top-2 left-2 z-40 p-2 rounded-full transition ${item.isForcedAvailable ? 'bg-green-600/90 text-white' : 'bg-zinc-800/90 text-zinc-400'}`}
                    title={item.isForcedAvailable ? "Rendre indisponible" : "Rendre disponible"}
                    aria-label={item.isForcedAvailable ? "Rendre indisponible" : "Rendre disponible"}
                  >
                    {item.isForcedAvailable ? <Cloud className="w-3.5 h-3.5" /> : <CloudOff className="w-3.5 h-3.5" />}
                  </button>
                )}

                <div className={`absolute inset-0 bg-black/60 opacity-0 group-hover:opacity-100 transition-opacity duration-300 flex items-center justify-center p-4 ${!item.isLocal ? 'z-30' : ''}`}>
                  {item.isLocal && (
                    <button onClick={(e) => { e.stopPropagation(); if (item.video) onPlay(item.video); }} className="bg-white text-black p-3 rounded-full hover:bg-white/80">
                      <Play className="w-6 h-6 fill-black" />
                    </button>
                  )}
                  <button
                    onClick={(e) => { e.stopPropagation(); onToggleWatched(item.id); }}
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
      )}
    </div>
  );
};
