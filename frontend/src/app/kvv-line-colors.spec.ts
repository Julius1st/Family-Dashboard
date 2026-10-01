import { describe, expect, it } from 'vitest';

import { kvvLineColor } from './kvv-line-colors';

describe('kvvLineColor', () => {
  it('resolves a known tram line to its official KVV color (verkehrsgelb)', () => {
    expect(kvvLineColor('4')).toEqual({ background: '#f1c21f', text: '#000000' });
  });

  it('resolves a known Stadtbahn line to its official KVV color (signalviolett)', () => {
    expect(kvvLineColor('S2')).toEqual({ background: '#804387', text: '#ffffff' });
  });

  it('returns undefined for a bus line (not covered by the source data at all)', () => {
    expect(kvvLineColor('42')).toBeUndefined();
    expect(kvvLineColor('60')).toBeUndefined();
  });

  it('returns undefined for any unrecognized line string', () => {
    expect(kvvLineColor('does-not-exist')).toBeUndefined();
  });

  it('resolves the dedicated S41 entry (#bed730), not the grouped S4/S41 entry (#760d37) — see the source-conflict comment', () => {
    expect(kvvLineColor('S41')?.background).toBe('#bed730');
    expect(kvvLineColor('S4')?.background).toBe('#760d37');
  });

  it('picks dark text for bright backgrounds and light text for dark backgrounds', () => {
    // schwefelgelb (S7/S71) is a very bright yellow -> needs dark text.
    expect(kvvLineColor('S7')).toEqual({ background: '#fff90a', text: '#000000' });
    // nachtblau (S6) is a very dark navy -> needs light text.
    expect(kvvLineColor('S6')).toEqual({ background: '#3d2d7c', text: '#ffffff' });
  });

  it('applies grouped lines consistently', () => {
    expect(kvvLineColor('S1')).toEqual(kvvLineColor('S11'));
    expect(kvvLineColor('S11')).toEqual(kvvLineColor('S12'));
    expect(kvvLineColor('9')).toEqual(kvvLineColor('7')); // both anthrazitgrau
  });
});
