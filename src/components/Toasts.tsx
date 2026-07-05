import React from 'react';
import { AlertTriangle, Check, Info } from 'lucide-react';

export type ToastType = 'success' | 'error' | 'info';

export interface Toast {
  id: number;
  message: string;
  type: ToastType;
}

interface ToastsProps {
  toasts: Toast[];
}

/** Pile de toasts non bloquants (remplacent les alert() de la WebView). */
export const Toasts: React.FC<ToastsProps> = ({ toasts }) => {
  if (toasts.length === 0) return null;
  return (
    <div
      className="fixed left-4 right-4 md:left-auto md:right-8 md:w-96 z-[120] flex flex-col gap-2 pointer-events-none"
      style={{ bottom: 'calc(max(env(safe-area-inset-bottom), 8px) + 76px)' }}
      role="status"
      aria-live="polite"
    >
      {toasts.map(toast => (
        <div
          key={toast.id}
          className={`flex items-start gap-3 px-4 py-3 rounded-xl shadow-2xl border text-sm font-medium animate-in fade-in slide-in-from-bottom-4 duration-300 ${
            toast.type === 'error' ? 'bg-red-950/95 border-red-800 text-red-100'
            : toast.type === 'success' ? 'bg-emerald-950/95 border-emerald-800 text-emerald-100'
            : 'bg-zinc-900/95 border-zinc-700 text-zinc-100'
          }`}
        >
          {toast.type === 'error' ? <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
            : toast.type === 'success' ? <Check className="w-4 h-4 shrink-0 mt-0.5" />
            : <Info className="w-4 h-4 shrink-0 mt-0.5" />}
          <span className="flex-1">{toast.message}</span>
        </div>
      ))}
    </div>
  );
};
