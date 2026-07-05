import React from 'react';
import { FolderOpen } from 'lucide-react';

interface PermissionGateProps {
  onRequest: () => void;
  onOpenSystemSettings: () => void;
}

/** Écran bloquant demandant l'accès au stockage (Android). */
export const PermissionGate: React.FC<PermissionGateProps> = ({ onRequest, onOpenSystemSettings }) => (
  <div className="fixed inset-0 bg-black z-[100] flex items-center justify-center p-6 text-center">
    <div className="max-w-sm">
      <FolderOpen className="w-16 h-16 text-red-600 mx-auto mb-6" />
      <h2 className="text-2xl font-bold mb-4">Accès au stockage</h2>
      <p className="text-zinc-400 mb-8 text-sm leading-relaxed">
        LocalStream a besoin d'accéder à vos dossiers pour scanner et afficher vos vidéos.
        Veuillez accorder les permissions nécessaires pour continuer.
      </p>
      <button
        onClick={onRequest}
        className="w-full bg-red-600 hover:bg-red-700 text-white font-black py-4 rounded-2xl transition-all shadow-xl shadow-red-900/20 active:scale-95 uppercase tracking-widest text-xs"
      >
        Accorder l'accès
      </button>
      <button
        onClick={onOpenSystemSettings}
        className="w-full mt-3 bg-zinc-800 hover:bg-zinc-700 text-white/80 font-bold py-3 rounded-2xl transition-all text-xs"
      >
        Ouvrir les réglages système
      </button>
      <p className="mt-4 text-[10px] text-zinc-600 uppercase tracking-widest font-black">
        Si la demande n'apparaît plus, activez la permission dans les réglages système.
      </p>
    </div>
  </div>
);
