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

  it(
    'gives the app shell a definite height (not just a minimum), so the header + active page ' +
      'are forced to share a fixed budget instead of growing the shell past the viewport',
    async () => {
      // Regression test: `:host` used to be `min-height: 100%`, which is
      // only a floor — it let the shell grow TALLER than the viewport
      // whenever its children demanded more space, silently overriding
      // the min-height:0/overflow-y:auto flex-clip chain built further
      // down in tasks-page.css (the Tasks widget kept "outgrowing the
      // screen with a lot of tasks" even after that chain was fixed,
      // because this ancestor never actually constrained the space those
      // descendants had to fit into). `height: 100%` is a definite size,
      // which is what makes that chain actually take effect.
      //
      // jsdom does no real layout, so this only proves the CSS declares a
      // definite `height` rather than a `min-height` — it cannot
      // empirically confirm the browser page no longer grows/scrolls.
      const fixture = TestBed.createComponent(App);
      await fixture.whenStable();

      const hostStyle = getComputedStyle(fixture.nativeElement as HTMLElement);
      expect(hostStyle.height).toBe('100%');
      expect(hostStyle.minHeight).not.toBe('100%');
    },
  );

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
