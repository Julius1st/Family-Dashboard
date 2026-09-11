import { Component, ViewEncapsulation } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it } from 'vitest';

/*
 * Proves the light-mode color tokens declared in tokens.css's
 * `:root[data-theme="light"]` block actually take over once
 * `data-theme="light"` is set on `document.documentElement` — and, just
 * as importantly, that the one deliberately theme-invariant token
 * (`--ink-on-accent`) does NOT get a light-mode override while the new
 * `--ink-on-pill-active` token DOES diverge between themes.
 *
 * Same technique as `global-theme.spec.ts` (Ticket 1): mount a throwaway
 * component whose `styleUrls` point at the real `tokens.css`, with
 * `ViewEncapsulation.None` so the unscoped `:root`/`:root[data-theme]`
 * selectors apply exactly as authored in the shipped app, then read
 * values back with `getComputedStyle`.
 */
@Component({
  selector: 'app-light-theme-probe',
  template: '',
  styleUrls: ['./tokens.css'],
  encapsulation: ViewEncapsulation.None,
})
class LightThemeProbeComponent {}

describe('light theme tokens', () => {
  afterEach(() => {
    delete document.documentElement.dataset['theme'];
  });

  it('defaults to the dark token values when no data-theme is set', async () => {
    await TestBed.configureTestingModule({
      imports: [LightThemeProbeComponent],
    }).compileComponents();
    const fixture = TestBed.createComponent(LightThemeProbeComponent);
    await fixture.whenStable();

    const rootStyle = getComputedStyle(document.documentElement);
    expect(rootStyle.getPropertyValue('--bg-page').trim()).toBe('oklch(0.155 0.012 265)');
    expect(rootStyle.getPropertyValue('--ink-primary').trim()).toBe('oklch(0.96 0.005 265)');
    expect(rootStyle.getPropertyValue('--ink-on-accent').trim()).toBe('oklch(0.19 0.012 265)');
    expect(rootStyle.getPropertyValue('--ink-on-pill-active').trim()).toBe('oklch(0.19 0.012 265)');
  });

  it('resolves the light-mode palette once data-theme="light" is set', async () => {
    await TestBed.configureTestingModule({
      imports: [LightThemeProbeComponent],
    }).compileComponents();
    const fixture = TestBed.createComponent(LightThemeProbeComponent);
    await fixture.whenStable();

    document.documentElement.dataset['theme'] = 'light';

    const rootStyle = getComputedStyle(document.documentElement);

    // A representative sample across every color group.
    expect(rootStyle.getPropertyValue('--bg-page').trim()).toBe('oklch(0.95 0.006 265)');
    expect(rootStyle.getPropertyValue('--bg-surface').trim()).toBe('oklch(0.99 0.003 265)');
    expect(rootStyle.getPropertyValue('--border-widget').trim()).toBe('oklch(0.83 0.01 265)');
    expect(rootStyle.getPropertyValue('--ink-primary').trim()).toBe('oklch(0.22 0.01 265)');
    expect(rootStyle.getPropertyValue('--ink-muted').trim()).toBe('oklch(0.52 0.012 265)');
    expect(rootStyle.getPropertyValue('--status-late').trim()).toBe('oklch(0.55 0.16 75)');
    expect(rootStyle.getPropertyValue('--accent-rain').trim()).toBe('oklch(0.55 0.14 235)');

    // The tricky split this ticket calls out explicitly: the new
    // `--ink-on-pill-active` token diverges to the light-mode value...
    expect(rootStyle.getPropertyValue('--ink-on-pill-active').trim()).toBe('oklch(0.98 0.005 265)');
    // ...while `--ink-on-accent` stays fixed at its one dark-block
    // definition, since it is intentionally absent from the light block.
    expect(rootStyle.getPropertyValue('--ink-on-accent').trim()).toBe('oklch(0.19 0.012 265)');

    // Structural (font/radius/spacing) tokens are theme-agnostic and are
    // not duplicated in the light block, so they're unaffected.
    const normalize = (value: string) => value.replace(/\s+/g, ' ').replace(/"/g, "'").trim();
    expect(normalize(rootStyle.getPropertyValue('--font-sans'))).toBe(
      "'Schibsted Grotesk', system-ui, sans-serif",
    );
    expect(rootStyle.getPropertyValue('--radius-widget').trim()).toBe('18px');
  });
});
