import { Component, ViewEncapsulation } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

/*
 * Proves the design tokens declared in tokens.css are actually wired into
 * the global dark theme (global-theme.css), not just declared-but-unused
 * (Ticket 1 acceptance criterion).
 *
 * This mounts a throwaway component whose `styleUrls` point at the real
 * global stylesheets (not a hand-copied string), via Angular's normal
 * component-style pipeline with `ViewEncapsulation.None` so the `:root`/
 * `html, body` selectors in those files stay unscoped exactly as authored
 * in the shipped app. Vitest/jsdom's getComputedStyle resolves custom
 * property *values* (proving tokens.css's declarations reach the DOM) but
 * does not substitute var() inside shorthand properties like `background`
 * — so `background`/`color`/`font-family` are asserted to literally
 * reference the right custom property name, which is exactly what proves
 * global-theme.css is wired to tokens.css rather than using a hardcoded
 * color/font.
 */
@Component({
  selector: 'app-global-theme-probe',
  template: '',
  styleUrls: ['./tokens.css', './global-theme.css'],
  encapsulation: ViewEncapsulation.None,
})
class GlobalThemeProbeComponent {}

describe('global theme tokens (Ticket 1)', () => {
  it('wires bg/page, ink/primary and the sans font onto html/body', async () => {
    await TestBed.configureTestingModule({
      imports: [GlobalThemeProbeComponent],
    }).compileComponents();
    const fixture = TestBed.createComponent(GlobalThemeProbeComponent);
    await fixture.whenStable();

    const rootStyle = getComputedStyle(document.documentElement);

    // The token custom properties resolve to the exact oklch values from
    // the design handoff's token table.
    expect(rootStyle.getPropertyValue('--bg-page').trim()).toBe('oklch(0.155 0.012 265)');
    expect(rootStyle.getPropertyValue('--ink-primary').trim()).toBe('oklch(0.96 0.005 265)');
    // jsdom's CSSOM re-serializes the custom property (normalizes quotes
    // and whitespace), so compare on the normalized family list rather
    // than a byte-exact string.
    const normalize = (value: string) => value.replace(/\s+/g, ' ').replace(/"/g, "'").trim();
    expect(normalize(rootStyle.getPropertyValue('--font-sans'))).toBe(
      "'Schibsted Grotesk', system-ui, sans-serif",
    );
    expect(normalize(rootStyle.getPropertyValue('--font-mono'))).toBe(
      "'IBM Plex Mono', ui-monospace, monospace",
    );

    // The global html/body rule actually references those tokens by name
    // (not a hardcoded literal color/font standing in for them).
    expect(rootStyle.background).toContain('var(--bg-page)');
    expect(rootStyle.color).toContain('var(--ink-primary)');
    expect(rootStyle.fontFamily).toContain('var(--font-sans)');
  });
});
