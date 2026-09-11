import { Component, ViewEncapsulation } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

/*
 * Proves the kiosk-only `html, body` rules in the real `styles.css` are
 * actually wired up, not just declared-but-unused — same technique as
 * `styles/global-theme.spec.ts`: mount a throwaway component whose
 * `styleUrls` point at the real stylesheet, with `ViewEncapsulation.None`
 * so the unscoped `html, body` selector applies exactly as authored in the
 * shipped app, then read values back with `getComputedStyle`.
 *
 * Added alongside the `overflow: hidden` backstop (a second real-device
 * report of the Tasks widget outgrowing the screen, traced further up the
 * chain to `app.css`'s `:host` — see `app.spec.ts`'s companion test): this
 * is a single-purpose kiosk page with exactly one intentional scroll
 * region per screen, so the page itself must never be able to scroll or
 * grow past the viewport no matter what a layout bug further down the
 * tree does.
 */
@Component({
  selector: 'app-global-styles-probe',
  template: '',
  styleUrls: ['./styles.css'],
  encapsulation: ViewEncapsulation.None,
})
class GlobalStylesProbeComponent {}

describe('global kiosk styles (styles.css)', () => {
  it('never lets html/body scroll or grow past the viewport', async () => {
    await TestBed.configureTestingModule({
      imports: [GlobalStylesProbeComponent],
    }).compileComponents();
    const fixture = TestBed.createComponent(GlobalStylesProbeComponent);
    await fixture.whenStable();

    const htmlStyle = getComputedStyle(document.documentElement);
    const bodyStyle = getComputedStyle(document.body);

    expect(htmlStyle.overflow).toBe('hidden');
    expect(bodyStyle.overflow).toBe('hidden');

    // Pre-existing kiosk rules on the same selector, unaffected by adding
    // `overflow: hidden` alongside them.
    expect(htmlStyle.height).toBe('100%');
    expect(bodyStyle.height).toBe('100%');
    expect(bodyStyle.margin).toBe('0px');
  });
});
