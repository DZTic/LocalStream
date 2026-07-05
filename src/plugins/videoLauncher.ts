import { registerPlugin } from '@capacitor/core';

// Pont vers le plugin natif Android (android/app/src/main/java/com/localstream/app/VideoLauncherPlugin.java)

export interface ScannedVideo {
  name: string;
  /** Chemin absolu sur le stockage (MediaStore DATA), ex: /storage/emulated/0/Movies/x.mp4 */
  path: string;
  /** URI content:// MediaStore */
  uri: string;
  size: number;
  /** Timestamp de modification en millisecondes */
  lastModified: number;
  /** Durée en millisecondes (0 si inconnue) */
  durationMs: number;
  /** Chemin relatif au stockage, ex: Movies/Sub/x.mp4 */
  relativePath: string;
}

export interface ScannedSubtitle {
  name: string;
  path: string;
  uri: string;
  relativePath: string;
}

export interface VideoLauncherPlugin {
  openVideo(options: {
    path: string,
    title?: string,
    startPosition?: number,
    playerType?: string,
    packageId?: string,
    subtitlePath?: string
  }): Promise<void>;
  getList(): Promise<{ players: { name: string, packageId: string }[] }>;
  openSettings(): Promise<void>;
  pickSubtitle(): Promise<{ path: string, name: string }>;
  checkStoragePermission(): Promise<{ granted: boolean }>;
  requestStoragePermission(): Promise<{ granted: boolean }>;
  /** Scan de la bibliothèque via MediaStore (rapide, avec durées) */
  scanVideos(): Promise<{ videos: ScannedVideo[], subtitles: ScannedSubtitle[] }>;
}

export const VideoLauncher = registerPlugin<VideoLauncherPlugin>('VideoLauncher');
