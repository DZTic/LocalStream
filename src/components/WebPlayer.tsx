import React, { useState } from 'react';
import { ChevronLeft, Subtitles, Play, Pause, RotateCcw, RotateCw } from 'lucide-react';
import { VideoFile } from '../lib/types';

interface WebPlayerProps {
  video: VideoFile;
  videoRef: React.RefObject<HTMLVideoElement | null>;
  activeSubtitleUrl: string | null;
  onClose: () => void;
  onOpenSubtitles: () => void;
  onEnded: () => void;
  onTimeUpdate: () => void;
  onLoadedMetadata: () => void;
}

type FeedbackType = 'rewind' | 'forward' | 'pause' | 'play';

/**
 * Lecteur vidéo plein écran de la version web (balise <video>).
 * Sur Android natif, la lecture passe par PlayerActivity — ce composant ne sert pas.
 */
export const WebPlayer: React.FC<WebPlayerProps> = ({
  video, videoRef, activeSubtitleUrl, onClose, onOpenSubtitles, onEnded, onTimeUpdate, onLoadedMetadata,
}) => {
  const [feedback, setFeedback] = useState<{ type: FeedbackType, visible: boolean }>({ type: 'pause', visible: false });

  const flashFeedback = (type: FeedbackType) => {
    setFeedback({ type, visible: true });
    setTimeout(() => setFeedback(prev => ({ ...prev, visible: false })), 700);
  };

  return (
    <div className="fixed inset-0 bg-black z-50 flex flex-col">
      <div
        className="absolute top-0 left-0 right-0 px-4 pb-4 bg-gradient-to-b from-black/80 to-transparent z-10 flex items-center justify-between"
        style={{ paddingTop: 'max(env(safe-area-inset-top), 16px)' }}
      >
        <button
          onClick={onClose}
          className="p-2 rounded-full hover:bg-zinc-800 transition-colors bg-black/40 md:bg-transparent"
        >
          <ChevronLeft className="w-6 h-6 md:w-8 md:h-8" />
        </button>
        <div className="flex items-center gap-2">
          <button
            onClick={onOpenSubtitles}
            className="p-2 rounded-full hover:bg-zinc-800 transition-colors flex items-center gap-2 bg-black/40 md:bg-transparent"
          >
            <Subtitles className="w-5 h-5 md:w-6 md:h-6" />
          </button>
        </div>
      </div>

      <div className="flex-1 flex items-center justify-center bg-black relative group">
        <video
          ref={videoRef}
          src={video.url}
          controls
          autoPlay
          onEnded={onEnded}
          onTimeUpdate={onTimeUpdate}
          onLoadedMetadata={onLoadedMetadata}
          crossOrigin="anonymous"
          className="w-full h-full max-h-screen object-contain"
        >
          {activeSubtitleUrl && (
            <track
              kind="subtitles"
              src={activeSubtitleUrl}
              srcLang="fr"
              label="Français"
              default
            />
          )}
        </video>

        {/* Touch Controls Overlay - utilise onTouchEnd pour Android */}
        <div
          className="absolute inset-0 flex z-30"
          style={{ bottom: '60px' }}
        >
          {/* Zone gauche - Reculer 10s */}
          <div
            className="flex-1 h-full flex items-center justify-center"
            onTouchEnd={(e) => {
              e.stopPropagation();
              e.preventDefault();
              if (videoRef.current) {
                videoRef.current.currentTime = Math.max(0, videoRef.current.currentTime - 10);
                flashFeedback('rewind');
              }
            }}
          />
          {/* Zone centre - Pause/Play */}
          <div
            className="w-[34%] h-full flex items-center justify-center"
            onTouchEnd={(e) => {
              e.stopPropagation();
              e.preventDefault();
              if (videoRef.current) {
                if (videoRef.current.paused) {
                  videoRef.current.play();
                  flashFeedback('play');
                } else {
                  videoRef.current.pause();
                  flashFeedback('pause');
                }
              }
            }}
          />
          {/* Zone droite - Avancer 10s */}
          <div
            className="flex-1 h-full flex items-center justify-center"
            onTouchEnd={(e) => {
              e.stopPropagation();
              e.preventDefault();
              if (videoRef.current) {
                videoRef.current.currentTime = Math.min(videoRef.current.duration || 0, videoRef.current.currentTime + 10);
                flashFeedback('forward');
              }
            }}
          />
        </div>

        {/* Feedback UI */}
        {feedback.visible && (
          <div className="absolute inset-0 flex items-center justify-center pointer-events-none z-50">
            <div className="bg-black/60 backdrop-blur-md rounded-full p-8 animate-out fade-out zoom-out duration-500">
              {feedback.type === 'rewind' && <RotateCcw className="w-12 h-12 text-white animate-in slide-in-from-right-4" />}
              {feedback.type === 'forward' && <RotateCw className="w-12 h-12 text-white animate-in slide-in-from-left-4" />}
              {feedback.type === 'pause' && <Pause className="w-12 h-12 text-white scale-110" />}
              {feedback.type === 'play' && <Play className="w-12 h-12 text-white scale-110 fill-white" />}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
