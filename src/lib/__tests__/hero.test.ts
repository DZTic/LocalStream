import { describe, it, expect } from 'vitest';
import { getHeroCandidates, isFullyWatched } from '../hero';
import type { VideoFile } from '../types';

const v = (name: string, extra: Partial<VideoFile> = {}): VideoFile => ({
  url: 'blob:x', name, type: 'video/mp4', path: name, ...extra,
});

describe('isFullyWatched', () => {
  it('film : suit son propre état', () => {
    expect(isFullyWatched(v('Film.mkv'), { 'Film.mkv': true })).toBe(true);
    expect(isFullyWatched(v('Film.mkv'), {})).toBe(false);
  });
  it('série : terminée seulement si tous les épisodes sont vus', () => {
    const s = v('S', { isSeriesGroup: true, episodes: [v('S.E1.mkv'), v('S.E2.mkv')] });
    expect(isFullyWatched(s, { 'S.E1.mkv': true })).toBe(false);
    expect(isFullyWatched(s, { 'S.E1.mkv': true, 'S.E2.mkv': true })).toBe(true);
  });
});

describe('getHeroCandidates', () => {
  it('exclut les contenus entièrement vus', () => {
    const res = getHeroCandidates(
      [v('Vu.mkv'), v('NonVu.mkv')],
      { 'Vu.mkv': true },
    );
    expect(res.map(r => r.name)).toEqual(['NonVu.mkv']);
  });

  it('ne se limite pas au dernier ajouté : retourne tous les non-vus pour rotation', () => {
    const res = getHeroCandidates(
      [v('A.mkv', { lastModified: 1 }), v('B.mkv', { lastModified: 2 }), v('C.mkv', { lastModified: 3 })],
      {},
    );
    expect(res).toHaveLength(3);
    // le plus récent en tête, mais les autres restent candidats
    expect(res[0].name).toBe('C.mkv');
  });

  it('priorise les contenus en cours avant les non commencés', () => {
    const res = getHeroCandidates(
      [v('Recent.mkv', { lastModified: 10 }), v('EnCours.mkv', { lastModified: 1 })],
      {},
      { 'EnCours.mkv': 40 },
    );
    expect(res[0].name).toBe('EnCours.mkv');
  });

  it('ignore une progression terminée (>= 95%)', () => {
    const res = getHeroCandidates(
      [v('Presque.mkv', { lastModified: 1 }), v('Recent.mkv', { lastModified: 10 })],
      {},
      { 'Presque.mkv': 98 },
    );
    // 98% n'est pas "en cours" → l'ordre retombe sur la récence
    expect(res[0].name).toBe('Recent.mkv');
  });

  it('renvoie une liste vide si tout est vu', () => {
    expect(getHeroCandidates([v('A.mkv')], { 'A.mkv': true })).toHaveLength(0);
  });
});
