import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { TransitWeatherPage } from './transit-weather-page';
import { WeatherService } from './weather.service';

/**
 * `WeatherPanel` (Ticket 4) now fills the weather slot, so this page's own
 * tests provide a fake `WeatherService` (same pattern `TasksPage.spec.ts`
 * uses for `TodoService`) rather than hitting real HTTP — `WeatherPanel`'s
 * own spec covers its rendering in depth; this file only checks the page
 * host still wires up both slots correctly.
 */
describe('TransitWeatherPage', () => {
  function configureTestBed() {
    TestBed.configureTestingModule({
      imports: [TransitWeatherPage],
      providers: [{ provide: WeatherService, useValue: { snapshot: signal(undefined) } }],
    });
  }

  it('renders a departures placeholder that clearly reads as not-yet-available', async () => {
    configureTestBed();
    const fixture = TestBed.createComponent(TransitWeatherPage);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('ABFAHRTEN');
    expect(compiled.textContent).toContain('Abfahrten – noch nicht verfügbar');
  });

  it('renders the weather slot as the real WeatherPanel component, not the old placeholder', async () => {
    configureTestBed();
    const fixture = TestBed.createComponent(TransitWeatherPage);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-weather-panel')).not.toBeNull();
    expect(compiled.textContent).toContain('WETTER');
    expect(compiled.textContent).not.toContain('Wetter – noch nicht verfügbar');
  });

  it('renders both slots without errors and with non-blank content', async () => {
    configureTestBed();
    const fixture = TestBed.createComponent(TransitWeatherPage);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    const departuresSlot = compiled.querySelector('.transit-weather-page__slot--departures');
    const weatherPanel = compiled.querySelector('app-weather-panel');
    expect(departuresSlot?.textContent?.trim()).not.toBe('');
    expect(weatherPanel?.textContent?.trim()).not.toBe('');
  });

  it('gives the departures column exactly twice the weather column\'s share (2fr : 1fr, i.e. weather = 1/3)', async () => {
    configureTestBed();
    const fixture = TestBed.createComponent(TransitWeatherPage);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    const grid = compiled.querySelector('.transit-weather-page') as HTMLElement;

    // Honesty note on what this actually proves: jsdom has no real layout
    // engine, so unlike a real browser (which resolves grid-template-columns
    // into an actual list of track sizes in pixels, e.g. "608px 304px" for
    // some concrete container width), jsdom's getComputedStyle just echoes
    // back the specified value string verbatim (confirmed empirically -
    // there's no pixel resolution to assert on here). So this test can only
    // confirm the intended CSS rule actually applies to this element with
    // the correct 2fr:1fr ratio as authored - not that a real browser lays
    // out an exact 2:1 pixel split (which it does, per the CSS Grid spec's
    // definition of <flex> track sizing, but that's a browser-engine
    // guarantee this test genuinely cannot observe under jsdom).
    const computed = getComputedStyle(grid);
    expect(computed.gridTemplateColumns).toBe('minmax(0, 2fr) minmax(0, 1fr)');

    // Column order still matches the design handoff's "Departures widget
    // (left)... Weather widget (right)": the first (2fr, larger) column is
    // the departures slot, the second (1fr, smaller) is the weather panel.
    const children = Array.from(grid.children);
    expect(children[0].classList).toContain('transit-weather-page__slot--departures');
    expect(children[1].tagName.toLowerCase()).toBe('app-weather-panel');
  });
});
