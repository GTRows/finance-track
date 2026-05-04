import { describe, it, expect } from 'vitest';
import { base64UrlToBuffer, bufferToBase64Url } from './base64url';

describe('base64url helpers', () => {
  it('round-trips arbitrary bytes', () => {
    const original = new Uint8Array([0, 1, 2, 254, 255, 65, 66, 67, 128]);
    const encoded = bufferToBase64Url(original.buffer);
    const decoded = base64UrlToBuffer(encoded);
    expect(Array.from(decoded)).toEqual(Array.from(original));
  });

  it('emits unpadded base64url with url-safe characters', () => {
    const bytes = new Uint8Array([62, 63, 0]);
    const encoded = bufferToBase64Url(bytes);
    expect(encoded).not.toMatch(/=/);
    expect(encoded).not.toMatch(/[+/]/);
  });

  it('decodes a value missing padding', () => {
    const decoded = base64UrlToBuffer('AQID');
    expect(Array.from(decoded)).toEqual([1, 2, 3]);
  });

  it('decodes a 32-byte challenge round trip', () => {
    const original = new Uint8Array(32).map((_, i) => (i * 7) & 0xff);
    const encoded = bufferToBase64Url(original);
    expect(encoded).toHaveLength(43);
    const decoded = base64UrlToBuffer(encoded);
    expect(Array.from(decoded)).toEqual(Array.from(original));
  });
});
