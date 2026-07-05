import React from 'react';

export interface ConfirmDialogState {
  message: string;
  onConfirm: () => void;
}

interface ConfirmDialogProps {
  dialog: ConfirmDialogState;
  onCancel: () => void;
  onConfirm: () => void;
}

/** Dialogue de confirmation pour les actions destructives (suppression…). */
export const ConfirmDialog: React.FC<ConfirmDialogProps> = ({ dialog, onCancel, onConfirm }) => (
  <div
    className="fixed inset-0 bg-black/80 z-[130] flex items-center justify-center p-6"
    onClick={onCancel}
  >
    <div
      className="bg-zinc-900 border border-zinc-700 rounded-2xl w-full max-w-sm p-6 shadow-2xl animate-in fade-in zoom-in-95 duration-300"
      onClick={(e) => e.stopPropagation()}
    >
      <p className="text-white text-sm leading-relaxed mb-6">{dialog.message}</p>
      <div className="grid grid-cols-2 gap-3">
        <button
          onClick={onCancel}
          className="py-3 rounded-xl bg-zinc-800 hover:bg-zinc-700 text-white text-sm font-bold transition-colors"
        >
          Annuler
        </button>
        <button
          onClick={onConfirm}
          className="py-3 rounded-xl bg-red-600 hover:bg-red-700 text-white text-sm font-black transition-colors"
        >
          Confirmer
        </button>
      </div>
    </div>
  </div>
);
