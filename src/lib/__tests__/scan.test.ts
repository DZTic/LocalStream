import { describe, it, expect } from 'vitest';
import {
  parseSeriesInfo,
  videoBaseName,
  subtitleBaseName,
  parentFolder,
  buildSubtitleIndex,
  matchSubtitle,
  VIDEO_EXT_RE,
  SUBTITLE_EXT_RE,
} from '../scan';

describe('parseSeriesInfo', () => {
  it('détecte SxxExx', () => {
    expect(parseSeriesInfo('Breaking.Bad.S01E05.1080p.mkv')).toEqual({
      seriesName: 'Breaking Bad',
      season: 1,
      episode: 5,
    });
  });

  it('détecte le format 1x02', () => {
    expect(parseSeriesInfo('The Office 3x12.mp4')).toEqual({
      seriesName: 'The Office',
      season: 3,
      episode: 12,
    });
  });

  it('nettoie les séparateurs et crochets du nom de série', () => {
    const info = parseSeriesInfo('[Team]_Dark-S02E01.mkv');
    expect(info.seriesName).toBe('Team  Dark');
    expect(info.season).toBe(2);
    expect(info.episode).toBe(1);
  });

  it('retombe sur "Série Inconnue" si le nom est vide avant le marqueur', () => {
    expect(parseSeriesInfo('S01E01.mkv').seriesName).toBe('Série Inconnue');
  });

  it('détecte la série via la structure de dossiers', () => {
    expect(parseSeriesInfo('01.mp4', 'Series/Dark/Saison 2/01.mp4')).toEqual({
      seriesName: 'Dark',
      season: 2,
      episode: 1,
    });
    expect(parseSeriesInfo('pilot.mp4', 'Shows/Lost/Season 1/pilot.mp4')).toEqual({
      seriesName: 'Lost',
      season: 1,
      episode: undefined,
    });
  });

  it('renvoie un objet vide pour un film simple', () => {
    expect(parseSeriesInfo('Inception.2010.1080p.mkv', 'Movies/Inception.2010.1080p.mkv')).toEqual({});
  });
});

describe('noms de base', () => {
  it('videoBaseName retire l’extension et passe en minuscules', () => {
    expect(videoBaseName('Film.Test.MKV')).toBe('film.test');
  });

  it('subtitleBaseName retire extension et suffixe de langue', () => {
    expect(subtitleBaseName('Film.Test.fr.srt')).toBe('film.test');
    expect(subtitleBaseName('Film.Test.srt')).toBe('film.test');
  });
});

describe('parentFolder', () => {
  it('renvoie le dossier parent', () => {
    expect(parentFolder('Movies/Sub/film.mp4')).toBe('Movies/Sub');
    expect(parentFolder('film.mp4')).toBe('');
    expect(parentFolder('Movies\\film.mp4')).toBe('Movies');
  });
});

describe('matching des sous-titres', () => {
  const subs = [
    { name: 'Film.fr.srt', folder: 'Movies', uri: 'u1' },
    { name: 'Autre.vtt', folder: 'Movies', uri: 'u2' },
    { name: 'notes.txt', folder: 'Movies', uri: 'u3' },
  ];

  it('associe un sous-titre du même dossier avec le même nom de base', () => {
    const index = buildSubtitleIndex(subs);
    expect(matchSubtitle(index, 'Film.mkv', 'Movies')?.uri).toBe('u1');
    expect(matchSubtitle(index, 'Autre.mp4', 'Movies')?.uri).toBe('u2');
  });

  it('ignore les fichiers non sous-titres et les dossiers différents', () => {
    const index = buildSubtitleIndex(subs);
    expect(matchSubtitle(index, 'notes.mp4', 'Movies')).toBeUndefined();
    expect(matchSubtitle(index, 'Film.mkv', 'Download')).toBeUndefined();
  });
});

describe('regex d’extensions', () => {
  it('reconnaît les vidéos et sous-titres supportés', () => {
    for (const f of ['a.mp4', 'b.MKV', 'c.webm', 'd.avi', 'e.mov']) expect(VIDEO_EXT_RE.test(f)).toBe(true);
    expect(VIDEO_EXT_RE.test('a.txt')).toBe(false);
    for (const f of ['a.srt', 'b.VTT', 'c.ass', 'd.ssa']) expect(SUBTITLE_EXT_RE.test(f)).toBe(true);
    expect(SUBTITLE_EXT_RE.test('a.sub')).toBe(false);
  });
});
