import React from 'react';
import { Settings, FolderOpen, Search } from 'lucide-react';

interface AppHeaderProps {
  isScrolled: boolean;
  activeTab: 'home' | 'playlists' | 'history';
  searchQuery: string;
  showSearch: boolean;
  onSearchChange: (value: string) => void;
  onLogoClick: () => void;
  onOpenFolder: () => void;
  onOpenSettings: () => void;
}

/** Header fixe : logo, recherche, changement de dossier, réglages. */
export const AppHeader: React.FC<AppHeaderProps> = ({
  isScrolled, activeTab, searchQuery, showSearch, onSearchChange, onLogoClick, onOpenFolder, onOpenSettings,
}) => (
  <header
    className={`fixed top-0 z-50 w-full px-4 md:px-12 pb-3 md:pb-4 flex items-center justify-between transition-colors duration-300 ${(isScrolled || searchQuery.trim() || activeTab !== 'home') ? 'bg-black shadow-lg' : 'bg-gradient-to-b from-black/80 to-transparent'}`}
    style={{ paddingTop: 'max(env(safe-area-inset-top), 16px)' }}
  >
    <div className="flex items-center gap-6 md:gap-10">
      <h1
        className="text-lg md:text-3xl font-black text-red-600 tracking-tighter cursor-pointer active:scale-95 transition-transform"
        onClick={onLogoClick}
      >
        LOCALSTREAM
      </h1>

    </div>
    <div className="flex items-center gap-2 md:gap-4">
      {showSearch && (
        <div className="relative flex items-center">
          <Search className="w-4 h-4 md:w-5 md:h-5 text-white absolute left-3" />
          <input
            type="text"
            placeholder="Rechercher..."
            value={searchQuery}
            onChange={(e) => onSearchChange(e.target.value)}
            className="bg-zinc-900/80 border border-zinc-700 text-white text-sm rounded-full pl-9 md:pl-10 pr-4 py-1.5 focus:outline-none focus:border-zinc-500 focus:bg-black transition-all w-24 md:w-64 placeholder:text-zinc-500"
          />
        </div>
      )}
      <button
        onClick={onOpenFolder}
        className="text-sm font-medium text-white hover:text-zinc-300 transition flex items-center gap-2 bg-zinc-800/50 md:bg-transparent px-3 py-1.5 md:p-0 rounded-full md:rounded-none"
      >
        <FolderOpen className="w-4 h-4 md:hidden" />
        <span className="hidden md:inline">Changer de dossier</span>
      </button>
      <button
        onClick={onOpenSettings}
        className="p-2 rounded-full hover:bg-zinc-800/50 transition-colors"
      >
        <Settings className="w-5 h-5 text-white" />
      </button>
    </div>
  </header>
);
