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
});
