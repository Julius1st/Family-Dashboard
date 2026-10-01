import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { Departure, DeparturesResponse } from './departure';
import { DeparturesService } from './departures.service';

describe('DeparturesService', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('starts undefined before the request resolves, for both departures and stopName', () => {
    const service = TestBed.inject(DeparturesService);

    expect(service.departures()).toBeUndefined();
    expect(service.stopName()).toBeUndefined();

    httpMock.expectOne('/api/departures').flush(emptyResponse());
  });

  it('exposes a typed list of departures and the stop name as signals after a successful fetch, mapping every field, covering on-time/delayed/cancelled', () => {
    const service = TestBed.inject(DeparturesService);
    const response = threeDeparturesResponse();

    const req = httpMock.expectOne('/api/departures');
    expect(req.request.method).toBe('GET');
    req.flush(response);

    expect(service.stopName()).toBe('Wolfartsweierer Straße');
    const departures = service.departures();
    expect(departures).toEqual(response.departures);
    expect(departures?.[0]).toEqual({
      line: 'S2',
      destination: 'Bad Herrenalb',
      platform: 'Gl. 3',
      statusText: 'pünktlich',
      statusTone: 'ok',
      time: '2026-09-30T08:15:00',
      countdownMinutes: 9,
    });
    expect(departures?.[1]).toEqual({
      line: '5',
      destination: 'Rheinstetten Rathaus',
      platform: 'Gl. 2',
      statusText: '+3 Min',
      statusTone: 'late',
      time: '2026-09-30T08:23:00',
      countdownMinutes: 7,
    });
    expect(departures?.[2]).toEqual({
      line: '2',
      destination: 'Knielingen',
      platform: 'Gl. –',
      statusText: 'fällt aus',
      statusTone: 'cancelled',
      time: '2026-09-30T08:25:00',
      countdownMinutes: null,
    });
  });

  it('treats an empty departures array with a null stop name as a normal empty state, not an error — matching the backend\'s "no departures" and "provider not configured" cases alike', () => {
    const service = TestBed.inject(DeparturesService);

    httpMock.expectOne('/api/departures').flush(emptyResponse());

    expect(service.departures()).toEqual([]);
    expect(service.stopName()).toBeNull();
  });

  it('stays undefined (both departures and stopName) and does not throw an unhandled error on a request failure', () => {
    const service = TestBed.inject(DeparturesService);

    const req = httpMock.expectOne('/api/departures');
    expect(() => req.flush('Unexpected error', { status: 500, statusText: 'Internal Server Error' })).not.toThrow();

    expect(service.departures()).toBeUndefined();
    expect(service.stopName()).toBeUndefined();
  });

  /*
   * Polling: `DeparturesService` now re-fetches every 60s (not just once on
   * construction) — see its own doc comment for why (a departures board is
   * useless on a kiosk display if it never updates) and for the chosen
   * `interval` + `startWith(0)` + `switchMap` mechanism. These tests fake
   * only `Date`/`setInterval`/`clearInterval` (RxJS's async scheduler is
   * backed by `setInterval` under the hood — verified against
   * `node_modules/rxjs`'s own `intervalProvider`/`AsyncAction` sources, not
   * assumed), the same subset `header.spec.ts` fakes for its own
   * once-a-minute interval, leaving `setTimeout`/microtasks real so
   * `HttpTestingController`'s synchronous `flush()` and the zoneless
   * scheduler both keep working normally.
   */
  describe('periodic refresh (every 60s)', () => {
    beforeEach(() => {
      vi.useFakeTimers({ toFake: ['Date', 'setInterval', 'clearInterval'] });
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it('fetches immediately on construction, before any time has passed — not only after the first 60s tick', () => {
      TestBed.inject(DeparturesService);

      // No `advanceTimersByTime` call at all: if the implementation
      // regressed to waiting a full period before its first request (e.g.
      // plain `interval(60_000)` with no `startWith(0)`), this `expectOne`
      // would throw "Expected one matching request... found none", failing
      // the test immediately.
      httpMock.expectOne('/api/departures').flush(emptyResponse());
    });

    it('fetches again after 60 seconds, and the signal updates with the new response', async () => {
      const service = TestBed.inject(DeparturesService);

      httpMock.expectOne('/api/departures').flush(threeDeparturesResponse());
      expect(service.departures()).toEqual(threeDepartures());
      expect(service.stopName()).toBe('Wolfartsweierer Straße');

      await vi.advanceTimersByTimeAsync(60_000);

      const second = secondFetchResponse();
      httpMock.expectOne('/api/departures').flush(second);

      expect(service.departures()).toEqual(second.departures);
      expect(service.stopName()).toBe(second.stopName);
    });

    it('does not re-fetch before a full 60 seconds has elapsed', async () => {
      TestBed.inject(DeparturesService);

      httpMock.expectOne('/api/departures').flush(emptyResponse());

      await vi.advanceTimersByTimeAsync(59_000);

      httpMock.verify(); // still exactly one request made so far — a second one here would fail verify().
    });

    it('keeps showing the last-known-good data (does not clear to undefined/empty) when a periodic refresh fails', async () => {
      const service = TestBed.inject(DeparturesService);

      httpMock.expectOne('/api/departures').flush(threeDeparturesResponse());
      const staleDepartures = service.departures();
      const staleStopName = service.stopName();
      expect(staleDepartures).toEqual(threeDepartures());

      await vi.advanceTimersByTimeAsync(60_000);

      const failedReq = httpMock.expectOne('/api/departures');
      expect(() =>
        failedReq.flush('Upstream TRIAS error', { status: 502, statusText: 'Bad Gateway' }),
      ).not.toThrow();

      // Stale data from the first, successful fetch is still exactly what's exposed — not cleared, not an error state.
      expect(service.departures()).toBe(staleDepartures);
      expect(service.departures()).toEqual(threeDepartures());
      expect(service.stopName()).toBe(staleStopName);

      // And polling isn't dead after one failed tick: the next 60s tick still fetches again successfully.
      await vi.advanceTimersByTimeAsync(60_000);
      const third = secondFetchResponse();
      httpMock.expectOne('/api/departures').flush(third);
      expect(service.departures()).toEqual(third.departures);
      expect(service.stopName()).toBe(third.stopName);
    });
  });

  function emptyResponse(): DeparturesResponse {
    return { stopName: null, departures: [] };
  }

  function threeDeparturesResponse(): DeparturesResponse {
    return { stopName: 'Wolfartsweierer Straße', departures: threeDepartures() };
  }

  /**
   * A distinct, single-departure response used to assert a *second* poll's
   * data genuinely replaced the first — different from both
   * `threeDeparturesResponse()` and `emptyResponse()` so a test can't
   * accidentally pass by comparing a response against itself or against
   * the initial-fetch default.
   */
  function secondFetchResponse(): DeparturesResponse {
    return {
      stopName: 'Wolfartsweierer Straße',
      departures: [
        {
          line: '3',
          destination: 'Durlach',
          platform: 'Gl. 1',
          statusText: 'pünktlich',
          statusTone: 'ok',
          time: '2026-09-30T08:16:00',
          countdownMinutes: 1,
        },
      ],
    };
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
});
