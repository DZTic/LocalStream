import { describe, it, expect } from 'vitest';
import { decodeSubtitleBytes, srt2vtt } from '../utils';

const te = new TextEncoder();

describe('decodeSubtitleBytes', () => {
  it('decode de l\'UTF-8 sans BOM', () => {
    const out = decodeSubtitleBytes(te.encode('00:00:01,000 --> 00:00:02,000\nFor\u00e7a a\u00e9rea\n'));
    expect(out).toContain('For\u00e7a a\u00e9rea');
  });

  it('decode un BOM UTF-8', () => {
    const body = te.encode('Salut \u00e7a va ?');
    const withBom = new Uint8Array([0xef, 0xbb, 0xbf, ...body]);
    expect(decodeSubtitleBytes(withBom)).toBe('Salut \u00e7a va ?');
  });

  it('decode un BOM UTF-16LE', () => {
    const s = 'Caf\u00e9 cr\u00e8me';
    const bytes = new Uint8Array(2 + s.length * 2);
    bytes[0] = 0xff; bytes[1] = 0xfe;
    for (let i = 0; i < s.length; i++) {
      bytes[2 + i * 2] = s.charCodeAt(i) & 0xff;
      bytes[3 + i * 2] = s.charCodeAt(i) >> 8;
    }
    expect(decodeSubtitleBytes(bytes)).toBe(s);
  });

  it('decode du Windows-1252 : les accents sont preserves (issue #48)', () => {
    // "tr\u00e8s fran\u00e7ais : \u00e0 No\u00ebl" encode en Windows-1252
    const cp1252 = new Uint8Array([
      0x74, 0x72, 0xe8, 0x73, 0x20, 0x66, 0x72, 0x61, 0x6e, 0xe7, 0x61, 0x69,
      0x73, 0x20, 0x3a, 0x20, 0xe0, 0x20, 0x4e, 0x6f, 0xeb, 0x6c,
    ]);
    expect(decodeSubtitleBytes(cp1252)).toBe('tr\u00e8s fran\u00e7ais : \u00e0 No\u00ebl');
  });

  it('gere la ligature oe propre a Windows-1252 (0x9c), absente de Latin-1', () => {
    expect(decodeSubtitleBytes(new Uint8Array([0x9c, 0x75, 0x66]))).toBe('\u0153uf');
  });
});

describe('srt2vtt', () => {
  it('n\'empile pas un second en-tete si le contenu est deja du VTT', () => {
    const vtt = srt2vtt('1\n00:00:01,000 --> 00:00:02,000\nHello\n');
    expect(srt2vtt(vtt)).toBe(vtt);
  });
});