import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { WeatherSnapshot } from './weather-snapshot';
import { WeatherService } from './weather.service';

describe('WeatherService', () => {
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

  it('starts undefined before the request resolves', () => {
    const service = TestBed.inject(WeatherService);

    expect(service.snapshot()).toBeUndefined();

    httpMock.expectOne('/api/weather').flush(freshSnapshot());
  });

  it('exposes a typed weather snapshot as a signal after a successful fetch, mapping every field', () => {
    const service = TestBed.inject(WeatherService);
    const response = freshSnapshot();

    const req = httpMock.expectOne('/api/weather');
    expect(req.request.method).toBe('GET');
    req.flush(response);

    const snapshot = service.snapshot();
    expect(snapshot).toEqual(response);
    expect(snapshot?.currentTemperature).toBe(18.5);
    expect(snapshot?.conditionText).toBe('Wolkig');
    expect(snapshot?.highTemperature).toBe(21);
    expect(snapshot?.lowTemperature).toBe(12.3);
    expect(snapshot?.hourly).toEqual([
      { hour: 14, temperature: 18.5, rainProbability: 10 },
      { hour: 15, temperature: 19.1, rainProbability: 15 },
    ]);
    expect(snapshot?.outlook).toEqual([
      { date: '2026-09-27', conditionText: 'Leichter Regen', highTemperature: 19.8, lowTemperature: 8.4 },
      { date: '2026-09-28', conditionText: 'Gewitter', highTemperature: 17.5, lowTemperature: 7.1 },
      { date: '2026-09-29', conditionText: 'Überwiegend klar', highTemperature: 22.1, lowTemperature: 10.6 },
    ]);
    expect(snapshot?.humidityPercent).toBe(62);
    expect(snapshot?.windSpeedKmh).toBe(11.4);
    expect(snapshot?.sunset).toBe('2026-09-26T19:32:00');
    expect(snapshot?.fetchedAt).toBe('2026-09-26T14:02:31.123456Z');
    expect(snapshot?.stale).toBe(false);
  });

  it('surfaces stale is true with the older fetchedAt from a failed-refresh fallback response, distinct from the fresh case', () => {
    const service = TestBed.inject(WeatherService);
    const response = staleSnapshot();

    httpMock.expectOne('/api/weather').flush(response);

    const snapshot = service.snapshot();
    expect(snapshot?.stale).toBe(true);
    expect(snapshot?.fetchedAt).toBe('2026-09-26T09:17:05.000000Z');
    // The rest of the last-known-good values still come through unchanged, not blanked.
    expect(snapshot?.currentTemperature).toBe(16.2);
    expect(snapshot?.conditionText).toBe('Regen');
  });

  it(
    'stays undefined and does not throw an unhandled error on a cold-start request failure ' +
      '(e.g. the backend\'s documented 503 before its first scheduled refresh has ever succeeded)',
    () => {
      const service = TestBed.inject(WeatherService);

      const req = httpMock.expectOne('/api/weather');
      // Flushing an error response is the failure case this test targets:
      // without WeatherService's own catchError, RxJS would report this as
      // an unhandled error (rethrown asynchronously) even though nothing
      // here calls `.toThrow()` around it — the assertion is that this
      // call itself doesn't blow up the test, plus the signal staying
      // undefined below.
      expect(() => req.flush('Cache not yet populated', { status: 503, statusText: 'Service Unavailable' })).not.toThrow();

      expect(service.snapshot()).toBeUndefined();
    },
  );

  function freshSnapshot(): WeatherSnapshot {
    return {
      currentTemperature: 18.5,
      conditionText: 'Wolkig',
      highTemperature: 21,
      lowTemperature: 12.3,
      hourly: [
        { hour: 14, temperature: 18.5, rainProbability: 10 },
        { hour: 15, temperature: 19.1, rainProbability: 15 },
      ],
      outlook: [
        { date: '2026-09-27', conditionText: 'Leichter Regen', highTemperature: 19.8, lowTemperature: 8.4 },
        { date: '2026-09-28', conditionText: 'Gewitter', highTemperature: 17.5, lowTemperature: 7.1 },
        { date: '2026-09-29', conditionText: 'Überwiegend klar', highTemperature: 22.1, lowTemperature: 10.6 },
      ],
      humidityPercent: 62,
      windSpeedKmh: 11.4,
      sunset: '2026-09-26T19:32:00',
      fetchedAt: '2026-09-26T14:02:31.123456Z',
      stale: false,
    };
  }

  function staleSnapshot(): WeatherSnapshot {
    return {
      currentTemperature: 16.2,
      conditionText: 'Regen',
      highTemperature: 17,
      lowTemperature: 10,
      hourly: [{ hour: 9, temperature: 16.2, rainProbability: 80 }],
      outlook: [{ date: '2026-09-27', conditionText: 'Bedeckt', highTemperature: 16, lowTemperature: 9.5 }],
      humidityPercent: 88,
      windSpeedKmh: 22.7,
      sunset: '2026-09-26T19:32:00',
      fetchedAt: '2026-09-26T09:17:05.000000Z',
      stale: true,
    };
  }
});
