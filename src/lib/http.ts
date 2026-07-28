import { Capacitor, CapacitorHttp } from '@capacitor/core';
import { base64ToUint8Array } from './utils';

/**
 * Effectue une requête HTTP vers une API externe.
 * - Sur Android (natif) : via CapacitorHttp, qui contourne nativement le CORS.
 * - Sur le web : via le proxy Express (/api/os/proxy) défini dans server.ts.
 * Retourne une interface compatible fetch (json()/text()).
 */
export const externalRequest = async (opts: {
  url: string;
  method?: string;
  headers?: Record<string, string>;
  body?: any;
  /** 'binary' : recupere les octets bruts (l'appelant detecte l'encodage). */
  responseType?: 'binary';
}): Promise<{ json: () => Promise<any>; text: () => Promise<string>; arrayBuffer: () => Promise<ArrayBuffer> }> => {
  if (Capacitor.isNativePlatform()) {
    const res = await CapacitorHttp.request({
      url: opts.url,
      method: opts.method || 'GET',
      headers: opts.headers || {},
      data: opts.body,
      ...(opts.responseType === 'binary' ? { responseType: 'arraybuffer' as const } : {}),
    });
    return {
      json: async () => (typeof res.data === 'string' ? JSON.parse(res.data) : res.data),
      text: async () => (typeof res.data === 'string' ? res.data : JSON.stringify(res.data)),
      arrayBuffer: async () => {
        if (res.data instanceof ArrayBuffer) return res.data;
        if (typeof res.data === 'string') {
          // Selon la version du bridge, les binaires arrivent en base64
          return base64ToUint8Array(res.data).buffer as ArrayBuffer;
        }
        throw new Error('Reponse binaire inattendue (CapacitorHttp)');
      },
    };
  }
  const response = await fetch('/api/os/proxy', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(opts),
  });
  return {
    json: () => response.json(),
    text: () => response.text(),
    arrayBuffer: () => response.arrayBuffer(),
  };
};
