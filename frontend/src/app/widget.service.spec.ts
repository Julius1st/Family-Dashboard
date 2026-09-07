import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { WidgetDescriptor } from './widget-descriptor';
import { WidgetService } from './widget.service';

describe('WidgetService', () => {
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

  it('exposes a typed list of widgets as a signal after a successful fetch', () => {
    const service = TestBed.inject(WidgetService);
    const response: WidgetDescriptor[] = [
      { id: 'todo', displayName: 'Todo' },
      { id: 'weather', displayName: 'Weather' },
    ];

    const req = httpMock.expectOne('/api/widgets');
    expect(req.request.method).toBe('GET');
    req.flush(response);

    expect(service.widgets()).toEqual(response);
  });

  it('treats an empty array response as a normal empty state, not an error', () => {
    const service = TestBed.inject(WidgetService);

    const req = httpMock.expectOne('/api/widgets');
    req.flush([]);

    expect(service.widgets()).toEqual([]);
  });

  it('starts undefined before the request resolves', () => {
    const service = TestBed.inject(WidgetService);

    expect(service.widgets()).toBeUndefined();

    httpMock.expectOne('/api/widgets').flush([]);
  });
});
