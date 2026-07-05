import React from 'react';
import { Settings, FolderOpen, Search, X, RefreshCw } from 'lucide-react';

interface AppHeaderProps {
  isScrolled: boolean;
  activeTab: 'home' | 'playlists' | 'history';
  searchQuery: string;
  showSearch: boolean;
  isSearchOpen: boolean;
  isFetchingMetadata: boolean;
  searchInputRef: React.RefObject<HTMLInputElement | null>;
  onSearchChange: (value: string) => void;
  onOpenSearch: () => void;
  onCloseSearch: () => void;
  onLogoClick: () => void;
  onOpenFolder: () => void;
  onOpenSettings: () => void;
}

/** Header fixe : logo, recherche (plein header sur mobile), dossier, réglages. */
export const AppHeader: React.FC<AppHeaderProps> = ({
  isScrolled, activeTab, searchQuery, showSearch, isSearchOpen, isFetchingMetadata, searchInputRef,
  onSearchChange, onOpenSearch, onCloseSearch, onLogoClick, onOpenFolder, onOpenSettings,
}) => (
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
            onChange={(e) => onSearchChange(e.target.value)}
            className="w-full bg-zinc-900/80 border border-zinc-700 text-white text-sm rounded-full pl-9 pr-4 py-2 focus:outline-none focus:border-zinc-500 focus:bg-black placeholder:text-zinc-500"
          />
        </div>
        <button
          onClick={onCloseSearch}
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
        onClick={onLogoClick}
      >
        LOCALSTREAM
      </h1>
    </div>
    <div className={`${isSearchOpen ? 'hidden md:flex' : 'flex'} items-center gap-1 md:gap-4 shrink-0`}>
      {isFetchingMetadata && (
        <RefreshCw className="w-4 h-4 animate-spin text-red-500 shrink-0" aria-label="Récupération des métadonnées TMDB en cours" />
      )}
      {/* Desktop : champ toujours affiché (place suffisante) */}
      {showSearch && (
        <div className="relative hidden md:flex items-center">
          <Search className="w-5 h-5 text-white absolute left-3" />
          <input
            type="text"
            placeholder="Titre..."
            aria-label="Rechercher un titre"
            value={searchQuery}
            onChange={(e) => onSearchChange(e.target.value)}
            className="bg-zinc-900/80 border border-zinc-700 text-white text-sm rounded-full pl-10 pr-4 py-1.5 focus:outline-none focus:border-zinc-500 focus:bg-black transition-all w-64 placeholder:text-zinc-500"
          />
        </div>
      )}
      {/* Mobile : icône loupe qui ouvre la recherche plein header */}
      {showSearch && (
        <button
          onClick={onOpenSearch}
          aria-label="Rechercher"
          className="md:hidden p-2 rounded-full hover:bg-zinc-800/50 transition-colors"
        >
          <Search className="w-5 h-5 text-white" />
        </button>
      )}
      <button
        onClick={onOpenFolder}
        aria-label="Changer de dossier"
        className="text-sm font-medium text-white hover:text-zinc-300 transition flex items-center gap-2 md:bg-transparent p-2 md:p-0 rounded-full md:rounded-none"
      >
        <FolderOpen className="w-5 h-5 md:hidden" />
        <span className="hidden md:inline">Changer de dossier</span>
      </button>
      <button
        onClick={onOpenSettings}
        aria-label="Paramètres"
        className="p-2 rounded-full hover:bg-zinc-800/50 transition-colors"
      >
        <Settings className="w-5 h-5 text-white" />
      </button>
    </div>
  </header>
);
