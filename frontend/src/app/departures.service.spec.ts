import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

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

  function emptyResponse(): DeparturesResponse {
    return { stopName: null, departures: [] };
  }

  function threeDeparturesResponse(): DeparturesResponse {
    return { stopName: 'Wolfartsweierer Straße', departures: threeDepartures() };
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
