import { describe, expect, it } from 'vitest';

import { memberColor } from './member-color';

describe('memberColor', () => {
  it('returns a sensible first hue for index 0', () => {
    expect(memberColor(0)).toBe('oklch(0.74 0.13 0)');
  });

  it('steps the hue by 45° per index', () => {
    expect(memberColor(1)).toBe('oklch(0.74 0.13 45)');
    expect(memberColor(2)).toBe('oklch(0.74 0.13 90)');
    expect(memberColor(3)).toBe('oklch(0.74 0.13 135)');
  });

  it('produces different colors for different indices', () => {
    const colors = [0, 1, 2, 3, 4, 5, 6, 7].map(memberColor);
    expect(new Set(colors).size).toBe(colors.length);
  });

  it('wraps the hue around 360° so any number of members stays valid', () => {
    // index 8 * 45 = 360 -> wraps to 0, matching index 0's hue exactly.
    expect(memberColor(8)).toBe(memberColor(0));
  });

  it('always uses the fixed L=0.74 / C=0.13 from the design handoff', () => {
    for (const index of [0, 1, 5, 12, 100]) {
      expect(memberColor(index)).toMatch(/^oklch\(0\.74 0\.13 \d+(\.\d+)?\)$/);
    }
  });
});
