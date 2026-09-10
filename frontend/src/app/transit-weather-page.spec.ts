import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { TransitWeatherPage } from './transit-weather-page';

describe('TransitWeatherPage', () => {
  it('renders a departures placeholder that clearly reads as not-yet-available', async () => {
    TestBed.configureTestingModule({ imports: [TransitWeatherPage] });
    const fixture = TestBed.createComponent(TransitWeatherPage);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('ABFAHRTEN');
    expect(compiled.textContent).toContain('Abfahrten – noch nicht verfügbar');
  });

  it('renders a weather placeholder that clearly reads as not-yet-available', async () => {
    TestBed.configureTestingModule({ imports: [TransitWeatherPage] });
    const fixture = TestBed.createComponent(TransitWeatherPage);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('WETTER');
    expect(compiled.textContent).toContain('Wetter – noch nicht verfügbar');
  });

  it('renders both slots without errors and with non-blank content', async () => {
    TestBed.configureTestingModule({ imports: [TransitWeatherPage] });
    const fixture = TestBed.createComponent(TransitWeatherPage);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    const slots = compiled.querySelectorAll('.transit-weather-page__slot');
    expect(slots.length).toBe(2);
    for (const slot of Array.from(slots)) {
      expect(slot.textContent?.trim()).not.toBe('');
    }
  });
});
