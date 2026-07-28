import { useEffect, useRef } from 'react';
import { Filesystem, Directory, Encoding } from '@capacitor/filesystem';
import { Capacitor } from '@capacitor/core';

/** Nom du fichier de sauvegarde lu par l\u2019app native Phase 4. */
const BACKUP_FILENAME = 'localstream-backup.json';

/**
 * Cl\u00e9s localStorage \u00e0 exporter. Correspond \u00e0 l\u2019inventaire de l\u2019issue #36.
 *
 * Note : `tmdb_cache_*` n\u2019est PAS export\u00e9 — il sera r\u00e9g\u00e9n\u00e9r\u00e9 par l\u2019app native
 * (Phase 5) et ne sert \u00e0 rien sans le contexte Capacitor.
 */
const LS_KEYS = [
  'watchedVideos',
  'watchProgress',
  'watchPositions',
  'recentlyWatched',
  'whitelistedVideos',
  'forceAvailableVideos',
  'playlists',
  'tmdbApiKey',
  'osApiKey',
  'osUsername',
  'osPassword',
  'videoPlayer',
  'selectedExternalPlayer',
] as const;

/** D\u00e9lai de d\u00e9bounce en ms avant de relancer l\u2019\u00e9criture du backup. */
const DEBOUNCE_MS = 2_000;

/**
 * Construit le JSON de sauvegarde depuis le localStorage courant.
 * Chaque cl\u00e9 est inclu\u00e9e seulement si elle existe et est parseable.
 */
function buildBackupPayload(): Record<string, unknown> {
  const payload: Record<string, unknown> = {};
  for (const key of LS_KEYS) {
    const raw = localStorage.getItem(key);
    if (raw === null) continue;
    try {
      payload[key] = JSON.parse(raw);
    } catch {
      // valeur non-JSON (string brute) — stocker telle quelle
      payload[key] = raw;
    }
  }
  return payload;
}

/**
 * \u00c9crit `localstream-backup.json` dans `Directory.Data` de l\u2019app Capacitor.
 * Le fichier est lu au premier d\u00e9marrage de l\u2019app native (Phase 4) puis renomm\u00e9
 * en `.imported` pour \u00e9viter un double import.
 */
async function writeBackup(): Promise<void> {
  if (!Capacitor.isNativePlatform()) return;
  try {
    const payload = buildBackupPayload();
    await Filesystem.writeFile({
      path: BACKUP_FILENAME,
      data: JSON.stringify(payload),
      directory: Directory.Data,
      encoding: Encoding.UTF8,
    });
  } catch (err) {
    // L\u2019\u00e9chec de l\u2019\u00e9criture du backup ne doit jamais bloquer l\u2019app.
    console.warn('[useLegacyBackup] \u00c9chec \u00e9criture backup :', err);
  }
}

/**
 * Hook React qui maintient automatiquement `localstream-backup.json` \u00e0 jour.
 *
 * - Premier \u00e9crit au montage (d\u00e8s que l\u2019app est pr\u00eate).
 * - R\u00e9\u00e9crit \u00e0 chaque changement de l\u2019une des cl\u00e9s surveill\u00e9es, avec d\u00e9bounce.
 * - N\u2019a aucun effet sur le web (Capacitor.isNativePlatform() = false).
 *
 * @param watchTokens Tableau de valeurs r\u00e9actives \u00e0 surveiller (ex. watchedVideos,
 *   playlists, etc.). Un changement d\u2019une des valeurs d\u00e9clenche un re-export.
 */
export function useLegacyBackup(watchTokens: unknown[]): void {
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (!Capacitor.isNativePlatform()) return;

    if (timerRef.current) clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => {
      writeBackup();
    }, DEBOUNCE_MS);

    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, watchTokens);

  // \u00c9criture initiale au montage sans d\u00e9bounce
  useEffect(() => {
    writeBackup();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps
}
