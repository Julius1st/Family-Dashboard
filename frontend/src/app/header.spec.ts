import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { Header } from './header';
import { PageNavigationService } from './page-navigation.service';
import { ThemeService } from './theme.service';

describe('Header', () => {
  beforeEach(() => {
    // Fake only `Date`/`setInterval`/`clearInterval` — the clock's own
    // timing primitives — and leave `setTimeout`/microtasks real, since
    // Angular's zoneless change-detection scheduler relies on those to
    // resolve `fixture.whenStable()`.
    vi.useFakeTimers({ toFake: ['Date', 'setInterval', 'clearInterval'] });
    vi.setSystemTime(new Date('2026-09-10T07:42:00'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders the clock (mono, no seconds) and the German date', async () => {
    TestBed.configureTestingModule({ imports: [Header] });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.header__clock')?.textContent?.trim()).toBe('07:42');
    expect(compiled.querySelector('.header__date')?.textContent?.trim()).toBe('Donnerstag, 10. September');
  });

  it('updates the clock once a minute without a manual refresh', async () => {
    TestBed.configureTestingModule({ imports: [Header] });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();

    // Advance the shared fake clock (which also drives the faked `Date`)
    // by exactly one interval tick, rather than calling `setSystemTime`
    // again — jumping `Date` independently of the clock used to schedule
    // the interval would let the pending tick land mid-advance and fire
    // twice within the window.
    await vi.advanceTimersByTimeAsync(60_000);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.header__clock')?.textContent?.trim()).toBe('07:43');
  });

  it('does not update the clock before a full minute has elapsed', async () => {
    TestBed.configureTestingModule({ imports: [Header] });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();

    await vi.advanceTimersByTimeAsync(59_000);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.header__clock')?.textContent?.trim()).toBe('07:42');
  });

  it('defaults to the tasks page marked active in the nav pill', async () => {
    TestBed.configureTestingModule({ imports: [Header] });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    const [tasksItem, transitItem] = Array.from(compiled.querySelectorAll<HTMLButtonElement>('.header__nav-item'));
    expect(tasksItem.classList).toContain('header__nav-item--active');
    expect(tasksItem.getAttribute('aria-current')).toBe('page');
    expect(transitItem.classList).not.toContain('header__nav-item--active');
    expect(transitItem.getAttribute('aria-current')).toBeNull();
  });

  it('tapping the inactive nav item switches the shared active page', async () => {
    TestBed.configureTestingModule({ imports: [Header] });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();
    const navigation = TestBed.inject(PageNavigationService);

    const compiled = fixture.nativeElement as HTMLElement;
    const [, transitItem] = Array.from(compiled.querySelectorAll<HTMLButtonElement>('.header__nav-item'));
    transitItem.click();
    await fixture.whenStable();

    expect(navigation.activePage()).toBe('transit');
    const [tasksItem, transitItemAfter] = Array.from(
      compiled.querySelectorAll<HTMLButtonElement>('.header__nav-item'),
    );
    expect(tasksItem.classList).not.toContain('header__nav-item--active');
    expect(transitItemAfter.classList).toContain('header__nav-item--active');
  });

  it('meets the >=44px touch target baseline for both nav items', async () => {
    TestBed.configureTestingModule({ imports: [Header] });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    for (const item of Array.from(compiled.querySelectorAll<HTMLButtonElement>('.header__nav-item'))) {
      const minHeight = getComputedStyle(item).minHeight;
      expect(minHeight).toBe('44px');
    }
  });

  it('the active nav pill text genuinely resolves via --ink-on-pill-active, not --ink-on-accent', async () => {
    TestBed.configureTestingModule({ imports: [Header] });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    const activeItem = compiled.querySelector<HTMLButtonElement>('.header__nav-item--active');
    expect(activeItem).not.toBeNull();
    // jsdom doesn't substitute var() inside standard properties like
    // `color`, so getComputedStyle hands back the literal declaration —
    // which is exactly what lets this assert on the *token name* used,
    // not just a resolved color value that could match either token.
    const color = getComputedStyle(activeItem!).color;
    expect(color).toContain('var(--ink-on-pill-active)');
    expect(color).not.toContain('var(--ink-on-accent)');
  });

  it('renders a >=44px theme toggle button labeled with the current theme', async () => {
    TestBed.configureTestingModule({ imports: [Header] });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    const toggle = compiled.querySelector<HTMLButtonElement>('.header__theme-toggle');
    expect(toggle).not.toBeNull();
    expect(toggle!.textContent).toContain('Helles Design');

    const rect = getComputedStyle(toggle!).minHeight;
    expect(rect).toBe('44px');
  });

  it('tapping the theme toggle calls ThemeService.toggle()', async () => {
    TestBed.configureTestingModule({ imports: [Header] });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();
    const themeService = TestBed.inject(ThemeService);
    const toggleSpy = vi.spyOn(themeService, 'toggle');

    const compiled = fixture.nativeElement as HTMLElement;
    const toggle = compiled.querySelector<HTMLButtonElement>('.header__theme-toggle');
    toggle!.click();

    expect(toggleSpy).toHaveBeenCalledOnce();
  });
});
