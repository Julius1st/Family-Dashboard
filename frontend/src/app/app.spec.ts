import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { App } from './app';
import { PageNavigationService } from './page-navigation.service';

describe('App', () => {
  beforeEach(async () => {
    // The header renders a live clock; fake `Date`/interval timers keep
    // this smoke test from depending on real wall-clock time, while
    // leaving `setTimeout`/microtasks real so Angular's zoneless scheduler
    // can still resolve `fixture.whenStable()`.
    vi.useFakeTimers({ toFake: ['Date', 'setInterval', 'clearInterval'] });
    vi.setSystemTime(new Date('2026-09-10T07:42:00'));

    // Since Ticket 3, `TasksPage` injects the real `TodoService`, which
    // fires `GET /api/todos`/`GET /api/todos/members` from its
    // constructor — this test mounts the real `App` (not a fake
    // `TodoService`), so it needs `HttpClientTesting` to keep those
    // requests from hitting the real network as unhandled errors. This
    // smoke test only asserts which page component is mounted, not on
    // to-do content, so the requests are left unflushed/unverified on
    // purpose.
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('mounts the header and the tasks page by default', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('app-header')).toBeTruthy();
    expect(compiled.querySelector('app-tasks-page')).toBeTruthy();
    expect(compiled.querySelector('app-transit-weather-page')).toBeFalsy();
  });

  it('switches to the transit-weather page when the nav service selects it', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    TestBed.inject(PageNavigationService).select('transit');
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-transit-weather-page')).toBeTruthy();
    expect(compiled.querySelector('app-tasks-page')).toBeFalsy();
  });
});
