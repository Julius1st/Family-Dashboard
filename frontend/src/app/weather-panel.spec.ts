import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { WeatherPanel } from './weather-panel';
import { WeatherSnapshot } from './weather-snapshot';
import { WeatherService } from './weather.service';

describe('WeatherPanel', () => {
  /*
   * `WeatherPanel.hourly()` reads the real wall clock (`new Date()`) to
   * slice the "rest of today" hourly window — so every test here fakes
   * only `Date` (not timers; leaving `setTimeout` etc. real avoids hanging
   * zoneless change detection's own scheduling) to a fixed morning time,
   * making every sample hour (09/11/.../19) fall in the future and the
   * fresh/stale-snapshot tests deterministic regardless of when the suite
   * actually runs. The dedicated windowing test below overrides this to a
   * different time to prove the slicing itself.
   */
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-09-26T08:00:00'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function configureWith(snapshot: WeatherSnapshot | undefined) {
    const snapshotSignal = signal(snapshot);
    const fake = { snapshot: snapshotSignal };
    TestBed.configureTestingModule({
      imports: [WeatherPanel],
      providers: [{ provide: WeatherService, useValue: fake }],
    });
    return fake;
  }

  async function createFixture(): Promise<ComponentFixture<WeatherPanel>> {
    const fixture = TestBed.createComponent(WeatherPanel);
    await fixture.whenStable();
    return fixture;
  }

  function freshSnapshot(overrides: Partial<WeatherSnapshot> = {}): WeatherSnapshot {
    return {
      currentTemperature: 14.4,
      conditionText: 'Wolkig',
      highTemperature: 21,
      lowTemperature: 12.3,
      hourly: [
        { hour: 9, temperature: 16, rainProbability: 10, rainAmountMm: 0.0 },
        { hour: 11, temperature: 18, rainProbability: 0, rainAmountMm: 0.0 },
        { hour: 13, temperature: 21, rainProbability: 0, rainAmountMm: 0.0 },
        { hour: 15, temperature: 21, rainProbability: 10, rainAmountMm: 0.2 },
        { hour: 17, temperature: 19, rainProbability: 60, rainAmountMm: 1.8 },
        { hour: 19, temperature: 16, rainProbability: 70, rainAmountMm: 2.4 },
      ],
      outlook: [
        {
          date: '2026-09-27',
          conditionText: 'Leichter Regen',
          highTemperature: 19.8,
          lowTemperature: 8.4,
          rainProbability: 20,
          rainAmountMm: 1.2,
        },
        {
          date: '2026-09-28',
          conditionText: 'Gewitter',
          highTemperature: 17.5,
          lowTemperature: 7.1,
          rainProbability: 90,
          rainAmountMm: 8.5,
        },
        {
          date: '2026-09-29',
          conditionText: 'Überwiegend klar',
          highTemperature: 22.1,
          lowTemperature: 10.6,
          rainProbability: 5,
          rainAmountMm: 0.3,
        },
      ],
      humidityPercent: 62,
      windSpeedKmh: 11.4,
      sunset: '2026-09-26T19:42:00',
      fetchedAt: '2026-09-26T14:02:31.123456Z',
      stale: false,
      ...overrides,
    };
  }

  it('renders a loading placeholder while the snapshot has not resolved yet', async () => {
    configureWith(undefined);

    const fixture = await createFixture();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Wetter wird geladen');
    expect(compiled.querySelectorAll('.weather-panel__hour').length).toBe(0);
    expect(compiled.querySelectorAll('.weather-panel__outlook-row').length).toBe(0);
  });

  it('renders the title row (eyebrow/location) and a fresh snapshot\'s current block, hourly strip and stats row', async () => {
    configureWith(freshSnapshot());

    const fixture = await createFixture();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.weather-panel__eyebrow')?.textContent?.trim()).toBe('WETTER');
    expect(compiled.querySelector('.weather-panel__title')?.textContent?.trim()).toBe('Karlsruhe');

    // Fresh snapshot: the static "heute" freshness copy, not dimmed/marked stale.
    const freshness = compiled.querySelector('.weather-panel__freshness');
    expect(freshness?.textContent?.trim()).toBe('heute');
    expect(freshness?.classList).not.toContain('weather-panel__freshness--stale');

    // Current block: temperature/condition/hi-lo, each rounded to a whole degree.
    expect(compiled.querySelector('.weather-panel__temp')?.textContent?.trim()).toBe('14°');
    expect(compiled.querySelector('.weather-panel__condition')?.textContent?.trim()).toBe('Wolkig');
    expect(compiled.querySelector('.weather-panel__hi-lo')?.textContent?.trim()).toBe('21° / 12°');

    // Hourly strip: one cell per hour, with the design's exact rain-bar
    // high/low accent split at the 50% threshold.
    const hours = Array.from(compiled.querySelectorAll<HTMLElement>('.weather-panel__hour'));
    expect(hours.length).toBe(6);
    expect(hours[0].querySelector('.weather-panel__hour-label')?.textContent?.trim()).toBe('09');
    expect(hours[0].querySelector('.weather-panel__hour-temp')?.textContent?.trim()).toBe('16°');

    // 0% rain still renders a visible (if empty) track, not a hidden one —
    // "The empty track is what makes 0% legible" (design handoff).
    const zeroRainHour = hours[1];
    expect(zeroRainHour.querySelector('.weather-panel__rain-percent')?.textContent?.trim()).toBe('0%');
    expect(zeroRainHour.querySelector<HTMLElement>('.weather-panel__rain-fill')?.style.height).toBe('0px');
    expect(zeroRainHour.querySelector('.weather-panel__rain-track')).not.toBeNull();
    expect(zeroRainHour.querySelector('.weather-panel__rain-percent')?.classList).not.toContain(
      'weather-panel__rain-percent--high',
    );
    // Expected rain amount alongside the probability, German-locale-formatted (comma decimal, "l/m²" unit).
    expect(zeroRainHour.querySelector('.weather-panel__rain-amount')?.textContent?.trim()).toBe('0,0 l/m²');

    // >=50% rain gets the "high" accent treatment on both the bar and the percentage text.
    const highRainHour = hours[4];
    expect(highRainHour.querySelector('.weather-panel__rain-percent')?.textContent?.trim()).toBe('60%');
    expect(highRainHour.querySelector('.weather-panel__rain-fill')?.classList).toContain(
      'weather-panel__rain-fill--high',
    );
    expect(highRainHour.querySelector('.weather-panel__rain-percent')?.classList).toContain(
      'weather-panel__rain-percent--high',
    );
    expect(highRainHour.querySelector<HTMLElement>('.weather-panel__rain-fill')?.style.height).toBe('18px');
    expect(highRainHour.querySelector('.weather-panel__rain-amount')?.textContent?.trim()).toBe('1,8 l/m²');

    // Stats row: humidity/wind/sunset, formatted (no raw ISO string leaking through).
    const stats = Array.from(compiled.querySelectorAll<HTMLElement>('.weather-panel__stat'));
    expect(stats.length).toBe(3);
    expect(stats[0].textContent).toContain('Luftfeuchte');
    expect(stats[0].textContent).toContain('62%');
    expect(stats[1].textContent).toContain('Wind');
    expect(stats[1].textContent).toContain('11 km/h');
    expect(stats[2].textContent).toContain('Sonnenuntergang');
    expect(stats[2].querySelector('.weather-panel__stat-value')?.textContent?.trim()).toBe('19:42');

    // Stats row alignment: same "1fr auto 1fr" pattern as .weather-panel__header
    // (eyebrow/title/freshness) — Wind (the middle item) centered in the row,
    // Sonnenuntergang (the last item) pinned to the row's far edge with both
    // of its lines right-aligned, Luftfeuchte (the first item) at its default
    // start position. Honesty note (same as transit-weather-page.spec.ts's own
    // grid-template-columns test): jsdom has no real layout engine, so this
    // only confirms the intended CSS rules apply to the right elements with
    // the right values, not a rendered-pixel confirmation.
    const statsGrid = compiled.querySelector<HTMLElement>('.weather-panel__stats');
    expect(getComputedStyle(statsGrid!).gridTemplateColumns).toBe('1fr auto 1fr');
    expect(getComputedStyle(stats[0]).justifySelf).not.toBe('center');
    expect(getComputedStyle(stats[0]).justifySelf).not.toBe('end');
    expect(getComputedStyle(stats[1]).justifySelf).toBe('center');
    expect(getComputedStyle(stats[2]).justifySelf).toBe('end');
    expect(getComputedStyle(stats[2].querySelector('.weather-panel__stat-label')!).textAlign).toBe('right');
    expect(getComputedStyle(stats[2].querySelector('.weather-panel__stat-value')!).textAlign).toBe('right');
    // Luftfeuchte/Wind's own label/value must NOT have picked up the same
    // right-alignment - the nth-child(3) selector should be scoped to
    // Sonnenuntergang only, not leak onto its siblings.
    expect(getComputedStyle(stats[0].querySelector('.weather-panel__stat-value')!).textAlign).not.toBe('right');
    expect(getComputedStyle(stats[1].querySelector('.weather-panel__stat-value')!).textAlign).not.toBe('right');

    // 3-day outlook: one row per day, below the stats row, each with a
    // weekday/condition/hi-lo line and a rain-risk/rain-amount line.
    const outlookRows = Array.from(compiled.querySelectorAll<HTMLElement>('.weather-panel__outlook-row'));
    expect(outlookRows.length).toBe(3);
    expect(outlookRows[0].querySelector('.weather-panel__outlook-day')?.textContent?.trim()).toBe('So');
    expect(outlookRows[0].querySelector('.weather-panel__outlook-condition')?.textContent?.trim()).toBe(
      'Leichter Regen',
    );
    expect(outlookRows[0].querySelector('.weather-panel__outlook-hilo')?.textContent?.trim()).toBe('20° / 8°');
    expect(outlookRows[0].querySelector('.weather-panel__outlook-rain-risk')?.textContent?.trim()).toBe('20%');
    expect(outlookRows[0].querySelector('.weather-panel__outlook-rain-risk')?.classList).not.toContain(
      'weather-panel__outlook-rain-risk--high',
    );
    expect(outlookRows[0].querySelector('.weather-panel__outlook-rain-amount')?.textContent?.trim()).toBe(
      '1,2 l/m²',
    );

    expect(outlookRows[1].querySelector('.weather-panel__outlook-day')?.textContent?.trim()).toBe('Mo');
    expect(outlookRows[1].querySelector('.weather-panel__outlook-condition')?.textContent?.trim()).toBe('Gewitter');
    expect(outlookRows[1].querySelector('.weather-panel__outlook-hilo')?.textContent?.trim()).toBe('18° / 7°');
    // >=50% day-level rain risk gets the same "high" accent as the hourly strip.
    expect(outlookRows[1].querySelector('.weather-panel__outlook-rain-risk')?.textContent?.trim()).toBe('90%');
    expect(outlookRows[1].querySelector('.weather-panel__outlook-rain-risk')?.classList).toContain(
      'weather-panel__outlook-rain-risk--high',
    );
    expect(outlookRows[1].querySelector('.weather-panel__outlook-rain-amount')?.textContent?.trim()).toBe(
      '8,5 l/m²',
    );

    expect(outlookRows[2].querySelector('.weather-panel__outlook-day')?.textContent?.trim()).toBe('Di');
    expect(outlookRows[2].querySelector('.weather-panel__outlook-condition')?.textContent?.trim()).toBe(
      'Überwiegend klar',
    );
    expect(outlookRows[2].querySelector('.weather-panel__outlook-hilo')?.textContent?.trim()).toBe('22° / 11°');
    expect(outlookRows[2].querySelector('.weather-panel__outlook-rain-risk')?.textContent?.trim()).toBe('5%');
    expect(outlookRows[2].querySelector('.weather-panel__outlook-rain-amount')?.textContent?.trim()).toBe(
      '0,3 l/m²',
    );
  });

  it('marks a stale snapshot visibly while still showing its last-known-good numbers, not blanking the widget', async () => {
    configureWith(
      freshSnapshot({
        stale: true,
        currentTemperature: 9.1,
        conditionText: 'Regen',
        fetchedAt: '2026-09-26T09:17:05.000000Z',
      }),
    );

    const fixture = await createFixture();
    const compiled = fixture.nativeElement as HTMLElement;

    // Freshness marker visibly differs from the fresh case, but the widget
    // is not blanked — the rest of the snapshot's values still render.
    const freshness = compiled.querySelector('.weather-panel__freshness');
    expect(freshness?.classList).toContain('weather-panel__freshness--stale');
    expect(freshness?.textContent?.trim()).toBe('veraltet · 09:17');

    expect(compiled.querySelector('.weather-panel__temp')?.textContent?.trim()).toBe('9°');
    expect(compiled.querySelector('.weather-panel__condition')?.textContent?.trim()).toBe('Regen');
    expect(compiled.querySelectorAll('.weather-panel__hour').length).toBe(6);
    expect(compiled.querySelectorAll('.weather-panel__stat').length).toBe(3);
  });

  it('only shows hours from the current local hour onward, not the full 24-hour array', async () => {
    vi.setSystemTime(new Date('2026-09-26T16:30:00'));

    configureWith(
      freshSnapshot({
        hourly: [
          { hour: 9, temperature: 16, rainProbability: 10, rainAmountMm: 0.0 },
          { hour: 15, temperature: 21, rainProbability: 10, rainAmountMm: 0.1 },
          { hour: 16, temperature: 20, rainProbability: 20, rainAmountMm: 0.3 },
          { hour: 17, temperature: 19, rainProbability: 60, rainAmountMm: 1.5 },
        ],
      }),
    );

    const fixture = await createFixture();
    const compiled = fixture.nativeElement as HTMLElement;

    const labels = Array.from(compiled.querySelectorAll('.weather-panel__hour-label')).map((el) =>
      el.textContent?.trim(),
    );
    expect(labels).toEqual(['16', '17']);
  });
});
