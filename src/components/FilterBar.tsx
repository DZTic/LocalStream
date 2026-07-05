import React from 'react';
import { TMDB_GENRES } from '../lib/types';

export type SortBy = 'alpha' | 'date' | 'size' | 'duration';

interface FilterBarProps {
  sortBy: SortBy;
  onSortBy: (value: SortBy) => void;
  filterGenre: number | 'all';
  onFilterGenre: (value: number | 'all') => void;
  filterResolution: string | 'all';
  onFilterResolution: (value: string) => void;
}

/** Barre de tri et de filtres (genre, qualité) de la vue bibliothèque/recherche. */
export const FilterBar: React.FC<FilterBarProps> = ({
  sortBy, onSortBy, filterGenre, onFilterGenre, filterResolution, onFilterResolution,
}) => (
  <div
    className="px-4 md:px-12 pt-28 pb-6 flex flex-col md:flex-row items-center gap-3 z-30 w-full animate-in fade-in slide-in-from-top-4 duration-500"
    style={{ marginTop: 'max(env(safe-area-inset-top), 0px)' }}
  >
    <div className="flex flex-row flex-wrap pb-2 md:pb-0 w-full gap-2 md:gap-4">

      <div className="flex items-center gap-2 bg-zinc-900/80 backdrop-blur-md border border-zinc-700/50 rounded-full px-4 py-2 flex-shrink-0 shadow-lg">
        <span className="text-zinc-400 font-medium text-xs uppercase tracking-wider hidden sm:inline">Trier par:</span>
        <select
          value={sortBy}
          onChange={(e) => onSortBy(e.target.value as SortBy)}
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
          onChange={(e) => onFilterGenre(e.target.value === 'all' ? 'all' : Number(e.target.value))}
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
          onChange={(e) => onFilterResolution(e.target.value)}
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
);
