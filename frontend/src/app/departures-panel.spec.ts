import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { Departure } from './departure';
import { DeparturesPanel } from './departures-panel';
import { DeparturesService } from './departures.service';

describe('DeparturesPanel', () => {
  /*
   * `DeparturesPanel.freshnessLabel` reads the real wall clock (`new
   * Date()`) to render the "Stand hh:mm" marker — see its own doc comment
   * for why this is a safe, precedented pattern (same as `WeatherPanel.
   * hourly`'s `new Date().getHours()` read). Faking `Date` (not timers, for
   * the same "avoid hanging zoneless change detection's own scheduling"
   * reason `weather-panel.spec.ts` gives) makes that marker deterministic.
   */
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-09-30T08:12:34'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function configureWith(state: {
    departures: readonly Departure[] | undefined;
    stopName: string | null | undefined;
  }) {
    const departuresSignal = signal(state.departures);
    const stopNameSignal = signal(state.stopName);
    const fake = { departures: departuresSignal, stopName: stopNameSignal };
    TestBed.configureTestingModule({
      imports: [DeparturesPanel],
      providers: [{ provide: DeparturesService, useValue: fake }],
    });
    return fake;
  }

  async function createFixture(): Promise<ComponentFixture<DeparturesPanel>> {
    const fixture = TestBed.createComponent(DeparturesPanel);
    await fixture.whenStable();
    return fixture;
  }

  function threeDepartures(): Departure[] {
    return [
      {
        line: 'S2',
        destination: 'Bad Herrenalb',
        platform: 'Gl. 3',
        statusText: 'pünktlich',
        statusTone: 'ok',
        time: '2026-09-30T08:15:00',
        countdownMinutes: 9,
      },
      {
        line: '5',
        destination: 'Rheinstetten Rathaus',
        platform: 'Gl. 2',
        statusText: '+3 Min',
        statusTone: 'late',
        time: '2026-09-30T08:23:00',
        countdownMinutes: 7,
      },
      {
        line: '2',
        destination: 'Knielingen',
        platform: 'Gl. –',
        statusText: 'fällt aus',
        statusTone: 'cancelled',
        time: '2026-09-30T08:25:00',
        countdownMinutes: null,
      },
    ];
  }

  it('renders a loading placeholder while departures have not resolved yet, with a blank title, no rows and no freshness marker', async () => {
    configureWith({ departures: undefined, stopName: undefined });

    const fixture = await createFixture();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.departures-panel__eyebrow')?.textContent?.trim()).toBe('ABFAHRTEN');
    expect(compiled.querySelector('.departures-panel__title')?.textContent?.trim()).toBe('');
    expect(compiled.textContent).toContain('Abfahrten werden geladen');
    expect(compiled.querySelectorAll('.departures-panel__row').length).toBe(0);
    expect(compiled.querySelector('.departures-panel__freshness')?.textContent?.trim()).toBe('');
  });

  it('renders a non-blank empty state when the resolved list is genuinely empty, distinct from loading, with the generic fallback title when the stop name is unknown', async () => {
    configureWith({ departures: [], stopName: null });

    const fixture = await createFixture();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.departures-panel__title')?.textContent?.trim()).toBe('Haltestelle');
    expect(compiled.textContent).toContain('Keine Abfahrten.');
    expect(compiled.textContent).not.toContain('Abfahrten werden geladen');
    expect(compiled.querySelectorAll('.departures-panel__row').length).toBe(0);
    // Resolved-but-empty still counts as "data received" for the freshness marker.
    expect(compiled.querySelector('.departures-panel__freshness')?.textContent?.trim()).toBe('Stand 08:12');
  });

  it(
    'updates the freshness marker on a subsequent refresh even when the new data is value-identical to the old ' +
      '(a new array reference alone must be enough — DeparturesService never reuses a prior response\'s object ' +
      'identity, since every poll is a fresh JSON deserialization), proving this is genuinely driven by ' +
      'DeparturesService#departures changing on every poll, not just once',
    async () => {
      const state = configureWith({ departures: threeDepartures(), stopName: 'Wolfartsweierer Straße' });
      const fixture = await createFixture();
      const compiled = fixture.nativeElement as HTMLElement;

      expect(compiled.querySelector('.departures-panel__freshness')?.textContent?.trim()).toBe('Stand 08:12');

      // Simulate the next 60s poll resolving with byte-for-byte identical
      // departure data — a new array (and new element objects within it),
      // as every real `HttpClient` JSON response produces, but `toEqual`
      // to the previous one. Nothing here changes `rows()`'s rendered
      // content; the only thing that should move is the freshness marker.
      vi.setSystemTime(new Date('2026-09-30T08:13:45'));
      const identicalContentNewReference = threeDepartures();
      expect(identicalContentNewReference).not.toBe(state.departures());
      expect(identicalContentNewReference).toEqual(state.departures());
      state.departures.set(identicalContentNewReference);
      await fixture.whenStable();

      expect(compiled.querySelector('.departures-panel__freshness')?.textContent?.trim()).toBe('Stand 08:13');
      // The rows themselves are unaffected — only the clock, confirming
      // this isn't accidentally the row content changing too.
      expect(compiled.querySelectorAll('.departures-panel__row').length).toBe(3);
    },
  );

  it('renders the title row and one row per departure, covering on-time/delayed/cancelled', async () => {
    configureWith({ departures: threeDepartures(), stopName: 'Wolfartsweierer Straße' });

    const fixture = await createFixture();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.departures-panel__eyebrow')?.textContent?.trim()).toBe('ABFAHRTEN');
    expect(compiled.querySelector('.departures-panel__title')?.textContent?.trim()).toBe('Wolfartsweierer Straße');
    expect(compiled.querySelector('.departures-panel__freshness')?.textContent?.trim()).toBe('Stand 08:12');

    const rows = Array.from(compiled.querySelectorAll<HTMLElement>('.departures-panel__row'));
    expect(rows.length).toBe(3);

    // On time: default (unmarked) status/time styling, real countdown text.
    const onTime = rows[0];
    expect(onTime.querySelector('.departures-panel__line')?.textContent?.trim()).toBe('S2');
    expect(onTime.querySelector('.departures-panel__destination')?.textContent?.trim()).toBe('Bad Herrenalb');
    expect(onTime.querySelector('.departures-panel__platform')?.textContent?.trim()).toBe('Gl. 3');
    const onTimeStatus = onTime.querySelector('.departures-panel__status');
    expect(onTimeStatus?.textContent?.trim()).toBe('pünktlich');
    expect(onTimeStatus?.classList).not.toContain('departures-panel__status--late');
    expect(onTimeStatus?.classList).not.toContain('departures-panel__status--cancelled');
    expect(onTime.querySelector('.departures-panel__time')?.textContent?.trim()).toBe('08:15');
    expect(onTime.querySelector('.departures-panel__time')?.classList).not.toContain('departures-panel__time--cancelled');
    expect(onTime.querySelector('.departures-panel__countdown')?.textContent?.trim()).toBe('in 9 Min');

    // Delayed: status carries the "late" tone class, countdown still a real value.
    const delayed = rows[1];
    expect(delayed.querySelector('.departures-panel__line')?.textContent?.trim()).toBe('5');
    expect(delayed.querySelector('.departures-panel__destination')?.textContent?.trim()).toBe('Rheinstetten Rathaus');
    expect(delayed.querySelector('.departures-panel__platform')?.textContent?.trim()).toBe('Gl. 2');
    const delayedStatus = delayed.querySelector('.departures-panel__status');
    expect(delayedStatus?.textContent?.trim()).toBe('+3 Min');
    expect(delayedStatus?.classList).toContain('departures-panel__status--late');
    expect(delayed.querySelector('.departures-panel__time')?.textContent?.trim()).toBe('08:23');
    expect(delayed.querySelector('.departures-panel__countdown')?.textContent?.trim()).toBe('in 7 Min');

    // Cancelled: status carries the "cancelled" tone class, time is struck-
    // through/dimmed, and "—" replaces the countdown per the design's exact
    // spec for this case.
    const cancelled = rows[2];
    expect(cancelled.querySelector('.departures-panel__line')?.textContent?.trim()).toBe('2');
    expect(cancelled.querySelector('.departures-panel__destination')?.textContent?.trim()).toBe('Knielingen');
    expect(cancelled.querySelector('.departures-panel__platform')?.textContent?.trim()).toBe('Gl. –');
    const cancelledStatus = cancelled.querySelector('.departures-panel__status');
    expect(cancelledStatus?.textContent?.trim()).toBe('fällt aus');
    expect(cancelledStatus?.classList).toContain('departures-panel__status--cancelled');
    const cancelledTime = cancelled.querySelector('.departures-panel__time');
    expect(cancelledTime?.textContent?.trim()).toBe('08:25');
    expect(cancelledTime?.classList).toContain('departures-panel__time--cancelled');
    const cancelledCountdown = cancelled.querySelector('.departures-panel__countdown');
    expect(cancelledCountdown?.textContent?.trim()).toBe('—');
    expect(cancelledCountdown?.classList).toContain('departures-panel__countdown--cancelled');
  });

  it(
    'colors a recognized line badge with its official KVV color, and leaves an unrecognized/bus line on the ' +
      'default neutral badge styling (no inline background/color at all) — see kvv-line-colors.spec.ts for full ' +
      'coverage of the color table itself',
    async () => {
      const departures: Departure[] = [
        {
          line: '4', // tram line 4: verkehrsgelb #f1c21f, dark text
          destination: 'Durlach Turmberg',
          platform: 'Gl. 1',
          statusText: 'pünktlich',
          statusTone: 'ok',
          time: '2026-09-30T08:15:00',
          countdownMinutes: 3,
        },
        {
          line: 'S2', // Stadtbahn S2: signalviolett #804387, light text
          destination: 'Bad Herrenalb',
          platform: 'Gl. 3',
          statusText: 'pünktlich',
          statusTone: 'ok',
          time: '2026-09-30T08:16:00',
          countdownMinutes: 4,
        },
        {
          line: '42', // bus line — not covered by the KVV wiki source at all
          destination: 'Rintheim',
          platform: 'Bstg. 2',
          statusText: 'pünktlich',
          statusTone: 'ok',
          time: '2026-09-30T08:17:00',
          countdownMinutes: 5,
        },
      ];
      configureWith({ departures, stopName: 'Marktplatz' });

      const fixture = await createFixture();
      const compiled = fixture.nativeElement as HTMLElement;
      const rows = Array.from(compiled.querySelectorAll<HTMLElement>('.departures-panel__row'));

      const tramLine = rows[0].querySelector<HTMLElement>('.departures-panel__line');
      expect(tramLine?.style.background).toBe('rgb(241, 194, 31)'); // #f1c21f
      expect(tramLine?.style.color).toBe('rgb(0, 0, 0)'); // #000000

      const stadtbahnLine = rows[1].querySelector<HTMLElement>('.departures-panel__line');
      expect(stadtbahnLine?.style.background).toBe('rgb(128, 67, 135)'); // #804387
      expect(stadtbahnLine?.style.color).toBe('rgb(255, 255, 255)'); // #ffffff

      // Bus line: no inline background/color set at all, so the element
      // falls through to the stylesheet's default bg/inset-hi badge.
      const busLine = rows[2].querySelector<HTMLElement>('.departures-panel__line');
      expect(busLine?.style.background).toBe('');
      expect(busLine?.style.color).toBe('');
    },
  );
});
